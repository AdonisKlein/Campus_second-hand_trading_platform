param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market"
)

$ErrorActionPreference = "Continue"
$manifestDirectory = Join-Path $OutputDirectory "manifests"
$logDirectory = Join-Path $OutputDirectory "logs"
New-Item -ItemType Directory -Force -Path $manifestDirectory, $logDirectory | Out-Null

function Save-Command([string]$Path, [scriptblock]$Command) {
    try { & $Command 2>&1 | Out-File -LiteralPath $Path -Encoding utf8 } catch { $_.Exception.Message | Out-File -LiteralPath $Path -Encoding utf8 }
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    "kubectl is not installed" | Set-Content -LiteralPath (Join-Path $OutputDirectory "diagnostics-error.txt")
    return
}

Save-Command (Join-Path $OutputDirectory "cluster-resources.txt") { kubectl --context $Context -n $Namespace get pods,svc,pvc,hpa -o wide }
Save-Command (Join-Path $OutputDirectory "pod-events.txt") { kubectl --context $Context -n $Namespace get events --sort-by=.metadata.creationTimestamp }
Save-Command (Join-Path $manifestDirectory "workloads.yaml") { kubectl --context $Context -n $Namespace get deployments,statefulsets,services,hpa,pvc,configmaps -o yaml }
Save-Command (Join-Path $OutputDirectory "pod-describe.txt") { kubectl --context $Context -n $Namespace describe pods }

$podNames = @()
try { $podNames = @(kubectl --context $Context -n $Namespace get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>$null) } catch { }
foreach ($pod in $podNames) {
    $safeName = ($pod.Trim() -replace '[^a-zA-Z0-9_.-]', '_')
    if (-not $safeName) { continue }
    Save-Command (Join-Path $logDirectory "$safeName.log") { kubectl --context $Context -n $Namespace logs $pod --all-containers=true --prefix=true }
    Save-Command (Join-Path $logDirectory "$safeName.previous.log") { kubectl --context $Context -n $Namespace logs $pod --all-containers=true --prefix=true --previous }
}
