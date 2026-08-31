# 运行前提：已准备 frontend/.env.local（或旧路径回退文件）；Dev 模式已有 node_modules，Static 模式已有 dist 和已验证 Nginx。
# 破坏性边界：只启动本脚本创建的一个前端进程；不执行 npm install、不修改 tracked Nginx 配置、不接管后端或 Compose。
# 失败恢复：启动后状态记录失败时，只按本次返回的精确 PID 校验并停止；临时配置仅写入 temp/cc4c-host-frontend。
# 退出码：启动并记录成功返回 0，前置检查、路径或进程身份校验失败返回非零码。

[CmdletBinding()]
param(
    [ValidateSet('Dev', 'Static')]
    [string] $Mode = 'Dev',

    [string] $NginxPath,
    [ValidateRange(1, 65535)][int] $FrontendPort = 5173,
    [string] $FrontendEnvironmentPath
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'host-environment.ps1')
$started = $null
$startedProcessIds = @()
$expectedExecutable = $null
$environmentPathSnapshot = $null

function Get-Cc4cProcessInfo {
    param([Parameter(Mandatory = $true)][int] $ProcessId)
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function ConvertTo-Cc4cNginxPath {
    param([Parameter(Mandatory = $true)][string] $Path)
    return ([System.IO.Path]::GetFullPath($Path)).Replace('\', '/')
}

function Get-Cc4cStaticNginxProcesses {
    param(
        [Parameter(Mandatory = $true)][string] $ExecutablePath,
        [Parameter(Mandatory = $true)][string] $RunDirectory
    )
    $expectedPath = [System.IO.Path]::GetFullPath($ExecutablePath)
    return @(Get-CimInstance Win32_Process -Filter "Name = 'nginx.exe'" -ErrorAction SilentlyContinue | Where-Object {
        -not [string]::IsNullOrWhiteSpace([string] $_.ExecutablePath) -and
        [System.IO.Path]::GetFullPath([string] $_.ExecutablePath) -ceq $expectedPath -and
        ([string] $_.CommandLine) -like "*$RunDirectory*" -and
        ([string] $_.CommandLine) -like '*nginx.conf*'
    })
}

try {
    $environmentPathSnapshot = Set-Cc4cHostEnvironmentPathOverrides -FrontendPath $FrontendEnvironmentPath
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    $frontendRoot = Join-Path $workspaceRoot 'frontend'
    & (Join-Path $PSScriptRoot 'host-preflight.ps1') `
        -Component Frontend `
        -FrontendPort $FrontendPort `
        -FrontendEnvironmentPath $FrontendEnvironmentPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Host frontend preflight failed.'
    }
    $frontendEnvironment = Read-Cc4cEnvironmentFile -Kind Frontend
    if (-not $frontendEnvironment.Values.Contains('VITE_API_BASE_URL')) {
        throw 'VITE_API_BASE_URL is required for host frontend mode.'
    }
    $state = Read-Cc4cHostState 'frontend'
    if ($null -ne $state -and $null -ne (Get-Cc4cProcessInfo ([int] $state.pid))) {
        throw 'A recorded CC4C frontend process is still present.'
    }
    $runDirectory = Join-Path $workspaceRoot 'temp\cc4c-host-frontend'
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
    $environmentSnapshot = $null
    $expectedMarker = $null
    $configPath = $null
    if ($Mode -eq 'Dev') {
        $nodeCommand = Get-Command node.exe -ErrorAction Stop
        $expectedExecutable = (Resolve-Path -LiteralPath $nodeCommand.Source).Path
        $viteEntry = Join-Path $frontendRoot 'node_modules\vite\bin\vite.js'
        if (-not (Test-Path -LiteralPath $viteEntry -PathType Leaf)) {
            throw "Missing Vite entry: $viteEntry. Install dependencies with npm ci before host mode."
        }
        $expectedMarker = 'vite.js'
        try {
            $environmentSnapshot = Set-Cc4cProcessEnvironment $frontendEnvironment.Values
            $started = Start-Process -FilePath $expectedExecutable -WorkingDirectory $frontendRoot -ArgumentList @($viteEntry, '--host', '127.0.0.1', '--port', [string] $FrontendPort) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'frontend.stdout.log') -RedirectStandardError (Join-Path $runDirectory 'frontend.stderr.log')
        }
        finally {
            if ($null -ne $environmentSnapshot) {
                Restore-Cc4cProcessEnvironment $environmentSnapshot
            }
        }
    }
    else {
        if ([string]::IsNullOrWhiteSpace($NginxPath) -or -not [System.IO.Path]::IsPathRooted($NginxPath)) {
            throw 'Static mode requires an absolute NginxPath.'
        }
        $expectedExecutable = (Resolve-Path -LiteralPath $NginxPath -ErrorAction Stop).Path
        if (-not (Test-Path -LiteralPath $expectedExecutable -PathType Leaf)) {
            throw 'The specified Nginx executable does not exist.'
        }
        if ((Get-Cc4cStaticNginxProcesses -ExecutablePath $expectedExecutable -RunDirectory $runDirectory).Count -gt 0) {
            throw 'An unrecorded Nginx process already uses the CC4C frontend run directory.'
        }
        $distRoot = Join-Path $frontendRoot 'dist'
        if (-not (Test-Path -LiteralPath $distRoot -PathType Container)) {
            throw "Missing frontend dist directory: $distRoot"
        }
        $configPath = Join-Path $runDirectory 'nginx.conf'
        $dist = ConvertTo-Cc4cNginxPath $distRoot
        $blog = ConvertTo-Cc4cNginxPath (Join-Path $frontendRoot 'public\blogImg')
        $avatar = ConvertTo-Cc4cNginxPath (Join-Path $frontendRoot 'public\avatar')
        # Windows 版 Nginx 会在 prefix 下创建默认日志和请求临时目录；这些目录必须限定在本次运行的临时目录内。
        New-Item -ItemType Directory -Path (Join-Path $runDirectory 'logs') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $runDirectory 'temp\client_body_temp') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $runDirectory 'temp\proxy_temp') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $runDirectory 'temp\fastcgi_temp') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $runDirectory 'temp\uwsgi_temp') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $runDirectory 'temp\scgi_temp') -Force | Out-Null
        $config = @'
worker_processes 1;
error_log __RUN__/nginx.error.log;
pid __RUN__/nginx.pid;
events { worker_connections 128; }
http {
  access_log __RUN__/nginx.access.log;
  server {
    listen 127.0.0.1:__FRONTEND_PORT__;
    server_name localhost;
    root __DIST__;
    location /blogImg/ { alias __BLOG__/; try_files $uri =404; }
    location /avatar/ { alias __AVATAR__/; try_files $uri =404; }
    location / { try_files $uri $uri/ /index.html; }
  }
}
'@
        $config = $config.Replace('__RUN__', (ConvertTo-Cc4cNginxPath $runDirectory))
        $config = $config.Replace('__DIST__', $dist)
        $config = $config.Replace('__BLOG__', $blog)
        $config = $config.Replace('__AVATAR__', $avatar)
        $config = $config.Replace('__FRONTEND_PORT__', [string] $FrontendPort)
        [System.IO.File]::WriteAllText($configPath, $config, [System.Text.UTF8Encoding]::new($false))
        $expectedMarker = 'nginx.conf'
        # 使用 ProcessStartInfo 保留 daemon off; 的完整参数，并避免 Windows PowerShell 对 ArgumentList 的二次拆分。
        $startInfoObject = [System.Activator]::CreateInstance([System.Diagnostics.ProcessStartInfo])
        $startInfoObject.FileName = $expectedExecutable
        $startInfoObject.WorkingDirectory = $runDirectory
        $startInfoObject.Arguments = '-p "' + $runDirectory + '" -c "nginx.conf" -g "daemon off;"'
        $startInfoObject.UseShellExecute = $false
        $startInfoObject.CreateNoWindow = $true
        $started = New-Object -TypeName System.Diagnostics.Process
        $started.StartInfo = $startInfoObject
        if (-not $started.Start()) {
            $started = $null
        }
    }
    if ($null -eq $started) {
        throw 'The frontend process was not created.'
    }
    Start-Sleep -Milliseconds 250
    if ($Mode -eq 'Static') {
        $matchingProcesses = Get-Cc4cStaticNginxProcesses -ExecutablePath $expectedExecutable -RunDirectory $runDirectory
        $startedProcessIds = @($matchingProcesses | ForEach-Object { [int] $_.ProcessId })
    } else {
        $startedProcessIds = @([int] $started.Id)
    }
    $processInfo = Get-Cc4cProcessInfo $started.Id
    if ($null -eq $processInfo -or
        [string]::IsNullOrWhiteSpace($processInfo.ExecutablePath) -or
        [System.IO.Path]::GetFullPath($processInfo.ExecutablePath) -cne [System.IO.Path]::GetFullPath($expectedExecutable) -or
        ([string] $processInfo.CommandLine) -notlike "*$expectedMarker*") {
        throw 'The created frontend process did not match the requested mode.'
    }
    if ($Mode -eq 'Static' -and
        ($startedProcessIds.Count -lt 1 -or
        -not ($startedProcessIds -contains [int] $started.Id) -or
        -not (@(Get-NetTCPConnection -LocalPort $FrontendPort -State Listen -ErrorAction SilentlyContinue | Where-Object { $startedProcessIds -contains [int] $_.OwningProcess }).Count -gt 0))) {
        throw 'The created Nginx process set did not bind the requested frontend port.'
    }
    $record = [ordered]@{
        component = 'frontend'
        mode = $Mode
        pid = $started.Id
        pids = $startedProcessIds
        executablePath = [System.IO.Path]::GetFullPath($expectedExecutable)
        startedAtUtc = [DateTime]::UtcNow.ToString('o')
        marker = $expectedMarker
        workingDirectory = $frontendRoot
        configPath = $configPath
        commandLineSummary = if ($Mode -eq 'Dev') {
            "node vite.js --host 127.0.0.1 --port $FrontendPort"
        } else {
            'nginx -c nginx.conf -g "daemon off;"'
        }
        port = $FrontendPort
        status = 'running'
    }
    Write-Cc4cHostState 'frontend' $record | Out-Null
    Write-Output "CC4C frontend ($Mode) started with PID $($started.Id)."
    exit 0
}
catch {
    if ($null -ne $expectedExecutable) {
        try {
            $cleanupIds = @($startedProcessIds)
            if ($null -ne $started) {
                $cleanupIds += [int] $started.Id
            }
            $cleanupIds = @($cleanupIds | Sort-Object -Unique)
            foreach ($cleanupId in $cleanupIds) {
                $current = Get-Cc4cProcessInfo $cleanupId
                if ($null -ne $current -and
                    -not [string]::IsNullOrWhiteSpace($current.ExecutablePath) -and
                    [System.IO.Path]::GetFullPath($current.ExecutablePath) -ceq [System.IO.Path]::GetFullPath($expectedExecutable) -and
                    ([string] $current.CommandLine) -like "*$expectedMarker*") {
                    Stop-Process -Id $cleanupId -ErrorAction SilentlyContinue
                }
            }
        } catch { }
    }
    Write-Error $_.Exception.Message
    exit 1
}
finally {
    if ($null -ne $environmentPathSnapshot) {
        Restore-Cc4cHostEnvironmentPathOverrides $environmentPathSnapshot
    }
}
