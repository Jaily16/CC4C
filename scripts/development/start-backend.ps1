# 运行前提：已准备宿主机环境文件，并以精确数据库名确认目标；MySQL、Redis、RabbitMQ 和邮件服务已由外部提供。
# 破坏性边界：只启动本脚本创建的一个 CC4C JAR 进程；不接管外部依赖、不启动 Compose、不修改数据或本机配置。
# 失败恢复：启动后状态记录失败时，只按本次返回的精确 PID 校验并停止该进程；不按进程名批量处理。
# 退出码：启动并记录成功返回 0，前置检查、JAR、Java 或进程校验失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ConfirmDatabase,
    [ValidateRange(1, 65535)][int] $ApplicationPort = 4080,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081,
    [string] $RuntimeEnvironmentPath,
    [Parameter(ValueFromRemainingArguments = $true)][string[]] $ApplicationArguments
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')
$started = $null
$javaPath = $null
$environmentPathSnapshot = $null

function Get-Cc4cProcessInfo {
    param([Parameter(Mandatory = $true)][int] $ProcessId)
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function Stop-Cc4cStartedProcess {
    param(
        [Parameter(Mandatory = $true)] $Process,
        [Parameter(Mandatory = $true)][string] $ExpectedExecutable,
        [Parameter(Mandatory = $true)][string] $ExpectedJar
    )
    if ($null -eq $Process) {
        return
    }
    $current = Get-Cc4cProcessInfo $Process.Id
    if ($null -eq $current) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($current.ExecutablePath) -or
        [System.IO.Path]::GetFullPath($current.ExecutablePath) -cne [System.IO.Path]::GetFullPath($ExpectedExecutable) -or
        ([string] $current.CommandLine) -notlike "*$ExpectedJar*") {
        throw 'The started PID no longer matches the recorded CC4C executable and JAR.'
    }
    Stop-Process -Id $Process.Id -ErrorAction Stop
}

try {
    $environmentPathSnapshot = Set-Cc4cHostEnvironmentPathOverrides -RuntimePath $RuntimeEnvironmentPath
    $runtime = Read-Cc4cEnvironmentFile -Kind Runtime
    $values = Assert-Cc4cRuntimeEnvironment $runtime -ManagementPort $ManagementPort
    if ((Get-Cc4cDatabaseName $values) -cne $ConfirmDatabase) {
        throw 'ConfirmDatabase does not exactly match the configured database.'
    }
    & (Join-Path $PSScriptRoot 'host-preflight.ps1') `
        -Component Backend `
        -ConfirmDatabase $ConfirmDatabase `
        -ApplicationPort $ApplicationPort `
        -ManagementPort $ManagementPort `
        -RuntimeEnvironmentPath $RuntimeEnvironmentPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Host backend preflight failed.'
    }
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $backendRoot = Join-Path $workspaceRoot 'backend'
    $jarPath = Join-Path $backendRoot 'target\cc4c-5.0.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Missing backend JAR: $jarPath"
    }
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    $javaPath = (Resolve-Path -LiteralPath $javaCommand.Source).Path
    $runDirectory = Join-Path $workspaceRoot 'temp\cc4c-host-backend'
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
    $state = Read-Cc4cHostState 'backend'
    if ($null -ne $state) {
        # Windows 会复用已退出进程的 PID；不能仅凭编号存在就判定旧后端仍在运行。
        # 查询失败、记录不完整或存活进程身份不可读时停止；确认属于其他程序后只忽略旧记录，绝不结束该进程。
        $recordedPid = 0
        $recordedExecutable = [string] $state.executablePath
        $recordedJar = [string] $state.jarFileName
        if (-not [int]::TryParse([string] $state.pid, [ref] $recordedPid) -or $recordedPid -le 0 -or
            [string]::IsNullOrWhiteSpace($recordedExecutable) -or
            -not [System.IO.Path]::IsPathRooted($recordedExecutable) -or
            [string]::IsNullOrWhiteSpace($recordedJar)) {
            throw 'The recorded backend process identity is incomplete; inspect the state before restarting.'
        }
        $recordedExecutable = [System.IO.Path]::GetFullPath($recordedExecutable)
        $existing = Get-CimInstance Win32_Process -Filter "ProcessId = $recordedPid" -ErrorAction Stop
        if ($null -ne $existing) {
            if ([string]::IsNullOrWhiteSpace([string] $existing.ExecutablePath)) {
                throw 'The recorded backend PID identity cannot be verified; no process was changed.'
            }
            $sameExecutable = [string]::Equals(
                [System.IO.Path]::GetFullPath([string] $existing.ExecutablePath),
                $recordedExecutable,
                [System.StringComparison]::OrdinalIgnoreCase)
            if ($sameExecutable) {
                if ([string]::IsNullOrWhiteSpace([string] $existing.CommandLine)) {
                    throw 'The recorded backend PID command line cannot be verified; no process was changed.'
                }
                if (([string] $existing.CommandLine).IndexOf($recordedJar, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
                    throw 'A recorded CC4C backend process is still present.'
                }
            }
            Write-Output 'The previous backend PID belongs to a different process; leaving that process unchanged.'
        }
    }
    $extraArguments = if ($null -eq $ApplicationArguments) {
        @()
    } else {
        @($ApplicationArguments | Where-Object { $null -ne $_ -and $_ -ne '' })
    }
    $arguments = @(
        '-jar',
        $jarPath,
        "--server.port=$ApplicationPort",
        "--management.server.port=$ManagementPort"
    ) + $extraArguments
    $environmentSnapshot = $null
    $started = $null
    try {
        $environmentSnapshot = Set-Cc4cProcessEnvironment $values
        $started = Start-Process -FilePath $javaPath -WorkingDirectory $backendRoot -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'backend.stdout.log') -RedirectStandardError (Join-Path $runDirectory 'backend.stderr.log')
    }
    finally {
        if ($null -ne $environmentSnapshot) {
            Restore-Cc4cProcessEnvironment $environmentSnapshot
        }
    }
    if ($null -eq $started) {
        throw 'The backend process was not created.'
    }
    Start-Sleep -Milliseconds 250
    $processInfo = Get-Cc4cProcessInfo $started.Id
    $processMatches = $null -ne $processInfo -and
        -not [string]::IsNullOrWhiteSpace($processInfo.ExecutablePath) -and
        [System.IO.Path]::GetFullPath($processInfo.ExecutablePath) -ceq [System.IO.Path]::GetFullPath($javaPath) -and
        ([string] $processInfo.CommandLine) -like '*cc4c-5.0.0-SNAPSHOT.jar*'
    if (-not $processMatches) {
        Stop-Cc4cStartedProcess $started $javaPath 'cc4c-5.0.0-SNAPSHOT.jar'
        throw 'The created process did not match the CC4C JAR identity.'
    }
    $record = [ordered]@{
        component = 'backend'
        pid = $started.Id
        executablePath = [System.IO.Path]::GetFullPath($javaPath)
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        jarFileName = 'cc4c-5.0.0-SNAPSHOT.jar'
        workingDirectory = $backendRoot
        argumentCount = $extraArguments.Count
        commandLineSummary = 'java -jar cc4c-5.0.0-SNAPSHOT.jar'
        managementPort = $ManagementPort
        applicationPort = $ApplicationPort
        status = 'running'
    }
    Write-Cc4cHostState 'backend' $record | Out-Null
    Write-Output "CC4C backend started with PID $($started.Id)."
    exit 0
}
catch {
    if ($null -ne $started -and $null -ne $javaPath) {
        try {
            $current = Get-Cc4cProcessInfo $started.Id
            if ($null -ne $current -and
                -not [string]::IsNullOrWhiteSpace($current.ExecutablePath) -and
                [System.IO.Path]::GetFullPath($current.ExecutablePath) -ceq [System.IO.Path]::GetFullPath($javaPath) -and
                ([string] $current.CommandLine) -like '*cc4c-5.0.0-SNAPSHOT.jar*') {
                Stop-Process -Id $started.Id -ErrorAction SilentlyContinue
            }
        } catch { }
    }
    Write-Error $_.Exception.Message
    exit 1
}
finally {
    if ($null -ne $environmentPathSnapshot) {
        Restore-Cc4cHostEnvironmentPathOverrides $environmentPathSnapshot
    }
}
