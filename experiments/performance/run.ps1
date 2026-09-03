param(
    [string]$RunDirectory,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market",
    [string]$BaseUrl = "http://host.docker.internal:18080"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "resource-sampler.ps1")
if ($env:PERFORMANCE_ORCHESTRATED -ne "1") {
    & (Join-Path $PSScriptRoot "orchestrate.ps1") -RunDirectory $RunDirectory -Context $Context -Namespace $Namespace -BaseUrl $BaseUrl
    return
}
$fixedItemIds = @(5278, 7433, 7654, 8519, 11496, 18637, 3742, 13457, 4813, 9944)
$endpointTags = @("items_list", "item_detail", "item_messages")
$matrixVus = @(10, 50, 100)
$matrixRepeats = 3
$mode = if ([string]::IsNullOrWhiteSpace($env:PERFORMANCE_MODE)) { "" } else { $env:PERFORMANCE_MODE.Trim().ToLowerInvariant() }
$profile = if ([string]::IsNullOrWhiteSpace($env:PERFORMANCE_PROFILE)) { "formal" } else { $env:PERFORMANCE_PROFILE.Trim().ToLowerInvariant() }
$architecture = if ([string]::IsNullOrWhiteSpace($env:ARCHITECTURE)) { "" } else { $env:ARCHITECTURE.Trim().ToLowerInvariant() }
if ($env:PERFORMANCE_DEMO -eq "1") { $matrixVus = @(10); $matrixRepeats = 1 }

if ([string]::IsNullOrWhiteSpace($RunDirectory)) { throw "RunDirectory is required." }
if (-not @("smoke", "formal").Contains($mode)) { throw "PERFORMANCE_MODE must be smoke or formal." }
if (-not @("quick", "formal").Contains($profile)) { throw "PERFORMANCE_PROFILE must be quick or formal." }
if ($mode -eq "smoke") { $profile = "smoke" }
if ([string]::IsNullOrWhiteSpace($BaseUrl)) { throw "BaseUrl is required." }
if ($mode -eq "formal") {
    if (-not @("midterm-check", "microservices-end").Contains($architecture)) { throw "ARCHITECTURE must be midterm-check or microservices-end for formal mode." }
    if ($env:PERFORMANCE_CONFIRM -ne "RUN_9_FORMAL_TESTS") { throw "Set PERFORMANCE_CONFIRM=RUN_9_FORMAL_TESTS to run the 9-test matrix." }
} else {
    if (-not [string]::IsNullOrWhiteSpace($architecture) -and -not @("midterm-check", "microservices-end", "development").Contains($architecture)) { throw "ARCHITECTURE is invalid." }
    if ([string]::IsNullOrWhiteSpace($architecture)) { $architecture = "development" }
}
if (-not (Test-Path -LiteralPath $RunDirectory -PathType Container)) { throw "RunDirectory does not exist: $RunDirectory" }

$benchmarkSource = Join-Path $PSScriptRoot "benchmark.js"
$generator = Join-Path $PSScriptRoot "generate-benchmark.mjs"
$commonDirectory = Join-Path $PSScriptRoot "..\common"
$runtimeDirectory = Join-Path $PSScriptRoot ".generated-runtime"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$benchmarkSourceSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $benchmarkSource).Hash.ToLowerInvariant()
$failures = [Collections.Generic.List[string]]::new()

function Write-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
}

function Invoke-Phase([string]$Directory, [int]$Vus, [int]$Repeat, [string]$Phase, [string]$Duration, [string]$ValidationMode, [string]$RunLabel, [bool]$SampleResources) {
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    $generatedPath = Join-Path $Directory "benchmark.generated.js"
    & node $generator --source $benchmarkSource --output $generatedPath --vus $Vus --duration $Duration `
        --architecture $architecture --run-label $RunLabel --validation-mode $ValidationMode
    if ($LASTEXITCODE -ne 0) { throw "Failed to generate benchmark for $RunLabel." }
    $generatedSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $generatedPath).Hash.ToLowerInvariant()
    $metadataPath = Join-Path $Directory "run-metadata.json"
    $metadata = [ordered]@{
        schemaVersion=1; architecture=$architecture; vus=$Vus; repeat=$Repeat; phase=$Phase
        duration=$Duration; baseUrl=$BaseUrl.TrimEnd('/'); profile=$profile
        fixedItemIds=$fixedItemIds; endpointTags=$endpointTags
        benchmarkSourceSha256=$benchmarkSourceSha; generatedBenchmarkSha256=$generatedSha
        timestamp=[DateTimeOffset]::UtcNow.ToString("o"); status="running"; failure=$null
    }
    Write-Json $metadataPath $metadata

    # common/invoke-k6.ps1 accepts scripts only below experiments/. Keep the authoritative
    # generated script in artifacts and stage an identical temporary copy solely for invocation.
    New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
    $stagedPath = Join-Path $runtimeDirectory ("benchmark-{0}.generated.js" -f [guid]::NewGuid().ToString("N"))
    Copy-Item -LiteralPath $generatedPath -Destination $stagedPath
    $stopFile = Join-Path $Directory "resource-sampler.stop"
    $resourceSamplesPath = Join-Path $Directory "resource-samples.csv"
    $samplerStdoutPath = Join-Path $Directory "resource-sampler.stdout.log"
    $samplerStderrPath = Join-Path $Directory "resource-sampler.stderr.log"
    $sampler = $null
    try {
        if ($SampleResources) {
            $seconds = if ($Duration -match '^(\d+)s$') { [int]$Matches[1] } elseif ($Duration -match '^(\d+)m$') { [int]$Matches[1] * 60 } else { 360 }
            $minimumSamplerSeconds = if ($profile -eq "formal" -and $Phase -eq "measurement") { 360 } else { 30 }
            $maxSamplerSeconds = [Math]::Max($minimumSamplerSeconds, $seconds + 30)
            $sampler = Start-ResourceSampler (Join-Path $commonDirectory "collect-resources.ps1") $resourceSamplesPath $stopFile $Context $Namespace $maxSamplerSeconds $samplerStdoutPath $samplerStderrPath
        }
        & (Join-Path $commonDirectory "invoke-k6.ps1") -ScriptPath $stagedPath -ResultsDirectory $Directory -BaseUrl $BaseUrl
        if ($SampleResources) { Assert-ResourceSamplerHealthy $sampler $resourceSamplesPath $samplerStderrPath }
        $metadata.status = "passed"
    } catch {
        $metadata.failure = $_.Exception.Message
        $samplerFailure = $metadata.failure -match '^Resource sampler'
        $metadata.status = if ($samplerFailure) { "infrastructure_failed" } elseif (Test-Path -LiteralPath (Join-Path $Directory "k6-summary.json")) { "benchmark_failed" } else { "infrastructure_failed" }
        $failures.Add("${RunLabel}: $($metadata.status): $($metadata.failure)")
    } finally {
        if ($SampleResources) {
            try {
                Stop-ResourceSampler $sampler $stopFile
            } catch {
                $cleanupFailure = "Resource sampler cleanup failed: $($_.Exception.Message)"
                if ($metadata.status -eq "passed" -or $metadata.status -eq "running") {
                    $metadata.status = "infrastructure_failed"
                    $metadata.failure = $cleanupFailure
                    $failures.Add("${RunLabel}: infrastructure_failed: $cleanupFailure")
                } else {
                    $metadata.failure = "$($metadata.failure); $cleanupFailure"
                }
            }
            Read-ResourceSamplerLog $samplerStdoutPath | Out-Null
            Read-ResourceSamplerLog $samplerStderrPath | Out-Null
            Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $stagedPath -Force -ErrorAction SilentlyContinue
        Write-Json $metadataPath $metadata
    }
}

$orchestrationDatasetPath = Join-Path (Split-Path -Parent $RunDirectory) "dataset\manifest.json"
$canonicalManifestPath = if (Test-Path -LiteralPath $orchestrationDatasetPath) { $orchestrationDatasetPath } else { Join-Path $repoRoot "artifacts\cloud-native\dataset\manifest.json" }
$canonicalManifestSha = if (Test-Path -LiteralPath $canonicalManifestPath) { (Get-FileHash -Algorithm SHA256 -LiteralPath $canonicalManifestPath).Hash.ToLowerInvariant() } else { "unavailable" }
$gitCommitOutput = @(& git -C $repoRoot rev-parse HEAD)
$gitCommitExitCode = $LASTEXITCODE
if ($gitCommitExitCode -ne 0) { throw "Cannot read benchmark Git commit; git exited with code $gitCommitExitCode." }
$gitCommitText = $gitCommitOutput | Select-Object -Last 1
if ([string]::IsNullOrWhiteSpace($gitCommitText)) { throw "Cannot read benchmark Git commit: Git returned empty output." }
$gitBranchOutput = @(& git -C $repoRoot branch --show-current)
$gitBranchExitCode = $LASTEXITCODE
if ($gitBranchExitCode -ne 0) { throw "Cannot read benchmark Git branch; git exited with code $gitBranchExitCode." }
$gitBranchText = $gitBranchOutput | Select-Object -Last 1
$topMetadata = [ordered]@{
    schemaVersion=1; mode=$mode; architecture=$architecture; profile=$profile
    matrix=[ordered]@{ vus=$matrixVus; repeats=$matrixRepeats }
    canonicalManifestSha256=$canonicalManifestSha
    gitCommit=$gitCommitText.Trim()
    gitBranch=if ([string]::IsNullOrWhiteSpace($gitBranchText)) { "" } else { $gitBranchText.Trim() }
    fixedItemIds=$fixedItemIds; endpointTags=$endpointTags
    benchmarkSourceSha256=$benchmarkSourceSha; createdAt=[DateTimeOffset]::UtcNow.ToString("o")
}
Write-Json (Join-Path $RunDirectory "benchmark-metadata.json") $topMetadata

if ($mode -eq "smoke") {
    Invoke-Phase -Directory $RunDirectory -Vus 1 -Repeat 1 -Phase "smoke" -Duration "10s" -ValidationMode "strict" -RunLabel "smoke" -SampleResources $true
} else {
    $warmupDuration = if ($profile -eq "quick") { "10s" } else { "2m" }
    $measurementDuration = if ($profile -eq "quick") { "30s" } else { "5m" }
    $cooldownSeconds = if ($profile -eq "quick") { 10 } else { 120 }
    foreach ($vus in $matrixVus) {
        for ($repeat = 1; $repeat -le $matrixRepeats; $repeat++) {
            $currentRunDirectory = Join-Path (Join-Path $RunDirectory "vus-$vus") "run-$repeat"
            Invoke-Phase -Directory (Join-Path $currentRunDirectory "warmup") -Vus $vus -Repeat $repeat -Phase "warmup" -Duration $warmupDuration -ValidationMode "record" -RunLabel "vus-$vus-run-$repeat-warmup" -SampleResources $false
            Invoke-Phase -Directory (Join-Path $currentRunDirectory "measurement") -Vus $vus -Repeat $repeat -Phase "measurement" -Duration $measurementDuration -ValidationMode "record" -RunLabel "vus-$vus-run-$repeat-measurement" -SampleResources $true
            $warmupMetadata = Get-Content -Raw -Encoding utf8 -LiteralPath (Join-Path $currentRunDirectory "warmup\run-metadata.json") | ConvertFrom-Json
            $measurementMetadata = Get-Content -Raw -Encoding utf8 -LiteralPath (Join-Path $currentRunDirectory "measurement\run-metadata.json") | ConvertFrom-Json
            Write-Json (Join-Path $currentRunDirectory "run-metadata.json") ([ordered]@{ schemaVersion=1; architecture=$architecture; vus=$vus; repeat=$repeat; profile=$profile; warmupStatus=$warmupMetadata.status; measurementStatus=$measurementMetadata.status; status=if($warmupMetadata.status -eq "passed" -and $measurementMetadata.status -eq "passed"){"passed"}else{"failed"} })
            Start-Sleep -Seconds $cooldownSeconds
        }
    }
}

if ($failures.Count -gt 0) { throw "Performance run completed with $($failures.Count) failed phase(s): $($failures -join '; ')" }
