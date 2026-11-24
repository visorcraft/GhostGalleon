package com.visorcraft.ghostgalleon.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File

/**
 * Android seams → [SystemReadings]. Thread-safe for off-UI `PERF_HUD` and About screen
 * collection; formatting and throttle/battery logic stay pure in [SystemInfoFormat].
 */
object SystemInfoCollector {

    private data class StorageSnapshot(
        val internalTotalBytes: Long,
        val internalFreeBytes: Long,
        val secondary: Triple<String, Long, Long>?,
    )

    // Static hardware facts change only across reboots — cache for PERF_HUD ticks.
    @Volatile
    private var cachedCpuMaxMhz: Int? = null
    @Volatile
    private var cpuMaxResolved: Boolean = false
    @Volatile
    private var cachedRamTotal: Long = -1L
    private val storageCache = ThrottledStorageCache<StorageSnapshot>(
        ttlMs = SystemInfoFormat.STORAGE_STAT_TTL_MS,
    )
    private val volumeUuid = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")

    fun collect(context: Context): SystemReadings {
        val am = context.getSystemService(ActivityManager::class.java)
        // Local MemoryInfo per call so off-UI PERF_HUD & About screen are 100% thread-safe.
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        if (cachedRamTotal < 0L && mem.totalMem > 0L) cachedRamTotal = mem.totalMem

        val storage = storageCache.getOrRefresh(SystemClock.elapsedRealtime()) {
            readStorageSnapshot()
        }
        val secondary = storage.secondary
        val battery = readBattery(context)
        if (!cpuMaxResolved) {
            cachedCpuMaxMhz = readMaxCpuFreqMhz()
            cpuMaxResolved = true
        }

        return SystemReadings(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty(),
            cpuMaxMhz = cachedCpuMaxMhz,
            ramTotalBytes = if (cachedRamTotal > 0L) cachedRamTotal else mem.totalMem,
            ramAvailBytes = mem.availMem,
            internalTotalBytes = storage.internalTotalBytes,
            internalFreeBytes = storage.internalFreeBytes,
            secondaryName = secondary?.first,
            secondaryIsSd = secondary?.first?.matches(volumeUuid) == true,
            secondaryTotalBytes = secondary?.second,
            secondaryFreeBytes = secondary?.third,
            batteryPercent = battery.percent,
            charging = battery.charging,
            powerSource = battery.source,
            powerMicroWatts = battery.powerUw,
        )
    }

    private fun readStorageSnapshot(): StorageSnapshot {
        val data = StatFs(Environment.getDataDirectory().absolutePath)
        return StorageSnapshot(
            internalTotalBytes = data.totalBytes,
            internalFreeBytes = data.availableBytes,
            secondary = findSecondaryVolume(),
        )
    }

    private data class BatterySnap(
        val percent: Int?,
        val charging: Boolean?,
        val source: String?,
        val powerUw: Long?,
    )

    private fun readBattery(context: Context): BatterySnap {
        val bm = context.getSystemService(BatteryManager::class.java)
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = SystemInfoFormat.resolveBatteryPercent(level, scale) {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            -> false
            else -> null
        }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "WIRELESS"
            charging == false -> "BATTERY"
            else -> "UNKNOWN"
        }
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it > 0 }
        val currentUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Long.MIN_VALUE && it != 0L }
        val powerUw = SystemInfoFormat.powerMicroWatts(currentUa, voltageMv)
        return BatterySnap(pct, charging, source, powerUw)
    }

    private fun readMaxCpuFreqMhz(): Int? {
        // Best-effort: cpu0 scaling max (kHz). Unprivileged; may be missing.
        return runCatching {
            val f = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (!f.canRead()) return null
            val kHz = f.readText().trim().toLongOrNull() ?: return null
            (kHz / 1000L).toInt().takeIf { it > 0 }
        }.getOrNull()
    }

    /**
     * First removable/secondary storage volume under /storage that is not
     * emulated primary. Returns label, total bytes, free bytes.
     */
    private fun findSecondaryVolume(): Triple<String, Long, Long>? {
        val base = File("/storage")
        if (!base.isDirectory) return null
        val kids = base.listFiles() ?: return null
        for (f in kids) {
            val name = f.name ?: continue
            if (name == "emulated" || name == "self" || name.startsWith(".")) continue
            if (!f.isDirectory || !f.canRead()) continue
            val stat = runCatching { StatFs(f.absolutePath) }.getOrNull() ?: continue
            val total = runCatching { stat.totalBytes }.getOrNull() ?: continue
            if (total <= 0L) continue
            val free = runCatching { stat.availableBytes }.getOrNull() ?: 0L
            return Triple(name, total, free)
        }
        return null
    }
}
