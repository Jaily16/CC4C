#requires -Version 7.0
# 运行前提：observability/.env.local、Node 与锁定依赖已准备；只支持本机 Node/Vite。
# 外部依赖：依赖共享环境加载器、本机 Node/npm、已安装的 Vite 依赖和运行中的后端观测 API。
# 破坏性边界：只启动独立观测前端，不安装依赖，也不向浏览器传入后端或 Prometheus 凭据。
# 失败恢复：恢复进程变量，只按本次完整 PID 身份停止；唯一日志保留。
# 退出码：启动并记录成功为 0；配置、依赖、端口或身份验证失败为 1。

[CmdletBinding()]
param([ValidateRange(1, 65535)][int] $ObservabilityPort = 5174)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\infrastructure\host\host-environment.ps1')
$started = $null
$identity = $null
$snapshot = $null

try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $observabilityRoot = Join-Path $workspaceRoot 'observability'
    Assert-Cc4cCanStart observability
    & (Join-Path $workspaceRoot 'infrastructure\host\host-preflight.ps1') -Component Observability -ObservabilityPort $ObservabilityPort
    if ($LASTEXITCODE -ne 0) { throw 'Observability preflight failed.' }
    $public = Read-Cc4cEnvironmentFile -Kind Observability
    $values = [ordered]@{ VITE_API_BASE_URL = [string] $public.Values.VITE_API_BASE_URL }
    $nodePath = Assert-Cc4cOrdinaryPath (Get-Command node.exe -ErrorAction Stop).Source
    $vitePath = Assert-Cc4cOrdinaryPath (Join-Path $observabilityRoot 'node_modules\vite\bin\vite.js')
    $logs = New-Cc4cRunLogs observability
    $launch = @{
        FilePath = $nodePath
        WorkingDirectory = $observabilityRoot
        ArgumentList = @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', [string] $ObservabilityPort, '--strictPort')
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = $logs.Stdout
        RedirectStandardError = $logs.Stderr
    }
    try {
        $snapshot = Set-Cc4cProcessEnvironment $values -Observability
        $started = Start-Process @launch
    } finally {
        if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot; $snapshot = $null }
    }
    $identity = Get-Cc4cStartedIdentity $started $nodePath $vitePath observability
    if ($null -eq $identity) { throw 'The observability frontend exited before its identity could be recorded.' }
    $identity.workingDirectory = $observabilityRoot
    $identity.port = $ObservabilityPort
    $identity.runId = $logs.RunId
    $identity.stdout = $logs.Stdout
    $identity.stderr = $logs.Stderr
    Write-Cc4cHostState observability $identity
    Write-Output "CC4C observability frontend started with exact PID $($identity.pid)."
    exit 0
} catch {
    $failure = $_.Exception.Message
    if ($null -ne $started) {
        try {
            if ($null -eq $identity) { $identity = Get-Cc4cStartedIdentity $started $nodePath $vitePath observability }
            if ($null -ne $identity) { Stop-Cc4cExactProcess $identity }
        } catch { Write-Warning 'The newly created observability frontend could not be safely stopped; preserve its record and logs.' }
    }
    Write-Error $failure -ErrorAction Continue
    exit 1
} finally {
    if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot }
    if ($null -ne $started) { $started.Dispose() }
}
