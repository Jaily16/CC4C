#requires -Version 7.0
# 运行前提：三端使用固定 .env.local；数据库与所有中间件均由用户预先提供。
# 外部依赖：依赖共享环境加载器，并只读连接用户提供的 MySQL、Redis、RabbitMQ、SMTP 和 Prometheus 端点。
# 破坏性边界：只检查配置形状、精确数据库名、TCP、端口和外部 Prometheus 的公开状态。
# 失败恢复：失败即停止，不修改配置，不创建数据库，不管理任何服务。
# 退出码：所选检查全部通过为 0，否则为 1。

[CmdletBinding()]
param(
    [ValidateSet('All', 'MySQL', 'Redis', 'RabbitMQ', 'SMTP', 'Backend', 'Frontend', 'Observability', 'Prometheus')]
    [string] $Component = 'All',
    [string] $ConfirmDatabase,
    [ValidateRange(1, 65535)][int] $ApplicationPort = 4080,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081,
    [ValidateRange(1, 65535)][int] $FrontendPort = 5173,
    [ValidateRange(1, 65535)][int] $ObservabilityPort = 5174,
    [string] $PrometheusUrl = 'http://127.0.0.1:9090'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

# 仅建立一次有界 TCP 连接，不发送认证命令或读取中间件数据。
function Test-Cc4cTcpPort {
    param([string] $HostName, [ValidateRange(1, 65535)][int] $Port)
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.ConnectAsync($HostName, $Port)
        if (-not $pending.Wait(2000)) { return $false }
        return $client.Connected
    }
    catch { return $false }
    finally { $client.Dispose() }
}

# URL 解析和错误只暴露组件名称，不回显其中可能包含的凭据。
function Assert-Cc4cTcpUrl {
    param([string] $Url, [string[]] $Schemes, [int] $DefaultPort, [string] $Description)
    $uri = Get-Cc4cEndpointUri $Url $Schemes $Description
    $port = if ($uri.Port -gt 0) { $uri.Port } else { $DefaultPort }
    if (-not (Test-Cc4cTcpPort $uri.Host $port)) { throw "$Description endpoint is not accepting TCP connections." }
    return $uri
}

# 只查询监听端口；被占用时拒绝启动，不结束占用者，也不自动选择替代端口。
function Assert-Cc4cFreePort {
    param([int] $Port)
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object Port -eq $Port).Count -gt 0) { throw "Host port $Port is already occupied." }
}

# 数据库名必须逐字符一致；仅检查 JDBC 地址和连通性，不执行 SQL。
function Assert-Cc4cDatabase {
    param([System.Collections.IDictionary] $Values)
    if ([string]::IsNullOrWhiteSpace($ConfirmDatabase) -or (Get-Cc4cDatabaseName $Values) -cne $ConfirmDatabase) {
        throw 'ConfirmDatabase must exactly match the database named in backend/.env.local.'
    }
    $uri = Get-Cc4cEndpointUri (([string] $Values.CC4C_DB_URL).Substring(5)) @('mysql') 'MySQL'
    $port = if ($uri.Port -gt 0) { $uri.Port } else { 3306 }
    if (-not (Test-Cc4cTcpPort $uri.Host $port)) { throw 'MySQL endpoint is not accepting TCP connections.' }
}

# 复用一个 Redis 地址；不同 namespace 的形状与冲突由共享运行配置校验器检查。
function Assert-Cc4cRedis {
    param([System.Collections.IDictionary] $Values)
    $null = Assert-Cc4cTcpUrl $Values.CC4C_REDIS_URL @('redis', 'rediss') 6379 'Redis'
}

# 只接受预先配置的 vhost 和既有消息命名空间族，不创建、清空或删除消息资源。
function Assert-Cc4cRabbit {
    param([System.Collections.IDictionary] $Values)
    $scheme = (Get-Cc4cEndpointUri $Values.CC4C_RABBITMQ_URL @('amqp', 'amqps') 'RabbitMQ').Scheme
    $port = if ($scheme -eq 'amqps') { 5671 } else { 5672 }
    $uri = Assert-Cc4cTcpUrl $Values.CC4C_RABBITMQ_URL @('amqp', 'amqps') $port 'RabbitMQ'
    if ([Uri]::UnescapeDataString($uri.AbsolutePath.Trim('/')) -cne 'cc4c' -or
        $Values.CC4C_RABBITMQ_NAMESPACE -cnotmatch '^cc4c\.v3\.[A-Za-z0-9._-]+$') {
        throw 'RabbitMQ requires the preconfigured cc4c vhost and existing v3 namespace family.'
    }
}

# SMTP 只检查 TCP 可连接性，不发送邮件或验证 TLS 握手；共享加载器检查参数形状，真实认证和投递另行确认。
function Assert-Cc4cSmtp {
    param([System.Collections.IDictionary] $Values)
    if (-not (Test-Cc4cTcpPort $Values.CC4C_MAIL_HOST ([int] $Values.CC4C_MAIL_PORT))) {
        throw 'SMTP endpoint is not accepting TCP connections.'
    }
}

try {
    if ($Component -notin @('Frontend', 'Observability', 'Prometheus')) {
        $values = Assert-Cc4cRuntimeEnvironment (Read-Cc4cEnvironmentFile -Kind Runtime) -ManagementPort $ManagementPort
    }
    if ($Component -in @('All', 'Backend', 'MySQL')) { Assert-Cc4cDatabase $values }
    if ($Component -in @('All', 'Backend', 'Redis')) { Assert-Cc4cRedis $values }
    if ($Component -in @('All', 'Backend', 'RabbitMQ')) { Assert-Cc4cRabbit $values }
    if ($Component -in @('All', 'Backend', 'SMTP')) { Assert-Cc4cSmtp $values }
    if ($Component -in @('All', 'Backend')) {
        if ($ApplicationPort -eq $ManagementPort) { throw 'Application and management ports must differ.' }
        Assert-Cc4cFreePort $ApplicationPort
        Assert-Cc4cFreePort $ManagementPort
    }
    if ($Component -in @('All', 'Frontend')) {
        $null = Read-Cc4cEnvironmentFile -Kind Frontend
        if ($Component -eq 'All' -and $FrontendPort -in @($ApplicationPort, $ManagementPort, $ObservabilityPort)) {
            throw 'Backend, frontend and observability ports must differ.'
        }
        Assert-Cc4cFreePort $FrontendPort
    }
    if ($Component -in @('All', 'Observability')) {
        $null = Read-Cc4cEnvironmentFile -Kind Observability
        if ($Component -eq 'All' -and $ObservabilityPort -in @($ApplicationPort, $ManagementPort, $FrontendPort)) {
            throw 'Backend, frontend and observability ports must differ.'
        }
        Assert-Cc4cFreePort $ObservabilityPort
    }
    if ($Component -in @('All', 'Prometheus')) { Assert-Cc4cPrometheusEndpoint -BaseUrl $PrometheusUrl }
    Write-Output "Host preflight passed for '$Component'. No service or data was changed."
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
