# 运行前提：仅准备本地开发目录，不读取任何本机配置、环境变量秘密或 secret 内容。
# 破坏性边界：只创建 deploy/secrets/local 目录，不覆盖秘密、不删除卷、数据库、队列或上传文件。
# 失败恢复：目录创建失败时保留现状并停止；调用方修复权限后可安全重试。
# 退出码：成功返回 0，目录或权限失败返回非零码。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$secretDirectory = Join-Path $workspaceRoot 'deploy\secrets\local'
New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null

function New-RandomBytes([int]$Length) {
    $bytes = New-Object byte[] $Length
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return $bytes
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Write-NewSecret([string]$Name, [string]$Value) {
    $path = Join-Path $secretDirectory $Name
    if (Test-Path -LiteralPath $path) {
        return
    }
    [System.IO.File]::WriteAllText(
        $path,
        $Value,
        [System.Text.UTF8Encoding]::new($false))
}

foreach ($name in @(
        'mysql_root_password',
        'mysql_app_password',
        'redis_security_password',
        'redis_cache_password',
        'rabbit_bootstrap_password',
        'rabbit_app_password',
        'rabbit_monitor_password',
        'management_password',
        'grafana_admin_password',
        'admin_bootstrap_password')) {
    Write-NewSecret $name (ConvertTo-Base64Url (New-RandomBytes 32))
}

Write-NewSecret 'rabbit_erlang_cookie' (ConvertTo-Base64Url (New-RandomBytes 48))
Write-NewSecret 'security_pepper' (ConvertTo-Base64Url (New-RandomBytes 48))
Write-NewSecret 'messaging_payload_key' ([Convert]::ToBase64String((New-RandomBytes 32)))

Write-Host 'CC4C local secret files are ready. Existing files were preserved.'
Write-Host 'The generated administrator password is stored in deploy/secrets/local/admin_bootstrap_password.'
