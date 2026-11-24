package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLabelMetricsTest {

    @Test
    fun `disabled labels reserve zero height and no scrim`() {
        val spec = GridLabelMetrics.compute(
            cellWidthDp = 140,
            cellHeightDp = 160,
            iconSizeDp = 80,
            showLabels = false,
            hasWallpaper = true,
        )
        assertEquals(0, spec.reservedHeightDp)
        assertEquals(0, spec.topMarginDp)
        assertFalse(spec.useReadabilityScrim)
    }

    @Test
    fun `roomy Odin cells unlock 2-line titles while compact Sugar cells stay crisp 1-line`() {
        // Odin 2 Portal roomy cell: 144dp x 156dp with 80dp icon -> 60dp slack -> 2 lines.
        val roomy = GridLabelMetrics.compute(
            cellWidthDp = 144,
            cellHeightDp = 156,
            iconSizeDp = 80,
            showLabels = true,
            hasWallpaper = false,
        )
        assertEquals(2, roomy.maxLines)
        assertTrue(roomy.textSp in 12f..13f)
        assertTrue(roomy.reservedHeightDp >= 34)

        // Sugar compact cell: 74dp x 92dp with 52dp icon -> 24dp slack -> 1 line at 11sp.
        val compact = GridLabelMetrics.compute(
            cellWidthDp = 74,
            cellHeightDp = 92,
            iconSizeDp = 52,
            showLabels = true,
            hasWallpaper = false,
        )
        assertEquals(1, compact.maxLines)
        assertEquals(11f, compact.textSp, 0.01f)
        assertEquals(22, compact.reservedHeightDp)
    }

    @Test
    fun `fontScale stays clamped within legible sp bounds across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val spec = GridLabelMetrics.compute(
                cellWidthDp = 130,
                cellHeightDp = 150,
                iconSizeDp = 76,
                showLabels = true,
                hasWallpaper = false,
                fontScale = tokens.fontScale,
            )
            assertTrue(
                "theme=${tokens.id} textSp=${spec.textSp} out of range",
                spec.textSp in GridLabelMetrics.MIN_TEXT_SP..GridLabelMetrics.MAX_TEXT_SP,
            )
        }
    }

    @Test
    fun `wallpaper label scrim keeps white text WCAG AA even over pure white backdrop`() {
        val worstCaseBg = GridLabelMetrics.compositeOver(
            overlay = GridLabelMetrics.WALLPAPER_SCRIM_FILL,
            backdrop = 0xFFFFFFFF.toInt(),
        )
        val ratio = ThemePack.contrastRatio(UiDimens.ALPHA_PRIMARY, worstCaseBg)
        assertTrue("expected >= 4.5:1 over pure white wallpaper scrim, got $ratio", ratio >= ThemePack.MIN_TEXT_CONTRAST)

        val withWallpaper = GridLabelMetrics.compute(
            cellWidthDp = 120,
            cellHeightDp = 150,
            iconSizeDp = 72,
            showLabels = true,
            hasWallpaper = true,
        )
        assertTrue(withWallpaper.useReadabilityScrim)
    }

    @Test
    fun `focus and move roles emphasize selected label and calm siblings`() {
        val focused = GridLabelMetrics.labelStyleForRole(MoveChoreography.SlotRole.FOCUSED)
        val lifted = GridLabelMetrics.labelStyleForRole(MoveChoreography.SlotRole.LIFTED)
        val normal = GridLabelMetrics.labelStyleForRole(MoveChoreography.SlotRole.NORMAL)
        val drop = GridLabelMetrics.labelStyleForRole(MoveChoreography.SlotRole.DROP_TARGET)
        val missingFocused = GridLabelMetrics.labelStyleForRole(MoveChoreography.SlotRole.FOCUSED, isMissing = true)

        assertTrue(focused.bold)
        assertEquals(UiDimens.ALPHA_PRIMARY, focused.textColor)
        assertTrue(lifted.bold)
        assertFalse(normal.bold)
        assertFalse(drop.bold)
        assertFalse(missingFocused.bold)
    }
}
