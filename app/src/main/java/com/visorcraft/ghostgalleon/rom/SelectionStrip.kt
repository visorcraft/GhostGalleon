package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.library.RetroAchievements

/** Pure, translation-safe selection model for the single-display hero strip. */
object SelectionStrip {

    data class Model(
        val title: UiText,
        val subtitle: UiText,
        val detail: UiText?,
        val raLine: UiText?,
        val platformId: String?,
        val isRom: Boolean,
        val isEmpty: Boolean,
    )

    fun empty(): Model = Model(
        title = text(R.string.app_name),
        subtitle = text(R.string.deck_select_game_or_app),
        detail = null,
        raLine = null,
        platformId = null,
        isRom = false,
        isEmpty = true,
    )

    fun forApp(label: String): Model = Model(
        title = if (label.isBlank()) text(R.string.label_app) else dynamicText(label),
        subtitle = text(R.string.label_app),
        detail = null,
        raLine = null,
        platformId = null,
        isRom = false,
        isEmpty = false,
    )

    fun forRom(
        rom: RomEntry,
        preferredPlayerId: String?,
        installed: (packageName: String) -> Boolean,
        playMeta: UiText?,
        raProgress: RaProgress?,
        hasRaCredentials: Boolean,
    ): Model {
        val platform = Platforms.byId(rom.platformId)
        val detailParts = listOfNotNull(
            HeroDetail.playerLine(platform, preferredPlayerId, installed),
            playMeta,
        )
        return Model(
            title = dynamicText(rom.name.ifBlank { rom.id }),
            subtitle = dynamicText(HeroDetail.platformLine(platform, rom.platformId)),
            detail = detailParts.takeIf { it.isNotEmpty() }?.let { joinText(it, " · ") },
            raLine = RetroAchievements.heroLine(raProgress, hasRaCredentials),
            platformId = rom.platformId,
            isRom = true,
            isEmpty = false,
        )
    }

    const val STRIP_HEIGHT_DP = 120
    const val ART_SIZE_DP = 88
}
