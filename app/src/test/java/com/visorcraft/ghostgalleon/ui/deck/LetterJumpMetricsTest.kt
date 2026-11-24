package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterJumpMetricsTest {

    @Test
    fun `countSuffixRange splits leading letter from count suffix cleanly`() {
        assertEquals(1 until 6, LetterJumpMetrics.countSuffixRange("A · 12", 'A'))
        assertEquals(1 until 5, LetterJumpMetrics.countSuffixRange("# · 3", '#'))
        assertEquals(1 until 5, LetterJumpMetrics.countSuffixRange("m · 9", 'M'))
        assertNull(LetterJumpMetrics.countSuffixRange("A", 'A'))
        assertNull(LetterJumpMetrics.countSuffixRange("", 'A'))
        assertNull(LetterJumpMetrics.countSuffixRange("B · 2", 'A'))
    }

    @Test
    fun `countTextColor adapts to dark and light theme accents while muting idle chips`() {
        assertEquals(
            UiDimens.ALPHA_MUTED,
            LetterJumpMetrics.countTextColor(selected = false, accentColor = ThemePack.GHOST.accentColor),
        )
        // Dark accents (Ghost/Teal/Neon) keep white-tinted secondary count.
        val ghostCount = LetterJumpMetrics.countTextColor(selected = true, accentColor = ThemePack.GHOST.accentColor)
        assertEquals(0xCCFFFFFF.toInt(), ghostCount)
        val neonCount = LetterJumpMetrics.countTextColor(selected = true, accentColor = ThemePack.NEON.accentColor)
        assertEquals(0xCCFFFFFF.toInt(), neonCount)
        // Light OLED accent (#E0E0E0) switches to black-tinted secondary count.
        val oledCount = LetterJumpMetrics.countTextColor(selected = true, accentColor = ThemePack.OLED.accentColor)
        assertEquals(0xCC000000.toInt(), oledCount)
    }

    @Test
    fun `letterChipAccessibilityText reflects jump target and active state`() {
        val base = text(R.string.format_jump_to_letter, "M", 14)
        assertEquals(
            base,
            LetterJumpMetrics.letterChipAccessibilityText('M', 14, selected = false),
        )
        assertEquals(
            text(R.string.format_selected_check, base),
            LetterJumpMetrics.letterChipAccessibilityText('M', 14, selected = true),
        )
    }

    @Test
    fun `adjacentLetter clamps at edges and handles empty or unknown current letter`() {
        val letters = listOf('#', 'A', 'M', 'Z')
        assertEquals('#', LetterJumpMetrics.adjacentLetter(letters, '#', -1))
        assertEquals('A', LetterJumpMetrics.adjacentLetter(letters, '#', 1))
        assertEquals('Z', LetterJumpMetrics.adjacentLetter(letters, 'Z', 1))
        assertEquals('M', LetterJumpMetrics.adjacentLetter(letters, 'Z', -1))
        assertEquals('#', LetterJumpMetrics.adjacentLetter(letters, null, 1))
        assertEquals('Z', LetterJumpMetrics.adjacentLetter(letters, null, -1))
        assertNull(LetterJumpMetrics.adjacentLetter(emptyList(), 'A', 1))
    }

    @Test
    fun `responsive horizontal padding, ripple, and WCAG focus strokes adapt across all themes`() {
        assertEquals(7, LetterJumpMetrics.chipHorizontalPaddingDp(viewportWidthDp = 540))
        assertEquals(7, LetterJumpMetrics.chipHorizontalPaddingDp(viewportWidthDp = 600))
        assertEquals(10, LetterJumpMetrics.chipHorizontalPaddingDp(viewportWidthDp = 860))
        assertEquals(10, LetterJumpMetrics.chipHorizontalPaddingDp(viewportWidthDp = 960))

        for (pack in ThemePack.BUILTINS) {
            val idleStroke = LetterJumpMetrics.focusStrokeColor(
                selected = false,
                accentColor = pack.accentColor,
                chipIdle = pack.chipIdle,
            )
            val idleRatio = ThemePack.contrastRatio(idleStroke, pack.chipIdle)
            assertTrue(
                "Idle letter focus stroke contrast too low ($idleRatio) on ${pack.id}",
                idleRatio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
            )
            val selectedStroke = LetterJumpMetrics.focusStrokeColor(
                selected = true,
                accentColor = pack.accentColor,
                chipIdle = pack.chipIdle,
            )
            val selectedRatio = ThemePack.contrastRatio(selectedStroke, pack.accentColor)
            assertTrue(
                "Selected letter focus stroke contrast too low ($selectedRatio) on ${pack.id}",
                selectedRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertEquals(
                BrowseChipStyleLogic.rippleColor(selected = false, accentColor = pack.accentColor),
                LetterJumpMetrics.rippleColor(selected = false, accentColor = pack.accentColor),
            )
            assertEquals(
                BrowseChipStyleLogic.rippleColor(selected = true, accentColor = pack.accentColor),
                LetterJumpMetrics.rippleColor(selected = true, accentColor = pack.accentColor),
            )
        }
    }
}
