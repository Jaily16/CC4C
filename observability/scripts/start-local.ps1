[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$observabilityRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $observabilityRoot -Parent
$environmentFile = Join-Path $observabilityRoot '.env.observability.local'
& (Join-Path $PSScriptRoot 'preflight.ps1') -EnvironmentFile $environmentFile | Out-Host

$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentFile) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    $values[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1)
}

function ConvertTo-YamlSingleQuotedValue([string]$Value) {
    return $Value.Replace("'", "''")
}

$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-v3-aspect6-observability'
$prometheusData = Join-Path $runDirectory 'prometheus-data'
$grafanaData = Join-Path $runDirectory 'grafana-data'
$grafanaLogs = Join-Path $runDirectory 'grafana-logs'
New-Item -ItemType Directory -Force -Path $runDirectory, $prometheusData, $grafanaData, $grafanaLogs | Out-Null

$template = Get-Content -Raw -LiteralPath (Join-Path $observabilityRoot 'prometheus\prometheus.yml.template')
$rendered = $template.Replace('__CC4C_MANAGEMENT_USERNAME__',
    (ConvertTo-YamlSingleQuotedValue $values.CC4C_MANAGEMENT_USERNAME))
$rendered = $rendered.Replace('__CC4C_MANAGEMENT_PASSWORD__',
    (ConvertTo-YamlSingleQuotedValue $values.CC4C_MANAGEMENT_PASSWORD))
$rendered = $rendered.Replace('__CC4C_ENVIRONMENT__',
    (ConvertTo-YamlSingleQuotedValue $values.CC4C_OBSERVABILITY_ENVIRONMENT))
$rendered = $rendered.Replace('__CC4C_RABBITMQ_MONITOR_USERNAME__',
    (ConvertTo-YamlSingleQuotedValue $values.CC4C_RABBITMQ_MONITOR_USERNAME))
$rendered = $rendered.Replace('__CC4C_RABBITMQ_MONITOR_PASSWORD__',
    (ConvertTo-YamlSingleQuotedValue $values.CC4C_RABBITMQ_MONITOR_PASSWORD))
$rendered = $rendered.Replace('__CC4C_RABBITMQ_VHOST_REGEX__',
    (ConvertTo-YamlSingleQuotedValue ([regex]::Escape($values.CC4C_RABBITMQ_VHOST))))
$rendered = $rendered.Replace('__CC4C_RABBITMQ_NAMESPACE_REGEX__',
    (ConvertTo-YamlSingleQuotedValue ([regex]::Escape($values.CC4C_RABBITMQ_NAMESPACE))))
$prometheusConfig = Join-Path $runDirectory 'prometheus.yml'
Set-Content -LiteralPath $prometheusConfig -Value $rendered -Encoding utf8NoBOM
Copy-Item -LiteralPath (Join-Path $observabilityRoot 'prometheus\rules') -Destination $runDirectory -Recurse -Force

$promtool = Join-Path $values.PROMETHEUS_HOME 'promtool.exe'
& $promtool check config $prometheusConfig
if ($LASTEXITCODE -ne 0) {
    throw 'Rendered Prometheus configuration validation failed.'
}

$prometheus = Join-Path $values.PROMETHEUS_HOME 'prometheus.exe'
$prometheusProcess = Start-Process -FilePath $prometheus -WindowStyle Hidden -PassThru -ArgumentList @(
    "--config.file=$prometheusConfig",
    "--storage.tsdb.path=$prometheusData",
    '--storage.tsdb.retention.time=15d',
    '--web.listen-address=127.0.0.1:9090'
) -RedirectStandardOutput (Join-Path $runDirectory 'prometheus.stdout.log') `
  -RedirectStandardError (Join-Path $runDirectory 'prometheus.stderr.log')
Set-Content -LiteralPath (Join-Path $runDirectory 'prometheus.pid') -Value $prometheusProcess.Id

$previous = @{
    GF_SECURITY_ADMIN_USER = $env:GF_SECURITY_ADMIN_USER
    GF_SECURITY_ADMIN_PASSWORD = $env:GF_SECURITY_ADMIN_PASSWORD
    GF_USERS_ALLOW_SIGN_UP = $env:GF_USERS_ALLOW_SIGN_UP
    GF_PATHS_PROVISIONING = $env:GF_PATHS_PROVISIONING
    GF_PATHS_DATA = $env:GF_PATHS_DATA
    GF_PATHS_LOGS = $env:GF_PATHS_LOGS
    GF_SERVER_HTTP_ADDR = $env:GF_SERVER_HTTP_ADDR
    GF_SERVER_HTTP_PORT = $env:GF_SERVER_HTTP_PORT
    CC4C_GRAFANA_DASHBOARD_PATH = $env:CC4C_GRAFANA_DASHBOARD_PATH
}
try {
    $env:GF_SECURITY_ADMIN_USER = $values.GRAFANA_ADMIN_USER
    $env:GF_SECURITY_ADMIN_PASSWORD = $values.GRAFANA_ADMIN_PASSWORD
    $env:GF_USERS_ALLOW_SIGN_UP = 'false'
    $env:GF_PATHS_PROVISIONING = Join-Path $observabilityRoot 'grafana\provisioning'
    $env:GF_PATHS_DATA = $grafanaData
    $env:GF_PATHS_LOGS = $grafanaLogs
    $env:GF_SERVER_HTTP_ADDR = '127.0.0.1'
    $env:GF_SERVER_HTTP_PORT = '3000'
    $env:CC4C_GRAFANA_DASHBOARD_PATH = Join-Path $observabilityRoot 'grafana\dashboards'
    $grafana = Join-Path $values.GRAFANA_HOME 'bin\grafana.exe'
    $grafanaProcess = Start-Process -FilePath $grafana -WindowStyle Hidden -PassThru `
      -WorkingDirectory $values.GRAFANA_HOME -ArgumentList @(
        'server', '--homepath', $values.GRAFANA_HOME
      ) -RedirectStandardOutput (Join-Path $runDirectory 'grafana.stdout.log') `
        -RedirectStandardError (Join-Path $runDirectory 'grafana.stderr.log')
    Set-Content -LiteralPath (Join-Path $runDirectory 'grafana.pid') -Value $grafanaProcess.Id
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

Write-Host 'Prometheus and Grafana were started on 127.0.0.1 with exact PID records.'
