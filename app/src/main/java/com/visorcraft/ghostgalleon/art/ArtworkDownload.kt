package com.visorcraft.ghostgalleon.art

/**
 * Pure gate for a one-ROM SteamGridDB scrape (slot-menu “Download art”).
 * Host-tested; Android only starts [ScrapeJob].
 */
object ArtworkDownload {

    enum class Gate {
        NO_KEY,
        BUSY,
        NOTHING_NEEDED,
        START,
    }

    fun gate(
        hasKey: Boolean,
        running: Boolean,
        needsWork: Boolean,
    ): Gate {
        if (!hasKey) return Gate.NO_KEY
        if (running) return Gate.BUSY
        if (!needsWork) return Gate.NOTHING_NEEDED
        return Gate.START
    }
}
