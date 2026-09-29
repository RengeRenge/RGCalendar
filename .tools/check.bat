@echo off
set BT=d:\pro\RGCalendar\.tools\android-sdk\build-tools\34.0.0
set APK=d:\pro\RGCalendar\probe\CalendarProbe.apk
"%BT%\aapt2.exe" dump badging "%APK%"
echo ---- signature ----
call "%BT%\apksigner.bat" verify --print-certs "%APK%"