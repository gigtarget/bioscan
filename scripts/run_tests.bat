@echo off
echo ===================================================
echo Running BioScan Kiosk Unit & Robolectric Tests...
echo ===================================================
cd ..
call gradle testDebugUnitTest
if %ERRORLEVEL% EQU 0 (
    echo.
    echo All tests passed successfully!
) else (
    echo.
    echo Tests failed with error code %ERRORLEVEL%
)
pause
