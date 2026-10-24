package com.nexguard.feature.onboarding

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Yalnızca gerçekten kullanılan izinler. SMS, kişiler, kamera gibi ilgisiz
 * hassas izinler istenmez; en az yetki ilkesi uygulanır.
 */
object PermissionManager {

    val runtimePermissions: List<String>
        get() = buildList {
            // Android 13+ için Wi-Fi taraması NEARBY_WIFI_DEVICES ile yapılır;
            // konum izni yalnızca eski sürümlerde gerekir (en az yetki).
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT == 29) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    /** Wi-Fi tarama sonuçları için gereken çalışma zamanı izni mevcut mu? */
    fun wifiScanPermissionGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            status(context, Manifest.permission.NEARBY_WIFI_DEVICES) ||
                status(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            status(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun missingRuntime(context: Context): List<String> =
        runtimePermissions.filter { !status(context, it) }

    fun status(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun allRuntimeGranted(context: Context): Boolean = missingRuntime(context).isEmpty()

    fun storageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            try { Environment.isExternalStorageManager() } catch (e: Exception) { false }
        } else {
            status(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun notificationsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) status(context, Manifest.permission.POST_NOTIFICATIONS) else true

    fun locationEnabled(context: Context): Boolean = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        lm.isLocationEnabled
    } catch (e: Exception) {
        false
    }

    fun batteryOptimizationIgnored(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        false
    }

    fun deviceAdminActive(context: Context, admin: ComponentName): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(admin)
    } catch (e: Exception) {
        false
    }

    fun accessibilityEnabled(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName } == true
        } catch (e: Exception) {
            false
        }
    }
}
