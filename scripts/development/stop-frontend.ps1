# 运行前提：只使用 temp/cc4c-host-stack/frontend.json 中的记录。
# 破坏性边界：仅停止记录的单个 Node/Vite 或 Nginx PID，并核对可执行文件和启动标记。
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
    $process = Get-Cc4cProcessInfo ([int] $state.pid)
    if ($null -ne $process) {
        $expected = [System.IO.Path]::GetFullPath([string] $state.executablePath)
        $actual = if ([string]::IsNullOrWhiteSpace($process.ExecutablePath)) {
            ''
        } else {
            [System.IO.Path]::GetFullPath($process.ExecutablePath)
        }
        if ($actual -cne $expected -or ([string] $process.CommandLine) -notlike "*$($state.marker)*") {
            throw 'Recorded frontend PID no longer belongs to the expected CC4C process.'
        }
        Stop-Process -Id ([int] $state.pid) -ErrorAction Stop
        $deadline = (Get-Date).AddSeconds(10)
        do {
            Start-Sleep -Milliseconds 200
            $stillRunning = Get-Cc4cProcessInfo ([int] $state.pid)
        } while ($null -ne $stillRunning -and (Get-Date) -lt $deadline)
        if ($null -ne $stillRunning) {
            throw 'The recorded frontend process did not stop within the timeout.'
        }
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
