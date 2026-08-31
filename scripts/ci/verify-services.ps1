param(
    [string]$JavaVersion = "25"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$serviceNames = @(
    "api-gateway",
    "account-service",
    "marketplace-service",
    "trading-service",
    "governance-service"
)

foreach ($serviceName in $serviceNames) {
    $pomPath = Join-Path $repositoryRoot "services/$serviceName/pom.xml"
    Write-Host "Verifying $serviceName"
    & mvn --batch-mode --file $pomPath "-Djava.version=$JavaVersion" verify
    if ($LASTEXITCODE -ne 0) {
        throw "$serviceName verification failed with exit code $LASTEXITCODE"
    }
}

Write-Host "All independent service projects passed Maven verify."
