param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [Parameter(Mandatory = $true)][string]$StopFile,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market",
    [int]$IntervalSeconds = 5,
    [int]$MaxDurationSeconds = 3600
)

$ErrorActionPreference = "Continue"
$started = [DateTimeOffset]::UtcNow
$firstWrite = $true

while (-not (Test-Path -LiteralPath $StopFile) -and ([DateTimeOffset]::UtcNow - $started).TotalSeconds -lt $MaxDurationSeconds) {
    $timestamp = [DateTimeOffset]::UtcNow.ToString("o")
    $desired = $null
    $ready = $null
    $hpaDesired = $null
    $hpaCurrent = $null
    $hpaCurrentCpu = $null
    $hpaTargetCpu = $null

    try {
        $deployment = kubectl --context $Context -n $Namespace get deployment marketplace-service -o json 2>$null | ConvertFrom-Json
        $desired = $deployment.spec.replicas
        $ready = if ($null -eq $deployment.status.readyReplicas) { 0 } else { $deployment.status.readyReplicas }
    } catch { }
    try {
        $hpa = kubectl --context $Context -n $Namespace get hpa marketplace-service -o json 2>$null | ConvertFrom-Json
        $hpaDesired = $hpa.status.desiredReplicas
        $hpaCurrent = $hpa.status.currentReplicas
        $cpuMetric = @($hpa.status.currentMetrics | Where-Object { $_.type -eq "Resource" -and $_.resource.name -eq "cpu" }) | Select-Object -First 1
        if ($cpuMetric) { $hpaCurrentCpu = $cpuMetric.resource.current.averageUtilization }
        $cpuSpec = @($hpa.spec.metrics | Where-Object { $_.type -eq "Resource" -and $_.resource.name -eq "cpu" }) | Select-Object -First 1
        if ($cpuSpec) { $hpaTargetCpu = $cpuSpec.resource.target.averageUtilization }
    } catch { }

    $metricRows = @()
    try {
        $lines = @(kubectl --context $Context -n $Namespace top pods --containers --no-headers 2>$null)
        foreach ($line in $lines) {
            if ($line -match '^\s*(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s*$') {
                $metricRows += [ordered]@{ pod = $Matches[1]; container = $Matches[2]; cpu = $Matches[3]; memory = $Matches[4] }
            }
        }
    } catch { }
    if ($metricRows.Count -eq 0) {
        $metricRows = @([ordered]@{ pod = $null; container = $null; cpu = $null; memory = $null })
    }

    $rows = foreach ($metric in $metricRows) {
        [pscustomobject][ordered]@{
            timestamp = $timestamp
            pod = $metric.pod
            container = $metric.container
            cpu = $metric.cpu
            memory = $metric.memory
            marketplaceDesired = $desired
            marketplaceReady = $ready
            hpaCurrentReplicas = $hpaCurrent
            hpaDesiredReplicas = $hpaDesired
            hpaCurrentCpuPercent = $hpaCurrentCpu
            hpaTargetCpuPercent = $hpaTargetCpu
        }
    }
    if ($firstWrite) {
        $rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
        $firstWrite = $false
    } else {
        $rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8 -Append
    }
    Start-Sleep -Seconds ([Math]::Max(1, $IntervalSeconds))
}

if ($firstWrite) {
    'timestamp,pod,container,cpu,memory,marketplaceDesired,marketplaceReady,hpaCurrentReplicas,hpaDesiredReplicas,hpaCurrentCpuPercent,hpaTargetCpuPercent' |
        Set-Content -LiteralPath $OutputPath -Encoding utf8
}
