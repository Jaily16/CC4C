[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-v3-aspect6-performance-server'
$pidPath = Join-Path $runDirectory 'server.pid'
if (-not (Test-Path -LiteralPath $pidPath -PathType Leaf)) {
    Write-Host 'No recorded CC4C performance server PID was found.'
    exit 0
}
$recordedId = [int](Get-Content -Raw -LiteralPath $pidPath).Trim()
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $recordedId" -ErrorAction SilentlyContinue
if ($null -eq $process) {
    Write-Host 'The recorded CC4C performance server is no longer running.'
    exit 0
}
$actualExecutable = [System.IO.Path]::GetFileName($process.ExecutablePath)
if (-not $actualExecutable.Equals('java.exe', [System.StringComparison]::OrdinalIgnoreCase) -or
        $process.CommandLine -notmatch 'CC4C-0\.0\.1-SNAPSHOT\.jar') {
    throw "PID $recordedId is not the recorded CC4C JAR process; refusing to stop it."
}
Stop-Process -Id $recordedId -ErrorAction Stop
$deadline = (Get-Date).AddSeconds(30)
do {
    if (-not (Get-Process -Id $recordedId -ErrorAction SilentlyContinue)) {
        break
    }
    Start-Sleep -Milliseconds 250
} while ((Get-Date) -lt $deadline)
if (Get-Process -Id $recordedId -ErrorAction SilentlyContinue) {
    throw 'The recorded CC4C performance server did not stop within 30 seconds.'
}
Write-Host 'The recorded CC4C performance server was stopped.'
