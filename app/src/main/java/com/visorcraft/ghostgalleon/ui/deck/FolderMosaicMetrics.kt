package com.visorcraft.ghostgalleon.ui.deck

import kotlin.math.roundToInt

/**
 * Pure, host-tested geometry and preview slicing for native 2x2 folder tile
 * mini-mosaics in [GridDeck].
 *
 * Computes optical nested quadrant padding, gap, mini-tile size, and corner
 * radius inside a folder tile's inner icon box across compact (~540dp Sugar)
 * and wide (16:9 Odin 2 Portal) viewports.
 */
object FolderMosaicMetrics {

    /** Maximum member previews shown in a 2x2 folder mosaic. */
    const val MAX_PREVIEW_SLOTS: Int = 4

    /** Quadrant grid dimension (2x2). */
    const val GRID_COLUMNS: Int = 2

    /** Minimum mini-tile size in dp required to render a legible 2x2 mosaic. */
    const val MIN_MOSAIC_TILE_DP: Int = 10

    data class Spec(
        val containerSizePx: Int,
        val innerPadPx: Int,
        val gapPx: Int,
        val miniTilePx: Int,
        val miniCornerRadiusPx: Float,
        val miniCornerRadiusDp: Int,
        val showMosaic: Boolean,
    )

    /**
     * Compute 2x2 mosaic geometry inside a square folder icon box of
     * [iconSizePx] pixels whose outer corner radius is [outerCornerRadiusPx].
     */
    fun compute(
        iconSizePx: Int,
        outerCornerRadiusPx: Float,
        density: Float,
    ): Spec {
        val d = density.coerceAtLeast(0.1f)
        val size = iconSizePx.coerceAtLeast(1)
        val minPadPx = (3f * d).roundToInt().coerceAtLeast(2)
        val maxPadPx = (8f * d).roundToInt().coerceAtLeast(minPadPx)
        val innerPadPx = (size * 0.09f).roundToInt().coerceIn(minPadPx, maxPadPx)

        val minGapPx = (2f * d).roundToInt().coerceAtLeast(1)
        val maxGapPx = (5f * d).roundToInt().coerceAtLeast(minGapPx)
        val gapPx = (size * 0.055f).roundToInt().coerceIn(minGapPx, maxGapPx)

        val available = (size - innerPadPx * 2 - gapPx).coerceAtLeast(2)
        val miniTilePx = (available / GRID_COLUMNS).coerceAtLeast(1)

        val minCornerPx = (3f * d).coerceAtLeast(1f)
        val concentricPx = DeckCornerMetrics.nestedRadiusPx(
            outerRadiusPx = outerCornerRadiusPx,
            insetPx = innerPadPx.toFloat(),
            minRadiusPx = minCornerPx,
        )
        val maxCornerPx = (miniTilePx * 0.36f).coerceAtLeast(1f)
        val miniCornerRadiusPx = concentricPx.coerceAtMost(maxCornerPx)
        val miniCornerRadiusDp = (miniCornerRadiusPx / d).roundToInt().coerceAtLeast(2)

        val minLegiblePx = (MIN_MOSAIC_TILE_DP * d).roundToInt().coerceAtLeast(8)
        val showMosaic = miniTilePx >= minLegiblePx

        return Spec(
            containerSizePx = size,
            innerPadPx = innerPadPx,
            gapPx = gapPx,
            miniTilePx = miniTilePx,
            miniCornerRadiusPx = miniCornerRadiusPx,
            miniCornerRadiusDp = miniCornerRadiusDp,
            showMosaic = showMosaic,
        )
    }

    /** First up to [MAX_PREVIEW_SLOTS] unique non-blank member keys in order. */
    fun previewMembers(members: List<String>): List<String> =
        members.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PREVIEW_SLOTS)
            .toList()

    /** Compact count badge text capped at "99+" so pill width stays bounded. */
    fun countBadgeText(count: Int): String = when {
        count <= 0 -> "0"
        count > 99 -> "99+"
        else -> count.toString()
    }

    /** Proportional font size bounds (sp) for folder count pills across 48..128dp tiles. */
    const val BADGE_MIN_TEXT_SP: Float = 8.5f
    const val BADGE_MAX_TEXT_SP: Float = 11.5f

    data class BadgeSpec(
        val visible: Boolean,
        val text: String,
        val textSp: Float,
        val padHPx: Int,
        val padVPx: Int,
        val minWidthPx: Int,
        val pillHeightPx: Int,
        val cornerRadiusPx: Float,
    )

    /**
     * Pure folder count-pill sizing & visibility spec.
     * Hides the pill on empty folders (`count <= 0`), scales font size smoothly
     * across `48..128dp` icon boxes, and derives an exact half-height capsule radius.
     */
    fun badgeSpec(
        iconSizePx: Int,
        count: Int,
        density: Float,
    ): BadgeSpec {
        val d = density.coerceAtLeast(0.1f)
        val iconDp = iconSizePx.coerceAtLeast(1) / d
        val frac = ((iconDp - 48f) / 80f).coerceIn(0f, 1f)
        val rawSp = BADGE_MIN_TEXT_SP + (BADGE_MAX_TEXT_SP - BADGE_MIN_TEXT_SP) * frac
        val textSp = (kotlin.math.round(rawSp * 2f) / 2f)
            .coerceIn(BADGE_MIN_TEXT_SP, BADGE_MAX_TEXT_SP)

        val padHDp = if (iconDp < 64f) 4f else 5f
        val padVDp = if (iconDp < 64f) 1.5f else 2f
        val padHPx = (padHDp * d).roundToInt().coerceAtLeast(2)
        val padVPx = (padVDp * d).roundToInt().coerceAtLeast(1)
        val textHeightPx = (textSp * d * 1.15f).roundToInt().coerceAtLeast(1)
        val pillHeightPx = textHeightPx + padVPx * 2
        val minWidthPx = maxOf(pillHeightPx, (16f * d).roundToInt())
        val cornerRadiusPx = pillHeightPx * 0.5f

        val visible = count > 0
        return BadgeSpec(
            visible = visible,
            text = if (visible) countBadgeText(count) else "",
            textSp = textSp,
            padHPx = padHPx,
            padVPx = padVPx,
            minWidthPx = minWidthPx,
            pillHeightPx = pillHeightPx,
            cornerRadiusPx = cornerRadiusPx,
        )
    }
}
