param(
    [ValidateSet("up", "status", "down", "forward-web", "forward-mail")]
    [string]$Action = "up",
    [string]$ClusterName = "campus-ci"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$overlay = Join-Path $repoRoot "k8s\overlays\ci"
$secretFile = Join-Path $overlay ".env.secret"

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
}

docker build -t campus-backend:dev (Join-Path $repoRoot "backend")
if ($LASTEXITCODE -ne 0) { throw "Backend image build failed." }
docker build -t campus-web:dev (Join-Path $repoRoot "frontend")
if ($LASTEXITCODE -ne 0) { throw "Web image build failed." }
kind load docker-image campus-backend:dev campus-web:dev --name $ClusterName

if (-not (Test-Path $secretFile)) {
    @(
        "MYSQL_ROOT_PASSWORD=$(New-HexSecret 24)"
        "MYSQL_PASSWORD=$(New-HexSecret 24)"
        "VERIFICATION_PEPPER=$(New-HexSecret 32)"
    ) | Set-Content -LiteralPath $secretFile -Encoding Ascii
}

kubectl --context "kind-$ClusterName" apply -k $overlay
kubectl --context "kind-$ClusterName" -n campus-market rollout status statefulset/campus-mysql --timeout=240s
kubectl --context "kind-$ClusterName" -n campus-market rollout status deployment/mailpit --timeout=180s
kubectl --context "kind-$ClusterName" -n campus-market rollout status deployment/campus-backend --timeout=300s
kubectl --context "kind-$ClusterName" -n campus-market rollout status deployment/campus-web --timeout=180s
kubectl --context "kind-$ClusterName" -n campus-market get pods,svc,pvc

$forward = Start-Process -FilePath kubectl -ArgumentList '--context',"kind-$ClusterName",'-n','campus-market','port-forward','service/web','18081:80' -WindowStyle Hidden -PassThru
try {
    Start-Sleep -Seconds 3
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:18081/api/actuator/health/liveness" -TimeoutSec 15
    if ($health.status -ne "UP") { throw "Backend smoke check did not return UP." }
} finally {
    Stop-Process -Id $forward.Id -ErrorAction SilentlyContinue
}
Write-Host "Kind deployment passed. Use forward-web or forward-mail to access it."
