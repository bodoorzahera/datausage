package com.datausage.monitor.util

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import javax.inject.Inject
import javax.inject.Singleton

data class AppInfo(
    val packageName: String,
    val appName: String,
    val uid: Int
)

data class UsageBytes(
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

@Singleton
class NetworkUsageHelper @Inject constructor(
    private val context: Context
) {
    private val networkStatsManager: NetworkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun getAppUsageSince(
        uid: Int,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): UsageBytes {
        var rxBytes = 0L
        var txBytes = 0L

        // Query WiFi
        try {
            val wifiStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI, null, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                if (bucket.uid == uid) {
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
            }
            wifiStats.close()
        } catch (_: Exception) {
            // WiFi stats may not be available
        }

        // Query Mobile
        try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val subscriberId = try {
                telephonyManager.subscriberId
            } catch (_: SecurityException) {
                null
            }
            val mobileStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE, subscriberId, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                if (bucket.uid == uid) {
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
            }
            mobileStats.close()
        } catch (_: Exception) {
            // Mobile stats may not be available (no SIM, no permission)
        }

        return UsageBytes(rxBytes, txBytes)
    }

    fun getAllAppsUsageSince(
        uids: List<Int>,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, UsageBytes> {
        val result = mutableMapOf<Int, UsageBytes>()
        val uidSet = uids.toSet()

        // Query WiFi
        try {
            val wifiStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI, null, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                if (bucket.uid in uidSet) {
                    val existing = result[bucket.uid] ?: UsageBytes(0, 0)
                    result[bucket.uid] = UsageBytes(
                        existing.rxBytes + bucket.rxBytes,
                        existing.txBytes + bucket.txBytes
                    )
                }
            }
            wifiStats.close()
        } catch (_: Exception) {}

        // Query Mobile
        try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val subscriberId = try {
                telephonyManager.subscriberId
            } catch (_: SecurityException) {
                null
            }
            val mobileStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE, subscriberId, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                if (bucket.uid in uidSet) {
                    val existing = result[bucket.uid] ?: UsageBytes(0, 0)
                    result[bucket.uid] = UsageBytes(
                        existing.rxBytes + bucket.rxBytes,
                        existing.txBytes + bucket.txBytes
                    )
                }
            }
            mobileStats.close()
        } catch (_: Exception) {}

        return result
    }

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || hasInternetPermission(it.packageName) }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    uid = appInfo.uid
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    private fun hasInternetPermission(packageName: String): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                packageName, PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.contains("android.permission.INTERNET") == true
        } catch (_: Exception) {
            false
        }
    }
}
