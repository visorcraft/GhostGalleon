package com.visorcraft.ghostgalleon.rom

/**
 * Cheap metadata from a ROM filename when gamelist.xml is missing.
 * Pure; host-tested.
 */
object FilenameMeta {

    private val YEAR = Regex("""\((19\d{2}|20\d{2})\)""")

    /** Four-digit year in parentheses, e.g. `Zelda (1991)` → `"1991"`. */
    fun yearFromLabel(label: String): String? =
        YEAR.find(label)?.groupValues?.get(1)
}
