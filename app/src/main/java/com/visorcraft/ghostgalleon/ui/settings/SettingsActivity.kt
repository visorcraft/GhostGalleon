package com.visorcraft.ghostgalleon.ui.settings

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.visorcraft.ghostgalleon.BuildConfig
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtArchive
import com.visorcraft.ghostgalleon.art.ScrapeJob
import com.visorcraft.ghostgalleon.art.SgdbScraper
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.audio.SoundPackId
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
import com.visorcraft.ghostgalleon.settings.BrowseChrome
import com.visorcraft.ghostgalleon.settings.CompanionRole
import com.visorcraft.ghostgalleon.settings.CompanionRoleResolve
import com.visorcraft.ghostgalleon.settings.SettingsBundle
import com.visorcraft.ghostgalleon.settings.SettingsCatalog
import com.visorcraft.ghostgalleon.settings.SettingsJump
import com.visorcraft.ghostgalleon.settings.SettingsStore
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.ui.deck.DeckWallpaper
import com.visorcraft.ghostgalleon.state.UIMode
import com.visorcraft.ghostgalleon.system.SystemInfoCollector
import com.visorcraft.ghostgalleon.system.SystemInfoFormat
import com.visorcraft.ghostgalleon.ui.ControllerLabActivity
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.HelperEmbedPolicy
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.deviceProfileName
import com.visorcraft.ghostgalleon.ui.applyThemeFontScale
import com.visorcraft.ghostgalleon.ui.hideStatusBar
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.themeName
import com.visorcraft.ghostgalleon.ui.useTabularNumerals
import java.io.File
import java.text.NumberFormat

/**
 * Pure, host-tested layout and contrast decisions shared by Settings, About,
 * and Controller Lab.
 */
internal object SettingsUiLogic {

    /**
     * Stack the row label above segmented controls whenever there are 3+
     * options or the viewport is narrow so long localized labels never squish.
     */
    fun shouldStackSegmented(optionCount: Int, isWide: Boolean): Boolean =
        optionCount >= 3 || !isWide

    /**
     * WCAG AA (`>= 4.5:1`) foreground text color on a filled [accent] pill.
     * Prefers [ThemePack.onFillTextColor] when it meets 4.5:1, else picks
     * whichever of black or white provides higher contrast.
     */
    fun accessibleOnAccent(accent: Int): Int {
        val preferred = ThemePack.onFillTextColor(accent)
        if (ThemePack.contrastRatio(preferred, accent) >= ThemePack.MIN_TEXT_CONTRAST) {
            return preferred
        }
        val white = UiDimens.ALPHA_PRIMARY
        val black = 0xFF000000.toInt()
        return if (ThemePack.contrastRatio(black, accent) >= ThemePack.contrastRatio(white, accent)) {
            black
        } else {
            white
        }
    }

    /**
     * Crisp focus ring color around an already accent-filled pill. Uses white
     * on colored accents (Ghost, Teal, Neon) and a cool slate ring on light
     * accents (OLED `#E0E0E0`) so focus stays distinct from both the filled
     * pill and the dark card behind it.
     */
    fun activePillFocusRing(accent: Int): Int =
        if (ThemePack.contrastRatio(UiDimens.ALPHA_PRIMARY, accent) >= 2.0) {
            UiDimens.ALPHA_PRIMARY
        } else {
            0xFF707A8A.toInt()
        }

    fun accentOnSurface(accent: Int, tokens: ThemeTokens): Int =
        ThemePack.accentTextColor(accent, ThemePack.cardFill(tokens))

    fun accentOnSunken(accent: Int, tokens: ThemeTokens): Int =
        ThemePack.accentTextColor(accent, ThemePack.surfaceSunken(tokens))

    fun isSecondSeatLayoutVisible(secondSeatEnabled: Boolean): Boolean = secondSeatEnabled

    /**
     * Wraps [currentIndex] by [delta] across [pageCount] settings pages for
     * gamepad L1/R1 quick tab switching. Pure; host-tested.
     */
    fun stepPageIndex(currentIndex: Int, delta: Int, pageCount: Int): Int {
        if (pageCount <= 1) return 0
        val start = currentIndex.coerceIn(0, pageCount - 1)
        return ((start + delta) % pageCount + pageCount) % pageCount
    }

    /**
     * Subtly accent-tinted elevated fill for the active left-blade navigation
     * item. Guarantees high contrast for both primary white text and the
     * luminance-lifted accent icon across Ghost, Teal, OLED, and Neon.
     */
    fun navActiveSurface(tokens: ThemeTokens, accent: Int): Int {
        val base = ThemePack.surfaceElevated(tokens)
        val tint = if (ThemePack.isHighLuminanceAccent(accent)) 0.08f else 0.14f
        return ThemePack.blendColors(base, accent, tint)
    }

    /**
     * Compact locale-neutral hardware keycap notation for common gamepad and
     * navigation keycodes, or `null` when callers should fall back to localized
     * `R.string.format_keycode`. Pure; host-tested.
     */
    fun friendlyKeycap(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_START -> "START"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
        KeyEvent.KEYCODE_BUTTON_MODE -> "HOME"
        KeyEvent.KEYCODE_DPAD_UP -> "\u25B2"
        KeyEvent.KEYCODE_DPAD_DOWN -> "\u25BC"
        KeyEvent.KEYCODE_DPAD_LEFT -> "\u25C0"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "\u25B6"
        KeyEvent.KEYCODE_DPAD_CENTER -> "OK"
        KeyEvent.KEYCODE_ENTER -> "\u21B5"
        KeyEvent.KEYCODE_BACK -> "BACK"
        KeyEvent.KEYCODE_ESCAPE -> "ESC"
        KeyEvent.KEYCODE_SPACE -> "SPACE"
        else -> null
    }

    /** Resolve the first keycode currently mapped to [action], or null if unbound. */
    fun boundKeyCodeFor(action: Action, keyMap: Map<Int, Action>): Int? =
        keyMap.entries.firstOrNull { it.value == action }?.key

    /** Normalized `[0f..1f]` progress ratio for determinate Settings progress bars. */
    fun scrapeProgressFraction(done: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** True when a user-configured text credential or path is non-blank. */
    fun isConfiguredStatus(value: String?): Boolean = !value.isNullOrBlank()

    /** True when RetroAchievements username and API key are both configured. */
    fun hasRaCredentials(username: String?, apiKey: String?): Boolean =
        isConfiguredStatus(username) && isConfiguredStatus(apiKey)

    /** True when either SteamGridDB or RetroAchievements credentials are ready. */
    fun hasAnyArtCredentials(sgdbKey: String?, raUsername: String?, raApiKey: String?): Boolean =
        isConfiguredStatus(sgdbKey) || hasRaCredentials(raUsername, raApiKey)

    /** Number of optional power-rail toggles under the Browse Chrome expander. */
    const val TOTAL_POWER_RAILS: Int = 16

    /** Count of currently enabled Browse Chrome power-rail / extra toggles (`0..16`). */
    fun enabledPowerRailsCount(chrome: BrowseChrome): Int =
        chrome.switchFlags().drop(2).count { it }

    /** Target rotation degrees for the collapsible Browse Chrome chevron. */
    fun chevronRotationDegrees(expanded: Boolean): Float = if (expanded) 180f else 0f

    internal enum class ModalChipTone { NEUTRAL, ACCENT, DESTRUCTIVE }

    /**
     * WCAG AA foreground text color for Settings management dialog chips
     * across [ModalChipTone.NEUTRAL] (`chipIdle`), [ModalChipTone.ACCENT]
     * (`accent`), and [ModalChipTone.DESTRUCTIVE] (`surfaceSunken`).
     */
    fun modalChipTextColor(tone: ModalChipTone, accent: Int, tokens: ThemeTokens): Int =
        when (tone) {
            ModalChipTone.ACCENT -> accessibleOnAccent(accent)
            ModalChipTone.DESTRUCTIVE -> UiDimens.COLOR_DESTRUCTIVE
            ModalChipTone.NEUTRAL -> ThemePack.accentTextColor(accent, tokens.chipIdle)
        }

    /**
     * Normalized `[0f..1f]` proportional bar width for Stats playtime rows.
     * Applies a subtle 4% visual floor for positive scores so short sessions
     * still render cleanly alongside long ones. Pure; host-tested.
     */
    fun playtimeBarFraction(scoreMs: Long, maxScoreMs: Long): Float {
        if (scoreMs <= 0L || maxScoreMs <= 0L) return 0f
        val raw = scoreMs.toDouble() / maxScoreMs.toDouble()
        return raw.toFloat().coerceIn(0.04f, 1f)
    }

    /** Sum of positive accumulated playtime durations across all tracked keys. */
    fun totalPlaytimeMs(playtimeByKey: Map<String, Long>): Long =
        playtimeByKey.values.sumOf { if (it > 0L) it else 0L }

    /** Warm amber warning tint with high contrast on dark sunken cards. */
    const val COLOR_WARNING_AMBER: Int = 0xFFFFB74D.toInt()

    /** Normalized used fraction `[0f..1f]` computed from free and total bytes. */
    fun freeToUsedFraction(freeBytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        val safeFree = freeBytes.coerceIn(0L, totalBytes)
        return ((totalBytes - safeFree).toDouble() / totalBytes.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /** Normalized battery charge fraction `[0f..1f]`. */
    fun batteryFraction(percent: Int?): Float =
        if (percent == null) 0f else (percent / 100f).coerceIn(0f, 1f)

    /**
     * Semantic meter bar color: uses [accent] in healthy range, warm amber in
     * warning range, and [UiDimens.COLOR_DESTRUCTIVE] when critical.
     */
    fun meterToneColor(fraction: Float, accent: Int, lowIsWarn: Boolean = false): Int {
        val f = fraction.coerceIn(0f, 1f)
        return if (lowIsWarn) {
            when {
                f <= 0.12f -> UiDimens.COLOR_DESTRUCTIVE
                f <= 0.25f -> COLOR_WARNING_AMBER
                else -> accent
            }
        } else {
            when {
                f >= 0.92f -> UiDimens.COLOR_DESTRUCTIVE
                f >= 0.80f -> COLOR_WARNING_AMBER
                else -> accent
            }
        }
    }

    /** True when the Library card should render a framed empty-folders hint. */
    fun shouldShowEmptyFoldersPrompt(folderCount: Int): Boolean = folderCount <= 0

    /** True when stick deadzone differs from the 50% factory default. */
    fun isCustomDeadzone(deadzonePercent: Int): Boolean =
        com.visorcraft.ghostgalleon.input.StickThresholds.clamp(deadzonePercent) !=
            com.visorcraft.ghostgalleon.input.StickThresholds.DEFAULT_PERCENT

    /** Locale-neutral hardware notation for quick shoulder page switching. */
    const val SHOULDER_PAGE_HINT: String = "L1 \u00B7 R1"

    /** WCAG AA accent text color drawn directly over pure black OLED header. */
    fun accentOnBlack(accent: Int): Int = ThemePack.accentTextColor(accent, 0xFF000000.toInt())

    /** Show the compact L1·R1 shoulder cue pill on wide displays without crowding narrow panels. */
    fun shouldShowShoulderCue(isWide: Boolean): Boolean = isWide

    internal data class ShellPaddingDp(val horizontal: Int, val top: Int, val bottom: Int)

    /** Responsive shell padding that reclaims vertical breathing room on ~540dp Sugar panels. */
    fun shellPaddingDp(isWide: Boolean, screenHeightDp: Int): ShellPaddingDp = when {
        !isWide -> ShellPaddingDp(horizontal = 16, top = 12, bottom = 10)
        screenHeightDp < 560 -> ShellPaddingDp(horizontal = 20, top = 12, bottom = 10)
        else -> ShellPaddingDp(horizontal = 24, top = 18, bottom = 14)
    }

    /** Adaptive wide left nav blade width (200dp on compact-wide, 224dp on 820dp+). */
    fun navBladeWidthDp(screenWidthDp: Int): Int = if (screenWidthDp >= 820) 224 else 200

    /** Interactive cues exposed in Settings → Audio Sound Test Pad. */
    val AUDIO_TEST_PAD_CUES: List<SoundCue> = listOf(
        SoundCue.NAV_TICK,
        SoundCue.CONFIRM,
        SoundCue.LAUNCH_GAME,
        SoundCue.SWAP_DISPLAY,
        SoundCue.FAVORITE_ON,
        SoundCue.RA_ACHIEVEMENT,
    )

    /** 3x2 grid on wide handheld/tablet displays; 2x3 on narrow panels so localized labels fit cleanly. */
    fun audioTestPadColumns(isWide: Boolean): Int = if (isWide) 3 else 2

    /** True when UI sound effects are actively audible under current settings. */
    fun isSfxAudioActive(soundEnabled: Boolean, soundPack: String, sfxVolume: Int): Boolean =
        soundEnabled && SoundPackId.fromId(soundPack).isPlayable && sfxVolume > 0

    /** True when ambient soundscape is enabled and audible under current settings. */
    fun isAmbientAudioActive(
        soundEnabled: Boolean,
        ambientEnabled: Boolean,
        soundPack: String,
        ambientVolume: Int,
    ): Boolean = soundEnabled && ambientEnabled && SoundPackId.fromId(soundPack).isPlayable && ambientVolume > 0
}

class SettingsActivity : AppCompatActivity() {

    private val app get() = application as GhostGalleonApp

    /** Settings pages: Display & Grid stay together; others are their own. */
    private enum class SettingsPage(val titleRes: Int, @DrawableRes val iconRes: Int) {
        DISPLAY_GRID(R.string.settings_page_display_grid, R.drawable.ic_nav_display),
        APPS(R.string.settings_page_apps, R.drawable.ic_nav_apps),
        CONTROLS(R.string.settings_page_controls, R.drawable.ic_nav_controls),
        AUDIO(R.string.settings_page_audio, R.drawable.ic_nav_audio),
        LIBRARY(R.string.settings_page_library, R.drawable.ic_nav_library),
        ART_DATA(R.string.settings_page_art, R.drawable.ic_nav_art),
        STATS(R.string.settings_page_stats, R.drawable.ic_nav_stats),
        SYSTEM(R.string.settings_page_system, R.drawable.ic_nav_system),
        ABOUT(R.string.settings_page_about, R.drawable.ic_nav_about),
    }

    private data class NavRowViews(
        val container: LinearLayout,
        val indicator: View,
        val icon: ImageView,
        val label: TextView,
    )

    private var currentPage: SettingsPage = SettingsPage.DISPLAY_GRID
    private var pageHost: LinearLayout? = null
    private var contentScroll: ScrollView? = null
    private val pageBodies = mutableMapOf<SettingsPage, LinearLayout>()
    private val navItems = mutableMapOf<SettingsPage, NavRowViews>()
    private var pageDropdownRow: View? = null
    private var pageDropdownIcon: ImageView? = null
    private var pageDropdownLabel: TextView? = null
    private var headerBackBtn: View? = null
    private var headerSearchBtn: View? = null
    private var headerBreadcrumbLabel: TextView? = null

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
    private val keyChipsByAction = mutableMapOf<Action, TextView>()

    private var scanning = false

    private val appLibrary by lazy {
        (application as GhostGalleonApp).appLibrary()
    }

    private var hiddenValue: TextView? = null
    private var hiddenRomsValue: TextView? = null
    private var dockValue: TextView? = null
    private var packageYieldValue: TextView? = null
    private var playHostHelperValue: TextView? = null
    private var artHeaderStatusPill: TextView? = null

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
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(rowLabel(label).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
            addView(TextView(this@SettingsActivity).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(UiDimens.ALPHA_SECONDARY)
                useTabularNumerals()
                gravity = Gravity.END
            })
        }

    private fun rankedPlaytimeRow(
        rank: Int,
        label: String,
        duration: String,
        barFraction: Float,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        val top = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val rankBadge = TextView(this@SettingsActivity).apply {
                text = formatNumber(rank)
                gravity = Gravity.CENTER
                minWidth = dp(26)
                styleStatusBadge(this, active = rank == 1)
            }
            addView(
                rankBadge,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(10) },
            )
            addView(
                rowLabel(label).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(10)
                },
            )
            addView(
                TextView(this@SettingsActivity).apply {
                    text = duration
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(if (rank == 1) accentOnCard else UiDimens.ALPHA_SECONDARY)
                    setTypeface(typeface, if (rank == 1) Typeface.BOLD else Typeface.NORMAL)
                    useTabularNumerals()
                    gravity = Gravity.END
                },
            )
        }
        addView(
            top,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val meter = DeterminateProgressBarView(this@SettingsActivity, themeTokens, accent).apply {
            setFraction(barFraction)
        }
        addView(
            meter,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
                marginStart = dp(36)
                topMargin = dp(6)
            },
        )
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
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.settings_bundled_packs,
            items = labels,
            neutralRes = R.string.action_load_all,
            onNeutral = { loadAllBundledPacks() },
            negativeRes = R.string.action_cancel,
        ) { which ->
            loadBundledPackAsset(names[which])
        }
    }

    private fun refreshAppsRows() {
        val hiddenCount = app.settings.hiddenPackages.size
        hiddenValue?.apply {
            text = formatNumber(hiddenCount)
            styleStatusBadge(this, active = hiddenCount > 0)
        }
        val hiddenRomsCount = app.settings.hiddenRomIds.size
        hiddenRomsValue?.apply {
            text = formatNumber(hiddenRomsCount)
            styleStatusBadge(this, active = hiddenRomsCount > 0)
        }
        val dock = app.settings.dockSlots.filterNotNull()
        dockValue?.text =
            if (dock.isEmpty()) getString(R.string.label_empty)
            else dock.joinToString(" · ", transform = ::dockEntryLabel)
        val yieldCount = app.settings.packageYield.count { it.value }
        packageYieldValue?.apply {
            text = formatNumber(yieldCount)
            styleStatusBadge(this, active = yieldCount > 0)
        }
        playHostHelperValue?.text = app.settings.playHostHelperPackage?.let { pkg ->
            appLabel(pkg)
        } ?: getString(R.string.action_none)
    }

    private fun modalRow(
        label: String,
        chip: String,
        tone: SettingsUiLogic.ModalChipTone = SettingsUiLogic.ModalChipTone.NEUTRAL,
        onChip: () -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowLabel(label).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            })
            val idleBg = when (tone) {
                SettingsUiLogic.ModalChipTone.ACCENT -> pillDrawable(accent, 14)
                SettingsUiLogic.ModalChipTone.DESTRUCTIVE ->
                    TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 14)
                SettingsUiLogic.ModalChipTone.NEUTRAL ->
                    TileBackgrounds.chipIdle(this@SettingsActivity, cornerRadiusDp = 14)
            }
            val focusColor = if (tone == SettingsUiLogic.ModalChipTone.ACCENT) {
                activeFocusRing
            } else {
                accent
            }
            addView(TextView(this@SettingsActivity).apply {
                text = chip
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(SettingsUiLogic.modalChipTextColor(tone, accent, themeTokens))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = dp(72)
                minHeight = dp(38)
                isFocusable = true
                background = TileBackgrounds.pressableRow(
                    context = this@SettingsActivity,
                    accent = focusColor,
                    idleBackground = idleBg,
                    cornerRadiusDp = 14,
                    strokeDp = 2,
                )
                val hp = dp(14); val vp = dp(6)
                setPadding(hp, vp, hp, vp)
                setOnClickListener { onChip() }
            })
        }

    private fun modalEmpty(text: String): View = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(UiDimens.ALPHA_MUTED)
        gravity = Gravity.CENTER
        background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 14)
        val h = dp(16)
        val v = dp(18)
        setPadding(h, v, h, v)
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
                hidden.forEachIndexed { index, pkg ->
                    if (index > 0) addDivider(list)
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
        DeckDialogs.customView(
            context = this,
            titleRes = R.string.settings_hidden_apps,
            view = dialogScroll(list),
            negativeRes = R.string.action_close,
        )
    }

    private fun dialogScroll(content: View): OverlayPrimitives.MaxHeightScrollView =
        OverlayPrimitives.MaxHeightScrollView(
            context = this,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = this,
                heightFrac = 0.56f,
                minDp = 140,
            ),
        ).apply {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(14))
            addView(content)
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
                hidden.forEachIndexed { index, id ->
                    if (index > 0) addDivider(list)
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
        DeckDialogs.customView(
            context = this,
            titleRes = R.string.settings_hidden_roms,
            view = dialogScroll(list),
            negativeRes = R.string.action_close,
        )
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
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.settings_pinned_companion,
            items = labels,
            neutralRes = R.string.action_clear,
            onNeutral = {
                app.updateSettings(app.settings.copy(companionPinnedPackage = null))
                refreshSettingsUi()
            },
        ) { which ->
            val pkg = apps[which].packageName
            app.updateSettings(app.settings.copy(companionPinnedPackage = pkg))
            refreshSettingsUi()
        }
    }

    /** Pick a non-session, non-dual-surface helper for the KEEP play host. */
    private fun showPlayHostHelperPicker() {
        val apps = helperPickerApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.settings_no_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.settings_play_host_helper,
            items = labels,
            neutralRes = R.string.action_clear,
            onNeutral = {
                app.updateSettings(app.settings.copy(playHostHelperPackage = null))
                refreshSettingsUi()
            },
        ) { which ->
            val pkg = apps[which].packageName
            app.updateSettings(app.settings.copy(playHostHelperPackage = pkg))
            refreshSettingsUi()
        }
    }

    private fun helperPickerApps() = appLibrary.visible(app.settings)
        .sortedBy { it.label.lowercase() }
        .filter { entry ->
            !HelperEmbedPolicy.refused(entry.packageName) &&
                !CompanionRoleResolve.pinConflictsWithSession(
                    entry.packageName,
                    app.sessionSurface,
                )
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
                dock.forEachIndexed { index, key ->
                    if (index > 0) addDivider(list)
                    list.addView(
                        modalRow(
                            label = dockEntryLabel(key),
                            chip = getString(R.string.action_remove),
                            tone = SettingsUiLogic.ModalChipTone.DESTRUCTIVE,
                        ) {
                            app.updateSettings(app.settings.copy(
                                dockSlots = app.settings.dockSlots.map {
                                    if (it == key) null else it
                                }))
                            refreshAppsRows()
                            rebuild()
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)),
                    )
                }
            }
        }
        rebuild()
        DeckDialogs.customView(
            context = this,
            titleRes = R.string.label_dock,
            view = dialogScroll(list),
            negativeRes = R.string.action_close,
        )
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
            apps.forEachIndexed { index, entry ->
                if (index > 0) addDivider(list)
                val pkg = entry.packageName
                val on = app.settings.packageYield[pkg] == true
                list.addView(
                    modalRow(
                        label = entry.label,
                        chip = yieldChip(on),
                        tone = if (on) {
                            SettingsUiLogic.ModalChipTone.ACCENT
                        } else {
                            SettingsUiLogic.ModalChipTone.NEUTRAL
                        },
                    ) {
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
        DeckDialogs.customView(
            context = this,
            titleRes = R.string.settings_package_yield,
            view = dialogScroll(list),
            negativeRes = R.string.action_close,
        )
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
    private var scrapeProgressBar: DeterminateProgressBarView? = null

    private val scrapeListener = object : ScrapeJob.Listener {
        override fun onProgress(done: Int, total: Int) {
            scrapeLabel?.setText(R.string.action_cancel)
            scrapeLabel?.setTextColor(UiDimens.COLOR_DESTRUCTIVE)
            scrapeValue?.apply {
                text = getString(R.string.format_progress, done, total)
                setTextColor(accentOnSunken)
                background = TileBackgrounds.valueBadge(this@SettingsActivity, accent)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                visibility = View.VISIBLE
            }
            scrapeProgressBar?.apply {
                visibility = View.VISIBLE
                setFraction(SettingsUiLogic.scrapeProgressFraction(done, total))
            }
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
        val hasKey = SettingsUiLogic.isConfiguredStatus(app.settings.sgdbApiKey)
        val job = app.scrapeJob
        val running = job.isRunning
        sgdbKeyValue?.apply {
            setText(if (hasKey) R.string.label_set else R.string.label_not_set)
            styleStatusBadge(this, active = hasKey)
        }
        refreshArtHeaderStatusPill()
        if (running) {
            scrapeLabel?.setText(R.string.action_cancel)
            scrapeLabel?.setTextColor(UiDimens.COLOR_DESTRUCTIVE)
            scrapeValue?.apply {
                text = if (job.progressTotal > 0) {
                    getString(
                        R.string.format_progress,
                        job.progressDone,
                        job.progressTotal,
                    )
                } else {
                    getString(R.string.glyph_ellipsis)
                }
                setTextColor(accentOnSunken)
                background = TileBackgrounds.valueBadge(this@SettingsActivity, accent)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                visibility = View.VISIBLE
            }
            scrapeProgressBar?.apply {
                visibility = View.VISIBLE
                setFraction(
                    SettingsUiLogic.scrapeProgressFraction(job.progressDone, job.progressTotal),
                )
            }
        } else {
            scrapeLabel?.setText(R.string.settings_download_artwork)
            scrapeLabel?.setTextColor(UiDimens.ALPHA_PRIMARY)
            scrapeValue?.apply {
                if (hasKey) {
                    text = ""
                    background = null
                    setPadding(0, 0, 0, 0)
                    visibility = View.GONE
                } else {
                    setText(R.string.settings_add_api_key_first)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    background = null
                    setPadding(0, 0, 0, 0)
                    visibility = View.VISIBLE
                }
            }
            scrapeProgressBar?.apply {
                setFraction(0f)
                visibility = View.GONE
            }
        }
        val usable = hasKey || running
        scrapeRow?.isEnabled = usable
        scrapeRow?.alpha = if (usable) 1f else 0.5f
    }

    private fun showCollectionsDialog() {
        val names = app.settings.collections.keys.sortedBy { it.lowercase() }
            .toMutableList()
        names.add(0, getString(R.string.settings_new_collection_row))
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.label_collections,
            items = names.toTypedArray(),
            negativeRes = R.string.action_close,
        ) { which ->
            if (which == 0) {
                DeckDialogs.promptText(
                    context = this,
                    titleRes = R.string.settings_new_collection,
                    hintRes = R.string.settings_collection_name_hint,
                    positiveRes = R.string.action_create,
                ) { rawName ->
                    val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                        .createCollection(app.settings.collections, rawName)
                    app.updateSettings(app.settings.copy(collections = next))
                }
            } else {
                val name = names[which]
                val count = app.settings.collections[name]?.size ?: 0
                val titleWithCount = getString(
                    R.string.format_dot_pair,
                    name,
                    resources.getQuantityString(R.plurals.count_items, count, count),
                )
                DeckDialogs.chooseItem(
                    context = this,
                    title = titleWithCount,
                    items = arrayOf(
                        getString(R.string.action_edit_members),
                        getString(R.string.action_mirror_to_folder),
                        getString(R.string.action_rename),
                        getString(R.string.action_delete),
                    ),
                    negativeRes = R.string.action_close,
                ) { action ->
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
                        2 -> DeckDialogs.promptText(
                            context = this,
                            titleRes = R.string.action_rename,
                            initialText = name,
                            positiveRes = R.string.action_save,
                        ) { newName ->
                            val next = com.visorcraft.ghostgalleon.library
                                .CollectionsOps.renameCollection(
                                    app.settings.collections,
                                    name,
                                    newName,
                                )
                            app.updateSettings(app.settings.copy(collections = next))
                        }
                        3 -> {
                            val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                                .deleteCollection(app.settings.collections, name)
                            app.updateSettings(app.settings.copy(collections = next))
                        }
                    }
                }
            }
        }
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
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.settings_default_players,
            items = labels,
            negativeRes = R.string.action_close,
        ) { which ->
            val platform = platforms[which]
            val playerLabels = platform.players.map { playerListLabel(it) }.toTypedArray()
            DeckDialogs.chooseItem(
                context = this,
                title = platform.displayName,
                items = playerLabels,
            ) { pWhich ->
                val player = platform.players[pWhich]
                app.updateSettings(
                    app.settings.copy(
                        defaultPlayers = app.settings.defaultPlayers +
                            (platform.id to player.id),
                    ),
                )
                refreshSettingsUi()
            }
        }
    }

    private fun playerListLabel(player: PlayerTemplate): String =
        playerSettingsLabel(
            player.displayName,
            player.sessionPolicy,
            getString(R.string.settings_player_uses_both_screens),
        )

    private fun showSgdbKeyDialog() {
        DeckDialogs.promptText(
            context = this,
            titleRes = R.string.settings_sgdb_api_key,
            initialText = app.settings.sgdbApiKey ?: "",
            hintRes = R.string.settings_api_key_hint,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        ) { raw ->
            val key = raw.trim().ifEmpty { null }
            app.updateSettings(app.settings.copy(sgdbApiKey = key))
            refreshSgdbRows()
        }
    }

    private fun showRaUsernameDialog() {
        DeckDialogs.promptText(
            context = this,
            titleRes = R.string.settings_ra_username,
            initialText = app.settings.raUsername ?: "",
            hintRes = R.string.settings_ra_username_hint,
        ) { raw ->
            val name = raw.trim().ifEmpty { null }
            app.updateSettings(app.settings.copy(raUsername = name))
            refreshRaRows()
        }
    }

    private fun showRaApiKeyDialog() {
        DeckDialogs.promptText(
            context = this,
            titleRes = R.string.settings_ra_api_key,
            initialText = app.settings.raApiKey ?: "",
            hintRes = R.string.settings_api_key_hint,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        ) { raw ->
            val key = raw.trim().ifEmpty { null }
            app.updateSettings(app.settings.copy(raApiKey = key))
            refreshRaRows()
        }
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
        DeckDialogs.confirm(
            context = this,
            titleRes = R.string.settings_ra_network_commands,
            message = getString(R.string.settings_ra_network_commands_help) + "\n\n" + paste,
            positiveRes = R.string.action_ok,
            negativeRes = null,
        )
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
            scrapeLabel?.setTextColor(UiDimens.COLOR_DESTRUCTIVE)
            scrapeValue?.apply {
                setText(R.string.glyph_ellipsis)
                setTextColor(accentOnSunken)
                background = TileBackgrounds.valueBadge(this@SettingsActivity, accent)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                visibility = View.VISIBLE
            }
            scrapeProgressBar?.apply {
                setFraction(0f)
                visibility = View.VISIBLE
            }
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
        val trees = app.settings.romTreeUris
        if (SettingsUiLogic.shouldShowEmptyFoldersPrompt(trees.size)) {
            val emptyHint = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(
                    ImageView(this@SettingsActivity).apply {
                        setImageResource(R.drawable.ic_folder)
                        imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_MUTED)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) },
                )
                addView(
                    TextView(this@SettingsActivity).apply {
                        setText(R.string.setup_check_rom_folder)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                        setTextColor(UiDimens.ALPHA_MUTED)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            rows.addView(
                emptyHint,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                    topMargin = dp(4)
                    bottomMargin = dp(6)
                },
            )
            return
        }
        trees.forEachIndexed { index, uriString ->
            if (index > 0) addDivider(rows)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
            }
            row.addView(
                ImageView(this).apply {
                    setImageResource(R.drawable.ic_folder)
                    imageTintList = ColorStateList.valueOf(accentOnCard)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) },
            )
            row.addView(rowLabel(resolveText(TreeLabels.label(uriString))).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            row.addView(TextView(this).apply {
                setText(R.string.action_remove)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(UiDimens.COLOR_DESTRUCTIVE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                minHeight = dp(38)
                isFocusable = true
                background = TileBackgrounds.pressableRow(
                    context = this@SettingsActivity,
                    accent = accent,
                    idleBackground = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 14),
                    cornerRadiusDp = 14,
                    strokeDp = 2,
                )
                val hp = dp(12); val vp = dp(6)
                setPadding(hp, vp, hp, vp)
                setOnClickListener { removeRomFolder(uriString) }
            })
            rows.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
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
        val userSet = SettingsUiLogic.isConfiguredStatus(app.settings.raUsername)
        val keySet = SettingsUiLogic.isConfiguredStatus(app.settings.raApiKey)
        raUserValue?.apply {
            text = app.settings.raUsername?.takeIf { it.isNotBlank() }
                ?: getString(R.string.label_not_set)
            styleStatusBadge(this, active = userSet)
        }
        raKeyValue?.apply {
            setText(if (keySet) R.string.label_set else R.string.label_not_set)
            styleStatusBadge(this, active = keySet)
        }
        refreshArtHeaderStatusPill()
    }

    private fun refreshArtHeaderStatusPill() {
        val pill = artHeaderStatusPill ?: return
        val ready = SettingsUiLogic.hasAnyArtCredentials(
            sgdbKey = app.settings.sgdbApiKey,
            raUsername = app.settings.raUsername,
            raApiKey = app.settings.raApiKey,
        )
        pill.setText(if (ready) R.string.label_set else R.string.label_not_set)
        styleStatusBadge(pill, active = ready)
    }

    private fun drillChevron(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.ic_chevron_right)
        imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_MUTED)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun styleStatusBadge(view: TextView, active: Boolean) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        view.setTypeface(null, Typeface.BOLD)
        view.useTabularNumerals()
        view.setTextColor(if (active) accentOnSunken else UiDimens.ALPHA_MUTED)
        TileBackgrounds.applyStatusBadgeState(
            view = view,
            active = active,
            accent = accent,
            cornerRadiusDp = UiDimens.RADIUS_SM,
        )
        view.setPadding(dp(9), dp(3), dp(9), dp(3))
    }

    private fun statusPill(text: String, active: Boolean): TextView = TextView(this).apply {
        this.text = text
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        styleStatusBadge(this, active)
    }

    private val accent get() = app.settings.accentColor
    private val themeTokens get() = ThemePack.resolve(app.settings)
    private val accentOnCard get() = SettingsUiLogic.accentOnSurface(accent, themeTokens)
    private val accentOnSunken get() = SettingsUiLogic.accentOnSunken(accent, themeTokens)
    private val onAccentFill get() = SettingsUiLogic.accessibleOnAccent(accent)
    private val activeFocusRing get() = SettingsUiLogic.activePillFocusRing(accent)

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
        cancelKeyCapture()
        app.scrapeJob.removeListener(scrapeListener)
        super.onPause()
    }

    override fun onDestroy() {
        cancelKeyCapture()
        super.onDestroy()
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

    private val offTint = 0x4DFFFFFF.toInt()

    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase(resources.configuration.locales[0])
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(accentOnCard)
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.15f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isAccessibilityHeading = true
        }
    }

    private fun sectionCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(this@SettingsActivity)
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }

    private fun addDivider(card: LinearLayout) {
        card.addView(
            View(this).apply { setBackgroundColor(TileBackgrounds.hairlineDivider(this@SettingsActivity)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, strokePx(1)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            },
        )
    }

    private fun addDividerIfNeeded(card: LinearLayout) {
        if (card.childCount > 0) addDivider(card)
    }

    private fun styleFocusableRow(row: View) {
        row.isFocusable = true
        row.setPadding(dp(10), dp(6), dp(10), dp(6))
        row.background = TileBackgrounds.pressableRow(
            context = this,
            accent = accent,
            cornerRadiusDp = 12,
            strokeDp = 2,
        )
    }

    private fun rowLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(UiDimens.ALPHA_PRIMARY)
    }

    /** A focusable control row: label left, control right. */
    private fun controlRow(label: String, control: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
            addView(control)
        }

    private fun accentSwitch(checked: Boolean, onChange: (Boolean) -> Unit): SwitchCompat =
        SwitchCompat(this).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbDrawable = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_switch_thumb)?.mutate()
            trackDrawable = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_switch_track)?.mutate()
            thumbTintList = ColorStateList(states, intArrayOf(onAccentFill, 0xCCFFFFFF.toInt()))
            trackTintList = ColorStateList(states, intArrayOf(accent, 0x33FFFFFF))
            isChecked = checked
            isFocusable = false
            setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        }

    private fun switchRow(label: String, sw: SwitchCompat): LinearLayout =
        controlRow(label, sw).apply {
            styleFocusableRow(this)
            setOnClickListener { if (sw.isEnabled) sw.toggle() }
        }

    private fun pillDrawable(fill: Int, radiusDp: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dpF(radiusDp)
            if (stroke != 0) setStroke(strokePx(1), stroke)
        }

    private fun swatchDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(strokePx(1), 0x66FFFFFF)
        val size = dp(10)
        setBounds(0, 0, size, size)
    }

    /**
     * Segmented pill control (Default mode / Grid scrolling / chrome preset / Theme).
     * Uses sunken theme track, WCAG-safe [onAccentFill] text, gamepad focus rings,
     * and optional circular accent swatch dots.
     */
    private fun segmented(
        options: List<Pair<String, String>>, // value -> pill text
        current: String,
        swatchColors: Map<String, Int> = emptyMap(),
        bindSelected: ((setSelected: (String) -> Unit) -> Unit)? = null,
        onSelect: (String) -> Unit,
    ): View {
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 20)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val pills = mutableMapOf<String, TextView>()
        var selected = current

        fun restyle() {
            pills.forEach { (value, pill) ->
                val active = value == selected
                pill.isSelected = active
                if (active) {
                    pill.background = TileBackgrounds.pressableRow(
                        context = this@SettingsActivity,
                        accent = activeFocusRing,
                        idleBackground = pillDrawable(accent, 17),
                        cornerRadiusDp = 17,
                        strokeDp = 2,
                    )
                    pill.setTextColor(onAccentFill)
                } else {
                    pill.background = TileBackgrounds.pressableRow(
                        context = this@SettingsActivity,
                        accent = accent,
                        cornerRadiusDp = 17,
                        strokeDp = 2,
                    )
                    pill.setTextColor(UiDimens.ALPHA_PRIMARY)
                }
            }
        }

        val orderedPills = mutableListOf<TextView>()
        options.forEach { (value, text) ->
            val pill = TextView(this).apply {
                id = View.generateViewId()
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                gravity = Gravity.CENTER
                maxLines = 1
                isFocusable = true
                setPadding(dp(14), 0, dp(14), 0)
                swatchColors[value]?.let { color ->
                    setCompoundDrawablesRelative(swatchDrawable(color), null, null, null)
                    compoundDrawablePadding = dp(6)
                }
                setOnClickListener {
                    if (selected != value) {
                        selected = value
                        onSelect(value)
                    }
                    restyle()
                }
            }
            pills[value] = pill
            orderedPills.add(pill)
            track.addView(pill, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)))
        }
        orderedPills.forEachIndexed { index, pill ->
            if (index > 0) pill.nextFocusLeftId = orderedPills[index - 1].id
            if (index < orderedPills.lastIndex) pill.nextFocusRightId = orderedPills[index + 1].id
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

    /**
     * Locale-safe adaptive segmented row: stacks label above a horizontally
     * scrollable sunken track for 3+ options or narrow panels so German,
     * Spanish, French, and Thai labels never clip or crush the label.
     */
    private fun segmentedRow(
        card: LinearLayout,
        label: String,
        options: List<Pair<String, String>>,
        current: String,
        swatchColors: Map<String, Int> = emptyMap(),
        bindSelected: ((setSelected: (String) -> Unit) -> Unit)? = null,
        onSelect: (String) -> Unit,
    ) {
        addDividerIfNeeded(card)
        val control = segmented(options, current, swatchColors, bindSelected, onSelect)
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(16))
            addView(control)
        }
        if (SettingsUiLogic.shouldStackSegmented(options.size, isWideSettings())) {
            val stacked = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                addView(
                    rowLabel(label),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(8) },
                )
                addView(
                    scroller,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            card.addView(
                stacked,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            val inline = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(
                    rowLabel(label),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(12)
                    },
                )
                addView(
                    scroller,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            card.addView(
                inline,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    /** Bound-key keycap pill: recessed sunken cap with accent border when bound. */
    private fun keyChip(keyCode: Int?): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTypeface(typeface, Typeface.BOLD)
        useTabularNumerals()
        gravity = Gravity.CENTER
        minWidth = dp(56)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        bindKeyChip(this, keyCode)
    }

    private fun bindKeyChip(chip: TextView, keyCode: Int?) {
        chip.alpha = 1f
        if (keyCode != null) {
            chip.text = SettingsUiLogic.friendlyKeycap(keyCode)
                ?: getString(R.string.format_keycode, keyCode)
            chip.contentDescription = getString(R.string.format_keycode, keyCode)
            chip.setTextColor(accentOnSunken)
            chip.background = TileBackgrounds.valueBadge(
                context = this@SettingsActivity,
                accent = accent,
                cornerRadiusDp = UiDimens.RADIUS_SM,
            )
        } else {
            chip.setText(R.string.settings_unbound)
            chip.contentDescription = getString(R.string.settings_unbound)
            chip.setTextColor(UiDimens.ALPHA_MUTED)
            chip.background = TileBackgrounds.sunkenField(
                context = this@SettingsActivity,
                cornerRadiusDp = UiDimens.RADIUS_SM,
            )
        }
    }

    private fun cancelKeyCapture() {
        val prevTarget = captureTarget
        val prevChip = captureLabel
        capturePulse?.cancel()
        capturePulse = null
        captureTarget = null
        captureLabel = null
        if (prevTarget != null && prevChip != null) {
            bindKeyChip(prevChip, SettingsUiLogic.boundKeyCodeFor(prevTarget, app.settings.keyMap))
        }
    }

    private fun startKeyCapture(action: Action, chip: TextView) {
        if (captureTarget == action) {
            cancelKeyCapture()
            return
        }
        cancelKeyCapture()
        captureTarget = action
        captureLabel = chip
        chip.setText(R.string.settings_press_button)
        chip.contentDescription = getString(R.string.settings_press_button)
        chip.setTextColor(onAccentFill)
        chip.background = pillDrawable(accent, UiDimens.RADIUS_SM, activeFocusRing)
        if (UiDimens.areAnimationsEnabled(this)) {
            capturePulse = ObjectAnimator.ofFloat(chip, View.ALPHA, 1f, 0.45f).apply {
                duration = 520
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    /** Wide layout (top Sugar panel / docked): left nav blade. Narrow: dropdown. */
    private fun isWideSettings(): Boolean {
        val dm = resources.displayMetrics
        return dm.widthPixels >= (700f * dm.density).toInt()
    }

    private fun ensureViewId(view: View): Int {
        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        return view.id
    }

    private fun firstFocusableIn(view: View): View? {
        if (view.visibility != View.VISIBLE || !view.isEnabled) return null
        if (view is ViewGroup) {
            if (view.isFocusable && view !is ScrollView && view !is HorizontalScrollView) {
                return view
            }
            for (i in 0 until view.childCount) {
                firstFocusableIn(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        return if (view.isFocusable) view else null
    }

    /**
     * Wire D-pad Left from vertical settings rows and the leftmost pill of
     * horizontal groups back to [navId] without interfering with SeekBars,
     * EditTexts, or inner horizontal navigation.
     */
    private fun wireContentBackToNav(view: View, navId: Int) {
        if (view.visibility != View.VISIBLE) return
        if (view is SeekBar || view is EditText) return
        if (view is HorizontalScrollView) {
            firstFocusableIn(view)?.nextFocusLeftId = navId
            return
        }
        if (view is LinearLayout && view.orientation == LinearLayout.HORIZONTAL) {
            if (view.isFocusable) {
                view.nextFocusLeftId = navId
            } else {
                for (i in 0 until view.childCount) {
                    val leftChild = firstFocusableIn(view.getChildAt(i))
                    if (leftChild != null && leftChild !is SeekBar && leftChild !is EditText) {
                        leftChild.nextFocusLeftId = navId
                        break
                    }
                }
            }
            return
        }
        if (view.isFocusable && view !is ScrollView) {
            view.nextFocusLeftId = navId
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                wireContentBackToNav(view.getChildAt(i), navId)
            }
        }
    }

    private fun wirePageFocusTraversal(body: View) {
        val firstContent = firstFocusableIn(body)
        val firstContentId = firstContent?.let(::ensureViewId) ?: View.NO_ID
        val backBtn = headerBackBtn
        val searchBtn = headerSearchBtn
        val activeNav = navItems[currentPage]?.container
        if (activeNav != null) {
            val activeNavId = ensureViewId(activeNav)
            navItems.values.forEach { row ->
                row.container.nextFocusRightId = firstContentId
            }
            navItems[SettingsPage.entries.first()]?.container?.nextFocusUpId =
                backBtn?.let(::ensureViewId) ?: View.NO_ID
            backBtn?.nextFocusDownId = activeNavId
            searchBtn?.nextFocusDownId = if (firstContentId != View.NO_ID) firstContentId else activeNavId
            wireContentBackToNav(body, activeNavId)
        } else {
            val dropdown = pageDropdownRow
            val dropdownId = dropdown?.let(::ensureViewId) ?: View.NO_ID
            backBtn?.nextFocusDownId = dropdownId
            searchBtn?.nextFocusDownId = dropdownId
            dropdown?.nextFocusDownId = firstContentId
        }
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
        contentScroll?.scrollTo(0, 0)
        paintNav()
        headerBreadcrumbLabel?.setText(page.titleRes)
        pageDropdownLabel?.setText(page.titleRes)
        pageDropdownIcon?.setImageResource(page.iconRes)
        wirePageFocusTraversal(body)
    }

    private fun paintNav() {
        val activeBgColor = SettingsUiLogic.navActiveSurface(themeTokens, accent)
        val activeIconTint = ThemePack.accentTextColor(accent, activeBgColor)
        val activeBorder = ThemePack.accentHairline(accent)
        navItems.forEach { (page, row) ->
            val selected = page == currentPage
            row.container.isSelected = selected
            row.indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            row.icon.imageTintList = ColorStateList.valueOf(
                if (selected) activeIconTint else UiDimens.ALPHA_MUTED,
            )
            row.label.setTextColor(if (selected) UiDimens.ALPHA_PRIMARY else UiDimens.ALPHA_SECONDARY)
            row.label.setTypeface(
                null,
                if (selected) Typeface.BOLD else Typeface.NORMAL,
            )
            row.container.background = if (selected) {
                TileBackgrounds.pressableRow(
                    context = this,
                    accent = activeFocusRing,
                    idleBackground = pillDrawable(activeBgColor, 14, activeBorder),
                    cornerRadiusDp = 14,
                    strokeDp = 2,
                )
            } else {
                TileBackgrounds.pressableRow(
                    context = this,
                    accent = accent,
                    cornerRadiusDp = 14,
                    strokeDp = 2,
                )
            }
        }
    }

    private fun buildNavBlade(): View {
        val blade = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 20)
        }
        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        SettingsPage.entries.forEach { page ->
            val indicator = View(this).apply {
                background = TileBackgrounds.activeAccentBar(this@SettingsActivity, accent, cornerRadiusDp = 2)
                visibility = View.INVISIBLE
            }
            val icon = ImageView(this).apply {
                setImageResource(page.iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val label = TextView(this).apply {
                setText(page.titleRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setTextColor(UiDimens.ALPHA_SECONDARY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val container = LinearLayout(this).apply {
                id = View.generateViewId()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPaddingRelative(dp(8), dp(10), dp(12), dp(10))
                isFocusable = true
                contentDescription = getString(page.titleRes)
                setOnClickListener { selectPage(page) }
                addView(indicator, LinearLayout.LayoutParams(dp(3), dp(22)))
                addView(
                    icon,
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                        marginStart = dp(10)
                        marginEnd = dp(10)
                    },
                )
                addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            navItems[page] = NavRowViews(container, indicator, icon, label)
            items.addView(container, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) })
        }
        paintNav()
        // Short panels (e.g. single-display 16:9 landscape) cannot fit all
        // eight entries: the blade must scroll and follow focus, so the
        // items below the fold (System/About) stay reachable at any height.
        blade.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(16))
            addView(items, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        return blade
    }

    private fun buildPageDropdown(): View {
        val row = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = TileBackgrounds.pressableRow(
                context = this@SettingsActivity,
                accent = accent,
                idleBackground = TileBackgrounds.card(this@SettingsActivity),
                cornerRadiusDp = 18,
                strokeDp = 2,
            )
            setPaddingRelative(dp(16), dp(12), dp(14), dp(12))
            isFocusable = true
            setOnClickListener { showPagePicker() }
            setOnLongClickListener {
                showSettingsSearch()
                true
            }
        }
        pageDropdownRow = row
        val icon = ImageView(this).apply {
            setImageResource(currentPage.iconRes)
            imageTintList = ColorStateList.valueOf(accentOnCard)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        pageDropdownIcon = icon
        row.addView(
            icon,
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) },
        )
        val label = TextView(this).apply {
            setText(currentPage.titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        pageDropdownLabel = label
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_dropdown)
                imageTintList = ColorStateList.valueOf(accentOnCard)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(22), dp(22)),
        )
        return row
    }

    private fun showSettingsSearch() {
        val jumps = settingsSearchIndex()
        DeckDialogs.promptText(
            context = this,
            titleRes = R.string.settings_search_hint,
            hintRes = R.string.settings_search_hint,
            positiveRes = R.string.action_search,
        ) { query ->
            val hits = SettingsCatalog.matches(query, jumps)
            if (hits.isEmpty()) {
                Toast.makeText(this, R.string.browse_no_matches_short, Toast.LENGTH_SHORT)
                    .show()
                return@promptText
            }
            if (hits.size == 1) {
                jumpToSettingsPage(hits[0].pageId)
                return@promptText
            }
            DeckDialogs.chooseItem(
                context = this,
                titleRes = R.string.settings_search_hint,
                items = hits.map { it.label }.toTypedArray(),
            ) { which ->
                jumpToSettingsPage(hits[which].pageId)
            }
        }
    }

    private fun jumpToSettingsPage(pageId: String) {
        SettingsPage.entries.firstOrNull { it.name == pageId }?.let { selectPage(it) }
    }

    private fun settingsSearchIndex(): List<SettingsJump> = listOf(
        SettingsJump(SettingsCatalog.PAGE_DISPLAY, getString(R.string.settings_page_display_grid),
            "theme display grid chrome wallpaper columns icon card posture hinge flat yield"),
        SettingsJump(SettingsCatalog.PAGE_APPS, getString(R.string.settings_page_apps),
            "apps hidden packages dock helper play-host"),
        SettingsJump(SettingsCatalog.PAGE_CONTROLS, getString(R.string.settings_page_controls),
            "controls remap deadzone haptics lab"),
        SettingsJump(SettingsCatalog.PAGE_AUDIO, getString(R.string.settings_page_audio),
            "audio sound sfx volume pack ambient soundscape chime boot achievement video music ringer mute test"),
        SettingsJump(SettingsCatalog.PAGE_LIBRARY, getString(R.string.settings_page_library),
            "library rom folder rescan hidden collections players retroarch network commands talk ferry save warm continue cinema"),
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
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.settings_page_picker,
            items = labels,
        ) { which ->
            selectPage(SettingsPage.entries[which])
        }
    }

    private fun buildContent(): View {
        val s = app.settings
        val wide = isWideSettings()
        cancelKeyCapture()
        navItems.clear()
        keyChipsByAction.clear()
        pageDropdownRow = null
        pageDropdownIcon = null
        pageDropdownLabel = null
        headerBackBtn = null
        headerSearchBtn = null
        headerBreadcrumbLabel = null
        artHeaderStatusPill = null

        SettingsPage.entries.forEach { page ->
            pageBodies[page] = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(32))
            }
        }

        fun addSection(
            page: SettingsPage,
            title: String,
            card: LinearLayout,
            badgeText: String? = null,
            badgeActive: Boolean = false,
            onBadgeView: ((TextView) -> Unit)? = null,
        ) {
            val root = pageBodies.getValue(page)
            if (badgeText != null) {
                val headerRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        sectionHeader(title),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        },
                    )
                    val badge = statusPill(badgeText, badgeActive)
                    onBadgeView?.invoke(badge)
                    addView(badge)
                }
                root.addView(
                    headerRow,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(24)
                        bottomMargin = dp(10)
                    },
                )
            } else {
                root.addView(sectionHeader(title), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(24)
                    bottomMargin = dp(10)
                })
            }
            root.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

        val dm = resources.displayMetrics
        val screenWidthDp = (dm.widthPixels / dm.density).toInt()
        val screenHeightDp = (dm.heightPixels / dm.density).toInt()
        val shellPad = SettingsUiLogic.shellPaddingDp(wide, screenHeightDp)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPaddingRelative(
                dp(shellPad.horizontal),
                dp(shellPad.top),
                dp(shellPad.horizontal),
                dp(shellPad.bottom),
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_PRIMARY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            background = TileBackgrounds.chromeButton(this@SettingsActivity, accent)
            val backLabel = getString(R.string.action_back)
            contentDescription = backLabel
            tooltipText = backLabel
            isFocusable = true
            setOnClickListener { finish() }
        }
        headerBackBtn = backBtn
        header.addView(
            backBtn,
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        val headerTitles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            addView(
                TextView(this@SettingsActivity).apply {
                    setText(R.string.settings_title)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                    setTextColor(UiDimens.ALPHA_PRIMARY)
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            val breadcrumb = TextView(this@SettingsActivity).apply {
                setText(currentPage.titleRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(SettingsUiLogic.accentOnBlack(accent))
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            headerBreadcrumbLabel = breadcrumb
            addView(
                breadcrumb,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(1) },
            )
        }
        header.addView(
            headerTitles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (SettingsUiLogic.shouldShowShoulderCue(wide)) {
            header.addView(
                TextView(this).apply {
                    text = SettingsUiLogic.SHOULDER_PAGE_HINT
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setTypeface(typeface, Typeface.BOLD)
                    useTabularNumerals()
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    background = TileBackgrounds.sunkenField(
                        this@SettingsActivity,
                        cornerRadiusDp = UiDimens.RADIUS_SM,
                    )
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(10) },
            )
        }
        val searchBtn = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_PRIMARY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            background = TileBackgrounds.chromeButton(this@SettingsActivity, accent)
            val searchLabel = getString(R.string.settings_search_hint)
            contentDescription = searchLabel
            tooltipText = searchLabel
            isFocusable = true
            setOnClickListener { showSettingsSearch() }
        }
        headerSearchBtn = searchBtn
        backBtn.nextFocusRightId = searchBtn.id
        searchBtn.nextFocusLeftId = backBtn.id
        header.addView(
            searchBtn,
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        shell.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val headerRule = FrameLayout(this).apply {
            addView(
                View(this@SettingsActivity).apply {
                    setBackgroundColor(TileBackgrounds.hairlineDivider(this@SettingsActivity))
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    strokePx(1),
                    Gravity.CENTER_VERTICAL,
                ),
            )
            addView(
                View(this@SettingsActivity).apply {
                    background = TileBackgrounds.activeAccentBar(
                        this@SettingsActivity,
                        accent,
                        cornerRadiusDp = 1,
                    )
                },
                FrameLayout.LayoutParams(dp(44), dp(2), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                    marginStart = dp(56)
                },
            )
        }
        shell.addView(
            headerRule,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            },
        )

        if (!wide) {
            shell.addView(buildPageDropdown(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }

        fun toggle(card: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            addDividerIfNeeded(card)
            val sw = accentSwitch(checked, onChange)
            card.addView(
                switchRow(label, sw),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)),
            )
        }

        fun addActionRow(
            card: LinearLayout,
            row: LinearLayout,
            heightDp: Int = 60,
            showChevron: Boolean = false,
        ) {
            addDividerIfNeeded(card)
            if (showChevron) {
                row.addView(
                    drillChevron(),
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(6) },
                )
            }
            styleFocusableRow(row)
            card.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)),
            )
        }

        fun seek(
            card: LinearLayout,
            label: String,
            value: Int,
            min: Int,
            max: Int,
            format: (Int) -> String = { formatNumber(it) },
            onLiveChange: ((Int) -> Unit)? = null,
            onChange: (Int) -> Unit,
        ) {
            addDividerIfNeeded(card)
            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(2))
            }
            labelRow.addView(rowLabel(label), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
            val valueView = TextView(this).apply {
                text = format(value)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(accentOnSunken)
                setTypeface(typeface, Typeface.BOLD)
                useTabularNumerals()
                gravity = Gravity.CENTER
                minWidth = dp(44)
                background = TileBackgrounds.valueBadge(this@SettingsActivity, accent)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            labelRow.addView(valueView)
            card.addView(labelRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(SeekBar(this).apply {
                progressDrawable = ContextCompat.getDrawable(
                    this@SettingsActivity,
                    R.drawable.bg_seekbar_progress,
                )?.mutate()
                thumb = ContextCompat.getDrawable(
                    this@SettingsActivity,
                    R.drawable.bg_seekbar_thumb,
                )?.mutate()
                splitTrack = false
                progressTintList = ColorStateList.valueOf(accent)
                thumbTintList = ColorStateList.valueOf(accent)
                progressBackgroundTintList = ColorStateList.valueOf(offTint)
                background = TileBackgrounds.focusableRow(
                    context = this@SettingsActivity,
                    accent = accent,
                    cornerRadiusDp = 12,
                )
                minimumHeight = dp(42)
                setPadding(dp(14), dp(8), dp(14), dp(10))
                this.max = max - min
                progress = value - min
                var trackingTouch = false
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val current = p + min
                            valueView.text = format(current)
                            onLiveChange?.invoke(current)
                            // Gamepad D-pad left/right adjusts SeekBar without touch events.
                            if (!trackingTouch) onChange(current)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {
                        trackingTouch = true
                    }
                    override fun onStopTrackingTouch(sb: SeekBar) {
                        trackingTouch = false
                        val committed = sb.progress + min
                        onLiveChange?.invoke(committed)
                        onChange(committed)
                    }
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
        segmentedRow(
            card = displayCard,
            label = getString(R.string.settings_default_mode),
            options = listOf(
                UIMode.GRID.name to getString(R.string.label_grid),
                UIMode.GAME.name to getString(R.string.label_game),
            ),
            current = s.defaultMode.name,
        ) { app.updateSettings(app.settings.copy(defaultMode = UIMode.valueOf(it))) }

        val themeOptions = ThemePack.BUILTINS.map {
            it.id to themeName(it.id).uppercase(resources.configuration.locales[0])
        }
        val themeSwatches = ThemePack.BUILTINS.associate { it.id to it.accentColor }
        val themeCurrent = ThemePack.resolve(s).id.let { id ->
            if (ThemePack.BUILTINS.any { it.id == id }) id else ThemePack.GHOST.id
        }
        segmentedRow(
            card = displayCard,
            label = getString(R.string.settings_theme),
            options = themeOptions,
            current = themeCurrent,
            swatchColors = themeSwatches,
        ) { packId ->
            val tokens = ThemePack.byId(packId)
            applyThemeAndRefresh(ThemePack.applyToSettings(app.settings, tokens))
        }
        val themePreview = ThemePreviewView(this, themeTokens, accent)
        displayCard.addView(
            themePreview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
                topMargin = dp(4)
                bottomMargin = dp(10)
            },
        )
        val themeImportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(if (s.themeCustomJson != null) accentOnCard else UiDimens.ALPHA_SUBTLE)
        })
        addActionRow(displayCard, themeImportRow, showChevron = true)
        val profileOptions = listOf(
            "auto" to getString(R.string.label_auto),
            "onex-sugar" to getString(R.string.label_sugar),
            "generic-dual" to getString(R.string.label_dual),
            "single" to getString(R.string.label_single),
        )
        segmentedRow(
            card = displayCard,
            label = getString(R.string.settings_device_profile),
            options = profileOptions,
            current = s.deviceProfileId,
        ) { id ->
            app.updateSettings(app.settings.copy(
                deviceProfileId = id,
                userPinnedPrimaryId = null,
            ))
            app.refreshDisplayConfig()
            refreshSettingsUi()
        }
        val interactiveOptions = listOf(
            "auto" to getString(R.string.label_auto),
            "default" to getString(R.string.label_default),
            "secondary" to getString(R.string.label_secondary),
        )
        val interactiveCurrent = when {
            s.interactiveDisplayMode.startsWith("id:") -> "auto"
            else -> s.interactiveDisplayMode
        }
        segmentedRow(
            card = displayCard,
            label = getString(R.string.settings_interactive_display),
            options = interactiveOptions,
            current = interactiveCurrent,
        ) { mode ->
            app.updateSettings(app.settings.copy(
                interactiveDisplayMode = mode,
                userPinnedPrimaryId = null,
            ))
            app.refreshDisplayConfig()
            refreshSettingsUi()
        }
        val orientOptions = listOf(
            "auto" to getString(R.string.label_auto),
            "sensor_landscape" to getString(R.string.label_sensor),
            "lock_landscape" to getString(R.string.label_lock),
        )
        segmentedRow(
            card = displayCard,
            label = getString(R.string.settings_orientation),
            options = orientOptions,
            current = s.orientationMode,
        ) { mode ->
            app.updateSettings(
                app.settings.copy(
                    orientationMode = mode,
                    angleLock = mode == "lock_landscape",
                    gyroEnabled = mode != "lock_landscape",
                ),
            )
            refreshSettingsUi()
        }
        val resetDisplayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        addActionRow(displayCard, resetDisplayRow)
        val liveSurface = app.displayConfig.mode
        val displayModeBadge = getString(
            R.string.format_dot_pair,
            getString(if (liveSurface == SurfaceMode.DUAL) R.string.label_dual else R.string.label_single),
            deviceProfileName(s.deviceProfileId),
        )
        addSection(
            page = SettingsPage.DISPLAY_GRID,
            title = getString(R.string.settings_section_display),
            card = displayCard,
            badgeText = displayModeBadge,
            badgeActive = liveSurface == SurfaceMode.DUAL,
        )

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
        var updatePowerExpanderMeta: ((BrowseChrome, Boolean) -> Unit)? = null
        fun rebindChromeUi(c: BrowseChrome) {
            rebindChromePreset?.invoke(chromePresetId(c))
            suppressChromeSwitchCallbacks = true
            try {
                chromeSwitchRebinds.forEach { it(c) }
            } finally {
                suppressChromeSwitchCallbacks = false
            }
            val visible = c.powerRailsPanelVisible()
            powerHost.visibility = if (visible) View.VISIBLE else View.GONE
            updatePowerExpanderMeta?.invoke(c, true)
        }
        segmentedRow(
            card = chromeCard,
            label = getString(R.string.settings_chrome_preset),
            options = presetOptions,
            current = chromePresetId(chrome),
            bindSelected = { set -> rebindChromePreset = set },
        ) { id ->
            when (id) {
                BrowseChrome.PRESET_FULL -> {
                    val next = BrowseChrome.FULL
                    val prev = app.settings.browseChrome
                    app.updateSettings(
                        app.settings.copy(browseChrome = next),
                        chromeOnly = next.allowsInPlaceChromeUpdate(prev),
                    )
                    rebindChromeUi(next)
                }
                BrowseChrome.PRESET_MINIMAL -> {
                    val next = BrowseChrome.MINIMAL
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
        }
        chromeCard.addView(TextView(this).apply {
            setText(R.string.settings_chrome_custom_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_SUBTLE)
            setPadding(dp(10), 0, dp(10), dp(6))
        })
        // Core always-visible toggles + power rails: keep Switch refs so
        // Minimal/Full rebind isChecked without a full Settings recreate.
        fun chromeFlag(
            host: LinearLayout,
            label: String,
            get: (BrowseChrome) -> Boolean,
            set: (BrowseChrome, Boolean) -> BrowseChrome,
        ) {
            addDividerIfNeeded(host)
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
                updatePowerExpanderMeta?.invoke(next, true)
            }
            host.addView(
                switchRow(label, sw),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(60),
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
        // Power rails expander — collapsed when currently minimal, with live count pill & chevron.
        powerHost.visibility = if (chrome.powerRailsPanelVisible()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val powerCountBadge = TextView(this).apply {
            maxLines = 1
        }
        val expanderChevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_dropdown)
            imageTintList = ColorStateList.valueOf(accentOnCard)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    v.animate().cancel()
                }
            })
        }
        updatePowerExpanderMeta = { c, animate ->
            val enabled = SettingsUiLogic.enabledPowerRailsCount(c)
            powerCountBadge.text = getString(
                R.string.format_progress,
                enabled,
                SettingsUiLogic.TOTAL_POWER_RAILS,
            )
            styleStatusBadge(powerCountBadge, active = enabled > 0)
            val targetDeg = SettingsUiLogic.chevronRotationDegrees(powerHost.visibility == View.VISIBLE)
            expanderChevron.animate().cancel()
            if (animate && UiDimens.areAnimationsEnabled(this)) {
                expanderChevron.animate().rotation(targetDeg).setDuration(160L).start()
            } else {
                expanderChevron.rotation = targetDeg
            }
        }
        val expander = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                powerHost.visibility =
                    if (powerHost.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                updatePowerExpanderMeta.invoke(app.settings.browseChrome, true)
            }
        }
        expander.addView(rowLabel(getString(R.string.settings_chrome_power_rails)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        expander.addView(
            powerCountBadge,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) },
        )
        expander.addView(TextView(this).apply {
            text = getString(R.string.settings_chrome_expand)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(accentOnCard)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(4) })
        expander.addView(expanderChevron, LinearLayout.LayoutParams(dp(20), dp(20)))
        updatePowerExpanderMeta.invoke(chrome, false)
        addActionRow(chromeCard, expander, heightDp = 56)
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
        segmentedRow(
            card = companionCard,
            label = getString(R.string.settings_companion_role),
            options = roleOptions,
            current = s.companionRole,
        ) { next ->
            app.updateSettings(app.settings.copy(companionRole = next))
        }
        val pinRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(accentOnCard)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        addActionRow(companionCard, pinRow, showChevron = true)
        companionCard.addView(TextView(this).apply {
            setText(R.string.settings_pin_help)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_SUBTLE)
            setPadding(dp(10), 0, dp(10), dp(6))
        })
        addSection(SettingsPage.DISPLAY_GRID, getString(R.string.settings_companion_section), companionCard)

        val gridCard = sectionCard()
        var liveGridCols = s.gridColumns
        var liveIconDp = s.iconSizeDp
        var liveCardDp = s.cardSizeDp
        var liveShowLabels = s.showLabels
        var liveGridDir = s.gridDirection
        val gridPreview = GridDensityPreviewView(
            context = this,
            tokens = themeTokens,
            accent = accent,
            columns = liveGridCols,
            iconSizeDp = liveIconDp,
            cardSizeDp = liveCardDp,
            showLabels = liveShowLabels,
            gridDirection = liveGridDir,
        )
        fun updateGridPreview() {
            gridPreview.update(
                columns = liveGridCols,
                iconSizeDp = liveIconDp,
                cardSizeDp = liveCardDp,
                showLabels = liveShowLabels,
                gridDirection = liveGridDir,
            )
        }
        gridCard.addView(
            gridPreview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
                topMargin = dp(6)
                bottomMargin = dp(8)
            },
        )
        segmentedRow(
            card = gridCard,
            label = getString(R.string.settings_grid_scrolling),
            options = listOf(
                "vertical" to getString(R.string.label_vertical),
                "horizontal" to getString(R.string.label_horizontal),
            ),
            current = s.gridDirection,
        ) {
            liveGridDir = it
            updateGridPreview()
            app.updateSettings(app.settings.copy(gridDirection = it))
        }
        seek(
            card = gridCard,
            label = getString(R.string.settings_grid_columns),
            value = s.gridColumns,
            min = 3,
            max = 8,
            onLiveChange = { liveGridCols = it; updateGridPreview() },
        ) {
            liveGridCols = it
            updateGridPreview()
            app.updateSettings(app.settings.copy(gridColumns = it))
        }
        seek(
            card = gridCard,
            label = getString(R.string.settings_icon_size),
            value = s.iconSizeDp,
            min = 48,
            max = 128,
            onLiveChange = { liveIconDp = it; updateGridPreview() },
        ) {
            liveIconDp = it
            updateGridPreview()
            app.updateSettings(app.settings.copy(iconSizeDp = it))
        }
        seek(
            card = gridCard,
            label = getString(R.string.settings_card_size),
            value = s.cardSizeDp,
            min = 120,
            max = 320,
            onLiveChange = { liveCardDp = it; updateGridPreview() },
        ) {
            liveCardDp = it
            updateGridPreview()
            app.updateSettings(
                app.settings.copy(cardSizeDp = it),
                chromeOnly = true,
            )
        }
        toggle(gridCard, getString(R.string.settings_show_app_names), s.showLabels) {
            liveShowLabels = it
            updateGridPreview()
            app.updateSettings(app.settings.copy(showLabels = it))
        }
        val wallpaperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { wallpaperPicker.launch(arrayOf("image/*")) }
        }
        wallpaperRow.addView(rowLabel(getString(R.string.settings_grid_wallpaper)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accentOnCard)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        wallpaperValue = valueView
        wallpaperRow.addView(valueView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
        val clearView = TextView(this).apply {
            setText(R.string.action_clear)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(38)
            isFocusable = true
            background = TileBackgrounds.pressableRow(
                context = this@SettingsActivity,
                accent = accent,
                idleBackground = TileBackgrounds.chipIdle(this@SettingsActivity, cornerRadiusDp = 14),
                cornerRadiusDp = 14,
                strokeDp = 2,
            )
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
        addActionRow(gridCard, wallpaperRow, showChevron = true)
        addSection(SettingsPage.DISPLAY_GRID, getString(R.string.settings_grid_section), gridCard)

        val appsCard = sectionCard()
        val hiddenRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showHiddenAppsDialog() }
        }
        hiddenRow.addView(rowLabel(getString(R.string.settings_hidden_apps)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hiddenValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accentOnCard)
        }
        hiddenRow.addView(hiddenValue)
        addActionRow(appsCard, hiddenRow, showChevron = true)
        val dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showDockDialog() }
        }
        dockRow.addView(rowLabel(getString(R.string.label_dock)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dockValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accentOnCard)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        dockRow.addView(dockValue, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        addActionRow(appsCard, dockRow, showChevron = true)
        val packageYieldRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showPackageYieldDialog() }
        }
        packageYieldRow.addView(
            rowLabel(getString(R.string.settings_package_yield)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        packageYieldValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accentOnCard)
        }
        packageYieldRow.addView(packageYieldValue)
        addActionRow(appsCard, packageYieldRow, showChevron = true)
        val helperRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showPlayHostHelperPicker() }
            setOnLongClickListener {
                app.updateSettings(app.settings.copy(playHostHelperPackage = null))
                refreshSettingsUi()
                true
            }
        }
        helperRow.addView(
            rowLabel(getString(R.string.settings_play_host_helper)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        playHostHelperValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accentOnCard)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        helperRow.addView(
            playHostHelperValue,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f),
        )
        addActionRow(appsCard, helperRow, showChevron = true)
        refreshAppsRows()
        val dockFilled = s.dockSlots.count { it != null }
        val appsBadge = if (dockFilled > 0) {
            resources.getQuantityString(R.plurals.count_items, dockFilled, dockFilled)
        } else {
            getString(R.string.label_empty)
        }
        addSection(
            page = SettingsPage.APPS,
            title = getString(R.string.settings_page_apps),
            card = appsCard,
            badgeText = appsBadge,
            badgeActive = dockFilled > 0,
        )

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
            setTextColor(UiDimens.ALPHA_SUBTLE)
            setPadding(dp(10), 0, dp(10), dp(6))
        })
        toggle(controlsCard, getString(R.string.settings_winlator_cockpit), s.winlatorCockpit) {
            app.updateSettings(app.settings.copy(winlatorCockpit = it))
        }
        // empty stored list = DEFAULT_ANCHORS (SNES-like lower-right 40%).
        val seatLayoutHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (SettingsUiLogic.isSecondSeatLayoutVisible(s.raSecondSeat)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        toggle(controlsCard, getString(R.string.settings_second_seat), s.raSecondSeat) { on ->
            app.updateSettings(app.settings.copy(raSecondSeat = on))
            seatLayoutHost.visibility = if (SettingsUiLogic.isSecondSeatLayoutVisible(on)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        fun bindSeatLayout() {
            seatLayoutHost.removeAllViews()
            addDivider(seatLayoutHost)
            val resetRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
                setTextColor(accentOnCard)
                setTypeface(typeface, Typeface.BOLD)
            })
            styleFocusableRow(resetRow)
            seatLayoutHost.addView(
                resetRow,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)),
            )
            val shown = SecondSeatPolicy.anchorsOrDefault(app.settings.raSeatAnchors)
            val seatPreview = SecondSeatPreviewView(
                context = this,
                tokens = themeTokens,
                accent = accent,
                initialAnchors = shown,
            )
            seatLayoutHost.addView(
                seatPreview,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)).apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                    topMargin = dp(6)
                    bottomMargin = dp(8)
                },
            )
            val colHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(10), dp(6))
            }
            colHeader.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            colHeader.addView(
                TextView(this).apply {
                    setText(R.string.label_horizontal)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                },
            )
            colHeader.addView(
                TextView(this).apply {
                    setText(R.string.label_vertical)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                },
            )
            seatLayoutHost.addView(
                colHeader,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            for (anchor in shown) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                }
                row.addView(
                    TextView(this).apply {
                        text = anchor.id
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(UiDimens.ALPHA_PRIMARY)
                        setTypeface(typeface, Typeface.BOLD)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                fun coordField(anchorId: String, value: Float, write: (Float) -> Unit): EditText =
                    EditText(this).apply {
                        isSingleLine = true
                        inputType = InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setText(value.toString())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(UiDimens.ALPHA_PRIMARY)
                        useTabularNumerals()
                        gravity = Gravity.CENTER
                        background = TileBackgrounds.focusableRow(
                            context = this@SettingsActivity,
                            accent = accent,
                            idleBackground = TileBackgrounds.sunkenField(
                                this@SettingsActivity,
                                cornerRadiusDp = 10,
                            ),
                            cornerRadiusDp = 10,
                            strokeDp = 2,
                        )
                        setPadding(dp(10), dp(8), dp(10), dp(8))
                        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                seatPreview.setHighlightedAnchor(anchorId)
                                return@OnFocusChangeListener
                            }
                            seatPreview.setHighlightedAnchor(null)
                            val parsed = text?.toString()?.toFloatOrNull()?.coerceIn(0f, 1f)
                                ?: return@OnFocusChangeListener
                            write(parsed)
                            seatPreview.setAnchors(
                                SecondSeatPolicy.anchorsOrDefault(app.settings.raSeatAnchors),
                            )
                        }
                    }
                row.addView(
                    coordField(anchor.id, anchor.nx) { nx ->
                        commitSeatAnchor(anchor.id, nx, null)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(8)
                    },
                )
                row.addView(
                    coordField(anchor.id, anchor.ny) { ny ->
                        commitSeatAnchor(anchor.id, null, ny)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(8)
                    },
                )
                seatLayoutHost.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(4) },
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
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ControllerLabActivity::class.java))
            }
        }
        labRow.addView(rowLabel(getString(R.string.settings_controller_lab)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        labRow.addView(TextView(this).apply {
            setText(R.string.action_open)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accentOnCard)
            setTypeface(typeface, Typeface.BOLD)
        })
        addActionRow(controlsCard, labRow, showChevron = true)
        var controlsDeadzoneBadge: TextView? = null
        fun updateDeadzoneBadge(percent: Int) {
            controlsDeadzoneBadge?.apply {
                text = getString(R.string.format_percent, percent)
                styleStatusBadge(this, active = SettingsUiLogic.isCustomDeadzone(percent))
            }
        }
        seek(
            card = controlsCard,
            label = getString(R.string.settings_stick_deadzone),
            value = s.stickDeadzone,
            min = 20,
            max = 80,
            format = { n -> getString(R.string.format_percent, n) },
            onLiveChange = ::updateDeadzoneBadge,
        ) { n ->
            updateDeadzoneBadge(n)
            app.updateSettings(app.settings.copy(stickDeadzone = n), notify = false)
        }
        remappable.forEach { action ->
            val boundCode = SettingsUiLogic.boundKeyCodeFor(action, app.settings.keyMap)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(rowLabel(resolveText(action.label())), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val chip = keyChip(boundCode)
            keyChipsByAction[action] = chip
            row.addView(chip)
            row.setOnClickListener { startKeyCapture(action, chip) }
            addActionRow(controlsCard, row)
        }
        addSection(
            page = SettingsPage.CONTROLS,
            title = getString(R.string.settings_page_controls),
            card = controlsCard,
            badgeText = getString(R.string.format_percent, s.stickDeadzone),
            badgeActive = SettingsUiLogic.isCustomDeadzone(s.stickDeadzone),
            onBadgeView = { controlsDeadzoneBadge = it },
        )

        // --- Audio page (all updates use notify = false to avoid dual-paint rebuilds) ---
        var audioSfxBadge: TextView? = null
        var audioAmbientBadge: TextView? = null
        var liveSoundEnabled = s.soundEnabled
        var liveSoundPack = SoundPackId.fromId(s.soundPack).id
        var liveSfxVolume = s.sfxVolume
        var liveAmbientEnabled = s.ambientEnabled
        var liveAmbientVolume = s.ambientVolume

        fun refreshAudioBadges() {
            val sfxActive = SettingsUiLogic.isSfxAudioActive(
                liveSoundEnabled, liveSoundPack, liveSfxVolume,
            )
            audioSfxBadge?.apply {
                text = if (sfxActive) {
                    getString(R.string.format_percent, liveSfxVolume)
                } else {
                    getString(R.string.label_off)
                }
                styleStatusBadge(this, active = sfxActive)
            }
            val ambientActive = SettingsUiLogic.isAmbientAudioActive(
                liveSoundEnabled, liveAmbientEnabled, liveSoundPack, liveAmbientVolume,
            )
            audioAmbientBadge?.apply {
                text = if (ambientActive) {
                    getString(R.string.format_percent, liveAmbientVolume)
                } else {
                    getString(R.string.label_off)
                }
                styleStatusBadge(this, active = ambientActive)
            }
        }

        val audioSfxCard = sectionCard()
        toggle(audioSfxCard, getString(R.string.settings_audio_master), s.soundEnabled) { on ->
            liveSoundEnabled = on
            app.updateSettings(app.settings.copy(soundEnabled = on), notify = false)
            refreshAudioBadges()
            if (on) {
                app.soundEngine.audition(SoundCue.CONFIRM)
            }
        }
        val audioLocale = resources.configuration.locales[0]
        val packOptions = listOf(
            SoundPackId.AUTO.id to getString(R.string.label_auto).uppercase(audioLocale),
            SoundPackId.GHOST.id to themeName(ThemePack.GHOST.id).uppercase(audioLocale),
            SoundPackId.THREEDS.id to themeName(ThemePack.THREEDS.id).uppercase(audioLocale),
            SoundPackId.OLED.id to getString(R.string.settings_audio_pack_oled).uppercase(audioLocale),
            SoundPackId.NEON.id to themeName(ThemePack.NEON.id).uppercase(audioLocale),
            SoundPackId.OFF.id to getString(R.string.label_off).uppercase(audioLocale),
        )
        val packSwatches = mapOf(
            SoundPackId.GHOST.id to ThemePack.GHOST.accentColor,
            SoundPackId.THREEDS.id to ThemePack.THREEDS.accentColor,
            SoundPackId.OLED.id to ThemePack.OLED.accentColor,
            SoundPackId.NEON.id to ThemePack.NEON.accentColor,
        )
        segmentedRow(
            card = audioSfxCard,
            label = getString(R.string.settings_audio_pack),
            options = packOptions,
            current = liveSoundPack,
            swatchColors = packSwatches,
        ) { nextId ->
            val pack = SoundPackId.fromId(nextId)
            liveSoundPack = pack.id
            app.updateSettings(app.settings.copy(soundPack = pack.id), notify = false)
            refreshAudioBadges()
            app.soundEngine.previewPack(pack)
        }
        seek(
            card = audioSfxCard,
            label = getString(R.string.settings_audio_sfx_volume),
            value = s.sfxVolume,
            min = 0,
            max = 100,
            format = { n -> getString(R.string.format_percent, n) },
            onLiveChange = { n ->
                liveSfxVolume = n
                refreshAudioBadges()
            },
        ) { n ->
            liveSfxVolume = n
            app.updateSettings(app.settings.copy(sfxVolume = n), notify = false)
            refreshAudioBadges()
            app.soundEngine.audition(SoundCue.NAV_TICK)
        }
        val initialSfxActive = SettingsUiLogic.isSfxAudioActive(
            liveSoundEnabled, liveSoundPack, liveSfxVolume,
        )
        addSection(
            page = SettingsPage.AUDIO,
            title = getString(R.string.settings_audio_section_sfx),
            card = audioSfxCard,
            badgeText = if (initialSfxActive) {
                getString(R.string.format_percent, liveSfxVolume)
            } else {
                getString(R.string.label_off)
            },
            badgeActive = initialSfxActive,
            onBadgeView = { audioSfxBadge = it },
        )

        val audioTestPadCard = sectionCard()
        audioTestPadCard.addView(
            TextView(this).apply {
                setText(R.string.settings_audio_test_pad_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(UiDimens.ALPHA_SUBTLE)
                setPadding(dp(10), dp(4), dp(10), dp(8))
            },
        )
        val padCueLabels = mapOf(
            SoundCue.NAV_TICK to R.string.settings_audio_cue_nav,
            SoundCue.CONFIRM to R.string.settings_audio_cue_confirm,
            SoundCue.LAUNCH_GAME to R.string.settings_audio_cue_launch,
            SoundCue.SWAP_DISPLAY to R.string.settings_audio_cue_swap,
            SoundCue.FAVORITE_ON to R.string.settings_audio_cue_favorite,
            SoundCue.RA_ACHIEVEMENT to R.string.settings_audio_cue_achievement,
        )
        val padCols = SettingsUiLogic.audioTestPadColumns(wide)
        val padRows = SettingsUiLogic.AUDIO_TEST_PAD_CUES.chunked(padCols)
        padRows.forEachIndexed { rowIndex, rowCues ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
            }
            val rowPills = mutableListOf<TextView>()
            rowCues.forEachIndexed { colIndex, cue ->
                val labelRes = padCueLabels.getValue(cue)
                val pill = TextView(this).apply {
                    id = View.generateViewId()
                    setText(labelRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(UiDimens.ALPHA_PRIMARY)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.04f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    minimumHeight = dp(44)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    isFocusable = true
                    background = TileBackgrounds.pressableRow(
                        context = this@SettingsActivity,
                        accent = accent,
                        idleBackground = TileBackgrounds.sunkenField(
                            this@SettingsActivity,
                            cornerRadiusDp = 12,
                        ),
                        cornerRadiusDp = 12,
                        strokeDp = 2,
                    )
                    setOnClickListener {
                        app.soundEngine.audition(cue)
                    }
                }
                rowPills.add(pill)
                row.addView(
                    pill,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (colIndex > 0) marginStart = dp(8)
                    },
                )
            }
            rowPills.forEachIndexed { colIndex, pill ->
                if (colIndex > 0) pill.nextFocusLeftId = rowPills[colIndex - 1].id
                if (colIndex < rowPills.lastIndex) pill.nextFocusRightId = rowPills[colIndex + 1].id
            }
            audioTestPadCard.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = if (rowIndex < padRows.lastIndex) dp(8) else dp(6)
                },
            )
        }
        addSection(
            page = SettingsPage.AUDIO,
            title = getString(R.string.settings_audio_section_test_pad),
            card = audioTestPadCard,
        )

        val audioAmbientCard = sectionCard()
        toggle(audioAmbientCard, getString(R.string.settings_audio_ambient), s.ambientEnabled) { on ->
            liveAmbientEnabled = on
            app.updateSettings(app.settings.copy(ambientEnabled = on), notify = false)
            refreshAudioBadges()
        }
        seek(
            card = audioAmbientCard,
            label = getString(R.string.settings_audio_ambient_volume),
            value = s.ambientVolume,
            min = 0,
            max = 100,
            format = { n -> getString(R.string.format_percent, n) },
            onLiveChange = { n ->
                liveAmbientVolume = n
                refreshAudioBadges()
            },
        ) { n ->
            liveAmbientVolume = n
            app.updateSettings(app.settings.copy(ambientVolume = n), notify = false)
            refreshAudioBadges()
        }
        audioAmbientCard.addView(
            TextView(this).apply {
                setText(R.string.settings_audio_ambient_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(UiDimens.ALPHA_SUBTLE)
                setPadding(dp(10), dp(2), dp(10), dp(6))
            },
        )
        val initialAmbientActive = SettingsUiLogic.isAmbientAudioActive(
            liveSoundEnabled, liveAmbientEnabled, liveSoundPack, liveAmbientVolume,
        )
        addSection(
            page = SettingsPage.AUDIO,
            title = getString(R.string.settings_audio_section_ambient),
            card = audioAmbientCard,
            badgeText = if (initialAmbientActive) {
                getString(R.string.format_percent, liveAmbientVolume)
            } else {
                getString(R.string.label_off)
            },
            badgeActive = initialAmbientActive,
            onBadgeView = { audioAmbientBadge = it },
        )

        val audioEventsCard = sectionCard()
        toggle(audioEventsCard, getString(R.string.settings_audio_boot_chime), s.bootChimeEnabled) { on ->
            app.updateSettings(app.settings.copy(bootChimeEnabled = on), notify = false)
        }
        toggle(audioEventsCard, getString(R.string.settings_audio_ra_chime), s.raAchievementChime) { on ->
            app.updateSettings(app.settings.copy(raAchievementChime = on), notify = false)
        }
        toggle(audioEventsCard, getString(R.string.settings_audio_video_preview), s.videoPreviewAudio) { on ->
            app.updateSettings(app.settings.copy(videoPreviewAudio = on), notify = false)
        }
        addSection(
            page = SettingsPage.AUDIO,
            title = getString(R.string.settings_audio_section_events),
            card = audioEventsCard,
        )

        val audioEtiquetteCard = sectionCard()
        toggle(audioEtiquetteCard, getString(R.string.settings_audio_respect_music), s.audioRespectMusic) { on ->
            app.updateSettings(app.settings.copy(audioRespectMusic = on), notify = false)
        }
        toggle(audioEtiquetteCard, getString(R.string.settings_audio_respect_ringer), s.audioRespectRinger) { on ->
            app.updateSettings(app.settings.copy(audioRespectRinger = on), notify = false)
        }
        addSection(
            page = SettingsPage.AUDIO,
            title = getString(R.string.settings_audio_section_etiquette),
            card = audioEtiquetteCard,
        )

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
            setOnClickListener { romFolderPicker.launch(null) }
        }
        addRow.addView(rowLabel(getString(R.string.settings_add_rom_folder)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addRow.addView(TextView(this).apply {
            setText(R.string.glyph_add)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(accentOnSunken)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = TileBackgrounds.valueBadge(this@SettingsActivity, accent, cornerRadiusDp = 12)
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        addActionRow(libraryCard, addRow)
        val hiddenRomsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showHiddenRomsDialog() }
        }
        hiddenRomsRow.addView(rowLabel(getString(R.string.settings_hidden_roms)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hiddenRomsValue = TextView(this).apply {
            val count = app.settings.hiddenRomIds.size
            text = formatNumber(count)
            styleStatusBadge(this, active = count > 0)
        }
        hiddenRomsRow.addView(hiddenRomsValue)
        addActionRow(libraryCard, hiddenRomsRow, showChevron = true)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(UiDimens.ALPHA_MUTED)
            background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = UiDimens.RADIUS_SM)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        })
        addActionRow(libraryCard, rescanRow)
        val pinFavsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        addActionRow(libraryCard, pinFavsRow)
        val collectionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showCollectionsDialog() }
        }
        collectionsRow.addView(rowLabel(getString(R.string.label_collections)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        collectionsRow.addView(TextView(this).apply {
            val count = app.settings.collections.size
            text = formatNumber(count)
            styleStatusBadge(this, active = count > 0)
        })
        addActionRow(libraryCard, collectionsRow, showChevron = true)
        val playersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(accentOnCard)
        })
        addActionRow(libraryCard, playersRow, showChevron = true)
        var raHandoffSaveRow: View? = null
        var raHandoffSaveSwitch: SwitchCompat? = null
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
        addDividerIfNeeded(libraryCard)
        val handoffRow = switchRow(
            getString(R.string.settings_ra_handoff_save),
            handoffSwitch,
        )
        raHandoffSaveRow = handoffRow
        libraryCard.addView(
            handoffRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)),
        )
        refreshRaHandoffSaveEnabled()
        toggle(libraryCard, getString(R.string.settings_ra_cinema), s.raCinemaEnabled) { on ->
            app.updateSettings(app.settings.copy(raCinemaEnabled = on))
        }
        toggle(libraryCard, getString(R.string.settings_ra_theater), s.raTheaterEnabled) { on ->
            app.updateSettings(app.settings.copy(raTheaterEnabled = on))
        }
        var warmLoadRow: View? = null
        var warmLoadSwitch: SwitchCompat? = null
        fun refreshWarmLoadEnabled() {
            val on = app.settings.warmResumeEnabled
            warmLoadRow?.isEnabled = on
            warmLoadRow?.alpha = if (on) 1f else 0.5f
            warmLoadSwitch?.isEnabled = on
        }
        toggle(libraryCard, getString(R.string.settings_warm_resume), s.warmResumeEnabled) { on ->
            app.updateSettings(app.settings.copy(warmResumeEnabled = on))
            refreshWarmLoadEnabled()
        }
        val warmLoadToggle = accentSwitch(s.warmResumeLoad) { on ->
            if (!app.settings.warmResumeEnabled) return@accentSwitch
            app.updateSettings(app.settings.copy(warmResumeLoad = on))
        }
        warmLoadSwitch = warmLoadToggle
        addDividerIfNeeded(libraryCard)
        val warmLoadControl = switchRow(
            getString(R.string.settings_warm_resume_load),
            warmLoadToggle,
        )
        warmLoadRow = warmLoadControl
        libraryCard.addView(
            warmLoadControl,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)),
        )
        refreshWarmLoadEnabled()
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
            setTextColor(accentOnCard)
        })
        addActionRow(libraryCard, lensPackRow, showChevron = true)
        val raUserRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addActionRow(artCard, raUserRow, showChevron = true)
        val raKeyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        })
        addActionRow(artCard, raKeyRow, showChevron = true)
        refreshRaRows()
        if (BuildConfig.DEBUG) {
            val raSampleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { loadRaSampleForSelection() }
            }
            raSampleRow.addView(rowLabel(getString(R.string.settings_load_ra_sample)), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            raSampleRow.addView(TextView(this).apply {
                setText(R.string.settings_demo)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(UiDimens.ALPHA_SUBTLE)
            })
            addActionRow(artCard, raSampleRow)
        }
        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        val keyValue = TextView(this)
        sgdbKeyValue = keyValue
        keyRow.addView(keyValue)
        addActionRow(artCard, keyRow, showChevron = true)
        val datRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(accentOnCard)
            useTabularNumerals()
        })
        addActionRow(artCard, datRow, showChevron = true)
        val downloadRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setOnClickListener { onScrapeRowClicked() }
        }
        val downloadTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val downloadLabel = rowLabel(getString(R.string.settings_download_artwork))
        scrapeLabel = downloadLabel
        downloadTop.addView(downloadLabel, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        val downloadValue = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(accentOnCard)
            setTypeface(typeface, Typeface.BOLD)
            useTabularNumerals()
        }
        scrapeValue = downloadValue
        downloadTop.addView(downloadValue)
        downloadRow.addView(
            downloadTop,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val progressBar = DeterminateProgressBarView(this, themeTokens, accent).apply {
            visibility = View.GONE
        }
        scrapeProgressBar = progressBar
        downloadRow.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
                topMargin = dp(8)
            },
        )
        scrapeRow = downloadRow
        addDividerIfNeeded(artCard)
        styleFocusableRow(downloadRow)
        downloadRow.setPadding(dp(10), dp(12), dp(10), dp(12))
        artCard.addView(
            downloadRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
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
            setOnClickListener { exportLauncher.launch(getString(R.string.file_settings_export)) }
        }
        exportRow.addView(rowLabel(getString(R.string.settings_export)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addActionRow(artCard, exportRow, showChevron = true)
        val importRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        }
        importRow.addView(rowLabel(getString(R.string.settings_import)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addActionRow(artCard, importRow, showChevron = true)
        val exportArtRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { exportArtLauncher.launch(getString(R.string.file_art_export)) }
        }
        exportArtRow.addView(rowLabel(getString(R.string.settings_export_art)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addActionRow(artCard, exportArtRow, showChevron = true)
        val importArtRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { importArtLauncher.launch(arrayOf("application/zip", "*/*")) }
        }
        importArtRow.addView(rowLabel(getString(R.string.settings_import_art)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addActionRow(artCard, importArtRow, showChevron = true)
        val packRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(accentOnCard)
        })
        addActionRow(artCard, packRow, showChevron = true)
        val bundledCount = listBundledPackAssets().size
        val examplePackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(accentOnCard)
        })
        addActionRow(artCard, examplePackRow, showChevron = true)
        val setupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        addActionRow(libraryCard, setupRow)
        val chromeDiscoverRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        addActionRow(libraryCard, chromeDiscoverRow)
        val visibleRoms = app.romEntries.count { it.visibleInUi }
        val romsBadge = resources.getQuantityString(R.plurals.count_roms_found, visibleRoms, visibleRoms)
        addSection(
            page = SettingsPage.LIBRARY,
            title = getString(R.string.settings_page_library),
            card = libraryCard,
            badgeText = romsBadge,
            badgeActive = visibleRoms > 0,
        )
        val artCredsReady = SettingsUiLogic.hasAnyArtCredentials(
            sgdbKey = s.sgdbApiKey,
            raUsername = s.raUsername,
            raApiKey = s.raApiKey,
        )
        addSection(
            page = SettingsPage.ART_DATA,
            title = getString(R.string.settings_page_art),
            card = artCard,
            badgeText = getString(if (artCredsReady) R.string.label_set else R.string.label_not_set),
            badgeActive = artCredsReady,
            onBadgeView = { artHeaderStatusPill = it },
        )

        val statsCard = sectionCard()
        val most = com.visorcraft.ghostgalleon.library.LibraryStats.mostPlayed(
            app.settings.playtimeMs, limit = 12,
        )
        val recent = com.visorcraft.ghostgalleon.library.LibraryStats.recentlyPlayed(
            app.settings.lastLaunchedMs, limit = 12,
        )
        val totalPlaytime = SettingsUiLogic.totalPlaytimeMs(app.settings.playtimeMs)
        val statsBadge = if (totalPlaytime > 0L) {
            resolveText(com.visorcraft.ghostgalleon.library.SessionMath.formatPlaytime(totalPlaytime))
        } else {
            null
        }
        if (!com.visorcraft.ghostgalleon.library.LibraryStats.hasAnySessions(
                app.settings.playtimeMs, app.settings.lastLaunchedMs,
            )
        ) {
            val emptyBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 14)
                setPadding(dp(18), dp(20), dp(18), dp(20))
                addView(
                    ImageView(this@SettingsActivity).apply {
                        setImageResource(R.drawable.ic_nav_stats)
                        imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_SUBTLE)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(dp(28), dp(28)).apply { bottomMargin = dp(8) },
                )
                addView(TextView(this@SettingsActivity).apply {
                    setText(R.string.stats_no_sessions)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    gravity = Gravity.CENTER
                })
            }
            statsCard.addView(
                emptyBox,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(6)
                    marginEnd = dp(6)
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                },
            )
        } else {
            statsCard.addView(
                sectionHeader(getString(R.string.stats_most_played)).apply {
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                },
            )
            val maxScore = most.firstOrNull()?.score ?: 0L
            most.forEachIndexed { index, row ->
                if (index > 0) addDivider(statsCard)
                statsCard.addView(
                    rankedPlaytimeRow(
                        rank = index + 1,
                        label = labelForStatsKey(row.key),
                        duration = resolveText(
                            com.visorcraft.ghostgalleon.library.SessionMath.formatPlaytime(row.score),
                        ),
                        barFraction = SettingsUiLogic.playtimeBarFraction(row.score, maxScore),
                    ),
                )
            }
            if (most.isEmpty()) {
                statsCard.addView(TextView(this).apply {
                    setText(R.string.browse_no_playtime)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(UiDimens.ALPHA_SUBTLE)
                    setPadding(dp(10), dp(4), dp(10), dp(8))
                })
            }
            addDivider(statsCard)
            statsCard.addView(
                sectionHeader(getString(R.string.stats_recently_played)).apply {
                    setPadding(dp(10), dp(14), dp(10), dp(6))
                },
            )
            recent.forEachIndexed { index, row ->
                if (index > 0) addDivider(statsCard)
                val whenLabel = com.visorcraft.ghostgalleon.library.SessionMath.formatLastPlayed(
                    row.score, System.currentTimeMillis(),
                )?.let(::resolveText) ?: getString(R.string.glyph_dash)
                statsCard.addView(statRow(labelForStatsKey(row.key), whenLabel))
            }
            if (recent.isEmpty()) {
                statsCard.addView(TextView(this).apply {
                    setText(R.string.stats_no_launches)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(UiDimens.ALPHA_SUBTLE)
                    setPadding(dp(10), dp(4), dp(10), dp(8))
                })
            }
        }
        addSection(
            page = SettingsPage.STATS,
            title = getString(R.string.settings_page_stats),
            card = statsCard,
            badgeText = statsBadge,
            badgeActive = totalPlaytime > 0L,
        )
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
        addDivider(systemCardExtra)
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

        fun meterTile(
            title: String,
            valueText: String,
            fraction: Float,
            hasReading: Boolean,
            lowIsWarn: Boolean = false,
        ): LinearLayout {
            val barColor = if (hasReading) {
                SettingsUiLogic.meterToneColor(fraction, accent, lowIsWarn)
            } else {
                UiDimens.ALPHA_SUBTLE
            }
            val valueColor = if (!hasReading) {
                UiDimens.ALPHA_MUTED
            } else if (barColor == accent) {
                accentOnSunken
            } else {
                barColor
            }
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 12)
                setPadding(dp(10), dp(8), dp(10), dp(9))
                val top = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@SettingsActivity).apply {
                            text = title
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                            setTextColor(UiDimens.ALPHA_MUTED)
                            setTypeface(typeface, Typeface.BOLD)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        },
                    )
                    addView(
                        TextView(this@SettingsActivity).apply {
                            text = valueText
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setTextColor(valueColor)
                            setTypeface(typeface, Typeface.BOLD)
                            useTabularNumerals()
                            maxLines = 1
                        },
                    )
                }
                addView(
                    top,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                val bar = DeterminateProgressBarView(this@SettingsActivity, themeTokens, barColor).apply {
                    setFraction(if (hasReading) fraction else 0f)
                }
                addView(
                    bar,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5)).apply {
                        topMargin = dp(6)
                    },
                )
            }
        }

        val ramFrac = SettingsUiLogic.freeToUsedFraction(readings.ramAvailBytes, readings.ramTotalBytes)
        val ramPct = (ramFrac * 100f).toInt()
        val intFrac = SettingsUiLogic.freeToUsedFraction(readings.internalFreeBytes, readings.internalTotalBytes)
        val intPct = (intFrac * 100f).toInt()
        val batPct = readings.batteryPercent
        val batFrac = SettingsUiLogic.batteryFraction(batPct)
        val secTotal = readings.secondaryTotalBytes
        val secFree = readings.secondaryFreeBytes
        val hasSec = secTotal != null && secFree != null && secTotal > 0L
        val secFrac = if (hasSec) SettingsUiLogic.freeToUsedFraction(secFree!!, secTotal!!) else 0f
        val secPct = (secFrac * 100f).toInt()

        fun meterPairRow(left: View, right: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                right,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                },
            )
        }

        val metersBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            addView(
                meterPairRow(
                    meterTile(
                        title = getString(R.string.system_ram),
                        valueText = if (readings.ramTotalBytes > 0L) {
                            getString(R.string.format_percent, ramPct)
                        } else {
                            getString(R.string.label_unavailable)
                        },
                        fraction = ramFrac,
                        hasReading = readings.ramTotalBytes > 0L,
                    ),
                    meterTile(
                        title = getString(R.string.system_internal_storage),
                        valueText = if (readings.internalTotalBytes > 0L) {
                            getString(R.string.format_percent, intPct)
                        } else {
                            getString(R.string.label_unavailable)
                        },
                        fraction = intFrac,
                        hasReading = readings.internalTotalBytes > 0L,
                    ),
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) },
            )
            addView(
                meterPairRow(
                    meterTile(
                        title = getString(R.string.system_battery),
                        valueText = if (batPct != null) {
                            getString(R.string.system_battery_percent, batPct)
                        } else {
                            getString(R.string.label_unavailable)
                        },
                        fraction = batFrac,
                        hasReading = batPct != null,
                        lowIsWarn = true,
                    ),
                    meterTile(
                        title = getString(R.string.system_microsd),
                        valueText = if (hasSec) {
                            getString(R.string.format_percent, secPct)
                        } else {
                            getString(R.string.system_not_present)
                        },
                        fraction = secFrac,
                        hasReading = hasSec,
                    ),
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        systemCard.addView(
            metersBlock,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        SystemInfoFormat.rows(readings).forEach { (label, value) ->
            addDivider(systemCard)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
            }
            row.addView(rowLabel(resolveText(label)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            row.addView(TextView(this).apply {
                text = resolveText(value)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(UiDimens.ALPHA_SECONDARY)
                useTabularNumerals()
                gravity = Gravity.END
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f,
            ))
            systemCard.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val refreshSys = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { refreshSettingsUi() }
        }
        refreshSys.addView(rowLabel(getString(R.string.settings_refresh_readings)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addActionRow(systemCard, refreshSys, heightDp = 56)

        // Dual-black recovery guidance callout + in-app companion restart (no App Info).
        addDivider(systemCard)
        val recoveryCallout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = TileBackgrounds.sunkenField(this@SettingsActivity, cornerRadiusDp = 12)
            setPaddingRelative(dp(10), dp(10), dp(12), dp(10))
            addView(
                View(this@SettingsActivity).apply {
                    background = TileBackgrounds.activeAccentBar(
                        this@SettingsActivity,
                        UiDimens.COLOR_DESTRUCTIVE,
                        cornerRadiusDp = 2,
                    )
                },
                LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(10)
                },
            )
            val texts = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@SettingsActivity).apply {
                    setText(R.string.settings_dual_recovery_title)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    setTextColor(UiDimens.ALPHA_PRIMARY)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(this@SettingsActivity).apply {
                    setText(R.string.settings_dual_recovery_body)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setLineSpacing(dpF(2), 1f)
                    setPadding(0, dp(4), 0, 0)
                })
            }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        systemCard.addView(
            recoveryCallout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
                topMargin = dp(10)
                bottomMargin = dp(6)
            },
        )
        val restartCompanion = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        addActionRow(systemCard, restartCompanion, heightDp = 56)
        toggle(systemCard, getString(R.string.settings_detect_black_companion), s.detectBlackCompanion) {
            app.updateSettings(app.settings.copy(detectBlackCompanion = it))
        }
        val systemBadge = getString(
            R.string.format_dot_pair,
            displayMode,
            deviceProfileName(effectiveProfile.id),
        )
        addSection(
            page = SettingsPage.SYSTEM,
            title = getString(R.string.settings_page_system),
            card = systemCard,
            badgeText = systemBadge,
            badgeActive = topo.mode == SurfaceMode.DUAL,
        )
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
                dp(SettingsUiLogic.navBladeWidthDp(screenWidthDp)),
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(18) })
        }
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        pageHost = host
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(18))
            clipToPadding = false
            setPadding(0, 0, 0, dp(12))
            addView(host, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        contentScroll = scroll
        body.addView(scroll, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
        ))
        shell.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        selectPage(currentPage)
        return shell
    }

    private fun switchPageBy(delta: Int) {
        val pages = SettingsPage.entries
        val nextIndex = SettingsUiLogic.stepPageIndex(pages.indexOf(currentPage), delta, pages.size)
        val nextPage = pages[nextIndex]
        if (nextPage == currentPage) return
        val wasInNav = navItems.values.any { it.container.hasFocus() }
        selectPage(nextPage)
        if (wasInNav) {
            navItems[nextPage]?.container?.requestFocus()
        } else {
            val target = pageBodies[nextPage]?.let(::firstFocusableIn)
                ?: navItems[nextPage]?.container
                ?: pageDropdownRow
            target?.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val target = captureTarget
        if (target == null) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
                switchPageBy(-1)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
                switchPageBy(1)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        val displaced = app.settings.keyMap[keyCode]
        val withoutTarget = app.settings.keyMap.filterValues { it != target }
        val newMap = withoutTarget + (keyCode to target)
        app.updateSettings(app.settings.copy(keyMap = newMap))
        capturePulse?.cancel()
        capturePulse = null
        captureLabel?.let { bindKeyChip(it, keyCode) }
        if (displaced != null && displaced != target) {
            keyChipsByAction[displaced]?.let { otherChip ->
                bindKeyChip(otherChip, SettingsUiLogic.boundKeyCodeFor(displaced, newMap))
            }
        }
        captureTarget = null
        captureLabel = null
        return true
    }
}
