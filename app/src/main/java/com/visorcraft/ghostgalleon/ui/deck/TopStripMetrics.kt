package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.SelectionStrip

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
    )

    /** Narrow width threshold (dp) below standard 720p/1080p landscape handhelds. */
    const val WIDTH_STANDARD_DP = 640f

    /** Wide width threshold (dp) for 1440p / large landscape displays. */
    const val WIDTH_WIDE_DP = 900f

    /** Corner radius (dp) shared by TOP_STRIP cover art tile, clip outline, and rim. */
    const val ART_CORNER_RADIUS_DP = 14

    fun forWidth(windowWidthDp: Float): Spec {
        val w = windowWidthDp.coerceAtLeast(1f)
        val raw = when {
            w < WIDTH_STANDARD_DP -> Spec(
                padHorizontalDp = 12,
                padVerticalDp = 10,
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
            )
            w < WIDTH_WIDE_DP -> Spec(
                padHorizontalDp = 18,
                padVerticalDp = 10,
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
            )
            else -> Spec(
                padHorizontalDp = 24,
                padVerticalDp = 10,
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
            )
        }
        val maxArt = (SelectionStrip.STRIP_HEIGHT_DP - raw.padVerticalDp * 2).coerceAtLeast(48)
        return if (raw.artSizeDp <= maxArt) raw else raw.copy(artSizeDp = maxArt)
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
    fun estimatedTextColumnHeightDp(spec: Spec): Int {
        val lineMult = 1.24f
        val title = (spec.titleSp * lineMult).toInt()
        val sub = (spec.subtitleSp * lineMult).toInt() + 2
        val detail = (spec.detailSp * lineMult).toInt() + 2
        val ra = (spec.raSp * lineMult).toInt() + 2
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
