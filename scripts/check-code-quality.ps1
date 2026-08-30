# 运行前提：Java、Maven、Node 和前端开发依赖已由调用方准备好；本脚本不安装依赖。
# 破坏性边界：本脚本只执行检查命令，不读取本机秘密、不启动服务、不修改源文件。
# 失败恢复：任一步返回非零码即停止，保留检查输出；调用方应按方面四清单处理回滚。
# 退出码：所有检查成功返回 0，任一步失败返回该检查的非零码。

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$originalLocation = (Get-Location).Path

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
    Invoke-QualityStep -WorkingDirectory $workspaceRoot -Command 'node' -Arguments @('scripts/check-source-quality.mjs')
    exit 0
} catch {
    Write-Error $_
    exit 1
} finally {
    Set-Location -LiteralPath $originalLocation
}
