package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerMatchTest {

    @Test
    fun `findMatchSpans locates case-insensitive and repeated non-overlapping occurrences`() {
        assertEquals(
            listOf(MatchSpan(6, 11)),
            AppPickerMatch.findMatchSpans("Super Mario World", "mario"),
        )
        assertEquals(
            listOf(MatchSpan(0, 2), MatchSpan(3, 5)),
            AppPickerMatch.findMatchSpans("ha-ha", "HA"),
        )
        // Non-overlapping: "aa" in "aaa" matches once at [0, 2).
        assertEquals(
            listOf(MatchSpan(0, 2)),
            AppPickerMatch.findMatchSpans("aaa", "aa"),
        )
    }

    @Test
    fun `findMatchSpans handles blank queries, trimming, and regex metacharacters safely`() {
        assertTrue(AppPickerMatch.findMatchSpans("Super Metroid", "").isEmpty())
        assertTrue(AppPickerMatch.findMatchSpans("Super Metroid", "   ").isEmpty())
        assertTrue(AppPickerMatch.findMatchSpans("", "zelda").isEmpty())
        assertEquals(
            listOf(MatchSpan(0, 5)),
            AppPickerMatch.findMatchSpans("Zelda", "  zelda  "),
        )
        assertEquals(
            listOf(MatchSpan(6, 11)),
            AppPickerMatch.findMatchSpans("Hades (USA) [Rev 1]", "(usa)"),
        )
    }

    @Test
    fun `hasQueryChanged ignores surrounding whitespace and detects actual query changes`() {
        assertFalse(AppPickerMatch.hasQueryChanged("", "   "))
        assertFalse(AppPickerMatch.hasQueryChanged("mario", "  mario "))
        assertTrue(AppPickerMatch.hasQueryChanged("", "m"))
        assertTrue(AppPickerMatch.hasQueryChanged("mar", "mario"))
        assertTrue(AppPickerMatch.hasQueryChanged("mario", ""))
    }

    @Test
    fun `canClearFromEmptyState is true only when zero results and query is non-blank`() {
        assertTrue(AppPickerMatch.canClearFromEmptyState(selectableCount = 0, query = "xyz"))
        assertFalse(AppPickerMatch.canClearFromEmptyState(selectableCount = 0, query = ""))
        assertFalse(AppPickerMatch.canClearFromEmptyState(selectableCount = 0, query = "   "))
        assertFalse(AppPickerMatch.canClearFromEmptyState(selectableCount = 3, query = "mario"))
    }

    @Test
    fun `title and badge highlight colors meet WCAG AA contrast across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val titleColor = AppPickerMatch.titleHighlightColor(tokens.accentColor, tokens)
            val titleRatio = ThemePack.contrastRatio(titleColor, ThemePack.cardFill(tokens))
            assertTrue(
                "Low title highlight contrast $titleRatio on theme=${tokens.id}",
                titleRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val badgeColor = AppPickerMatch.badgeHighlightColor(tokens.accentColor, tokens)
            val badgeRatio = ThemePack.contrastRatio(badgeColor, ThemePack.surfaceSunken(tokens))
            assertTrue(
                "Low badge highlight contrast $badgeRatio on theme=${tokens.id}",
                badgeRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }
}
