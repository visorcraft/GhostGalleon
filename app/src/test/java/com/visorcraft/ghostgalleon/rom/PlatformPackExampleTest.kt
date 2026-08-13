package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loads the in-repo example pack via the real [PlatformPack.parse] +
 * [PlatformPack.merge] path (same as Settings "Load example pack").
 */
class PlatformPackExampleTest {

    @Test
    fun `bundled pcengine example pack parses and merges`() {
        val candidates = listOf(
            File("app/src/main/assets/platform_packs/pcengine.json"),
            File("src/main/assets/platform_packs/pcengine.json"),
            File("../src/main/assets/platform_packs/pcengine.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("example pack not found; cwd=" + File(".").absolutePath)
        val text = file.readText()
        val parsed = PlatformPack.parse(text)
        assertNotNull(parsed)
        val merged = PlatformPack.merge(Platforms.BUILTIN, parsed!!.platforms)
        assertTrue(merged.any { it.id == "pcengine" })
        val snes = merged.first { it.id == "snes" }
        assertTrue(snes.players.any { it.id == "ra-bsnes" })
        // Builtins preserved.
        assertTrue(merged.any { it.id == "gb" })
    }

    @Test
    fun `pack sessionPolicy parses and omitted field keeps`() {
        val json = """
            {"schemaVersion":1,"platforms":[{
              "id":"nds","displayName":"NDS","shortName":"NDS",
              "folderNames":["nds"],"extensions":["nds"],
              "players":[
                {"id":"melondualds","displayName":"m","component":"a.b/.C",
                 "uriStyle":"URI","sessionPolicy":"YIELD_BOTH"},
                {"id":"other","displayName":"o","component":"c.d/.E","uriStyle":"URI"}
              ]
            }]}
        """.trimIndent()
        val parsed = PlatformPack.parse(json)!!
        val players = parsed.platforms.first().players.associateBy { it.id }
        assertEquals(SessionPolicy.YIELD_BOTH, players.getValue("melondualds").sessionPolicy)
        assertEquals(SessionPolicy.KEEP_COMPANION, players.getValue("other").sessionPolicy)
    }

    @Test
    fun `pack launchFace parses interactive and omitted is AUTO`() {
        val json = """
            {"schemaVersion":1,"platforms":[{
              "id":"snes","displayName":"SNES","shortName":"SNES",
              "folderNames":["snes"],"extensions":["smc"],
              "players":[
                {"id":"face-int","displayName":"I","component":"a.b/.C",
                 "uriStyle":"URI","launchFace":"interactive"},
                {"id":"face-def","displayName":"D","component":"c.d/.E","uriStyle":"URI"}
              ]
            }]}
        """.trimIndent()
        val parsed = PlatformPack.parse(json)!!
        val players = parsed.platforms.first().players.associateBy { it.id }
        assertEquals(LaunchFace.INTERACTIVE, players.getValue("face-int").launchFace)
        assertEquals(LaunchFace.AUTO, players.getValue("face-def").launchFace)
    }
}
