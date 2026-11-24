package com.visorcraft.ghostgalleon.ui.deck

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.art.ArtCache
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.art.ArtTileMetrics
import com.visorcraft.ghostgalleon.audio.SoundCue
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
import com.visorcraft.ghostgalleon.ui.BaseDeckActivity
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
    /** Sanitized browse query that produced the current [entries] list. */
    private var entriesBuiltForQuery: LibraryBrowse.BrowseQuery? = null
    /** Fallback O(1) ROM index when [roms] is not the app's live list instance. */
    private var fallbackRomById: Map<String, RomEntry>? = null
    /** Single snap helper for the carousel (never re-alloc on NAV). */
    private val snapHelper = LinearSnapHelper()
    /** True while a touch drag/fling is active so snap-idle commits selection once without D-pad echoes. */
    private var userDraggingCarousel: Boolean = false
    private val carouselSnapScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> userDraggingCarousel = true
                RecyclerView.SCROLL_STATE_IDLE -> {
                    val wasDrag = userDraggingCarousel
                    userDraggingCarousel = false
                    if (!wasDrag) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val snapView = snapHelper.findSnapView(lm) ?: return
                    val pos = lm.getPosition(snapView)
                    val snappedKey = entries.getOrNull(pos)?.key
                    if (CarouselNavigation.shouldCommitSnapSelection(
                            wasUserDrag = wasDrag,
                            scrollState = newState,
                            snappedKey = snappedKey,
                            currentKey = state.selectedKey,
                        )
                    ) {
                        val fromIdx = selectedIndex()
                        playAudio(DeckAudioLogic.carouselCardFocusEvent(fromIdx, pos))
                        state.select(snappedKey)
                    }
                }
            }
        }
    }
    private var entriesMemoKey: String? = null
    private var entriesMemo: List<CarouselEntry> = emptyList()

    /** O(1) ROM id lookup matching [roms] (reuses [GhostGalleonApp.romById] when identical). */
    private fun romIndex(): Map<String, RomEntry> {
        val liveApp = app()
        if (liveApp.romEntries === roms) return liveApp.romById
        return fallbackRomById ?: roms.associateBy { it.id }.also { fallbackRomById = it }
    }

    private fun installEntries(next: List<CarouselEntry>) {
        entries = next
        entriesBuiltForQuery = settings.browseChrome.sanitize(state.libraryBrowse)
        val map = HashMap<String, Int>(next.size * 4 / 3 + 1)
        for (i in next.indices) {
            map[next[i].key] = i
        }
        entryIndexByKey = map
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
        val byRomId = romIndex()
        val browsed = if (q.mode == LibraryBrowse.Mode.COLLECTION) {
            emptyList()
        } else {
            LibraryBrowse.browseRoms(
                roms, q,
                lastLaunchedMs = settings.lastLaunchedMs,
                favorites = settings.favorites,
                collections = settings.collections,
                playtimeMs = settings.playtimeMs,
                hiddenRomIds = settings.hiddenRomIds,
                nowMs = System.currentTimeMillis(),
                launchablePlatformIds = launchablePlatformIds,
                romById = byRomId,
            ).map {
                CarouselEntry(SlotKey.rom(it.id), romLabel(it), null, it)
            }
        }
        val built = when {
            q.mode == LibraryBrowse.Mode.COLLECTION -> {
                // Walk member keys in user order (apps + ROMs interleaved).
                val name = q.collectionName.orEmpty()
                val keys = settings.collections[name].orEmpty()
                val byPkg = library.curated(settings).associateBy { it.packageName }
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
    private var emptyIconMedallion: FrameLayout? = null
    private var emptyIconImage: ImageView? = null
    private var emptyIconGlyph: TextView? = null
    private var emptyMessageView: TextView? = null
    private var emptyResetButton: TextView? = null
    private var railIndicatorRow: LinearLayout? = null
    private var railCountView: TextView? = null
    private var railProgressFill: View? = null
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
    /** Applied root backdrop & wallpaper URI for crossfade + dynamic reconciliation. */
    private var appliedBackdropKey: String? = null
    private var appliedWallpaperUri: String? = null
    private data class LetterChipHandle(
        val letter: Char,
        val count: Int,
        val rawLabel: String,
        val view: TextView,
    )

    /** Letter-jump chips (A–Z / #) when ALPHA/UNPLAYED; repainted on selection. */
    private var letterChipViews: List<LetterChipHandle> = emptyList()
    /** Horizontal scrollers for auto-centering active browse and letter chips. */
    private var browseScrollView: android.widget.HorizontalScrollView? = null
    private var letterScrollView: android.widget.HorizontalScrollView? = null
    /** Contextual multi-select banner & browse chip refs updated in-place on SELECTION ticks. */
    private var multiSelectCountView: TextView? = null
    private var multiSelectAllButton: TextView? = null
    private var multiSelectChipView: TextView? = null
    /** Last filter-chrome structure; equal → restyle chip colors only. */
    private var chromeStructureKey: String = ""
    /** Reusable in-place-mutable interactive pill background (zero allocations on chip toggles). */
    private class MutablePillBackground(
        val ripple: RippleDrawable,
        private val idleFill: GradientDrawable,
        private val focusedFill: GradientDrawable,
        private val stroke1Px: Int,
        private val stroke2Px: Int,
        initialRippleColor: Int,
    ) {
        private var lastRippleColor: Int = initialRippleColor

        fun update(
            fillColor: Int,
            idleStrokeColor: Int?,
            focusedStrokeColor: Int,
            rippleColor: Int,
        ) {
            idleFill.setColor(fillColor)
            if (idleStrokeColor != null) {
                idleFill.setStroke(stroke1Px, idleStrokeColor)
            } else {
                idleFill.setStroke(0, Color.TRANSPARENT)
            }
            focusedFill.setColor(fillColor)
            focusedFill.setStroke(stroke2Px, focusedStrokeColor)
            if (lastRippleColor != rippleColor) {
                ripple.setColor(ColorStateList.valueOf(rippleColor))
                lastRippleColor = rippleColor
            }
        }
    }

    private data class BrowseChipHandle(
        val view: TextView,
        val pillBg: MutablePillBackground,
        var rawLabel: String,
        val selected: () -> Boolean,
        val platformId: String? = null,
        val platformDotDrawable: GradientDrawable? = null,
        val hasContinueCue: Boolean = false,
        var paintedSelected: Boolean? = null,
        var paintedAccent: Int = 0,
    )

    /** Browse chips + live selected predicates for in-place restyle. */
    private val browseChipHandles = mutableListOf<BrowseChipHandle>()

    private var slotMenu: SlotMenu? = null
    private var picker: AppPicker? = null

    private fun selectedIndex(): Int {
        val key = state.selectedKey ?: return 0
        return entryIndexByKey[key] ?: 0
    }

    private fun romLabel(rom: RomEntry): String =
        com.visorcraft.ghostgalleon.settings.RomNames.display(rom, settings.romNames)

    private fun playAudio(event: DeckAudioLogic.SoundEvent?) {
        if (event == null) return
        app().playSfx(
            cue = event.cue,
            pan = event.pan,
            repeatStreak = event.repeatStreak,
        )
    }

    private fun playCue(cue: SoundCue, pan: Float = 0f, repeatStreak: Int = 0) {
        app().playSfx(cue = cue, pan = pan, repeatStreak = repeatStreak)
    }

    private fun navStreak(action: Action): Int =
        (activity as? BaseDeckActivity)?.currentNavRepeatStreak(action) ?: 0

    private fun dockVisibleCount(): Int =
        DockSlots.visibleCount(settings.dockSlots)

    private fun resetToAllBrowse() {
        val live = app().settings
        val firstKey = library.curated(live).firstOrNull()?.packageName
            ?: HiddenRoms.listed(roms, settings.hiddenRomIds)
                .firstOrNull()?.let { SlotKey.rom(it.id) }
        playCue(SoundCue.FILTER_SELECT)
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
            val surface = TileBackgrounds.surfaceColor(context)
            val accentText = ThemePack.accentTextColor(settings.accentColor, surface)
            val kind = GameDeckChromeLogic.emptyVisualKind(q)
            val medallionFill: Int
            val medallionStroke: Int
            when (kind) {
                GameDeckChromeLogic.EmptyVisualKind.FAVORITES -> {
                    emptyIconImage?.visibility = View.GONE
                    emptyIconGlyph?.visibility = View.VISIBLE
                    emptyIconGlyph?.setTextColor(CarouselCardBadges.FAVORITE_BADGE_TINT)
                    medallionFill = 0x24FFD54F
                    medallionStroke = 0x55FFD54F
                }
                GameDeckChromeLogic.EmptyVisualKind.COLLECTION -> {
                    emptyIconGlyph?.visibility = View.GONE
                    emptyIconImage?.visibility = View.VISIBLE
                    emptyIconImage?.setImageResource(R.drawable.ic_folder)
                    emptyIconImage?.setColorFilter(accentText, PorterDuff.Mode.SRC_IN)
                    medallionFill = (accentText and 0x00FFFFFF) or (0x22 shl 24)
                    medallionStroke = (accentText and 0x00FFFFFF) or (0x55 shl 24)
                }
                GameDeckChromeLogic.EmptyVisualKind.SEARCH_OR_FILTER -> {
                    emptyIconGlyph?.visibility = View.GONE
                    emptyIconImage?.visibility = View.VISIBLE
                    emptyIconImage?.setImageResource(R.drawable.ic_search)
                    emptyIconImage?.setColorFilter(accentText, PorterDuff.Mode.SRC_IN)
                    medallionFill = (accentText and 0x00FFFFFF) or (0x22 shl 24)
                    medallionStroke = (accentText and 0x00FFFFFF) or (0x55 shl 24)
                }
                GameDeckChromeLogic.EmptyVisualKind.LIBRARY -> {
                    emptyIconGlyph?.visibility = View.GONE
                    emptyIconImage?.visibility = View.VISIBLE
                    emptyIconImage?.setImageResource(R.drawable.ic_search)
                    emptyIconImage?.setColorFilter(UiDimens.ALPHA_MUTED, PorterDuff.Mode.SRC_IN)
                    medallionFill = TileBackgrounds.surfaceSunken(context)
                    medallionStroke = TileBackgrounds.hairlineDivider(context)
                }
            }
            emptyIconMedallion?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(medallionFill)
                setStroke(context.strokePx(1), medallionStroke)
            }
            emptyMessageView?.text = context.resolveText(GameDeckChromeLogic.emptyStateMessage(q))
            val showReset = GameDeckChromeLogic.shouldShowResetAction(q)
            emptyResetButton?.let { btn ->
                btn.visibility = if (showReset) View.VISIBLE else View.GONE
                btn.setTextColor(ThemePack.onFillTextColor(settings.accentColor))
                btn.background = TileBackgrounds.focusableRow(
                    context = context,
                    accent = settings.accentColor,
                    idleBackground = TileBackgrounds.accentPill(context, settings.accentColor),
                    cornerRadiusDp = 20,
                    strokeDp = 2,
                )
            }
        } else {
            emptyHost.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
        syncRailIndicator(context)
    }

    private fun syncRailIndicator(context: Context) {
        val row = railIndicatorRow ?: return
        val progress = GameDeckChromeLogic.railProgressState(selectedIndex(), entries.size)
        if (!progress.visible) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        railCountView?.text = context.resolveText(
            GameDeckChromeLogic.railPositionText(progress.currentOneBased, progress.total),
        )
        railProgressFill?.let { fill ->
            fill.pivotX = 0f
            fill.scaleX = progress.fraction
            (fill.background as? GradientDrawable)?.setColor(
                ThemePack.accentTextColor(settings.accentColor, Color.BLACK),
            )
        }
    }

    private data class ResponsiveMetrics(
        val cardSizePx: Int,
        val cardSpacingPx: Int,
        val edgePadPx: Int,
        val cardTypography: CarouselLayoutMetrics.CardTypography,
    )

    private fun computeResponsiveMetrics(context: Context): ResponsiveMetrics {
        val dm = context.resources.displayMetrics
        val density = dm.density.coerceAtLeast(0.5f)
        val viewportWidthDp = (dm.widthPixels / density).toInt()
        val viewportHeightDp = (dm.heightPixels / density).toInt()
        val themeTokens = ThemePack.resolve(settings)
        val chromeReserveDp = CarouselLayoutMetrics.verticalChromeReserveDp(
            hasMultiSelect = state.multiSelectEnabled,
            hasActiveFilters = GameDeckChromeLogic.activeFilters(state.libraryBrowse).isNotEmpty(),
            hasLetterJump = GameDeckChromeLogic.supportsLetterJump(state.libraryBrowse.mode) &&
                entries.isNotEmpty(),
            showHints = settings.showHints,
        )
        val initialSizeDp = CarouselLayoutMetrics.effectiveCardSizeDp(
            requestedCardSizeDp = settings.cardSizeDp,
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            chromeVerticalReserveDp = chromeReserveDp,
        )
        val initialTypography = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = initialSizeDp,
            viewportHeightDp = viewportHeightDp,
            fontScale = themeTokens.fontScale,
        )
        val effectiveSizeDp = CarouselLayoutMetrics.effectiveCardSizeDp(
            requestedCardSizeDp = settings.cardSizeDp,
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            chromeVerticalReserveDp = chromeReserveDp,
            cardTextHeightDp = initialTypography.estimatedTextHeightDp,
        )
        val typography = CarouselLayoutMetrics.cardTypography(
            cardSizeDp = effectiveSizeDp,
            viewportHeightDp = viewportHeightDp,
            fontScale = themeTokens.fontScale,
        )
        val spacingDp = CarouselLayoutMetrics.cardSpacingDp(viewportWidthDp)
        val cardWidthDp = effectiveSizeDp + CELL_PADDING_DP * 2
        val edgePadDp = CarouselLayoutMetrics.horizontalEdgePaddingDp(viewportWidthDp, cardWidthDp)
        return ResponsiveMetrics(
            cardSizePx = (effectiveSizeDp * density).toInt().coerceAtLeast(1),
            cardSpacingPx = (spacingDp * density).toInt().coerceAtLeast(1),
            edgePadPx = (edgePadDp * density).toInt().coerceAtLeast(1),
            cardTypography = typography,
        )
    }

    private fun animateEdgeBump(direction: Int) {
        val rv = recycler ?: return
        val holder = rv.findViewHolderForAdapterPosition(selectedIndex()) as? CardAdapter.CardHolder
            ?: return
        val cardFrame = holder.cardFrame
        if (!DeckMotionPolicy.shouldAnimate(
                animatorsEnabled = DeckMotionPolicy.areSystemAnimatorsEnabled(),
                isLaidOut = cardFrame.isLaidOut,
            )
        ) {
            return
        }
        val nudgePx = direction * rv.context.dpF(CarouselLayoutMetrics.EDGE_BUMP_DP)
        holder.cancelEdgeBump()
        cardFrame.animate().cancel()
        val anim = ObjectAnimator.ofFloat(cardFrame, View.TRANSLATION_X, 0f, nudgePx, 0f).apply {
            duration = CarouselLayoutMetrics.EDGE_BUMP_MS
            interpolator = FOCUS_INTERPOLATOR
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (holder.edgeBumpAnimator === animation) {
                        holder.edgeBumpAnimator = null
                    }
                    cardFrame.translationX = 0f
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (holder.edgeBumpAnimator === animation) {
                        holder.edgeBumpAnimator = null
                    }
                    cardFrame.translationX = 0f
                }
            })
        }
        holder.edgeBumpAnimator = anim
        anim.start()
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

        val metrics = computeResponsiveMetrics(context)
        val nextSize = metrics.cardSizePx
        val nextSpacing = metrics.cardSpacingPx
        val edgePadPx = metrics.edgePadPx
        val nextTypography = metrics.cardTypography
        val nextPad = cellPaddingPx.coerceAtLeast(dp(CELL_PADDING_DP))
        cardSizePx = nextSize
        cardSpacingPx = nextSpacing
        val vertPad = dp(CarouselLayoutMetrics.MIN_EDGE_PAD_DP)
        if (rv.paddingStart != edgePadPx || rv.paddingEnd != edgePadPx) {
            rv.setPadding(edgePadPx, vertPad, edgePadPx, vertPad)
        }
        applyRootWallpaper(root)
        syncEmptyState(context)
        syncMultiSelectBanner(context)
        hintView?.text = currentHintText(context)
        val existing = rv.adapter as? CardAdapter
        val nextCardRadiusDp = ThemePack.resolve(settings).cardRadiusDp
        val adapterCompatible = existing != null &&
            existing.cardSize == nextSize &&
            existing.cardSpacing == nextSpacing &&
            existing.cellPadding == nextPad &&
            existing.cardRadiusDp == nextCardRadiusDp &&
            existing.cardTypography == nextTypography
        if (entries === oldEntries && adapterCompatible && existing != null) {
            existing.paintedSelectionKey = state.selectedKey
            existing.paintedDockFocused = state.dockSlot != null
            dockBar?.updateFocus(state.dockSlot, dockMove.index)
            return true
        }
        val adapter = if (adapterCompatible && existing != null) {
            if (GameDeckChromeLogic.shouldUseDiffUtil(oldEntries.size, entries.size)) {
                val diff = DiffUtil.calculateDiff(
                    EntryDiff(oldEntries, entries),
                    false,
                )
                existing.also {
                    diff.dispatchUpdatesTo(it)
                    it.notifyVisibleSelectionRange()
                }
            } else {
                existing.also {
                    @Suppress("NotifyDataSetChanged")
                    it.notifyDataSetChanged()
                }
            }
        } else {
            CardAdapter(context, nextSize, nextSpacing, nextPad, nextCardRadiusDp, nextTypography).also {
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
        val template = cachedTintBg?.takeIf { cachedTintKey == key } ?: run {
            val accent = PlatformLook.accentColor(platformId)
            val liftBand = BackdropTransitionLogic.platformLiftBandColor(accent)
            val vertical = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.BLACK, liftBand, Color.BLACK),
            )
            val radial = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(
                    BackdropTransitionLogic.platformRadialGlowColor(accent),
                    Color.TRANSPARENT,
                )
                setGradientCenter(0.5f, 0.48f)
                gradientRadius = maxOf(dm.widthPixels, dm.heightPixels) * 0.72f
            }
            LayerDrawable(arrayOf(vertical, radial)).also {
                cachedTintKey = key
                cachedTintBg = it
            }
        }
        return template.constantState?.newDrawable(context.resources)?.mutate() ?: template
    }

    private fun applyRootWallpaper(root: FrameLayout) {
        val platformFilter = state.libraryBrowse.platformId
            ?.takeIf { PlatformLook.hasFilter(it) }
        val nextKey = BackdropTransitionLogic.backdropKey(platformFilter)
        if (nextKey != appliedBackdropKey) {
            val nextDrawable: Drawable = if (platformFilter != null) {
                platformBackdrop(root.context, platformFilter)
            } else {
                ColorDrawable(Color.BLACK)
            }
            val currentBg = root.background
            if (currentBg != null &&
                BackdropTransitionLogic.shouldCrossfadeBackdrop(
                    previousKey = appliedBackdropKey,
                    nextKey = nextKey,
                    attachedToWindow = root.isAttachedToWindow,
                    animationsEnabled = ValueAnimator.areAnimatorsEnabled(),
                )
            ) {
                val prevClean = (currentBg as? TransitionDrawable)?.getDrawable(1) ?: currentBg
                val transition = TransitionDrawable(arrayOf(prevClean, nextDrawable)).apply {
                    isCrossFadeEnabled = true
                }
                root.background = transition
                transition.startTransition(BackdropTransitionLogic.BACKDROP_CROSSFADE_MS)
            } else {
                root.background = nextDrawable
            }
            appliedBackdropKey = nextKey
        }
        // Reconcile unified SAF wallpaper + vignette scrim cleanly across in-place chrome updates.
        val desiredUri = BackdropTransitionLogic.normalizeUri(settings.wallpaperUri)
        when (BackdropTransitionLogic.wallpaperAction(appliedWallpaperUri, desiredUri)) {
            BackdropTransitionLogic.WallpaperAction.KEEP -> Unit
            BackdropTransitionLogic.WallpaperAction.REMOVE -> {
                root.findViewWithTag<View>(TAG_GAME_WALLPAPER)?.let { root.removeView(it) }
                appliedWallpaperUri = null
            }
            BackdropTransitionLogic.WallpaperAction.ATTACH,
            BackdropTransitionLogic.WallpaperAction.REPLACE,
            -> {
                root.findViewWithTag<View>(TAG_GAME_WALLPAPER)?.let { root.removeView(it) }
                val uri = desiredUri ?: return
                val wallpaperView = ImageView(root.context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = BackdropTransitionLogic.WALLPAPER_ALPHA
                    addOnAttachStateChangeListener(DETACH_ANIM_CANCEL)
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
                DeckWallpaper.loadAsync(
                    context = root.context,
                    uriString = uri,
                    target = wallpaperView,
                    targetAlpha = BackdropTransitionLogic.WALLPAPER_ALPHA,
                )
                appliedWallpaperUri = uri
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
            letterJump = GameDeckChromeLogic.letterJumpStructureKey(q.mode, entries) { it.label },
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
        val active = browseChipHandles.firstOrNull { it.selected() }?.view ?: return
        scrollHorizontalToCenter(browseScrollView, active, smooth = smooth)
    }

    private fun centerActiveLetterChip(letter: Char?, smooth: Boolean) {
        if (letter == null) return
        val active = letterChipViews.firstOrNull { it.letter == letter }?.view ?: return
        scrollHorizontalToCenter(letterScrollView, active, smooth = smooth)
    }

    private fun buildMutablePillBackground(
        context: Context,
        fillColor: Int,
        idleStrokeColor: Int?,
        selected: Boolean,
        cornerRadiusDp: Int,
    ): MutablePillBackground {
        val radiusPx = context.dpF(cornerRadiusDp)
        val stroke1Px = context.strokePx(1)
        val stroke2Px = context.strokePx(2)
        val idle = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusPx
            if (idleStrokeColor != null) {
                setStroke(stroke1Px, idleStrokeColor)
            }
        }
        val focused = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radiusPx
            setStroke(
                stroke2Px,
                BrowseChipStyleLogic.focusStrokeColor(selected, settings.accentColor),
            )
        }
        val content = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), idle)
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusPx
        }
        val initialRipple = BrowseChipStyleLogic.rippleColor(selected, settings.accentColor)
        val ripple = RippleDrawable(
            ColorStateList.valueOf(initialRipple),
            content,
            mask,
        )
        return MutablePillBackground(
            ripple = ripple,
            idleFill = idle,
            focusedFill = focused,
            stroke1Px = stroke1Px,
            stroke2Px = stroke2Px,
            initialRippleColor = initialRipple,
        )
    }

    private fun interactivePillBackground(
        context: Context,
        fillColor: Int,
        idleStrokeColor: Int?,
        selected: Boolean,
        cornerRadiusDp: Int,
    ): Drawable = buildMutablePillBackground(
        context = context,
        fillColor = fillColor,
        idleStrokeColor = idleStrokeColor,
        selected = selected,
        cornerRadiusDp = cornerRadiusDp,
    ).ripple

    private fun styledBrowseChipLabel(rawLabel: String, selected: Boolean): CharSequence {
        val range = BrowseChipStyleLogic.countSuffixRange(rawLabel) ?: return rawLabel
        return SpannableString(rawLabel).apply {
            setSpan(
                RelativeSizeSpan(BrowseChipStyleLogic.CHIP_COUNT_RELATIVE_SIZE),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(
                    BrowseChipStyleLogic.countSuffixColor(selected, settings.accentColor),
                ),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun restyleBrowseChips() {
        val accent = settings.accentColor
        browseChipHandles.forEach { handle ->
            val nowSelected = handle.selected()
            if (handle.paintedSelected != nowSelected || handle.paintedAccent != accent) {
                paintChip(handle, nowSelected)
            }
        }
        centerActiveBrowseChip(smooth = true)
        paintLetterJumpSelection()
    }

    private fun paintChip(handle: BrowseChipHandle, selected: Boolean) {
        val tv = handle.view
        val context = tv.context
        val accent = settings.accentColor
        val chipIdle = TileBackgrounds.chipIdleColor(context)
        val restingHairline = TileBackgrounds.hairlineDivider(context)
        tv.text = styledBrowseChipLabel(handle.rawLabel, selected)
        tv.setTextColor(
            if (selected) ThemePack.onFillTextColor(accent)
            else UiDimens.ALPHA_PRIMARY,
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.isSelected = selected
        tv.contentDescription = context.resolveText(
            GameDeckChromeLogic.browseChipAccessibilityText(
                chipLabel = handle.rawLabel,
                selected = selected,
            ),
        )
        val idleStroke = if (selected) {
            null
        } else {
            BrowseChipStyleLogic.continueChipAccentBorder(
                hasTarget = handle.hasContinueCue,
                accent = accent,
                restingHairline = restingHairline,
            )
        }
        handle.pillBg.update(
            fillColor = if (selected) accent else chipIdle,
            idleStrokeColor = idleStroke,
            focusedStrokeColor = BrowseChipStyleLogic.focusStrokeColor(selected, accent),
            rippleColor = BrowseChipStyleLogic.rippleColor(selected, accent),
        )
        val dot = handle.platformDotDrawable
        val pid = handle.platformId
        if (dot != null && pid != null) {
            val platformAccent = PlatformLook.dotAccentForFilter(pid)
                ?: PlatformLook.accentColor(pid)
            dot.setColor(
                BrowseChipStyleLogic.platformDotColor(
                    platformAccent = platformAccent,
                    selected = selected,
                    themeAccent = accent,
                    chipIdle = chipIdle,
                ),
            )
        }
        handle.paintedSelected = selected
        handle.paintedAccent = accent
    }

    private fun buildActiveFilterStrip(context: Context, dp: (Int) -> Int): View? {
        val active = GameDeckChromeLogic.activeFilters(state.libraryBrowse)
        if (active.isEmpty()) return null
        val dm = context.resources.displayMetrics
        val viewportWidthDp = (dm.widthPixels / dm.density.coerceAtLeast(0.5f)).toInt()
        val maxFilterLabelPx = dp(GameDeckChromeLogic.maxFilterPillLabelWidthDp(viewportWidthDp))
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
                minimumHeight = dp(BrowseChipStyleLogic.FILTER_PILL_MIN_HEIGHT_DP)
                background = interactivePillBackground(
                    context = context,
                    fillColor = TileBackgrounds.chipIdleColor(context),
                    idleStrokeColor = (textAccent and 0x00FFFFFF) or (0x66 shl 24),
                    selected = false,
                    cornerRadiusDp = 14,
                )
                setPadding(dp(10), dp(4), dp(8), dp(4))
                isClickable = true
                contentDescription = context.resolveText(
                    GameDeckChromeLogic.filterPillAccessibilityText(label),
                )
                setOnClickListener {
                    playCue(SoundCue.FILTER_SELECT)
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
                maxWidth = maxFilterLabelPx
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
                    minimumHeight = dp(BrowseChipStyleLogic.FILTER_PILL_MIN_HEIGHT_DP)
                    background = interactivePillBackground(
                        context = context,
                        fillColor = settings.accentColor,
                        idleStrokeColor = null,
                        selected = true,
                        cornerRadiusDp = 14,
                    )
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    isClickable = true
                    contentDescription = clearAllLabel
                    setOnClickListener {
                        playCue(SoundCue.FILTER_SELECT)
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

    private fun currentHintText(context: Context): String = when {
        dockMove.active -> HintBar.moveText(activity)
        state.dockSlot != null -> HintBar.textFor(activity, dockFocused = true)
        state.multiSelectEnabled -> context.resolveText(
            CarouselMultiSelectLogic.multiSelectHintText(state.multiSelectKeys.size),
        )
        else -> HintBar.textFor(activity, dockFocused = false)
    }

    private fun syncMultiSelectBanner(context: Context) {
        val n = state.multiSelectKeys.size
        multiSelectCountView?.text = context.resolveText(CarouselMultiSelectLogic.bannerCountText(n))
        multiSelectAllButton?.visibility =
            if (CarouselMultiSelectLogic.showSelectAllPill(n, entries.size)) View.VISIBLE
            else View.GONE
        if (state.multiSelectEnabled) {
            val selectLabel = context.getString(R.string.format_select_count, n)
            browseChipHandles.firstOrNull { it.view === multiSelectChipView }?.let { handle ->
                handle.rawLabel = selectLabel
                paintChip(handle, selected = true)
            } ?: run {
                multiSelectChipView?.text = selectLabel
            }
        }
    }

    private fun buildMultiSelectBanner(context: Context, dp: (Int) -> Int): View? {
        if (!state.multiSelectEnabled) {
            multiSelectCountView = null
            multiSelectAllButton = null
            return null
        }
        val surface = TileBackgrounds.surfaceColor(context)
        val accent = settings.accentColor
        val accentText = ThemePack.accentTextColor(accent, surface)
        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(CarouselMultiSelectLogic.bannerTintFill(accent))
                cornerRadius = context.dpF(14)
                setStroke(context.strokePx(1), CarouselMultiSelectLogic.bannerTintStroke(accent))
            }
            setPadding(dp(12), dp(6), dp(10), dp(6))
        }
        banner.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_check_circle)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setColorFilter(accentText, PorterDuff.Mode.SRC_IN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) },
        )
        val countTv = TextView(context).apply {
            text = context.resolveText(
                CarouselMultiSelectLogic.bannerCountText(state.multiSelectKeys.size),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        multiSelectCountView = countTv
        banner.addView(
            countTv,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            },
        )
        val allBtn = TextView(context).apply {
            setText(R.string.label_all)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            background = TileBackgrounds.chipIdle(context, cornerRadiusDp = 12)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            isClickable = true
            visibility = if (CarouselMultiSelectLogic.showSelectAllPill(
                    state.multiSelectKeys.size,
                    entries.size,
                )
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setOnClickListener {
                playCue(SoundCue.CONFIRM)
                val railKeys = entries.map { it.key }
                val all = com.visorcraft.ghostgalleon.library.MultiSelectOps.selectAll(railKeys)
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
        }
        multiSelectAllButton = allBtn
        banner.addView(
            allBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(6) },
        )
        val actionsBtn = TextView(context).apply {
            setText(R.string.bulk_actions)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ThemePack.onFillTextColor(accent))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            background = TileBackgrounds.accentPill(context, accent, cornerRadiusDp = 12)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            isClickable = true
            setOnClickListener { showBulkActions() }
        }
        banner.addView(
            actionsBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(6) },
        )
        val cancelBtn = TextView(context).apply {
            setText(R.string.action_cancel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            background = TileBackgrounds.chipIdle(context, cornerRadiusDp = 12)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            isClickable = true
            setOnClickListener {
                playCue(SoundCue.BACK)
                state.clearMultiSelect()
            }
        }
        banner.addView(cancelBtn)
        return banner
    }

    private fun rebuildFilterChrome(
        chrome: LinearLayout,
        context: Context,
        dp: (Int) -> Int,
    ) {
        letterChipViews.forEach { it.view.animate().cancel() }
        browseChipHandles.clear()
        multiSelectCountView = null
        multiSelectAllButton = null
        multiSelectChipView = null
        chrome.removeAllViews()
        chrome.addView(
            buildBrowseBar(context, dp),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        buildMultiSelectBanner(context, dp)?.let { banner ->
            chrome.addView(
                banner,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                },
            )
        }
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
        val iconImg = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(UiDimens.ALPHA_MUTED, PorterDuff.Mode.SRC_IN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        emptyIconImage = iconImg
        val iconGlyph = TextView(context).apply {
            setText(R.string.glyph_favorite)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(CarouselCardBadges.FAVORITE_BADGE_TINT)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        emptyIconGlyph = iconGlyph
        val medallion = FrameLayout(context).apply {
            addView(
                iconImg,
                FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER),
            )
            addView(
                iconGlyph,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
        emptyIconMedallion = medallion
        card.addView(
            medallion,
            LinearLayout.LayoutParams(dp(52), dp(52)).apply { bottomMargin = dp(12) },
        )
        val msg = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
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
            background = TileBackgrounds.focusableRow(
                context = context,
                accent = settings.accentColor,
                idleBackground = TileBackgrounds.accentPill(context, settings.accentColor),
                cornerRadiusDp = 20,
                strokeDp = 2,
            )
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isClickable = true
            isFocusable = true
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

    private fun buildRailIndicator(context: Context, dp: (Int) -> Int): View {
        val dm = context.resources.displayMetrics
        val screenWidthDp = (dm.widthPixels / dm.density.coerceAtLeast(0.5f)).toInt()
        val trackWidthPx = dp(GameDeckChromeLogic.railTrackWidthDp(screenWidthDp))
        val trackHeightPx = dp(3).coerceAtLeast(2)
        val fillView = View(context).apply {
            background = GradientDrawable().apply {
                setColor(ThemePack.accentTextColor(settings.accentColor, Color.BLACK))
                cornerRadius = trackHeightPx / 2f
            }
            pivotX = 0f
            scaleX = 0f
        }
        railProgressFill = fillView
        val track = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                cornerRadius = trackHeightPx / 2f
            }
            addView(
                fillView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val counter = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            includeFontPadding = false
        }
        railCountView = counter
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0x99101016.toInt())
                cornerRadius = context.dpF(12)
                setStroke(context.strokePx(1), TileBackgrounds.hairlineDivider(context))
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(
                track,
                LinearLayout.LayoutParams(trackWidthPx, trackHeightPx).apply {
                    marginEnd = dp(8)
                },
            )
            addView(counter)
        }
        railIndicatorRow = pill
        return pill
    }

    override fun primaryView(context: Context): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        installEntries(buildEntries())
        val metrics = computeResponsiveMetrics(context)
        cardSizePx = metrics.cardSizePx
        cardSpacingPx = metrics.cardSpacingPx
        val edgePadPx = metrics.edgePadPx
        val cardTypography = metrics.cardTypography
        cellPaddingPx = dp(CELL_PADDING_DP)

        // FrameLayout root so modals (dock slot menu, app picker) can sit
        // on top of the whole deck.
        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.BLACK)
        }
        rootView = root
        appliedBackdropKey = null
        appliedWallpaperUri = null
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
            adapter = CardAdapter(
                context,
                cardSizePx,
                cardSpacingPx,
                cellPaddingPx,
                ThemePack.resolve(settings).cardRadiusDp,
                cardTypography,
            )
            // Fixed card metrics for this paint; chrome card-size rebuilds the RV.
            setHasFixedSize(true)
            setItemViewCacheSize(8)
            recycledViewPool.setMaxRecycledViews(0, 12)
            // Selection payloads only mutate ring/scale — default animator
            // fades those cells on every D-pad tick.
            itemAnimator = null
            snapHelper.attachToRecyclerView(this)
            userDraggingCarousel = false
            addOnScrollListener(carouselSnapScrollListener)
            val vertPad = dp(CarouselLayoutMetrics.MIN_EDGE_PAD_DP)
            setPadding(edgePadPx, vertPad, edgePadPx, vertPad)
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
            addView(
                buildRailIndicator(context, ::dp),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ).apply { bottomMargin = dp(2) },
            )
        }
        syncEmptyState(context)
        content.addView(carouselArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (settings.showHints) {
            val hints = HintBar.build(context) as TextView
            hints.text = currentHintText(context)
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
        userDraggingCarousel = false
        rv.stopScroll()
        if (entries.isEmpty()) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val index = selectedIndex()
        val laidOut = lm.findViewByPosition(index)
        if (laidOut == null) {
            // Prefer left-edge of card near viewport center relative to start padding.
            val cardW = GameDeckChromeLogic.carouselCardWidthPx(cardSizePx, cellPaddingPx)
            val offset = CarouselLayoutMetrics.centerScrollOffsetPx(
                viewportWidthPx = rv.width,
                paddingStartPx = rv.paddingStart,
                paddingEndPx = rv.paddingEnd,
                cardWidthPx = cardW,
            )
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
        syncRailIndicator(rv.context)
        syncMultiSelectBanner(rv.context)
        dockBar?.updateFocus(state.dockSlot, dockMove.index)
        hintView?.text = currentHintText(rv.context)
        return true
    }

    private var paintedLetter: Char? = null

    private fun styledLetterLabel(
        rawLabel: String,
        letter: Char,
        selected: Boolean,
    ): CharSequence {
        val range = LetterJumpMetrics.countSuffixRange(rawLabel, letter) ?: return rawLabel
        return SpannableString(rawLabel).apply {
            setSpan(
                RelativeSizeSpan(LetterJumpMetrics.COUNT_RELATIVE_SIZE),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(
                    LetterJumpMetrics.countTextColor(selected, settings.accentColor),
                ),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun paintLetterChip(
        handle: LetterChipHandle,
        selected: Boolean,
        animate: Boolean = false,
    ) {
        val tv = handle.view
        tv.text = styledLetterLabel(handle.rawLabel, handle.letter, selected)
        tv.setTextColor(
            if (selected) ThemePack.onFillTextColor(settings.accentColor)
            else UiDimens.ALPHA_PRIMARY,
        )
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.isSelected = selected
        tv.contentDescription = tv.context.resolveText(
            LetterJumpMetrics.letterChipAccessibilityText(handle.letter, handle.count, selected),
        )
        TileBackgrounds.applyChipState(
            view = tv,
            selected = selected,
            accent = settings.accentColor,
            cornerRadiusDp = LetterJumpMetrics.CHIP_CORNER_RADIUS_DP,
        )
        tv.animate().cancel()
        if (selected && animate && tv.isLaidOut && ValueAnimator.areAnimatorsEnabled()) {
            tv.scaleX = LetterJumpMetrics.ACTIVE_PULSE_SCALE
            tv.scaleY = LetterJumpMetrics.ACTIVE_PULSE_SCALE
            tv.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(LetterJumpMetrics.ACTIVE_PULSE_MS)
                .setInterpolator(FOCUS_INTERPOLATOR)
                .start()
        } else {
            tv.scaleX = 1f
            tv.scaleY = 1f
        }
    }

    private fun paintLetterJumpSelection() {
        if (letterChipViews.isEmpty()) return
        val selectedLetter = entries.getOrNull(selectedIndex())
            ?.label
            ?.let { LibraryBrowse.letterBucket(it) }
        if (selectedLetter == paintedLetter) return
        letterChipViews.forEach { handle ->
            if (handle.letter != selectedLetter && handle.letter != paintedLetter) return@forEach
            paintLetterChip(handle, selected = handle.letter == selectedLetter, animate = true)
        }
        paintedLetter = selectedLetter
        centerActiveLetterChip(selectedLetter, smooth = true)
    }

    override fun handleAction(action: Action): Boolean {
        slotMenu?.let { return it.handleAction(action) }
        picker?.let { return it.handleAction(action) }
        dockMove.index?.let { return handleDockMoveAction(action, it) }
        state.dockSlot?.let { return handleDockAction(action, it) }
        val focusedEntry = entries.getOrNull(selectedIndex())
        when (
            CarouselMultiSelectLogic.gamepadOutcome(
                multiSelectEnabled = state.multiSelectEnabled,
                action = action,
                hasFocusedEntry = focusedEntry != null,
            )
        ) {
            CarouselMultiSelectLogic.GamepadActionOutcome.TOGGLE_CURRENT_CARD -> {
                focusedEntry?.let {
                    state.toggleMultiSelectKey(it.key)
                    playAudio(DeckAudioLogic.multiSelectToggleEvent(it.key in state.multiSelectKeys))
                }
                return true
            }
            CarouselMultiSelectLogic.GamepadActionOutcome.OPEN_BULK_ACTIONS -> {
                showBulkActions()
                return true
            }
            CarouselMultiSelectLogic.GamepadActionOutcome.EXIT_MULTI_SELECT -> {
                playCue(SoundCue.BACK)
                state.clearMultiSelect()
                return true
            }
            CarouselMultiSelectLogic.GamepadActionOutcome.PASS_THROUGH -> Unit
        }
        return when (action) {
            Action.CONFIRM -> {
                if (GameDeckChromeLogic.emptyStateConfirmResets(
                        entriesEmpty = entries.isEmpty(),
                        query = state.libraryBrowse,
                    )
                ) {
                    resetToAllBrowse()
                    return true
                }
                if (entries.isEmpty()) return true
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
            Action.BACK -> {
                if (GameDeckChromeLogic.emptyStateConfirmResets(
                        entriesEmpty = entries.isEmpty(),
                        query = state.libraryBrowse,
                    )
                ) {
                    resetToAllBrowse()
                    true
                } else {
                    false
                }
            }
            Action.NAV_LEFT, Action.NAV_RIGHT, Action.PAGE_PREV, Action.PAGE_NEXT -> {
                // Select only — updateSelection → scrollSelectionToCenter once.
                // (Was smoothScrollToPosition + center = fighting scroll systems.)
                val currentIdx = selectedIndex()
                val bump = nav.edgeBumpDirection(currentIdx, action)
                if (bump != 0) {
                    // Fire edge-bump SFX outside animateEdgeBump so reduced-motion still hears it.
                    playAudio(
                        DeckAudioLogic.carouselNavigationEvent(
                            action = action,
                            fromIndex = currentIdx,
                            toIndex = currentIdx,
                            bumpDirection = bump,
                        ),
                    )
                    animateEdgeBump(bump)
                } else {
                    val newIndex = nav.move(currentIdx, action)
                    playAudio(
                        DeckAudioLogic.carouselNavigationEvent(
                            action = action,
                            fromIndex = currentIdx,
                            toIndex = newIndex,
                            bumpDirection = 0,
                            repeatStreak = navStreak(action),
                        ),
                    )
                    entries.getOrNull(newIndex)?.let { state.select(it.key) }
                }
                true
            }
            // NAV DOWN leaves the carousel and focuses the dock.
            Action.NAV_DOWN -> {
                playAudio(DeckAudioLogic.dockEnterEvent(0, dockVisibleCount()))
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
        if (!GameDeckChromeLogic.supportsLetterJump(mode)) {
            letterChipViews = emptyList()
            letterScrollView = null
            return null
        }
        val buckets = GameDeckChromeLogic.letterJumpBuckets(entries) { it.label }
        if (buckets.isEmpty()) {
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
        val chips = mutableListOf<LetterChipHandle>()
        val minW = dp(LetterJumpMetrics.CHIP_MIN_WIDTH_DP)
        val minH = dp(LetterJumpMetrics.CHIP_MIN_HEIGHT_DP)
        buckets.forEachIndexed { i, bucket ->
            if (i > 0) {
                row.addView(View(context), LinearLayout.LayoutParams(dp(4), 1))
            }
            val letter = bucket.letter
            val count = bucket.count
            val on = letter == selectedLetter
            val chipLabel = context.resolveText(
                LibraryBrowse.labeledChip(dynamicText(letter.toString()), count),
            )
            val chip = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                minWidth = minW
                minHeight = minH
                includeFontPadding = false
                setPadding(dp(10), dp(5), dp(10), dp(5))
                setOnClickListener {
                    val idx = GameDeckChromeLogic.firstIndexForLetter(entries, letter) { it.label }
                    val key = entries.getOrNull(idx)?.key ?: return@setOnClickListener
                    playCue(SoundCue.FILTER_SELECT)
                    state.select(key, force = true)
                    Toast.makeText(activity, chipLabel, Toast.LENGTH_SHORT).show()
                }
            }
            val handle = LetterChipHandle(letter, count, chipLabel, chip)
            paintLetterChip(handle, selected = on, animate = false)
            chips.add(handle)
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
        val dm = context.resources.displayMetrics
        val viewportWidthDp = (dm.widthPixels / dm.density.coerceAtLeast(0.5f)).toInt()
        val endPad = dp(
            GameDeckChromeLogic.browseBarEndPadDp(
                showStatusPill && settings.browseChrome.deckStatusPill,
            ),
        )
        val dividerColor = TileBackgrounds.hairlineDivider(context)
        val dividerW = context.strokePx(BrowseChipStyleLogic.GROUP_DIVIDER_WIDTH_DP)
        val dividerH = dp(BrowseChipStyleLogic.GROUP_DIVIDER_HEIGHT_DP)
        val dividerMargin = dp(BrowseChipStyleLogic.GROUP_DIVIDER_MARGIN_H_DP)
        val dotSizePx = dp(BrowseChipStyleLogic.PLATFORM_DOT_SIZE_DP)
        val dotPadPx = dp(BrowseChipStyleLogic.PLATFORM_DOT_PADDING_DP)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), endPad, dp(4))
        }
        var lastGroup: BrowseChipStyleLogic.BrowseChipGroup? = null
        fun addToGroup(group: BrowseChipStyleLogic.BrowseChipGroup, view: View) {
            if (BrowseChipStyleLogic.shouldInsertGroupDivider(lastGroup, group)) {
                row.addView(
                    View(context).apply {
                        setBackgroundColor(dividerColor)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(dividerW, dividerH).apply {
                        marginStart = dividerMargin
                        marginEnd = dividerMargin
                    },
                )
            } else if (lastGroup != null) {
                row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
            }
            row.addView(view)
            lastGroup = group
        }
        fun chip(
            label: String,
            selected: () -> Boolean,
            platformId: String? = null,
            hasContinueCue: Boolean = false,
            maxWidthPx: Int? = null,
            onLongClick: (() -> Unit)? = null,
            onClick: () -> Unit,
        ): TextView {
            val initSelected = selected()
            val pillBg = buildMutablePillBackground(
                context = context,
                fillColor = if (initSelected) settings.accentColor else TileBackgrounds.chipIdleColor(context),
                idleStrokeColor = if (initSelected) null else TileBackgrounds.hairlineDivider(context),
                selected = initSelected,
                cornerRadiusDp = 16,
            )
            val dotDrawable = platformId?.let {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setBounds(0, 0, dotSizePx, dotSizePx)
                }
            }
            val tv = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
                maxLines = 1
                includeFontPadding = false
                minimumHeight = dp(BrowseChipStyleLogic.CHIP_MIN_HEIGHT_DP)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = pillBg.ripple
                if (dotDrawable != null) {
                    setCompoundDrawablesRelative(dotDrawable, null, null, null)
                    compoundDrawablePadding = dotPadPx
                }
                if (maxWidthPx != null) {
                    maxWidth = maxWidthPx
                    ellipsize = TextUtils.TruncateAt.END
                }
                setOnClickListener { onClick() }
                if (onLongClick != null) {
                    setOnLongClickListener {
                        onLongClick()
                        true
                    }
                }
            }
            val handle = BrowseChipHandle(
                view = tv,
                pillBg = pillBg,
                rawLabel = label,
                selected = selected,
                platformId = platformId,
                platformDotDrawable = dotDrawable,
                hasContinueCue = hasContinueCue,
            )
            paintChip(handle, initSelected)
            browseChipHandles.add(handle)
            return tv
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
            playCue(SoundCue.FILTER_SELECT)
            state.setLibraryBrowse(next, force = force)
        }
        // All = full reset: clear platform/genre/search/collection/sort AND
        // jump the carousel to the first unrestricted entry. Keeping the prior
        // NDS selection centered made "All" look like a no-op (same cards still
        // on screen even though the filter was cleared). Long-press → sort order
        // (Name / Last / Top / Platform) for catalog rails — not always-on chrome.
        addToGroup(
            BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
        val chrome = settings.browseChrome
        fun setBrowse(next: LibraryBrowse.BrowseQuery, force: Boolean = false) {
            val qNext = chrome.sanitize(next)
            setQuery(qNext, force = force)
            toastIfEmptyBrowse(qNext)
        }
        addToGroup(
            BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            val gameApps = LibraryBrowse.filterGameApps(library.visible(settings)) { it.isGame }.size
            val romN = counts.listedRoms
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
        // Continue chip shows short target name when known (depth, not clutter).
        val contTargetKey = LibraryBrowse.continueKey(
            availableContinueKeys(settings),
            settings.lastLaunchedMs,
        )
        val contChipLabel = LibraryBrowse.continueChipLabel(
            contTargetKey?.let { continueLabel(it, settings) },
        )
        val continueMaxWidthPx = dp(BrowseChipStyleLogic.maxContinueChipWidthDp(viewportWidthDp))
        addToGroup(
            BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
            chip(
                localized(contChipLabel),
                { false },
                hasContinueCue = contTargetKey != null,
                maxWidthPx = continueMaxWidthPx,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
                chip(context.getString(R.string.label_random), { false }) {
                    pickRandomEntry()
                },
            )
        }
        addToGroup(
            BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            val alphaN = LibraryBrowse.alphaCatalogCount(
                library.curated(settings).size,
                counts.listedRoms,
            )
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
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
                val members = settings.collections[name]?.size ?: 0
                addToGroup(
                    BrowseChipStyleLogic.BrowseChipGroup.COLLECTIONS,
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
                val short = Platforms.byId(pid)?.shortName ?: pid
                addToGroup(
                    BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
                    chip(
                        counted(short, count),
                        { state.libraryBrowse.platformId == pid },
                        platformId = pid,
                        onLongClick = { showPlatformChipMenu(pid, short, count) },
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
                val selected = q.genre?.equals(genre, ignoreCase = true) == true
                addToGroup(
                    BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
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
                val selected = q.developer?.equals(dev, ignoreCase = true) == true
                addToGroup(
                    BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
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
                val selected = q.yearDecade?.equals(decade, ignoreCase = true) == true
                addToGroup(
                    BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
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
        // Gated so the active filter strip exclusively owns clear-all when >= 2 filters
        // are active (and individual dismissible × pills cover 1 filter).
        val activeMetaCount = GameDeckChromeLogic.activeFilters(q).size
        if (GameDeckChromeLogic.showBrowseBarClearChip(activeMetaCount)) {
            addToGroup(
                BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
                chip(
                    counted(R.string.browse_clear_filters, activeMetaCount),
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
        addToGroup(
            BrowseChipStyleLogic.BrowseChipGroup.ACTIONS,
            chip(
                if (q.text.isBlank()) context.getString(R.string.action_search)
                else context.getString(R.string.format_search_query, q.text),
                { state.libraryBrowse.text.isNotBlank() },
                onLongClick = { showSearchHistory() },
            ) {
                openSearchDialog()
            },
        )
        val selectChip = chip(
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
                playCue(SoundCue.CONFIRM)
                state.setMultiSelectEnabled(true)
            }
        }
        multiSelectChipView = selectChip
        addToGroup(BrowseChipStyleLogic.BrowseChipGroup.ACTIONS, selectChip)
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
            title = activity.resolveText(
                BrowseMenuFormatter.countedMenuTitle(text(R.string.label_favorites), count),
            ),
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
            title = activity.resolveText(
                BrowseMenuFormatter.countedMenuTitle(dynamicText(name), count),
            ),
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
                        entryIndexByKey[k]?.let { entries.getOrNull(it)?.label }
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
        playCue(SoundCue.FILTER_SELECT)
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
        playCue(SoundCue.FILTER_SELECT)
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
        val initialPlan = SearchDialogLogic.plan(state.libraryBrowse.text, hasHistory)
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setPaddingRelative(
            input.paddingStart,
            input.paddingTop,
            activity.dp(SearchDialogLogic.INPUT_END_PADDING_DP),
            input.paddingBottom,
        )
        val clearBtn = ImageView(activity).apply {
            setImageResource(R.drawable.ic_close)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(UiDimens.ALPHA_SECONDARY, PorterDuff.Mode.SRC_IN)
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(R.string.action_clear)
            background = TileBackgrounds.focusableRow(
                context = activity,
                accent = settings.accentColor,
                cornerRadiusDp = 18,
                strokeDp = 2,
            )
            visibility = if (initialPlan.showInlineClear) View.VISIBLE else View.GONE
            setOnClickListener {
                input.setText("")
                input.requestFocus()
            }
        }
        val clearSizePx = activity.dp(SearchDialogLogic.INLINE_CLEAR_SIZE_DP)
        container.addView(
            clearBtn,
            FrameLayout.LayoutParams(
                clearSizePx,
                clearSizePx,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ).apply {
                marginEnd = activity.dp(SearchDialogLogic.INLINE_CLEAR_END_MARGIN_DP)
            },
        )
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val p = SearchDialogLogic.plan(s?.toString().orEmpty(), hasHistory)
                clearBtn.visibility = if (p.showInlineClear) View.VISIBLE else View.GONE
            }
        })
        val builder = DeckDialogs.builder(activity)
            .setTitle(R.string.browse_search_library)
            .setView(container)
            .setPositiveButton(R.string.action_search) { _, _ ->
                applySearch(input.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.action_cancel, null)
        when (initialPlan.neutralButton) {
            SearchDialogLogic.NeutralButtonKind.HISTORY -> {
                builder.setNeutralButton(R.string.action_history) { _, _ ->
                    showSearchHistory()
                }
            }
            SearchDialogLogic.NeutralButtonKind.CLEAR -> {
                builder.setNeutralButton(R.string.action_clear) { _, _ ->
                    applySearch("")
                }
            }
        }
        val dialog = DeckDialogs.show(builder, activity)
        input.setOnEditorActionListener { _, actionId, event ->
            val submit = SearchDialogLogic.shouldSubmitFromEditor(
                actionId = actionId,
                keyCode = event?.keyCode,
                isKeyDown = event?.action == KeyEvent.ACTION_DOWN,
            )
            if (submit) {
                applySearch(input.text?.toString().orEmpty())
                dialog.dismiss()
            }
            submit
        }
    }

    /** Apply a library text filter and remember non-blank queries. */
    private fun applySearch(raw: String) {
        val text = SearchDialogLogic.normalizeQuery(raw)
        val next = state.libraryBrowse.copy(text = text)
        playCue(SoundCue.FILTER_SELECT)
        state.setLibraryBrowse(next)
        if (text.isNotEmpty()) {
            val live = app().settings
            val history = SearchHistory.push(live.searchHistory, text)
            if (history != live.searchHistory) {
                app().updateSettings(live.copy(searchHistory = history))
            }
        }
        val n = currentCarouselSizeFor(next)
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
        val activeSearch = state.libraryBrowse.text
        val displayItems = history.map { query ->
            activity.resolveText(
                BrowseMenuFormatter.withActiveCheck(
                    label = query,
                    active = BrowseMenuFormatter.isSearchHistoryEntryActive(query, activeSearch),
                ),
            )
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.browse_recent_searches,
            items = displayItems,
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
            if (which in history.indices) applySearch(history[which])
        }
    }

    /** Toast when a rail/filter would show zero cards. */
    private fun toastIfEmptyBrowse(q: LibraryBrowse.BrowseQuery) {
        val hint = BrowseFeedback.emptyHint(q) ?: return
        if (currentCarouselSizeFor(q) == 0) {
            Toast.makeText(activity, activity.resolveText(hint), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * O(1) carousel count when [entries] was already synchronously rebuilt for [q]
     * (the normal path after [DeckState.setLibraryBrowse]), falling back to
     * [estimateCarouselSize] only if browse painting was deferred.
     */
    private fun currentCarouselSizeFor(q: LibraryBrowse.BrowseQuery): Int {
        val sanitized = settings.browseChrome.sanitize(q)
        if (entriesBuiltForQuery == sanitized) return entries.size
        return estimateCarouselSize(sanitized)
    }

    /**
     * Lightweight size estimate for feedback toasts (apps + ROMs, same gates
     * as the carousel for search/platform/genre).
     */
    private fun estimateCarouselSize(q: LibraryBrowse.BrowseQuery): Int {
        val live = app().settings
        val now = System.currentTimeMillis()
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
        val byRom = romIndex()
        if (q.mode == LibraryBrowse.Mode.COLLECTION) {
            val name = q.collectionName.orEmpty()
            val keys = live.collections[name].orEmpty()
            if (keys.isEmpty()) return 0
            // Member keys that still resolve (apps or visible ROMs).
            val hidden = live.hiddenRomIds
            val curated = library.curated(live).map { it.packageName }.toSet()
            return keys.count { k ->
                SlotKey.romId(k)?.let { id -> id !in hidden && id in byRom } == true ||
                    (!SlotKey.isRom(k) && k in curated)
            }
        }
        val browsed = LibraryBrowse.browseRoms(
            roms, q,
            lastLaunchedMs = live.lastLaunchedMs,
            favorites = live.favorites,
            collections = live.collections,
            playtimeMs = live.playtimeMs,
            hiddenRomIds = live.hiddenRomIds,
            nowMs = now,
            launchablePlatformIds = resolveLaunchablePlatformIds(live),
            romById = byRom,
        )
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
    private fun showPlatformChipMenu(platformId: String, shortName: String, romCount: Int = 0) {
        val currentQuery = state.libraryBrowse
        val actions = LibraryBrowse.platformChipActions(
            currentQuery,
            platformId,
            shortName,
        )
        if (actions.isEmpty()) return
        fun baseLabel(action: LibraryBrowse.PlatformChipAction): String = when (action.kind) {
            LibraryBrowse.PlatformChipActionKind.FILTER ->
                activity.getString(R.string.browse_filter_platform, action.shortName)
            LibraryBrowse.PlatformChipActionKind.CLEAR ->
                activity.getString(R.string.browse_clear_platform)
            LibraryBrowse.PlatformChipActionKind.SORT_NAME ->
                activity.getString(R.string.browse_sort_alpha_platform, action.shortName)
            LibraryBrowse.PlatformChipActionKind.SORT_LAST_PLAYED ->
                activity.getString(R.string.browse_sort_last_platform, action.shortName)
        }
        val labels = actions.map { action ->
            val raw = baseLabel(action)
            val active = BrowseMenuFormatter.isPlatformActionActive(currentQuery, action)
            activity.resolveText(BrowseMenuFormatter.withActiveCheck(raw, active))
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            title = activity.resolveText(
                BrowseMenuFormatter.platformMenuTitle(shortName, platformId, romCount),
            ),
            items = labels,
        ) { which ->
            if (which !in actions.indices) return@chooseItem
            val action = actions[which]
            val next = settings.browseChrome.sanitize(action.query)
            state.setLibraryBrowse(next, force = true)
            toastIfEmptyBrowse(next)
            Toast.makeText(activity, baseLabel(action), Toast.LENGTH_SHORT).show()
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
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val surface = TileBackgrounds.surfaceColor(activity)
        val pillSurface = TileBackgrounds.chipIdleColor(activity)
        val pad = dp(20)
        val headerSpec = DetailsCardFormatter.headerSpec(
            title = entry.label,
            platformId = rom?.platformId,
            favorite = key in settings.favorites,
            year = rom?.year,
            genre = rom?.genre,
            surfaceColor = pillSurface,
        )
        val thumbPx = dp(DetailsCardFormatter.HEADER_THUMB_DP)
        val thumbRadiusPx = activity.dpF(DetailsCardFormatter.HEADER_THUMB_CORNER_DP)
        val thumbFrame = FrameLayout(activity).apply {
            clipToOutline = true
            outlineProvider = OutlineCache.roundedRect(thumbRadiusPx)
        }
        val thumbView: View = rom?.let {
            ArtTile.view(
                activity,
                app().artCache,
                it,
                targetPx = thumbPx,
                cornerRadiusDp = DetailsCardFormatter.HEADER_THUMB_CORNER_DP,
                artOverrides = settings.artOverrides,
            )
        } ?: ImageView(activity).apply {
            entry.appPackage?.let { pkg ->
                CustomIcon.bind(this, iconLoader, app().artCache, settings, pkg, thumbPx)
            }
        }
        thumbFrame.addView(
            thumbView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val headerTextCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerTextCol.addView(
            TextView(activity).apply {
                text = headerSpec.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        val badgeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val typePill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(TileBackgrounds.chipIdleColor(activity))
                cornerRadius = activity.dpF(8)
                setStroke(activity.strokePx(1), TileBackgrounds.hairlineDivider(activity))
            }
            setPadding(dp(7), dp(2), dp(7), dp(2))
        }
        headerSpec.badgeDotColor?.let { dot ->
            typePill.addView(
                View(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dot)
                    }
                },
                LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(5) },
            )
        }
        typePill.addView(
            TextView(activity).apply {
                text = activity.resolveText(headerSpec.badgeText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                includeFontPadding = false
            },
        )
        badgeRow.addView(typePill)
        if (headerSpec.showFavoriteStar) {
            badgeRow.addView(
                TextView(activity).apply {
                    setText(R.string.glyph_favorite)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(CarouselCardBadges.FAVORITE_BADGE_TINT)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(TileBackgrounds.chipIdleColor(activity))
                        setStroke(activity.strokePx(1), CarouselCardBadges.FAVORITE_STROKE_COLOR)
                    }
                    contentDescription = activity.getString(R.string.action_favorite)
                },
                LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(6) },
            )
        }
        headerTextCol.addView(
            badgeRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(5) },
        )
        headerSpec.subtitle?.let { sub ->
            headerTextCol.addView(
                TextView(activity).apply {
                    text = activity.resolveText(sub)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(3) },
            )
        }
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, dp(10), pad, dp(12))
            addView(thumbFrame, LinearLayout.LayoutParams(thumbPx, thumbPx).apply {
                marginEnd = dp(14)
            })
            addView(
                headerTextCol,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        val message = TextView(activity).apply {
            text = body
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            setLineSpacing(0f, DetailsCardFormatter.BODY_LINE_SPACING_MULT)
            setTextIsSelectable(true)
            setPadding(pad, dp(10), pad, dp(10))
        }
        val bodyView: TextView = message
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerRow)
            addView(
                View(activity).apply { setBackgroundColor(TileBackgrounds.hairlineDivider(activity)) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.strokePx(1),
                ).apply {
                    marginStart = pad
                    marginEnd = pad
                },
            )
            addView(message)
        }
        if (rom != null) {
            content.addView(
                View(activity).apply { setBackgroundColor(TileBackgrounds.hairlineDivider(activity)) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.strokePx(1),
                ).apply {
                    marginStart = pad
                    marginEnd = pad
                    topMargin = dp(2)
                    bottomMargin = dp(4)
                },
            )
            val helperValue = TextView(activity).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ThemePack.accentTextColor(settings.accentColor, surface))
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
            }
            val helperRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, dp(10), pad, dp(10))
                isFocusable = true
                isClickable = true
                background = TileBackgrounds.focusableRow(
                    context = activity,
                    accent = settings.accentColor,
                    cornerRadiusDp = 10,
                )
                setOnClickListener {
                    showRomHelperPicker(rom.id, helperValue) { label ->
                        contentDescription = activity.resolveText(
                            DetailsCardFormatter.helperRowAccessibilityText(label),
                        )
                    }
                }
                setOnLongClickListener {
                    clearRomHelper(rom.id, helperValue) { label ->
                        contentDescription = activity.resolveText(
                            DetailsCardFormatter.helperRowAccessibilityText(label),
                        )
                    }
                    true
                }
            }
            val initialHelperLabel = bindRomHelperValue(helperValue, rom.id)
            helperRow.contentDescription = activity.resolveText(
                DetailsCardFormatter.helperRowAccessibilityText(initialHelperLabel),
            )
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
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f),
            )
            helperRow.addView(
                ImageView(activity).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setColorFilter(UiDimens.ALPHA_MUTED, PorterDuff.Mode.SRC_IN)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(6) },
            )
            content.addView(helperRow)
        }
        builder.setView(
            OverlayPrimitives.MaxHeightScrollView(
                activity,
                OverlayPrimitives.maxModalHeight(activity, 0.58f),
            ).apply {
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(16))
                addView(content)
            },
        )
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
            bodyView.setOnLongClickListener {
                copyHashToClipboard(fullHash)
                true
            }
        }
    }

    private fun helperAppLabel(packageName: String): String =
        library.byPackage(settings)[packageName]?.label ?: packageName

    private fun bindRomHelperValue(value: TextView, romId: String): String {
        val pkg = app().settings.romHelpers[romId]
        val label = pkg?.let { helperAppLabel(it) }
            ?: activity.getString(R.string.action_none)
        value.text = label
        return label
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

    private fun clearRomHelper(
        romId: String,
        value: TextView,
        onUpdated: (String) -> Unit = {},
    ) {
        val live = app().settings
        app().updateSettings(live.copy(romHelpers = live.romHelpers - romId))
        onUpdated(bindRomHelperValue(value, romId))
    }

    private fun showRomHelperPicker(
        romId: String,
        value: TextView,
        onUpdated: (String) -> Unit = {},
    ) {
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
            onNeutral = { clearRomHelper(romId, value, onUpdated) },
        ) { which ->
            val pkg = apps[which].packageName
            val live = app().settings
            app().updateSettings(live.copy(romHelpers = live.romHelpers + (romId to pkg)))
            onUpdated(bindRomHelperValue(value, romId))
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
        playAudio(DeckAudioLogic.movePickupEvent(DeckAudioLogic.panForDockSlot(index, dockVisibleCount())))
        hintView?.text = HintBar.moveText(activity)
        state.focusDock(index)
        dockBar?.updateFocus(index, moving = index)
    }

    private fun handleDockMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> {
                val visible = dockVisibleCount()
                val to = dockNav.move(from, action)
                if (to != from) {
                    val next = dockMove.swap(from, to)
                    if (next == null) {
                        playAudio(DeckAudioLogic.dockMoveStepEvent(action, from, from, visible))
                        return true
                    }
                    playAudio(
                        DeckAudioLogic.dockMoveStepEvent(
                            action = action,
                            fromIndex = from,
                            toIndex = next,
                            visibleCount = visible,
                            repeatStreak = navStreak(action),
                        ),
                    )
                    dockBar?.rebind()
                    state.focusDock(next)
                    dockBar?.updateFocus(next, moving = next)
                } else {
                    playAudio(DeckAudioLogic.dockMoveStepEvent(action, from, to, visible))
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
        playAudio(
            DeckAudioLogic.moveDropEvent(
                DeckAudioLogic.panForDockSlot(result.focusIndex, dockVisibleCount()),
            ),
        )
        hintView?.text = HintBar.textFor(activity, state.dockSlot != null)
        updateDockSlots(result.compacted)
        state.focusDock(result.focusIndex)
    }

    private fun cancelDockMove() {
        val pan = state.dockSlot?.let { DeckAudioLogic.panForDockSlot(it, dockVisibleCount()) } ?: 0f
        playAudio(DeckAudioLogic.moveCancelEvent(pan))
        dockMove.clear()
        hintView?.text = HintBar.textFor(activity, state.dockSlot != null)
        dockBar?.rebind()
        dockBar?.updateFocus(state.dockSlot)
    }

    private fun handleDockAction(action: Action, dockIndex: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> {
                val next = dockNav.move(dockIndex, action)
                playAudio(
                    DeckAudioLogic.dockNavigationEvent(
                        action = action,
                        fromIndex = dockIndex,
                        toIndex = next,
                        visibleCount = dockVisibleCount(),
                        repeatStreak = navStreak(action),
                    ),
                )
                state.focusDock(next)
            }
            Action.NAV_UP, Action.BACK -> {
                playAudio(DeckAudioLogic.dockExitEvent(dockIndex, dockVisibleCount()))
                state.select(entries.getOrNull(selectedIndex())?.key ?: state.selectedKey)
            }
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
        val cardRadiusDp: Int,
        val cardTypography: CarouselLayoutMetrics.CardTypography,
    ) : RecyclerView.Adapter<CardAdapter.CardHolder>() {

        /** Last selection key/dock-focus we painted (for partial updates). */
        var paintedSelectionKey: String? = null
        var paintedDockFocused: Boolean = false
        private var cachedCheckAccent: Int = 0
        private var cachedCheckTint: Int = 0
        private var hasCachedCheckTint: Boolean = false
        private var cachedFocusAccent: Int = 0
        private var cachedFocusSurface: Int = 0
        private var cachedFocusStroke: Int = 0
        private var hasCachedFocusStroke: Boolean = false
        private var cachedRippleColor: Int = 0
        private var cachedRippleColorList: ColorStateList? = null
        private val platformDotColors = HashMap<String, Int>(16)

        private val artCache: ArtCache = (activity.application as GhostGalleonApp).artCache
        private val cardWidthPx: Int = GameDeckChromeLogic.carouselCardWidthPx(cardSize, cellPadding)
        private val outerCardRadiusPx: Float = context.dpF(cardRadiusDp)
        private val multiSelectStrokePx: Int = context.strokePx(2)
        private val innerArtRadiusDp: Int = CarouselDepthMetrics.opticalInnerArtRadiusDp(
            outerCardRadiusDp = cardRadiusDp,
            cellPaddingDp = CELL_PADDING_DP,
        )
        private val innerArtRadiusPx: Float = context.dpF(innerArtRadiusDp)
        private val romOverlayRadiusPx: Float = ArtTileMetrics.clampedCornerRadiusPx(
            cornerRadiusDp = innerArtRadiusDp,
            density = context.resources.displayMetrics.density,
            targetPx = cardSize,
        )
        private val scrimHeightPx: Int = (cardSize * CarouselCardBadges.ART_SCRIM_HEIGHT_FRAC)
            .toInt()
            .coerceAtLeast(context.dp(24))
        private val scrimTemplate: GradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            CarouselCardBadges.artBottomScrimColors(),
        )
        private val platformPillBgTemplate: GradientDrawable = GradientDrawable().apply {
            setColor(CarouselCardBadges.PILL_BACKDROP_COLOR)
            cornerRadius = context.dpF(8)
            setStroke(context.strokePx(1), CarouselCardBadges.PILL_STROKE_COLOR)
        }
        private val favPillBgTemplate: GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(CarouselCardBadges.PILL_BACKDROP_COLOR)
            setStroke(context.strokePx(1), CarouselCardBadges.FAVORITE_STROKE_COLOR)
        }

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long =
            entries.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()

        inner class CardHolder(
            val root: LinearLayout,
            val cardFrame: FrameLayout,
            val rippleForeground: RippleDrawable,
            val multiSelectOverlay: GradientDrawable,
            val romArtFrame: FrameLayout,
            val romPlaceholder: TextView,
            val romOverlay: ImageView,
            val appIcon: ImageView,
            val platformBadge: LinearLayout,
            val platformDotDrawable: GradientDrawable,
            val platformLabel: TextView,
            val favBadge: TextView,
            val titleView: TextView,
            val metaView: TextView,
            val multiBadge: ImageView,
            val checkBadgeBg: GradientDrawable,
        ) : RecyclerView.ViewHolder(root) {
            var boundEntry: CarouselEntry? = null
            var boundIsRom: Boolean? = null
            var boundPlatformId: String? = null
            var paintedFocused: Boolean? = null
            var paintedAccent: Int = 0
            var paintedRippleColor: Int = 0
            var paintedShadowColor: Int = CarouselDepthMetrics.UNFOCUSED_SHADOW_COLOR
            var paintedCheckStyle: CarouselMultiSelectLogic.CardCheckStyle? = null
            var paintedCheckAccent: Int = 0
            var paintedSelectionKey: String? = null
            var paintedDockFocused: Boolean = false
            var paintedMultiSelectEnabled: Boolean = false
            var paintedMultiSelected: Boolean = false
            var edgeBumpAnimator: ObjectAnimator? = null

            fun cancelEdgeBump() {
                edgeBumpAnimator?.let { anim ->
                    edgeBumpAnimator = null
                    anim.cancel()
                }
                if (cardFrame.translationX != 0f) {
                    cardFrame.translationX = 0f
                }
            }
        }

        private fun cloneDrawable(template: GradientDrawable): GradientDrawable =
            template.constantState?.newDrawable(context.resources)?.mutate() as? GradientDrawable
                ?: template

        private fun accessibleFocusStroke(accent: Int, surfaceFill: Int): Int {
            if (!hasCachedFocusStroke || cachedFocusAccent != accent || cachedFocusSurface != surfaceFill) {
                cachedFocusAccent = accent
                cachedFocusSurface = surfaceFill
                cachedFocusStroke = CarouselDepthMetrics.accessibleCardFocusStroke(accent, surfaceFill)
                hasCachedFocusStroke = true
            }
            return cachedFocusStroke
        }

        private fun rippleColorStateList(rippleColor: Int): ColorStateList {
            val cached = cachedRippleColorList
            if (cached != null && cachedRippleColor == rippleColor) return cached
            return ColorStateList.valueOf(rippleColor).also {
                cachedRippleColor = rippleColor
                cachedRippleColorList = it
            }
        }

        private fun checkTint(accent: Int): Int {
            if (!hasCachedCheckTint || cachedCheckAccent != accent) {
                cachedCheckAccent = accent
                cachedCheckTint = ThemePack.accentTextColor(accent, Color.BLACK)
                hasCachedCheckTint = true
            }
            return cachedCheckTint
        }

        private fun platformDotColor(platformId: String): Int =
            platformDotColors.getOrPut(platformId) {
                ThemePack.accentTextColor(
                    PlatformLook.accentColor(platformId),
                    CarouselCardBadges.PILL_BACKDROP_COLOR,
                )
            }

        private fun releaseRomOverlay(overlay: ImageView) {
            overlay.animate().cancel()
            val bmp = when (val d = overlay.drawable) {
                is RoundedBitmapDrawable -> d.bitmap
                is BitmapDrawable -> d.bitmap
                else -> null
            }
            if (bmp != null) ArtCache.releaseDisplay(bmp)
            overlay.setImageDrawable(null)
            overlay.tag = null
        }

        private fun releaseAppIcon(icon: ImageView) {
            ArtCache.dropDisplayed(icon)
            icon.setTag(R.id.custom_icon_key, null)
            icon.setImageDrawable(null)
        }

        override fun onViewAttachedToWindow(holder: CardHolder) {
            super.onViewAttachedToWindow(holder)
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return
            val entry = entries.getOrNull(pos) ?: return
            val dockFocused = state.dockSlot != null
            val multiEnabled = state.multiSelectEnabled
            val multiSelected = multiEnabled && entry.key in state.multiSelectKeys
            if (holder.paintedSelectionKey != state.selectedKey ||
                holder.paintedDockFocused != dockFocused ||
                holder.paintedMultiSelectEnabled != multiEnabled ||
                holder.paintedMultiSelected != multiSelected
            ) {
                applySelectionVisuals(holder, pos, animate = false)
            }
        }

        override fun onViewDetachedFromWindow(holder: CardHolder) {
            super.onViewDetachedFromWindow(holder)
            holder.cancelEdgeBump()
            holder.cardFrame.animate().cancel()
            holder.rippleForeground.jumpToCurrentState()
            holder.romOverlay.animate().cancel()
        }

        override fun onViewRecycled(holder: CardHolder) {
            super.onViewRecycled(holder)
            holder.cancelEdgeBump()
            holder.cardFrame.animate().cancel()
            holder.rippleForeground.jumpToCurrentState()
            holder.romOverlay.animate().cancel()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
            // Build the entire fixed card view tree ONCE per pooled ViewHolder so
            // scrolling and browse swaps rebind in place with zero view churn.
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
            val initialRippleColor = CarouselCardBadges.cardRippleColor(
                surfaceFill = TileBackgrounds.surfaceColor(context),
                accent = settings.accentColor,
            )
            val rippleMask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = outerCardRadiusPx
            }
            val rippleForeground = RippleDrawable(
                rippleColorStateList(initialRippleColor),
                null,
                rippleMask,
            )
            val multiSelectOverlay = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = outerCardRadiusPx
                setStroke(0, Color.TRANSPARENT)
            }
            val cardFrame = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                isDuplicateParentStateEnabled = true
                // Persistent foreground: layer 0 = touch ripple, layer 1 = multi-select overlay.
                foreground = LayerDrawable(arrayOf(rippleForeground, multiSelectOverlay))
            }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            val artContainer = FrameLayout(context).apply {
                clipToOutline = true
                outlineProvider = OutlineCache.roundedRect(innerArtRadiusPx)
            }
            val romPlaceholder = PlatformTile.view(context, "", innerArtRadiusDp).apply {
                // Force first PlatformTile.restyle to apply the real platform style.
                setTag(R.id.platform_tile_id, null)
            }
            val romOverlay = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                clipToOutline = true
                outlineProvider = OutlineCache.roundedRect(romOverlayRadiusPx)
                addOnAttachStateChangeListener(DETACH_ANIM_CANCEL)
            }
            val romArtFrame = FrameLayout(context).apply {
                addView(
                    romPlaceholder,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    romOverlay,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            artContainer.addView(
                romArtFrame,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            val appIcon = ImageView(context).apply {
                visibility = View.GONE
            }
            artContainer.addView(
                appIcon,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            // Subtle bottom gradient scrim grounds corner pills and cover-to-title transition.
            artContainer.addView(
                View(context).apply {
                    background = cloneDrawable(scrimTemplate)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    scrimHeightPx,
                    Gravity.BOTTOM,
                ),
            )
            val platformDotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
            }
            val platformLabel = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                includeFontPadding = false
            }
            val platformBadge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = cloneDrawable(platformPillBgTemplate)
                setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(2))
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(
                    View(context).apply {
                        background = platformDotDrawable
                    },
                    LinearLayout.LayoutParams(context.dp(6), context.dp(6)).apply {
                        marginEnd = context.dp(4)
                    },
                )
                addView(platformLabel)
            }
            artContainer.addView(
                platformBadge,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                ).apply {
                    marginStart = context.dp(6)
                    bottomMargin = context.dp(6)
                },
            )
            val favPill = TextView(context).apply {
                setText(R.string.glyph_favorite)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(CarouselCardBadges.FAVORITE_BADGE_TINT)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = cloneDrawable(favPillBgTemplate)
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, cardTypography.titleSp)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                gravity = if (cardTypography.titleMaxLines > 1) {
                    Gravity.CENTER_HORIZONTAL or Gravity.TOP
                } else {
                    Gravity.CENTER
                }
                minLines = cardTypography.titleMinLines
                maxLines = cardTypography.titleMaxLines
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            body.addView(
                titleTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(cardTypography.titleTopMarginDp) },
            )
            val metaTv = TextView(context).apply {
                tag = "card_meta"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, cardTypography.metaSp)
                setTextColor(UiDimens.ALPHA_MUTED)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            body.addView(
                metaTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(cardTypography.metaTopMarginDp) },
            )
            cardFrame.addView(
                body,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            val checkBadgeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC000000.toInt())
            }
            val checkBadge = ImageView(context).apply {
                setImageResource(R.drawable.ic_check_circle)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                background = checkBadgeBg
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
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
            slot.addView(
                cardFrame,
                LinearLayout.LayoutParams(
                    cardWidthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            val holder = CardHolder(
                root = slot,
                cardFrame = cardFrame,
                rippleForeground = rippleForeground,
                multiSelectOverlay = multiSelectOverlay,
                romArtFrame = romArtFrame,
                romPlaceholder = romPlaceholder,
                romOverlay = romOverlay,
                appIcon = appIcon,
                platformBadge = platformBadge,
                platformDotDrawable = platformDotDrawable,
                platformLabel = platformLabel,
                favBadge = favPill,
                titleView = titleTv,
                metaView = metaTv,
                multiBadge = checkBadge,
                checkBadgeBg = checkBadgeBg,
            ).apply {
                paintedRippleColor = initialRippleColor
            }
            slot.setOnClickListener {
                val entry = holder.boundEntry ?: return@setOnClickListener
                if (state.multiSelectEnabled) {
                    state.toggleMultiSelectKey(entry.key)
                    playAudio(DeckAudioLogic.multiSelectToggleEvent(entry.key in state.multiSelectKeys))
                    return@setOnClickListener
                }
                if (state.selectedKey == entry.key && state.dockSlot == null) {
                    launch(entry)
                } else {
                    val fromIdx = selectedIndex()
                    val toIdx = entryIndexByKey[entry.key] ?: fromIdx
                    playAudio(DeckAudioLogic.carouselCardFocusEvent(fromIdx, toIdx))
                    state.select(entry.key)
                }
            }
            slot.setOnLongClickListener {
                val entry = holder.boundEntry ?: return@setOnLongClickListener true
                if (state.multiSelectEnabled) {
                    state.toggleMultiSelectKey(entry.key)
                    playAudio(DeckAudioLogic.multiSelectToggleEvent(entry.key in state.multiSelectKeys))
                } else {
                    state.select(entry.key)
                    openEntryMenu(entry)
                }
                true
            }
            return holder
        }

        override fun getItemCount() = entries.size

        /** Notify only cards around the visible viewport (+pad) instead of scanning 6k–20k items. */
        fun notifyVisibleSelectionRange() {
            val count = itemCount
            if (count <= 0) return
            val lm = recycler?.layoutManager as? LinearLayoutManager ?: return
            val range = GameDeckChromeLogic.visibleSelectionNotifyRange(
                firstVisible = lm.findFirstVisibleItemPosition(),
                lastVisible = lm.findLastVisibleItemPosition(),
                itemCount = count,
            )
            if (range != null) {
                notifyItemRangeChanged(
                    range.first,
                    range.last - range.first + 1,
                    PAYLOAD_SELECTION,
                )
            } else if (count <= GameDeckChromeLogic.MAX_SYNC_DIFF_ITEMS) {
                notifyItemRangeChanged(0, count, PAYLOAD_SELECTION)
            }
        }

        /**
         * Selection-only: rebind just the cards whose depth tier (center / ±1
         * near / ±2+ far) changed. Dock focus / multi-select updates visible window.
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
                notifyVisibleSelectionRange()
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

        /** Mutate ring/depth-scale/alpha/Y-lift/Z-elevation/multi-select only; false → full rebind needed. */
        private fun applySelectionVisuals(
            holder: CardHolder,
            position: Int,
            animate: Boolean,
        ): Boolean {
            val entry = entries.getOrNull(position) ?: return false
            holder.boundEntry = entry
            val cardFrame = holder.cardFrame
            val dockFocused = state.dockSlot != null
            val selectedKey = state.selectedKey
            val focusedIndex = selectedKey?.let { entryIndexByKey[it] }
            val focused = entry.key == selectedKey && !dockFocused
            val multiEnabled = state.multiSelectEnabled
            val multiSelected = multiEnabled && entry.key in state.multiSelectKeys
            val depthStyle = CarouselDepthMetrics.styleFor(
                position = position,
                focusedIndex = focusedIndex,
                dockFocused = dockFocused,
                multiSelected = multiSelected,
            )
            val surfaceFill = TileBackgrounds.surfaceColor(context)
            val selectedSurfaceFill = TileBackgrounds.surfaceElevated(context)
            val accent = settings.accentColor
            val focusStroke = accessibleFocusStroke(accent, selectedSurfaceFill)
            if (holder.paintedFocused != focused || holder.paintedAccent != focusStroke) {
                cardFrame.background = if (focused) {
                    TileBackgrounds.selected(context, focusStroke)
                } else {
                    TileBackgrounds.card(context)
                }
                holder.paintedFocused = focused
                holder.paintedAccent = focusStroke
            }
            val rippleColor = CarouselCardBadges.cardRippleColor(surfaceFill, accent)
            if (holder.paintedRippleColor != rippleColor) {
                holder.rippleForeground.setColor(rippleColorStateList(rippleColor))
                holder.paintedRippleColor = rippleColor
            }
            holder.titleView.setTextColor(
                if (focused || multiSelected) UiDimens.ALPHA_PRIMARY else UiDimens.ALPHA_SECONDARY,
            )
            val targetLiftY = context.dpF(depthStyle.translationYDp)
            val targetElevationPx = context.dpF(depthStyle.translationZDp)
            holder.root.translationZ = targetElevationPx
            cardFrame.elevation = targetElevationPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val platformAccent = entry.rom?.platformId?.let { PlatformLook.accentColor(it) }
                val shadowColor = CarouselDepthMetrics.cardFocusShadowColor(
                    focused = focused,
                    platformAccent = platformAccent,
                    themeAccent = focusStroke,
                )
                if (holder.paintedShadowColor != shadowColor) {
                    cardFrame.outlineSpotShadowColor = shadowColor
                    cardFrame.outlineAmbientShadowColor = shadowColor
                    holder.paintedShadowColor = shadowColor
                }
            }
            holder.cancelEdgeBump()
            cardFrame.animate().cancel()
            if (animate && cardFrame.isLaidOut && ValueAnimator.areAnimatorsEnabled()) {
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
            val favorite = entry.key in settings.favorites
            val overlay = CarouselCardBadges.overlaySpec(
                platformId = entry.rom?.platformId,
                activePlatformFilter = state.libraryBrowse.platformId,
                favorite = favorite,
                multiSelectEnabled = multiEnabled,
                multiSelected = multiSelected,
            )
            val checkStyle = CarouselMultiSelectLogic.cardCheckStyle(
                multiSelectEnabled = multiEnabled,
                multiSelected = multiSelected,
            )
            if (holder.paintedCheckStyle != checkStyle ||
                (checkStyle == CarouselMultiSelectLogic.CardCheckStyle.CHECKED &&
                    holder.paintedCheckAccent != focusStroke)
            ) {
                val fgPolicy = CarouselCardBadges.cardForegroundPolicy(checkStyle)
                if (fgPolicy.showMultiSelectOverlay) {
                    holder.multiSelectOverlay.setColor((accent and 0x00FFFFFF) or (0x38 shl 24))
                    holder.multiSelectOverlay.setStroke(multiSelectStrokePx, focusStroke)
                } else {
                    holder.multiSelectOverlay.setColor(Color.TRANSPARENT)
                    holder.multiSelectOverlay.setStroke(0, Color.TRANSPARENT)
                }
                when (checkStyle) {
                    CarouselMultiSelectLogic.CardCheckStyle.CHECKED -> {
                        holder.multiBadge.apply {
                            setImageResource(R.drawable.ic_check_circle)
                            setColorFilter(checkTint(accent), PorterDuff.Mode.SRC_IN)
                            visibility = View.VISIBLE
                        }
                        holder.checkBadgeBg.setColor(0xCC000000.toInt())
                    }
                    CarouselMultiSelectLogic.CardCheckStyle.UNCHECKED -> {
                        holder.multiBadge.apply {
                            setImageResource(R.drawable.ic_radio_unchecked)
                            setColorFilter(UiDimens.ALPHA_SECONDARY, PorterDuff.Mode.SRC_IN)
                            visibility = View.VISIBLE
                        }
                        holder.checkBadgeBg.setColor(0x99000000.toInt())
                    }
                    CarouselMultiSelectLogic.CardCheckStyle.HIDDEN -> {
                        holder.multiBadge.visibility = View.GONE
                    }
                }
                holder.paintedCheckStyle = checkStyle
                holder.paintedCheckAccent = focusStroke
            }
            holder.favBadge.visibility =
                if (overlay.showFavoriteBadge) View.VISIBLE else View.GONE
            holder.platformBadge.visibility =
                if (overlay.platformBadgeText != null) View.VISIBLE else View.GONE
            holder.root.contentDescription = context.resolveText(
                CarouselCardBadges.cardAccessibilityText(
                    title = entry.label,
                    platformBadge = entry.rom?.platformId?.let { PlatformLook.filterBadge(it) },
                    favorite = favorite,
                    multiSelected = multiSelected,
                ),
            )
            holder.paintedSelectionKey = selectedKey
            holder.paintedDockFocused = dockFocused
            holder.paintedMultiSelectEnabled = multiEnabled
            holder.paintedMultiSelected = multiSelected
            return true
        }

        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            val entry = entries[position]
            val prevKey = holder.root.getTag(R.id.carousel_entry_key) as? String
            holder.boundEntry = entry
            // Same key already bound in this holder: skip art rebind (biggest fling win).
            if (prevKey == entry.key && holder.boundIsRom != null) {
                if (holder.titleView.text != entry.label) {
                    holder.titleView.text = entry.label
                }
                val rom = entry.rom
                if (rom != null) {
                    if (holder.romOverlay.drawable == null) {
                        ArtTile.bind(
                            holder.romOverlay,
                            artCache,
                            rom,
                            targetPx = cardSize,
                            cornerRadiusDp = innerArtRadiusDp,
                            artOverrides = settings.artOverrides,
                        )
                    } else if (holder.romOverlay.alpha < 1f) {
                        holder.romOverlay.animate().cancel()
                        holder.romOverlay.alpha = 1f
                    }
                }
                applySelectionVisuals(holder, position, animate = false)
                rebindCardMetaText(holder, entry)
                scheduleNeighborPrefetch(position)
                return
            }
            holder.cancelEdgeBump()
            holder.cardFrame.animate().cancel()
            holder.root.setTag(R.id.carousel_entry_key, entry.key)
            paintedSelectionKey = state.selectedKey
            paintedDockFocused = state.dockSlot != null

            // Rebind ROM vs App art in place inside the pooled ViewHolder (zero removeAllViews).
            val rom = entry.rom
            if (rom != null) {
                if (holder.boundIsRom != true) {
                    if (holder.boundIsRom == false) {
                        releaseAppIcon(holder.appIcon)
                    }
                    holder.appIcon.visibility = View.GONE
                    holder.romArtFrame.visibility = View.VISIBLE
                    holder.boundIsRom = true
                }
                PlatformTile.restyle(holder.romPlaceholder, context, rom.platformId, innerArtRadiusDp)
                ArtTile.bind(
                    holder.romOverlay,
                    artCache,
                    rom,
                    targetPx = cardSize,
                    cornerRadiusDp = innerArtRadiusDp,
                    artOverrides = settings.artOverrides,
                )
                if (holder.boundPlatformId != rom.platformId) {
                    holder.platformLabel.text = PlatformLook.filterBadge(rom.platformId)
                    holder.platformDotDrawable.setColor(platformDotColor(rom.platformId))
                    holder.boundPlatformId = rom.platformId
                }
            } else {
                if (holder.boundIsRom != false) {
                    if (holder.boundIsRom == true) {
                        releaseRomOverlay(holder.romOverlay)
                    }
                    holder.romArtFrame.visibility = View.GONE
                    holder.appIcon.visibility = View.VISIBLE
                    holder.platformBadge.visibility = View.GONE
                    holder.boundIsRom = false
                }
                CustomIcon.bind(
                    holder.appIcon,
                    iconLoader,
                    artCache,
                    settings,
                    entry.appPackage!!,
                    cardSize,
                    cornerRadiusPx = innerArtRadiusPx,
                )
            }
            holder.titleView.text = entry.label
            rebindCardMetaText(holder, entry)
            applySelectionVisuals(holder, position, animate = false)
            scheduleNeighborPrefetch(position)
        }

        private fun rebindCardMetaText(holder: CardHolder, entry: CarouselEntry) {
            val meta = SessionMath.cardMetaLine(
                settings.lastLaunchedMs[entry.key],
                settings.playtimeMs[entry.key] ?: 0L,
                System.currentTimeMillis(),
                favorite = entry.key in settings.favorites,
                inDock = DockSlots.containsKey(settings.dockSlots, entry.key),
            )
            holder.metaView.text = context.resolveText(meta)
        }

        /** Prefetch art for ±2 neighbors so flings hit memory/disk more often. */
        private fun scheduleNeighborPrefetch(center: Int) {
            val overrides = settings.artOverrides
            val epoch = entries.size
            for (delta in PREFETCH_DELTAS) {
                val i = center + delta
                if (i !in entries.indices) continue
                val rom = entries[i].rom ?: continue
                artCache.prefetch(
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
        val DETACH_ANIM_CANCEL = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                v.animate().cancel()
            }
        }
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
