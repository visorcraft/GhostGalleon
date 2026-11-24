package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.BrowseFeedback
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.settings.ThemePack

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

    /** Responsive label max-width bounds (dp) on active filter pills so `×` stays visible. */
    const val MIN_FILTER_PILL_LABEL_WIDTH_DP: Int = 120
    const val MAX_FILTER_PILL_LABEL_WIDTH_DP: Int = 220

    /** Minimum entries required before showing the carousel position indicator. */
    const val MIN_RAIL_COUNT_FOR_INDICATOR: Int = 2

    /** Translucent alpha for the theme-tinted rail progress pill backdrop. */
    const val RAIL_PILL_BG_ALPHA: Int = 0xCC

    /** Duration (ms) for smooth cancelable rail progress fill scaleX lerp. */
    const val RAIL_PROGRESS_ANIM_MS: Long = 90L

    /** Soft-reveal animation parameters when transitioning into the empty carousel state. */
    const val EMPTY_REVEAL_START_ALPHA: Float = 0.65f
    const val EMPTY_REVEAL_DURATION_MS: Long = 140L

    /** Ergonomic minimum touch height (dp) for the empty-state Reset CTA button. */
    const val EMPTY_RESET_MIN_HEIGHT_DP: Int = 44

    /** Viewport height cutoff (dp) below which empty-state framing tightens for Sugar panels. */
    const val COMPACT_EMPTY_MAX_HEIGHT_DP: Int = 560

    data class EmptyCardMetrics(
        val padHorizontalDp: Int,
        val padVerticalDp: Int,
        val medallionSizeDp: Int,
        val iconSizeDp: Int,
        val medallionBottomMarginDp: Int,
        val messageSp: Float,
        val resetTopMarginDp: Int,
        val resetMinHeightDp: Int,
        val estimatedHeightDp: Int,
    )

    /**
     * Upper bound on combined old + new carousel item counts for synchronous Myers
     * DiffUtil calculation on the UI thread. Above this cap, browse dataset
     * replacement swaps directly to avoid multi-hundred-ms stalls on 6k–20k ROMs.
     */
    const val MAX_SYNC_DIFF_ITEMS: Int = 512

    /** Extra cards padded on each side of the visible carousel window during selection diffing. */
    const val VISIBLE_SELECTION_PAD: Int = 2

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

    /**
     * Whether the primary browse bar should render its own `"Clear filters"` chip.
     * Mutually exclusive with [shouldShowStripClearAll]: when [hasActiveFilterStrip]
     * is true (the standard Game Mode chrome), the active filter strip already
     * provides individual dismissible `×` pills for 1 filter and owns the single
     * Clear-All pill for `>= 2` filters, keeping the primary bar decluttered.
     */
    fun showBrowseBarClearChip(
        activeFilterCount: Int,
        hasActiveFilterStrip: Boolean = true,
    ): Boolean {
        if (activeFilterCount <= 0) return false
        if (shouldShowStripClearAll(activeFilterCount)) return false
        return !hasActiveFilterStrip
    }

    /**
     * Responsive max width (dp) for an active filter pill's text label inside a
     * HorizontalScrollView so long search queries or developer names never push
     * the pill's dismiss `×` button off-screen (`140dp` on 540dp Sugar, `220dp`
     * on Odin 2 Portal; always bounded in `[120, 240]dp`).
     */
    fun maxFilterPillLabelWidthDp(viewportWidthDp: Int): Int =
        (viewportWidthDp.coerceAtLeast(1) * 0.26f)
            .toInt()
            .coerceIn(MIN_FILTER_PILL_LABEL_WIDTH_DP, MAX_FILTER_PILL_LABEL_WIDTH_DP)

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

    /**
     * Responsive padding, medallion, typography, and CTA sizing for the in-layout
     * empty carousel card. Scales compactly on `<= 560dp` Sugar panels so the
     * card fits comfortably between active filter strips, hints, and dock.
     */
    fun emptyCardMetrics(viewportHeightDp: Int): EmptyCardMetrics {
        val compact = viewportHeightDp <= COMPACT_EMPTY_MAX_HEIGHT_DP
        val padH = if (compact) 18 else 24
        val padV = if (compact) 14 else 20
        val medallion = if (compact) 40 else 52
        val icon = if (compact) 20 else 26
        val medallionGap = if (compact) 8 else 12
        val messageSp = if (compact) 14.5f else 16f
        val resetTop = if (compact) 10 else 14
        val resetMinH = EMPTY_RESET_MIN_HEIGHT_DP
        val msgHeight = (messageSp * 1.25f * 2f).toInt()
        val estimated = padV * 2 + medallion + medallionGap + msgHeight + resetTop + resetMinH
        return EmptyCardMetrics(
            padHorizontalDp = padH,
            padVerticalDp = padV,
            medallionSizeDp = medallion,
            iconSizeDp = icon,
            medallionBottomMarginDp = medallionGap,
            messageSp = messageSp,
            resetTopMarginDp = resetTop,
            resetMinHeightDp = resetMinH,
            estimatedHeightDp = estimated,
        )
    }

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

    /** Theme-synced translucent pill backdrop color derived from [surfaceSunken]. */
    fun railPillBackgroundColor(surfaceSunken: Int): Int =
        ThemePack.withAlpha(surfaceSunken, RAIL_PILL_BG_ALPHA)

    /**
     * Clamp a positive rail progress [fraction] so even huge libraries (e.g.
     * `1/5000`) always paint at least a visible circular dot
     * (`>= trackHeight / trackWidth`) while remaining monotonic in `min..1f`.
     */
    fun effectiveRailFillFraction(
        fraction: Float,
        trackWidthPx: Int,
        trackHeightPx: Int,
    ): Float {
        if (fraction <= 0f) return 0f
        val clamped = fraction.coerceIn(0f, 1f)
        if (trackWidthPx <= 0 || trackHeightPx <= 0) return clamped
        val minVisible = (trackHeightPx.toFloat() / trackWidthPx.toFloat()).coerceIn(0f, 1f)
        return clamped.coerceIn(minVisible, 1f)
    }

    /** True when [oldSize] + [newSize] is small enough for synchronous Myers DiffUtil. */
    fun shouldUseDiffUtil(oldSize: Int, newSize: Int): Boolean {
        val safeOld = oldSize.coerceAtLeast(0).toLong()
        val safeNew = newSize.coerceAtLeast(0).toLong()
        return safeOld + safeNew <= MAX_SYNC_DIFF_ITEMS.toLong()
    }

    /**
     * Clamped adapter range around the currently visible carousel window
     * (`[firstVisible - pad, lastVisible + pad]`) for selection payload refreshes.
     * Returns null when the adapter is empty, layout has not run (`NO_POSITION`),
     * or the visible window does not overlap `[0, itemCount - 1]`.
     */
    fun visibleSelectionNotifyRange(
        firstVisible: Int,
        lastVisible: Int,
        itemCount: Int,
        pad: Int = VISIBLE_SELECTION_PAD,
    ): IntRange? {
        if (itemCount <= 0 || firstVisible < 0 || lastVisible < 0 || firstVisible > lastVisible) {
            return null
        }
        val safePad = pad.coerceAtLeast(0)
        val start = (firstVisible - safePad).coerceAtLeast(0)
        val end = (lastVisible + safePad).coerceAtMost(itemCount - 1)
        return if (start <= end) start..end else null
    }

    /** True when [mode] displays the A–Z / '#' letter-jump strip. */
    fun supportsLetterJump(mode: LibraryBrowse.Mode): Boolean =
        mode == LibraryBrowse.Mode.ALPHA || mode == LibraryBrowse.Mode.UNPLAYED

    /** One populated bucket in the A–Z / '#' letter-jump strip with its first item index. */
    data class LetterJumpBucket(
        val letter: Char,
        val count: Int,
        val firstIndex: Int,
    )

    /**
     * Single-pass zero-copy letter-jump bucket summary over [items] (A–Z then '#').
     * Computes counts and first indices directly without allocating a `List<String>`.
     */
    inline fun <T> letterJumpBuckets(
        items: List<T>,
        labelOf: (T) -> String,
    ): List<LetterJumpBucket> {
        if (items.isEmpty()) return emptyList()
        val counts = IntArray(27)
        val firstIndices = IntArray(27) { -1 }
        for (i in items.indices) {
            val b = LibraryBrowse.letterBucket(labelOf(items[i]))
            val slot = if (b == '#') 26 else b - 'A'
            if (counts[slot] == 0) firstIndices[slot] = i
            counts[slot]++
        }
        val out = ArrayList<LetterJumpBucket>(27)
        for (i in 0 until 26) {
            val n = counts[i]
            if (n > 0) out.add(LetterJumpBucket(('A' + i), n, firstIndices[i]))
        }
        if (counts[26] > 0) out.add(LetterJumpBucket('#', counts[26], firstIndices[26]))
        return out
    }

    /**
     * Mode-gated, zero-copy equivalent of [LibraryBrowse.letterJumpStructureKey].
     * Returns `""` immediately without touching [items] or invoking [labelOf]
     * when [mode] has no letter-jump strip.
     */
    inline fun <T> letterJumpStructureKey(
        mode: LibraryBrowse.Mode,
        items: List<T>,
        labelOf: (T) -> String,
    ): String {
        if (!supportsLetterJump(mode) || items.isEmpty()) return ""
        val counts = IntArray(27)
        for (i in items.indices) {
            val b = LibraryBrowse.letterBucket(labelOf(items[i]))
            val slot = if (b == '#') 26 else b - 'A'
            counts[slot]++
        }
        val sb = StringBuilder(128)
        for (i in 0 until 26) {
            val n = counts[i]
            if (n > 0) {
                if (sb.isNotEmpty()) sb.append(',')
                sb.append(('A' + i)).append(':').append(n)
            }
        }
        if (counts[26] > 0) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append('#').append(':').append(counts[26])
        }
        return sb.toString()
    }

    /** Zero-copy first index in [items] whose [LibraryBrowse.letterBucket] matches [letter]. */
    inline fun <T> firstIndexForLetter(
        items: List<T>,
        letter: Char,
        labelOf: (T) -> String,
    ): Int {
        val bucket = letter.uppercaseChar().let { c -> if (c in 'A'..'Z') c else '#' }
        for (i in items.indices) {
            if (LibraryBrowse.letterBucket(labelOf(items[i])) == bucket) return i
        }
        return -1
    }
}
