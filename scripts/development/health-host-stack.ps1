# 运行前提：宿主机栈已由 start-host-stack.ps1 启动，或提供了对应的精确状态记录。
# 破坏性边界：只读检查记录的 PID、可执行文件、监听端口和健康 HTTP 状态，不读取环境秘密、不修改服务。
# 失败恢复：检查失败仅返回诊断规则，不停止任何进程；由调用方决定是否使用精确停止脚本。
# 退出码：所有请求组件健康返回 0，记录缺失、身份不符、端口或健康检查失败返回非零码。

[CmdletBinding()]
param(
    [switch] $IncludeObservability
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

function Assert-Cc4cListening {
    param([Parameter(Mandatory = $true)][int] $Port)
    if (-not (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)) {
        throw "Expected host port $Port is not listening."
    }
}

function Assert-Cc4cRecordedProcess {
    param(
        [Parameter(Mandatory = $true)] $State,
        [Parameter(Mandatory = $true)][string] $Name
    )
    if ($null -eq $State) {
        throw "No state was recorded for host component '$Name'."
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int] $State.pid)" -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        throw "Recorded host component '$Name' is not running."
    }
    $actual = if ([string]::IsNullOrWhiteSpace($process.ExecutablePath)) {
        ''
    } else {
        [System.IO.Path]::GetFullPath($process.ExecutablePath)
    }
    $marker = if ($null -ne $State.marker) { [string] $State.marker } else { [string] $State.jarFileName }
    if ($actual -cne [System.IO.Path]::GetFullPath([string] $State.executablePath) -or
        ([string] $process.CommandLine) -notlike "*$marker*") {
        throw "Recorded host component '$Name' failed its exact PID identity check."
    }
}

function Assert-Cc4cHttp {
    param([Parameter(Mandatory = $true)][string] $Uri, [Parameter(Mandatory = $true)][string] $Name)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Head -Uri $Uri -TimeoutSec 5
        if ([int] $response.StatusCode -ge 500) {
            throw 'server error'
        }
    }
    catch {
        throw "Host health endpoint '$Name' did not respond successfully."
    }
}

try {
    $stack = Read-Cc4cHostState 'stack'
    if ($null -eq $stack -or [string] $stack.status -ne 'running') {
        throw 'No running CC4C host stack is recorded.'
    }
    $backend = Read-Cc4cHostState 'backend'
    $frontend = Read-Cc4cHostState 'frontend'
    Assert-Cc4cRecordedProcess $backend 'backend'
    Assert-Cc4cRecordedProcess $frontend 'frontend'
    $applicationPort = if ($null -ne $backend.applicationPort) { [int] $backend.applicationPort } else { 4080 }
    $managementPort = if ($null -ne $backend.managementPort) { [int] $backend.managementPort } else { 4081 }
    $frontendPort = if ($null -ne $frontend.port) { [int] $frontend.port } else { 5173 }
    Assert-Cc4cListening $applicationPort
    Assert-Cc4cListening $managementPort
    Assert-Cc4cListening $frontendPort
    Assert-Cc4cHttp ("http://127.0.0.1:{0}/" -f $frontendPort) 'frontend'
    Assert-Cc4cHttp ("http://127.0.0.1:{0}/actuator/health" -f $managementPort) 'backend management'
    if ($IncludeObservability) {
        $observability = Read-Cc4cHostState 'observability'
        if ($null -eq $observability) {
            throw 'Observability was requested but no state was recorded.'
        }
        foreach ($entry in @(
                [pscustomobject]@{
                    pid = $observability.prometheusPid
                    executablePath = $observability.prometheusExecutablePath
                    marker = 'prometheus.exe'
                }
                [pscustomobject]@{
                    pid = $observability.grafanaPid
                    executablePath = $observability.grafanaExecutablePath
                    marker = 'grafana.exe'
                }
            )) {
            Assert-Cc4cRecordedProcess $entry 'observability'
        }
        $prometheusPort = if ($null -ne $observability.prometheusPort) { [int] $observability.prometheusPort } else { 9090 }
        $grafanaPort = if ($null -ne $observability.grafanaPort) { [int] $observability.grafanaPort } else { 3000 }
        Assert-Cc4cListening $prometheusPort
        Assert-Cc4cListening $grafanaPort
        Assert-Cc4cHttp ("http://127.0.0.1:{0}/-/ready" -f $prometheusPort) 'Prometheus'
        Assert-Cc4cHttp ("http://127.0.0.1:{0}/api/health" -f $grafanaPort) 'Grafana'
    }
    Write-Output 'CC4C host stack health checks passed.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
