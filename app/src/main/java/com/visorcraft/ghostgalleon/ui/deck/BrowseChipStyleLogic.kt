package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens

/**
 * Pure, host-tested visual style helpers for [GameDeck]'s interactive browse
 * chips and active filter pills: theme-adaptive touch ripples, contrasting
 * focus strokes, touch target minimums, and subtle numeric-count span ranges.
 */
object BrowseChipStyleLogic {

    /** Minimum touch height (dp) for primary browse rail chips. */
    const val CHIP_MIN_HEIGHT_DP: Int = 34

    /** Minimum touch height (dp) for secondary active-filter pills. */
    const val FILTER_PILL_MIN_HEIGHT_DP: Int = 30

    /** Subtle scale applied to trailing numeric counts (` · 42`) inside chips. */
    const val CHIP_COUNT_RELATIVE_SIZE: Float = 0.88f

    /** Light ripple over dark idle chips or dark accent fills. */
    const val RIPPLE_ON_DARK: Int = 0x33FFFFFF

    /** Dark ripple over light accent fills (e.g. OLED `#E0E0E0`). */
    const val RIPPLE_ON_LIGHT: Int = 0x26000000

    private const val DOT_SEPARATOR: String = " · "
    private const val SELECTED_COUNT_ALPHA: Int = 0xCC

    /** Touch ripple tint that stays visible over both dark and light accent chips. */
    fun rippleColor(selected: Boolean, accentColor: Int): Int {
        if (!selected) return RIPPLE_ON_DARK
        return if (ThemePack.onFillTextColor(accentColor) == 0xFF000000.toInt()) {
            RIPPLE_ON_LIGHT
        } else {
            RIPPLE_ON_DARK
        }
    }

    /**
     * Gamepad/keyboard focus ring color: accent color on idle chips, or high-
     * contrast on-fill text color on already-accent-filled selected chips so
     * focus remains unmistakably legible.
     */
    fun focusStrokeColor(selected: Boolean, accentColor: Int): Int =
        if (selected) ThemePack.onFillTextColor(accentColor) else accentColor

    /** Secondary tint for numeric count suffixes inside browse chips. */
    fun countSuffixColor(selected: Boolean, accentColor: Int): Int {
        if (!selected) return UiDimens.ALPHA_MUTED
        val onFill = ThemePack.onFillTextColor(accentColor)
        return (SELECTED_COUNT_ALPHA shl 24) or (onFill and 0x00FFFFFF)
    }

    /**
     * Character range of a trailing numeric count suffix (`" · 42"`) inside a
     * browse chip label. Returns null for plain chips (`"All"`) and non-count
     * paired labels (`"All · A–Z"`, `"Continue · Zelda"`). Supports Unicode
     * locale digits.
     */
    fun countSuffixRange(label: String): IntRange? {
        val idx = label.lastIndexOf(DOT_SEPARATOR)
        if (idx <= 0) return null
        val tailStart = idx + DOT_SEPARATOR.length
        if (tailStart >= label.length) return null
        val tail = label.substring(tailStart).trim()
        if (tail.isEmpty() || !tail.all { Character.isDigit(it) }) return null
        return idx until label.length
    }
}
