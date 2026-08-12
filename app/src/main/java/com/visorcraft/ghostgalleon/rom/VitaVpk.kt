package com.visorcraft.ghostgalleon.rom

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Pull `sce_sys/param.sfo` out of a Vita `.vpk` / zip without buffering
 * the whole archive. Pure; host-tested.
 */
object VitaVpk {

    fun paramSfo(zip: InputStream): ByteArray? {
        ZipInputStream(zip.buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/').lowercase()
                if (name == "sce_sys/param.sfo" || name.endsWith("/sce_sys/param.sfo")) {
                    return zin.readBytes()
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return null
    }
}
