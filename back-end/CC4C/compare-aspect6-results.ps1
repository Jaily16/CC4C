[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$BaselineDirectory,
    [Parameter(Mandatory)] [string]$ObservedDirectory
)

$ErrorActionPreference = 'Stop'
function Read-Medians([string]$Directory) {
    $summaries = Get-ChildItem -LiteralPath $Directory -Filter summary.json -Recurse |
        ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json }
    if ($summaries.Count -ne 3) { throw "Exactly three summaries are required under $Directory." }
    function Median([object[]]$values) { return ($values | Sort-Object)[1] }
    return [pscustomobject]@{
        errors = Median @($summaries.errors)
        p95Ms = Median @($summaries.p95Ms)
        p99Ms = Median @($summaries.p99Ms)
        throughputPerSecond = Median @($summaries.throughputPerSecond)
    }
}

$baseline = Read-Medians $BaselineDirectory
$observed = Read-Medians $ObservedDirectory
$failures = [System.Collections.Generic.List[string]]::new()
if ($baseline.errors -ne 0) { $failures.Add('Baseline HTTP errors must be zero.') }
if ($observed.errors -ne 0) { $failures.Add('Observed HTTP errors must be zero.') }
if ($observed.p95Ms -gt $baseline.p95Ms * 1.10) { $failures.Add('Observed p95 regression exceeds 10%.') }
if ($observed.p99Ms -gt $baseline.p99Ms * 1.15) { $failures.Add('Observed p99 regression exceeds 15%.') }
if ($observed.throughputPerSecond -lt $baseline.throughputPerSecond * 0.90) {
    $failures.Add('Observed throughput regression exceeds 10%.')
}
[pscustomobject]@{ baseline = $baseline; observed = $observed; passed = $failures.Count -eq 0 }
if ($failures.Count -gt 0) { throw ($failures -join ' ') }
