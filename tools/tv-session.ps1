#requires -Version 5.1
<# All options, including the explicit ADB --serial, are forwarded to Python. #>
$ErrorActionPreference='Stop'
$taskPython=(Get-Command python -ErrorAction Stop).Source
& $taskPython (Join-Path $PSScriptRoot 'tv-session.py') @args
exit $LASTEXITCODE
