package com.visorcraft.ghostgalleon.rom

/**
 * Drop BIOS dumps and redundant disc siblings so the library shows playable
 * masters (.m3u / .cue) instead of every .bin track.
 * Pure; host-tested.
 */
object DiscHygiene {

    private val SKIP_DIRS = setOf(
        "bios", "firmware", "sys", "system", "firmware-install", "firmware_install",
    )

    private val BIOS_STEMS = setOf(
        "bios", "scph1000", "scph1001", "scph1002", "scph3000", "scph3500",
        "scph5000", "scph5500", "scph5501", "scph5502", "scph7000", "scph7001",
        "scph7502", "scph9002", "kick", "kickstart", "erom", "osrom",
        "dc_boot", "dc_flash", "naomi", "awbios",
    )

    /** True when [relativePath] is under a BIOS folder or a known BIOS stem. */
    fun skipPath(relativePath: String): Boolean {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.dropLast(1).any { it.lowercase() in SKIP_DIRS }) return true
        val file = parts.lastOrNull() ?: return false
        val stem = file.substringBeforeLast('.', file).lowercase()
        if (stem in BIOS_STEMS) return true
        if (stem.startsWith("scph") && stem.length <= 10) return true
        return false
    }

    /**
     * Prefer playlist/cue masters: same folder+stem keeps .m3u over .cue/.bin,
     * and .cue over .bin when no playlist exists.
     */
    fun preferDiscMasters(entries: List<RomEntry>): List<RomEntry> {
        if (entries.size < 2) return entries
        val byFolderStem = entries.groupBy { folderStemKey(it) }
        if (byFolderStem.values.none { it.size > 1 }) return entries
        val drop = HashSet<String>()
        for (group in byFolderStem.values) {
            if (group.size < 2) continue
            val byExt = group.associateBy { extOf(it) }
            val master = when {
                "m3u" in byExt -> byExt.getValue("m3u")
                "cue" in byExt -> byExt.getValue("cue")
                "gdi" in byExt -> byExt.getValue("gdi")
                else -> null
            } ?: continue
            for (entry in group) {
                if (entry.id != master.id && extOf(entry) in REDUNDANT) {
                    drop += entry.id
                }
            }
        }
        if (drop.isEmpty()) return entries
        return entries.filter { it.id !in drop }
    }

    private val REDUNDANT = setOf("bin", "img", "iso", "raw", "cue", "gdi")

    private fun folderStemKey(entry: RomEntry): String {
        val rel = relativePathOf(entry)
        val slash = rel.lastIndexOf('/')
        val file = if (slash >= 0) rel.substring(slash + 1) else rel
        val parent = if (slash >= 0) rel.substring(0, slash) else ""
        val stem = file.substringBeforeLast('.', file).lowercase()
        return "${entry.platformId}\u0000${parent.lowercase()}\u0000$stem"
    }

    private fun extOf(entry: RomEntry): String {
        val rel = relativePathOf(entry)
        val file = rel.substringAfterLast('/')
        val dot = file.lastIndexOf('.')
        return if (dot > 0) file.substring(dot + 1).lowercase() else ""
    }

    internal fun relativePathOf(entry: RomEntry): String {
        val prefix = "${entry.platformId}:"
        return if (entry.id.startsWith(prefix)) entry.id.removePrefix(prefix) else entry.id
    }
}
