package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDeckChromeLogicTest {

    @Test
    fun `activeFilters collects all set meta and search filters and clearFilter removes one at a time`() {
        val full = LibraryBrowse.BrowseQuery(
            mode = LibraryBrowse.Mode.ALL,
            text = "zelda",
            platformId = "snes",
            genre = "RPG",
            developer = "Nintendo",
            yearDecade = "1990s",
        )
        val filters = GameDeckChromeLogic.activeFilters(full)
        assertEquals(
            listOf(
                GameDeckChromeLogic.ActiveFilter.Platform("snes"),
                GameDeckChromeLogic.ActiveFilter.Genre("RPG"),
                GameDeckChromeLogic.ActiveFilter.Developer("Nintendo"),
                GameDeckChromeLogic.ActiveFilter.YearDecade("1990s"),
                GameDeckChromeLogic.ActiveFilter.Search("zelda"),
            ),
            filters,
        )

        val noPlatform = GameDeckChromeLogic.clearFilter(full, filters[0])
        assertEquals(null, noPlatform.platformId)
        assertEquals("RPG", noPlatform.genre)
        assertEquals("zelda", noPlatform.text)

        val noSearch = GameDeckChromeLogic.clearFilter(full, filters[4])
        assertEquals("", noSearch.text)
        assertEquals("snes", noSearch.platformId)

        assertTrue(GameDeckChromeLogic.activeFilters(LibraryBrowse.BrowseQuery()).isEmpty())
    }

    @Test
    fun `emptyStateMessage falls back to library empty on unrestricted ALL and uses specific hint otherwise`() {
        val unrestricted = LibraryBrowse.BrowseQuery()
        assertEquals(
            text(R.string.browse_library_empty),
            GameDeckChromeLogic.emptyStateMessage(unrestricted),
        )
        assertFalse(GameDeckChromeLogic.shouldShowResetAction(unrestricted))

        val filtered = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES)
        assertEquals(
            text(R.string.browse_no_favorites),
            GameDeckChromeLogic.emptyStateMessage(filtered),
        )
        assertTrue(GameDeckChromeLogic.shouldShowResetAction(filtered))

        val searched = LibraryBrowse.BrowseQuery(text = "metroid")
        assertEquals(
            text(R.string.browse_no_matches, "metroid"),
            GameDeckChromeLogic.emptyStateMessage(searched),
        )
        assertTrue(GameDeckChromeLogic.shouldShowResetAction(searched))
    }

    @Test
    fun `browseBarEndPadDp reserves status pill clearance only when pill is enabled`() {
        assertTrue(
            GameDeckChromeLogic.browseBarEndPadDp(hasStatusPill = true) >
                GameDeckChromeLogic.browseBarEndPadDp(hasStatusPill = false),
        )
        assertTrue(GameDeckChromeLogic.browseBarEndPadDp(hasStatusPill = true) >= 100)
        assertEquals(12, GameDeckChromeLogic.browseBarEndPadDp(hasStatusPill = false))
    }

    @Test
    fun `carousel card width and visual alpha helpers are deterministic`() {
        assertEquals(216, GameDeckChromeLogic.carouselCardWidthPx(cardSizePx = 200, cellPaddingPx = 8))
        assertEquals(1.0f, GameDeckChromeLogic.cardVisualAlpha(focused = true, multiSelected = false), 0.001f)
        assertEquals(1.0f, GameDeckChromeLogic.cardVisualAlpha(focused = false, multiSelected = true), 0.001f)
        assertTrue(GameDeckChromeLogic.cardVisualAlpha(focused = false, multiSelected = false) < 1.0f)
    }
}
