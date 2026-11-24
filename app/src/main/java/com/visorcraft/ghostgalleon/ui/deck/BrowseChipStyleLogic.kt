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
    const val CHIP_MIN_HEIGHT_DP: Int = 36

    /** Minimum touch height (dp) for secondary active-filter pills. */
    const val FILTER_PILL_MIN_HEIGHT_DP: Int = 30

    /** Platform identity dot diameter (dp) inside browse-bar platform chips. */
    const val PLATFORM_DOT_SIZE_DP: Int = 6

    /** Gap (dp) between platform dot drawable and chip text. */
    const val PLATFORM_DOT_PADDING_DP: Int = 6

    /** Subtle vertical hairline divider dimensions (dp) between semantic chip groups. */
    const val GROUP_DIVIDER_WIDTH_DP: Int = 1
    const val GROUP_DIVIDER_HEIGHT_DP: Int = 16
    const val GROUP_DIVIDER_MARGIN_H_DP: Int = 6

    /** Responsive width clamp bounds (dp) for the Continue quick-jump chip. */
    const val MIN_CONTINUE_CHIP_WIDTH_DP: Int = 160
    const val MAX_CONTINUE_CHIP_WIDTH_DP: Int = 220

    /** Semantic groups inside [GameDeck]'s primary horizontal browse chip bar. */
    enum class BrowseChipGroup {
        PRIMARY_RAILS,
        COLLECTIONS,
        FILTERS,
        ACTIONS,
    }

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
     * Gamepad/keyboard focus ring color: accent color on idle chips, or the
     * higher-contrast pole (`#FFFFFF` vs `#000000`, mathematically `>= 4.58:1`
     * against every 24-bit color) on already-accent-filled selected chips so
     * focus stays unmistakably legible across Ghost, Teal, OLED, Neon, and
     * custom themes.
     */
    fun focusStrokeColor(selected: Boolean, accentColor: Int): Int {
        if (!selected) return accentColor
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        return if (ThemePack.contrastRatio(white, accentColor) >=
            ThemePack.contrastRatio(black, accentColor)
        ) {
            white
        } else {
            black
        }
    }

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

    /**
     * WCAG 1.4.11 (`>= 3.0:1`) platform identity dot color for browse-bar
     * platform chips over either dark [chipIdle] or filled [themeAccent]
     * (including mid-luma Teal `#00A8A0` and light OLED `#E0E0E0`).
     */
    fun platformDotColor(
        platformAccent: Int,
        selected: Boolean,
        themeAccent: Int,
        chipIdle: Int,
    ): Int {
        val bg = (0xFF shl 24) or ((if (selected) themeAccent else chipIdle) and 0x00FFFFFF)
        val opaqueDot = (0xFF shl 24) or (platformAccent and 0x00FFFFFF)
        val minRatio = DeckThemeContrast.MIN_NON_TEXT_CONTRAST
        if (ThemePack.contrastRatio(opaqueDot, bg) >= minRatio) return opaqueDot

        val target = 3.15
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val whiteContrast = ThemePack.contrastRatio(white, bg)
        val blackContrast = ThemePack.contrastRatio(black, bg)

        // Prefer lifting toward white on dark surfaces (preserving vibrant hues)
        // whenever white clears the target margin; otherwise darken toward black
        // (e.g. OLED #E0E0E0 and mid-luma Teal #00A8A0 where white is < 3.0:1).
        if (whiteContrast >= target && (whiteContrast >= blackContrast || ThemePack.relativeLuminance(bg) < 0.27)) {
            return DeckThemeContrast.accessibleFocusStroke(opaqueDot, bg, minRatio)
        }
        var lo = 0f
        var hi = 1f
        var best = black
        repeat(16) {
            val mid = (lo + hi) * 0.5f
            val cand = ThemePack.blendColors(opaqueDot, black, mid)
            if (ThemePack.contrastRatio(cand, bg) >= target) {
                best = cand
                hi = mid
            } else {
                lo = mid
            }
        }
        return if (ThemePack.contrastRatio(best, bg) >= minRatio) best else black
    }

    /**
     * True when a subtle vertical hairline divider should separate a non-empty
     * [previousGroup] from the following [nextGroup] in the browse bar.
     */
    fun shouldInsertGroupDivider(
        previousGroup: BrowseChipGroup?,
        nextGroup: BrowseChipGroup,
    ): Boolean = previousGroup != null && previousGroup != nextGroup

    /**
     * Responsive max width (dp) for the `Continue · <Title>` browse chip so
     * long game titles ellipsize cleanly instead of pushing rails/Search off-screen.
     */
    fun maxContinueChipWidthDp(viewportWidthDp: Int): Int =
        (viewportWidthDp.coerceAtLeast(1) * 0.30f)
            .toInt()
            .coerceIn(MIN_CONTINUE_CHIP_WIDTH_DP, MAX_CONTINUE_CHIP_WIDTH_DP)

    /**
     * Idle border color for the Continue browse chip: uses a luminance-adaptive
     * accent hairline when a resume target exists, or [restingHairline] when idle.
     */
    fun continueChipAccentBorder(
        hasTarget: Boolean,
        accent: Int,
        restingHairline: Int,
    ): Int = if (hasTarget) ThemePack.accentHairline(accent) else restingHairline
}
