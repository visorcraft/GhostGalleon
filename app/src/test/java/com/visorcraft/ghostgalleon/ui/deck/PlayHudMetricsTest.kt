package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHudMetricsTest {

    @Test
    fun `active and paused session indicators use distinct WCAG AA colors across all themes`() {
        val active = PlayHudMetrics.sessionStatusColor(isActive = true)
        val paused = PlayHudMetrics.sessionStatusColor(isActive = false)
        assertNotEquals(active, paused)
        assertEquals(PlayHudMetrics.SESSION_ACTIVE_COLOR, active)
        assertEquals(PlayHudMetrics.SESSION_PAUSED_COLOR, paused)

        for (pack in ThemePack.BUILTINS) {
            val sunken = ThemePack.surfaceSunken(pack)
            val card = ThemePack.cardFill(pack)
            assertTrue(
                "Active dot on ${pack.id} sunken must be >= 4.5",
                ThemePack.contrastRatio(active, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "Paused dot on ${pack.id} sunken must be >= 4.5",
                ThemePack.contrastRatio(paused, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            for (isActive in listOf(true, false)) {
                val fg = PlayHudMetrics.sessionTimerTextColor(isActive, sunken)
                val opaqueFg = (0xFF shl 24) or (fg and 0x00FFFFFF)
                assertTrue(
                    "Timer text contrast (active=$isActive) on ${pack.id} must be >= 4.5",
                    ThemePack.contrastRatio(opaqueFg, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
                )
                assertTrue(
                    "Timer text contrast on ${pack.id} card must be >= 4.5",
                    ThemePack.contrastRatio(opaqueFg, card) >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `nowPlaying and playHud artwork sizes scale up from compact to standard`() {
        assertTrue(PlayHudMetrics.nowPlayingArtDp(compact = true) < PlayHudMetrics.nowPlayingArtDp(compact = false))
        assertTrue(PlayHudMetrics.playHudArtDp(compact = true) < PlayHudMetrics.playHudArtDp(compact = false))
        assertEquals(0x48, (PlayHudMetrics.sessionPillBorderColor(true) ushr 24) and 0xFF)
    }

    @Test
    fun `headerSpec scales Play HUD typography and padding cleanly from compact to standard`() {
        val compact = PlayHudMetrics.headerSpec(compact = true)
        val standard = PlayHudMetrics.headerSpec(compact = false)
        assertTrue(compact.artDp < standard.artDp)
        assertTrue(compact.titleSp < standard.titleSp)
        assertTrue(compact.playerSp < standard.playerSp)
        assertTrue(compact.padHorizontalDp <= standard.padHorizontalDp)
    }

    @Test
    fun `slotBorderSpec prioritizes pinned gold over latest accent over resting hairline`() {
        val accent = ThemePack.NEON.accentColor
        val hairline = ThemePack.HAIRLINE_LIGHT

        val pinned = PlayHudMetrics.slotBorderSpec(true, true, accent, hairline)
        assertEquals(2, pinned.strokeDp)
        assertEquals(PlayHudMetrics.SLOT_PINNED_GOLD, pinned.color)

        val latest = PlayHudMetrics.slotBorderSpec(false, true, accent, hairline)
        assertEquals(2, latest.strokeDp)
        assertEquals(accent, latest.color)

        val idle = PlayHudMetrics.slotBorderSpec(false, false, accent, hairline)
        assertEquals(1, idle.strokeDp)
        assertEquals(hairline, idle.color)
    }

    @Test
    fun `cinemaPaintKey invalidates when pinned or latest slot changes`() {
        val frames = listOf(com.visorcraft.ghostgalleon.rom.CinemaFrame(9, 1000L, "a.png"))
        val k1 = PlayHudMetrics.cinemaPaintKey(frames, pinnedSlot = null, lastSlot = 9)
        val k2 = PlayHudMetrics.cinemaPaintKey(frames, pinnedSlot = 9, lastSlot = 9)
        val k3 = PlayHudMetrics.cinemaPaintKey(frames, pinnedSlot = 9, lastSlot = 10)
        assertNotEquals(k1, k2)
        assertNotEquals(k2, k3)
        assertTrue(PlayHudMetrics.slotCellSizeDp(compact = true) < PlayHudMetrics.slotCellSizeDp(compact = false))
    }

    @Test
    fun `trackerCellStyle separates active and inactive pills with WCAG AA active contrast`() {
        for (pack in ThemePack.BUILTINS) {
            val sunken = ThemePack.surfaceSunken(pack)
            val hairline = ThemePack.hairlineDivider(pack)
            val on = PlayHudMetrics.trackerCellStyle(
                on = true,
                accentColor = pack.accentColor,
                idleSurface = pack.chipIdle,
                sunkenSurface = sunken,
                hairlineColor = hairline,
            )
            val off = PlayHudMetrics.trackerCellStyle(
                on = false,
                accentColor = pack.accentColor,
                idleSurface = pack.chipIdle,
                sunkenSurface = sunken,
                hairlineColor = hairline,
            )
            assertEquals(1f, on.alpha, 0.001f)
            assertEquals(PlayHudMetrics.TRACKER_OFF_ALPHA, off.alpha, 0.001f)
            assertNotEquals(on.fillColor, off.fillColor)
            val ratio = ThemePack.contrastRatio(on.textColor, on.fillColor)
            assertTrue(
                "Active tracker cell contrast on ${pack.id} must be >= 4.5, was $ratio",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `normalizeTrackpadPoint clamps coordinates and defaults to center on zero bounds`() {
        assertEquals(0.5f to 0.5f, PlayHudMetrics.normalizeTrackpadPoint(10f, 20f, 0, 100))
        assertEquals(0.5f to 0.5f, PlayHudMetrics.normalizeTrackpadPoint(10f, 20f, 100, 0))
        assertEquals(0f to 1f, PlayHudMetrics.normalizeTrackpadPoint(-25f, 300f, 200, 100))
        val (nx, ny) = PlayHudMetrics.normalizeTrackpadPoint(50f, 75f, 200, 100)
        assertEquals(0.25f, nx, 0.001f)
        assertEquals(0.75f, ny, 0.001f)
    }

    @Test
    fun `seatButtonStyle distinguishes pressed state and keeps 44dp minimum touch height`() {
        assertTrue(PlayHudMetrics.seatButtonMinHeightDp(compact = true) >= 44)
        assertTrue(
            PlayHudMetrics.seatButtonMinHeightDp(compact = false) >
                PlayHudMetrics.seatButtonMinHeightDp(compact = true),
        )
        for (pack in ThemePack.BUILTINS) {
            val hairline = ThemePack.hairlineDivider(pack)
            val (idleFill, idleStroke) = PlayHudMetrics.seatButtonStyle(
                pressed = false,
                accentColor = pack.accentColor,
                idleSurface = pack.chipIdle,
                hairlineColor = hairline,
            )
            val (downFill, downStroke) = PlayHudMetrics.seatButtonStyle(
                pressed = true,
                accentColor = pack.accentColor,
                idleSurface = pack.chipIdle,
                hairlineColor = hairline,
            )
            assertEquals(pack.chipIdle, idleFill)
            assertEquals(hairline, idleStroke)
            assertNotEquals(idleFill, downFill)
            assertNotEquals(idleStroke, downStroke)
            assertTrue(
                "Seat button pressed text contrast on ${pack.id} must be >= 4.5",
                ThemePack.contrastRatio(0xFFFFFFFF.toInt(), downFill) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }
}
