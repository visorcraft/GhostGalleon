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

    @Test
    fun `full pack on AC is charging not net-drain`() {
        val snap = StatusPill.snapshotFrom(
            level = 100,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            currentUa = -400_000L,
        )
        assertEquals(StatusBattery.Glyph.CHARGING, snap.glyph)
    }

    @Test
    fun `all three battery states map to vector drawables with distinct tints`() {
        assertEquals(
            R.drawable.ic_status_battery,
            StatusPill.batteryIconRes(StatusBattery.Glyph.BATTERY),
        )
        assertEquals(
            R.drawable.ic_status_bolt,
            StatusPill.batteryIconRes(StatusBattery.Glyph.CHARGING),
        )
        assertEquals(
            R.drawable.ic_status_bolt,
            StatusPill.batteryIconRes(StatusBattery.Glyph.NET_DRAIN),
        )
        assertEquals(
            StatusPill.CHARGING_BOLT,
            StatusPill.batteryIconTint(50, StatusBattery.Glyph.CHARGING),
        )
        assertEquals(
            StatusPill.NET_DRAIN_BOLT,
            StatusPill.batteryIconTint(50, StatusBattery.Glyph.NET_DRAIN),
        )
    }

    @Test
    fun `unplugged low battery tints amber at 11 to 15 and red at 10 or below`() {
        assertFalse(StatusPill.isLowBattery(16, StatusBattery.Glyph.BATTERY))
        assertEquals(
            StatusPill.NORMAL_COLOR,
            StatusPill.batteryTextColor(16, StatusBattery.Glyph.BATTERY),
        )
        assertEquals(
            StatusPill.NORMAL_COLOR,
            StatusPill.batteryIconTint(16, StatusBattery.Glyph.BATTERY),
        )

        assertTrue(StatusPill.isLowBattery(15, StatusBattery.Glyph.BATTERY))
        assertEquals(
            StatusPill.LOW_BATTERY_WARN,
            StatusPill.batteryTextColor(15, StatusBattery.Glyph.BATTERY),
        )
        assertEquals(
            StatusPill.LOW_BATTERY_WARN,
            StatusPill.batteryIconTint(11, StatusBattery.Glyph.BATTERY),
        )

        assertTrue(StatusPill.isLowBattery(10, StatusBattery.Glyph.BATTERY))
        assertEquals(
            StatusPill.LOW_BATTERY_CRITICAL,
            StatusPill.batteryTextColor(10, StatusBattery.Glyph.BATTERY),
        )
        assertEquals(
            StatusPill.LOW_BATTERY_CRITICAL,
            StatusPill.batteryIconTint(5, StatusBattery.Glyph.BATTERY),
        )

        // Plugged states never show unplugged low-battery tint on text.
        assertFalse(StatusPill.isLowBattery(5, StatusBattery.Glyph.CHARGING))
        assertEquals(
            StatusPill.NORMAL_COLOR,
            StatusPill.batteryTextColor(5, StatusBattery.Glyph.CHARGING),
        )
        assertEquals(
            StatusPill.CHARGING_BOLT,
            StatusPill.batteryIconTint(5, StatusBattery.Glyph.CHARGING),
        )
    }

    @Test
    fun `pillBorderColor reflects charging, net-drain, low, critical, and resting states`() {
        assertEquals(
            StatusPill.DEFAULT_BORDER_COLOR,
            StatusPill.pillBorderColor(65, StatusBattery.Glyph.BATTERY),
        )
        assertEquals(
            StatusPill.LOW_BATTERY_WARN and 0x00FFFFFF,
            StatusPill.pillBorderColor(15, StatusBattery.Glyph.BATTERY) and 0x00FFFFFF,
        )
        assertEquals(
            StatusPill.LOW_BATTERY_CRITICAL and 0x00FFFFFF,
            StatusPill.pillBorderColor(8, StatusBattery.Glyph.BATTERY) and 0x00FFFFFF,
        )
        assertEquals(
            StatusPill.CHARGING_BOLT and 0x00FFFFFF,
            StatusPill.pillBorderColor(45, StatusBattery.Glyph.CHARGING) and 0x00FFFFFF,
        )
        assertEquals(
            StatusPill.NET_DRAIN_BOLT and 0x00FFFFFF,
            StatusPill.pillBorderColor(45, StatusBattery.Glyph.NET_DRAIN) and 0x00FFFFFF,
        )
    }

    @Test
    fun `accessibilitySummary combines localized battery description with live time`() {
        assertEquals(
            "Battery 72 percent, charging, Clock 8:15 PM",
            StatusPill.accessibilitySummary("Battery 72 percent, charging", "8:15 PM", "Clock", "Status"),
        )
        assertEquals(
            "Clock 8:15 PM",
            StatusPill.accessibilitySummary(null, "8:15 PM", "Clock", "Status"),
        )
        assertEquals(
            "Status",
            StatusPill.accessibilitySummary("", "  ", "Clock", "Status"),
        )
    }

    @Test
    fun `metricsFor scales compact and large status pills cleanly`() {
        val compact = StatusPill.metricsFor(compact = true)
        val large = StatusPill.metricsFor(compact = false)
        assertTrue(compact.textSp < large.textSp)
        assertTrue(compact.iconDp < large.iconDp)
        assertTrue(compact.padHorizontalDp < large.padHorizontalDp)
        assertTrue(compact.dividerHeightDp < large.dividerHeightDp)
    }

    @Test
    fun `all status pill foreground tints meet WCAG AA contrast across every built-in theme`() {
        val tints = listOf(
            StatusPill.NORMAL_COLOR,
            StatusPill.CHARGING_BOLT,
            StatusPill.NET_DRAIN_BOLT,
            StatusPill.LOW_BATTERY_WARN,
            StatusPill.LOW_BATTERY_CRITICAL,
        )
        for (pack in com.visorcraft.ghostgalleon.settings.ThemePack.BUILTINS) {
            val pillSurface = com.visorcraft.ghostgalleon.settings.ThemePack.cardFill(pack)
            for (fg in tints) {
                val ratio = com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(fg, pillSurface)
                assertTrue(
                    "StatusPill tint #${Integer.toHexString(fg)} on ${pack.id} must be >= 4.5, was $ratio",
                    ratio >= com.visorcraft.ghostgalleon.settings.ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `NET_DRAIN tints battery percent red with WCAG AA contrast over statusPillFill across themes`() {
        assertEquals(
            StatusPill.NET_DRAIN_BOLT,
            StatusPill.batteryTextColor(50, StatusBattery.Glyph.NET_DRAIN),
        )
        assertEquals(
            StatusPill.NET_DRAIN_BOLT,
            StatusPill.batteryTextColor(95, StatusBattery.Glyph.NET_DRAIN),
        )
        for (pack in com.visorcraft.ghostgalleon.settings.ThemePack.BUILTINS) {
            val pillFill = TileBackgrounds.statusPillFill(
                com.visorcraft.ghostgalleon.settings.ThemePack.cardFill(pack),
            )
            // Composite translucent statusPillFill over black backdrop for exact on-screen surface.
            val compositedBg = CompanionThemeAudit.compositeAlphaOver(pillFill, 0xFF000000.toInt())
            val ratio = com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(
                StatusPill.batteryTextColor(50, StatusBattery.Glyph.NET_DRAIN),
                compositedBg,
            )
            assertTrue(
                "${pack.id} NET_DRAIN percent contrast $ratio < 4.5 over statusPillFill",
                ratio >= com.visorcraft.ghostgalleon.settings.ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `pillBorderColor uses theme restingHairline at rest while preserving semantic overrides`() {
        for (pack in com.visorcraft.ghostgalleon.settings.ThemePack.BUILTINS) {
            val hairline = com.visorcraft.ghostgalleon.settings.ThemePack.hairlineDivider(pack)
            assertEquals(
                hairline,
                StatusPill.pillBorderColor(75, StatusBattery.Glyph.BATTERY, hairline),
            )
            // Semantic overrides take precedence over resting hairline across all themes.
            assertEquals(
                StatusPill.CHARGING_BOLT and 0x00FFFFFF,
                StatusPill.pillBorderColor(75, StatusBattery.Glyph.CHARGING, hairline) and 0x00FFFFFF,
            )
            assertEquals(
                StatusPill.NET_DRAIN_BOLT and 0x00FFFFFF,
                StatusPill.pillBorderColor(75, StatusBattery.Glyph.NET_DRAIN, hairline) and 0x00FFFFFF,
            )
            assertEquals(
                StatusPill.LOW_BATTERY_WARN and 0x00FFFFFF,
                StatusPill.pillBorderColor(15, StatusBattery.Glyph.BATTERY, hairline) and 0x00FFFFFF,
            )
            assertEquals(
                StatusPill.LOW_BATTERY_CRITICAL and 0x00FFFFFF,
                StatusPill.pillBorderColor(8, StatusBattery.Glyph.BATTERY, hairline) and 0x00FFFFFF,
            )
        }
    }
}
