package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import java.net.URLDecoder

/** Human-readable labels for SAF tree URIs. Pure string logic, host-testable. */
object TreeLabels {

    /**
     * `content://…/tree/7F7E-2949%3Aroms` → "roms (SD card)";
     * `…/tree/primary%3AEmulation%2FROMs` → "ROMs". Non-primary volumes are
     * removable, hence the "SD card" suffix.
     */
    fun label(treeUri: String): UiText {
        val raw = treeUri.substringAfter("/tree/", "")
        if (raw.isEmpty()) return dynamicText(treeUri)
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }
            .getOrDefault(raw)
        val volume = decoded.substringBefore(':', "")
        if (volume.isEmpty()) return dynamicText(treeUri)
        val path = decoded.substringAfter(':', "")
        val segment = path.trimEnd('/').substringAfterLast('/').ifEmpty { volume }
        return if (volume == "primary") dynamicText(segment)
        else text(R.string.settings_tree_sd_card, segment)
    }
}
