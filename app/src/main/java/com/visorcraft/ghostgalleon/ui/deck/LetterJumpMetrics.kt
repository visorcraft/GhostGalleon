package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens

/**
 * Pure, host-tested typography, contrast, touch-sizing, and bucket-stepping
 * helpers for [GameDeck]'s A–Z letter-jump strip.
 */
object LetterJumpMetrics {

    /** Minimum touch width (dp) for letter-jump pills. */
    const val CHIP_MIN_WIDTH_DP: Int = 36

    /** Minimum touch height (dp) for letter-jump pills. */
    const val CHIP_MIN_HEIGHT_DP: Int = 30

    /** Rounded pill corner radius (dp) for letter-jump pills. */
    const val CHIP_CORNER_RADIUS_DP: Int = 14

    /** Relative font scale for the `· N` count suffix next to the bucket letter. */
    const val COUNT_RELATIVE_SIZE: Float = 0.84f

    /** Subtle scale-settle start value when crossing into a new letter bucket. */
    const val ACTIVE_PULSE_SCALE: Float = 1.07f

    /** Settle animation duration (ms) when active letter bucket changes. */
    const val ACTIVE_PULSE_MS: Long = 110L

    /** Alpha applied to active on-fill text for the secondary count suffix (~80%). */
    private const val SELECTED_COUNT_ALPHA: Int = 0xCC

    /**
     * Character range of the secondary count suffix in a resolved letter chip
     * label (e.g. `" · 12"` inside `"A · 12"`), or null when only the letter
     * is present or the prefix does not match [letter].
     */
    fun countSuffixRange(resolvedLabel: String, letter: Char): IntRange? {
        if (resolvedLabel.length <= 1) return null
        if (!resolvedLabel[0].equals(letter, ignoreCase = true)) return null
        return 1 until resolvedLabel.length
    }

    /**
     * Secondary count tint inside a letter pill: 80% of [ThemePack.onFillTextColor]
     * when active (stays legible on both dark Ghost/Teal/Neon and light OLED
     * accents), or [UiDimens.ALPHA_MUTED] when idle.
     */
    fun countTextColor(selected: Boolean, accentColor: Int): Int {
        if (!selected) return UiDimens.ALPHA_MUTED
        val onFill = ThemePack.onFillTextColor(accentColor)
        return (SELECTED_COUNT_ALPHA shl 24) or (onFill and 0x00FFFFFF)
    }

    /** Localized TalkBack description for a letter-jump pill including active state. */
    fun letterChipAccessibilityText(
        letter: Char,
        count: Int,
        selected: Boolean,
    ): UiText {
        val base = text(R.string.format_jump_to_letter, letter.toString(), count.coerceAtLeast(0))
        return if (selected) text(R.string.format_selected_check, base) else base
    }

    /** Clamped adjacent bucket letter when stepping by [step] (`-1` / `+1`). */
    fun adjacentLetter(
        presentLetters: List<Char>,
        currentLetter: Char?,
        step: Int,
    ): Char? {
        if (presentLetters.isEmpty()) return null
        val idx = currentLetter?.let { presentLetters.indexOf(it) } ?: -1
        if (idx < 0) return if (step >= 0) presentLetters.first() else presentLetters.last()
        val next = (idx + step).coerceIn(0, presentLetters.lastIndex)
        return presentLetters[next]
    }
}
