package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Pure, host-tested header framing and accessibility helpers for
 * [GameDeck]'s `showDetails` dialog. Pairs rounded artwork with WCAG-legible
 * platform/type badges, concise year/genre subtitles, and helper-row labels.
 */
object DetailsCardFormatter {

    /** Rounded thumbnail dimension (dp) in the details dialog header. */
    const val HEADER_THUMB_DP: Int = 64

    /** Thumbnail corner radius (dp) matching card framing. */
    const val HEADER_THUMB_CORNER_DP: Int = 12

    /** Readable body copy line spacing multiplier. */
    const val BODY_LINE_SPACING_MULT: Float = 1.24f

    data class HeaderSpec(
        val title: String,
        val badgeText: UiText,
        val badgeDotColor: Int?,
        val showFavoriteStar: Boolean,
        val subtitle: UiText?,
    )

    /**
     * Build structured header metadata for a ROM or App details dialog.
     * - ROMs display their platform short badge + WCAG-lifted platform color dot.
     * - Apps display the localized `App` badge without a platform dot.
     * - Optional subtitle pairs release year and primary genre token when present.
     */
    fun headerSpec(
        title: String,
        platformId: String?,
        favorite: Boolean,
        year: String?,
        genre: String?,
        surfaceColor: Int,
    ): HeaderSpec {
        val pid = platformId?.trim()?.takeIf { it.isNotEmpty() }
        val badgeText = if (pid != null) {
            dynamicText(PlatformLook.filterBadge(pid))
        } else {
            text(R.string.label_app)
        }
        val dotColor = pid?.let {
            ThemePack.accentTextColor(PlatformLook.accentColor(it), surfaceColor)
        }
        val cleanYear = LibraryBrowse.parseYear(year)?.toString()
            ?: year?.trim()?.takeIf { it.isNotEmpty() }
        val primaryGenre = LibraryBrowse.genreTokens(genre).firstOrNull()
        val subtitle = when {
            cleanYear != null && primaryGenre != null ->
                text(R.string.format_dot_pair, dynamicText(cleanYear), dynamicText(primaryGenre))
            cleanYear != null -> dynamicText(cleanYear)
            primaryGenre != null -> dynamicText(primaryGenre)
            else -> null
        }
        return HeaderSpec(
            title = title,
            badgeText = badgeText,
            badgeDotColor = dotColor,
            showFavoriteStar = favorite,
            subtitle = subtitle,
        )
    }

    /** Localized TalkBack label for the Play-host helper picker row. */
    fun helperRowAccessibilityText(currentHelperLabel: String): UiText =
        text(
            R.string.format_colon_pair,
            text(R.string.settings_play_host_helper),
            dynamicText(currentHelperLabel),
        )
}
