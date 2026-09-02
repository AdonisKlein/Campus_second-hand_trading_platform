param(
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [Parameter(Mandatory = $true)][string]$ResultsDirectory,
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [string]$Image = "ghcr.io/grafana/k6@sha256:2072ea9eafa596532d9aee0cc0e0a50cfb0e7fb703981a46179af5f4f22c5ea4"
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "docker is required to run k6." }
$scriptFull = (Resolve-Path -LiteralPath $ScriptPath).Path
$experimentsRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resultsFull = (Resolve-Path -LiteralPath $ResultsDirectory).Path
if (-not $scriptFull.StartsWith($experimentsRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "k6 script must be located below $experimentsRoot."
}
$relativeScript = $scriptFull.Substring($experimentsRoot.Length).TrimStart('\', '/').Replace("\", "/")
$consolePath = Join-Path $resultsFull "k6-console.txt"
$arguments = @(
    "run", "--rm",
    "-e", "BASE_URL=$BaseUrl",
    "-e", "K6_SUMMARY_PATH=/artifacts/k6-summary.json",
    "-v", "${experimentsRoot}:/scripts:ro",
    "-v", "${resultsFull}:/artifacts",
    $Image,
    "run", "--summary-mode=full", "--summary-trend-stats=avg,min,med,max,p(90),p(95),p(99),count",
    "/scripts/$relativeScript"
)

$previousErrorAction = $ErrorActionPreference
try {
    # Windows PowerShell turns native stderr into ErrorRecord objects. Continue keeps the
    # stream available for Tee-Object so a failed image pull still leaves diagnostics.
    $ErrorActionPreference = "Continue"
    & docker @arguments 2>&1 | Tee-Object -FilePath $consolePath
    $exitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorAction
}
try {
    $digest = (& docker image inspect $Image --format '{{json .RepoDigests}}' 2>$null | Out-String).Trim()
    $imageJson = [ordered]@{ image = $Image; repoDigests = $digest } | ConvertTo-Json
    [IO.File]::WriteAllText(
        (Join-Path $resultsFull "k6-image.json"),
        $imageJson,
        [Text.UTF8Encoding]::new($false)
    )
} catch { }
if ($exitCode -ne 0) { throw "k6 failed with exit code $exitCode." }
if (-not (Test-Path -LiteralPath (Join-Path $resultsFull "k6-summary.json"))) { throw "k6 did not produce k6-summary.json." }
