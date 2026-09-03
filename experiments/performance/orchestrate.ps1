param(
    [string]$RunDirectory,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market",
    [string]$BaseUrl = "http://host.docker.internal:18080"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lifecycle.ps1")
Remove-StalePerformanceShimPathEntries

$mode = if ([string]::IsNullOrWhiteSpace($env:PERFORMANCE_MODE)) { "" } else { $env:PERFORMANCE_MODE.Trim().ToLowerInvariant() }
if (-not @("dry-run","smoke","demo","formal").Contains($mode)) { throw "PERFORMANCE_MODE must be dry-run, smoke, demo, or formal." }
if ($mode -eq "formal" -and $env:PERFORMANCE_CONFIRM -ne "RUN_18_FORMAL_TESTS") { throw "Set PERFORMANCE_CONFIRM=RUN_18_FORMAL_TESTS to run all 18 formal measurements." }
if ([string]::IsNullOrWhiteSpace($RunDirectory) -or -not (Test-Path -LiteralPath $RunDirectory -PathType Container)) { throw "RunDirectory must be an existing directory." }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$clusterName = "campus-performance"
$experimentContext = "kind-$clusterName"
$fixedItemIds = @(5278, 7433, 7654, 8519, 11496, 18637, 3742, 13457, 4813, 9944)
$architectures = @("midterm-check","microservices-end")
$tagShas = [ordered]@{}
foreach ($tag in $architectures) {
    $shaOutput = @(Invoke-NativeCaptureSafe "git" @("-C", $repoRoot, "rev-list", "-n", "1", $tag) "Cannot resolve fixed tag: $tag")
    $shaText = $shaOutput | Select-Object -Last 1
    if ([string]::IsNullOrWhiteSpace($shaText)) { throw "Cannot resolve fixed tag: $tag returned empty output." }
    $sha = $shaText.Trim()
    if ($sha -notmatch '^[0-9a-f]{40}$') { throw "Cannot resolve fixed tag: $tag returned an invalid SHA." }
    $tagShas[$tag] = $sha
}

$profile = if ($mode -eq "formal") { "formal" } elseif ($mode -eq "demo") { "quick" } else { "smoke" }
$plan = [ordered]@{
    schemaVersion=1; mode=$mode; profile=$profile; clusterName=$clusterName; context=$experimentContext
    tags=$tagShas; architectures=$architectures
    steps=@("resolve-tags","generate-canonical-dataset","generate-sql-adapters","archive-tag-workspaces","deploy-midterm-check","apply-migrations","import-and-verify-midterm","benchmark-midterm","collect-diagnostics","cleanup-midterm","deploy-microservices-end","apply-migrations","import-and-verify-microservices","benchmark-microservices","collect-diagnostics","cleanup-microservices","summarize-comparison","index-evidence")
    formalMatrix=[ordered]@{ vus=@(10,50,100); repeats=3; architectures=2; measurements=18; warmup="2m"; measurement="5m"; cooldown="2m" }
}
Write-Utf8Json (Join-Path $RunDirectory "orchestration-plan.json") $plan
if ($mode -eq "dry-run") {
    $plan.steps | ForEach-Object { Write-Host "DRY-RUN $_" }
    return
}

foreach ($tool in @("git","node","docker","kind","kubectl")) { Assert-Tool $tool }
Invoke-Checked { docker version } "Docker availability"
$existing = @(Get-KindClustersSafe)
if ($existing -contains $clusterName) { throw "Dedicated performance cluster '$clusterName' already exists. Clean it explicitly before starting to avoid mixing architectures." }

$datasetDirectory = Join-Path $RunDirectory "dataset"
$sqlDirectory = Join-Path $datasetDirectory "sql"
New-Item -ItemType Directory -Force -Path $datasetDirectory, $sqlDirectory | Out-Null
Invoke-Checked { node (Join-Path $repoRoot "experiments\common\dataset\generate-canonical-data.mjs") --seed 20260902 --users 2500 --items 20000 --messages 50000 --output $datasetDirectory } "Canonical dataset generation"
Invoke-Checked { node (Join-Path $PSScriptRoot "generate-sql.mjs") --input $datasetDirectory --output $sqlDirectory --target all } "SQL adapter generation"
$manifestSha = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $datasetDirectory "manifest.json")).Hash.ToLowerInvariant()

$workspaceRoot = Join-Path $repoRoot "artifacts\cloud-native\performance-workspaces"
New-Item -ItemType Directory -Force -Path $workspaceRoot | Out-Null
$workspaces = @{}
foreach ($architecture in $architectures) { $workspaces[$architecture] = Expand-TaggedWorkspace $repoRoot $architecture $tagShas[$architecture] $workspaceRoot }

$orchestration = [ordered]@{ schemaVersion=1; mode=$mode; profile=$profile; tags=$tagShas; canonicalManifestSha256=$manifestSha; architectures=@(); startedAt=[DateTimeOffset]::UtcNow.ToString("o"); status="running"; failure=$null }
$metadataPath = Join-Path $RunDirectory "performance-metadata.json"
Write-Utf8Json $metadataPath $orchestration
$failures = [Collections.Generic.List[string]]::new()

foreach ($architecture in $architectures) {
    $architectureDirectory = Join-Path $RunDirectory $architecture
    New-Item -ItemType Directory -Force -Path $architectureDirectory | Out-Null
    $record = [ordered]@{ architecture=$architecture; gitSha=$tagShas[$architecture]; status="running"; startedAt=[DateTimeOffset]::UtcNow.ToString("o"); finishedAt=$null; failure=$null; dockerfiles=@(); images=@() }
    $orchestration.architectures += $record
    Write-Utf8Json (Join-Path $architectureDirectory "metadata.json") $record
    $forward = $null
    $stage = "prepare"
    try {
        $stage = "hash-dockerfiles"
        $dockerfiles = if ($architecture -eq "midterm-check") { @("backend/Dockerfile","frontend/Dockerfile") } else { @("services/api-gateway/Dockerfile","services/account-service/Dockerfile","services/marketplace-service/Dockerfile","services/trading-service/Dockerfile","services/governance-service/Dockerfile","frontend/Dockerfile") }
        $record.dockerfiles = @($dockerfiles | ForEach-Object { [ordered]@{ path=$_; sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $workspaces[$architecture] $_)).Hash.ToLowerInvariant() } })
        $stage = "kind-up"
        Invoke-KindLifecycle $workspaces[$architecture] "up" $clusterName (Join-Path $architectureDirectory "kind-lifecycle")
        $stage = "set-fair-resources"
        Set-FairResources $architecture $experimentContext $Namespace
        $stage = "collect-fairness"
        $fairness = Get-FairnessEvidence $architecture $experimentContext $Namespace
        Write-Utf8Json (Join-Path $architectureDirectory "fairness.json") $fairness
        $stage = "prepare-data-import-evidence"
        Initialize-PerformanceDataImportEvidence $architectureDirectory | Out-Null
        $stage = "collect-environment"
        $collectorPath = Join-Path $repoRoot "experiments\common\collect-environment.ps1"
        $architectureEnvironmentPath = Join-Path $architectureDirectory "environment.json"
        Invoke-PerformanceEnvironmentCollection $architecture $collectorPath $architectureEnvironmentPath "performance" $experimentContext $Namespace $architectureDirectory | Out-Null
        if (-not (Test-Path -LiteralPath (Join-Path $RunDirectory "environment.json"))) {
            Copy-Item -LiteralPath $architectureEnvironmentPath -Destination (Join-Path $RunDirectory "environment.json")
        }
        $stage = "inspect-images"
        $imageNames = if ($architecture -eq "midterm-check") { @("campus-backend:dev","campus-web:dev") } else { @("campus-api-gateway:dev","campus-account-service:dev","campus-marketplace-service:dev","campus-trading-service:dev","campus-governance-service:dev","campus-web:dev") }
        $record.images = @($imageNames | ForEach-Object {
            $image = $_
            $imageIdOutput = @(Invoke-NativeCaptureSafe "docker" @("image", "inspect", $image, "--format", "{{.Id}}") "Cannot inspect image $image")
            $imageIdText = $imageIdOutput | Select-Object -Last 1
            if ([string]::IsNullOrWhiteSpace($imageIdText)) { throw "Cannot inspect image ${image}: Docker returned empty output." }
            [ordered]@{ name=$image; id=$imageIdText.Trim() }
        })
        $stage = "resolve-import-and-verify-data"
        Import-AndVerifyData $architecture $sqlDirectory $experimentContext $Namespace $fixedItemIds (Join-Path $architectureDirectory "data-verification.json")
        $stage = "start-port-forward"
        $forward = Start-PerformanceForward $experimentContext $Namespace 18080 $architectureDirectory

        $oldValues = @{ Orchestrated=$env:PERFORMANCE_ORCHESTRATED; Mode=$env:PERFORMANCE_MODE; Profile=$env:PERFORMANCE_PROFILE; Architecture=$env:ARCHITECTURE; Confirm=$env:PERFORMANCE_CONFIRM; Demo=$env:PERFORMANCE_DEMO }
        try {
            $env:PERFORMANCE_ORCHESTRATED = "1"
            $env:ARCHITECTURE = $architecture
            if ($mode -eq "smoke") { $env:PERFORMANCE_MODE="smoke"; $env:PERFORMANCE_PROFILE="formal"; $env:PERFORMANCE_DEMO="" }
            else { $env:PERFORMANCE_MODE="formal"; $env:PERFORMANCE_PROFILE=$profile; $env:PERFORMANCE_CONFIRM="RUN_9_FORMAL_TESTS"; $env:PERFORMANCE_DEMO=if($mode -eq "demo"){"1"}else{""} }
            & (Join-Path $PSScriptRoot "run.ps1") -RunDirectory $architectureDirectory -Context $experimentContext -Namespace $Namespace -BaseUrl $BaseUrl
        } finally {
            $env:PERFORMANCE_ORCHESTRATED=$oldValues.Orchestrated; $env:PERFORMANCE_MODE=$oldValues.Mode; $env:PERFORMANCE_PROFILE=$oldValues.Profile; $env:ARCHITECTURE=$oldValues.Architecture; $env:PERFORMANCE_CONFIRM=$oldValues.Confirm; $env:PERFORMANCE_DEMO=$oldValues.Demo
        }
        $record.status = "passed"
    } catch {
        Write-PerformanceErrorDetails (Join-Path $architectureDirectory "error-details.json") $architecture $stage $_
        $record.status = "failed"; $record.failure = $_.Exception.Message; $failures.Add("${architecture}: $($record.failure)")
    } finally {
        if ($forward) { Stop-Process -Id $forward.Id -Force -ErrorAction SilentlyContinue }
        try { & (Join-Path $repoRoot "experiments\common\collect-diagnostics.ps1") -OutputDirectory (Join-Path $architectureDirectory "diagnostics") -Context $experimentContext -Namespace $Namespace } catch { }
        try { Invoke-KindLifecycle $workspaces[$architecture] "down" $clusterName (Join-Path $architectureDirectory "kind-lifecycle") } catch { $failures.Add("${architecture} cleanup: $($_.Exception.Message)") }
        $record.finishedAt=[DateTimeOffset]::UtcNow.ToString("o")
        Write-Utf8Json (Join-Path $architectureDirectory "metadata.json") $record
        Write-Utf8Json $metadataPath $orchestration
    }
}

try { Invoke-Checked { node (Join-Path $PSScriptRoot "summarize-results.mjs") --input $RunDirectory --comparison } "Comparison summary" } catch { $failures.Add("summary: $($_.Exception.Message)") }
$orchestration.status=if($failures.Count -eq 0){"passed"}else{"failed"}; $orchestration.failure=if($failures.Count -eq 0){$null}else{$failures -join '; '}; $orchestration.finishedAt=[DateTimeOffset]::UtcNow.ToString("o"); Write-Utf8Json $metadataPath $orchestration
$evidence = @(Get-ChildItem -LiteralPath $RunDirectory -Recurse -File | Where-Object Name -ne "evidence-index.json" | ForEach-Object { [ordered]@{ path=$_.FullName.Substring($RunDirectory.Length).TrimStart('\','/').Replace('\','/'); sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant(); bytes=$_.Length } })
Write-Utf8Json (Join-Path $RunDirectory "evidence-index.json") ([ordered]@{ schemaVersion=1; generatedAt=[DateTimeOffset]::UtcNow.ToString("o"); evidence=$evidence })
if ($failures.Count -gt 0) { throw "End-to-end performance experiment failed: $($failures -join '; ')" }
