@echo off
echo ===================================================
echo Building BioScan Kiosk Debug APK...
echo ===================================================
cd ..
call gradle assembleDebug
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Debug APK built successfully at app\build\outputs\apk\debug\app-debug.apk
) else (
    echo.
    echo Build failed with error code %ERRORLEVEL%
)
pause
