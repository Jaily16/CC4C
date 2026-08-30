# 运行前提：基线和观测目录必须是用户明确指定的性能证据目录。
# 破坏性边界：只读比较并输出差异，不修改输入报告、数据库、服务或临时历史证据。
# 失败恢复：比较失败时保留原始目录和输出，修复参数后可重新执行。
# 退出码：比较完成返回 0，输入无效或门禁不通过返回非零码。

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
