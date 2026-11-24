package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPanelMetricsTest {

    @Test
    fun `narrow Sugar stays 3 columns while wide Odin promotes large folders to 4 columns`() {
        // One X Sugar bottom (~540dp = 1080px @ 2.0) stays 3 columns even with 12 items.
        val sugar = FolderPanelMetrics.compute(
            screenWidthPx = 1080,
            screenHeightPx = 1080,
            density = 2.0f,
            memberCount = 12,
        )
        assertEquals(3, sugar.columns)
        assertTrue(sugar.cardWidthPx <= 1080)
        assertTrue(sugar.thumbSizePx >= (56 * 2.0f).toInt())

        // Wide Odin 2 Portal (960dp = 1920px @ 2.0) keeps 3 columns for small folders (<= 6)...
        val odinSmall = FolderPanelMetrics.compute(
            screenWidthPx = 1920,
            screenHeightPx = 1080,
            density = 2.0f,
            memberCount = 5,
        )
        assertEquals(3, odinSmall.columns)

        // ...and upgrades to 4 columns for larger folders (>= 7 items).
        val odinLarge = FolderPanelMetrics.compute(
            screenWidthPx = 1920,
            screenHeightPx = 1080,
            density = 2.0f,
            memberCount = 10,
        )
        assertEquals(4, odinLarge.columns)
        assertTrue(odinLarge.cardWidthPx > odinSmall.cardWidthPx)
        assertTrue(odinLarge.thumbSizePx >= (56 * 2.0f).toInt())
    }

    @Test
    fun `thumbnails strictly fit within card width without horizontal overflow across viewports`() {
        val viewports = listOf(
            Triple(720, 720, 2.0f),   // tight 360dp
            Triple(1080, 1080, 2.0f), // Sugar 540dp
            Triple(1920, 1080, 2.5f), // Odin 768dp
            Triple(1920, 1080, 2.0f), // Odin 960dp
        )
        for ((w, h, d) in viewports) {
            for (count in listOf(0, 1, 3, 6, 7, 15)) {
                val spec = FolderPanelMetrics.compute(w, h, d, count)
                val innerPadding = ((UiDimens.SPACE_LG * 2 + UiDimens.SPACE_XS * 2) * d).toInt()
                val colGaps = (UiDimens.SPACE_SM * (spec.columns - 1) * d).toInt()
                val cellPads = (UiDimens.SPACE_XS * 2 * spec.columns * d).toInt()
                val requiredWidth = innerPadding + colGaps + cellPads + spec.thumbSizePx * spec.columns
                assertTrue(
                    "overflow on ${w}x${h}@$d count=$count: required=$requiredWidth > card=${spec.cardWidthPx}",
                    requiredWidth <= spec.cardWidthPx + 4,
                )
                assertTrue(spec.thumbSizePx >= 1)
                assertTrue(spec.maxScrollHeightPx <= h)
            }
        }
    }

    @Test
    fun `scrollCueState reflects non-scrollable, top, middle, and bottom positions accurately`() {
        // Short content fits cleanly -> neither cue shows.
        val fits = FolderPanelMetrics.scrollCueState(scrollY = 0, contentHeightPx = 300, viewportHeightPx = 400)
        assertFalse(fits.showTopCue)
        assertFalse(fits.showBottomCue)

        // Tall content at top -> only bottom cue shows.
        val atTop = FolderPanelMetrics.scrollCueState(scrollY = 0, contentHeightPx = 800, viewportHeightPx = 400)
        assertFalse(atTop.showTopCue)
        assertTrue(atTop.showBottomCue)

        // Middle of scroll -> both cues show.
        val middle = FolderPanelMetrics.scrollCueState(scrollY = 180, contentHeightPx = 800, viewportHeightPx = 400)
        assertTrue(middle.showTopCue)
        assertTrue(middle.showBottomCue)

        // Reached bottom -> only top cue shows.
        val atBottom = FolderPanelMetrics.scrollCueState(scrollY = 400, contentHeightPx = 800, viewportHeightPx = 400)
        assertTrue(atBottom.showTopCue)
        assertFalse(atBottom.showBottomCue)
    }

    @Test
    fun `emptyMedallionTint meets WCAG AA on every built-in theme sunken surface`() {
        for (tokens in ThemePack.BUILTINS) {
            val sunken = ThemePack.surfaceSunken(tokens)
            val tint = FolderPanelMetrics.emptyMedallionTint(tokens.accentColor, sunken)
            val ratio = ThemePack.contrastRatio(tint, sunken)
            assertTrue("theme=${tokens.id} medallion contrast $ratio < 4.5", ratio >= ThemePack.MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `mirrorButtonStyle keeps focused text and border WCAG AA across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val fill = ThemePack.cardFill(tokens)
            val hairline = ThemePack.hairlineDivider(tokens)
            val focused = FolderPanelMetrics.mirrorButtonStyle(
                focused = true,
                accent = tokens.accentColor,
                surfaceFill = fill,
                chipIdleFill = tokens.chipIdle,
                hairlineColor = hairline,
            )
            val idle = FolderPanelMetrics.mirrorButtonStyle(
                focused = false,
                accent = tokens.accentColor,
                surfaceFill = fill,
                chipIdleFill = tokens.chipIdle,
                hairlineColor = hairline,
            )
            assertTrue(focused.bold)
            assertTrue(focused.scale > idle.scale)
            assertTrue(focused.elevationDp > idle.elevationDp)
            assertTrue(
                "theme=${tokens.id} focused text contrast < 4.5",
                ThemePack.contrastRatio(focused.textColor, focused.fillColor) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "theme=${tokens.id} focused border contrast < 4.5",
                ThemePack.contrastRatio(focused.strokeColor, fill) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "theme=${tokens.id} idle text contrast < 4.5",
                ThemePack.contrastRatio(idle.textColor, idle.fillColor) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `compute adapts small 1-2 item folders cleanly across 540dp Sugar and 960dp Odin 2 Portal`() {
        val viewports = listOf(
            "sugar-540dp" to Triple(1080, 1080, 2.0f),
            "odin-960dp" to Triple(1920, 1080, 2.0f),
        )
        for ((name, vp) in viewports) {
            val (w, h, d) = vp
            val empty = FolderPanelMetrics.compute(w, h, d, memberCount = 0)
            val one = FolderPanelMetrics.compute(w, h, d, memberCount = 1)
            val two = FolderPanelMetrics.compute(w, h, d, memberCount = 2)
            val three = FolderPanelMetrics.compute(w, h, d, memberCount = 3)
            val seven = FolderPanelMetrics.compute(w, h, d, memberCount = 7)
            val twelve = FolderPanelMetrics.compute(w, h, d, memberCount = 12)

            assertEquals("$name empty columns", 3, empty.columns)
            assertEquals("$name 1-item columns", 1, one.columns)
            assertEquals("$name 2-item columns", 2, two.columns)
            assertEquals("$name 3-item columns", 3, three.columns)
            assertEquals("$name 1-item card width", (280 * d).toInt(), one.cardWidthPx)
            assertEquals("$name 2-item card width", (340 * d).toInt(), two.cardWidthPx)
            assertTrue("$name 1-item card narrower than 2-item", one.cardWidthPx < two.cardWidthPx)
            assertTrue("$name 2-item card narrower than 3-item", two.cardWidthPx < three.cardWidthPx)
            if (name == "odin-960dp") {
                assertEquals(4, seven.columns)
                assertEquals(4, twelve.columns)
            } else {
                assertEquals(3, seven.columns)
                assertEquals(3, twelve.columns)
            }
        }
        assertEquals(90L, FolderPanelMetrics.SCROLL_CUE_FADE_MS)
    }
}
