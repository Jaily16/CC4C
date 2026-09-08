#requires -Version 7.0
# 运行前提：读取当前工作区该应用的精确 PID 状态记录；无需加载环境秘密。
# 外部依赖：仅依赖共享进程身份校验函数以及 Windows 进程和创建时间元数据。
# 破坏性边界：只停止 PID、可执行文件、应用标记和真实创建时间均一致的进程。
# 失败恢复：旧运行记录不完整或身份不明时停止；不按端口、名称或进程树终止。
# 退出码：已停止、已退出或本次精确停止成功为 0，否则为 1。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\infrastructure\host\host-environment.ps1')

try {
    $state = Read-Cc4cHostState backend
    if ($null -eq $state -or $state.status -eq 'stopped') {
        Write-Output 'No running recorded backend requires stopping.'
        exit 0
    }
    if ($state.status -ne 'running' -or $state.component -ne 'backend') { throw 'Unexpected application state; nothing was stopped.' }
    Stop-Cc4cOwnedComponent $state
    Write-Output "Recorded backend PID $($state.pid) is stopped; external services were unchanged."
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
