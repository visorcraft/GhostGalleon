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
import com.visorcraft.ghostgalleon.library.RaTheater
import com.visorcraft.ghostgalleon.library.RaTheaterSnap
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.DeviceProfileCatalog
import com.visorcraft.ghostgalleon.display.DisplayTopology
import com.visorcraft.ghostgalleon.display.ResolvedTopology
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.input.InputAssistPolicy
import com.visorcraft.ghostgalleon.input.InputAssistService
import com.visorcraft.ghostgalleon.rom.CinemaFrame
import com.visorcraft.ghostgalleon.rom.LensCatalog
import com.visorcraft.ghostgalleon.rom.LensSpec
import com.visorcraft.ghostgalleon.rom.PlatformPackStore
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RaCommandClient
import com.visorcraft.ghostgalleon.rom.RaUdpTransport
import com.visorcraft.ghostgalleon.rom.RemountPolicy
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLibrary
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.SessionRing
import com.visorcraft.ghostgalleon.rom.SessionRingEntry
import com.visorcraft.ghostgalleon.rom.SessionSurface
import com.visorcraft.ghostgalleon.rom.clearInstalledPackageCache
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.settings.DataMigrator
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SettingsStore
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.BaseDeckActivity
import com.visorcraft.ghostgalleon.ui.CompanionActivity
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.ui.DisplayRole
import com.visorcraft.ghostgalleon.ui.DualPaintPolicy
import com.visorcraft.ghostgalleon.ui.HostSurface
import com.visorcraft.ghostgalleon.ui.PlayHostPolicy
import com.visorcraft.ghostgalleon.ui.deck.PickerItem
import com.visorcraft.ghostgalleon.ui.deck.PickerItems
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.PackageManagerAppsSource
import android.hardware.display.DisplayManager
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.visorcraft.ghostgalleon.rom.ArcadeTitles
import com.visorcraft.ghostgalleon.rom.RomIdentities
import com.visorcraft.ghostgalleon.rom.RomIdentity
import com.visorcraft.ghostgalleon.rom.RomIdentityStore
import com.visorcraft.ghostgalleon.rom.VitaSfo
import com.visorcraft.ghostgalleon.rom.VitaTitles
import com.visorcraft.ghostgalleon.rom.VitaVpk
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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

    val romIdentityStore: RomIdentityStore by lazy {
        RomIdentityStore(File(filesDir, "rom_identity.json"))
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
        rematchArcadeLibrary(onlyFallbackNames = false)
        return parsed.size
    }

    fun clearArcadeDat() {
        com.visorcraft.ghostgalleon.rom.ArcadeTitles.installOverlay(emptyMap())
        arcadeDatFile().delete()
        rematchArcadeLibrary(onlyFallbackNames = false)
    }

    private fun rematchArcadeLibrary(onlyFallbackNames: Boolean = false) {
        val current = if (romEntries.isEmpty()) romLibrary.load() else romEntries
        val next = com.visorcraft.ghostgalleon.rom.ArcadeTitles.relabel(
            current,
            onlyFallbackNames = onlyFallbackNames,
        )
        if (next !== current) {
            romLibrary.save(next)
            publishRomEntries(next)
        }
    }

    /** Catalog parse + rematch after first paint. Publish only if names move. */
    private fun rematchArcadeLibraryOffPaint() {
        val current = romEntries
        val next = com.visorcraft.ghostgalleon.rom.ArcadeTitles.relabel(
            current,
            onlyFallbackNames = true,
        )
        if (next === current) return
        romLibrary.save(next)
        mainHandler.post {
            if (romEntries === current) publishRomEntries(next)
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

    /**
     * Content fingerprints beside [romEntries]. Filled on [ROM_IO] after
     * the path index is ready; never blocks first paint.
     */
    @Volatile
    var romIdentities: Map<String, RomIdentity> = emptyMap()
        private set

    // Honest open session (pause while launcher focused / device asleep).
    // Exposed for Now Playing companion UI.
    @Volatile
    var openSession: OpenSession? = null
        private set

    // Process-only session surface beside playtime. Policy/player record;
    // OpenSession stays playtime-only.
    @Volatile
    var sessionSurface: SessionSurface? = null
        private set

    // Process-only KEEP play HUD chrome. Expanded shows the actions row.
    var playHudExpanded: Boolean = true

    // Process-only RAM lens catalog (bundled assets + optional SAF pack).
    @Volatile
    var lenses: List<LensSpec> = emptyList()
        private set

    // Process-only: lenses that failed 3 times this process (no Settings write).
    @Volatile
    var lensDisabledThisProcess: Set<String> = emptySet()
        private set

    private val lensFailCounts = HashMap<String, Int>()

    /** True once [id] reaches 3 consecutive failures this process. */
    fun noteLensFailure(id: String): Boolean {
        if (id.isEmpty() || id in lensDisabledThisProcess) return true
        val n = (lensFailCounts[id] ?: 0) + 1
        lensFailCounts[id] = n
        if (n < 3) return false
        lensDisabledThisProcess = lensDisabledThisProcess + id
        return true
    }

    fun noteLensSuccess(id: String) {
        if (id.isEmpty()) return
        lensFailCounts.remove(id)
    }

    /**
     * Load bundled assets/lenses JSON plus optional [Settings.ramLensPackUri]
     * on a background thread. Invalid JSON is ignored. Does not notify decks.
     */
    fun reloadLenses() {
        if (!LensCatalog.shouldLoad(settings.ramLensesEnabled, settings.ramLensPackUri)) {
            lenses = emptyList()
            return
        }
        ROM_IO.execute {
            val loaded = ArrayList<LensSpec>()
            val names = runCatching {
                assets.list("lenses")?.filter { it.endsWith(".json", ignoreCase = true) }
            }.getOrNull().orEmpty()
            for (name in names) {
                val text = runCatching {
                    assets.open("lenses/$name").bufferedReader().use { it.readText() }
                }.getOrNull() ?: continue
                loaded.addAll(LensCatalog.parse(text))
            }
            val packUri = settings.ramLensPackUri
            if (!packUri.isNullOrBlank()) {
                val text = runCatching {
                    contentResolver.openInputStream(Uri.parse(packUri))
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()
                if (text != null) loaded.addAll(LensCatalog.parse(text))
            }
            lenses = loaded
        }
    }

    // Process-only pad owner flip (GAME ↔ HOST). Not persisted.
    var hostClaimed: Boolean = false
        private set

    // Process-only play-host chrome. Not persisted.
    var hostSurface: HostSurface = HostSurface.HUD

    // Process-only cinema ring. Not persisted.
    var cinemaFrames: List<CinemaFrame> = emptyList()
    var cinemaLastSlot: Int? = null
    var cinemaLastCaptureMs: Long = 0L
    var cinemaPinnedSlot: Int? = null

    // Process-only achievement theater. Not persisted.
    var theaterSnap: RaTheaterSnap? = null
    var theaterLastPollMs: Long = 0L
    var theaterAttempted: Set<String> = emptySet()
    var theaterRomId: String? = null
    var theaterTickerTitle: String? = null
    var theaterTickerId: Int? = null
    var theaterTickerUntilMs: Long = 0L

    fun claimHost() {
        hostClaimed = true
    }

    fun releaseHost() {
        hostClaimed = false
    }

    // True while optional InputAssistService is bound by the system.
    @Volatile
    var inputAssistConnected: Boolean = false

    // Bound assist instance for launch-display pointer inject (null when unbound).
    @Volatile
    var inputAssistService: InputAssistService? = null

    /**
     * Absolute pointer on the session launch display via assist gestures.
     * No-ops unless assist is connected, [InputAssistPolicy.mayInjectPointer]
     * is true, display-targeted gestures exist, and the session does not own
     * the companion display. Never targets the play-host display.
     */
    fun injectLaunchPointer(normX: Float, normY: Float, down: Boolean) {
        if (!InputAssistService.supportsDisplayGesture()) return
        val service = inputAssistService ?: return
        if (!inputAssistConnected) return
        val surface = sessionSurface ?: return
        val sessionOwns = DualPaintPolicy.sessionOwnsCompanionDisplay(
            surface.policy,
            surface.greedy,
        )
        if (sessionOwns) return
        val launchId = surface.launchDisplayId ?: return
        val dual = displayConfig.mode == SurfaceMode.DUAL
        val hostId = displayConfig.allIds.firstOrNull { it != launchId }
        val allowed = PlayHostPolicy.playHostAllowed(
            dualMode = dual,
            policy = surface.policy,
            greedy = surface.greedy,
            hostDisplayId = hostId,
            launchDisplayId = launchId,
        )
        if (!InputAssistPolicy.mayInjectPointer(
                assistConnected = true,
                playHostAllowed = allowed,
                sessionOwnsCompanion = false,
                playerId = surface.playerId,
            )
        ) return
        service.injectOnLaunchDisplay(normX, normY, down, launchId)
    }

    // Process-only RetroArch UDP client. Transport stays out of RaCommand.kt.
    @Volatile
    var raCommandClient: RaCommandClient? = null
        private set
    private val raUdpOutstanding = AtomicBoolean(false)
    private val raUdpWorker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ra-udp").apply { isDaemon = true }
    }

    fun ensureRaCommandClient(): RaCommandClient {
        raCommandClient?.let { return it }
        synchronized(this) {
            raCommandClient?.let { return it }
            return RaCommandClient(RaUdpTransport()) {
                android.os.SystemClock.elapsedRealtime()
            }.also { raCommandClient = it }
        }
    }

    /**
     * Run RetroArch UDP on the dedicated worker. Drops if a datagram is
     * already in flight so Companion never queues overlapping bind/receive.
     * [onMain] always posts; it must only mutate views.
     */
    fun enqueueRaUdp(work: (RaCommandClient) -> Unit, onMain: () -> Unit): Boolean {
        if (!raUdpOutstanding.compareAndSet(false, true)) return false
        val client = ensureRaCommandClient()
        raUdpWorker.execute {
            try {
                work(client)
            } finally {
                mainHandler.post {
                    try {
                        onMain()
                    } finally {
                        raUdpOutstanding.set(false)
                    }
                }
            }
        }
        return true
    }

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
        startRaProgressFetch(romId.trim(), user, key, titleHint, platformId, theater = false)
    }

    /**
     * KEEP theater poll. One HTTP attempt per romId per process until
     * [RaTheater.pollDue]. Shares the RA in-flight set; does not toast.
     */
    fun requestTheaterPoll(romId: String, titleHint: String?, platformId: String? = null) {
        val user = settings.raUsername?.trim().orEmpty()
        val key = settings.raApiKey?.trim().orEmpty()
        if (user.isEmpty() || key.isEmpty()) return
        val id = romId.trim()
        if (id.isEmpty()) return
        if (id in raFetchInFlight) return
        val now = android.os.SystemClock.elapsedRealtime()
        val last = if (theaterRomId == id) theaterLastPollMs else 0L
        val due = RaTheater.pollDue(last, now, settings.raTheaterPollMs.toLong())
        if (id in theaterAttempted && !due) return
        if (!due) return
        startRaProgressFetch(id, user, key, titleHint, platformId, theater = true)
    }

    fun theaterSnapFor(romId: String): RaTheaterSnap? =
        theaterSnap.takeIf { theaterRomId == romId }

    fun theaterBadgeKey(badgeName: String): String = "ra-badge-$badgeName"

    private fun startRaProgressFetch(
        id: String,
        user: String,
        key: String,
        titleHint: String?,
        platformId: String?,
        theater: Boolean,
    ) {
        raFetchInFlight = raFetchInFlight + id
        raFetchAttempted = raFetchAttempted + id
        if (theater) theaterAttempted = theaterAttempted + id
        val cachedGameId = raProgressByRomId[id]?.gameId
            ?: theaterSnap?.takeIf { theaterRomId == id }?.progress?.gameId
        val platform = platformId ?: romById[id]?.platformId
        RA_IO.execute {
            val body = try {
                RaFetcher.fetchProgressJson(
                    username = user,
                    apiKey = key,
                    gameId = cachedGameId,
                    titleHint = titleHint,
                    platformId = platform,
                )
            } catch (_: Exception) {
                null
            }
            val snap = RaTheater.parse(body)
            Handler(Looper.getMainLooper()).post {
                raFetchInFlight = raFetchInFlight - id
                val stamp = android.os.SystemClock.elapsedRealtime()
                if (theater) {
                    applyTheaterSnap(id, snap, stamp, hadBody = body != null)
                } else if (body != null && sessionRomId() == id) {
                    applyTheaterSnap(id, snap, stamp, hadBody = true)
                }
                if (!snap.progress.isEmpty) putRaProgress(id, snap.progress)
            }
        }
    }

    private fun applyTheaterSnap(
        romId: String,
        snap: RaTheaterSnap,
        nowMs: Long,
        hadBody: Boolean,
    ) {
        if (theaterRomId != romId) {
            theaterRomId = romId
            theaterSnap = null
            theaterTickerTitle = null
            theaterTickerId = null
            theaterTickerUntilMs = 0L
        }
        theaterLastPollMs = nowMs
        theaterAttempted = theaterAttempted + romId
        if (!hadBody) return
        if (snap.progress.isEmpty && snap.nextLocked == null && snap.unlockedIds.isEmpty()) {
            return
        }
        val prev = theaterSnap
        if (prev != null &&
            prev.unlockedIds == snap.unlockedIds &&
            prev.nextLocked?.id == snap.nextLocked?.id &&
            prev.lastUnlock?.id == snap.lastUnlock?.id &&
            RaProgressGate.isSameProgress(prev.progress, snap.progress)
        ) {
            return
        }
        if (prev != null) {
            val newly = RaTheater.newlyUnlocked(prev.unlockedIds, snap.unlockedIds)
            if (newly.isNotEmpty()) {
                val unlockId = newly.first()
                theaterTickerId = unlockId
                theaterTickerTitle = snap.items.firstOrNull { it.id == unlockId }?.title
                    ?: snap.lastUnlock?.title
                theaterTickerUntilMs = nowMs + THEATER_TICKER_MS
            }
        }
        theaterSnap = snap
        prefetchTheaterBadges(snap)
    }

    private fun sessionRomId(): String? =
        sessionSurface?.key?.let { SlotKey.romId(it) }

    private fun prefetchTheaterBadges(snap: RaTheaterSnap) {
        val names = linkedSetOf<String>()
        snap.nextLocked?.badgeName?.let(names::add)
        snap.lastUnlock?.badgeName?.let(names::add)
        for (name in names) {
            val key = theaterBadgeKey(name)
            if (artCache.diskHas(key)) continue
            RA_IO.execute {
                val bytes = fetchTheaterBadgeBytes(name) ?: return@execute
                if (bytes.isNotEmpty()) artCache.writeDiskBytes(key, bytes)
            }
        }
    }

    private fun fetchTheaterBadgeBytes(badgeName: String): ByteArray? {
        return try {
            val url = java.net.URL(
                "https://media.retroachievements.org/Badge/$badgeName.png",
            )
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
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
        val current = DrawerListKey(
            contentEpoch = contentEpoch,
            romCount = romEntries.size,
            hiddenFingerprint = DrawerListCache.stableHash(settings.hiddenPackages),
            appsFingerprint = DrawerListCache.appsFingerprint(apps),
            hiddenRomsFingerprint = DrawerListCache.stableHash(settings.hiddenRomIds),
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
        scheduleIdentityRefresh(entries)
    }

    /**
     * Compute missing [RomIdentity] rows on [ROM_IO], persist the sidecar,
     * then post one identity map + selection refresh on the main thread.
     * Ready rows are kept; failures stay `ready=false` without crashing.
     */
    private fun scheduleIdentityRefresh(entries: List<RomEntry> = romEntries) {
        val snapshot = entries
        ROM_IO.execute { refreshIdentities(snapshot) }
    }

    private fun refreshIdentities(entries: List<RomEntry>) {
        // Prefer the live map so a quiet rescan does not re-read the sidecar.
        val prior = romIdentities.ifEmpty {
            try {
                romIdentityStore.load()
            } catch (_: Exception) {
                emptyMap()
            }
        }
        val next = LinkedHashMap<String, RomIdentity>(entries.size.coerceAtLeast(prior.size))
        var ready = 0
        var fail = 0
        var newlyComputed = 0
        for (entry in entries) {
            val kept = prior[entry.id]
            if (kept != null && kept.ready) {
                next[entry.id] = kept
                ready++
                continue
            }
            newlyComputed++
            val computed = try {
                computeIdentity(entry)
            } catch (_: Exception) {
                notReady(entry)
            }
            next[entry.id] = computed
            if (computed.ready) ready++ else fail++
        }
        val quiet = RomIdentities.sidecarQuiet(prior.keys, next.keys, newlyComputed)
        if (!quiet) {
            try {
                romIdentityStore.save(next)
            } catch (_: Exception) {
                // Keep in-memory map even if disk write fails.
            }
            Log.i(IDENT_TAG, "ready=$ready fail=$fail")
        }
        mainHandler.post {
            val firstPaint = romIdentities.isEmpty() && next.isNotEmpty()
            romIdentities = next
            if (!quiet || firstPaint) {
                deckState.notifySelectionRefresh()
            }
        }
    }

    private fun notReady(entry: RomEntry, algo: String = RomIdentities.ALGO_SHA1_PAYLOAD): RomIdentity =
        RomIdentity(
            romId = entry.id,
            algo = algo,
            hash = null,
            headerTitle = null,
            groupId = null,
            discIndex = null,
            ready = false,
        )

    private fun computeIdentity(entry: RomEntry): RomIdentity {
        val size = romByteSize(entry)
        // Unknown SAF length must never chooseAlgo(0) → full-stream sha1 buffer.
        val algo = if (size == null || size < 0L) {
            when (entry.platformId) {
                "psvita", "vita" -> RomIdentities.ALGO_SFO_TITLE
                "arcade" -> RomIdentities.ALGO_DAT_CRC
                else -> RomIdentities.ALGO_SHA256_SAMPLE
            }
        } else {
            RomIdentities.chooseAlgo(size, entry.platformId)
        }
        return when (algo) {
            RomIdentities.ALGO_SFO_TITLE -> computeSfoTitle(entry)
            RomIdentities.ALGO_DAT_CRC -> computeDatCrc(entry)
            RomIdentities.ALGO_SHA256_SAMPLE -> computeSample(entry, size)
            else -> computeSha1Payload(entry)
        }
    }

    private fun computeSfoTitle(entry: RomEntry): RomIdentity {
        val algo = RomIdentities.ALGO_SFO_TITLE
        val fromId = entry.id.removePrefix("psvita:").substringBefore('/')
        if (VitaTitles.isTitleId(fromId)) {
            val id = fromId.uppercase()
            return RomIdentity(
                romId = entry.id,
                algo = algo,
                hash = id,
                headerTitle = entry.name,
                groupId = id,
                discIndex = null,
                ready = true,
            )
        }
        val info = readVitaSfo(entry) ?: return notReady(entry, algo)
        val titleId = info.titleId?.takeIf { it.isNotBlank() }
            ?: return notReady(entry, algo)
        return RomIdentity(
            romId = entry.id,
            algo = algo,
            hash = titleId,
            headerTitle = info.title ?: entry.name,
            groupId = titleId,
            discIndex = null,
            ready = true,
        )
    }

    private fun readVitaSfo(entry: RomEntry): VitaSfo.Info? {
        val name = entry.uri.substringAfterLast('/').substringBefore('?')
            .lowercase()
        val pathName = entry.path?.substringAfterLast('/')?.lowercase().orEmpty()
        val fileName = if (name.isNotEmpty()) name else pathName
        return runCatching {
            openRomStream(entry)?.use { stream ->
                when {
                    fileName.endsWith(".vpk") || fileName.endsWith(".zip") ->
                        VitaVpk.paramSfo(stream)?.let { VitaSfo.parse(it) }
                    fileName.equals("param.sfo", ignoreCase = true) ||
                        fileName.endsWith("param.sfo") ->
                        VitaSfo.parse(stream.readBytes())
                    else -> {
                        // Eboot / folder dumps: try whole stream as SFO, else VPK.
                        val bytes = stream.readBytes()
                        VitaSfo.parse(bytes)
                            ?: bytes.inputStream().use { VitaVpk.paramSfo(it)?.let { b -> VitaSfo.parse(b) } }
                    }
                }
            }
        }.getOrNull()
    }

    private fun computeDatCrc(entry: RomEntry): RomIdentity {
        val algo = RomIdentities.ALGO_DAT_CRC
        val stem = ArcadeTitles.stemOf(entry).trim().lowercase()
        if (stem.isEmpty()) return notReady(entry, algo)
        // Zip short-name is the DAT key; CRC is not stored in our DAT parse.
        val title = ArcadeTitles.displayName(stem)
        return RomIdentity(
            romId = entry.id,
            algo = algo,
            hash = stem,
            headerTitle = title.takeIf { !it.equals(stem, ignoreCase = true) } ?: entry.name,
            groupId = stem,
            discIndex = null,
            ready = true,
        )
    }

    private fun computeSha1Payload(entry: RomEntry): RomIdentity {
        val algo = RomIdentities.ALGO_SHA1_PAYLOAD
        val bytes = runCatching {
            openRomStream(entry)?.use { it.readBytes() }
        }.getOrNull() ?: return notReady(entry, algo)
        val payload = RomIdentities.stripInes(bytes)
        val hash = RomIdentities.sha1Hex(payload)
        return RomIdentity(
            romId = entry.id,
            algo = algo,
            hash = hash,
            headerTitle = null,
            groupId = hash,
            discIndex = null,
            ready = true,
        )
    }

    private fun computeSample(entry: RomEntry, size: Long?): RomIdentity {
        val algo = RomIdentities.ALGO_SHA256_SAMPLE
        val total = size ?: return notReady(entry, algo)
        if (total <= 0L) return notReady(entry, algo)
        val chunks = readSampleChunks(entry, total) ?: return notReady(entry, algo)
        val hash = RomIdentities.sampleSha256(total, chunks.first, chunks.second, chunks.third)
        return RomIdentity(
            romId = entry.id,
            algo = algo,
            hash = hash,
            headerTitle = null,
            groupId = hash,
            discIndex = null,
            ready = true,
        )
    }

    private fun romByteSize(entry: RomEntry): Long? {
        entry.path?.let { p ->
            val f = File(p)
            if (f.isFile) {
                val n = f.length()
                if (n >= 0L) return n
            }
        }
        return runCatching {
            DocumentFile.fromSingleUri(this, Uri.parse(entry.uri))
                ?.length()
                ?.takeIf { it >= 0L }
        }.getOrNull()
    }

    private fun openRomStream(entry: RomEntry): InputStream? {
        entry.path?.let { p ->
            val f = File(p)
            if (f.isFile) return f.inputStream()
        }
        return runCatching {
            contentResolver.openInputStream(Uri.parse(entry.uri))
        }.getOrNull()
    }

    private fun readSampleChunks(
        entry: RomEntry,
        size: Long,
    ): Triple<ByteArray, ByteArray, ByteArray>? {
        val chunk = SAMPLE_CHUNK_BYTES
        val headLen = minOf(chunk.toLong(), size).toInt()
        val tailLen = minOf(chunk.toLong(), size).toInt()
        val midStart = ((size - chunk.toLong()).coerceAtLeast(0L) / 2L)
        val midLen = minOf(chunk.toLong(), (size - midStart).coerceAtLeast(0L)).toInt()
        entry.path?.let { p ->
            val f = File(p)
            if (f.isFile) {
                return runCatching {
                    RandomAccessFile(f, "r").use { raf ->
                        val head = ByteArray(headLen)
                        raf.seek(0L)
                        raf.readFully(head)
                        val mid = ByteArray(midLen)
                        if (midLen > 0) {
                            raf.seek(midStart)
                            raf.readFully(mid)
                        }
                        val tail = ByteArray(tailLen)
                        if (tailLen > 0) {
                            raf.seek((size - tailLen).coerceAtLeast(0L))
                            raf.readFully(tail)
                        }
                        Triple(head, mid, tail)
                    }
                }.getOrNull()
            }
        }
        return runCatching {
            openRomStream(entry)?.use { stream ->
                val head = stream.readNBytesCompat(headLen)
                if (head.size < headLen && size > headLen) return@use null
                val skipMid = midStart - headLen.toLong()
                if (skipMid > 0) {
                    var left = skipMid
                    while (left > 0) {
                        val n = stream.skip(left)
                        if (n <= 0) break
                        left -= n
                    }
                }
                val mid = if (midLen > 0 && midStart >= headLen) {
                    stream.readNBytesCompat(midLen)
                } else if (midLen > 0 && midStart < headLen) {
                    // Mid overlaps head on tiny files; re-open for exact windows.
                    return@use null
                } else {
                    ByteArray(0)
                }
                val afterMid = midStart + midLen
                val tailStart = (size - tailLen).coerceAtLeast(0L)
                val skipTail = tailStart - afterMid
                if (skipTail > 0) {
                    var left = skipTail
                    while (left > 0) {
                        val n = stream.skip(left)
                        if (n <= 0) break
                        left -= n
                    }
                }
                val tail = stream.readNBytesCompat(tailLen)
                Triple(head, mid, tail)
            }
        }.getOrNull() ?: runCatching {
            // SAF streams that cannot skip: buffer whole file only if ≤ SMALL_MAX
            // is wrong for sample path (file is large). Re-open three times.
            val head = openRomStream(entry)?.use { it.readNBytesCompat(headLen) }
                ?: return null
            val mid = openRomStream(entry)?.use { s ->
                s.skipFully(midStart)
                s.readNBytesCompat(midLen)
            } ?: return null
            val tail = openRomStream(entry)?.use { s ->
                s.skipFully((size - tailLen).coerceAtLeast(0L))
                s.readNBytesCompat(tailLen)
            } ?: return null
            Triple(head, mid, tail)
        }.getOrNull()
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        if (n <= 0) return ByteArray(0)
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) out else out.copyOf(off)
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val skipped = skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            if (read() < 0) break
            left--
        }
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
        applyOpenSession(SessionTracker.onLauncherFocused(s, nowMs))
    }

    fun onSessionLauncherUnfocused(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        applyOpenSession(SessionTracker.onLauncherUnfocused(s, nowMs))
    }

    fun onSessionDeviceSleep(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        applyOpenSession(SessionTracker.onDeviceSleep(s, nowMs))
    }

    fun onSessionDeviceWake(nowMs: Long = System.currentTimeMillis()) {
        val s = openSession ?: return
        applyOpenSession(SessionTracker.onDeviceWake(s, nowMs))
    }

    /** In-place HUD clock when pause/resume flips; no SETTINGS rebuild. */
    private fun applyOpenSession(next: OpenSession) {
        val prev = openSession
        openSession = next
        if (prev != null && prev.isActive != next.isActive) {
            deckState.notifySelectionRefresh()
        }
    }

    fun beginSession(surface: SessionSurface, nowMs: Long = System.currentTimeMillis()) {
        sessionSurface = surface
        hostClaimed = false
        hostSurface = HostSurface.HUD
        clearCinemaRing()
        val romName = SlotKey.romId(surface.key)?.let { romEntry(it)?.name }
        val appLabel = if (romName != null || SlotKey.isRom(surface.key)) {
            null
        } else {
            appLibrary().byPackage(settings)[surface.key]?.label
        }
        val title = SessionRing.titleFor(romName, appLabel, surface.key)
        val entry = SessionRingEntry(
            key = surface.key,
            playerId = surface.playerId,
            packageName = surface.packageName,
            policy = surface.policy,
            launchedAtMs = nowMs,
            title = title,
        )
        settings = settings.copy(sessionRing = SessionRing.push(settings.sessionRing, entry))
        scheduleSettingsSave(settings)
        if (surface.policy == SessionPolicy.YIELD_BOTH) {
            liveCompanions().forEach { it.closeQuietly() }
        }
        // Ownership / surface change: re-apply FLAG_NOT_FOCUSABLE on every live deck
        // (companion stays resumed on secondary and would otherwise keep a stale flag).
        liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
    }

    fun markSessionGreedy() {
        sessionSurface = sessionSurface?.copy(greedy = true)
        hostClaimed = false
        hostSurface = HostSurface.HUD
        clearCinemaRing()
        liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
    }

    fun clearSessionSurface() {
        sessionSurface = null
        hostClaimed = false
        hostSurface = HostSurface.HUD
        clearCinemaRing()
        liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
    }

    private fun clearCinemaRing() {
        cinemaFrames = emptyList()
        cinemaLastSlot = null
        cinemaLastCaptureMs = 0L
        cinemaPinnedSlot = null
        clearTheaterState()
    }

    private fun clearTheaterState() {
        theaterSnap = null
        theaterRomId = null
        theaterLastPollMs = 0L
        theaterTickerTitle = null
        theaterTickerId = null
        theaterTickerUntilMs = 0L
    }

    private fun endOpenSession(nowMs: Long) {
        val s = openSession ?: return
        openSession = null
        sessionAwaitingReturn = false
        clearSessionSurface()
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
        // 20k-title TSV + rematch + identity sidecar stay off the first paint.
        ROM_IO.execute {
            loadBundledArcadeTitles()
            loadArcadeDatOverlay()
            rematchArcadeLibraryOffPaint()
            // Path index is already live; hashes never gate first paint.
            refreshIdentities(romEntries)
        }
        // Bundled + optional SAF lens pack; zero bundled games is fine.
        reloadLenses()
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
        const val SAMPLE_CHUNK_BYTES = 64 * 1024
        const val IDENT_TAG = "GGIdent"
        const val THEATER_TICKER_MS = 4_000L
    }
}
