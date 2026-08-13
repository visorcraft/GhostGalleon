package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomIdentityTest {

    /** 16-byte iNES header (`NES\u001a` + 12 pad) plus 4 payload bytes. */
    private val inesRom: ByteArray = byteArrayOf(
        0x4E, 0x45, 0x53, 0x1A,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0x01, 0x02, 0x03, 0x04,
    )
    private val inesPayload: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    @Test
    fun `stripInes drops the 16-byte header`() {
        assertArrayEquals(inesPayload, RomIdentities.stripInes(inesRom))
    }

    @Test
    fun `stripInes leaves non-ines and header-only bytes`() {
        val raw = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertArrayEquals(raw, RomIdentities.stripInes(raw))

        val headerOnly = inesRom.copyOf(16)
        assertEquals(16, headerOnly.size)
        assertArrayEquals(headerOnly, RomIdentities.stripInes(headerOnly))
    }

    @Test
    fun `sha1Hex is stable and matches the stripped payload`() {
        val once = RomIdentities.sha1Hex(inesPayload)
        val twice = RomIdentities.sha1Hex(inesPayload.copyOf())
        assertEquals(once, twice)
        assertEquals("12dada1fff4d4787ade3333147202c3b443e376f", once)
        assertNotEquals(once, RomIdentities.sha1Hex(inesRom))
    }

    @Test
    fun `sampleSha256 is stable on tiny chunks for a 40MiB size`() {
        val size = 40L * 1024 * 1024
        val head = byteArrayOf('H'.code.toByte())
        val mid = byteArrayOf('M'.code.toByte())
        val tail = byteArrayOf('T'.code.toByte())
        val once = RomIdentities.sampleSha256(size, head, mid, tail)
        val twice = RomIdentities.sampleSha256(size, head.copyOf(), mid.copyOf(), tail.copyOf())
        assertEquals(once, twice)
        assertEquals("6ab3d45aedb22bafc816492faa9ee00da53e916b049b5671f6a07c8e004b259c", once)
        assertTrue(head.size + mid.size + tail.size < 16)
    }

    @Test
    fun `chooseAlgo table`() {
        assertEquals(RomIdentities.ALGO_SFO_TITLE, RomIdentities.chooseAlgo(1L, "vita"))
        assertEquals(
            RomIdentities.ALGO_SFO_TITLE,
            RomIdentities.chooseAlgo(RomIdentities.SMALL_MAX_BYTES + 1, "vita"),
        )
        assertEquals(RomIdentities.ALGO_DAT_CRC, RomIdentities.chooseAlgo(1L, "arcade"))
        assertEquals(
            RomIdentities.ALGO_DAT_CRC,
            RomIdentities.chooseAlgo(RomIdentities.SMALL_MAX_BYTES + 1, "arcade"),
        )
        assertEquals(
            RomIdentities.ALGO_SHA1_PAYLOAD,
            RomIdentities.chooseAlgo(RomIdentities.SMALL_MAX_BYTES, "nes"),
        )
        assertEquals(
            RomIdentities.ALGO_SHA256_SAMPLE,
            RomIdentities.chooseAlgo(RomIdentities.SMALL_MAX_BYTES + 1, "nes"),
        )
        assertEquals(RomIdentities.ALGO_SHA1_PAYLOAD, RomIdentities.chooseAlgo(0L, "snes"))
    }
}
