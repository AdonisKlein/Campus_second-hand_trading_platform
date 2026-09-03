param(
    [Parameter(Mandatory = $true)]
    [string]$ImageNamespace,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^sha-[0-9a-f]{7}$')]
    [string]$ImageTag,
    [string]$ClusterName = 'campus-ci',
    [string]$Namespace = 'campus-market',
    [int]$RolloutTimeoutSeconds = 360
)

$ErrorActionPreference = 'Stop'
$context = "kind-$ClusterName"

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name"
    }
}

foreach ($command in @('docker', 'kubectl')) { Assert-Command $command }

kubectl config get-contexts $context 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Kind context '$context' was not found. Start it with scripts/ci/kind-local.ps1 -Action up -ClusterName $ClusterName."
}

$images = @(
    @{ Deployment = 'gateway'; Container = 'gateway'; Image = 'campus-gateway' },
    @{ Deployment = 'account-service'; Container = 'account'; Image = 'campus-account' },
    @{ Deployment = 'marketplace-service'; Container = 'marketplace'; Image = 'campus-marketplace' },
    @{ Deployment = 'trading-service'; Container = 'trading'; Image = 'campus-trading' },
    @{ Deployment = 'governance-service'; Container = 'governance'; Image = 'campus-governance' },
    @{ Deployment = 'web'; Container = 'web'; Image = 'campus-web' }
)

foreach ($entry in $images) {
    $image = "$ImageNamespace/$($entry.Image):$ImageTag"
    Write-Host "Pulling $image"
    docker pull $image
    if ($LASTEXITCODE -ne 0) { throw "Failed to pull $image" }
    kubectl --context $context -n $Namespace set image "deployment/$($entry.Deployment)" "$($entry.Container)=$image"
    if ($LASTEXITCODE -ne 0) { throw "Failed to update deployment/$($entry.Deployment)" }
}

foreach ($entry in $images) {
    kubectl --context $context -n $Namespace set env "deployment/$($entry.Deployment)" "APP_VERSION=$ImageTag" "GIT_COMMIT=$env:GITHUB_SHA" | Out-Null
    kubectl --context $context -n $Namespace rollout status "deployment/$($entry.Deployment)" "--timeout=${RolloutTimeoutSeconds}s"
    if ($LASTEXITCODE -ne 0) { throw "Rollout failed for deployment/$($entry.Deployment)" }
}

$deployed = kubectl --context $context -n $Namespace get deployments -o custom-columns='NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image' --no-headers
Write-Host $deployed

$webForward = Start-Process -FilePath kubectl -ArgumentList '--context', $context, '-n', $Namespace, 'port-forward', 'service/web', '18081:80' -WindowStyle Hidden -PassThru
try {
    $ready = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:18081/index.html' -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { $ready = $true; break }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw 'Local Kind Web smoke check failed.' }
    Write-Host "Local Kind deployment succeeded: $ImageTag"
} finally {
    Stop-Process -Id $webForward.Id -ErrorAction SilentlyContinue
}
