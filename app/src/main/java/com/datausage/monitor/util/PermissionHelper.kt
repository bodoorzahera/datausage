package com.datausage.monitor.util

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class PermissionStep {
    USAGE_STATS,
    POST_NOTIFICATIONS,
    READ_PHONE_STATE,
    BATTERY_OPTIMIZATION
}

object PermissionHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasReadPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun getNextRequiredPermission(context: Context): PermissionStep? {
        if (!hasUsageStatsPermission(context)) return PermissionStep.USAGE_STATS
        if (!hasNotificationPermission(context)) return PermissionStep.POST_NOTIFICATIONS
        if (!hasReadPhoneStatePermission(context)) return PermissionStep.READ_PHONE_STATE
        if (!isBatteryOptimizationExempt(context)) return PermissionStep.BATTERY_OPTIMIZATION
        return null
    }

    fun allPermissionsGranted(context: Context): Boolean {
        return getNextRequiredPermission(context) == null
    }
}
