package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens

/**
 * Pure visual craft policy for the dual-screen Companion Hero: cover art
 * framing/rim/shadow colors, cancelable in-place transition gating, and
 * RetroAchievements mini progress bar state. Host-tested.
 */
object CompanionHeroStyle {

    /** Warm gold accent for 100% mastered RetroAchievements progress bars. */
    const val MASTERY_GOLD: Int = 0xFFFFD54F.toInt()

    /** Standard rounded corner radius (dp) matching [com.visorcraft.ghostgalleon.art.ArtTile]. */
    const val ART_CORNER_RADIUS_DP: Int = 24

    /** Duration (ms) for cancelable in-place selection text/art settle transitions. */
    const val HERO_CROSSFADE_MS: Long = 115L

    /** Duration (ms) for ambient panel background glow crossfade on console/app change. */
    const val PANEL_GLOW_CROSSFADE_MS: Long = 160L

    /** Base focal radial halo alpha before high-luminance damping. */
    const val PANEL_GLOW_FOCAL_ALPHA: Int = 0x34

    /** Base wide atmospheric glow alpha before high-luminance damping. */
    const val PANEL_GLOW_AMBIENT_ALPHA: Int = 0x26

    /** Duration (ms) for async wide HERO banner reveal fade-in. */
    const val BANNER_REVEAL_MS: Long = 140L

    /** Duration (ms) for off-UI animated WebP layer fade-in on the empty brand scene. */
    const val BRAND_ANIM_FADE_MS: Long = 220L

    /** Translucent dark scrim fill seating the empty brand scene's idle prompt pill. */
    const val IDLE_PROMPT_SCRIM_COLOR: Int = 0x88000000.toInt()

    /** Legible muted light foreground for the idle prompt pill over animated sea waves. */
    const val IDLE_PROMPT_TEXT_COLOR: Int = 0xCCFFFFFF.toInt()

    /** Initial alpha when settling in-place hero text on selection change. */
    const val HERO_START_ALPHA: Float = 0.62f

    /** Initial alpha when settling in-place hero artwork on selection change. */
    const val ART_START_ALPHA: Float = 0.74f

    /** Initial vertical optical settle offset (dp) for hero text. */
    const val HERO_SETTLE_TRANSLATION_DP: Float = 4f

    /** Height (dp) of the horizontal RetroAchievements mini progress track. */
    const val RA_BAR_HEIGHT_DP: Int = 4

    /** Width (dp) of the RetroAchievements mini track on compact (~540dp) vs tall panels. */
    fun raBarWidthDp(compact: Boolean): Int = if (compact) 120 else 172

    /** Elevation (dp) for hero cover art scaled cleanly with [artSizeDp]. */
    fun artElevationDp(artSizeDp: Int): Float = when {
        artSizeDp <= 140 -> 6f
        artSizeDp < 200 -> 8f
        else -> 12f
    }

    /**
     * Subtle 1dp specular hairline tint framing hero artwork: blends 72% white
     * with 28% platform/app [accentColor] at ~24% opacity so cover edges read
     * crisply against OLED black without hard neon outlines.
     */
    fun artRimColor(accentColor: Int): Int {
        val r = ((255 * 72 + ((accentColor shr 16) and 0xFF) * 28) / 100).coerceIn(0, 255)
        val g = ((255 * 72 + ((accentColor shr 8) and 0xFF) * 28) / 100).coerceIn(0, 255)
        val b = ((255 * 72 + (accentColor and 0xFF) * 28) / 100).coerceIn(0, 255)
        return (0x3D shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Tinted spot/ambient shadow color (API 28+) creating a soft halo under cover art. */
    fun artShadowColor(accentColor: Int): Int =
        (0xA6 shl 24) or (accentColor and 0x00FFFFFF)

    /**
     * Dual-stop focal/atmospheric glow colors (inner halo, wide atmospheric bloom).
     * High-luminance accents (e.g. OLED `#E0E0E0` or bright platforms) dampen alpha
     * so top chrome behind `StatusPill` never washes out.
     */
    fun panelGlowColors(glowColor: Int): Pair<Int, Int> {
        val highLum = ThemePack.isHighLuminanceAccent(glowColor)
        val focalAlpha = if (highLum) (PANEL_GLOW_FOCAL_ALPHA * 0.60).toInt() else PANEL_GLOW_FOCAL_ALPHA
        val ambientAlpha = if (highLum) (PANEL_GLOW_AMBIENT_ALPHA * 0.60).toInt() else PANEL_GLOW_AMBIENT_ALPHA
        return ThemePack.withAlpha(glowColor, focalAlpha) to ThemePack.withAlpha(glowColor, ambientAlpha)
    }

    /** Gate for smooth ambient panel-glow crossfades (`MotionPolicy` + prior background check). */
    fun shouldCrossfadePanelGlow(
        hasPreviousBackground: Boolean,
        animatorsEnabled: Boolean,
        attached: Boolean,
    ): Boolean = hasPreviousBackground &&
        MotionPolicy.shouldAnimate(PANEL_GLOW_CROSSFADE_MS, animatorsEnabled, attached)

    /** Inner padding (dp) framing transparent Android app icons inside the app hero pedestal. */
    fun appPedestalInsetDp(artSizeDp: Int): Int =
        (artSizeDp * 0.11f).toInt().coerceIn(12, 26)

    /** Concentric corner radius (dp) for custom app icons clipped inside the hero pedestal. */
    fun appPedestalInnerRadiusDp(artSizeDp: Int): Int =
        ThemePack.nestedRadiusDp(
            outerRadiusDp = ART_CORNER_RADIUS_DP,
            insetDp = appPedestalInsetDp(artSizeDp),
            minDp = 8,
        )

    /** Theme-harmonized base fill beneath the radial icon glow on the app hero pedestal. */
    fun appPedestalBaseFill(tokens: ThemeTokens): Int =
        ThemePack.withAlpha(ThemePack.cardFill(tokens), 0xB8)

    /** Subtle radial pedestal bloom color behind an Android app icon (OLED luminance-damped). */
    fun appPedestalGlowColor(accentColor: Int): Int =
        ThemePack.accentGlowColor(accentColor, 0x2E)

    /**
     * Vertical top-to-bottom scrim stops inside wide HERO banners: crystal-clear
     * upper half transitioning smoothly into a deep bottom edge so wide art melts
     * cleanly into the title stack below.
     */
    fun bannerScrimColors(): IntArray = intArrayOf(
        0x00000000,
        0x00000000,
        0x38000000,
        0xC4000000.toInt(),
    )

    /**
     * Pure Porter-Duff `SRC_OVER` alpha composite of translucent ARGB [foregroundArgb]
     * over opaque [backgroundOpaqueRgb]. Host-tested for WCAG contrast audits.
     */
    fun compositeOver(foregroundArgb: Int, backgroundOpaqueRgb: Int): Int {
        val alpha = ((foregroundArgb ushr 24) and 0xFF) / 255.0
        val inv = 1.0 - alpha
        val fr = (foregroundArgb shr 16) and 0xFF
        val fg = (foregroundArgb shr 8) and 0xFF
        val fb = foregroundArgb and 0xFF
        val br = (backgroundOpaqueRgb shr 16) and 0xFF
        val bg = (backgroundOpaqueRgb shr 8) and 0xFF
        val bb = backgroundOpaqueRgb and 0xFF
        val r = (fr * alpha + br * inv).toInt().coerceIn(0, 255)
        val g = (fg * alpha + bg * inv).toInt().coerceIn(0, 255)
        val b = (fb * alpha + bb * inv).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Programmatic dusk-horizon gradient endpoints (sky horizon tint, sea top tint,
     * hairline sheen) when animated clouds/sea raw assets are absent on the empty
     * companion panel. Keeps OLED black at top & bottom edges.
     */
    data class HorizonPalette(
        val skyMid: Int,
        val skyHorizon: Int,
        val horizonLine: Int,
        val seaTop: Int,
    )

    fun brandHorizonPalette(panelLift: Int, accentColor: Int): HorizonPalette {
        fun blend(a: Int, b: Int, bWeightPct: Int): Int {
            val aW = 100 - bWeightPct
            val r = (((a shr 16) and 0xFF) * aW + ((b shr 16) and 0xFF) * bWeightPct) / 100
            val g = (((a shr 8) and 0xFF) * aW + ((b shr 8) and 0xFF) * bWeightPct) / 100
            val bl = ((a and 0xFF) * aW + (b and 0xFF) * bWeightPct) / 100
            return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
        }
        val skyMid = blend(0xFF000000.toInt(), panelLift, 75)
        val skyHorizon = blend(panelLift, accentColor, 18)
        val horizonLine = (0x33 shl 24) or (blend(0xFFFFFFFF.toInt(), accentColor, 35) and 0x00FFFFFF)
        val seaTop = blend(0xFF000000.toInt(), skyHorizon, 55)
        return HorizonPalette(
            skyMid = skyMid,
            skyHorizon = skyHorizon,
            horizonLine = horizonLine,
            seaTop = seaTop,
        )
    }

    /**
     * Gate for in-place selection micro-transitions: animate only when binding
     * a new title whose displayed string actually changed, never on same-ROM
     * refreshes (RA fetch, favorite toggle, repeat NAV ticks).
     */
    fun shouldAnimateSelection(
        alreadyBound: Boolean,
        previousText: CharSequence?,
        nextText: String,
    ): Boolean = !alreadyBound &&
        nextText.isNotEmpty() &&
        previousText?.toString() != nextText

    data class RaMeterState(
        val visible: Boolean,
        val fraction: Float,
        val mastered: Boolean,
        val fillColor: Int,
    )

    /**
     * Pure RetroAchievements progress bar presentation derived from [progress].
     * Guarantees WCAG AA legible [RaMeterState.fillColor] over [surfaceColor]
     * and highlights 100% completion in [MASTERY_GOLD].
     */
    fun raMeterState(
        progress: RaProgress?,
        hasCredentials: Boolean,
        accentColor: Int,
        surfaceColor: Int = ThemePack.DEFAULT_DARK_SURFACE,
    ): RaMeterState {
        if (!hasCredentials || progress == null || progress.isEmpty || progress.numPossible <= 0) {
            return RaMeterState(
                visible = false,
                fraction = 0f,
                mastered = false,
                fillColor = ThemePack.accentTextColor(accentColor, surfaceColor),
            )
        }
        val fraction = (progress.numAwarded.toFloat() / progress.numPossible.toFloat())
            .coerceIn(0f, 1f)
        val mastered = progress.numAwarded >= progress.numPossible
        val fill = if (mastered) {
            MASTERY_GOLD
        } else {
            ThemePack.accentTextColor(accentColor, surfaceColor)
        }
        return RaMeterState(
            visible = true,
            fraction = fraction,
            mastered = mastered,
            fillColor = fill,
        )
    }
}
