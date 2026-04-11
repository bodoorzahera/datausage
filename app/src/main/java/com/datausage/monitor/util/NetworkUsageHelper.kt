package com.datausage.monitor.util

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NetworkUsageHelper"

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
    operator fun plus(other: UsageBytes) = UsageBytes(
        rxBytes + other.rxBytes, txBytes + other.txBytes
    )
}

/**
 * Per-UID traffic split by network interface.
 *
 * wifi   = traffic through WiFi (NetworkStatsManager TYPE_WIFI)
 * mobile = traffic through cellular (NetworkStatsManager TYPE_MOBILE)
 * total  = wifi + mobile  (all internet traffic)
 */
data class SplitUsage(
    val wifi: UsageBytes,
    val mobile: UsageBytes
) {
    val total: UsageBytes get() = wifi + mobile
}

@Singleton
class NetworkUsageHelper @Inject constructor(
    private val context: Context
) {
    private val networkStatsManager: NetworkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun hasUsageStatsPermission(): Boolean =
        PermissionHelper.hasUsageStatsPermission(context)

    /**
     * Get WiFi + Mobile usage separately for a single UID via NetworkStatsManager.
     * If NetworkStatsManager fails, falls back to TrafficStats (reported as wifi).
     */
    fun getUsageSince(
        uid: Int,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): SplitUsage {
        val hasPermission = hasUsageStatsPermission()
        if (!hasPermission) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted for uid=$uid — using TrafficStats fallback")
        }

        var wifi = UsageBytes(0, 0)
        var mobile = UsageBytes(0, 0)

        if (hasPermission) {
            wifi = queryNetworkUsageForUid(
                ConnectivityManager.TYPE_WIFI, listOf(null), uid, startTime, endTime
            )
            mobile = getMobileUsageForUid(uid, startTime, endTime)
        }

        // Fallback: if NetworkStatsManager returned nothing, use TrafficStats
        if (wifi.totalBytes == 0L && mobile.totalBytes == 0L) {
            val ts = getTrafficStatsUsage(uid)
            if (ts.totalBytes > 0) {
                Log.w(TAG, "NetworkStatsManager=0 for uid=$uid, TrafficStats=${ts.totalBytes}B — using TrafficStats fallback")
                // Report TrafficStats as wifi since we can't distinguish
                wifi = ts
            }
        }

        return SplitUsage(wifi = wifi, mobile = mobile)
    }

    /**
     * Get split usage for all monitored UIDs.
     * Uses NetworkStatsManager with TrafficStats fallback per-UID.
     */
    fun getAllAppsSplitUsage(
        uids: List<Int>,
        startTime: Long,
        baselineTraffic: Map<Int, UsageBytes>,
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, SplitUsage> {
        val hasPermission = hasUsageStatsPermission()
        if (!hasPermission) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted — using TrafficStats fallback for all UIDs")
        }

        val result = mutableMapOf<Int, SplitUsage>()

        for (uid in uids) {
            var wifi = UsageBytes(0, 0)
            var mobile = UsageBytes(0, 0)

            if (hasPermission) {
                wifi = queryNetworkUsageForUid(
                    ConnectivityManager.TYPE_WIFI, listOf(null), uid, startTime, endTime
                )
                mobile = getMobileUsageForUid(uid, startTime, endTime)
            }

            // Fallback: if NetworkStatsManager returned nothing, use TrafficStats delta
            if (wifi.totalBytes == 0L && mobile.totalBytes == 0L) {
                val currentTotal = getTrafficStatsUsage(uid)
                val baseline = baselineTraffic[uid] ?: UsageBytes(0, 0)
                val delta = UsageBytes(
                    rxBytes = maxOf(0L, currentTotal.rxBytes - baseline.rxBytes),
                    txBytes = maxOf(0L, currentTotal.txBytes - baseline.txBytes)
                )
                if (delta.totalBytes > 0) {
                    Log.w(TAG, "NetworkStatsManager=0 for uid=$uid, TrafficStats delta=${delta.totalBytes}B — fallback")
                    wifi = delta
                }
            }

            result[uid] = SplitUsage(wifi = wifi, mobile = mobile)
        }

        return result
    }

    /**
     * Get total usage from TrafficStats (all interfaces, cumulative since boot).
     */
    fun getTrafficStatsUsage(uid: Int): UsageBytes {
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        return UsageBytes(
            rxBytes = if (rx == TrafficStats.UNSUPPORTED.toLong()) 0L else rx,
            txBytes = if (tx == TrafficStats.UNSUPPORTED.toLong()) 0L else tx
        )
    }

    /**
     * Capture TrafficStats baseline for all UIDs at session start.
     */
    fun captureBaseline(uids: List<Int>): Map<Int, UsageBytes> =
        uids.associateWith { uid -> getTrafficStatsUsage(uid) }

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun getMobileUsageForUid(uid: Int, startTime: Long, endTime: Long): UsageBytes {
        val subscriberIds = buildMobileSubscriberIds()
        return queryNetworkUsageForUid(
            ConnectivityManager.TYPE_MOBILE, subscriberIds, uid, startTime, endTime
        )
    }

    private fun buildMobileSubscriberIds(): List<String?> {
        val ids = mutableListOf<String?>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ids.add(null)
        }

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val subId = try { tm.subscriberId } catch (_: SecurityException) { null }
            if (!subId.isNullOrEmpty()) ids.add(subId)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ids.add(null)
        }

        ids.add("")

        return ids.distinct()
    }

    private fun queryNetworkUsageForUid(
        networkType: Int,
        subscriberIds: List<String?>,
        uid: Int,
        startTime: Long,
        endTime: Long
    ): UsageBytes {
        var bestRx = 0L
        var bestTx = 0L

        for (subscriberId in subscriberIds) {
            var rx = 0L
            var tx = 0L
            try {
                val stats = networkStatsManager.queryDetailsForUid(
                    networkType, subscriberId, startTime, endTime, uid
                )
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    rx += bucket.rxBytes
                    tx += bucket.txBytes
                }
                stats.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: networkType=$networkType uid=$uid — PACKAGE_USAGE_STATS missing?", e)
                continue
            } catch (e: Exception) {
                Log.w(TAG, "Query failed: networkType=$networkType uid=$uid subId=$subscriberId", e)
                continue
            }

            if (rx + tx > bestRx + bestTx) {
                bestRx = rx
                bestTx = tx
            }
        }

        return UsageBytes(bestRx, bestTx)
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
