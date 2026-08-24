package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBatteryTest {

    @Test
    fun `four bars track depletion buckets`() {
        assertEquals(0, StatusBattery.batteryBars(0))
        assertEquals(1, StatusBattery.batteryBars(1))
        assertEquals(1, StatusBattery.batteryBars(25))
        assertEquals(2, StatusBattery.batteryBars(26))
        assertEquals(2, StatusBattery.batteryBars(50))
        assertEquals(3, StatusBattery.batteryBars(51))
        assertEquals(3, StatusBattery.batteryBars(75))
        assertEquals(4, StatusBattery.batteryBars(76))
        assertEquals(4, StatusBattery.batteryBars(100))
    }

    @Test
    fun `unplugged is always the battery glyph`() {
        assertEquals(
            StatusBattery.Glyph.BATTERY,
            StatusBattery.glyph(plugged = false, draining = true),
        )
        assertEquals(
            StatusBattery.Glyph.BATTERY,
            StatusBattery.glyph(plugged = false, draining = false),
        )
    }

    @Test
    fun `plugged and gaining is the charging bolt`() {
        assertEquals(
            StatusBattery.Glyph.CHARGING,
            StatusBattery.glyph(plugged = true, draining = false),
        )
    }

    @Test
    fun `plugged and net drain is the red bolt`() {
        assertEquals(
            StatusBattery.Glyph.NET_DRAIN,
            StatusBattery.glyph(plugged = true, draining = true, percent = 36),
        )
    }

    @Test
    fun `full pack stays charging even if current looks like drain`() {
        assertEquals(
            StatusBattery.Glyph.CHARGING,
            StatusBattery.glyph(plugged = true, draining = true, percent = 100),
        )
    }

    @Test
    fun `AOSP negative current is drain while plugged`() {
        assertTrue(
            StatusBattery.draining(
                currentUa = -400_000L,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = null,
            ),
        )
        assertFalse(
            StatusBattery.draining(
                currentUa = 400_000L,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = null,
            ),
        )
    }

    @Test
    fun `learned inverted sign treats positive current as drain`() {
        assertEquals(1, StatusBattery.learnDischargeSign(350_000L))
        assertTrue(
            StatusBattery.draining(
                currentUa = 350_000L,
                dischargeSign = 1,
                counterDelta = null,
            ),
        )
        assertFalse(
            StatusBattery.draining(
                currentUa = -350_000L,
                dischargeSign = 1,
                counterDelta = null,
            ),
        )
    }

    @Test
    fun `tiny current is ignored so charge-counter can decide`() {
        assertNull(StatusBattery.learnDischargeSign(1_000L))
        assertTrue(
            StatusBattery.draining(
                currentUa = 1_000L,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = -500L,
            ),
        )
        assertFalse(
            StatusBattery.draining(
                currentUa = 1_000L,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = 500L,
            ),
        )
    }

    @Test
    fun `falling charge counter is drain when current is unknown`() {
        assertTrue(
            StatusBattery.draining(
                currentUa = null,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = -1_000L,
            ),
        )
        assertFalse(
            StatusBattery.draining(
                currentUa = null,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = -50L,
            ),
        )
        assertFalse(
            StatusBattery.draining(
                currentUa = null,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = null,
            ),
        )
    }

    @Test
    fun `known charging current wins over a falling counter`() {
        assertFalse(
            StatusBattery.draining(
                currentUa = 400_000L,
                dischargeSign = StatusBattery.AOSP_DISCHARGE_SIGN,
                counterDelta = -5_000L,
            ),
        )
    }

    @Test
    fun `plug and unplug swap glyphs immediately`() {
        val toBattery = StatusBattery.stabilize(
            shown = StatusBattery.Glyph.CHARGING,
            candidate = StatusBattery.Glyph.BATTERY,
            pending = null,
            hits = 0,
        )
        assertEquals(StatusBattery.Glyph.BATTERY, toBattery.first)
        assertNull(toBattery.second)
        assertEquals(0, toBattery.third)

        val toCharge = StatusBattery.stabilize(
            shown = StatusBattery.Glyph.BATTERY,
            candidate = StatusBattery.Glyph.CHARGING,
            pending = null,
            hits = 0,
        )
        assertEquals(StatusBattery.Glyph.CHARGING, toCharge.first)
    }

    @Test
    fun `charging versus net-drain waits for a second matching sample`() {
        val first = StatusBattery.stabilize(
            shown = StatusBattery.Glyph.CHARGING,
            candidate = StatusBattery.Glyph.NET_DRAIN,
            pending = null,
            hits = 0,
        )
        assertEquals(StatusBattery.Glyph.CHARGING, first.first)
        assertEquals(StatusBattery.Glyph.NET_DRAIN, first.second)
        assertEquals(1, first.third)

        val second = StatusBattery.stabilize(
            shown = first.first,
            candidate = StatusBattery.Glyph.NET_DRAIN,
            pending = first.second,
            hits = first.third,
        )
        assertEquals(StatusBattery.Glyph.NET_DRAIN, second.first)
        assertNull(second.second)
        assertEquals(0, second.third)
    }

    @Test
    fun `a flipped candidate resets hysteresis`() {
        val pending = StatusBattery.stabilize(
            shown = StatusBattery.Glyph.CHARGING,
            candidate = StatusBattery.Glyph.NET_DRAIN,
            pending = null,
            hits = 0,
        )
        val flipped = StatusBattery.stabilize(
            shown = pending.first,
            candidate = StatusBattery.Glyph.CHARGING,
            pending = pending.second,
            hits = pending.third,
        )
        assertEquals(StatusBattery.Glyph.CHARGING, flipped.first)
        assertNull(flipped.second)
        assertEquals(0, flipped.third)
    }

    @Test
    fun `chrome write skips identical percent glyph and bars`() {
        assertFalse(
            StatusBattery.chromeNeedsWrite(38, StatusBattery.Glyph.CHARGING, 2, 38, StatusBattery.Glyph.CHARGING, 2),
        )
        assertTrue(
            StatusBattery.chromeNeedsWrite(38, StatusBattery.Glyph.CHARGING, 2, 40, StatusBattery.Glyph.CHARGING, 2),
        )
        assertTrue(
            StatusBattery.chromeNeedsWrite(38, StatusBattery.Glyph.CHARGING, 2, 38, StatusBattery.Glyph.NET_DRAIN, 2),
        )
        assertTrue(
            StatusBattery.chromeNeedsWrite(38, StatusBattery.Glyph.BATTERY, 2, 38, StatusBattery.Glyph.BATTERY, 1),
        )
        assertFalse(
            StatusBattery.chromeNeedsWrite(
                38,
                StatusBattery.Glyph.NET_DRAIN,
                0,
                38,
                StatusBattery.Glyph.NET_DRAIN,
                2,
            ),
        )
    }
}
