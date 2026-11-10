package com.bxiidn.esimmanager.net

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DailyUsage(
    val label: String,
    val dateMillis: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

data class BootUsage(
    val totalBytes: Long,
    val mobileBytes: Long,
) {
    val wifiAndOtherBytes: Long get() = (totalBytes - mobileBytes).coerceAtLeast(0)
}

/** Penggunaan data perangkat dari NetworkStatsManager (per hari) dan TrafficStats (sejak boot). */
class DataUsageReader(context: Context) {
    private val context = context.applicationContext

    /** True bila pengguna sudah memberi "Akses penggunaan" di Setelan. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Penggunaan per hari untuk [days] hari terakhir (hari ini paling akhir). */
    @Suppress("DEPRECATION")
    fun daily(days: Int): List<DailyUsage> {
        if (!hasUsageAccess()) return emptyList()
        val stats = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val formatter = DateTimeFormatter.ofPattern("d/M", Locale.US)
        val result = ArrayList<DailyUsage>()
        for (offset in (days - 1) downTo 0) {
            val day = today.minusDays(offset.toLong())
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val wifi = runCatching {
                val bucket = stats.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, start, end)
                bucket.rxBytes + bucket.txBytes
            }.getOrDefault(0L)
            val mobile = runCatching {
                val bucket = stats.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, end)
                bucket.rxBytes + bucket.txBytes
            }.getOrDefault(0L)
            result += DailyUsage(day.format(formatter), start, wifi.coerceAtLeast(0), mobile.coerceAtLeast(0))
        }
        return result
    }

    /** Total trafik sejak perangkat menyala; tidak memerlukan izin apa pun. */
    fun sinceBoot(): BootUsage {
        fun safe(value: Long): Long = if (value == TrafficStats.UNSUPPORTED.toLong() || value < 0) 0 else value
        val total = safe(TrafficStats.getTotalRxBytes()) + safe(TrafficStats.getTotalTxBytes())
        val mobile = safe(TrafficStats.getMobileRxBytes()) + safe(TrafficStats.getMobileTxBytes())
        return BootUsage(total, mobile)
    }
}
