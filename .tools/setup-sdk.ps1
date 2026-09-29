# 安装 platform(android.jar) 与 build-tools
$ErrorActionPreference = 'Stop'
$root  = 'd:\pro\RGCalendar'
$tools = Join-Path $root '.tools'
$sdk   = Join-Path $tools 'android-sdk'

$env:JAVA_HOME        = Join-Path $tools 'jdk17'
$env:ANDROID_USER_HOME = Join-Path $tools '.android'
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = (Join-Path $tools 'jdk17\bin') + ';' + $env:PATH
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null

# 预置许可哈希，使 sdkmanager 免交互
$licDir = Join-Path $sdk 'licenses'
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
Set-Content -LiteralPath (Join-Path $licDir 'android-sdk-license') -Encoding ASCII -Value @(
    '8933bad161af4178b1185d1a37fbf41ea5269c55',
    'd56f5187479451eabf01fb78af6dfcb131a6481e',
    '24333f8a63b6825ea9c5514f83c2829b004d1fee'
)
Set-Content -LiteralPath (Join-Path $licDir 'android-sdk-preview-license') -Encoding ASCII -Value '84831b9409646a918e30573bab4c9c91346d8abd'

$sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
Write-Host "sdkmanager: $sdkmanager"
& $sdkmanager --sdk_root=$sdk 'platforms;android-34' 'build-tools;34.0.0'
Write-Host ("sdkmanager exit = " + $LASTEXITCODE)
Write-Host ''
Write-Host ("android.jar : " + (Test-Path (Join-Path $sdk 'platforms\android-34\android.jar')))
$bt = Join-Path $sdk 'build-tools\34.0.0'
Write-Host ("aapt2       : " + (Test-Path (Join-Path $bt 'aapt2.exe')))
Write-Host ("d8          : " + (Test-Path (Join-Path $bt 'd8.bat')))
Write-Host ("zipalign    : " + (Test-Path (Join-Path $bt 'zipalign.exe')))
Write-Host ("apksigner   : " + (Test-Path (Join-Path $bt 'apksigner.bat')))
Write-Host 'STAGE2_DONE'