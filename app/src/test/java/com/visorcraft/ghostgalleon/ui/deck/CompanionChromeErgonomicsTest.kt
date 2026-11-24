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
}
