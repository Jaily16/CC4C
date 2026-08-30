# 运行前提：已完成宿主机依赖预检，并提供精确数据库名；外部 MySQL、Redis、RabbitMQ 和邮件服务由管理员管理。
# 破坏性边界：只按固定顺序启动后端、前端和可选观测进程；不启动/停止外部依赖，不触碰 Docker 卷或旧 Compose 身份。
# 失败恢复：按本次已成功启动的逆序调用精确 PID 停止脚本；恢复失败时保留状态和日志，不扩大清理范围。
# 退出码：全部请求组件启动并记录成功返回 0，任何前置、启动或回滚失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $ConfirmDatabase,
    [ValidateSet('Dev', 'Static')][string] $FrontendMode = 'Dev',
    [string] $NginxPath,
    [switch] $WithObservability,
    [ValidateRange(1, 65535)][int] $ApplicationPort = 4080,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081,
    [ValidateRange(1, 65535)][int] $FrontendPort = 5173,
    [ValidateRange(1, 65535)][int] $PrometheusPort = 9090,
    [ValidateRange(1, 65535)][int] $GrafanaPort = 3000,
    [string] $RuntimeEnvironmentPath,
    [string] $FrontendEnvironmentPath,
    [string] $ObservabilityEnvironmentPath
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

$backendStarted = $false
$frontendStarted = $false
$observabilityStarted = $false
$environmentPathSnapshot = $null

function Test-Cc4cRecordedProcess {
    param([Parameter(Mandatory = $true)] $State)
    if ($null -eq $State) { return $false }
    return $null -ne (Get-CimInstance Win32_Process -Filter "ProcessId = $([int] $State.pid)" -ErrorAction SilentlyContinue)
}

try {
    $environmentPathSnapshot = Set-Cc4cHostEnvironmentPathOverrides `
        -RuntimePath $RuntimeEnvironmentPath `
        -FrontendPath $FrontendEnvironmentPath `
        -ObservabilityPath $ObservabilityEnvironmentPath
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $oldStack = Read-Cc4cHostState 'stack'
    if ($null -ne $oldStack -and [string] $oldStack.status -eq 'running') {
        throw 'A recorded host stack is already marked as running.'
    }
    & (Join-Path $PSScriptRoot 'host-preflight.ps1') `
        -Component All `
        -ConfirmDatabase $ConfirmDatabase `
        -ApplicationPort $ApplicationPort `
        -ManagementPort $ManagementPort `
        -FrontendPort $FrontendPort `
        -PrometheusPort $PrometheusPort `
        -GrafanaPort $GrafanaPort `
        -RuntimeEnvironmentPath $RuntimeEnvironmentPath `
        -FrontendEnvironmentPath $FrontendEnvironmentPath `
        -ObservabilityEnvironmentPath $ObservabilityEnvironmentPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Host stack dependency preflight failed.'
    }
    & (Join-Path $PSScriptRoot 'start-backend.ps1') `
        -ConfirmDatabase $ConfirmDatabase `
        -ApplicationPort $ApplicationPort `
        -ManagementPort $ManagementPort `
        -RuntimeEnvironmentPath $RuntimeEnvironmentPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Host backend startup failed.'
    }
    $backendStarted = $true
    if ($FrontendMode -eq 'Static') {
        & (Join-Path $PSScriptRoot 'start-frontend.ps1') `
            -Mode Static `
            -NginxPath $NginxPath `
            -FrontendPort $FrontendPort `
            -FrontendEnvironmentPath $FrontendEnvironmentPath
    } else {
        & (Join-Path $PSScriptRoot 'start-frontend.ps1') `
            -Mode Dev `
            -FrontendPort $FrontendPort `
            -FrontendEnvironmentPath $FrontendEnvironmentPath
    }
    if ($LASTEXITCODE -ne 0) {
        throw 'Host frontend startup failed.'
    }
    $frontendStarted = $true
    if ($WithObservability) {
        & (Join-Path $PSScriptRoot 'start-observability.ps1') `
            -PrometheusPort $PrometheusPort `
            -GrafanaPort $GrafanaPort `
            -ManagementPort $ManagementPort `
            -ObservabilityEnvironmentPath $ObservabilityEnvironmentPath
        if ($LASTEXITCODE -ne 0) {
            throw 'Host observability startup failed.'
        }
        $observabilityStarted = $true
    }
    $backendState = Read-Cc4cHostState 'backend'
    $frontendState = Read-Cc4cHostState 'frontend'
    $observabilityState = if ($WithObservability) { Read-Cc4cHostState 'observability' } else { $null }
    $stackState = [ordered]@{
        component = 'stack'
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        frontendMode = $FrontendMode
        withObservability = [bool] $WithObservability
        backendPid = [int] $backendState.pid
        frontendPid = [int] $frontendState.pid
        observabilityPids = if ($null -eq $observabilityState) {
            @()
        } else {
            @([int] $observabilityState.prometheusPid, [int] $observabilityState.grafanaPid)
        }
        status = 'running'
    }
    Write-Cc4cHostState 'stack' $stackState | Out-Null
    Write-Output 'CC4C host stack started in backend, frontend, and optional observability order.'
    exit 0
}
catch {
    $rollbackErrors = @()
    if ($observabilityStarted) {
        & (Join-Path $PSScriptRoot 'stop-observability.ps1') 2>$null
        if ($LASTEXITCODE -ne 0) { $rollbackErrors += 'observability' }
    }
    if ($frontendStarted) {
        & (Join-Path $PSScriptRoot 'stop-frontend.ps1') 2>$null
        if ($LASTEXITCODE -ne 0) { $rollbackErrors += 'frontend' }
    }
    if ($backendStarted) {
        & (Join-Path $PSScriptRoot 'stop-backend.ps1') 2>$null
        if ($LASTEXITCODE -ne 0) { $rollbackErrors += 'backend' }
    }
    if ($rollbackErrors.Count -gt 0) {
        Write-Error ("Host startup failed and exact rollback failed for: " + ($rollbackErrors -join ', '))
    }
    Write-Error $_.Exception.Message
    exit 1
}
finally {
    if ($null -ne $environmentPathSnapshot) {
        Restore-Cc4cHostEnvironmentPathOverrides $environmentPathSnapshot
    }
}
