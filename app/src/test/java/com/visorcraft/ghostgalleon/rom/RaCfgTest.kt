package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaCfgTest {

    @Test
    fun `enables without clobbering other keys`() {
        val src = "foo = \"bar\"\n"
        val (out, changed) = RaCfg.enableNetworkCommands(src)
        assertTrue(changed)
        assertTrue(out.contains("foo = \"bar\""))
        assertTrue(out.contains("network_cmd_enable = \"true\""))
        assertTrue(out.contains("network_cmd_port = \"55355\""))
        val again = RaCfg.enableNetworkCommands(out)
        assertFalse(again.second)
    }

    @Test
    fun `readPort`() {
        assertEquals(1234, RaCfg.readPort("network_cmd_port = \"1234\"\n"))
        assertEquals(55355, RaCfg.readPort("x = 1\n"))
    }

    @Test
    fun `already true still adds missing port`() {
        val src = "network_cmd_enable = \"true\"\nvideo_vsync = \"true\"\n"
        val (out, changed) = RaCfg.enableNetworkCommands(src)
        assertTrue(changed)
        assertEquals(1, Regex("network_cmd_enable").findAll(out).count())
        assertTrue(out.contains("video_vsync = \"true\""))
        assertTrue(out.contains("network_cmd_port = \"55355\""))
    }

    @Test
    fun `keeps an existing port`() {
        val src = "network_cmd_enable = \"false\"\nnetwork_cmd_port = \"1234\"\n"
        val (out, changed) = RaCfg.enableNetworkCommands(src)
        assertTrue(changed)
        assertTrue(out.contains("network_cmd_enable = \"true\""))
        assertTrue(out.contains("network_cmd_port = \"1234\""))
        assertFalse(out.contains("55355"))
        assertEquals(1234, RaCfg.readPort(out))
    }
}
