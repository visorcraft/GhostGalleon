package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import kotlin.math.roundToInt

/**
 * Pure, host-tested sizing, optical corner-bisector placement, and WCAG AA
 * contrast tokens for GridDeck corner micro-badges (Favorite ★, Dock pin dot)
 * and missing-ROM visual affordances.
 */
object GridBadgeMetrics {

    enum class BadgeCorner { NONE, TOP_START, TOP_END }

    data class Placement(
        val favoriteCorner: BadgeCorner,
        val dockCorner: BadgeCorner,
    )

    data class Spec(
        val badgeSizePx: Int,
        val dockDotSizePx: Int,
        val insetPx: Int,
        val starTextSp: Float,
    )

    /** 80% opaque dark scrim: guarantees high contrast even over pure white box art. */
    const val BADGE_SCRIM_FILL: Int = 0xCC000000.toInt()
    const val BADGE_SCRIM_STROKE: Int = 0x33FFFFFF
    const val FAVORITE_STAR_COLOR: Int = 0xFFFFD54F.toInt()

    /** Subtle coral-tinted dashed outline for missing-ROM art boxes. */
    const val MISSING_FRAME_STROKE: Int = 0x66FF8A80
    const val MISSING_FRAME_FILL: Int = 0x22FF8A80
    const val MISSING_LABEL_COLOR: Int = UiDimens.COLOR_DESTRUCTIVE

    /**
     * Resolve non-overlapping corners: Favorite claims top-end; Dock pin yields
     * to top-start when Favorite is present, otherwise claims top-end.
     */
    fun placement(isFavorite: Boolean, isPinnedToDock: Boolean): Placement = Placement(
        favoriteCorner = if (isFavorite) BadgeCorner.TOP_END else BadgeCorner.NONE,
        dockCorner = when {
            !isPinnedToDock -> BadgeCorner.NONE
            isFavorite -> BadgeCorner.TOP_START
            else -> BadgeCorner.TOP_END
        },
    )

    /**
     * Compute optical badge size, inner dock dot diameter, corner inset along
     * the rounded tile bisector, and star sp size.
     */
    fun compute(
        cellWidthPx: Int,
        cardRadiusDp: Int,
        density: Float,
    ): Spec {
        val d = density.coerceAtLeast(0.1f)
        val cellWidthDp = (cellWidthPx.coerceAtLeast(1) / d).roundToInt()
        val badgeSizeDp = (cellWidthDp * 0.16f).roundToInt().coerceIn(16, 20)
        val dockDotSizeDp = (badgeSizeDp * 0.44f).roundToInt().coerceIn(7, 9)
        // Nest circular badge comfortably along the card corner's 45° arc.
        val insetDp = (cardRadiusDp.coerceAtLeast(0) * 0.30f).roundToInt().coerceIn(5, 9)
        val starTextSp = (((badgeSizeDp * 0.60f) * 2f).roundToInt() / 2f).coerceIn(9.5f, 12f)

        return Spec(
            badgeSizePx = (badgeSizeDp * d).roundToInt().coerceAtLeast(1),
            dockDotSizePx = (dockDotSizeDp * d).roundToInt().coerceAtLeast(1),
            insetPx = (insetDp * d).roundToInt().coerceAtLeast(1),
            starTextSp = starTextSp,
        )
    }

    /** High-contrast accent dot color legible inside [BADGE_SCRIM_FILL]. */
    fun dockDotColor(accent: Int): Int =
        ThemePack.accentTextColor(accent, 0xFF000000.toInt())

    /** Dimmed badge opacity during move mode on non-lifted drop-target tiles. */
    const val DROP_TARGET_BADGE_ALPHA: Float = 0.45f

    /** Corner-badge opacity for a tile's current [MoveChoreography.SlotRole]. */
    fun badgeAlphaForRole(role: MoveChoreography.SlotRole): Float =
        if (role == MoveChoreography.SlotRole.DROP_TARGET) DROP_TARGET_BADGE_ALPHA else 1f
}
