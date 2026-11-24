package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
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
    const val MIRROR_FOCUS_SCALE: Float = 1.02f
    const val MIRROR_FOCUS_ELEVATION_DP: Float = 4f
    const val MIRROR_MIN_HEIGHT_DP: Int = 44
    const val FOCUS_ANIM_MS: Long = 100L
    const val EMPTY_MEDALLION_SIZE_DP: Int = 56
    const val EMPTY_MEDALLION_ICON_DP: Int = 26

    data class MirrorStyle(
        val fillColor: Int,
        val strokeColor: Int,
        val strokeDp: Int,
        val textColor: Int,
        val bold: Boolean,
        val scale: Float,
        val elevationDp: Float,
    )

    /** Visual tokens for the Mirror-to-collection footer CTA in focused vs idle state. */
    fun mirrorButtonStyle(
        focused: Boolean,
        accent: Int,
        surfaceFill: Int,
        chipIdleFill: Int,
        hairlineColor: Int,
    ): MirrorStyle = if (focused) {
        val fill = MoveChoreography.liftedWashFill(accent, surfaceFill)
        MirrorStyle(
            fillColor = fill,
            strokeColor = MoveChoreography.liftedBorderColor(accent, surfaceFill),
            strokeDp = 3,
            textColor = ThemePack.onFillTextColor(fill),
            bold = true,
            scale = MIRROR_FOCUS_SCALE,
            elevationDp = MIRROR_FOCUS_ELEVATION_DP,
        )
    } else {
        MirrorStyle(
            fillColor = chipIdleFill,
            strokeColor = hairlineColor,
            strokeDp = 1,
            textColor = UiDimens.ALPHA_PRIMARY,
            bold = false,
            scale = 1f,
            elevationDp = 0f,
        )
    }

    data class LayoutSpec(
        val columns: Int,
        val cardWidthPx: Int,
        val thumbSizePx: Int,
        val maxScrollHeightPx: Int,
    )

    data class ScrollCueState(
        val showTopCue: Boolean,
        val showBottomCue: Boolean,
    )

    /** Determine whether top/bottom scroll-more cues should show for [scrollY]. */
    fun scrollCueState(
        scrollY: Int,
        contentHeightPx: Int,
        viewportHeightPx: Int,
        tolerancePx: Int = 2,
    ): ScrollCueState {
        if (contentHeightPx <= viewportHeightPx + tolerancePx || viewportHeightPx <= 0) {
            return ScrollCueState(showTopCue = false, showBottomCue = false)
        }
        val clampedY = scrollY.coerceAtLeast(0)
        return ScrollCueState(
            showTopCue = clampedY > tolerancePx,
            showBottomCue = (contentHeightPx - (clampedY + viewportHeightPx)) > tolerancePx,
        )
    }

    /** WCAG-AA accent glyph tint inside the sunken empty-folder medallion. */
    fun emptyMedallionTint(accent: Int, sunkenFill: Int): Int =
        ThemePack.accentTextColor(accent, sunkenFill)

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
