#requires -Version 7.0
# 运行前提：backend/.env.local、Java 21 和生产 JAR 已准备，用户确认精确数据库名。
# 外部依赖：依赖共享环境加载器、Java 21、生产 JAR 及用户预先运行的 MySQL、Redis、RabbitMQ 和 SMTP。
# 破坏性边界：仅启动当前工作区的一个后端；Flyway 按既有规则运行，不接管中间件。
# 失败恢复：恢复进程变量，仅停止本次返回且身份完整的 PID，日志使用唯一名称并保留。
# 退出码：启动和状态记录成功为 0；预检、启动或身份验证失败为 1。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ConfirmDatabase,
    [ValidateRange(1, 65535)][int] $ApplicationPort = 4080,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\infrastructure\host\host-environment.ps1')
$started = $null
$identity = $null
$snapshot = $null

try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $backendRoot = Join-Path $workspaceRoot 'backend'
    Assert-Cc4cCanStart backend
    $preflight = @{ Component = 'Backend'; ConfirmDatabase = $ConfirmDatabase; ApplicationPort = $ApplicationPort; ManagementPort = $ManagementPort }
    & (Join-Path $workspaceRoot 'infrastructure\host\host-preflight.ps1') @preflight
    if ($LASTEXITCODE -ne 0) { throw 'Backend preflight failed.' }
    $values = Assert-Cc4cRuntimeEnvironment (Read-Cc4cEnvironmentFile -Kind Runtime) -ManagementPort $ManagementPort
    if ((Get-Cc4cDatabaseName $values) -cne $ConfirmDatabase) { throw 'Database configuration changed after preflight.' }
    $jarPath = Assert-Cc4cOrdinaryPath (Join-Path $backendRoot 'target\cc4c-5.0.0-SNAPSHOT.jar')
    $javaPath = Assert-Cc4cOrdinaryPath (Get-Command java.exe -ErrorAction Stop).Source
    $javaVersion = @(& $javaPath -version 2>&1)
    if ($LASTEXITCODE -ne 0 -or ($javaVersion -join ' ') -notmatch 'version "21(?:\.|"|-)') { throw 'Java 21 is required.' }
    $logs = New-Cc4cRunLogs backend
    $launch = @{
        FilePath = $javaPath
        WorkingDirectory = $backendRoot
        ArgumentList = @('-jar', ('"' + $jarPath + '"'), "--server.port=$ApplicationPort", "--management.server.port=$ManagementPort")
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = $logs.Stdout
        RedirectStandardError = $logs.Stderr
    }
    try {
        $snapshot = Set-Cc4cProcessEnvironment $values -Backend
        $started = Start-Process @launch
    }
    finally {
        if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot; $snapshot = $null }
    }
    $identity = Get-Cc4cStartedIdentity $started $javaPath $jarPath backend
    if ($null -eq $identity) { throw 'The backend exited before its identity could be recorded; inspect its new log locally.' }
    $identity.workingDirectory = $backendRoot
    $identity.applicationPort = $ApplicationPort
    $identity.managementPort = $ManagementPort
    $identity.runId = $logs.RunId
    $identity.stdout = $logs.Stdout
    $identity.stderr = $logs.Stderr
    Write-Cc4cHostState backend $identity
    Write-Output "CC4C backend started with exact PID $($identity.pid)."
    exit 0
}
catch {
    $failure = $_.Exception.Message
    if ($null -ne $started) {
        try {
            if ($null -eq $identity) { $identity = Get-Cc4cStartedIdentity $started $javaPath $jarPath backend }
            if ($null -ne $identity) { Stop-Cc4cExactProcess $identity }
        }
        catch { Write-Warning 'The newly created backend could not be safely stopped; preserve its PID record and logs.' }
    }
    Write-Error $failure -ErrorAction Continue
    exit 1
}
finally {
    if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot }
    if ($null -ne $started) { $started.Dispose() }
}
