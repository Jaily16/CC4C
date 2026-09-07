#requires -Version 7.0
# 运行前提：Java、Maven、Node 和前端开发依赖已由调用方准备好；本脚本不安装依赖。
# 破坏性边界：本脚本只执行检查命令，不读取本机秘密、不启动服务、不修改源文件。
# 失败恢复：任一步返回非零码即停止，保留检查输出；调用方应保留现场并先解决失败原因。
# 退出码：所有检查成功返回 0，任一步失败返回该检查的非零码。

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$originalLocation = (Get-Location).Path

# 仅在指定应用目录执行既定质量命令，任何非零码立即中止并恢复调用目录。
function Invoke-QualityStep {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    Push-Location -LiteralPath $WorkingDirectory
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Quality check failed: $Command $($Arguments -join ' ') (exit code $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
}

try {
    Invoke-QualityStep -WorkingDirectory (Join-Path $workspaceRoot 'backend') -Command 'mvn' -Arguments @('-B', '-ntp', 'spotless:check')
    Invoke-QualityStep -WorkingDirectory (Join-Path $workspaceRoot 'frontend') -Command 'npm' -Arguments @('run', 'lint')
    Invoke-QualityStep -WorkingDirectory (Join-Path $workspaceRoot 'frontend') -Command 'npm' -Arguments @('run', 'format:check')
    Invoke-QualityStep -WorkingDirectory (Join-Path $workspaceRoot 'observability') -Command 'npm' -Arguments @('run', 'lint')
    Invoke-QualityStep -WorkingDirectory (Join-Path $workspaceRoot 'observability') -Command 'npm' -Arguments @('run', 'format:check')
    Invoke-QualityStep -WorkingDirectory $workspaceRoot -Command 'node' -Arguments @('infrastructure/quality/check-source-quality.mjs')
    Invoke-QualityStep -WorkingDirectory $workspaceRoot -Command 'node' -Arguments @('infrastructure/quality/check-doc-links.mjs')
    Invoke-QualityStep -WorkingDirectory $workspaceRoot -Command 'node' -Arguments @('infrastructure/quality/check-observability-contract.mjs')
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
} finally {
    Set-Location -LiteralPath $originalLocation
}
