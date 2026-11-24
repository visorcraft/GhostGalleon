package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselDepthMetricsTest {

    @Test
    fun `depth tiers fall off monotonically in scale, opacity, lift, and elevation`() {
        val focused = CarouselDepthMetrics.styleFor(
            position = 5, focusedIndex = 5, dockFocused = false, multiSelected = false,
        )
        val near = CarouselDepthMetrics.styleFor(
            position = 4, focusedIndex = 5, dockFocused = false, multiSelected = false,
        )
        val far = CarouselDepthMetrics.styleFor(
            position = 2, focusedIndex = 5, dockFocused = false, multiSelected = false,
        )
        val symmetricNear = CarouselDepthMetrics.styleFor(
            position = 6, focusedIndex = 5, dockFocused = false, multiSelected = false,
        )

        assertEquals(near, symmetricNear)
        assertTrue(focused.scale > near.scale)
        assertTrue(near.scale > far.scale)
        assertTrue(focused.alpha > near.alpha)
        assertTrue(near.alpha > far.alpha)
        assertTrue(focused.translationYDp < near.translationYDp)
        assertEquals(0f, near.translationYDp, 0.001f)
        assertEquals(0f, far.translationYDp, 0.001f)
        assertTrue(focused.translationZDp > near.translationZDp)
        assertTrue(near.translationZDp > far.translationZDp)
    }

    @Test
    fun `dock focus or off-rail selection calms carousel while multi-selected cards remain crisp`() {
        val dockNormal = CarouselDepthMetrics.styleFor(
            position = 5, focusedIndex = 5, dockFocused = true, multiSelected = false,
        )
        val offRail = CarouselDepthMetrics.styleFor(
            position = 0, focusedIndex = null, dockFocused = false, multiSelected = false,
        )
        val dockMulti = CarouselDepthMetrics.styleFor(
            position = 5, focusedIndex = 5, dockFocused = true, multiSelected = true,
        )
        val farMulti = CarouselDepthMetrics.styleFor(
            position = 0, focusedIndex = 5, dockFocused = false, multiSelected = true,
        )

        assertEquals(CarouselDepthMetrics.ALPHA_DOCK_REST, dockNormal.alpha, 0.001f)
        assertEquals(CarouselDepthMetrics.SCALE_DOCK_REST, dockNormal.scale, 0.001f)
        assertEquals(0f, dockNormal.translationYDp, 0.001f)
        assertEquals(dockNormal, offRail)

        assertEquals(CarouselDepthMetrics.ALPHA_FOCUSED, dockMulti.alpha, 0.001f)
        assertEquals(1.0f, dockMulti.scale, 0.001f)
        assertEquals(CarouselDepthMetrics.ALPHA_FOCUSED, farMulti.alpha, 0.001f)
        assertEquals(1.0f, farMulti.scale, 0.001f)
    }

    @Test
    fun `affectedSelectionIndices returns minimal delta indices for single-step and jump moves`() {
        // Adjacent step 5 -> 6 only changes tiers on 4 (1->2), 5 (0->1), 6 (1->0), 7 (2->1).
        assertEquals(
            listOf(4, 5, 6, 7),
            CarouselDepthMetrics.affectedSelectionIndices(previousIndex = 5, nextIndex = 6, itemCount = 20),
        )
        // Edge step 0 -> 1 only affects 0, 1, 2.
        assertEquals(
            listOf(0, 1, 2),
            CarouselDepthMetrics.affectedSelectionIndices(previousIndex = 0, nextIndex = 1, itemCount = 10),
        )
        // Far jump 0 -> 10 affects 0,1 (prev) and 9,10,11 (next).
        assertEquals(
            listOf(0, 1, 9, 10, 11),
            CarouselDepthMetrics.affectedSelectionIndices(previousIndex = 0, nextIndex = 10, itemCount = 15),
        )
        // Identical index or empty list emits zero invalidations.
        assertEquals(
            emptyList<Int>(),
            CarouselDepthMetrics.affectedSelectionIndices(previousIndex = 3, nextIndex = 3, itemCount = 10),
        )
        assertEquals(
            emptyList<Int>(),
            CarouselDepthMetrics.affectedSelectionIndices(previousIndex = 0, nextIndex = 1, itemCount = 0),
        )
        // Initial selection from null only touches next center and ±1 neighbors.
        assertEquals(
            listOf(2, 3, 4),
            CarouselDepthMetrics.affectedSelectionIndices(previousIndex = null, nextIndex = 3, itemCount = 10),
        )
    }

    @Test
    fun `opticalInnerArtRadiusDp preserves concentric corners across all built-in and custom themes`() {
        assertEquals(16, CarouselDepthMetrics.opticalInnerArtRadiusDp(ThemePack.GHOST.cardRadiusDp, 8))
        assertEquals(16, CarouselDepthMetrics.opticalInnerArtRadiusDp(ThemePack.THREEDS.cardRadiusDp, 8))
        assertEquals(16, CarouselDepthMetrics.opticalInnerArtRadiusDp(ThemePack.NEON.cardRadiusDp, 8))
        assertEquals(6, CarouselDepthMetrics.opticalInnerArtRadiusDp(ThemePack.OLED.cardRadiusDp, 8))
        // Custom ultra-sharp themes never return an inner radius larger than outer radius.
        assertEquals(4, CarouselDepthMetrics.opticalInnerArtRadiusDp(4, 8))
        assertEquals(0, CarouselDepthMetrics.opticalInnerArtRadiusDp(0, 8))
    }
}
