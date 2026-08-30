# 运行前提：宿主机依赖由管理员或用户预先提供；本脚本只做精确连通性、版本/路径和端口检查。
# 破坏性边界：不启动或停止 MySQL、Redis、RabbitMQ、Mailpit、Docker、后端、前端或观测服务，
# 不执行 Redis flush、Rabbit purge、Flyway 操作、数据库写入或任何本机配置修改。
# 失败恢复：检查失败即退出，保留外部服务原状；修正非敏感前置条件后可重复执行。
# 退出码：所有指定检查通过返回 0，任何缺失、占用、协议或连通性问题返回非零码。

[CmdletBinding()]
param(
    [ValidateSet('All', 'MySQL', 'Redis', 'RabbitMQ', 'Mailpit', 'Backend', 'Frontend', 'Observability')]
    [string] $Component = 'All',

    [string] $ConfirmDatabase,

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
$environmentPathSnapshot = Set-Cc4cHostEnvironmentPathOverrides `
    -RuntimePath $RuntimeEnvironmentPath `
    -FrontendPath $FrontendEnvironmentPath `
    -ObservabilityPath $ObservabilityEnvironmentPath

function Test-Cc4cTcpPort {
    param([Parameter(Mandatory = $true)][string] $HostName, [Parameter(Mandatory = $true)][int] $Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1500)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Assert-Cc4cTcpUrl {
    param(
        [Parameter(Mandatory = $true)][string] $Url,
        [Parameter(Mandatory = $true)][string] $Scheme,
        [Parameter(Mandatory = $true)][string] $Description
    )
    try {
        $uri = [Uri] $Url
    }
    catch {
        throw "$Description URL is invalid."
    }
    if ($uri.Scheme -cne $Scheme) {
        throw "$Description URL uses an unexpected protocol."
    }
    $port = if ($uri.IsDefaultPort) {
        if ($Scheme -eq 'redis') { 6379 } elseif ($Scheme -eq 'amqp') { 5672 } else { 1 }
    } else {
        $uri.Port
    }
    if (-not (Test-Cc4cTcpPort $uri.Host $port)) {
        throw "$Description endpoint is not accepting TCP connections."
    }
    return $uri
}

function Assert-Cc4cFreePort {
    param([Parameter(Mandatory = $true)][int] $Port)
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw "Host port $Port is already occupied."
    }
}

function Assert-Cc4cDatabase {
    param([Parameter(Mandatory = $true)] $Values)
    if ([string]::IsNullOrWhiteSpace($ConfirmDatabase)) {
        throw 'ConfirmDatabase is required for a database-dependent host preflight.'
    }
    $database = Get-Cc4cDatabaseName $Values
    if ($database -cne $ConfirmDatabase) {
        throw 'ConfirmDatabase does not exactly match the database named by CC4C_DB_URL.'
    }
    if ($Values.CC4C_DB_URL -notmatch '^jdbc:mysql://(?<host>[^/:?]+)(?::(?<port>[0-9]+))?/') {
        throw 'CC4C_DB_URL host or port is invalid.'
    }
    $port = if ($Matches.port) { [int] $Matches.port } else { 3306 }
    if (-not (Test-Cc4cTcpPort $Matches.host $port)) {
        throw 'MySQL endpoint is not accepting TCP connections.'
    }
}

function Assert-Cc4cRuntimeDependencies {
    $environment = Read-Cc4cEnvironmentFile -Kind Runtime
    $values = Assert-Cc4cRuntimeEnvironment $environment -ManagementPort $ManagementPort
    Assert-Cc4cDatabase $values
    Assert-Cc4cRedisDependencies $values
    Assert-Cc4cRabbitDependency $values
    Assert-Cc4cMailDependency $values
    return $values
}

function Get-Cc4cRuntimeValues {
    return (Read-Cc4cEnvironmentFile -Kind Runtime).Values
}

function Assert-Cc4cRuntimeFields {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values,
        [Parameter(Mandatory = $true)][string[]] $Names
    )
    foreach ($name in $Names) {
        if (-not $Values.Contains($name) -or [string]::IsNullOrWhiteSpace([string] $Values[$name])) {
            throw "Required runtime variable '$name' is missing or empty for this component."
        }
    }
}

function Assert-Cc4cRedisDependencies {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values)
    Assert-Cc4cTcpUrl $Values.CC4C_REDIS_URL 'redis' 'Security Redis' | Out-Null
    Assert-Cc4cTcpUrl $Values.CC4C_CACHE_REDIS_URL 'redis' 'Business cache Redis' | Out-Null
}

function Assert-Cc4cRabbitDependency {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values)
    $rabbitUri = Assert-Cc4cTcpUrl $Values.CC4C_RABBITMQ_URL 'amqp' 'RabbitMQ'
    if ($rabbitUri.AbsolutePath.Trim('/') -cne 'cc4c') {
        throw 'RabbitMQ host mode requires the preconfigured cc4c vhost.'
    }
    if ([string] $Values.CC4C_RABBITMQ_NAMESPACE -notlike 'cc4c.v3.*') {
        throw 'RabbitMQ namespace must retain the existing v3 namespace family.'
    }
}

function Assert-Cc4cMailDependency {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values)
    Assert-Cc4cTcpUrl ("tcp://{0}:{1}" -f $Values.CC4C_MAIL_HOST, $Values.CC4C_MAIL_PORT) 'tcp' 'Mailpit or SMTP' | Out-Null
}

function Assert-Cc4cFrontendDependency {
    $frontend = Read-Cc4cEnvironmentFile -Kind Frontend
    if (-not $frontend.Values.Contains('VITE_API_BASE_URL') -or
        [string]::IsNullOrWhiteSpace([string] $frontend.Values.VITE_API_BASE_URL)) {
        throw 'VITE_API_BASE_URL is required for host frontend mode.'
    }
}

function Assert-Cc4cObservability {
    $environment = Read-Cc4cEnvironmentFile -Kind Observability
    $values = $environment.Values
    foreach ($name in @(
            'PROMETHEUS_HOME',
            'GRAFANA_HOME',
            'CC4C_MANAGEMENT_USERNAME',
            'CC4C_MANAGEMENT_PASSWORD',
            'CC4C_RABBITMQ_MONITOR_USERNAME',
            'CC4C_RABBITMQ_MONITOR_PASSWORD',
            'CC4C_RABBITMQ_VHOST',
            'CC4C_RABBITMQ_NAMESPACE',
            'GRAFANA_ADMIN_USER',
            'GRAFANA_ADMIN_PASSWORD'
        )) {
        if (-not $values.Contains($name) -or [string]::IsNullOrWhiteSpace([string] $values[$name])) {
            throw "Observability variable '$name' is missing or empty."
        }
    }
    foreach ($file in @(
            (Join-Path $values.PROMETHEUS_HOME 'prometheus.exe'),
            (Join-Path $values.PROMETHEUS_HOME 'promtool.exe'),
            (Join-Path $values.GRAFANA_HOME 'bin\grafana.exe')
        )) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
            throw 'A required Prometheus or Grafana executable was not found.'
        }
    }
    Assert-Cc4cFreePort $PrometheusPort
    Assert-Cc4cFreePort $GrafanaPort
    if (-not (Get-NetTCPConnection -State Listen -LocalPort $ManagementPort -ErrorAction SilentlyContinue)) {
        throw 'The CC4C management endpoint must be listening before observability starts.'
    }
    if (-not (Get-NetTCPConnection -State Listen -LocalPort 15692 -ErrorAction SilentlyContinue)) {
        throw 'The RabbitMQ Prometheus endpoint must be listening before observability starts.'
    }
}

try {
    switch ($Component) {
        'All' {
            $values = Assert-Cc4cRuntimeDependencies
            Assert-Cc4cFreePort $ApplicationPort
            Assert-Cc4cFreePort $ManagementPort
            Assert-Cc4cFreePort $FrontendPort
            Assert-Cc4cFrontendDependency
        }
        'Backend' {
            $values = Assert-Cc4cRuntimeDependencies
            Assert-Cc4cFreePort $ApplicationPort
            Assert-Cc4cFreePort $ManagementPort
        }
        'MySQL' {
            $values = Get-Cc4cRuntimeValues
            Assert-Cc4cRuntimeFields $values @('CC4C_DB_URL', 'CC4C_DB_USERNAME', 'CC4C_DB_PASSWORD')
            Assert-Cc4cDatabase $values
        }
        'Redis' {
            $values = Get-Cc4cRuntimeValues
            Assert-Cc4cRuntimeFields $values @('CC4C_REDIS_URL', 'CC4C_CACHE_REDIS_URL', 'CC4C_SESSION_NAMESPACE', 'CC4C_CACHE_NAMESPACE')
            Assert-Cc4cRedisDependencies $values
        }
        'RabbitMQ' {
            $values = Get-Cc4cRuntimeValues
            Assert-Cc4cRuntimeFields $values @('CC4C_RABBITMQ_URL', 'CC4C_RABBITMQ_NAMESPACE')
            Assert-Cc4cRabbitDependency $values
        }
        'Mailpit' {
            $values = Get-Cc4cRuntimeValues
            Assert-Cc4cRuntimeFields $values @('CC4C_MAIL_HOST', 'CC4C_MAIL_PORT', 'CC4C_MAIL_AUTH', 'CC4C_MAIL_SSL_ENABLED', 'CC4C_MAIL_STARTTLS_ENABLED')
            Assert-Cc4cMailDependency $values
        }
        'Frontend' {
            Assert-Cc4cFreePort $FrontendPort
            Assert-Cc4cFrontendDependency
        }
        'Observability' {
            Assert-Cc4cObservability
        }
    }
    Write-Output "Host preflight passed for component '$Component'. No service was started or stopped."
    exit 0
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
finally {
    Restore-Cc4cHostEnvironmentPathOverrides $environmentPathSnapshot
}
