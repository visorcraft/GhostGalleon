package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensCatalogTest {

    private val fixture = """
        [
          {
            "id": "snes-alttp-items",
            "title": "A Link to the Past — items",
            "match": { "platformId": "snes", "hash": ["abc123"] },
            "intervalMs": 200,
            "blocks": [
              { "address": "0x7EF340", "length": 16, "format": "bitfield", "labels": ["bow", "boomerang"] }
            ]
          },
          {
            "id": "snes-alttp-rom",
            "title": "A Link to the Past — rom",
            "match": { "platformId": "snes", "romId": ["snes:zelda.sfc"] },
            "intervalMs": 200,
            "blocks": [
              { "address": "0x7EF340", "length": 300, "format": "bytes", "labels": [] }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `parse match hash over romId and reject oversized`() {
        val lenses = LensCatalog.parse(fixture)
        assertEquals(2, lenses.size)
        assertEquals("snes-alttp-items", lenses[0].id)
        assertEquals(0x7EF340, lenses[0].blocks[0].address)
        assertEquals(listOf("bow", "boomerang"), lenses[0].blocks[0].labels)

        val hashHit = LensCatalog.match(lenses, "snes:zelda.sfc", "abc123", "snes")
        assertEquals("snes-alttp-items", hashHit!!.id)

        val romHit = LensCatalog.match(lenses, "snes:zelda.sfc", null, "snes")
        assertEquals("snes-alttp-rom", romHit!!.id)

        assertNull(LensCatalog.match(lenses, "snes:other.sfc", "nope", "snes"))
        assertNull(LensCatalog.match(lenses, "snes:zelda.sfc", "abc123", "n64"))

        assertTrue(LensCatalog.acceptable(lenses[0]))
        assertEquals(16, LensCatalog.totalBytes(lenses[0]))
        assertFalse(LensCatalog.acceptable(lenses[1]))
        assertEquals(300, LensCatalog.totalBytes(lenses[1]))
    }

    @Test
    fun `invalid json is empty list`() {
        assertEquals(emptyList<LensSpec>(), LensCatalog.parse("{ not json"))
        assertEquals(emptyList<LensSpec>(), LensCatalog.parse(""))
    }

    @Test
    fun `lens assets stay on disk when the feature is off and no pack is set`() {
        assertFalse(LensCatalog.shouldLoad(enabled = false, packUri = null))
        assertFalse(LensCatalog.shouldLoad(enabled = false, packUri = ""))
        assertTrue(LensCatalog.shouldLoad(enabled = true, packUri = null))
        assertTrue(LensCatalog.shouldLoad(enabled = false, packUri = "content://lenses/pack.json"))
    }

    @Test
    fun `parse surface and widgets with line default`() {
        val json = """
          [{"id":"line-only","title":"t","match":{"romId":["snes:a"]},"intervalMs":200,
            "blocks":[{"address":"0x1","length":1,"format":"bytes","labels":[]}]}]
        """.trimIndent()
        val spec = LensCatalog.parse(json).single()
        assertEquals("line", spec.surface)
        assertTrue(spec.widgets.isEmpty())
    }
}
