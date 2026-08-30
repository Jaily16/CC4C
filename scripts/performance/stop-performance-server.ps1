# 运行前提：只处理启动脚本写入且已通过绝对路径校验的性能服务 PID。
# 破坏性边界：禁止按进程名批量终止，不停止现有 Compose 服务，不删除性能结果或数据卷。
# 失败恢复：停止失败时保留 PID 与日志，人工确认命令行和路径后再处理。
# 退出码：目标已停止或不存在返回 0，目标不匹配或停止失败返回非零码。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-performance-server'
$pidPath = Join-Path $runDirectory 'server.pid'
if (-not (Test-Path -LiteralPath $pidPath -PathType Leaf)) {
    Write-Host 'No recorded CC4C performance server PID was found.'
    exit 0
}
$recordedId = [int](Get-Content -Raw -LiteralPath $pidPath).Trim()
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $recordedId" -ErrorAction SilentlyContinue
if ($null -eq $process) {
    Write-Host 'The recorded CC4C performance server is no longer running.'
    exit 0
}
$actualExecutable = [System.IO.Path]::GetFileName($process.ExecutablePath)
if (-not $actualExecutable.Equals('java.exe', [System.StringComparison]::OrdinalIgnoreCase) -or
        $process.CommandLine -notmatch 'cc4c-4\.0\.0-SNAPSHOT\.jar') {
    throw "PID $recordedId is not the recorded CC4C JAR process; refusing to stop it."
}
Stop-Process -Id $recordedId -ErrorAction Stop
$deadline = (Get-Date).AddSeconds(30)
do {
    if (-not (Get-Process -Id $recordedId -ErrorAction SilentlyContinue)) {
        break
    }
    Start-Sleep -Milliseconds 250
} while ((Get-Date) -lt $deadline)
if (Get-Process -Id $recordedId -ErrorAction SilentlyContinue) {
    throw 'The recorded CC4C performance server did not stop within 30 seconds.'
}
Write-Host 'The recorded CC4C performance server was stopped.'
