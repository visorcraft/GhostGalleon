package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
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

    @Test
    fun `scrollCueAlphas and scrollCueTint stay translucent and contrast-lifted across themes`() {
        assertEquals(0f to 0f, OverlayPrimitives.scrollCueAlphas(false, false))
        assertEquals(0f to 1f, OverlayPrimitives.scrollCueAlphas(false, true))
        assertEquals(1f to 0f, OverlayPrimitives.scrollCueAlphas(true, false))
        assertEquals(1f to 1f, OverlayPrimitives.scrollCueAlphas(true, true))

        for (tokens in ThemePack.BUILTINS) {
            val surface = ThemePack.cardFill(tokens)
            val tint = OverlayPrimitives.scrollCueTint(tokens.accentColor, surface)
            assertEquals(0x66, (tint ushr 24) and 0xFF)
            val opaqueRgb = (0xFF shl 24) or (tint and 0x00FFFFFF)
            assertTrue(
                "Scroll cue RGB should be WCAG-lifted on ${tokens.id}",
                ThemePack.contrastRatio(opaqueRgb, surface) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `safeBottomInsetPx returns zero in immersive fullscreen and pads cleanly when IME opens`() {
        assertEquals(0, OverlayPrimitives.safeBottomInsetPx(imeBottomPx = 0, navBarBottomPx = 0, extraMarginPx = 16))
        assertEquals(416, OverlayPrimitives.safeBottomInsetPx(imeBottomPx = 400, navBarBottomPx = 48, extraMarginPx = 16))
        assertEquals(64, OverlayPrimitives.safeBottomInsetPx(imeBottomPx = 0, navBarBottomPx = 48, extraMarginPx = 16))
        assertEquals(0, OverlayPrimitives.safeBottomInsetPx(imeBottomPx = -10, navBarBottomPx = -5, extraMarginPx = 16))
    }

    @Test
    fun `imeAwareCardHeightPx shrinks tall drawers cleanly above soft keyboard and restores when closed`() {
        // 1080px screen, 0.88f drawer -> 950px with keyboard closed.
        assertEquals(
            950,
            OverlayPrimitives.imeAwareCardHeightPx(
                screenHeightPx = 1080,
                heightFrac = 0.88f,
                bottomInsetPx = 0,
                topMarginPx = 32,
                minHeightPx = 440,
            ),
        )
        // With 480px IME open: available = 1080 - 480 - 64 = 536px (< 950px desired).
        assertEquals(
            536,
            OverlayPrimitives.imeAwareCardHeightPx(
                screenHeightPx = 1080,
                heightFrac = 0.88f,
                bottomInsetPx = 480,
                topMarginPx = 32,
                minHeightPx = 440,
            ),
        )
        // Very tight Sugar panel with large IME clamps safely to available space (>= 1).
        assertEquals(
            180,
            OverlayPrimitives.imeAwareCardHeightPx(
                screenHeightPx = 540,
                heightFrac = 0.88f,
                bottomInsetPx = 300,
                topMarginPx = 30,
                minHeightPx = 360,
            ),
        )
    }
}
