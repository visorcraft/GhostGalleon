package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens

/**
 * Pure, host-tested width-budget & slot sizing math for [DockBar].
 *
 * Guarantees the centered floating dock bar never overlaps the bottom-start
 * Swap / bottom-end Settings chrome buttons (`40dp` icon + `8dp` edge + `8dp`
 * clearance = `56dp` per side) on single-screen handhelds (Odin 2 Portal) or
 * wide dual panels, while staying generously sized on narrow secondary panels
 * (One X Sugar ~540dp) where system chrome icons are absent.
 */
object DockBarMetrics {

    /** 40dp chrome button + 8dp screen margin + 8dp breathing gap. */
    const val CHROME_SIDE_INSET_DP: Int = 56

    /** Minimum outer margin on displays without bottom chrome buttons. */
    const val EDGE_MARGIN_DP: Int = 8

    /** Legacy baseline horizontal padding inside the dock pill container on each side. */
    const val BAR_PAD_H_DP: Int = 8

    /** Tight horizontal pill padding on compact/dense 8–9 slot docks. */
    const val BAR_PAD_H_TIGHT_DP: Int = 6

    /** Generous horizontal pill padding when comfy slot margins fit. */
    const val BAR_PAD_H_COMFY_DP: Int = 10

    /** Floating bottom bezel margin under the dock pill. */
    const val BOTTOM_MARGIN_DP: Int = 8

    /** Preferred horizontal margin on each side of a slot when space allows. */
    const val SLOT_MARGIN_COMFY_DP: Int = 6

    /** Tight horizontal margin on each side of a slot on narrow panels. */
    const val SLOT_MARGIN_TIGHT_DP: Int = 3

    /** Minimum comfortable slot size before tightening margins / adapting to visible count. */
    const val MIN_COMFY_SLOT_DP: Int = 40

    /** Hard lower bound so touch/focus targets stay usable on extreme widths. */
    const val MIN_SLOT_DP: Int = 28

    /** Minimum "+" placeholder glyph size in sp for compact Sugar slots. */
    const val MIN_BLANK_PLUS_SP: Float = 15f

    /** Maximum "+" placeholder glyph size in sp for wide Odin 2 Portal slots. */
    const val MAX_BLANK_PLUS_SP: Float = 26f

    /** Dock pill container resting vs focus-active elevation (dp) and active border alpha. */
    const val BAR_ELEVATION_IDLE_DP: Float = 4f
    const val BAR_ELEVATION_ACTIVE_DP: Float = 8f
    const val BAR_ACTIVE_STROKE_ALPHA: Int = 0x88

    /** Dock horizontal wall-clamp micro-bump excursion (dp) and duration (ms). */
    const val EDGE_BUMP_DP: Float = 4f
    const val EDGE_BUMP_DURATION_MS: Long = 120L

    /** Tight-slot focus/lift scale caps so adjacent rings never collide on 8–9 slot docks. */
    const val FOCUS_SCALE_TIGHT_THRESHOLD_DP: Int = 36
    const val FOCUS_SCALE_TIGHT: Float = 1.06f
    const val FOCUS_SCALE_COMFY: Float = 1.08f
    const val LIFT_SCALE_TIGHT: Float = 1.08f
    const val LIFT_SCALE_COMFY: Float = MoveChoreography.DOCK_LIFT_SCALE

    data class BarFocusChrome(
        val active: Boolean,
        val strokeColor: Int,
        val elevationDp: Float,
    )

    /**
     * Pure container chrome tokens for the floating dock pill: lifts elevation
     * `4dp -> 8dp` and switches the 1dp border to a WCAG-accessible translucent
     * accent hairline while focus or move-mode is active inside the dock.
     */
    fun barFocusChrome(
        focused: Int?,
        moving: Int?,
        accent: Int,
        surface: Int,
        hairline: Int,
    ): BarFocusChrome {
        val active = focused != null || moving != null
        if (!active) {
            return BarFocusChrome(
                active = false,
                strokeColor = hairline,
                elevationDp = BAR_ELEVATION_IDLE_DP,
            )
        }
        val accessibleAccent = DeckThemeContrast.accessibleFocusStroke(accent, surface)
        return BarFocusChrome(
            active = true,
            strokeColor = ThemePack.accentHairline(accessibleAccent, BAR_ACTIVE_STROKE_ALPHA),
            elevationDp = BAR_ELEVATION_ACTIVE_DP,
        )
    }

    /** Role-aware tint for blank `+` dock slots painted inside `DockVisualDiff`. */
    fun blankPlusColorForRole(
        role: MoveChoreography.SlotRole,
        accent: Int = ThemePack.GHOST.accentColor,
        surfaceFill: Int = ThemePack.DEFAULT_CARD_FILL,
    ): Int = when (role) {
        MoveChoreography.SlotRole.FOCUSED -> ThemePack.accentTextColor(accent, surfaceFill)
        MoveChoreography.SlotRole.LIFTED -> MoveChoreography.liftedBorderColor(accent, surfaceFill)
        MoveChoreography.SlotRole.DROP_TARGET -> MoveChoreography.dropTargetStrokeColor(accent, surfaceFill)
        MoveChoreography.SlotRole.NORMAL -> UiDimens.ALPHA_SUBTLE
    }

    /**
     * Horizontal wall-clamp bump direction (`-1` left wall, `+1` right wall, `0` none)
     * when horizontal dock navigation stays clamped at the same index.
     */
    fun edgeBumpDirection(fromIndex: Int, toIndex: Int, action: Action): Int {
        if (fromIndex != toIndex) return 0
        return when (action) {
            Action.NAV_LEFT -> -1
            Action.NAV_RIGHT -> +1
            else -> 0
        }
    }

    /** Non-colliding focus scale for a dock slot of [slotSizeDp] dp. */
    fun focusScaleForSlot(slotSizeDp: Int): Float =
        if (slotSizeDp <= FOCUS_SCALE_TIGHT_THRESHOLD_DP) FOCUS_SCALE_TIGHT else FOCUS_SCALE_COMFY

    /** Non-colliding move-mode lift scale for a dock slot of [slotSizeDp] dp. */
    fun liftScaleForSlot(slotSizeDp: Int): Float =
        if (slotSizeDp <= FOCUS_SCALE_TIGHT_THRESHOLD_DP) LIFT_SCALE_TIGHT else LIFT_SCALE_COMFY

    /**
     * Optical inner icon/art pixel size inside a [slotSizePx] dock slot, keeping
     * balanced clearance around the 3dp focus ring on every density.
     */
    fun iconSizePx(slotSizePx: Int): Int {
        val safe = slotSizePx.coerceAtLeast(1)
        return (safe * 40 / 48).coerceIn(1, safe)
    }

    /**
     * Adaptive sp text size for the blank "+" placeholder so tight ~32dp Sugar
     * slots never clip vertically while wide 64dp+ slots stay optically bold.
     */
    fun blankPlusTextSp(slotSizeDp: Int): Float {
        val safeDp = slotSizeDp.coerceAtLeast(MIN_SLOT_DP)
        return (safeDp * 0.44f).coerceIn(MIN_BLANK_PLUS_SP, MAX_BLANK_PLUS_SP)
    }

    data class Spec(
        val slotSizePx: Int,
        val slotMarginHPx: Int,
        val barPadHPx: Int,
        val sideInsetPx: Int,
        val bottomMarginPx: Int,
    ) {
        /** Total rendered width of the dock bar for [visibleSlots] slots. */
        fun barWidthPx(visibleSlots: Int): Int {
            val n = visibleSlots.coerceAtLeast(0)
            return barPadHPx * 2 + n * (slotSizePx + slotMarginHPx * 2)
        }

        /** Optical inner icon/art pixel size for this dock bar spec. */
        fun iconSizePx(): Int = DockBarMetrics.iconSizePx(slotSizePx)
    }

    /**
     * Compute dock slot & margin metrics from window pixel width, density,
     * [suggestedSlotDp] (from `LayoutMetrics`), [visibleSlots] (`4..9`), and
     * whether bottom-edge system chrome buttons share this panel.
     */
    fun compute(
        windowWidthPx: Int,
        density: Float,
        suggestedSlotDp: Int,
        visibleSlots: Int,
        hasChromeIcons: Boolean,
    ): Spec {
        val d = density.coerceAtLeast(0.1f)
        fun dp(v: Int): Int = (v * d).toInt()

        val safeW = windowWidthPx.coerceAtLeast(1)
        val slots = visibleSlots.coerceIn(DockSlots.MIN_VISIBLE, DockSlots.CAPACITY)
        val sideInsetPx = dp(if (hasChromeIcons) CHROME_SIDE_INSET_DP else EDGE_MARGIN_DP)
        val comfyPadHPx = dp(BAR_PAD_H_COMFY_DP)
        val tightPadHPx = dp(BAR_PAD_H_TIGHT_DP)
        val bottomMarginPx = dp(BOTTOM_MARGIN_DP)

        val availablePx = (safeW - sideInsetPx * 2).coerceAtLeast(1)
        val innerComfyPx = (availablePx - comfyPadHPx * 2).coerceAtLeast(1)
        val innerTightPx = (availablePx - tightPadHPx * 2).coerceAtLeast(1)

        val suggestedPx = dp(suggestedSlotDp.coerceAtLeast(MIN_COMFY_SLOT_DP))
        val minComfyPx = dp(MIN_COMFY_SLOT_DP)
        val minHardPx = dp(MIN_SLOT_DP)
        val comfyMarginPx = dp(SLOT_MARGIN_COMFY_DP)
        val tightMarginPx = dp(SLOT_MARGIN_TIGHT_DP)

        // Prefer uniform sizing across 4..9 slots (CAPACITY) with comfy padding
        // & margins whenever all 9 fit cleanly; otherwise tighten bar padding
        // (6dp) and slot margins (3dp) so 8–9 slot Sugar docks get extra room.
        val capComfySlot = (innerComfyPx / DockSlots.CAPACITY) - comfyMarginPx * 2
        val capTightSlot = (innerTightPx / DockSlots.CAPACITY) - tightMarginPx * 2
        val visComfySlot = (innerComfyPx / slots) - comfyMarginPx * 2
        val visTightSlot = (innerTightPx / slots) - tightMarginPx * 2

        val (rawSlotPx, slotMarginHPx, barPadHPx) = when {
            capComfySlot >= minComfyPx ->
                Triple(minOf(suggestedPx, capComfySlot), comfyMarginPx, comfyPadHPx)
            capTightSlot >= minComfyPx ->
                Triple(minOf(suggestedPx, capTightSlot), tightMarginPx, tightPadHPx)
            visComfySlot >= minComfyPx ->
                Triple(minOf(suggestedPx, visComfySlot), comfyMarginPx, comfyPadHPx)
            else ->
                Triple(minOf(suggestedPx, visTightSlot), tightMarginPx, tightPadHPx)
        }

        // Never allow slotSizePx to push the bar past availablePx even on tiny screens.
        val innerPx = (availablePx - barPadHPx * 2).coerceAtLeast(1)
        val maxFitPx = ((innerPx / slots) - slotMarginHPx * 2).coerceAtLeast(1)
        val slotSizePx = rawSlotPx.coerceAtLeast(minOf(minHardPx, maxFitPx)).coerceAtMost(maxFitPx)

        return Spec(
            slotSizePx = slotSizePx,
            slotMarginHPx = slotMarginHPx,
            barPadHPx = barPadHPx,
            sideInsetPx = sideInsetPx,
            bottomMarginPx = bottomMarginPx,
        )
    }
}
