package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import kotlin.math.roundToInt

/**
 * Pure, host-tested optical nested corner radius geometry (`outer - inset = inner`)
 * for GridDeck tiles, DockBar slots, and FolderPanel thumbnails.
 *
 * Keeps inner artwork, folder frames, dock slot focus rings, and modal thumbnails
 * optically concentric with their parent container across Ghost/Teal/OLED/Neon
 * and custom theme packs on both narrow (~540dp Sugar) and wide (16:9 Odin 2 Portal)
 * displays.
 */
object DeckCornerMetrics {

    /** Minimum readable corner radius for standard tiles/thumbnails when outer permits. */
    const val MIN_INNER_RADIUS_DP: Int = 6

    /** Maximum radius-to-size fraction so small square tiles stay squircle-like, not circular. */
    const val MAX_TILE_RADIUS_FRACTION: Float = 0.38f

    /**
     * Optical nested corner radius in dp: `outerRadiusDp - insetDp`, clamped to
     * `[min(minRadiusDp, outerRadiusDp), outerRadiusDp]`.
     */
    fun nestedRadiusDp(
        outerRadiusDp: Int,
        insetDp: Int,
        minRadiusDp: Int = MIN_INNER_RADIUS_DP,
    ): Int {
        val safeOuter = outerRadiusDp.coerceAtLeast(0)
        val safeInset = insetDp.coerceAtLeast(0)
        val floor = minRadiusDp.coerceIn(0, safeOuter)
        return (safeOuter - safeInset).coerceIn(floor, safeOuter)
    }

    /**
     * Size-aware optical nested corner radius in dp: applies [nestedRadiusDp]
     * and caps to [MAX_TILE_RADIUS_FRACTION] of [innerSizeDp] so compact slots
     * never collapse into circles while normal tiles stay strictly concentric.
     */
    fun nestedRadiusDp(
        outerRadiusDp: Int,
        insetDp: Int,
        innerSizeDp: Int,
        minRadiusDp: Int = MIN_INNER_RADIUS_DP,
    ): Int {
        val concentric = nestedRadiusDp(outerRadiusDp, insetDp, minRadiusDp)
        if (innerSizeDp <= 0) return concentric
        val maxForSize = (innerSizeDp * MAX_TILE_RADIUS_FRACTION).toInt().coerceAtLeast(1)
        return concentric.coerceAtMost(maxForSize)
    }

    /**
     * Pixel-space concentric nested radius (`outerRadiusPx - insetPx`), clamped to
     * `[min(minRadiusPx, outerRadiusPx), outerRadiusPx]`. Useful for sub-tile frames
     * like folder mosaic quadrants.
     */
    fun nestedRadiusPx(
        outerRadiusPx: Float,
        insetPx: Float,
        minRadiusPx: Float = 0f,
    ): Float {
        val safeOuter = outerRadiusPx.coerceAtLeast(0f)
        val safeInset = insetPx.coerceAtLeast(0f)
        val floor = minRadiusPx.coerceIn(0f, safeOuter)
        return (safeOuter - safeInset).coerceIn(floor, safeOuter)
    }

    /**
     * Inner artwork / folder-box corner radius inside a GridDeck cell whose outer
     * card uses [ThemeTokens.cardRadiusDp] and inner padding is [cellPaddingDp].
     */
    fun gridTileInnerRadiusDp(
        tokens: ThemeTokens,
        cellPaddingDp: Int = 8,
        iconSizeDp: Int = 72,
    ): Int = nestedRadiusDp(
        outerRadiusDp = tokens.cardRadiusDp,
        insetDp = cellPaddingDp,
        innerSizeDp = iconSizeDp,
        minRadiusDp = MIN_INNER_RADIUS_DP,
    )

    /**
     * Focus-frame corner radius for a [DockBar] slot inside the floating dock pill
     * (`ThemePack.pillRadiusDp(tokens)` with vertical padding [barPadVDp]).
     */
    fun dockSlotRadiusDp(
        tokens: ThemeTokens,
        barPadVDp: Int = 8,
        slotSizeDp: Int = 48,
    ): Int = nestedRadiusDp(
        outerRadiusDp = ThemePack.pillRadiusDp(tokens),
        insetDp = barPadVDp,
        innerSizeDp = slotSizeDp,
        minRadiusDp = 8,
    )

    /**
     * Inner icon/art/blank corner radius inside a [DockBar] slot frame, concentric
     * with [dockSlotRadiusDp] minus the frame-to-icon inset.
     */
    fun dockInnerContentRadiusDp(
        tokens: ThemeTokens,
        barPadVDp: Int = 8,
        slotSizeDp: Int = 48,
        iconSizeDp: Int = 40,
    ): Int {
        val slotRadius = dockSlotRadiusDp(tokens, barPadVDp, slotSizeDp)
        val insetDp = (((slotSizeDp - iconSizeDp) / 2f).roundToInt()).coerceAtLeast(0)
        return nestedRadiusDp(
            outerRadiusDp = slotRadius,
            insetDp = insetDp,
            innerSizeDp = iconSizeDp,
            minRadiusDp = MIN_INNER_RADIUS_DP,
        )
    }

    /**
     * Member cell focus-frame corner radius inside [FolderPanel]'s modal card.
     */
    fun folderCellRadiusDp(
        tokens: ThemeTokens,
        insetDp: Int = 8,
    ): Int = nestedRadiusDp(
        outerRadiusDp = tokens.cardRadiusDp,
        insetDp = insetDp,
        minRadiusDp = 10,
    )

    /**
     * Member thumbnail corner radius inside a [FolderPanel] cell, concentric with
     * [folderCellRadiusDp] minus [cellPaddingDp].
     */
    fun folderThumbRadiusDp(
        tokens: ThemeTokens,
        cellPaddingDp: Int = 4,
        thumbSizeDp: Int = 72,
    ): Int = nestedRadiusDp(
        outerRadiusDp = folderCellRadiusDp(tokens),
        insetDp = cellPaddingDp,
        innerSizeDp = thumbSizeDp,
        minRadiusDp = MIN_INNER_RADIUS_DP,
    )
}
