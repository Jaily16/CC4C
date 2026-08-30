# 运行前提：必须提供用户明确指定、校验过的测试备份；目标数据库必须是隔离测试数据库。
# 破坏性边界：只恢复显式测试目标，不删除 Docker 卷、不触碰生产数据库或上传文件。
# 失败恢复：恢复失败保留原备份、校验结果和输出，禁止覆盖用户新文件或自动改选目标。
# 退出码：恢复成功返回 0，备份校验、连接或导入失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,

    [string] $MySqlBin
)

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$canonicalEnvironmentPath = Join-Path $workspaceRoot 'backend\.env.test.local'
$legacyEnvironmentPath = Join-Path $workspaceRoot 'back-end\CC4C\.env.test.local'
$environmentPath = if (Test-Path -LiteralPath $canonicalEnvironmentPath -PathType Leaf) {
    $canonicalEnvironmentPath
} else {
    $legacyEnvironmentPath
}
if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'Missing the local test environment file.'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    if ($line -match '^(?<name>[A-Z0-9_]+)=(?<value>.*)$') {
        $values[$Matches.name] = $Matches.value
    }
}

foreach ($name in @('CC4C_TEST_DB_URL', 'CC4C_TEST_DB_USERNAME', 'CC4C_TEST_DB_PASSWORD')) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required variable '$name' is missing or empty."
    }
}

$urlMatch = [regex]::Match(
    $values['CC4C_TEST_DB_URL'],
    '^jdbc:mysql://(?<host>[^/:?;]+)(?::(?<port>\d+))?/(?<database>[^?;]+)(?:[?;].*)?$',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $urlMatch.Success) {
    throw 'CC4C_TEST_DB_URL is not a supported MySQL JDBC URL.'
}
if (-not $urlMatch.Groups['database'].Value.Equals('cc4c_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Recovery is only allowed while CC4C_TEST_DB_URL still targets cc4c_test.'
}

$targetDatabase = 'cc4c_recovery_test'
$resolvedBackupPath = (Resolve-Path -LiteralPath $BackupPath -ErrorAction Stop).Path
$allowedBackupDirectory = [System.IO.Path]::GetFullPath(
    (Join-Path $workspaceRoot 'temp'))
if (-not $resolvedBackupPath.StartsWith(
        $allowedBackupDirectory + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase) -or
    -not (@(
        [System.IO.Path]::GetFileName($resolvedBackupPath).StartsWith(
            'cc4c-flyway-test-', [System.StringComparison]::OrdinalIgnoreCase),
        [System.IO.Path]::GetFileName($resolvedBackupPath).StartsWith(
            'cc4c-v3-aspect2-cc4c_test-', [System.StringComparison]::OrdinalIgnoreCase)
    ) -contains $true) -or
    [System.IO.Path]::GetExtension($resolvedBackupPath) -ne '.sql') {
    throw 'Backup must be a verified CC4C test SQL dump under the ignored temp directory.'
}

$hashPath = "$resolvedBackupPath.sha256"
if (-not (Test-Path -LiteralPath $hashPath -PathType Leaf)) {
    throw 'The companion SHA-256 file is missing.'
}
$expectedHash = ((Get-Content -LiteralPath $hashPath -Raw).Trim() -split '\s+')[0]
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedBackupPath).Hash
if (-not $actualHash.Equals($expectedHash, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Backup SHA-256 validation failed.'
}

if ($MySqlBin) {
    $mysql = Join-Path $MySqlBin 'mysql.exe'
} else {
    $mysql = (Get-Command mysql -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $mysql -PathType Leaf)) {
    throw 'mysql.exe is required.'
}

$dumpLines = [System.IO.File]::ReadAllLines($resolvedBackupPath, [System.Text.Encoding]::UTF8)
if ($dumpLines | Where-Object { $_ -match '^(USE\s|CREATE DATABASE|DROP DATABASE)' }) {
    throw 'Backup contains a database-selection or database-level destructive statement.'
}
if ($dumpLines | Where-Object { $_ -match 'flyway_schema_history' }) {
    throw 'Backup unexpectedly contains Flyway history.'
}
$safeLines = $dumpLines | Where-Object {
    $_ -notmatch '^DROP TABLE IF EXISTS' -and
    $_ -notmatch '^LOCK TABLES' -and
    $_ -notmatch '^UNLOCK TABLES'
}
$safeRestorePath = Join-Path $allowedBackupDirectory 'cc4c-flyway-test-recovery-import.sql'
[System.IO.File]::WriteAllLines(
    $safeRestorePath,
    $safeLines,
    [System.Text.UTF8Encoding]::new($false))

$hostName = $urlMatch.Groups['host'].Value
$port = if ($urlMatch.Groups['port'].Success) { $urlMatch.Groups['port'].Value } else { '3306' }
$userArgument = '--user=' + $values['CC4C_TEST_DB_USERNAME']
$baseArguments = @(
    '--protocol=TCP',
    "--host=$hostName",
    "--port=$port",
    $userArgument,
    "--database=$targetDatabase",
    '--default-character-set=utf8mb4',
    '--batch',
    '--skip-column-names'
)

$oldPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    [Environment]::SetEnvironmentVariable(
        'MYSQL_PWD',
        $values['CC4C_TEST_DB_PASSWORD'],
        'Process')

    $tableCount = & $mysql @baseArguments '--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = ''BASE TABLE'';'
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect $targetDatabase. Run infrastructure/database/test-database-recovery-setup.sql as an administrator first."
    }
    if ([long] $tableCount -ne 0) {
        throw "$targetDatabase must be newly created and empty."
    }

    $sourcePath = $safeRestorePath.Replace('\', '/')
    & $mysql @baseArguments "--execute=SOURCE $sourcePath;"
    if ($LASTEXITCODE -ne 0) {
        throw 'Restoring the verified backup into cc4c_recovery_test failed.'
    }

    $validationSql = @"
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE();
SELECT COUNT(*) FROM (
    SELECT comment_id FROM (
        SELECT comment_id FROM course_direct_comment
        UNION ALL SELECT comment_id FROM blog_direct_comment
        UNION ALL SELECT comment_id FROM indirect_comment
    ) associations
    GROUP BY comment_id HAVING COUNT(*) > 1
) duplicate_owners;
SELECT COUNT(*) FROM indirect_comment child
LEFT JOIN comment parent ON parent.comment_id = child.father_id
WHERE parent.comment_id IS NULL;
SELECT COUNT(*) FROM administrator;
SELECT COUNT(*) FROM programming_language;
SELECT COUNT(*) FROM course;
SELECT COUNT(*) FROM course_module;
SELECT COUNT(*) FROM module_course;
"@
    $result = @(& $mysql @baseArguments "--execute=$validationSql")
    if ($LASTEXITCODE -ne 0) {
        throw 'Recovery validation query failed.'
    }
    $counts = @($result | ForEach-Object { [long] $_ })
    $expected = @(16, 21, 0, 0, 4, 4, 62, 9, 62)
    if ($counts.Count -ne $expected.Count) {
        throw 'Recovery validation returned an unexpected result shape.'
    }
    for ($index = 0; $index -lt $expected.Count; $index++) {
        if ($counts[$index] -ne $expected[$index]) {
            throw "Recovery validation failed at check $index."
        }
    }
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPassword, 'Process')
}

Write-Output "RecoveryDatabase=$targetDatabase"
Write-Output "BackupSHA256=$($actualHash.ToLowerInvariant())"
Write-Output 'Tables=16'
Write-Output 'ForeignKeys=21'
Write-Output 'DuplicateCommentOwners=0'
Write-Output 'OrphanReplyParents=0'
