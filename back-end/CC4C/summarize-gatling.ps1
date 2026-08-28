[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$SimulationLog,
    [Parameter(Mandatory)] [string]$OutputPath,
    [ValidateRange(0, 3600)] [int]$WarmupSeconds = 0
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $SimulationLog -PathType Leaf)) {
    throw 'Gatling simulation.log was not found.'
}

$reportPath = Join-Path ([IO.Path]::GetDirectoryName($SimulationLog)) 'index.html'
if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    throw 'Gatling index.html was not found beside simulation.log.'
}
$html = Get-Content -Raw -LiteralPath $reportPath
$rootMatch = [regex]::Match(
    $html,
    '<tbody><tr id="ROOT"[^>]*>(?<row>.*?)</tr></tbody>',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $rootMatch.Success) { throw 'Gatling global statistics row was not found.' }
$row = $rootMatch.Groups['row'].Value
function Read-Column([int]$Column, [Type]$Type) {
    $match = [regex]::Match(
        $row,
        ('<td class="value [^"]*col-{0}">(?<value>[^<]+)</td>' -f $Column),
        [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $match.Success) { throw "Gatling statistics column $Column was not found." }
    $value = $match.Groups['value'].Value.Trim()
    return [Convert]::ChangeType($value, $Type, [Globalization.CultureInfo]::InvariantCulture)
}
$summary = [ordered]@{
    requests = Read-Column 2 ([long])
    errors = Read-Column 4 ([long])
    warmupSecondsExcluded = $WarmupSeconds
    p50Ms = Read-Column 8 ([long])
    p95Ms = Read-Column 10 ([long])
    p99Ms = Read-Column 11 ([long])
    throughputPerSecond = Read-Column 6 ([double])
}
$directory = Split-Path $OutputPath -Parent
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$summary | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding utf8NoBOM
$summary
