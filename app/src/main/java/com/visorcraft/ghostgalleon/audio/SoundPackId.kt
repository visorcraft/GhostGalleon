package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Sound pack selection mirroring Ghost Galleon's visual [ThemePack] IDs,
 * plus [AUTO] (follows active visual theme) and [OFF] (silences pack output).
 */
enum class SoundPackId(val id: String) {
    AUTO("auto"),
    GHOST("ghost"),
    THREEDS("threeds"),
    OLED("oled"),
    NEON("neon"),
    OFF("off");

    /** True when this pack is not [OFF]. */
    val isPlayable: Boolean
        get() = this != OFF

    /**
     * Resolves [AUTO] to the concrete pack matching [themeId] (via [ThemePack.byId]),
     * while leaving explicit packs ([GHOST], [THREEDS], [OLED], [NEON]) and [OFF] unchanged.
     */
    fun resolve(themeId: String?): SoundPackId = when (this) {
        AUTO -> fromThemeId(themeId)
        else -> this
    }

    companion object {
        /** Concrete playable sound packs with distinct acoustic timbres. */
        val CONCRETE_PACKS: List<SoundPackId> = listOf(GHOST, THREEDS, OLED, NEON)

        /** Parses a persisted pack [id], safely falling back to [AUTO] on unknown or blank input. */
        fun fromId(id: String?): SoundPackId {
            val normalized = id?.trim()?.lowercase() ?: return AUTO
            if (normalized.isEmpty()) return AUTO
            return entries.firstOrNull { it.id == normalized } ?: AUTO
        }

        /** Maps a visual [themeId] to its matching concrete [SoundPackId]. */
        fun fromThemeId(themeId: String?): SoundPackId = when (ThemePack.byId(themeId).id) {
            ThemePack.THREEDS.id -> THREEDS
            ThemePack.OLED.id -> OLED
            ThemePack.NEON.id -> NEON
            else -> GHOST
        }
    }
}
