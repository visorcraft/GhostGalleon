package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsTest {

    @Test
    fun `parseProgress reads NumAwardedToUser and NumAchievements`() {
        val json = """
            {
              "ID": 123,
              "Title": "Super Mario World",
              "NumAwardedToUser": 3,
              "NumAchievements": 10,
              "HardcoreMode": 0
            }
        """.trimIndent()
        val p = RetroAchievements.parseProgress(json)
        assertEquals(123, p.gameId)
        assertEquals("Super Mario World", p.title)
        assertEquals(3, p.numAwarded)
        assertEquals(10, p.numPossible)
        assertEquals(30, p.percent)
        assertEquals(text(R.string.ra_progress, 3, 10, 30), p.label)
        assertFalse(p.isEmpty)
        assertFalse(p.hardcore)
    }

    @Test
    fun `parseProgress accepts nested Game object`() {
        val json = """
            {
              "Game": {
                "ID": 456,
                "Title": "Zelda",
                "NumAwardedToUser": 5,
                "NumAchievements": 20
              }
            }
        """.trimIndent()
        val p = RetroAchievements.parseProgress(json)
        assertEquals(456, p.gameId)
        assertEquals("Zelda", p.title)
        assertEquals(5, p.numAwarded)
        assertEquals(20, p.numPossible)
    }

    @Test
    fun `parseProgress malformed or empty returns empty without throw`() {
        assertTrue(RetroAchievements.parseProgress(null).isEmpty)
        assertTrue(RetroAchievements.parseProgress("").isEmpty)
        assertTrue(RetroAchievements.parseProgress("   ").isEmpty)
        assertTrue(RetroAchievements.parseProgress("not json").isEmpty)
        assertTrue(RetroAchievements.parseProgress("{}").isEmpty)
    }

    @Test
    fun `heroLine null without credentials or empty progress`() {
        val progress = RetroAchievements.parseProgress(
            """{"Title":"X","NumAwardedToUser":1,"NumAchievements":2}""",
        )
        assertNull(RetroAchievements.heroLine(progress, hasCredentials = false))
        assertNull(RetroAchievements.heroLine(null, hasCredentials = true))
        assertNull(RetroAchievements.heroLine(RaProgress(), hasCredentials = true))
        assertEquals(
            text(R.string.label_ra, text(R.string.ra_progress, 1, 2, 50)),
            RetroAchievements.heroLine(progress, hasCredentials = true),
        )
    }
}
