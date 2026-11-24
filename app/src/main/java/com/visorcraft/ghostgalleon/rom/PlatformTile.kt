package com.visorcraft.ghostgalleon.rom

import java.util.Locale
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.abs

/**
 * Programmatic placeholder art for ROM tiles (replaced with real
 * covers): a rounded card with a perceptually balanced vertical sheen in a
 * deterministic per-platform color and the platform short name centered (bold,
 * white, padded, auto-sized to fit). Used by the grid, the picker, the
 * carousel, and the companion hero.
 *
 * [colorFor]/[gradientColorsFor]/[shortText]/[restyleNeeded] are pure (no
 * Android framework classes) so host tests can pin determinism and WCAG
 * contrast across all platforms.
 */
object PlatformTile {

    private const val SATURATION = 0.56f
    private const val MIN_VALUE = 0.24f
    private const val MAX_VALUE = 0.43f
    private const val TARGET_LUMINANCE = 0.078
    private const val TOP_SHEEN_BLEND = 0.08f
    private const val BOTTOM_DEPTH_BLEND = 0.22f
    private const val PAD_H_DP = 5
    private const val PAD_V_DP = 2

    /** Minimum white-text contrast ratio guaranteed across every platform stop. */
    const val MIN_WHITE_CONTRAST: Double = 5.5

    /**
     * Deterministic, perceptually balanced base color for [platformId]:
     * maps `platformId.hashCode()` to hue and solves HSV value to a uniform
     * WCAG relative luminance band (`~0.078`, max channel `<= 110`) so yellow-
     * greens never glare and deep blues never sink to muddy black.
     */
    fun colorFor(platformId: String): Int {
        val hue = ((platformId.hashCode() and 0x7FFFFFFF) % 360).toFloat()
        var lo = MIN_VALUE
        var hi = MAX_VALUE
        repeat(14) {
            val mid = (lo + hi) * 0.5f
            val cand = hsvToColor(hue, SATURATION, mid)
            if (ThemePack.relativeLuminance(cand) < TARGET_LUMINANCE) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return hsvToColor(hue, SATURATION, (lo + hi) * 0.5f)
    }

    /**
     * Subtle 2-stop vertical sheen `[topSheen, bottomDepth]` derived from
     * [colorFor], guaranteeing `>= 5.5:1` white-text contrast on both stops.
     */
    fun gradientColorsFor(platformId: String): IntArray {
        val base = colorFor(platformId)
        val top = ThemePack.blendColors(base, 0xFFFFFFFF.toInt(), TOP_SHEEN_BLEND)
        val bottom = ThemePack.blendColors(base, 0xFF000000.toInt(), BOTTOM_DEPTH_BLEND)
        return intArrayOf(top, bottom)
    }

    /** Composite tag key encoding both [platformId] and [cornerRadiusDp] for [restyle]. */
    internal fun styleKey(platformId: String, cornerRadiusDp: Int): String =
        "$platformId@$cornerRadiusDp"

    /** Pure check: true when a tile's bound tag differs in platform id or corner radius. */
    fun restyleNeeded(
        boundTag: Any?,
        platformId: String,
        cornerRadiusDp: Int = 24,
    ): Boolean = boundTag != styleKey(platformId, cornerRadiusDp)

    /** Tile text: the platform's shortName, uppercased ("SNES", "3DS",
     *  "SWITCH"); unknown ids fall back to the uppercased id itself. */
    fun shortText(platformId: String): String =
        (Platforms.byId(platformId)?.shortName ?: platformId).uppercase(Locale.ROOT)

    /** Rounded platform-colored vertical sheen background, same 24dp card radius
     *  style as TileBackgrounds.card by default. */
    fun background(context: Context, platformId: String, cornerRadiusDp: Int = 24) =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            gradientColorsFor(platformId),
        ).apply {
            cornerRadius =
                cornerRadiusDp * context.resources.displayMetrics.density
        }

    /** The placeholder tile view: auto-sized bold white short name centered
     *  with safe horizontal breathing room on the platform sheen. */
    fun view(context: Context, platformId: String, cornerRadiusDp: Int = 24): TextView {
        val density = context.resources.displayMetrics.density
        val padH = (PAD_H_DP * density).toInt().coerceAtLeast(2)
        val padV = (PAD_V_DP * density).toInt().coerceAtLeast(1)
        return TextView(context).apply {
            text = shortText(platformId)
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setPadding(padH, padV, padH, padV)
            background = background(context, platformId, cornerRadiusDp)
            setTag(R.id.platform_tile_id, styleKey(platformId, cornerRadiusDp))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 8, 28, 1, TypedValue.COMPLEX_UNIT_SP,
            )
        }
    }

    /**
     * Re-text/re-color an existing tile created by [view] in place (zero
     * [GradientDrawable] allocations on platform or corner-radius swaps).
     * Keyed on `(platformId, cornerRadiusDp)` via `R.id.platform_tile_id` so
     * unchanged ticks are O(1) no-ops and `findViewWithTag` identities stay intact.
     */
    fun restyle(
        tile: TextView,
        context: Context,
        platformId: String,
        cornerRadiusDp: Int = 24,
    ) {
        if (!restyleNeeded(tile.getTag(R.id.platform_tile_id), platformId, cornerRadiusDp)) {
            return
        }
        tile.setTag(R.id.platform_tile_id, styleKey(platformId, cornerRadiusDp))
        tile.text = shortText(platformId)
        val radiusPx = cornerRadiusDp * context.resources.displayMetrics.density
        val existing = (tile.background as? GradientDrawable)?.mutate() as? GradientDrawable
        if (existing != null) {
            existing.orientation = GradientDrawable.Orientation.TOP_BOTTOM
            existing.colors = gradientColorsFor(platformId)
            existing.cornerRadius = radiusPx
        } else {
            tile.background = background(context, platformId, cornerRadiusDp)
        }
    }

    // Integer HSV->RGB (h in [0,360)); implemented by hand instead of
    // android.graphics.Color so colorFor stays host-testable.
    private fun hsvToColor(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun channel(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
