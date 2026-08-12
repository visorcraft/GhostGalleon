package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.BrowseChrome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for CHROME-path structural gate — must not thrash SELECTION when
 * resumeChip is on but the chip was omitted for content reasons.
 */
class CompanionChromeInPlaceTest {

    @Test
    fun `resume already on with chip absent is content omit - in-place OK`() {
        val chrome = BrowseChrome.MINIMAL.copy(resumeChip = true)
        assertTrue(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = false,
                hasResumeChip = false,
                previous = chrome,
                next = chrome,
            ),
        )
        // Rail-only change while resume stays on / chip still absent.
        assertTrue(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = false,
                hasResumeChip = false,
                previous = chrome,
                next = chrome.copy(topRail = true),
            ),
        )
    }

    @Test
    fun `resume just turned on without chip needs full rebuild`() {
        assertFalse(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = false,
                hasResumeChip = false,
                previous = BrowseChrome.MINIMAL,
                next = BrowseChrome.MINIMAL.copy(resumeChip = true),
            ),
        )
        // Chip already present after toggle — OK.
        assertTrue(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = false,
                hasResumeChip = true,
                previous = BrowseChrome.MINIMAL,
                next = BrowseChrome.MINIMAL.copy(resumeChip = true),
            ),
        )
    }

    @Test
    fun `status pill flag vs presence mismatch fails`() {
        assertFalse(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = false,
                hasResumeChip = false,
                previous = BrowseChrome.MINIMAL,
                next = BrowseChrome.MINIMAL.copy(deckStatusPill = true),
            ),
        )
        assertTrue(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = true,
                hasResumeChip = false,
                previous = BrowseChrome.MINIMAL.copy(deckStatusPill = true),
                next = BrowseChrome.MINIMAL.copy(deckStatusPill = true, topRail = true),
            ),
        )
    }

    @Test
    fun `resume off never requires chip presence`() {
        assertTrue(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = false,
                hasResumeChip = false,
                previous = BrowseChrome.MINIMAL.copy(resumeChip = true),
                next = BrowseChrome.MINIMAL,
            ),
        )
    }
}
