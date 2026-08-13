package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.rom.LaunchSession
import com.visorcraft.ghostgalleon.rom.PlayerTemplate
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLauncher
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.resolveText

interface Deck {
    fun primaryView(context: Context): View

    // Applies a selection-only change to the already-built primary view
    // (ring/scale move, page dots, scroll alignment). Returns false when no
    // live view state exists to update — the caller then does a full rebuild.
    fun updateSelection(): Boolean

    /**
     * Game Mode browse chip change without activity [setContentView].
     * Recomputes carousel entries + chip chrome in place. Default false
     * (GridDeck / unready views fall through to full rebuild).
     */
    fun applyBrowseChange(): Boolean = false

    /**
     * Settings chrome-only change (card size, browse chrome flags) without
     * full activity rebuild. Default false → SETTINGS full paint.
     */
    fun applyChromeChange(): Boolean = false

    fun handleAction(action: Action): Boolean
}

// Dual-screen launch model: apps open on the topology launch display
// (non-interactive panel) when dual; same-display fallback for single.
// [launchDisplayId] overrides topology when non-null (stage-plot face).
internal fun launchOnOtherDisplay(
    activity: Activity,
    state: DeckState,
    intent: Intent,
    launchDisplayId: Int? = null,
) {
    val app = activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp
    val topo = app?.displayConfig
    val launchId = launchDisplayId
        ?: topo?.launchDisplayId
        ?: run {
            // Soft fallback without topology: any display that is not primary.
            val dm = activity.getSystemService(DisplayManager::class.java)
            dm.displays.map { it.displayId }
                .firstOrNull { it != state.primaryDisplayId }
        }
    val dm = activity.getSystemService(DisplayManager::class.java)
    val current = activity.currentDisplayId()
    if (launchId != null &&
        launchId != current &&
        dm.displays.any { it.displayId == launchId }
    ) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(launchId)
        activity.startActivity(intent, options.toBundle())
    } else {
        activity.startActivity(intent)
    }
}

// Shared slot/dock launch path: ROM references go through RomLauncher (the
// platform template fires on the non-interactive display), app packages
// through their launcher intent. A ROM that dropped out of the library
// toasts instead of launching. [playerId] forces Open-with; otherwise the
// platform's settings default is used. On success: noteLaunch first (ends
// any prior play session + surface), then beginSession for the new surface.
internal fun launchSlotKey(
    activity: AppCompatActivity,
    state: DeckState,
    roms: List<RomEntry>,
    key: String,
    playerId: String? = null,
) {
    // Folder tiles are opened by GridDeck (member list), never launched.
    if (SlotKey.isFolder(key)) return
    val app = activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp
    if (SlotKey.isRom(key)) {
        val id = SlotKey.romId(key)
        val entry = id?.let { app?.romById?.get(it) }
            ?: roms.firstOrNull { it.id == id }
        if (entry != null) {
            val settings = app?.settings
            val preferred = RomProfiles.preferredPlayerId(
                entry.id,
                settings?.romProfiles.orEmpty(),
                settings?.defaultPlayers?.get(entry.platformId),
            )
            val topo = app?.displayConfig
            val stagePlots = settings?.stagePlots.orEmpty()
            val packageYield = settings?.packageYield.orEmpty()
            val interactiveId = topo?.primaryDisplayId ?: state.primaryDisplayId
            val companionId = topo?.companionDisplayId
            val topologyLaunchId = topo?.launchDisplayId
            fun surfaceFor(template: PlayerTemplate) =
                LaunchSession.forRom(
                    key = key,
                    template = template,
                    entryId = entry.id,
                    stagePlots = stagePlots,
                    packageYield = packageYield,
                    interactiveId = interactiveId,
                    companionId = companionId,
                    topologyLaunchId = topologyLaunchId,
                )
            val template = RomLauncher.launch(
                activity, state, entry,
                playerId = playerId,
                preferredPlayerId = preferred,
                resolveLaunchDisplayId = { surfaceFor(it).launchDisplayId },
            )
            if (template != null && app != null) {
                app.noteLaunch(key)
                app.beginSession(surfaceFor(template))
            }
        } else {
            Toast.makeText(activity, R.string.deck_rom_missing, Toast.LENGTH_SHORT).show()
        }
        return
    }
    activity.packageManager.getLaunchIntentForPackage(key)
        ?.let {
            val pkgYield = app?.settings?.packageYield?.get(key) == true
            val launchId = app?.displayConfig?.launchDisplayId
            launchOnOtherDisplay(activity, state, it, launchId)
            if (app != null) {
                app.noteLaunch(key)
                app.beginSession(
                    LaunchSession.forApp(key, launchId, packageYield = pkgYield),
                )
            }
        }
}

/**
 * One-ROM SteamGridDB scrape (grid + hero slots still missing).
 * Uses the app-scoped [com.visorcraft.ghostgalleon.art.ScrapeJob].
 */
internal fun requestMissingArtwork(activity: AppCompatActivity, rom: RomEntry) {
    val app = activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp ?: return
    val cache = app.artCache
    val needsWork = com.visorcraft.ghostgalleon.art.SgdbQueue.prioritize(
        listOf(rom),
        hasGrid = { cache.diskHas(it) },
        hasHero = { cache.diskHas(it, com.visorcraft.ghostgalleon.art.ArtCache.ArtKind.HERO) },
    ).isNotEmpty()
    when (
        com.visorcraft.ghostgalleon.art.ArtworkDownload.gate(
            hasKey = !app.settings.sgdbApiKey.isNullOrBlank(),
            running = app.scrapeJob.isRunning,
            needsWork = needsWork,
        )
    ) {
        com.visorcraft.ghostgalleon.art.ArtworkDownload.Gate.NO_KEY -> {
            Toast.makeText(activity, R.string.artwork_need_api_key, Toast.LENGTH_SHORT).show()
            return
        }
        com.visorcraft.ghostgalleon.art.ArtworkDownload.Gate.BUSY -> {
            Toast.makeText(activity, R.string.artwork_download_busy, Toast.LENGTH_SHORT).show()
            return
        }
        com.visorcraft.ghostgalleon.art.ArtworkDownload.Gate.NOTHING_NEEDED -> {
            Toast.makeText(activity, R.string.artwork_already_cached, Toast.LENGTH_SHORT).show()
            return
        }
        com.visorcraft.ghostgalleon.art.ArtworkDownload.Gate.START -> Unit
    }
    when (
        val decision = com.visorcraft.ghostgalleon.art.ScrapeEnvironment.decision(
            activity,
            app.settings,
        )
    ) {
        is com.visorcraft.ghostgalleon.art.ScrapePolicy.Decision.Block -> {
            Toast.makeText(
                activity,
                activity.resolveText(
                    com.visorcraft.ghostgalleon.art.ScrapePolicy.blockMessage(decision.reason),
                ),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        com.visorcraft.ghostgalleon.art.ScrapePolicy.Decision.Allow -> Unit
    }
    val apiKey = app.settings.sgdbApiKey ?: return
    pickSgdbMatchThenStart(activity, app, rom, apiKey)
}

private fun pickSgdbMatchThenStart(
    activity: AppCompatActivity,
    app: com.visorcraft.ghostgalleon.GhostGalleonApp,
    rom: RomEntry,
    apiKey: String,
) {
    ART_SEARCH_EXECUTOR.execute {
        val query = com.visorcraft.ghostgalleon.art.Sgdb.normalizeName(rom.name)
        val json = com.visorcraft.ghostgalleon.art.HttpSgdbTransport()
            .get(com.visorcraft.ghostgalleon.art.Sgdb.searchUrl(query), apiKey)
        val hits = json?.let {
            com.visorcraft.ghostgalleon.art.Sgdb.parseSearchHits(it)
        }.orEmpty()
        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            if (hits.size <= 1) {
                hits.firstOrNull()?.let {
                    com.visorcraft.ghostgalleon.art.Sgdb.forcedGameIds[rom.id] = it.id
                    com.visorcraft.ghostgalleon.art.Sgdb.pickedGameIds[rom.id] = it.id
                    app.persistSgdbPicks()
                    pickSgdbImagesThenStart(activity, app, rom, apiKey, it.id)
                    return@runOnUiThread
                }
                startArtworkJob(activity, app, rom, apiKey)
                return@runOnUiThread
            }
            val labels = hits.map { it.name }.toTypedArray()
            android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.artwork_pick_match)
                .setItems(labels) { _, which ->
                    val hit = hits.getOrNull(which) ?: return@setItems
                    com.visorcraft.ghostgalleon.art.Sgdb.forcedGameIds[rom.id] = hit.id
                    com.visorcraft.ghostgalleon.art.Sgdb.pickedGameIds[rom.id] = hit.id
                    app.persistSgdbPicks()
                    pickSgdbImagesThenStart(activity, app, rom, apiKey, hit.id)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }
}

private fun pickSgdbImagesThenStart(
    activity: AppCompatActivity,
    app: com.visorcraft.ghostgalleon.GhostGalleonApp,
    rom: RomEntry,
    apiKey: String,
    gameId: Long,
) {
    ART_SEARCH_EXECUTOR.execute {
        val transport = com.visorcraft.ghostgalleon.art.HttpSgdbTransport()
        val grids = transport.get(
            com.visorcraft.ghostgalleon.art.Sgdb.gridsUrl(gameId), apiKey,
        )?.let { com.visorcraft.ghostgalleon.art.Sgdb.parseImageUrls(it) }.orEmpty()
        val heroes = transport.get(
            com.visorcraft.ghostgalleon.art.Sgdb.heroesUrl(gameId), apiKey,
        )?.let { com.visorcraft.ghostgalleon.art.Sgdb.parseImageUrls(it) }.orEmpty()
        val logos = transport.get(
            com.visorcraft.ghostgalleon.art.Sgdb.logosUrl(gameId), apiKey,
        )?.let { com.visorcraft.ghostgalleon.art.Sgdb.parseImageUrls(it) }.orEmpty()
        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            pickImageKind(activity, R.string.artwork_pick_grid, grids) { gridUrl ->
                gridUrl?.let {
                    com.visorcraft.ghostgalleon.art.Sgdb.forcedGridUrls[rom.id] = it
                }
                pickImageKind(activity, R.string.artwork_pick_hero, heroes) { heroUrl ->
                    heroUrl?.let {
                        com.visorcraft.ghostgalleon.art.Sgdb.forcedHeroUrls[rom.id] = it
                    }
                    pickImageKind(activity, R.string.artwork_pick_logo, logos) { logoUrl ->
                        logoUrl?.let {
                            com.visorcraft.ghostgalleon.art.Sgdb.forcedLogoUrls[rom.id] = it
                        }
                        startArtworkJob(activity, app, rom, apiKey)
                    }
                }
            }
        }
    }
}

private fun pickImageKind(
    activity: AppCompatActivity,
    titleRes: Int,
    urls: List<String>,
    onPicked: (String?) -> Unit,
) {
    if (urls.size <= 1) {
        onPicked(urls.firstOrNull())
        return
    }
    ART_SEARCH_EXECUTOR.execute {
        val transport = com.visorcraft.ghostgalleon.art.HttpSgdbTransport()
        val shown = urls.take(12)
        val thumbs = shown.map { url ->
            val bmp = runCatching {
                val bytes = transport.download(url) ?: return@runCatching null
                decodeThumb(bytes, 144)
            }.getOrNull()
            url to bmp
        }
        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            val density = activity.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()
            val col = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            val builder = android.app.AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setNeutralButton(R.string.artwork_use_first) { _, _ ->
                    onPicked(urls.first())
                }
                .setNegativeButton(R.string.action_cancel, null)
            val dialog = builder.create()
            thumbs.forEachIndexed { i, (url, bmp) ->
                val row = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                    isClickable = true
                    setOnClickListener {
                        dialog.dismiss()
                        onPicked(url)
                    }
                }
                val thumb = android.widget.ImageView(activity).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    if (bmp != null) setImageBitmap(bmp)
                    else setBackgroundColor(0xFF2A2A32.toInt())
                }
                row.addView(thumb, android.widget.LinearLayout.LayoutParams(dp(72), dp(40)))
                row.addView(
                    android.widget.TextView(activity).apply {
                        text = (i + 1).toString() + ". " + url.substringAfterLast('/').take(36)
                        setTextColor(0xFFFFFFFF.toInt())
                        setPadding(dp(10), 0, 0, 0)
                    },
                    android.widget.LinearLayout.LayoutParams(
                        0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                    ),
                )
                col.addView(row)
            }
            dialog.setView(android.widget.ScrollView(activity).apply { addView(col) })
            dialog.show()
        }
    }
}

private fun decodeThumb(bytes: ByteArray, maxDimension: Int): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension &&
        bounds.outHeight / (sample * 2) >= maxDimension
    ) {
        sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private val ART_SEARCH_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor()

private fun startArtworkJob(
    activity: AppCompatActivity,
    app: com.visorcraft.ghostgalleon.GhostGalleonApp,
    rom: RomEntry,
    apiKey: String,
) {
    val job = app.scrapeJob
    val listener = object : com.visorcraft.ghostgalleon.art.ScrapeJob.Listener {
        override fun onProgress(done: Int, total: Int) = Unit
        override fun onFinished(summary: com.visorcraft.ghostgalleon.art.SgdbScraper.Summary) {
            job.removeListener(this)
            if (!activity.isFinishing) {
                Toast.makeText(
                    activity,
                    activity.getString(
                        if (summary.cancelled) {
                            R.string.artwork_summary_cancelled
                        } else {
                            R.string.artwork_summary
                        },
                        summary.downloaded,
                        summary.skipped,
                        summary.failed,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
            app.deckState.notifySelectionRefresh()
        }
    }
    job.addListener(listener)
    if (!job.start(apiKey, listOf(rom))) {
        job.removeListener(listener)
        Toast.makeText(activity, R.string.artwork_download_busy, Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(activity, R.string.artwork_download_started, Toast.LENGTH_SHORT).show()
}
