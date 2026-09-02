$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$errors = @()
Get-ChildItem -LiteralPath $root -Recurse -Filter *.ps1 | ForEach-Object {
    $tokens = $null
    $parseErrors = $null
    [Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$tokens, [ref]$parseErrors) | Out-Null
    foreach ($parseError in $parseErrors) {
        $errors += "$($_.FullName):$($parseError.Extent.StartLineNumber): $($parseError.Message)"
    }
}
if ($errors.Count -gt 0) { throw ($errors -join [Environment]::NewLine) }
Write-Host "All experiment PowerShell files parsed successfully."
