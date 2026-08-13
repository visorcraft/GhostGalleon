package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityStackTest {

    @Test
    fun `three ids two share a group yields two primaries`() {
        val ids = listOf("a", "b", "c")
        val groups = mapOf("a" to "g1", "b" to "g1", "c" to null)
        val primaries = IdentityStack.primaryIds(
            ids = ids,
            groupId = { groups[it] },
            lastLaunchedMs = emptyMap(),
        )
        assertEquals(listOf("a", "c"), primaries)
    }

    @Test
    fun `primary is max lastLaunchedMs in the group`() {
        val ids = listOf("first", "second", "other")
        val groups = mapOf("first" to "set", "second" to "set", "other" to "set")
        val primaries = IdentityStack.primaryIds(
            ids = ids,
            groupId = { groups[it] },
            lastLaunchedMs = mapOf("first" to 10L, "second" to 99L, "other" to 50L),
        )
        assertEquals(listOf("second"), primaries)
    }

    @Test
    fun `null groupIds stay flat`() {
        val ids = listOf("x", "y", "z")
        val primaries = IdentityStack.primaryIds(
            ids = ids,
            groupId = { null },
            lastLaunchedMs = mapOf("y" to 1L),
        )
        assertEquals(ids, primaries)
    }

    @Test
    fun `tie on lastLaunched keeps first in input order`() {
        val ids = listOf("alpha", "beta")
        val primaries = IdentityStack.primaryIds(
            ids = ids,
            groupId = { "same" },
            lastLaunchedMs = mapOf("alpha" to 5L, "beta" to 5L),
        )
        assertEquals(listOf("alpha"), primaries)
    }

    @Test
    fun `shortHash keeps short values and trims long ones`() {
        assertEquals("abcdef", IdentityStack.shortHash("abcdef"))
        assertEquals("1234567890abcdef", IdentityStack.shortHash("1234567890abcdef"))
        assertEquals(
            "01234567…89abcdef",
            IdentityStack.shortHash("0123456789abcdef89abcdef"),
        )
    }
}
