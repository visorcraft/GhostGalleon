package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.roundToInt

/**
 * Pure, host-tested badge overlay, cover-art scrim, and TalkBack accessibility
 * helpers for [GameDeck]'s carousel cards. Keeps platform and favorite badges
 * WCAG-legible, hides redundant platform badges when a platform filter is
 * already active, and yields top-end badge space to multi-select cleanly.
 */
object CarouselCardBadges {

    /** High-contrast dark translucent pill fill over cover art. */
    const val PILL_BACKDROP_COLOR: Int = 0xCC101016.toInt()

    /** Subtle 1dp translucent border on dark overlay pills. */
    const val PILL_STROKE_COLOR: Int = 0x2EFFFFFF

    /** Warm gold favorite star tint (>11:1 contrast over [PILL_BACKDROP_COLOR]). */
    const val FAVORITE_BADGE_TINT: Int = 0xFFFFD54F.toInt()

    /** Subtle golden hairline on favorite star pill. */
    const val FAVORITE_STROKE_COLOR: Int = 0x44FFD54F

    /** Cover art bottom gradient stops: transparent top → subtle dark anchor. */
    const val ART_SCRIM_TOP: Int = 0x00000000
    const val ART_SCRIM_BOTTOM: Int = 0x7A000000

    /** Height fraction of cover art covered by the subtle bottom gradient scrim. */
    const val ART_SCRIM_HEIGHT_FRAC: Float = 0.38f

    /** Surface luminance threshold separating dark themes from light custom surfaces. */
    const val LIGHT_SURFACE_LUMINANCE: Double = 0.35

    /** Tactile touch-ripple alpha on dark built-in themes (Ghost, Teal, Neon). */
    const val CARD_RIPPLE_DARK_ALPHA: Int = 0x38

    /** Luminance-damped ripple alpha for bright accents (e.g. OLED `#E0E0E0`). */
    const val CARD_RIPPLE_HIGH_LUMA_ALPHA: Int = 0x26

    /** Subtle dark ripple alpha over custom light card surfaces. */
    const val CARD_RIPPLE_LIGHT_SURFACE_ALPHA: Int = 0x26

    /** Fraction of lifted accent blended into white ripples on dark cards. */
    const val CARD_RIPPLE_ACCENT_BLEND: Float = 0.28f

    /** Persistent foreground layer order on carousel card frames (bottom → top). */
    enum class CardForegroundLayer {
        TOUCH_RIPPLE,
        MULTI_SELECT_OVERLAY,
    }

    /** Declarative layering contract for [GameDeck]'s persistent card foreground. */
    data class CardForegroundPolicy(
        val layers: List<CardForegroundLayer>,
        val showMultiSelectOverlay: Boolean,
    ) {
        val rippleLayerIndex: Int get() = layers.indexOf(CardForegroundLayer.TOUCH_RIPPLE)
        val multiSelectLayerIndex: Int get() = layers.indexOf(CardForegroundLayer.MULTI_SELECT_OVERLAY)
    }

    /** Proportional badge size and optical corner-bisector inset metrics for carousel cards. */
    data class BadgeLayout(
        val favSizeDp: Int,
        val favSizePx: Int,
        val favTextSp: Float,
        val insetDp: Int,
        val insetPx: Int,
        val platformTextSp: Float,
        val platformDotSizePx: Int,
        val platformPadHPx: Int,
        val platformPadVPx: Int,
    )

    data class CardOverlaySpec(
        val platformBadgeText: String?,
        val platformDotColor: Int?,
        val showFavoriteBadge: Boolean,
        val showMultiCheck: Boolean,
    )

    /**
     * Compute which corner overlays should appear on a carousel card.
     * - Platform pill appears for ROMs only when [activePlatformFilter] is not
     *   already pinned to the same platform (avoids visual redundancy).
     * - Favorite star appears in top-end only outside multi-select mode so it
     *   never collides with the selection checkmark badge.
     */
    fun overlaySpec(
        platformId: String?,
        activePlatformFilter: String?,
        favorite: Boolean,
        multiSelectEnabled: Boolean,
        multiSelected: Boolean,
    ): CardOverlaySpec {
        val pid = platformId?.trim()?.takeIf { it.isNotEmpty() }
        val redundantFilter = pid != null &&
            activePlatformFilter != null &&
            activePlatformFilter.equals(pid, ignoreCase = true)
        val badgeText = if (pid != null && !redundantFilter) {
            PlatformLook.filterBadge(pid)
        } else {
            null
        }
        val dotColor = if (pid != null && badgeText != null) {
            ThemePack.accentTextColor(PlatformLook.accentColor(pid), PILL_BACKDROP_COLOR)
        } else {
            null
        }
        return CardOverlaySpec(
            platformBadgeText = badgeText,
            platformDotColor = dotColor,
            showFavoriteBadge = favorite && !multiSelectEnabled,
            showMultiCheck = multiSelectEnabled && multiSelected,
        )
    }

    /** Pure vertical gradient stops for the bottom cover-art vignette scrim. */
    fun artBottomScrimColors(): IntArray = intArrayOf(ART_SCRIM_TOP, ART_SCRIM_BOTTOM)

    /**
     * Show the bottom dark art scrim for ROM box-art cards only — never over
     * transparent Android app icons whose silhouettes would otherwise muddy.
     */
    fun showArtScrim(isRom: Boolean): Boolean = isRom

    /**
     * Proportional corner-badge sizing (`18..24dp`) and optical corner-bisector
     * insets (`5..9dp`) scaled cleanly across `96..260dp` carousel cards and
     * rounded theme radii.
     */
    fun badgeLayout(
        cardSizePx: Int,
        innerArtRadiusDp: Int,
        density: Float,
    ): BadgeLayout {
        val safeDensity = density.coerceAtLeast(0.5f)
        val cardSizeDp = cardSizePx.coerceAtLeast(1) / safeDensity
        val favSizeDp = (cardSizeDp * 0.125f).roundToInt().coerceIn(18, 24)
        val radiusInset = (innerArtRadiusDp.coerceAtLeast(0) * 0.45f).roundToInt()
        val sizeInset = (cardSizeDp * 0.035f).roundToInt()
        val insetDp = maxOf(radiusInset, sizeInset).coerceIn(5, 9)
        val favTextSp = when {
            favSizeDp <= 18 -> 9.5f
            favSizeDp <= 21 -> 10.5f
            else -> 11.5f
        }
        val platformTextSp = when {
            cardSizeDp < 120f -> 9f
            cardSizeDp < 170f -> 10f
            else -> 10.5f
        }
        val dotDp = if (cardSizeDp < 120f) 5 else 6
        val padHDp = if (cardSizeDp < 120f) 5 else 6
        val padVDp = 2
        fun toPx(dp: Int): Int = (dp * safeDensity).roundToInt().coerceAtLeast(1)
        return BadgeLayout(
            favSizeDp = favSizeDp,
            favSizePx = toPx(favSizeDp),
            favTextSp = favTextSp,
            insetDp = insetDp,
            insetPx = toPx(insetDp),
            platformTextSp = platformTextSp,
            platformDotSizePx = toPx(dotDp),
            platformPadHPx = toPx(padHDp),
            platformPadVPx = toPx(padVDp),
        )
    }

    /**
     * Surface- and accent-adaptive touch ripple color for carousel cards.
     * Uses an accent-tinted light highlight on dark surfaces (luminance-damped
     * for high-luminance accents like OLED `#E0E0E0`) and a subtle dark ripple
     * on custom light card surfaces.
     */
    fun cardRippleColor(surfaceFill: Int, accent: Int): Int {
        if (ThemePack.relativeLuminance(surfaceFill) >= LIGHT_SURFACE_LUMINANCE) {
            return ThemePack.withAlpha(0xFF000000.toInt(), CARD_RIPPLE_LIGHT_SURFACE_ALPHA)
        }
        val liftedAccent = ThemePack.accentTextColor(accent, surfaceFill)
        val tinted = ThemePack.blendColors(
            base = 0xFFFFFFFF.toInt(),
            overlay = liftedAccent,
            overlayFraction = CARD_RIPPLE_ACCENT_BLEND,
        )
        val alpha = if (ThemePack.isHighLuminanceAccent(accent)) {
            CARD_RIPPLE_HIGH_LUMA_ALPHA
        } else {
            CARD_RIPPLE_DARK_ALPHA
        }
        return ThemePack.withAlpha(tinted, alpha)
    }

    /**
     * Foreground layering policy for carousel card frames: keeps the rounded
     * touch ripple persistently mounted beneath the multi-select overlay so
     * checking/unchecking mutates layer 1 in-place with zero drawable swaps.
     */
    fun cardForegroundPolicy(
        checkStyle: CarouselMultiSelectLogic.CardCheckStyle,
    ): CardForegroundPolicy = CardForegroundPolicy(
        layers = listOf(
            CardForegroundLayer.TOUCH_RIPPLE,
            CardForegroundLayer.MULTI_SELECT_OVERLAY,
        ),
        showMultiSelectOverlay = checkStyle == CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
    )

    /**
     * Localized TalkBack description combining card title, optional platform
     * badge, favorite state, and multi-select state using existing string
     * formats.
     */
    fun cardAccessibilityText(
        title: String,
        platformBadge: String?,
        favorite: Boolean,
        multiSelected: Boolean,
    ): UiText {
        var desc: UiText = dynamicText(title)
        if (!platformBadge.isNullOrBlank()) {
            desc = text(R.string.format_dot_pair, desc, dynamicText(platformBadge))
        }
        if (favorite) {
            desc = text(R.string.format_dot_pair, desc, text(R.string.action_favorite))
        }
        if (multiSelected) {
            desc = text(R.string.format_selected_check, desc)
        }
        return desc
    }
}
