package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMotionAndContrastTest {

    @Test
    fun `DeckMotionPolicy gates transitions and continuous pulses cleanly`() {
        assertTrue(DeckMotionPolicy.shouldAnimate(animatorsEnabled = true, isLaidOut = true))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = true, isLaidOut = false))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = false, isLaidOut = true))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = false, isLaidOut = false))

        assertTrue(DeckMotionPolicy.shouldPulse(animatorsEnabled = true))
        assertFalse(DeckMotionPolicy.shouldPulse(animatorsEnabled = false))
    }

    @Test
    fun `accessibleFocusStroke preserves passing accents and lifts dark accents to WCAG 1_4_11`() {
        // Teal, OLED, and Neon accents already exceed 3.0:1 over their card fill -> returned unchanged.
        for (tokens in listOf(ThemePack.THREEDS, ThemePack.OLED, ThemePack.NEON)) {
            val fill = ThemePack.cardFill(tokens)
            assertEquals(
                "expected ${tokens.id} accent untouched",
                tokens.accentColor,
                DeckThemeContrast.accessibleFocusStroke(tokens.accentColor, fill),
            )
        }

        // Ghost indigo (#3F51B5) + adversarial dark accents must all clear >= 3.0:1.
        val testAccents = ThemePack.BUILTINS.map { it.accentColor } + listOf(
            0xFF0D1B4C.toInt(), // deep navy
            0xFF2B0033.toInt(), // deep plum
            0xFF000000.toInt(), // pure black
            0xFFFFFFFF.toInt(), // pure white
        )
        for (tokens in ThemePack.BUILTINS) {
            val fill = ThemePack.cardFill(tokens)
            for (accent in testAccents) {
                val stroke = DeckThemeContrast.accessibleFocusStroke(accent, fill)
                val ratio = ThemePack.contrastRatio(stroke, fill)
                assertTrue(
                    "theme=${tokens.id} accent=${accent.toUInt().toString(16)} ratio=$ratio < 3.0",
                    ratio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `accessibleFocusStroke darkens light accents on custom light surfaces`() {
        val lightSurface = 0xFFF2F2F7.toInt()
        val stroke = DeckThemeContrast.accessibleFocusStroke(0xFFE0E0E0.toInt(), lightSurface)
        val ratio = ThemePack.contrastRatio(stroke, lightSurface)
        assertTrue("light surface focus ratio $ratio < 3.0", ratio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST)
    }
}
