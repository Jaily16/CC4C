# 运行前提：必须显式确认项目名为 cc4c，并确认当前操作窗口允许重置本地开发资源。
# 破坏性边界：只操作确认的本地开发目标；禁止将参数扩展为任意项目、卷、数据库或上传目录。
# 失败恢复：任何前置检查失败立即停止并保留现有资源；执行失败时按脚本输出逐项恢复配置。
# 退出码：成功返回 0，确认、服务或资源操作失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ConfirmProjectName
)

$ErrorActionPreference = 'Stop'
$expectedProject = 'cc4c'
if ($ConfirmProjectName -cne $expectedProject) {
    throw "Refusing to remove volumes. ConfirmProjectName must exactly equal '$expectedProject'."
}

$typed = Read-Host "Type DELETE-$expectedProject to remove only this project's local containers and volumes"
if ($typed -cne "DELETE-$expectedProject") {
    throw 'Confirmation did not match. Nothing was removed.'
}

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location -LiteralPath $workspaceRoot
try {
    & docker compose -p $expectedProject down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose volume reset failed.'
    }
}
finally {
    Pop-Location
}
