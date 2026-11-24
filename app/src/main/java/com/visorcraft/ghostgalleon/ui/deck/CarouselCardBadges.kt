package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.settings.ThemePack

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
