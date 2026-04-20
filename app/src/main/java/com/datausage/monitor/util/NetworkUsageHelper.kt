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
     * ROOT CAUSE OF UNDER-COUNTING:
     * NetworkStatsManager writes data in "buckets" (time windows).
     * Querying from startTime→now MISSES data in the current open bucket
     * because it hasn't been finalized yet (can take up to 2 hours!).
     *
     * THE FIX (same approach as Android Settings > Data Usage):
     * Query from epoch 0 → now (cumulative total) and subtract a baseline
     * captured at session start using NetworkStatsManager (not TrafficStats).
     * This way we always include the current open bucket.
     *
     * Fallback: TrafficStats delta for UIDs where NetworkStatsManager returns 0.
     */
    fun getAllAppsSplitUsage(
        uids: List<Int>,
        startTime: Long,
        baselineTraffic: Map<Int, UsageBytes>,
        nsBaseline: Map<Int, SplitUsage>,
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, SplitUsage> {
        val hasPermission = hasUsageStatsPermission()
        val result = mutableMapOf<Int, SplitUsage>()

        // Query cumulative totals from epoch 0 → now (avoids open-bucket problem)
        var wifiCumulative = emptyMap<Int, UsageBytes>()
        var mobileCumulative = emptyMap<Int, UsageBytes>()

        if (hasPermission) {
            val uidSet = uids.toSet()
            wifiCumulative = queryBulkUsage(ConnectivityManager.TYPE_WIFI, buildWifiSubscriberIds(), uidSet, 0L, endTime)
            mobileCumulative = queryBulkUsage(ConnectivityManager.TYPE_MOBILE, buildMobileSubscriberIds(), uidSet, 0L, endTime)
        }

        for (uid in uids) {
            // NetworkStatsManager delta = cumulative_now - cumulative_at_session_start
            val nsBase = nsBaseline[uid] ?: SplitUsage(UsageBytes(0, 0), UsageBytes(0, 0))
            val wifiNow = wifiCumulative[uid] ?: UsageBytes(0, 0)
            val mobileNow = mobileCumulative[uid] ?: UsageBytes(0, 0)

            var nsWifi = UsageBytes(
                rxBytes = maxOf(0L, wifiNow.rxBytes - nsBase.wifi.rxBytes),
                txBytes = maxOf(0L, wifiNow.txBytes - nsBase.wifi.txBytes)
            )
            var nsMobile = UsageBytes(
                rxBytes = maxOf(0L, mobileNow.rxBytes - nsBase.mobile.rxBytes),
                txBytes = maxOf(0L, mobileNow.txBytes - nsBase.mobile.txBytes)
            )

            // Per-UID fallback if bulk query returned 0
            if (hasPermission && nsWifi.totalBytes == 0L && nsMobile.totalBytes == 0L) {
                val wifiPerUid = queryPerUidUsage(ConnectivityManager.TYPE_WIFI, buildWifiSubscriberIds(), uid, 0L, endTime)
                val mobilePerUid = queryPerUidUsage(ConnectivityManager.TYPE_MOBILE, buildMobileSubscriberIds(), uid, 0L, endTime)
                nsWifi = UsageBytes(
                    rxBytes = maxOf(0L, wifiPerUid.rxBytes - nsBase.wifi.rxBytes),
                    txBytes = maxOf(0L, wifiPerUid.txBytes - nsBase.wifi.txBytes)
                )
                nsMobile = UsageBytes(
                    rxBytes = maxOf(0L, mobilePerUid.rxBytes - nsBase.mobile.rxBytes),
                    txBytes = maxOf(0L, mobilePerUid.txBytes - nsBase.mobile.txBytes)
                )
            }

            val nsTotal = nsWifi.totalBytes + nsMobile.totalBytes

            // TrafficStats delta for fallback (real-time but no WiFi/Mobile split)
            val currentTs = getTrafficStatsUsage(uid)
            val tsBase = baselineTraffic[uid] ?: UsageBytes(0, 0)
            val tsDelta = UsageBytes(
                rxBytes = maxOf(0L, currentTs.rxBytes - tsBase.rxBytes),
                txBytes = maxOf(0L, currentTs.txBytes - tsBase.txBytes)
            )
            val tsTotal = tsDelta.totalBytes

            val wifi: UsageBytes
            val mobile: UsageBytes

            when {
                nsTotal > 0 -> {
                    // NetworkStatsManager has data — use it (accurate WiFi/Mobile split)
                    wifi = nsWifi
                    mobile = nsMobile
                    Log.d(TAG, "uid=$uid NS: wifi=${nsWifi.totalBytes}B mobile=${nsMobile.totalBytes}B")
                }
                tsTotal > 0 -> {
                    // Fallback: TrafficStats delta — can't split, report all as WiFi
                    wifi = tsDelta
                    mobile = UsageBytes(0, 0)
                    Log.d(TAG, "uid=$uid TS fallback: ${tsTotal}B (NS=0)")
                }
                else -> {
                    wifi = UsageBytes(0, 0)
                    mobile = UsageBytes(0, 0)
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

    /**
     * Capture NetworkStatsManager baseline at session start.
     * Queries epoch 0 → now so future polls can subtract this to get session delta.
     */
    fun captureNsBaseline(uids: List<Int>): Map<Int, SplitUsage> {
        if (!hasUsageStatsPermission()) return emptyMap()
        val now = System.currentTimeMillis()
        val uidSet = uids.toSet()
        val wifiCumulative = queryBulkUsage(ConnectivityManager.TYPE_WIFI, buildWifiSubscriberIds(), uidSet, 0L, now)
        val mobileCumulative = queryBulkUsage(ConnectivityManager.TYPE_MOBILE, buildMobileSubscriberIds(), uidSet, 0L, now)
        val baseline = uids.associateWith { uid ->
            SplitUsage(
                wifi = wifiCumulative[uid] ?: UsageBytes(0, 0),
                mobile = mobileCumulative[uid] ?: UsageBytes(0, 0)
            )
        }
        Log.d(TAG, "NS baseline captured for ${uids.size} UIDs")
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
