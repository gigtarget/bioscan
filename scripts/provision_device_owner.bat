@echo off
echo ===================================================
echo Provisioning BioScan Kiosk as Device Owner (Device Policy Controller)
echo ===================================================
echo WARNING: This command requires a newly factory-reset device or device without secondary accounts.
echo.
adb shell dpm set-device-owner com.aistudio.bioscankiosk.app/com.example.bioscan.core.kiosk.BioScanDeviceAdminReceiver
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Device Owner successfully provisioned! LockTask Kiosk Mode is now authorized.
) else (
    echo.
    echo Provisioning failed. Make sure no Google accounts are logged in on the device.
)
pause
