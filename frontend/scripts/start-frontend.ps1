#requires -Version 7.0
# 运行前提：固定前后端 .env.local、Node 与现有 Vite 依赖已准备；只支持本机 Node/Vite。
# 外部依赖：依赖共享环境加载器、本机 Node/npm、已安装的 Vite 依赖和运行中的后端公开接口。
# 破坏性边界：只启动一个前端进程，不安装依赖，不读取上传内容，不向 Vite 传入后端凭据。
# 失败恢复：恢复进程变量，只按本次完整 PID 身份停止；唯一日志和既有上传数据保留。
# 退出码：启动并记录成功为 0；配置、依赖、路径、端口或身份验证失败为 1。

[CmdletBinding()]
param([ValidateRange(1, 65535)][int] $FrontendPort = 5173)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\infrastructure\host\host-environment.ps1')
$started = $null
$identity = $null
$snapshot = $null


try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $frontendRoot = Join-Path $workspaceRoot 'frontend'
    Assert-Cc4cCanStart frontend
    & (Join-Path $workspaceRoot 'infrastructure\host\host-preflight.ps1') -Component Frontend -FrontendPort $FrontendPort
    if ($LASTEXITCODE -ne 0) { throw 'Frontend preflight failed.' }
    $public = Read-Cc4cEnvironmentFile -Kind Frontend
    $runtime = Read-Cc4cEnvironmentFile -Kind Runtime
    $values = [ordered]@{
        VITE_API_BASE_URL = [string] $public.Values.VITE_API_BASE_URL
        CC4C_HOST_BLOG_IMG_ROOT = Resolve-Cc4cHostUploadRoot $runtime.Values 'CC4C_SAVE_IMG_PATH' (Join-Path $workspaceRoot 'backend')
        CC4C_HOST_AVATAR_ROOT = Resolve-Cc4cHostUploadRoot $runtime.Values 'CC4C_SAVE_AVATAR_PATH' (Join-Path $workspaceRoot 'backend')
    }
    if ($values.CC4C_HOST_BLOG_IMG_ROOT -eq $values.CC4C_HOST_AVATAR_ROOT) { throw 'Blog image and avatar roots must differ.' }
    $runtime = $null
    $nodePath = Assert-Cc4cOrdinaryPath (Get-Command node.exe -ErrorAction Stop).Source
    $vitePath = Assert-Cc4cOrdinaryPath (Join-Path $frontendRoot 'node_modules\vite\bin\vite.js')
    $logs = New-Cc4cRunLogs frontend
    $launch = @{
        FilePath = $nodePath
        WorkingDirectory = $frontendRoot
        ArgumentList = @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', [string] $FrontendPort, '--strictPort')
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = $logs.Stdout
        RedirectStandardError = $logs.Stderr
    }
    try {
        $snapshot = Set-Cc4cProcessEnvironment $values -Frontend
        $started = Start-Process @launch
    }
    finally {
        if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot; $snapshot = $null }
    }
    $identity = Get-Cc4cStartedIdentity $started $nodePath $vitePath frontend
    if ($null -eq $identity) { throw 'The frontend exited before its identity could be recorded; inspect its new log locally.' }
    $identity.workingDirectory = $frontendRoot
    $identity.port = $FrontendPort
    $identity.runId = $logs.RunId
    $identity.stdout = $logs.Stdout
    $identity.stderr = $logs.Stderr
    Write-Cc4cHostState frontend $identity
    Write-Output "CC4C frontend started with exact PID $($identity.pid)."
    exit 0
}
catch {
    $failure = $_.Exception.Message
    if ($null -ne $started) {
        try {
            if ($null -eq $identity) { $identity = Get-Cc4cStartedIdentity $started $nodePath $vitePath frontend }
            if ($null -ne $identity) { Stop-Cc4cExactProcess $identity }
        }
        catch { Write-Warning 'The newly created frontend could not be safely stopped; preserve its PID record and logs.' }
    }
    Write-Error $failure -ErrorAction Continue
    exit 1
}
finally {
    if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot }
    if ($null -ne $started) { $started.Dispose() }
}
