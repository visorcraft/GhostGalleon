package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.literalArgs
import com.visorcraft.ghostgalleon.i18n.resourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailsTest {

    @Test
    fun `collectionsContaining finds membership sorted`() {
        val cols = mapOf(
            "Zebra" to listOf("rom:a"),
            "Alpha" to listOf("pkg", "rom:a"),
            "Empty" to listOf("other"),
        )
        assertEquals(
            listOf("Alpha", "Zebra"),
            GameDetails.collectionsContaining(cols, "rom:a"),
        )
        assertEquals(listOf("Alpha"), GameDetails.collectionsContaining(cols, "pkg"))
        assertTrue(GameDetails.collectionsContaining(cols, "missing").isEmpty())
        assertTrue(GameDetails.collectionsContaining(cols, "  ").isEmpty())
    }

    @Test
    fun `body includes type playtime favorite and collections`() {
        val body = GameDetails.body(
            GameDetails.Input(
                title = "Celeste",
                key = "rom:celeste",
                kind = GameDetails.Kind.ROM,
                platformId = "switch",
                genre = "Platform / Indie",
                developer = "Maddy Makes Games",
                year = "2018",
                rating = "9.5",
                description = "  Climb the mountain.  ",
                lastLaunchedMs = 1_000L,
                playtimeMs = 90 * 60_000L, // 1h 30m
                favorite = true,
                collections = listOf("Indie", "Speedrun"),
                nowMs = 1_000L + 5 * 60_000L, // 5m ago
            ),
        )
        val ids = body.resourceIds()
        val values = body.literalArgs()
        assertTrue("Celeste" in values)
        assertTrue("switch" in values)
        assertTrue("2018" in values)
        assertTrue("Platform / Indie" in values)
        assertTrue("Maddy Makes Games" in values)
        assertTrue("9.5" in values)
        assertTrue("Indie" in values)
        assertTrue("Speedrun" in values)
        assertTrue("rom:celeste" in values)
        assertTrue("Climb the mountain." in values)
        assertTrue(R.string.label_rom in ids)
        assertTrue(R.string.time_minutes_ago in ids)
        assertTrue(R.string.time_hours_minutes in ids)
        assertTrue(R.string.label_yes in ids)
    }

    @Test
    fun `body never-played and empty collections`() {
        val body = GameDetails.body(
            GameDetails.Input(
                title = "App",
                key = "com.example",
                kind = GameDetails.Kind.APP,
                lastLaunchedMs = null,
                playtimeMs = 0L,
                favorite = false,
                collections = emptyList(),
                nowMs = 99L,
            ),
        )
        val ids = body.resourceIds()
        assertTrue(R.string.label_never in ids)
        assertTrue(R.string.time_zero_minutes in ids)
        assertTrue(R.string.label_no in ids)
        assertTrue(R.string.glyph_dash in ids)
        assertFalse(R.string.details_platform in ids)
        assertFalse(R.string.details_genre in ids)
        assertFalse("Climb" in body.literalArgs())
    }

    @Test
    fun `relatedOptions respects chrome flags and genre tokens`() {
        assertTrue(
            GameDetails.relatedOptions(
                platformId = "snes",
                genre = "Action / RPG",
                developer = "Nintendo",
                year = "1991",
                allowPlatform = false,
                allowGenre = false,
                allowDeveloper = false,
                allowYear = false,
            ).isEmpty(),
        )
        val platformOnly = GameDetails.relatedOptions(
            platformId = "snes",
            genre = "Action / RPG",
            developer = "Nintendo",
            year = "1991",
            allowPlatform = true,
        )
        assertEquals(1, platformOnly.size)
        assertEquals(
            listOf(R.string.format_platform_badge),
            GameDetails.relatedOptionLabel(platformOnly[0]).resourceIds(),
        )
        assertEquals("snes", platformOnly[0].platformId)

        val full = GameDetails.relatedOptions(
            platformId = "snes",
            genre = "Action / RPG, Action",
            developer = "Nintendo",
            year = "1991",
            allowPlatform = true,
            allowGenre = true,
            allowDeveloper = true,
            allowYear = true,
        )
        assertEquals(
            listOf(R.string.format_platform_badge),
            GameDetails.relatedOptionLabel(full[0]).resourceIds(),
        )
        // Distinct genre tokens: Action, RPG (duplicate Action dropped)
        assertEquals(
            listOf("Action", "RPG"),
            full.filter { it.genre != null }.map { it.genre },
        )
        assertTrue(full.any { it.developer == "Nintendo" })
        assertEquals("1990s", full.first { it.yearDecade != null }.yearDecade)

        val q = GameDetails.toBrowseQuery(
            GameDetails.RelatedOption(genre = "RPG"),
            sort = LibraryBrowse.Sort.NAME,
        )
        assertEquals(LibraryBrowse.Mode.ALL, q.mode)
        assertEquals("RPG", q.genre)
        assertNull(q.platformId)
        assertEquals(LibraryBrowse.Sort.NAME, q.sort)
        assertEquals("", q.text)
        assertNull(q.collectionName)
    }
}
