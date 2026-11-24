package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPrimitivesTest {

    @Test
    fun `responsiveCardWidthPx caps to maxDp, widthFrac, and horizontal margins`() {
        // Large 1920px @ 2x (960dp): capped to maxDp 440dp = 880px.
        assertEquals(
            880,
            OverlayPrimitives.responsiveCardWidthPx(
                screenWidthPx = 1920,
                density = 2f,
                maxDp = 440,
                widthFrac = 0.88f,
                minMarginDp = 16,
            ),
        )
        // Compact 720px @ 2x (360dp): 88% is 633px, margin cap is 656px -> 633px.
        assertEquals(
            633,
            OverlayPrimitives.responsiveCardWidthPx(
                screenWidthPx = 720,
                density = 2f,
                maxDp = 440,
                widthFrac = 0.88f,
                minMarginDp = 16,
            ),
        )
        // Very narrow screen respects margins and stays >= 1px.
        assertEquals(
            1,
            OverlayPrimitives.responsiveCardWidthPx(
                screenWidthPx = 10,
                density = 2f,
                maxDp = 440,
                widthFrac = 0.90f,
                minMarginDp = 16,
            ),
        )
    }

    @Test
    fun `maxModalHeightPx honors minDp without ever exceeding screen height`() {
        // Short Sugar bottom panel (~540dp @ 2x = 1080px): 72% = 777px.
        assertEquals(
            777,
            OverlayPrimitives.maxModalHeightPx(
                screenHeightPx = 1080,
                density = 2f,
                heightFrac = 0.72f,
                minDp = 180,
            ),
        )
        // Very short viewport enforces minDp when screen allows, clamps at screenHeightPx.
        assertEquals(
            360,
            OverlayPrimitives.maxModalHeightPx(
                screenHeightPx = 400,
                density = 2f,
                heightFrac = 0.50f,
                minDp = 180,
            ),
        )
        assertEquals(
            240,
            OverlayPrimitives.maxModalHeightPx(
                screenHeightPx = 240,
                density = 2f,
                heightFrac = 0.50f,
                minDp = 180,
            ),
        )
    }

    @Test
    fun `fadingEdgeLengthPx scales with density and rejects negative values`() {
        assertEquals(40, OverlayPrimitives.fadingEdgeLengthPx(density = 2f))
        assertEquals(60, OverlayPrimitives.fadingEdgeLengthPx(density = 3f))
        assertEquals(0, OverlayPrimitives.fadingEdgeLengthPx(density = 2f, edgeDp = -8))
    }

    @Test
    fun `scrollCenterTargetY centers focused row and clamps to valid scroll bounds`() {
        // Item near top stays at 0.
        assertEquals(
            0,
            OverlayPrimitives.scrollCenterTargetY(
                itemTop = 20,
                itemHeight = 48,
                viewportHeight = 400,
                contentHeight = 1200,
            ),
        )
        // Item in middle centers cleanly: 500 - (400 - 48) / 2 = 324.
        assertEquals(
            324,
            OverlayPrimitives.scrollCenterTargetY(
                itemTop = 500,
                itemHeight = 48,
                viewportHeight = 400,
                contentHeight = 1200,
            ),
        )
        // Item near bottom clamps at maxScroll = 1200 - 400 = 800.
        assertEquals(
            800,
            OverlayPrimitives.scrollCenterTargetY(
                itemTop = 1100,
                itemHeight = 48,
                viewportHeight = 400,
                contentHeight = 1200,
            ),
        )
        // Content shorter than viewport always yields 0.
        assertEquals(
            0,
            OverlayPrimitives.scrollCenterTargetY(
                itemTop = 150,
                itemHeight = 48,
                viewportHeight = 400,
                contentHeight = 300,
            ),
        )
        // Zero viewport safely returns 0.
        assertEquals(
            0,
            OverlayPrimitives.scrollCenterTargetY(
                itemTop = 200,
                itemHeight = 48,
                viewportHeight = 0,
            ),
        )
    }

    @Test
    fun `entrance choreography constants and helpers behave safely`() {
        assertEquals(16, OverlayPrimitives.MODAL_ELEVATION_DP)
        assertEquals(120L, OverlayPrimitives.ENTER_DURATION_MS)
        assertEquals(0.96f, OverlayPrimitives.ENTER_START_SCALE, 0.0001f)

        assertTrue(OverlayPrimitives.shouldAnimateEntrance(animatorsEnabled = true, skipAnimation = false))
        assertFalse(OverlayPrimitives.shouldAnimateEntrance(animatorsEnabled = false, skipAnimation = false))
        assertFalse(OverlayPrimitives.shouldAnimateEntrance(animatorsEnabled = true, skipAnimation = true))

        assertEquals(0.96f, OverlayPrimitives.entranceScale(0f), 0.0001f)
        assertEquals(0.98f, OverlayPrimitives.entranceScale(0.5f), 0.0001f)
        assertEquals(1.0f, OverlayPrimitives.entranceScale(1f), 0.0001f)
        assertEquals(0.96f, OverlayPrimitives.entranceScale(-1f), 0.0001f)
        assertEquals(1.0f, OverlayPrimitives.entranceScale(2f), 0.0001f)
    }
}
