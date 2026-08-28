[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env.observability.local')
)

$ErrorActionPreference = 'Stop'
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
        throw 'Missing observability/.env.observability.local. Copy the example and fill it first.'
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

foreach ($port in @(4081, 9090, 3000, 15692)) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($listeners -and $port -in @(9090, 3000)) {
        throw "Port $port is already occupied."
    }
}
foreach ($requiredPort in @(4081, 15692)) {
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
    'http://127.0.0.1:4081/actuator/prometheus' `
    $values.CC4C_MANAGEMENT_USERNAME `
    $values.CC4C_MANAGEMENT_PASSWORD `
    'CC4C management metrics endpoint'
Test-BasicEndpoint `
    'http://127.0.0.1:15692/metrics' `
    $values.CC4C_RABBITMQ_MONITOR_USERNAME `
    $values.CC4C_RABBITMQ_MONITOR_PASSWORD `
    'RabbitMQ Prometheus endpoint'

$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$rules = Join-Path $root 'observability\prometheus\rules\cc4c-alerts.yml'
$tests = Join-Path $root 'observability\prometheus\tests\cc4c-alerts.test.yml'
& $promtool check rules $rules
if ($LASTEXITCODE -ne 0) { throw 'Prometheus rule validation failed.' }
$config = Join-Path $root 'observability\prometheus\prometheus.yml.template'
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
    ManagementPortListening = [bool](Get-NetTCPConnection -State Listen -LocalPort 4081 -ErrorAction SilentlyContinue)
    RabbitExporterListening = [bool](Get-NetTCPConnection -State Listen -LocalPort 15692 -ErrorAction SilentlyContinue)
}
