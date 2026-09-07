#requires -Version 7.0
# 运行前提：用户已提供 promtool 绝对路径，外部 Prometheus 已启动；脚本不加载任何 .env.local。
# 破坏性边界：仅校验仓库公开模板和规则，并只读查询就绪、版本及可选的 backend up 指标。
# 失败恢复：失败即停止，不读取外部私有配置，不启动、停止、重载 Prometheus 或访问其数据目录。
# 退出码：所选静态与只读检查全部通过为 0，否则为 1。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $PromtoolPath,
    [string] $PrometheusUrl = 'http://127.0.0.1:9090',
    [switch] $RequireBackendScrape
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\host\host-environment.ps1')

try {
    $tool = Assert-Cc4cOrdinaryPath $PromtoolPath
    $template = Assert-Cc4cOrdinaryPath (Join-Path $PSScriptRoot 'prometheus.yml.template')
    $rules = Assert-Cc4cOrdinaryPath (Join-Path $PSScriptRoot 'rules\cc4c-alerts.yml')
    Push-Location -LiteralPath $PSScriptRoot
    try {
        & $tool check config $template
        if ($LASTEXITCODE -ne 0) { throw 'The public Prometheus template did not pass configuration validation.' }
        & $tool check rules $rules
        if ($LASTEXITCODE -ne 0) { throw 'The retained Prometheus rules did not pass syntax validation.' }
    }
    finally { Pop-Location }
    Assert-Cc4cPrometheusEndpoint -BaseUrl $PrometheusUrl -RequireBackendScrape:$RequireBackendScrape
    Write-Output 'Public template/rules and the selected read-only Prometheus checks passed. The external private configuration was not inspected.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
