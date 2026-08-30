# 运行前提：仅启动隔离性能后端，Java 路径和模式必须由用户明确确认。
# 破坏性边界：不停止现有 Java/Compose 服务，不读取受保护配置，不修改数据库、卷或上传目录。
# 失败恢复：启动失败保留 PID/日志和原环境变量，按记录的目标执行精确恢复。
# 退出码：启动成功返回 0，参数、构建或进程启动失败返回非零码。

[CmdletBinding()]
param(
    [ValidateSet('baseline', 'observability-on')]
    [string]$Mode = 'observability-on',
    [string]$JavaHome = 'D:\tool\Java\jdk-21'
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$canonicalBackendRoot = Join-Path $workspaceRoot 'backend'
$legacyBackendRoot = Join-Path $workspaceRoot 'back-end\CC4C'
$performanceFile = if (Test-Path -LiteralPath (Join-Path $canonicalBackendRoot '.env.performance.local') -PathType Leaf) {
    Join-Path $canonicalBackendRoot '.env.performance.local'
} else {
    Join-Path $legacyBackendRoot '.env.performance.local'
}
$runtimeFile = if (Test-Path -LiteralPath (Join-Path $canonicalBackendRoot '.env.runtime.local') -PathType Leaf) {
    Join-Path $canonicalBackendRoot '.env.runtime.local'
} else {
    Join-Path $legacyBackendRoot '.env.runtime.local'
}
$canonicalObservabilityFile = Join-Path $workspaceRoot 'infrastructure\observability\.env.observability.local'
$legacyObservabilityFile = Join-Path $workspaceRoot 'observability\.env.observability.local'
$observabilityFile = if (Test-Path -LiteralPath $canonicalObservabilityFile -PathType Leaf) {
    $canonicalObservabilityFile
} else {
    $legacyObservabilityFile
}

function Read-EnvironmentFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required local environment file is missing: $Path"
    }
    $values = @{}
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid environment entry on line $lineNumber of $Path."
        }
        $name = $line.Substring(0, $separator).Trim()
        if ($values.ContainsKey($name)) {
            throw "Duplicate environment variable '$name' in $Path."
        }
        $values[$name] = $line.Substring($separator + 1)
    }
    return $values
}

function Required([hashtable]$Values, [string]$Name) {
    if (-not $Values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Values[$Name])) {
        throw "Required performance setting '$Name' is missing or empty."
    }
    return $Values[$Name]
}

$performance = Read-EnvironmentFile $performanceFile
$runtime = Read-EnvironmentFile $runtimeFile
$observability = Read-EnvironmentFile $observabilityFile

$databaseUrl = Required $performance 'CC4C_PERF_DB_URL'
$databaseMatch = [regex]::Match(
    $databaseUrl,
    '^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $databaseMatch.Success) {
    throw 'Performance JDBC URL is invalid.'
}
$database = $databaseMatch.Groups['database'].Value
$confirmation = Required $performance 'CC4C_PERF_DB_RESET_CONFIRM'
if (-not $database.EndsWith('_perf_test') -or $database -cne $confirmation) {
    throw 'Performance database must end with _perf_test and exactly match confirmation.'
}

$baseUri = [Uri](Required $performance 'CC4C_PERF_BASE_URL')
if ($baseUri.Scheme -ne 'http' -or
        $baseUri.Host -notin @('127.0.0.1', 'localhost') -or
        $baseUri.Port -ne 4080) {
    throw 'Performance Base URL must be loopback HTTP on port 4080.'
}
$sessionNamespace = Required $performance 'CC4C_PERF_SESSION_NAMESPACE'
$cacheNamespace = Required $performance 'CC4C_PERF_CACHE_NAMESPACE'
$rabbitNamespace = Required $performance 'CC4C_PERF_RABBITMQ_NAMESPACE'
if ((@($sessionNamespace, $cacheNamespace, $rabbitNamespace) | Select-Object -Unique).Count -ne 3) {
    throw 'Performance Session, cache, and RabbitMQ namespaces must be distinct.'
}
if ($sessionNamespace -notmatch '^[A-Za-z0-9:._-]{3,120}$' -or
        $cacheNamespace -notmatch '^[A-Za-z0-9:._-]{3,120}$' -or
        $rabbitNamespace -notmatch '^[A-Za-z0-9:._-]{3,120}$') {
    throw 'A performance namespace contains unsupported characters.'
}

foreach ($port in @(4080, 4081)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is occupied; stop the current CC4C backend before starting a performance server."
    }
}
if ($Mode -eq 'observability-on' -and
        -not (Get-NetTCPConnection -State Listen -LocalPort 9090 -ErrorAction SilentlyContinue)) {
    throw 'Observability-on mode requires Prometheus on port 9090.'
}

$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw "Java executable was not found below $JavaHome."
}
$javaVersion = @(& $java -version 2>&1)
if ($javaVersion.Count -eq 0 -or [string]$javaVersion[0] -notmatch 'version "21\.') {
    throw 'Java 21 is required for the performance server.'
}
$backendRoot = Join-Path $workspaceRoot 'backend'
$jarPath = Join-Path $backendRoot 'target\cc4c-4.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw 'The verified CC4C application JAR is missing.'
}

$observabilityEnabled = $Mode -eq 'observability-on'
$managementUsername = if ($observabilityEnabled) {
    Required $observability 'CC4C_MANAGEMENT_USERNAME'
} else {
    'benchmark-observer'
}
$managementPassword = if ($observabilityEnabled) {
    Required $observability 'CC4C_MANAGEMENT_PASSWORD'
} else {
    'benchmark-observer-disabled'
}
if ($observabilityEnabled -and $managementPassword.Length -lt 24) {
    throw 'The observability-on management password must contain at least 24 characters.'
}

$settings = [ordered]@{
    CC4C_DB_URL = $databaseUrl
    CC4C_DB_USERNAME = Required $performance 'CC4C_PERF_DB_USERNAME'
    CC4C_DB_PASSWORD = Required $performance 'CC4C_PERF_DB_PASSWORD'
    CC4C_REDIS_URL = Required $performance 'CC4C_PERF_CACHE_REDIS_URL'
    CC4C_SESSION_NAMESPACE = $sessionNamespace
    CC4C_BUSINESS_CACHE_ENABLED = 'true'
    CC4C_CACHE_REDIS_URL = Required $performance 'CC4C_PERF_CACHE_REDIS_URL'
    CC4C_CACHE_NAMESPACE = $cacheNamespace
    CC4C_SECURITY_PEPPER = 'cc4c-performance-pepper-not-secret-20260827'
    CC4C_SESSION_COOKIE_SECURE = 'false'
    CC4C_ALLOWED_ORIGINS = 'http://127.0.0.1:5173,http://localhost:5173'
    CC4C_MAIL_USERNAME = ''
    CC4C_MAIL_PASSWORD = ''
    CC4C_RABBITMQ_URL = Required $runtime 'CC4C_RABBITMQ_URL'
    CC4C_RABBITMQ_NAMESPACE = $rabbitNamespace
    CC4C_MODERATION_NOTIFICATION_RECIPIENTS = 'benchmark@example.invalid'
    CC4C_MESSAGING_ACTIVE_KEY_ID = 'performance-v1'
    CC4C_MESSAGING_PAYLOAD_KEYS = 'performance-v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='
    CC4C_MESSAGING_CONFIRM_TIMEOUT = '1s'
    CC4C_MESSAGING_CONSUMER_RETRY_DELAYS = '1s,2s,3s'
    CC4C_OUTBOX_DISPATCHER_ENABLED = 'false'
    CC4C_MESSAGE_CONSUMERS_ENABLED = 'false'
    CC4C_API_DOCS_ENABLED = 'false'
    CC4C_OBSERVABILITY_ENABLED = $observabilityEnabled.ToString().ToLowerInvariant()
    CC4C_MANAGEMENT_ADDRESS = '127.0.0.1'
    CC4C_MANAGEMENT_PORT = if ($observabilityEnabled) { '4081' } else { '-1' }
    CC4C_MANAGEMENT_USERNAME = $managementUsername
    CC4C_MANAGEMENT_PASSWORD = $managementPassword
    CC4C_OBSERVABILITY_ENVIRONMENT = 'performance'
    CC4C_LOG_FORMAT = if ($observabilityEnabled) { 'ecs' } else { '' }
    CC4C_MESSAGING_SAMPLE_INTERVAL = '15s'
    CC4C_MAX_HTTP_URI_TAGS = '100'
    CC4C_SAVE_IMG_PATH = 'target/performance-files/blog/'
    CC4C_REQUEST_IMG_PATH = 'http://127.0.0.1:5173/performance-blog/'
    CC4C_SAVE_AVATAR_PATH = 'target/performance-files/avatar/'
    CC4C_REQUEST_AVATAR_PATH = 'http://127.0.0.1:5173/performance-avatar/'
    SPRING_CONFIG_NAME = 'application-example'
    SPRING_APPLICATION_NAME = 'CC4C'
}

$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-performance-server'
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdout = Join-Path $runDirectory "server-$Mode-$stamp.stdout.log"
$stderr = Join-Path $runDirectory "server-$Mode-$stamp.stderr.log"
$previous = @{}
foreach ($name in $settings.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
}
try {
    $arguments = @('-jar', $jarPath)
    if (-not $observabilityEnabled) {
        $arguments += '--management.metrics.enable.all=false'
    }
    $process = Start-Process -FilePath $java -WindowStyle Hidden -PassThru `
        -WorkingDirectory $backendRoot -ArgumentList $arguments `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
} finally {
    foreach ($name in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

$deadline = (Get-Date).AddSeconds(60)
do {
    $business = Get-NetTCPConnection -State Listen -LocalPort 4080 -ErrorAction SilentlyContinue |
        Where-Object { $_.OwningProcess -eq $process.Id } |
        Select-Object -First 1
    $management = Get-NetTCPConnection -State Listen -LocalPort 4081 -ErrorAction SilentlyContinue |
        Where-Object { $_.OwningProcess -eq $process.Id } |
        Select-Object -First 1
    $ready = $business -and (-not $observabilityEnabled -or $management)
    if ($ready -or $process.HasExited) {
        break
    }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $deadline)

if (-not $ready) {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -ErrorAction Stop
    }
    throw 'Performance server did not become ready. Inspect the ignored performance server logs.'
}
Set-Content -LiteralPath (Join-Path $runDirectory 'server.pid') -Value $process.Id
Set-Content -LiteralPath (Join-Path $runDirectory 'server.mode') -Value $Mode
Set-Content -LiteralPath (Join-Path $runDirectory 'server.stdout.current') -Value $stdout
Set-Content -LiteralPath (Join-Path $runDirectory 'server.stderr.current') -Value $stderr
Write-Host "CC4C performance server started in $Mode mode with an exact Java PID record."
