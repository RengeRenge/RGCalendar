@echo off
set SDK=%ANDROID_SDK_ROOT%
if not exist "%SDK%\build-tools" set SDK=D:\Android\sdk
set BT=%SDK%\build-tools\36.0.0
set APK=d:\pro\RGCalendar\probe\CalendarProbe.apk
"%BT%\aapt2.exe" dump badging "%APK%"
echo ---- signature ----
call "%BT%\apksigner.bat" verify --print-certs "%APK%"