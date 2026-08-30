# 运行前提：只在用户明确启动宿主机模式时调用；本文件本身不读取任何本机配置。
# 破坏性边界：只解析用户提供的环境变量名称，不打印值，不管理外部数据库、缓存、消息或邮件服务。
# 失败恢复：调用方必须在 finally 中恢复进程级环境变量；本文件不写回本机环境文件。
# 退出码：函数通过异常报告失败；调用脚本负责将异常转换为非零退出码。

$script:Cc4cHostWorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:Cc4cRuntimeRequiredNames = @(
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
$script:Cc4cRuntimeDefaults = [ordered]@{
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
$script:Cc4cRuntimeAllowedNames = @(
    $script:Cc4cRuntimeRequiredNames
    $script:Cc4cRuntimeDefaults.Keys
)

function Get-Cc4cHostWorkspaceRoot {
    return $script:Cc4cHostWorkspaceRoot
}

function Resolve-Cc4cLocalFile {
    param(
        [Parameter(Mandatory = $true)][string] $CanonicalRelativePath,
        [Parameter(Mandatory = $true)][string] $LegacyRelativePath,
        [string] $OverrideEnvironmentVariable
    )
    if (-not [string]::IsNullOrWhiteSpace($OverrideEnvironmentVariable)) {
        $override = [Environment]::GetEnvironmentVariable($OverrideEnvironmentVariable, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($override)) {
            if (-not [System.IO.Path]::IsPathRooted($override)) {
                throw "The explicit environment path for $OverrideEnvironmentVariable must be absolute."
            }
            $resolvedOverride = [System.IO.Path]::GetFullPath($override)
            $workspaceRoot = [System.IO.Path]::GetFullPath($script:Cc4cHostWorkspaceRoot).TrimEnd('\') + '\'
            if ($resolvedOverride.StartsWith($workspaceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "The explicit environment path for $OverrideEnvironmentVariable must be outside the workspace."
            }
            if (-not (Test-Path -LiteralPath $resolvedOverride -PathType Leaf)) {
                throw "The explicit environment file for $OverrideEnvironmentVariable does not exist."
            }
            return $resolvedOverride
        }
    }
    $canonical = Join-Path $script:Cc4cHostWorkspaceRoot $CanonicalRelativePath
    $legacy = Join-Path $script:Cc4cHostWorkspaceRoot $LegacyRelativePath
    if (Test-Path -LiteralPath $canonical -PathType Leaf) {
        return (Resolve-Path -LiteralPath $canonical).Path
    }
    if (Test-Path -LiteralPath $legacy -PathType Leaf) {
        return (Resolve-Path -LiteralPath $legacy).Path
    }
    return $null
}

function Set-Cc4cHostEnvironmentPathOverrides {
    param(
        [string] $RuntimePath,
        [string] $FrontendPath,
        [string] $ObservabilityPath
    )
    $names = @(
        'CC4C_HOST_RUNTIME_ENV_PATH',
        'CC4C_HOST_FRONTEND_ENV_PATH',
        'CC4C_HOST_OBSERVABILITY_ENV_PATH'
    )
    $requested = @($RuntimePath, $FrontendPath, $ObservabilityPath)
    $original = [ordered]@{}
    for ($index = 0; $index -lt $names.Count; $index++) {
        $name = $names[$index]
        $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($requested[$index])) {
            [Environment]::SetEnvironmentVariable($name, $requested[$index], 'Process')
        }
    }
    return [pscustomobject]@{ Names = $names; Values = $original }
}

function Restore-Cc4cHostEnvironmentPathOverrides {
    param([Parameter(Mandatory = $true)] $Snapshot)
    foreach ($name in $Snapshot.Names) {
        [Environment]::SetEnvironmentVariable($name, $Snapshot.Values[$name], 'Process')
    }
}

function Read-Cc4cEnvironmentFile {
    param(
        [Parameter(Mandatory = $true)][string] $Kind,
        [switch] $AllowMissing
    )
    switch ($Kind) {
        'Runtime' {
            $path = Resolve-Cc4cLocalFile 'backend\.env.runtime.local' 'back-end\CC4C\.env.runtime.local' 'CC4C_HOST_RUNTIME_ENV_PATH'
            $allowed = $script:Cc4cRuntimeAllowedNames
            $defaults = $script:Cc4cRuntimeDefaults
        }
        'Frontend' {
            $path = Resolve-Cc4cLocalFile 'frontend\.env.local' 'front-end\CC4C\.env.local' 'CC4C_HOST_FRONTEND_ENV_PATH'
            $allowed = @('VITE_API_BASE_URL')
            $defaults = @{}
        }
        'Observability' {
            $path = Resolve-Cc4cLocalFile 'infrastructure\observability\.env.observability.local' 'observability\.env.observability.local' 'CC4C_HOST_OBSERVABILITY_ENV_PATH'
            $allowed = @(
                'PROMETHEUS_HOME',
                'GRAFANA_HOME',
                'CC4C_MANAGEMENT_USERNAME',
                'CC4C_MANAGEMENT_PASSWORD',
                'CC4C_OBSERVABILITY_ENVIRONMENT',
                'CC4C_RABBITMQ_MONITOR_USERNAME',
                'CC4C_RABBITMQ_MONITOR_PASSWORD',
                'CC4C_RABBITMQ_VHOST',
                'CC4C_RABBITMQ_NAMESPACE',
                'GRAFANA_ADMIN_USER',
                'GRAFANA_ADMIN_PASSWORD'
            )
            $defaults = @{}
        }
        default {
            throw "Unsupported host environment kind '$Kind'."
        }
    }
    if ($null -eq $path) {
        if ($AllowMissing) {
            return [pscustomobject]@{ Path = $null; Values = @{} }
        }
        throw "Missing the local $Kind environment file. Use the corresponding .env.*.example as a template."
    }
    $values = [ordered]@{}
    $lineNumber = 0
    foreach ($rawLine in Get-Content -LiteralPath $path) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($rawLine) -or $rawLine.TrimStart().StartsWith('#')) {
            continue
        }
        $separator = $rawLine.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid $Kind environment entry on line $lineNumber."
        }
        $name = $rawLine.Substring(0, $separator).Trim()
        if ($allowed -notcontains $name) {
            throw "Unsupported $Kind environment variable '$name'."
        }
        if ($values.Contains($name)) {
            throw "Duplicate $Kind environment variable '$name'."
        }
        $values[$name] = $rawLine.Substring($separator + 1)
    }
    foreach ($name in $defaults.Keys) {
        if (-not $values.Contains($name)) {
            $values[$name] = $defaults[$name]
        }
    }
    return [pscustomobject]@{ Path = $path; Values = $values }
}

function Assert-Cc4cBooleanValue {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values, [Parameter(Mandatory = $true)][string] $Name)
    if ($Values[$Name] -notin @('true', 'false')) {
        throw "$Name must be true or false."
    }
}

function Assert-Cc4cRuntimeEnvironment {
    param(
        [Parameter(Mandatory = $true)] $Environment,
        [ValidateRange(1, 65535)][int] $ManagementPort = 4081
    )
    $values = $Environment.Values
    foreach ($name in $script:Cc4cRuntimeRequiredNames) {
        if (-not $values.Contains($name)) {
            throw "Required runtime variable '$name' is missing."
        }
    }
    foreach ($name in $script:Cc4cRuntimeRequiredNames | Where-Object {
            $_ -notin @('CC4C_MAIL_USERNAME', 'CC4C_MAIL_PASSWORD')
        }) {
        if ([string]::IsNullOrWhiteSpace([string] $values[$name])) {
            throw "Required runtime variable '$name' is empty."
        }
    }
    if ([string] $values.CC4C_DB_URL -notmatch '^jdbc:mysql://[^/]+/(?<database>[^?]+)') {
        throw 'CC4C_DB_URL must identify a MySQL database.'
    }
    Assert-Cc4cBooleanValue $values 'CC4C_SESSION_COOKIE_SECURE'
    Assert-Cc4cBooleanValue $values 'CC4C_BUSINESS_CACHE_ENABLED'
    Assert-Cc4cBooleanValue $values 'CC4C_API_DOCS_ENABLED'
    Assert-Cc4cBooleanValue $values 'CC4C_OBSERVABILITY_ENABLED'
    Assert-Cc4cBooleanValue $values 'CC4C_OUTBOX_DISPATCHER_ENABLED'
    Assert-Cc4cBooleanValue $values 'CC4C_MESSAGE_CONSUMERS_ENABLED'
    Assert-Cc4cBooleanValue $values 'CC4C_MAIL_AUTH'
    Assert-Cc4cBooleanValue $values 'CC4C_MAIL_SSL_ENABLED'
    Assert-Cc4cBooleanValue $values 'CC4C_MAIL_STARTTLS_ENABLED'
    if ([string]::IsNullOrWhiteSpace([string] $values.CC4C_SECURITY_PEPPER) -or
        ([string] $values.CC4C_SECURITY_PEPPER).Length -lt 32) {
        throw 'CC4C_SECURITY_PEPPER must contain at least 32 characters.'
    }
    if ([string] $values.CC4C_MANAGEMENT_ADDRESS -cne '127.0.0.1' -or
        [string] $values.CC4C_MANAGEMENT_PORT -cne [string] $ManagementPort) {
        throw "Host mode requires the loopback management endpoint on port $ManagementPort."
    }
    $mailPort = 0
    if (-not [int]::TryParse([string] $values.CC4C_MAIL_PORT, [ref] $mailPort) -or $mailPort -lt 1 -or $mailPort -gt 65535) {
        throw 'CC4C_MAIL_PORT must be between 1 and 65535.'
    }
    return $values
}

function Get-Cc4cDatabaseName {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values)
    if ([string] $Values.CC4C_DB_URL -notmatch '^jdbc:mysql://[^/]+/(?<database>[^?]+)') {
        throw 'CC4C_DB_URL does not contain a database name.'
    }
    return [string] $Matches.database
}

function Set-Cc4cProcessEnvironment {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary] $Values)
    $names = @($Values.Keys + 'SPRING_CONFIG_NAME' + 'SPRING_APPLICATION_NAME')
    $original = [ordered]@{}
    foreach ($name in $names) {
        $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    foreach ($name in $Values.Keys) {
        [Environment]::SetEnvironmentVariable($name, [string] $Values[$name], 'Process')
    }
    [Environment]::SetEnvironmentVariable('SPRING_CONFIG_NAME', 'application-example', 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_APPLICATION_NAME', 'CC4C', 'Process')
    return [pscustomobject]@{ Names = $names; Values = $original }
}

function Restore-Cc4cProcessEnvironment {
    param([Parameter(Mandatory = $true)] $Snapshot)
    foreach ($name in $Snapshot.Names) {
        [Environment]::SetEnvironmentVariable($name, $Snapshot.Values[$name], 'Process')
    }
}

function Get-Cc4cHostStateRoot {
    $path = Join-Path $script:Cc4cHostWorkspaceRoot 'temp\cc4c-host-stack'
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
    }
    return $path
}

function Write-Cc4cHostState {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)] $State
    )
    $path = Join-Path (Get-Cc4cHostStateRoot) "$Name.json"
    $json = $State | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))
    return $path
}

function Read-Cc4cHostState {
    param([Parameter(Mandatory = $true)][string] $Name)
    $path = Join-Path (Get-Cc4cHostStateRoot) "$Name.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }
    return (Get-Content -Raw -LiteralPath $path | ConvertFrom-Json)
}
