# 运行前提：只停止 temp/cc4c-host-stack/stack.json 记录的宿主机组件。
# 破坏性边界：按前端、后端、观测的精确记录停止本次启动的 PID；绝不停止外部依赖或 Docker Compose 服务。
# 失败恢复：每个组件独立执行并保留失败状态；不按进程名、端口或服务标签批量终止。
# 退出码：记录的组件均停止或不存在返回 0，任一精确停止失败返回非零码。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

try {
    $state = Read-Cc4cHostState 'stack'
    if ($null -eq $state -or [string] $state.status -eq 'stopped') {
        Write-Output 'No running recorded CC4C host stack exists.'
        exit 0
    }
    $errors = @()
    if ([bool] $state.withObservability) {
        & (Join-Path $PSScriptRoot 'stop-observability.ps1') 2>$null
        if ($LASTEXITCODE -ne 0) { $errors += 'observability' }
    }
    & (Join-Path $PSScriptRoot 'stop-frontend.ps1') 2>$null
    if ($LASTEXITCODE -ne 0) { $errors += 'frontend' }
    & (Join-Path $PSScriptRoot 'stop-backend.ps1') 2>$null
    if ($LASTEXITCODE -ne 0) { $errors += 'backend' }
    if ($errors.Count -gt 0) {
        throw "Exact host component stop failed for: $($errors -join ', ')"
    }
    $updatedState = [ordered]@{}
    foreach ($property in $state.PSObject.Properties) {
        $updatedState[$property.Name] = $property.Value
    }
    $updatedState.status = 'stopped'
    $updatedState.stoppedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-Cc4cHostState 'stack' $updatedState | Out-Null
    Write-Output 'Recorded CC4C host stack processes were stopped.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
