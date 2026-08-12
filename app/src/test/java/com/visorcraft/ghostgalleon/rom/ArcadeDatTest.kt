package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeDatTest {

    @Test
    fun `blank xml is empty`() {
        assertTrue(ArcadeDat.parse("").isEmpty())
        assertTrue(ArcadeDat.parse("   ").isEmpty())
    }

    @Test
    fun `game and machine blocks map name to description`() {
        val xml = """
            <datafile>
              <game name="mslug">
                <description>Metal Slug - Super Vehicle-001</description>
              </game>
              <machine name="SF2">
                <description>Street Fighter II: The World Warrior</description>
              </machine>
            </datafile>
        """.trimIndent()
        assertEquals(
            mapOf(
                "mslug" to "Metal Slug - Super Vehicle-001",
                "sf2" to "Street Fighter II: The World Warrior",
            ),
            ArcadeDat.parse(xml),
        )
    }

    @Test
    fun `clrmame pro text uses rom zip stem as the key`() {
        val text = """
            clrmamepro (
            name "HBMAME"
            )
            game (
            name "1942 (Revision B)"
            rom ( name 1942.zip size 1 crc 0 )
            )
            game (
            name "2020 Super Baseball (set 1)"
            description "2020 Super Baseball"
            rom ( name "2020bb.neo" size 1 crc 0 )
            )
        """.trimIndent()
        assertEquals(
            mapOf(
                "1942" to "1942 (Revision B)",
                "2020bb" to "2020 Super Baseball",
            ),
            ArcadeDat.parse(text),
        )
    }

    @Test
    fun `isbios machines are skipped and entities unescape`() {
        val xml = """
            <mame>
              <machine name="neogeo" isbios="yes">
                <description>Neo-Geo</description>
              </machine>
              <game name="dino">
                <description>Cadillacs &amp; Dinosaurs</description>
              </game>
              <game name="empty">
                <description>   </description>
              </game>
            </mame>
        """.trimIndent()
        assertEquals(
            mapOf("dino" to "Cadillacs & Dinosaurs"),
            ArcadeDat.parse(xml),
        )
    }
}
