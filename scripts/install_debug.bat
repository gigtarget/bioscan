@echo off
echo ===================================================
echo Installing BioScan Kiosk Debug APK via ADB...
echo ===================================================
adb install -r ..\app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Installed successfully. Starting BioScan Kiosk...
    adb shell am start -n com.aistudio.bioscankiosk.app/com.example.MainActivity
) else (
    echo.
    echo Installation failed.
)
pause
