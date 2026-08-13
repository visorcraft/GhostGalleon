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
        assertFalse(c.probe(55355, nowMs = 0L))
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
    fun `client commands return false on timeout`() {
        val c = RaCommandClient(
            transport = { _, _, _ -> null },
            clockMs = { 0L },
        )
        assertFalse(c.pauseToggle(55355))
        assertFalse(c.saveState(55355))
        assertFalse(c.loadState(55355))
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
        // Rate-limit still applies while down after the last VERSION at t=0.
        assertFalse(c.probe(55355, nowMs = 100L))
        mode = "up"
        assertTrue(c.probe(55355, nowMs = 5000L))
    }
}
