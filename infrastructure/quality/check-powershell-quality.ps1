#requires -Version 7.0
# 运行前提：PowerShell 7、Git 和当前仓库工作区可用；脚本只检查 Git 清单中的 PowerShell 源码。
# 外部依赖：仅依赖 Git 与 PowerShell AST，不连接应用、中间件或外部服务。
# 破坏性边界：只读源码和 Git 路径清单，不读取 .env.local、秘密、运行数据或构建产物。
# 失败恢复：Git、路径、语法、脚本头或函数注释任一检查失败即报告，不修改任何文件。
# 退出码：18 个脚本及其函数全部通过返回 0，否则返回 1。

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$script:ExpectedScriptCount = 18
$script:ChinesePattern = '[\u3400-\u9fff]'
$script:RequiredHeaders = @('运行前提', '外部依赖', '破坏性边界', '失败恢复', '退出码')

# 执行只读 Git 查询并确保失败不会回退为文件系统递归扫描。
function Get-GitPathList {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = @(& git -C $RepositoryRoot @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Git 文件清单查询失败：$($output -join [Environment]::NewLine)"
    }
    return @($output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
}

# 确认脚本及全部父路径都位于仓库内并且不是 reparse point。
function Assert-SafeScriptPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $root = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "脚本路径越出仓库：$full"
    }
    $item = Get-Item -LiteralPath $full -Force
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "脚本不是普通文件：$full"
    }
    $cursor = $item.Directory
    while ($null -ne $cursor -and $cursor.FullName.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        if ($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "脚本父路径包含 reparse point：$full"
        }
        $cursor = $cursor.Parent
    }
}

# 返回语法树节点之前最近一行非空源码，供紧邻中文函数说明检查使用。
function Get-PreviousMeaningfulLine {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]]$Lines,
        [Parameter(Mandatory = $true)]
        [int]$StartLine
    )

    for ($index = $StartLine - 2; $index -ge 0; $index--) {
        if (-not [string]::IsNullOrWhiteSpace($Lines[$index])) {
            return $Lines[$index].Trim()
        }
    }
    return ''
}

# 检查单个 PowerShell 脚本的五项头部、语法和全部具名函数说明。
function Test-PowerShellScript {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [Collections.Generic.List[string]]$Errors
    )

    $fullPath = Join-Path $RepositoryRoot $RelativePath
    Assert-SafeScriptPath -RepositoryRoot $RepositoryRoot -Path $fullPath
    $content = Get-Content -LiteralPath $fullPath -Raw -Encoding UTF8
    $lines = @($content -split "`r?`n")
    foreach ($header in $script:RequiredHeaders) {
        $headerPattern = '(?m)^#\s*' + [regex]::Escape($header) + '：.+$'
        if ($content -notmatch $headerPattern) {
            $Errors.Add(('{0}:1: 缺少脚本头字段“{1}”。' -f $RelativePath, $header))
        }
    }

    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($fullPath, [ref]$tokens, [ref]$parseErrors)
    foreach ($parseError in @($parseErrors)) {
        $Errors.Add(('{0}:{1}: PowerShell 语法错误：{2}' -f $RelativePath, $parseError.Extent.StartLineNumber, $parseError.Message))
    }
    $functions = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $true))
    foreach ($function in $functions) {
        $previous = Get-PreviousMeaningfulLine -Lines $lines -StartLine $function.Extent.StartLineNumber
        if ($previous -notmatch '^#' -or $previous -notmatch $script:ChinesePattern) {
            $Errors.Add(('{0}:{1}: 函数 {2} 前缺少紧邻中文说明。' -f $RelativePath, $function.Extent.StartLineNumber, $function.Name))
        }
    }
}

try {
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    $candidates = @(Get-GitPathList -RepositoryRoot $repositoryRoot -Arguments @(
            'ls-files', '--cached', '--others', '--exclude-standard', '--', '*.ps1'
        ))
    $deleted = @(Get-GitPathList -RepositoryRoot $repositoryRoot -Arguments @('ls-files', '--deleted', '--', '*.ps1'))
    $paths = @($candidates | Where-Object { $_ -notin $deleted } | Sort-Object -Unique)
    $errors = [Collections.Generic.List[string]]::new()

    if ($paths.Count -ne $script:ExpectedScriptCount) {
        $errors.Add("PowerShell 脚本清单数量为 $($paths.Count)，方面六基线要求 $script:ExpectedScriptCount。")
    }
    foreach ($relativePath in $paths) {
        Test-PowerShellScript -RepositoryRoot $repositoryRoot -RelativePath $relativePath -Errors $errors
    }
    if ($errors.Count -gt 0) {
        $errors | ForEach-Object { Write-Error $_ -ErrorAction Continue }
        exit 1
    }
    Write-Output "PowerShell 中文质量门禁通过：$($paths.Count)/$script:ExpectedScriptCount 个脚本。"
    exit 0
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
