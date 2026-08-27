[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Before', 'After')]
    [string] $Phase,
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
foreach ($name in @('CC4C_TEST_DB_URL', 'CC4C_TEST_DB_USERNAME', 'CC4C_TEST_DB_PASSWORD')) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Required variable '$name' is missing or empty."
    }
}

$urlMatch = [regex]::Match(
    $values.CC4C_TEST_DB_URL,
    '^jdbc:mysql://(?<host>[^/:?;]+)(?::(?<port>\d+))?/(?<database>[^?;]+)(?:[?;].*)?$',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $urlMatch.Success) {
    throw "CC4C_TEST_DB_URL is not a supported MySQL JDBC URL."
}
$database = $urlMatch.Groups['database'].Value
if (-not $database.EndsWith('_test', [System.StringComparison]::OrdinalIgnoreCase) -or
    $database.EndsWith('_flyway_test', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Query plans may only be captured from the main dedicated *_test database."
}
$hostName = $urlMatch.Groups['host'].Value
$port = if ($urlMatch.Groups['port'].Success) { $urlMatch.Groups['port'].Value } else { '3306' }
$mysql = if ($MySqlBin) {
    Join-Path $MySqlBin 'mysql.exe'
} else {
    (Get-Command mysql -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $mysql -PathType Leaf)) {
    throw "mysql.exe is required."
}

$outputDirectory = [System.IO.Path]::GetFullPath(
    (Join-Path (Split-Path $PSScriptRoot -Parent) '..\temp'))
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$outputPath = Join-Path $outputDirectory "cc4c-v3-aspect2-explain-$($Phase.ToLowerInvariant()).txt"
$queries = [ordered]@{
    course_home = @'
SELECT c.course_id, c.course_name, c.language_name, COUNT(ufc.user_id) AS favors_num
FROM course c
LEFT JOIN user_favors_course ufc ON ufc.course_id = c.course_id
WHERE c.deleted = 0
GROUP BY c.course_id, c.course_name, c.language_name
ORDER BY favors_num DESC, c.course_id ASC
LIMIT 20
'@
    blog_by_time = @'
SELECT b.* FROM blog b
WHERE b.state = 1 AND b.deleted = 0
ORDER BY b.publish_time DESC, b.blog_id DESC
LIMIT 20
'@
    blog_by_click = @'
SELECT b.* FROM blog b
WHERE b.state = 1 AND b.deleted = 0
ORDER BY b.click DESC, b.blog_id DESC
LIMIT 20
'@
    comment_top_level = @'
SELECT c.comment_id, c.user_id, c.content, c.time, c.`like`
FROM course_direct_comment cdc
JOIN comment c ON c.comment_id = cdc.comment_id
JOIN user u ON u.user_id = c.user_id
WHERE cdc.course_id = 1 AND c.deleted = 0 AND u.deleted = 0
ORDER BY c.time DESC, c.comment_id DESC
LIMIT 10
'@
    comment_replies = @'
SELECT c.comment_id, ic.father_id, ic.layer
FROM indirect_comment ic
JOIN comment c ON c.comment_id = ic.comment_id
WHERE ic.father_id IN (1) AND c.deleted = 0
ORDER BY c.time ASC, c.comment_id ASC
'@
}

$oldPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $values.CC4C_TEST_DB_PASSWORD, 'Process')
    $commonArguments = @(
        '--protocol=TCP',
        "--host=$hostName",
        "--port=$port",
        "--user=$($values.CC4C_TEST_DB_USERNAME)",
        "--database=$database",
        '--batch',
        '--raw',
        '--skip-column-names'
    )
    $indexCountSql = @"
SELECT COUNT(*) FROM (
    SELECT DISTINCT table_name, index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND index_name IN ('idx_blog_state_time', 'idx_blog_state_click', 'idx_indirect_comment_father')
) query_indexes;
"@
    $indexCount = [int] (& $mysql @commonArguments "--execute=$indexCountSql")
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect query indexes."
    }
    if ($Phase -eq 'Before' -and $indexCount -ne 0) {
        throw "Before plans require the pre-V3 schema."
    }
    if ($Phase -eq 'After' -and $indexCount -ne 3) {
        throw "After plans require all three V3 query indexes."
    }

    $sections = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $queries.GetEnumerator()) {
        $plan = & $mysql @commonArguments "--execute=EXPLAIN FORMAT=JSON $($entry.Value)"
        if ($LASTEXITCODE -ne 0) {
            throw "EXPLAIN failed for '$($entry.Key)'."
        }
        $sections.Add("[$($entry.Key)]")
        $sections.Add(($plan -join [Environment]::NewLine))
        $sections.Add('')
    }
    [System.IO.File]::WriteAllLines($outputPath, $sections)
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPassword, 'Process')
}

Write-Output "Phase=$Phase"
Write-Output "Plans=$outputPath"
Write-Output "QueryCount=$($queries.Count)"
