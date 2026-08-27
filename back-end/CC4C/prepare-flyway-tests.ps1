[CmdletBinding()]
param(
    [string] $MySqlBin
)

$environmentPath = Join-Path $PSScriptRoot '.env.test.local'
if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Missing .env.test.local."
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentPath) {
    if ($line -match '^(?<name>[A-Z0-9_]+)=(?<value>.*)$') {
        $values[$Matches.name] = $Matches.value
    }
}

$required = @(
    'CC4C_TEST_DB_URL',
    'CC4C_TEST_EMPTY_DB_URL',
    'CC4C_TEST_DB_USERNAME',
    'CC4C_TEST_DB_PASSWORD'
)
foreach ($name in $required) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required variable '$name' is missing or empty."
    }
}

function Parse-JdbcUrl([string] $url) {
    $match = [regex]::Match(
        $url,
        '^jdbc:mysql://(?<host>[^/:?;]+)(?::(?<port>\d+))?/(?<database>[^?;]+)(?:[?;].*)?$',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $match.Success) {
        throw "Test database URL is not a supported MySQL JDBC URL."
    }
    return @{
        Host = $match.Groups['host'].Value
        Port = if ($match.Groups['port'].Success) { $match.Groups['port'].Value } else { '3306' }
        Database = $match.Groups['database'].Value
    }
}

$main = Parse-JdbcUrl $values['CC4C_TEST_DB_URL']
$empty = Parse-JdbcUrl $values['CC4C_TEST_EMPTY_DB_URL']
if (-not $main.Database.EndsWith('_test', [System.StringComparison]::OrdinalIgnoreCase) -or
    $main.Database.EndsWith('_flyway_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Main test database name must end with '_test' but not '_flyway_test'."
}
if (-not $empty.Database.EndsWith('_flyway_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Empty migration database name must end with '_flyway_test'."
}
if ($main.Database.Equals($empty.Database, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Main and empty migration databases must be different."
}

if ($MySqlBin) {
    $mysql = Join-Path $MySqlBin 'mysql.exe'
    $mysqldump = Join-Path $MySqlBin 'mysqldump.exe'
} else {
    $mysql = (Get-Command mysql -ErrorAction Stop).Source
    $mysqldump = (Get-Command mysqldump -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $mysql -PathType Leaf) -or
    -not (Test-Path -LiteralPath $mysqldump -PathType Leaf)) {
    throw "mysql.exe and mysqldump.exe are required."
}

$backupDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) '..\temp'
$backupDirectory = [System.IO.Path]::GetFullPath($backupDirectory)
[System.IO.Directory]::CreateDirectory($backupDirectory) | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupPath = Join-Path $backupDirectory "cc4c-v3-aspect2-$($main.Database)-$timestamp.sql"
$hashPath = "$backupPath.sha256"

$oldPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $values['CC4C_TEST_DB_PASSWORD'], 'Process')
    $userArgument = '--user=' + $values['CC4C_TEST_DB_USERNAME']
    $dumpArguments = @(
        '--single-transaction',
        '--skip-lock-tables',
        '--no-tablespaces',
        "--host=$($main.Host)",
        "--port=$($main.Port)",
        $userArgument,
        "--result-file=$backupPath",
        $main.Database
    )
    & $mysqldump @dumpArguments
    if ($LASTEXITCODE -ne 0) {
        throw "mysqldump failed."
    }

    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $backupPath).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText($hashPath, "$hash  $([System.IO.Path]::GetFileName($backupPath))")

    $preflightSql = @"
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';
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
"@
    $mysqlArguments = @(
        '--protocol=TCP',
        "--host=$($main.Host)",
        "--port=$($main.Port)",
        $userArgument,
        "--database=$($main.Database)",
        '--batch',
        '--skip-column-names',
        "--execute=$preflightSql"
    )
    $result = & $mysql @mysqlArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Database preflight failed."
    }
    $counts = @($result | ForEach-Object { [long] $_ })
    if ($counts.Count -ne 4 -or $counts[0] -ne 16 -or $counts[1] -lt 1 -or
        $counts[2] -ne 0 -or $counts[3] -ne 0) {
        throw "Database preflight failed: expected 16 tables, existing foreign keys, no duplicate comment ownership, and no orphan reply parents."
    }
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPassword, 'Process')
}

Write-Output "Backup=$backupPath"
Write-Output "SHA256=$hash"
Write-Output "PreflightTables=$($counts[0])"
Write-Output "PreflightForeignKeys=$($counts[1])"
Write-Output "DuplicateCommentOwners=$($counts[2])"
Write-Output "OrphanReplyParents=$($counts[3])"
