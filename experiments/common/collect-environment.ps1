param(
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [Parameter(Mandatory = $true)][string]$Experiment,
    [string]$Context = "kind-campus-ci",
    [string]$Namespace = "campus-market"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Invoke-Captured([string]$Command, [string[]]$Arguments) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) { return $null }
    try {
        $output = & $Command @Arguments 2>&1 | Out-String
        return [ordered]@{ exitCode = $LASTEXITCODE; output = $output.Trim() }
    } catch {
        return [ordered]@{ exitCode = 1; output = $_.Exception.Message }
    }
}

function Parse-JsonOutput($Captured) {
    if ($null -eq $Captured -or $Captured.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($Captured.output)) { return $null }
    try { return $Captured.output | ConvertFrom-Json } catch { return $Captured.output }
}

$cpuModel = $env:PROCESSOR_IDENTIFIER
$logicalProcessors = [Environment]::ProcessorCount
$physicalMemoryBytes = $null
try {
    $computer = Get-CimInstance Win32_ComputerSystem -ErrorAction Stop
    $physicalMemoryBytes = [long]$computer.TotalPhysicalMemory
    $processor = Get-CimInstance Win32_Processor -ErrorAction Stop | Select-Object -First 1
    if ($processor.Name) { $cpuModel = $processor.Name.Trim() }
} catch { }

$dockerInfo = Invoke-Captured "docker" @("info", "--format", '{"serverVersion":"{{.ServerVersion}}","operatingSystem":"{{.OperatingSystem}}","kernelVersion":"{{.KernelVersion}}","cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}')
$kubernetesVersion = Invoke-Captured "kubectl" @("--context", $Context, "version", "-o", "json")
$nodes = Invoke-Captured "kubectl" @("--context", $Context, "get", "nodes", "-o", "json")

$environment = [ordered]@{
    schemaVersion = 1
    capturedAt = [DateTimeOffset]::UtcNow.ToString("o")
    experiment = $Experiment
    git = [ordered]@{
        commit = (& git -C $repoRoot rev-parse HEAD).Trim()
        branch = (& git -C $repoRoot branch --show-current).Trim()
        midtermCheck = (& git -C $repoRoot rev-list -n 1 midterm-check 2>$null).Trim()
        microservicesEnd = (& git -C $repoRoot rev-list -n 1 microservices-end 2>$null).Trim()
    }
    host = [ordered]@{
        operatingSystem = [Environment]::OSVersion.VersionString
        architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
        cpuModel = $cpuModel
        logicalProcessors = $logicalProcessors
        physicalMemoryBytes = $physicalMemoryBytes
    }
    tools = [ordered]@{
        docker = Parse-JsonOutput $dockerInfo
        kind = Invoke-Captured "kind" @("version")
        kubectl = Parse-JsonOutput $kubernetesVersion
        node = Invoke-Captured "node" @("--version")
        java = Invoke-Captured "java" @("-version")
        maven = Invoke-Captured "mvn" @("-version")
    }
    kubernetes = [ordered]@{
        context = $Context
        namespace = $Namespace
        nodes = Parse-JsonOutput $nodes
    }
}

$parent = Split-Path -Parent $OutputPath
if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
$environmentJson = $environment | ConvertTo-Json -Depth 30
[IO.File]::WriteAllText($OutputPath, $environmentJson, [Text.UTF8Encoding]::new($false))
