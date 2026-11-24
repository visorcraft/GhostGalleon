package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.abs
import kotlin.math.roundToInt

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

    /** Perceptually uniform relative luminance targets for normalized platform backdrops. */
    const val TARGET_LIFT_LUMINANCE_OLED: Double = 0.014
    const val TARGET_LIFT_LUMINANCE_STANDARD: Double = 0.020
    const val TARGET_PEAK_LUMINANCE_OLED: Double = 0.028
    const val TARGET_PEAK_LUMINANCE_STANDARD: Double = 0.038
    const val MAX_PEAK_BACKDROP_LUMINANCE: Double = 0.055

    /** Pure OLED black edge anchor. */
    const val OLED_BLACK: Int = 0xFF000000.toInt()

    data class NormalizedPlatformBackdrop(
        val edgeColor: Int,
        val liftBandColor: Int,
        val radialGlowColor: Int,
        val peakCompositedColor: Int,
    )

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
     * already-attached view while system animations are enabled (initial paint
     * and reduced-motion stay instant).
     */
    fun shouldCrossfadeBackdrop(
        previousKey: String?,
        nextKey: String,
        attachedToWindow: Boolean,
        animationsEnabled: Boolean = true,
    ): Boolean = animationsEnabled && attachedToWindow && previousKey != null && previousKey != nextKey

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

    /**
     * Perceptually normalized platform atmospheric backdrop palette. Caps peak
     * backdrop luminance uniformly across bright (yellow/cyan) and dark (blue/
     * indigo) consoles, harmonizes non-OLED backdrops with [themePanelLift],
     * and anchors OLED top/bottom edges strictly to `#FF000000`.
     */
    fun normalizedPlatformGlow(
        platformAccent: Int,
        themePanelLift: Int,
        isOled: Boolean,
    ): NormalizedPlatformBackdrop {
        val edgeColor = if (isOled) {
            OLED_BLACK
        } else {
            ThemePack.blendColors(OLED_BLACK, themePanelLift, 0.30f)
        }
        val liftBase = if (isOled) {
            OLED_BLACK
        } else {
            ThemePack.blendColors(OLED_BLACK, themePanelLift, 0.40f)
        }
        val hue = normalizeHueEnergy(platformAccent)
        val targetLift = if (isOled) TARGET_LIFT_LUMINANCE_OLED else TARGET_LIFT_LUMINANCE_STANDARD
        val targetPeak = if (isOled) TARGET_PEAK_LUMINANCE_OLED else TARGET_PEAK_LUMINANCE_STANDARD

        // Binary-search lift blend fraction to match targetLift perceptually.
        var loF = 0.05f
        var hiF = 0.45f
        var bestLift = ThemePack.blendColors(liftBase, hue, loF)
        var bestLiftErr = abs(ThemePack.relativeLuminance(bestLift) - targetLift)
        repeat(16) {
            val mid = (loF + hiF) * 0.5f
            val cand = ThemePack.blendColors(liftBase, hue, mid)
            val lum = ThemePack.relativeLuminance(cand)
            val err = abs(lum - targetLift)
            if (err < bestLiftErr) {
                bestLift = cand
                bestLiftErr = err
            }
            if (lum < targetLift) loF = mid else hiF = mid
        }

        // Binary-search translucent radial alpha so composited center stays tightly bounded.
        var loA = 0x10
        var hiA = 0x4C
        var bestAlpha = loA
        var bestPeak = ThemePack.blendColors(bestLift, hue, loA / 255f)
        var bestPeakErr = abs(ThemePack.relativeLuminance(bestPeak) - targetPeak)
        repeat(10) {
            val mid = (loA + hiA) / 2
            val cand = ThemePack.blendColors(bestLift, hue, mid / 255f)
            val lum = ThemePack.relativeLuminance(cand)
            val err = abs(lum - targetPeak)
            if (err < bestPeakErr && lum <= MAX_PEAK_BACKDROP_LUMINANCE) {
                bestAlpha = mid
                bestPeak = cand
                bestPeakErr = err
            }
            if (lum < targetPeak) loA = mid + 1 else hiA = mid - 1
        }
        // Final safety guard if targetLift + minimum alpha still hovered near cap.
        if (ThemePack.relativeLuminance(bestPeak) > MAX_PEAK_BACKDROP_LUMINANCE) {
            bestAlpha = 0x10
            bestPeak = ThemePack.blendColors(bestLift, hue, bestAlpha / 255f)
        }

        return NormalizedPlatformBackdrop(
            edgeColor = edgeColor,
            liftBandColor = bestLift,
            radialGlowColor = ThemePack.withAlpha(hue, bestAlpha),
            peakCompositedColor = bestPeak,
        )
    }

    /**
     * Normalize RGB channel peak energy so dark blue/indigo console accents
     * contribute visible atmospheric hue without oversaturating bright hues.
     */
    private fun normalizeHueEnergy(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val maxCh = maxOf(r, maxOf(g, b))
        if (maxCh == 0) return 0xFF808080.toInt()
        val scale = 216f / maxCh.toFloat()
        val nr = (r * scale).roundToInt().coerceIn(0, 255)
        val ng = (g * scale).roundToInt().coerceIn(0, 255)
        val nb = (b * scale).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}
