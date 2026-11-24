package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.BrowseFeedback
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.PlatformLook

/**
 * Pure, host-tested UI helpers for [GameDeck]'s filter pill row, StatusPill
 * clearance, empty-state copy/action gating, horizontal chip centering, and
 * carousel card geometry.
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

    /** Minimum simultaneous active filters before showing the inline Clear-All pill. */
    const val MIN_FILTERS_FOR_STRIP_CLEAR_ALL: Int = 2

    /** Minimum entries required before showing the carousel position indicator. */
    const val MIN_RAIL_COUNT_FOR_INDICATOR: Int = 2

    enum class EmptyVisualKind {
        SEARCH_OR_FILTER,
        FAVORITES,
        COLLECTION,
        LIBRARY,
    }

    data class RailProgressState(
        val visible: Boolean,
        val currentOneBased: Int,
        val total: Int,
        val fraction: Float,
    )

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

    /**
     * Clamped HorizontalScrollView scroll-X that centers a chip at
     * `[childLeft, childLeft + childWidth]` within [viewportWidth].
     * Returns 0 when the strip fits within the viewport or is unmeasured.
     */
    fun horizontalCenterScrollX(
        childLeft: Int,
        childWidth: Int,
        viewportWidth: Int,
        contentWidth: Int,
    ): Int {
        if (viewportWidth <= 0 || contentWidth <= viewportWidth) return 0
        val maxScroll = (contentWidth - viewportWidth).coerceAtLeast(0)
        val desired = childLeft + childWidth / 2 - viewportWidth / 2
        return desired.coerceIn(0, maxScroll)
    }

    /** True when the active filter pill strip should append a quick Clear-All pill. */
    fun shouldShowStripClearAll(activeFilterCount: Int): Boolean =
        activeFilterCount >= MIN_FILTERS_FOR_STRIP_CLEAR_ALL

    /** Descriptive TalkBack label for removing an individual active filter pill. */
    fun filterPillAccessibilityText(filterLabel: String): UiText =
        text(R.string.format_colon_pair, text(R.string.action_clear), dynamicText(filterLabel))

    /** Descriptive TalkBack label for a browse chip including active state cue. */
    fun browseChipAccessibilityText(chipLabel: String, selected: Boolean): UiText =
        if (selected) {
            text(R.string.format_selected_check, dynamicText(chipLabel))
        } else {
            dynamicText(chipLabel)
        }

    /** Pick contextual empty-state icon framing matching the active browse query. */
    fun emptyVisualKind(query: LibraryBrowse.BrowseQuery): EmptyVisualKind = when {
        LibraryBrowse.hasActiveMetaFilters(query) -> EmptyVisualKind.SEARCH_OR_FILTER
        query.mode == LibraryBrowse.Mode.FAVORITES -> EmptyVisualKind.FAVORITES
        query.mode == LibraryBrowse.Mode.COLLECTION -> EmptyVisualKind.COLLECTION
        else -> EmptyVisualKind.LIBRARY
    }

    /**
     * True when gamepad CONFIRM/BACK on an empty carousel should reset filters
     * back to the unrestricted catalog instead of trying to launch a stale key.
     */
    fun emptyStateConfirmResets(
        entriesEmpty: Boolean,
        query: LibraryBrowse.BrowseQuery,
    ): Boolean = entriesEmpty && shouldShowResetAction(query)

    /** Pure position & progress fraction for the carousel rail indicator. */
    fun railProgressState(selectedIndex: Int, itemCount: Int): RailProgressState {
        val safeTotal = itemCount.coerceAtLeast(0)
        if (safeTotal < MIN_RAIL_COUNT_FOR_INDICATOR) {
            return RailProgressState(
                visible = false,
                currentOneBased = 0,
                total = safeTotal,
                fraction = 0f,
            )
        }
        val clamped = selectedIndex.coerceIn(0, safeTotal - 1)
        val oneBased = clamped + 1
        return RailProgressState(
            visible = true,
            currentOneBased = oneBased,
            total = safeTotal,
            fraction = (oneBased.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f),
        )
    }

    /** Localized `current/total` rail counter text. */
    fun railPositionText(currentOneBased: Int, total: Int): UiText =
        text(R.string.format_progress, currentOneBased, total)

    /** Responsive rail progress track width (dp) for compact and widescreen panels. */
    fun railTrackWidthDp(screenWidthDp: Int): Int =
        (screenWidthDp.coerceAtLeast(1) * 0.20f).toInt().coerceIn(72, 148)
}
