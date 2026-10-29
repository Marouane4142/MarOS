#requires -Version 5.1
<# Builds only. For SDK selection and release signing options, use --help. #>
$ErrorActionPreference='Stop'
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) { $PSNativeCommandUseErrorActionPreference=$false }
$taskPython=(Get-Command python -ErrorAction Stop).Source
& $taskPython (Join-Path $PSScriptRoot 'build.py') @args
exit $LASTEXITCODE
