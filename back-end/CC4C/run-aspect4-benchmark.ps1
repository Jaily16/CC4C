[CmdletBinding()]
param()

$environmentPath = Join-Path $PSScriptRoot '.env.performance.local'
$requiredNames = @(
    'CC4C_PERF_DB_URL',
    'CC4C_PERF_DB_USERNAME',
    'CC4C_PERF_DB_PASSWORD',
    'CC4C_PERF_DB_RESET_CONFIRM',
    'CC4C_PERF_CACHE_REDIS_URL',
    'CC4C_PERF_USER_PASSWORD'
)
$optionalNames = @(
    'CC4C_PERF_BASE_URL',
    'CC4C_PERF_SESSION_NAMESPACE',
    'CC4C_PERF_CACHE_NAMESPACE',
    'CC4C_PERF_RABBITMQ_NAMESPACE'
)

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Missing .env.performance.local. Copy .env.performance.example and fill it first."
}

$values = @{}
$lineNumber = 0
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }
    $separator = $line.IndexOf('=')
    if ($separator -le 0) {
        throw "Invalid entry on line $lineNumber of .env.performance.local."
    }
    $name = $line.Substring(0, $separator).Trim()
    if ($requiredNames -notcontains $name -and $optionalNames -notcontains $name) {
        throw "Unsupported variable '$name' in .env.performance.local."
    }
    $values[$name] = $line.Substring($separator + 1)
}
foreach ($name in $requiredNames) {
    if ((-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name]))) {
        $processValue = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($processValue)) {
            $values[$name] = $processValue
        }
    }
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required variable '$name' is missing or empty."
    }
}

$match = [regex]::Match(
    $values.CC4C_PERF_DB_URL,
    '^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $match.Success) {
    throw 'CC4C_PERF_DB_URL must be a MySQL JDBC URL with an explicit database name.'
}
$database = $match.Groups['database'].Value
if (-not $database.EndsWith('_perf_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'CC4C_PERF_DB_URL must target a database whose name ends with _perf_test.'
}
if (-not $database.Equals($values.CC4C_PERF_DB_RESET_CONFIRM, [System.StringComparison]::Ordinal)) {
    throw 'CC4C_PERF_DB_RESET_CONFIRM must exactly equal the performance database name.'
}

$javaVersion = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersion -join "`n") -notmatch 'version "21(?:\.|"|-)') {
    throw 'Java 21 is required for the aspect four benchmark.'
}

$original = @{}
foreach ($name in $requiredNames) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & mvn --no-transfer-progress -DskipTests -Paspect4-benchmark verify
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $requiredNames) {
        [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process')
    }
}
exit $exitCode
