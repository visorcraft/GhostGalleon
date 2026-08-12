package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.rom.RomEntry

/**
 * Per-ROM display names keyed by [RomEntry.id]. Empty / blank overrides
 * are ignored. Pure; host-tested.
 */
object RomNames {

    fun display(rom: RomEntry, romNames: Map<String, String>): String {
        val override = romNames[rom.id]?.trim().orEmpty()
        return override.ifEmpty { rom.name }
    }

    fun set(
        romNames: Map<String, String>,
        romId: String,
        name: String?,
    ): Map<String, String> {
        val id = romId.trim()
        if (id.isEmpty()) return romNames
        val trimmed = name?.trim().orEmpty()
        return if (trimmed.isEmpty()) romNames - id else romNames + (id to trimmed)
    }

    fun clear(romNames: Map<String, String>, romId: String): Map<String, String> =
        romNames - romId.trim()
}
