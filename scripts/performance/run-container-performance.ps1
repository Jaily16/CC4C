<#
运行前提：仅使用隔离的 CI/性能 Compose 项目和非敏感占位值，先确认目标项目不是 cc4c。
破坏性边界：不得删除现有卷、数据库、Redis/RabbitMQ 数据或上传文件；只清理本次隔离资源。
失败恢复：任一步失败保留报告和容器输出，按项目记录精确停止本次资源，不执行广泛清理。
退出码：性能门禁通过返回 0，启动、采集或阈值失败返回非零码。
#>

[CmdletBinding()]
param(
    [ValidateRange(0, 3)]
    [int] $StandardRounds = 3,

    [ValidateRange(1024, 65535)]
    [int] $RabbitManagementPort = 15672,

    [ValidateRange(1024, 65535)]
    [int] $MailpitUiPort = 18026
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$project = 'cc4c-perf'
$previousRabbitManagementPort = $env:CC4C_RABBITMQ_MANAGEMENT_PORT
$previousMailpitUiPort = $env:CC4C_MAILPIT_UI_PORT
$previousGatlingHostPath = $env:CC4C_PERF_GATLING_HOST_PATH
$performanceOutputRoot = Join-Path $workspaceRoot 'temp\cc4c-performance-gatling'
$metricsHelperName = $null
$metricsStagingPath = $null
New-Item -ItemType Directory -Force -Path $performanceOutputRoot | Out-Null

function Repair-GatlingOutputOwnership {
    <#
    Linux CI 上的性能镜像默认以 root 运行；其 bind mount 输出若保留 root
    所有权，宿主机侧汇总器无法写入 summary.json。只在 Unix 主机上将本轮
    隔离性能输出交还给当前运行用户，不触碰其他路径或 Docker 卷。
    #>
    if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Unix) {
        return
    }

    $hostUid = (& id -u).Trim()
    $hostGid = (& id -g).Trim()
    if ($LASTEXITCODE -ne 0 -or $hostUid -notmatch '^\d+$' -or $hostGid -notmatch '^\d+$') {
        throw 'Unable to resolve the Unix host uid/gid for Gatling output ownership.'
    }

    $hostOwner = '{0}:{1}' -f $hostUid, $hostGid
    & docker compose --ansi never -p $project --profile performance run `
        --rm --no-deps --user 0:0 --entrypoint /bin/chown performance-tools `
        -R $hostOwner /workspace/target/gatling | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to restore host ownership for container performance output.'
    }
}

Push-Location -LiteralPath $workspaceRoot
try {
    $env:CC4C_RABBITMQ_MANAGEMENT_PORT = [string] $RabbitManagementPort
    $env:CC4C_MAILPIT_UI_PORT = [string] $MailpitUiPort
    $env:CC4C_PERF_GATLING_HOST_PATH = $performanceOutputRoot
    & (Join-Path $workspaceRoot 'scripts/deployment/prepare-local.ps1')

    & docker compose -p $project --profile performance up -d --wait --wait-timeout 300 `
        mysql redis-security redis-cache rabbitmq mailpit
    if ($LASTEXITCODE -ne 0) { throw 'Performance infrastructure failed to start.' }

    & docker compose -p $project --profile performance run --rm --no-deps rabbit-init
    if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ initialization failed.' }

    & docker compose -p $project --profile performance run --rm --no-deps perf-init
    if ($LASTEXITCODE -ne 0) { throw 'Performance database initialization failed.' }

    & docker compose -p $project --profile performance build performance-tools backend-perf
    if ($LASTEXITCODE -ne 0) { throw 'Performance images failed to build.' }

    & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
        mvn -o -B -ntp '-DskipTests' `
        '-Dexec.classpathScope=test' `
        '-Dexec.mainClass=com.cc4c.performance.PerformanceDataSeeder' `
        exec:java
    if ($LASTEXITCODE -ne 0) { throw 'Performance data preparation failed.' }

    & docker compose -p $project --profile performance up -d --wait --wait-timeout 300 backend-perf
    if ($LASTEXITCODE -ne 0) { throw 'Performance backend failed to start.' }

    & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
        mvn -o -B -ntp -Pperformance-gatling gatling:test `
        '-Dgatling.simulationClass=com.cc4c.performance.PublicReadSmoke' `
        '-Dgatling.resultsFolder=/workspace/target/gatling/container/smoke'
    if ($LASTEXITCODE -ne 0) { throw 'Container PublicReadSmoke gate failed.' }
    Repair-GatlingOutputOwnership

    for ($round = 1; $round -le $StandardRounds; $round++) {
        & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
            mvn -o -B -ntp -Pperformance-gatling gatling:test `
            '-Dgatling.simulationClass=com.cc4c.performance.PublicReadWarmup' `
            "-Dgatling.resultsFolder=/workspace/target/gatling/container/round-$round/warmup"
        if ($LASTEXITCODE -ne 0) { throw "Container warm-up round $round failed." }
        Repair-GatlingOutputOwnership

        & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
            mvn -o -B -ntp -Pperformance-gatling gatling:test `
            '-Dgatling.simulationClass=com.cc4c.performance.PublicReadStandard' `
            "-Dgatling.resultsFolder=/workspace/target/gatling/container/round-$round/measurement"
        if ($LASTEXITCODE -ne 0) { throw "Container standard round $round failed." }
        Repair-GatlingOutputOwnership

        $roundRoot = Join-Path $performanceOutputRoot "container/round-$round/measurement"
        $simulationLog = Get-ChildItem -LiteralPath $roundRoot -Filter simulation.log -Recurse |
            Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($null -eq $simulationLog) { throw "Round $round did not produce simulation.log." }
        & (Join-Path $workspaceRoot 'scripts/performance/summarize-gatling.ps1') `
            -SimulationLog $simulationLog.FullName `
            -OutputPath (Join-Path $roundRoot 'summary.json') `
            -WarmupSeconds 120 | Out-Host
    }

    $summaries = @()
    if ($StandardRounds -gt 0) {
        $summaries = @(Get-ChildItem `
                -Path (Join-Path $performanceOutputRoot 'container/round-*') `
                -Filter summary.json -Recurse |
            ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json })
    }
    if ($StandardRounds -gt 0) {
        if (($summaries | Measure-Object).Count -ne $StandardRounds) {
            throw 'Container performance summaries are incomplete.'
        }
        if (($summaries | Measure-Object -Property errors -Sum).Sum -ne 0) {
            throw 'Container standard scenario produced HTTP errors.'
        }
    }
    if ($StandardRounds -gt 0) {
        function Get-Median([double[]] $Values) {
            $ordered = @($Values | Sort-Object)
            return $ordered[[int][Math]::Floor($ordered.Count / 2)]
        }
        $median = [ordered]@{
            rounds = $StandardRounds
            p50Ms = Get-Median @($summaries.p50Ms)
            p95Ms = Get-Median @($summaries.p95Ms)
            p99Ms = Get-Median @($summaries.p99Ms)
            throughputPerSecond = Get-Median @($summaries.throughputPerSecond)
            errors = 0
        }
        $medianPath = Join-Path $performanceOutputRoot 'container/median.json'
        [System.IO.File]::WriteAllText($medianPath, ($median | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
        $median | Format-List | Out-Host
    }

    [string] $cc4cMetricsCommandBase64 = 'cGFzc3dvcmQ9JCh0ciAtZCAiXHJcbiIgPCAvcnVuL3NlY3JldHMvbWFuYWdlbWVudF9wYXNzd29yZCk7IGN1cmwgLS1mYWlsIC0tc2lsZW50IC0tdXNlciAiY2M0Y19vYnNlcnZlcjoke3Bhc3N3b3JkfSIgaHR0cDovL2JhY2tlbmQtcGVyZjo0MDgxL2FjdHVhdG9yL3Byb21ldGhldXM='
    $metricsPath = Join-Path $performanceOutputRoot 'container/metrics.prom'
    $metricsContainerPath = '/tmp/cc4c-performance-metrics.prom'
    $metricsHelperName = 'cc4c-perf-metrics-helper'
    $metrics = @()
    $metricsText = ''
    $metricsStagingPath = ([string] $metricsPath) + '.staging'
    <#
    PowerShell 5.1 会在直接传递嵌套 shell 引号时重新解释命令；通过非敏感
    Base64 传递固定脚本，避免管理密码或 shell 语法被宿主机解析。
    这是固定的非敏感 shell 脚本编码；凭据仍只在容器内从 secret 文件读取。
    #>
    if (Test-Path -LiteralPath $metricsStagingPath) {
        throw 'Container performance metrics staging path already exists.'
    }
    $helperNames = @(& docker ps -a --filter ("name=^/{0}$" -f $metricsHelperName) --format '{{.Names}}' 2>$null)
    if ($helperNames.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace(($helperNames -join ''))) {
        throw 'Container performance metrics helper name is already in use.'
    }
    & docker compose --ansi never -p $project --profile performance run --name $metricsHelperName -d --no-deps performance-tools /bin/bash -ec "echo $cc4cMetricsCommandBase64 | base64 -d | /bin/bash -e > $metricsContainerPath"
    $metricsRunExitCode = $LASTEXITCODE
    if ($metricsRunExitCode -ne 0) {
        throw 'Unable to start container performance metrics helper.'
    }
    & docker wait $metricsHelperName | Out-Null
    $metricsWaitExitCode = $LASTEXITCODE
    if ($metricsWaitExitCode -ne 0) { throw 'Container performance metrics helper failed.' }
    $metricsContainerExitCode = [int] (& docker inspect $metricsHelperName --format '{{.State.ExitCode}}')
    if ($metricsContainerExitCode -ne 0) {
        throw ("Container performance metrics helper exited with code {0}." -f $metricsContainerExitCode)
    }
    & docker cp ($metricsHelperName + ':' + $metricsContainerPath) $metricsStagingPath
    $metricsCopyExitCode = $LASTEXITCODE
    if ($metricsCopyExitCode -ne 0) { throw 'Unable to retrieve container performance metrics.' }
    $metricsText = [System.IO.File]::ReadAllText($metricsStagingPath)
    if ([string]::IsNullOrWhiteSpace($metricsText)) { throw 'Container performance metrics response was empty.' }
    $metrics = @($metricsText -split "`r?`n")
    [System.IO.File]::Copy($metricsStagingPath, $metricsPath, $true)
    Remove-Item -LiteralPath $metricsStagingPath -Force
    $metricsStagingPath = $null
    & docker rm -f $metricsHelperName | Out-Null
    $metricsHelperName = $null

    $hits = 0.0
    $misses = 0.0
    foreach ($line in $metrics) {
        if ($line -match '^cc4c_cache_requests_total\{[^}]*outcome="hit"[^}]*\}\s+(?<value>[0-9.eE+-]+)$') {
            $hits += [double]::Parse($Matches.value, [Globalization.CultureInfo]::InvariantCulture)
        }
        if ($line -match '^cc4c_cache_requests_total\{[^}]*outcome="miss"[^}]*\}\s+(?<value>[0-9.eE+-]+)$') {
            $misses += [double]::Parse($Matches.value, [Globalization.CultureInfo]::InvariantCulture)
        }
        if ($line -match '^hikaricp_connections_pending(?:\{[^}]*\})?\s+(?<value>[0-9.eE+-]+)$' -and
                [double]::Parse($Matches.value, [Globalization.CultureInfo]::InvariantCulture) -gt 0) {
            throw 'Hikari pending connections remained above zero after the performance run.'
        }
    }
    if (($hits + $misses) -lt 100) { throw 'Too few cache observations were collected.' }
    if (($hits / ($hits + $misses)) -lt 0.85) {
        throw 'Hot-cache hit rate was below 85 percent.'
    }
}
finally {
    & docker compose -p $project --profile performance stop backend-perf perf-init | Out-Host
    if ($null -ne $metricsHelperName) {
        $remainingHelperNames = @(docker ps -a --filter ("name=^/{0}$" -f $metricsHelperName) --format '{{.Names}}' 2>$null)
        if ($remainingHelperNames.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace(($remainingHelperNames -join ''))) {
            & docker rm -f $metricsHelperName | Out-Host
        }
    }
    if ($null -ne $metricsStagingPath -and (Test-Path -LiteralPath $metricsStagingPath)) {
        Remove-Item -LiteralPath $metricsStagingPath -Force
    }
    if ($null -eq $previousRabbitManagementPort) {
        Remove-Item Env:CC4C_RABBITMQ_MANAGEMENT_PORT -ErrorAction SilentlyContinue
    }
    else {
        $env:CC4C_RABBITMQ_MANAGEMENT_PORT = $previousRabbitManagementPort
    }
    if ($null -eq $previousMailpitUiPort) {
        Remove-Item Env:CC4C_MAILPIT_UI_PORT -ErrorAction SilentlyContinue
    }
    else {
        $env:CC4C_MAILPIT_UI_PORT = $previousMailpitUiPort
    }
    if ($null -eq $previousGatlingHostPath) {
        Remove-Item Env:CC4C_PERF_GATLING_HOST_PATH -ErrorAction SilentlyContinue
    }
    else {
        $env:CC4C_PERF_GATLING_HOST_PATH = $previousGatlingHostPath
    }
    <# 外部捕获文件保留，便于失败诊断；它不在仓库内，也不包含凭据或业务数据。 #>
    Pop-Location
}
