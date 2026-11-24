package com.visorcraft.ghostgalleon.ui.deck

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens

/** Pure character index range `[start, endExclusive)` for highlighted query matches. */
internal data class MatchSpan(
    val start: Int,
    val endExclusive: Int,
)

/** Per-section item totals for live [AppPicker] header badges. */
internal data class PickerSectionCounts(
    val apps: Int,
    val roms: Int,
) {
    fun countFor(section: PickerItem.Header.Section): Int = when (section) {
        PickerItem.Header.Section.APPS -> apps
        PickerItem.Header.Section.ROMS -> roms
    }
}

/**
 * Pure, host-tested search-match span finder, section counts, IME launch, and
 * empty-state/contrast logic for [AppPicker]. Uses plain case-insensitive
 * substring scanning (no regex) so 6,500+ ROM lists stay allocation-light and
 * safe against special characters.
 */
internal object AppPickerMatch {

    /** Count visible apps and ROMs in [items] in a single allocation-free pass. */
    fun sectionCounts(items: List<PickerItem>): PickerSectionCounts {
        var apps = 0
        var roms = 0
        for (item in items) {
            when (item) {
                is PickerItem.App -> apps++
                is PickerItem.Rom -> roms++
                is PickerItem.Header -> Unit
            }
        }
        return PickerSectionCounts(apps = apps, roms = roms)
    }

    /**
     * Pure check for whether an IME Search/Go/Done action or hardware Enter
     * key-down should pick the highlighted item (or clear empty search).
     */
    fun isSearchLaunchAction(
        actionId: Int,
        keyCode: Int? = null,
        keyAction: Int? = null,
    ): Boolean {
        if (actionId == EditorInfo.IME_ACTION_GO ||
            actionId == EditorInfo.IME_ACTION_SEARCH ||
            actionId == EditorInfo.IME_ACTION_DONE
        ) {
            return true
        }
        val isEnter = keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        return actionId == EditorInfo.IME_NULL &&
            isEnter &&
            (keyAction == null || keyAction == KeyEvent.ACTION_DOWN)
    }

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
