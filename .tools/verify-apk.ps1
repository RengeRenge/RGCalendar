# 只做校验：列出条目的压缩状态，确认 resources.arsc 未压缩、classes.dex 已合入
$ErrorActionPreference = 'Stop'
$work = if ($env:RG_VERIFY_WORK) { $env:RG_VERIFY_WORK } else { 'd:\pro\RGCalendar\probe\.build' }
$apk  = Join-Path $work 'unsigned.apk'

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$bad = @()
$hasDex = $false
$hasArsc = $false
try {
    Write-Host ("      entries = " + $zip.Entries.Count)
    foreach ($e in $zip.Entries) {
        $stored = ($e.CompressedLength -eq $e.Length)
        $n = $e.FullName
        if ($n -eq 'resources.arsc') { $hasArsc = $true; if (-not $stored) { $bad += $n } }
        if ($n -eq 'classes.dex')   { $hasDex = $true }
        if ($n -notlike 'res/*') {
            Write-Host ("        {0,-24} {1,10} bytes  {2}" -f $n, $e.Length, $(if ($stored) { 'stored' } else { 'deflate' }))
        }
    }
} finally {
    $zip.Dispose()
}
if (-not $hasArsc) { throw 'APK 里没有 resources.arsc' }
if (-not $hasDex)  { throw 'APK 里没有 classes.dex' }
if ($bad.Count -gt 0) { throw ('resources.arsc 仍是压缩的，安装会失败: ' + ($bad -join ',')) }
Write-Host '      verify = OK'