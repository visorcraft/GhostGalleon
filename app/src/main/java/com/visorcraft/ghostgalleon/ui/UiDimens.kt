package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import android.widget.TextView

/**
 * Density-independent sizing and shared UI alpha/spacing tokens used across
 * decks, overlays, and settings.
 */
object UiDimens {

    /** 100% white — primary titles and labels on dark surfaces. */
    const val ALPHA_PRIMARY: Int = 0xFFFFFFFF.toInt()

    /** 80% white — subtitles and secondary body copy. */
    const val ALPHA_SECONDARY: Int = 0xCCFFFFFF.toInt()

    /** 60% white — metadata, captions, and secondary hints. */
    const val ALPHA_MUTED: Int = 0x99FFFFFF.toInt()

    /** 40% white — footer control hints, placeholders, and disabled labels. */
    const val ALPHA_SUBTLE: Int = 0x66FFFFFF.toInt()

    /** Destructive action red with accessible contrast on dark card surfaces. */
    const val COLOR_DESTRUCTIVE: Int = 0xFFFF8A80.toInt()

    /** Standard 80% black modal scrim backdrop. */
    const val SCRIM_MODAL: Int = 0xCC000000.toInt()

    const val SPACE_XS: Int = 4
    const val SPACE_SM: Int = 8
    const val SPACE_MD: Int = 12
    const val SPACE_LG: Int = 16
    const val SPACE_XL: Int = 24

    /** OpenType feature tag for fixed-width tabular numerals on live readouts. */
    const val TABULAR_NUMS_FEATURE: String = "tnum"

    /** Minimum accessible touch/focus target size in dp. */
    const val TOUCH_MIN_DP: Int = 44

    const val RADIUS_SM: Int = 10
    const val RADIUS_MD: Int = 14
    const val RADIUS_LG: Int = 20

    const val ELEVATION_CARD_DP: Int = 2
    const val ELEVATION_FLOATING_DP: Int = 6

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun dpF(context: Context, value: Int): Float =
        value * context.resources.displayMetrics.density

    fun dpF(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    /** Pixel width for hairline/focus strokes, clamped to at least 1px. */
    fun strokePx(context: Context, dpValue: Int = 1): Int =
        dp(context, dpValue).coerceAtLeast(1)

    /** Enable tabular numerals (`tnum`) so live counters and slider badges do not jitter. */
    fun applyTabularNumerals(view: TextView) {
        view.fontFeatureSettings = TABULAR_NUMS_FEATURE
    }

    /** Pure check for system reduced-motion (`ANIMATOR_DURATION_SCALE == 0`). Host-tested. */
    fun isMotionEnabled(animatorDurationScale: Float): Boolean =
        !animatorDurationScale.isNaN() && animatorDurationScale > 0f

    /** True when system animator duration scale allows subtle motion transitions. */
    fun areAnimationsEnabled(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        return isMotionEnabled(scale)
    }
}

fun Context.dp(value: Int): Int = UiDimens.dp(this, value)

fun Context.dp(value: Float): Int = UiDimens.dp(this, value)

fun Context.dpF(value: Int): Float = UiDimens.dpF(this, value)

fun Context.dpF(value: Float): Float = UiDimens.dpF(this, value)

fun Context.strokePx(dpValue: Int = 1): Int = UiDimens.strokePx(this, dpValue)

fun View.dp(value: Int): Int = context.dp(value)

fun View.dp(value: Float): Int = context.dp(value)

fun View.dpF(value: Int): Float = context.dpF(value)

fun View.dpF(value: Float): Float = context.dpF(value)

fun View.strokePx(dpValue: Int = 1): Int = context.strokePx(dpValue)

fun TextView.useTabularNumerals() = UiDimens.applyTabularNumerals(this)
