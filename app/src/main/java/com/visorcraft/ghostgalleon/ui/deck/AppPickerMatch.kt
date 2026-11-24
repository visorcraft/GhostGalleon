package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens

/** Pure character index range `[start, endExclusive)` for highlighted query matches. */
internal data class MatchSpan(
    val start: Int,
    val endExclusive: Int,
)

/**
 * Pure, host-tested search-match span finder and empty-state/contrast logic
 * for [AppPicker]. Uses plain case-insensitive substring scanning (no regex)
 * so 6,500+ ROM lists stay allocation-light and safe against special characters.
 */
internal object AppPickerMatch {

    /**
     * Find all non-overlapping case-insensitive occurrences of trimmed [query]
     * inside [text]. Returns an empty list for blank queries or non-matching text.
     */
    fun findMatchSpans(text: String, query: String): List<MatchSpan> {
        val needle = query.trim()
        if (text.isEmpty() || needle.isEmpty()) return emptyList()
        val out = mutableListOf<MatchSpan>()
        var cursor = 0
        while (cursor <= text.length - needle.length) {
            val idx = text.indexOf(needle, startIndex = cursor, ignoreCase = true)
            if (idx < 0) break
            val end = (idx + needle.length).coerceAtMost(text.length)
            if (end > idx) {
                out.add(MatchSpan(idx, end))
            }
            cursor = end
        }
        return out
    }

    /** True when trimmed query text changed and surviving rows must refresh match spans. */
    fun hasQueryChanged(previousQuery: String, nextQuery: String): Boolean =
        previousQuery.trim() != nextQuery.trim()

    /** True when the picker has zero results due to an active search query. */
    fun canClearFromEmptyState(selectableCount: Int, query: String): Boolean =
        selectableCount <= 0 && query.isNotBlank()

    /** WCAG-AA accent text color for matched substrings on the picker card surface. */
    fun titleHighlightColor(accent: Int, tokens: ThemeTokens): Int =
        ThemePack.accentTextColor(accent, ThemePack.cardFill(tokens))

    /** WCAG-AA accent text color for matched substrings inside ROM platform pills. */
    fun badgeHighlightColor(accent: Int, tokens: ThemeTokens): Int =
        ThemePack.accentTextColor(accent, ThemePack.surfaceSunken(tokens))
}
