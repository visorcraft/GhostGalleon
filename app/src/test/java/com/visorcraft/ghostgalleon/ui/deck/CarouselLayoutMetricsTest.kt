package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
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

    @Test
    fun `cardTypography unlocks uniform 2-line titles on Odin and stays compact 1-line on Sugar`() {
        // Sugar (~540dp height): stays compact 1-line with aligned minLines == maxLines.
        val sugar = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 180,
            viewportHeightDp = 540,
            fontScale = ThemePack.GHOST.fontScale,
        )
        assertEquals(1, sugar.titleMaxLines)
        assertEquals(1, sugar.titleMinLines)
        assertEquals(14.0f, sugar.titleSp, 0.001f)
        assertEquals(11.0f, sugar.metaSp, 0.001f)

        // Odin 2 Portal roomy card (>= 148dp & >= 560dp height): unlocks uniform 2-line titles.
        val odin = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 200,
            viewportHeightDp = 600,
            fontScale = ThemePack.GHOST.fontScale,
        )
        assertEquals(2, odin.titleMaxLines)
        assertEquals(2, odin.titleMinLines)
        assertTrue(odin.estimatedTextHeightDp > sugar.estimatedTextHeightDp)

        // Small card (< 148dp) stays 1-line even on tall viewports.
        val smallCard = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 120,
            viewportHeightDp = 720,
            fontScale = 1f,
        )
        assertEquals(1, smallCard.titleMaxLines)
        assertEquals(1, smallCard.titleMinLines)
        assertTrue(smallCard.titleSp < odin.titleSp)

        // NEON (fontScale 1.05f) and custom fontScales (0.85f..1.30f) scale cleanly within bounds.
        val neon = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 200,
            viewportHeightDp = 600,
            fontScale = ThemePack.NEON.fontScale,
        )
        assertTrue("NEON titleSp (${neon.titleSp}) should exceed Ghost (${odin.titleSp})", neon.titleSp > odin.titleSp)
        assertTrue("NEON metaSp (${neon.metaSp}) should exceed Ghost (${odin.metaSp})", neon.metaSp > odin.metaSp)

        val customCompact = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 200,
            viewportHeightDp = 600,
            fontScale = 0.85f,
        )
        val customLarge = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 200,
            viewportHeightDp = 600,
            fontScale = 1.30f,
        )
        val clampedExtreme = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = 200,
            viewportHeightDp = 600,
            fontScale = 2.5f,
        )
        assertTrue(customCompact.titleSp in CarouselLayoutMetrics.MIN_TITLE_SP..odin.titleSp)
        assertTrue(customLarge.titleSp > neon.titleSp)
        assertTrue(customLarge.titleSp <= CarouselLayoutMetrics.MAX_TITLE_SP)
        assertEquals(customLarge.titleSp, clampedExtreme.titleSp, 0.001f)
        assertEquals(customLarge.metaSp, clampedExtreme.metaSp, 0.001f)
    }

    @Test
    fun `verticalChromeReserveDp keeps focused 1_10x card plus title and worst-case chrome within 540dp Sugar`() {
        val baseline = CarouselLayoutMetrics.verticalChromeReserveDp(
            hasMultiSelect = false,
            hasActiveFilters = false,
            hasLetterJump = false,
            showHints = true,
        )
        assertEquals(CarouselLayoutMetrics.CHROME_VERTICAL_RESERVE_DP, baseline)

        val noHints = CarouselLayoutMetrics.verticalChromeReserveDp(
            hasMultiSelect = false,
            hasActiveFilters = false,
            hasLetterJump = false,
            showHints = false,
        )
        assertTrue(noHints < baseline)

        val worstChrome = CarouselLayoutMetrics.verticalChromeReserveDp(
            hasMultiSelect = true,
            hasActiveFilters = true,
            hasLetterJump = true,
            showHints = true,
        )
        assertTrue(worstChrome > baseline)

        // Worst-case active chrome stack on 540dp Sugar (normal and elevated custom fontScale).
        for (scale in listOf(1.0f, ThemePack.NEON.fontScale, 1.30f)) {
            val initialSize = CarouselLayoutMetrics.effectiveCardSizeDp(
                requestedCardSizeDp = 200,
                viewportWidthDp = 540,
                viewportHeightDp = 540,
                chromeVerticalReserveDp = worstChrome,
            )
            val initialTypo = CarouselLayoutMetrics.cardTypography(
                cardSizeDp = initialSize,
                viewportHeightDp = 540,
                fontScale = scale,
            )
            val effectiveSize = CarouselLayoutMetrics.effectiveCardSizeDp(
                requestedCardSizeDp = 200,
                viewportWidthDp = 540,
                viewportHeightDp = 540,
                chromeVerticalReserveDp = worstChrome,
                cardTextHeightDp = initialTypo.estimatedTextHeightDp,
            )
            val finalTypo = CarouselLayoutMetrics.cardTypography(
                cardSizeDp = effectiveSize,
                viewportHeightDp = 540,
                fontScale = scale,
            )
            val totalHeightDp = effectiveSize * CarouselDepthMetrics.SCALE_FOCUSED +
                finalTypo.estimatedTextHeightDp +
                worstChrome
            assertTrue(
                "Worst-case Sugar height ($totalHeightDp dp at scale=$scale, card=$effectiveSize) exceeded 540dp",
                totalHeightDp <= 540f,
            )
            assertTrue(effectiveSize >= CarouselLayoutMetrics.MIN_CARD_SIZE_DP)
        }

        // Smooth 2-phase edge bump duration matches 140ms budget.
        assertEquals(140L, CarouselLayoutMetrics.EDGE_BUMP_MS)
    }
}
