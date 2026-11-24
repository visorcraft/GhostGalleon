package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.SelectionStrip
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopStripMetricsTest {

    @Test
    fun `width buckets scale padding, gap, type, and RA meter across 720p to 1440p`() {
        val narrow = TopStripMetrics.forWidth(540f)
        val standard = TopStripMetrics.forWidth(760f) // Odin 2 Portal 1080p landscape
        val wide = TopStripMetrics.forWidth(1080f)    // 1440p landscape

        assertTrue(narrow.padHorizontalDp < standard.padHorizontalDp)
        assertTrue(standard.padHorizontalDp < wide.padHorizontalDp)
        assertTrue(narrow.artGapDp < standard.artGapDp)
        assertTrue(standard.artGapDp < wide.artGapDp)
        assertTrue(narrow.titleSp < standard.titleSp)
        assertTrue(standard.titleSp < wide.titleSp)
        assertTrue(narrow.raBarWidthDp < standard.raBarWidthDp)
        assertTrue(standard.raBarWidthDp < wide.raBarWidthDp)
    }

    @Test
    fun `art and four-line text stack fit strictly inside STRIP_HEIGHT_DP at all widths`() {
        for (widthDp in listOf(480f, 640f, 760f, 960f, 1280f)) {
            val spec = TopStripMetrics.forWidth(widthDp)
            assertTrue(
                "art + padding at $widthDp dp must fit inside ${SelectionStrip.STRIP_HEIGHT_DP}",
                spec.artSizeDp + spec.padVerticalDp * 2 <= SelectionStrip.STRIP_HEIGHT_DP,
            )
            val textHeight = TopStripMetrics.estimatedTextColumnHeightDp(spec)
            assertTrue(
                "text column ($textHeight dp) at $widthDp dp must fit inside ${SelectionStrip.STRIP_HEIGHT_DP}",
                textHeight <= SelectionStrip.STRIP_HEIGHT_DP,
            )
        }
    }

    @Test
    fun `stripDividerColor preserves subtle alpha across all built-in theme accents`() {
        for (pack in ThemePack.BUILTINS) {
            val divider = TopStripMetrics.stripDividerColor(pack.accentColor)
            assertEquals(0x38, (divider ushr 24) and 0xFF)
        }
    }

    @Test
    fun `accessibilitySummary joins title and subtitle cleanly without duplication`() {
        assertEquals(
            "Chrono Trigger, Super Nintendo",
            TopStripMetrics.accessibilitySummary("Chrono Trigger", "Super Nintendo"),
        )
        assertEquals(
            "Settings",
            TopStripMetrics.accessibilitySummary("Settings", "Settings"),
        )
        assertEquals(
            "Ghost Galleon",
            TopStripMetrics.accessibilitySummary("Ghost Galleon", "   "),
        )
    }
}
