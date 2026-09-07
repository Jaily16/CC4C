#requires -Version 7.0
# 运行前提：使用当前工作区的栈记录及两端完整身份快照，不加载任何环境文件。
# 破坏性边界：只按前端、后端顺序停止精确 PID，不触及外部服务、上传或数据库。
# 失败恢复：先校验全部身份；任何未知记录停止，部分成功时保留记录供人工判断。
# 退出码：无运行记录或精确停止完成为 0；记录或停止失败为 1。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')

try {
    $stack = Read-Cc4cHostState stack
    if ($null -eq $stack -or $stack.status -eq 'stopped') {
        Write-Output 'No running recorded stack requires stopping.'
        exit 0
    }
    Assert-Cc4cRunningStack $stack
    Stop-Cc4cOwnedComponent $stack.frontend
    Write-Output "Frontend PID $($stack.frontend.pid) is stopped."
    Stop-Cc4cOwnedComponent $stack.backend
    Write-Output "Backend PID $($stack.backend.pid) is stopped."
    $current = Read-Cc4cHostState stack
    if ($null -eq $current -or $current.runId -cne $stack.runId) {
        throw 'The stack record changed; the newer record was preserved.'
    }
    $updated = [ordered]@{}
    foreach ($property in $stack.PSObject.Properties) { $updated[$property.Name] = $property.Value }
    $updated.status = 'stopped'
    $updated.stoppedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-Cc4cHostState stack $updated
    Write-Output 'Only the recorded frontend and backend were stopped. All external services and data were preserved.'
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
