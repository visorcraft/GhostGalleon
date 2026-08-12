package com.visorcraft.ghostgalleon.art

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkDownloadTest {

    @Test
    fun `gate requires key first`() {
        assertEquals(
            ArtworkDownload.Gate.NO_KEY,
            ArtworkDownload.gate(hasKey = false, running = false, needsWork = true),
        )
    }

    @Test
    fun `gate busy when a job is already running`() {
        assertEquals(
            ArtworkDownload.Gate.BUSY,
            ArtworkDownload.gate(hasKey = true, running = true, needsWork = true),
        )
    }

    @Test
    fun `gate nothing needed when both slots already cached`() {
        assertEquals(
            ArtworkDownload.Gate.NOTHING_NEEDED,
            ArtworkDownload.gate(hasKey = true, running = false, needsWork = false),
        )
    }

    @Test
    fun `gate start when missing art and idle`() {
        assertEquals(
            ArtworkDownload.Gate.START,
            ArtworkDownload.gate(hasKey = true, running = false, needsWork = true),
        )
    }
}
