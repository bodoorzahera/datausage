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
}

/**
 * Separates external (internet) traffic from internal (localhost/LAN) traffic.
 *
 * Primary source: TrafficStats (reliable, real-time, per-UID)
 * Split source:   NetworkStatsManager (WiFi + Mobile breakdown)
 *
 * Fallback: if NetworkStatsManager returns 0 but TrafficStats shows traffic,
 *           all traffic is treated as external (internet) to avoid showing 0.
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
     * Check if PACKAGE_USAGE_STATS permission is granted.
     * Without this, NetworkStatsManager queries will fail silently.
     */
    fun hasUsageStatsPermission(): Boolean =
        PermissionHelper.hasUsageStatsPermission(context)

    /**
     * Get ONLY external (internet) usage for a single UID.
     * Uses queryDetailsForUid for precise per-UID query.
     * Falls back to TrafficStats if NetworkStatsManager returns nothing.
     */
    fun getExternalUsageSince(
        uid: Int,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): UsageBytes {
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted — NetworkStatsManager unavailable")
        }
        val wifi = queryNetworkUsageForUid(ConnectivityManager.TYPE_WIFI, listOf(null), uid, startTime, endTime)
        val mobile = getMobileUsageForUid(uid, startTime, endTime)
        return UsageBytes(wifi.rxBytes + mobile.rxBytes, wifi.txBytes + mobile.txBytes)
    }

    /**
     * Get total usage (all interfaces including loopback) from TrafficStats.
     * Cumulative since device boot — caller must subtract a baseline captured at session start.
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
     * Get split usage for all monitored UIDs in one pass.
     *
     * Strategy (TrafficStats-primary with NetworkStatsManager split):
     *  1. Query TrafficStats delta since baseline → total traffic (all interfaces)
     *  2. Query NetworkStatsManager (WiFi + Mobile) → network-interface traffic
     *  3. If NetworkStatsManager returned data:
     *       external = NetworkStatsManager result
     *       internal = TrafficStats total − external (clamped ≥ 0)
     *  4. If NetworkStatsManager returned 0 but TrafficStats shows traffic:
     *       external = TrafficStats total (fallback — treat all as internet)
     *       internal = 0
     *     This prevents the bug where failed NetworkStatsManager queries cause
     *     all traffic to be misclassified as "internal/local".
     */
    fun getAllAppsSplitUsage(
        uids: List<Int>,
        startTime: Long,
        baselineTraffic: Map<Int, UsageBytes>,
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, SplitUsage> {
        val result = mutableMapOf<Int, SplitUsage>()
        val hasPermission = hasUsageStatsPermission()
        if (!hasPermission) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted — falling back to TrafficStats only")
        }

        for (uid in uids) {
            // Step 1: TrafficStats delta since baseline (primary source, always available)
            val currentTotal = getTotalTrafficStatsUsage(uid)
            val baseline = baselineTraffic[uid] ?: UsageBytes(0, 0)
            val totalSinceStart = UsageBytes(
                rxBytes = maxOf(0L, currentTotal.rxBytes - baseline.rxBytes),
                txBytes = maxOf(0L, currentTotal.txBytes - baseline.txBytes)
            )

            // Step 2: NetworkStatsManager query (WiFi + Mobile)
            var networkStatsExternal = UsageBytes(0, 0)
            if (hasPermission) {
                val wifi = queryNetworkUsageForUid(
                    ConnectivityManager.TYPE_WIFI, listOf(null), uid, startTime, endTime
                )
                val mobile = getMobileUsageForUid(uid, startTime, endTime)
                networkStatsExternal = UsageBytes(
                    rxBytes = wifi.rxBytes + mobile.rxBytes,
                    txBytes = wifi.txBytes + mobile.txBytes
                )
            }

            // Step 3: Determine split
            val external: UsageBytes
            val internal: UsageBytes

            if (networkStatsExternal.totalBytes > 0) {
                // NetworkStatsManager has data — use it for the split
                external = networkStatsExternal
                internal = UsageBytes(
                    rxBytes = maxOf(0L, totalSinceStart.rxBytes - external.rxBytes),
                    txBytes = maxOf(0L, totalSinceStart.txBytes - external.txBytes)
                )
            } else if (totalSinceStart.totalBytes > 0) {
                // Fallback: NetworkStatsManager returned 0 but TrafficStats shows traffic.
                // Treat ALL traffic as external (internet) to avoid showing 0.
                Log.w(TAG, "NetworkStatsManager returned 0 for uid=$uid but TrafficStats=" +
                    "${totalSinceStart.totalBytes}B — treating all as external (internet)")
                external = totalSinceStart
                internal = UsageBytes(0, 0)
            } else {
                // No traffic at all
                external = UsageBytes(0, 0)
                internal = UsageBytes(0, 0)
            }

            result[uid] = SplitUsage(external = external, internal = internal)
        }

        return result
    }

    /**
     * Capture TrafficStats baseline for all UIDs at session start.
     * Required to calculate per-session internal traffic later.
     */
    fun captureBaseline(uids: List<Int>): Map<Int, UsageBytes> =
        uids.associateWith { uid -> getTotalTrafficStatsUsage(uid) }

    // ─── Private helpers ────────────────────────────────────────────────────

    /**
     * Query mobile data for a UID, trying multiple subscriberId strategies to
     * handle Android Q+ restrictions on IMSI access.
     *
     * Strategy (ordered by preference):
     *  1. null  — Android Q+ recommended (active subscription, no IMSI needed)
     *  2. actual IMSI — pre-Q and some Q+ OEM implementations
     *  3. ""    — some OEMs accept empty string instead of null
     *
     * Returns the result with the highest byte count (most complete data).
     */
    private fun getMobileUsageForUid(uid: Int, startTime: Long, endTime: Long): UsageBytes {
        val subscriberIds = buildMobileSubscriberIds()
        return queryNetworkUsageForUid(
            ConnectivityManager.TYPE_MOBILE, subscriberIds, uid, startTime, endTime
        )
    }

    private fun buildMobileSubscriberIds(): List<String?> {
        val ids = mutableListOf<String?>()

        // Android Q+ (API 29+): null is recommended — system resolves active subscription
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ids.add(null)
        }

        // Try actual IMSI/subscriberId (required pre-Q, works on many Q+ devices too)
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val subId = try { tm.subscriberId } catch (_: SecurityException) { null }
            if (!subId.isNullOrEmpty()) ids.add(subId)
        } catch (_: Exception) {}

        // Pre-Q: null as fallback if no subscriberId retrieved
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ids.add(null)
        }

        // Empty string — accepted on some OEM devices (Xiaomi, Samsung, etc.)
        ids.add("")

        return ids.distinct()
    }

    /**
     * Query NetworkStatsManager for a specific UID using queryDetailsForUid.
     * Tries each subscriberId in order and returns the result with the most bytes.
     */
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
                Log.e(TAG, "SecurityException querying networkType=$networkType uid=$uid — " +
                    "is PACKAGE_USAGE_STATS granted?", e)
                continue
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query networkType=$networkType uid=$uid subId=$subscriberId", e)
                continue
            }

            // Keep whichever subscriberId strategy yields the most data
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
