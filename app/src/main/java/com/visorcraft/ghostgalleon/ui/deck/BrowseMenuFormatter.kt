package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.LibraryBrowse

/**
 * Pure, host-tested title and active-checkmark formatting helpers for
 * [GameDeck]'s long-press browse menus (Platform, Collection, Favorites,
 * and Search History).
 */
object BrowseMenuFormatter {

    /** Append localized active checkmark (`✓`) when [active] is true. */
    fun withActiveCheck(label: UiText, active: Boolean): UiText =
        if (active) text(R.string.format_selected_check, label) else label

    /** String overload of [withActiveCheck] for dynamic labels. */
    fun withActiveCheck(label: String, active: Boolean): UiText =
        withActiveCheck(dynamicText(label), active)

    /** Consistent `Title · N` menu header reusing [LibraryBrowse.labeledChip]. */
    fun countedMenuTitle(title: UiText, count: Int): UiText =
        LibraryBrowse.labeledChip(title, count.coerceAtLeast(0))

    /** Counted platform long-press menu header falling back to [platformId]. */
    fun platformMenuTitle(shortName: String, platformId: String, romCount: Int): UiText {
        val base = shortName.trim().ifEmpty { platformId.trim() }
        return countedMenuTitle(dynamicText(base), romCount)
    }

    /** True when a platform chip menu action reflects the currently active query. */
    fun isPlatformActionActive(
        currentQuery: LibraryBrowse.BrowseQuery,
        action: LibraryBrowse.PlatformChipAction,
    ): Boolean = when (action.kind) {
        LibraryBrowse.PlatformChipActionKind.FILTER ->
            currentQuery.platformId == action.query.platformId &&
                currentQuery.sort == LibraryBrowse.Sort.DEFAULT
        LibraryBrowse.PlatformChipActionKind.SORT_NAME ->
            currentQuery.platformId == action.query.platformId &&
                currentQuery.sort == LibraryBrowse.Sort.NAME
        LibraryBrowse.PlatformChipActionKind.SORT_LAST_PLAYED ->
            currentQuery.platformId == action.query.platformId &&
                currentQuery.sort == LibraryBrowse.Sort.LAST_PLAYED
        LibraryBrowse.PlatformChipActionKind.CLEAR -> false
    }

    /** True when a search history entry matches the active library search text. */
    fun isSearchHistoryEntryActive(
        historyEntry: String,
        activeSearchText: String,
    ): Boolean {
        val active = activeSearchText.trim()
        if (active.isEmpty()) return false
        return historyEntry.trim().equals(active, ignoreCase = true)
    }
}
