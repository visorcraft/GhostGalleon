package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.CinemaFrame
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens

/**
 * Pure visual metrics, status colors, and layout specs for `NOW_PLAYING`,
 * `PLAY_HUD`, Achievement Theater, RAM Tracker, Save-Slot / Cinema strips,
 * and Winlator Cockpit / Second-Seat controls. Host-tested.
 */
object PlayHudMetrics {

    /** Emerald status dot/accent for an active live game session. */
    const val SESSION_ACTIVE_COLOR: Int = 0xFF69F0AE.toInt()

    /** Warm amber status dot/accent for a paused session. */
    const val SESSION_PAUSED_COLOR: Int = 0xFFFFB74D.toInt()

    /** Diameter (dp) of the live/paused session indicator dot. */
    const val SESSION_DOT_DP: Int = 7

    /** Corner radius (dp) for framed Play HUD & Now Playing cover art thumbnails. */
    const val ART_CORNER_RADIUS_DP: Int = 16

    /** Corner radius (dp) for Save-Slot and Cinema thumbnail cells. */
    const val SLOT_CORNER_RADIUS_DP: Int = 10

    /** Warm gold outline and star tint for a pinned Cinema save-state slot. */
    const val SLOT_PINNED_GOLD: Int = 0xFFFFD54F.toInt()

    /** Thumbnail cell edge (dp) for Save-Slot and Cinema strips. */
    fun slotCellSizeDp(compact: Boolean): Int = if (compact) 40 else 48

    data class SlotBorderSpec(
        val strokeDp: Int,
        val color: Int,
    )

    /** Pure border hierarchy for Save-Slot / Cinema cells: pinned gold > latest accent > subtle hairline. */
    fun slotBorderSpec(
        isPinned: Boolean,
        isLatest: Boolean,
        accentColor: Int,
        hairlineColor: Int,
    ): SlotBorderSpec = when {
        isPinned -> SlotBorderSpec(strokeDp = 2, color = SLOT_PINNED_GOLD)
        isLatest -> SlotBorderSpec(strokeDp = 2, color = accentColor)
        else -> SlotBorderSpec(strokeDp = 1, color = hairlineColor)
    }

    /** Stable diff key for Cinema strip repainting, including pinned and latest captured slots. */
    fun cinemaPaintKey(
        frames: List<CinemaFrame>,
        pinnedSlot: Int?,
        lastSlot: Int?,
    ): String {
        val body = frames.joinToString(",") { "${it.slot}:${it.savedAtMs}:${it.thumbKey ?: ""}" }
        return "$body|p=${pinnedSlot ?: -1}|l=${lastSlot ?: -1}"
    }

    /** Corner radius (dp) for the Achievement Theater badge frame. */
    const val THEATER_BADGE_RADIUS_DP: Int = 8

    /** Height (dp) of the Achievement Theater horizontal progress meter. */
    const val THEATER_METER_HEIGHT_DP: Int = 4

    /** Alpha for inactive RAM tracker pills (readable pill silhouette, clearly dimmed). */
    const val TRACKER_OFF_ALPHA: Float = 0.42f

    data class TrackerCellStyle(
        val fillColor: Int,
        val strokeColor: Int,
        val textColor: Int,
        val alpha: Float,
    )

    /** Pure pill styling for RAM tracker cells separating acquired/on items from off slots. */
    fun trackerCellStyle(
        on: Boolean,
        accentColor: Int,
        idleSurface: Int,
        sunkenSurface: Int,
        hairlineColor: Int,
    ): TrackerCellStyle = if (on) {
        val fill = CompanionChromeErgonomics.activeChipFill(idleSurface, accentColor)
        val liftedAccent = ThemePack.accentTextColor(accentColor, fill)
        TrackerCellStyle(
            fillColor = fill,
            strokeColor = (liftedAccent and 0x00FFFFFF) or (0x88 shl 24),
            textColor = UiDimens.ALPHA_PRIMARY,
            alpha = 1f,
        )
    } else {
        TrackerCellStyle(
            fillColor = sunkenSurface,
            strokeColor = hairlineColor,
            textColor = UiDimens.ALPHA_MUTED,
            alpha = TRACKER_OFF_ALPHA,
        )
    }

    /** Live/paused indicator color (WCAG AA verified across all dark surfaces). */
    fun sessionStatusColor(isActive: Boolean): Int =
        if (isActive) SESSION_ACTIVE_COLOR else SESSION_PAUSED_COLOR

    /** Artwork edge (dp) for Now Playing card in compact banner vs full tab. */
    fun nowPlayingArtDp(compact: Boolean): Int = if (compact) 56 else 96

    /** Artwork edge (dp) for Play HUD header in compact vs standard panels. */
    fun playHudArtDp(compact: Boolean): Int = if (compact) 68 else 96

    data class HeaderSpec(
        val artDp: Int,
        val titleSp: Float,
        val playerSp: Float,
        val padHorizontalDp: Int,
        val padVerticalDp: Int,
    )

    /** Pure typography and spacing tokens for the Play HUD header card. */
    fun headerSpec(compact: Boolean): HeaderSpec = if (compact) {
        HeaderSpec(
            artDp = playHudArtDp(compact = true),
            titleSp = 20f,
            playerSp = 12f,
            padHorizontalDp = 14,
            padVerticalDp = 10,
        )
    } else {
        HeaderSpec(
            artDp = playHudArtDp(compact = false),
            titleSp = 26f,
            playerSp = 14f,
            padHorizontalDp = 16,
            padVerticalDp = 12,
        )
    }

    /** Subtle hairline border for the sunken session timer pill tinted by session state. */
    fun sessionPillBorderColor(isActive: Boolean): Int =
        (0x48 shl 24) or (sessionStatusColor(isActive) and 0x00FFFFFF)

    /** Accessible foreground text color for the session timer pill over [surfaceColor]. */
    fun sessionTimerTextColor(isActive: Boolean, surfaceColor: Int = ThemePack.DEFAULT_DARK_SURFACE): Int {
        val candidate = if (isActive) 0xE6FFFFFF.toInt() else SESSION_PAUSED_COLOR
        val opaque = (0xFF shl 24) or (candidate and 0x00FFFFFF)
        return if (ThemePack.contrastRatio(opaque, surfaceColor) >= ThemePack.MIN_TEXT_CONTRAST) {
            candidate
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    /** Normalize a touch coordinate into 0..1 launch-display trackpad space. */
    fun normalizeTrackpadPoint(x: Float, y: Float, width: Int, height: Int): Pair<Float, Float> {
        if (width <= 0 || height <= 0) return 0.5f to 0.5f
        return (x / width.toFloat()).coerceIn(0f, 1f) to (y / height.toFloat()).coerceIn(0f, 1f)
    }

    /** Minimum height (dp) for Second-Seat touch cluster buttons (always >= 44dp). */
    fun seatButtonMinHeightDp(compact: Boolean): Int = if (compact) 44 else 50

    /** Press/idle `(fillColor, strokeColor)` pair for Second-Seat cluster buttons. */
    fun seatButtonStyle(
        pressed: Boolean,
        accentColor: Int,
        idleSurface: Int,
        hairlineColor: Int,
    ): Pair<Int, Int> = if (pressed) {
        val fill = CompanionChromeErgonomics.activeChipFill(idleSurface, accentColor)
        val stroke = ThemePack.accentTextColor(accentColor, fill)
        fill to stroke
    } else {
        idleSurface to hairlineColor
    }
}
