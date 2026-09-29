@echo off
setlocal enabledelayedexpansion
set ROOT=d:\pro\RGCalendar
set TOOLS=%ROOT%\.tools
set SDK=%TOOLS%\android-sdk
set BT=%SDK%\build-tools\34.0.0
set JDK=%TOOLS%\jdk17
set AJAR=%SDK%\platforms\android-34\android.jar
set PROJ=%ROOT%\app
set WORK=%PROJ%\.build
set JAVA_HOME=%JDK%
set PATH=%JDK%\bin;%PATH%
set RG_VERIFY_WORK=%WORK%

if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\compiled" 2>nul
mkdir "%WORK%\gen" 2>nul
mkdir "%WORK%\classes" 2>nul
mkdir "%WORK%\dex" 2>nul

echo [1/7] aapt2 compile
"%BT%\aapt2.exe" compile --dir "%PROJ%\res" -o "%WORK%\compiled\res.zip"
if errorlevel 1 goto fail

echo [2/7] aapt2 link
"%BT%\aapt2.exe" link -o "%WORK%\base.apk" -I "%AJAR%" --manifest "%PROJ%\AndroidManifest.xml" -R "%WORK%\compiled\res.zip" --java "%WORK%\gen" --min-sdk-version 24 --target-sdk-version 33 --auto-add-overlay
if errorlevel 1 goto fail

echo [3/7] javac
if exist "%WORK%\sources.txt" del "%WORK%\sources.txt"
for /f "delims=" %%f in ('dir /b /s "%PROJ%\src\*.java"') do call :addsrc "%%f"
for /f "delims=" %%f in ('dir /b /s "%WORK%\gen\*.java"') do call :addsrc "%%f"
"%JDK%\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -bootclasspath "%AJAR%" -nowarn -d "%WORK%\classes" @"%WORK%\sources.txt"
if errorlevel 1 goto fail

echo [4/7] d8
set CLSLIST=
for /f "delims=" %%f in ('dir /b /s "%WORK%\classes\*.class"') do set CLSLIST=!CLSLIST! "%%f"
call "%BT%\d8.bat" --min-api 24 --lib "%AJAR%" --output "%WORK%\dex" %CLSLIST%
if errorlevel 1 goto fail
if not exist "%WORK%\dex\classes.dex" goto fail

echo [5/7] assemble apk
set WINTAR=C:\Windows\System32\tar.exe
if exist "%WORK%\apkroot" rmdir /s /q "%WORK%\apkroot"
mkdir "%WORK%\apkroot" 2>nul
"%WINTAR%" -xf "%WORK%\base.apk" -C "%WORK%\apkroot"
if errorlevel 1 goto fail
copy /y "%WORK%\dex\classes.dex" "%WORK%\apkroot\classes.dex" >nul
if exist "%WORK%\unsigned.apk" del "%WORK%\unsigned.apk"
set JARARGS=-C "%WORK%\apkroot" AndroidManifest.xml -C "%WORK%\apkroot" resources.arsc -C "%WORK%\apkroot" classes.dex
if exist "%WORK%\apkroot\res" set JARARGS=-C "%WORK%\apkroot" res %JARARGS%
"%JDK%\bin\jar.exe" -c -0 -M -f "%WORK%\unsigned.apk" %JARARGS%
if errorlevel 1 goto fail
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression (Get-Content -LiteralPath '%TOOLS%\verify-apk.ps1' -Raw)"
if errorlevel 1 goto fail

echo [6/7] zipalign
"%BT%\zipalign.exe" -f -p 4 "%WORK%\unsigned.apk" "%WORK%\aligned.apk"
if errorlevel 1 goto fail

echo [7/7] apksigner
if not exist "%TOOLS%\debug.keystore" "%JDK%\bin\keytool.exe" -genkeypair -keystore "%TOOLS%\debug.keystore" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
if errorlevel 1 goto fail
if exist "%PROJ%\RGCalendar.apk" del "%PROJ%\RGCalendar.apk"
call "%BT%\apksigner.bat" sign --ks "%TOOLS%\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "%PROJ%\RGCalendar.apk" "%WORK%\aligned.apk"
if errorlevel 1 goto fail
call "%BT%\apksigner.bat" verify "%PROJ%\RGCalendar.apk"
if errorlevel 1 goto fail

echo.
echo APK  = %PROJ%\RGCalendar.apk
echo BUILD_OK
exit /b 0

:addsrc
set "SF=%~1"
set "SF=%SF:\=/%"
echo "%SF%" >> "%WORK%\sources.txt"
exit /b 0

:fail
echo.
echo BUILD_FAILED (errorlevel=%errorlevel%)
exit /b 1