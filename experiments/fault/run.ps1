# C：故障隔离实验 adapter
# 由 experiments/run.ps1 调用。不要在这里写 result.json、采集环境或诊断哈希。

param(
    [Parameter(Mandatory = $true)][string]$RunDirectory,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market",
    [string]$BaseUrl = "http://host.docker.internal:18080"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:Passed = $true
$script:Failures = New-Object System.Collections.Generic.List[string]
$script:ApiBase = $null
$forward = $null
$scaledMarketplaceDown = $false
$startedAt = [DateTimeOffset]::UtcNow

function Add-Failure([string]$Message) {
    $script:Passed = $false
    $script:Failures.Add($Message)
    Write-Warning $Message
}

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message"
}

function Save-Utf8([string]$Relative, [string]$Content) {
    $path = Join-Path $RunDirectory $Relative
    $parent = Split-Path $path
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [IO.File]::WriteAllText($path, $Content, [Text.UTF8Encoding]::new($false))
}

function Save-Json([string]$Relative, $Object) {
    Save-Utf8 $Relative ($Object | ConvertTo-Json -Depth 8)
}

function Wait-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutSeconds) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $task = $client.ConnectAsync($HostName, $Port)
            if ($task.Wait(1000) -and $client.Connected) { return }
        } catch { } finally { $client.Dispose() }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Timed out waiting for ${HostName}:$Port."
}

function Test-TcpPort([string]$HostName, [int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        return ($task.Wait(400) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-MysqlRootPassword {
    $secretFile = Join-Path $repoRoot "k8s\overlays\ci\.env.secret"
    if (Test-Path -LiteralPath $secretFile) {
        foreach ($line in Get-Content -LiteralPath $secretFile) {
            if ($line -match '^MYSQL_ROOT_PASSWORD=(.+)$') { return $Matches[1] }
        }
    }
    $encoded = & kubectl --context $Context -n $Namespace get secret campus-secrets -o jsonpath="{.data.MYSQL_ROOT_PASSWORD}"
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($encoded)) {
        throw "Cannot read MYSQL_ROOT_PASSWORD from campus-secrets."
    }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded))
}

function Invoke-Mysql([string]$Password, [string]$Database, [string]$Sql) {
    $output = & kubectl --context $Context -n $Namespace exec statefulset/campus-mysql -- `
        env "MYSQL_PWD=$Password" mysql -uroot -D $Database -Nse $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed: $Sql" }
    return ($output | Out-String).Trim()
}

function Get-OrderCount([string]$Password) {
    return [int](Invoke-Mysql $Password "campus_trading" "SELECT COUNT(*) FROM trade_orders")
}

function Get-PodReady([string]$App) {
    $ready = & kubectl --context $Context -n $Namespace get pod -l "app=$App" `
        -o jsonpath="{.items[0].status.containerStatuses[0].ready}"
    return $ready -eq "true"
}

function Get-RestartCount([string]$App) {
    $value = & kubectl --context $Context -n $Namespace get pod -l "app=$App" `
        -o jsonpath="{.items[0].status.containerStatuses[0].restartCount}"
    if ([string]::IsNullOrWhiteSpace($value)) { return 0 }
    return [int]$value
}

function Get-Csrf($Session) {
    $csrf = Invoke-RestMethod -Uri "$script:ApiBase/api/auth/csrf" -WebSession $Session -TimeoutSec 30
    if (-not $csrf.success -or [string]::IsNullOrWhiteSpace([string]$csrf.data)) {
        throw "CSRF token unavailable"
    }
    return [string]$csrf.data
}

function Read-HttpHeader($Headers, [string]$Name) {
    if (-not $Headers) { return $null }
    try {
        $value = $Headers[$Name]
        if ($null -eq $value) { return $null }
        if ($value -is [System.Array]) { return [string]$value[0] }
        return [string]$value
    } catch {
        return $null
    }
}

function Invoke-CampusRequest {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Method,
        [string]$Path,
        [string]$Csrf,
        $Body,
        [int]$TimeoutSec = 15
    )
    $headers = @{ "X-Correlation-Id" = ([guid]::NewGuid().ToString("N").Substring(0, 16)) }
    if ($Csrf) { $headers["X-XSRF-TOKEN"] = $Csrf }
    $params = @{
        Uri = "$script:ApiBase$Path"
        Method = $Method
        WebSession = $Session
        Headers = $headers
        TimeoutSec = $TimeoutSec
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $json = $Body | ConvertTo-Json -Compress -Depth 6
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest @params
        $watch.Stop()
        $payload = $null
        if ($response.Content) { $payload = $response.Content | ConvertFrom-Json }
        return @{
            StatusCode = [int]$response.StatusCode
            Payload = $payload
            RetryAfter = Read-HttpHeader $response.Headers "Retry-After"
            CorrelationId = Read-HttpHeader $response.Headers "X-Correlation-Id"
            ElapsedMs = [int]$watch.ElapsedMilliseconds
        }
    } catch {
        $watch.Stop()
        $http = $_.Exception.Response
        if (-not $http) { throw }
        $status = [int]$http.StatusCode
        $content = $null
        $retryAfter = $null
        try {
            $stream = $http.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $raw = $reader.ReadToEnd()
                if ($raw) { $content = $raw | ConvertFrom-Json }
            }
        } catch { }
        try { $retryAfter = Read-HttpHeader $http.Headers "Retry-After" } catch { }
        return @{
            StatusCode = $status
            Payload = $content
            RetryAfter = $retryAfter
            CorrelationId = $headers["X-Correlation-Id"]
            ElapsedMs = [int]$watch.ElapsedMilliseconds
        }
    }
}

function Connect-CampusUser($Email, $Password) {
    $last = $null
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
        $csrf = Get-Csrf $session
        $login = Invoke-CampusRequest -Session $session -Method POST -Path "/api/auth/login" -Csrf $csrf -Body @{
            email = $Email
            password = $Password
        } -TimeoutSec 30
        if ($login.StatusCode -eq 200 -and $login.Payload -and $login.Payload.success) {
            return @{ Session = $session; Csrf = (Get-Csrf $session) }
        }
        $last = $login
        Start-Sleep -Seconds $attempt
    }
    $message = $null
    if ($last -and $last.Payload) { $message = $last.Payload.message }
    throw "Login failed for $Email : $($last.StatusCode) $message"
}

function Get-CircuitTransitions([string]$Logs) {
    $transitions = @()
    foreach ($match in [regex]::Matches($Logs, 'marketplace circuit transition from=([A-Z_]+) to=([A-Z_]+)')) {
        if ($transitions.Count -eq 0) { $transitions += $match.Groups[1].Value }
        $transitions += $match.Groups[2].Value
    }
    return $transitions
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is not installed"
}

$uri = [Uri]$BaseUrl
$forwardPort = $uri.Port
if ($forwardPort -le 0) {
    if ($uri.Scheme -eq "https") { $forwardPort = 443 } else { $forwardPort = 80 }
}
$localHost = $uri.Host
if ($localHost -eq "host.docker.internal") { $localHost = "127.0.0.1" }
$script:ApiBase = "{0}://{1}:{2}" -f $uri.Scheme, $localHost, $forwardPort

$orderCountBefore = $null
$orderCountDuring = $null
$orderCountAfter = $null
$faultCalls = @()
$maxLatency = $null
$otherReady = $false
$tradingRestartsBefore = 0
$tradingRestartsAfter = 0
$tradingRestarted = $false
$existingOrderId = $null
$recoveredOrderId = $null
$transitions = @()
$summary = $null

try {
    Write-Step "Port-forward Web on $forwardPort"
    if (-not (Test-TcpPort -HostName $localHost -Port $forwardPort)) {
        $kubectl = (Get-Command kubectl -ErrorAction Stop).Source
        $forwardLog = Join-Path $RunDirectory "port-forward.log"
        $forwardError = Join-Path $RunDirectory "port-forward-error.log"
        $forward = Start-Process -FilePath $kubectl -ArgumentList @(
            "--context", $Context, "-n", $Namespace, "port-forward", "service/web", "${forwardPort}:80"
        ) -RedirectStandardOutput $forwardLog -RedirectStandardError $forwardError -WindowStyle Hidden -PassThru
    }
    Wait-TcpPort -HostName $localHost -Port $forwardPort -TimeoutSeconds 30

    $mysqlPassword = Get-MysqlRootPassword
    Write-Step "Seed buyer, seller and admin"
    $mysqlPod = (& kubectl --context $Context -n $Namespace get pod -l app=campus-mysql -o jsonpath="{.items[0].metadata.name}").Trim()
    if ([string]::IsNullOrWhiteSpace($mysqlPod)) { throw "campus-mysql pod not found" }
    # kubectl cp on Windows treats "C:" as a pod name, so copy from a relative path.
    Push-Location $PSScriptRoot
    try {
        & kubectl --context $Context cp ".\seed.sql" "${Namespace}/${mysqlPod}:/tmp/fault-seed.sql"
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) { throw "Failed to copy fault seed SQL into MySQL pod" }
    & kubectl --context $Context -n $Namespace exec $mysqlPod -- env "MYSQL_PWD=$mysqlPassword" `
        sh -c "mysql -uroot --default-character-set=utf8mb4 < /tmp/fault-seed.sql"
    if ($LASTEXITCODE -ne 0) { throw "Failed to import fault seed SQL" }

    Write-Step "Create items and an existing purchase intent"
    $seller = Connect-CampusUser "e2e-seller@example.test" "abc123"
    $buyer = Connect-CampusUser "e2e-buyer@example.test" "abc123"
    $fixturePath = Join-Path $PSScriptRoot "item-fixture.json"
    $fixture = [IO.File]::ReadAllText($fixturePath, [Text.UTF8Encoding]::new($false)) | ConvertFrom-Json
    $stamp = Get-Date -Format "HHmmss"
    $itemBody = @{
        title = "$($fixture.existingTitlePrefix)$stamp"
        category = [string]$fixture.category
        price = 25
        description = [string]$fixture.description
        imageUrl = ""
        region = [string]$fixture.region
        tags = @($fixture.tags)
    }
    $faultBody = @{
        title = "$($fixture.faultTitlePrefix)$stamp"
        category = [string]$fixture.category
        price = 25
        description = [string]$fixture.description
        imageUrl = ""
        region = [string]$fixture.region
        tags = @($fixture.tags)
    }
    $existingItem = Invoke-CampusRequest -Session $seller.Session -Method POST -Path "/api/items" -Csrf $seller.Csrf -Body $itemBody
    $seller.Csrf = Get-Csrf $seller.Session
    $faultItem = Invoke-CampusRequest -Session $seller.Session -Method POST -Path "/api/items" -Csrf $seller.Csrf -Body $faultBody
    if ($existingItem.StatusCode -notin 200, 201 -or -not $existingItem.Payload.success) {
        throw "Failed to publish existing item: $($existingItem.StatusCode) $($existingItem.Payload.message)"
    }
    if ($faultItem.StatusCode -notin 200, 201 -or -not $faultItem.Payload.success) {
        throw "Failed to publish fault item: $($faultItem.StatusCode) $($faultItem.Payload.message)"
    }
    $existingItemId = $existingItem.Payload.data.id
    $faultItemId = $faultItem.Payload.data.id
    $buyer.Csrf = Get-Csrf $buyer.Session
    $existingOrder = Invoke-CampusRequest -Session $buyer.Session -Method POST -Path "/api/orders" -Csrf $buyer.Csrf -Body @{ itemId = $existingItemId }
    if ($existingOrder.StatusCode -notin 200, 201 -or -not $existingOrder.Payload.success) {
        throw "Failed to create baseline purchase intent: $($existingOrder.StatusCode) $($existingOrder.Payload.message)"
    }
    $existingOrderId = $existingOrder.Payload.data.id
    $orderCountBefore = Get-OrderCount $mysqlPassword
    $tradingRestartsBefore = Get-RestartCount "trading"

    Write-Step "Scale Marketplace to 0"
    & kubectl --context $Context -n $Namespace scale deployment/marketplace-service --replicas=0
    if ($LASTEXITCODE -ne 0) { throw "Failed to scale Marketplace to 0" }
    $scaledMarketplaceDown = $true
    $deadline = (Get-Date).AddSeconds(120)
    $marketplacePods = "pending"
    do {
        $marketplacePods = & kubectl --context $Context -n $Namespace get pod -l app=marketplace -o jsonpath="{.items[*].metadata.name}"
        if ([string]::IsNullOrWhiteSpace($marketplacePods)) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if (-not [string]::IsNullOrWhiteSpace($marketplacePods)) {
        throw "Marketplace pods are still present after scale-to-0"
    }

    Write-Step "Trip circuit with new purchase intents"
    for ($i = 0; $i -lt 8; $i++) {
        $buyer.Csrf = Get-Csrf $buyer.Session
        $call = Invoke-CampusRequest -Session $buyer.Session -Method POST -Path "/api/orders" -Csrf $buyer.Csrf -Body @{ itemId = $faultItemId } -TimeoutSec 5
        $faultCalls += $call
        $code = $null
        if ($call.Payload) { $code = $call.Payload.code }
        Write-Host ("  call {0}: HTTP {1} code={2} {3}ms" -f ($i + 1), $call.StatusCode, $code, $call.ElapsedMs)
    }
    $bad = @($faultCalls | Where-Object {
        $_.StatusCode -ne 503 -or
        -not $_.Payload -or
        $_.Payload.code -ne "PRODUCT_SERVICE_UNAVAILABLE" -or
        $_.RetryAfter -ne "1"
    })
    if ($bad.Count -gt 0) {
        Add-Failure "Fault-time purchase intents did not all return 503 PRODUCT_SERVICE_UNAVAILABLE with Retry-After 1"
    }
    $latencies = @($faultCalls | ForEach-Object { $_.ElapsedMs })
    $maxLatency = ($latencies | Measure-Object -Maximum).Maximum
    if ($maxLatency -gt 2500) {
        Add-Failure "Fault response latency $maxLatency ms exceeded the designed upper bound"
    }
    $orderCountDuring = Get-OrderCount $mysqlPassword
    if ($orderCountDuring -ne $orderCountBefore) {
        Add-Failure "Trading database order count changed during Marketplace outage ($orderCountBefore -> $orderCountDuring)"
    }

    $accountReady = Get-PodReady "account"
    $governanceReady = Get-PodReady "governance"
    $tradingReady = Get-PodReady "trading"
    $otherReady = $accountReady -and $governanceReady -and $tradingReady
    if (-not $otherReady) {
        Add-Failure "Account/Governance/Trading were not Ready while Marketplace was stopped"
    }
    $existingLookup = Invoke-CampusRequest -Session $buyer.Session -Method GET -Path "/api/orders"
    $listed = @($existingLookup.Payload.data)
    $foundExisting = $listed | Where-Object { $_.id -eq $existingOrderId }
    if ($existingLookup.StatusCode -ne 200 -or -not $foundExisting) {
        Add-Failure "Existing purchase intent could not be read from Trading while Marketplace was stopped"
    }

    Write-Step "Restore Marketplace and wait for circuit recovery"
    & kubectl --context $Context -n $Namespace scale deployment/marketplace-service --replicas=1
    if ($LASTEXITCODE -ne 0) { throw "Failed to scale Marketplace back to 1" }
    $scaledMarketplaceDown = $false
    & kubectl --context $Context -n $Namespace rollout status deployment/marketplace-service --timeout=180s
    if ($LASTEXITCODE -ne 0) { throw "Marketplace did not become Ready after restore" }
    Start-Sleep -Seconds 15
    $probe1 = $null
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        $buyer.Csrf = Get-Csrf $buyer.Session
        $probe1 = Invoke-CampusRequest -Session $buyer.Session -Method POST -Path "/api/orders" -Csrf $buyer.Csrf -Body @{ itemId = $faultItemId }
        if ($probe1.StatusCode -in 200, 201 -and $probe1.Payload.success) { break }
        Start-Sleep -Seconds 3
    }
    $buyer.Csrf = Get-Csrf $buyer.Session
    $probe2 = Invoke-CampusRequest -Session $buyer.Session -Method POST -Path "/api/orders" -Csrf $buyer.Csrf -Body @{ itemId = $faultItemId }
    if ($probe1.StatusCode -notin 200, 201 -or -not $probe1.Payload.success) {
        Add-Failure "Recovery probe did not create a new purchase intent: $($probe1.StatusCode) $($probe1.Payload.message)"
    } else {
        $recoveredOrderId = $probe1.Payload.data.id
    }
    $orderCountAfter = Get-OrderCount $mysqlPassword
    if ($orderCountAfter -le $orderCountBefore) {
        Add-Failure "No new purchase intent was stored after Marketplace recovered"
    }
    $tradingRestartsAfter = Get-RestartCount "trading"
    $tradingRestarted = $tradingRestartsAfter -gt $tradingRestartsBefore
    if ($tradingRestarted) {
        Add-Failure "Trading was restarted during the experiment; recovery must not require a restart"
    }

    $tradingLogLines = @(& kubectl --context $Context -n $Namespace logs deploy/trading-service --since=20m)
    $tradingLogs = $tradingLogLines -join "`n"
    Save-Utf8 "logs\trading-circuit.log" $tradingLogs
    $transitions = @(Get-CircuitTransitions ([string]$tradingLogs))
    $joined = ($transitions -join " -> ")
    Save-Utf8 "circuit-transitions.txt" $joined
    if ($joined -notmatch "CLOSED" -or $joined -notmatch "OPEN" -or $joined -notmatch "HALF_OPEN") {
        Add-Failure "Trading logs did not show CLOSED -> OPEN -> HALF_OPEN -> CLOSED (saw: $joined)"
    }
} catch {
    Add-Failure $_.Exception.Message
    throw
} finally {
    if ($scaledMarketplaceDown) {
        try {
            & kubectl --context $Context -n $Namespace scale deployment/marketplace-service --replicas=1 | Out-Null
        } catch { }
    }
    $samples = @()
    foreach ($call in $faultCalls) {
        $code = $null
        if ($call.Payload) { $code = $call.Payload.code }
        $samples += [ordered]@{
            status = $call.StatusCode
            code = $code
            retryAfter = $call.RetryAfter
            elapsedMs = $call.ElapsedMs
        }
    }
    $summary = [ordered]@{
        experiment = "fault"
        passed = [bool]$script:Passed
        startedAt = $startedAt.ToString("o")
        finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
        circuitTransitions = @($transitions)
        faultResponses = [ordered]@{
            status = 503
            code = "PRODUCT_SERVICE_UNAVAILABLE"
            retryAfter = "1"
            maxLatencyMs = $maxLatency
            samples = $samples
        }
        orderCountBefore = $orderCountBefore
        orderCountDuringFault = $orderCountDuring
        orderCountAfterRecovery = $orderCountAfter
        otherServicesReady = [bool]$otherReady
        tradingRestarted = [bool]$tradingRestarted
        existingOrderId = $existingOrderId
        recoveredOrderId = $recoveredOrderId
        failures = @($script:Failures)
    }
    Save-Json "fault-summary.json" $summary
    if ($forward) { Stop-Process -Id $forward.Id -Force -ErrorAction SilentlyContinue }
}

if (-not $script:Passed) {
    throw "Fault isolation experiment failed. See $(Join-Path $RunDirectory 'fault-summary.json')"
}
Write-Host "Fault isolation adapter passed. Summary: $(Join-Path $RunDirectory 'fault-summary.json')"
