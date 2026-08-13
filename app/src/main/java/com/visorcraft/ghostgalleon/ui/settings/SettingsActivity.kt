package com.visorcraft.ghostgalleon.ui.settings

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.BuildConfig
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtArchive
import com.visorcraft.ghostgalleon.art.ScrapeJob
import com.visorcraft.ghostgalleon.art.SgdbScraper
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.PackageManagerAppsSource
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerTemplate
import com.visorcraft.ghostgalleon.rom.RaCfg
import com.visorcraft.ghostgalleon.rom.RomLibrary
import com.visorcraft.ghostgalleon.rom.TreeLabels
import com.visorcraft.ghostgalleon.rom.playerSettingsLabel
import com.visorcraft.ghostgalleon.display.DeviceProfileCatalog
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.input.SeatAnchor
import com.visorcraft.ghostgalleon.input.SecondSeatPolicy
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.CompanionRole
import com.visorcraft.ghostgalleon.settings.SettingsBundle
import com.visorcraft.ghostgalleon.settings.SettingsCatalog
import com.visorcraft.ghostgalleon.settings.SettingsJump
import com.visorcraft.ghostgalleon.settings.SettingsStore
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.ui.deck.DeckWallpaper
import com.visorcraft.ghostgalleon.state.UIMode
import com.visorcraft.ghostgalleon.system.SystemInfoCollector
import com.visorcraft.ghostgalleon.system.SystemInfoFormat
import com.visorcraft.ghostgalleon.ui.ControllerLabActivity
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.deviceProfileName
import com.visorcraft.ghostgalleon.ui.applyThemeFontScale
import com.visorcraft.ghostgalleon.ui.hideStatusBar
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.themeName
import java.io.File
import java.text.NumberFormat

class SettingsActivity : AppCompatActivity() {

    private val app get() = application as GhostGalleonApp

    /** Settings pages: Display & Grid stay together; others are their own. */
    private enum class SettingsPage(val titleRes: Int) {
        DISPLAY_GRID(R.string.settings_page_display_grid),
        APPS(R.string.settings_page_apps),
        CONTROLS(R.string.settings_page_controls),
        LIBRARY(R.string.settings_page_library),
        ART_DATA(R.string.settings_page_art),
        STATS(R.string.settings_page_stats),
        SYSTEM(R.string.settings_page_system),
        ABOUT(R.string.settings_page_about),
    }

    private var currentPage: SettingsPage = SettingsPage.DISPLAY_GRID
    private var pageHost: LinearLayout? = null
    private val pageBodies = mutableMapOf<SettingsPage, LinearLayout>()
    private val navItems = mutableMapOf<SettingsPage, TextView>()
    private var pageDropdownLabel: TextView? = null

    private val remappable = listOf(
        Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
        Action.CONFIRM, Action.BACK, Action.SWAP_SCREENS,
        Action.TOGGLE_MODE, Action.OPEN_SETTINGS, Action.PAGE_PREV, Action.PAGE_NEXT,
        Action.OPEN_QUICK_PANEL,
        Action.SEARCH_LIBRARY, Action.TOGGLE_FAVORITE, Action.SHOW_DETAILS,
        Action.OPEN_SESSION_SWITCHER, Action.TOGGLE_PLAY_HUD,
        Action.CLAIM_HOST, Action.RELEASE_HOST, Action.TOGGLE_SEAT,
    )

    private var captureTarget: Action? = null
    private var captureLabel: TextView? = null
    private var capturePulse: ObjectAnimator? = null

    private var scanning = false

    private val appLibrary by lazy {
        (application as GhostGalleonApp).appLibrary()
    }

    private var hiddenValue: TextView? = null
    private var hiddenRomsValue: TextView? = null
    private var dockValue: TextView? = null
    private var packageYieldValue: TextView? = null

    private fun appLabel(packageName: String): String =
        appLibrary.all(app.settings).firstOrNull { it.packageName == packageName }
            ?.label ?: packageName

    private fun dockEntryLabel(key: String): String {
        val romId = SlotKey.romId(key)
        return if (romId != null) {
            app.romEntries.firstOrNull { it.id == romId }?.name ?: key
        } else {
            appLabel(key)
        }
    }

    private fun labelForStatsKey(key: String): String = dockEntryLabel(key)

    private fun statRow(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@SettingsActivity).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.END
            })
        }

    /** Bundled pack asset basenames under assets/platform_packs/. */
    private fun listBundledPackAssets(): List<String> =
        runCatching {
            assets.list("platform_packs")
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())

    private fun loadBundledPackAsset(assetName: String) {
        val text = runCatching {
            assets.open("platform_packs/$assetName").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(
                this,
                getString(R.string.settings_pack_missing, assetName),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val parsed = app.platformPackStore.importJson(text)
        if (parsed == null) {
            Toast.makeText(
                this,
                getString(R.string.settings_pack_invalid, assetName),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Toast.makeText(
                this,
                getString(
                    R.string.settings_pack_loaded,
                    assetName,
                    parsed.platforms.joinToString { it.id },
                ),
                Toast.LENGTH_LONG,
            ).show()
            refreshSettingsUi()
        }
    }

    /** Merge all bundled packs into one overlay (later packs win on id clash). */
    private fun loadAllBundledPacks() {
        val names = listBundledPackAssets()
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.settings_no_bundled_packs, Toast.LENGTH_SHORT).show()
            return
        }
        var merged = emptyList<com.visorcraft.ghostgalleon.rom.Platform>()
        var loaded = 0
        for (name in names) {
            val text = runCatching {
                assets.open("platform_packs/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            val parsed = com.visorcraft.ghostgalleon.rom.PlatformPack.parse(text) ?: continue
            merged = com.visorcraft.ghostgalleon.rom.PlatformPack.merge(merged, parsed.platforms)
            loaded++
        }
        if (merged.isEmpty()) {
            Toast.makeText(this, R.string.settings_no_valid_packs, Toast.LENGTH_LONG).show()
            return
        }
        val root = org.json.JSONObject()
            .put("schemaVersion", 1)
            .put("platforms", org.json.JSONArray().apply {
                merged.forEach { p ->
                    put(org.json.JSONObject()
                        .put("id", p.id)
                        .put("displayName", p.displayName)
                        .put("shortName", p.shortName)
                        .put("folderNames", org.json.JSONArray(p.folderNames))
                        .put("extensions", org.json.JSONArray(p.extensions))
                        .put("players", org.json.JSONArray().apply {
                            p.players.forEach { pl ->
                                put(org.json.JSONObject()
                                    .put("id", pl.id)
                                    .put("displayName", pl.displayName)
                                    .put("component", pl.component)
                                    .put("action", pl.action ?: "")
                                    .put("uriStyle", pl.uriStyle.name)
                                    .put("grantRead", pl.grantRead)
                                    .put("flags", pl.flags)
                                    .put("extras", org.json.JSONObject().apply {
                                        pl.extras.forEach { (k, v) -> put(k, v) }
                                    }))
                            }
                        }))
                }
            })
        val result = app.platformPackStore.importJson(root.toString())
        if (result == null) {
            Toast.makeText(this, R.string.settings_pack_merge_failed, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                getString(
                    R.string.settings_loaded_summary,
                    resources.getQuantityString(R.plurals.count_packs, loaded, loaded),
                    resources.getQuantityString(
                        R.plurals.count_platforms,
                        result.platforms.size,
                        result.platforms.size,
                    ),
                ),
                Toast.LENGTH_LONG,
            ).show()
            refreshSettingsUi()
        }
    }

    private fun showBundledPackCatalog() {
        val names = listBundledPackAssets()
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.settings_no_bundled_packs, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = names.map { it.removeSuffix(".json") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_bundled_packs)
            .setItems(labels) { _, which ->
                loadBundledPackAsset(names[which])
            }
            .setNeutralButton(R.string.action_load_all) { _, _ -> loadAllBundledPacks() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshAppsRows() {
        hiddenValue?.text = formatNumber(app.settings.hiddenPackages.size)
        hiddenRomsValue?.text = formatNumber(app.settings.hiddenRomIds.size)
        val dock = app.settings.dockSlots.filterNotNull()
        dockValue?.text =
            if (dock.isEmpty()) getString(R.string.label_empty)
            else dock.joinToString(" · ", transform = ::dockEntryLabel)
        val yieldCount = app.settings.packageYield.count { it.value }
        packageYieldValue?.text = formatNumber(yieldCount)
    }

    private fun modalRow(label: String, chip: String, onChip: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowLabel(label).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@SettingsActivity).apply {
                text = chip
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(accent)
                background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
                val hp = dp(12); val vp = dp(6)
                setPadding(hp, vp, hp, vp)
                setOnClickListener { onChip() }
            })
        }

    private fun modalEmpty(text: String): View = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(0x66FFFFFF)
        gravity = Gravity.CENTER
        val v = dp(16)
        setPadding(0, v, 0, v)
    }

    private fun showHiddenAppsDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun rebuild() {
            list.removeAllViews()
            val hidden = app.settings.hiddenPackages
                .sortedBy { appLabel(it).lowercase() }
            if (hidden.isEmpty()) {
                list.addView(modalEmpty(getString(R.string.settings_no_hidden_apps)))
            } else {
                hidden.forEach { pkg ->
                    list.addView(modalRow(appLabel(pkg), getString(R.string.action_unhide)) {
                        app.updateSettings(app.settings.copy(
                            hiddenPackages = app.settings.hiddenPackages - pkg))
                        refreshAppsRows()
                        rebuild()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_hidden_apps)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun showHiddenRomsDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun romLabel(id: String): String {
            val rom = app.romEntries.firstOrNull { it.id == id }
            return rom?.name ?: id.substringAfterLast(':').ifBlank { id }
        }
        fun rebuild() {
            list.removeAllViews()
            val hidden = app.settings.hiddenRomIds
                .sortedBy { romLabel(it).lowercase() }
            if (hidden.isEmpty()) {
                list.addView(modalEmpty(getString(R.string.settings_no_hidden_roms)))
            } else {
                hidden.forEach { id ->
                    list.addView(modalRow(romLabel(id), getString(R.string.action_unhide)) {
                        val next = com.visorcraft.ghostgalleon.library.HiddenRoms
                            .unhide(app.settings.hiddenRomIds, id)
                        app.updateSettings(app.settings.copy(hiddenRomIds = next))
                        refreshAppsRows()
                        rebuild()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_hidden_roms)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    /** Pick an installed launcher app as the companion pin. */
    private fun showPinnedCompanionPicker() {
        val apps = appLibrary.visible(app.settings)
            .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.settings_no_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_pinned_companion)
            .setItems(labels) { _, which ->
                val pkg = apps[which].packageName
                app.updateSettings(app.settings.copy(companionPinnedPackage = pkg))
                refreshSettingsUi()
            }
            .setNeutralButton(R.string.action_clear) { _, _ ->
                app.updateSettings(app.settings.copy(companionPinnedPackage = null))
                refreshSettingsUi()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDockDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun rebuild() {
            list.removeAllViews()
            val dock = app.settings.dockSlots.filterNotNull()
            if (dock.isEmpty()) {
                list.addView(modalEmpty(getString(R.string.settings_dock_empty)))
            } else {
                dock.forEach { key ->
                    list.addView(modalRow(dockEntryLabel(key), getString(R.string.action_remove)) {
                        app.updateSettings(app.settings.copy(
                            dockSlots = app.settings.dockSlots.map {
                                if (it == key) null else it
                            }))
                        refreshAppsRows()
                        rebuild()
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle(R.string.label_dock)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    /** Per-package YIELD_BOTH overrides for Android apps (no launch face). */
    private fun showPackageYieldDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, dp(8), pad, dp(8))
        }
        fun yieldChip(on: Boolean): String =
            if (on) getString(R.string.label_yes) else getString(R.string.label_no)
        fun rebuild() {
            list.removeAllViews()
            val apps = appLibrary.visible(app.settings)
                .sortedBy { it.label.lowercase() }
            if (apps.isEmpty()) {
                list.addView(modalEmpty(getString(R.string.settings_no_apps)))
                return
            }
            apps.forEach { entry ->
                val pkg = entry.packageName
                val on = app.settings.packageYield[pkg] == true
                list.addView(
                    modalRow(entry.label, yieldChip(on)) {
                        val live = app.settings
                        val next = if (live.packageYield[pkg] == true) {
                            live.packageYield - pkg
                        } else {
                            live.packageYield + (pkg to true)
                        }
                        app.updateSettings(live.copy(packageYield = next))
                        refreshAppsRows()
                        rebuild()
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56),
                    ),
                )
            }
        }
        rebuild()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_package_yield)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private val platformPackLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(this, R.string.settings_pack_read_failed, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val parsed = app.platformPackStore.importJson(text)
            if (parsed == null) {
                Toast.makeText(this, R.string.settings_platform_pack_invalid, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this,
                    resources.getQuantityString(
                        R.plurals.count_platforms_imported,
                        parsed.platforms.size,
                        parsed.platforms.size,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                refreshSettingsUi()
            }
        }

    private val lensPackLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            app.updateSettings(app.settings.copy(ramLensPackUri = uri.toString()))
            app.reloadLenses()
            Toast.makeText(this, R.string.settings_import_lens_pack, Toast.LENGTH_SHORT).show()
            refreshSettingsUi()
        }

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val text = SettingsBundle.pack(
                    SettingsStore.toJson(app.settings),
                    RomLibrary.entriesToJson(app.romLibrary.load()),
                )
                contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("could not open $uri")
            }.onSuccess {
                Toast.makeText(this, R.string.settings_exported, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, R.string.settings_export_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private val exportArtLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val n = contentResolver.openOutputStream(uri)?.use { out ->
                    ArtArchive.zip(File(filesDir, "art"), out)
                } ?: error("could not open $uri")
                n
            }.onSuccess { n ->
                Toast.makeText(
                    this,
                    getString(R.string.settings_art_exported, n),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(this, R.string.settings_art_export_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private val importArtLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    ArtArchive.unzip(input, File(filesDir, "art"))
                } ?: error("could not open $uri")
            }.onSuccess { n ->
                Toast.makeText(
                    this,
                    getString(R.string.settings_art_imported, n),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(this, R.string.settings_art_import_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("could not open $uri")
                val (settingsJson, romJson) = SettingsBundle.unpack(text)
                val newSettings = SettingsStore.parse(settingsJson)
                val entries = RomLibrary.parseEntries(romJson)
                app.romLibrary.save(entries)
                app.publishRomEntries(app.romLibrary.load())
                app.updateSettings(newSettings)
            }.onSuccess {
                Toast.makeText(this, R.string.settings_imported, Toast.LENGTH_SHORT).show()
                refreshSettingsUi()
            }.onFailure {
                Toast.makeText(this, R.string.settings_invalid_file, Toast.LENGTH_SHORT).show()
            }
        }

    private val arcadeDatPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(this, R.string.settings_arcade_dat_failed, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val n = app.importArcadeDat(text)
            Toast.makeText(
                this,
                if (n > 0) getString(R.string.settings_arcade_dat_imported, n)
                else getString(R.string.settings_arcade_dat_empty),
                Toast.LENGTH_LONG,
            ).show()
            refreshSettingsUi()
        }

    private val wallpaperPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                DeckWallpaper.dropCache()
                app.updateSettings(app.settings.copy(wallpaperUri = uri.toString()))
                refreshWallpaperRow()
            }
        }

    private val themeJsonPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(this, R.string.settings_theme_read_failed, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val tokens = ThemePack.parseJson(text)
            if (tokens == null) {
                Toast.makeText(this, R.string.settings_theme_invalid, Toast.LENGTH_LONG).show()
            } else {
                applyThemeAndRefresh(ThemePack.applyCustom(app.settings, tokens, text))
                Toast.makeText(
                    this,
                    getString(R.string.format_theme, tokens.displayName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    private var wallpaperValue: TextView? = null
    private var wallpaperClear: View? = null
    private var raUserValue: TextView? = null
    private var raKeyValue: TextView? = null

    private var sgdbKeyValue: TextView? = null
    private var scrapeLabel: TextView? = null
    private var scrapeValue: TextView? = null
    private var scrapeRow: View? = null

    private val scrapeListener = object : ScrapeJob.Listener {
        override fun onProgress(done: Int, total: Int) {
            scrapeLabel?.setText(R.string.action_cancel)
            scrapeValue?.text = getString(R.string.format_progress, done, total)
        }

        override fun onFinished(summary: SgdbScraper.Summary) {
            refreshSgdbRows()
            if (!isFinishing && !isDestroyed) {
                val message = getString(
                    if (summary.cancelled) {
                        R.string.artwork_summary_cancelled
                    } else {
                        R.string.artwork_summary
                    },
                    summary.downloaded,
                    summary.skipped,
                    summary.failed,
                )
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshSgdbRows() {
        val hasKey = !app.settings.sgdbApiKey.isNullOrEmpty()
        val job = app.scrapeJob
        val running = job.isRunning
        sgdbKeyValue?.setText(if (hasKey) R.string.label_set else R.string.label_not_set)
        if (running) {
            scrapeLabel?.setText(R.string.action_cancel)
            scrapeValue?.text = if (job.progressTotal > 0) {
                getString(
                    R.string.format_progress,
                    job.progressDone,
                    job.progressTotal,
                )
            } else {
                getString(R.string.glyph_ellipsis)
            }
        } else {
            scrapeLabel?.setText(R.string.settings_download_artwork)
            scrapeValue?.text = if (hasKey) "" else getString(R.string.settings_add_api_key_first)
        }
        val usable = hasKey || running
        scrapeRow?.isEnabled = usable
        scrapeRow?.alpha = if (usable) 1f else 0.5f
    }

    private fun showCollectionsDialog() {
        val names = app.settings.collections.keys.sortedBy { it.lowercase() }
            .toMutableList()
        names.add(0, getString(R.string.settings_new_collection_row))
        AlertDialog.Builder(this)
            .setTitle(R.string.label_collections)
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply {
                        setHint(R.string.settings_collection_name_hint)
                        setTextColor(Color.WHITE)
                        setHintTextColor(0x66FFFFFF)
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.settings_new_collection)
                        .setView(input)
                        .setPositiveButton(R.string.action_create) { _, _ ->
                            val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                                .createCollection(
                                    app.settings.collections,
                                    input.text?.toString().orEmpty(),
                                )
                            app.updateSettings(app.settings.copy(collections = next))
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                } else {
                    val name = names[which]
                    val count = app.settings.collections[name]?.size ?: 0
                    AlertDialog.Builder(this)
                        .setTitle(name)
                        .setMessage(resources.getQuantityString(
                            R.plurals.count_items,
                            count,
                            count,
                        ))
                        .setItems(
                            arrayOf(
                                getString(R.string.action_edit_members),
                                getString(R.string.action_mirror_to_folder),
                                getString(R.string.action_rename),
                                getString(R.string.action_delete),
                            ),
                        ) { _, action ->
                            when (action) {
                                0 -> com.visorcraft.ghostgalleon.ui.deck.CollectionDialogs
                                    .promptMembers(
                                        this,
                                        app,
                                        name,
                                        labelOf = { k ->
                                            com.visorcraft.ghostgalleon.settings.SlotKey.romId(k)
                                                ?.let { id ->
                                                    app.romEntries.firstOrNull { it.id == id }?.name
                                                } ?: k
                                        },
                                    )
                                1 -> mirrorCollectionToGridFolder(name)
                                2 -> {
                                    val input = EditText(this).apply {
                                        setText(name)
                                        setTextColor(Color.WHITE)
                                    }
                                    AlertDialog.Builder(this)
                                        .setTitle(R.string.action_rename)
                                        .setView(input)
                                        .setPositiveButton(R.string.action_save) { _, _ ->
                                            val next = com.visorcraft.ghostgalleon.library
                                                .CollectionsOps.renameCollection(
                                                    app.settings.collections,
                                                    name,
                                                    input.text?.toString().orEmpty(),
                                                )
                                            app.updateSettings(
                                                app.settings.copy(collections = next),
                                            )
                                        }
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .show()
                                }
                                3 -> {
                                    val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                                        .deleteCollection(app.settings.collections, name)
                                    app.updateSettings(app.settings.copy(collections = next))
                                }
                            }
                        }
                        .setNegativeButton(R.string.action_close, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    /** Collection → grid folder mirror (symmetric with folder → collection). */
    private fun mirrorCollectionToGridFolder(collectionName: String) {
        val live = app.settings
        val members = live.collections[collectionName].orEmpty()
        if (members.isEmpty()) {
            Toast.makeText(this, R.string.deck_empty_collection, Toast.LENGTH_SHORT).show()
            return
        }
        val fid = com.visorcraft.ghostgalleon.settings.Folders.nextId(live.folders)
        val folders = com.visorcraft.ghostgalleon.library.FolderCollectionBridge
            .mirrorCollectionToFolder(
                live.collections,
                collectionName,
                live.folders,
                folderId = fid,
                folderDisplayName = collectionName,
            )
        val folderKey = com.visorcraft.ghostgalleon.settings.Folders.key(fid)
        val slots = com.visorcraft.ghostgalleon.library.CollectionsOps.bulkFillSlots(
            live.gridSlots,
            listOf(folderKey),
        )
        app.updateSettings(live.copy(folders = folders, gridSlots = slots))
        Toast.makeText(this, R.string.deck_mirrored_to_folder, Toast.LENGTH_SHORT).show()
    }

    private fun showDefaultPlayersDialog() {
        val platforms = Platforms.ALL
        val labels = platforms.map { p ->
            val defId = app.settings.defaultPlayers[p.id]
            val def = defId?.let { id -> p.players.firstOrNull { it.id == id } }
                ?: p.player
            getString(R.string.format_label_value, p.displayName, playerListLabel(def))
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_default_players)
            .setItems(labels) { _, which ->
                val platform = platforms[which]
                val playerLabels = platform.players.map { playerListLabel(it) }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(platform.displayName)
                    .setItems(playerLabels) { _, pWhich ->
                        val player = platform.players[pWhich]
                        app.updateSettings(
                            app.settings.copy(
                                defaultPlayers = app.settings.defaultPlayers +
                                    (platform.id to player.id),
                            ),
                        )
                        refreshSettingsUi()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun playerListLabel(player: PlayerTemplate): String =
        playerSettingsLabel(
            player.displayName,
            player.sessionPolicy,
            getString(R.string.settings_player_uses_both_screens),
        )

    private fun showSgdbKeyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(app.settings.sgdbApiKey ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(offTint)
            setHint(R.string.settings_api_key_hint)
        }
        val container = FrameLayout(this).apply {
            val margin = dp(20)
            setPadding(margin, dp(12), margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_sgdb_api_key)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val key = input.text.toString().trim().ifEmpty { null }
                app.updateSettings(app.settings.copy(sgdbApiKey = key))
                refreshSgdbRows()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showRaUsernameDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(app.settings.raUsername ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(offTint)
            setHint(R.string.settings_ra_username_hint)
        }
        val container = FrameLayout(this).apply {
            val margin = dp(20)
            setPadding(margin, dp(12), margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ra_username)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { null }
                app.updateSettings(app.settings.copy(raUsername = name))
                refreshRaRows()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showRaApiKeyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(app.settings.raApiKey ?: "")
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(offTint)
            setHint(R.string.settings_api_key_hint)
        }
        val container = FrameLayout(this).apply {
            val margin = dp(20)
            setPadding(margin, dp(12), margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ra_api_key)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val key = input.text.toString().trim().ifEmpty { null }
                app.updateSettings(app.settings.copy(raApiKey = key))
                refreshRaRows()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** First ON: mutate RA cfg if writable; otherwise show paste lines. Toggle stays on. */
    private fun applyRaNetworkCommandsOn() {
        val path = Platforms.ALL.asSequence()
            .flatMap { it.players.asSequence() }
            .mapNotNull { it.extras["CONFIGFILE"] }
            .firstOrNull()
        val file = path?.let(::File)?.takeIf { it.canWrite() }
        if (file == null) {
            app.updateSettings(app.settings.copy(raNetworkCommands = true))
            showRaNetworkCommandsHelp()
            return
        }
        try {
            val src = file.readText()
            val (out, changed) = RaCfg.enableNetworkCommands(src)
            if (changed) {
                file.writeText(out)
                Log.i("GGSession", "ra-cmd enabled")
            }
            app.updateSettings(
                app.settings.copy(
                    raNetworkCommands = true,
                    raNetworkCmdPort = RaCfg.readPort(out),
                ),
            )
        } catch (_: Exception) {
            app.updateSettings(app.settings.copy(raNetworkCommands = true))
            showRaNetworkCommandsHelp()
        }
    }

    private fun showRaNetworkCommandsHelp() {
        val paste = """
            |network_cmd_enable = "true"
            |network_cmd_port = "55355"
        """.trimMargin()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ra_network_commands)
            .setMessage(getString(R.string.settings_ra_network_commands_help) + "\n\n" + paste)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    /**
     * Demo path: parse a tiny embedded RA progress payload for the currently
     * selected ROM (or first library ROM) so the hero RA line can be verified.
     */
    private fun loadRaSampleForSelection() {
        val romId = SlotKey.romId(app.deckState.selectedKey)
            ?: app.romEntries.firstOrNull { it.visibleInUi }?.id
        if (romId == null) {
            Toast.makeText(this, R.string.settings_no_rom_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val sample = """
            {"ID":1,"Title":"Sample","NumAwardedToUser":3,"NumAchievements":10,"HardcoreMode":0}
        """.trimIndent()
        // Never persist a fake API key — demo only injects progress for the
        // current selection so hero RA line can be verified offline.
        app.setRaProgress(romId, sample)
        val line = RetroAchievements.heroLine(
            app.raProgressFor(romId),
            hasCredentials = true, // sample preview only — key not persisted
        )
        Toast.makeText(
            this,
            line?.let(::resolveText) ?: getString(R.string.settings_ra_sample_loaded),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun onScrapeRowClicked() {
        val job = app.scrapeJob
        if (job.isRunning) {
            job.cancel()
            scrapeValue?.setText(R.string.settings_cancelling)
            return
        }
        val key = app.settings.sgdbApiKey ?: return
        when (
            val decision = com.visorcraft.ghostgalleon.art.ScrapeEnvironment.decision(
                this,
                app.settings,
            )
        ) {
            is com.visorcraft.ghostgalleon.art.ScrapePolicy.Decision.Block -> {
                Toast.makeText(
                    this,
                    resolveText(
                        com.visorcraft.ghostgalleon.art.ScrapePolicy.blockMessage(decision.reason),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            com.visorcraft.ghostgalleon.art.ScrapePolicy.Decision.Allow -> Unit
        }
        if (job.start(key, app.romEntries)) {
            scrapeLabel?.setText(R.string.action_cancel)
            scrapeValue?.setText(R.string.glyph_ellipsis)
        }
    }

    private val romFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val trees = app.settings.romTreeUris
                if (uri.toString() !in trees) {
                    app.updateSettings(app.settings.copy(romTreeUris = trees + uri.toString()))
                }
                refreshFolderRows()
            }
        }

    private var folderRows: LinearLayout? = null

    private fun removeRomFolder(uriString: String) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.updateSettings(app.settings.copy(
            romTreeUris = app.settings.romTreeUris - uriString))
        refreshFolderRows()
    }

    private fun refreshFolderRows() {
        val rows = folderRows ?: return
        rows.removeAllViews()
        app.settings.romTreeUris.forEach { uriString ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(rowLabel(resolveText(TreeLabels.label(uriString))), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                setText(R.string.action_remove)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(accent)
                background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
                val hp = dp(12); val vp = dp(6)
                setPadding(hp, vp, hp, vp)
                setOnClickListener { removeRomFolder(uriString) }
            })
            rows.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
    }

    private fun wallpaperDisplayName(uriString: String): String = runCatching {
        val uri = Uri.parse(uriString)
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: getString(R.string.label_set)
    }.getOrDefault(getString(R.string.label_set))

    private fun refreshWallpaperRow() {
        val uri = app.settings.wallpaperUri
        wallpaperValue?.text = if (uri != null) {
            wallpaperDisplayName(uri)
        } else {
            getString(R.string.action_none)
        }
        wallpaperClear?.visibility = if (uri != null) View.VISIBLE else View.GONE
    }

    private fun refreshRaRows() {
        raUserValue?.text = app.settings.raUsername?.takeIf { it.isNotBlank() }
            ?: getString(R.string.label_not_set)
        raKeyValue?.setText(
            if (!app.settings.raApiKey.isNullOrBlank()) {
                R.string.label_set
            } else {
                R.string.label_not_set
            },
        )
    }

    private val accent get() = app.settings.accentColor

    /** Rebuild the settings tree without an activity recreate. */
    private fun refreshSettingsUi() {
        if (isFinishing || isDestroyed) return
        setContentView(buildContent())
    }

    private fun applyThemeAndRefresh(next: com.visorcraft.ghostgalleon.settings.Settings) {
        val prevScale = ThemePack.resolve(app.settings).fontScale
        app.updateSettings(next)
        val nextScale = ThemePack.resolve(app.settings).fontScale
        if (prevScale != nextScale) recreate() else refreshSettingsUi()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(applyThemeFontScale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings_title)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar(window)
        app.scrapeJob.addListener(scrapeListener)
        refreshSgdbRows()
    }

    override fun onPause() {
        app.scrapeJob.removeListener(scrapeListener)
        super.onPause()
    }

    private fun dp(value: Int): Int =
        com.visorcraft.ghostgalleon.ui.UiDimens.dp(this, value)

    private val integerFormat by lazy(LazyThreadSafetyMode.NONE) {
        NumberFormat.getIntegerInstance(resources.configuration.locales[0])
    }

    private fun commitSeatAnchor(id: String, nx: Float?, ny: Float?) {
        val current = SecondSeatPolicy.anchorsOrDefault(app.settings.raSeatAnchors).toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return
        val prev = current[idx]
        current[idx] = SeatAnchor(id, nx ?: prev.nx, ny ?: prev.ny)
        app.updateSettings(app.settings.copy(raSeatAnchors = current), notify = false)
    }

    private fun formatNumber(value: Int): String = integerFormat.format(value)

    private fun dpF(value: Int): Float =
        com.visorcraft.ghostgalleon.ui.UiDimens.dpF(this, value)

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private val offTint = 0x4DFFFFFF.toInt()

    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase(resources.configuration.locales[0])
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(withAlpha(accent, 0xCC))
        letterSpacing = 0.15f
    }
    private fun sectionCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(this@SettingsActivity)
        val pad = dp(20)
        setPadding(pad, pad, pad, pad)
    }

    private fun rowLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(Color.WHITE)
    }

    /** A 64dp control row: label left, control right. */
    private fun controlRow(label: String, control: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(control)
        }

    private fun accentSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(accent, offTint))
            trackTintList = ColorStateList(
                states, intArrayOf(withAlpha(accent, 0x66), offTint))
            isChecked = checked
            setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        }

    private fun pillDrawable(fill: Int, radiusDp: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dpF(radiusDp)
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    /**
     * Segmented pill control (Default mode / Grid scrolling / chrome preset).
     * Optional [bindSelected] receives a setter so hosts can rebind selection
     * when external state changes without a full recreate.
     * [onSelect] is last so trailing-lambda call sites keep working.
     */
    private fun segmented(
        options: List<Pair<String, String>>, // value -> pill text
        current: String,
        bindSelected: ((setSelected: (String) -> Unit) -> Unit)? = null,
        onSelect: (String) -> Unit,
    ): View {
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = pillDrawable(0xFF1C1C22.toInt(), 20, 0x26FFFFFF)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val pills = mutableMapOf<String, TextView>()
        var selected = current

        fun restyle() {
            pills.forEach { (value, pill) ->
                if (value == selected) {
                    pill.background = pillDrawable(accent, 17)
                    pill.setTextColor(Color.BLACK)
                } else {
                    pill.background = null
                    pill.setTextColor(Color.WHITE)
                }
            }
        }

        options.forEach { (value, text) ->
            val pill = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
                isFocusable = true
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener {
                    if (selected != value) {
                        selected = value
                        onSelect(value)
                    }
                    restyle()
                }
            }
            pills[value] = pill
            track.addView(pill, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        }
        restyle()
        bindSelected?.invoke { value ->
            if (selected != value) {
                selected = value
                restyle()
            }
        }
        return track
    }

    /** Two-pill segmented control for the default mode. */
    private fun modeSegmented(current: UIMode): View = segmented(
        listOf(
            UIMode.GRID.name to getString(R.string.label_grid),
            UIMode.GAME.name to getString(R.string.label_game),
        ),
        current.name,
    ) { app.updateSettings(app.settings.copy(defaultMode = UIMode.valueOf(it))) }

    /** Bound-key chip: card-surface pill, accent text. */
    private fun keyChip(bound: String): TextView = TextView(this).apply {
        text = bound
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(accent)
        background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    /** Wide layout (top Sugar panel / docked): left nav blade. Narrow: dropdown. */
    private fun isWideSettings(): Boolean {
        val dm = resources.displayMetrics
        return dm.widthPixels >= (700f * dm.density).toInt()
    }

    private fun selectPage(page: SettingsPage) {
        currentPage = page
        val host = pageHost ?: return
        host.removeAllViews()
        val body = pageBodies[page] ?: return
        (body.parent as? ViewGroup)?.removeView(body)
        host.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        paintNav()
        pageDropdownLabel?.setText(page.titleRes)
    }

    private fun paintNav() {
        navItems.forEach { (page, view) ->
            val selected = page == currentPage
            view.setTextColor(if (selected) Color.BLACK else Color.WHITE)
            view.background = if (selected) {
                pillDrawable(accent, 14)
            } else {
                null
            }
            view.setTypeface(
                null,
                if (selected) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL,
            )
        }
    }

    private fun buildNavBlade(): View {
        val blade = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF121218.toInt())
                cornerRadius = dpF(20)
            }
        }
        SettingsPage.entries.forEach { page ->
            val item = TextView(this).apply {
                setText(page.titleRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isFocusable = true
                setOnClickListener { selectPage(page) }
            }
            navItems[page] = item
            blade.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) })
        }
        paintNav()
        return blade
    }

    private fun buildPageDropdown(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1C1C22.toInt())
                cornerRadius = dpF(18)
                setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            setOnClickListener { showPagePicker() }
            setOnLongClickListener {
                showSettingsSearch()
                true
            }
        }
        val label = TextView(this).apply {
            setText(currentPage.titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
        }
        pageDropdownLabel = label
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            setText(R.string.glyph_dropdown)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(accent)
        })
        return row
    }

    private fun showSettingsSearch() {
        val input = android.widget.EditText(this).apply {
            setHint(R.string.settings_search_hint)
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF)
        }
        val jumps = settingsSearchIndex()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_search_hint)
            .setView(input)
            .setPositiveButton(R.string.action_search) { _, _ ->
                val hits = SettingsCatalog.matches(input.text.toString(), jumps)
                if (hits.isEmpty()) {
                    Toast.makeText(this, R.string.browse_nothing_to_continue, Toast.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                }
                if (hits.size == 1) {
                    jumpToSettingsPage(hits[0].pageId)
                    return@setPositiveButton
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.settings_search_hint)
                    .setItems(hits.map { it.label }.toTypedArray()) { _, which ->
                        jumpToSettingsPage(hits[which].pageId)
                    }
                    .show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun jumpToSettingsPage(pageId: String) {
        SettingsPage.entries.firstOrNull { it.name == pageId }?.let { selectPage(it) }
    }

    private fun settingsSearchIndex(): List<SettingsJump> = listOf(
        SettingsJump(SettingsCatalog.PAGE_DISPLAY, getString(R.string.settings_page_display_grid),
            "theme display grid chrome wallpaper columns icon card posture hinge flat yield"),
        SettingsJump(SettingsCatalog.PAGE_APPS, getString(R.string.settings_page_apps),
            "apps hidden packages dock"),
        SettingsJump(SettingsCatalog.PAGE_CONTROLS, getString(R.string.settings_page_controls),
            "controls remap deadzone haptics lab"),
        SettingsJump(SettingsCatalog.PAGE_LIBRARY, getString(R.string.settings_page_library),
            "library rom folder rescan hidden collections players retroarch network commands talk ferry save"),
        SettingsJump(SettingsCatalog.PAGE_ART, getString(R.string.settings_page_art),
            "artwork backup export import steamgriddb retroachievements scrape pack"),
        SettingsJump(SettingsCatalog.PAGE_STATS, getString(R.string.settings_page_stats),
            "stats playtime"),
        SettingsJump(SettingsCatalog.PAGE_SYSTEM, getString(R.string.settings_page_system),
            "system topology display black companion oracle"),
        SettingsJump(SettingsCatalog.PAGE_ABOUT, getString(R.string.settings_page_about),
            "about license credits"),
    )

    private fun showPagePicker() {
        val labels = SettingsPage.entries.map { getString(it.titleRes) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_page_picker)
            .setItems(labels) { _, which ->
                selectPage(SettingsPage.entries[which])
            }
            .show()
    }

    private fun buildContent(): View {
        val s = app.settings
        val wide = isWideSettings()

        SettingsPage.entries.forEach { page ->
            pageBodies[page] = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(24))
            }
        }

        fun addSection(page: SettingsPage, title: String, card: LinearLayout) {
            val root = pageBodies.getValue(page)
            root.addView(sectionHeader(title), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(24)
                bottomMargin = dp(10)
            })
            root.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            setText(R.string.glyph_back)
            scaleX = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                -1f
            } else {
                1f
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.action_back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
            setText(R.string.settings_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            setPadding(dp(16), 0, 0, 0)
        })
        shell.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        shell.addView(View(this).apply {
            setBackgroundColor(accent)
        }, LinearLayout.LayoutParams(dp(40), dp(2)).apply {
            marginStart = dp(64)
            topMargin = dp(6)
            bottomMargin = dp(12)
        })

        if (!wide) {
            shell.addView(buildPageDropdown(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }

        fun toggle(card: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            card.addView(controlRow(label, accentSwitch(checked, onChange)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }

        fun seek(
            card: LinearLayout,
            label: String,
            value: Int,
            min: Int,
            max: Int,
            format: (Int) -> String = { formatNumber(it) },
            onChange: (Int) -> Unit,
        ) {
            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labelRow.addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val valueView = TextView(this).apply {
                text = format(value)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(accent)
            }
            labelRow.addView(valueView)
            card.addView(labelRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            card.addView(SeekBar(this).apply {
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                progressBackgroundTintList = ColorStateList.valueOf(offTint)
                minimumHeight = dp(32)
                this.max = max - min
                progress = value - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (fromUser) valueView.text = format(p + min)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar) = onChange(sb.progress + min)
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

        val displayCard = sectionCard()
        toggle(displayCard, getString(R.string.settings_show_hints), s.showHints) {
            app.updateSettings(app.settings.copy(showHints = it))
        }
        displayCard.addView(controlRow(
            getString(R.string.settings_default_mode),
            modeSegmented(s.defaultMode),
        ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        val themeOptions = ThemePack.BUILTINS.map {
            it.id to themeName(it.id).uppercase(resources.configuration.locales[0])
        }
        val themeCurrent = ThemePack.resolve(s).id.let { id ->
            if (ThemePack.BUILTINS.any { it.id == id }) id else ThemePack.GHOST.id
        }
        displayCard.addView(controlRow(
            getString(R.string.settings_theme),
            segmented(themeOptions, themeCurrent) { packId ->
                val tokens = ThemePack.byId(packId)
                applyThemeAndRefresh(ThemePack.applyToSettings(app.settings, tokens))
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val themeImportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { themeJsonPicker.launch(arrayOf("application/json", "text/*", "*/*")) }
            setOnLongClickListener {
                if (app.settings.themeCustomJson != null) {
                    applyThemeAndRefresh(
                        ThemePack.applyToSettings(app.settings, ThemePack.GHOST),
                    )
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.settings_custom_theme_cleared,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                true
            }
        }
        themeImportRow.addView(rowLabel(getString(R.string.settings_import_theme)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        themeImportRow.addView(TextView(this).apply {
            setText(if (s.themeCustomJson != null) {
                R.string.label_custom
            } else {
                R.string.label_saf
            })
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (s.themeCustomJson != null) accent else 0x66FFFFFF.toInt())
        })
        displayCard.addView(themeImportRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val profileOptions = listOf(
            "auto" to getString(R.string.label_auto),
            "onex-sugar" to getString(R.string.label_sugar),
            "generic-dual" to getString(R.string.label_dual),
            "single" to getString(R.string.label_single),
        )
        displayCard.addView(controlRow(
            getString(R.string.settings_device_profile),
            segmented(profileOptions, s.deviceProfileId) { id ->
                app.updateSettings(app.settings.copy(
                    deviceProfileId = id,
                    userPinnedPrimaryId = null,
                ))
                app.refreshDisplayConfig()
                refreshSettingsUi()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val interactiveOptions = listOf(
            "auto" to getString(R.string.label_auto),
            "default" to getString(R.string.label_default),
            "secondary" to getString(R.string.label_secondary),
        )
        val interactiveCurrent = when {
            s.interactiveDisplayMode.startsWith("id:") -> "auto"
            else -> s.interactiveDisplayMode
        }
        displayCard.addView(controlRow(
            getString(R.string.settings_interactive_display),
            segmented(interactiveOptions, interactiveCurrent) { mode ->
                app.updateSettings(app.settings.copy(
                    interactiveDisplayMode = mode,
                    userPinnedPrimaryId = null,
                ))
                app.refreshDisplayConfig()
                refreshSettingsUi()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val orientOptions = listOf(
            "auto" to getString(R.string.label_auto),
            "sensor_landscape" to getString(R.string.label_sensor),
            "lock_landscape" to getString(R.string.label_lock),
        )
        displayCard.addView(controlRow(
            getString(R.string.settings_orientation),
            segmented(orientOptions, s.orientationMode) { mode ->
                app.updateSettings(
                    app.settings.copy(
                        orientationMode = mode,
                        angleLock = mode == "lock_landscape",
                        gyroEnabled = mode != "lock_landscape",
                    ),
                )
                refreshSettingsUi()
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val resetDisplayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                app.updateSettings(app.settings.copy(
                    userPinnedPrimaryId = null,
                    interactiveDisplayMode = "auto",
                    deviceProfileId = "auto",
                ))
                app.refreshDisplayConfig()
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.settings_display_roles_reset,
                    Toast.LENGTH_SHORT,
                ).show()
                refreshSettingsUi()
            }
        }
        resetDisplayRow.addView(rowLabel(getString(R.string.settings_reset_display_roles)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        displayCard.addView(resetDisplayRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        addSection(SettingsPage.DISPLAY_GRID, getString(R.string.settings_section_display), displayCard)

        if (app.displayConfig.mode != SurfaceMode.SINGLE) {
            val postureCard = sectionCard()
            toggle(postureCard, getString(R.string.settings_posture), s.postureAware) { on ->
                app.updateSettings(app.settings.copy(postureAware = on))
            }
            toggle(
                postureCard,
                getString(R.string.settings_posture_suggest_yield),
                s.postureSuggestYield,
            ) { on ->
                app.updateSettings(app.settings.copy(postureSuggestYield = on))
            }
            addSection(
                SettingsPage.DISPLAY_GRID,
                getString(R.string.settings_posture),
                postureCard,
            )
        }

        val chrome = s.browseChrome
        val chromeCard = sectionCard()
        fun chromePresetId(c: com.visorcraft.ghostgalleon.settings.BrowseChrome): String =
            c.presetId()
        val presetOptions = listOf(
            com.visorcraft.ghostgalleon.settings.BrowseChrome.PRESET_MINIMAL to
                getString(R.string.label_minimal),
            com.visorcraft.ghostgalleon.settings.BrowseChrome.PRESET_CUSTOM to
                getString(R.string.label_custom),
            com.visorcraft.ghostgalleon.settings.BrowseChrome.PRESET_FULL to
                getString(R.string.label_full),
        )
        var rebindChromePreset: ((String) -> Unit)? = null
        // Programmatic switch rebind must not fire updateSettings (would thrash
        // and write partial flags from stale isChecked after Minimal/Full).
        var suppressChromeSwitchCallbacks = false
        val chromeSwitchRebinds =
            mutableListOf<(com.visorcraft.ghostgalleon.settings.BrowseChrome) -> Unit>()
        val powerHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun rebindChromeUi(c: com.visorcraft.ghostgalleon.settings.BrowseChrome) {
            rebindChromePreset?.invoke(chromePresetId(c))
            suppressChromeSwitchCallbacks = true
            try {
                chromeSwitchRebinds.forEach { it(c) }
            } finally {
                suppressChromeSwitchCallbacks = false
            }
            powerHost.visibility = if (c.powerRailsPanelVisible()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        chromeCard.addView(controlRow(
            getString(R.string.settings_chrome_preset),
            segmented(
                presetOptions,
                chromePresetId(chrome),
                bindSelected = { set -> rebindChromePreset = set },
            ) { id ->
                when (id) {
                    com.visorcraft.ghostgalleon.settings.BrowseChrome.PRESET_FULL -> {
                        val next = com.visorcraft.ghostgalleon.settings.BrowseChrome.FULL
                        val prev = app.settings.browseChrome
                        app.updateSettings(
                            app.settings.copy(browseChrome = next),
                            chromeOnly = next.allowsInPlaceChromeUpdate(prev),
                        )
                        rebindChromeUi(next)
                    }
                    com.visorcraft.ghostgalleon.settings.BrowseChrome.PRESET_MINIMAL -> {
                        val next = com.visorcraft.ghostgalleon.settings.BrowseChrome.MINIMAL
                        val prev = app.settings.browseChrome
                        app.updateSettings(
                            app.settings.copy(browseChrome = next),
                            chromeOnly = next.allowsInPlaceChromeUpdate(prev),
                        )
                        rebindChromeUi(next)
                    }
                    else -> {
                        // Custom is sticky when flags already differ; selecting
                        // Custom while Minimal/Full is a no-op on flags (user
                        // toggles power rails below to leave preset).
                        rebindChromePreset?.invoke(chromePresetId(app.settings.browseChrome))
                    }
                }
            },
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        chromeCard.addView(TextView(this).apply {
            setText(R.string.settings_chrome_custom_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        // Core always-visible toggles + power rails: keep Switch refs so
        // Minimal/Full rebind isChecked without a full Settings recreate.
        fun chromeFlag(
            host: LinearLayout,
            label: String,
            get: (com.visorcraft.ghostgalleon.settings.BrowseChrome) -> Boolean,
            set: (com.visorcraft.ghostgalleon.settings.BrowseChrome, Boolean) ->
                com.visorcraft.ghostgalleon.settings.BrowseChrome,
        ) {
            val sw = accentSwitch(get(chrome)) { on ->
                if (suppressChromeSwitchCallbacks) return@accentSwitch
                val prev = app.settings.browseChrome
                val next = set(prev, on)
                // Status pill / Resume chip need full dual paint; rails stay CHROME.
                app.updateSettings(
                    app.settings.copy(browseChrome = next),
                    chromeOnly = next.allowsInPlaceChromeUpdate(prev),
                )
                rebindChromePreset?.invoke(chromePresetId(next))
                powerHost.visibility = if (next.powerRailsPanelVisible()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            host.addView(
                controlRow(label, sw),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(64),
                ),
            )
            chromeSwitchRebinds.add { c ->
                val want = get(c)
                if (sw.isChecked != want) sw.isChecked = want
            }
        }
        chromeFlag(
            chromeCard,
            getString(R.string.settings_platform_chips),
            get = { it.platformChips },
            set = { c, v -> c.copy(platformChips = v) },
        )
        chromeFlag(
            chromeCard,
            getString(R.string.settings_collection_rails),
            get = { it.collectionRails },
            set = { c, v -> c.copy(collectionRails = v) },
        )
        // Power rails expander — collapsed when currently minimal.
        powerHost.visibility = if (chrome.powerRailsPanelVisible()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val expander = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                powerHost.visibility =
                    if (powerHost.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        expander.addView(rowLabel(getString(R.string.settings_chrome_power_rails)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        expander.addView(TextView(this).apply {
            text = getString(R.string.settings_chrome_expand)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(accent)
        })
        chromeCard.addView(expander, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        chromeFlag(
            powerHost, getString(R.string.settings_installed_rail),
            get = { it.installedRail }, set = { c, v -> c.copy(installedRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_games_rail),
            get = { it.gamesRail }, set = { c, v -> c.copy(gamesRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_top_rail),
            get = { it.topRail }, set = { c, v -> c.copy(topRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_today_rail),
            get = { it.todayRail }, set = { c, v -> c.copy(todayRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_week_rail),
            get = { it.weekRail }, set = { c, v -> c.copy(weekRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_month_rail),
            get = { it.monthRail }, set = { c, v -> c.copy(monthRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_alpha_rail),
            get = { it.alphaRail }, set = { c, v -> c.copy(alphaRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_unplayed_rail),
            get = { it.unplayedRail }, set = { c, v -> c.copy(unplayedRail = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_random_chip),
            get = { it.randomChip }, set = { c, v -> c.copy(randomChip = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_genre_chips),
            get = { it.genreChips }, set = { c, v -> c.copy(genreChips = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_developer_chips),
            get = { it.developerChips }, set = { c, v -> c.copy(developerChips = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_year_chips),
            get = { it.yearChips }, set = { c, v -> c.copy(yearChips = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_launchable_only),
            get = { it.launchableOnly }, set = { c, v -> c.copy(launchableOnly = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_status_pill),
            get = { it.deckStatusPill }, set = { c, v -> c.copy(deckStatusPill = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_resume_chip),
            get = { it.resumeChip }, set = { c, v -> c.copy(resumeChip = v) },
        )
        chromeFlag(
            powerHost, getString(R.string.settings_quick_browse),
            get = { it.quickPanelBrowse }, set = { c, v -> c.copy(quickPanelBrowse = v) },
        )
        chromeCard.addView(powerHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addSection(
            SettingsPage.DISPLAY_GRID,
            getString(R.string.settings_chrome_section),
            chromeCard,
        )

        val companionCard = sectionCard()
        val roleOptions = listOf(
            CompanionRole.HERO.name to getString(R.string.role_hero),
            CompanionRole.NOW_PLAYING.name to getString(R.string.label_now),
            CompanionRole.PERF_HUD.name to getString(R.string.label_perf),
            CompanionRole.PINNED_APP.name to getString(R.string.label_pin),
        )
        companionCard.addView(
            controlRow(
                getString(R.string.settings_companion_role),
                segmented(roleOptions, s.companionRole) { next ->
                    app.updateSettings(app.settings.copy(companionRole = next))
                },
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)),
        )
        val pinRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showPinnedCompanionPicker() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(companionPinnedPackage = null))
                Toast.makeText(this@SettingsActivity, R.string.settings_pin_cleared, Toast.LENGTH_SHORT).show()
                refreshSettingsUi()
                true
            }
        }
        pinRow.addView(rowLabel(getString(R.string.settings_pinned_companion)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pinRow.addView(TextView(this).apply {
            text = s.companionPinnedPackage?.let { pkg ->
                appLabel(pkg)
            } ?: getString(R.string.settings_no_pin)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        companionCard.addView(pinRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        companionCard.addView(TextView(this).apply {
            setText(R.string.settings_pin_help)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        addSection(SettingsPage.DISPLAY_GRID, getString(R.string.settings_companion_section), companionCard)

        val gridCard = sectionCard()
        gridCard.addView(controlRow(getString(R.string.settings_grid_scrolling), segmented(
            listOf(
                "vertical" to getString(R.string.label_vertical),
                "horizontal" to getString(R.string.label_horizontal),
            ),
            s.gridDirection,
        ) { app.updateSettings(app.settings.copy(gridDirection = it)) }),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        seek(gridCard, getString(R.string.settings_grid_columns), s.gridColumns, 3, 8) {
            app.updateSettings(app.settings.copy(gridColumns = it))
        }
        seek(gridCard, getString(R.string.settings_icon_size), s.iconSizeDp, 48, 128) {
            app.updateSettings(app.settings.copy(iconSizeDp = it))
        }
        seek(gridCard, getString(R.string.settings_card_size), s.cardSizeDp, 120, 320) {
            app.updateSettings(
                app.settings.copy(cardSizeDp = it),
                chromeOnly = true,
            )
        }
        toggle(gridCard, getString(R.string.settings_show_app_names), s.showLabels) {
            app.updateSettings(app.settings.copy(showLabels = it))
        }
        val wallpaperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { wallpaperPicker.launch(arrayOf("image/*")) }
        }
        wallpaperRow.addView(rowLabel(getString(R.string.settings_grid_wallpaper)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        wallpaperValue = valueView
        wallpaperRow.addView(valueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
        val clearView = TextView(this).apply {
            setText(R.string.action_clear)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
            val hp = dp(12); val vp = dp(6)
            setPadding(hp, vp, hp, vp)
            setOnClickListener {
                DeckWallpaper.dropCache()
                app.updateSettings(app.settings.copy(wallpaperUri = null))
                refreshWallpaperRow()
            }
        }
        wallpaperClear = clearView
        wallpaperRow.addView(clearView)
        refreshWallpaperRow()
        gridCard.addView(wallpaperRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        addSection(SettingsPage.DISPLAY_GRID, getString(R.string.settings_grid_section), gridCard)

        val appsCard = sectionCard()
        val hiddenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showHiddenAppsDialog() }
        }
        hiddenRow.addView(rowLabel(getString(R.string.settings_hidden_apps)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hiddenValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        hiddenRow.addView(hiddenValue)
        appsCard.addView(hiddenRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showDockDialog() }
        }
        dockRow.addView(rowLabel(getString(R.string.label_dock)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dockValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        dockRow.addView(dockValue, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        appsCard.addView(dockRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val packageYieldRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showPackageYieldDialog() }
        }
        packageYieldRow.addView(
            rowLabel(getString(R.string.settings_package_yield)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        packageYieldValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        packageYieldRow.addView(packageYieldValue)
        appsCard.addView(
            packageYieldRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)),
        )
        refreshAppsRows()
        addSection(SettingsPage.APPS, getString(R.string.settings_page_apps), appsCard)

        val controlsCard = sectionCard()
        toggle(controlsCard, getString(R.string.settings_haptics), s.haptics) {
            app.updateSettings(app.settings.copy(haptics = it))
        }
        toggle(controlsCard, getString(R.string.settings_input_assist), s.inputAssistEnabled) { on ->
            app.updateSettings(app.settings.copy(inputAssistEnabled = on))
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        controlsCard.addView(TextView(this).apply {
            setText(R.string.settings_input_assist_open_system)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        toggle(controlsCard, getString(R.string.settings_winlator_cockpit), s.winlatorCockpit) {
            app.updateSettings(app.settings.copy(winlatorCockpit = it))
        }
        toggle(controlsCard, getString(R.string.settings_second_seat), s.raSecondSeat) {
            app.updateSettings(app.settings.copy(raSecondSeat = it))
        }
        // empty stored list = DEFAULT_ANCHORS (SNES-like lower-right 40%).
        val seatLayoutHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun bindSeatLayout() {
            seatLayoutHost.removeAllViews()
            val resetRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                setOnClickListener {
                    app.updateSettings(
                        app.settings.copy(raSeatAnchors = emptyList()),
                        notify = false,
                    )
                    bindSeatLayout()
                }
            }
            resetRow.addView(
                rowLabel(getString(R.string.settings_second_seat_layout)),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            resetRow.addView(TextView(this).apply {
                setText(R.string.action_clear)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(accent)
            })
            seatLayoutHost.addView(
                resetRow,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)),
            )
            val shown = SecondSeatPolicy.anchorsOrDefault(app.settings.raSeatAnchors)
            for (anchor in shown) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(
                    TextView(this).apply {
                        text = anchor.id
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(Color.WHITE)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                fun coordField(value: Float, write: (Float) -> Unit): EditText =
                    EditText(this).apply {
                        setText(value.toString())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(Color.WHITE)
                        inputType = InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_FLAG_DECIMAL
                        isSingleLine = true
                        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) return@OnFocusChangeListener
                            val parsed = text?.toString()?.toFloatOrNull()?.coerceIn(0f, 1f)
                                ?: return@OnFocusChangeListener
                            write(parsed)
                        }
                    }
                row.addView(
                    coordField(anchor.nx) { nx ->
                        commitSeatAnchor(anchor.id, nx, null)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                row.addView(
                    coordField(anchor.ny) { ny ->
                        commitSeatAnchor(anchor.id, null, ny)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                seatLayoutHost.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
        bindSeatLayout()
        controlsCard.addView(
            seatLayoutHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val labRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ControllerLabActivity::class.java))
            }
        }
        labRow.addView(rowLabel(getString(R.string.settings_controller_lab)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        labRow.addView(TextView(this).apply {
            setText(R.string.action_open)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        controlsCard.addView(labRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        seek(
            controlsCard,
            getString(R.string.settings_stick_deadzone),
            s.stickDeadzone,
            20,
            80,
            format = { n -> getString(R.string.format_percent, n) },
        ) { n ->
            app.updateSettings(app.settings.copy(stickDeadzone = n), notify = false)
        }
        remappable.forEach { action ->
            val bound = app.settings.keyMap.entries
                .firstOrNull { it.value == action }?.key?.let {
                    getString(R.string.format_keycode, it)
                } ?: getString(R.string.settings_unbound)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
            }
            row.addView(rowLabel(resolveText(action.label())), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val chip = keyChip(bound)
            row.addView(chip)
            row.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (captureTarget != action) {
                    row.background = if (hasFocus) {
                        GradientDrawable().apply {
                            cornerRadius = dpF(16)
                            setStroke(dp(2), accent)
                        }
                    } else {
                        null
                    }
                }
            }
            row.setOnClickListener {
                captureTarget = action
                captureLabel = chip
                chip.setText(R.string.settings_press_button)
                chip.setTextColor(Color.BLACK)
                chip.background = pillDrawable(accent, 14)
                capturePulse?.cancel()
                capturePulse = ObjectAnimator.ofFloat(chip, View.ALPHA, 1f, 0.35f).apply {
                    duration = 500
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
            controlsCard.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
        addSection(SettingsPage.CONTROLS, getString(R.string.settings_page_controls), controlsCard)

        val libraryCard = sectionCard()
        val artCard = sectionCard()
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        folderRows = rows
        libraryCard.addView(rows, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        refreshFolderRows()
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { romFolderPicker.launch(null) }
        }
        addRow.addView(rowLabel(getString(R.string.settings_add_rom_folder)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addRow.addView(TextView(this).apply {
            setText(R.string.glyph_add)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        libraryCard.addView(addRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val hiddenRomsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showHiddenRomsDialog() }
        }
        hiddenRomsRow.addView(rowLabel(getString(R.string.settings_hidden_roms)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hiddenRomsValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            text = formatNumber(app.settings.hiddenRomIds.size)
        }
        hiddenRomsRow.addView(hiddenRomsValue)
        libraryCard.addView(hiddenRomsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val rescanLabel = rowLabel(getString(R.string.settings_rescan_library))
        fun startRescan(force: Boolean) {
            if (scanning) return
            scanning = true
            rescanLabel.setText(if (force) {
                R.string.settings_full_scan
            } else {
                R.string.settings_scanning
            })
            app.romLibrary.rescan(
                this@SettingsActivity,
                app.settings,
                force = force,
                onProgress = progress@{ done, total ->
                    if (isFinishing || isDestroyed) return@progress
                    rescanLabel.text = resolveText(
                        com.visorcraft.ghostgalleon.rom.RescanFeedback.progressLabel(
                            done, total, force,
                        ),
                    )
                },
            ) { result ->
                scanning = false
                rescanLabel.setText(R.string.settings_rescan_library)
                app.noteRescanOutcome(result)
                if (result is RomLibrary.RescanResult.Success) {
                    app.publishRomEntries(result.entries)
                }
                if (isFinishing || isDestroyed) return@rescan
                when (result) {
                    is RomLibrary.RescanResult.Success -> {
                        val msg = resolveText(
                            com.visorcraft.ghostgalleon.rom.RescanFeedback.successMessage(
                                entryCount = result.entries.size,
                                skippedCleanTrees = result.skippedCleanTrees,
                                scannedTrees = result.scannedTrees,
                                retainedUnreadableTrees = result.retainedUnreadableTrees,
                            ),
                        )
                        Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                    }
                    RomLibrary.RescanResult.Unreadable ->
                        Toast.makeText(this@SettingsActivity,
                            getString(R.string.settings_card_unreadable),
                            Toast.LENGTH_LONG).show()
                }
            }
        }
        val rescanRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { startRescan(force = false) }
            setOnLongClickListener {
                startRescan(force = true)
                true
            }
        }
        rescanRow.addView(rescanLabel, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rescanRow.addView(TextView(this).apply {
            setText(R.string.settings_hold_full)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
        })
        libraryCard.addView(rescanRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val pinFavsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                val keys = app.settings.favorites.toList()
                if (keys.isEmpty()) {
                    Toast.makeText(this@SettingsActivity,
                        R.string.browse_no_favorites, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val filled = com.visorcraft.ghostgalleon.library.CollectionsOps
                    .bulkFillSlots(app.settings.gridSlots, keys)
                app.updateSettings(app.settings.copy(gridSlots = filled))
                Toast.makeText(this@SettingsActivity,
                    resources.getQuantityString(
                        R.plurals.count_pinned_favorites_grid,
                        keys.size,
                        keys.size,
                    ),
                    Toast.LENGTH_SHORT).show()
            }
        }
        pinFavsRow.addView(
            rowLabel(getString(R.string.settings_pin_favorites)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        libraryCard.addView(pinFavsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val collectionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showCollectionsDialog() }
        }
        collectionsRow.addView(rowLabel(getString(R.string.label_collections)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        collectionsRow.addView(TextView(this).apply {
            text = formatNumber(app.settings.collections.size)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(collectionsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val playersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showDefaultPlayersDialog() }
        }
        playersRow.addView(rowLabel(getString(R.string.settings_default_players)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playersRow.addView(TextView(this).apply {
            text = if (app.settings.defaultPlayers.isEmpty()) {
                getString(R.string.settings_system_defaults)
            } else {
                resources.getQuantityString(
                    R.plurals.count_set,
                    app.settings.defaultPlayers.size,
                    app.settings.defaultPlayers.size,
                )
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(playersRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        var raHandoffSaveRow: View? = null
        var raHandoffSaveSwitch: Switch? = null
        fun refreshRaHandoffSaveEnabled() {
            val talk = app.settings.raNetworkCommands
            raHandoffSaveRow?.isEnabled = talk
            raHandoffSaveRow?.alpha = if (talk) 1f else 0.5f
            raHandoffSaveSwitch?.isEnabled = talk
        }
        toggle(libraryCard, getString(R.string.settings_stack_clones), s.stackClones) { on ->
            app.updateSettings(app.settings.copy(stackClones = on))
        }
        toggle(libraryCard, getString(R.string.settings_ra_network_commands), s.raNetworkCommands) { on ->
            if (on) {
                applyRaNetworkCommandsOn()
            } else {
                app.updateSettings(app.settings.copy(raNetworkCommands = false))
            }
            refreshRaHandoffSaveEnabled()
        }
        val handoffSwitch = accentSwitch(s.raHandoffSave) { on ->
            if (!app.settings.raNetworkCommands) return@accentSwitch
            app.updateSettings(app.settings.copy(raHandoffSave = on))
        }
        raHandoffSaveSwitch = handoffSwitch
        val handoffRow = controlRow(
            getString(R.string.settings_ra_handoff_save),
            handoffSwitch,
        )
        raHandoffSaveRow = handoffRow
        libraryCard.addView(
            handoffRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)),
        )
        refreshRaHandoffSaveEnabled()
        toggle(libraryCard, getString(R.string.settings_ra_cinema), s.raCinemaEnabled) { on ->
            app.updateSettings(app.settings.copy(raCinemaEnabled = on))
        }
        toggle(libraryCard, getString(R.string.settings_ra_theater), s.raTheaterEnabled) { on ->
            app.updateSettings(app.settings.copy(raTheaterEnabled = on))
        }
        toggle(libraryCard, getString(R.string.settings_save_ferry), s.saveFerryEnabled) { on ->
            app.updateSettings(app.settings.copy(saveFerryEnabled = on))
        }
        toggle(libraryCard, getString(R.string.settings_ram_lenses), s.ramLensesEnabled) { on ->
            app.updateSettings(app.settings.copy(ramLensesEnabled = on))
            app.reloadLenses()
        }
        toggle(libraryCard, getString(R.string.settings_ram_trackers), s.ramTrackersEnabled) { on ->
            app.updateSettings(app.settings.copy(ramTrackersEnabled = on))
        }
        val lensPackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                lensPackLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(ramLensPackUri = null))
                app.reloadLenses()
                refreshSettingsUi()
                true
            }
        }
        lensPackRow.addView(
            rowLabel(getString(R.string.settings_import_lens_pack)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        lensPackRow.addView(TextView(this).apply {
            text = if (s.ramLensPackUri.isNullOrBlank()) {
                getString(R.string.action_none)
            } else {
                getString(R.string.action_open)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        libraryCard.addView(
            lensPackRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)),
        )
        val raUserRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showRaUsernameDialog() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(raUsername = null))
                Toast.makeText(this@SettingsActivity,
                    R.string.settings_ra_username_cleared, Toast.LENGTH_SHORT).show()
                refreshRaRows()
                true
            }
        }
        raUserRow.addView(rowLabel(getString(R.string.settings_ra_username)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raUserRow.addView(TextView(this).apply {
            raUserValue = this
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        artCard.addView(raUserRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val raKeyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showRaApiKeyDialog() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(raApiKey = null))
                Toast.makeText(this@SettingsActivity,
                    R.string.settings_ra_api_key_cleared, Toast.LENGTH_SHORT).show()
                refreshRaRows()
                true
            }
        }
        raKeyRow.addView(rowLabel(getString(R.string.settings_ra_api_key)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raKeyRow.addView(TextView(this).apply {
            raKeyValue = this
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        artCard.addView(raKeyRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        refreshRaRows()
        if (BuildConfig.DEBUG) {
            val raSampleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                setOnClickListener { loadRaSampleForSelection() }
            }
            raSampleRow.addView(rowLabel(getString(R.string.settings_load_ra_sample)), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            raSampleRow.addView(TextView(this).apply {
                setText(R.string.settings_demo)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0x66FFFFFF.toInt())
            })
            artCard.addView(raSampleRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showSgdbKeyDialog() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(sgdbApiKey = null))
                refreshSgdbRows()
                Toast.makeText(this@SettingsActivity,
                    R.string.settings_api_key_cleared, Toast.LENGTH_SHORT).show()
                true
            }
        }
        keyRow.addView(rowLabel(getString(R.string.settings_sgdb_api_key)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val keyValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        sgdbKeyValue = keyValue
        keyRow.addView(keyValue)
        artCard.addView(keyRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val datRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                arcadeDatPicker.launch(arrayOf("application/xml", "text/xml", "text/*", "*/*"))
            }
            setOnLongClickListener {
                app.clearArcadeDat()
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.settings_arcade_dat_cleared,
                    Toast.LENGTH_SHORT,
                ).show()
                refreshSettingsUi()
                true
            }
        }
        datRow.addView(rowLabel(getString(R.string.settings_import_arcade_dat)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        datRow.addView(TextView(this).apply {
            val n = app.arcadeDatCount()
            val bundled = com.visorcraft.ghostgalleon.rom.ArcadeTitles.bundledCount()
            text = when {
                n > 0 -> n.toString()
                bundled > 0 -> getString(R.string.settings_arcade_dat_bundled, bundled)
                else -> getString(R.string.action_none)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        artCard.addView(datRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val downloadRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { onScrapeRowClicked() }
        }
        val downloadLabel = rowLabel(getString(R.string.settings_download_artwork))
        scrapeLabel = downloadLabel
        downloadRow.addView(downloadLabel, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val downloadValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        }
        scrapeValue = downloadValue
        downloadRow.addView(downloadValue)
        scrapeRow = downloadRow
        artCard.addView(downloadRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        toggle(
            artCard,
            getString(R.string.settings_scrape_wifi_only),
            s.scrapeWifiOnly,
        ) { on ->
            app.updateSettings(app.settings.copy(scrapeWifiOnly = on), notify = false)
        }
        seek(
            artCard,
            getString(R.string.settings_scrape_battery),
            s.scrapePauseBelowBattery,
            0,
            50,
            format = { n ->
                resolveText(com.visorcraft.ghostgalleon.art.ScrapePolicy.floorLabel(n))
            },
        ) { n ->
            app.updateSettings(app.settings.copy(scrapePauseBelowBattery = n), notify = false)
        }
        refreshSgdbRows()

        val exportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { exportLauncher.launch(getString(R.string.file_settings_export)) }
        }
        exportRow.addView(rowLabel(getString(R.string.settings_export)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        artCard.addView(exportRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val importRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        }
        importRow.addView(rowLabel(getString(R.string.settings_import)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        artCard.addView(importRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val exportArtRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { exportArtLauncher.launch(getString(R.string.file_art_export)) }
        }
        exportArtRow.addView(rowLabel(getString(R.string.settings_export_art)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        artCard.addView(exportArtRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val importArtRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { importArtLauncher.launch(arrayOf("application/zip", "*/*")) }
        }
        importArtRow.addView(rowLabel(getString(R.string.settings_import_art)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        artCard.addView(importArtRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val packRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                platformPackLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
            setOnLongClickListener {
                app.platformPackStore.clear()
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_platform_pack_cleared),
                    Toast.LENGTH_SHORT,
                ).show()
                refreshSettingsUi()
                true
            }
        }
        packRow.addView(rowLabel(getString(R.string.settings_import_platform_pack)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        packRow.addView(TextView(this).apply {
            text = if (app.platformPackStore.hasPack()) {
                resources.getQuantityString(
                    R.plurals.count_packs,
                    Platforms.packOverlay().size,
                    Platforms.packOverlay().size,
                )
            } else {
                getString(R.string.action_none)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
        })
        artCard.addView(packRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val bundledCount = listBundledPackAssets().size
        val examplePackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { showBundledPackCatalog() }
        }
        examplePackRow.addView(rowLabel(getString(R.string.settings_bundled_packs)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        examplePackRow.addView(TextView(this).apply {
            text = if (bundledCount > 0) {
                resources.getQuantityString(R.plurals.count_packs, bundledCount, bundledCount)
            } else {
                getString(R.string.action_none)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(accent)
        })
        artCard.addView(examplePackRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val setupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                app.updateSettings(app.settings.copy(
                    setupDismissed = false,
                    chromeDiscoverDismissed = false,
                ))
                Toast.makeText(this@SettingsActivity,
                    R.string.settings_setup_reset_done, Toast.LENGTH_SHORT).show()
            }
        }
        setupRow.addView(rowLabel(getString(R.string.settings_reset_setup)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        libraryCard.addView(setupRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val chromeDiscoverRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                app.updateSettings(app.settings.copy(chromeDiscoverDismissed = false))
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.settings_reset_chrome_discover,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        chromeDiscoverRow.addView(
            rowLabel(getString(R.string.settings_reset_chrome_discover)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        libraryCard.addView(chromeDiscoverRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        addSection(SettingsPage.LIBRARY, getString(R.string.settings_page_library), libraryCard)
        addSection(SettingsPage.ART_DATA, getString(R.string.settings_page_art), artCard)

        val statsCard = sectionCard()
        val most = com.visorcraft.ghostgalleon.library.LibraryStats.mostPlayed(
            app.settings.playtimeMs, limit = 12,
        )
        val recent = com.visorcraft.ghostgalleon.library.LibraryStats.recentlyPlayed(
            app.settings.lastLaunchedMs, limit = 12,
        )
        if (!com.visorcraft.ghostgalleon.library.LibraryStats.hasAnySessions(
                app.settings.playtimeMs, app.settings.lastLaunchedMs,
            )
        ) {
            statsCard.addView(TextView(this).apply {
                setText(R.string.stats_no_sessions)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(0x99FFFFFF.toInt())
                setPadding(0, dp(8), 0, dp(8))
            })
        } else {
            statsCard.addView(sectionHeader(getString(R.string.stats_most_played)))
            most.forEach { row ->
                statsCard.addView(statRow(labelForStatsKey(row.key),
                    resolveText(
                        com.visorcraft.ghostgalleon.library.SessionMath.formatPlaytime(row.score),
                    )))
            }
            if (most.isEmpty()) {
                statsCard.addView(TextView(this).apply {
                    setText(R.string.browse_no_playtime)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF.toInt())
                })
            }
            statsCard.addView(sectionHeader(getString(R.string.stats_recently_played)))
            recent.forEach { row ->
                val whenLabel = com.visorcraft.ghostgalleon.library.SessionMath.formatLastPlayed(
                    row.score, System.currentTimeMillis(),
                )?.let(::resolveText) ?: getString(R.string.glyph_dash)
                statsCard.addView(statRow(labelForStatsKey(row.key), whenLabel))
            }
            if (recent.isEmpty()) {
                statsCard.addView(TextView(this).apply {
                    setText(R.string.stats_no_launches)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF.toInt())
                })
            }
        }
        addSection(SettingsPage.STATS, getString(R.string.settings_page_stats), statsCard)
        val systemCard = sectionCard()
        val topo = app.refreshDisplayConfig()
        val systemCardExtra = systemCard
        val effectiveProfile = DeviceProfileCatalog.effective(
            app.settings.deviceProfileId,
            com.visorcraft.ghostgalleon.display.AndroidDisplayProbe.read(this),
        )
        val displayMode = if (topo.mode == com.visorcraft.ghostgalleon.display.SurfaceMode.DUAL) {
            getString(R.string.label_dual)
        } else {
            getString(R.string.label_single)
        }
        systemCardExtra.addView(statRow(
            getString(R.string.settings_display_mode),
            getString(
                R.string.settings_display_mode_value,
                displayMode,
                deviceProfileName(effectiveProfile.id),
            ),
        ))
        systemCardExtra.addView(statRow(
            getString(R.string.settings_topology),
            getString(
                R.string.settings_topology_value,
                formatNumber(topo.primaryDisplayId),
                topo.companionDisplayId?.let(::formatNumber)
                    ?: getString(R.string.glyph_dash),
                formatNumber(topo.launchDisplayId),
                topo.secondaryHomeDisplayId?.let(::formatNumber)
                    ?: getString(R.string.glyph_dash),
                topo.largerDisplayId?.let(::formatNumber)
                    ?: getString(R.string.glyph_dash),
            ),
        ))
        val readings = SystemInfoCollector.collect(this)
        SystemInfoFormat.rows(readings).forEach { (label, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(rowLabel(resolveText(label)), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = resolveText(value)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.END
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f,
            ))
            systemCard.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            })
        }
        val refreshSys = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener { refreshSettingsUi() }
        }
        refreshSys.addView(rowLabel(getString(R.string.settings_refresh_readings)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        systemCard.addView(refreshSys, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        // Dual-black recovery guidance + in-app companion restart (no App Info).
        systemCard.addView(TextView(this).apply {
            setText(R.string.settings_dual_recovery_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(12), 0, dp(4))
        })
        systemCard.addView(TextView(this).apply {
            setText(R.string.settings_dual_recovery_body)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0x99FFFFFF.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        val restartCompanion = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setOnClickListener {
                val main = app.liveDeckActivities()
                    .filterIsInstance<com.visorcraft.ghostgalleon.ui.MainActivity>()
                    .firstOrNull()
                if (main != null) {
                    main.restartCompanionPanel("settings-system")
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.deck_restart_bottom_panel,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.settings_dual_recovery_no_main,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        restartCompanion.addView(
            rowLabel(getString(R.string.settings_restart_companion)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        systemCard.addView(restartCompanion, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        toggle(systemCard, getString(R.string.settings_detect_black_companion), s.detectBlackCompanion) {
            app.updateSettings(app.settings.copy(detectBlackCompanion = it))
        }
        addSection(SettingsPage.SYSTEM, getString(R.string.settings_page_system), systemCard)
        pageBodies.getValue(SettingsPage.ABOUT).addView(
            AboutPage.build(this, accent),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        if (wide) {
            body.addView(buildNavBlade(), LinearLayout.LayoutParams(
                dp(220), ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(20) })
        }
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        pageHost = host
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(host, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        body.addView(scroll, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
        ))
        shell.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        selectPage(currentPage)
        return shell
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val target = captureTarget ?: return super.onKeyDown(keyCode, event)
        val withoutTarget = app.settings.keyMap.filterValues { it != target }
        val newMap = withoutTarget + (keyCode to target)
        app.updateSettings(app.settings.copy(keyMap = newMap))
        capturePulse?.cancel()
        capturePulse = null
        captureLabel?.apply {
            alpha = 1f
            text = getString(R.string.format_keycode, keyCode)
            setTextColor(accent)
            background = pillDrawable(0xFF1C1C22.toInt(), 14, 0x26FFFFFF)
        }
        captureTarget = null
        captureLabel = null
        return true
    }
}
