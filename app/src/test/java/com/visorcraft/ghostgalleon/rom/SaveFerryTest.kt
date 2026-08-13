package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFerryTest {

    private fun ident(id: String, hash: String?, ready: Boolean = true, group: String? = hash) =
        RomIdentity(id, RomIdentities.ALGO_SHA1_PAYLOAD, hash, null, group, null, ready)

    @Test
    fun `same title and RA player produce srm and user-state offers`() {
        val a = ident("snes:a.sfc", "aaa")
        val b = ident("snes:b.sfc", "aaa")
        assertEquals(FerryRefuse.NONE, SaveFerry.refuse(a, b, "ra-snes9x", "ra-snes9x", false))
        assertEquals(FerryKind.RA_SRM to null, SaveFerry.classifyName("a.srm", "a"))
        assertEquals(FerryKind.RA_STATE to 3, SaveFerry.classifyName("a.state3", "a"))
        assertNull(SaveFerry.classifyName("a.state9", "a"))
        val offers = SaveFerry.offers(
            RomEntry("snes:a.sfc", "A", "snes", "content://a", null),
            RomEntry("snes:b.sfc", "B", "snes", "content://b", null),
            listOf(SaveDoc("content://saves/a.srm", "a.srm")),
            FerryRefuse.NONE,
        )
        assertEquals(1, offers.size)
        assertEquals(FerryKind.RA_SRM, offers[0].kind)
    }

    @Test
    fun `refuse not ready different player and yield dest`() {
        assertEquals(
            FerryRefuse.NOT_READY,
            SaveFerry.refuse(ident("a", null, ready = false), ident("b", "x"), "ra-x", "ra-x", false),
        )
        assertEquals(
            FerryRefuse.DIFFERENT_TITLE,
            SaveFerry.refuse(ident("a", "aaa"), ident("b", "bbb"), "ra-x", "ra-x", false),
        )
        assertEquals(
            FerryRefuse.DIFFERENT_PLAYER,
            SaveFerry.refuse(ident("a", "aaa"), ident("b", "aaa"), "ra-x", "drastic", false),
        )
        assertEquals(
            FerryRefuse.YIELD_DEST,
            SaveFerry.refuse(ident("a", "aaa"), ident("b", "aaa"), "ra-x", "ra-x", true),
        )
        assertTrue(SaveFerry.samePlayerHint("ra-snes9x", "ra-mgba"))
        assertTrue(SaveFerry.samePlayerHint("ra-snes9x", SessionHandoff.RA_PACKAGE))
    }
}
