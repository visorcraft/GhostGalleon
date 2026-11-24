package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Pure, host-tested visual state helpers for [GameDeck]'s multi-select action
 * banner and per-card selection checkbox affordances.
 */
object CarouselMultiSelectLogic {

    /** Subtle translucent tint fill alpha for the contextual multi-select banner. */
    const val BANNER_FILL_ALPHA: Int = 0x24

    /** Accent hairline border alpha for the contextual multi-select banner. */
    const val BANNER_STROKE_ALPHA: Int = 0x66

    /** Ergonomic minimum touch height (dp) for multi-select banner action pills. */
    const val BANNER_BUTTON_MIN_HEIGHT_DP: Int = 34

    /** Base translucent tint fill alpha for selected carousel card overlays. */
    const val CARD_OVERLAY_FILL_ALPHA: Int = 0x38

    /** Micro-pop scale start and duration (ms) when checking a card (`UNCHECKED → CHECKED`). */
    const val CHECK_POP_START_SCALE: Float = 0.78f
    const val CHECK_POP_DURATION_MS: Long = 110L

    data class CardOverlayColors(
        val fillColor: Int,
        val strokeColor: Int,
    )

    enum class CardCheckStyle {
        HIDDEN,
        UNCHECKED,
        CHECKED,
    }

    enum class GamepadActionOutcome {
        TOGGLE_CURRENT_CARD,
        OPEN_BULK_ACTIONS,
        EXIT_MULTI_SELECT,
        PASS_THROUGH,
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

    /** Soft luminance-adaptive tinted banner background color derived from [accentColor]. */
    fun bannerTintFill(accentColor: Int): Int =
        ThemePack.accentGlowColor(accentColor, BANNER_FILL_ALPHA)

    /** Translucent luminance-adaptive accent border color derived from [accentColor]. */
    fun bannerTintStroke(accentColor: Int): Int =
        ThemePack.accentHairline(accentColor, BANNER_STROKE_ALPHA)

    /**
     * OLED-safe & WCAG-accessible overlay fill + border stroke for checked
     * multi-select carousel cards over [surfaceFill]. Lifts dark accents so
     * borders clear `>= 3.0:1` while damping alpha on bright accents (`#E0E0E0`)
     * so box art never washes out.
     */
    fun cardSelectedOverlayColors(
        accent: Int,
        surfaceFill: Int,
    ): CardOverlayColors {
        val accessibleStroke = DeckThemeContrast.accessibleFocusStroke(accent, surfaceFill)
        val fill = ThemePack.accentGlowColor(accessibleStroke, CARD_OVERLAY_FILL_ALPHA)
        return CardOverlayColors(fillColor = fill, strokeColor = accessibleStroke)
    }

    /** True when toggling a card from [CardCheckStyle.UNCHECKED] to [CardCheckStyle.CHECKED] should pop. */
    fun shouldPopCheckmark(
        previousStyle: CardCheckStyle?,
        nextStyle: CardCheckStyle,
        animate: Boolean,
        animatorsEnabled: Boolean,
    ): Boolean = animate &&
        animatorsEnabled &&
        previousStyle == CardCheckStyle.UNCHECKED &&
        nextStyle == CardCheckStyle.CHECKED

    /**
     * Gamepad action routing while multi-select mode is active:
     * - CONFIRM toggles selection on the focused card instead of launching.
     * - BACK exits multi-select cleanly.
     * - SHOW_DETAILS opens the bulk-actions sheet.
     * - Directional / nav actions pass through so users browse cards freely.
     */
    fun gamepadOutcome(
        multiSelectEnabled: Boolean,
        action: Action,
        hasFocusedEntry: Boolean,
    ): GamepadActionOutcome {
        if (!multiSelectEnabled) return GamepadActionOutcome.PASS_THROUGH
        return when (action) {
            Action.CONFIRM ->
                if (hasFocusedEntry) GamepadActionOutcome.TOGGLE_CURRENT_CARD
                else GamepadActionOutcome.OPEN_BULK_ACTIONS
            Action.BACK -> GamepadActionOutcome.EXIT_MULTI_SELECT
            Action.SHOW_DETAILS -> GamepadActionOutcome.OPEN_BULK_ACTIONS
            else -> GamepadActionOutcome.PASS_THROUGH
        }
    }

    /**
     * Localized gamepad-keycap-aware HintBar cue while multi-select mode is
     * active (`"Select (N) - A · Bulk actions · B Cancel"`). Uses existing
     * string resources and universal button symbols so [HintBarFormat] styles
     * `Select (N)` as an accented `PREFIX` and `A`/`B` as tactile `KEYCAP`s
     * without drawing fake keycaps on localized action words.
     */
    fun multiSelectHintText(selectedCount: Int): UiText {
        val safe = selectedCount.coerceAtLeast(0)
        val countPart = text(R.string.format_select_count, safe)
        val leadToggle = joinText(listOf(countPart, dynamicText("A")), " - ")
        val cancelAction = joinText(listOf(dynamicText("B"), text(R.string.action_cancel)), " ")
        val actionPart = text(
            R.string.format_dot_pair,
            text(R.string.bulk_actions),
            cancelAction,
        )
        return text(R.string.format_dot_pair, leadToggle, actionPart)
    }
}
