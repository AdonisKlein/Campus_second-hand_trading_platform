param(
    [string]$Context = "kind-campus-ci",
    [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = "Stop"
$manifest = Join-Path $PSScriptRoot "third-party\metrics-server-v0.9.0.yaml"
$expectedHash = "1cec29a5267809306a2c6ec74a3e449abbb705b4a8beed0c8a1963910f72c79b"
if (-not (Test-Path -LiteralPath $manifest)) { throw "Pinned Metrics Server manifest is missing: $manifest" }
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifest).Hash.ToLowerInvariant()
if ($actualHash -ne $expectedHash) { throw "Metrics Server manifest checksum mismatch. Expected $expectedHash, got $actualHash." }

$pinnedImage = "registry.k8s.io/metrics-server/metrics-server:v0.9.0"
$existingDeployment = $null
$previousErrorAction = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    $rawDeployment = kubectl --context $Context -n kube-system get deployment metrics-server -o json 2>$null
    if ($LASTEXITCODE -eq 0) { $existingDeployment = $rawDeployment | ConvertFrom-Json }
} finally {
    $ErrorActionPreference = $previousErrorAction
}

if ($null -eq $existingDeployment -or $existingDeployment.spec.template.spec.containers[0].image -ne $pinnedImage) {
    kubectl --context $Context apply -f $manifest
    if ($LASTEXITCODE -ne 0) { throw "Metrics Server apply failed." }
} else {
    Write-Host "Metrics Server v0.9.0 is already installed; preserving its ready Pod."
}

$deployment = kubectl --context $Context -n kube-system get deployment metrics-server -o json | ConvertFrom-Json
$args = @($deployment.spec.template.spec.containers[0].args)
if ($args -notcontains "--kubelet-insecure-tls") {
    $patch = '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
    $patchFile = [IO.Path]::GetTempFileName()
    try {
        # Windows PowerShell strips JSON quotes when a string is passed to a native -p argument.
        # A patch file preserves the exact bytes on both Windows PowerShell and PowerShell 7.
        $patch | Set-Content -LiteralPath $patchFile -Encoding Ascii
        kubectl --context $Context -n kube-system patch deployment metrics-server --type=json --patch-file $patchFile
        if ($LASTEXITCODE -ne 0) { throw "Metrics Server Kind TLS patch failed." }
    } finally {
        Remove-Item -LiteralPath $patchFile -Force -ErrorAction SilentlyContinue
    }
}

kubectl --context $Context -n kube-system rollout status deployment/metrics-server "--timeout=${TimeoutSeconds}s"
if ($LASTEXITCODE -ne 0) { throw "Metrics Server rollout did not complete." }

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    try {
        $metrics = kubectl --context $Context get --raw /apis/metrics.k8s.io/v1beta1/nodes 2>$null | ConvertFrom-Json
        if (@($metrics.items).Count -gt 0) {
            Write-Host "Metrics Server is ready and returned $(@($metrics.items).Count) node metric record(s)."
            return
        }
    } catch { }
    Start-Sleep -Seconds 3
} while ([DateTimeOffset]::UtcNow -lt $deadline)

throw "Metrics API did not become ready within $TimeoutSeconds seconds."
