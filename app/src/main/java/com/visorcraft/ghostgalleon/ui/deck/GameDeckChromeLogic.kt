package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.BrowseFeedback
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.PlatformLook

/**
 * Pure, host-tested UI helpers for [GameDeck]'s filter pill row, StatusPill
 * clearance, empty-state copy/action gating, and carousel card geometry.
 */
object GameDeckChromeLogic {

    /** End padding (dp) on browse chip row when compact StatusPill overlays top-end. */
    const val BROWSE_END_PAD_WITH_PILL_DP: Int = 120

    /** Standard end padding (dp) on browse chip row when StatusPill is hidden. */
    const val BROWSE_END_PAD_DEFAULT_DP: Int = 12

    /** Focused / multi-selected carousel card opacity. */
    const val CARD_ALPHA_FOCUSED: Float = 1.0f

    /** Subtle de-emphasis opacity for unfocused neighbor cards. */
    const val CARD_ALPHA_UNFOCUSED: Float = 0.78f

    sealed class ActiveFilter {
        data class Platform(val platformId: String) : ActiveFilter()
        data class Genre(val value: String) : ActiveFilter()
        data class Developer(val value: String) : ActiveFilter()
        data class YearDecade(val decade: String) : ActiveFilter()
        data class Search(val queryText: String) : ActiveFilter()
    }

    /** Active meta/search filters in display order for the single-row pill strip. */
    fun activeFilters(query: LibraryBrowse.BrowseQuery): List<ActiveFilter> = buildList {
        query.platformId
            ?.takeIf { PlatformLook.hasFilter(it) }
            ?.let { add(ActiveFilter.Platform(it)) }
        query.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { add(ActiveFilter.Genre(it)) }
        query.developer?.trim()?.takeIf { it.isNotEmpty() }?.let { add(ActiveFilter.Developer(it)) }
        query.yearDecade?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(ActiveFilter.YearDecade(it))
        }
        query.text.trim().takeIf { it.isNotEmpty() }?.let { add(ActiveFilter.Search(it)) }
    }

    /** Return a copy of [query] with only [filter] cleared. */
    fun clearFilter(
        query: LibraryBrowse.BrowseQuery,
        filter: ActiveFilter,
    ): LibraryBrowse.BrowseQuery = when (filter) {
        is ActiveFilter.Platform -> query.copy(platformId = null)
        is ActiveFilter.Genre -> query.copy(genre = null)
        is ActiveFilter.Developer -> query.copy(developer = null)
        is ActiveFilter.YearDecade -> query.copy(yearDecade = null)
        is ActiveFilter.Search -> query.copy(text = "")
    }

    /** Right-side browse bar padding (dp) keeping chips clear of top-end StatusPill. */
    fun browseBarEndPadDp(hasStatusPill: Boolean): Int =
        if (hasStatusPill) BROWSE_END_PAD_WITH_PILL_DP else BROWSE_END_PAD_DEFAULT_DP

    /** Localized message for the in-layout empty state card. */
    fun emptyStateMessage(query: LibraryBrowse.BrowseQuery): UiText =
        BrowseFeedback.emptyHint(query) ?: text(R.string.browse_library_empty)

    /** True when clearing filters/rail can recover from an empty carousel. */
    fun shouldShowResetAction(query: LibraryBrowse.BrowseQuery): Boolean =
        query != LibraryBrowse.BrowseQuery()

    /** Pinned uniform card container width so long titles never stretch spacing. */
    fun carouselCardWidthPx(cardSizePx: Int, cellPaddingPx: Int): Int =
        cardSizePx.coerceAtLeast(1) + cellPaddingPx.coerceAtLeast(0) * 2

    /** Target card opacity separating focused/multi-selected cards from neighbors. */
    fun cardVisualAlpha(focused: Boolean, multiSelected: Boolean): Float =
        if (focused || multiSelected) CARD_ALPHA_FOCUSED else CARD_ALPHA_UNFOCUSED
}
