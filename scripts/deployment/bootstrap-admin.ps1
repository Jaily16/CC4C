# 运行前提：仅在隔离的 CC4C 环境中使用，并提供明确的管理员标识。
# 破坏性边界：只创建或更新指定管理员，不删除数据库、卷、上传文件或本机秘密。
# 失败恢复：失败时保留服务输出，由调用方检查管理员状态后重试；不自动回滚其他数据。
# 退出码：成功返回 0，参数、连接或业务失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d{7}$')]
    [string] $AdminId
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$passwordFile = Join-Path $workspaceRoot 'deploy\secrets\local\admin_bootstrap_password'
if (-not (Test-Path -LiteralPath $passwordFile -PathType Leaf)) {
    throw 'Run scripts/deployment/prepare-local.ps1 before bootstrapping an administrator.'
}

$previousId = $env:CC4C_ADMIN_BOOTSTRAP_ID
try {
    $env:CC4C_ADMIN_BOOTSTRAP_ID = $AdminId
    Push-Location -LiteralPath $workspaceRoot
    try {
        & docker compose -p cc4c --profile bootstrap run --rm admin-bootstrap
        if ($LASTEXITCODE -ne 0) {
            throw 'Administrator bootstrap failed.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    [Environment]::SetEnvironmentVariable(
        'CC4C_ADMIN_BOOTSTRAP_ID',
        $previousId,
        'Process')
}
