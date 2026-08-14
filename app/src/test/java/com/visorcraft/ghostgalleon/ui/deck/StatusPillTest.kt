package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `formatBatteryLabel marks charging`() {
        assertEquals(
            text(R.string.battery_percent_charging, 88),
            StatusPill.formatBatteryLabel(88, charging = true),
        )
        assertEquals(
            text(R.string.system_battery_percent, 88),
            StatusPill.formatBatteryLabel(88, charging = false),
        )
    }

    @Test
    fun `flush companion overlay sits in the true corner`() {
        assertEquals(8 to 12, StatusPill.overlayInsetDp(flushCorner = false))
        assertEquals(4 to 4, StatusPill.overlayInsetDp(flushCorner = true))
    }
}
