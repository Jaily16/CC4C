#requires -Version 7.0
# 运行前提：用户明确要求创建首个管理员，Java 21、引导 JAR、backend/.env.local 和仓库外密码文件已准备。
# 外部依赖：依赖共享环境加载器、Java 21、管理员引导 JAR，以及配置中已就绪的专用 MySQL。
# 破坏性边界：只在精确确认的数据库内执行管理员引导，不创建数据库、不生成密码文件、不管理服务。
# 失败恢复：路径、身份或业务失败即停止，finally 恢复全部临时变量；不打印配置和密码。
# 退出码：引导成功为 0，否则为 1；已有管理员的环境不应重复运行本入口。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^\d{7}$')][string] $AdminId,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_]+$')][string] $ConfirmDatabase,
    [Parameter(Mandatory = $true)][string] $PasswordFile
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\infrastructure\host\host-environment.ps1')
$snapshot = $null

try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $passwordPath = Assert-Cc4cOrdinaryPath $PasswordFile -OutsideWorkspace
    $values = Assert-Cc4cRuntimeEnvironment (Read-Cc4cEnvironmentFile -Kind Runtime)
    if ((Get-Cc4cDatabaseName $values) -cne $ConfirmDatabase) { throw 'ConfirmDatabase must exactly match the configured database.' }
    & (Join-Path $workspaceRoot 'infrastructure\host\host-preflight.ps1') -Component MySQL -ConfirmDatabase $ConfirmDatabase
    if ($LASTEXITCODE -ne 0) { throw 'MySQL preflight failed.' }
    $jarPath = Assert-Cc4cOrdinaryPath (Join-Path $workspaceRoot 'backend\target\cc4c-6.0.0-SNAPSHOT-admin-bootstrap.jar')
    $javaPath = Assert-Cc4cOrdinaryPath (Get-Command java.exe -ErrorAction Stop).Source
    $javaVersion = @(& $javaPath -version 2>&1)
    if ($LASTEXITCODE -ne 0 -or ($javaVersion -join ' ') -notmatch 'version "21(?:\.|"|-)') { throw 'Java 21 is required.' }
    $values.CC4C_ADMIN_BOOTSTRAP_ID = $AdminId
    $values.CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE = $ConfirmDatabase
    $values.CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE = $passwordPath
    $snapshot = Set-Cc4cProcessEnvironment $values -Backend
    Push-Location -LiteralPath (Join-Path $workspaceRoot 'backend')
    try {
        & $javaPath -jar $jarPath
        if ($LASTEXITCODE -ne 0) { throw 'Administrator bootstrap failed; preserve the database and password file.' }
    }
    finally { Pop-Location }
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
finally { if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot } }
