# 运行前提：只使用 temp/cc4c-host-stack/backend.json 中的记录。
# 破坏性边界：仅停止记录的单个 PID，并核对可执行文件和 JAR；禁止按进程名、端口或用户批量终止。
# 失败恢复：PID 不匹配时拒绝操作并保留记录，等待人工确认；不删除日志、数据或上传文件。
# 退出码：目标已停止或不存在返回 0，身份校验或停止失败返回非零码。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

function Get-Cc4cProcessInfo {
    param([Parameter(Mandatory = $true)][int] $ProcessId)
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

try {
    $state = Read-Cc4cHostState 'backend'
    if ($null -eq $state) {
        Write-Output 'No recorded host backend process exists.'
        exit 0
    }
    $process = Get-Cc4cProcessInfo ([int] $state.pid)
    if ($null -ne $process) {
        $expected = [System.IO.Path]::GetFullPath([string] $state.executablePath)
        $actual = if ([string]::IsNullOrWhiteSpace($process.ExecutablePath)) {
            ''
        } else {
            [System.IO.Path]::GetFullPath($process.ExecutablePath)
        }
        if ($actual -cne $expected -or ([string] $process.CommandLine) -notlike "*$($state.jarFileName)*") {
            throw 'Recorded backend PID no longer belongs to the expected CC4C JAR.'
        }
        Stop-Process -Id ([int] $state.pid) -ErrorAction Stop
        $deadline = (Get-Date).AddSeconds(10)
        do {
            Start-Sleep -Milliseconds 200
            $stillRunning = Get-Cc4cProcessInfo ([int] $state.pid)
        } while ($null -ne $stillRunning -and (Get-Date) -lt $deadline)
        if ($null -ne $stillRunning) {
            throw 'The recorded backend process did not stop within the timeout.'
        }
    }
    $updatedState = [ordered]@{}
    foreach ($property in $state.PSObject.Properties) {
        $updatedState[$property.Name] = $property.Value
    }
    $updatedState.status = 'stopped'
    $updatedState.stoppedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-Cc4cHostState 'backend' $updatedState | Out-Null
    Write-Output 'Recorded CC4C backend process was stopped when present.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
