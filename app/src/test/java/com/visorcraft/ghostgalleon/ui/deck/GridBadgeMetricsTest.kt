package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridBadgeMetricsTest {

    @Test
    fun `favorite and dock badges never collide in the same corner`() {
        val none = GridBadgeMetrics.placement(isFavorite = false, isPinnedToDock = false)
        assertEquals(GridBadgeMetrics.BadgeCorner.NONE, none.favoriteCorner)
        assertEquals(GridBadgeMetrics.BadgeCorner.NONE, none.dockCorner)

        val favOnly = GridBadgeMetrics.placement(isFavorite = true, isPinnedToDock = false)
        assertEquals(GridBadgeMetrics.BadgeCorner.TOP_END, favOnly.favoriteCorner)
        assertEquals(GridBadgeMetrics.BadgeCorner.NONE, favOnly.dockCorner)

        val dockOnly = GridBadgeMetrics.placement(isFavorite = false, isPinnedToDock = true)
        assertEquals(GridBadgeMetrics.BadgeCorner.NONE, dockOnly.favoriteCorner)
        assertEquals(GridBadgeMetrics.BadgeCorner.TOP_END, dockOnly.dockCorner)

        val both = GridBadgeMetrics.placement(isFavorite = true, isPinnedToDock = true)
        assertEquals(GridBadgeMetrics.BadgeCorner.TOP_END, both.favoriteCorner)
        assertEquals(GridBadgeMetrics.BadgeCorner.TOP_START, both.dockCorner)
        assertNotEquals(both.favoriteCorner, both.dockCorner)
    }

    @Test
    fun `badge metrics adapt optically across Sugar, Odin, OLED, and Ghost`() {
        val sugarOled = GridBadgeMetrics.compute(
            cellWidthPx = 152, // 76dp @ 2.0
            cardRadiusDp = ThemePack.OLED.cardRadiusDp,
            density = 2.0f,
        )
        val odinGhost = GridBadgeMetrics.compute(
            cellWidthPx = 300, // 150dp @ 2.0
            cardRadiusDp = ThemePack.GHOST.cardRadiusDp,
            density = 2.0f,
        )

        assertTrue("Odin badge (${odinGhost.badgeSizePx}) >= Sugar badge (${sugarOled.badgeSizePx})", odinGhost.badgeSizePx >= sugarOled.badgeSizePx)
        assertTrue("Ghost 24dp card inset (${odinGhost.insetPx}) > OLED 12dp inset (${sugarOled.insetPx})", odinGhost.insetPx > sugarOled.insetPx)
        assertTrue(sugarOled.dockDotSizePx < sugarOled.badgeSizePx)
        assertTrue(odinGhost.dockDotSizePx < odinGhost.badgeSizePx)
        assertTrue(sugarOled.starTextSp in 9.5f..12f)
        assertTrue(odinGhost.starTextSp in 9.5f..12f)
    }

    @Test
    fun `favorite star and dock pin dot meet WCAG AA even over pure white box art under scrim`() {
        val worstScrimBg = GridLabelMetrics.compositeOver(
            overlay = GridBadgeMetrics.BADGE_SCRIM_FILL,
            backdrop = 0xFFFFFFFF.toInt(),
        )
        val starContrast = ThemePack.contrastRatio(GridBadgeMetrics.FAVORITE_STAR_COLOR, worstScrimBg)
        assertTrue("favorite star contrast $starContrast < 4.5", starContrast >= ThemePack.MIN_TEXT_CONTRAST)

        for (tokens in ThemePack.BUILTINS) {
            val dotColor = GridBadgeMetrics.dockDotColor(tokens.accentColor)
            val dotContrast = ThemePack.contrastRatio(dotColor, 0xFF000000.toInt())
            assertTrue("theme=${tokens.id} dock dot contrast $dotContrast < 4.5", dotContrast >= ThemePack.MIN_TEXT_CONTRAST)
            val missingLabelContrast = ThemePack.contrastRatio(
                GridBadgeMetrics.MISSING_LABEL_COLOR,
                ThemePack.cardFill(tokens),
            )
            assertTrue("theme=${tokens.id} missing label contrast $missingLabelContrast < 4.5", missingLabelContrast >= ThemePack.MIN_TEXT_CONTRAST)
        }
    }
}
