package com.visorcraft.ghostgalleon.system

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.literalArgs
import com.visorcraft.ghostgalleon.i18n.resourceIds
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInfoFormatTest {

    @Test
    fun `formatBytes scales`() {
        assertEquals(text(R.string.system_bytes, 512L), SystemInfoFormat.formatBytes(512))
        assertEquals(text(R.string.system_kilobytes, 1.0), SystemInfoFormat.formatBytes(1024))
        assertEquals(
            text(R.string.system_megabytes, 1.5),
            SystemInfoFormat.formatBytes((1.5 * 1024 * 1024).toLong()),
        )
    }

    @Test
    fun `formatWatts converts microwatts or unavailable`() {
        assertEquals(text(R.string.label_unavailable), SystemInfoFormat.formatWatts(null))
        assertEquals(text(R.string.label_unavailable), SystemInfoFormat.formatWatts(-1L))
        assertEquals(text(R.string.system_watts, 12.5), SystemInfoFormat.formatWatts(12_500_000L))
    }

    @Test
    fun `powerMicroWatts from current and voltage`() {
        // 1_000_000 µA * 5000 mV / 1000 = 5_000_000 µW = 5 W
        assertEquals(5_000_000L, SystemInfoFormat.powerMicroWatts(1_000_000L, 5000))
        // Negative discharge current still yields positive power.
        assertEquals(5_000_000L, SystemInfoFormat.powerMicroWatts(-1_000_000L, 5000))
        assertEquals(null, SystemInfoFormat.powerMicroWatts(null, 5000))
        assertEquals(null, SystemInfoFormat.powerMicroWatts(1000L, null))
    }

    @Test
    fun `rows include hardware ram storage battery power`() {
        val r = SystemReadings(
            manufacturer = "OneXPlayer",
            model = "One X Sugar",
            device = "onexsugar",
            hardware = "qcom",
            androidRelease = "14",
            sdkInt = 34,
            cpuCoreCount = 8,
            cpuAbi = "arm64-v8a",
            ramTotalBytes = 8L * 1024 * 1024 * 1024,
            ramAvailBytes = 3L * 1024 * 1024 * 1024,
            internalTotalBytes = 128L * 1024 * 1024 * 1024,
            internalFreeBytes = 40L * 1024 * 1024 * 1024,
            secondaryName = "7F7E-2949",
            secondaryTotalBytes = 512L * 1024 * 1024 * 1024,
            secondaryFreeBytes = 200L * 1024 * 1024 * 1024,
            batteryPercent = 77,
            charging = true,
            powerSource = "AC",
            powerMicroWatts = 8_000_000L,
        )
        val map = SystemInfoFormat.rows(r).associate { (label, value) ->
            (label as UiText.Resource).id to value
        }
        assertEquals(dynamicText("OneXPlayer One X Sugar"), map[R.string.system_hardware])
        assertTrue(R.string.system_used_total in map.getValue(R.string.system_ram).resourceIds())
        assertTrue(
            R.string.system_free_total in
                map.getValue(R.string.system_internal_storage).resourceIds(),
        )
        val secondary = SystemInfoFormat.rows(r).first {
            R.string.system_microsd_named in it.first.resourceIds()
        }.second
        assertTrue(R.string.system_free_total in secondary.resourceIds())
        assertTrue(R.string.system_battery_detail in map.getValue(R.string.system_battery).resourceIds())
        assertEquals(text(R.string.system_watts, 8.0), map[R.string.system_power_draw])
        assertTrue(R.plurals.system_cpu_cores in map.getValue(R.string.system_cpu).resourceIds())
        assertTrue("arm64-v8a" in map.getValue(R.string.system_cpu).literalArgs())
    }

    @Test
    fun `rows mark missing microSD and power`() {
        val r = SystemReadings(
            manufacturer = "X",
            model = "Y",
            batteryPercent = 50,
            charging = false,
            powerSource = "BATTERY",
            powerMicroWatts = null,
        )
        val map = SystemInfoFormat.rows(r).associate { (label, value) ->
            (label as UiText.Resource).id to value
        }
        assertEquals(text(R.string.system_not_present), map[R.string.system_microsd])
        assertEquals(text(R.string.label_unavailable), map[R.string.system_power_draw])
    }

    @Test
    fun `storage throttle refreshes every 15-30s and caches intermediate 1_5s PERF_HUD ticks`() {
        assertTrue(
            "storage TTL should be in 15..30 s",
            SystemInfoFormat.STORAGE_STAT_TTL_MS in 15_000L..30_000L,
        )
        val ttl = SystemInfoFormat.STORAGE_STAT_TTL_MS
        assertTrue(SystemInfoFormat.shouldRefreshStorage(Long.MIN_VALUE, 1_000L, ttl))
        assertFalse(SystemInfoFormat.shouldRefreshStorage(1_000L, 1_000L + ttl - 1L, ttl))
        assertTrue(SystemInfoFormat.shouldRefreshStorage(1_000L, 1_000L + ttl, ttl))
        // Backwards clock jump refreshes safely
        assertTrue(SystemInfoFormat.shouldRefreshStorage(5_000L, 4_000L, ttl))

        val cache = ThrottledStorageCache<Int>(ttlMs = ttl)
        var statCalls = 0
        // Simulate 15 PERF_HUD ticks spaced 1,500 ms apart (0 ms .. 21,000 ms) -> only 2 StatFs reads!
        for (tick in 0 until 15) {
            val nowMs = tick * 1_500L
            val value = cache.getOrRefresh(nowMs) { ++statCalls }
            assertEquals(statCalls, value)
        }
        assertEquals("15 ticks across 21s must invoke StatFs only twice", 2, statCalls)

        cache.invalidate()
        cache.getOrRefresh(21_500L) { ++statCalls }
        assertEquals(3, statCalls)
    }

    @Test
    fun `resolveBatteryPercent derives from sticky intent without HAL fallback and falls back when invalid`() {
        var fallbackCalls = 0
        // Valid sticky level/scale skips fallback HAL IPC completely
        assertEquals(
            73,
            SystemInfoFormat.resolveBatteryPercent(73, 100) {
                fallbackCalls++
                99
            },
        )
        assertEquals(
            75,
            SystemInfoFormat.resolveBatteryPercent(150, 200) {
                fallbackCalls++
                99
            },
        )
        assertEquals(0, fallbackCalls)

        // Missing or invalid sticky data falls back cleanly to BatteryManager capacity
        assertEquals(
            64,
            SystemInfoFormat.resolveBatteryPercent(-1, 100) {
                fallbackCalls++
                64
            },
        )
        assertEquals(
            52,
            SystemInfoFormat.resolveBatteryPercent(50, 0) {
                fallbackCalls++
                52
            },
        )
        assertEquals(
            88,
            SystemInfoFormat.resolveBatteryPercent(120, 100) {
                fallbackCalls++
                88
            },
        )
        assertEquals(3, fallbackCalls)

        // Invalid fallback value yields null
        assertEquals(
            null,
            SystemInfoFormat.resolveBatteryPercent(-1, -1) { -5 },
        )
    }
}
