package com.example.bioscan.core.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.bioscan.core.common.KioskModeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KioskController(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, BioScanDeviceAdminReceiver::class.java)

    private val _kioskState = MutableStateFlow(KioskModeState.INACTIVE)
    val kioskState: StateFlow<KioskModeState> = _kioskState.asStateFlow()

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun enableKioskMode(activity: Activity) {
        try {
            if (isDeviceOwner()) {
                val packages = arrayOf(context.packageName)
                devicePolicyManager.setLockTaskPackages(adminComponent, packages)
                _kioskState.value = KioskModeState.ACTIVE
            }

            // Start LockTask mode
            activity.startLockTask()
            hideSystemUi(activity)
            _kioskState.value = KioskModeState.LOCKED
        } catch (e: Exception) {
            e.printStackTrace()
            _kioskState.value = KioskModeState.INACTIVE
        }
    }

    fun disableKioskMode(activity: Activity) {
        try {
            activity.stopLockTask()
            showSystemUi(activity)
            _kioskState.value = KioskModeState.INACTIVE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideSystemUi(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun showSystemUi(activity: Activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
