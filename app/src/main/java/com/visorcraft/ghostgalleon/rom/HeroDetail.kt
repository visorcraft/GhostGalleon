package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.text

/**
 * Pure hero/detail lines for ROM selection: platform, player, description,
 * and screenshot URI. Host-tested; no Android view types.
 */
object HeroDetail {

    /** Non-blank description for display, or null when absent/whitespace. */
    fun descriptionText(description: String?): String? =
        description?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Preferred/installed player label, e.g. "Player: Eden" or
     * "Player: RetroArch (Snes9x) (not installed)" / null when no platform.
     */
    fun playerLine(
        platform: Platform?,
        preferredPlayerId: String?,
        installed: (packageName: String) -> Boolean,
    ): UiText? {
        if (platform == null) return null
        val resolved = PlayerResolver.resolve(platform, preferredPlayerId, installed)
        if (resolved != null) {
            val preferred = preferredPlayerId?.let { PlayerResolver.byId(platform, it) }
            return if (preferred != null &&
                !installed(PlayerResolver.packageName(preferred)) &&
                preferred.id != resolved.id
            ) {
                text(R.string.format_player_default_offline, resolved.displayName)
            } else {
                text(R.string.format_player, resolved.displayName)
            }
        }
        val fallback = preferredPlayerId?.let { PlayerResolver.byId(platform, it) }
            ?: platform.player
        return text(R.string.format_player_not_installed, fallback.displayName)
    }

    fun platformLine(platform: Platform?, platformId: String): String =
        platform?.displayName ?: platformId

    /**
     * Compact hero subline for short dual panels: join platform, play meta,
     * and player into one " · " line so three labels do not stack vertically.
     * [playerLabel] should be the bare player name (no "Player:" prefix).
     */
    fun compactSubline(
        platformLabel: String?,
        playMeta: UiText?,
        playerLabel: String?,
    ): UiText = joinText(
        listOfNotNull(
            platformLabel?.trim()?.takeIf { it.isNotEmpty() }?.let(::dynamicText),
            playMeta,
            playerLabel?.trim()?.takeIf { it.isNotEmpty() }?.let(::dynamicText),
        ),
        " · ",
    )

    /**
     * Bare preferred/installed player display name (no "Player:" prefix),
     * for [compactSubline]. Null when platform is null.
     */
    fun playerShortName(
        platform: Platform?,
        preferredPlayerId: String?,
        installed: (packageName: String) -> Boolean,
    ): String? {
        if (platform == null) return null
        return PlayerResolver.resolve(platform, preferredPlayerId, installed)?.displayName
            ?: (preferredPlayerId?.let { PlayerResolver.byId(platform, it) }
                ?: platform.player).displayName
    }

    /** Screenshot URI to bind, or null. */
    fun screenshotUri(rom: RomEntry): String? =
        rom.screenshotUri?.takeIf { it.isNotBlank() }

    /** Logo / wheel URI when present. */
    fun logoUri(rom: RomEntry): String? =
        rom.logoUri?.takeIf { it.isNotBlank() }

    /** Video snap URI when present. */
    fun videoUri(rom: RomEntry): String? =
        rom.videoUri?.takeIf { it.isNotBlank() }

    /**
     * Compact metadata line: year · genre · developer (non-blank parts only).
     * Null when nothing to show.
     */
    fun metadataLine(rom: RomEntry): String? {
        val parts = listOfNotNull(
            rom.year?.trim()?.takeIf { it.isNotEmpty() },
            rom.genre?.trim()?.takeIf { it.isNotEmpty() },
            rom.developer?.trim()?.takeIf { it.isNotEmpty() },
            rom.rating?.trim()?.takeIf { it.isNotEmpty() }?.let { "★ $it" },
        )
        return parts.joinToString(" · ").ifEmpty { null }
    }
}
