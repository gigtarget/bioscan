@echo off
echo ===================================================
echo Exporting BioScan Kiosk Logcat Logs...
echo ===================================================
adb logcat -d -s "BioScanKiosk:V" "CameraManager:V" "RecognitionCoordinator:V" > bioscan_kiosk_logs.txt
echo Logs saved to bioscan_kiosk_logs.txt
pause
