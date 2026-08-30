# 运行前提：仅检查用户指定的非敏感观测配置路径；本脚本不输出配置值。
# 破坏性边界：只读检查，不启动服务，不修改秘密、卷、数据库、Redis 或 RabbitMQ 数据。
# 失败恢复：检查失败时保留原文件并停止；修正非敏感配置后可重复执行。
# 退出码：检查通过返回 0，发现配置或路径问题返回非零码。

[CmdletBinding()]
param(
    [string]$EnvironmentFile,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081,
    [ValidateRange(1, 65535)][int] $PrometheusPort = 9090,
    [ValidateRange(1, 65535)][int] $GrafanaPort = 3000,
    [ValidateRange(1, 65535)][int] $RabbitExporterPort = 15692
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$canonicalEnvironmentFile = Join-Path $workspaceRoot 'infrastructure\observability\.env.observability.local'
$legacyEnvironmentFile = Join-Path $workspaceRoot 'observability\.env.observability.local'
$explicitEnvironmentFile = -not [string]::IsNullOrWhiteSpace($EnvironmentFile)
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    $EnvironmentFile = if (Test-Path -LiteralPath $canonicalEnvironmentFile -PathType Leaf) {
        $canonicalEnvironmentFile
    } else {
        $legacyEnvironmentFile
    }
}
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath($EnvironmentFile)
$allowedEnvironmentFiles = @(
    [System.IO.Path]::GetFullPath($canonicalEnvironmentFile),
    [System.IO.Path]::GetFullPath($legacyEnvironmentFile)
)
if ($explicitEnvironmentFile) {
    $workspacePrefix = [System.IO.Path]::GetFullPath($workspaceRoot).TrimEnd('\') + '\'
    if ($resolvedEnvironmentFile.StartsWith($workspacePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'An explicit observability environment file must be outside the workspace.'
    }
} elseif ($allowedEnvironmentFiles -notcontains $resolvedEnvironmentFile) {
    throw 'Only the canonical or legacy observability environment file is allowed.'
}
$EnvironmentFile = $resolvedEnvironmentFile
$required = @(
    'PROMETHEUS_HOME', 'GRAFANA_HOME',
    'CC4C_MANAGEMENT_USERNAME', 'CC4C_MANAGEMENT_PASSWORD',
    'CC4C_OBSERVABILITY_ENVIRONMENT',
    'CC4C_RABBITMQ_MONITOR_USERNAME', 'CC4C_RABBITMQ_MONITOR_PASSWORD',
    'CC4C_RABBITMQ_VHOST', 'CC4C_RABBITMQ_NAMESPACE',
    'GRAFANA_ADMIN_USER', 'GRAFANA_ADMIN_PASSWORD'
)

function Read-EnvironmentFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'Missing the local observability environment file. Copy the example and fill it first.'
    }
    $values = @{}
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) { throw "Invalid entry on line $lineNumber." }
        $name = $line.Substring(0, $separator).Trim()
        if ($required -notcontains $name) { throw "Unsupported variable '$name'." }
        $values[$name] = $line.Substring($separator + 1)
    }
    foreach ($name in $required) {
        if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
            throw "Required variable '$name' is missing or empty."
        }
    }
    return $values
}

$values = Read-EnvironmentFile $EnvironmentFile
if ($values.CC4C_MANAGEMENT_PASSWORD.Length -lt 24) {
    throw 'CC4C_MANAGEMENT_PASSWORD must contain at least 24 characters.'
}
if ($values.GRAFANA_ADMIN_PASSWORD.Length -lt 24) {
    throw 'GRAFANA_ADMIN_PASSWORD must contain at least 24 characters.'
}
if ($values.CC4C_OBSERVABILITY_ENVIRONMENT -notmatch '^[a-z0-9-]{2,32}$') {
    throw 'CC4C_OBSERVABILITY_ENVIRONMENT is invalid.'
}
if ($values.CC4C_RABBITMQ_NAMESPACE -notmatch '^[A-Za-z0-9._:-]{3,120}$') {
    throw 'CC4C_RABBITMQ_NAMESPACE is invalid.'
}

$prometheus = Join-Path $values.PROMETHEUS_HOME 'prometheus.exe'
$promtool = Join-Path $values.PROMETHEUS_HOME 'promtool.exe'
$grafana = Join-Path $values.GRAFANA_HOME 'bin\grafana.exe'
foreach ($file in @($prometheus, $promtool, $grafana)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Required executable was not found: $file"
    }
}

$prometheusVersionOutput = @(& $prometheus --version 2>&1)
$prometheusExitCode = $LASTEXITCODE
$prometheusVersion = [string]($prometheusVersionOutput | Select-Object -First 1)
if ($prometheusExitCode -ne 0 -or $prometheusVersion -notmatch '3\.13\.2') {
    throw 'Prometheus 3.13.2 is required.'
}
$grafanaVersionOutput = @(& $grafana -v 2>&1)
$grafanaExitCode = $LASTEXITCODE
$grafanaVersion = [string]($grafanaVersionOutput | Select-Object -First 1)
if ($grafanaExitCode -ne 0 -or $grafanaVersion -notmatch '13\.1\.0') {
    throw 'Grafana 13.1.0 is required.'
}

foreach ($port in @($ManagementPort, $PrometheusPort, $GrafanaPort, $RabbitExporterPort)) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listeners -and $port -in @($PrometheusPort, $GrafanaPort)) {
        throw "Port $port is already occupied."
    }
}
foreach ($requiredPort in @($ManagementPort, $RabbitExporterPort)) {
    if (-not (Get-NetTCPConnection -State Listen -LocalPort $requiredPort -ErrorAction SilentlyContinue)) {
        throw "Required observability source port $requiredPort is not listening."
    }
}

function Test-BasicEndpoint(
        [string]$Uri,
        [string]$Username,
        [string]$Password,
        [string]$Description) {
    $credentialText = '{0}:{1}' -f $Username, $Password
    $credentialBytes = [System.Text.Encoding]::UTF8.GetBytes($credentialText)
    $authorization = [Convert]::ToBase64String($credentialBytes)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Headers @{
            Authorization = "Basic $authorization"
        } -TimeoutSec 5
        if ($response.StatusCode -ne 200) {
            throw "$Description returned HTTP $($response.StatusCode)."
        }
    } catch {
        throw "$Description authentication or connectivity check failed."
    }
}

Test-BasicEndpoint `
    ("http://127.0.0.1:{0}/actuator/prometheus" -f $ManagementPort) `
    $values.CC4C_MANAGEMENT_USERNAME `
    $values.CC4C_MANAGEMENT_PASSWORD `
    'CC4C management metrics endpoint'
Test-BasicEndpoint `
    ("http://127.0.0.1:{0}/metrics" -f $RabbitExporterPort) `
    $values.CC4C_RABBITMQ_MONITOR_USERNAME `
    $values.CC4C_RABBITMQ_MONITOR_PASSWORD `
    'RabbitMQ Prometheus endpoint'

$rules = Join-Path $workspaceRoot 'infrastructure\observability\prometheus\rules\cc4c-alerts.yml'
$tests = Join-Path $workspaceRoot 'infrastructure\observability\prometheus\tests\cc4c-alerts.test.yml'
& $promtool check rules $rules
if ($LASTEXITCODE -ne 0) { throw 'Prometheus rule validation failed.' }
$config = Join-Path $workspaceRoot 'infrastructure\observability\prometheus\prometheus.yml.template'
& $promtool check config $config
if ($LASTEXITCODE -ne 0) { throw 'Prometheus configuration validation failed.' }
Push-Location -LiteralPath (Split-Path $tests -Parent)
try {
    & $promtool test rules (Split-Path $tests -Leaf)
    if ($LASTEXITCODE -ne 0) { throw 'Prometheus rule unit tests failed.' }
} finally {
    Pop-Location
}

[pscustomobject]@{
    Prometheus = $prometheusVersion
    Grafana = $grafanaVersion
    ManagementPortListening = [bool](Get-NetTCPConnection -State Listen -LocalPort $ManagementPort -ErrorAction SilentlyContinue)
    RabbitExporterListening = [bool](Get-NetTCPConnection -State Listen -LocalPort $RabbitExporterPort -ErrorAction SilentlyContinue)
}
