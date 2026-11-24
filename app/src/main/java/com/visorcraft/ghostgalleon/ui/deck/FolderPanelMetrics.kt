package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.ui.UiDimens
import kotlin.math.roundToInt

/**
 * Pure, host-tested responsive grid layout and motion tokens for [FolderPanel].
 *
 * Adapts cleanly from compact ~540dp Sugar panels (3 columns) to wide 16:9
 * handhelds like Odin 2 Portal (4 columns when a folder holds 7+ members),
 * guaranteeing thumbnails stay comfortably sized and strictly within card bounds.
 */
object FolderPanelMetrics {

    const val WIDE_PANEL_MIN_DP: Int = 700
    const val WIDE_FOUR_COLUMN_MIN_MEMBERS: Int = 7
    const val WIDE_COLUMNS: Int = 4

    const val CARD_MAX_WIDTH_3COL_DP: Int = 420
    const val CARD_MAX_WIDTH_4COL_DP: Int = 520

    const val MIN_THUMB_DP: Int = 56
    const val MAX_THUMB_DP: Int = 96

    const val CELL_FOCUS_SCALE: Float = 1.045f
    const val CELL_FOCUS_ELEVATION_DP: Float = 6f
    const val FOCUS_ANIM_MS: Long = 100L

    data class LayoutSpec(
        val columns: Int,
        val cardWidthPx: Int,
        val thumbSizePx: Int,
        val maxScrollHeightPx: Int,
    )

    /**
     * Compute responsive column count, modal card width, thumbnail size, and
     * max scroll viewport height for [memberCount] items on a display of
     * [screenWidthPx] x [screenHeightPx] at [density].
     */
    fun compute(
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float,
        memberCount: Int,
    ): LayoutSpec {
        val d = density.coerceAtLeast(0.1f)
        val safeW = screenWidthPx.coerceAtLeast(1)
        val safeH = screenHeightPx.coerceAtLeast(1)
        val widthDp = (safeW / d).roundToInt()

        val columns = if (widthDp >= WIDE_PANEL_MIN_DP && memberCount >= WIDE_FOUR_COLUMN_MIN_MEMBERS) {
            WIDE_COLUMNS
        } else {
            FolderPanelNav.COLUMNS
        }
        val maxCardDp = if (columns >= WIDE_COLUMNS) CARD_MAX_WIDTH_4COL_DP else CARD_MAX_WIDTH_3COL_DP
        val cardWidthPx = OverlayPrimitives.responsiveCardWidthPx(
            screenWidthPx = safeW,
            density = d,
            maxDp = maxCardDp,
            widthFrac = 0.88f,
            minMarginDp = UiDimens.SPACE_LG,
        )

        val cardPadPx = (UiDimens.SPACE_LG * 2 * d).roundToInt()
        val gridPadPx = (UiDimens.SPACE_XS * 2 * d).roundToInt()
        val colGapsPx = (UiDimens.SPACE_SM * (columns - 1) * d).roundToInt()
        val cellInnerPadPx = (UiDimens.SPACE_XS * 2 * d).roundToInt()
        val usableRowPx = (cardWidthPx - cardPadPx - gridPadPx - colGapsPx).coerceAtLeast(columns)
        val slotWidthPx = usableRowPx / columns
        val maxThumbInSlotPx = (slotWidthPx - cellInnerPadPx).coerceAtLeast(1)

        val minThumbPx = (MIN_THUMB_DP * d).roundToInt().coerceAtLeast(1)
        val maxThumbPx = (MAX_THUMB_DP * d).roundToInt().coerceAtLeast(minThumbPx)
        val thumbSizePx = maxThumbInSlotPx.coerceIn(minOf(minThumbPx, maxThumbInSlotPx), maxThumbPx)

        val heightFrac = if (widthDp >= WIDE_PANEL_MIN_DP) 0.54f else 0.50f
        val maxScrollHeightPx = OverlayPrimitives.maxModalHeightPx(
            screenHeightPx = safeH,
            density = d,
            heightFrac = heightFrac,
            minDp = 140,
        )

        return LayoutSpec(
            columns = columns,
            cardWidthPx = cardWidthPx,
            thumbSizePx = thumbSizePx,
            maxScrollHeightPx = maxScrollHeightPx,
        )
    }
}
