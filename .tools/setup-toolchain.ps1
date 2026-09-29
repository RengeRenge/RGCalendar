# 下载并解压最小工具链（不依赖 Android Studio）
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root  = 'd:\pro\RGCalendar'
$tools = Join-Path $root '.tools'
$dl    = Join-Path $tools 'downloads'
$sdk   = Join-Path $tools 'android-sdk'
New-Item -ItemType Directory -Force -Path $tools, $dl, $sdk | Out-Null

function Get-Remote($url, $out) {
    if ((Test-Path $out) -and ((Get-Item $out).Length -gt 0)) {
        Write-Host ("skip cached : " + (Split-Path $out -Leaf))
        return
    }
    Write-Host ("download    : " + (Split-Path $out -Leaf))
    & curl.exe -L --fail --retry 3 --connect-timeout 30 --no-progress-meter -o $out $url
    if ($LASTEXITCODE -ne 0) { throw ("download failed: " + $url) }
    Write-Host ("        ok  : " + [math]::Round((Get-Item $out).Length / 1MB, 1) + " MB")
}

$jdkZip   = Join-Path $dl 'jdk17.zip'
$ptZip    = Join-Path $dl 'platform-tools.zip'
$ctZip    = Join-Path $dl 'cmdline-tools.zip'

Get-Remote 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $jdkZip
Get-Remote 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'            $ptZip
Get-Remote 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'    $ctZip

Write-Host ''
Write-Host 'extracting...'

# --- JDK 17 ---
$jdkDir = Join-Path $tools 'jdk17'
if (-not (Test-Path (Join-Path $jdkDir 'bin\java.exe'))) {
    if (Test-Path $jdkDir) { Remove-Item -Recurse -Force $jdkDir }
    New-Item -ItemType Directory -Force -Path $jdkDir | Out-Null
    & tar.exe -xf $jdkZip -C $jdkDir
    if ($LASTEXITCODE -ne 0) { throw 'jdk extract failed' }
    # 解压后多一层 jdk-17.x.y+z 目录，摊平到 jdk17
    $inner = Get-ChildItem $jdkDir -Directory | Where-Object { $_.Name -like 'jdk-17*' } | Select-Object -First 1
    if ($inner) {
        Get-ChildItem $inner.FullName -Force | Move-Item -Destination $jdkDir -Force
        Remove-Item -Recurse -Force $inner.FullName
    }
}
Write-Host ("  jdk17       : " + (Test-Path (Join-Path $jdkDir 'bin\java.exe')))

# --- platform-tools (adb) ---
if (-not (Test-Path (Join-Path $sdk 'platform-tools\adb.exe'))) {
    & tar.exe -xf $ptZip -C $sdk
    if ($LASTEXITCODE -ne 0) { throw 'platform-tools extract failed' }
}
Write-Host ("  adb         : " + (Test-Path (Join-Path $sdk 'platform-tools\adb.exe')))

# --- cmdline-tools ---
$ctTarget = Join-Path $sdk 'cmdline-tools\latest'
if (-not (Test-Path (Join-Path $ctTarget 'bin\sdkmanager.bat'))) {
    $tmp = Join-Path $tools '_ct_tmp'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar.exe -xf $ctZip -C $tmp
    if ($LASTEXITCODE -ne 0) { throw 'cmdline-tools extract failed' }
    New-Item -ItemType Directory -Force -Path (Split-Path $ctTarget -Parent) | Out-Null
    if (Test-Path $ctTarget) { Remove-Item -Recurse -Force $ctTarget }
    Move-Item (Join-Path $tmp 'cmdline-tools') $ctTarget
    Remove-Item -Recurse -Force $tmp
}
Write-Host ("  sdkmanager  : " + (Test-Path (Join-Path $ctTarget 'bin\sdkmanager.bat')))

Write-Host ''
Write-Host 'STAGE1_DONE'