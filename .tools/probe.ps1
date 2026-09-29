# Executed via Invoke-Expression (never loaded as a script file), so ExecutionPolicy does not apply.
$ErrorActionPreference = 'Stop'
Write-Host "PROBE_OK"
Write-Host ("ps=" + $PSVersionTable.PSVersion.ToString())
Write-Host ("isAdmin=" + ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))
Write-Host ("writable_D=" + (Test-Path 'd:\pro\RGCalendar'))
$probe = 'd:\pro\RGCalendar\.tools\_write_test.txt'
Set-Content -LiteralPath $probe -Value 'ok' -Encoding ASCII
Write-Host ("write_test=" + (Test-Path $probe))
Remove-Item -LiteralPath $probe -Force
Write-Host ("curl=" + (Get-Command curl.exe -ErrorAction SilentlyContinue).Source)
Write-Host ("tar=" + (Get-Command tar.exe -ErrorAction SilentlyContinue).Source)