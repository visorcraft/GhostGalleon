package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, host-tested state-machine and color-math helpers for [GameDeck]'s
 * atmospheric platform backdrop crossfade and dynamic SAF wallpaper layer
 * reconciliation during in-place chrome/browse updates.
 */
object BackdropTransitionLogic {

    /** Crossfade duration (ms) when switching between platform atmospheric tints. */
    const val BACKDROP_CROSSFADE_MS: Int = 220

    /** Canonical backdrop key representing the default OLED pure-black state. */
    const val DEFAULT_BACKDROP_KEY: String = "__black__"

    /** Default dimmed wallpaper opacity behind carousel cards. */
    const val WALLPAPER_ALPHA: Float = 0.35f

    /** Alpha for centerpiece radial atmospheric wash (~24%). */
    const val RADIAL_GLOW_ALPHA: Int = 0x3D

    /** Percent brightness for the OLED-blended vertical platform lift band. */
    const val LIFT_BAND_PERCENT: Int = 22

    enum class WallpaperAction {
        KEEP,
        REMOVE,
        ATTACH,
        REPLACE,
    }

    /** Trim and collapse blank wallpaper URIs to null. */
    fun normalizeUri(uri: String?): String? = uri?.trim()?.takeIf { it.isNotEmpty() }

    /** Reconcile currently attached wallpaper URI against desired settings URI. */
    fun wallpaperAction(attachedUri: String?, desiredUri: String?): WallpaperAction {
        val current = normalizeUri(attachedUri)
        val next = normalizeUri(desiredUri)
        return when {
            current == null && next == null -> WallpaperAction.KEEP
            current != null && next == null -> WallpaperAction.REMOVE
            current == null && next != null -> WallpaperAction.ATTACH
            current == next -> WallpaperAction.KEEP
            else -> WallpaperAction.REPLACE
        }
    }

    /** Stable key identifying the active atmospheric backdrop layer. */
    fun backdropKey(platformFilter: String?): String =
        platformFilter?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BACKDROP_KEY

    /**
     * Crossfade only when transitioning between two distinct backdrops on an
     * already-attached view (initial paint stays instant).
     */
    fun shouldCrossfadeBackdrop(
        previousKey: String?,
        nextKey: String,
        attachedToWindow: Boolean,
    ): Boolean = attachedToWindow && previousKey != null && previousKey != nextKey

    /** Deep OLED-blended opaque vertical lift band derived from [platformAccent]. */
    fun platformLiftBandColor(platformAccent: Int): Int {
        val r = (((platformAccent shr 16) and 0xFF) * LIFT_BAND_PERCENT / 100).coerceIn(0, 255)
        val g = (((platformAccent shr 8) and 0xFF) * LIFT_BAND_PERCENT / 100).coerceIn(0, 255)
        val b = ((platformAccent and 0xFF) * LIFT_BAND_PERCENT / 100).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Translucent centerpiece radial atmospheric glow derived from [platformAccent]. */
    fun platformRadialGlowColor(platformAccent: Int): Int =
        (platformAccent and 0x00FFFFFF) or (RADIAL_GLOW_ALPHA shl 24)
}
