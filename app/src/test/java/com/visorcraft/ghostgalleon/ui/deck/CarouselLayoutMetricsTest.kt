package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselLayoutMetricsTest {

    @Test
    fun `effectiveCardSizeDp preserves 200dp default on Sugar and Odin while clamping extreme sizes on short panels`() {
        // Sugar compact panel (540dp x 480dp): 200dp fits untouched.
        assertEquals(
            200,
            CarouselLayoutMetrics.effectiveCardSizeDp(200, viewportWidthDp = 540, viewportHeightDp = 480),
        )
        // Odin 2 Portal 16:9 landscape (860dp x 480dp): 200dp fits untouched.
        assertEquals(
            200,
            CarouselLayoutMetrics.effectiveCardSizeDp(200, viewportWidthDp = 860, viewportHeightDp = 480),
        )
        // Ultra-short landscape (720dp x 360dp): clamps 200dp cleanly so labels and 1.10x lift fit.
        val shortClamped = CarouselLayoutMetrics.effectiveCardSizeDp(200, viewportWidthDp = 720, viewportHeightDp = 360)
        assertTrue("Expected short-panel clamp below 200dp, got $shortClamped", shortClamped in 120..165)

        // Tiny requested size respects minimum floor.
        assertEquals(
            CarouselLayoutMetrics.MIN_CARD_SIZE_DP,
            CarouselLayoutMetrics.effectiveCardSizeDp(40, viewportWidthDp = 540, viewportHeightDp = 540),
        )
    }

    @Test
    fun `cardSpacingDp scales cleanly from compact to widescreen`() {
        assertEquals(10, CarouselLayoutMetrics.cardSpacingDp(480))
        assertEquals(10, CarouselLayoutMetrics.cardSpacingDp(540))
        assertEquals(14, CarouselLayoutMetrics.cardSpacingDp(720))
        assertEquals(18, CarouselLayoutMetrics.cardSpacingDp(960))
    }

    @Test
    fun `horizontalEdgePaddingDp centers first and last cards and centerScrollOffsetPx accounts for padding`() {
        val sugarPad = CarouselLayoutMetrics.horizontalEdgePaddingDp(viewportWidthDp = 540, cardWidthDp = 216)
        assertEquals(162, sugarPad)
        val odinPad = CarouselLayoutMetrics.horizontalEdgePaddingDp(viewportWidthDp = 860, cardWidthDp = 216)
        assertEquals(322, odinPad)
        // Very wide card falls back to MIN_EDGE_PAD_DP.
        assertEquals(
            CarouselLayoutMetrics.MIN_EDGE_PAD_DP,
            CarouselLayoutMetrics.horizontalEdgePaddingDp(viewportWidthDp = 300, cardWidthDp = 290),
        )

        // When symmetric edge padding already equals (viewport - cardWidth) / 2, scroll offset is 0.
        assertEquals(
            0,
            CarouselLayoutMetrics.centerScrollOffsetPx(
                viewportWidthPx = 1080,
                paddingStartPx = 324,
                paddingEndPx = 324,
                cardWidthPx = 432,
            ),
        )
        // When padding is minimal (32px), offset centers the card inside the remaining inner span.
        assertEquals(
            292,
            CarouselLayoutMetrics.centerScrollOffsetPx(
                viewportWidthPx = 1080,
                paddingStartPx = 32,
                paddingEndPx = 32,
                cardWidthPx = 432,
            ),
        )
    }
}
