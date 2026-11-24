package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.quantityText

/**
 * Pure, host-tested visual state helpers for [GameDeck]'s multi-select action
 * banner and per-card selection checkbox affordances.
 */
object CarouselMultiSelectLogic {

    /** Subtle translucent tint fill alpha for the contextual multi-select banner. */
    const val BANNER_FILL_ALPHA: Int = 0x24

    /** Accent hairline border alpha for the contextual multi-select banner. */
    const val BANNER_STROKE_ALPHA: Int = 0x66

    enum class CardCheckStyle {
        HIDDEN,
        UNCHECKED,
        CHECKED,
    }

    /** Determine which corner indicator a carousel card shows for multi-select. */
    fun cardCheckStyle(
        multiSelectEnabled: Boolean,
        multiSelected: Boolean,
    ): CardCheckStyle = when {
        !multiSelectEnabled -> CardCheckStyle.HIDDEN
        multiSelected -> CardCheckStyle.CHECKED
        else -> CardCheckStyle.UNCHECKED
    }

    /** Localized selection counter label for the multi-select banner. */
    fun bannerCountText(selectedCount: Int): UiText {
        val safe = selectedCount.coerceAtLeast(0)
        return quantityText(R.plurals.count_selected, safe, safe)
    }

    /** True when the quick Select-All pill can still add unselected cards on this rail. */
    fun showSelectAllPill(selectedCount: Int, railTotal: Int): Boolean =
        railTotal > 0 && selectedCount.coerceAtLeast(0) < railTotal

    /** Soft tinted banner background color derived from [accentColor]. */
    fun bannerTintFill(accentColor: Int): Int =
        (accentColor and 0x00FFFFFF) or (BANNER_FILL_ALPHA shl 24)

    /** Translucent accent border color derived from [accentColor]. */
    fun bannerTintStroke(accentColor: Int): Int =
        (accentColor and 0x00FFFFFF) or (BANNER_STROKE_ALPHA shl 24)
}
