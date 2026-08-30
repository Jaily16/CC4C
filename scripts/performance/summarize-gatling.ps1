# 运行前提：输入必须是用户明确指定的 Gatling 日志，输出路径不得覆盖历史证据。
# 破坏性边界：只读解析性能日志，不修改服务、数据库、卷或原始结果。
# 失败恢复：解析失败保留原始日志并停止，不删除或覆盖任何报告。
# 退出码：汇总成功返回 0，日志无效或门禁失败返回非零码。

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
[System.IO.File]::WriteAllText($OutputPath, ($summary | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
$summary
