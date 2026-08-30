# 运行前提：必须使用隔离测试数据库和用户明确提供的非敏感性能配置。
# 破坏性边界：只生成性能基准证据，不修改 Flyway 迁移、生产数据库、上传文件或历史报告。
# 失败恢复：失败时保留输出和退出码，不覆盖既有性能证据；恢复由调用方按备份清单执行。
# 退出码：基准通过返回 0，服务、请求或阈值失败返回非零码。

[CmdletBinding()]
param()

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$canonicalEnvironmentPath = Join-Path $workspaceRoot 'backend\.env.performance.local'
$legacyEnvironmentPath = Join-Path $workspaceRoot 'back-end\CC4C\.env.performance.local'
$environmentPath = if (Test-Path -LiteralPath $canonicalEnvironmentPath -PathType Leaf) {
    $canonicalEnvironmentPath
} else {
    $legacyEnvironmentPath
}
$requiredNames = @(
    'CC4C_PERF_DB_URL',
    'CC4C_PERF_DB_USERNAME',
    'CC4C_PERF_DB_PASSWORD',
    'CC4C_PERF_DB_RESET_CONFIRM',
    'CC4C_PERF_CACHE_REDIS_URL',
    'CC4C_PERF_USER_PASSWORD'
)
$optionalNames = @(
    'CC4C_PERF_BASE_URL',
    'CC4C_PERF_SESSION_NAMESPACE',
    'CC4C_PERF_CACHE_NAMESPACE',
    'CC4C_PERF_RABBITMQ_NAMESPACE'
)

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'Missing the local performance environment file. Copy .env.performance.example and fill it first.'
}

$values = @{}
$lineNumber = 0
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
        continue
    }
    $separator = $line.IndexOf('=')
    if ($separator -le 0) {
        throw "Invalid entry on line $lineNumber of .env.performance.local."
    }
    $name = $line.Substring(0, $separator).Trim()
    if ($requiredNames -notcontains $name -and $optionalNames -notcontains $name) {
        throw "Unsupported variable '$name' in .env.performance.local."
    }
    $values[$name] = $line.Substring($separator + 1)
}
foreach ($name in $requiredNames) {
    if ((-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name]))) {
        $processValue = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($processValue)) {
            $values[$name] = $processValue
        }
    }
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required variable '$name' is missing or empty."
    }
}

$match = [regex]::Match(
    $values.CC4C_PERF_DB_URL,
    '^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $match.Success) {
    throw 'CC4C_PERF_DB_URL must be a MySQL JDBC URL with an explicit database name.'
}
$database = $match.Groups['database'].Value
if (-not $database.EndsWith('_perf_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'CC4C_PERF_DB_URL must target a database whose name ends with _perf_test.'
}
if (-not $database.Equals($values.CC4C_PERF_DB_RESET_CONFIRM, [System.StringComparison]::Ordinal)) {
    throw 'CC4C_PERF_DB_RESET_CONFIRM must exactly equal the performance database name.'
}

$javaVersion = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaVersion -join "`n") -notmatch 'version "21(?:\.|"|-)') {
    throw 'Java 21 is required for the performance benchmark.'
}

$original = @{}
foreach ($name in $requiredNames) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}

Push-Location -LiteralPath (Join-Path $workspaceRoot 'backend')
try {
    & mvn --no-transfer-progress -DskipTests -Pperformance-benchmark verify
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $requiredNames) {
        [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process')
    }
}
exit $exitCode
