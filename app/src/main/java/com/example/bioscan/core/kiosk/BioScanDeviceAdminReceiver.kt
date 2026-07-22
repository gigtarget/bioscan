package com.example.bioscan.core.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class BioScanDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "BioScan Kiosk Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "BioScan Kiosk Device Admin Disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Toast.makeText(context, "BioScan Entered Kiosk Mode", Toast.LENGTH_SHORT).show()
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Toast.makeText(context, "BioScan Exited Kiosk Mode", Toast.LENGTH_SHORT).show()
    }
}
