package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseMenuFormatterTest {

    @Test
    fun `withActiveCheck and countedMenuTitle format active items and headers cleanly`() {
        assertEquals(
            dynamicText("zelda"),
            BrowseMenuFormatter.withActiveCheck("zelda", active = false),
        )
        assertEquals(
            text(R.string.format_selected_check, dynamicText("zelda")),
            BrowseMenuFormatter.withActiveCheck("zelda", active = true),
        )
        assertEquals(
            LibraryBrowse.labeledChip(dynamicText("SNES"), 42),
            BrowseMenuFormatter.platformMenuTitle(shortName = "SNES", platformId = "snes", romCount = 42),
        )
        assertEquals(
            LibraryBrowse.labeledChip(dynamicText("gba"), 0),
            BrowseMenuFormatter.platformMenuTitle(shortName = "  ", platformId = "gba", romCount = -2),
        )
    }

    @Test
    fun `isPlatformActionActive matches current platform and sort accurately`() {
        val snesAlphaQuery = LibraryBrowse.BrowseQuery(
            mode = LibraryBrowse.Mode.ALL,
            platformId = "snes",
            sort = LibraryBrowse.Sort.NAME,
        )
        val actions = LibraryBrowse.platformChipActions(snesAlphaQuery, "snes", "SNES")
        val clear = actions.first { it.kind == LibraryBrowse.PlatformChipActionKind.CLEAR }
        val sortName = actions.first { it.kind == LibraryBrowse.PlatformChipActionKind.SORT_NAME }
        val sortLast = actions.first { it.kind == LibraryBrowse.PlatformChipActionKind.SORT_LAST_PLAYED }

        assertFalse(BrowseMenuFormatter.isPlatformActionActive(snesAlphaQuery, clear))
        assertTrue(BrowseMenuFormatter.isPlatformActionActive(snesAlphaQuery, sortName))
        assertFalse(BrowseMenuFormatter.isPlatformActionActive(snesAlphaQuery, sortLast))

        val gbaActions = LibraryBrowse.platformChipActions(snesAlphaQuery, "gba", "GBA")
        val gbaFilter = gbaActions.first { it.kind == LibraryBrowse.PlatformChipActionKind.FILTER }
        assertFalse(BrowseMenuFormatter.isPlatformActionActive(snesAlphaQuery, gbaFilter))
    }

    @Test
    fun `isSearchHistoryEntryActive matches active query case-insensitively and ignores blanks`() {
        assertTrue(BrowseMenuFormatter.isSearchHistoryEntryActive("Zelda", "  zelda "))
        assertFalse(BrowseMenuFormatter.isSearchHistoryEntryActive("Mario", "zelda"))
        assertFalse(BrowseMenuFormatter.isSearchHistoryEntryActive("Zelda", "   "))
    }
}
