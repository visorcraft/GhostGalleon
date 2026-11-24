package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMediaTest {

    @Test
    fun `lookupArt matches stem and suffixes`() {
        val docs = listOf(
            DocFile("smw-image.png", "content://art/smw-image.png", "images/smw-image.png"),
            DocFile("other.png", "content://art/other.png", "images/other.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        assertEquals("content://art/smw-image.png", LocalMedia.lookupArt(idx, "", "smw"))
    }

    @Test
    fun `screenshotUri prefers screenshot suffix over bare art`() {
        val docs = listOf(
            DocFile("zelda.png", "content://art/zelda.png", "images/zelda.png"),
            DocFile("zelda-screenshot.png", "content://shot/zelda.png", "screenshots/zelda-screenshot.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        val art = LocalMedia.lookupArt(idx, "", "zelda")
        assertEquals("content://art/zelda.png", art)
        assertEquals(
            "content://shot/zelda.png",
            LocalMedia.screenshotUri(idx, "", "zelda", art),
        )
    }

    @Test
    fun `screenshotUri null when only art match exists`() {
        val docs = listOf(
            DocFile("zelda.png", "content://art/zelda.png", "images/zelda.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        val art = LocalMedia.lookupArt(idx, "", "zelda")
        assertNull(LocalMedia.screenshotUri(idx, "", "zelda", art))
    }

    @Test
    fun `parseMediaPath accepts nested media screenshots`() {
        val nested = LocalMedia.parseMediaPath("media/screenshots/zelda.png", rootIsPlatform = true)
        assertEquals(Triple("", "screenshots", "zelda.png"), nested)
        val container = LocalMedia.parseMediaPath(
            "snes/media/screenshots/zelda.png",
            rootIsPlatform = false,
        )
        assertEquals(Triple("snes", "screenshots", "zelda.png"), container)
    }

    @Test
    fun `indexImages finds nested screenshot and logo`() {
        val docs = listOf(
            DocFile("zelda.png", "content://art/z.png", "images/zelda.png"),
            DocFile(
                "zelda-screenshot.png",
                "content://shot/z.png",
                "media/screenshots/zelda-screenshot.png",
            ),
            DocFile("zelda-logo.png", "content://logo/z.png", "media/logos/zelda-logo.png"),
        )
        val idx = LocalMedia.indexImages(docs, rootIsPlatform = true)
        val art = LocalMedia.lookupArt(idx, "", "zelda")
        assertEquals("content://art/z.png", art)
        assertEquals(
            "content://shot/z.png",
            LocalMedia.screenshotUri(idx, "", "zelda", art),
        )
        assertEquals("content://logo/z.png", LocalMedia.lookupLogo(idx, "", "zelda"))
    }

    @Test
    fun `indexVideos matches flat and nested media paths`() {
        val flat = listOf(
            DocFile("chrono.mp4", "content://vid/chrono.mp4", "videos/chrono.mp4"),
        )
        val idxFlat = LocalMedia.indexVideos(flat, rootIsPlatform = true)
        assertEquals("content://vid/chrono.mp4", LocalMedia.lookupVideo(idxFlat, "", "chrono"))

        val nested = listOf(
            DocFile(
                "zelda.webm",
                "content://vid/zelda.webm",
                "snes/media/videos/zelda.webm",
            ),
        )
        val idxNested = LocalMedia.indexVideos(nested, rootIsPlatform = false)
        assertEquals(
            "content://vid/zelda.webm",
            LocalMedia.lookupVideo(idxNested, "snes", "zelda"),
        )
        assertNull(LocalMedia.lookupVideo(idxNested, "snes", "missing"))
    }

    @Test
    fun `hasDotSegment and zero-alloc parse paths match split parity on 20000 DocFiles`() {
        val edgePaths = listOf(
            "",
            ".",
            ".DS_Store",
            "snes/.DS_Store",
            "snes/._Chrono.smc",
            "snes/.hidden/game.smc",
            "snes/game.smc",
            "snes//game.smc",
            "images/zelda.png",
            "media/screenshots/zelda.png",
            "images/extra/zelda.png",
            "snes/images/zelda.png",
            "snes/media/screenshots/zelda.png",
            "snes/videos/zelda.mp4",
            "snes/media/videos/zelda.mp4",
            "snes/a/b/c/too-deep.png",
        )
        for (p in edgePaths) {
            assertEquals(
                "hasDotSegment mismatch for '$p'",
                p.split('/').any { it.startsWith('.') },
                LocalMedia.hasDotSegment(p),
            )
        }
        assertEquals(Triple("", "images", "zelda.png"), LocalMedia.parseMediaPath("images/extra/zelda.png", rootIsPlatform = true))
        assertNull(LocalMedia.parseMediaPath("snes/a/b/c/too-deep.png", rootIsPlatform = false))
        assertEquals("snes" to "zelda.mp4", LocalMedia.parseVideoPath("snes/videos/zelda.mp4", rootIsPlatform = false))
        assertEquals("" to "zelda.mp4", LocalMedia.parseVideoPath("media/videos/zelda.mp4", rootIsPlatform = true))

        // 20,000 walked DocFiles (mixed ROMs, covers, screenshots, videos, dotfiles).
        val docs = ArrayList<DocFile>(20_000)
        for (i in 0 until 15_000) {
            val plat = if (i % 2 == 0) "snes" else "gba"
            val ext = if (plat == "snes") "smc" else "gba"
            docs.add(DocFile("Game_$i.$ext", "content://doc/$plat/$i.$ext", "$plat/Game_$i.$ext"))
        }
        for (i in 0 until 4_000) {
            val plat = if (i % 2 == 0) "snes" else "gba"
            docs.add(DocFile("Game_$i.png", "content://art/$plat/$i.png", "$plat/images/Game_$i.png"))
        }
        for (i in 0 until 800) {
            docs.add(DocFile("Game_$i.mp4", "content://vid/snes/$i.mp4", "snes/media/videos/Game_$i.mp4"))
        }
        for (i in 0 until 200) {
            docs.add(DocFile("._Game_$i.smc", "content://junk/$i", "snes/._Game_$i.smc"))
        }

        repeat(2) {
            LocalMedia.indexImages(docs, rootIsPlatform = false)
            LocalMedia.indexVideos(docs, rootIsPlatform = false)
        }
        val t0 = System.nanoTime()
        val imgIdx = LocalMedia.indexImages(docs, rootIsPlatform = false)
        val vidIdx = LocalMedia.indexVideos(docs, rootIsPlatform = false)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        println("LocalMediaTest (20,000 DocFiles indexImages+indexVideos): ${"%.2f".format(elapsedMs)}ms")
        assertEquals(4_000, imgIdx.size)
        assertEquals(800, vidIdx.size)
        assertEquals("content://art/snes/0.png", LocalMedia.lookupArt(imgIdx, "snes", "Game_0"))
        assertEquals("content://vid/snes/0.mp4", LocalMedia.lookupVideo(vidIdx, "snes", "Game_0"))
        org.junit.Assert.assertTrue("20k LocalMedia index too slow: ${"%.2f".format(elapsedMs)}ms", elapsedMs < 80.0)
    }
}
