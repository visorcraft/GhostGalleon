package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.roundToInt

/**
 * Pure, host-tested visual state & color choreography for grid and dock
 * move-mode ("lifted in hand" tile + dashed drop-target slots).
 *
 * Uses pure ARGB integer math (no Android framework dependencies) so all
 * contrast, scale, elevation, and role transitions stay host-tested across
 * Ghost, Teal, OLED, and Neon theme packs.
 */
object MoveChoreography {

    enum class SlotRole {
        NORMAL,
        FOCUSED,
        LIFTED,
        DROP_TARGET,
    }

    data class VisualSpec(
        val role: SlotRole,
        val scale: Float,
        val elevationDp: Float,
        val alpha: Float,
        val dashedDropOutline: Boolean,
    )

    const val PULSE_DURATION_MS: Long = 650L
    const val PULSE_ALPHA_MIN: Float = 0.84f
    const val GRID_LIFT_SCALE: Float = 1.11f
    const val GRID_PULSE_SCALE_MIN: Float = 1.09f
    const val GRID_PULSE_SCALE_MAX: Float = 1.13f
    const val DOCK_LIFT_SCALE: Float = 1.10f
    const val DOCK_PULSE_SCALE_MIN: Float = 1.08f
    const val DOCK_PULSE_SCALE_MAX: Float = 1.12f
    const val GRID_FLOAT_DP: Float = -2.5f
    const val DOCK_FLOAT_DP: Float = -2.0f
    const val DROP_TARGET_SCALE: Float = 0.96f
    const val SETTLE_DURATION_MS: Long = 110L
    const val FOCUS_GAIN_DURATION_MS: Long = 110L
    const val FOCUS_GAIN_OVERSHOOT_TENSION: Float = 0.9f
    const val FOCUS_LOSS_DURATION_MS: Long = 80L

    enum class MotionCurve {
        OVERSHOOT,
        DECELERATE,
    }

    data class SettleMotionSpec(
        val durationMs: Long,
        val curve: MotionCurve,
        val overshootTension: Float = 0f,
    )

    /**
     * Asymmetric focus settle choreography for [GridDeck] tiles:
     * focus gain settles with a subtle `110ms` `OvershootInterpolator(0.9f)` spring,
     * while focus loss snaps back cleanly over `80ms` `DecelerateInterpolator`.
     */
    fun gridSettleMotion(role: SlotRole): SettleMotionSpec = when (role) {
        SlotRole.FOCUSED -> SettleMotionSpec(
            durationMs = FOCUS_GAIN_DURATION_MS,
            curve = MotionCurve.OVERSHOOT,
            overshootTension = FOCUS_GAIN_OVERSHOOT_TENSION,
        )
        SlotRole.DROP_TARGET -> SettleMotionSpec(
            durationMs = SETTLE_DURATION_MS,
            curve = MotionCurve.DECELERATE,
        )
        SlotRole.NORMAL -> SettleMotionSpec(
            durationMs = FOCUS_LOSS_DURATION_MS,
            curve = MotionCurve.DECELERATE,
        )
        SlotRole.LIFTED -> SettleMotionSpec(
            durationMs = SETTLE_DURATION_MS,
            curve = MotionCurve.DECELERATE,
        )
    }

    /** Determine visual role for slot [position] given focus and move state. */
    fun resolveRole(
        position: Int,
        selectedIndex: Int?,
        movingIndex: Int?,
        surfaceFocused: Boolean,
    ): SlotRole = when {
        movingIndex != null ->
            if (position == movingIndex) SlotRole.LIFTED else SlotRole.DROP_TARGET
        surfaceFocused && position == selectedIndex -> SlotRole.FOCUSED
        else -> SlotRole.NORMAL
    }

    /** Visual metrics for a GridDeck slot in [role]. */
    fun gridVisualSpec(role: SlotRole, isBlank: Boolean): VisualSpec = when (role) {
        SlotRole.LIFTED -> VisualSpec(
            role = role,
            scale = GRID_LIFT_SCALE,
            elevationDp = 14f,
            alpha = 1f,
            dashedDropOutline = false,
        )
        SlotRole.FOCUSED -> VisualSpec(
            role = role,
            scale = 1.08f,
            elevationDp = 8f,
            alpha = 1f,
            dashedDropOutline = false,
        )
        SlotRole.DROP_TARGET -> VisualSpec(
            role = role,
            scale = DROP_TARGET_SCALE,
            elevationDp = 0f,
            alpha = if (isBlank) 0.94f else 0.78f,
            dashedDropOutline = true,
        )
        SlotRole.NORMAL -> VisualSpec(
            role = role,
            scale = 1f,
            elevationDp = 0f,
            alpha = 1f,
            dashedDropOutline = false,
        )
    }

    /** Visual metrics for a DockBar slot in [role]. */
    fun dockVisualSpec(role: SlotRole, isBlank: Boolean): VisualSpec = when (role) {
        SlotRole.LIFTED -> VisualSpec(
            role = role,
            scale = DOCK_LIFT_SCALE,
            elevationDp = 10f,
            alpha = 1f,
            dashedDropOutline = false,
        )
        SlotRole.FOCUSED -> VisualSpec(
            role = role,
            scale = 1.08f,
            elevationDp = 6f,
            alpha = 1f,
            dashedDropOutline = false,
        )
        SlotRole.DROP_TARGET -> VisualSpec(
            role = role,
            scale = DROP_TARGET_SCALE,
            elevationDp = 0f,
            alpha = if (isBlank) 0.92f else 0.80f,
            dashedDropOutline = true,
        )
        SlotRole.NORMAL -> VisualSpec(
            role = role,
            scale = 1f,
            elevationDp = 0f,
            alpha = 1f,
            dashedDropOutline = false,
        )
    }

    /** Return [color] with its alpha channel replaced by [alpha] (`0..255`). */
    fun withAlpha(color: Int, alpha: Int): Int =
        ((alpha.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)

    /** Linear RGB blend of [overlay] over [base] by [fraction] (`0f..1f`), opaque output. */
    fun blendRgb(base: Int, overlay: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        val br = (base shr 16) and 0xFF
        val bg = (base shr 8) and 0xFF
        val bb = base and 0xFF
        val or = (overlay shr 16) and 0xFF
        val og = (overlay shr 8) and 0xFF
        val ob = overlay and 0xFF
        val r = (br + (or - br) * t).roundToInt().coerceIn(0, 255)
        val g = (bg + (og - bg) * t).roundToInt().coerceIn(0, 255)
        val b = (bb + (ob - bb) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Luminous WCAG AA border color for the lifted tile over [surfaceFill] so
     * dark accents (e.g. Ghost indigo) remain bright and unmistakable.
     */
    fun liftedBorderColor(accent: Int, surfaceFill: Int): Int =
        ThemePack.accentTextColor(accent, surfaceFill)

    /** Subtle accent-tinted surface fill for the lifted tile (16% luminous wash). */
    fun liftedWashFill(accent: Int, surfaceFill: Int): Int =
        blendRgb(surfaceFill, liftedBorderColor(accent, surfaceFill), 0.16f)

    /** Translucent luminous accent dashed border color for drop-target slots. */
    fun dropTargetStrokeColor(accent: Int, surfaceFill: Int): Int =
        withAlpha(liftedBorderColor(accent, surfaceFill), 0x85)
}
