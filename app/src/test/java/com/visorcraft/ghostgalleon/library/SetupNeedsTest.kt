package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupNeedsTest {

    private fun snap(
        dismissed: Boolean = false,
        trees: Int = 0,
        roms: Int = 0,
        players: Int = 0,
        sgdb: Boolean = false,
        resume: Boolean = false,
        status: Boolean = false,
        chromeDismissed: Boolean = false,
    ) = SetupNeeds.Snapshot(
        setupDismissed = dismissed,
        romTreeCount = trees,
        romEntryCount = roms,
        installedPlayerCount = players,
        hasSgdbKey = sgdb,
        resumeChip = resume,
        statusPill = status,
        chromeDiscoverDismissed = chromeDismissed,
    )

    @Test
    fun `library setup when empty and not dismissed`() {
        assertTrue(SetupNeeds.shouldShowLibrarySetup(snap()))
        assertTrue(SetupNeeds.shouldShow(snap()))
    }

    @Test
    fun `library setup hidden when dismissed or has trees or entries`() {
        assertFalse(SetupNeeds.shouldShowLibrarySetup(snap(dismissed = true)))
        assertFalse(SetupNeeds.shouldShowLibrarySetup(snap(trees = 1)))
        assertFalse(SetupNeeds.shouldShowLibrarySetup(snap(roms = 5)))
    }

    @Test
    fun `chrome discover when library present and features off`() {
        assertTrue(SetupNeeds.shouldShowChromeDiscover(snap(trees = 1)))
        assertTrue(SetupNeeds.shouldShow(snap(trees = 1)))
        assertTrue(SetupNeeds.isChromeDiscoverOnly(snap(trees = 1)))
        assertFalse(SetupNeeds.shouldShowChromeDiscover(snap(trees = 1, chromeDismissed = true)))
        assertFalse(
            SetupNeeds.shouldShowChromeDiscover(
                snap(trees = 1, resume = true, status = true),
            ),
        )
    }

    @Test
    fun `checklist empty library includes chrome rows`() {
        val list = SetupNeeds.checklist(snap(trees = 0, players = 2, sgdb = true))
        assertEquals(5, list.size)
        assertTrue(list[0].second.not() || list[0].second) // ROM folder row exists
        assertFalse(list[0].second) // no tree
        assertTrue(list[1].second) // emulator
        assertTrue(list[2].second) // sgdb
        assertFalse(list[3].second) // resume
        assertFalse(list[4].second) // status
    }

    @Test
    fun `checklist chrome discover only has two rows`() {
        val list = SetupNeeds.checklist(snap(trees = 2, resume = true, status = false))
        assertEquals(2, list.size)
        assertTrue(list[0].second) // resume on
        assertFalse(list[1].second) // status off
    }

    @Test
    fun `allRequiredDone only needs a tree`() {
        assertFalse(SetupNeeds.allRequiredDone(snap()))
        assertTrue(SetupNeeds.allRequiredDone(snap(trees = 1)))
    }

    @Test
    fun `enableCompanionChrome ors flags`() {
        assertEquals(true to true, SetupNeeds.enableCompanionChrome(true, true, false, false))
        assertEquals(true to false, SetupNeeds.enableCompanionChrome(true, false, true, false))
    }

    @Test
    fun `granting a tree ends library setup but may still show chrome discover`() {
        assertTrue(SetupNeeds.shouldShowLibrarySetup(snap(trees = 0, roms = 0)))
        assertFalse(SetupNeeds.shouldShowLibrarySetup(snap(trees = 1, roms = 0)))
        assertTrue(SetupNeeds.shouldShowChromeDiscover(snap(trees = 1, roms = 0)))
    }
}
