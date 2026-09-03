function Read-ResourceSamplerLog([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return "" }
    return [IO.File]::ReadAllText($Path)
}

function Write-RunspaceEvidence($Handle) {
    $stdout = @($Handle.Output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    $errorLines = @($Handle.PowerShell.Streams.Error | ForEach-Object { $_.ToString() })
    if (-not [string]::IsNullOrWhiteSpace($Handle.CompletionException)) { $errorLines += $Handle.CompletionException }
    $stderr = $errorLines -join [Environment]::NewLine
    [IO.File]::WriteAllText($Handle.StdoutPath, $stdout, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($Handle.StderrPath, $stderr, [Text.UTF8Encoding]::new($false))
}

function Complete-RunspaceInvocation($Handle) {
    if ($null -eq $Handle -or $Handle.EndInvoked) { return }
    try {
        $result = $Handle.PowerShell.EndInvoke($Handle.AsyncResult)
        foreach ($item in $result) { $Handle.Output.Add($item) }
    } catch {
        $Handle.CompletionException = $_.Exception.Message
    } finally {
        $Handle.EndInvoked = $true
        Write-RunspaceEvidence $Handle
    }
}

function Get-ResourceSamplerFailure($Handle, [string]$Prefix) {
    Write-RunspaceEvidence $Handle
    $errors = Read-ResourceSamplerLog $Handle.StderrPath
    $detail = if ([string]::IsNullOrWhiteSpace($errors)) { "" } else { " errors: $($errors.Trim())" }
    if (-not [string]::IsNullOrWhiteSpace($Handle.CompletionException)) { $detail += " exception: $($Handle.CompletionException)" }
    return "$Prefix; state $($Handle.PowerShell.InvocationStateInfo.State); stderr log: $($Handle.StderrPath).$detail"
}

function Assert-ResourceSamples([string]$OutputPath) {
    if (-not (Test-Path -LiteralPath $OutputPath -PathType Leaf)) { throw "Resource sampler did not create resource-samples.csv: $OutputPath" }
    if ((Get-Item -LiteralPath $OutputPath).Length -le 0) { throw "Resource sampler created an empty resource-samples.csv: $OutputPath" }
}

function Start-ResourceSampler(
    [string]$Collector,
    [string]$OutputPath,
    [string]$StopFile,
    [string]$Context,
    [string]$Namespace,
    [int]$MaxDurationSeconds,
    [string]$StdoutPath,
    [string]$StderrPath,
    [int]$StartupTimeoutSeconds = 10
) {
    $collectorPath = (Resolve-Path -LiteralPath $Collector -ErrorAction Stop).Path
    Remove-Item -LiteralPath $OutputPath,$StopFile,$StdoutPath,$StderrPath -Force -ErrorAction SilentlyContinue
    $powerShell = [PowerShell]::Create()
    $output = [Collections.ObjectModel.Collection[psobject]]::new()
    $null = $powerShell.AddCommand($collectorPath).
        AddParameter("OutputPath", $OutputPath).
        AddParameter("StopFile", $StopFile).
        AddParameter("Context", $Context).
        AddParameter("Namespace", $Namespace).
        AddParameter("MaxDurationSeconds", $MaxDurationSeconds)
    try {
        $asyncResult = $powerShell.BeginInvoke()
    } catch {
        $powerShell.Dispose()
        throw "Resource sampler runspace failed to start. stderr log: $StderrPath. $($_.Exception.Message)"
    }
    $handle = [pscustomobject]@{
        PowerShell=$powerShell; AsyncResult=$asyncResult; Output=$output
        StdoutPath=$StdoutPath; StderrPath=$StderrPath
        EndInvoked=$false; Disposed=$false; CompletionException=$null
    }
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        $state = $powerShell.InvocationStateInfo.State
        if ($state -eq "Failed" -or $state -eq "Stopped" -or $state -eq "Completed") {
            Complete-RunspaceInvocation $handle
            $message = Get-ResourceSamplerFailure $handle "Resource sampler terminated early"
            Stop-ResourceSampler $handle $StopFile
            throw $message
        }
        if ((Test-Path -LiteralPath $OutputPath -PathType Leaf) -and (Get-Item -LiteralPath $OutputPath).Length -gt 0) { return $handle }
        Start-Sleep -Milliseconds 100
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    Stop-ResourceSampler $handle $StopFile
    throw "Resource sampler failed to start: resource-samples.csv was not created within $StartupTimeoutSeconds seconds. stderr log: $StderrPath"
}

function Assert-ResourceSamplerHealthy($Handle, [string]$OutputPath, [string]$StderrPath) {
    if ($null -eq $Handle) { throw "Resource sampler runspace was not created. stderr log: $StderrPath" }
    $state = $Handle.PowerShell.InvocationStateInfo.State
    if ($state -ne "Running") {
        Complete-RunspaceInvocation $Handle
        throw (Get-ResourceSamplerFailure $Handle "Resource sampler terminated early")
    }
    Assert-ResourceSamples $OutputPath
}

function Stop-ResourceSampler($Handle, [string]$StopFile, [int]$TimeoutMilliseconds = 10000) {
    New-Item -ItemType File -Force -Path $StopFile | Out-Null
    if ($null -eq $Handle -or $Handle.Disposed) { return }
    try {
        if (-not $Handle.AsyncResult.IsCompleted) { $null = $Handle.AsyncResult.AsyncWaitHandle.WaitOne($TimeoutMilliseconds) }
        if (-not $Handle.AsyncResult.IsCompleted) { $Handle.PowerShell.Stop() }
        Complete-RunspaceInvocation $Handle
    } finally {
        Write-RunspaceEvidence $Handle
        $Handle.PowerShell.Dispose()
        $Handle.Disposed = $true
    }
}
