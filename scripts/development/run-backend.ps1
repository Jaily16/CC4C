# 运行前提：Java、Maven 和用户已提供的运行配置可用；配置内容不在脚本输出中展开。
# 破坏性边界：只启动当前后端进程，不停止或重启现有 Compose 服务，不删除数据或上传文件。
# 失败恢复：进程失败时保留退出码和日志，由调用方处理；脚本不执行自动清理。
# 退出码：透传后端进程退出码，启动前置检查失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ApplicationArguments
)

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $workspaceRoot 'backend'
$canonicalRuntimeEnvironmentPath = Join-Path $workspaceRoot 'backend\.env.runtime.local'
$legacyRuntimeEnvironmentPath = Join-Path $workspaceRoot 'back-end\CC4C\.env.runtime.local'
$runtimeEnvironmentPath = if (Test-Path -LiteralPath $canonicalRuntimeEnvironmentPath -PathType Leaf) {
    $canonicalRuntimeEnvironmentPath
} else {
    $legacyRuntimeEnvironmentPath
}
$requiredNames = @(
    'CC4C_DB_URL',
    'CC4C_DB_USERNAME',
    'CC4C_DB_PASSWORD',
    'CC4C_REDIS_URL',
    'CC4C_SESSION_NAMESPACE',
    'CC4C_BUSINESS_CACHE_ENABLED',
    'CC4C_CACHE_REDIS_URL',
    'CC4C_CACHE_NAMESPACE',
    'CC4C_SECURITY_PEPPER',
    'CC4C_SESSION_COOKIE_SECURE',
    'CC4C_ALLOWED_ORIGINS',
    'CC4C_MAIL_USERNAME',
    'CC4C_MAIL_PASSWORD',
    'CC4C_RABBITMQ_URL',
    'CC4C_RABBITMQ_NAMESPACE',
    'CC4C_MODERATION_NOTIFICATION_RECIPIENTS',
    'CC4C_MESSAGING_ACTIVE_KEY_ID',
    'CC4C_MESSAGING_PAYLOAD_KEYS',
    'CC4C_MESSAGING_CONFIRM_TIMEOUT',
    'CC4C_MESSAGING_CONSUMER_RETRY_DELAYS',
    'CC4C_OUTBOX_DISPATCHER_ENABLED',
    'CC4C_MESSAGE_CONSUMERS_ENABLED',
    'CC4C_API_DOCS_ENABLED',
    'CC4C_OBSERVABILITY_ENABLED',
    'CC4C_MANAGEMENT_ADDRESS',
    'CC4C_MANAGEMENT_PORT',
    'CC4C_MANAGEMENT_USERNAME',
    'CC4C_MANAGEMENT_PASSWORD',
    'CC4C_OBSERVABILITY_ENVIRONMENT',
    'CC4C_LOG_FORMAT',
    'CC4C_SAVE_IMG_PATH',
    'CC4C_REQUEST_IMG_PATH',
    'CC4C_SAVE_AVATAR_PATH',
    'CC4C_REQUEST_AVATAR_PATH'
)
$optionalDefaults = @{
    CC4C_DB_CONNECTION_TIMEOUT_MS = '3000'
    CC4C_DB_VALIDATION_TIMEOUT_MS = '1000'
    CC4C_MESSAGING_SAMPLE_INTERVAL = '15s'
    CC4C_MAX_HTTP_URI_TAGS = '100'
    CC4C_MAIL_HOST = '127.0.0.1'
    CC4C_MAIL_PORT = '1025'
    CC4C_MAIL_AUTH = 'false'
    CC4C_MAIL_SSL_ENABLED = 'false'
    CC4C_MAIL_STARTTLS_ENABLED = 'false'
}
$optionalNames = @($optionalDefaults.Keys)
$allowedNames = $requiredNames + $optionalNames

if (-not (Test-Path -LiteralPath $runtimeEnvironmentPath -PathType Leaf)) {
    throw 'Missing the local runtime environment file. Copy .env.runtime.example and fill the local values first.'
}

$values = @{}
$lineNumber = 0
foreach ($rawLine in Get-Content -LiteralPath $runtimeEnvironmentPath) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($rawLine) -or $rawLine.TrimStart().StartsWith('#')) {
        continue
    }
    $separator = $rawLine.IndexOf('=')
    if ($separator -le 0) {
        throw "Invalid entry on line $lineNumber of the local runtime environment file. Expected NAME=value."
    }
    $name = $rawLine.Substring(0, $separator).Trim()
    if ($allowedNames -notcontains $name) {
        throw "Unsupported variable '$name' in the local runtime environment file."
    }
    if ($values.ContainsKey($name)) {
        throw "Duplicate variable '$name' in the local runtime environment file."
    }
    $values[$name] = $rawLine.Substring($separator + 1)
}

foreach ($name in $requiredNames) {
    if (-not $values.ContainsKey($name)) {
        throw "Required variable '$name' is missing from .env.runtime.local."
    }
}
foreach ($name in $optionalNames) {
    if (-not $values.ContainsKey($name)) {
        $values[$name] = $optionalDefaults[$name]
    }
}
foreach ($name in $requiredNames | Where-Object { $_ -notin @('CC4C_MAIL_USERNAME', 'CC4C_MAIL_PASSWORD') }) {
    if ([string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required variable '$name' is empty in .env.runtime.local."
    }
}
if ($values['CC4C_SECURITY_PEPPER'].Length -lt 32) {
    throw 'CC4C_SECURITY_PEPPER must contain at least 32 characters.'
}
if ($values['CC4C_SESSION_COOKIE_SECURE'] -notin @('true', 'false')) {
    throw 'CC4C_SESSION_COOKIE_SECURE must be true or false.'
}
if ($values['CC4C_API_DOCS_ENABLED'] -notin @('true', 'false')) {
    throw 'CC4C_API_DOCS_ENABLED must be true or false.'
}
if ($values['CC4C_OBSERVABILITY_ENABLED'] -notin @('true', 'false')) {
    throw 'CC4C_OBSERVABILITY_ENABLED must be true or false.'
}
foreach ($mailSwitch in @('CC4C_MAIL_AUTH', 'CC4C_MAIL_SSL_ENABLED', 'CC4C_MAIL_STARTTLS_ENABLED')) {
    if ($values[$mailSwitch] -notin @('true', 'false')) {
        throw "$mailSwitch must be true or false."
    }
}
$mailPort = 0
if (-not [int]::TryParse($values['CC4C_MAIL_PORT'], [ref]$mailPort) -or $mailPort -lt 1 -or $mailPort -gt 65535) {
    throw 'CC4C_MAIL_PORT must be between 1 and 65535.'
}
if ($values['CC4C_MANAGEMENT_ADDRESS'] -ne '127.0.0.1') {
    throw 'CC4C_MANAGEMENT_ADDRESS must be 127.0.0.1 for local runs.'
}
if ($values['CC4C_MANAGEMENT_PORT'] -ne '4081') {
    throw 'CC4C_MANAGEMENT_PORT must be 4081 for local runs.'
}
if ($values['CC4C_MANAGEMENT_PASSWORD'].Length -lt 24) {
    throw 'CC4C_MANAGEMENT_PASSWORD must contain at least 24 characters.'
}
if ($values['CC4C_OBSERVABILITY_ENVIRONMENT'] -notmatch '^[a-z0-9-]{2,32}$') {
    throw 'CC4C_OBSERVABILITY_ENVIRONMENT is invalid.'
}
if ($values['CC4C_LOG_FORMAT'] -ne 'ecs') {
    throw 'CC4C_LOG_FORMAT must be ecs for the Aspect 6 local acceptance run.'
}
if ($values['CC4C_MESSAGING_SAMPLE_INTERVAL'] -notmatch '^\d+(ms|s|m)$') {
    throw 'CC4C_MESSAGING_SAMPLE_INTERVAL must be a positive duration such as 15s.'
}
$databaseConnectionTimeout = 0
if (-not [int]::TryParse($values['CC4C_DB_CONNECTION_TIMEOUT_MS'], [ref]$databaseConnectionTimeout) -or
        $databaseConnectionTimeout -lt 250 -or $databaseConnectionTimeout -gt 60000) {
    throw 'CC4C_DB_CONNECTION_TIMEOUT_MS must be an integer from 250 to 60000.'
}
$databaseValidationTimeout = 0
if (-not [int]::TryParse($values['CC4C_DB_VALIDATION_TIMEOUT_MS'], [ref]$databaseValidationTimeout) -or
        $databaseValidationTimeout -lt 250 -or
        $databaseValidationTimeout -ge $databaseConnectionTimeout) {
    throw 'CC4C_DB_VALIDATION_TIMEOUT_MS must be at least 250 and less than CC4C_DB_CONNECTION_TIMEOUT_MS.'
}
$maxHttpUriTags = 0
if (-not [int]::TryParse($values['CC4C_MAX_HTTP_URI_TAGS'], [ref]$maxHttpUriTags) -or
        $maxHttpUriTags -lt 1 -or $maxHttpUriTags -gt 1000) {
    throw 'CC4C_MAX_HTTP_URI_TAGS must be an integer from 1 to 1000.'
}
if ($values['CC4C_BUSINESS_CACHE_ENABLED'] -notin @('true', 'false')) {
    throw 'CC4C_BUSINESS_CACHE_ENABLED must be true or false.'
}
if ($values['CC4C_OUTBOX_DISPATCHER_ENABLED'] -notin @('true', 'false')) {
    throw 'CC4C_OUTBOX_DISPATCHER_ENABLED must be true or false.'
}
if ($values['CC4C_MESSAGE_CONSUMERS_ENABLED'] -notin @('true', 'false')) {
    throw 'CC4C_MESSAGE_CONSUMERS_ENABLED must be true or false.'
}
if ($values['CC4C_CACHE_NAMESPACE'] -eq $values['CC4C_SESSION_NAMESPACE']) {
    throw 'CC4C_CACHE_NAMESPACE must differ from CC4C_SESSION_NAMESPACE.'
}
if ($values['CC4C_ALLOWED_ORIGINS'].Contains('*')) {
    throw 'CC4C_ALLOWED_ORIGINS must contain exact origins and cannot contain wildcards.'
}

$javaVersionOutput = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersionOutput -join "`n") -notmatch 'version "21(?:\.|"|-)') {
    throw 'Java 21 is required. Set JAVA_HOME and place its bin directory first on PATH.'
}

$jarPath = Join-Path $workspaceRoot 'backend\target\cc4c-4.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Missing application JAR: $jarPath. Run .\scripts\testing\run-backend-tests.ps1 clean verify first."
}

$original = @{}
$processNames = $allowedNames + 'SPRING_CONFIG_NAME' + 'SPRING_APPLICATION_NAME'
foreach ($name in $processNames) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
foreach ($name in $allowedNames) {
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}
[Environment]::SetEnvironmentVariable('SPRING_CONFIG_NAME', 'application-example', 'Process')
[Environment]::SetEnvironmentVariable('SPRING_APPLICATION_NAME', 'CC4C', 'Process')

Push-Location -LiteralPath $backendRoot
try {
    & java -jar $jarPath @ApplicationArguments
    $applicationExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
    foreach ($name in $processNames) {
        [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process')
    }
}

exit $applicationExitCode
