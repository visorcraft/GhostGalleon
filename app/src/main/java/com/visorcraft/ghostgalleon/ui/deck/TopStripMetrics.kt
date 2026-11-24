package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.SelectionStrip
import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Pure responsive layout metrics and accent styling for the single-display
 * `TOP_STRIP` selection header (720p–1440p landscape handhelds). Host-tested.
 */
object TopStripMetrics {

    data class Spec(
        val padHorizontalDp: Int,
        val padVerticalDp: Int,
        val artGapDp: Int,
        val artSizeDp: Int,
        val artCornerRadiusDp: Int,
        val artElevationDp: Float,
        val titleSp: Float,
        val subtitleSp: Float,
        val detailSp: Float,
        val raSp: Float,
        val raBarWidthDp: Int,
        val raBarHeightDp: Int,
        val fontScale: Float = 1f,
    )

    /** Narrow width threshold (dp) below standard 720p/1080p landscape handhelds. */
    const val WIDTH_STANDARD_DP = 640f

    /** Wide width threshold (dp) for 1440p / large landscape displays. */
    const val WIDTH_WIDE_DP = 900f

    /** Corner radius (dp) shared by TOP_STRIP cover art tile, clip outline, and rim. */
    const val ART_CORNER_RADIUS_DP = 14

    /** Frosted pedestal backing for app/brand icons inside `TOP_STRIP`. */
    const val APP_PEDESTAL_FILL: Int = 0x18FFFFFF

    /** Subtle elevation (dp) for app/brand pedestals so they stay unified with ROM tiles. */
    const val APP_ART_ELEVATION_DP: Float = 4f

    /** Platform color dot diameter (dp) prepended to `TOP_STRIP` ROM subtitle. */
    const val PLATFORM_DOT_SIZE_DP: Int = 6

    /** Crossfade duration (ms) for async cover-art reveal in `TOP_STRIP`. */
    const val ASYNC_ART_FADE_MS: Long = 120L

    fun forWidth(windowWidthDp: Float, fontScale: Float = 1f): Spec {
        val w = windowWidthDp.coerceAtLeast(1f)
        val clampedScale = fontScale.coerceIn(0.85f, 1.35f)
        val padV = when {
            clampedScale > 1.25f -> 6
            clampedScale > 1.02f -> 8
            else -> 10
        }
        val raw = when {
            w < WIDTH_STANDARD_DP -> Spec(
                padHorizontalDp = 12,
                padVerticalDp = padV,
                artGapDp = 12,
                artSizeDp = SelectionStrip.ART_SIZE_DP,
                artCornerRadiusDp = ART_CORNER_RADIUS_DP,
                artElevationDp = 6f,
                titleSp = 17f,
                subtitleSp = 12.5f,
                detailSp = 11.5f,
                raSp = 11.5f,
                raBarWidthDp = 64,
                raBarHeightDp = 4,
                fontScale = clampedScale,
            )
            w < WIDTH_WIDE_DP -> Spec(
                padHorizontalDp = 18,
                padVerticalDp = padV,
                artGapDp = 16,
                artSizeDp = SelectionStrip.ART_SIZE_DP,
                artCornerRadiusDp = ART_CORNER_RADIUS_DP,
                artElevationDp = 6f,
                titleSp = 19f,
                subtitleSp = 13.5f,
                detailSp = 12f,
                raSp = 12f,
                raBarWidthDp = 84,
                raBarHeightDp = 4,
                fontScale = clampedScale,
            )
            else -> Spec(
                padHorizontalDp = 24,
                padVerticalDp = padV,
                artGapDp = 20,
                artSizeDp = SelectionStrip.ART_SIZE_DP,
                artCornerRadiusDp = ART_CORNER_RADIUS_DP,
                artElevationDp = 8f,
                titleSp = 20f,
                subtitleSp = 14f,
                detailSp = 12.5f,
                raSp = 12.5f,
                raBarWidthDp = 104,
                raBarHeightDp = 4,
                fontScale = clampedScale,
            )
        }
        val maxArt = (SelectionStrip.STRIP_HEIGHT_DP - raw.padVerticalDp * 2).coerceAtLeast(48)
        return if (raw.artSizeDp <= maxArt) raw else raw.copy(artSizeDp = maxArt)
    }

    /** Inner padding (dp) framing app/brand icons inside the frosted `TOP_STRIP` pedestal. */
    fun appArtInsetDp(artSizeDp: Int): Int =
        (artSizeDp * 0.095f).toInt().coerceIn(6, 10)

    /** Unified non-zero frame elevation (dp) for ROM tiles and app/brand pedestals. */
    fun artFrameElevationDp(isRom: Boolean, spec: Spec): Float =
        if (isRom) spec.artElevationDp else APP_ART_ELEVATION_DP

    /** Unified 1dp rim hairline color framing both ROM tiles and app/brand pedestals. */
    fun artFrameRimColor(accentColor: Int): Int =
        CompanionHeroStyle.artRimColor(accentColor)

    /** WCAG AA-lifted platform accent color for the `TOP_STRIP` console dot over [surfaceColor]. */
    fun platformDotColor(
        platformAccent: Int,
        surfaceColor: Int = ThemePack.DEFAULT_DARK_SURFACE,
    ): Int = ThemePack.accentTextColor(platformAccent, surfaceColor)

    /**
     * Reveal fade duration (ms) for `TOP_STRIP` cover art: snaps immediately (`0L`) on
     * synchronous cache hits (zero 120Hz flicker), detached views, or reduced motion;
     * fades smoothly over [ASYNC_ART_FADE_MS] on true async arrivals.
     */
    fun asyncArtFadeMs(
        syncHit: Boolean,
        attached: Boolean,
        animEnabled: Boolean,
    ): Long = if (syncHit) {
        0L
    } else if (MotionPolicy.shouldAnimate(ASYNC_ART_FADE_MS, animEnabled, attached)) {
        ASYNC_ART_FADE_MS
    } else {
        0L
    }

    /**
     * Subtle 1dp bottom hairline separating `TOP_STRIP` from the deck below,
     * gently tinted with the selected platform/theme [accentColor].
     */
    fun stripDividerColor(accentColor: Int): Int {
        val r = ((255 * 55 + ((accentColor shr 16) and 0xFF) * 45) / 100).coerceIn(0, 255)
        val g = ((255 * 55 + ((accentColor shr 8) and 0xFF) * 45) / 100).coerceIn(0, 255)
        val b = ((255 * 55 + (accentColor and 0xFF) * 45) / 100).coerceIn(0, 255)
        return (0x38 shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Conservative vertical height estimate (dp) verifying fit within [SelectionStrip.STRIP_HEIGHT_DP]. */
    fun estimatedTextColumnHeightDp(spec: Spec, fontScale: Float = spec.fontScale): Int {
        val scale = fontScale.coerceIn(0.85f, 1.35f)
        val lineMult = 1.24f
        val title = (spec.titleSp * scale * lineMult).toInt()
        val sub = (spec.subtitleSp * scale * lineMult).toInt() + 2
        val detail = (spec.detailSp * scale * lineMult).toInt() + 2
        val ra = (spec.raSp * scale * lineMult).toInt() + 2
        return title + sub + detail + ra + spec.padVerticalDp * 2
    }

    /** TalkBack summary joining non-blank [title] and [subtitle]. */
    fun accessibilitySummary(title: String, subtitle: String): String {
        val t = title.trim()
        val s = subtitle.trim()
        return when {
            t.isNotEmpty() && s.isNotEmpty() && !t.equals(s, ignoreCase = true) -> "$t, $s"
            t.isNotEmpty() -> t
            else -> s
        }
    }
}
