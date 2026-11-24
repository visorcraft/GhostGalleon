package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack

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

    /** Live/paused indicator color (WCAG AA verified across all dark surfaces). */
    fun sessionStatusColor(isActive: Boolean): Int =
        if (isActive) SESSION_ACTIVE_COLOR else SESSION_PAUSED_COLOR

    /** Artwork edge (dp) for Now Playing card in compact banner vs full tab. */
    fun nowPlayingArtDp(compact: Boolean): Int = if (compact) 56 else 96

    /** Artwork edge (dp) for Play HUD header in compact vs standard panels. */
    fun playHudArtDp(compact: Boolean): Int = if (compact) 68 else 96

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
}
