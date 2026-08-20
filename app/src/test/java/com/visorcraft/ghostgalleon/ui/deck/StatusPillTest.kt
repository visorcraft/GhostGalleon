package com.visorcraft.ghostgalleon.ui.deck

import android.os.BatteryManager
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPillTest {

    @Test
    fun `formatBatteryLabel accepts 0 through 100`() {
        assertEquals(text(R.string.system_battery_percent, 0), StatusPill.formatBatteryLabel(0))
        assertEquals(text(R.string.system_battery_percent, 42), StatusPill.formatBatteryLabel(42))
        assertEquals(text(R.string.system_battery_percent, 100), StatusPill.formatBatteryLabel(100))
    }

    @Test
    fun `formatBatteryLabel rejects out of range`() {
        assertNull(StatusPill.formatBatteryLabel(-1))
        assertNull(StatusPill.formatBatteryLabel(101))
        assertNull(StatusPill.formatBatteryLabel(999))
    }

    @Test
    fun `formatBatteryLabel is percent only`() {
        assertEquals(
            text(R.string.system_battery_percent, 88),
            StatusPill.formatBatteryLabel(88),
        )
    }

    @Test
    fun `flush companion overlay sits in the true corner`() {
        assertEquals(8 to 12, StatusPill.overlayInsetDp(flushCorner = false))
        assertEquals(4 to 4, StatusPill.overlayInsetDp(flushCorner = true))
    }

    @Test
    fun `broadcast level wins over a stale capacity property`() {
        assertEquals(
            40,
            StatusPill.percentFrom(level = 40, scale = 100, capacityProperty = 38),
        )
    }

    @Test
    fun `capacity fills in when broadcast extras are missing`() {
        assertEquals(
            38,
            StatusPill.percentFrom(level = -1, scale = -1, capacityProperty = 38),
        )
        assertEquals(
            -1,
            StatusPill.percentFrom(level = -1, scale = 0, capacityProperty = -1),
        )
    }

    @Test
    fun `AC plugged counts as charging even when status flaps to discharging`() {
        val snap = StatusPill.snapshotFrom(
            level = 40,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            capacityProperty = 38,
        )
        assertEquals(40, snap.percent)
        assertTrue(snap.charging)
        assertEquals(StatusBattery.Glyph.CHARGING, snap.glyph)
        assertEquals(2, snap.bars)
        assertEquals(
            text(R.string.system_battery_percent, 40),
            StatusPill.formatBatteryLabel(snap.percent),
        )
    }

    @Test
    fun `unplugged discharging is not charging`() {
        val snap = StatusPill.snapshotFrom(
            level = 38,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            plugged = 0,
        )
        assertEquals(38, snap.percent)
        assertFalse(snap.charging)
        assertEquals(StatusBattery.Glyph.BATTERY, snap.glyph)
        assertEquals(2, snap.bars)
        assertEquals(
            text(R.string.system_battery_percent, 38),
            StatusPill.formatBatteryLabel(snap.percent),
        )
    }

    @Test
    fun `battery label write is skipped when the visible text already matches`() {
        assertFalse(StatusPill.batteryLabelNeedsWrite("38%", "38%"))
        assertTrue(StatusPill.batteryLabelNeedsWrite("38%", "40%"))
        assertTrue(StatusPill.batteryLabelNeedsWrite(null, "40%"))
    }

    @Test
    fun `plugged drain current becomes the net-drain bolt`() {
        val snap = StatusPill.snapshotFrom(
            level = 38,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            currentUa = -400_000L,
        )
        assertTrue(snap.plugged)
        assertEquals(StatusBattery.Glyph.NET_DRAIN, snap.glyph)
    }
}
