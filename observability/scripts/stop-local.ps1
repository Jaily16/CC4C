[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-v3-aspect6-observability'

function Stop-RecordedProcess([string]$Name, [string]$ExpectedExecutable) {
    $pidPath = Join-Path $runDirectory "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidPath -PathType Leaf)) { return }
    $recordedId = [int](Get-Content -Raw -LiteralPath $pidPath).Trim()
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $recordedId" -ErrorAction SilentlyContinue
    if ($null -eq $process) { return }
    $actual = [System.IO.Path]::GetFileName($process.ExecutablePath)
    if (-not $actual.Equals($ExpectedExecutable, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "PID $recordedId no longer belongs to $ExpectedExecutable; refusing to stop it."
    }
    Stop-Process -Id $recordedId -ErrorAction Stop
}

Stop-RecordedProcess 'prometheus' 'prometheus.exe'
Stop-RecordedProcess 'grafana' 'grafana.exe'
Write-Host 'Recorded Prometheus and Grafana processes were stopped when present.'
