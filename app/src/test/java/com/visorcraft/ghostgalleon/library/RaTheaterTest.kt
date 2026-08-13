package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaTheaterTest {

    private val fixture = """
      {
        "ID": 1, "Title": "Game", "NumAwardedToUser": 1, "NumAchievements": 2,
        "Achievements": {
          "10": {"ID":10,"Title":"First","Description":"d","Points":5,"DateEarned":"2020-01-01","BadgeName":"001"},
          "11": {"ID":11,"Title":"Second","Description":"e","Points":10,"DateEarned":"","BadgeName":"002"}
        }
      }
    """.trimIndent()

    @Test
    fun `parse next locked and unlock diff`() {
        val snap = RaTheater.parse(fixture)
        assertEquals(1, snap.progress.numAwarded)
        assertEquals(2, snap.progress.numPossible)
        assertEquals("Second", snap.nextLocked?.title)
        assertEquals(setOf(10), snap.unlockedIds)
        assertEquals(listOf(11), RaTheater.newlyUnlocked(setOf(10), setOf(10, 11)))
        assertTrue(RaTheater.newlyUnlocked(setOf(10), setOf(10)).isEmpty())
        assertTrue(RaTheater.pollDue(0L, 60_000L, 60_000L))
        assertFalse(RaTheater.pollDue(10_000L, 20_000L, 60_000L))
    }

    @Test
    fun `malformed json is empty`() {
        val empty = RaTheater.parse("{ not json")
        assertTrue(empty.progress.isEmpty)
        assertNull(empty.nextLocked)
        assertTrue(empty.unlockedIds.isEmpty())
        assertTrue(RaTheater.parse(null).progress.isEmpty)
    }
}
