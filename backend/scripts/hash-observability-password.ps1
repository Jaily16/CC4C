#requires -Version 7.0
# 运行前提：生产构建已生成观测密码辅助 JAR，密码文件位于仓库外且只含一行。
# 外部依赖：仅依赖 PowerShell 7、Java 21、观测密码辅助 JAR 和用户提供的仓库外普通密码文件。
# 破坏性边界：只把文件路径临时放入当前进程环境，输出唯一 BCrypt 哈希，不修改密码文件或配置。
# 失败恢复：始终恢复原进程变量；路径、密码规则或 Java 失败时不生成替代值。
# 退出码：哈希成功为 0，任一安全检查或 Java 调用失败为 1。

[CmdletBinding()]
param([Parameter(Mandatory = $true)][string] $PasswordFile)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\infrastructure\host\host-environment.ps1')
$previous = [Environment]::GetEnvironmentVariable('CC4C_OBSERVABILITY_PASSWORD_FILE', 'Process')

try {
    $protectedFile = Assert-Cc4cOrdinaryPath $PasswordFile -OutsideWorkspace
    $backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $jarPath = Assert-Cc4cOrdinaryPath (Join-Path $backendRoot 'target\cc4c-6.0.0-SNAPSHOT-observability-password.jar')
    $javaPath = Assert-Cc4cOrdinaryPath (Get-Command java.exe -ErrorAction Stop).Source
    [Environment]::SetEnvironmentVariable('CC4C_OBSERVABILITY_PASSWORD_FILE', $protectedFile, 'Process')
    & $javaPath -jar $jarPath
    if ($LASTEXITCODE -ne 0) { throw 'Observability password hashing failed.' }
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
} finally {
    [Environment]::SetEnvironmentVariable('CC4C_OBSERVABILITY_PASSWORD_FILE', $previous, 'Process')
}
