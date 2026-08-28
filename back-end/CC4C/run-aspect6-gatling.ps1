[CmdletBinding()]
param(
    [ValidateSet('PublicReadSmoke', 'PublicReadStandard', 'AuthenticatedMixed', 'StepCapacity')]
    [string]$Simulation = 'PublicReadSmoke',
    [ValidateSet('baseline', 'observability-on')]
    [string]$Mode = 'observability-on',
    [ValidateRange(1, 3)]
    [int]$Rounds = 1
)

$ErrorActionPreference = 'Stop'
if ($Simulation -eq 'PublicReadStandard' -and $Rounds -ne 3) {
    throw 'PublicReadStandard must run exactly three rounds.'
}
$environmentPath = Join-Path $PSScriptRoot '.env.performance.local'
$required = @(
    'CC4C_PERF_DB_URL', 'CC4C_PERF_DB_USERNAME', 'CC4C_PERF_DB_PASSWORD',
    'CC4C_PERF_DB_RESET_CONFIRM', 'CC4C_PERF_CACHE_REDIS_URL',
    'CC4C_PERF_BASE_URL', 'CC4C_PERF_USER_PASSWORD',
    'CC4C_PERF_SESSION_NAMESPACE', 'CC4C_PERF_CACHE_NAMESPACE',
    'CC4C_PERF_RABBITMQ_NAMESPACE'
)
if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'Missing .env.performance.local. Copy .env.performance.example and fill it first.'
}
$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -le 0) { throw 'Invalid .env.performance.local entry.' }
    $name = $line.Substring(0, $separator).Trim()
    if ($required -notcontains $name) { throw "Unsupported performance variable '$name'." }
    $values[$name] = $line.Substring($separator + 1)
}
foreach ($name in $required) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required performance variable '$name' is missing or empty."
    }
}

$databaseMatch = [regex]::Match($values.CC4C_PERF_DB_URL,
    '^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $databaseMatch.Success) { throw 'Performance JDBC URL is invalid.' }
$database = $databaseMatch.Groups['database'].Value
if (-not $database.EndsWith('_perf_test') -or $database -cne $values.CC4C_PERF_DB_RESET_CONFIRM) {
    throw 'Performance database must end with _perf_test and exactly match confirmation.'
}
$baseUri = [Uri]$values.CC4C_PERF_BASE_URL
if ($baseUri.Scheme -ne 'http' -or $baseUri.Host -notin @('localhost', '127.0.0.1')) {
    throw 'CC4C_PERF_BASE_URL must use loopback HTTP.'
}
$namespaces = @(
    $values.CC4C_PERF_SESSION_NAMESPACE,
    $values.CC4C_PERF_CACHE_NAMESPACE,
    $values.CC4C_PERF_RABBITMQ_NAMESPACE)
if (($namespaces | Select-Object -Unique).Count -ne 3) {
    throw 'Performance Session, cache, and RabbitMQ namespaces must be distinct.'
}
$javaVersion = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersion -join "`n") -notmatch 'version "21(?:\.|"|-)') {
    throw 'Java 21 is required for the aspect six Gatling profile.'
}
$managementListening = [bool](Get-NetTCPConnection -State Listen -LocalPort 4081 -ErrorAction SilentlyContinue)
$prometheusListening = [bool](Get-NetTCPConnection -State Listen -LocalPort 9090 -ErrorAction SilentlyContinue)
if ($Mode -eq 'baseline' -and $managementListening) {
    throw 'Baseline mode requires the management HTTP port to be disabled.'
}
if ($Mode -eq 'observability-on' -and (-not $managementListening -or -not $prometheusListening)) {
    throw 'Observability-on mode requires ports 4081 and 9090 to be listening.'
}

$original = @{}
foreach ($name in $required) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}
$resultRoot = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) `
    "temp\cc4c-v3-aspect6-gatling\$Simulation\$Mode"
$warmupSeconds = if ($Simulation -eq 'PublicReadStandard') { 120 } else { 0 }
try {
    for ($round = 1; $round -le $Rounds; $round++) {
        $roundDirectory = Join-Path $resultRoot "round-$round"
        New-Item -ItemType Directory -Force -Path $roundDirectory | Out-Null
        Push-Location -LiteralPath $PSScriptRoot
        try {
            if ($Simulation -eq 'PublicReadStandard') {
                $warmupDirectory = Join-Path $roundDirectory 'warmup'
                New-Item -ItemType Directory -Force -Path $warmupDirectory | Out-Null
                & mvn --no-transfer-progress -Paspect6-gatling gatling:test `
                    '-Dgatling.simulationClass=com.cc4c.performance.PublicReadWarmup' `
                    "-Dgatling.resultsFolder=$warmupDirectory"
                if ($LASTEXITCODE -ne 0) { throw "Gatling warm-up for round $round failed." }
            }
            & mvn --no-transfer-progress -Paspect6-gatling gatling:test `
                "-Dgatling.simulationClass=com.cc4c.performance.$Simulation" `
                "-Dgatling.resultsFolder=$roundDirectory"
            if ($LASTEXITCODE -ne 0) { throw "Gatling round $round failed." }
        } finally {
            Pop-Location
        }
        $simulationLog = Get-ChildItem -LiteralPath $roundDirectory -Filter simulation.log -Recurse |
            Where-Object { $_.FullName -notlike "*$([IO.Path]::DirectorySeparatorChar)warmup$([IO.Path]::DirectorySeparatorChar)*" } |
            Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($null -eq $simulationLog) { throw "Gatling round $round did not produce simulation.log." }
        & (Join-Path $PSScriptRoot 'summarize-gatling.ps1') `
            -SimulationLog $simulationLog.FullName `
            -OutputPath (Join-Path $roundDirectory 'summary.json') `
            -WarmupSeconds $warmupSeconds | Out-Host
    }
} finally {
    foreach ($name in $required) {
        [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process')
    }
}
