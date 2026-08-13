package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomIdentityStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun identity(
        romId: String = "snes:snes/smw.smc",
        algo: String = RomIdentities.ALGO_SHA1_PAYLOAD,
        hash: String? = "abc123",
        headerTitle: String? = null,
        groupId: String? = "abc123",
        discIndex: Int? = null,
        ready: Boolean = true,
    ) = RomIdentity(romId, algo, hash, headerTitle, groupId, discIndex, ready)

    @Test
    fun `missing file loads as empty`() {
        val store = RomIdentityStore(tmp.root.resolve("nope/rom_identity.json"))
        assertEquals(emptyMap<String, RomIdentity>(), store.load())
    }

    @Test
    fun `corrupt file loads as empty`() {
        val f = tmp.newFile("rom_identity.json")
        f.writeText("{ not valid json at all")
        assertEquals(emptyMap<String, RomIdentity>(), RomIdentityStore(f).load())
    }

    @Test
    fun `save then load round-trips identities`() {
        val f = tmp.root.resolve("id/rom_identity.json")
        val map = mapOf(
            "snes:snes/smw.smc" to identity(),
            "arcade:mslug.zip" to identity(
                romId = "arcade:mslug.zip",
                algo = RomIdentities.ALGO_DAT_CRC,
                hash = "mslug",
                headerTitle = "Metal Slug",
                groupId = "mslug",
                ready = true,
            ),
            "psvita:PCSE00001" to identity(
                romId = "psvita:PCSE00001",
                algo = RomIdentities.ALGO_SFO_TITLE,
                hash = "PCSE00001",
                headerTitle = "Some Vita Game",
                groupId = "PCSE00001",
                ready = true,
            ),
            "nes:big.iso" to identity(
                romId = "nes:big.iso",
                algo = RomIdentities.ALGO_SHA256_SAMPLE,
                hash = null,
                groupId = null,
                ready = false,
            ),
        )
        RomIdentityStore(f).save(map)
        assertEquals(map, RomIdentityStore(f).load())
    }

    @Test
    fun `null optional fields round-trip`() {
        val f = tmp.root.resolve("id-null/rom_identity.json")
        val id = identity(
            hash = null,
            headerTitle = null,
            groupId = null,
            discIndex = null,
            ready = false,
        )
        RomIdentityStore(f).save(mapOf(id.romId to id))
        val loaded = RomIdentityStore(f).load()[id.romId]!!
        assertNull(loaded.hash)
        assertNull(loaded.headerTitle)
        assertNull(loaded.groupId)
        assertNull(loaded.discIndex)
        assertFalse(loaded.ready)
    }

    @Test
    fun `discIndex round-trips`() {
        val f = tmp.root.resolve("id-disc/rom_identity.json")
        val id = identity(discIndex = 2, groupId = "set-a")
        RomIdentityStore(f).save(mapOf(id.romId to id))
        assertEquals(2, RomIdentityStore(f).load()[id.romId]!!.discIndex)
        assertEquals("set-a", RomIdentityStore(f).load()[id.romId]!!.groupId)
    }

    @Test
    fun `save is atomic and creates parent directories`() {
        val f = tmp.root.resolve("deep/nested/rom_identity.json")
        RomIdentityStore(f).save(mapOf("a" to identity(romId = "a")))
        assertTrue(f.exists())
        assertFalse(File(f.path + ".tmp").exists())
        assertEquals(1, RomIdentityStore(f).load().size)
    }

    @Test
    fun `saving empty map round-trips`() {
        val f = tmp.root.resolve("id-empty/rom_identity.json")
        RomIdentityStore(f).save(emptyMap())
        assertEquals(emptyMap<String, RomIdentity>(), RomIdentityStore(f).load())
    }

    @Test
    fun `save overwrites prior contents`() {
        val f = tmp.root.resolve("id-ow/rom_identity.json")
        val store = RomIdentityStore(f)
        store.save(mapOf("old" to identity(romId = "old")))
        store.save(mapOf("new" to identity(romId = "new", hash = "ff")))
        val loaded = store.load()
        assertEquals(setOf("new"), loaded.keys)
        assertEquals("ff", loaded["new"]!!.hash)
    }
}
