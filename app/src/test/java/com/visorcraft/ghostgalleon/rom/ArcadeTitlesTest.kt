package com.visorcraft.ghostgalleon.rom

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeTitlesTest {

    @After
    fun tearDown() {
        ArcadeTitles.installOverlay(emptyMap())
        ArcadeTitles.installBundled(emptyMap())
    }

    @Test
    fun `bundled asset gzip contains the FBNeo catalog`() {
        val candidates = listOf(
            java.io.File("app/src/main/assets/arcade_titles.tsv.gz"),
            java.io.File("src/main/assets/arcade_titles.tsv.gz"),
            java.io.File("../src/main/assets/arcade_titles.tsv.gz"),
        )
        val file = candidates.first { it.isFile }
        file.inputStream().use { ArcadeTitles.loadBundledGzip(it) }
        assertTrue(ArcadeTitles.bundledCount() > 18000)
        assertEquals(
            "Metal Slug - Super Vehicle-001",
            ArcadeTitles.displayName("mslug"),
        )
    }

    @Test
    fun `known stems become titles`() {
        assertEquals("Metal Slug", ArcadeTitles.displayName("mslug"))
        assertEquals("Street Fighter II", ArcadeTitles.displayName("SF2"))
        assertEquals("Mortal Kombat II", ArcadeTitles.displayName("mk2"))
        assertEquals("DoDonPachi", ArcadeTitles.displayName("ddonpach"))
        assertEquals("unknownzip", ArcadeTitles.displayName("unknownzip"))
        assertTrue(ArcadeTitles.knownCount() > 200)
    }

    @Test
    fun `bundled titles win over the built-in map and overlay wins over bundled`() {
        ArcadeTitles.installBundled(mapOf("mslug" to "Metal Slug Bundled"))
        assertEquals("Metal Slug Bundled", ArcadeTitles.displayName("mslug"))
        ArcadeTitles.installOverlay(mapOf("mslug" to "Metal Slug Overlay"))
        assertEquals("Metal Slug Overlay", ArcadeTitles.displayName("mslug"))
        ArcadeTitles.installOverlay(emptyMap())
        assertEquals("Metal Slug Bundled", ArcadeTitles.displayName("mslug"))
    }

    @Test
    fun `loadBundledGzip reads a gzip tsv stream`() {
        val raw = "foo\tFoo Title\n".toByteArray(Charsets.UTF_8)
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(raw) }
        ArcadeTitles.loadBundledGzip(java.io.ByteArrayInputStream(gz.toByteArray()))
        assertEquals("Foo Title", ArcadeTitles.displayName("foo"))
        assertEquals(1, ArcadeTitles.bundledCount())
    }

    @Test
    fun `parseTsv maps short names and skips junk`() {
        val map = ArcadeTitles.parseTsv(
            "mslug\tMetal Slug - Super Vehicle-001\n" +
                "\n" +
                "badline\n" +
                "sf2\tStreet Fighter II\n",
        )
        assertEquals("Metal Slug - Super Vehicle-001", map["mslug"])
        assertEquals("Street Fighter II", map["sf2"])
        assertEquals(2, map.size)
    }

    @Test
    fun `overlay wins over the built-in map then clear falls back`() {
        ArcadeTitles.installOverlay(mapOf("mslug" to "METAL SLUG (DAT)"))
        assertEquals("METAL SLUG (DAT)", ArcadeTitles.displayName("MSLUG"))
        assertEquals(1, ArcadeTitles.overlayCount())
        ArcadeTitles.installOverlay(emptyMap())
        assertEquals("Metal Slug", ArcadeTitles.displayName("mslug"))
        assertEquals(0, ArcadeTitles.overlayCount())
    }

    @Test
    fun `relabel rematches arcade stems and leaves other platforms alone`() {
        ArcadeTitles.installOverlay(mapOf("foo" to "Foo Fighter"))
        val arcade = RomEntry(
            id = "arcade:arcade/foo.zip",
            name = "foo",
            platformId = "arcade",
            uri = "content://x/foo.zip",
            path = "/storage/x/arcade/foo.zip",
        )
        val snes = RomEntry(
            id = "snes:snes/foo.smc",
            name = "foo",
            platformId = "snes",
            uri = "content://x/foo.smc",
            path = "/storage/x/snes/foo.smc",
        )
        val out = ArcadeTitles.relabel(listOf(arcade, snes))
        assertEquals("Foo Fighter", out[0].name)
        assertEquals("foo", out[1].name)
        assertSame(snes, out[1])
    }

    @Test
    fun `relabel returns the same list when nothing would change`() {
        val e = RomEntry(
            id = "arcade:arcade/mslug.zip",
            name = "Metal Slug",
            platformId = "arcade",
            uri = "u",
            path = "/x/mslug.zip",
        )
        val list = listOf(e)
        assertSame(list, ArcadeTitles.relabel(list))
    }

    @Test
    fun `conservative relabel skips gamelist titles`() {
        ArcadeTitles.installBundled(mapOf("mslug" to "Metal Slug - Super Vehicle-001"))
        val custom = RomEntry(
            id = "arcade:arcade/mslug.zip",
            name = "Metal Slug (World)",
            platformId = "arcade",
            uri = "u",
            path = "/x/mslug.zip",
        )
        val list = listOf(custom)
        assertSame(list, ArcadeTitles.relabel(list, onlyFallbackNames = true))
        val stem = custom.copy(name = "mslug")
        assertEquals(
            "Metal Slug - Super Vehicle-001",
            ArcadeTitles.relabel(listOf(stem), onlyFallbackNames = true)[0].name,
        )
        val compiled = custom.copy(name = "Metal Slug")
        assertEquals(
            "Metal Slug - Super Vehicle-001",
            ArcadeTitles.relabel(listOf(compiled), onlyFallbackNames = true)[0].name,
        )
    }

    @Test
    fun `stemOf prefers the path filename then the id`() {
        val fromPath = RomEntry(
            id = "arcade:ignored",
            name = "n",
            platformId = "arcade",
            uri = "u",
            path = "/roms/arcade/mslug.zip",
        )
        assertEquals("mslug", ArcadeTitles.stemOf(fromPath))
        val fromId = RomEntry(
            id = "arcade:arcade/sf2.zip",
            name = "n",
            platformId = "arcade",
            uri = "u",
            path = null,
        )
        assertEquals("sf2", ArcadeTitles.stemOf(fromId))
    }
}
