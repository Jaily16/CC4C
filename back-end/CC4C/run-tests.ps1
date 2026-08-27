[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)

$testEnvironmentPath = Join-Path $PSScriptRoot '.env.test.local'
$requiredTestVariableNames = @(
    'CC4C_TEST_DB_URL',
    'CC4C_TEST_EMPTY_DB_URL',
    'CC4C_TEST_DB_USERNAME',
    'CC4C_TEST_DB_PASSWORD',
    'CC4C_TEST_REDIS_URL',
    'CC4C_TEST_CACHE_REDIS_URL'
)

if (-not (Test-Path -LiteralPath $testEnvironmentPath -PathType Leaf)) {
    throw "Missing local test environment file: $testEnvironmentPath. Copy .env.test.example to .env.test.local first."
}

$testEnvironmentValues = @{}
$testEnvironmentLineNumber = 0
foreach ($testEnvironmentLine in Get-Content -LiteralPath $testEnvironmentPath) {
    $testEnvironmentLineNumber++
    if ([string]::IsNullOrWhiteSpace($testEnvironmentLine) -or $testEnvironmentLine.TrimStart().StartsWith('#')) {
        continue
    }

    $testEnvironmentSeparatorIndex = $testEnvironmentLine.IndexOf('=')
    if ($testEnvironmentSeparatorIndex -le 0) {
        throw "Invalid entry on line $testEnvironmentLineNumber of .env.test.local. Expected NAME=value."
    }

    $testEnvironmentVariableName = $testEnvironmentLine.Substring(0, $testEnvironmentSeparatorIndex).Trim()
    if ($requiredTestVariableNames -notcontains $testEnvironmentVariableName) {
        throw "Unsupported variable '$testEnvironmentVariableName' in .env.test.local."
    }

    $testEnvironmentValues[$testEnvironmentVariableName] = $testEnvironmentLine.Substring($testEnvironmentSeparatorIndex + 1)
}

foreach ($requiredTestVariableName in $requiredTestVariableNames) {
    if (-not $testEnvironmentValues.ContainsKey($requiredTestVariableName) -or
        [string]::IsNullOrWhiteSpace($testEnvironmentValues[$requiredTestVariableName])) {
        throw "Required variable '$requiredTestVariableName' is missing or empty in .env.test.local."
    }
}

function Get-DatabaseNameFromJdbcUrl([string] $jdbcUrl, [string] $variableName) {
    $match = [regex]::Match(
        $jdbcUrl,
        '^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $match.Success) {
        throw "Variable '$variableName' must be a MySQL JDBC URL with an explicit database name."
    }
    return $match.Groups['database'].Value
}

$mainTestDatabaseName = Get-DatabaseNameFromJdbcUrl $testEnvironmentValues['CC4C_TEST_DB_URL'] 'CC4C_TEST_DB_URL'
$emptyTestDatabaseName = Get-DatabaseNameFromJdbcUrl $testEnvironmentValues['CC4C_TEST_EMPTY_DB_URL'] 'CC4C_TEST_EMPTY_DB_URL'

if (-not $mainTestDatabaseName.EndsWith('_test', [System.StringComparison]::OrdinalIgnoreCase) -or
    $mainTestDatabaseName.EndsWith('_flyway_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "CC4C_TEST_DB_URL must target a dedicated database whose name ends with '_test' but not '_flyway_test'."
}
if (-not $emptyTestDatabaseName.EndsWith('_flyway_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "CC4C_TEST_EMPTY_DB_URL must target a dedicated database whose name ends with '_flyway_test'."
}
if ($mainTestDatabaseName.Equals($emptyTestDatabaseName, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "CC4C_TEST_DB_URL and CC4C_TEST_EMPTY_DB_URL must target different databases."
}

if ($MavenArguments.Count -eq 0) {
    $MavenArguments = @('test')
}

$testEnvironmentValues['CC4C_TEST_REDIS_NAMESPACE'] =
    'cc4c:test:' + [Guid]::NewGuid().ToString('N')
$testEnvironmentValues['CC4C_TEST_CACHE_REDIS_NAMESPACE'] =
    $testEnvironmentValues['CC4C_TEST_REDIS_NAMESPACE'] + ':cache'
$requiredTestVariableNames += @(
    'CC4C_TEST_REDIS_NAMESPACE',
    'CC4C_TEST_CACHE_REDIS_NAMESPACE'
)

$javaVersionOutput = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersionOutput -join "`n") -notmatch 'version "21(?:\.|"|-)') {
    throw "Java 21 is required. Set JAVA_HOME and place its bin directory first on PATH for this process."
}

$originalProcessEnvironment = @{}
foreach ($requiredTestVariableName in $requiredTestVariableNames) {
    $originalProcessEnvironment[$requiredTestVariableName] =
        [Environment]::GetEnvironmentVariable($requiredTestVariableName, 'Process')
    [Environment]::SetEnvironmentVariable(
        $requiredTestVariableName,
        $testEnvironmentValues[$requiredTestVariableName],
        'Process')
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $lifecycleGoalsThatRunTests = @('test', 'package', 'verify', 'install')
    $runsTests = $false
    foreach ($argument in $MavenArguments) {
        if ($lifecycleGoalsThatRunTests -contains $argument.ToLowerInvariant()) {
            $runsTests = $true
            break
        }
    }
    $skipsTests = $MavenArguments -contains '-DskipTests' -or
        $MavenArguments -contains '-Dmaven.test.skip=true'

    if ($runsTests -and -not $skipsTests) {
        $migrationGateArguments = @(
            '-Dtest=AExistingDatabaseMigrationTest,ZEmptyDatabaseMigrationTest',
            'test'
        )
        if ($MavenArguments -contains '-q' -or $MavenArguments -contains '--quiet') {
            $migrationGateArguments = @('-q') + $migrationGateArguments
        }
        if ($MavenArguments -contains '--no-transfer-progress') {
            $migrationGateArguments = @('--no-transfer-progress') + $migrationGateArguments
        }

        & mvn @migrationGateArguments
        $mavenExitCode = $LASTEXITCODE
    } else {
        $mavenExitCode = 0
    }

    if ($mavenExitCode -eq 0) {
        & mvn @MavenArguments
        $mavenExitCode = $LASTEXITCODE
    }
}
finally {
    Pop-Location
    foreach ($requiredTestVariableName in $requiredTestVariableNames) {
        [Environment]::SetEnvironmentVariable(
            $requiredTestVariableName,
            $originalProcessEnvironment[$requiredTestVariableName],
            'Process')
    }
}

exit $mavenExitCode
