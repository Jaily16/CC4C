#requires -Version 7.0
# 运行前提：调用方位于对应应用目录，已有工具链和固定 .env.local 可用；后端需确认数据库名。
# 外部依赖：共享环境辅助及调用方指定的一个前台 Maven、Java 或 npm 命令；不安装依赖。
# 破坏性边界：包裹层仅加载配置，不创建日志/PID 或执行预检；所选命令及应用启动仍可能执行迁移、Session 和消息处理。
# 失败恢复：命令结束或异常时恢复所有被调整的环境变量，不打印配置正文或自动重试。
# 退出码：保留原生命令非零退出码；参数、配置或环境准备失败返回 1，成功返回 0。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('Backend', 'Frontend', 'Observability')][string] $Application,
    [Parameter(Mandatory = $true)][scriptblock] $Command,
    [string] $ConfirmDatabase,
    [ValidateRange(1, 65535)][int] $ManagementPort = 4081
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')
$snapshot = $null
$values = $null
$runtime = $null
$exitCode = 1

try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $applicationRoot = Assert-Cc4cOrdinaryPath (Join-Path $workspaceRoot $Application.ToLowerInvariant()) -Kind Directory
    if (-not [string]::Equals((Get-Location).Path, $applicationRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Enter the selected application directory before loading its environment.'
    }
    # 只接受一个前台命令语句，不接收管道、重定向、后台执行或额外脚本语句。
    $statements = @($Command.Ast.EndBlock.Statements)
    if ($null -ne $Command.Ast.BeginBlock -or $null -ne $Command.Ast.ProcessBlock -or
        $statements.Count -ne 1 -or $statements[0] -isnot [Management.Automation.Language.PipelineAst] -or
        $statements[0].Background -or $statements[0].PipelineElements.Count -ne 1 -or
        $statements[0].PipelineElements[0] -isnot [Management.Automation.Language.CommandAst]) {
        throw 'Provide one foreground Maven, Java or npm command.'
    }
    $commandAst = $statements[0].PipelineElements[0]
    $allowedCommands = if ($Application -eq 'Backend') { @('mvn', 'mvn.cmd', 'java', 'java.exe') } else { @('npm', 'npm.cmd') }
    if ($commandAst.Redirections.Count -ne 0 -or $commandAst.GetCommandName() -notin $allowedCommands) {
        throw 'The command must use the selected application tool without output redirection.'
    }

    if ($Application -eq 'Backend') {
        if ([string]::IsNullOrWhiteSpace($ConfirmDatabase)) { throw 'ConfirmDatabase is required for the backend.' }
        $values = Assert-Cc4cRuntimeEnvironment (Read-Cc4cEnvironmentFile -Kind Runtime) -ManagementPort $ManagementPort
        if ((Get-Cc4cDatabaseName $values) -cne $ConfirmDatabase) {
            throw 'The configured database does not match the explicitly confirmed name.'
        }
        $snapshot = Set-Cc4cProcessEnvironment $values -Backend
    } elseif ($Application -eq 'Frontend') {
        $public = Read-Cc4cEnvironmentFile -Kind Frontend
        $runtime = Read-Cc4cEnvironmentFile -Kind Runtime
        $backendRoot = Join-Path $workspaceRoot 'backend'
        $values = [ordered]@{
            VITE_API_BASE_URL = [string] $public.Values.VITE_API_BASE_URL
            CC4C_HOST_BLOG_IMG_ROOT = Resolve-Cc4cHostUploadRoot $runtime.Values 'CC4C_SAVE_IMG_PATH' $backendRoot
            CC4C_HOST_AVATAR_ROOT = Resolve-Cc4cHostUploadRoot $runtime.Values 'CC4C_SAVE_AVATAR_PATH' $backendRoot
        }
        if ($values.CC4C_HOST_BLOG_IMG_ROOT -eq $values.CC4C_HOST_AVATAR_ROOT) {
            throw 'Blog image and avatar roots must differ.'
        }
        $runtime = $null
        $snapshot = Set-Cc4cProcessEnvironment $values -Frontend
    } else {
        $public = Read-Cc4cEnvironmentFile -Kind Observability
        $values = [ordered]@{ VITE_API_BASE_URL = [string] $public.Values.VITE_API_BASE_URL }
        $snapshot = Set-Cc4cProcessEnvironment $values -Observability
    }
    $values = $null
    # 在当前脚本作用域执行，使原生命令的退出码可直接取得；前台 Ctrl+C 仍经过 finally。
    $LASTEXITCODE = 0
    . $Command
    $exitCode = $LASTEXITCODE
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    $exitCode = 1
} finally {
    if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot }
    $snapshot = $null
    $values = $null
    $runtime = $null
}
exit $exitCode
