[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)

$javaVersionOutput = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersionOutput -join "`n") -notmatch 'version "21(?:\.|"|-)' ) {
    throw 'Java 21 is required. Set JAVA_HOME and place its bin directory first on PATH.'
}

$dockerServerVersion = & docker version --format '{{.Server.Version}}' 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($dockerServerVersion)) {
    throw 'A running Docker Engine is required for the isolated Testcontainers test suite.'
}

if ($MavenArguments.Count -eq 0) {
    $MavenArguments = @('--no-transfer-progress', 'clean', 'verify')
}

$previousReuseSetting = [Environment]::GetEnvironmentVariable(
    'TESTCONTAINERS_REUSE_ENABLE',
    'Process')
[Environment]::SetEnvironmentVariable(
    'TESTCONTAINERS_REUSE_ENABLE',
    'false',
    'Process')

Push-Location -LiteralPath $PSScriptRoot
try {
    & mvn @MavenArguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
    [Environment]::SetEnvironmentVariable(
        'TESTCONTAINERS_REUSE_ENABLE',
        $previousReuseSetting,
        'Process')
}
