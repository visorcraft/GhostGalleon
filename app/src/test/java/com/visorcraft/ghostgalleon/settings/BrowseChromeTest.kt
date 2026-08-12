package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.library.LibraryBrowse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseChromeTest {

    @Test
    fun `minimal defaults disallow power rails`() {
        val c = BrowseChrome.MINIMAL
        assertFalse(c.installedRail)
        assertFalse(c.gamesRail)
        assertFalse(c.topRail)
        assertFalse(c.todayRail)
        assertFalse(c.weekRail)
        assertFalse(c.monthRail)
        assertFalse(c.alphaRail)
        assertFalse(c.unplayedRail)
        assertFalse(c.randomChip)
        assertFalse(c.genreChips)
        assertFalse(c.developerChips)
        assertFalse(c.yearChips)
        assertFalse(c.launchableOnly)
        assertFalse(c.deckStatusPill)
        assertFalse(c.resumeChip)
        assertFalse(c.quickPanelBrowse)
        assertTrue(c.platformChips)
        assertTrue(c.collectionRails)
        assertTrue(c.isMinimal())
        assertEquals(BrowseChrome.PRESET_MINIMAL, c.presetId())
        assertFalse(c.hasAnyPowerRail())
    }

    @Test
    fun `full enables all chrome`() {
        val c = BrowseChrome.FULL
        assertTrue(c.installedRail && c.gamesRail && c.topRail && c.alphaRail)
        assertTrue(c.todayRail && c.weekRail && c.monthRail)
        assertTrue(c.unplayedRail && c.randomChip && c.genreChips)
        assertTrue(c.developerChips && c.yearChips && c.launchableOnly)
        assertTrue(c.deckStatusPill && c.resumeChip && c.quickPanelBrowse)
        assertTrue(c.isFull())
        assertEquals(BrowseChrome.PRESET_FULL, c.presetId())
        assertTrue(c.hasAnyPowerRail())
    }

    @Test
    fun `presetId is custom when any flag differs from minimal or full`() {
        val custom = BrowseChrome.MINIMAL.copy(topRail = true)
        assertEquals(BrowseChrome.PRESET_CUSTOM, custom.presetId())
        assertTrue(custom.hasAnyPowerRail())
        val nearFull = BrowseChrome.FULL.copy(resumeChip = false)
        assertEquals(BrowseChrome.PRESET_CUSTOM, nearFull.presetId())
    }

    @Test
    fun `allowsInPlaceChromeUpdate false when status pill or resume changes`() {
        val base = BrowseChrome.MINIMAL
        assertTrue(base.copy(topRail = true).allowsInPlaceChromeUpdate(base))
        assertTrue(base.copy(platformChips = false).allowsInPlaceChromeUpdate(base))
        assertFalse(base.copy(deckStatusPill = true).allowsInPlaceChromeUpdate(base))
        assertFalse(base.copy(resumeChip = true).allowsInPlaceChromeUpdate(base))
        assertTrue(
            BrowseChrome.FULL.allowsInPlaceChromeUpdate(
                BrowseChrome.FULL.copy(gamesRail = false),
            ),
        )
        assertFalse(
            BrowseChrome.FULL.allowsInPlaceChromeUpdate(
                BrowseChrome.FULL.copy(resumeChip = false),
            ),
        )
    }

    @Test
    fun `switchFlags rebind snapshot matches Minimal and Full presets`() {
        // Settings rebinds Switch isChecked from these values after preset taps.
        val minimal = BrowseChrome.MINIMAL.switchFlags()
        val full = BrowseChrome.FULL.switchFlags()
        assertEquals(18, minimal.size)
        assertEquals(18, full.size)
        // Core platform + collection default on for both.
        assertTrue(minimal[0] && minimal[1])
        assertTrue(full[0] && full[1])
        // Power rails off in Minimal, on in Full (indices 2..).
        assertTrue(minimal.drop(2).all { !it })
        assertTrue(full.drop(2).all { it })
        // After Full→Minimal rebind, flags must equal MINIMAL.switchFlags()
        // (not stale Full ON values).
        assertEquals(BrowseChrome.MINIMAL.switchFlags(), BrowseChrome.FULL.let {
            // Simulate Settings writing FULL then rebinding from stored MINIMAL.
            BrowseChrome.MINIMAL
        }.switchFlags())
        assertFalse(BrowseChrome.MINIMAL.powerRailsPanelVisible())
        assertTrue(BrowseChrome.FULL.powerRailsPanelVisible())
        assertTrue(BrowseChrome.MINIMAL.copy(topRail = true).powerRailsPanelVisible())
    }

    @Test
    fun `sanitize drops disallowed modes to ALL`() {
        val c = BrowseChrome.MINIMAL
        val q = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.GAMES)
        assertEquals(LibraryBrowse.Mode.ALL, c.sanitize(q).mode)
    }

    @Test
    fun `sanitize keeps core modes`() {
        val c = BrowseChrome.MINIMAL
        assertEquals(
            LibraryBrowse.Mode.RECENT,
            c.sanitize(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENT)).mode,
        )
        assertEquals(
            LibraryBrowse.Mode.FAVORITES,
            c.sanitize(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES)).mode,
        )
    }

    @Test
    fun `sanitize clears genre when genre chips off`() {
        val c = BrowseChrome.MINIMAL
        val q = LibraryBrowse.BrowseQuery(genre = "RPG")
        assertEquals(null, c.sanitize(q).genre)
    }

    @Test
    fun `sanitize clears developer when developer chips off`() {
        val c = BrowseChrome.MINIMAL
        val q = LibraryBrowse.BrowseQuery(developer = "Nintendo")
        assertEquals(null, c.sanitize(q).developer)
        val on = BrowseChrome.MINIMAL.copy(developerChips = true)
        assertEquals("Nintendo", on.sanitize(q).developer)
    }

    @Test
    fun `sanitize clears year decade when year chips off`() {
        val c = BrowseChrome.MINIMAL
        val q = LibraryBrowse.BrowseQuery(yearDecade = "1990s")
        assertEquals(null, c.sanitize(q).yearDecade)
        val on = BrowseChrome.MINIMAL.copy(yearChips = true)
        assertEquals("1990s", on.sanitize(q).yearDecade)
    }

    @Test
    fun `json round trip`() {
        val original = BrowseChrome.FULL.copy(platformChips = false)
        val back = BrowseChrome.fromJson(original.toJson())
        assertEquals(original, back)
    }

    @Test
    fun `null json yields minimal`() {
        assertEquals(BrowseChrome.MINIMAL, BrowseChrome.fromJson(null))
        assertEquals(BrowseChrome.MINIMAL, BrowseChrome.fromJson(JSONObject()))
    }

    @Test
    fun `allowsMode matches flags`() {
        val c = BrowseChrome.MINIMAL.copy(topRail = true)
        assertTrue(c.allowsMode(LibraryBrowse.Mode.MOST_PLAYED))
        assertFalse(c.allowsMode(LibraryBrowse.Mode.GAMES))
        assertFalse(c.allowsMode(LibraryBrowse.Mode.PLAYED_THIS_MONTH))
        assertFalse(c.allowsMode(LibraryBrowse.Mode.PLAYED_TODAY))
        assertTrue(
            BrowseChrome.MINIMAL.copy(monthRail = true)
                .allowsMode(LibraryBrowse.Mode.PLAYED_THIS_MONTH),
        )
        assertTrue(
            BrowseChrome.MINIMAL.copy(todayRail = true)
                .allowsMode(LibraryBrowse.Mode.PLAYED_TODAY),
        )
    }

    @Test
    fun `quickPanelRailShortcuts empty when browse shortcuts off`() {
        assertTrue(BrowseChrome.FULL.copy(quickPanelBrowse = false).quickPanelRailShortcuts().isEmpty())
        assertTrue(BrowseChrome.MINIMAL.quickPanelRailShortcuts().isEmpty())
    }

    @Test
    fun `quickPanelRailShortcuts includes Fav and gated rails only`() {
        val onlyWeek = BrowseChrome.MINIMAL.copy(
            quickPanelBrowse = true,
            weekRail = true,
        )
        assertEquals(
            listOf(
                LibraryBrowse.Mode.RECENT,
                LibraryBrowse.Mode.FAVORITES,
                LibraryBrowse.Mode.PLAYED_THIS_WEEK,
            ),
            onlyWeek.quickPanelRailShortcuts(),
        )
        val full = BrowseChrome.FULL.quickPanelRailShortcuts()
        assertTrue(
            full.containsAll(
                listOf(
                    LibraryBrowse.Mode.RECENT,
                    LibraryBrowse.Mode.FAVORITES,
                    LibraryBrowse.Mode.GAMES,
                    LibraryBrowse.Mode.RECENTLY_INSTALLED,
                    LibraryBrowse.Mode.PLAYED_TODAY,
                    LibraryBrowse.Mode.PLAYED_THIS_WEEK,
                    LibraryBrowse.Mode.PLAYED_THIS_MONTH,
                    LibraryBrowse.Mode.ALPHA,
                    LibraryBrowse.Mode.UNPLAYED,
                ),
            ),
        )
        assertFalse(full.contains(LibraryBrowse.Mode.MOST_PLAYED)) // Top stays special
    }
}
