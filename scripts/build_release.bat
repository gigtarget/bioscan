@echo off
echo ===================================================
echo Building BioScan Kiosk Signed Release APK...
echo ===================================================
cd ..
call gradle assembleRelease
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Release APK built successfully at app\build\outputs\apk\release\app-release.apk
) else (
    echo.
    echo Build failed with error code %ERRORLEVEL%
)
pause
