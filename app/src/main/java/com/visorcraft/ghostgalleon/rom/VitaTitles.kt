package com.visorcraft.ghostgalleon.rom

/**
 * PlayStation Vita title-id folders (PCSE00001, PCSG00000, …) and
 * `eboot.bin` dumps. Pure; host-tested.
 */
object VitaTitles {

    private val TITLE_ID = Regex("^[A-Z]{4}[0-9]{5}$")

    fun isTitleId(name: String): Boolean =
        TITLE_ID.matches(name.trim().uppercase())

    /** First title-id path segment, if any. */
    fun titleIdIn(relativePath: String): String? =
        relativePath.split('/').firstOrNull { isTitleId(it) }?.uppercase()

    fun isEboot(fileName: String): Boolean =
        fileName.equals("eboot.bin", ignoreCase = true)
}
