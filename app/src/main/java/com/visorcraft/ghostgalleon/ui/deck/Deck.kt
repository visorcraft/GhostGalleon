package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.rom.LaunchReason
import com.visorcraft.ghostgalleon.rom.LaunchSession
import com.visorcraft.ghostgalleon.rom.PlayerTemplate
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLauncher
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.toast

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
    val dm = activity.getSystemService(DisplayManager::class.java)
    val availableIds = dm.displays.map { it.displayId }
    val resolvedId = LaunchGuardLogic.resolveLaunchDisplayId(
        overrideDisplayId = launchDisplayId,
        topologyLaunchDisplayId = topo?.launchDisplayId,
        primaryDisplayId = state.primaryDisplayId,
        currentDisplayId = activity.currentDisplayId(),
        availableDisplayIds = availableIds,
    )
    if (resolvedId != null) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(resolvedId)
        activity.startActivity(intent, options.toBundle())
    } else {
        activity.startActivity(intent)
    }
}

// Shared slot/dock launch path: ROM references go through RomLauncher (the
// platform template fires on the non-interactive display), app packages
// through their launcher intent. A ROM that dropped out of the library
// toasts instead of launching. [playerId] forces Open-with; otherwise the
// platform's settings default is used. On success: noteLaunchReason then
// noteLaunch (ends any prior play session + surface), then beginSession.
internal fun launchSlotKey(
    activity: AppCompatActivity,
    state: DeckState,
    roms: List<RomEntry>,
    key: String,
    playerId: String? = null,
    reason: LaunchReason = LaunchReason.OTHER,
) {
    // Folder tiles are opened by GridDeck (member list), never launched.
    val targetKind = LaunchGuardLogic.classifySlotKey(key)
    if (targetKind == LaunchGuardLogic.LaunchTargetKind.FOLDER_NOOP) return
    val app = activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp
    if (targetKind == LaunchGuardLogic.LaunchTargetKind.ROM) {
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
                OverlayAudioLogic.launchCueFor(targetKind, succeeded = true)?.let { app.playSfx(it) }
                app.noteLaunchReason(reason, key)
                app.noteLaunch(key)
                app.beginSession(surfaceFor(template))
            } else {
                OverlayAudioLogic.launchCueFor(targetKind, succeeded = false)
                    ?.let { app?.playSfx(it) }
            }
        } else {
            OverlayAudioLogic.launchCueFor(targetKind, succeeded = false)
                ?.let { app?.playSfx(it) }
            Toast.makeText(activity, R.string.deck_rom_missing, Toast.LENGTH_SHORT).show()
        }
        return
    }
    val launchIntent = activity.packageManager.getLaunchIntentForPackage(key)
    if (launchIntent == null) {
        OverlayAudioLogic.launchCueFor(targetKind, succeeded = false)
            ?.let { app?.playSfx(it) }
        Toast.makeText(activity, R.string.deck_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    OverlayAudioLogic.launchCueFor(targetKind, succeeded = true)?.let { app?.playSfx(it) }
    val pkgYield = app?.settings?.packageYield?.get(key) == true
    val launchId = app?.displayConfig?.launchDisplayId
    launchOnOtherDisplay(activity, state, launchIntent, launchId)
    if (app != null) {
        app.noteLaunchReason(reason, key)
        app.noteLaunch(key)
        app.beginSession(
            LaunchSession.forApp(key, launchId, packageYield = pkgYield),
        )
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
    activity.toast(R.string.settings_scanning)
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
            DeckDialogs.chooseItem(
                context = activity,
                titleRes = R.string.artwork_pick_match,
                items = labels,
            ) { which ->
                val hit = hits.getOrNull(which) ?: return@chooseItem
                com.visorcraft.ghostgalleon.art.Sgdb.forcedGameIds[rom.id] = hit.id
                com.visorcraft.ghostgalleon.art.Sgdb.pickedGameIds[rom.id] = hit.id
                app.persistSgdbPicks()
                pickSgdbImagesThenStart(activity, app, rom, apiKey, hit.id)
            }
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

private data class DecodedThumb(
    val bitmap: Bitmap?,
    val origWidth: Int,
    val origHeight: Int,
)

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
    val kind = ArtPickerFormatter.previewKindForTitleRes(titleRes)
    val spec = ArtPickerFormatter.previewSpec(kind)
    val decodeDim = ArtPickerFormatter.decodeTargetPx(
        kind = kind,
        density = activity.resources.displayMetrics.density,
    )
    activity.toast(R.string.settings_scanning)
    ART_SEARCH_EXECUTOR.execute {
        val transport = com.visorcraft.ghostgalleon.art.HttpSgdbTransport()
        val shown = urls.take(12)
        val thumbs = shown.map { url ->
            val decoded = runCatching {
                val bytes = transport.download(url) ?: return@runCatching DecodedThumb(null, 0, 0)
                decodeThumb(bytes, decodeDim, preserveAlpha = spec.preserveAlpha)
            }.getOrDefault(DecodedThumb(null, 0, 0))
            url to decoded
        }
        activity.runOnUiThread {
            if (activity.isFinishing) {
                thumbs.forEach { (_, d) -> d.bitmap?.takeIf { !it.isRecycled }?.recycle() }
                return@runOnUiThread
            }
            val dm = activity.resources.displayMetrics
            val density = dm.density.coerceAtLeast(0.1f)
            fun dp(v: Int) = (v * density).toInt()
            val screenWidthDp = (dm.widthPixels / density).toInt()
            val columns = ArtPickerFormatter.gridColumns(kind, screenWidthDp)
            val col = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            val builder = DeckDialogs.builder(activity)
                .setTitle(titleRes)
                .setNeutralButton(R.string.artwork_use_first) { _, _ ->
                    onPicked(urls.first())
                }
                .setNegativeButton(R.string.action_cancel, null)
            val dialog = builder.create()
            val accent = (activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp)
                ?.settings?.accentColor
                ?: com.visorcraft.ghostgalleon.settings.ThemePack.GHOST.accentColor
            val cardSurface = TileBackgrounds.surfaceColor(activity)
            val focusStroke = OverlayPrimitives.accessibleFocusStroke(accent, cardSurface)
            val sunkenSurface = TileBackgrounds.surfaceSunken(activity)
            val hairlineColor = TileBackgrounds.hairlineDivider(activity)
            val stroke1Px = activity.strokePx(1)
            val thumbViews = mutableListOf<ImageView>()
            val thumbRadiusPx = activity.dpF(spec.cornerRadiusDp)
            val thumbOutline = OutlineCache.roundedRect(thumbRadiusPx)
            val fallbackTint = android.content.res.ColorStateList.valueOf(UiDimens.ALPHA_MUTED)

            fun buildCandidateCard(i: Int, url: String, decoded: DecodedThumb): LinearLayout {
                val titleUi = ArtPickerFormatter.candidateTitleText(
                    indexOneBased = i + 1,
                    totalCount = thumbs.size,
                    url = url,
                )
                val sublineUi = ArtPickerFormatter.candidateSublineText(
                    origWidth = decoded.origWidth,
                    origHeight = decoded.origHeight,
                    url = url,
                )
                val idleCardBg = GradientDrawable().apply {
                    setColor(sunkenSurface)
                    cornerRadius = activity.dpF(12)
                    setStroke(stroke1Px, hairlineColor)
                }
                val focusableBg = TileBackgrounds.focusableRow(
                    context = activity,
                    accent = focusStroke,
                    idleBackground = idleCardBg,
                    cornerRadiusDp = 12,
                )
                val isTwoCol = columns > 1
                val card = LinearLayout(activity).apply {
                    orientation = if (isTwoCol) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = if (isTwoCol) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
                    setPadding(
                        dp(if (isTwoCol) 8 else 10),
                        dp(8),
                        dp(if (isTwoCol) 8 else 12),
                        dp(8),
                    )
                    isClickable = true
                    isFocusable = true
                    contentDescription = activity.resolveText(
                        ArtPickerFormatter.candidateAccessibilityText(titleUi, sublineUi),
                    )
                    background = OverlayPrimitives.roundedRipple(
                        context = activity,
                        content = focusableBg,
                        cornerRadiusDp = 12,
                    )
                    setOnClickListener {
                        dialog.dismiss()
                        onPicked(url)
                    }
                }
                val thumbFrame = FrameLayout(activity).apply {
                    background = GradientDrawable().apply {
                        setColor(cardSurface)
                        cornerRadius = thumbRadiusPx
                        setStroke(stroke1Px, hairlineColor)
                    }
                    clipToOutline = true
                    outlineProvider = thumbOutline
                }
                val liveBmp = decoded.bitmap?.takeIf { !it.isRecycled }
                val thumb = ImageView(activity).apply {
                    if (liveBmp != null) {
                        scaleType = if (spec.fitCenter) {
                            ImageView.ScaleType.FIT_CENTER
                        } else {
                            ImageView.ScaleType.CENTER_CROP
                        }
                        if (spec.fitCenter) {
                            val logoPad = dp(4)
                            setPadding(logoPad, logoPad, logoPad, logoPad)
                        }
                        setImageBitmap(liveBmp)
                    } else {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        val pad = dp(12)
                        setPadding(pad, pad, pad, pad)
                        setImageResource(R.drawable.ic_nav_art)
                        imageTintList = fallbackTint
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                thumbViews.add(thumb)
                thumbFrame.addView(
                    thumb,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                card.addView(thumbFrame, LinearLayout.LayoutParams(dp(spec.widthDp), dp(spec.heightDp)))

                val textCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    if (isTwoCol) {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(6), 0, 0)
                    } else {
                        setPadding(dp(12), 0, 0, 0)
                    }
                }
                textCol.addView(
                    TextView(activity).apply {
                        text = activity.resolveText(titleUi)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTwoCol) 12.5f else 14f)
                        setTextColor(UiDimens.ALPHA_PRIMARY)
                        typeface = Typeface.DEFAULT_BOLD
                        UiDimens.applyTabularNumerals(this)
                        gravity = if (isTwoCol) Gravity.CENTER else Gravity.START
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                    },
                )
                if (sublineUi != null) {
                    textCol.addView(
                        TextView(activity).apply {
                            text = activity.resolveText(sublineUi)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTwoCol) 11f else 12f)
                            setTextColor(UiDimens.ALPHA_MUTED)
                            UiDimens.applyTabularNumerals(this)
                            gravity = if (isTwoCol) Gravity.CENTER else Gravity.START
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(3) },
                    )
                }
                card.addView(
                    textCol,
                    if (isTwoCol) {
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    } else {
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
                return card
            }

            if (columns > 1) {
                thumbs.chunked(columns).forEachIndexed { rowIdx, chunk ->
                    val rowLine = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                    }
                    chunk.forEachIndexed { colIdx, (url, decoded) ->
                        val itemIndex = rowIdx * columns + colIdx
                        rowLine.addView(
                            buildCandidateCard(itemIndex, url, decoded),
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                if (colIdx > 0) marginStart = dp(8)
                            },
                        )
                    }
                    repeat(columns - chunk.size) {
                        rowLine.addView(
                            View(activity),
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = dp(8)
                            },
                        )
                    }
                    col.addView(
                        rowLine,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            if (rowIdx > 0) topMargin = dp(8)
                        },
                    )
                }
            } else {
                thumbs.forEachIndexed { i, (url, decoded) ->
                    col.addView(
                        buildCandidateCard(i, url, decoded),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            if (i > 0) topMargin = dp(6)
                        },
                    )
                }
            }
            val scroll = OverlayPrimitives.MaxHeightScrollView(
                activity,
                OverlayPrimitives.maxModalHeight(activity, 0.56f),
            ).apply {
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(16))
                addView(col)
            }
            dialog.setOnDismissListener {
                thumbViews.forEach { it.setImageDrawable(null) }
                thumbs.forEach { (_, decoded) ->
                    decoded.bitmap?.takeIf { !it.isRecycled }?.recycle()
                }
            }
            dialog.setView(scroll)
            dialog.show()
            DeckDialogs.style(dialog, activity, isCustomView = columns > 1)
        }
    }
}

private fun decodeThumb(
    bytes: ByteArray,
    maxDimension: Int,
    preserveAlpha: Boolean = false,
): DecodedThumb {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return DecodedThumb(null, 0, 0)
    val sample = DeckWallpaper.computeSampleSize(
        outWidth = bounds.outWidth,
        outHeight = bounds.outHeight,
        targetWidth = maxDimension,
        targetHeight = maxDimension,
        maxTextureDim = maxDimension.coerceAtLeast(128) * 2,
    )
    val opts = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = if (preserveAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
    }
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return DecodedThumb(bmp, bounds.outWidth, bounds.outHeight)
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
