package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.BrowseFeedback
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.GameDetails
import com.visorcraft.ghostgalleon.library.HiddenRoms
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.library.PlayStats
import com.visorcraft.ghostgalleon.library.SearchHistory
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.rom.FerryKind
import com.visorcraft.ghostgalleon.rom.FerryOffer
import com.visorcraft.ghostgalleon.rom.FerryRefuse
import com.visorcraft.ghostgalleon.rom.IdentityStack
import com.visorcraft.ghostgalleon.rom.LaunchReason
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLauncher
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.rom.SaveFerry
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.CompanionRoleResolve
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.GridSlots
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.HelperEmbedPolicy
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx

class GameDeck(
    private val activity: AppCompatActivity,
    private val state: DeckState,
    private var settings: Settings,
    private val library: AppLibrary,
    private val iconLoader: AppIconLoader,
    private val roms: List<RomEntry>,
    private val showStatusPill: Boolean = true,
) : Deck {

    // One carousel entry: a curated-grid app (key = package name) or a
    // scanned ROM (key = "rom:<id>"). Labels render on the card; the hero
    // panel follows the key via DeckState.selectedKey.
    private enum class BulkAction {
        SELECT_ALL, INVERT, FAVORITE, UNFAVORITE, PIN_GRID, PIN_DOCK, UNPIN_DOCK,
        COPY_TITLES, ADD_COLLECTION, HIDE_ROMS, MARK_PLAYED, CLEAR_STATS,
        REMOVE_COLLECTION, CLEAR_SELECTION, CANCEL_MODE,
    }

    private data class CarouselEntry(
        val key: String,
        val label: String,
        val appPackage: String?,
        val rom: RomEntry?,
    )

    // Curated apps (when browse mode is ALL and no platform/genre filter/search),
    // then browsed ROMs via LibraryBrowse (platform / genre / search / recent /
    // top / A–Z / unplayed / fav). Ranked modes interleave apps by the same maps.
    // Genre is ROM-only: when set, app interleaving is skipped.
    // Mutable so browse chips can rebuild the list without setContentView.
    private var entries: List<CarouselEntry> = emptyList()
    /** O(1) selection index; rebuilt with [entries]. */
    private var entryIndexByKey: Map<String, Int> = emptyMap()
    /** Single snap helper for the carousel (never re-alloc on NAV). */
    private val snapHelper = LinearSnapHelper()
    private var entriesMemoKey: String? = null
    private var entriesMemo: List<CarouselEntry> = emptyList()

    private fun installEntries(next: List<CarouselEntry>) {
        entries = next
        entryIndexByKey = next.withIndex().associate { (i, e) -> e.key to i }
    }

    private fun buildEntries(): List<CarouselEntry> {
        // Drop power-user modes if chrome toggles turned them off mid-session.
        // Quiet adopt — never setLibraryBrowse here (would re-enter BROWSE).
        val q = settings.browseChrome.sanitize(state.libraryBrowse)
        if (q != state.libraryBrowse) {
            state.adoptLibraryBrowse(q)
        }
        val appsOk = q.platformId == null &&
            q.genre.isNullOrBlank() &&
            q.developer.isNullOrBlank() &&
            q.yearDecade.isNullOrBlank()
        val identities = app().romIdentities
        val memoKey = LibraryBrowse.browseRebuildKey(
            mode = q.mode.name,
            text = q.text,
            platformId = q.platformId,
            genre = q.genre,
            developer = q.developer,
            yearDecade = q.yearDecade,
            collectionName = q.collectionName,
            sort = q.sort.name,
            contentEpoch = app().contentEpoch,
            hiddenCount = settings.hiddenRomIds.size,
            favoriteCount = settings.favorites.size,
            lastLaunchCount = settings.lastLaunchedMs.size,
            playtimeCount = settings.playtimeMs.size,
            romCount = roms.size,
            appCount = library.visible(settings).size,
            nowBucket = System.currentTimeMillis() / LibraryBrowse.DAY_WINDOW_MS,
        ) + "|stack=" + settings.stackClones +
            if (settings.stackClones) {
                "|idN=" + identities.size +
                    "|idG=" + identities.values.count { !it.groupId.isNullOrBlank() }
            } else {
                ""
            }
        if (memoKey == entriesMemoKey) return entriesMemo
        val launchablePlatformIds = resolveLaunchablePlatformIds()
        val browsed = LibraryBrowse.browseRoms(
            roms, q,
            lastLaunchedMs = settings.lastLaunchedMs,
            favorites = settings.favorites,
            collections = settings.collections,
            playtimeMs = settings.playtimeMs,
            hiddenRomIds = settings.hiddenRomIds,
            nowMs = System.currentTimeMillis(),
            launchablePlatformIds = launchablePlatformIds,
        ).map {
            CarouselEntry(SlotKey.rom(it.id), romLabel(it), null, it)
        }
        val built = when {
            q.mode == LibraryBrowse.Mode.COLLECTION -> {
                // Walk member keys in user order (apps + ROMs interleaved).
                val name = q.collectionName.orEmpty()
                val keys = settings.collections[name].orEmpty()
                val byPkg = library.curated(settings).associateBy { it.packageName }
                val byRomId = roms.associateBy { it.id }
                val hidden = settings.hiddenRomIds
                val needle = q.text.trim()
                keys.mapNotNull { k ->
                    val entry = SlotKey.romId(k)?.let { id ->
                        if (id in hidden) return@mapNotNull null
                        val rom = byRomId[id] ?: return@mapNotNull null
                        if (q.platformId != null && rom.platformId != q.platformId) {
                            return@mapNotNull null
                        }
                        if (!LibraryBrowse.matchesGenre(rom, q.genre)) {
                            return@mapNotNull null
                        }
                        if (!LibraryBrowse.matchesDeveloper(rom, q.developer)) {
                            return@mapNotNull null
                        }
                        if (!LibraryBrowse.matchesYearDecade(rom, q.yearDecade)) {
                            return@mapNotNull null
                        }
                        if (launchablePlatformIds != null &&
                            rom.platformId !in launchablePlatformIds
                        ) {
                            return@mapNotNull null
                        }
                        CarouselEntry(SlotKey.rom(rom.id), romLabel(rom), null, rom)
                    } ?: byPkg[k]?.let {
                        // Platform/genre/developer/year chips are ROM-only — drop apps when set.
                        if (q.platformId != null ||
                            !q.genre.isNullOrBlank() ||
                            !q.developer.isNullOrBlank() ||
                            !q.yearDecade.isNullOrBlank()
                        ) {
                            return@mapNotNull null
                        }
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                    if (needle.isNotEmpty() && entry != null) {
                        val hit = entry.label.contains(needle, ignoreCase = true) ||
                            entry.key.contains(needle, ignoreCase = true)
                        if (!hit) return@mapNotNull null
                    }
                    entry
                }
            }
            q.mode == LibraryBrowse.Mode.RECENT &&
                appsOk && q.text.isBlank() -> {
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val recentApps = LibraryBrowse.orderByRecent(
                    appKeys, settings.lastLaunchedMs,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                // Apps + ROMs both ordered by recency: merge by lastLaunched.
                (recentApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.PLAYED_TODAY &&
                appsOk && q.text.isBlank() -> {
                val now = System.currentTimeMillis()
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val dayApps = LibraryBrowse.filterPlayedInWindow(
                    appKeys, settings.lastLaunchedMs, nowMs = now,
                    windowMs = LibraryBrowse.DAY_WINDOW_MS,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (dayApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.PLAYED_THIS_WEEK &&
                appsOk && q.text.isBlank() -> {
                val now = System.currentTimeMillis()
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val weekApps = LibraryBrowse.filterPlayedInWindow(
                    appKeys, settings.lastLaunchedMs, nowMs = now,
                    windowMs = LibraryBrowse.WEEK_WINDOW_MS,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (weekApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.PLAYED_THIS_MONTH &&
                appsOk && q.text.isBlank() -> {
                val now = System.currentTimeMillis()
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val monthApps = LibraryBrowse.filterPlayedInWindow(
                    appKeys, settings.lastLaunchedMs, nowMs = now,
                    windowMs = LibraryBrowse.MONTH_WINDOW_MS,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (monthApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.MOST_PLAYED &&
                appsOk && q.text.isBlank() -> {
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.playtimeMs
                    .filter { (k, v) ->
                        v > 0L && !SlotKey.isRom(k) && k in byPkg
                    }
                    .keys
                    .toList()
                val topApps = LibraryBrowse.orderByPlaytime(
                    appKeys, settings.playtimeMs,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (topApps + browsed).sortedByDescending {
                    settings.playtimeMs[it.key] ?: 0L
                }
            }
            // Recently installed: all non-hidden launchable apps by firstInstallTime
            // (not just curated grid). Platform chips are ROM-only → empty here.
            q.mode == LibraryBrowse.Mode.RECENTLY_INSTALLED &&
                appsOk -> {
                val apps = library.visible(settings).let { list ->
                    if (q.text.isBlank()) list
                    else {
                        val needle = q.text.trim()
                        list.filter {
                            it.label.contains(needle, ignoreCase = true) ||
                                it.packageName.contains(needle, ignoreCase = true)
                        }
                    }
                }
                val installMap = apps.associate { it.packageName to it.firstInstallMs }
                val ordered = LibraryBrowse.orderByInstallTime(
                    apps.map { it.packageName },
                    installMap,
                )
                val byPkg = apps.associateBy { it.packageName }
                ordered.mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
            }
            // Games: CATEGORY_GAME apps (all visible, not only curated) + ROMs.
            q.mode == LibraryBrowse.Mode.GAMES &&
                appsOk -> {
                val apps = LibraryBrowse.filterGameApps(library.visible(settings)) { it.isGame }
                    .let { list ->
                        if (q.text.isBlank()) list
                        else {
                            val needle = q.text.trim()
                            list.filter {
                                it.label.contains(needle, ignoreCase = true) ||
                                    it.packageName.contains(needle, ignoreCase = true)
                            }
                        }
                    }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                LibraryBrowse.orderByName(apps + browsed) { it.label }
            }
            q.mode == LibraryBrowse.Mode.ALPHA &&
                appsOk && q.text.isBlank() -> {
                val apps = library.curated(settings).map {
                    CarouselEntry(it.packageName, it.label, it.packageName, null)
                }
                LibraryBrowse.orderByName(apps + browsed) { it.label }
            }
            q.mode == LibraryBrowse.Mode.UNPLAYED &&
                appsOk && q.text.isBlank() -> {
                val apps = library.curated(settings)
                    .filter {
                        LibraryBrowse.isUnplayed(it.packageName, settings.lastLaunchedMs)
                    }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                LibraryBrowse.orderByName(apps + browsed) { it.label }
            }
            q.mode == LibraryBrowse.Mode.FAVORITES &&
                appsOk -> {
                val favApps = library.curated(settings)
                    .filter { it.packageName in settings.favorites }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                favApps + browsed
            }
            q.mode == LibraryBrowse.Mode.ALL &&
                appsOk &&
                q.text.isBlank() -> {
                library.curated(settings).map {
                    CarouselEntry(it.packageName, it.label, it.packageName, null)
                } + browsed
            }
            // Unified text search: include matching curated apps when there is
            // no platform/genre chip (those filters are ROM-only).
            q.text.isNotBlank() && appsOk &&
                q.mode != LibraryBrowse.Mode.COLLECTION -> {
                val needle = q.text.trim()
                val matchedApps = library.curated(settings)
                    .filter {
                        it.label.contains(needle, ignoreCase = true) ||
                            it.packageName.contains(needle, ignoreCase = true)
                    }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                // Keep A–Z / unplayed semantics under search when those chips
                // are active; otherwise curated order + browsed ROMs.
                when (q.mode) {
                    LibraryBrowse.Mode.ALPHA ->
                        LibraryBrowse.orderByName(matchedApps + browsed) { it.label }
                    LibraryBrowse.Mode.UNPLAYED -> {
                        val apps = matchedApps.filter {
                            LibraryBrowse.isUnplayed(it.key, settings.lastLaunchedMs)
                        }
                        LibraryBrowse.orderByName(apps + browsed) { it.label }
                    }
                    else -> matchedApps + browsed
                }
            }
            else -> browsed
        }
        // Custom sort (long-press All) reorders catalog rails after app/ROM merge.
        val sorted = LibraryBrowse.applyQuerySort(
            built,
            q,
            keyOf = { it.key },
            labelOf = { it.label },
            platformOf = { it.rom?.platformId },
            lastLaunchedMs = settings.lastLaunchedMs,
            playtimeMs = settings.playtimeMs,
        )
        val stacked = if (settings.stackClones) {
            stackCloneEntries(sorted, identities)
        } else {
            sorted
        }
        entriesMemoKey = memoKey
        entriesMemo = stacked
        return stacked
    }

    /**
     * Fold ROM carousel cards that share a non-null identity [groupId]
     * into one primary (max lastLaunched, else first in list). Apps pass through.
     */
    private fun stackCloneEntries(
        entries: List<CarouselEntry>,
        identities: Map<String, com.visorcraft.ghostgalleon.rom.RomIdentity>,
    ): List<CarouselEntry> {
        val romIds = entries.mapNotNull { it.rom?.id }
        if (romIds.isEmpty()) return entries
        val launchByRomId = HashMap<String, Long>(romIds.size)
        for (id in romIds) {
            launchByRomId[id] = settings.lastLaunchedMs[SlotKey.rom(id)] ?: 0L
        }
        val primary = IdentityStack.primaryIds(
            ids = romIds,
            groupId = { identities[it]?.groupId },
            lastLaunchedMs = launchByRomId,
        ).toHashSet()
        return entries.filter { entry ->
            val rom = entry.rom ?: return@filter true
            rom.id in primary
        }
    }

    private val nav get() = CarouselNavigation(entries.size)
    private val dockMove = DockMoveState()
    private val dockNav get() = DockNavigation(
        DockSlots.visibleCount(dockMove.working ?: settings.dockSlots), 0, 1)
    private var recycler: RecyclerView? = null
    private var emptyStateView: View? = null
    private var emptyMessageView: TextView? = null
    private var emptyResetButton: TextView? = null
    private var dockBar: DockBar? = null
    private var hintView: TextView? = null
    private var rootView: FrameLayout? = null
    /** Badges + chip bar + letter jump — rebuilt on browse without full paint. */
    private var filterChrome: LinearLayout? = null
    private var cardSizePx: Int = 0
    private var cardSpacingPx: Int = 0
    private var cellPaddingPx: Int = 0
    /** Cached atmospheric backdrop for active platform filters (no alloc on NAV). */
    private var cachedTintKey: String? = null
    private var cachedTintBg: Drawable? = null
    /** Letter-jump chips (A–Z / #) when ALPHA/UNPLAYED; repainted on selection. */
    private var letterChipViews: List<Pair<Char, TextView>> = emptyList()
    /** Horizontal scrollers for auto-centering active browse and letter chips. */
    private var browseScrollView: android.widget.HorizontalScrollView? = null
    private var letterScrollView: android.widget.HorizontalScrollView? = null
    /** Last filter-chrome structure; equal → restyle chip colors only. */
    private var chromeStructureKey: String = ""
    /** Browse chips + live selected predicates for in-place restyle. */
    private val browseChipHandles = mutableListOf<Pair<TextView, () -> Boolean>>()

    private var slotMenu: SlotMenu? = null
    private var picker: AppPicker? = null

    private fun selectedIndex(): Int {
        val key = state.selectedKey ?: return 0
        return entryIndexByKey[key] ?: 0
    }

    private fun romLabel(rom: RomEntry): String =
        com.visorcraft.ghostgalleon.settings.RomNames.display(rom, settings.romNames)

    private fun resetToAllBrowse() {
        val live = app().settings
        val firstKey = library.curated(live).firstOrNull()?.packageName
            ?: HiddenRoms.listed(roms, settings.hiddenRomIds)
                .firstOrNull()?.let { SlotKey.rom(it.id) }
        state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
        if (firstKey != null) state.select(firstKey, force = true)
    }

    private fun syncEmptyState(context: Context) {
        val rv = recycler ?: return
        val emptyHost = emptyStateView ?: return
        if (entries.isEmpty()) {
            rv.visibility = View.GONE
            emptyHost.visibility = View.VISIBLE
            val q = state.libraryBrowse
            emptyMessageView?.text = context.resolveText(GameDeckChromeLogic.emptyStateMessage(q))
            val showReset = GameDeckChromeLogic.shouldShowResetAction(q)
            emptyResetButton?.let { btn ->
                btn.visibility = if (showReset) View.VISIBLE else View.GONE
                btn.setTextColor(ThemePack.onFillTextColor(settings.accentColor))
                btn.background = TileBackgrounds.accentPill(context, settings.accentColor)
            }
        } else {
            emptyHost.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    /**
     * Rebuild carousel list + filter chrome in place (browse chips).
     * Keeps dock/root/modals — no activity setContentView.
     */
    override fun applyBrowseChange(): Boolean {
        val root = rootView ?: return false
        val chrome = filterChrome ?: return false
        val rv = recycler ?: return false
        val context = root.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // Live settings (chrome-only updates must not keep a stale snapshot).
        settings = (activity.application as GhostGalleonApp).settings
        val oldEntries = entries
        installEntries(buildEntries())
        val nextChrome = filterChromeStructureKey()
        if (nextChrome == chromeStructureKey && browseChipHandles.isNotEmpty()) {
            restyleBrowseChips()
        } else {
            rebuildFilterChrome(chrome, context, ::dp)
            chromeStructureKey = nextChrome
        }

        val nextSize = dp(settings.cardSizeDp).coerceAtLeast(1)
        val nextSpacing = cardSpacingPx.coerceAtLeast(dp(12))
        val nextPad = cellPaddingPx.coerceAtLeast(dp(8))
        cardSizePx = nextSize
        applyRootWallpaper(root)
        syncEmptyState(context)
        val existing = rv.adapter as? CardAdapter
        val sameCards = entries === oldEntries &&
            existing != null &&
            existing.cardSize == nextSize &&
            existing.cardSpacing == nextSpacing &&
            existing.cellPadding == nextPad
        if (sameCards && existing != null) {
            existing.paintedSelectionKey = state.selectedKey
            existing.paintedDockFocused = state.dockSlot != null
            dockBar?.updateFocus(state.dockSlot, dockMove.index)
            return true
        }
        val adapter = if (
            existing != null &&
            existing.cardSize == nextSize &&
            existing.cardSpacing == nextSpacing &&
            existing.cellPadding == nextPad
        ) {
            val diff = DiffUtil.calculateDiff(
                EntryDiff(oldEntries, entries),
                false,
            )
            existing.also {
                diff.dispatchUpdatesTo(it)
                if (entries.isNotEmpty()) {
                    it.notifyItemRangeChanged(0, entries.size, PAYLOAD_SELECTION)
                }
            }
        } else {
            CardAdapter(context, nextSize, nextSpacing, nextPad).also {
                rv.adapter = it
            }
        }
        // Selection may be outside the new filter — keep key; scroll coerces.
        adapter.paintedSelectionKey = state.selectedKey
        adapter.paintedDockFocused = state.dockSlot != null
        if (entries.isNotEmpty()) {
            rv.scrollToPosition(selectedIndex())
            rv.post { scrollSelectionToCenter(rv) }
        }
        dockBar?.updateFocus(state.dockSlot, dockMove.index)
        return true
    }

    /**
     * In-place chrome rebind. Returns false when structural chrome changed
     * (status pill / resume chip) so the activity does a full SETTINGS paint.
     */
    override fun applyChromeChange(): Boolean {
        val live = (activity.application as GhostGalleonApp).settings
        if (!live.browseChrome.allowsInPlaceChromeUpdate(settings.browseChrome)) {
            return false
        }
        val root = rootView ?: return false
        val wantsPill = showStatusPill && live.browseChrome.deckStatusPill
        val hasPill = root.findViewWithTag<View>(
            com.visorcraft.ghostgalleon.ui.deck.StatusPill.TAG,
        ) != null
        if (wantsPill != hasPill) return false
        return applyBrowseChange()
    }

    private fun platformBackdrop(context: Context, platformId: String): Drawable {
        val dm = context.resources.displayMetrics
        val key = "$platformId|${dm.widthPixels}x${dm.heightPixels}"
        cachedTintBg?.takeIf { cachedTintKey == key }?.let { return it }
        val accent = PlatformLook.accentColor(platformId)
        val liftBand = (0xFF shl 24) or
            ((((accent shr 16) and 0xFF) * 22 / 100) shl 16) or
            ((((accent shr 8) and 0xFF) * 22 / 100) shl 8) or
            ((accent and 0xFF) * 22 / 100)
        val vertical = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.BLACK, liftBand, Color.BLACK),
        )
        val radial = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                (accent and 0x00FFFFFF) or (0x3D shl 24),
                Color.TRANSPARENT,
            )
            setGradientCenter(0.5f, 0.48f)
            gradientRadius = maxOf(dm.widthPixels, dm.heightPixels) * 0.72f
        }
        return LayerDrawable(arrayOf(vertical, radial)).also {
            cachedTintKey = key
            cachedTintBg = it
        }
    }

    private fun applyRootWallpaper(root: FrameLayout) {
        val platformFilter = state.libraryBrowse.platformId
            ?.takeIf { PlatformLook.hasFilter(it) }
        if (platformFilter != null) {
            root.background = platformBackdrop(root.context, platformFilter)
        } else {
            root.setBackgroundColor(Color.BLACK)
        }
        // Unified SAF wallpaper + top/bottom vignette scrim (shared with GridDeck).
        if (root.findViewWithTag<View>(TAG_GAME_WALLPAPER) == null) {
            val uri = settings.wallpaperUri
            if (!uri.isNullOrBlank()) {
                val wallpaperView = ImageView(root.context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = 0.35f
                }
                val scrimView = View(root.context).apply {
                    background = DeckWallpaper.scrimDrawable()
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val layer = FrameLayout(root.context).apply {
                    tag = TAG_GAME_WALLPAPER
                    addView(
                        wallpaperView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        scrimView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                root.addView(
                    layer,
                    0,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                DeckWallpaper.loadAsync(root.context, uri, wallpaperView)
            }
        }
    }

    private fun filterChromeStructureKey(): String {
        val q = state.libraryBrowse
        val snap = app().browseChipSnapshot(roms, settings, System.currentTimeMillis())
        val cont = LibraryBrowse.continueKey(
            availableContinueKeys(settings),
            settings.lastLaunchedMs,
        )
        return LibraryBrowse.filterChromeStructureKey(
            platformBadge = q.platformId.orEmpty(),
            genreBadge = q.genre.orEmpty(),
            developerBadge = q.developer.orEmpty(),
            yearBadge = q.yearDecade.orEmpty(),
            letterJump = LibraryBrowse.letterJumpStructureKey(
                q.mode,
                entries.map { it.label },
            ),
            clearFilters = LibraryBrowse.hasActiveMetaFilters(q),
            searchText = q.text,
            sort = q.sort.name,
            chromeFlags = settings.browseChrome.chipBarSignature(),
            countsSig = listOf(
                snap.recent, snap.today, snap.week, snap.month, snap.top,
                snap.listedRoms, snap.unplayed,
                snap.platforms, snap.genres, snap.developers, snap.years,
                settings.favorites.size,
                library.visible(settings).size,
                library.curated(settings).size,
                settings.collections.entries.sortedBy { it.key }
                    .map { it.key to it.value.size },
            ).toString(),
            continueName = cont?.let { continueLabel(it, settings) }.orEmpty(),
            selectSig = if (state.multiSelectEnabled) {
                "s${state.multiSelectKeys.size}"
            } else {
                ""
            },
        )
    }

    private fun scrollHorizontalToCenter(
        scroller: android.widget.HorizontalScrollView?,
        child: View?,
        smooth: Boolean,
    ) {
        if (scroller == null || child == null) return
        fun applyScroll() {
            val contentWidth = scroller.getChildAt(0)?.width ?: return
            if (scroller.width <= 0 || child.width <= 0) return
            val targetX = GameDeckChromeLogic.horizontalCenterScrollX(
                childLeft = child.left,
                childWidth = child.width,
                viewportWidth = scroller.width,
                contentWidth = contentWidth,
            )
            if (smooth) {
                scroller.smoothScrollTo(targetX, 0)
            } else {
                scroller.scrollTo(targetX, 0)
            }
        }
        if (scroller.width > 0 && child.width > 0) {
            applyScroll()
        } else {
            scroller.post { applyScroll() }
        }
    }

    private fun centerActiveBrowseChip(smooth: Boolean) {
        val active = browseChipHandles.firstOrNull { (_, selected) -> selected() }?.first ?: return
        scrollHorizontalToCenter(browseScrollView, active, smooth = smooth)
    }

    private fun centerActiveLetterChip(letter: Char?, smooth: Boolean) {
        if (letter == null) return
        val active = letterChipViews.firstOrNull { (ch, _) -> ch == letter }?.second ?: return
        scrollHorizontalToCenter(letterScrollView, active, smooth = smooth)
    }

    private fun restyleBrowseChips() {
        browseChipHandles.forEach { (tv, selected) ->
            paintChip(tv, selected())
        }
        centerActiveBrowseChip(smooth = true)
        paintLetterJumpSelection()
    }

    private fun paintChip(tv: TextView, selected: Boolean) {
        tv.setTextColor(
            if (selected) ThemePack.onFillTextColor(settings.accentColor)
            else UiDimens.ALPHA_PRIMARY,
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.isSelected = selected
        tv.contentDescription = tv.context.resolveText(
            GameDeckChromeLogic.browseChipAccessibilityText(
                chipLabel = tv.text?.toString().orEmpty(),
                selected = selected,
            ),
        )
        TileBackgrounds.applyChipState(
            view = tv,
            selected = selected,
            accent = settings.accentColor,
            cornerRadiusDp = 16,
        )
    }

    private fun buildActiveFilterStrip(context: Context, dp: (Int) -> Int): View? {
        val active = GameDeckChromeLogic.activeFilters(state.libraryBrowse)
        if (active.isEmpty()) return null
        val surface = TileBackgrounds.surfaceColor(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(12), dp(4))
        }
        active.forEachIndexed { index, filter ->
            if (index > 0) {
                row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
            }
            val (label, accent) = when (filter) {
                is GameDeckChromeLogic.ActiveFilter.Platform ->
                    context.getString(
                        R.string.format_platform_badge,
                        PlatformLook.filterBadge(filter.platformId),
                    ) to PlatformLook.accentColor(filter.platformId)
                is GameDeckChromeLogic.ActiveFilter.Genre ->
                    context.getString(R.string.format_genre_badge, filter.value) to
                        settings.accentColor
                is GameDeckChromeLogic.ActiveFilter.Developer ->
                    context.getString(R.string.format_developer_badge, filter.value) to
                        settings.accentColor
                is GameDeckChromeLogic.ActiveFilter.YearDecade ->
                    context.getString(
                        R.string.format_year_badge,
                        context.resolveText(LibraryBrowse.decadeText(filter.decade)),
                    ) to settings.accentColor
                is GameDeckChromeLogic.ActiveFilter.Search ->
                    context.getString(R.string.format_search_query, filter.queryText) to
                        settings.accentColor
            }
            val textAccent = ThemePack.accentTextColor(accent, surface)
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(TileBackgrounds.chipIdleColor(context))
                    cornerRadius = context.dpF(14)
                    setStroke(context.strokePx(1), (textAccent and 0x00FFFFFF) or (0x66 shl 24))
                }
                setPadding(dp(10), dp(4), dp(8), dp(4))
                isClickable = true
                contentDescription = context.resolveText(
                    GameDeckChromeLogic.filterPillAccessibilityText(label),
                )
                setOnClickListener {
                    val next = GameDeckChromeLogic.clearFilter(state.libraryBrowse, filter)
                    state.setLibraryBrowse(next, force = true)
                }
            }
            pill.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(textAccent)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            })
            pill.addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_close)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setColorFilter(UiDimens.ALPHA_SECONDARY, PorterDuff.Mode.SRC_IN)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginStart = dp(6) },
            )
            row.addView(pill)
        }
        if (GameDeckChromeLogic.shouldShowStripClearAll(active.size)) {
            row.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            val clearAllLabel = context.getString(R.string.browse_clear_filters)
            row.addView(
                TextView(context).apply {
                    text = clearAllLabel
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ThemePack.onFillTextColor(settings.accentColor))
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    maxLines = 1
                    includeFontPadding = false
                    background = TileBackgrounds.accentPill(
                        context = context,
                        accent = settings.accentColor,
                        cornerRadiusDp = 14,
                    )
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    isClickable = true
                    contentDescription = clearAllLabel
                    setOnClickListener {
                        state.setLibraryBrowse(
                            LibraryBrowse.clearMetaFilters(state.libraryBrowse),
                            force = true,
                        )
                        Toast.makeText(
                            activity,
                            R.string.browse_filters_cleared,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(20))
            addView(row)
        }
    }

    private fun rebuildFilterChrome(
        chrome: LinearLayout,
        context: Context,
        dp: (Int) -> Int,
    ) {
        browseChipHandles.clear()
        chrome.removeAllViews()
        chrome.addView(
            buildBrowseBar(context, dp),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        buildActiveFilterStrip(context, dp)?.let { strip ->
            chrome.addView(
                strip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        buildLetterJumpBar(context, dp)?.let { letterBar ->
            chrome.addView(
                letterBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun buildEmptyState(context: Context, dp: (Int) -> Int): View {
        val host = FrameLayout(context).apply {
            visibility = View.GONE
        }
        val card = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = 24,
            padVerticalDp = 20,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        card.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setColorFilter(UiDimens.ALPHA_SUBTLE, PorterDuff.Mode.SRC_IN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(8) },
        )
        val msg = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        emptyMessageView = msg
        card.addView(
            msg,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val resetBtn = TextView(context).apply {
            setText(R.string.browse_clear_filters)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.onFillTextColor(settings.accentColor))
            background = TileBackgrounds.accentPill(context, settings.accentColor)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            isClickable = true
            setOnClickListener { resetToAllBrowse() }
        }
        emptyResetButton = resetBtn
        card.addView(
            resetBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(14)
            },
        )
        val cardW = OverlayPrimitives.responsiveCardWidth(context, maxDp = 360, widthFrac = 0.84f)
        host.addView(
            card,
            FrameLayout.LayoutParams(
                cardW,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        emptyStateView = host
        return host
    }

    override fun primaryView(context: Context): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        installEntries(buildEntries())
        cardSizePx = dp(settings.cardSizeDp)
        cardSpacingPx = dp(12)
        cellPaddingPx = dp(8)

        // FrameLayout root so modals (dock slot menu, app picker) can sit
        // on top of the whole deck.
        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.BLACK)
        }
        rootView = root
        applyRootWallpaper(root)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val chrome = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        filterChrome = chrome
        rebuildFilterChrome(chrome, context, ::dp)
        chromeStructureKey = filterChromeStructureKey()
        content.addView(
            chrome,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = CardAdapter(context, cardSizePx, cardSpacingPx, cellPaddingPx)
            // Fixed card metrics for this paint; chrome card-size rebuilds the RV.
            setHasFixedSize(true)
            setItemViewCacheSize(8)
            recycledViewPool.setMaxRecycledViews(0, 12)
            // Selection payloads only mutate ring/scale — default animator
            // fades those cells on every D-pad tick.
            itemAnimator = null
            snapHelper.attachToRecyclerView(this)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
        }
        recycler = rv
        val carouselArea = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                rv,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                buildEmptyState(context, ::dp),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        syncEmptyState(context)
        content.addView(carouselArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (settings.showHints) {
            val hints = HintBar.build(context) as TextView
            hints.text = HintBar.textFor(activity, state.dockSlot != null)
            hintView = hints
            content.addView(hints, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Same centered floating dock bar as GridDeck (reserving side insets
        // when Swap/Settings chrome icons share this panel).
        val hostChromeIcons = shouldHostSystemChromeIcons(activity)
        val bar = DockBar(
            activity, settings, library, iconLoader, roms,
            slots = { dockMove.working ?: settings.dockSlots },
            onTap = ::onDockTap,
            onLongPress = ::onDockLongPress,
        )
        dockBar = bar
        content.addView(bar.build(context, pageDots = null, reserveChromeInset = hostChromeIcons))
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Compact clock/battery overlay (omitted when single-screen TOP_STRIP hosts it).
        if (showStatusPill && settings.browseChrome.deckStatusPill) {
            root.addView(
                StatusPill.build(context, compact = true),
                StatusPill.overlayLayoutParams(context),
            )
        }
        // Grid → Game library bridge may request search once after mode switch.
        val appBridge = activity.application as GhostGalleonApp
        if (appBridge.pendingLibrarySearch) {
            appBridge.pendingLibrarySearch = false
            root.post { openSearchDialog() }
        }
        // Larger panel only (or single): Swap bottom-left, Settings bottom-right.
        if (hostChromeIcons) {
            attachSystemChromeOverlay(root, context, activity, state)
        }
        // A rebuild while the dock holds focus must repaint the ring
        // immediately — updateFocus otherwise only runs on selection updates.
        bar.updateFocus(state.dockSlot)
        rv.post { scrollSelectionToCenter(rv) }
        return root
    }

    // Centers the selected card deterministically: cancel competing
    // scrolls, jump near the target if it is not laid out yet, then snap
    // the residual distance (instant — NavRepeater must not queue smooth
    // animators that fight the next D-pad tick).
    private fun scrollSelectionToCenter(rv: RecyclerView) {
        rv.stopScroll()
        if (entries.isEmpty()) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val index = selectedIndex()
        val laidOut = lm.findViewByPosition(index)
        if (laidOut == null) {
            // Prefer left-edge of card near viewport center (cardSize known).
            val offset = ((rv.width - cardSizePx) / 2).coerceAtLeast(0)
            lm.scrollToPositionWithOffset(index, offset)
            rv.post {
                val view = lm.findViewByPosition(index) ?: return@post
                val distance = snapHelper.calculateDistanceToFinalSnap(lm, view) ?: return@post
                if (distance[0] != 0 || distance[1] != 0) {
                    rv.scrollBy(distance[0], distance[1])
                }
            }
            return
        }
        val distance = snapHelper.calculateDistanceToFinalSnap(lm, laidOut) ?: return
        if (distance[0] != 0 || distance[1] != 0) {
            rv.scrollBy(distance[0], distance[1])
        }
    }

    override fun updateSelection(): Boolean {
        val rv = recycler ?: return false
        val adapter = rv.adapter as? CardAdapter ?: return false
        // Hot path: only repaint previous + next selection (and dock-focus
        // ring drop). Full notifyDataSetChanged() rebuilt every visible card
        // tree + re-queued art on every D-pad tick.
        adapter.notifySelectionChanged(
            previousKey = adapter.paintedSelectionKey,
            previousDockFocused = adapter.paintedDockFocused,
            nextKey = state.selectedKey,
            nextDockFocused = state.dockSlot != null,
        )
        scrollSelectionToCenter(rv)
        paintLetterJumpSelection()
        dockBar?.updateFocus(state.dockSlot, dockMove.index)
        hintView?.text = if (dockMove.active) {
            HintBar.moveText(activity)
        } else {
            HintBar.textFor(activity, state.dockSlot != null)
        }
        return true
    }

    private var paintedLetter: Char? = null

    private fun paintLetterChip(tv: TextView, selected: Boolean) {
        tv.setTextColor(
            if (selected) ThemePack.onFillTextColor(settings.accentColor)
            else UiDimens.ALPHA_PRIMARY,
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.isSelected = selected
        TileBackgrounds.applyChipState(
            view = tv,
            selected = selected,
            accent = settings.accentColor,
            cornerRadiusDp = 12,
        )
    }

    private fun paintLetterJumpSelection() {
        if (letterChipViews.isEmpty()) return
        val selectedLetter = entries.getOrNull(selectedIndex())
            ?.label
            ?.let { LibraryBrowse.letterBucket(it) }
        if (selectedLetter == paintedLetter) return
        letterChipViews.forEach { (letter, tv) ->
            if (letter != selectedLetter && letter != paintedLetter) return@forEach
            paintLetterChip(tv, letter == selectedLetter)
        }
        paintedLetter = selectedLetter
        centerActiveLetterChip(selectedLetter, smooth = true)
    }

    override fun handleAction(action: Action): Boolean {
        slotMenu?.let { return it.handleAction(action) }
        picker?.let { return it.handleAction(action) }
        dockMove.index?.let { return handleDockMoveAction(action, it) }
        state.dockSlot?.let { return handleDockAction(action, it) }
        return when (action) {
            Action.CONFIRM -> {
                // Launch the DeckState selection, not merely carousel index 0
                // when selectedKey is outside the current filter (Random /
                // Continue / chip changes can select a key not in entries).
                val key = state.selectedKey
                if (key != null) {
                    launchSlotKey(
                        activity, state, roms, key,
                        reason = gameLaunchReason(key),
                    )
                }
                true
            }
            Action.NAV_LEFT, Action.NAV_RIGHT, Action.PAGE_PREV, Action.PAGE_NEXT -> {
                // Select only — updateSelection → scrollSelectionToCenter once.
                // (Was smoothScrollToPosition + center = fighting scroll systems.)
                val newIndex = nav.move(selectedIndex(), action)
                entries.getOrNull(newIndex)?.let { state.select(it.key) }
                true
            }
            // NAV DOWN leaves the carousel and focuses the dock.
            Action.NAV_DOWN -> {
                state.focusDock(0)
                true
            }
            Action.SEARCH_LIBRARY -> {
                openSearchDialog()
                true
            }
            Action.TOGGLE_FAVORITE -> {
                state.selectedKey?.let { toggleFavorite(it) }
                true
            }
            Action.SHOW_DETAILS -> {
                entries.getOrNull(selectedIndex())?.let { showDetails(it) }
                true
            }
            else -> false
        }
    }

    /**
     * Letter jump strip for A–Z ordered rails (ALPHA + UNPLAYED). Tapping a
     * letter selects the first carousel entry in that bucket and recenters.
     * Hidden when the rail is empty or mode is not letter-ordered.
     */
    private fun buildLetterJumpBar(context: Context, dp: (Int) -> Int): View? {
        val mode = state.libraryBrowse.mode
        if (mode != LibraryBrowse.Mode.ALPHA && mode != LibraryBrowse.Mode.UNPLAYED) {
            letterChipViews = emptyList()
            letterScrollView = null
            return null
        }
        val labels = entries.map { it.label }
        val letterCounts = LibraryBrowse.presentLetterCounts(labels)
        if (letterCounts.isEmpty()) {
            letterChipViews = emptyList()
            letterScrollView = null
            return null
        }
        val selectedLetter = entries.getOrNull(selectedIndex())
            ?.label
            ?.let { LibraryBrowse.letterBucket(it) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(0), dp(12), dp(6))
        }
        val chips = mutableListOf<Pair<Char, TextView>>()
        letterCounts.forEachIndexed { i, (letter, count) ->
            if (i > 0) {
                row.addView(View(context), LinearLayout.LayoutParams(dp(4), 1))
            }
            val on = letter == selectedLetter
            val chipLabel = context.resolveText(
                LibraryBrowse.labeledChip(dynamicText(letter.toString()), count),
            )
            val chip = TextView(context).apply {
                text = chipLabel
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                paintLetterChip(this, on)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                contentDescription = context.getString(
                    R.string.format_jump_to_letter,
                    letter.toString(),
                    count,
                )
                setOnClickListener {
                    val idx = LibraryBrowse.firstIndexForLetter(labels, letter)
                    val key = entries.getOrNull(idx)?.key ?: return@setOnClickListener
                    state.select(key, force = true)
                    Toast.makeText(activity, chipLabel, Toast.LENGTH_SHORT).show()
                }
            }
            chips.add(letter to chip)
            row.addView(chip)
        }
        letterChipViews = chips
        paintedLetter = selectedLetter
        val scroller = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
            addView(row)
        }
        letterScrollView = scroller
        scroller.post { centerActiveLetterChip(selectedLetter, smooth = false) }
        return scroller
    }

    // Browse chip row: All / Recent / Favorites + platform filters + search.
    private fun buildBrowseBar(context: Context, dp: (Int) -> Int): View {
        val endPad = dp(
            GameDeckChromeLogic.browseBarEndPadDp(
                showStatusPill && settings.browseChrome.deckStatusPill,
            ),
        )
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), endPad, dp(4))
        }
        fun chip(
            label: String,
            selected: () -> Boolean,
            onLongClick: (() -> Unit)? = null,
            onClick: () -> Unit,
        ): TextView =
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                paintChip(this, selected())
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { onClick() }
                if (onLongClick != null) {
                    setOnLongClickListener {
                        onLongClick()
                        true
                    }
                }
                browseChipHandles.add(this to selected)
            }
        fun localized(value: UiText): String = context.resolveText(value)
        fun counted(labelRes: Int, count: Int): String = localized(
            LibraryBrowse.labeledChip(text(labelRes), count),
        )
        fun counted(label: String, count: Int): String = localized(
            LibraryBrowse.labeledChip(dynamicText(label), count),
        )

        val q = state.libraryBrowse
        val nowMs = System.currentTimeMillis()
        val counts = app().browseChipSnapshot(roms, settings, nowMs)
        fun setQuery(next: LibraryBrowse.BrowseQuery, force: Boolean = false) {
            state.setLibraryBrowse(next, force = force)
        }
        // All = full reset: clear platform/genre/search/collection/sort AND
        // jump the carousel to the first unrestricted entry. Keeping the prior
        // NDS selection centered made "All" look like a no-op (same cards still
        // on screen even though the filter was cleared). Long-press → sort order
        // (Name / Last / Top / Platform) for catalog rails — not always-on chrome.
        row.addView(
            chip(
                localized(LibraryBrowse.allChipLabel(q.sort)),
                { LibraryBrowse.isAllChipSelected(state.libraryBrowse) },
                onLongClick = { showSortOrderDialog() },
            ) {
                val live = app().settings
                val firstKey = library.curated(live).firstOrNull()?.packageName
                    ?: HiddenRoms.listed(roms, settings.hiddenRomIds)
                        .firstOrNull()?.let { SlotKey.rom(it.id) }
                setQuery(LibraryBrowse.BrowseQuery(), force = true)
                if (firstKey != null) state.select(firstKey, force = true)
            },
        )
        row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        val chrome = settings.browseChrome
        fun addGap() {
            row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        }
        fun setBrowse(next: LibraryBrowse.BrowseQuery, force: Boolean = false) {
            val qNext = chrome.sanitize(next)
            setQuery(qNext, force = force)
            toastIfEmptyBrowse(qNext)
        }
        row.addView(
            chip(
                counted(
                    R.string.label_recent,
                    counts.recent,
                ),
                { state.libraryBrowse.mode == LibraryBrowse.Mode.RECENT },
                // Long-press: jump list of recent titles (same depth as Continue).
                onLongClick = { showRecentHistory() },
            ) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.RECENT,
                    platformId = null,
                    genre = null,
                    developer = null,
                    yearDecade = null,
                    collectionName = null,
                ))
            },
        )
        if (chrome.todayRail) {
            addGap()
            row.addView(
                chip(
                    counted(
                        R.string.label_today,
                        counts.today,
                    ),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.PLAYED_TODAY },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.PLAYED_TODAY,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.weekRail) {
            addGap()
            row.addView(
                chip(
                    counted(
                        R.string.label_week,
                        counts.week,
                    ),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.PLAYED_THIS_WEEK },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.monthRail) {
            addGap()
            row.addView(
                chip(
                    counted(
                        R.string.label_month,
                        counts.month,
                    ),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.PLAYED_THIS_MONTH },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.PLAYED_THIS_MONTH,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.installedRail) {
            addGap()
            row.addView(
                chip(
                    counted(R.string.label_installed, library.visible(settings).size),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.RECENTLY_INSTALLED },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.RECENTLY_INSTALLED,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.gamesRail) {
            addGap()
            val gameApps = LibraryBrowse.filterGameApps(library.visible(settings)) { it.isGame }.size
            val romN = counts.listedRoms
            row.addView(
                chip(
                    counted(
                        R.string.label_games,
                        LibraryBrowse.gamesCatalogCount(gameApps, romN),
                    ),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.GAMES },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.GAMES,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.topRail) {
            addGap()
            row.addView(
                chip(
                    counted(
                        R.string.label_top,
                        counts.top,
                    ),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.MOST_PLAYED },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.MOST_PLAYED,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        addGap()
        // Continue chip shows short target name when known (depth, not clutter).
        val contTargetKey = LibraryBrowse.continueKey(
            availableContinueKeys(settings),
            settings.lastLaunchedMs,
        )
        val contChipLabel = LibraryBrowse.continueChipLabel(
            contTargetKey?.let { continueLabel(it, settings) },
        )
        row.addView(
            chip(
                localized(contChipLabel),
                { false },
                onLongClick = { showContinueHistory() },
            ) {
                // Live settings: GameDeck holds a construction-time snapshot, so
                // lastLaunchedMs from `settings` can be empty after launches until
                // the next SETTINGS rebuild — Continue would silently toast-null.
                val live = app().settings
                val cont = LibraryBrowse.continueKey(
                    availableContinueKeys(live),
                    live.lastLaunchedMs,
                )
                if (cont == null) {
                    Toast.makeText(
                        activity,
                        R.string.browse_nothing_to_continue,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    jumpToContinue(cont, live)
                }
            },
        )
        if (chrome.randomChip) {
            addGap()
            row.addView(chip(context.getString(R.string.label_random), { false }) {
                pickRandomEntry()
            })
        }
        addGap()
        row.addView(
            chip(
                counted(R.string.label_favorites_short, settings.favorites.size),
                { state.libraryBrowse.mode == LibraryBrowse.Mode.FAVORITES },
                onLongClick = { showFavoritesManageDialog() },
            ) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.FAVORITES,
                    platformId = null,
                    genre = null,
                    developer = null,
                    yearDecade = null,
                    collectionName = null,
                ))
            },
        )
        if (chrome.alphaRail) {
            addGap()
            val alphaN = LibraryBrowse.alphaCatalogCount(
                library.curated(settings).size,
                counts.listedRoms,
            )
            row.addView(
                chip(
                    counted(R.string.label_alpha_sort, alphaN),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.ALPHA },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.ALPHA,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.unplayedRail) {
            addGap()
            row.addView(
                chip(
                    counted(
                        R.string.label_new,
                        counts.unplayed,
                    ),
                    { state.libraryBrowse.mode == LibraryBrowse.Mode.UNPLAYED },
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.UNPLAYED,
                        platformId = null,
                        genre = null,
                        developer = null,
                        yearDecade = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.collectionRails) {
            LibraryBrowse.presentCollectionRails(settings.collections).forEach { name ->
                if (name.equals(CollectionsOps.FAVORITES_RAIL, ignoreCase = true)) return@forEach
                addGap()
                val members = settings.collections[name]?.size ?: 0
                row.addView(
                    chip(
                        counted(name, members),
                        {
                            val cur = state.libraryBrowse
                            cur.mode == LibraryBrowse.Mode.COLLECTION &&
                                cur.collectionName == name
                        },
                        onLongClick = { showCollectionManageDialog(name) },
                    ) {
                        setBrowse(
                            LibraryBrowse.BrowseQuery(
                                mode = LibraryBrowse.Mode.COLLECTION,
                                collectionName = name,
                            ),
                        )
                    },
                )
            }
        }
        if (chrome.platformChips) {
            counts.platforms.forEach { (pid, count) ->
                addGap()
                val short = Platforms.byId(pid)?.shortName ?: pid
                row.addView(
                    chip(
                        counted(short, count),
                        { state.libraryBrowse.platformId == pid },
                        onLongClick = { showPlatformChipMenu(pid, short) },
                    ) {
                        setBrowse(
                            q.copy(
                                mode = LibraryBrowse.Mode.ALL,
                                platformId = if (q.platformId == pid) null else pid,
                                collectionName = null,
                            ),
                        )
                    },
                )
            }
        }
        // Genre chips (opt-in): gamelist meta, ROM-only filter + counts.
        if (chrome.genreChips) {
            counts.genres.forEach { (genre, count) ->
                addGap()
                val selected = q.genre?.equals(genre, ignoreCase = true) == true
                row.addView(
                    chip(
                        counted(genre, count),
                        {
                            state.libraryBrowse.genre?.equals(genre, ignoreCase = true) == true
                        },
                    ) {
                        setBrowse(
                            q.copy(
                                mode = LibraryBrowse.Mode.ALL,
                                genre = if (selected) null else genre,
                                collectionName = null,
                            ),
                        )
                    },
                )
            }
        }
        // Developer chips (opt-in): gamelist meta, ROM-only filter + counts.
        if (chrome.developerChips) {
            counts.developers.forEach { (dev, count) ->
                addGap()
                val selected = q.developer?.equals(dev, ignoreCase = true) == true
                row.addView(
                    chip(
                        counted(dev, count),
                        {
                            state.libraryBrowse.developer?.equals(dev, ignoreCase = true) == true
                        },
                    ) {
                        setBrowse(
                            q.copy(
                                mode = LibraryBrowse.Mode.ALL,
                                developer = if (selected) null else dev,
                                collectionName = null,
                            ),
                        )
                    },
                )
            }
        }
        // Year decade chips (opt-in): gamelist meta, ROM-only filter + counts.
        if (chrome.yearChips) {
            counts.years.forEach { (decade, count) ->
                addGap()
                val selected = q.yearDecade?.equals(decade, ignoreCase = true) == true
                row.addView(
                    chip(
                        counted(context.resolveText(LibraryBrowse.decadeText(decade)), count),
                        {
                            state.libraryBrowse.yearDecade?.equals(decade, ignoreCase = true) ==
                                true
                        },
                    ) {
                        setBrowse(
                            q.copy(
                                mode = LibraryBrowse.Mode.ALL,
                                yearDecade = if (selected) null else decade,
                                collectionName = null,
                            ),
                        )
                    },
                )
            }
        }
        // Contextual only: appears when platform/genre/dev/year/search is set.
        if (LibraryBrowse.hasActiveMetaFilters(q)) {
            addGap()
            val n = LibraryBrowse.activeMetaFilterCount(q)
            row.addView(
                chip(
                    counted(R.string.browse_clear_filters, n),
                    { true },
                ) {
                    setBrowse(LibraryBrowse.clearMetaFilters(q), true)
                    Toast.makeText(
                        activity,
                        R.string.browse_filters_cleared,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
        addGap()
        row.addView(
            chip(
                if (q.text.isBlank()) context.getString(R.string.action_search)
                else context.getString(R.string.format_search_query, q.text),
                { state.libraryBrowse.text.isNotBlank() },
                onLongClick = { showSearchHistory() },
            ) {
                openSearchDialog()
            },
        )
        addGap()
        row.addView(chip(
            if (state.multiSelectEnabled) {
                context.getString(R.string.format_select_count, state.multiSelectKeys.size)
            } else {
                context.getString(R.string.label_select)
            },
            { state.multiSelectEnabled },
        ) {
            if (state.multiSelectEnabled) {
                showBulkActions()
            } else {
                state.setMultiSelectEnabled(true)
            }
        })
        // Horizontal scroll with fading edge so many platforms never crush the bar.
        val scroller = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
            addView(row)
        }
        browseScrollView = scroller
        scroller.post { centerActiveBrowseChip(smooth = false) }
        return scroller
    }

    private fun showBulkActions() {
        val n = state.multiSelectKeys.size
        val romCount = state.multiSelectKeys.count {
            com.visorcraft.ghostgalleon.settings.SlotKey.isRom(it)
        }
        val live = app().settings
        val favInSel = com.visorcraft.ghostgalleon.library.MultiSelectOps
            .favoriteCountInSelection(live.favorites, state.multiSelectKeys)
        val railKeys = entries.map { it.key }
        val activeCol = activeCollectionName()
        val statsInSel = SessionMath.statsCountInSelection(
            PlayStats(live.lastLaunchedMs, live.playtimeMs),
            state.multiSelectKeys,
        )
        val unplayedInSel = SessionMath.unplayedCountInSelection(
            live.lastLaunchedMs,
            state.multiSelectKeys,
        )
        val dockedInSel = com.visorcraft.ghostgalleon.library.MultiSelectOps
            .dockedCountInSelection(live.dockSlots, state.multiSelectKeys)
        val actions = mutableListOf(
            BulkAction.SELECT_ALL to activity.getString(R.string.bulk_select_all, railKeys.size),
            BulkAction.INVERT to activity.getString(R.string.bulk_invert),
            BulkAction.FAVORITE to activity.getString(R.string.bulk_favorite, n),
        )
        if (favInSel > 0) {
            actions += BulkAction.UNFAVORITE to
                activity.getString(R.string.bulk_unfavorite, favInSel)
        }
        actions += BulkAction.PIN_GRID to activity.getString(R.string.bulk_pin_grid, n)
        actions += BulkAction.PIN_DOCK to activity.getString(R.string.bulk_pin_dock, n)
        if (dockedInSel > 0) {
            actions += BulkAction.UNPIN_DOCK to
                activity.getString(R.string.bulk_unpin_dock, dockedInSel)
        }
        if (n > 0) {
            val titleLabels = com.visorcraft.ghostgalleon.library.MultiSelectOps.labelsForKeys(
                state.multiSelectKeys,
            ) { k -> continueLabel(k, live) }
            val titleN = com.visorcraft.ghostgalleon.library.MultiSelectOps
                .bulkTitlesCount(titleLabels)
            if (titleN > 0) {
                actions += BulkAction.COPY_TITLES to
                    activity.getString(R.string.bulk_copy_titles, titleN)
            }
        }
        actions += BulkAction.ADD_COLLECTION to activity.getString(R.string.action_add_to_collection)
        actions += BulkAction.HIDE_ROMS to activity.getString(R.string.bulk_hide_roms, romCount)
        if (unplayedInSel > 0) {
            actions += BulkAction.MARK_PLAYED to
                activity.getString(R.string.bulk_mark_played, unplayedInSel)
        }
        if (statsInSel > 0) {
            actions += BulkAction.CLEAR_STATS to
                activity.getString(R.string.bulk_clear_stats, statsInSel)
        }
        if (activeCol != null) {
            actions += BulkAction.REMOVE_COLLECTION to
                activity.getString(R.string.bulk_remove_collection, activeCol, n)
        }
        actions += BulkAction.CLEAR_SELECTION to activity.getString(R.string.bulk_clear_selection)
        actions += BulkAction.CANCEL_MODE to activity.getString(R.string.bulk_cancel_mode)
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.bulk_actions,
            items = actions.map { it.second }.toTypedArray(),
            negativeRes = R.string.action_close,
        ) { which ->
                when (actions[which].first) {
                    BulkAction.SELECT_ALL -> {
                        val all = com.visorcraft.ghostgalleon.library.MultiSelectOps
                            .selectAll(railKeys)
                        state.setMultiSelectKeys(all)
                        Toast.makeText(
                            activity,
                            activity.resources.getQuantityString(
                                R.plurals.count_selected,
                                all.size,
                                all.size,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    BulkAction.INVERT -> {
                        val inv = com.visorcraft.ghostgalleon.library.MultiSelectOps
                            .invertSelectionOnRail(railKeys, state.multiSelectKeys)
                        state.setMultiSelectKeys(inv)
                        Toast.makeText(
                            activity,
                            activity.resources.getQuantityString(
                                R.plurals.count_selected,
                                inv.size,
                                inv.size,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    BulkAction.FAVORITE -> {
                        val fav = com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkFavorite(
                            app().settings.favorites, state.multiSelectKeys, add = true)
                        app().updateSettings(app().settings.copy(favorites = fav))
                        state.clearMultiSelect()
                    }
                    BulkAction.UNFAVORITE -> {
                        val fav = com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkFavorite(
                            app().settings.favorites, state.multiSelectKeys, add = false)
                        app().updateSettings(app().settings.copy(favorites = fav))
                        state.clearMultiSelect()
                        Toast.makeText(activity, R.string.deck_unfavorited, Toast.LENGTH_SHORT).show()
                    }
                    BulkAction.PIN_GRID -> {
                        val slots = com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkPinToGrid(
                            app().settings.gridSlots, state.multiSelectKeys)
                        app().updateSettings(app().settings.copy(gridSlots = slots))
                        state.clearMultiSelect()
                        Toast.makeText(activity, R.string.deck_pinned_to_grid, Toast.LENGTH_SHORT).show()
                    }
                    BulkAction.PIN_DOCK -> {
                        val (dock, added) =
                            com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkPinToDock(
                                app().settings.dockSlots, state.multiSelectKeys)
                        app().updateSettings(app().settings.copy(dockSlots = dock))
                        state.clearMultiSelect()
                        Toast.makeText(
                            activity,
                            if (added > 0) activity.resources.getQuantityString(
                                R.plurals.count_pinned_dock,
                                added,
                                added,
                            ) else activity.getString(R.string.bulk_dock_full),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    BulkAction.UNPIN_DOCK -> {
                        val (dock, removed) =
                            com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkUnpinFromDock(
                                app().settings.dockSlots, state.multiSelectKeys)
                        if (removed == 0) {
                            Toast.makeText(activity, R.string.deck_none_on_dock, Toast.LENGTH_SHORT).show()
                        } else {
                            app().updateSettings(app().settings.copy(dockSlots = dock))
                            state.clearMultiSelect()
                            Toast.makeText(
                                activity,
                                activity.resources.getQuantityString(
                                    R.plurals.count_unpinned_dock,
                                    removed,
                                    removed,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    BulkAction.COPY_TITLES -> {
                        val cur = app().settings
                        val titleLabels =
                            com.visorcraft.ghostgalleon.library.MultiSelectOps.labelsForKeys(
                                state.multiSelectKeys,
                            ) { k -> continueLabel(k, cur) }
                        val text = com.visorcraft.ghostgalleon.library.MultiSelectOps
                            .bulkTitlesText(titleLabels)
                        val count = com.visorcraft.ghostgalleon.library.MultiSelectOps
                            .bulkTitlesCount(titleLabels)
                        if (text.isEmpty()) {
                            Toast.makeText(activity, R.string.deck_nothing_to_copy, Toast.LENGTH_SHORT).show()
                        } else {
                            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            if (clipboard == null) {
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.deck_clipboard_unavailable),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        activity.getString(R.string.clipboard_titles_label),
                                        text,
                                    ),
                                )
                                state.clearMultiSelect()
                                Toast.makeText(
                                    activity,
                                    activity.resources.getQuantityString(
                                        R.plurals.count_titles_copied,
                                        count,
                                        count,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    BulkAction.ADD_COLLECTION ->
                        promptAddToCollection(state.multiSelectKeys.toList(), clearMulti = true)
                    BulkAction.HIDE_ROMS -> {
                        val (hidden, added) =
                            com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkHideRoms(
                                app().settings.hiddenRomIds, state.multiSelectKeys,
                            )
                        if (added == 0) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.bulk_no_roms),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            app().updateSettings(app().settings.copy(hiddenRomIds = hidden))
                            state.clearMultiSelect()
                            Toast.makeText(
                                activity,
                                activity.resources.getQuantityString(
                                    R.plurals.count_hidden_roms,
                                    added,
                                    added,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    BulkAction.MARK_PLAYED -> {
                        val cur = app().settings
                        val (next, stamped) = SessionMath.bulkStampLastPlayed(
                            PlayStats(cur.lastLaunchedMs, cur.playtimeMs),
                            state.multiSelectKeys,
                            System.currentTimeMillis(),
                        )
                        if (stamped == 0) {
                            Toast.makeText(activity, R.string.deck_nothing_to_mark, Toast.LENGTH_SHORT).show()
                        } else {
                            app().updateSettings(
                                cur.copy(lastLaunchedMs = next.lastLaunchedMs),
                            )
                            state.clearMultiSelect()
                            Toast.makeText(
                                activity,
                                activity.resources.getQuantityString(
                                    R.plurals.count_marked_played,
                                    stamped,
                                    stamped,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    BulkAction.CLEAR_STATS -> {
                        val cur = app().settings
                        val (next, cleared) = SessionMath.bulkClearStats(
                            PlayStats(cur.lastLaunchedMs, cur.playtimeMs),
                            state.multiSelectKeys,
                        )
                        if (cleared == 0) {
                            Toast.makeText(activity, R.string.stats_no_play_stats, Toast.LENGTH_SHORT).show()
                        } else {
                            app().updateSettings(
                                cur.copy(
                                    lastLaunchedMs = next.lastLaunchedMs,
                                    playtimeMs = next.totalPlaytimeMs,
                                ),
                            )
                            state.clearMultiSelect()
                            Toast.makeText(
                                activity,
                                activity.resources.getQuantityString(
                                    R.plurals.count_cleared_stats,
                                    cleared,
                                    cleared,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    BulkAction.REMOVE_COLLECTION -> activeCol?.let {
                        removeFromCollection(
                            it,
                            state.multiSelectKeys.toList(),
                            clearMulti = true,
                        )
                    }
                    BulkAction.CLEAR_SELECTION -> state.setMultiSelectKeys(emptySet())
                    BulkAction.CANCEL_MODE -> state.clearMultiSelect()
                }
        }
    }

    /**
     * Long-press Fav chip: open Favorites rail or clear all favorites.
     * Depth of core Fav without new always-on chrome.
     */
    private fun showFavoritesManageDialog() {
        val live = app().settings
        val count = live.favorites.size
        val labels = buildList {
            add(activity.getString(R.string.format_open_count, count))
            if (count > 0) add(activity.getString(R.string.deck_clear_all_favorites))
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.label_favorites,
            items = labels,
        ) { which ->
            when (which) {
                0 -> state.setLibraryBrowse(
                    LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES),
                    force = true,
                )
                1 -> if (count > 0) confirmClearAllFavorites()
            }
        }
    }

    private fun confirmClearAllFavorites() {
        val live = app().settings
        val n = live.favorites.size
        if (n == 0) {
            Toast.makeText(activity, R.string.deck_no_favorites, Toast.LENGTH_SHORT).show()
            return
        }
        DeckDialogs.confirm(
            context = activity,
            titleRes = R.string.deck_clear_all_favorites,
            message = activity.resources.getQuantityString(
                R.plurals.deck_clear_all_favorites_confirm,
                n,
                n,
            ),
            positiveRes = R.string.action_clear,
            destructive = true,
        ) {
            val (favs, cols) = CollectionsOps.clearAllFavorites(live.collections)
            app().updateSettings(
                live.copy(favorites = favs, collections = cols),
            )
            if (state.libraryBrowse.mode == LibraryBrowse.Mode.FAVORITES) {
                state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
            }
            Toast.makeText(
                activity,
                activity.resources.getQuantityString(
                    R.plurals.count_cleared_favorites,
                    n,
                    n,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Long-press a collection rail chip: rename, delete, or open the rail.
     */
    private fun showCollectionManageDialog(name: String) {
        val count = settings.collections[name]?.size ?: 0
        val labels = arrayOf(
            activity.getString(R.string.format_open_count, count),
            activity.getString(R.string.action_edit_members),
            activity.getString(R.string.action_rename),
            activity.getString(R.string.deck_delete_collection),
        )
        DeckDialogs.chooseItem(
            context = activity,
            title = name,
            items = labels,
        ) { which ->
            when (which) {
                0 -> state.setLibraryBrowse(
                    LibraryBrowse.BrowseQuery(
                        mode = LibraryBrowse.Mode.COLLECTION,
                        collectionName = name,
                    ),
                    force = true,
                )
                1 -> CollectionDialogs.promptMembers(
                    activity,
                    app(),
                    name,
                    labelOf = { k ->
                        entries.firstOrNull { it.key == k }?.label
                            ?: continueLabel(k, settings)
                    },
                    onChanged = {
                        if (state.libraryBrowse.collectionName == name) {
                            state.setLibraryBrowse(
                                LibraryBrowse.BrowseQuery(
                                    mode = LibraryBrowse.Mode.COLLECTION,
                                    collectionName = name,
                                ),
                                force = true,
                            )
                        }
                    },
                )
                2 -> promptRenameCollection(name)
                3 -> {
                    val next = CollectionsOps.deleteCollection(settings.collections, name)
                    app().updateSettings(settings.copy(collections = next))
                    // Leave collection filter if it pointed at the deleted rail.
                    if (state.libraryBrowse.collectionName == name) {
                        state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
                    }
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.format_deleted_named, name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun showRenameDialog(entry: CarouselEntry) {
        val rom = entry.rom ?: return
        val current = romLabel(rom)
        DeckDialogs.promptText(
            context = activity,
            title = activity.getString(R.string.deck_rename_named, rom.name),
            initialText = current,
            hintRes = R.string.deck_display_name_hint,
            positiveRes = R.string.action_save,
        ) { raw ->
            val name = raw.trim()
            app().updateSettings(
                settings.copy(
                    romNames = com.visorcraft.ghostgalleon.settings.RomNames.set(
                        app().settings.romNames,
                        rom.id,
                        name.takeIf { it.isNotEmpty() && it != rom.name },
                    ),
                ),
            )
        }
    }

    private fun resetRomName(rom: RomEntry) {
        app().updateSettings(
            settings.copy(
                romNames = com.visorcraft.ghostgalleon.settings.RomNames.clear(
                    app().settings.romNames,
                    rom.id,
                ),
            ),
        )
    }

    private fun promptRenameCollection(from: String) {
        DeckDialogs.promptText(
            context = activity,
            titleRes = R.string.deck_rename_collection,
            initialText = from,
            hintRes = R.string.settings_collection_name_hint,
            positiveRes = R.string.action_save,
        ) { to ->
            val next = CollectionsOps.renameCollection(settings.collections, from, to)
            app().updateSettings(settings.copy(collections = next))
            val dest = to.trim()
            if (dest.isNotEmpty() &&
                state.libraryBrowse.collectionName == from
            ) {
                state.setLibraryBrowse(
                    LibraryBrowse.BrowseQuery(
                        mode = LibraryBrowse.Mode.COLLECTION,
                        collectionName = dest,
                    ),
                    force = true,
                )
            }
            Toast.makeText(activity, R.string.deck_renamed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptAddToCollection(keys: List<String>, clearMulti: Boolean = false) {
        CollectionDialogs.promptAdd(
            activity,
            app(),
            keys,
            onDone = if (clearMulti) ({ state.clearMultiSelect() }) else null,
        )
    }

    /**
     * Continue / history membership. Callers also add last-launched keys
     * to the available set, so intersecting with lastLaunched is the whole
     * map — do not allocate `rom:` strings for the entire library.
     */
    private fun availableContinueKeys(live: Settings): List<String> =
        live.lastLaunchedMs.keys.toList()

    /** Human label for a continue/slot key (folder name, ROM display name, or app label). */
    private fun continueLabel(key: String, live: Settings): String {
        SlotKey.folderId(key)?.let { fid ->
            live.folders[fid]?.name?.takeIf { it.isNotBlank() }?.let { return it }
        }
        SlotKey.romId(key)?.let { id ->
            app().romEntry(id)?.let { rom ->
                return com.visorcraft.ghostgalleon.settings.RomNames.display(rom, live.romNames)
            }
        }
        live.customNames[key]?.takeIf { it.isNotBlank() }?.let { return it }
        library.all(live).firstOrNull { it.packageName == key }?.label?.let { return it }
        return key.substringAfterLast(':').ifBlank { key }
    }

    /** Jump to [key] on the Recent rail (Continue / Recent history pickers). */
    private fun jumpToContinue(
        key: String,
        live: Settings,
        toastPrefixRes: Int = R.string.label_continue,
    ) {
        // RECENT rail puts cont at the front after rebuild. force on both
        // sides so re-tapping still scrolls/rebinds when already selected.
        state.setLibraryBrowse(
            LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENT),
            force = true,
        )
        state.select(key, force = true)
        val label = continueLabel(key, live)
        Toast.makeText(
            activity,
            activity.getString(
                R.string.format_colon_pair,
                activity.getString(toastPrefixRes),
                label,
            ),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * Shared long-press history picker for Continue / Recent chips.
     * Newest-first, capped; no always-on chrome.
     */
    private fun showLaunchHistoryPicker(
        titleRes: Int,
        emptyToastRes: Int,
        toastPrefixRes: Int,
        keysOf: (available: List<String>, last: Map<String, Long>) -> List<String>,
        lineOf: (label: String, lastMs: Long?, nowMs: Long) -> UiText,
    ) {
        val live = app().settings
        val nowMs = System.currentTimeMillis()
        val keys = keysOf(availableContinueKeys(live), live.lastLaunchedMs)
        if (keys.isEmpty()) {
            Toast.makeText(activity, emptyToastRes, Toast.LENGTH_SHORT).show()
            return
        }
        val lines = keys.map { k ->
            activity.resolveText(lineOf(continueLabel(k, live), live.lastLaunchedMs[k], nowMs))
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = titleRes,
            items = lines,
        ) { which ->
            if (which in keys.indices) {
                jumpToContinue(
                    keys[which],
                    app().settings,
                    toastPrefixRes = toastPrefixRes,
                )
            }
        }
    }

    /**
     * Long-press Continue: pick any recent title (newest first, capped).
     * No new always-on chrome — depth of the core Continue chip.
     */
    private fun showContinueHistory() {
        showLaunchHistoryPicker(
            titleRes = R.string.label_continue,
            emptyToastRes = R.string.browse_nothing_to_continue,
            toastPrefixRes = R.string.label_continue,
            keysOf = { available, last ->
                LibraryBrowse.continueHistory(available, last)
            },
            lineOf = LibraryBrowse::continueHistoryLine,
        )
    }

    /**
     * Long-press Recent: same history pool as Continue, titled Recent.
     * Depth of the core Recent chip — no always-on chrome.
     */
    private fun showRecentHistory() {
        showLaunchHistoryPicker(
            titleRes = R.string.label_recent,
            emptyToastRes = R.string.browse_nothing_recent,
            toastPrefixRes = R.string.label_recent,
            keysOf = { available, last ->
                LibraryBrowse.recentHistory(available, last)
            },
            lineOf = LibraryBrowse::recentHistoryLine,
        )
    }

    /**
     * Platforms that have at least one installed player. Null when
     * launchableOnly is off (no filter). Empty set when on and nothing installed.
     * Cached on the app for PM query reuse across browse rebuilds.
     */
    private fun resolveLaunchablePlatformIds(
        live: Settings = settings,
    ): Set<String>? = app().launchablePlatformIds(live.browseChrome.launchableOnly)

    private fun markAsPlayed(key: String) {
        EntryActions.markAsPlayed(activity, key)
    }

    private fun clearPlayStats(key: String) {
        EntryActions.clearPlayStats(activity, key, continueLabel(key, app().settings))
    }

    /**
     * Select a random item. Prefers the **current rail/filter** (Random stays
     * inside what you see). Falls back to the full library and resets browse
     * to All when the carousel is empty.
     */
    private fun pickRandomEntry() {
        val live = app().settings
        val filtered = entries.map { it.key }
        val full = buildList {
            addAll(library.curated(live).map { it.packageName })
            addAll(
                HiddenRoms.listed(roms, live.hiddenRomIds)
                    .map { SlotKey.rom(it.id) },
            )
        }
        val pool = LibraryBrowse.randomPool(filtered, full)
        val key = LibraryBrowse.pickRandom(pool) { size ->
            java.util.concurrent.ThreadLocalRandom.current().nextInt(size)
        }
        if (key == null) {
            Toast.makeText(activity, R.string.browse_library_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (filtered.isEmpty()) {
            // Full library view so the selection is present after rebuild.
            state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
        }
        state.select(key, force = true)
        val scope = activity.getString(
            if (filtered.isNotEmpty()) R.string.browse_random_filtered
            else R.string.browse_random_pick,
        )
        Toast.makeText(activity, scope, Toast.LENGTH_SHORT).show()
    }

    private fun openSearchDialog() {
        val (container, input) = DeckDialogs.createInput(
            context = activity,
            initialText = state.libraryBrowse.text,
            hintRes = R.string.browse_search_hint,
        )
        val hasHistory = app().settings.searchHistory.isNotEmpty()
        val builder = DeckDialogs.builder(activity)
            .setTitle(R.string.browse_search_library)
            .setView(container)
            .setPositiveButton(R.string.action_search) { _, _ ->
                applySearch(input.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.action_cancel, null)
        if (hasHistory) {
            builder.setNeutralButton(R.string.action_history) { _, _ ->
                showSearchHistory()
            }
        } else {
            builder.setNeutralButton(R.string.action_clear) { _, _ ->
                applySearch("")
            }
        }
        DeckDialogs.show(builder, activity)
    }

    /** Apply a library text filter and remember non-blank queries. */
    private fun applySearch(raw: String) {
        val text = raw.trim()
        val next = state.libraryBrowse.copy(text = text)
        state.setLibraryBrowse(next)
        if (text.isNotEmpty()) {
            val live = app().settings
            val history = SearchHistory.push(live.searchHistory, text)
            if (history != live.searchHistory) {
                app().updateSettings(live.copy(searchHistory = history))
            }
        }
        val n = estimateCarouselSize(next)
        Toast.makeText(
            activity,
            activity.resolveText(BrowseFeedback.searchApplied(n, text)),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * Long-press Search (or Search dialog → History): pick a recent query.
     * Depth of core Search without new always-on chrome.
     */
    private fun showSearchHistory() {
        val live = app().settings
        val history = live.searchHistory
        if (history.isEmpty()) {
            Toast.makeText(activity, R.string.browse_no_search_history, Toast.LENGTH_SHORT).show()
            return
        }
        val items = history.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.browse_recent_searches,
            items = items,
            neutralRes = R.string.browse_clear_history,
            onNeutral = {
                app().updateSettings(live.copy(searchHistory = SearchHistory.clear()))
                Toast.makeText(
                    activity,
                    R.string.browse_search_history_cleared,
                    Toast.LENGTH_SHORT,
                ).show()
            },
        ) { which ->
            if (which in items.indices) applySearch(items[which])
        }
    }

    /** Toast when a rail/filter would show zero cards. */
    private fun toastIfEmptyBrowse(q: LibraryBrowse.BrowseQuery) {
        val hint = BrowseFeedback.emptyHint(q) ?: return
        if (estimateCarouselSize(q) == 0) {
            Toast.makeText(activity, activity.resolveText(hint), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Lightweight size estimate for feedback toasts (apps + ROMs, same gates
     * as the carousel for search/platform/genre).
     */
    private fun estimateCarouselSize(q: LibraryBrowse.BrowseQuery): Int {
        val live = app().settings
        val now = System.currentTimeMillis()
        val browsed = LibraryBrowse.browseRoms(
            roms, q,
            lastLaunchedMs = live.lastLaunchedMs,
            favorites = live.favorites,
            collections = live.collections,
            playtimeMs = live.playtimeMs,
            hiddenRomIds = live.hiddenRomIds,
            nowMs = now,
            launchablePlatformIds = resolveLaunchablePlatformIds(live),
        )
        val appsOk = q.platformId == null &&
            q.genre.isNullOrBlank() &&
            q.developer.isNullOrBlank() &&
            q.yearDecade.isNullOrBlank()
        if (q.mode == LibraryBrowse.Mode.RECENTLY_INSTALLED) {
            val apps = library.visible(live)
            if (q.text.isBlank()) return apps.size
            val needle = q.text.trim()
            return apps.count {
                it.label.contains(needle, ignoreCase = true) ||
                    it.packageName.contains(needle, ignoreCase = true)
            }
        }
        if (q.mode == LibraryBrowse.Mode.COLLECTION) {
            val name = q.collectionName.orEmpty()
            val keys = live.collections[name].orEmpty()
            if (keys.isEmpty()) return 0
            // Member keys that still resolve (apps or visible ROMs).
            val hidden = live.hiddenRomIds
            val byRom = roms.associateBy { it.id }
            val curated = library.curated(live).map { it.packageName }.toSet()
            return keys.count { k ->
                SlotKey.romId(k)?.let { id -> id !in hidden && id in byRom } == true ||
                    (!SlotKey.isRom(k) && k in curated)
            }
        }
        var n = browsed.size
        if (!appsOk) return n
        when (q.mode) {
            LibraryBrowse.Mode.ALL,
            LibraryBrowse.Mode.GAMES,
            LibraryBrowse.Mode.ALPHA,
            -> {
                val apps = when (q.mode) {
                    LibraryBrowse.Mode.GAMES ->
                        LibraryBrowse.filterGameApps(library.curated(live)) { it.isGame }
                    LibraryBrowse.Mode.UNPLAYED -> emptyList()
                    else -> library.curated(live)
                }
                n += if (q.text.isBlank()) {
                    apps.size
                } else {
                    val needle = q.text.trim()
                    apps.count {
                        it.label.contains(needle, ignoreCase = true) ||
                            it.packageName.contains(needle, ignoreCase = true)
                    }
                }
            }
            LibraryBrowse.Mode.RECENT,
            LibraryBrowse.Mode.PLAYED_TODAY,
            LibraryBrowse.Mode.PLAYED_THIS_WEEK,
            LibraryBrowse.Mode.PLAYED_THIS_MONTH,
            LibraryBrowse.Mode.MOST_PLAYED,
            LibraryBrowse.Mode.FAVORITES,
            -> {
                // Apps interleave; count positive last-launch / playtime / fav keys.
                val byPkg = library.curated(live).associateBy { it.packageName }
                n += when (q.mode) {
                    LibraryBrowse.Mode.FAVORITES ->
                        live.favorites.count { !SlotKey.isRom(it) && it in byPkg }
                    LibraryBrowse.Mode.MOST_PLAYED ->
                        live.playtimeMs.count { (k, v) ->
                            v > 0L && !SlotKey.isRom(k) && k in byPkg
                        }
                    LibraryBrowse.Mode.PLAYED_TODAY ->
                        LibraryBrowse.filterPlayedInWindow(
                            live.lastLaunchedMs.keys.filter { !SlotKey.isRom(it) && it in byPkg },
                            live.lastLaunchedMs,
                            nowMs = now,
                            windowMs = LibraryBrowse.DAY_WINDOW_MS,
                        ).size
                    LibraryBrowse.Mode.PLAYED_THIS_WEEK ->
                        LibraryBrowse.filterPlayedInWindow(
                            live.lastLaunchedMs.keys.filter { !SlotKey.isRom(it) && it in byPkg },
                            live.lastLaunchedMs,
                            nowMs = now,
                            windowMs = LibraryBrowse.WEEK_WINDOW_MS,
                        ).size
                    LibraryBrowse.Mode.PLAYED_THIS_MONTH ->
                        LibraryBrowse.filterPlayedInWindow(
                            live.lastLaunchedMs.keys.filter { !SlotKey.isRom(it) && it in byPkg },
                            live.lastLaunchedMs,
                            nowMs = now,
                            windowMs = LibraryBrowse.MONTH_WINDOW_MS,
                        ).size
                    else -> // RECENT
                        live.lastLaunchedMs.keys.count { !SlotKey.isRom(it) && it in byPkg }
                }
            }
            else -> {}
        }
        return n
    }

    // Apps launch through their package intent, ROMs through the platform
    // template; both open on the non-interactive display.
    private fun launch(entry: CarouselEntry, playerId: String? = null) {
        val reason = if (playerId != null) {
            LaunchReason.OTHER
        } else {
            gameLaunchReason(entry.key)
        }
        launchSlotKey(
            activity, state, roms, entry.key,
            playerId = playerId,
            reason = reason,
        )
    }

    private fun gameLaunchReason(key: String): LaunchReason {
        val live = app().settings
        val cont = LibraryBrowse.continueKey(
            live.lastLaunchedMs.keys.toList(),
            live.lastLaunchedMs,
        )
        return if (
            key == cont &&
            state.libraryBrowse.mode == LibraryBrowse.Mode.RECENT
        ) {
            LaunchReason.CONTINUE
        } else {
            LaunchReason.SLOT
        }
    }

    private fun app(): GhostGalleonApp = activity.application as GhostGalleonApp

    private fun toggleFavorite(key: String) {
        EntryActions.toggleFavorite(activity, key)
    }

    private fun openWithMenu(entry: CarouselEntry) {
        val rom = entry.rom ?: return
        EntryActions.openWith(activity, rom) { playerId ->
            launch(entry, playerId = playerId)
        }
    }

    private fun showPlayerProfileMenu(rom: RomEntry) {
        EntryActions.playerProfile(activity, rom)
    }

    private fun showScreensPlotMenu(rom: RomEntry) {
        EntryActions.screensPlot(activity, rom)
    }

    private fun setArtOverride(rom: RomEntry) {
        val host = activity as? com.visorcraft.ghostgalleon.ui.BaseDeckActivity
        if (host == null) {
            Toast.makeText(
                activity,
                R.string.deck_cannot_open_image_picker,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        host.requestCustomIcon { uri ->
            val next = settings.artOverrides + (rom.id to uri.toString())
            // Drop mem+disk for this rom so override is re-decoded (source
            // stamps also reject mismatched cache on the next load).
            app().artCache.invalidate(rom.id)
            app().updateSettings(settings.copy(artOverrides = next))
            Toast.makeText(activity, R.string.deck_artwork_set, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToGrid(key: String) {
        val filled = CollectionsOps.bulkFillSlots(settings.gridSlots, listOf(key))
        app().updateSettings(settings.copy(gridSlots = filled))
        Toast.makeText(activity, R.string.deck_added_to_grid, Toast.LENGTH_SHORT).show()
    }

    private fun pinToDock(key: String) {
        DockActions.pin(activity, app(), key) { slots, toast -> updateDockSlots(slots, toast) }
    }

    private fun unpinFromDock(key: String) {
        DockActions.unpin(activity, app(), key) { slots, toast -> updateDockSlots(slots, toast) }
    }

    private fun hideRom(rom: RomEntry) {
        val next = HiddenRoms.hide(settings.hiddenRomIds, rom.id)
        app().updateSettings(settings.copy(hiddenRomIds = next))
        Toast.makeText(
            activity,
            activity.getString(R.string.format_hidden_named, rom.name),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Named collection rail under browse, or Favorites when Fav is selected. */
    private fun activeCollectionName(): String? =
        CollectionsOps.activeCollectionName(
            state.libraryBrowse.mode.name,
            state.libraryBrowse.collectionName,
        )

    /**
     * Drop [keys] from [name]. Favorites also clears the favorites set.
     * Leaves the collection filter when the rail is deleted (emptied).
     */
    private fun removeFromCollection(
        name: String,
        keys: List<String>,
        clearMulti: Boolean = false,
    ) {
        if (keys.isEmpty()) {
            Toast.makeText(activity, R.string.deck_nothing_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val n = name.trim()
        val cols = CollectionsOps.bulkRemoveFromCollection(settings.collections, n, keys)
        val favs = if (n.equals(CollectionsOps.FAVORITES_RAIL, ignoreCase = true)) {
            CollectionsOps.bulkRemoveFavorites(settings.favorites, keys)
        } else {
            settings.favorites
        }
        app().updateSettings(settings.copy(favorites = favs, collections = cols))
        if (clearMulti) state.clearMultiSelect()
        // Emptied user collection is dropped by bulkRemove — leave the filter.
        val stillThere = n.equals(CollectionsOps.FAVORITES_RAIL, ignoreCase = true) || n in cols
        if (!stillThere &&
            state.libraryBrowse.mode == LibraryBrowse.Mode.COLLECTION &&
            state.libraryBrowse.collectionName == n
        ) {
            state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
        }
        val feedback = if (keys.size == 1) {
            activity.getString(R.string.format_removed_from_named, n)
        } else {
            activity.resources.getQuantityString(
                R.plurals.count_removed_collection,
                keys.size,
                keys.size,
                n,
            )
        }
        Toast.makeText(activity, feedback, Toast.LENGTH_SHORT).show()
    }

    /** Named COLLECTION rail only (ordered list); not the unordered Fav set. */
    private fun reorderCollectionName(): String? {
        val q = state.libraryBrowse
        return if (CollectionsOps.canReorderCollection(q.mode.name, q.collectionName)) {
            q.collectionName!!.trim()
        } else {
            null
        }
    }

    private fun reorderInCollection(name: String, key: String, choice: SlotMenu.Choice) {
        val next = when (choice) {
            SlotMenu.Choice.MOVE_TO_TOP ->
                CollectionsOps.moveMemberToEdge(settings.collections, name, key, toFront = true)
            SlotMenu.Choice.MOVE_TO_END ->
                CollectionsOps.moveMemberToEdge(settings.collections, name, key, toFront = false)
            SlotMenu.Choice.MOVE_UP ->
                CollectionsOps.moveMemberBy(settings.collections, name, key, delta = -1)
            SlotMenu.Choice.MOVE_DOWN ->
                CollectionsOps.moveMemberBy(settings.collections, name, key, delta = 1)
            else -> settings.collections
        }
        app().updateSettings(settings.copy(collections = next))
        val feedback = when (choice) {
            SlotMenu.Choice.MOVE_TO_TOP -> R.string.deck_moved_top
            SlotMenu.Choice.MOVE_TO_END -> R.string.deck_moved_end
            SlotMenu.Choice.MOVE_UP -> R.string.deck_moved_up
            SlotMenu.Choice.MOVE_DOWN -> R.string.deck_moved_down
            else -> R.string.deck_reordered
        }
        Toast.makeText(activity, feedback, Toast.LENGTH_SHORT).show()
    }

    /**
     * Long-press platform chip: filter / clear / sort on that platform.
     * No always-on chrome — depth of core platform chips.
     */
    private fun showPlatformChipMenu(platformId: String, shortName: String) {
        val actions = LibraryBrowse.platformChipActions(
            state.libraryBrowse,
            platformId,
            shortName,
        )
        if (actions.isEmpty()) return
        fun label(action: LibraryBrowse.PlatformChipAction): String = when (action.kind) {
            LibraryBrowse.PlatformChipActionKind.FILTER ->
                activity.getString(R.string.browse_filter_platform, action.shortName)
            LibraryBrowse.PlatformChipActionKind.CLEAR ->
                activity.getString(R.string.browse_clear_platform)
            LibraryBrowse.PlatformChipActionKind.SORT_NAME ->
                activity.getString(R.string.browse_sort_alpha_platform, action.shortName)
            LibraryBrowse.PlatformChipActionKind.SORT_LAST_PLAYED ->
                activity.getString(R.string.browse_sort_last_platform, action.shortName)
        }
        val labels = actions.map(::label).toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            title = shortName.ifBlank { platformId },
            items = labels,
        ) { which ->
            if (which !in actions.indices) return@chooseItem
            val action = actions[which]
            val next = settings.browseChrome.sanitize(action.query)
            state.setLibraryBrowse(next, force = true)
            toastIfEmptyBrowse(next)
            Toast.makeText(activity, label(action), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Long-press All chip: pick catalog sort (Default / Name / Last played /
     * Most played / Platform). Applies to All / Games / Fav / A–Z / New;
     * time rails switch to All so the sort is visible. Not always-on chrome.
     */
    private fun showSortOrderDialog() {
        val options = listOf(
            LibraryBrowse.Sort.DEFAULT,
            LibraryBrowse.Sort.NAME,
            LibraryBrowse.Sort.LAST_PLAYED,
            LibraryBrowse.Sort.MOST_PLAYED,
            LibraryBrowse.Sort.PLATFORM,
        )
        val current = state.libraryBrowse.sort
        val labels = options.map { sort ->
            val name = activity.resolveText(LibraryBrowse.sortDisplayName(sort))
            if (sort == current) activity.getString(R.string.format_selected_check, name) else name
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.deck_sort_order,
            items = labels,
        ) { which ->
            if (which !in options.indices) return@chooseItem
            val picked = options[which]
            val next = LibraryBrowse.queryWithSort(state.libraryBrowse, picked)
            state.setLibraryBrowse(next, force = true)
            val toast = if (picked == LibraryBrowse.Sort.DEFAULT) {
                activity.getString(R.string.label_default_order)
            } else {
                activity.getString(
                    R.string.format_sorted_by,
                    activity.resolveText(LibraryBrowse.sortDisplayName(picked)),
                )
            }
            Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDetails(entry: CarouselEntry) {
        val key = entry.key
        val rom = entry.rom
        val identity = rom?.let { app().romIdentities[it.id] }
        val input = GameDetails.Input(
            title = entry.label,
            key = key,
            kind = if (rom != null) GameDetails.Kind.ROM else GameDetails.Kind.APP,
            platformId = rom?.platformId,
            genre = rom?.genre,
            developer = rom?.developer,
            year = rom?.year,
            rating = rom?.rating,
            description = rom?.description,
            lastLaunchedMs = settings.lastLaunchedMs[key],
            playtimeMs = settings.playtimeMs[key] ?: 0L,
            favorite = key in settings.favorites,
            collections = GameDetails.collectionsContaining(settings.collections, key),
            nowMs = System.currentTimeMillis(),
            identity = identity,
        )
        val body = activity.resolveText(GameDetails.body(input))
        val related = relatedOptionsFor(rom)
        val ferryPeers = if (rom != null) saveFerryPeers(rom) else emptyList()
        val builder = DeckDialogs.builder(activity)
            .setTitle(R.string.details_title)
            .setPositiveButton(R.string.action_ok, null)
            .setNeutralButton(R.string.action_copy_title) { _, _ ->
                copyTitleToClipboard(entry.label)
            }
        var bodyView: TextView? = null
        if (rom != null) {
            val pad = (20 * activity.resources.displayMetrics.density).toInt()
            val surface = TileBackgrounds.surfaceColor(activity)
            val message = TextView(activity).apply {
                text = body
                setTextColor(UiDimens.ALPHA_SECONDARY)
                setTextIsSelectable(true)
                setPadding(pad, pad / 2, pad, pad / 2)
            }
            bodyView = message
            val helperValue = TextView(activity).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ThemePack.accentTextColor(settings.accentColor, surface))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
            }
            bindRomHelperValue(helperValue, rom.id)
            val helperRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, activity.dp(8), pad, activity.dp(8))
                isFocusable = true
                background = TileBackgrounds.focusableRow(
                    context = activity,
                    accent = settings.accentColor,
                    cornerRadiusDp = 10,
                )
                setOnClickListener { showRomHelperPicker(rom.id, helperValue) }
                setOnLongClickListener {
                    clearRomHelper(rom.id, helperValue)
                    true
                }
            }
            helperRow.addView(
                TextView(activity).apply {
                    setText(R.string.settings_play_host_helper)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(UiDimens.ALPHA_PRIMARY)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            helperRow.addView(
                helperValue,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f),
            )
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(message)
                addView(helperRow)
            }
            builder.setView(
                OverlayPrimitives.MaxHeightScrollView(
                    activity,
                    OverlayPrimitives.maxModalHeight(activity, 0.56f),
                ).apply { addView(content) },
            )
        } else {
            builder.setMessage(body)
        }
        // Apps: system package details. Same-title ferry dests. Else related.
        when {
            rom == null && !SlotKey.isRom(key) ->
                builder.setNegativeButton(R.string.action_app_info) { _, _ -> openAppInfo(key) }
            rom != null && ferryPeers.isNotEmpty() ->
                builder.setNegativeButton(R.string.settings_save_ferry) { _, _ ->
                    showSaveFerryDests(rom, ferryPeers)
                }
            related.isNotEmpty() ->
                builder.setNegativeButton(R.string.action_browse_related) { _, _ ->
                    showBrowseRelatedDialog(related)
                }
        }
        val dialog = DeckDialogs.show(builder, activity)
        if (rom != null && ferryPeers.isNotEmpty()) {
            val ferryBtn = dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
            ferryBtn.visibility = View.GONE
            app().hasFerryDocs(rom) { readable ->
                if (dialog.isShowing && readable) ferryBtn.visibility = View.VISIBLE
            }
        }
        val fullHash = GameDetails.copyableHash(identity)
        if (fullHash != null) {
            val hashTarget = bodyView
                ?: dialog.findViewById<TextView>(android.R.id.message)
            hashTarget?.setOnLongClickListener {
                copyHashToClipboard(fullHash)
                true
            }
        }
    }

    private fun helperAppLabel(packageName: String): String =
        library.byPackage(settings)[packageName]?.label ?: packageName

    private fun bindRomHelperValue(value: TextView, romId: String) {
        val pkg = app().settings.romHelpers[romId]
        value.text = pkg?.let { helperAppLabel(it) }
            ?: activity.getString(R.string.action_none)
    }

    private fun helperPickerApps() = library.visible(settings)
        .sortedBy { it.label.lowercase() }
        .filter { entry ->
            !HelperEmbedPolicy.refused(entry.packageName) &&
                !CompanionRoleResolve.pinConflictsWithSession(
                    entry.packageName,
                    app().sessionSurface,
                )
        }

    private fun clearRomHelper(romId: String, value: TextView) {
        val live = app().settings
        app().updateSettings(live.copy(romHelpers = live.romHelpers - romId))
        bindRomHelperValue(value, romId)
    }

    private fun showRomHelperPicker(romId: String, value: TextView) {
        val apps = helperPickerApps()
        if (apps.isEmpty()) {
            Toast.makeText(activity, R.string.settings_no_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.settings_play_host_helper,
            items = labels,
            neutralRes = R.string.action_clear,
            onNeutral = { clearRomHelper(romId, value) },
        ) { which ->
            val pkg = apps[which].packageName
            val live = app().settings
            app().updateSettings(live.copy(romHelpers = live.romHelpers + (romId to pkg)))
            bindRomHelperValue(value, romId)
        }
    }

    private fun saveFerryPeers(rom: RomEntry): List<RomEntry> {
        if (!settings.saveFerryEnabled) return emptyList()
        val fromId = app().romIdentities[rom.id]
        return app().romEntries.filter { other ->
            other.id != rom.id && SaveFerry.sameTitle(fromId, app().romIdentities[other.id])
        }
    }

    private fun showSaveFerryDests(from: RomEntry, peers: List<RomEntry>) {
        if (peers.isEmpty()) return
        if (peers.size == 1) {
            startSaveFerry(from, peers[0])
            return
        }
        val labels = peers.map { romLabel(it) }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.settings_save_ferry,
            items = labels,
        ) { which ->
            if (which in peers.indices) startSaveFerry(from, peers[which])
        }
    }

    private fun startSaveFerry(from: RomEntry, to: RomEntry) {
        val refuse = SaveFerry.refuse(
            app().romIdentities[from.id],
            app().romIdentities[to.id],
            playerIdOf(from),
            playerIdOf(to),
            app().destIsOpenYield(to.id),
        )
        if (refuse != FerryRefuse.NONE) {
            val reason = when (refuse) {
                FerryRefuse.YIELD_DEST -> R.string.settings_stage_yield
                else -> R.string.ferry_refuse_player
            }
            Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show()
            return
        }
        app().loadFerryOffers(from, to, refuse) { offers ->
            if (offers.isEmpty()) {
                Toast.makeText(activity, R.string.ferry_dest_unwritable, Toast.LENGTH_SHORT).show()
                return@loadFerryOffers
            }
            if (offers.size == 1) {
                confirmSaveFerry(from, to, offers[0])
            } else {
                pickSaveFerryOffer(from, to, offers)
            }
        }
    }

    private fun pickSaveFerryOffer(from: RomEntry, to: RomEntry, offers: List<FerryOffer>) {
        val labels = offers.map { ferryKindLabel(it) }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.settings_save_ferry,
            items = labels,
        ) { which ->
            if (which in offers.indices) confirmSaveFerry(from, to, offers[which])
        }
    }

    private fun confirmSaveFerry(from: RomEntry, to: RomEntry, offer: FerryOffer) {
        DeckDialogs.confirm(
            context = activity,
            titleRes = R.string.settings_save_ferry,
            message = activity.getString(
                R.string.confirm_save_ferry,
                ferryKindLabel(offer),
                romLabel(from),
                romLabel(to),
            ),
            positiveRes = R.string.action_ok,
        ) {
            if (app().destIsOpenYield(to.id)) {
                Toast.makeText(activity, R.string.settings_stage_yield, Toast.LENGTH_SHORT)
                    .show()
                return@confirm
            }
            app().ferryCopy(offer)
        }
    }

    private fun ferryKindLabel(offer: FerryOffer): String = when (offer.kind) {
        FerryKind.RA_SRM -> "SRM"
        FerryKind.RA_STATE -> {
            val slot = offer.slot
            if (slot == null || slot == 0) "state" else "state$slot"
        }
    }

    private fun playerIdOf(rom: RomEntry): String? {
        val preferred = RomProfiles.preferredPlayerId(
            rom.id,
            settings.romProfiles,
            settings.defaultPlayers[rom.platformId],
        )
        val platform = Platforms.byId(rom.platformId) ?: return preferred
        return PlayerResolver.resolve(platform, preferred) { pkg ->
            activity.packageManager.isInstalled(pkg)
        }?.id ?: preferred
    }

    private fun copyHashToClipboard(hash: String) {
        val text = hash.trim().ifEmpty { return }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        if (clipboard == null) {
            Toast.makeText(activity, R.string.deck_clipboard_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                activity.getString(R.string.identity_hash, IdentityStack.shortHash(text)),
                text,
            ),
        )
        Toast.makeText(activity, R.string.action_copy, Toast.LENGTH_SHORT).show()
    }

    /** Related-filter options for a ROM, gated by browse-chrome chip flags. */
    private fun relatedOptionsFor(rom: com.visorcraft.ghostgalleon.rom.RomEntry?): List<GameDetails.RelatedOption> {
        if (rom == null) return emptyList()
        val chrome = settings.browseChrome
        return GameDetails.relatedOptions(
            platformId = rom.platformId,
            genre = rom.genre,
            developer = rom.developer,
            year = rom.year,
            allowPlatform = chrome.platformChips,
            allowGenre = chrome.genreChips,
            allowDeveloper = chrome.developerChips,
            allowYear = chrome.yearChips,
        )
    }

    /**
     * Apply a Browse-related jump: All rail + one meta filter; preserves sort.
     * Sanitize keeps options aligned with chrome (caller only offers allowed).
     */
    private fun applyRelatedOption(option: GameDetails.RelatedOption) {
        val next = settings.browseChrome.sanitize(
            GameDetails.toBrowseQuery(option, sort = state.libraryBrowse.sort),
        )
        state.setLibraryBrowse(next, force = true)
        toastIfEmptyBrowse(next)
        Toast.makeText(
            activity,
            activity.resolveText(GameDetails.relatedOptionLabel(option)),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showBrowseRelatedDialog(options: List<GameDetails.RelatedOption>) {
        if (options.isEmpty()) return
        if (options.size == 1) {
            applyRelatedOption(options[0])
            return
        }
        val labels = options.map {
            activity.resolveText(GameDetails.relatedOptionLabel(it))
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.details_browse_related,
            items = labels,
        ) { which ->
            if (which in options.indices) applyRelatedOption(options[which])
        }
    }

    private fun openAppInfo(packageName: String) {
        EntryActions.openAppInfo(activity, packageName)
    }

    private fun copyTitleToClipboard(title: String) {
        EntryActions.copyTitle(activity, title)
    }

    private fun openEntryMenu(entry: CarouselEntry) {
        val key = entry.key
        val fav = key in settings.favorites
        val activeCol = activeCollectionName()
        val reorderCol = reorderCollectionName()
        val isApp = entry.rom == null && !SlotKey.isRom(key)
        val liveStats = PlayStats(
            lastLaunchedMs = settings.lastLaunchedMs,
            totalPlaytimeMs = settings.playtimeMs,
        )
        val neverPlayed = LibraryBrowse.isUnplayed(key, settings.lastLaunchedMs)
        val related = relatedOptionsFor(entry.rom)
        val choices = buildList {
            add(SlotMenu.Choice.DETAILS)
            add(SlotMenu.Choice.COPY_TITLE)
            if (related.isNotEmpty()) {
                add(SlotMenu.Choice.BROWSE_RELATED)
            }
            if (neverPlayed) {
                add(SlotMenu.Choice.MARK_PLAYED)
            }
            if (SessionMath.hasStats(liveStats, key)) {
                add(SlotMenu.Choice.CLEAR_PLAY_STATS)
            }
            if (DockSlots.containsKey(settings.dockSlots, key)) {
                add(SlotMenu.Choice.UNPIN_FROM_DOCK)
            } else {
                add(SlotMenu.Choice.PIN_TO_DOCK)
            }
            add(if (fav) SlotMenu.Choice.UNFAVORITE else SlotMenu.Choice.FAVORITE)
            add(SlotMenu.Choice.ADD_TO_COLLECTION)
            if (activeCol != null) {
                add(SlotMenu.Choice.REMOVE_FROM_COLLECTION)
            }
            if (reorderCol != null) {
                add(SlotMenu.Choice.MOVE_TO_TOP)
                add(SlotMenu.Choice.MOVE_UP)
                add(SlotMenu.Choice.MOVE_DOWN)
                add(SlotMenu.Choice.MOVE_TO_END)
            }
            if (isApp) {
                add(SlotMenu.Choice.APP_INFO)
            }
            if (entry.rom != null) {
                add(SlotMenu.Choice.RENAME)
                if (settings.romNames[entry.rom.id] != null) {
                    add(SlotMenu.Choice.RESET_NAME)
                }
                add(SlotMenu.Choice.OPEN_WITH)
                add(SlotMenu.Choice.PLAYER)
                add(SlotMenu.Choice.SCREENS)
                add(SlotMenu.Choice.SET_ART)
                add(SlotMenu.Choice.DOWNLOAD_ART)
                add(SlotMenu.Choice.ADD_TO_GRID)
                add(SlotMenu.Choice.HIDE)
            }
            add(SlotMenu.Choice.CANCEL)
        }
        val menu = SlotMenu.fromChoices(
            activity,
            settings.accentColor,
            choices,
            title = entry.label,
        ) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.DETAILS -> showDetails(entry)
                SlotMenu.Choice.COPY_TITLE -> copyTitleToClipboard(entry.label)
                SlotMenu.Choice.BROWSE_RELATED -> showBrowseRelatedDialog(related)
                SlotMenu.Choice.MARK_PLAYED -> markAsPlayed(key)
                SlotMenu.Choice.CLEAR_PLAY_STATS -> clearPlayStats(key)
                SlotMenu.Choice.PIN_TO_DOCK -> pinToDock(key)
                SlotMenu.Choice.UNPIN_FROM_DOCK -> unpinFromDock(key)
                SlotMenu.Choice.APP_INFO -> openAppInfo(key)
                SlotMenu.Choice.FAVORITE, SlotMenu.Choice.UNFAVORITE -> toggleFavorite(key)
                SlotMenu.Choice.ADD_TO_COLLECTION -> promptAddToCollection(listOf(key))
                SlotMenu.Choice.REMOVE_FROM_COLLECTION ->
                    activeCol?.let { removeFromCollection(it, listOf(key)) }
                SlotMenu.Choice.MOVE_TO_TOP,
                SlotMenu.Choice.MOVE_UP,
                SlotMenu.Choice.MOVE_DOWN,
                SlotMenu.Choice.MOVE_TO_END,
                -> reorderCol?.let { reorderInCollection(it, key, choice) }
                SlotMenu.Choice.RENAME -> showRenameDialog(entry)
                SlotMenu.Choice.RESET_NAME -> entry.rom?.let { resetRomName(it) }
                SlotMenu.Choice.OPEN_WITH -> openWithMenu(entry)
                SlotMenu.Choice.PLAYER -> entry.rom?.let { showPlayerProfileMenu(it) }
                SlotMenu.Choice.SCREENS -> entry.rom?.let { showScreensPlotMenu(it) }
                SlotMenu.Choice.SET_ART -> entry.rom?.let { setArtOverride(it) }
                SlotMenu.Choice.DOWNLOAD_ART -> entry.rom?.let { requestMissingArtwork(activity, it) }
                SlotMenu.Choice.ADD_TO_GRID -> addToGrid(key)
                SlotMenu.Choice.HIDE -> entry.rom?.let { hideRom(it) }
                else -> {}
            }
        }
        slotMenu = menu
        rootView?.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun updateDockSlots(slots: List<String?>, feedback: UiText? = null) {
        DockActions.persist(activity, app(), slots, feedback)
    }

    private fun onDockTap(index: Int) {
        val bar = dockBar ?: return
        when {
            dockMove.active -> dropDockMove(tapSlot = index)
            bar.isBlank(index) -> openDockPicker(index)
            else -> bar.keyAt(index)?.let { launchSlotKey(activity, state, roms, it) }
        }
    }

    private fun onDockLongPress(index: Int) {
        if (!dockMove.active && dockBar?.isBlank(index) == false) {
            openDockSlotMenu(index)
        }
    }

    private fun openDockSlotMenu(index: Int) {
        state.focusDock(index)
        val choices = listOf(
            SlotMenu.Choice.MOVE, SlotMenu.Choice.REMOVE, SlotMenu.Choice.CANCEL)
        val dockTitle = dockBar?.keyAt(index)?.let { continueLabel(it, settings) }
        val menu = SlotMenu.fromChoices(
            activity,
            settings.accentColor,
            choices,
            title = dockTitle,
        ) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.MOVE -> startDockMove(index)
                SlotMenu.Choice.REMOVE -> {
                    val next = DockActions.removeAt(app(), index)
                    updateDockSlots(next, text(R.string.deck_removed_from_dock))
                    DockActions.clampFocus(state.dockSlot, next)?.let { state.focusDock(it) }
                }
                else -> {}
            }
        }
        slotMenu = menu
        DeckOverlays.attach(rootView, menu.view)
    }

    private fun closeSlotMenu() {
        DeckOverlays.detach(rootView, slotMenu?.view)
        slotMenu = null
    }

    private fun startDockMove(index: Int) {
        dockMove.start(index, settings.dockSlots)
        hintView?.text = HintBar.moveText(activity)
        state.focusDock(index)
        dockBar?.updateFocus(index, moving = index)
    }

    private fun handleDockMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> {
                val to = dockNav.move(from, action)
                if (to != from) {
                    val next = dockMove.swap(from, to) ?: return true
                    dockBar?.rebind()
                    state.focusDock(next)
                    dockBar?.updateFocus(next, moving = next)
                }
            }
            Action.CONFIRM -> dropDockMove()
            Action.BACK -> cancelDockMove()
            else -> {}
        }
        return true
    }

    private fun dropDockMove(tapSlot: Int? = null) {
        val result = dockMove.drop(tapSlot) ?: return
        hintView?.text = HintBar.textFor(activity, state.dockSlot != null)
        updateDockSlots(result.compacted)
        state.focusDock(result.focusIndex)
    }

    private fun cancelDockMove() {
        dockMove.clear()
        hintView?.text = HintBar.textFor(activity, state.dockSlot != null)
        dockBar?.rebind()
        dockBar?.updateFocus(state.dockSlot)
    }

    private fun handleDockAction(action: Action, dockIndex: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT ->
                state.focusDock(dockNav.move(dockIndex, action))
            Action.NAV_UP, Action.BACK ->
                state.select(entries.getOrNull(selectedIndex())?.key ?: state.selectedKey)
            Action.CONFIRM -> {
                val bar = dockBar
                if (bar == null || bar.isBlank(dockIndex)) {
                    openDockPicker(dockIndex)
                } else {
                    bar.keyAt(dockIndex)?.let { launchSlotKey(activity, state, roms, it) }
                }
            }
            else -> {}
        }
        return true
    }

    private fun openDockPicker(slot: Int) {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = activity.getString(R.string.action_add_to_dock),
            onPick = { key ->
                closePicker()
                updateDockSlots(DockActions.fill(app(), slot, key))
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun closePicker() {
        DeckOverlays.detach(rootView, picker?.view)
        DeckOverlays.hideIme(activity, rootView)
        picker = null
    }

    private inner class CardAdapter(
        private val context: Context,
        val cardSize: Int,
        val cardSpacing: Int,
        val cellPadding: Int,
    ) : RecyclerView.Adapter<CardAdapter.CardHolder>() {

        /** Last selection key/dock-focus we painted (for partial updates). */
        var paintedSelectionKey: String? = null
        var paintedDockFocused: Boolean = false

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long =
            entries.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()

        inner class CardHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            var cardFrame: FrameLayout? = null
            var multiBadge: ImageView? = null
            var favBadge: TextView? = null
            var platformBadge: LinearLayout? = null
            var titleView: TextView? = null
            var metaView: TextView? = null
        }

        private val cardWidthPx: Int = GameDeckChromeLogic.carouselCardWidthPx(cardSize, cellPadding)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
            // Transparent full-height slot so the card surface wraps the
            // icon+label and stays vertically centered in the carousel.
            val slot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            slot.layoutParams = RecyclerView.LayoutParams(
                cardWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = cardSpacing / 2
                marginEnd = cardSpacing / 2
            }
            return CardHolder(slot)
        }

        override fun getItemCount() = entries.size

        /**
         * Selection-only: rebind just the cards whose depth tier (center / ±1
         * near / ±2+ far) changed. Dock focus change updates visible rings.
         */
        fun notifySelectionChanged(
            previousKey: String?,
            previousDockFocused: Boolean,
            nextKey: String?,
            nextDockFocused: Boolean,
        ) {
            // Multi-select membership can change without selectedKey moving.
            if (state.multiSelectEnabled ||
                previousDockFocused != nextDockFocused
            ) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
            } else if (previousKey != nextKey) {
                val prevIdx = previousKey?.let { entryIndexByKey[it] }
                val nextIdx = nextKey?.let { entryIndexByKey[it] }
                val affected = CarouselDepthMetrics.affectedSelectionIndices(
                    previousIndex = prevIdx,
                    nextIndex = nextIdx,
                    itemCount = itemCount,
                )
                affected.forEach { notifyItemChanged(it, PAYLOAD_SELECTION) }
            }
            paintedSelectionKey = nextKey
            paintedDockFocused = nextDockFocused
        }

        override fun onBindViewHolder(
            holder: CardHolder,
            position: Int,
            payloads: MutableList<Any>,
        ) {
            if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }) {
                if (applySelectionVisuals(holder, position, animate = true)) return
            }
            onBindViewHolder(holder, position)
        }

        private fun multiSelectForeground(): GradientDrawable {
            val radiusDp = ThemePack.resolve(settings).cardRadiusDp
            val accent = settings.accentColor
            return GradientDrawable().apply {
                setColor((accent and 0x00FFFFFF) or (0x38 shl 24))
                cornerRadius = context.dpF(radiusDp)
                setStroke(context.strokePx(2), accent)
            }
        }

        /** Mutate ring/depth-scale/alpha/Y-lift/Z-elevation/multi-select only; false → full rebind needed. */
        private fun applySelectionVisuals(
            holder: CardHolder,
            position: Int,
            animate: Boolean,
        ): Boolean {
            val entry = entries.getOrNull(position) ?: return false
            val cardFrame = holder.cardFrame
                ?: (holder.root.getChildAt(0) as? FrameLayout)?.also { holder.cardFrame = it }
                ?: return false
            val dockFocused = state.dockSlot != null
            val focusedIndex = state.selectedKey?.let { entryIndexByKey[it] }
            val focused = entry.key == state.selectedKey && !dockFocused
            val multiSelected = state.multiSelectEnabled && entry.key in state.multiSelectKeys
            val depthStyle = CarouselDepthMetrics.styleFor(
                position = position,
                focusedIndex = focusedIndex,
                dockFocused = dockFocused,
                multiSelected = multiSelected,
            )
            cardFrame.background = if (focused) {
                TileBackgrounds.selected(context, settings.accentColor)
            } else {
                TileBackgrounds.card(context)
            }
            holder.titleView?.setTextColor(
                if (focused || multiSelected) UiDimens.ALPHA_PRIMARY else UiDimens.ALPHA_SECONDARY,
            )
            val targetLiftY = context.dpF(depthStyle.translationYDp)
            holder.root.translationZ = context.dpF(depthStyle.translationZDp)
            cardFrame.animate().cancel()
            if (animate && cardFrame.isLaidOut) {
                cardFrame.animate()
                    .scaleX(depthStyle.scale)
                    .scaleY(depthStyle.scale)
                    .alpha(depthStyle.alpha)
                    .translationY(targetLiftY)
                    .setDuration(FOCUS_ANIM_MS)
                    .setInterpolator(FOCUS_INTERPOLATOR)
                    .start()
            } else {
                cardFrame.scaleX = depthStyle.scale
                cardFrame.scaleY = depthStyle.scale
                cardFrame.alpha = depthStyle.alpha
                cardFrame.translationY = targetLiftY
            }
            val overlay = CarouselCardBadges.overlaySpec(
                platformId = entry.rom?.platformId,
                activePlatformFilter = state.libraryBrowse.platformId,
                favorite = entry.key in settings.favorites,
                multiSelectEnabled = state.multiSelectEnabled,
                multiSelected = multiSelected,
            )
            if (overlay.showMultiCheck) {
                cardFrame.foreground = multiSelectForeground()
                holder.multiBadge?.visibility = View.VISIBLE
            } else {
                cardFrame.foreground = null
                holder.multiBadge?.visibility = View.GONE
            }
            holder.favBadge?.visibility =
                if (overlay.showFavoriteBadge) View.VISIBLE else View.GONE
            holder.platformBadge?.visibility =
                if (overlay.platformBadgeText != null) View.VISIBLE else View.GONE
            holder.root.contentDescription = context.resolveText(
                CarouselCardBadges.cardAccessibilityText(
                    title = entry.label,
                    platformBadge = entry.rom?.platformId?.let { PlatformLook.filterBadge(it) },
                    favorite = entry.key in settings.favorites,
                    multiSelected = multiSelected,
                ),
            )
            return true
        }

        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            val entry = entries[position]
            val prevKey = holder.root.getTag(R.id.carousel_entry_key) as? String
            // Same key + existing hierarchy: skip art rebuild (biggest fling cost).
            if (prevKey == entry.key && holder.cardFrame != null && holder.root.childCount > 0) {
                applySelectionVisuals(holder, position, animate = false)
                rebindCardMeta(holder, entry)
                wireCardClicks(holder, entry)
                scheduleNeighborPrefetch(position)
                return
            }
            holder.cardFrame?.animate()?.cancel()
            holder.cardFrame = null
            holder.multiBadge = null
            holder.favBadge = null
            holder.platformBadge = null
            holder.titleView = null
            holder.metaView = null
            holder.root.removeAllViews()
            holder.root.setTag(R.id.carousel_entry_key, entry.key)
            val cardFrame = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
            }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            paintedSelectionKey = state.selectedKey
            paintedDockFocused = state.dockSlot != null
            val innerArtRadiusDp = CarouselDepthMetrics.opticalInnerArtRadiusDp(
                outerCardRadiusDp = ThemePack.resolve(settings).cardRadiusDp,
                cellPaddingDp = CELL_PADDING_DP,
            )
            val innerArtRadiusPx = context.dpF(innerArtRadiusDp)
            val artContainer = FrameLayout(context).apply {
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, innerArtRadiusPx)
                    }
                }
            }
            // ROM cards show cached artwork over the platform placeholder
            // (async fill, no decode on the UI thread); without art the
            // placeholder shows through — a cheap draw even deep in the ROM
            // section.
            val art: View = entry.rom?.let {
                ArtTile.view(
                    context,
                    (activity.application as GhostGalleonApp).artCache,
                    it,
                    targetPx = cardSize,
                    cornerRadiusDp = innerArtRadiusDp,
                    artOverrides = settings.artOverrides,
                )
            } ?: ImageView(context).apply {
                CustomIcon.bind(
                    this, iconLoader,
                    (activity.application as GhostGalleonApp).artCache,
                    settings, entry.appPackage!!, cardSize)
            }
            artContainer.addView(
                art,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            // Subtle bottom gradient scrim grounds corner pills and cover-to-title transition.
            val scrimHeight = (cardSize * CarouselCardBadges.ART_SCRIM_HEIGHT_FRAC).toInt()
                .coerceAtLeast(context.dp(24))
            artContainer.addView(
                View(context).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        CarouselCardBadges.artBottomScrimColors(),
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    scrimHeight,
                    Gravity.BOTTOM,
                ),
            )
            val initialOverlay = CarouselCardBadges.overlaySpec(
                platformId = entry.rom?.platformId,
                activePlatformFilter = state.libraryBrowse.platformId,
                favorite = entry.key in settings.favorites,
                multiSelectEnabled = state.multiSelectEnabled,
                multiSelected = state.multiSelectEnabled && entry.key in state.multiSelectKeys,
            )
            entry.rom?.let { rom ->
                val badgeText = PlatformLook.filterBadge(rom.platformId)
                val dotColor = ThemePack.accentTextColor(
                    PlatformLook.accentColor(rom.platformId),
                    CarouselCardBadges.PILL_BACKDROP_COLOR,
                )
                val pill = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(CarouselCardBadges.PILL_BACKDROP_COLOR)
                        cornerRadius = context.dpF(8)
                        setStroke(context.strokePx(1), CarouselCardBadges.PILL_STROKE_COLOR)
                    }
                    setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
                    visibility = if (initialOverlay.platformBadgeText != null) View.VISIBLE else View.GONE
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
                pill.addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(dotColor)
                        }
                    },
                    LinearLayout.LayoutParams(context.dp(6), context.dp(6)).apply {
                        marginEnd = context.dp(4)
                    },
                )
                pill.addView(
                    TextView(context).apply {
                        text = badgeText
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTextColor(UiDimens.ALPHA_PRIMARY)
                        setTypeface(typeface, Typeface.BOLD)
                        maxLines = 1
                        includeFontPadding = false
                    },
                )
                holder.platformBadge = pill
                artContainer.addView(
                    pill,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START,
                    ).apply {
                        marginStart = context.dp(6)
                        bottomMargin = context.dp(6)
                    },
                )
            }
            val favPill = TextView(context).apply {
                setText(R.string.glyph_favorite)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(CarouselCardBadges.FAVORITE_BADGE_TINT)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(CarouselCardBadges.PILL_BACKDROP_COLOR)
                    setStroke(context.strokePx(1), CarouselCardBadges.FAVORITE_STROKE_COLOR)
                }
                visibility = if (initialOverlay.showFavoriteBadge) View.VISIBLE else View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            holder.favBadge = favPill
            artContainer.addView(
                favPill,
                FrameLayout.LayoutParams(
                    context.dp(22),
                    context.dp(22),
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = context.dp(6)
                    marginEnd = context.dp(6)
                },
            )
            body.addView(artContainer, LinearLayout.LayoutParams(cardSize, cardSize))
            val titleTv = TextView(context).apply {
                text = entry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            holder.titleView = titleTv
            body.addView(titleTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(6) })
            // Playtime / last-played + Fav/Dock status (no extra chrome chips).
            val nowMs = System.currentTimeMillis()
            val meta = SessionMath.cardMetaLine(
                settings.lastLaunchedMs[entry.key],
                settings.playtimeMs[entry.key] ?: 0L,
                nowMs,
                favorite = entry.key in settings.favorites,
                inDock = DockSlots.containsKey(settings.dockSlots, entry.key),
            )
            val metaTv = TextView(context).apply {
                tag = "card_meta"
                text = context.resolveText(meta)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(UiDimens.ALPHA_MUTED)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            holder.metaView = metaTv
            body.addView(metaTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(2) })
            cardFrame.addView(
                body,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            val checkBadge = ImageView(context).apply {
                setImageResource(R.drawable.ic_check_circle)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setColorFilter(
                    ThemePack.accentTextColor(settings.accentColor, Color.BLACK),
                    PorterDuff.Mode.SRC_IN,
                )
                setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xCC000000.toInt())
                }
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            holder.multiBadge = checkBadge
            cardFrame.addView(
                checkBadge,
                FrameLayout.LayoutParams(
                    context.dp(24),
                    context.dp(24),
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = context.dp(8)
                    marginEnd = context.dp(8)
                },
            )
            holder.cardFrame = cardFrame
            holder.root.addView(cardFrame, LinearLayout.LayoutParams(
                cardWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            applySelectionVisuals(holder, position, animate = false)
            wireCardClicks(holder, entry)
            scheduleNeighborPrefetch(position)
        }

        private fun rebindCardMeta(holder: CardHolder, entry: CarouselEntry) {
            val metaView = holder.metaView
                ?: holder.cardFrame?.findViewWithTag<TextView>("card_meta")
                    ?.also { holder.metaView = it }
            val favorite = entry.key in settings.favorites
            if (metaView != null) {
                val meta = SessionMath.cardMetaLine(
                    settings.lastLaunchedMs[entry.key],
                    settings.playtimeMs[entry.key] ?: 0L,
                    System.currentTimeMillis(),
                    favorite = favorite,
                    inDock = DockSlots.containsKey(settings.dockSlots, entry.key),
                )
                metaView.text = context.resolveText(meta)
            }
            val multiSelected = state.multiSelectEnabled && entry.key in state.multiSelectKeys
            val overlay = CarouselCardBadges.overlaySpec(
                platformId = entry.rom?.platformId,
                activePlatformFilter = state.libraryBrowse.platformId,
                favorite = favorite,
                multiSelectEnabled = state.multiSelectEnabled,
                multiSelected = multiSelected,
            )
            holder.favBadge?.visibility =
                if (overlay.showFavoriteBadge) View.VISIBLE else View.GONE
            holder.platformBadge?.visibility =
                if (overlay.platformBadgeText != null) View.VISIBLE else View.GONE
            holder.root.contentDescription = context.resolveText(
                CarouselCardBadges.cardAccessibilityText(
                    title = entry.label,
                    platformBadge = entry.rom?.platformId?.let { PlatformLook.filterBadge(it) },
                    favorite = favorite,
                    multiSelected = multiSelected,
                ),
            )
        }

        private fun wireCardClicks(holder: CardHolder, entry: CarouselEntry) {
            holder.root.setOnClickListener {
                if (state.multiSelectEnabled) {
                    state.toggleMultiSelectKey(entry.key)
                    return@setOnClickListener
                }
                if (state.selectedKey == entry.key && state.dockSlot == null) {
                    launch(entry)
                } else {
                    state.select(entry.key)
                }
            }
            holder.root.setOnLongClickListener {
                if (state.multiSelectEnabled) {
                    state.toggleMultiSelectKey(entry.key)
                } else {
                    state.select(entry.key)
                    openEntryMenu(entry)
                }
                true
            }
        }

        /** Prefetch art for ±2 neighbors so flings hit memory/disk more often. */
        private fun scheduleNeighborPrefetch(center: Int) {
            val cache = (activity.application as GhostGalleonApp).artCache
            val overrides = settings.artOverrides
            val epoch = entries.size
            for (delta in PREFETCH_DELTAS) {
                val i = center + delta
                if (i !in entries.indices) continue
                val rom = entries[i].rom ?: continue
                cache.prefetch(
                    context,
                    rom,
                    maxDimension = cardSize,
                    artOverrides = overrides,
                    isStillValid = {
                        // Drop if list rebuilt or position scrolled far away.
                        entries.size == epoch &&
                            i in entries.indices &&
                            entries[i].rom?.id == rom.id
                    },
                )
            }
        }
    }

    private companion object {
        /** RecyclerView payload: only ring/scale/multi-select chrome. */
        const val PAYLOAD_SELECTION = "selection"
        const val TAG_GAME_WALLPAPER = "game_wallpaper"
        const val CELL_PADDING_DP = 8
        const val FOCUS_ANIM_MS = 90L
        val FOCUS_INTERPOLATOR = DecelerateInterpolator()
        val PREFETCH_DELTAS = intArrayOf(-2, -1, 1, 2)
    }

    private class EntryDiff(
        private val old: List<CarouselEntry>,
        private val next: List<CarouselEntry>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = next.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].key == next[newItemPosition].key
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == next[newItemPosition]
    }
}
