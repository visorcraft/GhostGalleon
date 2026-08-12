package com.visorcraft.ghostgalleon

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.visorcraft.ghostgalleon.art.HttpSgdbTransport
import com.visorcraft.ghostgalleon.art.ScrapeJob
import com.visorcraft.ghostgalleon.art.SgdbScraper
import com.visorcraft.ghostgalleon.library.DrawerListCache
import com.visorcraft.ghostgalleon.library.DrawerListKey
import com.visorcraft.ghostgalleon.library.OpenSession
import com.visorcraft.ghostgalleon.library.PlayStats
import com.visorcraft.ghostgalleon.library.RaFetcher
import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.library.RaProgressGate
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.DeviceProfileCatalog
import com.visorcraft.ghostgalleon.display.DisplayTopology
import com.visorcraft.ghostgalleon.display.ResolvedTopology
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.rom.PlatformPackStore
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RemountPolicy
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLibrary
import com.visorcraft.ghostgalleon.rom.clearInstalledPackageCache
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.settings.DataMigrator
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SettingsStore
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.BaseDeckActivity
import com.visorcraft.ghostgalleon.ui.CompanionActivity
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.ui.DisplayRole
import com.visorcraft.ghostgalleon.ui.deck.PickerItem
import com.visorcraft.ghostgalleon.ui.deck.PickerItems
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.PackageManagerAppsSource
import android.hardware.display.DisplayManager
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class GhostGalleonApp : Application() {

    lateinit var deckState: DeckState
        private set

    lateinit var settings: Settings
        private set

    /**
     * Last resolved display topology (SINGLE/DUAL roles + launch id).
     * Refreshed via [refreshDisplayConfig]; safe default until first probe.
     */
    @Volatile
    var displayConfig: ResolvedTopology = ResolvedTopology(
        mode = SurfaceMode.SINGLE,
        primaryDisplayId = 0,
        companionDisplayId = null,
        launchDisplayId = 0,
        allIds = listOf(0),
        reason = "uninitialized",
    )
        private set

    private var lastDisplayRefreshUptimeMs: Long = 0L
    private var displayListenerRegistered = false

    val settingsStore: SettingsStore by lazy {
        SettingsStore(File(filesDir, "settings.json"))
    }

    /**
     * Probe displays, match profile, resolve topology, align DeckState.
     * Debounced when [debounce] is true (resume path).
     */
    fun refreshDisplayConfig(debounce: Boolean = false): ResolvedTopology {
        val now = android.os.SystemClock.uptimeMillis()
        if (debounce && now - lastDisplayRefreshUptimeMs < 500L) {
            return displayConfig
        }
        lastDisplayRefreshUptimeMs = now
        val readings = AndroidDisplayProbe.read(this)
        val profile = DeviceProfileCatalog.effective(settings.deviceProfileId, readings)
        val topo = DisplayTopology.resolve(
            readings = readings,
            profile = profile,
            interactiveDisplayMode = settings.interactiveDisplayMode,
            userPinnedPrimaryId = settings.userPinnedPrimaryId,
        )
        displayConfig = topo
        if (::deckState.isInitialized) {
            // Prefer pin/topology primary; only rewrite if invalid.
            if (settings.userPinnedPrimaryId != null &&
                settings.userPinnedPrimaryId in topo.allIds
            ) {
                deckState.setPrimaryDisplayId(settings.userPinnedPrimaryId!!)
            } else {
                deckState.ensurePrimaryIn(topo.allIds, topo.primaryDisplayId)
                if (deckState.primaryDisplayId != topo.primaryDisplayId &&
                    settings.userPinnedPrimaryId == null
                ) {
                    deckState.setPrimaryDisplayId(topo.primaryDisplayId)
                }
            }
        }
        return topo
    }

    /**
     * Topology-aware swap + sticky pin so Auto refresh does not undo it.
     * @return true when a dual-display swap occurred; false on single-display
     * (honest no-op — callers may toast).
     */
    fun swapInteractiveDisplay(): Boolean {
        val topo = refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) return false
        val companion = topo.allIds.firstOrNull { it != deckState.primaryDisplayId }
            ?: return false
        val current = ResolvedTopology(
            mode = SurfaceMode.DUAL,
            primaryDisplayId = deckState.primaryDisplayId,
            companionDisplayId = companion,
            launchDisplayId = companion,
            secondaryHomeDisplayId = topo.secondaryHomeDisplayId,
            largerDisplayId = topo.largerDisplayId,
            allIds = topo.allIds,
            reason = topo.reason,
        )
        val swapped = DisplayTopology.swap(current)
        val pin = DisplayTopology.pinAfterSwap(swapped)
        deckState.setPrimaryDisplayId(pin)
        settings = settings.copy(userPinnedPrimaryId = pin)
        scheduleSettingsSave(settings)
        displayConfig = swapped
        return true
    }

    private fun registerDisplayListener() {
        if (displayListenerRegistered) return
        displayListenerRegistered = true
        val dm = getSystemService(DisplayManager::class.java) ?: return
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                Handler(Looper.getMainLooper()).post { refreshDisplayConfig() }
            }
            override fun onDisplayRemoved(displayId: Int) {
                Handler(Looper.getMainLooper()).post { refreshDisplayConfig() }
            }
            override fun onDisplayChanged(displayId: Int) {
                Handler(Looper.getMainLooper()).post { refreshDisplayConfig(debounce = true) }
            }
        }, Handler(Looper.getMainLooper()))
    }

    val romLibrary: RomLibrary by lazy {
        RomLibrary(File(filesDir, "rom_library.json"))
    }

    val platformPackStore: PlatformPackStore by lazy {
        PlatformPackStore(File(filesDir, "platform_pack.json"))
    }

    val artCache: com.visorcraft.ghostgalleon.art.ArtCache by lazy {
        com.visorcraft.ghostgalleon.art.ArtCache(File(filesDir, "art"))
    }

    // App-scoped owner of the SteamGridDB batch scrape: a multi-thousand-ROM
    // job must survive the settings screen that started it. The executor and
    // cooperative cancel semantics stay in SgdbScraper; only the lifecycle
    // moved here. If the process dies the job dies with it - a re-run
    // resumes where cached art left off.
    val scrapeJob: ScrapeJob by lazy {
        ScrapeJob {
            SgdbScraper(
                artCache,
                HttpSgdbTransport(),
                skipMiss = { rom ->
                    val q = com.visorcraft.ghostgalleon.art.Sgdb.normalizeName(rom.name)
                    com.visorcraft.ghostgalleon.art.SgdbMissCache.shouldSkip(
                        sgdbMisses[rom.id],
                        q,
                        System.currentTimeMillis(),
                    )
                },
                onMiss = { id, query -> noteSgdbMiss(id, query) },
            )
        }
    }

    @Volatile
    private var sgdbMisses: Map<String, com.visorcraft.ghostgalleon.art.SgdbMissCache.Miss> =
        emptyMap()

    private fun noteSgdbMiss(romId: String, query: String) {
        sgdbMisses = com.visorcraft.ghostgalleon.art.SgdbMissCache.record(
            sgdbMisses, romId, query, System.currentTimeMillis(),
        )
        persistSgdbMisses()
    }

    private fun loadSgdbMissFile() {
        val file = File(filesDir, "sgdb_miss.json")
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val next = mutableMapOf<String, com.visorcraft.ghostgalleon.art.SgdbMissCache.Miss>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = root.optJSONObject(id) ?: continue
                val query = o.optString("query", "")
                val at = o.optLong("atMs", 0L)
                if (query.isNotBlank() && at > 0L) {
                    next[id] = com.visorcraft.ghostgalleon.art.SgdbMissCache.Miss(id, query, at)
                }
            }
            sgdbMisses = com.visorcraft.ghostgalleon.art.SgdbMissCache.prune(
                next,
                System.currentTimeMillis(),
            )
        }
    }

    private fun loadSgdbPicksFile() {
        val file = File(filesDir, "sgdb_picks.json")
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val gameId = root.optLong(id, 0L)
                if (gameId > 0L) com.visorcraft.ghostgalleon.art.Sgdb.pickedGameIds[id] = gameId
            }
        }
    }

    private fun loadBundledArcadeTitles() {
        runCatching {
            assets.open("arcade_titles.tsv.gz").use {
                com.visorcraft.ghostgalleon.rom.ArcadeTitles.loadBundledGzip(it)
            }
        }
    }

    private fun arcadeDatFile() = File(filesDir, "arcade_dat.json")

    private fun loadArcadeDatOverlay() {
        val file = arcadeDatFile()
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val map = mutableMapOf<String, String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val title = root.optString(id, "")
                if (title.isNotBlank()) map[id] = title
            }
            com.visorcraft.ghostgalleon.rom.ArcadeTitles.installOverlay(map)
        }
    }

    fun importArcadeDat(xml: String): Int {
        val parsed = com.visorcraft.ghostgalleon.rom.ArcadeDat.parse(xml)
        com.visorcraft.ghostgalleon.rom.ArcadeTitles.installOverlay(parsed)
        runCatching {
            val root = JSONObject()
            parsed.forEach { (k, v) -> root.put(k, v) }
            arcadeDatFile().writeText(root.toString())
        }
        rematchArcadeLibrary()
        return parsed.size
    }

    fun clearArcadeDat() {
        com.visorcraft.ghostgalleon.rom.ArcadeTitles.installOverlay(emptyMap())
        arcadeDatFile().delete()
        rematchArcadeLibrary()
    }

    private fun rematchArcadeLibrary() {
        val current = if (romEntries.isEmpty()) romLibrary.load() else romEntries
        val next = com.visorcraft.ghostgalleon.rom.ArcadeTitles.relabel(current)
        if (next !== current) {
            romLibrary.save(next)
            publishRomEntries(next)
        }
    }

    fun arcadeDatCount(): Int =
        com.visorcraft.ghostgalleon.rom.ArcadeTitles.overlayCount()

    fun maybeSealHomeWallpaper() {
        val flag = File(filesDir, "home_wallpaper_sealed")
        if (flag.isFile) return
        if (com.visorcraft.ghostgalleon.ui.deck.HomeWallpaper.seal(this)) {
            runCatching { flag.writeText("1") }
        }
    }

    fun persistSgdbPicks() {
        SETTINGS_IO.execute {
            runCatching {
                val root = JSONObject()
                com.visorcraft.ghostgalleon.art.Sgdb.pickedGameIds.forEach { (id, gameId) ->
                    root.put(id, gameId)
                }
                File(filesDir, "sgdb_picks.json").writeText(root.toString())
            }
        }
    }

    private fun persistSgdbMisses() {
        SETTINGS_IO.execute {
            runCatching {
                val root = JSONObject()
                sgdbMisses.forEach { (id, miss) ->
                    root.put(
                        id,
                        JSONObject().put("query", miss.query).put("atMs", miss.atMs),
                    )
                }
                File(filesDir, "sgdb_miss.json").writeText(root.toString())
            }
        }
    }

    @Volatile
    private var browseChipCacheKey: Int = 0
    @Volatile
    private var browseChipCache: com.visorcraft.ghostgalleon.library.LibraryBrowse.BrowseChipSnapshot? =
        null

    fun browseChipSnapshot(
        roms: List<RomEntry>,
        settings: Settings,
        nowMs: Long,
    ): com.visorcraft.ghostgalleon.library.LibraryBrowse.BrowseChipSnapshot {
        val key = contentEpoch xor
            roms.size xor
            settings.lastLaunchedMs.size xor
            settings.playtimeMs.size xor
            settings.favorites.size xor
            settings.hiddenRomIds.size xor
            (nowMs / 60_000L).toInt()
        browseChipCache?.let { if (browseChipCacheKey == key) return it }
        val snap = com.visorcraft.ghostgalleon.library.LibraryBrowse.browseChipSnapshot(
            roms = roms,
            lastLaunchedMs = settings.lastLaunchedMs,
            playtimeMs = settings.playtimeMs,
            hiddenRomIds = settings.hiddenRomIds,
            nowMs = nowMs,
            launchablePlatformIds = launchablePlatformIds(settings.browseChrome.launchableOnly),
        )
        browseChipCacheKey = key
        browseChipCache = snap
        return snap
    }

    fun invalidateBrowseChipCache() {
        browseChipCache = null
    }

    // In-memory snapshot of the persisted ROM index, read by every deck.
    // Loaded once off the UI thread at boot (a full card index is thousands
    // of entries - JSON parse must not block first render); rescans publish
    // fresh snapshots via publishRomEntries().
    @Volatile
    var romEntries: List<RomEntry> = emptyList()
        private set

    /** O(1) ROM lookup by id; rebuilt with [romEntries]. */
    @Volatile
    var romById: Map<String, RomEntry> = emptyMap()
        private set

    // Honest open session (pause while launcher focused / device asleep).
    // Exposed for Now Playing companion UI.
    @Volatile
    var openSession: OpenSession? = null
        private set

    // Optional RetroAchievements progress by ROM id (filled by network fetch).
    @Volatile
    private var raProgressByRomId: Map<String, com.visorcraft.ghostgalleon.library.RaProgress> =
        emptyMap()

    /** Cached RA progress for a ROM, or null when unknown / not fetched. */
    fun raProgressFor(romId: String): com.visorcraft.ghostgalleon.library.RaProgress? =
        raProgressByRomId[romId]

    fun putRaProgress(romId: String, progress: RaProgress) {
        val id = romId.trim()
        if (id.isEmpty()) return
        val prev = raProgressByRomId[id]
        // Pure gate: no SETTINGS full-rebuild notify (black-screen thrash).
        when (RaProgressGate.notifyAfterStore(prev, progress)) {
            RaProgressGate.NotifyKind.NONE -> return
            RaProgressGate.NotifyKind.SELECTION_ONLY -> {
                raProgressByRomId = raProgressByRomId + (id to progress)
                persistRaCache()
                Handler(Looper.getMainLooper()).post {
                    deckState.notifySelectionRefresh()
                }
            }
        }
    }

    /** Parse and store RA progress JSON for [romId]; empty/malformed clears. */
    fun setRaProgress(romId: String, json: String?) {
        val id = romId.trim()
        if (id.isEmpty()) return
        if (json.isNullOrBlank()) {
            if (id !in raProgressByRomId) return
            raProgressByRomId = raProgressByRomId - id
        } else {
            val parsed = RetroAchievements.parseProgress(json)
            val next = if (parsed.isEmpty) raProgressByRomId - id
            else raProgressByRomId + (id to parsed)
            if (next == raProgressByRomId) return
            raProgressByRomId = next
        }
        persistRaCache()
        Handler(Looper.getMainLooper()).post { deckState.notifySelectionRefresh() }
    }

    /** Load optional filesDir/ra_cache.json: `{ "romId": {…progress…}, … }`. */
    private fun loadRaCacheFile() {
        val file = File(filesDir, "ra_cache.json")
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val next = raProgressByRomId.toMutableMap()
            val keys = root.keys()
            while (keys.hasNext()) {
                val romId = keys.next()
                val value = root.opt(romId) ?: continue
                val json = when (value) {
                    is JSONObject -> value.toString()
                    is String -> value
                    else -> continue
                }
                val progress = RetroAchievements.parseProgress(json)
                if (!progress.isEmpty) next[romId] = progress
            }
            raProgressByRomId = next
        }
    }

    /** Persist in-memory RA map so process death keeps last-known progress. */
    private fun persistRaCache() {
        val snapshot = raProgressByRomId
        RA_IO.execute {
            runCatching {
                val root = JSONObject()
                snapshot.forEach { (romId, progress) ->
                    if (progress.isEmpty) return@forEach
                    root.put(
                        romId,
                        JSONObject()
                            .put("ID", progress.gameId ?: JSONObject.NULL)
                            .put("Title", progress.title ?: JSONObject.NULL)
                            .put("NumAwardedToUser", progress.numAwarded)
                            .put("NumAchievements", progress.numPossible)
                            .put("Score", progress.userScore ?: JSONObject.NULL)
                            .put("HardcoreMode", if (progress.hardcore) 1 else 0),
                    )
                }
                val file = File(filesDir, "ra_cache.json")
                val tmp = File(filesDir, "ra_cache.json.tmp")
                tmp.writeText(root.toString())
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    /**
     * Background RA fetch for [romId] when credentials are set. Uses cache
     * immediately; network updates overwrite + persist. Failures are silent.
     */
    fun requestRaProgress(romId: String, titleHint: String?, platformId: String? = null) {
        val user = settings.raUsername?.trim().orEmpty()
        val key = settings.raApiKey?.trim().orEmpty()
        if (!RaProgressGate.mayFetch(
                romId, user, key, raFetchInFlight, raFetchAttempted,
            )
        ) {
            return
        }
        val id = romId.trim()
        raFetchInFlight = raFetchInFlight + id
        raFetchAttempted = raFetchAttempted + id
        val cachedGameId = raProgressByRomId[id]?.gameId
        val platform = platformId
            ?: romById[id]?.platformId
        RA_IO.execute {
            val progress = try {
                RaFetcher.fetchProgress(
                    username = user,
                    apiKey = key,
                    gameId = cachedGameId,
                    titleHint = titleHint,
                    platformId = platform,
                )
            } catch (_: Exception) {
                RaProgress()
            }
            Handler(Looper.getMainLooper()).post {
                raFetchInFlight = raFetchInFlight - id
                if (!progress.isEmpty) putRaProgress(id, progress)
            }
        }
    }

    @Volatile
    private var raFetchInFlight: Set<String> = emptySet()
    @Volatile
    private var raFetchAttempted: Set<String> = emptySet()

    // --- Remount / quiet resume rescan ------------------------------------
    @Volatile
    var lastHadUnreadableTree: Boolean = false
        private set
    @Volatile
    var hadSuccessfulScan: Boolean = false
        private set
    @Volatile
    private var quietRescanInFlight: Boolean = false
    private var lastQuietRescanUptimeMs: Long = 0L

    /**
     * Note the outcome of any rescan (Settings or quiet resume) for the
     * remount policy. Call from the main-thread rescan callback.
     */
    fun noteRescanOutcome(result: RomLibrary.RescanResult) {
        when (result) {
            is RomLibrary.RescanResult.Success -> {
                hadSuccessfulScan = true
                lastHadUnreadableTree = RemountPolicy.nextHadUnreadableFlag(
                    allUnreadable = false,
                    retainedUnreadableTreeCount = result.retainedUnreadableTrees,
                )
            }
            RomLibrary.RescanResult.Unreadable -> {
                lastHadUnreadableTree = RemountPolicy.nextHadUnreadableFlag(
                    allUnreadable = true,
                )
            }
        }
    }

    /**
     * If remount policy says so, start a quiet incremental rescan (no toast
     * unless the library was empty and we recover entries). Debounced so
     * dual-display resume does not double-scan.
     */
    fun maybeQuietRescanOnResume(context: android.content.Context) {
        if (quietRescanInFlight) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastQuietRescanUptimeMs < 8_000L) return
        if (!RemountPolicy.shouldQuietRescanOnResume(
                grantedTreeCount = settings.romTreeUris.size,
                libraryEntryCount = romEntries.size,
                lastHadUnreadableTree = lastHadUnreadableTree,
                hadSuccessfulScan = hadSuccessfulScan,
            )
        ) {
            return
        }
        lastQuietRescanUptimeMs = now
        quietRescanInFlight = true
        val beforeCount = romEntries.size
        try {
            romLibrary.rescan(context.applicationContext, settings, force = false) { result ->
                quietRescanInFlight = false
                noteRescanOutcome(result)
                if (result is RomLibrary.RescanResult.Success) {
                    publishRomEntries(result.entries)
                    // Only toast when we recovered from empty after remount.
                    if (beforeCount == 0 && result.entries.isNotEmpty()) {
                        android.widget.Toast.makeText(
                            context.applicationContext,
                            context.resources.getQuantityString(
                                R.plurals.count_roms_restored,
                                result.entries.size,
                                result.entries.size,
                            ),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        } catch (_: Exception) {
            quietRescanInFlight = false
        }
    }

    private var sessionAwaitingReturn: Boolean = false
    private var liveDeckCount: Int = 0

    @Volatile
    var romIndexReady: Boolean = false
        private set

    private val romReadyWaiters = mutableListOf<() -> Unit>()

    fun whenRomIndexReady(block: () -> Unit) {
        if (romIndexReady) {
            block()
            return
        }
        synchronized(romReadyWaiters) { romReadyWaiters += block }
    }

    private fun markRomIndexReady() {
        romIndexReady = true
        val waiters = synchronized(romReadyWaiters) {
            val copy = romReadyWaiters.toList()
            romReadyWaiters.clear()
            copy
        }
        waiters.forEach { it.invoke() }
    }

    /** Load the persisted ROM index on this thread so first paint is honest. */
    private fun loadRomIndexBlocking() {
        val loaded = romLibrary.load()
        romEntries = loaded
        romById = loaded.associateBy { it.id }
        if (loaded.isNotEmpty()) hadSuccessfulScan = true
        markRomIndexReady()
    }

    private fun reloadRomEntries() {
        ROM_IO.execute {
            val loaded = romLibrary.load()
            val prev = romEntries
            romEntries = loaded
            romById = loaded.associateBy { it.id }
            Handler(Looper.getMainLooper()).post {
                if (loaded.isNotEmpty()) hadSuccessfulScan = true
                markRomIndexReady()
                if (prev.size != loaded.size || prev != loaded) {
                    contentEpoch++
                    invalidateDrawerListCache()
                    deckState.notifyChanged()
                }
            }
        }
    }

    // Bumped when settings or the ROM index change so decks can skip a full
    // rebuild on resume when nothing actually changed (HOME / SECONDARY_HOME
    // redelivery used to flash-rebuild every swipe).
    var contentEpoch: Int = 0
        private set

    // Shared across Main + Companion: one swipe delivers intents to both.
    @Volatile
    var lastDrawerRequestUptimeMs: Long = 0L

    // First-run setup overlay is primary-hosted; block deck input globally
    // while it is showing (keys may land on the companion activity).
    @Volatile
    var setupBlockingInput: Boolean = false

    /**
     * Grid → Game Mode library bridge: when true, next GameDeck primaryView
     * opens the search dialog once (then clears the flag).
     */
    @Volatile
    var pendingLibrarySearch: Boolean = false

    // All-apps drawer list reuse: avoid rebuilding thousands of PickerItems
    // on every swipe when contentEpoch + apps/hidden sets are unchanged.
    @Volatile
    private var drawerListKey: DrawerListKey? = null
    @Volatile
    private var drawerListItems: List<PickerItem>? = null

    /**
     * Cached empty-query drawer rows for [apps] + current [romEntries].
     * Rebuilds only when [DrawerListCache.key] changes.
     */
    fun drawerPickerItems(apps: List<AppEntry>): List<PickerItem> {
        val current = DrawerListCache.key(
            contentEpoch = contentEpoch,
            romCount = romEntries.size,
            hiddenPackages = settings.hiddenPackages,
            appPackageNames = apps.map { it.packageName },
            hiddenRomIds = settings.hiddenRomIds,
        )
        val cachedKey = drawerListKey
        val cachedItems = drawerListItems
        if (DrawerListCache.matches(cachedKey, current) && cachedItems != null) {
            return cachedItems
        }
        val built = PickerItems.build(
            apps, romEntries, "",
            hiddenRomIds = settings.hiddenRomIds,
        )
        drawerListKey = current
        drawerListItems = built
        return built
    }

    fun invalidateDrawerListCache() {
        drawerListKey = null
        drawerListItems = null
    }

    // A fresh scan result: swap the snapshot and rebuild the decks so the
    // picker/carousel/grid see the new entries immediately.
    fun publishRomEntries(entries: List<RomEntry>) {
        romEntries = entries
        romById = entries.associateBy { it.id }
        contentEpoch++
        invalidateDrawerListCache()
        deckState.notifyChanged()
    }

    /** O(1) ROM by id from the process snapshot (falls back to linear scan). */
    fun romEntry(id: String): RomEntry? =
        romById[id] ?: romEntries.firstOrNull { it.id == id }

    /** Stamp last-launched and open a play session for [key]. */
    fun noteLaunch(key: String, nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
        openSession = SessionTracker.onLaunch(key, nowMs)
        val stamped = SessionMath.recordLaunch(
            PlayStats(
                lastLaunchedMs = settings.lastLaunchedMs,
                totalPlaytimeMs = settings.playtimeMs,
            ),
            key,
            nowMs,
        )
        updateSettings(
            settings.copy(
                lastLaunchedMs = stamped.lastLaunchedMs,
                playtimeMs = stamped.totalPlaytimeMs,
                // New launch re-enables the companion Resume chip.
                hideResumeChip = false,
            ),
            notify = false,
        )
        // Now Playing: companion should rebuild when session opens.
        Handler(Looper.getMainLooper()).post { deckState.notifyChanged() }
    }

    /**
     * User swiped the companion Resume chip away. Does **not** wipe recents;
     * only hides Resume until the next [noteLaunch]. One SETTINGS notify so
     * the chip drops without [updateSettings]'s setMode / contentEpoch path
     * (that full thrash contributed to pure-black dual panels).
     */
    fun dismissResumeChip() {
        if (settings.hideResumeChip) return
        settings = settings.copy(hideResumeChip = true)
        scheduleSettingsSave(settings)
        // SELECTION in-place: CompanionPanel.updateSelection / full path
        // re-reads hideResumeChip; avoid dual setContentView for one chip.
        Handler(Looper.getMainLooper()).post { deckState.notifySelectionRefresh() }
    }

    /** Accrue honest active playtime when returning to a deck activity. */
    fun noteReturnToLauncher(nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
        // Playtime/session chrome can update in place; full rebuild was a
        // dual-panel thrash source when returning from games.
        Handler(Looper.getMainLooper()).post { deckState.notifySelectionRefresh() }
    }

    fun onSessionLauncherFocused(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onLauncherFocused(s, nowMs)
    }

    fun onSessionLauncherUnfocused(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onLauncherUnfocused(s, nowMs)
    }

    fun onSessionDeviceSleep(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onDeviceSleep(s, nowMs)
    }

    fun onSessionDeviceWake(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        openSession = SessionTracker.onDeviceWake(s, nowMs)
    }

    private fun endOpenSession(nowMs: Long) {
        val s = openSession ?: return
        openSession = null
        sessionAwaitingReturn = false
        val activeMs = SessionTracker.onReturn(s, nowMs)
        val stamped = SessionTracker.commitPlaytime(
            PlayStats(
                lastLaunchedMs = settings.lastLaunchedMs,
                totalPlaytimeMs = settings.playtimeMs,
            ),
            s.key,
            activeMs,
        )
        // Keep last-launched from noteLaunch; only merge playtime.
        updateSettings(
            settings.copy(
                lastLaunchedMs = settings.lastLaunchedMs,
                playtimeMs = stamped.totalPlaytimeMs,
            ),
            notify = false,
        )
    }

    // Live BaseDeckActivity instances, one per display task. The set is the
    // authority: an entry is removed in onActivityDestroyed before any
    // requestExitAll cascade runs, so reentrant finish() calls are no-ops.
    private val liveDeckActivities = mutableSetOf<BaseDeckActivity>()

    /**
     * Sole CompanionActivity allowed to paint / redirect. Claimed in
     * [CompanionActivity.onCreate] *before* lifecycle callbacks register the
     * instance — without this, SECONDARY_HOME MULTIPLE_TASK storms each miss
     * liveCompanions() and thrash the main thread (ANR + pure-black panels).
     */
    private val companionSeatLock = Any()
    @Volatile
    private var companionSeat: CompanionActivity? = null

    /**
     * @return true if [claimant] now holds the sole companion seat.
     * False → caller must absorb (finish without paint).
     */
    fun tryClaimCompanionSeat(claimant: CompanionActivity): Boolean {
        synchronized(companionSeatLock) {
            val cur = companionSeat
            if (cur === claimant) return true
            if (cur != null && !cur.isFinishing && !cur.isDestroyed) return false
            companionSeat = claimant
            return true
        }
    }

    fun releaseCompanionSeat(claimant: CompanionActivity) {
        synchronized(companionSeatLock) {
            if (companionSeat === claimant) companionSeat = null
        }
    }

    /** Non-finishing seat holder, if any (may not yet be STARTED). */
    fun companionSeatHolder(): CompanionActivity? {
        val cur = companionSeat
        return if (cur != null && !cur.isFinishing && !cur.isDestroyed) cur else null
    }

    override fun onCreate() {
        super.onCreate()
        // Package-rename bridge: BlackPearl update with EXPORT_MIGRATE_ON_BOOT
        // dumps private data to external files; Ghost Galleon then imports
        // from migrate-import/ before the first settings load.
        if (BuildConfig.EXPORT_MIGRATE_ON_BOOT) {
            runCatching { DataMigrator.exportToExternal(this) }
        } else {
            runCatching { DataMigrator.tryImportFromExternal(this) }
        }
        settings = settingsStore.load()
        // Install any persisted platform pack before ROM scans / launches.
        runCatching {
            if (platformPackStore.loadIntoRegistry()) {
                launchablePlatformIdsCache = null
                clearInstalledPackageCache()
            }
        }
        loadRaCacheFile()
        loadSgdbMissFile()
        loadSgdbPicksFile()
        loadBundledArcadeTitles()
        loadArcadeDatOverlay()
        deckState = DeckState()
        deckState.setMode(settings.defaultMode)
        // Topology-driven primary (secondary prefer on Sugar Auto); not raw primaryDisplay.
        refreshDisplayConfig()
        registerDisplayListener()
        // Disk index before any deck paints or cold-start seed.
        loadRomIndexBlocking()
        // Cold-start hero seed: prefer Continue key when known, else slot 0.
        // Do not auto-launch — selection only so the companion shows the game.
        seedColdStartSelection()
        // PM query off the UI thread before decks paint.
        prewarmAppLibrary()
        registerPackageChangeReceiver()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is BaseDeckActivity) liveDeckActivities.add(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity is BaseDeckActivity) liveDeckActivities.remove(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                if (activity is BaseDeckActivity) {
                    liveDeckCount++
                    if (liveDeckCount == 1 && openSession != null) {
                        // Returning to launcher after all decks were stopped.
                        sessionAwaitingReturn = true
                    }
                }
            }
            override fun onActivityResumed(activity: Activity) {
                if (activity is BaseDeckActivity) {
                    onSessionLauncherFocused()
                    if (sessionAwaitingReturn && openSession != null) {
                        // Still in session but launcher is focused again:
                        // keep session open for Now Playing; only end when
                        // the user starts a new launch or we explicitly clear.
                        // Honest pause already applied via onSessionLauncherFocused.
                        sessionAwaitingReturn = false
                    }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                if (activity is BaseDeckActivity) {
                    liveDeckCount = (liveDeckCount - 1).coerceAtLeast(0)
                    if (liveDeckCount == 0 && openSession != null) {
                        onSessionLauncherUnfocused()
                        sessionAwaitingReturn = true
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
        // Honest sleep/wake: pair SCREEN_OFF with SCREEN_ON so pausedForSleep
        // cannot stick forever (TRIM_MEMORY_UI_HIDDEN is NOT screen-off).
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> onSessionDeviceSleep()
                    Intent.ACTION_SCREEN_ON -> onSessionDeviceWake()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    /** End the open session and commit playtime (e.g. user dismissed Now Playing). */
    fun clearOpenSession(nowMs: Long = System.currentTimeMillis()) {
        endOpenSession(nowMs)
        deckState.notifyChanged()
    }

    /**
     * Cold-start hero selection: first filled grid slot (curated home order),
     * not last-launched. Continue stays an explicit user action (chip / Quick
     * Panel). Never launches.
     */
    private fun seedColdStartSelection() {
        val key = com.visorcraft.ghostgalleon.library.LibraryBrowse.coldStartKey(
            gridSlots = settings.gridSlots,
            dockSlots = settings.dockSlots,
            lastLaunchedMs = settings.lastLaunchedMs,
        ) ?: return
        val idx = settings.gridSlots.indexOf(key)
        if (idx >= 0) deckState.selectSlot(idx, key)
        else deckState.select(key)
    }

    /** The currently live CompanionActivity, if any. */
    fun liveCompanion(): CompanionActivity? = liveCompanions().firstOrNull()

    /** All live CompanionActivity instances, oldest first. The ROM's
     *  SECONDARY_HOME starts can spawn duplicates despite singleInstance. */
    fun liveCompanions(): List<CompanionActivity> =
        liveDeckActivities.filterIsInstance<CompanionActivity>()

    /** Finish every other live deck activity; called when one deck exits. */
    fun requestExitAll(except: BaseDeckActivity) {
        liveDeckActivities.filter { it !== except && !it.isFinishing }
            .forEach { it.finish() }
    }

    /**
     * @param notify when true, notifies decks. [chromeOnly] uses
     * [DeckState.notifyChromeRefresh] (in-place rebind) instead of a full
     * SETTINGS dual rebuild — for browse chrome, card size, accent-only.
     */
    fun updateSettings(s: Settings, notify: Boolean = true, chromeOnly: Boolean = false) {
        val displayPolicyChanged =
            s.deviceProfileId != settings.deviceProfileId ||
                s.interactiveDisplayMode != settings.interactiveDisplayMode ||
                s.orientationMode != settings.orientationMode ||
                s.userPinnedPrimaryId != settings.userPinnedPrimaryId
        val modeChanged = s.defaultMode != settings.defaultMode
        val drawerRelevant = drawerRelevantSettingsChanged(settings, s)
        settings = s
        // Debounced async persist — every favorite/dock/launch used to
        // block the main thread on pretty-printed JSON IO.
        scheduleSettingsSave(s)
        // contentEpoch + drawer cache only when app/ROM listing inputs change
        // (not on every chrome-only or favorite-adjacent write that doesn't
        // affect the all-apps drawer rows).
        if (drawerRelevant || (!chromeOnly && notify)) {
            contentEpoch++
            if (drawerRelevant) invalidateDrawerListCache()
            invalidateBrowseChipCache()
        }
        if (displayPolicyChanged) refreshDisplayConfig()
        if (notify) {
            if (chromeOnly && !modeChanged && !displayPolicyChanged) {
                deckState.notifyChromeRefresh()
            } else {
                deckState.setMode(s.defaultMode)
                deckState.notifyChanged()
            }
        }
    }

    /**
     * Fields that change all-apps drawer row membership or labels.
     * Pure relative compare — avoids thrashing DrawerListCache on chrome.
     */
    private fun drawerRelevantSettingsChanged(prev: Settings, next: Settings): Boolean =
        prev.hiddenPackages != next.hiddenPackages ||
            prev.hiddenRomIds != next.hiddenRomIds ||
            prev.customNames != next.customNames ||
            prev.romNames != next.romNames ||
            prev.customIcons != next.customIcons ||
            prev.romTreeUris != next.romTreeUris

    /**
     * Shared installed-app catalog (PM query is expensive). Pre-warmed on a
     * background thread at process start; decks reuse this instance so the
     * first paint rarely blocks on queryIntentActivities.
     */
    fun appLibrary(): AppLibrary {
        sharedAppLibrary.get()?.let { return it }
        // Race: prewarm not finished — build once under lock and publish.
        synchronized(appLibraryLock) {
            sharedAppLibrary.get()?.let { return it }
            val lib = AppLibrary(PackageManagerAppsSource(packageManager, packageName))
            lib.warm()
            sharedAppLibrary.set(lib)
            return lib
        }
    }

    /** Drop app cache after install/uninstall (next [appLibrary] re-queries). */
    fun invalidateAppLibrary() {
        sharedAppLibrary.set(null)
        com.visorcraft.ghostgalleon.rom.clearInstalledPackageCache()
        launchablePlatformIdsCache = null
        invalidateDrawerListCache()
    }

    /**
     * Cached result of launchable-only platform filter (package installs).
     * Cleared on [invalidateAppLibrary] / pack overlay change.
     */
    @Volatile
    private var launchablePlatformIdsCache: Set<String>? = null

    /** Platforms with at least one installed player; null when filter off. */
    fun launchablePlatformIds(launchableOnly: Boolean): Set<String>? {
        if (!launchableOnly) return null
        launchablePlatformIdsCache?.let { return it }
        val byPlatform = playerPackagesByPlatform()
        val installed = byPlatform.values.flatten()
            .filter { packageManager.isInstalled(it) }
            .toSet()
        val ids = com.visorcraft.ghostgalleon.library.LibraryBrowse.launchablePlatformIds(
            byPlatform,
            installed,
        )
        launchablePlatformIdsCache = ids
        return ids
    }

    private fun playerPackagesByPlatform(): Map<String, List<String>> {
        // Rebuild when pack overlay may have changed (cheap vs PM queries).
        return Platforms.ALL.associate { platform ->
            platform.id to platform.players.map {
                com.visorcraft.ghostgalleon.rom.PlayerResolver.packageName(it)
            }
        }
    }

    /** Flush any debounced settings write (call from activity onPause). */
    fun flushSettingsNow() {
        mainHandler.removeCallbacks(persistSettingsRunnable)
        // Nothing dirty: skip a full JSON rewrite on every pause / launch.
        val snapshot = pendingSettingsSave.getAndSet(null) ?: return
        // Sync on caller thread when leaving foreground — process may die.
        runCatching { settingsStore.save(snapshot) }
    }

    private fun scheduleSettingsSave(s: Settings) {
        pendingSettingsSave.set(s)
        mainHandler.removeCallbacks(persistSettingsRunnable)
        mainHandler.postDelayed(persistSettingsRunnable, SETTINGS_SAVE_DEBOUNCE_MS)
    }

    private val persistSettingsRunnable = Runnable {
        val snapshot = pendingSettingsSave.getAndSet(null) ?: return@Runnable
        SETTINGS_IO.execute {
            runCatching { settingsStore.save(snapshot) }
        }
    }

    /** Interactive (PRIMARY-role) deck activity, if any is live. */
    fun primaryDeckActivity(): BaseDeckActivity? =
        liveDeckActivities.firstOrNull { activity ->
            !activity.isFinishing &&
                DisplayRole.roleFor(
                    activity.currentDisplayId() ?: -1,
                    deckState,
                ) == DisplayRole.PRIMARY
        }

    /** All live deck activities (Main + Companion). */
    fun liveDeckActivities(): List<BaseDeckActivity> =
        liveDeckActivities.filter { !it.isFinishing }

    private fun prewarmAppLibrary() {
        APP_IO.execute {
            runCatching {
                val lib = AppLibrary(PackageManagerAppsSource(packageManager, packageName))
                lib.warm()
                sharedAppLibrary.compareAndSet(null, lib)
            }
        }
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                invalidateAppLibrary()
                prewarmAppLibrary()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private val sharedAppLibrary = AtomicReference<AppLibrary?>(null)
    private val appLibraryLock = Any()
    private val pendingSettingsSave = AtomicReference<Settings?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    private companion object {
        val ROM_IO = Executors.newSingleThreadExecutor()
        val RA_IO = Executors.newSingleThreadExecutor()
        val APP_IO = Executors.newSingleThreadExecutor()
        val SETTINGS_IO = Executors.newSingleThreadExecutor()
        // Slightly longer debounce: bulk multi-select / favorite storms
        // coalesce into fewer disk writes without feeling laggy on pause flush.
        const val SETTINGS_SAVE_DEBOUNCE_MS = 180L
    }
}
