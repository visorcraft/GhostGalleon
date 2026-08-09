package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.i18n.text

/** Translation-safe feedback for empty browse rails and search results. */
object BrowseFeedback {

    /**
     * Message when a rail / filter yields zero items. Null when silence is
     * better (e.g. unrestricted All — apps may still fill the carousel).
     */
    fun emptyHint(query: LibraryBrowse.BrowseQuery): UiText? {
        val queryText = query.text.trim()
        if (queryText.isNotEmpty()) return text(R.string.browse_no_matches, queryText)
        query.genre?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return text(R.string.browse_no_titles_in_genre, it)
        }
        query.developer?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return text(R.string.browse_no_titles_by_developer, it)
        }
        query.yearDecade?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return text(R.string.browse_no_titles_from_decade, LibraryBrowse.decadeText(it))
        }
        query.platformId?.let { return text(R.string.browse_no_titles_on_platform, it) }
        return when (query.mode) {
            LibraryBrowse.Mode.PLAYED_TODAY -> text(R.string.browse_nothing_played_today)
            LibraryBrowse.Mode.PLAYED_THIS_WEEK -> text(R.string.browse_nothing_played_week)
            LibraryBrowse.Mode.PLAYED_THIS_MONTH -> text(R.string.browse_nothing_played_month)
            LibraryBrowse.Mode.MOST_PLAYED -> text(R.string.browse_no_playtime)
            LibraryBrowse.Mode.UNPLAYED -> text(R.string.browse_no_unplayed)
            LibraryBrowse.Mode.RECENTLY_INSTALLED -> text(R.string.browse_no_installs)
            LibraryBrowse.Mode.COLLECTION -> {
                val name = query.collectionName?.trim().orEmpty()
                if (name.isEmpty()) text(R.string.browse_collection_empty)
                else text(R.string.browse_named_collection_empty, name)
            }
            LibraryBrowse.Mode.FAVORITES -> text(R.string.browse_no_favorites)
            LibraryBrowse.Mode.RECENT -> text(R.string.browse_nothing_recent)
            LibraryBrowse.Mode.GAMES -> text(R.string.browse_no_games)
            LibraryBrowse.Mode.ALPHA -> text(R.string.browse_library_empty)
            LibraryBrowse.Mode.ALL -> null
        }
    }

    /** Toast after applying or clearing a text search. */
    fun searchApplied(count: Int, query: String): UiText {
        val q = query.trim()
        if (q.isEmpty()) return text(R.string.browse_search_cleared)
        return if (count <= 0) {
            text(R.string.browse_no_matches, q)
        } else {
            quantityText(R.plurals.browse_matches_for_query, count, count, q)
        }
    }

    fun preferFullCount(mode: LibraryBrowse.Mode): Boolean = when (mode) {
        LibraryBrowse.Mode.RECENT,
        LibraryBrowse.Mode.PLAYED_TODAY,
        LibraryBrowse.Mode.PLAYED_THIS_WEEK,
        LibraryBrowse.Mode.PLAYED_THIS_MONTH,
        LibraryBrowse.Mode.MOST_PLAYED,
        LibraryBrowse.Mode.FAVORITES,
        LibraryBrowse.Mode.GAMES,
        LibraryBrowse.Mode.RECENTLY_INSTALLED,
        LibraryBrowse.Mode.COLLECTION,
        -> true
        else -> false
    }
}
