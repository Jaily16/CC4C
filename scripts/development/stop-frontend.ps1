# 运行前提：只使用 temp/cc4c-host-stack/frontend.json 中的记录。
# 破坏性边界：仅停止记录的 Node/Vite PID 或同一次 Nginx 启动产生的精确 PID 集合，并核对可执行文件和启动标记。
# 失败恢复：PID 不匹配时拒绝操作并保留记录；不按进程名、端口或用户批量终止。
# 退出码：目标已停止或不存在返回 0，身份校验或停止失败返回非零码。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

function Get-Cc4cProcessInfo {
    param([Parameter(Mandatory = $true)][int] $ProcessId)
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

try {
    $state = Read-Cc4cHostState 'frontend'
    if ($null -eq $state) {
        Write-Output 'No recorded host frontend process exists.'
        exit 0
    }
    $targetPids = if ($null -ne $state.pids) {
        @($state.pids | ForEach-Object { [int] $_ })
    } else {
        @([int] $state.pid)
    }
    $expected = [System.IO.Path]::GetFullPath([string] $state.executablePath)
    $liveProcesses = @()
    foreach ($targetPid in $targetPids) {
        $process = Get-Cc4cProcessInfo $targetPid
        if ($null -ne $process) {
            $actual = if ([string]::IsNullOrWhiteSpace($process.ExecutablePath)) {
                ''
            } else {
                [System.IO.Path]::GetFullPath($process.ExecutablePath)
            }
            if ($actual -cne $expected -or ([string] $process.CommandLine) -notlike "*$($state.marker)*") {
                throw "Recorded frontend PID $targetPid no longer belongs to the expected CC4C process."
            }
            $liveProcesses += $process
        }
    }
    # Nginx 的 worker 可能由 master 派生，先停止子进程，再停止记录的 master。
    foreach ($process in @($liveProcesses | Sort-Object @{ Expression = { if ([int] $_.ProcessId -eq [int] $state.pid) { 1 } else { 0 } } })) {
        Stop-Process -Id ([int] $process.ProcessId) -ErrorAction Stop
    }
    $deadline = (Get-Date).AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 200
        $remaining = @($targetPids | ForEach-Object { Get-Cc4cProcessInfo $_ })
    } while ($remaining.Count -gt 0 -and (Get-Date) -lt $deadline)
    if ($remaining.Count -gt 0) {
        throw 'A recorded frontend process did not stop within the timeout.'
    }
    $updatedState = [ordered]@{}
    foreach ($property in $state.PSObject.Properties) {
        $updatedState[$property.Name] = $property.Value
    }
    $updatedState.status = 'stopped'
    $updatedState.stoppedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-Cc4cHostState 'frontend' $updatedState | Out-Null
    Write-Output 'Recorded CC4C frontend process was stopped when present.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
