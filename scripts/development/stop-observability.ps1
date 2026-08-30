# 运行前提：只停止由观测启动脚本记录且路径已校验的本地进程。
# 破坏性边界：禁止按进程名批量终止，不停止业务服务，不删除观测卷或数据。
# 失败恢复：停止失败时保留 PID 记录和输出，人工确认目标后再处理。
# 退出码：目标均已停止或不存在返回 0，路径校验或停止失败返回非零码。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'host-environment.ps1')
$runDirectory = Join-Path $workspaceRoot 'temp\cc4c-observability'

function Stop-RecordedProcess([string]$Name, [string]$ExpectedExecutable) {
    $pidPath = Join-Path $runDirectory "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidPath -PathType Leaf)) { return }
    $recordedId = [int](Get-Content -Raw -LiteralPath $pidPath).Trim()
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $recordedId" -ErrorAction SilentlyContinue
    if ($null -eq $process) { return }
    $actual = [System.IO.Path]::GetFileName($process.ExecutablePath)
    if (-not $actual.Equals($ExpectedExecutable, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "PID $recordedId no longer belongs to $ExpectedExecutable; refusing to stop it."
    }
    Stop-Process -Id $recordedId -ErrorAction Stop
}

Stop-RecordedProcess 'prometheus' 'prometheus.exe'
Stop-RecordedProcess 'grafana' 'grafana.exe'
$state = Read-Cc4cHostState 'observability'
if ($null -ne $state) {
    $updatedState = [ordered]@{}
    foreach ($property in $state.PSObject.Properties) {
        $updatedState[$property.Name] = $property.Value
    }
    $updatedState.status = 'stopped'
    $updatedState.stoppedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-Cc4cHostState 'observability' $updatedState | Out-Null
}
Write-Host 'Recorded Prometheus and Grafana processes were stopped when present.'
