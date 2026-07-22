@echo off
echo ===================================================
echo Installing BioScan Kiosk Release APK via ADB...
echo ===================================================
adb install -r ..\app\build\outputs\apk\release\app-release.apk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Installed successfully. Starting BioScan Kiosk...
    adb shell am start -n com.aistudio.bioscankiosk.app/com.example.MainActivity
) else (
    echo.
    echo Installation failed.
)
pause
