[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ConfirmProjectName
)

$ErrorActionPreference = 'Stop'
$expectedProject = 'cc4c-v3'
if ($ConfirmProjectName -cne $expectedProject) {
    throw "Refusing to remove volumes. ConfirmProjectName must exactly equal '$expectedProject'."
}

$typed = Read-Host "Type DELETE-$expectedProject to remove only this project's local containers and volumes"
if ($typed -cne "DELETE-$expectedProject") {
    throw 'Confirmation did not match. Nothing was removed.'
}

$workspaceRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location -LiteralPath $workspaceRoot
try {
    & docker compose -p $expectedProject down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose volume reset failed.'
    }
}
finally {
    Pop-Location
}
