. (Join-Path $PSScriptRoot "resource-sampler.ps1")

function Assert-Tool([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "Required tool is missing: $Name" }
}

function Invoke-Checked([scriptblock]$Command, [string]$Description) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Description failed with exit code $LASTEXITCODE." }
}

function Invoke-NativeCaptureSafe([string]$Command, [string[]]$Arguments, [string]$Description) {
    $executable = (Get-Command $Command -ErrorAction Stop).Source
    $stderrPath = [IO.Path]::GetTempFileName()
    $previousLastExitCode = $global:LASTEXITCODE
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            # Windows PowerShell 5.1 promotes native stderr to ErrorRecord objects when
            # ErrorActionPreference is Stop. Capture stderr separately and trust the exit code.
            $ErrorActionPreference = "Continue"
            $stdout = @(& $executable @Arguments 2> $stderrPath)
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -ne 0) {
            $stderr = [IO.File]::ReadAllText($stderrPath).Trim()
            $detail = if ([string]::IsNullOrWhiteSpace($stderr)) { "" } else { " $stderr" }
            throw "$Description failed with exit code $exitCode.$detail"
        }
        return @($stdout | ForEach-Object { $_.ToString().Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    } finally {
        $global:LASTEXITCODE = $previousLastExitCode
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-KindClustersSafe([string]$KindCommand = "kind") {
    return @(Invoke-NativeCaptureSafe $KindCommand @("get", "clusters") "Kind cluster discovery")
}

function Remove-StalePerformanceShimPathEntries {
    $entries = @($env:PATH -split [regex]::Escape([IO.Path]::PathSeparator) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $clean = @($entries | Where-Object {
        $candidate = $_.Trim().Trim('"')
        $isShimDirectory = (Split-Path -Leaf $candidate) -eq "native-shims"
        -not ($isShimDirectory -and (Test-Path -LiteralPath (Join-Path $candidate "performance-native-launcher.exe") -PathType Leaf))
    })
    $env:PATH = $clean -join [IO.Path]::PathSeparator
}

function Resolve-PerformanceNativeExecutable([string]$Command, [string]$ShimDirectory, [string]$Name) {
    $path = (Resolve-Path -LiteralPath (Get-Command $Command -ErrorAction Stop).Source -ErrorAction Stop).Path
    $absoluteShim = [IO.Path]::GetFullPath($ShimDirectory).TrimEnd('\') + '\'
    $isAnyPerformanceShim = (Split-Path -Leaf (Split-Path -Parent $path)) -eq "native-shims"
    if ($isAnyPerformanceShim -or $path.StartsWith($absoluteShim, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Performance native launcher refused recursive self invocation: $Name real executable resolves inside native-shims ($path)."
    }
    return $path
}

function New-PerformanceNativeWrappers([string]$Directory, [hashtable]$Executables, [string]$StderrPath) {
    $absoluteDirectory = [IO.Path]::GetFullPath($Directory)
    New-Item -ItemType Directory -Force -Path $absoluteDirectory | Out-Null
    $absoluteDirectory = (Resolve-Path -LiteralPath $absoluteDirectory -ErrorAction Stop).Path
    $absoluteStderrPath = [IO.Path]::GetFullPath($StderrPath)
    [IO.File]::WriteAllText($absoluteStderrPath, "", [Text.UTF8Encoding]::new($false))
    $launcherPath = Join-Path $absoluteDirectory "performance-native-launcher.exe"
    foreach ($staleName in @("kind", "docker", "kubectl", "git")) {
        Remove-Item -LiteralPath (Join-Path $absoluteDirectory "$staleName.cmd") -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $absoluteDirectory "$staleName.exe") -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $launcherPath -Force -ErrorAction SilentlyContinue
    $launcherSource = @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;

public static class PerformanceNativeLauncher {
    private static void AppendStderr(string path, string value) {
        using (var mutex = new Mutex(false, "Local\\CampusPerformanceNativeStderr")) {
            mutex.WaitOne();
            try { File.AppendAllText(path, value, new UTF8Encoding(false)); }
            finally { mutex.ReleaseMutex(); }
        }
    }

    private static string Quote(string value) {
        if (value.Length == 0) return "\"\"";
        if (value.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '\"' }) < 0) return value;
        var result = new StringBuilder("\"");
        var slashes = 0;
        foreach (var character in value) {
            if (character == '\\') { slashes++; continue; }
            if (character == '\"') {
                result.Append('\\', slashes * 2 + 1).Append('\"');
            } else {
                result.Append('\\', slashes).Append(character);
            }
            slashes = 0;
        }
        result.Append('\\', slashes * 2).Append('\"');
        return result.ToString();
    }

    public static int Main(string[] args) {
        var command = Path.GetFileNameWithoutExtension(Environment.GetCommandLineArgs()[0]).ToUpperInvariant();
        var executable = Environment.GetEnvironmentVariable("PERFORMANCE_" + command + "_EXECUTABLE");
        var stderrPath = Environment.GetEnvironmentVariable("PERFORMANCE_NATIVE_STDERR");
        if (String.IsNullOrWhiteSpace(executable) || String.IsNullOrWhiteSpace(stderrPath)) return 127;
        var launcherPath = Path.GetFullPath(Environment.GetCommandLineArgs()[0]);
        var launcherDirectory = Path.GetDirectoryName(launcherPath).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var realPath = Path.GetFullPath(executable);
        if (String.Equals(realPath, launcherPath, StringComparison.OrdinalIgnoreCase) ||
            realPath.StartsWith(launcherDirectory, StringComparison.OrdinalIgnoreCase)) {
            AppendStderr(stderrPath, "Performance native launcher refused recursive self invocation: " + command.ToLowerInvariant() + " real executable resolves inside native-shims (" + realPath + ")." + Environment.NewLine);
            return 126;
        }
        var quoted = new List<string>();
        foreach (var argument in args) quoted.Add(Quote(argument));
        var start = new ProcessStartInfo(executable, String.Join(" ", quoted));
        start.UseShellExecute = false;
        start.RedirectStandardError = true;
        start.CreateNoWindow = true;
        start.StandardErrorEncoding = new UTF8Encoding(false);
        using (var child = Process.Start(start)) {
            var stderr = child.StandardError.ReadToEnd();
            child.WaitForExit();
            if (stderr.Length > 0) {
                AppendStderr(stderrPath, stderr);
            }
            return child.ExitCode;
        }
    }
}
'@
    Add-Type -TypeDefinition $launcherSource -Language CSharp -OutputAssembly $launcherPath -OutputType ConsoleApplication -ErrorAction Stop
    foreach ($name in @("kind", "docker", "kubectl")) {
        if ($Executables.ContainsKey($name)) {
            Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $absoluteDirectory "$name.exe") -Force
        }
    }
    if ($Executables.ContainsKey("git")) {
        Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $absoluteDirectory "git.exe") -Force
    }
    return $absoluteDirectory
}

function Invoke-PerformanceEnvironmentCollection(
    [string]$Architecture,
    [string]$CollectorPath,
    [string]$OutputPath,
    [string]$Experiment,
    [string]$Context,
    [string]$Namespace,
    [string]$EvidenceDirectory,
    [string]$GitCommand = "git",
    [string]$DockerCommand = "docker",
    [string]$KubectlCommand = "kubectl",
    [string]$KindCommand = "kind"
) {
    if (-not (Test-Path -LiteralPath $CollectorPath -PathType Leaf)) {
        throw "Environment collection failed for ${Architecture}: collector does not exist: $CollectorPath"
    }
    New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
    $stdoutPath = Join-Path $EvidenceDirectory "environment-collection.stdout.log"
    $stderrPath = Join-Path $EvidenceDirectory "environment-collection.stderr.log"
    $shimDirectoryCandidate = Join-Path $EvidenceDirectory "native-shims"
    $nativeStderrPath = [IO.Path]::GetFullPath((Join-Path $shimDirectoryCandidate "native.stderr.log"))

    # Resolve every executable before the shim directory is placed on PATH. This also
    # rejects a stale performance launcher selected from an inherited PATH.
    $executables = @{
        git = Resolve-PerformanceNativeExecutable $GitCommand $shimDirectoryCandidate "git"
        docker = Resolve-PerformanceNativeExecutable $DockerCommand $shimDirectoryCandidate "docker"
        kubectl = Resolve-PerformanceNativeExecutable $KubectlCommand $shimDirectoryCandidate "kubectl"
        kind = Resolve-PerformanceNativeExecutable $KindCommand $shimDirectoryCandidate "kind"
    }
    $shimDirectory = New-PerformanceNativeWrappers $shimDirectoryCandidate $executables $nativeStderrPath
    $previousEnvironment = @{
        PATH = $env:PATH
        GIT = $env:PERFORMANCE_GIT_EXECUTABLE
        DOCKER = $env:PERFORMANCE_DOCKER_EXECUTABLE
        KUBECTL = $env:PERFORMANCE_KUBECTL_EXECUTABLE
        KIND = $env:PERFORMANCE_KIND_EXECUTABLE
        STDERR = $env:PERFORMANCE_NATIVE_STDERR
    }
    $previousErrorActionPreference = $ErrorActionPreference
    $powerShell = $null
    try {
        $env:PERFORMANCE_GIT_EXECUTABLE = $executables.git
        $env:PERFORMANCE_DOCKER_EXECUTABLE = $executables.docker
        $env:PERFORMANCE_KUBECTL_EXECUTABLE = $executables.kubectl
        $env:PERFORMANCE_KIND_EXECUTABLE = $executables.kind
        $env:PERFORMANCE_NATIVE_STDERR = $nativeStderrPath
        $env:PATH = $shimDirectory + [IO.Path]::PathSeparator + $previousEnvironment.PATH

        $repoRoot = (Resolve-Path (Join-Path (Split-Path -Parent $CollectorPath) "..\..")).Path
        $gitChecks = @(
            [pscustomobject]@{ Name="commit"; Arguments=@("-C",$repoRoot,"rev-parse","HEAD"); Pattern='^[0-9a-f]{40}$' },
            [pscustomobject]@{ Name="branch"; Arguments=@("-C",$repoRoot,"branch","--show-current"); Pattern='^.+$' },
            [pscustomobject]@{ Name="midterm-check"; Arguments=@("-C",$repoRoot,"rev-list","-n","1","midterm-check"); Pattern='^[0-9a-f]{40}$' },
            [pscustomobject]@{ Name="microservices-end"; Arguments=@("-C",$repoRoot,"rev-list","-n","1","microservices-end"); Pattern='^[0-9a-f]{40}$' }
        )
        foreach ($check in $gitChecks) {
            $lines = @(Invoke-NativeCaptureSafe "git" $check.Arguments "Environment collection Git $($check.Name) query for $Architecture")
            $text = $lines | Select-Object -Last 1
            if ([string]::IsNullOrWhiteSpace($text)) {
                throw "Environment collection Git $($check.Name) query for $Architecture returned no stdout."
            }
            if ($text.Trim() -notmatch $check.Pattern) {
                throw "Environment collection Git $($check.Name) query for $Architecture returned invalid output."
            }
        }

        $powerShell = [PowerShell]::Create()
        $scriptText = '$ErrorActionPreference = "Continue"; & $args[0] -OutputPath $args[1] -Experiment $args[2] -Context $args[3] -Namespace $args[4]'
        $null = $powerShell.AddScript($scriptText).AddArgument($CollectorPath).AddArgument($OutputPath).AddArgument($Experiment).AddArgument($Context).AddArgument($Namespace)
        $invocationException = $null
        try {
            $asyncResult = $powerShell.BeginInvoke()
            $output = @($powerShell.EndInvoke($asyncResult))
        } catch {
            $output = @()
            $invocationException = $_.Exception.Message
        }
        $state = $powerShell.InvocationStateInfo.State
        $stdout = @($output | ForEach-Object { $_.ToString() })
        $nativeStderr = if (Test-Path -LiteralPath $nativeStderrPath) { [IO.File]::ReadAllText($nativeStderrPath, [Text.Encoding]::UTF8).TrimEnd() } else { "" }
        $stderr = @($powerShell.Streams.Error | ForEach-Object { $_.ToString() })
        if (-not [string]::IsNullOrWhiteSpace($nativeStderr)) { $stderr = @($nativeStderr) + $stderr }
        if (-not [string]::IsNullOrWhiteSpace($invocationException)) { $stderr += $invocationException }
        [IO.File]::WriteAllText($stdoutPath, ($stdout -join [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($stderrPath, ($stderr -join [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
        if ($state -ne "Completed" -or -not [string]::IsNullOrWhiteSpace($invocationException)) {
            throw "Environment collection failed for $Architecture with state $state. See $stderrPath."
        }
        if (-not (Test-Path -LiteralPath $OutputPath -PathType Leaf)) {
            throw "Environment collection failed for ${Architecture}: environment.json was not created. See $stderrPath."
        }
        $json = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $OutputPath).Path, [Text.Encoding]::UTF8)
        if ([string]::IsNullOrWhiteSpace($json)) {
            throw "Environment collection failed for ${Architecture}: environment.json is empty. See $stderrPath."
        }
        try { $document = $json | ConvertFrom-Json -ErrorAction Stop } catch {
            throw "Environment collection failed for ${Architecture}: environment.json is invalid JSON. See $stderrPath. $($_.Exception.Message)"
        }
        if ([string]::IsNullOrWhiteSpace($document.git.commit)) {
            throw "Environment collection failed for ${Architecture}: environment.json has no Git commit. See $stderrPath."
        }
        return $document
    } catch {
        $existingStderr = if (Test-Path -LiteralPath $stderrPath) { [IO.File]::ReadAllText($stderrPath, [Text.Encoding]::UTF8) } else { "" }
        $failureText = $_.Exception.Message
        if ($existingStderr -notmatch [regex]::Escape($failureText)) {
            $separator = if ([string]::IsNullOrWhiteSpace($existingStderr)) { "" } else { [Environment]::NewLine }
            [IO.File]::WriteAllText($stderrPath, $existingStderr + $separator + $failureText, [Text.UTF8Encoding]::new($false))
        }
        throw
    } finally {
        if ($null -ne $powerShell) { $powerShell.Dispose() }
        $env:PATH = $previousEnvironment.PATH
        $env:PERFORMANCE_GIT_EXECUTABLE = $previousEnvironment.GIT
        $env:PERFORMANCE_DOCKER_EXECUTABLE = $previousEnvironment.DOCKER
        $env:PERFORMANCE_KUBECTL_EXECUTABLE = $previousEnvironment.KUBECTL
        $env:PERFORMANCE_KIND_EXECUTABLE = $previousEnvironment.KIND
        $env:PERFORMANCE_NATIVE_STDERR = $previousEnvironment.STDERR
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Get-KubectlResourcesSafe([string]$Context, [string]$Namespace, [string]$Resource) {
    return @(Invoke-NativeCaptureSafe "kubectl" @("--context", $Context, "-n", $Namespace, "get", $Resource, "-o", "name") "kubectl $Resource discovery")
}

function Get-ResourceFairnessLimits([string]$Context, [string]$Namespace, [string]$Resource, [string]$KubectlCommand = "kubectl") {
    $output = @(Invoke-NativeCaptureSafe $KubectlCommand @("--context", $Context, "-n", $Namespace, "get", $Resource, "-o", "json") "Cannot read resource fairness data for $Resource")
    if ($output.Count -eq 0) { throw "Cannot read resource fairness data for ${Resource}: kubectl returned empty output." }
    $json = $output -join [Environment]::NewLine
    try {
        $document = $json | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Cannot parse resource fairness data for ${Resource}: $($_.Exception.Message)"
    }
    $cpu = $document.spec.template.spec.containers[0].resources.limits.cpu
    $memory = $document.spec.template.spec.containers[0].resources.limits.memory
    if ([string]::IsNullOrWhiteSpace($cpu) -or [string]::IsNullOrWhiteSpace($memory)) {
        throw "Resource fairness data for $Resource does not contain CPU and memory limits."
    }
    return [pscustomobject]@{ Cpu=$cpu.ToString(); Memory=$memory.ToString() }
}

function ConvertTo-KubernetesSelector($MatchLabels) {
    if ($null -eq $MatchLabels) { throw "StatefulSet/campus-mysql has no selector.matchLabels." }
    $parts = @($MatchLabels.psobject.Properties | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" })
    if ($parts.Count -eq 0) { throw "StatefulSet/campus-mysql has an empty selector.matchLabels." }
    return $parts -join ","
}

function Get-MySqlPodName([string]$Architecture, [string]$Context, [string]$Namespace, [string]$KubectlCommand = "kubectl") {
    $statefulSetOutput = @(Invoke-NativeCaptureSafe $KubectlCommand @("--context", $Context, "-n", $Namespace, "get", "statefulset/campus-mysql", "-o", "json") "Cannot read MySQL StatefulSet for $Architecture")
    if ($statefulSetOutput.Count -eq 0) { throw "Cannot read MySQL StatefulSet for ${Architecture}: kubectl returned empty output." }
    try { $statefulSet = ($statefulSetOutput -join [Environment]::NewLine) | ConvertFrom-Json -ErrorAction Stop } catch { throw "Cannot parse MySQL StatefulSet for ${Architecture}: $($_.Exception.Message)" }
    $selector = ConvertTo-KubernetesSelector $statefulSet.spec.selector.matchLabels
    $podOutput = @(Invoke-NativeCaptureSafe $KubectlCommand @("--context", $Context, "-n", $Namespace, "get", "pods", "-l", $selector, "-o", "json") "Cannot list MySQL pods for $Architecture using selector $selector")
    if ($podOutput.Count -eq 0) { throw "Cannot list MySQL pods for ${Architecture}: kubectl returned empty output for selector $selector." }
    try { $podList = ($podOutput -join [Environment]::NewLine) | ConvertFrom-Json -ErrorAction Stop } catch { throw "Cannot parse MySQL pod list for ${Architecture}: $($_.Exception.Message)" }
    $items = @($podList.items)
    if ($items.Count -eq 0) { throw "No MySQL pods found for $Architecture using selector $selector." }
    $owned = @($items | Where-Object { @($_.metadata.ownerReferences | Where-Object { $_.kind -eq "StatefulSet" -and $_.name -eq "campus-mysql" }).Count -gt 0 })
    if ($owned.Count -eq 0) { throw "No pods owned by StatefulSet/campus-mysql were found for $Architecture using selector $selector." }
    if ($owned.Count -gt 1) {
        $ready = @($owned | Where-Object { @($_.status.conditions | Where-Object { $_.type -eq "Ready" -and $_.status -eq "True" }).Count -gt 0 })
        if ($ready.Count -ne 1) { throw "Cannot uniquely select a Ready MySQL pod for $Architecture using selector $selector; owned=$($owned.Count), ready=$($ready.Count)." }
        $owned = $ready
    }
    $name = $owned[0].metadata.name
    if ([string]::IsNullOrWhiteSpace($name)) { throw "The selected MySQL pod for $Architecture has no metadata.name." }
    return $name.ToString().Trim()
}

function Invoke-KubectlDataCommand([string[]]$Arguments, [string]$Description, [string]$StdoutPath, [string]$StderrPath, [string]$KubectlCommand = "kubectl") {
    $kubectl = (Get-Command $KubectlCommand -ErrorAction Stop).Source
    $process = Start-Process -FilePath $kubectl -ArgumentList $Arguments -RedirectStandardOutput $StdoutPath -RedirectStandardError $StderrPath -WindowStyle Hidden -Wait -PassThru
    $exitCode = $process.ExitCode
    $stdout = if (Test-Path -LiteralPath $StdoutPath) { [IO.File]::ReadAllText($StdoutPath) } else { "" }
    $stderr = if (Test-Path -LiteralPath $StderrPath) { [IO.File]::ReadAllText($StderrPath) } else { "" }
    [IO.File]::WriteAllText($StdoutPath, $stdout, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($StderrPath, $stderr, [Text.UTF8Encoding]::new($false))
    if ($exitCode -ne 0) { throw "$Description failed with exit code $exitCode. stderr: $StderrPath" }
    return $stdout
}

function Copy-LocalFileToPod(
    [string]$Context,
    [string]$Namespace,
    [string]$Pod,
    [string]$LocalPath,
    [string]$RemotePath,
    [string]$StdoutLog,
    [string]$StderrLog,
    [string]$Description,
    [string]$KubectlCommand = "kubectl"
) {
    $resolvedLocalPath = (Resolve-Path -LiteralPath $LocalPath -ErrorAction Stop).Path
    $localDirectory = Split-Path -Parent $resolvedLocalPath
    $localName = Split-Path -Leaf $resolvedLocalPath
    $relativeLocalPath = ".\$localName"
    $remoteSpecification = "${Pod}:$RemotePath"
    $copyDescription = "$Description (local file: $resolvedLocalPath; remote path: $remoteSpecification)"
    Push-Location -LiteralPath $localDirectory
    try {
        return Invoke-KubectlDataCommand @("--context",$Context,"-n",$Namespace,"cp",$relativeLocalPath,$remoteSpecification) $copyDescription $StdoutLog $StderrLog $KubectlCommand
    } finally {
        Pop-Location
    }
}

function Write-Utf8Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
}

function Write-PerformanceErrorDetails([string]$Path, [string]$Architecture, [string]$Stage, [Management.Automation.ErrorRecord]$ErrorRecord) {
    $invocation = $ErrorRecord.InvocationInfo
    $details = [ordered]@{
        schemaVersion = 1
        architecture = $Architecture
        stage = $Stage
        capturedAt = [DateTimeOffset]::UtcNow.ToString("o")
        message = $ErrorRecord.Exception.Message
        exceptionType = $ErrorRecord.Exception.GetType().FullName
        scriptStackTrace = $ErrorRecord.ScriptStackTrace
        positionMessage = if ($null -eq $invocation) { $null } else { $invocation.PositionMessage }
        scriptName = if ($null -eq $invocation) { $null } else { $invocation.ScriptName }
        scriptLineNumber = if ($null -eq $invocation) { $null } else { $invocation.ScriptLineNumber }
        offsetInLine = if ($null -eq $invocation) { $null } else { $invocation.OffsetInLine }
    }
    Write-Utf8Json $Path $details
}

function Initialize-PerformanceDataImportEvidence([string]$ArchitectureDirectory) {
    $path = Join-Path $ArchitectureDirectory "data-import"
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    return (Resolve-Path -LiteralPath $path -ErrorAction Stop).Path
}

function Expand-TaggedWorkspace([string]$RepoRoot, [string]$Tag, [string]$Sha, [string]$WorkspaceRoot) {
    $target = [IO.Path]::GetFullPath((Join-Path $WorkspaceRoot $Tag))
    $root = [IO.Path]::GetFullPath($WorkspaceRoot).TrimEnd('\') + '\'
    if (-not $target.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe workspace target: $target" }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    $archive = Join-Path $WorkspaceRoot "$Tag.zip"
    Invoke-Checked { git -C $RepoRoot archive --format=zip --output=$archive $Sha } "Archive $Tag"
    Expand-Archive -LiteralPath $archive -DestinationPath $target -Force
    Remove-Item -LiteralPath $archive -Force
    return $target
}

function Get-WebDeploymentName([string]$Architecture) {
    if ($Architecture -eq "midterm-check") { return "campus-web" }
    if ($Architecture -eq "microservices-end") { return "web" }
    throw "Unknown performance architecture: $Architecture"
}

function Invoke-KindLifecycle(
    [string]$Workspace,
    [string]$Action,
    [string]$ClusterName,
    [string]$EvidenceDirectory,
    [string]$KindCommand = "kind",
    [string]$DockerCommand = "docker",
    [string]$KubectlCommand = "kubectl"
) {
    $script = Join-Path $Workspace "scripts\ci\kind-local.ps1"
    if (-not (Test-Path -LiteralPath $script)) { throw "Tag workspace has no Kind entry: $script" }
    New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
    $stdoutPath = Join-Path $EvidenceDirectory "kind-$Action.stdout.log"
    $stderrPath = Join-Path $EvidenceDirectory "kind-$Action.stderr.log"
    $shimDirectoryCandidate = Join-Path $EvidenceDirectory "native-shims"
    $nativeStderrPath = [IO.Path]::GetFullPath((Join-Path $shimDirectoryCandidate "native.stderr.log"))
    $nativeExecutables = @{
        kind = Resolve-PerformanceNativeExecutable $KindCommand $shimDirectoryCandidate "kind"
        docker = Resolve-PerformanceNativeExecutable $DockerCommand $shimDirectoryCandidate "docker"
        kubectl = Resolve-PerformanceNativeExecutable $KubectlCommand $shimDirectoryCandidate "kubectl"
    }
    $shimDirectory = New-PerformanceNativeWrappers $shimDirectoryCandidate $nativeExecutables $nativeStderrPath
    $powerShell = [PowerShell]::Create()
$runspaceScript = @'
$ErrorActionPreference = "Continue"
$previousEnvironment = @{
    PATH = $env:PATH
    KIND = $env:PERFORMANCE_KIND_EXECUTABLE
    DOCKER = $env:PERFORMANCE_DOCKER_EXECUTABLE
    KUBECTL = $env:PERFORMANCE_KUBECTL_EXECUTABLE
    STDERR = $env:PERFORMANCE_NATIVE_STDERR
}
try {
    $env:PERFORMANCE_KIND_EXECUTABLE = $args[4].kind
    $env:PERFORMANCE_DOCKER_EXECUTABLE = $args[4].docker
    $env:PERFORMANCE_KUBECTL_EXECUTABLE = $args[4].kubectl
    $env:PERFORMANCE_NATIVE_STDERR = $args[5]
    $env:PATH = $args[3] + [IO.Path]::PathSeparator + $previousEnvironment.PATH
    & $args[0] -Action $args[1] -ClusterName $args[2]
} finally {
    $env:PATH = $previousEnvironment.PATH
    $env:PERFORMANCE_KIND_EXECUTABLE = $previousEnvironment.KIND
    $env:PERFORMANCE_DOCKER_EXECUTABLE = $previousEnvironment.DOCKER
    $env:PERFORMANCE_KUBECTL_EXECUTABLE = $previousEnvironment.KUBECTL
    $env:PERFORMANCE_NATIVE_STDERR = $previousEnvironment.STDERR
}
'@
    $null = $powerShell.AddScript($runspaceScript).AddArgument($script).AddArgument($Action).AddArgument($ClusterName).AddArgument($shimDirectory).AddArgument($nativeExecutables).AddArgument($nativeStderrPath)
    $invocationException = $null
    try {
        $asyncResult = $powerShell.BeginInvoke()
        $output = @($powerShell.EndInvoke($asyncResult))
    } catch {
        $output = @()
        $invocationException = $_.Exception.Message
    }
    $state = $powerShell.InvocationStateInfo.State
    $information = @($powerShell.Streams.Information | ForEach-Object { $_.MessageData.ToString() })
    $stdout = @($output | ForEach-Object { $_.ToString() }) + $information
    $nativeStderr = if (Test-Path -LiteralPath $nativeStderrPath) { [IO.File]::ReadAllText($nativeStderrPath, [Text.Encoding]::UTF8).TrimEnd() } else { "" }
    $stderr = @()
    if (-not [string]::IsNullOrWhiteSpace($nativeStderr)) { $stderr += $nativeStderr }
    $stderr += @($powerShell.Streams.Error | ForEach-Object { $_.ToString() })
    if (-not [string]::IsNullOrWhiteSpace($invocationException)) { $stderr += $invocationException }
    [IO.File]::WriteAllText($stdoutPath, ($stdout -join [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($stderrPath, ($stderr -join [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
    $powerShell.Dispose()
    if ($stdout.Count -gt 0) { Write-Host ($stdout -join [Environment]::NewLine) }
    if ($stderr.Count -gt 0) { Write-Host ($stderr -join [Environment]::NewLine) -ForegroundColor Red }
    if ($state -ne "Completed" -or -not [string]::IsNullOrWhiteSpace($invocationException)) {
        throw "Kind $Action failed in isolated runspace for $Workspace with state $state. See $stdoutPath and $stderrPath."
    }
    $clusters = @(Get-KindClustersSafe $KindCommand)
    if ($Action -eq "up" -and $clusters -notcontains $ClusterName) { throw "Kind up completed but cluster '$ClusterName' does not exist." }
    if ($Action -eq "down" -and $clusters -contains $ClusterName) { throw "Kind down completed but cluster '$ClusterName' still exists." }
}

function Set-FairResources([string]$Architecture, [string]$Context, [string]$Namespace) {
    $webDeployment = Get-WebDeploymentName $Architecture
    Invoke-Checked { kubectl --context $Context -n $Namespace set resources statefulset/campus-mysql --limits=cpu=1,memory=1Gi --requests=cpu=250m,memory=512Mi } "Set MySQL resources"
    Invoke-Checked { kubectl --context $Context -n $Namespace set resources "deployment/$webDeployment" --limits=cpu=100m,memory=128Mi --requests=cpu=25m,memory=32Mi } "Set Web resources"
    if ($Architecture -eq "midterm-check") {
        Invoke-Checked { kubectl --context $Context -n $Namespace set resources deployment/campus-backend --limits=cpu=2,memory=2Gi --requests=cpu=500m,memory=512Mi } "Set monolith resources"
        Invoke-Checked { kubectl --context $Context -n $Namespace rollout status deployment/campus-backend --timeout=360s } "Wait for monolith resource rollout"
        $applications = @("campus-backend")
    } else {
        foreach ($deployment in @("gateway","account-service","marketplace-service","trading-service","governance-service")) {
            $memory = if ($deployment -eq "gateway") { "448Mi" } else { "400Mi" }
            Invoke-Checked { kubectl --context $Context -n $Namespace set resources "deployment/$deployment" "--limits=cpu=400m,memory=$memory" --requests=cpu=100m,memory=128Mi } "Set $deployment resources"
            Invoke-Checked { kubectl --context $Context -n $Namespace rollout status "deployment/$deployment" --timeout=360s } "Wait for $deployment resource rollout"
        }
        $applications = @("gateway","account-service","marketplace-service","trading-service","governance-service")
    }
    foreach ($deployment in $applications) {
        Invoke-Checked { kubectl --context $Context -n $Namespace set env "deployment/$deployment" JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 LOGGING_LEVEL_ROOT=INFO SPRING_JPA_SHOW_SQL=false } "Set deterministic JVM/logging options for $deployment"
        Invoke-Checked { kubectl --context $Context -n $Namespace rollout status "deployment/$deployment" --timeout=360s } "Wait for $deployment configuration rollout"
    }
    Invoke-Checked { kubectl --context $Context -n $Namespace rollout status statefulset/campus-mysql --timeout=360s } "Wait for MySQL resource rollout"
    Invoke-Checked { kubectl --context $Context -n $Namespace rollout status "deployment/$webDeployment" --timeout=240s } "Wait for Web resource rollout"
    $hpa = @(Get-KubectlResourcesSafe $Context $Namespace "hpa")
    if ($hpa.Count -ne 0) { throw "HPA must be disabled for performance experiments: $($hpa -join ', ')" }
    foreach ($deployment in $applications + @($webDeployment)) {
        $replicaOutput = @(Invoke-NativeCaptureSafe "kubectl" @("--context", $Context, "-n", $Namespace, "get", "deployment/$deployment", "-o", "jsonpath={.spec.replicas}") "Cannot read replica count for $deployment")
        $replicaText = $replicaOutput | Select-Object -Last 1
        if ([string]::IsNullOrWhiteSpace($replicaText)) { throw "Cannot read replica count for ${deployment}: kubectl returned empty output." }
        $replicas = $replicaText.Trim()
        if ($replicas -ne "1") { throw "$deployment must have exactly one replica." }
    }
}

function Get-FairnessEvidence([string]$Architecture, [string]$Context, [string]$Namespace) {
    $webDeployment = Get-WebDeploymentName $Architecture
    $expected = [ordered]@{
        "statefulset/campus-mysql" = @("1", "1Gi")
        "deployment/$webDeployment" = @("100m", "128Mi")
    }
    if ($Architecture -eq "midterm-check") {
        $expected["deployment/campus-backend"] = @("2", "2Gi")
    } else {
        $expected["deployment/gateway"] = @("400m", "448Mi")
        foreach ($name in @("account-service","marketplace-service","trading-service","governance-service")) {
            $expected["deployment/$name"] = @("400m", "400Mi")
        }
    }
    $workloads = @()
    foreach ($resource in $expected.Keys) {
        $limits = Get-ResourceFairnessLimits $Context $Namespace $resource
        $actual = @($limits.Cpu, $limits.Memory)
        if ($actual[0] -ne $expected[$resource][0] -or $actual[1] -ne $expected[$resource][1]) {
            throw "Resource fairness verification failed for $resource; expected $($expected[$resource] -join '/'), got $($actual -join '/')."
        }
        $workloads += [ordered]@{ resource=$resource; cpuLimit=$actual[0]; memoryLimit=$actual[1] }
    }
    $applicationNames = if ($Architecture -eq "midterm-check") { @("campus-backend") } else { @("gateway","account-service","marketplace-service","trading-service","governance-service") }
    foreach ($name in $applicationNames) {
        $environmentOutput = @(Invoke-NativeCaptureSafe "kubectl" @("--context", $Context, "-n", $Namespace, "get", "deployment/$name", "-o", "json") "Cannot verify JVM/logging options for $name")
        if ($environmentOutput.Count -eq 0) { throw "Cannot verify JVM/logging options for ${name}: kubectl returned empty output." }
        try { $environmentDocument = ($environmentOutput -join [Environment]::NewLine) | ConvertFrom-Json -ErrorAction Stop } catch { throw "Cannot parse JVM/logging options for ${name}: $($_.Exception.Message)" }
        $environment = @($environmentDocument.spec.template.spec.containers[0].env)
        $requiredEnvironment = [ordered]@{ JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=75.0'; LOGGING_LEVEL_ROOT='INFO'; SPRING_JPA_SHOW_SQL='false' }
        foreach ($required in $requiredEnvironment.GetEnumerator()) {
            $entry = @($environment | Where-Object name -eq $required.Key)
            if ($entry.Count -ne 1 -or $entry[0].value -ne $required.Value) { throw "Fairness option $($required.Key) is not fixed for $name." }
        }
    }
    $hpa = @(Get-KubectlResourcesSafe $Context $Namespace "hpa")
    if ($hpa.Count -ne 0) { throw "HPA fairness verification failed." }
    return [ordered]@{ schemaVersion=1; architecture=$Architecture; verifiedAt=[DateTimeOffset]::UtcNow.ToString("o"); hpaDisabled=$true; applicationReplicas=1; javaToolOptions='-XX:MaxRAMPercentage=75.0'; loggingLevelRoot='INFO'; sqlLogging=$false; workloads=$workloads }
}

function Import-AndVerifyData([string]$Architecture, [string]$SqlDirectory, [string]$Context, [string]$Namespace, [long[]]$FixedItemIds, [string]$OutputPath) {
    $pod = Get-MySqlPodName $Architecture $Context $Namespace
    $importEvidence = Join-Path (Split-Path -Parent $OutputPath) "data-import"
    New-Item -ItemType Directory -Force -Path $importEvidence | Out-Null
    $mysqlHelper = Join-Path $PSScriptRoot "mysql-client.sh"
    Copy-LocalFileToPod $Context $Namespace $pod $mysqlHelper "/tmp/performance-mysql-client.sh" (Join-Path $importEvidence "mysql-client.copy.stdout.log") (Join-Path $importEvidence "mysql-client.copy.stderr.log") "Copy MySQL client helper for $Architecture" | Out-Null
    $imports = if ($Architecture -eq "midterm-check") {
        @(@{ File="midterm-check.sql"; Database="campus_secondhand" })
    } else {
        @(@{ File="microservices-end-account.sql"; Database="campus_account" }, @{ File="microservices-end-marketplace.sql"; Database="campus_marketplace" })
    }
    foreach ($import in $imports) {
        $source = Join-Path $SqlDirectory $import.File
        $logBase = Join-Path $importEvidence $import.File
        Copy-LocalFileToPod $Context $Namespace $pod $source "/tmp/$($import.File)" "$logBase.copy.stdout.log" "$logBase.copy.stderr.log" "Copy $($import.File) for $Architecture" | Out-Null
        Invoke-KubectlDataCommand @("--context",$Context,"-n",$Namespace,"exec",$pod,"--","sh","/tmp/performance-mysql-client.sh","import",$import.Database,"/tmp/$($import.File)") "Import $($import.File) for architecture $Architecture database $($import.Database)" "$logBase.stdout.log" "$logBase.stderr.log" | Out-Null
    }
    $ids = $FixedItemIds -join ','
    $queries = if ($Architecture -eq "midterm-check") {
        @(@{Database="campus_secondhand"; Sql="SELECT (SELECT COUNT(*) FROM users),(SELECT COUNT(*) FROM items),(SELECT COUNT(*) FROM item_tags),(SELECT COUNT(*) FROM messages),(SELECT COUNT(*) FROM items WHERE id IN ($ids)),(SELECT COUNT(DISTINCT item_id) FROM messages WHERE item_id IN ($ids));"; Expected=@(2500,20000,24000,50000,10,10)})
    } else {
        @(@{Database="campus_account"; Sql="SELECT COUNT(*) FROM users;"; Expected=@(2500)}, @{Database="campus_marketplace"; Sql="SELECT (SELECT COUNT(*) FROM searchable_user_projection),(SELECT COUNT(*) FROM items),(SELECT COUNT(*) FROM item_tags),(SELECT COUNT(*) FROM messages),(SELECT COUNT(*) FROM items WHERE id IN ($ids)),(SELECT COUNT(DISTINCT item_id) FROM messages WHERE item_id IN ($ids));"; Expected=@(2500,20000,24000,50000,10,10)})
    }
    $evidence = @()
    foreach ($query in $queries) {
        $validationFile = "$($query.Database)-validation.sql"
        $validationPath = Join-Path $importEvidence $validationFile
        [IO.File]::WriteAllText($validationPath, $query.Sql + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
        $validationLogBase = Join-Path $importEvidence "$($query.Database)-validation"
        Copy-LocalFileToPod $Context $Namespace $pod $validationPath "/tmp/$validationFile" "$validationLogBase.copy.stdout.log" "$validationLogBase.copy.stderr.log" "Copy validation SQL for $Architecture database $($query.Database)" | Out-Null
        $queryOutput = Invoke-KubectlDataCommand @("--context",$Context,"-n",$Namespace,"exec",$pod,"--","sh","/tmp/performance-mysql-client.sh","validate",$query.Database,"/tmp/$validationFile") "Validate data for architecture $Architecture database $($query.Database)" "$validationLogBase.stdout.log" "$validationLogBase.stderr.log"
        $lineText = @($queryOutput -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) | Select-Object -Last 1
        if ([string]::IsNullOrWhiteSpace($lineText)) { throw "Data verification query returned empty output for $($query.Database)." }
        $line = $lineText.Trim()
        $actual = @($line -split "`t" | ForEach-Object { [long]$_ })
        if (($actual -join ',') -ne ($query.Expected -join ',')) { throw "Unexpected counts in $($query.Database): $($actual -join ',')" }
        $evidence += [ordered]@{ database=$query.Database; values=$actual }
    }
    Write-Utf8Json $OutputPath ([ordered]@{ schemaVersion=1; architecture=$Architecture; verifiedAt=[DateTimeOffset]::UtcNow.ToString("o"); fixedItemIds=$FixedItemIds; checks=$evidence })
}

function Start-PerformanceForward([string]$Context, [string]$Namespace, [int]$Port, [string]$LogDirectory) {
    $kubectl = (Get-Command kubectl -ErrorAction Stop).Source
    $process = Start-Process -FilePath $kubectl -ArgumentList @("--context",$Context,"-n",$Namespace,"port-forward","service/web","${Port}:80") -RedirectStandardOutput (Join-Path $LogDirectory "port-forward.log") -RedirectStandardError (Join-Path $LogDirectory "port-forward-error.log") -WindowStyle Hidden -PassThru
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(45)
    do {
        if ($process.HasExited) { throw "Web port-forward exited before readiness." }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/actuator/health/liveness" -TimeoutSec 3
            if ($health.status -eq "UP") { return $process }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    throw "Timed out waiting for the Web/API port-forward."
}
