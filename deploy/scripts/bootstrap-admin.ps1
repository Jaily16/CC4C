[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d{7}$')]
    [string] $AdminId
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$passwordFile = Join-Path $workspaceRoot 'deploy\secrets\local\admin_bootstrap_password'
if (-not (Test-Path -LiteralPath $passwordFile -PathType Leaf)) {
    throw 'Run deploy/scripts/prepare-local.ps1 before bootstrapping an administrator.'
}

$previousId = $env:CC4C_ADMIN_BOOTSTRAP_ID
try {
    $env:CC4C_ADMIN_BOOTSTRAP_ID = $AdminId
    Push-Location -LiteralPath $workspaceRoot
    try {
        & docker compose -p cc4c-v3 --profile bootstrap run --rm admin-bootstrap
        if ($LASTEXITCODE -ne 0) {
            throw 'Administrator bootstrap failed.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    [Environment]::SetEnvironmentVariable(
        'CC4C_ADMIN_BOOTSTRAP_ID',
        $previousId,
        'Process')
}
