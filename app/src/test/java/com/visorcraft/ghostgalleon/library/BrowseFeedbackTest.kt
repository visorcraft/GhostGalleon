package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFeedbackTest {

    @Test
    fun `emptyHint prioritizes search over mode`() {
        val q = LibraryBrowse.BrowseQuery(
            mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK,
            text = "zelda",
        )
        assertEquals(text(R.string.browse_no_matches, "zelda"), BrowseFeedback.emptyHint(q))
    }

    @Test
    fun `emptyHint genre and platform`() {
        assertEquals(
            text(R.string.browse_no_titles_in_genre, "RPG"),
            BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery(genre = "RPG")),
        )
        assertEquals(
            text(R.string.browse_no_titles_by_developer, "Nintendo"),
            BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery(developer = "Nintendo")),
        )
        assertEquals(
            text(
                R.string.browse_no_titles_from_decade,
                LibraryBrowse.decadeText("1990s"),
            ),
            BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery(yearDecade = "1990s")),
        )
        assertEquals(
            text(R.string.browse_no_titles_on_platform, "snes"),
            BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery(platformId = "snes")),
        )
    }

    @Test
    fun `emptyHint mode messages`() {
        assertEquals(
            text(R.string.browse_nothing_played_today),
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_TODAY),
            ),
        )
        assertEquals(
            text(R.string.browse_nothing_played_week),
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK),
            ),
        )
        assertEquals(
            text(R.string.browse_nothing_played_month),
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_MONTH),
            ),
        )
        assertEquals(
            text(R.string.browse_named_collection_empty, "Speedrun"),
            BrowseFeedback.emptyHint(
                LibraryBrowse.BrowseQuery(
                    mode = LibraryBrowse.Mode.COLLECTION,
                    collectionName = "Speedrun",
                ),
            ),
        )
        assertNull(BrowseFeedback.emptyHint(LibraryBrowse.BrowseQuery()))
    }

    @Test
    fun `searchApplied formats count and clear`() {
        assertEquals(text(R.string.browse_search_cleared), BrowseFeedback.searchApplied(0, "  "))
        assertEquals(
            text(R.string.browse_no_matches, "xyz"),
            BrowseFeedback.searchApplied(0, "xyz"),
        )
        assertEquals(
            quantityText(R.plurals.browse_matches_for_query, 1, 1, "zel"),
            BrowseFeedback.searchApplied(1, "zel"),
        )
        assertEquals(
            quantityText(R.plurals.browse_matches_for_query, 12, 12, "a"),
            BrowseFeedback.searchApplied(12, "a"),
        )
    }

    @Test
    fun `preferFullCount marks interleaved modes`() {
        assertTrue(BrowseFeedback.preferFullCount(LibraryBrowse.Mode.RECENT))
        assertTrue(BrowseFeedback.preferFullCount(LibraryBrowse.Mode.FAVORITES))
        assertTrue(!BrowseFeedback.preferFullCount(LibraryBrowse.Mode.ALL))
    }
}
