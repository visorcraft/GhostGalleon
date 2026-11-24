package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.system.SystemInfoFormat
import com.visorcraft.ghostgalleon.system.SystemReadings
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.deck.PerfHudMetrics.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfHudMetricsTest {

    @Test
    fun `ramMeter classifies nominal, warn, and critical thresholds accurately`() {
        assertNull(PerfHudMetrics.ramMeter(totalBytes = 0L, availBytes = 0L))

        val ok = PerfHudMetrics.ramMeter(totalBytes = 1000L, availBytes = 500L)!!
        assertEquals(0.50f, ok.fraction, 0.001f)
        assertEquals(Severity.GOOD, ok.severity)

        val warn = PerfHudMetrics.ramMeter(totalBytes = 1000L, availBytes = 250L)!!
        assertEquals(0.75f, warn.fraction, 0.001f)
        assertEquals(Severity.WARN, warn.severity)

        val crit = PerfHudMetrics.ramMeter(totalBytes = 1000L, availBytes = 100L)!!
        assertEquals(0.90f, crit.fraction, 0.001f)
        assertEquals(Severity.CRITICAL, crit.severity)
    }

    @Test
    fun `storageMeter classifies nominal, warn, and critical thresholds and rejects missing volumes`() {
        assertNull(PerfHudMetrics.storageMeter(freeBytes = null, totalBytes = null))
        assertNull(PerfHudMetrics.storageMeter(freeBytes = 0L, totalBytes = 0L))

        val ok = PerfHudMetrics.storageMeter(freeBytes = 500L, totalBytes = 1000L)!!
        assertEquals(0.50f, ok.fraction, 0.001f)
        assertEquals(Severity.GOOD, ok.severity)

        val warn = PerfHudMetrics.storageMeter(freeBytes = 200L, totalBytes = 1000L)!!
        assertEquals(0.80f, warn.fraction, 0.001f)
        assertEquals(Severity.WARN, warn.severity)

        val crit = PerfHudMetrics.storageMeter(freeBytes = 80L, totalBytes = 1000L)!!
        assertEquals(0.92f, crit.fraction, 0.001f)
        assertEquals(Severity.CRITICAL, crit.severity)
    }

    @Test
    fun `batteryMeter flags low and critical unplugged packs but marks charging good`() {
        assertNull(PerfHudMetrics.batteryMeter(percent = null, charging = false))
        assertNull(PerfHudMetrics.batteryMeter(percent = -1, charging = false))

        assertEquals(Severity.GOOD, PerfHudMetrics.batteryMeter(60, charging = false)!!.severity)
        assertEquals(Severity.WARN, PerfHudMetrics.batteryMeter(20, charging = false)!!.severity)
        assertEquals(Severity.WARN, PerfHudMetrics.batteryMeter(11, charging = false)!!.severity)
        assertEquals(Severity.CRITICAL, PerfHudMetrics.batteryMeter(10, charging = false)!!.severity)
        assertEquals(Severity.GOOD, PerfHudMetrics.batteryMeter(8, charging = true)!!.severity)
    }

    @Test
    fun `powerMeter scales against 15W handheld ceiling with warn and critical thresholds`() {
        assertNull(PerfHudMetrics.powerMeter(null))
        assertNull(PerfHudMetrics.powerMeter(0L))

        val low = PerfHudMetrics.powerMeter(3_000_000L)!! // 3W
        assertEquals(0.20f, low.fraction, 0.001f)
        assertEquals(Severity.GOOD, low.severity)

        val mid = PerfHudMetrics.powerMeter(7_500_000L)!! // 7.5W
        assertEquals(0.50f, mid.fraction, 0.001f)
        assertEquals(Severity.WARN, mid.severity)

        val high = PerfHudMetrics.powerMeter(12_000_000L)!! // 12W
        assertEquals(0.80f, high.fraction, 0.001f)
        assertEquals(Severity.CRITICAL, high.severity)

        val clamped = PerfHudMetrics.powerMeter(22_000_000L)!! // 22W -> clamped to 1.0
        assertEquals(1.0f, clamped.fraction, 0.001f)
        assertEquals(Severity.CRITICAL, clamped.severity)
    }

    @Test
    fun `meterForRow resolves meters by UiText resource id independent of optional rows`() {
        val readings = SystemReadings(
            manufacturer = "OneX",
            model = "Sugar",
            device = "sugar_1",
            hardware = "qcom",
            androidRelease = "14",
            sdkInt = 34,
            ramTotalBytes = 12_000_000_000L,
            ramAvailBytes = 2_000_000_000L,
            internalTotalBytes = 256_000_000_000L,
            internalFreeBytes = 40_000_000_000L,
            secondaryName = "ABCD-1234",
            secondaryIsSd = true,
            secondaryTotalBytes = 512_000_000_000L,
            secondaryFreeBytes = 256_000_000_000L,
            batteryPercent = 18,
            charging = false,
            powerMicroWatts = 8_000_000L,
        )
        val rows = SystemInfoFormat.rows(readings)
        val metered = rows.mapNotNull { (label, _) ->
            PerfHudMetrics.meterForRow(label, readings)?.let { label to it }
        }
        assertEquals(5, metered.size)
        assertNull(PerfHudMetrics.meterForRow(text(R.string.system_hardware), readings))
        assertNotNull(PerfHudMetrics.meterForRow(text(R.string.system_ram), readings))
        assertEquals(Severity.WARN, PerfHudMetrics.meterForRow(text(R.string.system_battery), readings)!!.severity)
    }

    @Test
    fun `all semantic status colors meet WCAG AA contrast across every built-in theme card and track`() {
        for (pack in ThemePack.BUILTINS) {
            val card = ThemePack.cardFill(pack)
            val track = ThemePack.surfaceSunken(pack)
            for (severity in Severity.values()) {
                val color = PerfHudMetrics.statusColor(severity)
                val cardRatio = ThemePack.contrastRatio(color, card)
                val trackRatio = ThemePack.contrastRatio(color, track)
                assertTrue(
                    "PERF $severity on ${pack.id} card must be >= 4.5, was $cardRatio",
                    cardRatio >= ThemePack.MIN_TEXT_CONTRAST,
                )
                assertTrue(
                    "PERF $severity on ${pack.id} track must be >= 4.5, was $trackRatio",
                    trackRatio >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
        assertEquals(UiDimens.ALPHA_PRIMARY, PerfHudMetrics.valueTextColor(null))
        assertEquals(UiDimens.ALPHA_PRIMARY, PerfHudMetrics.valueTextColor(Severity.GOOD))
        assertEquals(PerfHudMetrics.COLOR_WARN, PerfHudMetrics.valueTextColor(Severity.WARN))
        assertEquals(PerfHudMetrics.COLOR_CRITICAL, PerfHudMetrics.valueTextColor(Severity.CRITICAL))
    }
}
