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

    @Test
    fun `json null dates stay locked and array shape works`() {
        val json = """
          {
            "ID": 1, "Title": "Game", "NumAwardedToUser": 1, "NumAchievements": 3,
            "Achievements": [
              {"ID":10,"Title":"A","Description":"d","Points":5,"DateEarned":null,"DateEarnedHardcore":null,"BadgeName":null},
              {"ID":11,"Title":"B","Description":"e","Points":10,"DateEarnedHardcore":"2020-02-02","BadgeName":"002"},
              {"ID":0,"Title":"Skip","Description":"x","Points":1}
            ]
          }
        """.trimIndent()
        val snap = RaTheater.parse(json)
        assertEquals("A", snap.nextLocked?.title)
        assertEquals("B", snap.lastUnlock?.title)
        assertEquals(setOf(11), snap.unlockedIds)
        assertNull(snap.nextLocked?.badgeName)
        assertEquals("002", snap.lastUnlock?.badgeName)
    }

    @Test
    fun `single-parse JSONObject overload matches String parse bit-for-bit across flat and nested Game payloads`() {
        val fromString = RaTheater.parse(fixture)
        val fromObj = RaTheater.parse(org.json.JSONObject(fixture))
        assertEquals(fromString, fromObj)

        val nested = """
          {
            "Game": {"ID": 77, "Title": "Chrono", "NumAwardedToUser": 4, "NumAchievements": 12, "HardcoreMode": 1},
            "Achievements": [
              {"ID": 100, "Title": "Start", "Description": "Go", "Points": 5, "DateEarnedHardcore": "2025-01-01", "BadgeName": "b1"},
              {"ID": 101, "Title": "Boss", "Description": "Win", "Points": 25, "DateEarned": null, "BadgeName": "b2"}
            ]
          }
        """.trimIndent()
        val nestedFromString = RaTheater.parse(nested)
        val nestedFromObj = RaTheater.parse(org.json.JSONObject(nested))
        assertEquals(nestedFromString, nestedFromObj)
        assertEquals(77, nestedFromObj.progress.gameId)
        assertTrue(nestedFromObj.progress.hardcore)
        assertEquals(4, nestedFromObj.progress.numAwarded)
        assertEquals(12, nestedFromObj.progress.numPossible)
        assertEquals(101, nestedFromObj.nextLocked?.id)
        assertEquals(setOf(100), nestedFromObj.unlockedIds)
    }
}
