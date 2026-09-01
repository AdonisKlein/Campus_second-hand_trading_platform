param(
    [ValidateSet("up", "status", "down", "forward-web", "forward-mail")]
    [string]$Action = "up",
    [string]$ClusterName = "campus-ci"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$overlay = Join-Path $repoRoot "k8s\overlays\ci"
$secretFile = Join-Path $overlay ".env.secret"
$serviceImages = @(
    @{ Name = "campus-api-gateway:dev"; Path = "services\api-gateway" },
    @{ Name = "campus-account-service:dev"; Path = "services\account-service" },
    @{ Name = "campus-marketplace-service:dev"; Path = "services\marketplace-service" },
    @{ Name = "campus-trading-service:dev"; Path = "services\trading-service" },
    @{ Name = "campus-governance-service:dev"; Path = "services\governance-service" },
    @{ Name = "campus-web:dev"; Path = "frontend" }
)

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name. Follow k8s/README.md to install it."
    }
}

function New-HexSecret([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($buffer)
    } finally {
        $generator.Dispose()
    }
    return -join ($buffer | ForEach-Object { $_.ToString("x2") })
}

Assert-Command docker
Assert-Command kubectl
Assert-Command kind

if ($Action -eq "down") {
    kind delete cluster --name $ClusterName
    exit $LASTEXITCODE
}

function Read-SecretFile {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $secretFile) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.+)$') { $values[$Matches[1]] = $Matches[2] }
    }
    return $values
}

function Assert-SecretFile {
    $values = Read-SecretFile
    $required = @(
        "MYSQL_ROOT_PASSWORD", "ACCOUNT_DB_PASSWORD", "MARKETPLACE_DB_PASSWORD",
        "TRADING_DB_PASSWORD", "GOVERNANCE_DB_PASSWORD", "REDIS_PASSWORD",
        "RABBITMQ_PASSWORD", "VERIFICATION_PEPPER", "INTERNAL_SERVICE_TOKEN",
        "INTERNAL_JWT_SECRET"
    )
    foreach ($key in $required) {
        if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
            throw "Kind secret file is missing $key. Remove only k8s/overlays/ci/.env.secret and rerun to generate a complete local file."
        }
    }
    foreach ($key in @("VERIFICATION_PEPPER", "INTERNAL_SERVICE_TOKEN", "INTERNAL_JWT_SECRET")) {
        if ($values[$key].Length -lt 32) { throw "Kind secret $key must contain at least 32 characters." }
    }
    return $values
}

function Assert-LastExit([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed with exit code $LASTEXITCODE." }
}

function Wait-Rollout([string]$Kind, [string]$Name, [string]$Timeout) {
    & kubectl --context "kind-$ClusterName" -n campus-market rollout status "$Kind/$Name" "--timeout=$Timeout"
    Assert-LastExit "Rollout $Kind/$Name"
}

if ($Action -eq "status") {
    kubectl --context "kind-$ClusterName" -n campus-market get pods,svc,pvc
    exit $LASTEXITCODE
}

if ($Action -eq "forward-web") {
    Write-Host "Web and API: http://127.0.0.1:18080 (Ctrl+C to stop)"
    kubectl --context "kind-$ClusterName" -n campus-market port-forward service/web 18080:80
    exit $LASTEXITCODE
}

if ($Action -eq "forward-mail") {
    Write-Host "Mailpit: http://127.0.0.1:18025 (Ctrl+C to stop)"
    kubectl --context "kind-$ClusterName" -n campus-market port-forward service/mailpit 18025:8025
    exit $LASTEXITCODE
}

docker version | Out-Null
if (-not (kind get clusters | Select-String -SimpleMatch $ClusterName -Quiet)) {
    kind create cluster --name $ClusterName --config (Join-Path $overlay "kind-config.yaml")
    Assert-LastExit "Kind cluster creation"
}

foreach ($image in $serviceImages) {
    docker build -t $image.Name (Join-Path $repoRoot $image.Path)
    if ($LASTEXITCODE -ne 0) { throw "Image build failed: $($image.Name)" }
}
kind load docker-image ($serviceImages | ForEach-Object { $_.Name }) --name $ClusterName
Assert-LastExit "Kind image loading"

if (-not (Test-Path $secretFile)) {
    @(
        "MYSQL_ROOT_PASSWORD=$(New-HexSecret 24)"
        "ACCOUNT_DB_PASSWORD=$(New-HexSecret 24)"
        "MARKETPLACE_DB_PASSWORD=$(New-HexSecret 24)"
        "TRADING_DB_PASSWORD=$(New-HexSecret 24)"
        "GOVERNANCE_DB_PASSWORD=$(New-HexSecret 24)"
        "REDIS_PASSWORD=$(New-HexSecret 24)"
        "RABBITMQ_PASSWORD=$(New-HexSecret 24)"
        "VERIFICATION_PEPPER=$(New-HexSecret 32)"
        "INTERNAL_SERVICE_TOKEN=$(New-HexSecret 32)"
        "INTERNAL_JWT_SECRET=$(New-HexSecret 32)"
    ) | Set-Content -LiteralPath $secretFile -Encoding Ascii
}
$secrets = Assert-SecretFile

kubectl --context "kind-$ClusterName" apply -k $overlay
Assert-LastExit "Kubernetes apply"
$applicationDeployments = @("gateway", "account-service", "marketplace-service", "trading-service", "governance-service", "web")
Wait-Rollout statefulset campus-mysql 360s
Wait-Rollout deployment redis 360s
Wait-Rollout deployment rabbitmq 360s
Wait-Rollout deployment mailpit 360s
kubectl --context "kind-$ClusterName" -n campus-market scale ($applicationDeployments | ForEach-Object { "deployment/$_" }) --replicas=1
Assert-LastExit "Application deployment scale-up"
foreach ($deployment in @("account-service", "marketplace-service", "trading-service", "governance-service", "gateway")) {
    Wait-Rollout deployment $deployment 360s
}
Wait-Rollout deployment web 360s
kubectl --context "kind-$ClusterName" -n campus-market get pods,svc,pvc
Assert-LastExit "Kubernetes status"

$databaseScopes = @(
    @{ User = "account_user"; Password = $secrets.ACCOUNT_DB_PASSWORD; Own = "campus_account"; Foreign = "campus_marketplace" },
    @{ User = "marketplace_user"; Password = $secrets.MARKETPLACE_DB_PASSWORD; Own = "campus_marketplace"; Foreign = "campus_trading" },
    @{ User = "trading_user"; Password = $secrets.TRADING_DB_PASSWORD; Own = "campus_trading"; Foreign = "campus_governance" },
    @{ User = "governance_user"; Password = $secrets.GOVERNANCE_DB_PASSWORD; Own = "campus_governance"; Foreign = "campus_account" }
)
foreach ($scope in $databaseScopes) {
    & kubectl --context "kind-$ClusterName" -n campus-market exec statefulset/campus-mysql -- env "MYSQL_PWD=$($scope.Password)" mysql -u $scope.User -D $scope.Own -Nse "SELECT COUNT(*) FROM flyway_schema_history" | Out-Null
    Assert-LastExit "$($scope.User) own database check"
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & kubectl --context "kind-$ClusterName" -n campus-market exec statefulset/campus-mysql -- env "MYSQL_PWD=$($scope.Password)" mysql -u $scope.User -D $scope.Foreign -Nse "SELECT 1" 2>$null | Out-Null
    $foreignExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($foreignExitCode -eq 0) { throw "$($scope.User) must not read $($scope.Foreign)." }
}

$forward = Start-Process -FilePath kubectl -ArgumentList '--context',"kind-$ClusterName",'-n','campus-market','port-forward','service/web','18081:80' -WindowStyle Hidden -PassThru
try {
    Start-Sleep -Seconds 3
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:18081/api/actuator/health/liveness" -TimeoutSec 15
    if ($health.status -ne "UP") { throw "Gateway liveness did not return UP." }
    $homeResponse = Invoke-WebRequest -Uri "http://127.0.0.1:18081" -UseBasicParsing -TimeoutSec 15
    if ($homeResponse.StatusCode -lt 200 -or $homeResponse.StatusCode -ge 400) { throw "Web smoke check failed." }
} finally {
    Stop-Process -Id $forward.Id -ErrorAction SilentlyContinue
}
Write-Host "Kind deployment, four Flyway schemas, cross-database denial and Web/Gateway smoke checks passed."
