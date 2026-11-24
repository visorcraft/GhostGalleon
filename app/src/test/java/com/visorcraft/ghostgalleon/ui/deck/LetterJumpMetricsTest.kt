package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
