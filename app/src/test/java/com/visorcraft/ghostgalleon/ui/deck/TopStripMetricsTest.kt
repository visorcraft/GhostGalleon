package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.rom.Platforms
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

    @Test
    fun `appArtInsetDp and unified frame elevation and rim tokens stay non-zero for ROM and App`() {
        val standard = TopStripMetrics.forWidth(760f)
        assertEquals(8, TopStripMetrics.appArtInsetDp(standard.artSizeDp))
        assertTrue(TopStripMetrics.appArtInsetDp(64) in 6..10)
        assertTrue(TopStripMetrics.appArtInsetDp(100) in 6..10)

        val romElev = TopStripMetrics.artFrameElevationDp(isRom = true, spec = standard)
        val appElev = TopStripMetrics.artFrameElevationDp(isRom = false, spec = standard)
        assertTrue(romElev > 0f)
        assertTrue(appElev > 0f)
        assertEquals(TopStripMetrics.APP_ART_ELEVATION_DP, appElev, 0.01f)

        for (pack in ThemePack.BUILTINS) {
            val rim = TopStripMetrics.artFrameRimColor(pack.accentColor)
            assertTrue(((rim ushr 24) and 0xFF) > 0)
        }
    }

    @Test
    fun `platformDotColor clears WCAG AA contrast across every platform and asyncArtFadeMs gates sync hits`() {
        for (platform in Platforms.ALL) {
            val accent = PlatformLook.accentColor(platform.id)
            for (pack in ThemePack.BUILTINS) {
                val surface = ThemePack.cardFill(pack)
                val dot = TopStripMetrics.platformDotColor(accent, surface)
                val ratio = ThemePack.contrastRatio(dot, surface)
                assertTrue(
                    "Platform dot for ${platform.id} on ${pack.id} must be >= 4.5, was $ratio",
                    ratio >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
        assertEquals(0L, TopStripMetrics.asyncArtFadeMs(syncHit = true, attached = true, animEnabled = true))
        assertEquals(0L, TopStripMetrics.asyncArtFadeMs(syncHit = false, attached = false, animEnabled = true))
        assertEquals(0L, TopStripMetrics.asyncArtFadeMs(syncHit = false, attached = true, animEnabled = false))
        assertEquals(
            TopStripMetrics.ASYNC_ART_FADE_MS,
            TopStripMetrics.asyncArtFadeMs(syncHit = false, attached = true, animEnabled = true),
        )
    }

    @Test
    fun `fontScale adapts vertical padding so four-line strip fits inside 120dp up to 1_25x`() {
        val normal = TopStripMetrics.forWidth(760f, fontScale = 1f)
        val neon = TopStripMetrics.forWidth(760f, fontScale = ThemePack.NEON.fontScale)
        val large = TopStripMetrics.forWidth(760f, fontScale = 1.25f)
        assertEquals(10, normal.padVerticalDp)
        assertEquals(8, neon.padVerticalDp)
        assertEquals(8, large.padVerticalDp)

        for (widthDp in listOf(480f, 640f, 760f, 960f, 1280f)) {
            for (scale in listOf(1.0f, 1.05f, 1.15f, 1.25f)) {
                val spec = TopStripMetrics.forWidth(widthDp, fontScale = scale)
                val est = TopStripMetrics.estimatedTextColumnHeightDp(spec, fontScale = scale)
                assertTrue(
                    "TOP_STRIP text ($est dp) at width=$widthDp scale=$scale must fit <= ${SelectionStrip.STRIP_HEIGHT_DP}dp",
                    est <= SelectionStrip.STRIP_HEIGHT_DP,
                )
            }
        }
    }
}
