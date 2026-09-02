param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("smoke", "hpa", "fault", "performance")]
    [string]$Experiment,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market",
    [string]$BaseUrl = "http://host.docker.internal:18080",
    [string]$OutputRoot
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "artifacts\cloud-native"
}
$shortSha = (& git -C $repoRoot rev-parse --short=8 HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw "Cannot resolve the current Git commit." }
$runId = "{0}-{1}-{2}" -f $Experiment, ([DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssZ")), $shortSha
$runDirectory = Join-Path $OutputRoot $runId
$startedAt = [DateTimeOffset]::UtcNow
$status = "FAIL"
$failure = $null
$experimentExitCode = 1

New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $runDirectory "manifests") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $runDirectory "logs") | Out-Null

try {
    & (Join-Path $PSScriptRoot "common\collect-environment.ps1") `
        -OutputPath (Join-Path $runDirectory "environment.json") `
        -Experiment $Experiment -Context $Context -Namespace $Namespace

    $implementation = if ($Experiment -eq "smoke") {
        Join-Path $PSScriptRoot "common\run-smoke.ps1"
    } else {
        Join-Path $PSScriptRoot "$Experiment\run.ps1"
    }
    if (-not (Test-Path -LiteralPath $implementation)) {
        throw "Experiment '$Experiment' has no implementation at $implementation."
    }
    & $implementation -RunDirectory $runDirectory -Context $Context -Namespace $Namespace -BaseUrl $BaseUrl
    $status = "PASS"
    $experimentExitCode = 0
} catch {
    $failure = $_.Exception.Message
    Write-Error $failure -ErrorAction Continue
} finally {
    try {
        & (Join-Path $PSScriptRoot "common\collect-diagnostics.ps1") `
            -OutputDirectory $runDirectory -Context $Context -Namespace $Namespace
    } catch {
        Add-Content -LiteralPath (Join-Path $runDirectory "diagnostics-error.txt") -Value $_.Exception.Message
    }

    $evidence = @()
    Get-ChildItem -LiteralPath $runDirectory -Recurse -File | Where-Object Name -ne "result.json" | ForEach-Object {
        $evidence += [ordered]@{
            path = $_.FullName.Substring($runDirectory.Length).TrimStart('\', '/').Replace("\", "/")
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
            bytes = $_.Length
        }
    }
    $result = [ordered]@{
        schemaVersion = 1
        runId = $runId
        experiment = $Experiment
        status = $status
        startedAt = $startedAt.ToString("o")
        finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
        gitCommit = (& git -C $repoRoot rev-parse HEAD).Trim()
        context = $Context
        namespace = $Namespace
        failure = $failure
        evidence = $evidence
    }
    $resultJson = $result | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText(
        (Join-Path $runDirectory "result.json"),
        $resultJson,
        [Text.UTF8Encoding]::new($false)
    )
    Write-Host "Experiment evidence: $runDirectory"
}

exit $experimentExitCode
