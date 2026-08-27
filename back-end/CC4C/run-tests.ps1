[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)

$testEnvironmentPath = Join-Path $PSScriptRoot '.env.test.local'
$requiredTestVariableNames = @(
    'CC4C_TEST_DB_URL',
    'CC4C_TEST_DB_USERNAME',
    'CC4C_TEST_DB_PASSWORD'
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

if ($MavenArguments.Count -eq 0) {
    $MavenArguments = @('test')
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
    & mvn @MavenArguments
    $mavenExitCode = $LASTEXITCODE
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
