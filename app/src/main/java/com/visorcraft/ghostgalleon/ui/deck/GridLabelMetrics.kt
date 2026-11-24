package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.ui.UiDimens
import kotlin.math.roundToInt

/**
 * Pure, host-tested adaptive label typography and wallpaper readability scrim
 * logic for [GridDeck] tiles.
 *
 * Unlocks 2-line labels on roomy handheld cells (e.g. Odin 2 Portal 16:9) so
 * long game titles remain legible without truncation, falls back cleanly to a
 * compact 1-line budget on tight cells (e.g. One X Sugar ~540dp), and applies
 * a WCAG-AA dark pill scrim over custom wallpapers.
 */
object GridLabelMetrics {

    /** 60% opaque black scrim: even over pure white wallpaper yields 5.74:1 contrast for white text. */
    const val WALLPAPER_SCRIM_FILL: Int = 0x99000000.toInt()
    const val WALLPAPER_SHADOW_COLOR: Int = 0xCC000000.toInt()
    const val LABEL_UNFOCUSED_COLOR: Int = 0xEBFFFFFF.toInt()
    const val LABEL_MISSING_FOCUSED_COLOR: Int = UiDimens.COLOR_DESTRUCTIVE
    const val LABEL_MISSING_IDLE_COLOR: Int = 0xCCFF8A80.toInt()

    const val MIN_TEXT_SP: Float = 10.5f
    const val MAX_TEXT_SP: Float = 14f

    data class Spec(
        val maxLines: Int,
        val textSp: Float,
        val reservedHeightDp: Int,
        val topMarginDp: Int,
        val useReadabilityScrim: Boolean,
    )

    data class LabelStyle(
        val bold: Boolean,
        val textColor: Int,
    )

    /**
     * Compute adaptive label lines, sp size, vertical reservation, and scrim
     * requirement from cell dimensions and theme font scale.
     */
    fun compute(
        cellWidthDp: Int,
        cellHeightDp: Int,
        iconSizeDp: Int,
        cellPaddingDp: Int = 8,
        showLabels: Boolean = true,
        hasWallpaper: Boolean = false,
        fontScale: Float = 1f,
    ): Spec {
        if (!showLabels) {
            return Spec(
                maxLines = 1,
                textSp = 11f,
                reservedHeightDp = 0,
                topMarginDp = 0,
                useReadabilityScrim = false,
            )
        }
        val safeW = cellWidthDp.coerceAtLeast(1)
        val safeH = cellHeightDp.coerceAtLeast(1)
        val safeIcon = iconSizeDp.coerceAtLeast(1)
        val scale = fontScale.coerceIn(0.90f, 1.15f)

        val remainingDp = safeH - cellPaddingDp * 2 - safeIcon
        val allowTwoLines = remainingDp >= 36 && safeW >= 82

        val baseSp = when {
            safeW < 76 -> 11f
            safeW >= 124 && allowTwoLines -> 12.5f
            else -> 12f
        }
        val textSp = ((baseSp * scale) * 2f).roundToInt() / 2f
        val clampedSp = textSp.coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)

        val reservedHeightDp = if (allowTwoLines) {
            if (hasWallpaper) 38 else 34
        } else {
            if (hasWallpaper) 24 else 22
        }
        val topMarginDp = if (allowTwoLines) 5 else 4

        return Spec(
            maxLines = if (allowTwoLines) 2 else 1,
            textSp = clampedSp,
            reservedHeightDp = reservedHeightDp,
            topMarginDp = topMarginDp,
            useReadabilityScrim = hasWallpaper,
        )
    }

    /** Bold high-contrast emphasis for focused/lifted tiles; calm weight for idle siblings. */
    fun labelStyleForRole(
        role: MoveChoreography.SlotRole,
        isMissing: Boolean = false,
    ): LabelStyle = when (role) {
        MoveChoreography.SlotRole.FOCUSED,
        MoveChoreography.SlotRole.LIFTED,
        -> LabelStyle(
            bold = !isMissing,
            textColor = if (isMissing) LABEL_MISSING_FOCUSED_COLOR else UiDimens.ALPHA_PRIMARY,
        )
        MoveChoreography.SlotRole.DROP_TARGET -> LabelStyle(
            bold = false,
            textColor = if (isMissing) UiDimens.ALPHA_SUBTLE else UiDimens.ALPHA_MUTED,
        )
        MoveChoreography.SlotRole.NORMAL -> LabelStyle(
            bold = false,
            textColor = if (isMissing) LABEL_MISSING_IDLE_COLOR else LABEL_UNFOCUSED_COLOR,
        )
    }

    /**
     * Composite translucent ARGB [overlay] over opaque [backdrop] for host-side
     * WCAG AA readability verification.
     */
    fun compositeOver(overlay: Int, backdrop: Int): Int {
        val alpha = ((overlay ushr 24) and 0xFF) / 255f
        return MoveChoreography.blendRgb(backdrop, overlay, alpha)
    }
}
