package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SystemChromeMetricsTest {

    @Test
    fun `chrome buttons stay dead-center vertically on dock pill across Sugar and Odin`() {
        val viewports = listOf(
            Triple(1080, 2.0f, 48), // Sugar 540dp
            Triple(1920, 2.5f, 64), // Odin 2 Portal 768dp
            Triple(1920, 2.0f, 72), // Odin 2 Portal 960dp
        )
        for ((widthPx, density, suggestedDp) in viewports) {
            for (slots in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
                val dock = DockBarMetrics.compute(
                    windowWidthPx = widthPx,
                    density = density,
                    suggestedSlotDp = suggestedDp,
                    visibleSlots = slots,
                    hasChromeIcons = true,
                )
                val chrome = SystemChromeMetrics.compute(dock, density)

                val dockHeightPx = dock.slotSizePx + (SystemChromeMetrics.DOCK_BAR_PAD_V_DP * 2 * density).toInt()
                val dockCenterY = dock.bottomMarginPx + dockHeightPx / 2
                val chromeCenterY = chrome.bottomMarginPx + chrome.buttonSizePx / 2
                assertTrue(
                    "vertical drift > 1px on w=$widthPx d=$density slots=$slots: dock=$dockCenterY chrome=$chromeCenterY",
                    abs(dockCenterY - chromeCenterY) <= 1,
                )
            }
        }
    }

    @Test
    fun `chrome buttons maintain at least 8dp horizontal clearance from centered dock pill`() {
        val viewports = listOf(
            Triple(1080, 2.0f, 48),
            Triple(1920, 2.5f, 64),
            Triple(1920, 2.0f, 72),
        )
        for ((widthPx, density, suggestedDp) in viewports) {
            for (slots in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
                val dock = DockBarMetrics.compute(widthPx, density, suggestedDp, slots, hasChromeIcons = true)
                val chrome = SystemChromeMetrics.compute(dock, density)
                val chromeInnerEdgePx = chrome.edgeMarginPx + chrome.buttonSizePx
                val dockStartEdgePx = (widthPx - dock.barWidthPx(slots)) / 2
                val gapPx = dockStartEdgePx - chromeInnerEdgePx
                val minGapPx = (SystemChromeMetrics.MIN_HORIZONTAL_GAP_DP * density).toInt()
                assertTrue(
                    "overlap/tight gap on w=$widthPx d=$density slots=$slots: gap=$gapPx < $minGapPx",
                    gapPx >= minGapPx,
                )
            }
        }
    }

    @Test
    fun `wide Odin bottom margin rises well above legacy hardcoded 14dp`() {
        // Odin 960dp (72dp slots -> 88dp pill + 8dp margin -> 52dp centerline -> 32dp bottom margin).
        val chrome = SystemChromeMetrics.computeForWindow(
            windowWidthPx = 1920,
            density = 2.0f,
            suggestedSlotDp = 72,
            visibleSlots = 5,
        )
        assertEquals(64, chrome.bottomMarginPx) // 32dp @ 2.0x
        assertEquals(80, chrome.buttonSizePx)   // 40dp @ 2.0x
    }

    @Test
    fun `swap and settings icon tints exceed WCAG AA contrast across all built-in themes`() {
        val scrimBg = SystemChromeMetrics.CHROME_SCRIM_RGB
        val settingsContrast = ThemePack.contrastRatio(SystemChromeMetrics.settingsIconTint(), scrimBg)
        assertTrue("settings tint contrast $settingsContrast < 4.5", settingsContrast >= ThemePack.MIN_TEXT_CONTRAST)

        val accents = ThemePack.BUILTINS.map { it.accentColor } + listOf(
            0xFF1A237E.toInt(),
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
        )
        for (accent in accents) {
            val swapTint = SystemChromeMetrics.swapIconTint(accent)
            val ratio = ThemePack.contrastRatio(swapTint, scrimBg)
            assertTrue(
                "swap tint contrast $ratio < 4.5 for accent=${accent.toUInt().toString(16)}",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }
}
