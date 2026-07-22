@echo off
echo ===================================================
echo Safe In-Place Update (Preserving User & Attendance Data)
echo ===================================================
adb install -r ..\app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Application updated safely without clearing local database or biometric templates.
) else (
    echo.
    echo Update failed.
)
pause
