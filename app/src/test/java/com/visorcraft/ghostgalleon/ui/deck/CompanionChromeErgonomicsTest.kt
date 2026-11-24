package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionChromeErgonomicsTest {

    @Test
    fun `horizontalScrollTarget skips scrolling when content fits or chip is already visible`() {
        // Content fits cleanly inside usable viewport -> no scroll needed.
        assertNull(
            CompanionChromeErgonomics.horizontalScrollTarget(
                chipLeft = 220,
                chipRight = 300,
                viewportWidth = 540,
                endClearancePx = 148,
                contentWidth = 360,
                currentScrollX = 0,
            ),
        )
        // Content overflows, but leading chip is already visible in [0, 392].
        assertNull(
            CompanionChromeErgonomics.horizontalScrollTarget(
                chipLeft = 0,
                chipRight = 100,
                viewportWidth = 540,
                endClearancePx = 148,
                contentWidth = 500,
                currentScrollX = 0,
            ),
        )
    }

    @Test
    fun `horizontalScrollTarget centers clipped trailing role pill while respecting StatusPill clearance`() {
        // Viewport 540px, StatusPill end clearance 148px -> usable 392px, content 520px, maxScroll 128px.
        val target = CompanionChromeErgonomics.horizontalScrollTarget(
            chipLeft = 420,
            chipRight = 510,
            viewportWidth = 540,
            endClearancePx = 148,
            contentWidth = 520,
            currentScrollX = 0,
        )
        // Center 465 - 196 = 269, clamped to maxScroll = 128 -> reveals chip up to 520 without overlapping StatusPill.
        assertEquals(128, target)
    }

    @Test
    fun `horizontalScrollTarget scrolls left when active chip is behind currentScrollX`() {
        val target = CompanionChromeErgonomics.horizontalScrollTarget(
            chipLeft = 20,
            chipRight = 100,
            viewportWidth = 540,
            endClearancePx = 148,
            contentWidth = 520,
            currentScrollX = 120,
        )
        assertEquals(0, target)
    }

    @Test
    fun `touch inset expands visual pill to 44dp accessible minimum`() {
        assertEquals(
            CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP,
            CompanionChromeErgonomics.CHIP_VISUAL_HEIGHT_DP +
                CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP * 2,
        )
        assertTrue(CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP >= 44)
    }

    @Test
    fun `targetScale prioritizes pressed shrink over focused lift`() {
        assertEquals(
            CompanionChromeErgonomics.CHIP_PRESS_SCALE,
            CompanionChromeErgonomics.targetScale(pressed = true, focused = true),
            0.001f,
        )
        assertEquals(
            CompanionChromeErgonomics.CHIP_FOCUS_SCALE,
            CompanionChromeErgonomics.targetScale(pressed = false, focused = true),
            0.001f,
        )
        assertEquals(
            1f,
            CompanionChromeErgonomics.targetScale(pressed = false, focused = false),
            0.001f,
        )
    }

    @Test
    fun `favoriteChipText prepends star glyph only when active`() {
        assertEquals(
            "★ Unfavorite",
            CompanionChromeErgonomics.favoriteChipText("★", "Unfavorite", isFavorite = true),
        )
        assertEquals(
            "Favorite",
            CompanionChromeErgonomics.favoriteChipText("★", "Favorite", isFavorite = false),
        )
        assertEquals(
            "Unfavorite",
            CompanionChromeErgonomics.favoriteChipText(" ", "Unfavorite", isFavorite = true),
        )
    }

    @Test
    fun `activeChipFill keeps WCAG AA contrast for accent text and gold star across all themes`() {
        val goldStar = 0xFFFFD54F.toInt()
        for (pack in com.visorcraft.ghostgalleon.settings.ThemePack.BUILTINS) {
            val fill = CompanionChromeErgonomics.activeChipFill(pack.chipIdle, pack.accentColor)
            val textAccent = com.visorcraft.ghostgalleon.settings.ThemePack.accentTextColor(
                pack.accentColor,
                fill,
            )
            val accentRatio = com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(textAccent, fill)
            val starRatio = com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(goldStar, fill)
            assertTrue(
                "Active chip accent text on ${pack.id} must be >= 4.5, was $accentRatio",
                accentRatio >= com.visorcraft.ghostgalleon.settings.ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "Active chip gold star on ${pack.id} must be >= 4.5, was $starRatio",
                starRatio >= com.visorcraft.ghostgalleon.settings.ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `resumeSwipeFeedback ignores micro-jitter and vertical drags`() {
        val jitter = CompanionChromeErgonomics.resumeSwipeFeedback(
            dx = 4f,
            dy = 1f,
            dismissSlopPx = 80f,
            touchSlopPx = 8f,
        )
        assertEquals(0f, jitter.translationX, 0.001f)
        assertEquals(1f, jitter.alpha, 0.001f)
        assertEquals(false, jitter.shouldDismiss)

        val vertical = CompanionChromeErgonomics.resumeSwipeFeedback(
            dx = 30f,
            dy = 50f,
            dismissSlopPx = 80f,
        )
        assertEquals(0f, vertical.translationX, 0.001f)
        assertEquals(1f, vertical.alpha, 0.001f)
        assertEquals(false, vertical.shouldDismiss)
    }

    @Test
    fun `resumeSwipeFeedback tracks horizontal drag left and right with proportional fade and dismiss`() {
        val midRight = CompanionChromeErgonomics.resumeSwipeFeedback(
            dx = 40f,
            dy = 4f,
            dismissSlopPx = 80f,
        )
        assertTrue(midRight.translationX > 0f)
        assertTrue(midRight.alpha < 1f && midRight.alpha > CompanionChromeErgonomics.RESUME_SWIPE_MIN_ALPHA)
        assertEquals(false, midRight.shouldDismiss)

        val dismissLeft = CompanionChromeErgonomics.resumeSwipeFeedback(
            dx = -100f,
            dy = 5f,
            dismissSlopPx = 80f,
        )
        assertTrue(dismissLeft.translationX < 0f)
        assertEquals(true, dismissLeft.shouldDismiss)
        assertTrue(dismissLeft.alpha >= CompanionChromeErgonomics.RESUME_SWIPE_MIN_ALPHA)

        val clamped = CompanionChromeErgonomics.resumeSwipeFeedback(
            dx = 999f,
            dy = 0f,
            dismissSlopPx = 80f,
        )
        assertEquals(80f * 2.2f, clamped.translationX, 0.01f)
        assertEquals(CompanionChromeErgonomics.RESUME_SWIPE_MIN_ALPHA, clamped.alpha, 0.001f)
    }

    @Test
    fun `icon button scale and cold-start breathing constants stay bounded`() {
        assertEquals(
            CompanionChromeErgonomics.ICON_PRESS_SCALE,
            CompanionChromeErgonomics.targetScale(
                pressed = true,
                focused = false,
                pressScale = CompanionChromeErgonomics.ICON_PRESS_SCALE,
                focusScale = CompanionChromeErgonomics.ICON_FOCUS_SCALE,
            ),
            0.001f,
        )
        assertEquals(
            CompanionChromeErgonomics.ICON_FOCUS_SCALE,
            CompanionChromeErgonomics.targetScale(
                pressed = false,
                focused = true,
                pressScale = CompanionChromeErgonomics.ICON_PRESS_SCALE,
                focusScale = CompanionChromeErgonomics.ICON_FOCUS_SCALE,
            ),
            0.001f,
        )
        assertTrue(CompanionChromeErgonomics.COLD_START_ALPHA_MIN < CompanionChromeErgonomics.COLD_START_ALPHA_STATIC)
        assertTrue(CompanionChromeErgonomics.COLD_START_ALPHA_STATIC < CompanionChromeErgonomics.COLD_START_ALPHA_MAX)
        val glow = CompanionChromeErgonomics.coldStartGlowColor(0xFF00A8A0.toInt())
        assertEquals(0x24, (glow ushr 24) and 0xFF)
        assertEquals(0x00A8A0, glow and 0x00FFFFFF)
    }
}
