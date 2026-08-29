[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$secretDirectory = Join-Path $workspaceRoot 'deploy\secrets\local'
New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null

function New-RandomBytes([int]$Length) {
    $bytes = New-Object byte[] $Length
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return $bytes
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Write-NewSecret([string]$Name, [string]$Value) {
    $path = Join-Path $secretDirectory $Name
    if (Test-Path -LiteralPath $path) {
        return
    }
    [System.IO.File]::WriteAllText(
        $path,
        $Value,
        [System.Text.UTF8Encoding]::new($false))
}

foreach ($name in @(
        'mysql_root_password',
        'mysql_app_password',
        'redis_security_password',
        'redis_cache_password',
        'rabbit_bootstrap_password',
        'rabbit_app_password',
        'rabbit_monitor_password',
        'management_password',
        'grafana_admin_password',
        'admin_bootstrap_password')) {
    Write-NewSecret $name (ConvertTo-Base64Url (New-RandomBytes 32))
}

Write-NewSecret 'rabbit_erlang_cookie' (ConvertTo-Base64Url (New-RandomBytes 48))
Write-NewSecret 'security_pepper' (ConvertTo-Base64Url (New-RandomBytes 48))
Write-NewSecret 'messaging_payload_key' ([Convert]::ToBase64String((New-RandomBytes 32)))

Write-Host 'CC4C local secret files are ready. Existing files were preserved.'
Write-Host 'The generated administrator password is stored in deploy/secrets/local/admin_bootstrap_password.'
