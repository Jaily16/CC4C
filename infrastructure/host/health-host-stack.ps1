#requires -Version 7.0
# 运行前提：本机栈已由新入口启动并记录；可选检查外部 Prometheus。
# 破坏性边界：只读精确 PID 身份、端口所有者及健康状态，不读取秘密，不修改服务。
# 失败恢复：缺失、身份不明或不健康时只报告失败，不自动停止或重启。
# 退出码：全部所选检查通过为 0，否则为 1。

[CmdletBinding()]
param(
    [switch] $IncludePrometheus,
    [string] $PrometheusUrl = 'http://127.0.0.1:9090'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

# 端口必须存在并全部归属于已验证的精确 PID；其他进程监听同号端口也不能误判成功。
function Assert-Cc4cOwnedPort {
    param([int] $Port, [int] $ProcessId)
    if ($Port -lt 1 -or $Port -gt 65535) { throw 'A required port is missing from the process record.' }
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop)
    if ($listeners.Count -eq 0 -or @($listeners | Where-Object OwningProcess -ne $ProcessId).Count -gt 0) {
        throw "Port $Port is not owned exclusively by the recorded application."
    }
}

try {
    $stack = Read-Cc4cHostState stack
    if ($null -eq $stack) { throw 'No running stack is recorded.' }
    Assert-Cc4cRunningStack $stack
    foreach ($state in @($stack.backend, $stack.frontend, $stack.observability)) {
        if ($null -eq (Get-Cc4cRecordedProcess $state)) { throw 'A recorded application has exited.' }
    }
    Assert-Cc4cOwnedPort $stack.backend.applicationPort $stack.backend.pid
    Assert-Cc4cOwnedPort $stack.backend.managementPort $stack.backend.pid
    Assert-Cc4cOwnedPort $stack.frontend.port $stack.frontend.pid
    Assert-Cc4cOwnedPort $stack.observability.port $stack.observability.pid
    Assert-Cc4cBackendHealth $stack.backend.managementPort
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$($stack.frontend.port)/" -TimeoutSec 5 -MaximumRedirection 0
        if ([int] $response.StatusCode -ne 200) { throw 'not OK' }
    }
    catch { throw 'The frontend did not return HTTP 200.' }
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$($stack.observability.port)/" -TimeoutSec 5 -MaximumRedirection 0
        if ([int] $response.StatusCode -ne 200) { throw 'not OK' }
    }
    catch { throw 'The observability frontend did not return HTTP 200.' }
    if ($IncludePrometheus) { Assert-Cc4cPrometheusEndpoint -BaseUrl $PrometheusUrl -RequireBackendScrape }
    Write-Output 'Both frontends return HTTP 200; backend health, liveness and readiness are UP; all four ports belong to the exact recorded PIDs.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
