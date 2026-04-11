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

/**
 * Buffer subtracted from startTime when querying NetworkStatsManager.
 * Android batches stats writes, so very recent data may not appear yet.
 * 3 minutes buffer ensures we catch recently-written data.
 */
private const val QUERY_TIME_BUFFER_MS = 3 * 60 * 1000L

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
     * Get split usage for all monitored UIDs.
     *
     * Strategy (ordered by reliability):
     *  1. queryDetails() — iterates ALL buckets, filter by target UIDs
     *     This works on MORE devices than queryDetailsForUid (known OEM bugs)
     *  2. queryDetailsForUid() — per-UID query (more efficient but buggy on some ROMs)
     *  3. TrafficStats delta — real-time fallback (no WiFi/Mobile split possible)
     *
     * WiFi subscriberIds tried: [null, ""] — some OEMs need empty string
     * Mobile subscriberIds: [null (Q+), actual IMSI, ""]
     * Time buffer: 3 min subtracted from startTime to account for Android's delayed writes
     */
    fun getAllAppsSplitUsage(
        uids: List<Int>,
        startTime: Long,
        baselineTraffic: Map<Int, UsageBytes>,
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, SplitUsage> {
        val hasPermission = hasUsageStatsPermission()
        if (!hasPermission) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted — falling back to TrafficStats only")
        }

        val result = mutableMapOf<Int, SplitUsage>()

        // Apply time buffer to catch recently-written stats
        val bufferedStart = maxOf(0L, startTime - QUERY_TIME_BUFFER_MS)

        // Strategy 1: Batch query using queryDetails (all UIDs at once)
        var wifiByUid = emptyMap<Int, UsageBytes>()
        var mobileByUid = emptyMap<Int, UsageBytes>()

        if (hasPermission) {
            val uidSet = uids.toSet()
            wifiByUid = queryBulkUsage(ConnectivityManager.TYPE_WIFI, buildWifiSubscriberIds(), uidSet, bufferedStart, endTime)
            mobileByUid = queryBulkUsage(ConnectivityManager.TYPE_MOBILE, buildMobileSubscriberIds(), uidSet, bufferedStart, endTime)

            Log.d(TAG, "queryDetails: wifi=${wifiByUid.values.sumOf { it.totalBytes }}B, " +
                "mobile=${mobileByUid.values.sumOf { it.totalBytes }}B for ${uids.size} UIDs")
        }

        // Strategy 2: Per-UID queryDetailsForUid as fallback for UIDs that returned 0
        for (uid in uids) {
            var wifi = wifiByUid[uid] ?: UsageBytes(0, 0)
            var mobile = mobileByUid[uid] ?: UsageBytes(0, 0)

            // If batch query returned 0 for this UID, try per-UID query
            if (hasPermission && wifi.totalBytes == 0L && mobile.totalBytes == 0L) {
                wifi = queryPerUidUsage(ConnectivityManager.TYPE_WIFI, buildWifiSubscriberIds(), uid, bufferedStart, endTime)
                mobile = queryPerUidUsage(ConnectivityManager.TYPE_MOBILE, buildMobileSubscriberIds(), uid, bufferedStart, endTime)
                if (wifi.totalBytes > 0 || mobile.totalBytes > 0) {
                    Log.d(TAG, "queryDetailsForUid fallback worked for uid=$uid: wifi=${wifi.totalBytes}B mobile=${mobile.totalBytes}B")
                }
            }

            // Strategy 3: TrafficStats delta as last resort
            if (wifi.totalBytes == 0L && mobile.totalBytes == 0L) {
                val currentTotal = getTrafficStatsUsage(uid)
                val baseline = baselineTraffic[uid] ?: UsageBytes(0, 0)
                val delta = UsageBytes(
                    rxBytes = maxOf(0L, currentTotal.rxBytes - baseline.rxBytes),
                    txBytes = maxOf(0L, currentTotal.txBytes - baseline.txBytes)
                )
                if (delta.totalBytes > 0) {
                    Log.d(TAG, "TrafficStats fallback for uid=$uid: ${delta.totalBytes}B (current=${currentTotal.totalBytes}, baseline=${baseline.totalBytes})")
                    // Can't distinguish wifi/mobile from TrafficStats, report as wifi
                    wifi = delta
                } else {
                    Log.d(TAG, "All methods returned 0 for uid=$uid (TrafficStats current=${currentTotal.totalBytes}, baseline=${baseline.totalBytes})")
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
    fun captureBaseline(uids: List<Int>): Map<Int, UsageBytes> {
        val baseline = uids.associateWith { uid -> getTrafficStatsUsage(uid) }
        Log.d(TAG, "Baseline captured for ${uids.size} UIDs, total=${baseline.values.sumOf { it.totalBytes }}B")
        return baseline
    }

    // ─── Bulk query (queryDetails — all UIDs in one pass) ───────────────────

    /**
     * Query NetworkStatsManager.queryDetails() which returns ALL buckets for ALL UIDs.
     * Filter results to only include our target UIDs.
     * This is more reliable than queryDetailsForUid on many OEM ROMs.
     */
    private fun queryBulkUsage(
        networkType: Int,
        subscriberIds: List<String?>,
        targetUids: Set<Int>,
        startTime: Long,
        endTime: Long
    ): Map<Int, UsageBytes> {
        val bestResults = mutableMapOf<Int, UsageBytes>()

        for (subscriberId in subscriberIds) {
            val perUid = mutableMapOf<Int, Pair<Long, Long>>() // uid -> (rx, tx)
            try {
                val stats = networkStatsManager.queryDetails(
                    networkType, subscriberId, startTime, endTime
                )
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid in targetUids) {
                        val current = perUid.getOrDefault(uid, Pair(0L, 0L))
                        perUid[uid] = Pair(current.first + bucket.rxBytes, current.second + bucket.txBytes)
                    }
                }
                stats.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException in queryDetails networkType=$networkType — PACKAGE_USAGE_STATS missing?", e)
                continue
            } catch (e: Exception) {
                Log.w(TAG, "queryDetails failed networkType=$networkType subId=$subscriberId: ${e.message}")
                continue
            }

            // Keep best result per UID across subscriber IDs
            for ((uid, pair) in perUid) {
                val existing = bestResults[uid]
                val newTotal = pair.first + pair.second
                if (existing == null || newTotal > existing.totalBytes) {
                    bestResults[uid] = UsageBytes(pair.first, pair.second)
                }
            }
        }

        return bestResults
    }

    // ─── Per-UID query (queryDetailsForUid) ─────────────────────────────────

    private fun queryPerUidUsage(
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
                Log.e(TAG, "SecurityException queryDetailsForUid networkType=$networkType uid=$uid", e)
                continue
            } catch (e: Exception) {
                Log.w(TAG, "queryDetailsForUid failed networkType=$networkType uid=$uid subId=$subscriberId: ${e.message}")
                continue
            }

            if (rx + tx > bestRx + bestTx) {
                bestRx = rx
                bestTx = tx
            }
        }

        return UsageBytes(bestRx, bestTx)
    }

    // ─── Subscriber ID builders ─────────────────────────────────────────────

    /**
     * WiFi subscriber IDs to try.
     * null works on most devices, but some OEMs (Samsung, Xiaomi) need "".
     */
    private fun buildWifiSubscriberIds(): List<String?> = listOf(null, "")

    /**
     * Mobile subscriber IDs with multiple strategies for maximum compatibility.
     */
    private fun buildMobileSubscriberIds(): List<String?> {
        val ids = mutableListOf<String?>()

        // Android Q+ (API 29+): null is recommended
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ids.add(null)
        }

        // Try actual IMSI/subscriberId
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val subId = try { tm.subscriberId } catch (_: SecurityException) { null }
            if (!subId.isNullOrEmpty()) ids.add(subId)
        } catch (_: Exception) {}

        // Pre-Q: null as fallback
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ids.add(null)
        }

        // Empty string — some OEMs
        ids.add("")

        return ids.distinct()
    }

    // ─── App discovery ──────────────────────────────────────────────────────

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
