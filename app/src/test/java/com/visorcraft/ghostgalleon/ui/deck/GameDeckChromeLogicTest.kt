package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
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

    @Test
    fun `horizontalCenterScrollX centers active chips and clamps cleanly at both edges`() {
        // Middle chip (left=600, width=100, center=650) in 400px viewport -> 650 - 200 = 450.
        assertEquals(
            450,
            GameDeckChromeLogic.horizontalCenterScrollX(
                childLeft = 600,
                childWidth = 100,
                viewportWidth = 400,
                contentWidth = 1200,
            ),
        )
        // First chip clamps to 0.
        assertEquals(
            0,
            GameDeckChromeLogic.horizontalCenterScrollX(
                childLeft = 12,
                childWidth = 80,
                viewportWidth = 400,
                contentWidth = 1200,
            ),
        )
        // Last chip clamps to maxScroll = 1200 - 400 = 800.
        assertEquals(
            800,
            GameDeckChromeLogic.horizontalCenterScrollX(
                childLeft = 1100,
                childWidth = 90,
                viewportWidth = 400,
                contentWidth = 1200,
            ),
        )
        // Content fits within viewport -> 0.
        assertEquals(
            0,
            GameDeckChromeLogic.horizontalCenterScrollX(
                childLeft = 120,
                childWidth = 80,
                viewportWidth = 500,
                contentWidth = 350,
            ),
        )
        // Unmeasured viewport -> 0.
        assertEquals(
            0,
            GameDeckChromeLogic.horizontalCenterScrollX(
                childLeft = 200,
                childWidth = 80,
                viewportWidth = 0,
                contentWidth = 1000,
            ),
        )
    }

    @Test
    fun `strip clear-all pill and chip accessibility text helpers behave deterministically`() {
        assertFalse(GameDeckChromeLogic.shouldShowStripClearAll(0))
        assertFalse(GameDeckChromeLogic.shouldShowStripClearAll(1))
        assertTrue(GameDeckChromeLogic.shouldShowStripClearAll(2))
        assertTrue(GameDeckChromeLogic.shouldShowStripClearAll(4))

        assertEquals(
            text(R.string.format_colon_pair, text(R.string.action_clear), dynamicText("Platform · SNES")),
            GameDeckChromeLogic.filterPillAccessibilityText("Platform · SNES"),
        )
        assertEquals(
            text(R.string.format_selected_check, dynamicText("Fav · 4")),
            GameDeckChromeLogic.browseChipAccessibilityText("Fav · 4", selected = true),
        )
        assertEquals(
            dynamicText("Fav · 4"),
            GameDeckChromeLogic.browseChipAccessibilityText("Fav · 4", selected = false),
        )
    }

    @Test
    fun `emptyVisualKind and emptyStateConfirmResets map every browse state cleanly`() {
        assertEquals(
            GameDeckChromeLogic.EmptyVisualKind.SEARCH_OR_FILTER,
            GameDeckChromeLogic.emptyVisualKind(LibraryBrowse.BrowseQuery(text = "zelda")),
        )
        assertEquals(
            GameDeckChromeLogic.EmptyVisualKind.SEARCH_OR_FILTER,
            GameDeckChromeLogic.emptyVisualKind(LibraryBrowse.BrowseQuery(platformId = "snes")),
        )
        assertEquals(
            GameDeckChromeLogic.EmptyVisualKind.FAVORITES,
            GameDeckChromeLogic.emptyVisualKind(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES)),
        )
        assertEquals(
            GameDeckChromeLogic.EmptyVisualKind.COLLECTION,
            GameDeckChromeLogic.emptyVisualKind(
                LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.COLLECTION, collectionName = "RPGs"),
            ),
        )
        assertEquals(
            GameDeckChromeLogic.EmptyVisualKind.LIBRARY,
            GameDeckChromeLogic.emptyVisualKind(LibraryBrowse.BrowseQuery()),
        )

        val filtered = LibraryBrowse.BrowseQuery(text = "nomatch")
        assertTrue(GameDeckChromeLogic.emptyStateConfirmResets(entriesEmpty = true, query = filtered))
        assertFalse(GameDeckChromeLogic.emptyStateConfirmResets(entriesEmpty = false, query = filtered))
        assertFalse(
            GameDeckChromeLogic.emptyStateConfirmResets(
                entriesEmpty = true,
                query = LibraryBrowse.BrowseQuery(),
            ),
        )
    }

    @Test
    fun `railProgressState and responsive track width behave across panel sizes`() {
        val single = GameDeckChromeLogic.railProgressState(selectedIndex = 0, itemCount = 1)
        assertFalse(single.visible)
        assertEquals(0f, single.fraction, 0.001f)

        val empty = GameDeckChromeLogic.railProgressState(selectedIndex = 0, itemCount = 0)
        assertFalse(empty.visible)

        val first = GameDeckChromeLogic.railProgressState(selectedIndex = 0, itemCount = 10)
        assertTrue(first.visible)
        assertEquals(1, first.currentOneBased)
        assertEquals(10, first.total)
        assertEquals(0.1f, first.fraction, 0.001f)

        val last = GameDeckChromeLogic.railProgressState(selectedIndex = 99, itemCount = 10)
        assertEquals(10, last.currentOneBased)
        assertEquals(1.0f, last.fraction, 0.001f)

        assertEquals(
            text(R.string.format_progress, 3, 42),
            GameDeckChromeLogic.railPositionText(3, 42),
        )

        val sugarTrack = GameDeckChromeLogic.railTrackWidthDp(screenWidthDp = 540)
        val odinTrack = GameDeckChromeLogic.railTrackWidthDp(screenWidthDp = 960)
        assertTrue("Sugar track width out of bounds: $sugarTrack", sugarTrack in 72..148)
        assertTrue("Odin track width out of bounds: $odinTrack", odinTrack in 72..148)
        assertTrue("Widescreen track should be at least as wide as compact", odinTrack >= sugarTrack)
    }
}
