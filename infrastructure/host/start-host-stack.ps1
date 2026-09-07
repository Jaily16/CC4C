#requires -Version 7.0
# 运行前提：固定配置已由用户整理，生产 JAR 与前端依赖存在，外部中间件及 Prometheus 已就绪。
# 破坏性边界：只按后端、前端顺序启动当前工作区应用，不管理观测服务或其他中间件。
# 失败恢复：只按本次保存的完整身份逆序停止；不读取其他运行日志，不清理数据。
# 退出码：两端启动、健康和栈记录完成为 0，任何失败为 1。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ConfirmDatabase,
    [ValidateRange(1, 65535)][int] $ApplicationPort = 4080,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081,
    [ValidateRange(1, 65535)][int] $FrontendPort = 5173,
    [string] $PrometheusUrl = 'http://127.0.0.1:9090'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')
$backendState = $null
$frontendState = $null

# 仅在本次精确进程仍存活时等待后端就绪，超时即交由启动失败分支停止该进程。
function Wait-Cc4cBackendReady {
    param($State, [int] $Port)
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        if ($null -eq (Get-Cc4cRecordedProcess $State)) { throw 'The newly started backend exited.' }
        try { Assert-Cc4cBackendHealth $Port; return } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'The newly started backend did not become healthy within sixty seconds.'
}

# 前端使用 strictPort；只有本次 PID 已监听且页面返回 200 才认为启动成功。
function Wait-Cc4cFrontendReady {
    param($State, [int] $Port)
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        if ($null -eq (Get-Cc4cRecordedProcess $State)) { throw 'The newly started frontend exited.' }
        try {
            $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop)
            if ($listeners.Count -eq 0 -or @($listeners | Where-Object OwningProcess -ne $State.pid).Count -gt 0) {
                throw 'requested frontend port is not owned'
            }
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -TimeoutSec 3 -MaximumRedirection 0
            if ([int] $response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'The newly started frontend did not become healthy within thirty seconds.'
}

try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $oldStack = Read-Cc4cHostState stack
    if ($null -ne $oldStack -and $oldStack.status -ne 'stopped') {
        throw 'An existing stack is not marked stopped; inspect its identity before starting.'
    }
    Assert-Cc4cCanStart backend
    Assert-Cc4cCanStart frontend
    $preflight = @{
        Component = 'All'; ConfirmDatabase = $ConfirmDatabase
        ApplicationPort = $ApplicationPort; ManagementPort = $ManagementPort
        FrontendPort = $FrontendPort; PrometheusUrl = $PrometheusUrl
    }
    & (Join-Path $PSScriptRoot 'host-preflight.ps1') @preflight
    if ($LASTEXITCODE -ne 0) { throw 'Host stack preflight failed.' }
    $backendArguments = @{ ConfirmDatabase = $ConfirmDatabase; ApplicationPort = $ApplicationPort; ManagementPort = $ManagementPort }
    & (Join-Path $workspaceRoot 'backend\scripts\start-backend.ps1') @backendArguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
    $backendState = Read-Cc4cHostState backend
    if ($null -eq $backendState) { throw 'The new backend record is missing; do not guess its PID.' }
    Wait-Cc4cBackendReady $backendState $ManagementPort
    & (Join-Path $workspaceRoot 'frontend\scripts\start-frontend.ps1') -FrontendPort $FrontendPort
    if ($LASTEXITCODE -ne 0) { throw 'Frontend startup failed.' }
    $frontendState = Read-Cc4cHostState frontend
    if ($null -eq $frontendState) { throw 'The new frontend record is missing; do not guess its PID.' }
    Wait-Cc4cFrontendReady $frontendState $FrontendPort
    $stack = [ordered]@{
        schemaVersion = 2
        component = 'stack'
        runId = [Guid]::NewGuid().ToString('N')
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        backend = $backendState
        frontend = $frontendState
        status = 'running'
    }
    Write-Cc4cHostState stack $stack
    Write-Output 'CC4C host stack is healthy; startup order was backend then frontend. External services were unchanged.'
    exit 0
}
catch {
    $failure = $_.Exception.Message
    foreach ($entry in @($frontendState, $backendState)) {
        if ($null -ne $entry) {
            try { Stop-Cc4cOwnedComponent $entry }
            catch { Write-Warning "Exact rollback failed for $($entry.component) PID $($entry.pid); preserve its record and logs." }
        }
    }
    Write-Error $failure -ErrorAction Continue
    exit 1
}
