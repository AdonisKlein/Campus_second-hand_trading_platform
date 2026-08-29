param(
    [ValidateSet("up", "status", "verify", "down")]
    [string]$Action = "up",
    [switch]$Mailpit,
    [string]$EnvFile
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$composeFile = Join-Path $repoRoot "deploy\docker-compose.yml"
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $repoRoot "deploy\.env"
} elseif (-not [IO.Path]::IsPathRooted($EnvFile)) {
    $EnvFile = Join-Path $repoRoot $EnvFile
}
$envFile = $EnvFile

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name. Install Docker Desktop and retry."
    }
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose --env-file $envFile -f $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed. Review the output above and retry." }
}

function Invoke-ComposeCapture([string[]]$Arguments) {
    $output = & docker compose --env-file $envFile -f $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed. Review the output above and retry." }
    return $output
}

function Assert-LocalConfiguration {
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw "Environment file not found. Copy deploy/.env.example to deploy/.env and fill random local secrets."
    }
    $required = @(
        "MYSQL_ROOT_PASSWORD", "ACCOUNT_DB_PASSWORD", "MARKETPLACE_DB_PASSWORD",
        "TRADING_DB_PASSWORD", "GOVERNANCE_DB_PASSWORD", "REDIS_PASSWORD",
        "RABBITMQ_USERNAME", "RABBITMQ_PASSWORD", "VERIFICATION_PEPPER",
        "INTERNAL_SERVICE_TOKEN", "INTERNAL_JWT_SECRET"
    )
    $present = @{}
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $present[$Matches[1]] = $Matches[2] }
    }
    foreach ($key in $required) {
        if (-not $present.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($present[$key])) {
            throw "Environment file is missing $key. Add a random local value and retry."
        }
        if ($present[$key] -notmatch '^[A-Za-z0-9_-]+$') {
            throw "$key may contain only letters, digits, underscores, and hyphens."
        }
    }
    foreach ($key in @("VERIFICATION_PEPPER", "INTERNAL_SERVICE_TOKEN", "INTERNAL_JWT_SECRET")) {
        if ($present[$key].Length -lt 32) { throw "$key must contain at least 32 characters." }
    }
    return $present
}

Assert-Command docker
if (-not (Test-Path -LiteralPath $composeFile)) { throw "deploy/docker-compose.yml was not found." }
$configuration = Assert-LocalConfiguration

$mailCompose = Join-Path $repoRoot "deploy\docker-compose.mailpit.yml"
$composeArgs = @()
if ($Mailpit) {
    if (-not (Test-Path -LiteralPath $mailCompose)) { throw "Mailpit Compose adapter was not found." }
    $composeArgs = @("-f", $mailCompose)
}

switch ($Action) {
    "up" {
        $args = $composeArgs + @("up", "-d", "--build", "--remove-orphans")
        Invoke-Compose $args
        Write-Host "Microservices are starting. Run -Action verify after startup. Mailpit: $Mailpit"
    }
    "status" {
        $args = $composeArgs + @("ps")
        Invoke-Compose $args
    }
    "verify" {
        $args = $composeArgs + @("ps", "--all")
        Invoke-Compose $args
        $expectedServices = @("mysql", "redis", "rabbitmq", "account-service", "marketplace-service", "trading-service", "governance-service", "api-gateway", "web")
        if ($Mailpit) { $expectedServices += "mailpit" }
        foreach ($service in $expectedServices) {
            $containerId = (Invoke-ComposeCapture ($composeArgs + @("ps", "-q", $service)) | Select-Object -First 1)
            if ([string]::IsNullOrWhiteSpace($containerId)) { throw "Service $service has no running container." }
            $state = (& docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' $containerId).Trim()
            if ($LASTEXITCODE -ne 0 -or $state -notmatch '^running healthy$') { throw "Service $service is not ready ($state)." }
        }

        $databaseScopes = @(
            @{ User = "account_user"; Password = $configuration.ACCOUNT_DB_PASSWORD; Own = "campus_account"; Foreign = "campus_marketplace" },
            @{ User = "marketplace_user"; Password = $configuration.MARKETPLACE_DB_PASSWORD; Own = "campus_marketplace"; Foreign = "campus_trading" },
            @{ User = "trading_user"; Password = $configuration.TRADING_DB_PASSWORD; Own = "campus_trading"; Foreign = "campus_governance" },
            @{ User = "governance_user"; Password = $configuration.GOVERNANCE_DB_PASSWORD; Own = "campus_governance"; Foreign = "campus_account" }
        )
        foreach ($scope in $databaseScopes) {
            & docker compose --env-file $envFile -f $composeFile exec -T -e "MYSQL_PWD=$($scope.Password)" mysql mysql -u $scope.User -D $scope.Own -Nse "SELECT COUNT(*) FROM flyway_schema_history" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "$($scope.User) cannot read its own Flyway history." }
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            & docker compose --env-file $envFile -f $composeFile exec -T -e "MYSQL_PWD=$($scope.Password)" mysql mysql -u $scope.User -D $scope.Foreign -Nse "SELECT 1" 2>$null | Out-Null
            $foreignExitCode = $LASTEXITCODE
            $ErrorActionPreference = $previousPreference
            if ($foreignExitCode -eq 0) { throw "$($scope.User) must not read $($scope.Foreign)." }
        }

        $webPort = if ($configuration.ContainsKey("WEB_PORT") -and $configuration.WEB_PORT) { $configuration.WEB_PORT } else { "80" }
        $origin = if ($webPort -eq "80") { "http://127.0.0.1" } else { "http://127.0.0.1:$webPort" }
        $health = Invoke-RestMethod -Uri "$origin/api/actuator/health/liveness" -TimeoutSec 15
        if ($health.status -ne "UP") { throw "Gateway liveness did not return UP. Check status and gateway logs." }
        try {
            $web = Invoke-WebRequest -Uri $origin -UseBasicParsing -TimeoutSec 15
            if ($web.StatusCode -lt 200 -or $web.StatusCode -ge 400) { throw "Web did not return a successful status." }
        } catch {
            throw "Web health check failed. Check status and gateway/web logs."
        }
        Write-Host "All containers, Gateway/Web, four Flyway schemas, and cross-database denial checks passed."
    }
    "down" {
        $args = $composeArgs + @("down")
        Invoke-Compose $args
        Write-Host "Microservices stopped. Named volumes remain intact."
    }
}
