param(
    [string]$ClusterContext = "kind-campus-ci",
    [string]$Output = "resource-samples.csv",
    [int]$IntervalSeconds = 5,
    [string]$Namespace = "campus-market"
)

# 每 5 秒记录 Marketplace 相关副本数与 CPU/内存采样，供 HPA 实验证据使用。
$ErrorActionPreference = "Continue"
$header = "timestamp,hpa-current,hpa-target,hpa-desired,replicas-ready,pod,cpu-millicores,memory-bytes"
Set-Content -LiteralPath $Output -Value $header -Encoding utf8
Write-Host "Sampling every ${IntervalSeconds}s into $Output (Ctrl+C to stop)"

while ($true) {
    $timestamp = (Get-Date -Format "o")
    $hpa = kubectl --context $ClusterContext -n $Namespace get hpa marketplace-hpa -o json 2>$null | ConvertFrom-Json
    $hpaCurrent = $hpa.status.currentReplicas
    $hpaTarget = $hpa.status.currentMetrics[0].resource.current.averageUtilization
    $hpaDesired = $hpa.status.desiredReplicas
    $pods = kubectl --context $ClusterContext -n $Namespace get pods -l app=marketplace -o json 2>$null | ConvertFrom-Json
    $ready = @($pods.items | Where-Object { $_.status.phase -eq "Running" -and
            ($_.status.conditions | Where-Object { $_.type -eq "Ready" -and $_.status -eq "True" }) }).Count
    if (-not $hpa) {
        Add-Content -LiteralPath $Output -Value "$timestamp,,,,$ready,,," -Encoding utf8
    } elseif (-not $pods) {
        Add-Content -LiteralPath $Output -Value "$timestamp,$hpaCurrent,$hpaTarget,$hpaDesired,$ready,,," -Encoding utf8
    } else {
        foreach ($pod in $pods.items) {
            $metrics = kubectl --context $ClusterContext -n $Namespace top pod $pod.metadata.name --containers -o json 2>$null | ConvertFrom-Json
            $container = $metrics.containers | Where-Object { $_.name -eq "marketplace" } | Select-Object -First 1
            if (-not $container) { $container = $metrics.containers | Select-Object -First 1 }
            $cpu = if ($container) { $container.usage.cpu } else { "" }
            $memory = if ($container) { $container.usage.memory } else { "" }
            Add-Content -LiteralPath $Output -Value "$timestamp,$hpaCurrent,$hpaTarget,$hpaDesired,$ready,$($pod.metadata.name),$cpu,$memory" -Encoding utf8
        }
    }
    Start-Sleep -Seconds $IntervalSeconds
}
