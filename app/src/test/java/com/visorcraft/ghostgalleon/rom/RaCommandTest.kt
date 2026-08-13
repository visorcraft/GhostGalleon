package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaCommandTest {

    @Test
    fun `encode is ascii with newline`() {
        assertArrayEquals("VERSION\n".toByteArray(Charsets.US_ASCII), RaCommand.encode("VERSION"))
    }

    @Test
    fun `encode trims command before newline`() {
        assertArrayEquals("GET_STATUS\n".toByteArray(Charsets.US_ASCII), RaCommand.encode("  GET_STATUS  "))
    }

    @Test
    fun `parseStatus`() {
        assertEquals(RaStatus.PLAYING, RaCommand.parseStatus("GET_STATUS PLAYING"))
        assertEquals(RaStatus.PAUSED, RaCommand.parseStatus("GET_STATUS PAUSED"))
        assertEquals(RaStatus.UNKNOWN, RaCommand.parseStatus(null))
        assertEquals(RaStatus.UNKNOWN, RaCommand.parseStatus(""))
    }

    @Test
    fun `parseStatus prefers PAUSED over PLAYING substring`() {
        // "PAUSED" checked first so a PAUSED line never becomes PLAYING.
        assertEquals(RaStatus.PAUSED, RaCommand.parseStatus("GET_STATUS PAUSED content PLAYING"))
    }

    @Test
    fun `parseSlotReply accepts non-empty ACK`() {
        assertTrue(RaCommand.parseSlotReply("ACK"))
        assertTrue(RaCommand.parseSlotReply("  ok  "))
        assertFalse(RaCommand.parseSlotReply(null))
        assertFalse(RaCommand.parseSlotReply(""))
        assertFalse(RaCommand.parseSlotReply("   "))
    }

    @Test
    fun `client probe timeout is not thrown`() {
        val c = RaCommandClient(
            transport = { _, _, _ -> null },
            clockMs = { 0L },
        )
        assertFalse(c.probe(55355, nowMs = 0L))
        assertFalse(c.isLinkUp())
    }

    @Test
    fun `client probe succeeds on non-null VERSION reply`() {
        val c = RaCommandClient(
            transport = { _, payload, timeoutMs ->
                assertEquals(200, timeoutMs)
                assertArrayEquals("VERSION\n".toByteArray(Charsets.US_ASCII), payload)
                "1.19.0".toByteArray(Charsets.US_ASCII)
            },
            clockMs = { 0L },
        )
        assertTrue(c.probe(RaCommand.DEFAULT_PORT, nowMs = 0L))
        assertTrue(c.isLinkUp())
    }

    @Test
    fun `client probe rate-limits while down`() {
        var sends = 0
        val c = RaCommandClient(
            transport = { _, _, _ ->
                sends++
                null
            },
            clockMs = { 0L },
        )
        assertTrue(c.probeDue(0L))
        assertFalse(c.probe(55355, nowMs = 0L))
        assertFalse(c.probeDue(4999L))
        assertFalse(c.probe(55355, nowMs = 4999L))
        assertEquals(1, sends)
        assertFalse(c.probe(55355, nowMs = 5000L))
        assertEquals(2, sends)
    }

    @Test
    fun `client probe skips send after success`() {
        var sends = 0
        val c = RaCommandClient(
            transport = { _, _, _ ->
                sends++
                "ok".toByteArray(Charsets.US_ASCII)
            },
            clockMs = { 0L },
        )
        assertTrue(c.probe(55355, nowMs = 0L))
        assertTrue(c.probe(55355, nowMs = 100L))
        assertEquals(1, sends)
    }

    @Test
    fun `client status parses GET_STATUS reply`() {
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                assertArrayEquals("GET_STATUS\n".toByteArray(Charsets.US_ASCII), payload)
                "GET_STATUS PAUSED".toByteArray(Charsets.US_ASCII)
            },
            clockMs = { 0L },
        )
        assertEquals(RaStatus.PAUSED, c.status(55355))
    }

    @Test
    fun `client status null is UNKNOWN`() {
        val c = RaCommandClient(
            transport = { _, _, _ -> null },
            clockMs = { 0L },
        )
        assertEquals(RaStatus.UNKNOWN, c.status(55355))
    }

    @Test
    fun `client pause save load use command verbs`() {
        val seen = mutableListOf<String>()
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                seen += payload.toString(Charsets.US_ASCII).trim()
                "ACK".toByteArray(Charsets.US_ASCII)
            },
            clockMs = { 0L },
        )
        assertTrue(c.pauseToggle(55355))
        assertTrue(c.saveState(55355))
        assertTrue(c.loadState(55355))
        assertEquals(listOf("PAUSE_TOGGLE", "SAVE_STATE", "LOAD_STATE"), seen)
    }

    @Test
    fun `client fire-and-forget timeout is success and keeps linkUp`() {
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                val cmd = payload.toString(Charsets.US_ASCII).trim()
                if (cmd == "VERSION") "1.19.0".toByteArray(Charsets.US_ASCII) else null
            },
            clockMs = { 0L },
        )
        assertTrue(c.probe(55355, nowMs = 0L))
        assertTrue(c.isLinkUp())
        assertTrue(c.pauseToggle(55355))
        assertTrue(c.isLinkUp())
        assertTrue(c.saveState(55355))
        assertTrue(c.isLinkUp())
        assertTrue(c.loadState(55355))
        assertTrue(c.isLinkUp())
    }

    @Test
    fun `slot commands encode the slot and first timeout hides the strip`() {
        val seen = mutableListOf<String>()
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                seen += payload.toString(Charsets.US_ASCII).trim()
                null
            },
            clockMs = { 0L },
        )
        assertTrue(c.slotStripAllowed())
        assertFalse(c.loadStateSlot(55355, 3))
        assertFalse(c.slotStripAllowed())
        assertTrue(c.loadState(55355))
        assertFalse(c.saveStateSlot(55355, 2))
        assertTrue(c.saveState(55355))
        assertEquals(
            listOf("LOAD_STATE_SLOT 3", "LOAD_STATE", "SAVE_STATE_SLOT 2", "SAVE_STATE"),
            seen,
        )
    }

    @Test
    fun `slot commands accept cinema band and reject outside 1-12`() {
        val seen = mutableListOf<String>()
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                seen += payload.toString(Charsets.US_ASCII).trim()
                payload
            },
            clockMs = { 0L },
        )
        assertTrue(c.saveStateSlot(55355, 9))
        assertTrue(c.loadStateSlot(55355, 12))
        assertFalse(c.saveStateSlot(55355, 0))
        assertFalse(c.loadStateSlot(55355, 13))
        assertTrue(c.slotStripAllowed())
        assertEquals(listOf("SAVE_STATE_SLOT 9", "LOAD_STATE_SLOT 12"), seen)
    }

    @Test
    fun `cinema band timeout does not hide the user slot strip`() {
        val seen = mutableListOf<String>()
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                seen += payload.toString(Charsets.US_ASCII).trim()
                null
            },
            clockMs = { 0L },
        )
        assertTrue(c.slotStripAllowed())
        assertFalse(c.saveStateSlot(55355, 9))
        assertTrue(c.slotStripAllowed())
        assertFalse(c.loadStateSlot(55355, 12))
        assertTrue(c.slotStripAllowed())
        assertFalse(c.saveStateSlot(55355, 2))
        assertFalse(c.slotStripAllowed())
        assertEquals(
            listOf("SAVE_STATE_SLOT 9", "LOAD_STATE_SLOT 12", "SAVE_STATE_SLOT 2"),
            seen,
        )
    }

    @Test
    fun `slot command ACK keeps the strip allowed`() {
        val seen = mutableListOf<String>()
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                seen += payload.toString(Charsets.US_ASCII).trim()
                payload
            },
            clockMs = { 0L },
        )
        assertTrue(c.saveStateSlot(55355, 2))
        assertTrue(c.loadStateSlot(55355, 8))
        assertTrue(c.slotStripAllowed())
        assertEquals(listOf("SAVE_STATE_SLOT 2", "LOAD_STATE_SLOT 8"), seen)
    }

    @Test
    fun `client status timeout drops link so probe can run again`() {
        var mode = "up"
        val c = RaCommandClient(
            transport = { _, payload, _ ->
                val cmd = payload.toString(Charsets.US_ASCII).trim()
                when {
                    mode == "up" && cmd == "VERSION" -> "1".toByteArray(Charsets.US_ASCII)
                    mode == "down" -> null
                    else -> null
                }
            },
            clockMs = { 0L },
        )
        assertTrue(c.probe(55355, nowMs = 0L))
        mode = "down"
        assertEquals(RaStatus.UNKNOWN, c.status(55355))
        assertFalse(c.isLinkUp())
        // Rate-limit still applies while down after the last VERSION at t=0.
        assertFalse(c.probe(55355, nowMs = 100L))
        mode = "up"
        assertTrue(c.probe(55355, nowMs = 5000L))
    }

    @Test
    fun `encode READ_CORE_RAM rejects bad length and write is absent`() {
        assertArrayEquals(
            "READ_CORE_RAM 7EF340 16\n".toByteArray(Charsets.US_ASCII),
            RaCommand.encodeReadCoreRam(0x7EF340, 16),
        )
        assertEquals(null, RaCommand.encodeReadCoreRam(0, 0))
        assertEquals(null, RaCommand.encodeReadCoreRam(0, 257))
        assertEquals(null, RaCommand.encodeReadCoreRam(-1, 8))
        assertEquals(0, RaCommand::class.java.methods.count { it.name.contains("Write", ignoreCase = true) })
    }

    @Test
    fun `parse RAM reply`() {
        val bytes = RaCommand.parseRamReply("READ_CORE_RAM 00 01 02 03", 4)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), bytes)
        assertEquals(null, RaCommand.parseRamReply(null, 4))
        assertEquals(null, RaCommand.parseRamReply("00 01", 4))
        // Live RetroArch echoes the address after the verb.
        val withAddr = RaCommand.parseRamReply("READ_CORE_RAM 7EF340 00 01 02 03", 4)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), withAddr)
    }
}
