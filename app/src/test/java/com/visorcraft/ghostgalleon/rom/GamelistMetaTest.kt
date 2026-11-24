package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamelistMetaTest {

    private val sampleXml = """
        <?xml version="1.0"?>
        <gameList>
          <game>
            <path>./Chrono Trigger.smc</path>
            <name>Chrono Trigger</name>
            <desc>Time-travel RPG classic.</desc>
            <image>./media/images/Chrono Trigger.png</image>
          </game>
          <game>
            <path>./hacks/smw.smc</path>
            <name>Super Mario World</name>
            <description>Alternate desc tag.</description>
          </game>
          <game>
            <path>/home/pi/ROMs/nes/mm2.nes</path>
            <title>Mega Man 2</title>
            <desc>Mega Man &amp; the robot masters.</desc>
          </game>
          <folder>
            <path>./hacks</path>
            <name>Hacks</name>
          </folder>
          <game>
            <path></path>
            <name>Missing path</name>
          </game>
        </gameList>
    """.trimIndent()

    @Test
    fun `parse extracts path stem name and description`() {
        val entries = GamelistMeta.parse(sampleXml)
        assertEquals(3, entries.size)

        val chrono = entries[0]
        assertEquals("./Chrono Trigger.smc", chrono.path)
        assertEquals("Chrono Trigger", chrono.stem)
        assertEquals("Chrono Trigger", chrono.name)
        assertEquals("Time-travel RPG classic.", chrono.description)
        assertEquals("./media/images/Chrono Trigger.png", chrono.image)

        val smw = entries[1]
        assertEquals("smw", smw.stem)
        assertEquals("Super Mario World", smw.name)
        assertEquals("Alternate desc tag.", smw.description)
        assertNull(smw.image)

        val mm2 = entries[2]
        assertEquals("mm2", mm2.stem)
        assertEquals("Mega Man 2", mm2.name)
        assertEquals("Mega Man & the robot masters.", mm2.description)
    }

    @Test
    fun `parse ignores folder entries and games without path`() {
        val entries = GamelistMeta.parse(sampleXml)
        assertTrue(entries.none { it.name == "Hacks" })
        assertTrue(entries.none { it.name == "Missing path" })
    }

    @Test
    fun `parse empty or blank xml yields empty list`() {
        assertTrue(GamelistMeta.parse("").isEmpty())
        assertTrue(GamelistMeta.parse("   ").isEmpty())
        assertTrue(GamelistMeta.parse("<gameList></gameList>").isEmpty())
    }

    @Test
    fun `parse handles CDATA description`() {
        val xml = """
            <gameList>
              <game>
                <path>./foo.gb</path>
                <name>Foo</name>
                <desc><![CDATA[Line 1
Line 2 <raw>]]></desc>
              </game>
            </gameList>
        """.trimIndent()
        val entry = GamelistMeta.parse(xml).single()
        assertEquals("Line 1\nLine 2 <raw>", entry.description)
    }

    @Test
    fun `matchByStem is case-insensitive and accepts filename or stem`() {
        val entries = GamelistMeta.parse(sampleXml)

        assertEquals(
            "Chrono Trigger",
            GamelistMeta.matchByStem(entries, "Chrono Trigger")?.name,
        )
        assertEquals(
            "Chrono Trigger",
            GamelistMeta.matchByStem(entries, "chrono trigger.SMC")?.name,
        )
        assertEquals(
            "Super Mario World",
            GamelistMeta.matchByStem(entries, "./hacks/SMW.smc")?.name,
        )
        assertEquals(
            "Mega Man 2",
            GamelistMeta.matchByStem(entries, "mm2.nes")?.name,
        )
        assertNull(GamelistMeta.matchByStem(entries, "missing.smc"))
        assertNull(GamelistMeta.matchByStem(entries, ""))
    }

    @Test
    fun `stemOf strips path and extension`() {
        assertEquals("Chrono Trigger", GamelistMeta.stemOf("./Chrono Trigger.smc"))
        assertEquals("smw", GamelistMeta.stemOf("snes/hacks/smw.smc"))
        assertEquals("mm2", GamelistMeta.stemOf("""C:\ROMs\nes\mm2.nes"""))
        assertEquals("Tetris", GamelistMeta.stemOf("Tetris"))
        assertEquals("Tetris", GamelistMeta.stemOf("Tetris.gb"))
        assertEquals("", GamelistMeta.stemOf(""))
        assertEquals("", GamelistMeta.stemOf("./"))
        assertEquals(".hidden", GamelistMeta.stemOf(".hidden"))
    }

    @Test
    fun `media folder helpers build conventional relative paths`() {
        assertEquals("boxfront", GamelistMeta.mediaFolderKey(MediaFolder.BOXFRONT))
        assertEquals("images", GamelistMeta.mediaFolderKey(MediaFolder.IMAGE))
        assertEquals("screenshots", GamelistMeta.mediaFolderKey(MediaFolder.SCREENSHOT))

        assertEquals(
            "media/boxfront/Chrono Trigger.png",
            GamelistMeta.mediaRelativePath("Chrono Trigger", MediaFolder.BOXFRONT),
        )
        assertEquals(
            "media/images/mm2.jpg",
            GamelistMeta.mediaRelativePath("mm2", MediaFolder.IMAGE, "jpg"),
        )
        assertEquals(
            "media/screenshots/smw.png",
            GamelistMeta.mediaRelativePath("smw", MediaFolder.SCREENSHOT),
        )
        assertEquals("", GamelistMeta.mediaRelativePath("", MediaFolder.BOXFRONT))
        assertEquals("", GamelistMeta.mediaRelativePath("  ", MediaFolder.IMAGE))
    }

    @Test
    fun `tag names are case-insensitive`() {
        val xml = """
            <gameList>
              <GAME>
                <PATH>./Zelda.nes</PATH>
                <NAME>The Legend of Zelda</NAME>
                <DESC>Save the princess.</DESC>
              </GAME>
            </gameList>
        """.trimIndent()
        val entry = GamelistMeta.parse(xml).single()
        assertEquals("Zelda", entry.stem)
        assertEquals("The Legend of Zelda", entry.name)
        assertEquals("Save the princess.", entry.description)
    }

    @Test
    fun `enrichRoms applies title and description from gamelist`() {
        val xml = """
            <gameList>
              <game>
                <path>./Zelda.smc</path>
                <name>The Legend of Zelda</name>
                <desc>Save Hyrule.</desc>
              </game>
            </gameList>
        """.trimIndent()
        val meta = GamelistMeta.parse(xml)
        val roms = listOf(
            RomEntry("snes:Zelda.smc", "Zelda", "snes", "content://z", "/z/Zelda.smc"),
        )
        val out = GamelistMeta.enrichRoms(roms, meta)
        assertEquals("The Legend of Zelda", out[0].name)
        assertEquals("Save Hyrule.", out[0].description)
    }

    @Test
    fun `enrichRoms 10000 ROMs x 10000 gamelist entries preserves first-match precedence under 40ms`() {
        val n = 10_000
        val roms = List(n) { i ->
            RomEntry(
                id = "snes:snes/Game_$i.smc",
                name = "Game_$i",
                platformId = "snes",
                uri = "content://doc/$i",
                path = "/storage/roms/snes/Game_$i.smc",
            )
        }
        val gamelist = ArrayList<GamelistEntry>(n + 2)
        // Duplicate stem for Game_0: first entry must win (exact matchByStem parity).
        gamelist.add(
            GamelistEntry(
                path = "./game_0.smc",
                stem = "game_0",
                name = "First Game 0",
                description = "Winner desc",
                year = "1991",
                genre = "RPG",
                developer = "Nintendo",
                rating = "9.5",
            ),
        )
        for (i in 0 until n) {
            gamelist.add(
                GamelistEntry(
                    path = "./GAME_$i.smc",
                    stem = "GAME_$i",
                    name = "Title $i",
                    description = "Desc $i",
                    year = "${1985 + (i % 30)}",
                    genre = "Action",
                    developer = "Dev ${i % 20}",
                    rating = "8.0",
                ),
            )
        }

        // Warm up JIT.
        repeat(2) { GamelistMeta.enrichRoms(roms, gamelist) }

        var bestNs = Long.MAX_VALUE
        var enriched: List<RomEntry> = emptyList()
        repeat(5) {
            val t0 = System.nanoTime()
            enriched = GamelistMeta.enrichRoms(roms, gamelist)
            val dt = System.nanoTime() - t0
            if (dt < bestNs) bestNs = dt
        }
        val elapsedMs = bestNs / 1_000_000.0
        println("GamelistMetaTest (10k x 10k enrichRoms): ${"%.2f".format(elapsedMs)}ms")

        assertEquals(n, enriched.size)
        assertEquals("First Game 0", enriched[0].name)
        assertEquals("Winner desc", enriched[0].description)
        assertEquals("Title 9999", enriched[9999].name)
        assertEquals("Desc 9999", enriched[9999].description)
        assertTrue("10k x 10k enrichRoms too slow: ${"%.2f".format(elapsedMs)}ms", elapsedMs < 40.0)
    }
}
