#requires -Version 7.0
# 运行前提：由本机应用入口显式调用；载入本文件本身不读取任何 .env.local。
# 破坏性边界：只管理应用环境和自身状态/PID，不接管中间件，不打印配置、命令行或秘密。
# 失败恢复：调用方必须恢复进程变量；身份不明时保留现场，状态和日志只写入受控 temp 路径。
# 退出码：共享函数以异常报告失败，由入口脚本转换为非零退出码。

$script:Cc4cHostWorkspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:Cc4cRuntimeRequiredNames = @(
    'CC4C_DB_URL', 'CC4C_DB_USERNAME', 'CC4C_DB_PASSWORD',
    'CC4C_REDIS_URL', 'CC4C_SESSION_NAMESPACE', 'CC4C_BUSINESS_CACHE_ENABLED', 'CC4C_CACHE_NAMESPACE',
    'CC4C_SECURITY_PEPPER', 'CC4C_SESSION_COOKIE_SECURE', 'CC4C_ALLOWED_ORIGINS',
    'CC4C_MAIL_HOST', 'CC4C_MAIL_PORT', 'CC4C_MAIL_USERNAME', 'CC4C_MAIL_PASSWORD',
    'CC4C_RABBITMQ_URL', 'CC4C_RABBITMQ_NAMESPACE', 'CC4C_MODERATION_NOTIFICATION_RECIPIENTS',
    'CC4C_MESSAGING_ACTIVE_KEY_ID', 'CC4C_MESSAGING_PAYLOAD_KEYS',
    'CC4C_MESSAGING_CONFIRM_TIMEOUT', 'CC4C_MESSAGING_CONSUMER_RETRY_DELAYS',
    'CC4C_OUTBOX_DISPATCHER_ENABLED', 'CC4C_MESSAGE_CONSUMERS_ENABLED', 'CC4C_API_DOCS_ENABLED',
    'CC4C_OBSERVABILITY_ENABLED', 'CC4C_MANAGEMENT_ADDRESS', 'CC4C_MANAGEMENT_PORT',
    'CC4C_MANAGEMENT_USERNAME', 'CC4C_MANAGEMENT_PASSWORD', 'CC4C_OBSERVABILITY_ENVIRONMENT',
    'CC4C_LOG_FORMAT', 'CC4C_SAVE_IMG_PATH', 'CC4C_REQUEST_IMG_PATH',
    'CC4C_SAVE_AVATAR_PATH', 'CC4C_REQUEST_AVATAR_PATH'
)
$script:Cc4cRuntimeDefaults = [ordered]@{
    CC4C_DB_CONNECTION_TIMEOUT_MS = '3000'
    CC4C_DB_VALIDATION_TIMEOUT_MS = '1000'
    CC4C_MESSAGING_SAMPLE_INTERVAL = '15s'
    CC4C_MAX_HTTP_URI_TAGS = '100'
    CC4C_MAIL_AUTH = 'false'
    CC4C_MAIL_SSL_ENABLED = 'false'
    CC4C_MAIL_STARTTLS_ENABLED = 'false'
}
$script:Cc4cRuntimeAllowedNames = @($script:Cc4cRuntimeRequiredNames) + @($script:Cc4cRuntimeDefaults.Keys)

# 返回从脚本位置计算的唯一工作区根；不从环境变量接收替代项目目录。
function Get-Cc4cHostWorkspaceRoot {
    return $script:Cc4cHostWorkspaceRoot
}

# 仅检查目标及父路径元数据，拒绝网络路径、链接、特殊文件和非目录父节点；不枚举内容。
function Assert-Cc4cOrdinaryPath {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [ValidateSet('File', 'Directory')][string] $Kind = 'File',
        [switch] $AllowMissing,
        [switch] $OutsideWorkspace
    )
    try {
        if ($Path -notmatch '^[A-Za-z]:[\\/]') { throw 'not a local absolute path' }
        $absolute = [IO.Path]::GetFullPath($Path)
        $root = $script:Cc4cHostWorkspaceRoot.TrimEnd('\')
        if ($OutsideWorkspace -and ($absolute.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or
                $absolute.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase))) {
            throw 'must remain outside the workspace'
        }
        if (-not $AllowMissing -and -not (Test-Path -LiteralPath $absolute -ErrorAction Stop)) {
            throw 'required path is absent'
        }
        $cursor = $absolute
        while ($cursor) {
            if (Test-Path -LiteralPath $cursor -ErrorAction Stop) {
                $item = Get-Item -LiteralPath $cursor -Force -ErrorAction Stop
                if (($item.Attributes -band ([IO.FileAttributes]::ReparsePoint -bor [IO.FileAttributes]::Device)) -or
                    -not [string]::IsNullOrEmpty([string] $item.LinkType)) { throw 'links are not allowed' }
                $directoryExpected = $cursor -ne $absolute -or $Kind -eq 'Directory'
                if ([bool] $item.PSIsContainer -ne $directoryExpected) { throw 'unexpected path type' }
            }
            $parent = [IO.Directory]::GetParent($cursor)
            $cursor = if ($null -eq $parent) { $null } else { $parent.FullName }
        }
        return $absolute
    }
    catch {
        throw 'A required local path is missing, redirected, or has an unsafe file type.'
    }
}

# 只读取固定应用目录下的 .env.local；值按字面保留，不展开变量、不执行表达式、不回显正文。
function Read-Cc4cEnvironmentFile {
    param([Parameter(Mandatory = $true)][ValidateSet('Runtime', 'Frontend', 'Observability')][string] $Kind)
    $directory = switch ($Kind) { 'Runtime' { 'backend' }; 'Frontend' { 'frontend' }; 'Observability' { 'observability' } }
    $path = Join-Path $script:Cc4cHostWorkspaceRoot "$directory\.env.local"
    # Vite 会自行加载模式配置；只检查这些精确路径的存在性，拒绝额外入口而不读取内容。
    if ($Kind -ne 'Runtime') {
        foreach ($other in @('.env', '.env.development', '.env.development.local', '.env.production', '.env.production.local')) {
            if (Test-Path -LiteralPath (Join-Path (Join-Path $script:Cc4cHostWorkspaceRoot $directory) $other)) {
                throw "Only $directory/.env.local is supported; preserve and manually resolve the extra environment file."
            }
        }
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing $directory/.env.local. Prepare it manually from $directory/.env.example."
    }
    $null = Assert-Cc4cOrdinaryPath $path
    $allowed = if ($Kind -eq 'Runtime') { $script:Cc4cRuntimeAllowedNames } else { @('VITE_API_BASE_URL') }
    $values = [ordered]@{}
    $lineNumber = 0
    try { $lines = @(Get-Content -LiteralPath $path -Encoding utf8 -ErrorAction Stop) }
    catch { throw "Unable to read the $Kind environment file." }
    foreach ($line in $lines) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) { throw "Invalid $Kind environment syntax on line $lineNumber." }
        $name = $line.Substring(0, $separator).Trim()
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$' -or $allowed -notcontains $name) {
            throw "Unsupported $Kind environment variable on line $lineNumber; use the current example."
        }
        if ($values.Contains($name)) { throw "Duplicate $Kind environment variable '$name'." }
        $values[$name] = $line.Substring($separator + 1)
    }
    if ($Kind -eq 'Runtime') {
        foreach ($name in $script:Cc4cRuntimeDefaults.Keys) {
            if (-not $values.Contains($name)) { $values[$name] = $script:Cc4cRuntimeDefaults[$name] }
        }
    } else {
        if (-not $values.Contains('VITE_API_BASE_URL') -or [string]::IsNullOrWhiteSpace($values.VITE_API_BASE_URL)) {
            throw 'VITE_API_BASE_URL is required.'
        }
        $api = Get-Cc4cEndpointUri $values.VITE_API_BASE_URL @('http', 'https') 'Frontend API'
        if ($api.UserInfo -or $api.Query -or $api.Fragment) { throw 'Frontend API URL must not contain credentials, query, or fragment.' }
    }
    return [pscustomobject]@{ Values = $values }
}

# 解析带凭据的连接地址时只返回 URI 给调用方，失败信息不包含原始 URL。
function Get-Cc4cEndpointUri {
    param([string] $Value, [string[]] $Schemes, [string] $Description)
    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref] $uri) -or
        $Schemes -notcontains $uri.Scheme -or [string]::IsNullOrWhiteSpace($uri.Host)) {
        throw "$Description endpoint is invalid."
    }
    return $uri
}

# 验证严格布尔值，避免未填写的模板被隐式转换成 true。
function Assert-Cc4cBooleanValue {
    param([System.Collections.IDictionary] $Values, [string] $Name)
    if ($Values[$Name] -cnotin @('true', 'false')) { throw "$Name must be true or false." }
}

# 返回精确数据库名，不连接数据库；调用方须另行与用户确认值进行区分大小写的比较。
function Get-Cc4cDatabaseName {
    param([System.Collections.IDictionary] $Values)
    if ([string] $Values.CC4C_DB_URL -notmatch '^jdbc:mysql://[^/]+/(?<database>[A-Za-z0-9_]+)(?:\?.*)?$') {
        throw 'CC4C_DB_URL must name one explicit MySQL database.'
    }
    return [string] $Matches.database
}

# 合并所有仍有效的启动约束；只检查配置形状，不查询数据、不打印凭据、不生成缺失值。
function Assert-Cc4cRuntimeEnvironment {
    param([Parameter(Mandatory = $true)] $Environment, [ValidateRange(1, 65535)][int] $ManagementPort = 4081)
    $values = $Environment.Values
    foreach ($name in $script:Cc4cRuntimeRequiredNames) {
        if (-not $values.Contains($name)) { throw "Required runtime variable '$name' is missing." }
        if ($name -notin @('CC4C_MAIL_USERNAME', 'CC4C_MAIL_PASSWORD') -and
            [string]::IsNullOrWhiteSpace([string] $values[$name])) { throw "Required runtime variable '$name' is empty." }
    }
    foreach ($name in @('CC4C_SESSION_COOKIE_SECURE', 'CC4C_BUSINESS_CACHE_ENABLED', 'CC4C_API_DOCS_ENABLED',
            'CC4C_OBSERVABILITY_ENABLED', 'CC4C_OUTBOX_DISPATCHER_ENABLED', 'CC4C_MESSAGE_CONSUMERS_ENABLED',
            'CC4C_MAIL_AUTH', 'CC4C_MAIL_SSL_ENABLED', 'CC4C_MAIL_STARTTLS_ENABLED')) {
        Assert-Cc4cBooleanValue $values $name
    }
    $null = Get-Cc4cDatabaseName $values
    $null = Get-Cc4cEndpointUri $values.CC4C_REDIS_URL @('redis', 'rediss') 'Redis'
    $null = Get-Cc4cEndpointUri $values.CC4C_RABBITMQ_URL @('amqp', 'amqps') 'RabbitMQ'
    foreach ($name in @('CC4C_SESSION_NAMESPACE', 'CC4C_CACHE_NAMESPACE')) {
        if ([string] $values[$name] -notmatch '^[A-Za-z0-9:_-]{3,120}$') { throw "$name contains unsupported characters." }
    }
    if ($values.CC4C_SESSION_NAMESPACE -ceq $values.CC4C_CACHE_NAMESPACE) {
        throw 'Session and business cache namespaces must differ.'
    }
    if (([string] $values.CC4C_SECURITY_PEPPER).Length -lt 32) { throw 'CC4C_SECURITY_PEPPER must contain at least 32 characters.' }
    if (([string] $values.CC4C_MANAGEMENT_PASSWORD).Length -lt 24) { throw 'CC4C_MANAGEMENT_PASSWORD must contain at least 24 characters.' }
    if ([string] $values.CC4C_MANAGEMENT_ADDRESS -cne '127.0.0.1' -or
        [string] $values.CC4C_MANAGEMENT_PORT -cne [string] $ManagementPort) { throw 'The management endpoint must use the confirmed loopback port.' }
    foreach ($origin in ([string] $values.CC4C_ALLOWED_ORIGINS).Split(',')) {
        $uri = Get-Cc4cEndpointUri $origin.Trim() @('http', 'https') 'CORS origin'
        if ($origin.Contains('*') -or $uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment -or $uri.UserInfo) {
            throw 'CORS requires exact origins without wildcards, paths, or credentials.'
        }
    }
    $mailPort = 0
    if (-not [int]::TryParse([string] $values.CC4C_MAIL_PORT, [ref] $mailPort) -or $mailPort -lt 1 -or $mailPort -gt 65535) {
        throw 'CC4C_MAIL_PORT must be between 1 and 65535.'
    }
    if ($values.CC4C_MAIL_AUTH -ceq 'true' -and
        ([string]::IsNullOrWhiteSpace($values.CC4C_MAIL_USERNAME) -or [string]::IsNullOrWhiteSpace($values.CC4C_MAIL_PASSWORD))) {
        throw 'SMTP authentication requires both username and password.'
    }
    if ($values.CC4C_MAIL_SSL_ENABLED -ceq 'true' -and $values.CC4C_MAIL_STARTTLS_ENABLED -ceq 'true') {
        throw 'Choose SMTP implicit SSL or STARTTLS, not both.'
    }
    if ($values.CC4C_OBSERVABILITY_ENVIRONMENT -notmatch '^[a-z0-9-]{2,32}$' -or $values.CC4C_LOG_FORMAT -cne 'ecs') {
        throw 'A valid environment label and ECS log format are required.'
    }
    if ($values.CC4C_MESSAGING_SAMPLE_INTERVAL -notmatch '^[1-9]\d*(ms|s|m)$') { throw 'Messaging sample interval must be a positive duration.' }
    $connectionTimeout = 0
    $validationTimeout = 0
    $maximumTags = 0
    if (-not [int]::TryParse([string] $values.CC4C_DB_CONNECTION_TIMEOUT_MS, [ref] $connectionTimeout) -or
        $connectionTimeout -lt 250 -or $connectionTimeout -gt 60000) { throw 'Database connection timeout must be between 250 and 60000 ms.' }
    if (-not [int]::TryParse([string] $values.CC4C_DB_VALIDATION_TIMEOUT_MS, [ref] $validationTimeout) -or
        $validationTimeout -lt 250 -or $validationTimeout -ge $connectionTimeout) { throw 'Database validation timeout must be at least 250 ms and below connection timeout.' }
    if (-not [int]::TryParse([string] $values.CC4C_MAX_HTTP_URI_TAGS, [ref] $maximumTags) -or
        $maximumTags -lt 1 -or $maximumTags -gt 1000) { throw 'HTTP route tag limit must be between 1 and 1000.' }
    return $values
}

# 注入本次启动所需变量；前端清除已知后端运行变量，只注入公开地址和两个上传根变量。
function Set-Cc4cProcessEnvironment {
    param([System.Collections.IDictionary] $Values, [switch] $Backend, [switch] $Frontend)
    if ($Backend -and $Frontend) { throw 'Choose one application environment.' }
    $names = @($Values.Keys)
    if ($Backend -or $Frontend) { $names += @('SPRING_CONFIG_NAME', 'SPRING_APPLICATION_NAME', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT') }
    if ($Frontend) { $names += $script:Cc4cRuntimeAllowedNames }
    $names = @($names | Sort-Object -Unique)
    $original = [ordered]@{}
    foreach ($name in $names) { $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    $snapshot = [pscustomobject]@{ Names = $names; Values = $original }
    try {
        if ($Frontend) { foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') } }
        foreach ($name in $Values.Keys) { [Environment]::SetEnvironmentVariable($name, [string] $Values[$name], 'Process') }
        if ($Backend) {
            [Environment]::SetEnvironmentVariable('SPRING_CONFIG_NAME', 'application', 'Process')
            [Environment]::SetEnvironmentVariable('SPRING_APPLICATION_NAME', 'CC4C', 'Process')
            # 限定到已打包的脱敏配置，避免默认搜索工作目录或继承外部配置入口。
            [Environment]::SetEnvironmentVariable('SPRING_CONFIG_LOCATION', 'classpath:/application.yml', 'Process')
            [Environment]::SetEnvironmentVariable('SPRING_CONFIG_ADDITIONAL_LOCATION', $null, 'Process')
            [Environment]::SetEnvironmentVariable('SPRING_CONFIG_IMPORT', $null, 'Process')
        }
        return $snapshot
    }
    catch {
        Restore-Cc4cProcessEnvironment $snapshot
        throw 'Unable to prepare the application process environment.'
    }
}

# 恢复所有被本次临时注入或清除的进程变量，包括原来不存在的变量。
function Restore-Cc4cProcessEnvironment {
    param([Parameter(Mandatory = $true)] $Snapshot)
    foreach ($name in $Snapshot.Names) { [Environment]::SetEnvironmentVariable($name, $Snapshot.Values[$name], 'Process') }
}

# 状态根固定在当前工作区；读取时不创建目录，避免只读健康查询产生文件。
function Get-Cc4cHostStateRoot {
    return Assert-Cc4cOrdinaryPath (Join-Path $script:Cc4cHostWorkspaceRoot 'temp\cc4c-host-stack') -Kind Directory -AllowMissing
}

# 只读当前应用状态文件，不读取日志、其他 temp 内容或旧备份。
function Read-Cc4cHostState {
    param([ValidateSet('backend', 'frontend', 'stack')][string] $Name)
    $path = Join-Path (Get-Cc4cHostStateRoot) "$Name.json"
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $null = Assert-Cc4cOrdinaryPath $path
    try { return Get-Content -LiteralPath $path -Raw -Encoding utf8 -ErrorAction Stop | ConvertFrom-Json }
    catch { throw "The $Name process record cannot be read; preserve it for inspection." }
}

# 仅更新受控应用状态；拒绝链接，并保留其他运行日志和所有外部服务状态。
function Write-Cc4cHostState {
    param([ValidateSet('backend', 'frontend', 'stack')][string] $Name, [Parameter(Mandatory = $true)] $State)
    $root = Get-Cc4cHostStateRoot
    if (-not (Test-Path -LiteralPath $root)) { New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null }
    $path = Assert-Cc4cOrdinaryPath (Join-Path $root "$Name.json") -AllowMissing
    [IO.File]::WriteAllText($path, ($State | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
}

# 每次启动分配全新日志名；不读取、移动或覆盖过去运行的日志。
function New-Cc4cRunLogs {
    param([ValidateSet('backend', 'frontend')][string] $Name)
    $root = Assert-Cc4cOrdinaryPath (Join-Path $script:Cc4cHostWorkspaceRoot "temp\cc4c-host-$Name") -Kind Directory -AllowMissing
    if (-not (Test-Path -LiteralPath $root)) { New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null }
    $runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ') + '-' + [Guid]::NewGuid().ToString('N')
    $stdout = Join-Path $root "$Name.$runId.stdout.log"
    $stderr = Join-Path $root "$Name.$runId.stderr.log"
    if ((Test-Path -LiteralPath $stdout) -or (Test-Path -LiteralPath $stderr)) { throw 'A new log path unexpectedly exists.' }
    return [pscustomobject]@{ Stdout = $stdout; Stderr = $stderr; RunId = $runId }
}

# 统一真实创建时间格式；CIM 按微秒提供进程时间，避免 JSON 自动转换 DateTime 后比较失真。
function Get-Cc4cProcessTime {
    param([Parameter(Mandatory = $true)] $Value)
    try {
        $time = if ($Value -is [DateTime]) { [DateTimeOffset] $Value } else { [DateTimeOffset]::Parse([string] $Value, [Globalization.CultureInfo]::InvariantCulture) }
        return $time.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.ffffffZ', [Globalization.CultureInfo]::InvariantCulture)
    }
    catch { throw 'A recorded process creation time is invalid.' }
}

# 对刚启动且仍存活的 Process 对象取得 CIM 身份，不以当前墙钟时间代替进程真实创建时间。
function Get-Cc4cStartedIdentity {
    param([Parameter(Mandatory = $true)] $Process, [string] $Executable, [string] $Marker, [string] $Component)
    if ($Process.HasExited) { return $null }
    $current = Get-CimInstance Win32_Process -Filter "ProcessId = $($Process.Id)" -ErrorAction Stop
    if ($Process.HasExited) { return $null }
    if ($null -eq $current -or [string]::IsNullOrWhiteSpace($current.ExecutablePath) -or
        [string]::IsNullOrWhiteSpace($current.CommandLine) -or $null -eq $current.CreationDate -or
        -not [IO.Path]::GetFullPath($current.ExecutablePath).Equals([IO.Path]::GetFullPath($Executable), [StringComparison]::OrdinalIgnoreCase) -or
        ([string] $current.CommandLine).IndexOf($Marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw 'The created application process identity cannot be verified.'
    }
    return [ordered]@{
        schemaVersion = 2
        component = $Component
        pid = [int] $current.ProcessId
        executablePath = [IO.Path]::GetFullPath($Executable)
        marker = $Marker
        processCreatedAtUtc = Get-Cc4cProcessTime $current.CreationDate
        status = 'running'
    }
}

# 校验 PID、绝对可执行文件、完整应用标记和真实创建时间；缺失或不匹配时拒绝认领进程。
function Get-Cc4cRecordedProcess {
    param([Parameter(Mandatory = $true)] $State)
    $recordedPid = 0
    if (-not [int]::TryParse([string] $State.pid, [ref] $recordedPid) -or $recordedPid -le 0 -or
        $State.component -notin @('backend', 'frontend') -or
        [string]::IsNullOrWhiteSpace([string] $State.executablePath) -or
        -not [IO.Path]::IsPathFullyQualified([string] $State.executablePath) -or
        [string]::IsNullOrWhiteSpace([string] $State.marker) -or
        -not [IO.Path]::IsPathFullyQualified([string] $State.marker) -or
        $null -eq $State.processCreatedAtUtc) { throw 'Incomplete process identity; preserve the record for inspection.' }
    $relativeMarker = if ($State.component -eq 'backend') { 'backend\target\cc4c-5.0.0-SNAPSHOT.jar' }
    else { 'frontend\node_modules\vite\bin\vite.js' }
    $expectedMarker = Join-Path $script:Cc4cHostWorkspaceRoot $relativeMarker
    $expectedExecutableName = if ($State.component -eq 'backend') { 'java.exe' } else { 'node.exe' }
    if ($State.schemaVersion -ne 2 -or
        -not [IO.Path]::GetFullPath([string] $State.marker).Equals($expectedMarker, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName([string] $State.executablePath) -ine $expectedExecutableName) {
        throw 'The record does not identify this workspace application; nothing was changed.'
    }
    $expectedTime = Get-Cc4cProcessTime $State.processCreatedAtUtc
    $current = Get-CimInstance Win32_Process -Filter "ProcessId = $recordedPid" -ErrorAction Stop
    if ($null -eq $current) { return $null }
    if ([string]::IsNullOrWhiteSpace($current.ExecutablePath) -or [string]::IsNullOrWhiteSpace($current.CommandLine) -or
        $null -eq $current.CreationDate -or
        -not [IO.Path]::GetFullPath($current.ExecutablePath).Equals([IO.Path]::GetFullPath([string] $State.executablePath), [StringComparison]::OrdinalIgnoreCase) -or
        (Get-Cc4cProcessTime $current.CreationDate) -cne $expectedTime -or
        ([string] $current.CommandLine).IndexOf([string] $State.marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw 'Recorded PID identity differs; no process was changed.'
    }
    return $current
}

# 已停止的旧记录可正常替换；存活、未知或不完整的运行记录不允许被新启动覆盖。
function Assert-Cc4cCanStart {
    param([ValidateSet('backend', 'frontend')][string] $Name)
    $state = Read-Cc4cHostState $Name
    if ($null -eq $state -or $state.status -eq 'stopped') { return }
    if ($state.status -ne 'running') { throw 'Unknown process state; inspect it before starting.' }
    if ($null -ne (Get-Cc4cRecordedProcess $state)) { throw "A recorded $Name process is already running." }
}

# 通过已打开的 Process 句柄停止唯一目标，再等待退出；不按名称扫描，不结束子树或中间件。
function Stop-Cc4cExactProcess {
    param([Parameter(Mandatory = $true)] $State)
    $current = Get-Cc4cRecordedProcess $State
    if ($null -eq $current) { return }
    $process = $null
    try {
        $process = Get-Process -Id ([int] $State.pid) -ErrorAction Stop
        $null = $process.Handle
        if ($process.HasExited) { return }
        if ((Get-Cc4cProcessTime $process.StartTime) -cne (Get-Cc4cProcessTime $State.processCreatedAtUtc)) {
            throw 'The process changed before stop; nothing was terminated.'
        }
        if ($null -eq (Get-Cc4cRecordedProcess $State)) { return }
        $process.Kill()
        if (-not $process.WaitForExit(10000)) { throw 'The exact application process did not stop within ten seconds.' }
    }
    finally { if ($null -ne $process) { $process.Dispose() } }
}

# 停止已保存的应用身份；仅当当前状态仍属于该代进程时更新记录，避免覆盖另一轮启动。
function Stop-Cc4cOwnedComponent {
    param([Parameter(Mandatory = $true)] $State)
    if ($State.status -eq 'stopped') { return }
    Stop-Cc4cExactProcess $State
    $current = Read-Cc4cHostState $State.component
    if ($null -eq $current -or $current.pid -ne $State.pid -or
        (Get-Cc4cProcessTime $current.processCreatedAtUtc) -cne (Get-Cc4cProcessTime $State.processCreatedAtUtc)) {
        throw 'The process stopped, but its state record changed; the newer record was preserved.'
    }
    $updated = [ordered]@{}
    foreach ($property in $current.PSObject.Properties) { $updated[$property.Name] = $property.Value }
    $updated.status = 'stopped'
    $updated.stoppedAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-Cc4cHostState $State.component $updated
}

# 只查询外部 Prometheus 的公开状态接口；不读取私有配置，不修改服务或存储。
function Assert-Cc4cPrometheusEndpoint {
    param([string] $BaseUrl = 'http://127.0.0.1:9090', [switch] $RequireBackendScrape)
    $uri = Get-Cc4cEndpointUri $BaseUrl @('http', 'https') 'Prometheus'
    if ($uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw 'Prometheus URL must not contain credentials, query, or fragment.' }
    $base = $uri.AbsoluteUri.TrimEnd('/')
    try {
        $ready = Invoke-WebRequest -Uri "$base/-/ready" -TimeoutSec 5 -MaximumRedirection 0
        $build = Invoke-RestMethod -Uri "$base/api/v1/status/buildinfo" -TimeoutSec 5 -MaximumRedirection 0
        if ([int] $ready.StatusCode -ne 200 -or $build.status -cne 'success' -or $build.data.version -cne '3.13.2') {
            throw 'unexpected readiness or version'
        }
        if ($RequireBackendScrape) {
            $query = [Uri]::EscapeDataString('up{job="cc4c-backend"}')
            $response = Invoke-RestMethod -Uri "$base/api/v1/query?query=$query" -TimeoutSec 5 -MaximumRedirection 0
            $results = @($response.data.result)
            if ($response.status -cne 'success' -or $response.data.resultType -cne 'vector' -or $results.Count -eq 0 -or
                @($results | Where-Object { $_.value.Count -ne 2 -or [string] $_.value[1] -cne '1' }).Count -gt 0) {
                throw 'backend scrape is not healthy'
            }
        }
    }
    catch { throw 'External Prometheus readiness, version, or required backend scrape check failed; no service was changed.' }
    Write-Output 'External Prometheus is ready (3.13.2).'
    if ($RequireBackendScrape) { Write-Output 'up{job="cc4c-backend"} = 1.' }
}

# 栈记录必须包含两端完整身份快照；旧的已停止记录不走此函数，旧运行记录不可猜测补全。
function Assert-Cc4cRunningStack {
    param([Parameter(Mandatory = $true)] $State)
    if ($State.status -ne 'running' -or $State.schemaVersion -ne 2 -or
        $State.component -ne 'stack' -or [string]::IsNullOrWhiteSpace([string] $State.runId) -or
        $null -eq $State.backend -or $null -eq $State.frontend -or
        $State.backend.component -ne 'backend' -or $State.frontend.component -ne 'frontend') {
        throw 'Incomplete running stack record; preserve it for inspection.'
    }
    foreach ($entry in @($State.backend, $State.frontend)) {
        $null = Get-Cc4cRecordedProcess $entry
        $current = Read-Cc4cHostState $entry.component
        if ($null -eq $current -or $current.pid -ne $entry.pid -or
            (Get-Cc4cProcessTime $current.processCreatedAtUtc) -cne (Get-Cc4cProcessTime $entry.processCreatedAtUtc)) {
            throw 'The stack no longer owns the current application record; nothing was changed.'
        }
    }
}

# 管理端健康只读 UP 状态，不带管理认证，不输出依赖详情；三个接口必须全部成功。
function Assert-Cc4cBackendHealth {
    param([ValidateRange(1, 65535)][int] $ManagementPort)
    foreach ($endpoint in @('health', 'health/liveness', 'health/readiness')) {
        try {
            $result = Invoke-RestMethod -Uri "http://127.0.0.1:$ManagementPort/actuator/$endpoint" -TimeoutSec 3 -MaximumRedirection 0
            if ($result.status -cne 'UP') { throw 'not UP' }
        }
        catch { throw "Backend $endpoint is not UP." }
    }
}
