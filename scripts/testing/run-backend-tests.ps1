# 运行前提：Java、Maven、Docker/Testcontainers 已由调用方准备好；测试使用隔离资源。
# 破坏性边界：不读取本机 application.yml/env/secret，不操作现有 Compose 项目、卷、数据库或上传文件。
# 失败恢复：任一测试失败立即透传并保留报告；隔离容器由测试框架回收，不执行广泛清理。
# 退出码：透传 Maven 测试退出码；成功为 0，编译或测试失败为非零码。

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $workspaceRoot 'backend'

$javaVersionOutput = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersionOutput -join "`n") -notmatch 'version "21(?:\.|"|-)' ) {
    throw 'Java 21 is required. Set JAVA_HOME and place its bin directory first on PATH.'
}

$dockerServerVersion = & docker version --format '{{.Server.Version}}' 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($dockerServerVersion)) {
    throw 'A running Docker Engine is required for the isolated Testcontainers test suite.'
}

if ($MavenArguments.Count -eq 0) {
    $MavenArguments = @('--no-transfer-progress', 'clean', 'verify')
}

$previousReuseSetting = [Environment]::GetEnvironmentVariable(
    'TESTCONTAINERS_REUSE_ENABLE',
    'Process')
[Environment]::SetEnvironmentVariable(
    'TESTCONTAINERS_REUSE_ENABLE',
    'false',
    'Process')

Push-Location -LiteralPath $backendRoot
try {
    & mvn @MavenArguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
    [Environment]::SetEnvironmentVariable(
        'TESTCONTAINERS_REUSE_ENABLE',
        $previousReuseSetting,
        'Process')
}
