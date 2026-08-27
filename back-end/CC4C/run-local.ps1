[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ApplicationArguments
)

$runtimeEnvironmentPath = Join-Path $PSScriptRoot '.env.runtime.local'
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
    'CC4C_API_DOCS_ENABLED',
    'CC4C_SAVE_IMG_PATH',
    'CC4C_REQUEST_IMG_PATH',
    'CC4C_SAVE_AVATAR_PATH',
    'CC4C_REQUEST_AVATAR_PATH'
)

if (-not (Test-Path -LiteralPath $runtimeEnvironmentPath -PathType Leaf)) {
    throw "Missing $runtimeEnvironmentPath. Copy .env.runtime.example and fill the local values first."
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
        throw "Invalid entry on line $lineNumber of .env.runtime.local. Expected NAME=value."
    }
    $name = $rawLine.Substring(0, $separator).Trim()
    if ($requiredNames -notcontains $name) {
        throw "Unsupported variable '$name' in .env.runtime.local."
    }
    if ($values.ContainsKey($name)) {
        throw "Duplicate variable '$name' in .env.runtime.local."
    }
    $values[$name] = $rawLine.Substring($separator + 1)
}

foreach ($name in $requiredNames) {
    if (-not $values.ContainsKey($name)) {
        throw "Required variable '$name' is missing from .env.runtime.local."
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
if ($values['CC4C_BUSINESS_CACHE_ENABLED'] -notin @('true', 'false')) {
    throw 'CC4C_BUSINESS_CACHE_ENABLED must be true or false.'
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

$jarPath = Join-Path $PSScriptRoot 'target/CC4C-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Missing application JAR: $jarPath. Run .\run-tests.ps1 clean verify first."
}

$original = @{}
$processNames = $requiredNames + 'SPRING_CONFIG_NAME'
foreach ($name in $processNames) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
foreach ($name in $requiredNames) {
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}
[Environment]::SetEnvironmentVariable('SPRING_CONFIG_NAME', 'application-example', 'Process')

Push-Location -LiteralPath $PSScriptRoot
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
