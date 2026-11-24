package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(RomIdentities.ALGO_SFO_TITLE, RomIdentities.chooseAlgo(1L, "psvita"))
        assertEquals(
            RomIdentities.ALGO_SFO_TITLE,
            RomIdentities.chooseAlgo(RomIdentities.SMALL_MAX_BYTES + 1, "vita"),
        )
        assertEquals(
            RomIdentities.ALGO_SFO_TITLE,
            RomIdentities.chooseAlgo(RomIdentities.SMALL_MAX_BYTES + 1, "psvita"),
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

    @Test
    fun `sidecar stays quiet when every row was already ready`() {
        val keys = setOf("nes:a", "snes:b")
        assertTrue(RomIdentities.sidecarQuiet(keys, keys, newlyComputed = 0))
        assertFalse(RomIdentities.sidecarQuiet(keys, keys, newlyComputed = 1))
        assertFalse(RomIdentities.sidecarQuiet(keys, setOf("nes:a"), newlyComputed = 0))
        assertFalse(RomIdentities.sidecarQuiet(emptySet(), keys, newlyComputed = 0))
    }

    @Test
    fun `streaming sha1Payload matches stripInes plus sha1Hex bit-for-bit across iNES, boundaries, and fragmented streams`() {
        val headerOnly = inesRom.copyOf(16)
        val ines17 = ByteArray(17) { i ->
            when (i) {
                0 -> 'N'.code.toByte()
                1 -> 'E'.code.toByte()
                2 -> 'S'.code.toByte()
                3 -> 0x1A.toByte()
                16 -> 0x7F.toByte()
                else -> 0
            }
        }
        val nonInes17 = ByteArray(17) { i -> (i + 5).toByte() }
        val bigInes = ByteArray(256 * 1024 + 37) { i ->
            when (i) {
                0 -> 'N'.code.toByte()
                1 -> 'E'.code.toByte()
                2 -> 'S'.code.toByte()
                3 -> 0x1A.toByte()
                in 4..15 -> 0
                else -> ((i * 31 + 7) and 0xFF).toByte()
            }
        }
        val bigRaw = ByteArray(256 * 1024 + 37) { i -> ((i * 17 + 3) and 0xFF).toByte() }

        val cases = listOf(
            "empty" to ByteArray(0),
            "tiny-raw" to byteArrayOf(1, 2, 3, 4),
            "header-only-16" to headerOnly,
            "ines-17" to ines17,
            "non-ines-17" to nonInes17,
            "ines-20" to inesRom,
            "big-ines-256k" to bigInes,
            "big-raw-256k" to bigRaw,
        )

        for ((label, data) in cases) {
            val expected = RomIdentities.sha1Hex(RomIdentities.stripInes(data))
            val actual64k = RomIdentities.sha1Payload(data.inputStream())
            assertEquals("$label 64KiB stream mismatch", expected, actual64k)

            // Small chunk buffer + pathological 7-byte partial-read stream
            val fragmented = object : java.io.FilterInputStream(data.inputStream()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, minOf(len, 7))
            }
            val actualFragmented = RomIdentities.sha1Payload(fragmented, bufferSize = 64)
            assertEquals("$label fragmented stream mismatch", expected, actualFragmented)
        }
    }

    @Test
    fun `skipFully discards in 64KiB chunks instead of byte-by-byte when stream skip returns 0`() {
        val totalSize = 200_000
        val payload = ByteArray(totalSize) { i -> (i and 0xFF).toByte() }
        var readCalls = 0
        var singleByteReads = 0
        val unskippable = object : java.io.FilterInputStream(payload.inputStream()) {
            override fun skip(n: Long): Long = 0L
            override fun read(): Int {
                singleByteReads++
                return super.read()
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                readCalls++
                return super.read(b, off, len)
            }
        }

        val skipped = RomIdentities.skipFully(unskippable, 180_000L)
        assertEquals(180_000L, skipped)
        assertEquals("must never fall back to byte-by-byte read()", 0, singleByteReads)
        assertTrue("expected <= 4 chunked 64KiB reads, got $readCalls", readCalls <= 4)
        // Verify exact byte position after skip
        val nextByte = ByteArray(4)
        assertEquals(4, unskippable.read(nextByte))
        for (i in 0 until 4) {
            assertEquals(((180_000 + i) and 0xFF).toByte(), nextByte[i])
        }
    }

    @Test
    fun `shouldReusePrior, shouldCheckpoint, and hasArcadeEntries enforce retry and lazy TSV policies`() {
        val uriA = "content://provider/doc/a"
        val uriB = "content://provider/doc/b"
        val ready = RomIdentity("snes:a", RomIdentities.ALGO_SHA1_PAYLOAD, "abc", null, "abc", null, true, uriA)
        val failed = RomIdentity("snes:bad", RomIdentities.ALGO_SHA1_PAYLOAD, null, null, null, null, false, uriA)

        // Missing prior -> compute
        assertFalse(RomIdentities.shouldReusePrior(null, uriA, retryFailed = false))
        // Ready row -> reused on boot & rescan when URI unchanged, invalidated when URI changes
        assertTrue(RomIdentities.shouldReusePrior(ready, uriA, retryFailed = false))
        assertTrue(RomIdentities.shouldReusePrior(ready, uriA, retryFailed = true))
        assertFalse(RomIdentities.shouldReusePrior(ready, uriB, retryFailed = false))
        // Failed row -> skipped on cold boot (no infinite retry storm!), retried on explicit rescan or URI change
        assertTrue(RomIdentities.shouldReusePrior(failed, uriA, retryFailed = false))
        assertFalse(RomIdentities.shouldReusePrior(failed, uriA, retryFailed = true))
        assertFalse(RomIdentities.shouldReusePrior(failed, uriB, retryFailed = false))

        // Checkpoint cadence
        assertFalse(RomIdentities.shouldCheckpoint(RomIdentities.CHECKPOINT_INTERVAL - 1))
        assertTrue(RomIdentities.shouldCheckpoint(RomIdentities.CHECKPOINT_INTERVAL))

        // Lazy arcade TSV detection
        val nonArcade = listOf(
            RomEntry("snes:a", "SMW", "snes", uriA, path = null),
            RomEntry("ps1:b", "FF7", "ps1", uriB, path = null),
        )
        assertFalse(RomIdentities.hasArcadeEntries(nonArcade))
        assertTrue(RomIdentities.hasArcadeEntries(nonArcade + RomEntry("arcade:mslug", "Metal Slug", "arcade", uriA, path = null)))
    }
}
