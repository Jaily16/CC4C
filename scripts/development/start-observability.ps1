# 运行前提：仅在用户明确选择本地观测服务且非敏感环境文件已准备时运行。
# 破坏性边界：不得操作旧 Compose 业务栈、卷、数据库、Redis、RabbitMQ 或上传数据。
# 失败恢复：启动失败时按已记录的进程信息停止本次启动项，不触碰既有服务。
# 退出码：所有观测服务启动成功返回 0，任一前置检查或启动失败返回非零码。

[CmdletBinding()]
param(
    [ValidateRange(1, 65535)][int] $PrometheusPort = 9090,
    [ValidateRange(1, 65535)][int] $GrafanaPort = 3000,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081,
    [ValidateRange(1, 65535)][int] $RabbitExporterPort = 15692,
    [string] $ObservabilityEnvironmentPath
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'host-environment.ps1')
$environmentPathSnapshot = Set-Cc4cHostEnvironmentPathOverrides -ObservabilityPath $ObservabilityEnvironmentPath

try {
    $observabilityRoot = Join-Path $workspaceRoot 'infrastructure\observability'
    $environment = Read-Cc4cEnvironmentFile -Kind Observability
    $environmentFile = $environment.Path
    $values = $environment.Values
    & (Join-Path $PSScriptRoot 'observability-preflight.ps1') `
        -EnvironmentFile $environmentFile `
        -ManagementPort $ManagementPort `
        -PrometheusPort $PrometheusPort `
        -GrafanaPort $GrafanaPort `
        -RabbitExporterPort $RabbitExporterPort | Out-Host

function ConvertTo-YamlSingleQuotedValue([string]$Value) {
    return $Value.Replace("'", "''")
}

$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-observability'
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
$rendered = $rendered.Replace('127.0.0.1:4081', "127.0.0.1:$ManagementPort")
$rendered = $rendered.Replace('127.0.0.1:15692', "127.0.0.1:$RabbitExporterPort")
$prometheusConfig = Join-Path $runDirectory 'prometheus.yml'
[System.IO.File]::WriteAllText($prometheusConfig, $rendered, [System.Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $observabilityRoot 'prometheus\rules') -Destination $runDirectory -Recurse -Force

$promtool = Join-Path $values.PROMETHEUS_HOME 'promtool.exe'
& $promtool check config $prometheusConfig
if ($LASTEXITCODE -ne 0) {
    throw 'Rendered Prometheus configuration validation failed.'
}

$prometheus = Join-Path $values.PROMETHEUS_HOME 'prometheus.exe'

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
    CC4C_PROMETHEUS_URL = $env:CC4C_PROMETHEUS_URL
}
$prometheusProcess = $null
$grafanaProcess = $null
try {
    $prometheusProcess = Start-Process -FilePath $prometheus -WindowStyle Hidden -PassThru -ArgumentList @(
        "--config.file=$prometheusConfig",
        "--storage.tsdb.path=$prometheusData",
        '--storage.tsdb.retention.time=15d',
        "--web.listen-address=127.0.0.1:$PrometheusPort"
    ) -RedirectStandardOutput (Join-Path $runDirectory 'prometheus.stdout.log') `
      -RedirectStandardError (Join-Path $runDirectory 'prometheus.stderr.log')
    Set-Content -LiteralPath (Join-Path $runDirectory 'prometheus.pid') -Value $prometheusProcess.Id

    $env:GF_SECURITY_ADMIN_USER = $values.GRAFANA_ADMIN_USER
    $env:GF_SECURITY_ADMIN_PASSWORD = $values.GRAFANA_ADMIN_PASSWORD
    $env:GF_USERS_ALLOW_SIGN_UP = 'false'
    $env:GF_PATHS_PROVISIONING = Join-Path $observabilityRoot 'grafana\provisioning'
    $env:GF_PATHS_DATA = $grafanaData
    $env:GF_PATHS_LOGS = $grafanaLogs
    $env:GF_SERVER_HTTP_ADDR = '127.0.0.1'
    $env:GF_SERVER_HTTP_PORT = [string] $GrafanaPort
    $env:CC4C_GRAFANA_DASHBOARD_PATH = Join-Path $observabilityRoot 'grafana\dashboards'
    $env:CC4C_PROMETHEUS_URL = "http://127.0.0.1:$PrometheusPort"
    $grafana = Join-Path $values.GRAFANA_HOME 'bin\grafana.exe'
    $grafanaProcess = Start-Process -FilePath $grafana -WindowStyle Hidden -PassThru `
      -WorkingDirectory $values.GRAFANA_HOME -ArgumentList @(
        'server', '--homepath', $values.GRAFANA_HOME
      ) -RedirectStandardOutput (Join-Path $runDirectory 'grafana.stdout.log') `
        -RedirectStandardError (Join-Path $runDirectory 'grafana.stderr.log')
    Set-Content -LiteralPath (Join-Path $runDirectory 'grafana.pid') -Value $grafanaProcess.Id
    $observationState = [ordered]@{
        component = 'observability'
        prometheusPid = $prometheusProcess.Id
        prometheusExecutablePath = [System.IO.Path]::GetFullPath($prometheus)
        grafanaPid = $grafanaProcess.Id
        grafanaExecutablePath = [System.IO.Path]::GetFullPath($grafana)
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        commandLineSummary = [ordered]@{
            prometheus = "prometheus --config.file=<runtime> --web.listen-address=127.0.0.1:$PrometheusPort"
            grafana = 'grafana server --homepath=<runtime>'
        }
        prometheusPort = $PrometheusPort
        grafanaPort = $GrafanaPort
        managementPort = $ManagementPort
        rabbitExporterPort = $RabbitExporterPort
        status = 'running'
    }
    Write-Cc4cHostState 'observability' $observationState | Out-Null
} catch {
    foreach ($startedEntry in @(
            [pscustomobject]@{ Process = $grafanaProcess; Path = $grafana }
            [pscustomobject]@{ Process = $prometheusProcess; Path = $prometheus }
        )) {
        if ($null -eq $startedEntry.Process) { continue }
        $current = Get-CimInstance Win32_Process -Filter "ProcessId = $($startedEntry.Process.Id)" -ErrorAction SilentlyContinue
        if ($null -ne $current) {
            $actualPath = if ([string]::IsNullOrWhiteSpace($current.ExecutablePath)) {
                ''
            } else {
                [System.IO.Path]::GetFullPath($current.ExecutablePath)
            }
            if ($actualPath -cne [System.IO.Path]::GetFullPath($startedEntry.Path)) {
                throw 'Observability startup rollback encountered a PID identity mismatch.'
            }
            Stop-Process -Id $startedEntry.Process.Id -ErrorAction SilentlyContinue
        }
    }
    throw
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

Write-Host 'Prometheus and Grafana were started on 127.0.0.1 with exact PID records.'
}
finally {
    if ($null -ne $environmentPathSnapshot) {
        Restore-Cc4cHostEnvironmentPathOverrides $environmentPathSnapshot
    }
}
