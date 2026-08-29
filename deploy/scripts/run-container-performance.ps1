[CmdletBinding()]
param(
    [ValidateRange(0, 3)]
    [int] $StandardRounds = 3,

    [ValidateRange(1024, 65535)]
    [int] $RabbitManagementPort = 15672
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$project = 'cc4c-v3'
$previousRabbitManagementPort = $env:CC4C_RABBITMQ_MANAGEMENT_PORT

Push-Location -LiteralPath $workspaceRoot
try {
    $env:CC4C_RABBITMQ_MANAGEMENT_PORT = [string] $RabbitManagementPort
    & (Join-Path $PSScriptRoot 'prepare-local.ps1')

    & docker compose -p $project --profile performance up -d --wait --wait-timeout 300 `
        mysql redis-security redis-cache rabbitmq mailpit
    if ($LASTEXITCODE -ne 0) { throw 'Performance infrastructure failed to start.' }

    & docker compose -p $project --profile performance up --no-deps rabbit-init
    if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ initialization failed.' }

    & docker compose -p $project --profile performance up --no-deps perf-init
    if ($LASTEXITCODE -ne 0) { throw 'Performance database initialization failed.' }

    & docker compose -p $project --profile performance build performance-tools backend-perf
    if ($LASTEXITCODE -ne 0) { throw 'Performance images failed to build.' }

    & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
        mvn -o -B -ntp '-DskipTests' `
        '-Dexec.classpathScope=test' `
        '-Dexec.mainClass=com.cc4c.performance.Aspect4PerformanceDataSeeder' `
        exec:java
    if ($LASTEXITCODE -ne 0) { throw 'Performance data preparation failed.' }

    & docker compose -p $project --profile performance up -d --wait --wait-timeout 300 backend-perf
    if ($LASTEXITCODE -ne 0) { throw 'Performance backend failed to start.' }

    & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
        mvn -o -B -ntp -Paspect6-gatling gatling:test `
        '-Dgatling.simulationClass=com.cc4c.performance.PublicReadSmoke' `
        '-Dgatling.resultsFolder=/workspace/target/gatling/container/smoke'
    if ($LASTEXITCODE -ne 0) { throw 'Container PublicReadSmoke gate failed.' }

    for ($round = 1; $round -le $StandardRounds; $round++) {
        & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
            mvn -o -B -ntp -Paspect6-gatling gatling:test `
            '-Dgatling.simulationClass=com.cc4c.performance.PublicReadWarmup' `
            "-Dgatling.resultsFolder=/workspace/target/gatling/container/round-$round/warmup"
        if ($LASTEXITCODE -ne 0) { throw "Container warm-up round $round failed." }

        & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
            mvn -o -B -ntp -Paspect6-gatling gatling:test `
            '-Dgatling.simulationClass=com.cc4c.performance.PublicReadStandard' `
            "-Dgatling.resultsFolder=/workspace/target/gatling/container/round-$round/measurement"
        if ($LASTEXITCODE -ne 0) { throw "Container standard round $round failed." }

        $roundRoot = Join-Path $workspaceRoot "temp/cc4c-v3-aspect7-gatling/container/round-$round/measurement"
        $simulationLog = Get-ChildItem -LiteralPath $roundRoot -Filter simulation.log -Recurse |
            Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if ($null -eq $simulationLog) { throw "Round $round did not produce simulation.log." }
        & (Join-Path $workspaceRoot 'back-end/CC4C/summarize-gatling.ps1') `
            -SimulationLog $simulationLog.FullName `
            -OutputPath (Join-Path $roundRoot 'summary.json') `
            -WarmupSeconds 120 | Out-Host
    }

    $summaries = @()
    if ($StandardRounds -gt 0) {
        $summaries = @(Get-ChildItem `
                -Path (Join-Path $workspaceRoot 'temp/cc4c-v3-aspect7-gatling/container/round-*') `
                -Filter summary.json -Recurse |
            ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json })
    }
    if (($summaries | Measure-Object).Count -ne $StandardRounds) {
        throw 'Container performance summaries are incomplete.'
    }
    if (($summaries | Measure-Object -Property errors -Sum).Sum -ne 0) {
        throw 'Container standard scenario produced HTTP errors.'
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
        $medianPath = Join-Path $workspaceRoot 'temp/cc4c-v3-aspect7-gatling/container/median.json'
        $median | ConvertTo-Json | Set-Content -LiteralPath $medianPath -Encoding utf8NoBOM
        $median | Format-List | Out-Host
    }

    $metrics = & docker compose -p $project --profile performance run --rm --no-deps performance-tools `
        /bin/bash -ec `
        'password="$(tr -d ''\r\n'' < /run/secrets/management_password)"; curl --fail --silent --user "cc4c_observer:${password}" http://backend-perf:4081/actuator/prometheus'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to collect container performance metrics.' }
    $metricsText = $metrics -join "`n"
    $metricsPath = Join-Path $workspaceRoot 'temp/cc4c-v3-aspect7-gatling/container/metrics.prom'
    $metricsText | Set-Content -LiteralPath $metricsPath -Encoding utf8NoBOM

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
    if ($null -eq $previousRabbitManagementPort) {
        Remove-Item Env:CC4C_RABBITMQ_MANAGEMENT_PORT -ErrorAction SilentlyContinue
    }
    else {
        $env:CC4C_RABBITMQ_MANAGEMENT_PORT = $previousRabbitManagementPort
    }
    Pop-Location
}
