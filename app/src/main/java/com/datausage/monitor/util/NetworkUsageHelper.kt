package com.datausage.monitor.util

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
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

/**
 * Separates external (internet) traffic from internal (localhost/LAN) traffic.
 *
 * - external = traffic through WiFi + Mobile interfaces (internet-bound)
 * - internal = total TrafficStats minus external (loopback, localhost, Termux, LAN servers)
 */
data class SplitUsage(
    val external: UsageBytes,
    val internal: UsageBytes
) {
    val totalExternal: Long get() = external.totalBytes
    val totalInternal: Long get() = internal.totalBytes
}

@Singleton
class NetworkUsageHelper @Inject constructor(
    private val context: Context
) {
    private val networkStatsManager: NetworkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    /**
     * Get ONLY external (internet) usage for an app via NetworkStatsManager.
     * This counts WiFi + Mobile traffic only — excludes loopback/internal.
     */
    fun getExternalUsageSince(
        uid: Int,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): UsageBytes {
        var rxBytes = 0L
        var txBytes = 0L

        // WiFi (external)
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
        } catch (_: Exception) {}

        // Mobile (external)
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
        } catch (_: Exception) {}

        return UsageBytes(rxBytes, txBytes)
    }

    /**
     * Get total usage (all interfaces including loopback) from TrafficStats.
     * This is cumulative since device boot, so we store a baseline at session start.
     */
    fun getTotalTrafficStatsUsage(uid: Int): UsageBytes {
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        return UsageBytes(
            rxBytes = if (rx == TrafficStats.UNSUPPORTED.toLong()) 0L else rx,
            txBytes = if (tx == TrafficStats.UNSUPPORTED.toLong()) 0L else tx
        )
    }

    /**
     * Get split usage for all monitored apps.
     * Returns external (internet only) and internal (loopback/local) separately.
     *
     * External = NetworkStatsManager (WiFi + Mobile)
     * Internal = TrafficStats total - External (clamped to >= 0)
     */
    fun getAllAppsSplitUsage(
        uids: List<Int>,
        startTime: Long,
        baselineTraffic: Map<Int, UsageBytes>,
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, SplitUsage> {
        val uidSet = uids.toSet()
        val externalMap = mutableMapOf<Int, UsageBytes>()

        // Query WiFi (external)
        try {
            val wifiStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI, null, startTime, endTime
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                if (bucket.uid in uidSet) {
                    val existing = externalMap[bucket.uid] ?: UsageBytes(0, 0)
                    externalMap[bucket.uid] = UsageBytes(
                        existing.rxBytes + bucket.rxBytes,
                        existing.txBytes + bucket.txBytes
                    )
                }
            }
            wifiStats.close()
        } catch (_: Exception) {}

        // Query Mobile (external)
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
                    val existing = externalMap[bucket.uid] ?: UsageBytes(0, 0)
                    externalMap[bucket.uid] = UsageBytes(
                        existing.rxBytes + bucket.rxBytes,
                        existing.txBytes + bucket.txBytes
                    )
                }
            }
            mobileStats.close()
        } catch (_: Exception) {}

        // Calculate internal = (current TrafficStats - baseline) - external
        val result = mutableMapOf<Int, SplitUsage>()
        for (uid in uids) {
            val external = externalMap[uid] ?: UsageBytes(0, 0)

            val currentTotal = getTotalTrafficStatsUsage(uid)
            val baseline = baselineTraffic[uid] ?: UsageBytes(0, 0)

            val totalSinceStart = UsageBytes(
                rxBytes = maxOf(0, currentTotal.rxBytes - baseline.rxBytes),
                txBytes = maxOf(0, currentTotal.txBytes - baseline.txBytes)
            )

            // Internal = total - external (clamped to 0)
            val internal = UsageBytes(
                rxBytes = maxOf(0, totalSinceStart.rxBytes - external.rxBytes),
                txBytes = maxOf(0, totalSinceStart.txBytes - external.txBytes)
            )

            result[uid] = SplitUsage(external = external, internal = internal)
        }

        return result
    }

    /**
     * Capture TrafficStats baseline for all UIDs at session start.
     * Store this to calculate internal traffic later.
     */
    fun captureBaseline(uids: List<Int>): Map<Int, UsageBytes> {
        return uids.associateWith { uid -> getTotalTrafficStatsUsage(uid) }
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
