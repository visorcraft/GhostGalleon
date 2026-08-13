package com.visorcraft.ghostgalleon.rom

import java.io.File

/** Numbered RetroArch savestate slots. Thumbs are optional; labels stay 1–8. */
object RaStateSlots {
    val SLOTS: List<Int> = (1..8).toList()

    @Suppress("UNUSED_PARAMETER")
    fun slotLabels(readablePngNames: List<String>): List<Int> = SLOTS

    /** Side map of slot → png file name. RA thumbs look like `<stem>.stateN.png`. */
    fun thumbsBySlot(readablePngNames: List<String>): Map<Int, String> {
        val out = linkedMapOf<Int, String>()
        val dotted = Regex("""\.state([1-8])\.png$""", RegexOption.IGNORE_CASE)
        for (name in readablePngNames) {
            val m = dotted.find(name) ?: continue
            out.putIfAbsent(m.groupValues[1].toInt(), name)
        }
        return out
    }

    /** One directory listing. Null / unreadable → empty (numbers only). */
    fun pngNamesIn(statesDir: File): List<String> {
        val names = statesDir.list() ?: return emptyList()
        return names.filter { it.endsWith(".png", ignoreCase = true) }
    }
}
