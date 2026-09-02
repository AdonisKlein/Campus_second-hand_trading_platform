param(
    [Parameter(Mandatory = $true)][string]$RunDirectory,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market",
    [string]$BaseUrl = "http://host.docker.internal:18080"
)

$ErrorActionPreference = "Stop"
$stopFile = Join-Path $RunDirectory "resource-sampler.stop"
$sampler = $null
$forward = $null
Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue

function Wait-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutSeconds) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $task = $client.ConnectAsync($HostName, $Port)
            if ($task.Wait(1000) -and $client.Connected) { return }
        } catch { } finally { $client.Dispose() }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Timed out waiting for ${HostName}:$Port."
}

try {
    & (Join-Path $PSScriptRoot "install-metrics-server.ps1") -Context $Context

    # Reuse the host that launched the experiment. A student machine may only
    # have Windows PowerShell 5.1 (`powershell.exe`) and not PowerShell 7 (`pwsh`).
    $currentPowerShell = (Get-Process -Id $PID -ErrorAction Stop).Path
    if ([string]::IsNullOrWhiteSpace($currentPowerShell)) {
        throw "Cannot resolve the current PowerShell executable for the resource sampler."
    }
    $samplerArguments = @(
        "-NoProfile", "-File", (Join-Path $PSScriptRoot "collect-resources.ps1"),
        "-OutputPath", (Join-Path $RunDirectory "resource-samples.csv"),
        "-StopFile", $stopFile,
        "-Context", $Context,
        "-Namespace", $Namespace
    )
    $sampler = Start-Process -FilePath $currentPowerShell -ArgumentList $samplerArguments -WindowStyle Hidden -PassThru

    $kubectl = (Get-Command kubectl -ErrorAction Stop).Source
    $forwardLog = Join-Path $RunDirectory "port-forward.log"
    $forwardError = Join-Path $RunDirectory "port-forward-error.log"
    $forward = Start-Process -FilePath $kubectl -ArgumentList @(
        "--context", $Context, "-n", $Namespace, "port-forward", "service/web", "18080:80"
    ) -RedirectStandardOutput $forwardLog -RedirectStandardError $forwardError -WindowStyle Hidden -PassThru
    Wait-TcpPort -HostName "127.0.0.1" -Port 18080 -TimeoutSeconds 30

    & (Join-Path $PSScriptRoot "invoke-k6.ps1") -ScriptPath (Join-Path $PSScriptRoot "smoke.js") `
        -ResultsDirectory $RunDirectory -BaseUrl $BaseUrl
} finally {
    New-Item -ItemType File -Force -Path $stopFile | Out-Null
    if ($sampler) {
        try { Wait-Process -Id $sampler.Id -Timeout 10 -ErrorAction Stop } catch { Stop-Process -Id $sampler.Id -Force -ErrorAction SilentlyContinue }
    }
    if ($forward) { Stop-Process -Id $forward.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue
}
