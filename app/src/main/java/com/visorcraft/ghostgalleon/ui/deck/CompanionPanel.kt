package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import kotlin.math.abs
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.art.ArtCache
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.input.InputAssistPolicy
import com.visorcraft.ghostgalleon.input.InputAssistService
import com.visorcraft.ghostgalleon.input.InputOwner
import com.visorcraft.ghostgalleon.input.SecondSeatPolicy
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.library.RaCheevo
import com.visorcraft.ghostgalleon.library.RaTheaterSnap
import com.visorcraft.ghostgalleon.library.RetroAchievements
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
import com.visorcraft.ghostgalleon.display.DevicePosture
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.rom.CinemaFrame
import com.visorcraft.ghostgalleon.rom.CinemaPolicy
import com.visorcraft.ghostgalleon.rom.LaunchReason
import com.visorcraft.ghostgalleon.rom.CockpitPolicy
import com.visorcraft.ghostgalleon.rom.LaunchFace
import com.visorcraft.ghostgalleon.rom.HeroDetail
import com.visorcraft.ghostgalleon.rom.LensBlock
import com.visorcraft.ghostgalleon.rom.LensCatalog
import com.visorcraft.ghostgalleon.rom.LensSpec
import com.visorcraft.ghostgalleon.rom.TrackerCatalog
import com.visorcraft.ghostgalleon.rom.TrackerKind
import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RaCommandClient
import com.visorcraft.ghostgalleon.rom.RaStateSlots
import com.visorcraft.ghostgalleon.rom.RaStatus
import com.visorcraft.ghostgalleon.rom.SessionHandoff
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.SessionRingEntry
import com.visorcraft.ghostgalleon.rom.StagePlot
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.rom.SelectionStrip
import com.visorcraft.ghostgalleon.rom.SessionSurface
import com.visorcraft.ghostgalleon.settings.CompanionRole
import com.visorcraft.ghostgalleon.settings.CompanionRoleResolve
import com.visorcraft.ghostgalleon.settings.RomNames
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.hint
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.system.SystemInfoCollector
import com.visorcraft.ghostgalleon.system.SystemInfoFormat
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.DualPaintPolicy
import com.visorcraft.ghostgalleon.ui.HelperEmbedPolicy
import com.visorcraft.ghostgalleon.ui.HostSurface
import com.visorcraft.ghostgalleon.ui.HostSurfacePolicy
import com.visorcraft.ghostgalleon.ui.MainActivity
import com.visorcraft.ghostgalleon.ui.PlayHostPolicy
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.openSessionSwitcher
import com.visorcraft.ghostgalleon.ui.companionRoleName
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity
import java.io.File

object CompanionPanel {

    private const val TAG_HERO_ICON = "hero_icon"
    private const val TAG_HERO_APP_PEDESTAL = "hero_app_pedestal"
    private const val TAG_HERO_NAME = "hero_name"
    private const val TAG_HERO_SUB = "hero_sub"
    private const val TAG_HERO_META = "hero_meta"
    private const val TAG_HERO_METADATA = "hero_metadata"
    private const val TAG_HERO_PLAYER = "hero_player"
    private const val TAG_HERO_RA = "hero_ra"
    private const val TAG_HERO_RA_BAR = "hero_ra_bar"
    private const val TAG_HERO_DESC = "hero_desc"
    private const val TAG_HERO_SHOT = "hero_shot"
    private const val TAG_HERO_VIDEO = "hero_video"
    private const val TAG_HERO_VIDEO_HOST = "hero_video_host"
    private const val TAG_HERO_BANNER = "hero_banner"
    private const val TAG_HERO_LOGO = "hero_logo"
    private const val TAG_HERO_QUICK_BAR = "hero_quick_bar"
    private const val TAG_HERO_QUICK_FAV = "hero_quick_fav"
    private const val TAG_RESUME_CHIP = "resume_chip"
    /** Visible so PRIMARY TOP_STRIP paths can detect a hero panel in the tree. */
    const val TAG_PANEL_ROOT = "panel_root"
    /**
     * Marker on the compact single-display selection strip. Presence means
     * [updateSelection] must use the strip layout (not full dual hero).
     */
    const val TAG_TOP_STRIP = "top_strip"
    private const val TAG_ROLE_CHIPS = "role_chips"
    private const val TAG_STRIP_ART_HOST = "strip_art_host"
    private const val TAG_STRIP_DETAIL = "strip_detail"
    private const val TAG_STRIP_FAV = "strip_fav"
    private const val TAG_STRIP_RA_ROW = "strip_ra_row"
    private const val TAG_STRIP_RA_BAR = "strip_ra_bar"
    private const val TAG_STRIP_DIVIDER = "strip_divider"
    /** High-contrast warm gold for favorite star indicator on OLED dark surfaces. */
    private const val FAVORITE_STAR_COLOR = 0xFFFFD54F.toInt()
    private const val TAG_PERF_HUD_ROOT = "perf_hud_root"
    private const val TAG_PERF_VALUE_PREFIX = "perf_value_"
    private const val TAG_PERF_BAR_PREFIX = "perf_bar_"
    private const val TAG_PERF_DOT_PREFIX = "perf_dot_"
    private const val TAG_PLAY_HUD = "play_hud"
    private const val TAG_PLAY_HUD_CLOCK = "play_hud_clock"
    private const val TAG_PLAY_HUD_OWNER = "play_hud_owner"
    private const val TAG_PLAY_HUD_LENS = "play_hud_lens"
    private const val TAG_PLAY_HUD_TRACKER = "play_hud_tracker"
    private const val TAG_PLAY_HUD_CINEMA = "play_hud_cinema"
    private const val TAG_PLAY_HUD_THEATER = "play_hud_theater"
    private const val TAG_PLAY_HUD_THEATER_PROGRESS = "play_hud_theater_progress"
    private const val TAG_PLAY_HUD_THEATER_NEXT = "play_hud_theater_next"
    private const val TAG_PLAY_HUD_THEATER_TICKER = "play_hud_theater_ticker"
    private const val TAG_PLAY_HUD_THEATER_BADGE = "play_hud_theater_badge"
    private const val TAG_PLAY_HUD_THEATER_LETTER = "play_hud_theater_letter"
    private const val TAG_PLAY_HUD_ACTIONS = "play_hud_actions"
    private const val TAG_PLAY_HUD_SWITCHER = "play_hud_switcher"
    private const val TAG_PLAY_HUD_RA = "play_hud_ra"
    private const val TAG_PLAY_HUD_PAUSE = "play_hud_pause"
    private const val TAG_PLAY_HUD_SLOTS = "play_hud_slots"
    private const val TAG_PLAY_HUD_COCKPIT = "play_hud_cockpit"
    private const val TAG_PLAY_HUD_TRACKPAD = "play_hud_trackpad"
    private const val TAG_PLAY_HUD_SEAT = "play_hud_seat"
    private const val TAG_PLAY_HUD_SEAT_CHIP = "play_hud_seat_chip"
    private const val TAG_PLAY_HUD_SEAT_CLUSTER = "play_hud_seat_cluster"
    private const val TAG_PLAY_HUD_SEAT_HINT = "play_hud_seat_hint"
    private const val TAG_PLAY_HUD_HELPER = "play_hud_helper"
    private const val TAG_PLAY_HUD_HELPER_BODY = "play_hud_helper_body"
    private const val TAG_PLAY_HUD_HELPER_CHIP = "play_hud_helper_chip"
    private const val TAG_PLAY_HUD_TITLE = "play_hud_title"
    private const val TAG_PLAY_HUD_ART = "play_hud_art"
    private const val TAG_PLAY_HUD_POSTURE = "play_hud_posture"
    private const val HELPER_LOG = "GGHelper"
    private const val RA_PACKAGE = "com.retroarch.aarch64"
    private const val LENS_MAX_INTERVAL_MS = 200L
    /** Full-size overlay host on the panel FrameLayout (above HUD / hero). */
    const val TAG_SESSION_SWITCHER_HOST = "session_switcher_host"

    fun sessionSwitcherHost(root: View): ViewGroup? =
        root.findViewWithTag(TAG_SESSION_SWITCHER_HOST)

    /** In-place pad-owner hint on the KEEP play HUD (`play_hud_owner`). */
    fun bindOwnerHint(root: View, owner: InputOwner) {
        val tv = root.findViewWithTag<TextView>(TAG_PLAY_HUD_OWNER) ?: return
        val hint = owner.hint()
        if (hint == null) {
            if (tv.visibility != View.GONE) tv.visibility = View.GONE
            return
        }
        val text = tv.context.resolveText(hint)
        if (tv.visibility != View.VISIBLE) tv.visibility = View.VISIBLE
        if (tv.text?.toString() != text) tv.text = text
    }

    fun isSeatChromeTag(tag: Any?): Boolean =
        tag == TAG_PLAY_HUD_SEAT || tag == TAG_PLAY_HUD_SEAT_CHIP

    fun isHelperChromeTag(tag: Any?): Boolean =
        tag == TAG_PLAY_HUD_HELPER ||
            tag == TAG_PLAY_HUD_HELPER_BODY ||
            tag == TAG_PLAY_HUD_HELPER_CHIP

    /** True when [ev] lands on the Seat chip or the SEAT body (incl. cluster). */
    fun isSeatChromeHit(root: View?, ev: MotionEvent): Boolean {
        if (root == null) return false
        return containsRaw(root.findViewWithTag(TAG_PLAY_HUD_SEAT_CHIP), ev) ||
            containsRaw(root.findViewWithTag(TAG_PLAY_HUD_SEAT), ev)
    }

    /** True when [ev] lands on the Helper chip, body, or embed host. */
    fun isHelperChromeHit(root: View?, ev: MotionEvent): Boolean {
        if (root == null) return false
        return containsRaw(root.findViewWithTag(TAG_PLAY_HUD_HELPER_CHIP), ev) ||
            containsRaw(root.findViewWithTag(TAG_PLAY_HUD_HELPER_BODY), ev) ||
            containsRaw(root.findViewWithTag(TAG_PLAY_HUD_HELPER), ev)
    }

    /** Seat or Helper chrome: ACTION_DOWN must not claim HOST. */
    fun isNoClaimChromeHit(root: View?, ev: MotionEvent): Boolean =
        isSeatChromeHit(root, ev) || isHelperChromeHit(root, ev)

    private fun containsRaw(view: View?, ev: MotionEvent): Boolean {
        if (view == null || !view.isShown) return false
        val visible = Rect()
        if (!view.getLocalVisibleRect(visible)) return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        return x >= loc[0] + visible.left &&
            x < loc[0] + visible.right &&
            y >= loc[1] + visible.top &&
            y < loc[1] + visible.bottom
    }

    /**
     * Chip equivalent of [com.visorcraft.ghostgalleon.settings.Action.TOGGLE_SEAT].
     * Does not claim HOST.
     */
    fun toggleSeat(app: GhostGalleonApp) {
        setSeatActive(app, app.hostSurface != HostSurface.SEAT)
    }

    fun setSeatActive(app: GhostGalleonApp, on: Boolean) {
        if (on) {
            if (!seatChromeAllowed(app)) return
            app.hostSurface = HostSurface.SEAT
            app.releaseHost()
        } else if (app.hostSurface == HostSurface.SEAT) {
            app.hostSurface = HostSurface.HUD
        }
        app.liveDeckActivities().forEach { deck ->
            deck.applyPlayHostFocusLock()
            applySeatChrome(deck.window?.decorView, app)
        }
    }

    /** In-place FLAT chip. Hidden when posture is not FLAT or the edge was not SHOW. */
    fun applyPostureChip(root: View?, app: GhostGalleonApp) {
        val chip = root?.findViewWithTag<View>(TAG_PLAY_HUD_POSTURE) ?: return
        val show = app.postureYieldChipVisible &&
            app.devicePosture == DevicePosture.FLAT
        val vis = if (show) View.VISIBLE else View.GONE
        if (chip.visibility != vis) chip.visibility = vis
    }

    fun applySeatChrome(root: View?, app: GhostGalleonApp) {
        val allowed = seatChromeAllowed(app)
        if (!allowed && app.hostSurface == HostSurface.SEAT) {
            app.hostSurface = HostSurface.HUD
            app.liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
        }
        val seat = root?.findViewWithTag<View>(TAG_PLAY_HUD_SEAT) ?: return
        val onSeat = app.hostSurface == HostSurface.SEAT && allowed
        val vis = if (onSeat) View.VISIBLE else View.GONE
        if (seat.visibility != vis) seat.visibility = vis
        val canInject = onSeat &&
            app.inputAssistConnected &&
            InputAssistService.supportsDisplayGesture()
        val cluster = root.findViewWithTag<View>(TAG_PLAY_HUD_SEAT_CLUSTER)
        val hint = root.findViewWithTag<View>(TAG_PLAY_HUD_SEAT_HINT)
        val clusterVis = if (canInject) View.VISIBLE else View.GONE
        val hintVis = if (onSeat && !canInject) View.VISIBLE else View.GONE
        if (cluster != null && cluster.visibility != clusterVis) cluster.visibility = clusterVis
        if (hint != null && hint.visibility != hintVis) hint.visibility = hintVis
        val actions = root.findViewWithTag<View>(TAG_PLAY_HUD_ACTIONS)
        if (onSeat) {
            if (actions != null && actions.visibility != View.GONE) actions.visibility = View.GONE
            root.findViewWithTag<View>(TAG_PLAY_HUD_SLOTS)?.let {
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
            root.findViewWithTag<View>(TAG_PLAY_HUD_TRACKER)?.let {
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
            root.findViewWithTag<View>(TAG_PLAY_HUD_CINEMA)?.let {
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
            root.findViewWithTag<View>(TAG_PLAY_HUD_THEATER)?.let {
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
            root.findViewWithTag<View>(TAG_PLAY_HUD_LENS)?.let {
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
        } else if (actions != null && app.playHudExpanded) {
            if (actions.visibility != View.VISIBLE) actions.visibility = View.VISIBLE
        }
    }

    private fun seatChromeAllowed(app: GhostGalleonApp): Boolean {
        val surface = app.sessionSurface ?: return false
        val dual = app.displayConfig.mode == SurfaceMode.DUAL
        val launchId = surface.launchDisplayId
        val hostId = launchId?.let { lid ->
            app.displayConfig.allIds.firstOrNull { it != lid }
        }
        val playHost = PlayHostPolicy.playHostAllowed(
            dualMode = dual,
            policy = surface.policy,
            greedy = surface.greedy,
            hostDisplayId = hostId,
            launchDisplayId = launchId,
        )
        val sessionOwns = DualPaintPolicy.sessionOwnsCompanionDisplay(
            surface.policy,
            surface.greedy,
        )
        val cockpit = CockpitPolicy.cockpitAllowed(
            playHostAllowed = playHost,
            playerId = surface.playerId,
            cockpitEnabled = app.settings.winlatorCockpit,
        )
        if (!HostSurfacePolicy.seatAllowed(app.hostSurface, cockpit)) return false
        if (!app.settings.raSecondSeat) return false
        if (!SessionHandoff.isRaPlayer(surface.playerId, surface.packageName)) return false
        return dual && playHost && !sessionOwns
    }

    private enum class HelperChipKind { HIDDEN, ENABLED, UNAVAILABLE }

    fun releaseHelperEmbed(root: View?) {
        val host = root?.findViewWithTag<ViewGroup>(TAG_PLAY_HUD_HELPER) ?: return
        ActivityEmbed.release(host)
    }

    fun releaseHelperEmbeds(app: GhostGalleonApp) {
        app.liveDeckActivities().forEach { deck ->
            releaseHelperEmbed(deck.window?.decorView)
        }
    }

    fun applyHelperChrome(root: View?, app: GhostGalleonApp) {
        val kind = helperChipKind(app)
        val allowed = kind == HelperChipKind.ENABLED
        if (!allowed && app.hostSurface == HostSurface.HELPER) {
            releaseHelperEmbed(root)
            app.hostSurface = HostSurface.HUD
            app.liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
        }
        val body = root?.findViewWithTag<View>(TAG_PLAY_HUD_HELPER_BODY) ?: return
        val onHelper = app.hostSurface == HostSurface.HELPER &&
            helperChipKind(app) == HelperChipKind.ENABLED
        val vis = if (onHelper) View.VISIBLE else View.GONE
        if (body.visibility != vis) body.visibility = vis
        fun setTagged(tag: String, visibility: Int) {
            val v = root.findViewWithTag<View>(tag) ?: return
            if (v.visibility != visibility) v.visibility = visibility
        }
        if (onHelper) {
            setTagged(TAG_PLAY_HUD_ART, View.GONE)
            setTagged(TAG_PLAY_HUD_CLOCK, View.GONE)
            setTagged(TAG_PLAY_HUD_OWNER, View.GONE)
            setTagged(TAG_PLAY_HUD_LENS, View.GONE)
            setTagged(TAG_PLAY_HUD_TRACKER, View.GONE)
            setTagged(TAG_PLAY_HUD_CINEMA, View.GONE)
            setTagged(TAG_PLAY_HUD_THEATER, View.GONE)
            setTagged(TAG_PLAY_HUD_ACTIONS, View.GONE)
            setTagged(TAG_PLAY_HUD_SLOTS, View.GONE)
            setTagged(TAG_PLAY_HUD_SEAT, View.GONE)
        } else {
            setTagged(TAG_PLAY_HUD_ART, View.VISIBLE)
            setTagged(TAG_PLAY_HUD_CLOCK, View.VISIBLE)
            val actions = root.findViewWithTag<View>(TAG_PLAY_HUD_ACTIONS)
            if (actions != null && app.playHudExpanded && app.hostSurface != HostSurface.SEAT) {
                if (actions.visibility != View.VISIBLE) actions.visibility = View.VISIBLE
            }
        }
    }

    fun setHelperActive(app: GhostGalleonApp, on: Boolean) {
        if (on) {
            if (helperChipKind(app) != HelperChipKind.ENABLED) return
            app.hostSurface = HostSurface.HELPER
            app.releaseHost()
            var toasted = false
            app.liveDeckActivities().forEach { deck ->
                deck.applyPlayHostFocusLock()
                applyHelperChrome(deck.window?.decorView, app)
                applySeatChrome(deck.window?.decorView, app)
                if (!attachHelper(deck.window?.decorView, deck, app) && !toasted) {
                    toasted = true
                    Toast.makeText(deck, R.string.helper_embed_unavailable, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } else if (app.hostSurface == HostSurface.HELPER) {
            releaseHelperEmbeds(app)
            app.hostSurface = HostSurface.HUD
            app.liveDeckActivities().forEach { deck ->
                deck.applyPlayHostFocusLock()
                applyHelperChrome(deck.window?.decorView, app)
                applySeatChrome(deck.window?.decorView, app)
            }
        }
    }

    private fun resolvedHelperPackage(app: GhostGalleonApp): String? {
        val surface = app.sessionSurface ?: return null
        return HelperEmbedPolicy.resolvePackage(
            SlotKey.romId(surface.key),
            app.settings.romHelpers,
            app.settings.playHostHelperPackage,
        )
    }

    private fun helperChipKind(app: GhostGalleonApp): HelperChipKind {
        val pkg = resolvedHelperPackage(app)
        if (pkg.isNullOrBlank() || HelperEmbedPolicy.refused(pkg)) return HelperChipKind.HIDDEN
        val surface = app.sessionSurface ?: return HelperChipKind.HIDDEN
        val dual = app.displayConfig.mode == SurfaceMode.DUAL
        val launchId = surface.launchDisplayId
        val hostId = launchId?.let { lid ->
            app.displayConfig.allIds.firstOrNull { it != lid }
        }
        val playHost = PlayHostPolicy.playHostAllowed(
            dualMode = dual,
            policy = surface.policy,
            greedy = surface.greedy,
            hostDisplayId = hostId,
            launchDisplayId = launchId,
        )
        val sessionOwns = DualPaintPolicy.sessionOwnsCompanionDisplay(
            surface.policy,
            surface.greedy,
        )
        val cockpit = CockpitPolicy.cockpitAllowed(
            playHostAllowed = playHost,
            playerId = surface.playerId,
            cockpitEnabled = app.settings.winlatorCockpit,
        )
        if (!HostSurfacePolicy.helperAllowed(app.hostSurface, cockpit)) {
            return HelperChipKind.HIDDEN
        }
        if (!playHost || sessionOwns) return HelperChipKind.HIDDEN
        val embed = ActivityEmbed.available()
        if (HelperEmbedPolicy.mayEmbed(
                playHost,
                sessionOwns,
                pkg,
                surface.packageName,
                embed,
                cockpit,
            )
        ) {
            return HelperChipKind.ENABLED
        }
        if (!embed) return HelperChipKind.UNAVAILABLE
        return HelperChipKind.HIDDEN
    }

    /**
     * @return false when attach was attempted and failed (caller toasts once).
     */
    private fun attachHelper(root: View?, activity: Context, app: GhostGalleonApp): Boolean {
        if (app.hostSurface != HostSurface.HELPER) return true
        val host = root?.findViewWithTag<ViewGroup>(TAG_PLAY_HUD_HELPER) ?: return true
        val pkg = resolvedHelperPackage(app)
        val kind = helperChipKind(app)
        if (pkg == null || HelperEmbedPolicy.refused(pkg) || kind != HelperChipKind.ENABLED) {
            ActivityEmbed.release(host)
            app.hostSurface = HostSurface.HUD
            applyHelperChrome(root, app)
            return true
        }
        // v1: embed or nothing. mayLaunchOnHostDisplay is always false.
        if (HelperEmbedPolicy.mayLaunchOnHostDisplay()) {
            ActivityEmbed.release(host)
            app.hostSurface = HostSurface.HUD
            applyHelperChrome(root, app)
            return false
        }
        val ok = ActivityEmbed.attach(host, activity, pkg)
        if (!ok) {
            ActivityEmbed.release(host)
            app.hostSurface = HostSurface.HUD
            Log.i(HELPER_LOG, "attach failed pkg=$pkg")
            applyHelperChrome(root, app)
            return false
        }
        Log.i(HELPER_LOG, "attach pkg=$pkg")
        return true
    }

    private fun buildHelperBody(
        activity: AppCompatActivity,
        app: GhostGalleonApp,
        settings: Settings,
        dp: (Int) -> Int,
    ): View {
        val body = LinearLayout(activity).apply {
            tag = TAG_PLAY_HUD_HELPER_BODY
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            contentDescription = activity.getString(R.string.play_hud_helper)
        }
        body.addView(
            TextView(activity).apply {
                setText(R.string.play_hud_back_to_hud)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ThemePack.onFillTextColor(settings.accentColor))
                background = TileBackgrounds.accentPill(activity, settings.accentColor)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { setHelperActive(app, false) }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        body.addView(
            FrameLayout(activity).apply {
                tag = TAG_PLAY_HUD_HELPER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply { topMargin = dp(8) },
        )
        return body
    }

    private fun buildSeatBody(
        activity: AppCompatActivity,
        app: GhostGalleonApp,
        settings: Settings,
        dp: (Int) -> Int,
        compact: Boolean,
    ): View {
        val body = LinearLayout(activity).apply {
            tag = TAG_PLAY_HUD_SEAT
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            contentDescription = activity.getString(R.string.play_hud_seat)
        }
        body.addView(
            TextView(activity).apply {
                setText(R.string.play_hud_back_to_hud)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ThemePack.onFillTextColor(settings.accentColor))
                background = TileBackgrounds.accentPill(activity, settings.accentColor)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { setSeatActive(app, false) }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        body.addView(
            TextView(activity).apply {
                tag = TAG_PLAY_HUD_SEAT_HINT
                setText(R.string.seat_need_assist)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
                setTextColor(0xBBFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(8))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val cluster = GridLayout(activity).apply {
            tag = TAG_PLAY_HUD_SEAT_CLUSTER
            columnCount = 5
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        val anchors = SecondSeatPolicy.anchorsOrDefault(settings.raSeatAnchors)
        for (anchor in anchors) {
            val cell = TextView(activity).apply {
                text = anchor.id.uppercase()
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 15f)
                setTextColor(Color.WHITE)
                background = TileBackgrounds.chip(activity)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                minimumHeight = dp(if (compact) 40 else 48)
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> app.injectSeat(anchor.id, true)
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> app.injectSeat(anchor.id, false)
                    }
                    true
                }
            }
            cluster.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                },
            )
        }
        body.addView(
            cluster,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return body
    }

    fun attachSessionSwitcher(
        root: View,
        entries: List<SessionRingEntry>,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
        onClose: () -> Unit,
    ) {
        val host = sessionSwitcherHost(root) ?: return
        SessionSwitcherView.attach(host, entries, onPick, onRemove, onClose)
    }

    fun detachSessionSwitcher(root: View) {
        sessionSwitcherHost(root)?.let { SessionSwitcherView.detach(it) }
    }

    /**
     * Slot key companion Resume launches through [launchSlotKey].
     * KEEP prefers [sessionSurfaceKey] (then [selectedKey]) so a navigated
     * hero does not steal Resume. Otherwise the browse continue key.
     * No new intent extras.
     */
    internal fun resumeLaunchKey(
        continueKey: String?,
        sessionSurfaceKey: String?,
        selectedKey: String?,
        sessionPolicy: SessionPolicy?,
    ): String? =
        if (sessionPolicy == SessionPolicy.KEEP_COMPANION) {
            sessionSurfaceKey ?: selectedKey
        } else {
            continueKey
        }

    /**
     * Skip hero media rebind when this ROM is already painted and the
     * art cache has not received a new scrape / SET_ART write.
     */
    internal fun sameHeroBinding(
        boundId: Any?,
        boundGeneration: Any?,
        romId: String,
        generation: Int,
    ): Boolean = boundId == romId && boundGeneration == generation

    /** Pure host-tested label resource for the companion quick-action favorite chip. */
    internal fun favoriteChipLabelRes(selectedKey: String?, favorites: Set<String>): Int =
        if (selectedKey != null && selectedKey in favorites) {
            R.string.action_unfavorite
        } else {
            R.string.action_favorite
        }

    /** True when the live [selectedKey] is currently in [favorites]. */
    internal fun isSelectedFavorite(selectedKey: String?, favorites: Set<String>): Boolean =
        selectedKey != null && selectedKey in favorites

    private fun styleFavoriteQuickChip(
        chip: TextView,
        isFavorite: Boolean,
        accentColor: Int,
    ) {
        val context = chip.context
        val label = context.getString(
            if (isFavorite) R.string.action_unfavorite else R.string.action_favorite,
        )
        val star = context.getString(R.string.glyph_favorite)
        val display = CompanionChromeErgonomics.favoriteChipText(star, label, isFavorite)
        if (chip.text?.toString() == display &&
            chip.isSelected == isFavorite &&
            chip.background != null
        ) {
            return
        }
        chip.isSelected = isFavorite
        chip.contentDescription = label
        val idleSurface = TileBackgrounds.chipIdleColor(context)
        val fillSurface = if (isFavorite) {
            CompanionChromeErgonomics.activeChipFill(idleSurface, accentColor)
        } else {
            idleSurface
        }
        val textAccent = ThemePack.accentTextColor(accentColor, fillSurface)
        chip.setTextColor(if (isFavorite) textAccent else Color.WHITE)
        chip.setTypeface(chip.typeface, if (isFavorite) Typeface.BOLD else Typeface.NORMAL)
        if (isFavorite && star.isNotBlank() && display.startsWith(star)) {
            val styled = SpannableString(display).apply {
                setSpan(
                    ForegroundColorSpan(FAVORITE_STAR_COLOR),
                    0,
                    star.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            chip.text = styled
        } else {
            chip.text = display
        }
        val base = GradientDrawable().apply {
            setColor(fillSurface)
            cornerRadius = context.dpF(17)
            if (isFavorite) {
                setStroke(context.strokePx(1), (textAccent and 0x00FFFFFF) or (0x88 shl 24))
            } else {
                setStroke(context.strokePx(1), TileBackgrounds.hairlineDivider(context))
            }
        }
        chip.background = buildInteractivePillBackground(
            context = context,
            base = base,
            accent = accentColor,
            cornerRadiusDp = 17,
        )
        val insetV = (CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP * context.resources.displayMetrics.density).toInt()
        val padH = (12 * context.resources.displayMetrics.density).toInt()
        chip.setPadding(padH, insetV, padH, insetV)
    }

    // Layered depth background: a vertical gradient lifting to #FF202028 in
    // the center band, plus a huge soft radial glow behind the hero icon
    // tinted with the glow color at ~18% alpha.
    private var cachedPanelGlow = 0
    private var cachedPanelLift = 0
    private var cachedPanelW = 0
    private var cachedPanelH = 0
    private var cachedPanelBg: Drawable? = null

    private fun panelBackground(context: Context, glowColor: Int): Drawable {
        val lift = com.visorcraft.ghostgalleon.settings.ThemePack.resolve(
            (context.applicationContext as? GhostGalleonApp)?.settings
                ?: com.visorcraft.ghostgalleon.settings.Settings.DEFAULT,
        ).panelLift
        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val hit = cachedPanelBg
        if (hit != null &&
            cachedPanelGlow == glowColor &&
            cachedPanelLift == lift &&
            cachedPanelW == w &&
            cachedPanelH == h
        ) {
            return hit
        }
        val (focalColor, ambientColor) = CompanionHeroStyle.panelGlowColors(glowColor)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                0xFF000000.toInt(),
                lift,
                0xFF000000.toInt(),
            ),
        )
        val ambientGlow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(ambientColor, Color.TRANSPARENT)
            setGradientCenter(0.5f, 0.45f)
            gradientRadius = maxOf(w, h) * 0.8f
        }
        val focalHalo = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(focalColor, Color.TRANSPARENT)
            setGradientCenter(0.5f, 0.34f)
            gradientRadius = minOf(w, h) * 0.46f
        }
        return LayerDrawable(arrayOf(gradient, ambientGlow, focalHalo)).also {
            cachedPanelGlow = glowColor
            cachedPanelLift = lift
            cachedPanelW = w
            cachedPanelH = h
            cachedPanelBg = it
        }
    }

    private val heroSettleInterpolator = DecelerateInterpolator()

    /**
     * Frame rectangular hero cover art with rounded elevation shadow (platform-tinted
     * on API 28+) and a crisp 1dp specular hairline foreground rim. Updates strictly
     * in-place without interfering with [ArtTile] background backing.
     */
    private fun applyHeroArtFrame(
        target: View,
        accentColor: Int,
        elevationDp: Float,
        cornerRadiusDp: Int = CompanionHeroStyle.ART_CORNER_RADIUS_DP,
    ) {
        val context = target.context
        val radiusPx = context.dpF(cornerRadiusDp)
        target.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        target.elevation = context.dpF(elevationDp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val shadow = CompanionHeroStyle.artShadowColor(accentColor)
            target.outlineSpotShadowColor = shadow
            target.outlineAmbientShadowColor = shadow
        }
        val rimColor = CompanionHeroStyle.artRimColor(accentColor)
        val rim = (target.foreground as? GradientDrawable)?.mutate() as? GradientDrawable
            ?: GradientDrawable().also { target.foreground = it }
        rim.setColor(Color.TRANSPARENT)
        rim.cornerRadius = radiusPx
        rim.setStroke(context.strokePx(1), rimColor)
    }

    private fun applyAppPedestalFrame(
        pedestal: FrameLayout,
        accentColor: Int,
        artSizePx: Int,
        elevationDp: Float,
    ) {
        val context = pedestal.context
        val radiusPx = context.dpF(CompanionHeroStyle.ART_CORNER_RADIUS_DP)
        val base = GradientDrawable().apply {
            setColor(0x14FFFFFF)
            cornerRadius = radiusPx
        }
        val glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            cornerRadius = radiusPx
            colors = intArrayOf(
                CompanionHeroStyle.appPedestalGlowColor(accentColor),
                Color.TRANSPARENT,
            )
            setGradientCenter(0.5f, 0.5f)
            gradientRadius = (artSizePx * 0.56f).coerceAtLeast(1f)
        }
        pedestal.background = LayerDrawable(arrayOf(base, glow))
        applyHeroArtFrame(
            target = pedestal,
            accentColor = accentColor,
            elevationDp = elevationDp,
            cornerRadiusDp = CompanionHeroStyle.ART_CORNER_RADIUS_DP,
        )
    }

    private fun animateHeroTextSettle(view: View, density: Float) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = CompanionHeroStyle.HERO_START_ALPHA
        view.translationY = CompanionHeroStyle.HERO_SETTLE_TRANSLATION_DP * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(CompanionHeroStyle.HERO_CROSSFADE_MS)
            .setInterpolator(heroSettleInterpolator)
            .start()
    }

    private fun animateHeroArtSettle(view: View) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 1f
            return
        }
        view.alpha = CompanionHeroStyle.ART_START_ALPHA
        view.animate()
            .alpha(1f)
            .setDuration(CompanionHeroStyle.HERO_CROSSFADE_MS)
            .setInterpolator(heroSettleInterpolator)
            .start()
    }

    // Cheap Palette stand-in: draw the icon at 16x16 and average the opaque
    // pixels. Null when the icon cannot be rasterized.
    private fun dominantColor(drawable: Drawable): Int? = runCatching {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, 16, 16)
        drawable.draw(canvas)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val p = bmp.getPixel(x, y)
                if (p ushr 24 < 0x40) continue
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        if (n == 0L) return null
        Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }.getOrNull()

    // Glow tint cache: PM icon + 16×16 average is too heavy for every NAV.
    private val glowColorByPackage = HashMap<String, Int>(32)

    private var cachedIconLoader: AppIconLoader? = null
    private var cachedIconPm: android.content.pm.PackageManager? = null

    private fun iconLoader(context: Context): AppIconLoader {
        val pm = context.packageManager
        val hit = cachedIconLoader
        if (hit != null && cachedIconPm === pm) return hit
        return AppIconLoader(pm).also {
            cachedIconLoader = it
            cachedIconPm = pm
        }
    }

    // Glow tint: dominant color of the selected app's icon when available,
    // otherwise the accent color.
    private fun glowColor(context: Context, packageName: String?, settings: Settings): Int {
        if (packageName != null) {
            glowColorByPackage[packageName]?.let { return it }
            runCatching { context.packageManager.getApplicationIcon(packageName) }
                .getOrNull()
                ?.let { dominantColor(it) }
                ?.let {
                    glowColorByPackage[packageName] = it
                    return it
                }
        }
        return settings.accentColor
    }

    /**
     * Pure structural-chrome gate for the **CHROME** path only (not SELECTION).
     *
     * Resume chip is often omitted even when [BrowseChrome.resumeChip] is on
     * (no continue key, hideResumeChip, open session). That content omission
     * must NOT force a dual full rebuild on every NAV — only a flag toggle
     * that requires creating a missing view fails in-place rebind.
     *
     * Host-tested; no Android View types.
     */
    fun canApplyChromeInPlace(
        hasStatusPill: Boolean,
        hasResumeChip: Boolean,
        previous: com.visorcraft.ghostgalleon.settings.BrowseChrome,
        next: com.visorcraft.ghostgalleon.settings.BrowseChrome,
    ): Boolean {
        // Status pill is always built when the flag is on at paint time.
        if (next.deckStatusPill != hasStatusPill) return false
        // Resume just turned ON and no chip exists → need full rebuild to create it.
        if (next.resumeChip && !previous.resumeChip && !hasResumeChip) return false
        // Resume already on, chip absent → content omit (no continue target); OK.
        // Resume off → GONE existing chip in place; OK.
        return true
    }

    // Selection-only update on an already-built panel: swap the hero icon
    // and name in place. Returns false when the current hero structure does
    // not match the new selection (wordmark shown but an entry selected, or
    // an app hero showing while a ROM is now selected — the hero views
    // differ) so the caller falls back to a full rebuild.
    //
    // Does **not** gate on resumeChip flag vs chip presence — that thrash path
    // is CHROME-only via [canApplyChromeInPlace] + Settings chromeOnly split.
    fun updateSelection(
        view: View,
        context: Context,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): Boolean {
        // KEEP play HUD / PERF are the common companion KEEP surfaces —
        // check them before TOP_STRIP so NAV does not walk the tree twice.
        if (view.findViewWithTag<View>(TAG_PLAY_HUD) != null) {
            val ctxApp = context.applicationContext as? GhostGalleonApp
            if (ctxApp != null) {
                com.visorcraft.ghostgalleon.ui.tickPlayHudClock(view, ctxApp, context)
            }
            return true
        }
        if (view.findViewWithTag<View>(TAG_PERF_HUD_ROOT) != null) return true
        // Compact TOP_STRIP path: always selection-context, never full dual roles.
        if (view.findViewWithTag<View>(TAG_TOP_STRIP) != null) {
            return updateTopStrip(view, context, state, library, roms, settings)
        }
        // Resume chip: only touch an existing view (GONE/show). Never require
        // the chip to exist when resumeChip is on — content may omit it.
        view.findViewWithTag<View>(TAG_RESUME_CHIP)?.let { chip ->
            val show = settings.browseChrome.resumeChip && !settings.hideResumeChip
            chip.visibility = if (show) View.VISIBLE else View.GONE
        }
        val app = context.applicationContext as? GhostGalleonApp
        val rom = selectedRom(state.selectedKey, roms, app)
        if (rom != null) {
            // In-place only when the hero is already in ROM shape (banner
            // frame + tile TextView + name + platform subtitle).
            val tile = view.findViewWithTag<View>(TAG_HERO_ICON) as? TextView
                ?: return false
            val name = view.findViewWithTag<TextView>(TAG_HERO_NAME) ?: return false
            val sub = view.findViewWithTag<TextView>(TAG_HERO_SUB) ?: return false
            val banner = view.findViewWithTag<View>(TAG_HERO_BANNER) as? FrameLayout
                ?: return false
            val tileFrame = tile.parent as? FrameLayout ?: return false
            val cache = (context.applicationContext as GhostGalleonApp).artCache
            val artGen = cache.artGeneration(rom.id)
            val alreadyBound = sameHeroBinding(
                name.getTag(R.id.hero_bound_id),
                name.getTag(R.id.hero_art_gen),
                rom.id,
                artGen,
            )
            val dens = context.resources.displayMetrics.density
            val hDp = context.resources.displayMetrics.heightPixels / dens
            val heroSpec = CompanionHeroMetrics.forPanel(hDp)
            val platformAccent = PlatformLook.accentColor(rom.platformId)
            val previousName = name.text
            if (!alreadyBound) {
                PlatformTile.restyle(tile, context, rom.platformId)
            }
            // Rebind the art chain only when the ROM or cache generation
            // changed (NAV same-title skip; scrape / SET_ART still refresh).
            if (!alreadyBound) {
                val artPx = (heroSpec.artSizeDp * dens).toInt()
                val artElevation = CompanionHeroStyle.artElevationDp(heroSpec.artSizeDp)
                applyHeroArtFrame(tileFrame, platformAccent, artElevation)
                applyHeroArtFrame(banner, platformAccent, artElevation)
                bindRomHeroArt(
                    banner,
                    tileFrame,
                    cache,
                    rom,
                    settings.artOverrides,
                    artPx,
                )
                animateHeroArtSettle(tileFrame)
                bindHeroLogo(
                    view.findViewWithTag(TAG_HERO_LOGO),
                    cache,
                    rom,
                )
            }
            name.setTag(R.id.hero_bound_id, rom.id)
            name.setTag(R.id.hero_art_gen, artGen)
            val nextName = com.visorcraft.ghostgalleon.settings.RomNames.display(
                rom, settings.romNames,
            )
            val animateText = CompanionHeroStyle.shouldAnimateSelection(
                alreadyBound = alreadyBound,
                previousText = previousName,
                nextText = nextName,
            )
            name.text = nextName
            val platform = Platforms.byId(rom.platformId)
            val installed = { pkg: String -> context.packageManager.isInstalled(pkg) }
            val preferred = RomProfiles.preferredPlayerId(
                rom.id,
                settings.romProfiles,
                settings.defaultPlayers[rom.platformId],
            )
            // One compact subline (platform · play · player) — not three stacked rows.
            sub.text = context.resolveText(HeroDetail.compactSubline(
                HeroDetail.platformLine(platform, rom.platformId),
                romMetaLine(settings, SlotKey.rom(rom.id)),
                HeroDetail.playerShortName(platform, preferred, installed),
            ))
            if (animateText) {
                animateHeroTextSettle(name, dens)
                animateHeroTextSettle(sub, dens)
            }
            view.findViewWithTag<TextView>(TAG_HERO_META)?.visibility = View.GONE
            view.findViewWithTag<TextView>(TAG_HERO_PLAYER)?.visibility = View.GONE
            bindMetadataLine(view.findViewWithTag(TAG_HERO_METADATA), rom)
            val appCtx = app ?: (context.applicationContext as GhostGalleonApp)
            val raProgress = appCtx.raProgressFor(rom.id)
            val hasRaCreds = !settings.raApiKey.isNullOrBlank()
            bindRaLine(
                tv = view.findViewWithTag(TAG_HERO_RA),
                bar = view.findViewWithTag(TAG_HERO_RA_BAR),
                progress = raProgress,
                hasCreds = hasRaCreds,
                accentColor = settings.accentColor,
            )
            val desc = HeroDetail.descriptionText(rom.description)
            view.findViewWithTag<TextView>(TAG_HERO_DESC)?.let { tv ->
                if (desc != null) {
                    tv.visibility = View.VISIBLE
                    tv.text = desc
                } else {
                    tv.visibility = View.GONE
                    tv.text = ""
                }
            }
            if (!alreadyBound) {
                bindScreenshot(
                    view.findViewWithTag(TAG_HERO_SHOT),
                    appCtx.artCache,
                    rom,
                )
                bindHeroVideo(view.findViewWithTag(TAG_HERO_VIDEO_HOST), rom)
            }
            if (!alreadyBound) {
                view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background =
                    panelBackground(context, platformAccent)
                appCtx.requestRaProgress(rom.id, rom.name)
            }
            val romKey = SlotKey.rom(rom.id)
            view.findViewWithTag<TextView>(TAG_HERO_QUICK_FAV)?.let { favChip ->
                styleFavoriteQuickChip(
                    chip = favChip,
                    isFavorite = isSelectedFavorite(romKey, settings.favorites),
                    accentColor = settings.accentColor,
                )
            }
            view.findViewWithTag<View>(TAG_HERO_QUICK_BAR)?.let { bar ->
                if (bar.visibility != View.VISIBLE) bar.visibility = View.VISIBLE
            }
            return true
        }
        val entry = state.selectedKey?.let { library.byPackage(settings)[it] }
        // Find as View first: the ROM hero tags a TextView tile with
        // TAG_HERO_ICON, and findViewWithTag<ImageView> would throw a
        // ClassCastException on it instead of returning null.
        val heroIcon = view.findViewWithTag<View>(TAG_HERO_ICON)
        val name = view.findViewWithTag<TextView>(TAG_HERO_NAME)
        if (entry == null) {
            // Only already showing the wordmark counts as up to date.
            return heroIcon == null && name == null
        }
        // A ROM-shaped hero showing while an app is selected is a structure
        // mismatch -> full rebuild.
        val icon = heroIcon as? ImageView ?: return false
        if (name == null) return false
        val dens = context.resources.displayMetrics.density
        val hDp = context.resources.displayMetrics.heightPixels / dens
        val appHeroSpec = CompanionHeroMetrics.forPanel(hDp)
        val targetPx = (appHeroSpec.artSizeDp * dens).toInt()
        val appCtx = app ?: (context.applicationContext as GhostGalleonApp)
        val appAlreadyBound = name.getTag(R.id.hero_bound_id) == entry.packageName
        val previousAppName = name.text
        val appGlow = glowColor(context, entry.packageName, settings)
        if (!appAlreadyBound) {
            CustomIcon.bind(
                icon, iconLoader(context),
                appCtx.artCache,
                settings, entry.packageName, targetPx)
            animateHeroArtSettle(icon)
            view.findViewWithTag<FrameLayout>(TAG_HERO_APP_PEDESTAL)?.let { pedestal ->
                applyAppPedestalFrame(
                    pedestal = pedestal,
                    accentColor = appGlow,
                    artSizePx = targetPx,
                    elevationDp = CompanionHeroStyle.artElevationDp(appHeroSpec.artSizeDp),
                )
            }
        }
        name.setTag(R.id.hero_bound_id, entry.packageName)
        name.text = entry.label
        val appSub = view.findViewWithTag<TextView>(TAG_HERO_SUB)
        if (appSub != null) {
            appSub.text = context.resolveText(
                HeroDetail.compactSubline(
                    context.getString(R.string.label_app),
                    romMetaLine(settings, entry.packageName),
                    null,
                ),
            )
        }
        if (CompanionHeroStyle.shouldAnimateSelection(appAlreadyBound, previousAppName, entry.label)) {
            animateHeroTextSettle(name, dens)
            if (appSub != null) animateHeroTextSettle(appSub, dens)
        }
        // Cached glow (no PM + 16×16 average every NAV).
        view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background = panelBackground(
            context,
            appGlow,
        )
        return true
    }

    // The ROM referenced by a "rom:<id>" selection key, if still indexed.
    private fun selectedRom(
        key: String?,
        roms: List<RomEntry>,
        app: GhostGalleonApp? = null,
    ): RomEntry? {
        val id = SlotKey.romId(key) ?: return null
        // Process-wide O(1) map first; linear scan only if map is cold/stale.
        app?.romById?.get(id)?.let { return it }
        return roms.firstOrNull { it.id == id }
    }

    private fun resumeLabel(
        key: String,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        app: GhostGalleonApp? = null,
    ): String = when {
        SlotKey.isRom(key) ->
            selectedRom(key, roms, app)?.let { RomNames.display(it, settings.romNames) } ?: key
        else -> library.byPackage(settings)[key]?.label ?: key
    }

    /**
     * Tap = launch. Horizontal drag ≥ [swipeSlopPx] (either direction) =
     * dismiss Resume chip until next launch (no lastLaunched thrash).
     * Uses raw dx, not fling velocity — small OLED chips rarely hit 400px/s.
     */
    private fun bindResumeChipGestures(
        view: TextView,
        cont: String,
        activity: AppCompatActivity,
        state: DeckState,
        roms: List<RomEntry>,
        app: GhostGalleonApp,
        swipeSlopPx: Float,
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var tracking = false
        var dismissing = false
        val touchSlopPx = (8 * view.resources.displayMetrics.density).coerceAtLeast(4f)
        fun settleRest(scale: Float) {
            view.animate().cancel()
            if (!ValueAnimator.areAnimatorsEnabled()) {
                view.translationX = 0f
                view.alpha = 1f
                view.scaleX = scale
                view.scaleY = scale
                return
            }
            view.animate()
                .translationX(0f)
                .alpha(1f)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(CompanionChromeErgonomics.RESUME_SWIPE_ANIM_MS)
                .setInterpolator(heroSettleInterpolator)
                .start()
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (dismissing || tracking) return@setOnFocusChangeListener
            val scale = CompanionChromeErgonomics.targetScale(pressed = false, focused = hasFocus)
            view.animate().cancel()
            if (!ValueAnimator.areAnimatorsEnabled()) {
                view.scaleX = scale
                view.scaleY = scale
            } else {
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(CompanionChromeErgonomics.SCALE_ANIM_MS)
                    .setInterpolator(heroSettleInterpolator)
                    .start()
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.animate().cancel()
            }
        })
        view.setOnClickListener {
            val key = resumeLaunchKey(
                continueKey = cont,
                sessionSurfaceKey = app.sessionSurface?.key,
                selectedKey = state.selectedKey,
                sessionPolicy = app.sessionSurface?.policy,
            ) ?: return@setOnClickListener
            val idx = app.settings.gridSlots.indexOf(key)
            if (idx >= 0) state.selectSlot(idx, key) else state.select(key)
            launchSlotKey(activity, state, roms, key, reason = LaunchReason.CONTINUE)
        }
        view.setOnTouchListener { _, event ->
            if (dismissing) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    tracking = true
                    view.animate().cancel()
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        view.animate()
                            .scaleX(CompanionChromeErgonomics.CHIP_PRESS_SCALE)
                            .scaleY(CompanionChromeErgonomics.CHIP_PRESS_SCALE)
                            .setDuration(CompanionChromeErgonomics.SCALE_ANIM_MS)
                            .start()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return@setOnTouchListener false
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val fb = CompanionChromeErgonomics.resumeSwipeFeedback(
                        dx = dx,
                        dy = dy,
                        dismissSlopPx = swipeSlopPx,
                        touchSlopPx = touchSlopPx,
                    )
                    view.translationX = fb.translationX
                    view.alpha = fb.alpha
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!tracking) return@setOnTouchListener false
                    tracking = false
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val fb = CompanionChromeErgonomics.resumeSwipeFeedback(
                        dx = dx,
                        dy = dy,
                        dismissSlopPx = swipeSlopPx,
                        touchSlopPx = touchSlopPx,
                    )
                    if (event.actionMasked == MotionEvent.ACTION_UP && fb.shouldDismiss) {
                        dismissing = true
                        val exitX = if (dx >= 0f) swipeSlopPx * 2.5f else -swipeSlopPx * 2.5f
                        if (ValueAnimator.areAnimatorsEnabled()) {
                            view.animate()
                                .translationX(exitX)
                                .alpha(0f)
                                .setDuration(CompanionChromeErgonomics.RESUME_SWIPE_ANIM_MS)
                                .setInterpolator(heroSettleInterpolator)
                                .withEndAction { app.dismissResumeChip() }
                                .start()
                        } else {
                            app.dismissResumeChip()
                        }
                        true
                    } else {
                        val restScale = CompanionChromeErgonomics.targetScale(
                            pressed = false,
                            focused = view.isFocused,
                        )
                        settleRest(restScale)
                        if (abs(dx) < swipeSlopPx * 0.5f && abs(dy) < swipeSlopPx * 0.5f &&
                            event.actionMasked == MotionEvent.ACTION_UP
                        ) {
                            view.performClick()
                        }
                        true
                    }
                }
                else -> false
            }
        }
    }

    // Loads res/raw/<name> (animated WebP/GIF) as a started-or-startable
    // AnimatedImageDrawable; null when the asset is absent, undecodable, or
    // the platform predates ImageDecoder (API 28). Looked up by name so a
    // drop-in asset never needs a code change.
    private fun loadAnimated(context: Context, rawName: String): Drawable? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return null
        val id = context.resources.getIdentifier(rawName, "raw", context.packageName)
        if (id == 0) return null
        return runCatching {
            android.graphics.ImageDecoder.decodeDrawable(
                android.graphics.ImageDecoder.createSource(context.resources, id),
            )
        }.getOrNull()?.takeIf {
            it is android.graphics.drawable.AnimatedImageDrawable
        }
    }

    private fun ImageView.showAnimated(drawable: Drawable) {
        setImageDrawable(drawable)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
        }
    }

    // Wide banner frame for HERO art: GONE until wide art arrives, ~40% of
    // the display height, rounded corners via an outline clip (CENTER_CROP
    // keeps the corners true, unlike a scaled RoundedBitmapDrawable).
    private fun bannerFrame(context: Context): FrameLayout {
        val radiusPx = 24 * context.resources.displayMetrics.density
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
        }
        return FrameLayout(context).apply {
            tag = TAG_HERO_BANNER
            visibility = View.GONE
            addView(image, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    /** Platform subtitle companion line: last played + playtime when known. */
    internal fun romMetaLine(settings: Settings, slotKey: String): UiText =
        SessionMath.cardMetaLine(
            settings.lastLaunchedMs[slotKey],
            settings.playtimeMs[slotKey] ?: 0L,
            System.currentTimeMillis(),
            playedPrefix = true,
        )

    private fun bindMetadataLine(tv: TextView?, rom: RomEntry) {
        if (tv == null) return
        val line = HeroDetail.metadataLine(rom)
        if (line != null) {
            tv.visibility = View.VISIBLE
            tv.text = line
        } else {
            tv.visibility = View.GONE
            tv.text = ""
        }
    }

    private fun bindRaLine(
        tv: TextView?,
        bar: MeterBarView?,
        progress: com.visorcraft.ghostgalleon.library.RaProgress?,
        hasCreds: Boolean,
        accentColor: Int,
    ) {
        if (tv != null) {
            val line = RetroAchievements.heroLine(progress, hasCreds)
            if (line != null) {
                tv.visibility = View.VISIBLE
                tv.text = tv.context.resolveText(line)
                val meter = CompanionHeroStyle.raMeterState(
                    progress = progress,
                    hasCredentials = hasCreds,
                    accentColor = accentColor,
                    surfaceColor = TileBackgrounds.surfaceColor(tv.context),
                )
                tv.setTextColor(meter.fillColor)
            } else {
                tv.visibility = View.GONE
                tv.text = ""
            }
        }
        bindRaMeter(bar, progress, hasCreds, accentColor)
    }

    private fun bindRaMeter(
        bar: MeterBarView?,
        progress: com.visorcraft.ghostgalleon.library.RaProgress?,
        hasCreds: Boolean,
        accentColor: Int,
    ) {
        if (bar == null) return
        val sunken = TileBackgrounds.surfaceSunken(bar.context)
        val meter = CompanionHeroStyle.raMeterState(
            progress = progress,
            hasCredentials = hasCreds,
            accentColor = accentColor,
            surfaceColor = sunken,
        )
        if (meter.visible) {
            bar.setProgress(
                fraction = meter.fraction,
                fillColor = meter.fillColor,
                trackColor = sunken,
                borderColor = TileBackgrounds.hairlineDivider(bar.context),
            )
            if (bar.visibility != View.VISIBLE) bar.visibility = View.VISIBLE
        } else if (bar.visibility != View.GONE) {
            bar.visibility = View.GONE
        }
    }

    /** Wordmark / wheel overlay when [RomEntry.logoUri] is set. */
    private fun bindHeroLogo(
        image: ImageView?,
        cache: ArtCache,
        rom: RomEntry,
    ) {
        if (image == null) return
        val uri = HeroDetail.logoUri(rom)
        if (uri == null) {
            image.visibility = View.GONE
            ArtCache.dropDisplayed(image)
            image.setImageDrawable(null)
            image.setTag(R.id.media_bind_uri, null)
            return
        }
        image.visibility = View.VISIBLE
        image.setTag(R.id.media_bind_uri, uri)
        ArtCache.dropDisplayed(image)
        image.setImageDrawable(null)
        val targetPx = (200 * image.resources.displayMetrics.density).toInt()
        cache.loadUri(
            image.context,
            key = "${rom.id}.logo",
            uriString = uri,
            maxDimension = targetPx,
            isStillValid = { image.getTag(R.id.media_bind_uri) == uri },
        ) { bmp ->
            if (bmp != null && image.getTag(R.id.media_bind_uri) == uri &&
                image.isAttachedToWindow
            ) {
                ArtCache.showDisplayed(image, bmp)
            }
        }
    }

    // Async screenshot under the meta block when [RomEntry.screenshotUri] is set.
    private fun bindScreenshot(
        image: ImageView?,
        cache: ArtCache,
        rom: RomEntry,
    ) {
        if (image == null) return
        val uri = HeroDetail.screenshotUri(rom)
        if (uri == null) {
            image.visibility = View.GONE
            ArtCache.dropDisplayed(image)
            image.setImageDrawable(null)
            image.setTag(R.id.media_bind_uri, null)
            return
        }
        image.visibility = View.VISIBLE
        image.setTag(R.id.media_bind_uri, uri)
        ArtCache.dropDisplayed(image)
        image.setImageDrawable(null)
        val targetPx = (320 * image.resources.displayMetrics.density).toInt()
        cache.loadUri(
            image.context,
            key = "shot:${rom.id}",
            uriString = uri,
            maxDimension = targetPx,
            isStillValid = { image.getTag(R.id.media_bind_uri) == uri },
        ) { bmp ->
            if (bmp != null && image.getTag(R.id.media_bind_uri) == uri &&
                image.isAttachedToWindow
            ) {
                ArtCache.showDisplayed(image, bmp)
            }
        }
    }

    /**
     * Muted looping VideoView for [RomEntry.videoUri]. Starts after 300ms;
     * hides silently on error; stops/releases on detach or rebind.
     * [host] is a cheap FrameLayout; the SurfaceView is created only when
     * this ROM actually has a video URI.
     */
    private fun bindHeroVideo(host: FrameLayout?, rom: RomEntry) {
        if (host == null) return
        val uri = HeroDetail.videoUri(rom)
        if (uri == null) {
            releaseHostVideo(host)
            host.visibility = View.GONE
            return
        }
        host.visibility = View.VISIBLE
        var video = host.getChildAt(0) as? VideoView
        if (video != null &&
            video.getTag(R.id.media_bind_uri) == uri &&
            video.visibility == View.VISIBLE
        ) {
            return
        }
        if (video == null) {
            val radius = 12 * host.resources.displayMetrics.density
            video = VideoView(host.context).apply {
                tag = TAG_HERO_VIDEO
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            }
            host.addView(
                video,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            (video.getTag(android.R.id.message) as? Runnable)?.let {
                video.removeCallbacks(it)
            }
            runCatching { video.stopPlayback() }
        }
        video.setTag(R.id.media_bind_uri, uri)
        video.visibility = View.VISIBLE
        val bound = video
        val startRunnable = Runnable {
            if (!bound.isAttachedToWindow) return@Runnable
            if (bound.getTag(R.id.media_bind_uri) != uri) return@Runnable
            runCatching {
                bound.setVideoURI(Uri.parse(uri))
                bound.setOnPreparedListener { mp: MediaPlayer ->
                    runCatching {
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                    }
                    if (bound.isAttachedToWindow &&
                        bound.getTag(R.id.media_bind_uri) == uri
                    ) {
                        bound.start()
                    }
                }
                bound.setOnErrorListener { _, _, _ ->
                    bound.visibility = View.GONE
                    true
                }
            }.onFailure {
                bound.visibility = View.GONE
            }
        }
        bound.setTag(android.R.id.message, startRunnable)
        bound.postDelayed(startRunnable, 300L)
        if (bound.getTag(android.R.id.background) == null) {
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    (v.getTag(android.R.id.message) as? Runnable)?.let {
                        v.removeCallbacks(it)
                    }
                    (v as? VideoView)?.let { vv ->
                        runCatching { vv.stopPlayback() }
                    }
                }
            }
            bound.addOnAttachStateChangeListener(listener)
            bound.setTag(android.R.id.background, listener)
        }
    }

    private fun releaseHostVideo(host: FrameLayout) {
        val video = host.getChildAt(0) as? VideoView ?: return
        (video.getTag(android.R.id.message) as? Runnable)?.let {
            video.removeCallbacks(it)
        }
        runCatching { video.stopPlayback() }
        host.removeAllViews()
    }

    // ROM hero art chain: wide cached HERO art wins and swaps the square
    // tile for the banner; anything else (no hero, square-ish hero) keeps
    // the tile with grid art over the platform placeholder. Both loads are
    // async with the ArtTile-style stale guard (tag + attach check).
    private fun bindRomHeroArt(
        bannerFrame: FrameLayout,
        tileFrame: FrameLayout,
        cache: ArtCache,
        rom: RomEntry,
        artOverrides: Map<String, String> = emptyMap(),
        artSizePx: Int = 0,
    ) {
        val context = bannerFrame.context
        val metrics = context.resources.displayMetrics
        val image = bannerFrame.children.filterIsInstance<ImageView>().first()
        bannerFrame.visibility = View.GONE
        ArtCache.dropDisplayed(image)
        image.setImageDrawable(null)
        image.tag = rom.id
        tileFrame.visibility = View.VISIBLE
        val tilePx = if (artSizePx > 0) artSizePx else (240 * metrics.density).toInt()
        ArtTile.overlay(tileFrame)?.let { overlay ->
            ArtTile.bind(
                overlay, cache, rom,
                targetPx = tilePx,
                artOverrides = artOverrides,
            )
        }
        // Cap decode to scrape target / panel width — full widthPixels bloated
        // dual-screen memory for a banner that is ~40% panel height.
        val heroMax = minOf(
            metrics.widthPixels,
            ArtCache.HERO_SCRAPE_TARGET_WIDTH_PX,
        )
        cache.load(
            context, rom,
            maxDimension = heroMax,
            kind = ArtCache.ArtKind.HERO,
            isStillValid = { image.tag == rom.id },
        ) { bitmap ->
            // onResult is main-thread already.
            if (bitmap != null && bitmap.width >= bitmap.height * 4 / 3 &&
                image.tag == rom.id && image.isAttachedToWindow
            ) {
                ArtCache.showDisplayed(image, bitmap)
                tileFrame.visibility = View.GONE
                bannerFrame.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Compact selection hero for single-display TOP_STRIP. Always HERO /
     * selection context (ignores companionRole PERF_HUD / PINNED_APP).
     * Horizontal: art (fixed dp) + name / platform / player / RA — all
     * content fits inside [SelectionStrip.STRIP_HEIGHT_DP].
     */
    fun buildTopStrip(
        activity: AppCompatActivity,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): View {
        val context: Context = activity
        val dm = context.resources.displayMetrics
        val density = dm.density
        fun dp(value: Int) = (value * density).toInt()
        val app = activity.application as GhostGalleonApp
        val model = resolveStripModel(state, library, roms, settings, app)
        val stripSpec = TopStripMetrics.forWidth(dm.widthPixels / density)
        val stripAccent = model.platformId?.let { PlatformLook.accentColor(it) }
            ?: settings.accentColor
        val resolvedTitle = context.resolveText(model.title)
        val resolvedSub = context.resolveText(model.subtitle)

        val root = FrameLayout(context).apply {
            tag = TAG_TOP_STRIP
            clipChildren = true
            clipToPadding = true
            contentDescription = TopStripMetrics.accessibilitySummary(resolvedTitle, resolvedSub)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = TAG_PANEL_ROOT
            clipChildren = false
            clipToPadding = false
            setPadding(
                dp(stripSpec.padHorizontalDp),
                dp(stripSpec.padVerticalDp),
                dp(stripSpec.padHorizontalDp),
                dp(stripSpec.padVerticalDp),
            )
            background = panelBackground(context, stripAccent)
        }

        // Art host: square; holds ImageView (app) or platform tile (ROM).
        val artSize = dp(stripSpec.artSizeDp)
        val artHost = FrameLayout(context).apply {
            tag = TAG_STRIP_ART_HOST
        }
        applyStripArtFrame(artHost, isRom = model.isRom, accentColor = stripAccent, spec = stripSpec)
        bindStripArt(artHost, context, state, library, roms, settings, app, artSize)
        row.addView(artHost, LinearLayout.LayoutParams(artSize, artSize).apply {
            marginEnd = dp(stripSpec.artGapDp)
        })

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        titleRow.addView(TextView(context).apply {
            tag = TAG_STRIP_FAV
            setText(R.string.glyph_favorite)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(FAVORITE_STAR_COLOR)
            includeFontPadding = false
            visibility = if (isSelectedFavorite(state.selectedKey, settings.favorites)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            contentDescription = context.getString(R.string.action_favorite)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(6) })
        val stripRom = if (model.isRom) selectedRom(state.selectedKey, roms, app) else null
        titleRow.addView(TextView(context).apply {
            tag = TAG_HERO_NAME
            setTag(R.id.hero_bound_id, stripRom?.id ?: state.selectedKey)
            setTag(
                R.id.hero_art_gen,
                stripRom?.let { app.artCache.artGeneration(it.id) } ?: 0,
            )
            text = resolvedTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, stripSpec.titleSp)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ))
        texts.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        texts.addView(TextView(context).apply {
            tag = TAG_HERO_SUB
            text = resolvedSub
            setTextSize(TypedValue.COMPLEX_UNIT_SP, stripSpec.subtitleSp)
            setTextColor(0xCCFFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })
        texts.addView(TextView(context).apply {
            tag = TAG_STRIP_DETAIL
            text = model.detail?.let(context::resolveText).orEmpty()
            visibility = if (model.detail != null) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, stripSpec.detailSp)
            setTextColor(0x99FFFFFF.toInt())
            fontFeatureSettings = "tnum"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })
        val raRow = LinearLayout(context).apply {
            tag = TAG_STRIP_RA_ROW
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            visibility = if (model.raLine != null) View.VISIBLE else View.GONE
        }
        val stripRaTv = TextView(context).apply {
            tag = TAG_HERO_RA
            setTextSize(TypedValue.COMPLEX_UNIT_SP, stripSpec.raSp)
            fontFeatureSettings = "tnum"
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        val stripRaBar = MeterBarView(context).apply {
            tag = TAG_STRIP_RA_BAR
            visibility = View.GONE
        }
        raRow.addView(
            stripRaTv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        raRow.addView(
            stripRaBar,
            LinearLayout.LayoutParams(
                dp(stripSpec.raBarWidthDp),
                dp(stripSpec.raBarHeightDp),
            ).apply { marginStart = dp(8) },
        )
        val stripRaProgress = stripRom?.let { app.raProgressFor(it.id) }
        bindRaLine(
            tv = stripRaTv,
            bar = stripRaBar,
            progress = stripRaProgress,
            hasCreds = !settings.raApiKey.isNullOrBlank(),
            accentColor = settings.accentColor,
        )
        raRow.visibility = stripRaTv.visibility
        texts.addView(raRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })

        row.addView(texts, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
        ))
        // Host compact StatusPill in the top-right of TOP_STRIP on single-screen
        // devices so it never overlaps the deck chrome below.
        if (settings.browseChrome.deckStatusPill) {
            row.addView(
                StatusPill.build(context, compact = true),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP
                    marginStart = dp(10)
                },
            )
        }
        root.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        // Subtle 1dp accent-tinted bottom hairline separating TOP_STRIP from the deck below.
        root.addView(
            View(context).apply {
                tag = TAG_STRIP_DIVIDER
                setBackgroundColor(TopStripMetrics.stripDividerColor(stripAccent))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.strokePx(1),
                Gravity.BOTTOM,
            ),
        )

        // Kick live RA for ROM selection (same as full hero).
        if (stripRom != null) {
            app.requestRaProgress(stripRom.id, stripRom.name, stripRom.platformId)
        }
        return root
    }

    private fun applyStripArtFrame(
        artHost: FrameLayout,
        isRom: Boolean,
        accentColor: Int,
        spec: TopStripMetrics.Spec,
    ) {
        if (isRom) {
            applyHeroArtFrame(
                target = artHost,
                accentColor = accentColor,
                elevationDp = spec.artElevationDp,
                cornerRadiusDp = spec.artCornerRadiusDp,
            )
        } else {
            artHost.foreground = null
            artHost.elevation = 0f
            artHost.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
    }

    private fun resolveStripModel(
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        app: GhostGalleonApp,
    ): SelectionStrip.Model {
        val rom = selectedRom(state.selectedKey, roms, app)
        if (rom != null) {
            val pmInstalled = { pkg: String -> app.packageManager.isInstalled(pkg) }
            val preferred = RomProfiles.preferredPlayerId(
                rom.id,
                settings.romProfiles,
                settings.defaultPlayers[rom.platformId],
            )
            val displayName = RomNames.display(rom, settings.romNames)
            val displayRom = if (displayName == rom.name) rom else rom.copy(name = displayName)
            return SelectionStrip.forRom(
                rom = displayRom,
                preferredPlayerId = preferred,
                installed = pmInstalled,
                playMeta = romMetaLine(settings, SlotKey.rom(rom.id)),
                raProgress = app.raProgressFor(rom.id),
                hasRaCredentials = !settings.raApiKey.isNullOrBlank(),
            )
        }
        val entry = state.selectedKey?.let { library.byPackage(settings)[it] }
        if (entry != null) return SelectionStrip.forApp(entry.label)
        return SelectionStrip.empty()
    }

    private fun bindStripArt(
        host: FrameLayout,
        context: Context,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        app: GhostGalleonApp,
        artSize: Int,
    ) {
        host.removeAllViews()
        val rom = selectedRom(state.selectedKey, roms, app)
        if (rom != null) {
            val cornerDp = TopStripMetrics.ART_CORNER_RADIUS_DP
            val tile = PlatformTile.view(context, rom.platformId, cornerRadiusDp = cornerDp).apply {
                tag = TAG_HERO_ICON
            }
            host.addView(tile, FrameLayout.LayoutParams(artSize, artSize))
            // Prefer grid art when available; corner radius matches PlatformTile & rim.
            val radiusPx = context.dpF(cornerDp)
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                    }
                }
            }
            host.addView(image, FrameLayout.LayoutParams(artSize, artSize))
            val romId = rom.id
            app.artCache.load(
                context, rom,
                maxDimension = artSize,
                kind = ArtCache.ArtKind.GRID,
                artOverrides = settings.artOverrides,
                isStillValid = { image.isAttachedToWindow },
            ) { bitmap ->
                if (bitmap != null && image.isAttachedToWindow &&
                    selectedRom(state.selectedKey, roms, app)?.id == romId
                ) {
                    ArtCache.showDisplayed(image, bitmap)
                    image.visibility = View.VISIBLE
                    tile.visibility = View.GONE
                }
            }
            return
        }
        val entry = state.selectedKey?.let { library.byPackage(settings)[it] }
        val image = ImageView(context).apply {
            tag = TAG_HERO_ICON
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        host.addView(image, FrameLayout.LayoutParams(artSize, artSize))
        if (entry != null) {
            CustomIcon.bind(
                image,
                iconLoader(context),
                app.artCache,
                settings,
                entry.packageName,
                artSize,
            )
        } else {
            image.setImageResource(R.drawable.ic_brand_ship)
        }
    }

    private fun updateTopStrip(
        view: View,
        context: Context,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): Boolean {
        val name = view.findViewWithTag<TextView>(TAG_HERO_NAME) ?: return false
        val sub = view.findViewWithTag<TextView>(TAG_HERO_SUB) ?: return false
        val detail = view.findViewWithTag<TextView>(TAG_STRIP_DETAIL) ?: return false
        val ra = view.findViewWithTag<TextView>(TAG_HERO_RA) ?: return false
        val artHost = view.findViewWithTag<FrameLayout>(TAG_STRIP_ART_HOST) ?: return false
        val app = context.applicationContext as GhostGalleonApp
        val dm = context.resources.displayMetrics
        val density = dm.density
        val stripSpec = TopStripMetrics.forWidth(dm.widthPixels / density)
        val model = resolveStripModel(state, library, roms, settings, app)
        val stripAccent = model.platformId?.let { PlatformLook.accentColor(it) }
            ?: settings.accentColor
        val rom = if (model.isRom) selectedRom(state.selectedKey, roms, app) else null
        val bindId = rom?.id ?: state.selectedKey
        val artGen = rom?.let { app.artCache.artGeneration(it.id) } ?: 0
        val alreadyBound = bindId != null &&
            sameHeroBinding(
                name.getTag(R.id.hero_bound_id),
                name.getTag(R.id.hero_art_gen),
                bindId,
                artGen,
            )
        val previousTitle = name.text
        val nextTitle = context.resolveText(model.title)
        val nextSubtitle = context.resolveText(model.subtitle)
        val animateSelection = CompanionHeroStyle.shouldAnimateSelection(
            alreadyBound = alreadyBound,
            previousText = previousTitle,
            nextText = nextTitle,
        )
        name.text = nextTitle
        sub.text = nextSubtitle
        detail.text = model.detail?.let(context::resolveText).orEmpty()
        detail.visibility = if (model.detail != null) View.VISIBLE else View.GONE
        if (animateSelection) {
            animateHeroTextSettle(name, density)
            animateHeroTextSettle(sub, density)
        }
        val stripRaBar = view.findViewWithTag<MeterBarView>(TAG_STRIP_RA_BAR)
        bindRaLine(
            tv = ra,
            bar = stripRaBar,
            progress = rom?.let { app.raProgressFor(it.id) },
            hasCreds = !settings.raApiKey.isNullOrBlank(),
            accentColor = settings.accentColor,
        )
        view.findViewWithTag<View>(TAG_STRIP_RA_ROW)?.let { raRow ->
            if (raRow.visibility != ra.visibility) raRow.visibility = ra.visibility
        }
        view.findViewWithTag<View>(TAG_STRIP_FAV)?.let { fav ->
            val nextVis = if (isSelectedFavorite(state.selectedKey, settings.favorites)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (fav.visibility != nextVis) fav.visibility = nextVis
        }
        view.findViewWithTag<View>(TAG_TOP_STRIP)?.contentDescription =
            TopStripMetrics.accessibilitySummary(nextTitle, nextSubtitle)
        view.findViewWithTag<View>(TAG_PANEL_ROOT)?.background =
            panelBackground(context, stripAccent)
        view.findViewWithTag<View>(TAG_STRIP_DIVIDER)
            ?.setBackgroundColor(TopStripMetrics.stripDividerColor(stripAccent))
        if (!alreadyBound) {
            val artSize = (stripSpec.artSizeDp * density).toInt()
            applyStripArtFrame(artHost, isRom = model.isRom, accentColor = stripAccent, spec = stripSpec)
            bindStripArt(artHost, context, state, library, roms, settings, app, artSize)
            animateHeroArtSettle(artHost)
            if (rom != null) app.requestRaProgress(rom.id, rom.name, rom.platformId)
        }
        name.setTag(R.id.hero_bound_id, bindId)
        name.setTag(R.id.hero_art_gen, artGen)
        return true
    }

    fun build(
        activity: AppCompatActivity,
        state: DeckState,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
    ): View {
        val context: Context = activity
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        // Usable panel size in dp — bottom Sugar is short; scale hero art/name
        // so the title is never clipped by actions/hints (see CompanionHeroMetrics).
        val dm = context.resources.displayMetrics
        val panelHeightDp = dm.heightPixels / density
        val panelWidthDp = dm.widthPixels / density
        val heroSpec = CompanionHeroMetrics.forPanel(panelHeightDp)
        val artPx = dp(heroSpec.artSizeDp)

        // FrameLayout root so the fallback brand scene (clouds/sea behind,
        // rain in front) can span the WHOLE panel; all normal content lives
        // in the vertical `content` column, which carries TAG_PANEL_ROOT.
        val root = FrameLayout(context)
        fun installSwitcherHost(): FrameLayout {
            if (root.findViewWithTag<View>(TAG_SESSION_SWITCHER_HOST) == null) {
                root.addView(
                    FrameLayout(context).apply { tag = TAG_SESSION_SWITCHER_HOST },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            return root
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = TAG_PANEL_ROOT
            setPadding(dp(24), dp(8), dp(24), 0)
            clipChildren = false
            clipToPadding = false
        }

        val app = activity.application as GhostGalleonApp
        releaseHelperEmbed(activity.window?.decorView)

        // Companion role chips (Hero / Now Playing / Perf / Pin).
        val preferredRole = CompanionRole.parse(settings.companionRole)
        val pinPkg = settings.companionPinnedPackage
        val pinInstalled = pinPkg != null && context.packageManager.isInstalled(pinPkg)
        val effectiveRole = CompanionRoleResolve.effective(
            CompanionRoleResolve.Context(
                preferred = preferredRole,
                openSessionKey = app.openSession?.key,
                pinnedPackage = pinPkg,
                sessionPolicy = app.sessionSurface?.policy,
                pinnedPackageInstalled = pinInstalled,
                sessionGreedy = app.sessionSurface?.greedy == true,
            ),
        )
        val toDp: (Int) -> Int = { v -> dp(v) }
        val showsStatusPillOverlay = settings.browseChrome.deckStatusPill &&
            effectiveRole != CompanionRole.PERF_HUD &&
            !(effectiveRole == CompanionRole.PINNED_APP &&
                !CompanionRoleResolve.pinConflictsWithSession(pinPkg, app.sessionSurface))
        content.addView(
            roleChipRow(
                context = context,
                settings = settings,
                current = preferredRole,
                hasStatusPill = showsStatusPillOverlay,
                panelWidthDp = panelWidthDp,
                dp = toDp,
            ) { role ->
                app.updateSettings(settings.copy(companionRole = role.name))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(4) },
        )

        val surface = app.sessionSurface
        val hostId = activity.currentDisplayId()
        val playHud = PlayHostPolicy.playHostAllowed(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            policy = surface?.policy,
            greedy = surface?.greedy == true,
            hostDisplayId = hostId,
            launchDisplayId = surface?.launchDisplayId,
        )
        fun sessionCard(
            session: com.visorcraft.ghostgalleon.library.OpenSession,
            compact: Boolean,
        ): View =
            if (playHud && surface != null) {
                buildPlayHud(
                    activity, library, roms, settings, session, surface, toDp, compact,
                )
            } else {
                buildNowPlayingCard(
                    activity, library, roms, settings, session, toDp, compact,
                )
            }

        when (effectiveRole) {
            CompanionRole.PERF_HUD -> {
                content.addView(buildPerfHud(context, settings, toDp), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                content.background = panelBackground(context, settings.accentColor)
                root.addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
                return installSwitcherHost()
            }
            CompanionRole.PINNED_APP -> {
                if (!CompanionRoleResolve.pinConflictsWithSession(pinPkg, app.sessionSurface)) {
                    content.addView(
                        buildPinnedAppPanel(activity, settings, pinPkg, pinInstalled, toDp),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    content.background = panelBackground(context, settings.accentColor)
                    root.addView(content, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                    return installSwitcherHost()
                }
                // Pin is the open KEEP game — pause pin, show play HUD.
            }
            CompanionRole.NOW_PLAYING, CompanionRole.HERO -> { /* play HUD / hero below */ }
        }

        val openSession = app.openSession
        // KEEP play host: full play HUD replaces hero (and compact banner).
        // PERF stays the in-place tab (already returned). Pin without a
        // conflict already returned. Pin-of-the-open-game falls through here.
        if (playHud && surface != null && openSession != null) {
            val cockpit = CockpitPolicy.cockpitAllowed(
                playHostAllowed = true,
                playerId = surface.playerId,
                cockpitEnabled = settings.winlatorCockpit,
            )
            content.addView(
                buildPlayHud(
                    activity, library, roms, settings, openSession, surface, toDp,
                    compact = panelHeightDp < 500f,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (cockpit ||
                        app.hostSurface == HostSurface.SEAT ||
                        app.hostSurface == HostSurface.HELPER ||
                        helperChipKind(app) == HelperChipKind.ENABLED
                    ) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    },
                ),
            )
            content.background = panelBackground(context, settings.accentColor)
            root.addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            attachCompanionStatusOverlay(root, context, settings)
            return installSwitcherHost()
        }

        if (effectiveRole == CompanionRole.NOW_PLAYING && openSession != null) {
            content.addView(
                sessionCard(openSession, compact = false),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            content.background = panelBackground(context, settings.accentColor)
            root.addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            attachCompanionStatusOverlay(root, context, settings)
            return installSwitcherHost()
        }

        // Compact Now Playing banner when a session is open (non-play-host).
        openSession?.let { session ->
            content.addView(
                sessionCard(session, compact = true),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(12) },
            )
        }

        // Hero area.
        val selected = state.selectedKey
        val selectedRom = selectedRom(selected, roms, app)
        val selectedEntry = if (selectedRom == null && selected != null) {
            library.byPackage(settings)[selected]
        } else {
            null
        }
        content.background = panelBackground(
            context,
            selectedRom?.let { PlatformTile.colorFor(it.platformId) }
                ?: glowColor(context, selectedEntry?.packageName, settings),
        )
        var frontRain: Drawable? = null
        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // TOP + horizontal center: when space is tight, prefer keeping the
            // title (below art) over vertical centering that clips mid-glyph.
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(4))
        }
        // Resume chip = jump to a *different* last-played title. Opt-in via
        // Browse chrome [resumeChip]. Never show for the already-selected key,
        // open session, or after user swipe-dismiss ([Settings.hideResumeChip]
        // until the next real launch). Horizontal swipe dismisses the pill;
        // tap launches via launchSlotKey. KEEP click prefers sessionSurface.key
        // (resumeLaunchKey) so a navigated hero does not steal the session.
        run {
            if (!settings.browseChrome.resumeChip) return@run
            if (app.openSession != null) return@run
            if (settings.hideResumeChip) return@run
            val available = settings.lastLaunchedMs.keys.toList()
            val candidates = LibraryBrowse.continueCandidates(
                availableKeys = available,
                lastLaunchedMs = settings.lastLaunchedMs,
                excludeKey = state.selectedKey,
            )
            val cont = candidates.firstOrNull() ?: return@run
            val contName = resumeLabel(cont, library, roms, settings, app)
            val onFill = ThemePack.onFillTextColor(settings.accentColor)
            val chevron = androidx.core.content.ContextCompat.getDrawable(
                context,
                R.drawable.ic_chevron_right,
            )?.mutate()?.apply {
                setTint(onFill)
                val iconPx = dp(16)
                setBounds(0, 0, iconPx, iconPx)
            }
            // Filled accent pill + WCAG-safe contrast text (legible on OLED #E0E0E0).
            hero.addView(TextView(context).apply {
                tag = TAG_RESUME_CHIP
                text = context.getString(R.string.format_resume, contName)
                contentDescription = context.getString(
                    R.string.format_resume_accessibility,
                    contName,
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onFill)
                background = buildInteractivePillBackground(
                    context = context,
                    base = TileBackgrounds.accentPill(context, settings.accentColor, cornerRadiusDp = 22),
                    accent = settings.accentColor,
                    cornerRadiusDp = 22,
                    verticalInsetDp = 0,
                )
                elevation = context.dpF(6)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val shadow = CompanionHeroStyle.artShadowColor(settings.accentColor)
                    outlineSpotShadowColor = shadow
                    outlineAmbientShadowColor = shadow
                }
                minimumHeight = dp(CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP)
                setPadding(dp(18), dp(10), dp(14), dp(10))
                setCompoundDrawablesRelative(null, null, chevron, null)
                compoundDrawablePadding = dp(4)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                bindResumeChipGestures(
                    view = this,
                    cont = cont,
                    activity = activity,
                    state = state,
                    roms = roms,
                    app = app,
                    swipeSlopPx = dp(40).toFloat(),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(10)
                marginStart = dp(24)
                marginEnd = dp(24)
            })
        }
        if (selectedRom != null) {
            // ROM hero: wide HERO banner when cached (async swap-in),
            // otherwise the square tile — cached grid art over the platform
            // placeholder — then ROM name and platform label.
            val cache = (activity.application as GhostGalleonApp).artCache
            val platformAccent = PlatformLook.accentColor(selectedRom.platformId)
            val artElevation = CompanionHeroStyle.artElevationDp(heroSpec.artSizeDp)
            val banner = bannerFrame(context).also {
                applyHeroArtFrame(it, platformAccent, artElevation)
            }
            val bannerH = CompanionHeroMetrics.bannerHeightPx(dm.heightPixels, heroSpec)
            hero.addView(banner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                bannerH,
            ))
            val artFrame = ArtTile.view(
                context,
                cache,
                selectedRom,
                targetPx = artPx,
                // bindRomHeroArt below owns the single art bind for this
                // tile; binding GRID art here too would queue a redundant
                // decode that the rebind immediately obsoletes.
                bindNow = false,
            ) as FrameLayout
            applyHeroArtFrame(artFrame, platformAccent, artElevation)
            // updateSelection finds the placeholder tile by this tag for
            // in-place restyle; the art overlay sits next to it in the frame.
            artFrame.children.filterIsInstance<TextView>().first().tag = TAG_HERO_ICON
            hero.addView(artFrame, LinearLayout.LayoutParams(artPx, artPx))
            bindRomHeroArt(
                banner, artFrame, cache, selectedRom, settings.artOverrides, artPx,
            )
            val logo = ImageView(context).apply {
                tag = TAG_HERO_LOGO
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
            }
            hero.addView(logo, LinearLayout.LayoutParams(dp(200), dp(48)).apply {
                topMargin = dp(6)
                gravity = Gravity.CENTER_HORIZONTAL
            })
            bindHeroLogo(logo, cache, selectedRom)
            // Title must fit above actions on short secondary panels.
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_NAME
                setTag(R.id.hero_bound_id, selectedRom.id)
                setTag(R.id.hero_art_gen, cache.artGeneration(selectedRom.id))
                text = RomNames.display(selectedRom, settings.romNames)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, heroSpec.nameSp)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = heroSpec.nameMaxLines
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(
                    dp(heroSpec.nameSidePadDp),
                    dp(heroSpec.nameTopPadDp),
                    dp(heroSpec.nameSidePadDp),
                    dp(4),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            val platform = Platforms.byId(selectedRom.platformId)
            val installed = { pkg: String -> context.packageManager.isInstalled(pkg) }
            val preferredPlayer = RomProfiles.preferredPlayerId(
                selectedRom.id,
                settings.romProfiles,
                settings.defaultPlayers[selectedRom.platformId],
            )
            // Single compact subline saves ~2 rows on the short secondary panel.
            // Tags META/PLAYER kept GONE so updateSelection paths stay stable.
            val compact = HeroDetail.compactSubline(
                HeroDetail.platformLine(platform, selectedRom.platformId),
                romMetaLine(settings, SlotKey.rom(selectedRom.id)),
                HeroDetail.playerShortName(platform, preferredPlayer, installed),
            )
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_SUB
                text = context.resolveText(compact)
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (heroSpec.artSizeDp < 180) 13f else 15f,
                )
                setTextColor(0xB3FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(4), dp(heroSpec.nameSidePadDp), 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_META
                visibility = View.GONE
            })
            val metadataText = HeroDetail.metadataLine(selectedRom)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_METADATA
                text = metadataText.orEmpty()
                visibility = if (metadataText != null && heroSpec.showExtraMedia) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0x88FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(2), dp(heroSpec.nameSidePadDp), 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_PLAYER
                visibility = View.GONE
            })
            val raProgress = app.raProgressFor(selectedRom.id)
            val hasRaCreds = !settings.raApiKey.isNullOrBlank()
            val raTv = TextView(context).apply {
                tag = TAG_HERO_RA
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 1
                fontFeatureSettings = "tnum"
                setPadding(0, dp(2), 0, 0)
            }
            hero.addView(raTv)
            val raBar = MeterBarView(context).apply {
                tag = TAG_HERO_RA_BAR
                visibility = View.GONE
            }
            val compactHero = panelHeightDp < 600f
            hero.addView(
                raBar,
                LinearLayout.LayoutParams(
                    dp(CompanionHeroStyle.raBarWidthDp(compactHero)),
                    dp(CompanionHeroStyle.RA_BAR_HEIGHT_DP),
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(3)
                },
            )
            bindRaLine(
                tv = raTv,
                bar = raBar,
                progress = raProgress,
                hasCreds = hasRaCreds,
                accentColor = settings.accentColor,
            )
            val descText = HeroDetail.descriptionText(selectedRom.description)
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_DESC
                text = descText.orEmpty()
                visibility = if (descText != null && heroSpec.showExtraMedia) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0xA0FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = if (heroSpec.showExtraMedia) 4 else 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(4), dp(heroSpec.nameSidePadDp), 0)
            })
            if (heroSpec.showExtraMedia) {
                val shot = ImageView(context).apply {
                    tag = TAG_HERO_SHOT
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    visibility = View.GONE
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(
                                0, 0, view.width, view.height, dp(12).toFloat())
                        }
                    }
                }
                hero.addView(shot, LinearLayout.LayoutParams(dp(280), dp(140)).apply {
                    topMargin = dp(6)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                bindScreenshot(shot, cache, selectedRom)
                val videoHost = FrameLayout(context).apply {
                    tag = TAG_HERO_VIDEO_HOST
                    visibility = View.GONE
                }
                hero.addView(videoHost, LinearLayout.LayoutParams(dp(280), dp(140)).apply {
                    topMargin = dp(6)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                bindHeroVideo(videoHost, selectedRom)
            }
            // Hero quick actions — scrollable centered pill strip so localized
            // labels never clip on the ~540dp Sugar panel. Every click resolves
            // live state.selectedKey / app.settings so in-place updateSelection
            // never leaves stale build-time closures behind.
            if (heroSpec.showQuickChips) {
                val chipH = dp(CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP)
                val insetV = dp(CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP)
                val quick = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    isBaselineAligned = false
                    clipChildren = false
                    clipToPadding = false
                    setPadding(dp(4), dp(3), dp(4), 0)
                }
                val quickScroll = HorizontalScrollView(context).apply {
                    tag = TAG_HERO_QUICK_BAR
                    isFillViewport = true
                    isHorizontalScrollBarEnabled = false
                    isHorizontalFadingEdgeEnabled = true
                    setFadingEdgeLength(dp(18))
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    clipChildren = false
                    clipToPadding = false
                }
                fun liveRom(): RomEntry? = selectedRom(state.selectedKey, app.romEntries, app)
                fun quickChip(
                    label: String,
                    chipTag: Any? = null,
                    onClick: () -> Unit,
                ): TextView = TextView(context).apply {
                    tag = chipTag
                    text = label
                    contentDescription = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(Color.WHITE)
                    background = buildInteractivePillBackground(
                        context = context,
                        base = TileBackgrounds.chipIdle(context, cornerRadiusDp = 17),
                        accent = settings.accentColor,
                        cornerRadiusDp = 17,
                    )
                    gravity = Gravity.CENTER
                    setPadding(dp(12), insetV, dp(12), insetV)
                    includeFontPadding = true
                    setSingleLine()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }
                }.also { tv ->
                    attachChipTactileFeedback(tv) {
                        val target = CompanionChromeErgonomics.horizontalScrollTarget(
                            chipLeft = tv.left,
                            chipRight = tv.right,
                            viewportWidth = quickScroll.width,
                            endClearancePx = 0,
                            contentWidth = quick.width,
                            currentScrollX = quickScroll.scrollX,
                        )
                        if (target != null) quickScroll.smoothScrollTo(target, 0)
                    }
                }
                fun addChip(
                    label: String,
                    chipTag: Any? = null,
                    onClick: () -> Unit,
                ): TextView {
                    if (quick.childCount > 0) {
                        quick.addView(
                            View(context),
                            LinearLayout.LayoutParams(dp(6), chipH),
                        )
                    }
                    val tv = quickChip(label, chipTag, onClick)
                    quick.addView(
                        tv,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            chipH,
                        ),
                    )
                    return tv
                }
                val initialKey = SlotKey.rom(selectedRom.id)
                val favChip = addChip(
                    label = context.getString(favoriteChipLabelRes(initialKey, settings.favorites)),
                    chipTag = TAG_HERO_QUICK_FAV,
                ) {
                    val rom = liveRom() ?: return@addChip
                    EntryActions.toggleFavorite(activity, SlotKey.rom(rom.id))
                }
                styleFavoriteQuickChip(
                    chip = favChip,
                    isFavorite = isSelectedFavorite(initialKey, settings.favorites),
                    accentColor = settings.accentColor,
                )
                addChip(context.getString(R.string.action_pin)) {
                    val rom = liveRom() ?: return@addChip
                    val live = app.settings
                    val filled = CollectionsOps.bulkFillSlots(
                        live.gridSlots, listOf(SlotKey.rom(rom.id)))
                    app.updateSettings(live.copy(gridSlots = filled))
                    Toast.makeText(
                        activity,
                        R.string.deck_pinned_to_grid,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                addChip(context.getString(R.string.action_art)) {
                    val rom = liveRom() ?: return@addChip
                    (activity as? com.visorcraft.ghostgalleon.ui.BaseDeckActivity)
                        ?.requestCustomIcon { uri ->
                            val live = app.settings
                            app.artCache.invalidate(rom.id)
                            app.updateSettings(live.copy(
                                artOverrides = live.artOverrides +
                                    (rom.id to uri.toString())))
                        }
                }
                addChip(context.getString(R.string.action_open_with)) {
                    val rom = liveRom() ?: return@addChip
                    val romKey = SlotKey.rom(rom.id)
                    val openPlatform = Platforms.byId(rom.platformId)
                    val players = openPlatform?.players.orEmpty()
                    if (players.isEmpty()) {
                        Toast.makeText(
                            activity,
                            R.string.deck_no_players,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        DeckDialogs.chooseItem(
                            context = activity,
                            titleRes = R.string.action_open_with,
                            items = players.map { it.displayName }.toTypedArray(),
                        ) { which ->
                            val p = players[which]
                            val live = app.settings
                            app.updateSettings(
                                live.copy(
                                    defaultPlayers = live.defaultPlayers +
                                        (rom.platformId to p.id),
                                ),
                                notify = false,
                            )
                            launchSlotKey(
                                activity, state, app.romEntries, romKey, playerId = p.id)
                        }
                    }
                }
                quickScroll.addView(
                    quick,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL,
                    ),
                )
                hero.addView(
                    quickScroll,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        } else if (selectedEntry != null) {
            val appGlow = glowColor(context, selectedEntry.packageName, settings)
            val pedestalInset = dp(CompanionHeroStyle.appPedestalInsetDp(heroSpec.artSizeDp))
            val pedestal = FrameLayout(context).apply {
                tag = TAG_HERO_APP_PEDESTAL
                setPadding(pedestalInset, pedestalInset, pedestalInset, pedestalInset)
            }
            applyAppPedestalFrame(
                pedestal = pedestal,
                accentColor = appGlow,
                artSizePx = artPx,
                elevationDp = CompanionHeroStyle.artElevationDp(heroSpec.artSizeDp),
            )
            val icon = ImageView(context).apply {
                tag = TAG_HERO_ICON
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            CustomIcon.bind(
                icon, iconLoader(context),
                (activity.application as GhostGalleonApp).artCache,
                settings, selectedEntry.packageName, artPx)
            pedestal.addView(
                icon,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            hero.addView(pedestal, LinearLayout.LayoutParams(artPx, artPx))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_NAME
                setTag(R.id.hero_bound_id, selectedEntry.packageName)
                text = selectedEntry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, heroSpec.nameSp)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = heroSpec.nameMaxLines
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(
                    dp(heroSpec.nameSidePadDp),
                    dp(heroSpec.nameTopPadDp),
                    dp(heroSpec.nameSidePadDp),
                    dp(4),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            hero.addView(TextView(context).apply {
                tag = TAG_HERO_SUB
                text = context.resolveText(
                    HeroDetail.compactSubline(
                        context.getString(R.string.label_app),
                        romMetaLine(settings, selectedEntry.packageName),
                        null,
                    ),
                )
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (heroSpec.artSizeDp < 180) 13f else 15f,
                )
                setTextColor(0xB3FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(heroSpec.nameSidePadDp), dp(2), dp(heroSpec.nameSidePadDp), 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        } else {
            // Layered brand fallback, full-panel: clouds + sea behind the
            // content column (transparent so the scene shows edge to edge).
            // Ship is a root FrameLayout overlay (not inside the hero column)
            // so role chips / status pill never pull it off the waterline.
            // Size scales with panel height; hull sits on the clouds:sea 3:2
            // horizon (CompanionHeroMetrics.brandShipLayout) — works on any
            // dual/single panel, not only Sugar. Rain is added LAST so it
            // falls in front of ship + UI.
            // Clouds/sea are exact vertical slices of one 1280×720 scene
            // (432px sky, 288px water), stacked 3:2 so the horizon keeps its
            // authored 60/40 split (CENTER_CROP trims width overflow). Every
            // layer is optional: no sky/sea = programmatic dusk-horizon scene,
            // no ship anim (or pre-API-28) = ic_brand_ship, no rain = none.
            val tokens = com.visorcraft.ghostgalleon.settings.ThemePack.resolve(settings)
            val clouds = loadAnimated(context, "hero_clouds_anim")
            val sea = loadAnimated(context, "hero_sea_anim")
            val ocean = if (clouds == null || sea == null) {
                loadAnimated(context, "hero_ocean_anim")
            } else {
                null
            }
            if (clouds != null && sea != null) {
                val bgColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                listOf(clouds to 3f, sea to 2f).forEach { (anim, weight) ->
                    bgColumn.addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        showAnimated(anim)
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, weight))
                }
                root.addView(bgColumn, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))
                content.background = null
            } else {
                val singles = listOfNotNull(clouds, sea ?: ocean)
                if (singles.isNotEmpty()) {
                    singles.forEach { anim ->
                        root.addView(ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            showAnimated(anim)
                        }, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                    content.background = null
                } else {
                    // Programmatic 3:2 dusk-horizon backdrop grounding the galleon waterline.
                    val palette = CompanionHeroStyle.brandHorizonPalette(
                        panelLift = tokens.panelLift,
                        accentColor = settings.accentColor,
                    )
                    val horizonColumn = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    horizonColumn.addView(
                        View(context).apply {
                            background = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(0xFF000000.toInt(), palette.skyMid, palette.skyHorizon),
                            )
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f),
                    )
                    horizonColumn.addView(
                        View(context).apply {
                            setBackgroundColor(palette.horizonLine)
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            context.strokePx(1),
                        ),
                    )
                    horizonColumn.addView(
                        View(context).apply {
                            background = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(palette.seaTop, 0xFF000000.toInt()),
                            )
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f),
                    )
                    root.addView(
                        horizonColumn,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    content.background = null
                }
            }
            // z-order: bg (already added) → content (later) → ship → rain.
            // Transparent content regions show the ship on the waterline.
            val shipLayout = CompanionHeroMetrics.brandShipLayout(
                panelHeightPx = dm.heightPixels,
                density = density,
            )
            if (shipLayout.sizePx > 0) {
                root.addView(ImageView(context).apply {
                    tag = TAG_HERO_ICON
                    contentDescription = context.getString(R.string.app_name)
                    val ship = loadAnimated(context, "hero_ship_anim")
                    if (ship != null) {
                        showAnimated(ship)
                    } else {
                        setImageResource(R.drawable.ic_brand_ship)
                    }
                }, FrameLayout.LayoutParams(shipLayout.sizePx, shipLayout.sizePx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    topMargin = shipLayout.topMarginPx
                })
            }
            if (CompanionHeroMetrics.brandCaptionFitsBelowShip(dm.heightPixels, shipLayout, density)) {
                hero.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                hero.addView(
                    TextView(context).apply {
                        setText(R.string.deck_select_game_or_app)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(UiDimens.ALPHA_MUTED)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(24), 0, dp(24), dp(8))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            if (tokens.heroRain) {
                frontRain = loadAnimated(context, "hero_rain_anim")
            }
        }
        content.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        if (settings.showHints) {
            content.addView(HintBar.build(context), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        // Rain falls in front of the ship (and everything else).
        frontRain?.let { rain ->
            root.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                showAnimated(rain)
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // Dual: only larger panel. Overlay pins Swap/Settings to bottom
        // corners (above rain so they stay tappable).
        if (shouldHostSystemChromeIcons(activity)) {
            attachSystemChromeOverlay(root, context, activity, state)
        }
        attachCompanionStatusOverlay(root, context, settings)
        return installSwitcherHost()
    }

    /** Time + battery on the companion window, true top-end — not in the padded column. */
    private fun attachCompanionStatusOverlay(
        root: FrameLayout,
        context: Context,
        settings: Settings,
    ) {
        if (!settings.browseChrome.deckStatusPill) return
        if (root.findViewWithTag<View>(StatusPill.TAG) != null) return
        root.addView(
            StatusPill.build(context, compact = false),
            StatusPill.overlayLayoutParams(context, flushCorner = true),
        )
    }

    private fun buildInteractivePillBackground(
        context: Context,
        base: Drawable,
        accent: Int,
        cornerRadiusDp: Int,
        verticalInsetDp: Int = CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP,
    ): Drawable {
        val focusable = TileBackgrounds.focusableRow(
            context = context,
            accent = accent,
            idleBackground = base,
            cornerRadiusDp = cornerRadiusDp,
            strokeDp = 2,
        )
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = context.dpF(cornerRadiusDp)
        }
        val ripple = RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            focusable,
            mask,
        )
        val insetPx = (verticalInsetDp * context.resources.displayMetrics.density).toInt()
        return if (insetPx > 0) InsetDrawable(ripple, 0, insetPx, 0, insetPx) else ripple
    }

    private fun attachChipTactileFeedback(
        view: View,
        onFocused: (() -> Unit)? = null,
    ) {
        var pressed = false
        var focused = false
        fun updateScale() {
            val scale = CompanionChromeErgonomics.targetScale(pressed, focused)
            view.animate().cancel()
            if (!ValueAnimator.areAnimatorsEnabled()) {
                view.scaleX = scale
                view.scaleY = scale
                return
            }
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(CompanionChromeErgonomics.SCALE_ANIM_MS)
                .setInterpolator(heroSettleInterpolator)
                .start()
        }
        view.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true
                    updateScale()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pressed) {
                        pressed = false
                        updateScale()
                    }
                }
            }
            false
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            focused = hasFocus
            updateScale()
            if (hasFocus) onFocused?.invoke()
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.animate().cancel()
                v.scaleX = 1f
                v.scaleY = 1f
            }
        })
    }

    private fun roleChipRow(
        context: Context,
        settings: Settings,
        current: CompanionRole,
        hasStatusPill: Boolean,
        panelWidthDp: Float,
        dp: (Int) -> Int,
        onPick: (CompanionRole) -> Unit,
    ): View {
        val endClearanceDp = CompanionHeroMetrics.roleBarEndClearanceDp(
            hasStatusPill = hasStatusPill,
            panelWidthDp = panelWidthDp,
        )
        val endClearancePx = dp(endClearanceDp)
        val row = LinearLayout(context).apply {
            tag = TAG_ROLE_CHIPS
            orientation = LinearLayout.HORIZONTAL
            gravity = if (endClearanceDp > 0) {
                Gravity.START or Gravity.CENTER_VERTICAL
            } else {
                Gravity.CENTER
            }
            isBaselineAligned = false
            clipChildren = false
            clipToPadding = false
        }
        val scroll = HorizontalScrollView(context).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(18))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipChildren = false
            clipToPadding = false
            if (endClearancePx > 0) setPadding(0, 0, endClearancePx, 0)
        }
        fun scrollChipIntoView(chip: View, smooth: Boolean) {
            val target = CompanionChromeErgonomics.horizontalScrollTarget(
                chipLeft = chip.left,
                chipRight = chip.right,
                viewportWidth = scroll.width,
                endClearancePx = endClearancePx,
                contentWidth = row.width,
                currentScrollX = scroll.scrollX,
            ) ?: return
            if (smooth) scroll.smoothScrollTo(target, 0) else scroll.scrollTo(target, 0)
        }
        val roles = listOf(
            CompanionRole.HERO to R.string.role_hero,
            CompanionRole.NOW_PLAYING to R.string.label_now,
            CompanionRole.PERF_HUD to R.string.label_perf,
            CompanionRole.PINNED_APP to R.string.label_pin,
        )
        var selectedChip: View? = null
        val chipTouchHeight = dp(CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP)
        roles.forEachIndexed { index, (role, labelRes) ->
            val selected = current == role
            val basePill = if (selected) {
                TileBackgrounds.chipSelected(context, settings.accentColor, cornerRadiusDp = 17)
            } else {
                TileBackgrounds.chipIdle(context, cornerRadiusDp = 17)
            }
            val tv = TextView(context).apply {
                setText(labelRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    if (selected) ThemePack.onFillTextColor(settings.accentColor)
                    else UiDimens.ALPHA_PRIMARY,
                )
                background = buildInteractivePillBackground(
                    context = context,
                    base = basePill,
                    accent = settings.accentColor,
                    cornerRadiusDp = 17,
                )
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP), dp(12), dp(CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP))
                setSingleLine()
                isSelected = selected
                isClickable = true
                isFocusable = true
                setOnClickListener { onPick(role) }
            }
            attachChipTactileFeedback(tv) { scrollChipIntoView(tv, smooth = true) }
            if (selected) selectedChip = tv
            row.addView(
                tv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    chipTouchHeight,
                ).apply {
                    if (index < roles.lastIndex) marginEnd = dp(6)
                },
            )
        }
        scroll.addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (endClearanceDp > 0) Gravity.START else Gravity.CENTER_HORIZONTAL,
            ),
        )
        selectedChip?.let { active ->
            scroll.post { scrollChipIntoView(active, smooth = false) }
        }
        return scroll
    }

    private fun buildPerfHud(
        context: Context,
        settings: Settings,
        dp: (Int) -> Int,
    ): View {
        val surface = TileBackgrounds.surfaceColor(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(12))
            tag = TAG_PERF_HUD_ROOT
        }
        col.addView(TextView(context).apply {
            setText(R.string.label_perf_hud)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.accentTextColor(settings.accentColor, surface))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        col.addView(TextView(context).apply {
            setText(R.string.deck_perf_live_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(UiDimens.ALPHA_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        })
        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                grid,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        col.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        fun ensureRows() {
            // Build static 2-column metric cards once; ticks only mutate values & meters in place.
            if (grid.childCount > 0) return
            val readings = SystemInfoCollector.collect(context)
            val rows = SystemInfoFormat.rows(readings)
            val dotPx = dp(PerfHudMetrics.STATUS_DOT_DP)
            val meterHeightPx = dp(PerfHudMetrics.METER_HEIGHT_DP)
            rows.forEachIndexed { index, (label, value) ->
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = TileBackgrounds.card(context)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isBaselineAligned = false
                }
                headerRow.addView(
                    TextView(context).apply {
                        text = context.resolveText(label)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(UiDimens.ALPHA_MUTED)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        includeFontPadding = false
                    },
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
                headerRow.addView(
                    View(context).apply {
                        tag = TAG_PERF_DOT_PREFIX + index
                        visibility = View.GONE
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(PerfHudMetrics.COLOR_GOOD)
                        }
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(dotPx, dotPx).apply {
                        marginStart = dp(6)
                    },
                )
                card.addView(
                    headerRow,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                card.addView(TextView(context).apply {
                    tag = TAG_PERF_VALUE_PREFIX + index
                    text = context.resolveText(value)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(UiDimens.ALPHA_PRIMARY)
                    fontFeatureSettings = "tnum"
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) })
                card.addView(
                    MeterBarView(context).apply {
                        tag = TAG_PERF_BAR_PREFIX + index
                        visibility = View.GONE
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        meterHeightPx,
                    ).apply { topMargin = dp(7) },
                )
                val span = CompanionHeroMetrics.perfCardColumnSpan(index, rows.size)
                grid.addView(
                    card,
                    GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = if (span == 2) {
                            GridLayout.spec(0, 2, 1f)
                        } else {
                            GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        }
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    },
                )
            }
        }
        fun paintValues() {
            ensureRows()
            val readings = SystemInfoCollector.collect(context)
            val sunkenTrack = TileBackgrounds.surfaceSunken(context)
            val hairline = TileBackgrounds.hairlineDivider(context)
            SystemInfoFormat.rows(readings).forEachIndexed { index, (label, value) ->
                val tv = col.findViewWithTag<TextView>(TAG_PERF_VALUE_PREFIX + index)
                    ?: return@forEachIndexed
                val next = context.resolveText(value)
                if (tv.text?.toString() != next) tv.text = next
                val meter = PerfHudMetrics.meterForRow(label, readings)
                val nextTextColor = PerfHudMetrics.valueTextColor(meter?.severity)
                if (tv.currentTextColor != nextTextColor) tv.setTextColor(nextTextColor)
                val bar = col.findViewWithTag<MeterBarView>(TAG_PERF_BAR_PREFIX + index)
                val dot = col.findViewWithTag<View>(TAG_PERF_DOT_PREFIX + index)
                if (meter != null) {
                    val statusColor = PerfHudMetrics.statusColor(meter.severity)
                    bar?.let {
                        it.setProgress(
                            fraction = meter.fraction,
                            fillColor = statusColor,
                            trackColor = sunkenTrack,
                            borderColor = hairline,
                        )
                        if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
                    }
                    dot?.let {
                        ((it.background as? GradientDrawable)?.mutate() as? GradientDrawable)
                            ?.setColor(statusColor)
                        if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
                    }
                } else {
                    if (bar != null && bar.visibility != View.GONE) bar.visibility = View.GONE
                    if (dot != null && dot.visibility != View.GONE) dot.visibility = View.GONE
                }
            }
        }
        paintValues()
        // Live refresh without SETTINGS / setContentView thrash.
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (!col.isAttachedToWindow) return
                paintValues()
                handler.postDelayed(this, DualPaintPolicy.PERF_HUD_REFRESH_MS)
            }
        }
        col.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                handler.removeCallbacks(tick)
                handler.postDelayed(tick, DualPaintPolicy.PERF_HUD_REFRESH_MS)
            }

            override fun onViewDetachedFromWindow(v: View) {
                handler.removeCallbacks(tick)
            }
        })
        if (col.isAttachedToWindow) {
            handler.postDelayed(tick, DualPaintPolicy.PERF_HUD_REFRESH_MS)
        }
        return col
    }

    private fun buildPinnedAppPanel(
        activity: AppCompatActivity,
        settings: Settings,
        pinPkg: String?,
        installed: Boolean,
        dp: (Int) -> Int,
    ): View {
        val surface = TileBackgrounds.surfaceColor(activity)
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        col.addView(TextView(activity).apply {
            setText(R.string.label_pinned_app)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.accentTextColor(settings.accentColor, surface))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        })
        col.addView(TextView(activity).apply {
            setText(R.string.deck_pin_cta_subtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x88FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        })
        val app = activity.application as GhostGalleonApp
        val honesty = CompanionRoleResolve.pinHonesty(
            CompanionRoleResolve.Context(
                preferred = CompanionRole.PINNED_APP,
                openSessionKey = app.openSession?.key,
                pinnedPackage = pinPkg,
                sessionPolicy = app.sessionSurface?.policy,
                pinnedPackageInstalled = installed,
                sessionGreedy = app.sessionSurface?.greedy == true,
            ),
        )
        when (honesty) {
            CompanionRoleResolve.PinHonesty.EMPTY -> {
                col.addView(TextView(activity).apply {
                    setText(R.string.deck_set_pin_help)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor(0x99FFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(12))
                })
                col.addView(pinActionChip(activity, settings, dp, R.string.action_choose_pin) {
                    showCompanionPinPicker(activity)
                })
            }
            CompanionRoleResolve.PinHonesty.MISSING -> {
                col.addView(TextView(activity).apply {
                    setText(R.string.deck_pinned_app_missing)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor(0x99FFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(8))
                })
                col.addView(TextView(activity).apply {
                    text = pinPkg.orEmpty()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF.toInt())
                    gravity = Gravity.CENTER
                })
                col.addView(pinActionChip(activity, settings, dp, R.string.action_choose_pin) {
                    showCompanionPinPicker(activity)
                })
            }
            CompanionRoleResolve.PinHonesty.DUAL_CLAIM -> {
                col.addView(TextView(activity).apply {
                    setText(R.string.deck_pin_dual_claim)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(0x99FFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, 0)
                })
            }
            CompanionRoleResolve.PinHonesty.READY -> {
                val label = runCatching {
                    val pm = activity.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pinPkg!!, 0)).toString()
                }.getOrDefault(pinPkg!!)
                col.addView(TextView(activity).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(16))
                })
                val embedHost = FrameLayout(activity)
                val pinAllowed = !DualPaintPolicy.sessionOwnsCompanionDisplay(
                    app.sessionSurface?.policy,
                    app.sessionSurface?.greedy == true,
                )
                val embedded = pinAllowed && ActivityEmbed.available() &&
                    ActivityEmbed.attach(embedHost, activity, pinPkg)
                if (embedded) {
                    col.addView(
                        embedHost,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
                        ).apply { topMargin = dp(8) },
                    )
                } else {
                    if (pinAllowed && ActivityEmbed.available()) {
                        col.addView(TextView(activity).apply {
                            setText(R.string.deck_embed_unavailable)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setTextColor(0x88FFFFFF.toInt())
                            gravity = Gravity.CENTER
                            setPadding(0, 0, 0, dp(8))
                        })
                    }
                    if (pinAllowed) {
                        col.addView(pinActionChip(activity, settings, dp, R.string.action_launch_pin) {
                            val intent = activity.packageManager.getLaunchIntentForPackage(pinPkg)
                                ?: return@pinActionChip
                            val displayId = activity.currentDisplayId() ?: 0
                            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                            runCatching {
                                activity.startActivity(intent, options.toBundle())
                            }
                        })
                    }
                }
                col.addView(pinActionChip(activity, settings, dp, R.string.action_change_pin) {
                    ActivityEmbed.release(embedHost)
                    showCompanionPinPicker(activity)
                })
            }
        }
        return col
    }

    private fun pinActionChip(
        activity: AppCompatActivity,
        settings: Settings,
        dp: (Int) -> Int,
        labelRes: Int,
        onClick: () -> Unit,
    ): TextView = TextView(activity).apply {
        setText(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(ThemePack.onFillTextColor(settings.accentColor))
        background = TileBackgrounds.accentPill(activity, settings.accentColor)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) }
        layoutParams = lp
    }

    private fun showCompanionPinPicker(activity: AppCompatActivity) {
        val app = activity.application as GhostGalleonApp
        val apps = app.appLibrary().visible(app.settings)
            .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(activity, R.string.settings_no_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.settings_pinned_companion,
            items = labels,
            neutralRes = R.string.action_clear,
            onNeutral = {
                app.updateSettings(app.settings.copy(companionPinnedPackage = null))
            },
        ) { which ->
            val pkg = apps[which].packageName
            app.updateSettings(app.settings.copy(companionPinnedPackage = pkg))
        }
    }

    private fun applySessionTimerPillStyle(
        tv: TextView,
        isActive: Boolean,
        compact: Boolean,
        dp: (Int) -> Int,
    ) {
        val context = tv.context
        val sunken = TileBackgrounds.surfaceSunken(context)
        val dotSize = dp(PlayHudMetrics.SESSION_DOT_DP)
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PlayHudMetrics.sessionStatusColor(isActive))
            setBounds(0, 0, dotSize, dotSize)
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 15f)
        tv.setTextColor(PlayHudMetrics.sessionTimerTextColor(isActive, sunken))
        tv.fontFeatureSettings = "tnum"
        tv.includeFontPadding = false
        tv.gravity = Gravity.CENTER
        tv.setCompoundDrawablesRelative(dot, null, null, null)
        tv.compoundDrawablePadding = dp(7)
        tv.background = GradientDrawable().apply {
            setColor(sunken)
            cornerRadius = context.dpF(16)
            setStroke(context.strokePx(1), PlayHudMetrics.sessionPillBorderColor(isActive))
        }
        tv.setPadding(dp(12), dp(5), dp(12), dp(5))
    }

    private fun buildFramedSessionArt(
        activity: AppCompatActivity,
        app: GhostGalleonApp,
        roms: List<RomEntry>,
        settings: Settings,
        slotKey: String,
        fallbackPackage: String?,
        artPx: Int,
        viewTag: Any? = null,
    ): View {
        val rom = selectedRom(slotKey, roms, app)
        if (rom != null) {
            val tile = ArtTile.view(
                activity,
                app.artCache,
                rom,
                targetPx = artPx,
                cornerRadiusDp = PlayHudMetrics.ART_CORNER_RADIUS_DP,
                artOverrides = settings.artOverrides,
            )
            tile.tag = viewTag
            applyHeroArtFrame(
                target = tile,
                accentColor = PlatformLook.accentColor(rom.platformId),
                elevationDp = 6f,
                cornerRadiusDp = PlayHudMetrics.ART_CORNER_RADIUS_DP,
            )
            return tile
        }
        val pkg = fallbackPackage?.ifBlank { slotKey } ?: slotKey
        val inset = (artPx * 0.14f).toInt().coerceAtLeast(1)
        val pedestal = FrameLayout(activity).apply {
            tag = viewTag
            setPadding(inset, inset, inset, inset)
        }
        applyAppPedestalFrame(
            pedestal = pedestal,
            accentColor = glowColor(activity, pkg, settings),
            artSizePx = artPx,
            elevationDp = 6f,
        )
        val icon = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        CustomIcon.bind(
            icon,
            iconLoader(activity),
            app.artCache,
            settings,
            pkg,
            artPx,
        )
        pedestal.addView(
            icon,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return pedestal
    }

    private fun buildNowPlayingCard(
        activity: AppCompatActivity,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        session: com.visorcraft.ghostgalleon.library.OpenSession,
        dp: (Int) -> Int,
        compact: Boolean,
    ): View {
        val app = activity.application as GhostGalleonApp
        val surface = TileBackgrounds.surfaceColor(activity)
        val nowPlaying = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = TileBackgrounds.card(activity)
            clipChildren = false
            clipToPadding = false
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }
        val label = when {
            SlotKey.isRom(session.key) ->
                selectedRom(session.key, roms, app)
                    ?.let { RomNames.display(it, settings.romNames) }
                    ?: session.key
            else -> library.byPackage(settings)[session.key]?.label
                ?: session.key
        }
        val elapsed = SessionTracker.activeElapsedMs(session, System.currentTimeMillis())
        val artPx = dp(PlayHudMetrics.nowPlayingArtDp(compact))
        val artView = buildFramedSessionArt(
            activity = activity,
            app = app,
            roms = roms,
            settings = settings,
            slotKey = session.key,
            fallbackPackage = session.key,
            artPx = artPx,
        )
        val eyebrow = TextView(activity).apply {
            setText(R.string.label_now_playing)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.accentTextColor(settings.accentColor, surface))
            letterSpacing = 0.12f
            gravity = if (compact) Gravity.START else Gravity.CENTER
            includeFontPadding = false
        }
        val titleTv = TextView(activity).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 18f else 26f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = if (compact) Gravity.START else Gravity.CENTER
            maxLines = if (compact) 1 else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        val timerTv = TextView(activity).apply {
            text = activity.getString(
                if (session.isActive) R.string.format_session else R.string.format_session_paused,
                activity.resolveText(SessionMath.formatPlaytime(elapsed)),
            )
            applySessionTimerPillStyle(this, session.isActive, compact, dp)
        }
        if (compact) {
            val headerRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }
            headerRow.addView(
                artView,
                LinearLayout.LayoutParams(artPx, artPx).apply { marginEnd = dp(12) },
            )
            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
            }
            textCol.addView(eyebrow)
            textCol.addView(
                titleTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(2) },
            )
            textCol.addView(
                timerTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
            headerRow.addView(
                textCol,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            nowPlaying.addView(
                headerRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            nowPlaying.addView(eyebrow)
            nowPlaying.addView(
                artView,
                LinearLayout.LayoutParams(artPx, artPx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(10)
                    bottomMargin = dp(10)
                },
            )
            nowPlaying.addView(
                titleTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            nowPlaying.addView(
                timerTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(8)
                },
            )
        }
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(if (compact) 4 else 8), 0, 0)
        }
        val chipTouchH = dp(CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP)
        val insetV = dp(CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP)
        val swapBtn = TextView(activity).apply {
            setText(R.string.action_swap)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.onFillTextColor(settings.accentColor))
            background = buildInteractivePillBackground(
                context = activity,
                base = TileBackgrounds.accentPill(activity, settings.accentColor, cornerRadiusDp = 18),
                accent = settings.accentColor,
                cornerRadiusDp = 18,
            )
            gravity = Gravity.CENTER
            setPadding(dp(16), insetV, dp(16), insetV)
            setSingleLine()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                (activity.application as? com.visorcraft.ghostgalleon.GhostGalleonApp)
                    ?.swapInteractiveDisplay()
            }
        }
        attachChipTactileFeedback(swapBtn)
        actions.addView(
            swapBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipTouchH),
        )
        actions.addView(View(activity), LinearLayout.LayoutParams(dp(10), 1))
        val endBtn = TextView(activity).apply {
            setText(R.string.action_end_session)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            background = buildInteractivePillBackground(
                context = activity,
                base = TileBackgrounds.chipIdle(activity, cornerRadiusDp = 18),
                accent = settings.accentColor,
                cornerRadiusDp = 18,
            )
            gravity = Gravity.CENTER
            setPadding(dp(16), insetV, dp(16), insetV)
            setSingleLine()
            isClickable = true
            isFocusable = true
            setOnClickListener { app.clearOpenSession() }
        }
        attachChipTactileFeedback(endBtn)
        actions.addView(
            endBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipTouchH),
        )
        nowPlaying.addView(actions)
        return nowPlaying
    }

    private fun buildPlayHud(
        activity: AppCompatActivity,
        library: AppLibrary,
        roms: List<RomEntry>,
        settings: Settings,
        session: com.visorcraft.ghostgalleon.library.OpenSession,
        surface: SessionSurface,
        dp: (Int) -> Int,
        compact: Boolean,
    ): View {
        val app = activity.application as GhostGalleonApp
        val cardSurface = TileBackgrounds.surfaceColor(activity)
        val hdr = PlayHudMetrics.headerSpec(compact)
        val hud = LinearLayout(activity).apply {
            tag = TAG_PLAY_HUD
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = TileBackgrounds.card(activity)
            clipChildren = false
            clipToPadding = false
            setPadding(
                dp(hdr.padHorizontalDp),
                dp(hdr.padVerticalDp),
                dp(hdr.padHorizontalDp),
                dp(hdr.padVerticalDp),
            )
        }
        hud.addView(TextView(activity).apply {
            setText(R.string.label_now_playing)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.accentTextColor(settings.accentColor, cardSurface))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            includeFontPadding = false
        })
        val artPx = dp(hdr.artDp)
        hud.addView(
            buildFramedSessionArt(
                activity = activity,
                app = app,
                roms = roms,
                settings = settings,
                slotKey = surface.key,
                fallbackPackage = surface.packageName,
                artPx = artPx,
                viewTag = TAG_PLAY_HUD_ART,
            ),
            LinearLayout.LayoutParams(artPx, artPx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
                bottomMargin = dp(8)
            },
        )
        val label = resumeLabel(surface.key, library, roms, settings, app)
        hud.addView(TextView(activity).apply {
            tag = TAG_PLAY_HUD_TITLE
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, hdr.titleSp)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = if (compact) 1 else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        })
        val playerName = Platforms.ALL.flatMap { it.players }
            .firstOrNull { it.id == surface.playerId }
            ?.displayName
            .orEmpty()
        hud.addView(TextView(activity).apply {
            text = playerName
            visibility = if (playerName.isEmpty()) View.GONE else View.VISIBLE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, hdr.playerSp)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) })
        val elapsed = SessionTracker.activeElapsedMs(session, System.currentTimeMillis())
        hud.addView(
            TextView(activity).apply {
                tag = TAG_PLAY_HUD_CLOCK
                text = activity.getString(
                    if (session.isActive) R.string.format_session else R.string.format_session_paused,
                    activity.resolveText(SessionMath.formatPlaytime(elapsed)),
                )
                applySessionTimerPillStyle(this, session.isActive, compact, dp)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6)
            },
        )
        hud.addView(TextView(activity).apply {
            tag = TAG_PLAY_HUD_OWNER
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
            setTextColor(UiDimens.ALPHA_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        val postureInsetV = dp(CompanionChromeErgonomics.CHIP_TOUCH_INSET_V_DP)
        val postureBtn = TextView(activity).apply {
            tag = TAG_PLAY_HUD_POSTURE
            setText(R.string.posture_use_both_screens)
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemePack.onFillTextColor(settings.accentColor))
            background = buildInteractivePillBackground(
                context = activity,
                base = TileBackgrounds.accentPill(activity, settings.accentColor, cornerRadiusDp = 18),
                accent = settings.accentColor,
                cornerRadiusDp = 18,
            )
            setPadding(dp(16), postureInsetV, dp(16), postureInsetV)
            gravity = Gravity.CENTER
            setSingleLine()
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmPostureYield(activity, app) }
        }
        attachChipTactileFeedback(postureBtn)
        hud.addView(
            postureBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(CompanionChromeErgonomics.MIN_TOUCH_TARGET_DP),
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            },
        )
        // Opt-in RAM lens under the clock. Tick controls visibility; GONE until match.
        hud.addView(TextView(activity).apply {
            tag = TAG_PLAY_HUD_LENS
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            maxLines = 8
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        hud.addView(GridLayout(activity).apply {
            tag = TAG_PLAY_HUD_TRACKER
            visibility = View.GONE
            columnCount = 8
            contentDescription = activity.getString(R.string.play_hud_tracker)
            setPadding(0, dp(6), 0, 0)
        })
        hud.addView(
            HorizontalScrollView(activity).apply {
                tag = TAG_PLAY_HUD_CINEMA
                visibility = View.GONE
                contentDescription = activity.getString(R.string.play_hud_cinema)
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(6), 0, 0)
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        isBaselineAligned = false
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            },
        )
        hud.addView(
            LinearLayout(activity).apply {
                tag = TAG_PLAY_HUD_THEATER
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
                contentDescription = activity.getString(R.string.settings_ra_theater)
                setPadding(0, dp(6), 0, 0)
                val badgeSize = dp(if (compact) 28 else 32)
                addView(
                    FrameLayout(activity).apply {
                        background = TileBackgrounds.chip(activity)
                        addView(
                            ImageView(activity).apply {
                                tag = TAG_PLAY_HUD_THEATER_BADGE
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                visibility = View.GONE
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        addView(
                            TextView(activity).apply {
                                tag = TAG_PLAY_HUD_THEATER_LETTER
                                gravity = Gravity.CENTER
                                setTextColor(
                                    ThemePack.accentTextColor(
                                        settings.accentColor,
                                        TileBackgrounds.chipIdleColor(activity),
                                    ),
                                )
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setTypeface(typeface, Typeface.BOLD)
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    },
                    LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                        marginEnd = dp(8)
                    },
                )
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            TextView(activity).apply {
                                tag = TAG_PLAY_HUD_THEATER_PROGRESS
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
                                setTextColor(0xCCFFFFFF.toInt())
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            },
                        )
                        addView(
                            TextView(activity).apply {
                                tag = TAG_PLAY_HUD_THEATER_NEXT
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
                                setTextColor(0xBBFFFFFF.toInt())
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            },
                        )
                        addView(
                            TextView(activity).apply {
                                tag = TAG_PLAY_HUD_THEATER_TICKER
                                visibility = View.GONE
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
                                setTextColor(
                                    ThemePack.accentTextColor(settings.accentColor, cardSurface),
                                )
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            },
                        )
                    },
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            },
        )
        // buildPlayHud is only called when playHostAllowed is true.
        val cockpit = CockpitPolicy.cockpitAllowed(
            playHostAllowed = true,
            playerId = surface.playerId,
            cockpitEnabled = settings.winlatorCockpit,
        )
        // Pad/mouse inject needs assist + policy + display-targeted gestures.
        val mayPointer = InputAssistPolicy.mayInjectPointer(
            assistConnected = app.inputAssistConnected,
            playHostAllowed = true,
            sessionOwnsCompanion = DualPaintPolicy.sessionOwnsCompanionDisplay(
                surface.policy,
                surface.greedy,
            ),
            playerId = surface.playerId,
        ) && InputAssistService.supportsDisplayGesture()
        // Default center of launch display until the pad is touched.
        val padPoint = floatArrayOf(0.5f, 0.5f)
        fun tapLaunchPad() {
            if (!mayPointer) return
            app.injectLaunchPointer(padPoint[0], padPoint[1], true)
            app.injectLaunchPointer(padPoint[0], padPoint[1], false)
        }
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isBaselineAligned = false
        }
        fun actionChip(
            labelRes: Int? = null,
            label: CharSequence? = null,
            chipTag: Any? = null,
            filled: Boolean = false,
            visibility: Int = View.VISIBLE,
            enabled: Boolean = true,
            onClick: (() -> Unit)? = null,
        ): TextView = TextView(activity).apply {
            tag = chipTag
            if (labelRes != null) setText(labelRes) else text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, if (filled) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(
                if (filled) ThemePack.onFillTextColor(settings.accentColor)
                else UiDimens.ALPHA_PRIMARY,
            )
            background = if (filled) {
                TileBackgrounds.accentPill(activity, settings.accentColor)
            } else {
                TileBackgrounds.chipIdle(activity, cornerRadiusDp = 18)
            }
            gravity = Gravity.CENTER
            setSingleLine()
            setPadding(dp(14), dp(8), dp(14), dp(8))
            this.visibility = visibility
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            if (onClick != null && enabled) setOnClickListener { onClick() }
        }
        fun addChip(chip: View) {
            if (actions.childCount > 0 && chip.visibility != View.GONE) {
                actions.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
            }
            actions.addView(chip)
        }
        fun chipScroll(tagActions: Boolean): HorizontalScrollView =
            HorizontalScrollView(activity).apply {
                if (tagActions) tag = TAG_PLAY_HUD_ACTIONS
                isFillViewport = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                setPadding(0, dp(if (compact) 8 else 12), 0, 0)
                if (tagActions) {
                    visibility = if (app.playHudExpanded) View.VISIBLE else View.GONE
                }
                addView(
                    actions,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            }
        if (cockpit) {
            val imeHost = EditText(activity).apply {
                isSingleLine = true
                setTextColor(Color.TRANSPARENT)
                setBackgroundColor(Color.TRANSPARENT)
                setTextIsSelectable(false)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isFocusable = true
                isFocusableInTouchMode = true
                onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        app.releaseHost()
                        app.liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
                    }
                }
            }
            addChip(actionChip(labelRes = R.string.cockpit_keyboard, filled = true) {
                app.claimHost()
                app.liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
                imeHost.post {
                    imeHost.requestFocus()
                    activity.getSystemService(InputMethodManager::class.java)
                        ?.showSoftInput(imeHost, InputMethodManager.SHOW_IMPLICIT)
                }
            })
            // Touch taps on the launch display (assist); not true mouse buttons.
            addChip(actionChip(label = "LMB", enabled = mayPointer) { tapLaunchPad() })
            addChip(actionChip(label = "RMB", enabled = mayPointer) { tapLaunchPad() })
            addChip(actionChip(label = "MMB", enabled = mayPointer) { tapLaunchPad() })
            addChip(
                actionChip(
                    labelRes = R.string.play_hud_switcher,
                    chipTag = TAG_PLAY_HUD_SWITCHER,
                ) {
                    openSessionSwitcher(activity)
                },
            )
            val collapsible = LinearLayout(activity).apply {
                tag = TAG_PLAY_HUD_ACTIONS
                orientation = LinearLayout.VERTICAL
                visibility = if (app.playHudExpanded) View.VISIBLE else View.GONE
            }
            collapsible.addView(
                chipScroll(tagActions = false),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (!mayPointer) {
                collapsible.addView(
                    TextView(activity).apply {
                        setText(R.string.cockpit_need_assist)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
                        setTextColor(0xBBFFFFFF.toInt())
                        gravity = Gravity.CENTER
                        setPadding(0, dp(8), 0, dp(4))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val trackpad = View(activity).apply {
                tag = TAG_PLAY_HUD_TRACKPAD
                setTag(R.id.cockpit_pad_point, padPoint)
                minimumHeight = dp(if (compact) 96 else 160)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0x22FFFFFF)
                    setStroke(dp(1), 0x44FFFFFF)
                }
                setOnTouchListener { v, event ->
                    val w = v.width.coerceAtLeast(1).toFloat()
                    val h = v.height.coerceAtLeast(1).toFloat()
                    padPoint[0] = (event.x / w).coerceIn(0f, 1f)
                    padPoint[1] = (event.y / h).coerceIn(0f, 1f)
                    if (mayPointer) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE,
                            -> app.injectLaunchPointer(padPoint[0], padPoint[1], true)
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            -> app.injectLaunchPointer(padPoint[0], padPoint[1], false)
                        }
                    }
                    true
                }
            }
            collapsible.addView(
                trackpad,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ).apply { topMargin = dp(8) },
            )
            val cockpitRoot = LinearLayout(activity).apply {
                tag = TAG_PLAY_HUD_COCKPIT
                orientation = LinearLayout.VERTICAL
            }
            cockpitRoot.addView(
                collapsible,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
            cockpitRoot.addView(imeHost, LinearLayout.LayoutParams(1, 1))
            hud.addView(
                cockpitRoot,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        } else {
            addChip(actionChip(labelRes = R.string.action_swap, filled = true) {
                app.swapInteractiveDisplay()
            })
            addChip(actionChip(labelRes = R.string.play_hud_end) {
                app.clearOpenSession()
            })
            addChip(actionChip(labelRes = R.string.play_hud_reclaim) {
                app.noteReturnToLauncher()
                hud.visibility = View.GONE
                if (app.liveCompanion() == null) {
                    (activity as? MainActivity)?.restartCompanionPanel("return-from-keep-hud")
                }
            })
            addChip(
                actionChip(
                    labelRes = if (surface.key in settings.favorites) R.string.action_unfavorite
                    else R.string.action_favorite,
                ) {
                    EntryActions.toggleFavorite(activity, surface.key)
                },
            )
            addChip(actionChip(labelRes = R.string.action_open_with) {
                val rom = selectedRom(surface.key, roms, app) ?: return@actionChip
                EntryActions.openWith(activity, rom) { playerId ->
                    launchSlotKey(
                        activity, app.deckState, roms, surface.key, playerId = playerId,
                    )
                }
            })
            var slotStrip: View? = null
            if (raHudEligible(settings.raNetworkCommands, surface)) {
                val raChips = LinearLayout(activity).apply {
                    tag = TAG_PLAY_HUD_RA
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    isBaselineAligned = false
                    visibility = View.GONE
                    if (actions.childCount > 0) setPadding(dp(12), 0, 0, 0)
                }
                fun addRaChip(chip: View) {
                    if (raChips.childCount > 0) {
                        raChips.addView(View(activity), LinearLayout.LayoutParams(dp(12), 1))
                    }
                    raChips.addView(chip)
                }
                fun runRa(command: (RaCommandClient, Int) -> Unit) {
                    enqueueRaChipWork(hud, app, probe = false) { client, port ->
                        command(client, port)
                    }
                }
                var slotSaveMode = false
                val builtSlots = buildRaSlotStrip(
                    activity, settings, surface, dp, compact,
                ) { slot ->
                    val save = slotSaveMode
                    if (slot in RaStateSlots.SLOTS) app.noteUserSlot(slot)
                    enqueueRaChipWork(hud, app, probe = false) { client, port ->
                        val ok = if (save) client.saveStateSlot(port, slot)
                        else client.loadStateSlot(port, slot)
                        if (!ok) {
                            if (save) client.saveState(port) else client.loadState(port)
                        }
                    }
                }
                slotStrip = builtSlots
                fun showOrRunSlots(save: Boolean) {
                    val client = app.ensureRaCommandClient()
                    if (!client.slotStripAllowed()) {
                        enqueueRaChipWork(hud, app, probe = false) { c, port ->
                            if (save) c.saveState(port) else c.loadState(port)
                        }
                        return
                    }
                    if (builtSlots.visibility == View.VISIBLE && slotSaveMode == save) {
                        builtSlots.visibility = View.GONE
                        return
                    }
                    slotSaveMode = save
                    builtSlots.visibility = View.VISIBLE
                }
                addRaChip(
                    actionChip(
                        labelRes = R.string.play_hud_pause,
                        chipTag = TAG_PLAY_HUD_PAUSE,
                    ) {
                        runRa { client, port -> client.pauseToggle(port) }
                    },
                )
                addRaChip(
                    actionChip(labelRes = R.string.play_hud_save) {
                        showOrRunSlots(save = true)
                    },
                )
                addRaChip(
                    actionChip(labelRes = R.string.play_hud_load) {
                        showOrRunSlots(save = false)
                    },
                )
                actions.addView(raChips)
                hud.post {
                    if (!hud.isAttachedToWindow) return@post
                    enqueueRaChipWork(hud, app, probe = true)
                }
            }
            addChip(
                actionChip(
                    labelRes = R.string.play_hud_switcher,
                    chipTag = TAG_PLAY_HUD_SWITCHER,
                ) {
                    openSessionSwitcher(activity)
                },
            )
            val seatChrome = seatChromeAllowed(app)
            if (app.hostSurface == HostSurface.SEAT && !seatChrome) {
                app.hostSurface = HostSurface.HUD
            }
            if (seatChrome) {
                addChip(
                    actionChip(
                        labelRes = R.string.play_hud_seat,
                        chipTag = TAG_PLAY_HUD_SEAT_CHIP,
                    ) {
                        setSeatActive(app, true)
                    },
                )
            }
            val helperKind = helperChipKind(app)
            if (app.hostSurface == HostSurface.HELPER && helperKind != HelperChipKind.ENABLED) {
                releaseHelperEmbed(hud)
                app.hostSurface = HostSurface.HUD
            }
            if (helperKind != HelperChipKind.HIDDEN) {
                val helperEnabled = helperKind == HelperChipKind.ENABLED
                addChip(
                    actionChip(
                        labelRes = if (helperEnabled) {
                            R.string.play_hud_helper
                        } else {
                            R.string.helper_embed_unavailable
                        },
                        chipTag = TAG_PLAY_HUD_HELPER_CHIP,
                        enabled = helperEnabled,
                    ) {
                        setHelperActive(app, true)
                    },
                )
            }
            hud.addView(chipScroll(tagActions = true))
            slotStrip?.let { strip ->
                if (!app.playHudExpanded) strip.visibility = View.GONE
                hud.addView(strip)
            }
            if (seatChrome) {
                hud.addView(
                    buildSeatBody(activity, app, settings, dp, compact),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            }
            if (helperKind == HelperChipKind.ENABLED ||
                app.hostSurface == HostSurface.HELPER
            ) {
                hud.addView(
                    buildHelperBody(activity, app, settings, dp),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            }
            applySeatChrome(hud, app)
            applyHelperChrome(hud, app)
            if (app.hostSurface == HostSurface.HELPER &&
                !attachHelper(hud, activity, app)
            ) {
                Toast.makeText(
                    activity,
                    R.string.helper_embed_unavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        applyPostureChip(hud, app)
        return hud
    }

    private fun confirmPostureYield(activity: AppCompatActivity, app: GhostGalleonApp) {
        val builder = DeckDialogs.builder(activity)
            .setTitle(R.string.settings_stage_plot)
            .setMessage(R.string.confirm_yield_on_keep_player)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                app.dismissPostureYieldChip()
                writePostureYieldPlot(app)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                app.dismissPostureYieldChip()
            }
            .setOnCancelListener { app.dismissPostureYieldChip() }
        DeckDialogs.show(builder, activity)
    }

    /** Stage-plot / package yield only. Never beginSession or assign policy. */
    private fun writePostureYieldPlot(app: GhostGalleonApp) {
        val surface = app.sessionSurface ?: return
        val live = app.settings
        val romId = SlotKey.romId(surface.key)
        if (romId != null) {
            app.updateSettings(
                live.copy(
                    stagePlots = live.stagePlots +
                        (romId to StagePlot(SessionPolicy.YIELD_BOTH, LaunchFace.AUTO)),
                ),
            )
            return
        }
        val pkg = surface.packageName
        if (pkg.isNotBlank()) {
            app.updateSettings(live.copy(packageYield = live.packageYield + (pkg to true)))
        }
    }

    fun tickPlayHudRa(root: View?, app: GhostGalleonApp, activity: Context) {
        if (!app.settings.raNetworkCommands) return
        val surface = app.sessionSurface
        val hostId = (activity as? AppCompatActivity)?.currentDisplayId()
        val allowed = surface != null &&
            raHudEligible(true, surface) &&
            PlayHostPolicy.playHostAllowed(
                dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
                policy = surface.policy,
                greedy = surface.greedy,
                hostDisplayId = hostId,
                launchDisplayId = surface.launchDisplayId,
            )
        if (!allowed) {
            // Non-RA sessions never built the chip — skip the tree walk.
            if (surface != null && !raHudEligible(true, surface)) return
            val group = root?.findViewWithTag<View>(TAG_PLAY_HUD_RA) ?: return
            if (group.visibility != View.GONE) group.visibility = View.GONE
            root.findViewWithTag<View>(TAG_PLAY_HUD_SLOTS)?.let {
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
            return
        }
        val client = app.raCommandClient
        if (client?.isLinkUp() == true) return
        if (client != null &&
            !client.probeDue(android.os.SystemClock.elapsedRealtime())
        ) {
            return
        }
        if (root == null) return
        enqueueRaChipWork(root, app, probe = true)
    }

    /**
     * READ_CORE_RAM lens tick. In-place [TextView.setText] / tracker alpha only.
     * Never starts when [DualPaintPolicy.sessionOwnsCompanionDisplay].
     * @return next interval ms when a lens is active, else null.
     */
    fun tickPlayHudLens(root: View?, app: GhostGalleonApp, activity: Context): Long? {
        val settings = app.settings
        if (!settings.ramLensesEnabled) {
            hidePlayHudTracker(root)
            return null
        }
        val lensView = root?.findViewWithTag<TextView>(TAG_PLAY_HUD_LENS)
        if (lensView == null) {
            hidePlayHudTracker(root)
            return null
        }
        if (HostSurfacePolicy.exclusive(app.hostSurface)) {
            lensView.visibility = View.GONE
            hidePlayHudTracker(root)
            return null
        }
        val surface = app.sessionSurface
        if (surface == null ||
            DualPaintPolicy.sessionOwnsCompanionDisplay(surface.policy, surface.greedy) ||
            !settings.raNetworkCommands
        ) {
            lensView.visibility = View.GONE
            hidePlayHudTracker(root)
            return null
        }
        val hostId = (activity as? AppCompatActivity)?.currentDisplayId()
        val playHost = PlayHostPolicy.playHostAllowed(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            policy = surface.policy,
            greedy = surface.greedy,
            hostDisplayId = hostId,
            launchDisplayId = surface.launchDisplayId,
        )
        if (!playHost ||
            !SessionHandoff.isRaPlayer(surface.playerId, surface.packageName)
        ) {
            lensView.visibility = View.GONE
            hidePlayHudTracker(root)
            return null
        }
        val rom = selectedRom(surface.key, emptyList(), app)
        val romId = rom?.id ?: SlotKey.romId(surface.key)
        val platformId = rom?.platformId
        val identityHash = app.romIdentities[romId]?.takeIf { it.ready }?.hash
        val match = LensCatalog.match(app.lenses, romId, hash = identityHash, platformId = platformId)
        if (match == null ||
            !LensCatalog.acceptable(match) ||
            match.id in app.lensDisabledThisProcess
        ) {
            lensView.visibility = View.GONE
            hidePlayHudTracker(root)
            return null
        }
        val paintTracker = trackerPaintAllowed(match, settings, app.hostSurface)
        if (paintTracker) {
            lensView.visibility = View.GONE
        } else {
            lensView.visibility = View.VISIBLE
            hidePlayHudTracker(root)
        }
        val interval = minOf(match.intervalMs.coerceAtLeast(1L), LENS_MAX_INTERVAL_MS)
        val port = settings.raNetworkCmdPort
        var snap: LensReadSnap? = null
        // Drop when a datagram is already in flight; never stack. Interval
        // still schedules the next attempt after one completed UDP.
        app.enqueueRaUdp(
            work = { client ->
                val blocks = ArrayList<ByteArray>(match.blocks.size)
                var ok = true
                for (block in match.blocks) {
                    val bytes = client.readCoreRam(port, block.address, block.length)
                    if (bytes == null) {
                        ok = false
                        break
                    }
                    blocks.add(bytes)
                }
                snap = LensReadSnap(
                    lensId = match.id,
                    ok = ok,
                    text = if (ok) formatLensText(match, blocks) else null,
                    blocks = if (ok) blocks else emptyList(),
                )
            },
            onMain = {
                if (!lensView.isAttachedToWindow) return@enqueueRaUdp
                val s = snap
                if (s == null || !s.ok || s.text == null) {
                    if (s != null && app.noteLensFailure(s.lensId)) {
                        lensView.visibility = View.GONE
                        hidePlayHudTracker(root)
                    }
                    return@enqueueRaUdp
                }
                // Drop stale replies if rematch changed the active lens.
                val still = LensCatalog.match(
                    app.lenses,
                    romId,
                    hash = app.romIdentities[romId]?.takeIf { it.ready }?.hash,
                    platformId = platformId,
                )
                if (still?.id != s.lensId) return@enqueueRaUdp
                app.noteLensSuccess(s.lensId)
                if (trackerPaintAllowed(still, app.settings, app.hostSurface)) {
                    lensView.visibility = View.GONE
                    paintPlayHudTracker(root, still, s.blocks)
                } else {
                    if (lensView.text?.toString() != s.text) {
                        lensView.text = s.text
                    }
                    if (lensView.visibility != View.VISIBLE) lensView.visibility = View.VISIBLE
                    hidePlayHudTracker(root)
                }
            },
        )
        return interval
    }

    internal fun hidePlayHudTracker(root: View?) {
        val grid = root?.findViewWithTag<View>(TAG_PLAY_HUD_TRACKER) ?: return
        if (grid.visibility != View.GONE) grid.visibility = View.GONE
    }

    /**
     * Auto-ring reserved savestate slots 9–12. In-place strip only.
     * Never starts when [DualPaintPolicy.sessionOwnsCompanionDisplay].
     * @return next interval ms when cinema is shown, else null.
     */
    fun tickPlayHudCinema(root: View?, app: GhostGalleonApp, activity: Context): Long? {
        val settings = app.settings
        if (!PlayHostPolicy.cinemaTickAllowed(
                settings.raCinemaEnabled,
                settings.raNetworkCommands,
            )
        ) {
            return null
        }
        val strip = root?.findViewWithTag<ViewGroup>(TAG_PLAY_HUD_CINEMA)
        if (strip == null) return null
        val surface = app.sessionSurface
        if (surface == null ||
            DualPaintPolicy.sessionOwnsCompanionDisplay(surface.policy, surface.greedy)
        ) {
            if (strip.visibility != View.GONE) strip.visibility = View.GONE
            return null
        }
        val playHost = cinemaPlayHostAllowed(app, activity, surface)
        val raPlayer = SessionHandoff.isRaPlayer(surface.playerId, surface.packageName)
        val slotsLive = app.raCommandClient?.slotStripAllowed() ?: true
        if (!playHost ||
            !raPlayer ||
            !slotsLive ||
            !HostSurfacePolicy.showsCinema(app.hostSurface)
        ) {
            hidePlayHudCinema(root)
            return null
        }
        paintPlayHudCinema(strip, app, activity, surface)
        val interval = CinemaPolicy.clampInterval(settings.raCinemaIntervalMs.toLong())
        val now = SystemClock.elapsedRealtime()
        val due = CinemaPolicy.shouldCapture(
            enabled = settings.raCinemaEnabled,
            playHostAllowed = playHost,
            raPlayer = raPlayer,
            slotsLive = slotsLive,
            lastCaptureMs = app.cinemaLastCaptureMs,
            nowMs = now,
            intervalMs = settings.raCinemaIntervalMs.toLong(),
        )
        if (!due) {
            val rem = interval - (now - app.cinemaLastCaptureMs)
            return rem.coerceIn(1_000L, interval)
        }
        val client = app.raCommandClient
        if (client == null || !client.isLinkUp()) {
            return 1_000L
        }
        val next = CinemaPolicy.nextSlot(app.cinemaLastSlot)
        if (!CinemaPolicy.inBand(next)) return interval
        val port = settings.raNetworkCmdPort
        val surfaceKey = surface.key
        var saved = false
        val enqueued = app.enqueueRaUdp(
            work = { c -> saved = c.saveStateSlot(port, next) },
            onMain = {
                if (!strip.isAttachedToWindow) return@enqueueRaUdp
                val live = app.sessionSurface
                if (live == null || live.key != surfaceKey) return@enqueueRaUdp
                if (DualPaintPolicy.sessionOwnsCompanionDisplay(live.policy, live.greedy) ||
                    !cinemaPlayHostAllowed(app, activity, live)
                ) {
                    hidePlayHudCinema(root)
                    return@enqueueRaUdp
                }
                if (!saved) {
                    hidePlayHudCinema(root)
                    app.cinemaLastCaptureMs = SystemClock.elapsedRealtime()
                    return@enqueueRaUdp
                }
                if (app.raCommandClient?.slotStripAllowed() == false) {
                    hidePlayHudCinema(root)
                    return@enqueueRaUdp
                }
                val stamp = SystemClock.elapsedRealtime()
                app.cinemaLastSlot = next
                app.cinemaLastCaptureMs = stamp
                val thumbName = cinemaThumbFile(raStatesDir(live), next)?.name
                app.cinemaFrames = (app.cinemaFrames.filter { it.slot != next } +
                    CinemaFrame(next, stamp, thumbName)).takeLast(4)
                paintPlayHudCinema(strip, app, activity, live)
            },
        )
        if (!enqueued) return 1_000L
        return interval
    }

    internal fun hidePlayHudCinema(root: View?) {
        val strip = root?.findViewWithTag<View>(TAG_PLAY_HUD_CINEMA) ?: return
        if (strip.visibility != View.GONE) strip.visibility = View.GONE
    }

    /**
     * KEEP achievement theater. HTTP poll only; Talk to RetroArch is not
     * required. Hide on yield / exclusive surfaces.
     * @return next delay ms when the block is shown, else null.
     */
    fun tickPlayHudTheater(root: View?, app: GhostGalleonApp, activity: Context): Long? {
        val settings = app.settings
        val creds = !settings.raUsername.isNullOrBlank() &&
            !settings.raApiKey.isNullOrBlank()
        if (!PlayHostPolicy.theaterTickAllowed(settings.raTheaterEnabled, creds)) {
            return null
        }
        val block = root?.findViewWithTag<ViewGroup>(TAG_PLAY_HUD_THEATER)
        if (block == null) return null
        val surface = app.sessionSurface
        if (surface == null ||
            DualPaintPolicy.sessionOwnsCompanionDisplay(surface.policy, surface.greedy)
        ) {
            if (block.visibility != View.GONE) block.visibility = View.GONE
            return null
        }
        val playHost = cinemaPlayHostAllowed(app, activity, surface)
        if (!playHost || !HostSurfacePolicy.showsTheater(app.hostSurface)) {
            hidePlayHudTheater(root)
            return null
        }
        val rom = selectedRom(surface.key, emptyList(), app)
        val romId = rom?.id ?: SlotKey.romId(surface.key)
        if (romId.isNullOrBlank()) {
            hidePlayHudTheater(root)
            return null
        }
        app.requestTheaterPoll(romId, rom?.name, rom?.platformId)
        val snap = app.theaterSnapFor(romId)
        if (snap == null) {
            hidePlayHudTheater(root)
            val last = if (app.theaterRomId == romId) app.theaterLastPollMs else 0L
            val interval = settings.raTheaterPollMs.toLong().coerceAtLeast(30_000L)
            val rem = interval - (SystemClock.elapsedRealtime() - last)
            return rem.coerceIn(1_000L, interval)
        }
        paintPlayHudTheater(block, app, activity, snap)
        return 1_000L
    }

    internal fun hidePlayHudTheater(root: View?) {
        val block = root?.findViewWithTag<View>(TAG_PLAY_HUD_THEATER) ?: return
        if (block.visibility != View.GONE) block.visibility = View.GONE
    }

    private fun paintPlayHudTheater(
        block: ViewGroup,
        app: GhostGalleonApp,
        activity: Context,
        snap: RaTheaterSnap,
    ) {
        val progressTv = block.findViewWithTag<TextView>(TAG_PLAY_HUD_THEATER_PROGRESS)
        val nextTv = block.findViewWithTag<TextView>(TAG_PLAY_HUD_THEATER_NEXT)
        val tickerTv = block.findViewWithTag<TextView>(TAG_PLAY_HUD_THEATER_TICKER)
        val badge = block.findViewWithTag<ImageView>(TAG_PLAY_HUD_THEATER_BADGE)
        val letter = block.findViewWithTag<TextView>(TAG_PLAY_HUD_THEATER_LETTER)
        val progressText = activity.resolveText(snap.progress.label)
        if (progressTv != null && progressTv.text?.toString() != progressText) {
            progressTv.text = progressText
        }
        val nextTitle = snap.nextLocked?.title.orEmpty()
        if (nextTv != null && nextTv.text?.toString() != nextTitle) {
            nextTv.text = nextTitle
        }
        if (nextTv != null) {
            val vis = if (nextTitle.isEmpty()) View.GONE else View.VISIBLE
            if (nextTv.visibility != vis) nextTv.visibility = vis
        }
        val now = SystemClock.elapsedRealtime()
        val tickerTitle = app.theaterTickerTitle
        val tickerOn = !tickerTitle.isNullOrBlank() && now < app.theaterTickerUntilMs
        if (tickerTv != null) {
            if (tickerOn) {
                val line = activity.getString(R.string.play_hud_theater, tickerTitle)
                if (tickerTv.text?.toString() != line) tickerTv.text = line
                if (tickerTv.visibility != View.VISIBLE) tickerTv.visibility = View.VISIBLE
            } else if (tickerTv.visibility != View.GONE) {
                tickerTv.visibility = View.GONE
            }
        }
        val cheevo: RaCheevo? = when {
            tickerOn -> app.theaterTickerId?.let { id ->
                snap.items.firstOrNull { it.id == id }
            } ?: snap.lastUnlock
            else -> snap.nextLocked ?: snap.lastUnlock
        }
        paintTheaterBadge(app, badge, letter, cheevo)
        if (block.visibility != View.VISIBLE) block.visibility = View.VISIBLE
    }

    private fun paintTheaterBadge(
        app: GhostGalleonApp,
        badge: ImageView?,
        letter: TextView?,
        cheevo: RaCheevo?,
    ) {
        if (badge == null || letter == null) return
        val title = cheevo?.title.orEmpty()
        val initial = title.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        if (letter.text?.toString() != initial) letter.text = initial
        val name = cheevo?.badgeName
        if (name.isNullOrBlank()) {
            ArtCache.dropDisplayed(badge)
            badge.setImageDrawable(null)
            badge.setTag(R.id.theater_badge_key, null)
            if (badge.visibility != View.GONE) badge.visibility = View.GONE
            val letterVis = if (initial.isEmpty()) View.GONE else View.VISIBLE
            if (letter.visibility != letterVis) letter.visibility = letterVis
            return
        }
        val key = app.theaterBadgeKey(name)
        val bound = badge.getTag(R.id.theater_badge_key) as? String
        if (bound == key && badge.drawable != null) {
            if (badge.visibility != View.VISIBLE) badge.visibility = View.VISIBLE
            if (letter.visibility != View.GONE) letter.visibility = View.GONE
            return
        }
        if (bound == "$key!") {
            val letterVis = if (initial.isEmpty()) View.GONE else View.VISIBLE
            if (badge.visibility != View.GONE) badge.visibility = View.GONE
            if (letter.visibility != letterVis) letter.visibility = letterVis
            return
        }
        if (!app.artCache.diskHas(key)) {
            if (badge.visibility != View.GONE) badge.visibility = View.GONE
            val letterVis = if (initial.isEmpty()) View.GONE else View.VISIBLE
            if (letter.visibility != letterVis) letter.visibility = letterVis
            return
        }
        val bytes = app.artCache.readDiskBytes(key)
        val bmp = if (bytes != null && bytes.isNotEmpty()) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            null
        }
        if (bmp != null) {
            badge.setTag(R.id.theater_badge_key, key)
            ArtCache.showDisplayed(badge, bmp)
            if (badge.visibility != View.VISIBLE) badge.visibility = View.VISIBLE
            if (letter.visibility != View.GONE) letter.visibility = View.GONE
        } else {
            ArtCache.dropDisplayed(badge)
            badge.setImageDrawable(null)
            badge.setTag(R.id.theater_badge_key, "$key!")
            if (badge.visibility != View.GONE) badge.visibility = View.GONE
            val letterVis = if (initial.isEmpty()) View.GONE else View.VISIBLE
            if (letter.visibility != letterVis) letter.visibility = letterVis
        }
    }

    private fun trackerPaintAllowed(
        spec: LensSpec,
        settings: Settings,
        hostSurface: HostSurface,
    ): Boolean =
        spec.surface == "tracker" &&
            settings.ramTrackersEnabled &&
            TrackerCatalog.acceptable(spec) &&
            HostSurfacePolicy.showsTracker(hostSurface) &&
            spec.widgets.any { it.kind != TrackerKind.LINE }

    private fun paintPlayHudTracker(root: View?, spec: LensSpec, blocks: List<ByteArray>) {
        val grid = root?.findViewWithTag<GridLayout>(TAG_PLAY_HUD_TRACKER) ?: return
        val cells = trackerCells(spec, blocks)
        if (cells.isEmpty()) {
            if (grid.visibility != View.GONE) grid.visibility = View.GONE
            return
        }
        val cols = spec.widgets.firstOrNull { it.cols > 0 }?.cols ?: 8
        if (grid.columnCount != cols) grid.columnCount = cols
        if (grid.childCount != cells.size) {
            grid.removeAllViews()
            val ctx = grid.context
            val pad = (4 * ctx.resources.displayMetrics.density).toInt()
            for (cell in cells) {
                grid.addView(
                    TextView(ctx).apply {
                        text = cellLabel(cell)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(0xCCFFFFFF.toInt())
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(pad, pad, pad, pad)
                    },
                )
            }
        } else {
            for (i in cells.indices) {
                val tv = grid.getChildAt(i) as? TextView ?: continue
                val label = cellLabel(cells[i])
                if (tv.text?.toString() != label) tv.text = label
            }
        }
        for (i in cells.indices) {
            val tv = grid.getChildAt(i) as? TextView ?: continue
            val cell = cells[i]
            val on = when (cell.kind) {
                TrackerKind.METER -> TrackerCatalog.meterValue(cell.bytes) > 0
                else -> TrackerCatalog.bitOn(cell.bytes, cell.bitIndex)
            }
            val nextAlpha = if (on) 1f else 0.25f
            if (tv.alpha != nextAlpha) tv.alpha = nextAlpha
        }
        if (grid.visibility != View.VISIBLE) grid.visibility = View.VISIBLE
    }

    private class TrackerCell(
        val label: String,
        val kind: TrackerKind,
        val bytes: ByteArray,
        val bitIndex: Int,
    )

    private fun cellLabel(cell: TrackerCell): String =
        if (cell.kind == TrackerKind.METER && cell.label.isEmpty()) {
            TrackerCatalog.meterValue(cell.bytes).toString()
        } else {
            cell.label
        }

    private fun trackerCells(spec: LensSpec, blocks: List<ByteArray>): List<TrackerCell> {
        val out = ArrayList<TrackerCell>()
        for (w in spec.widgets) {
            val bytes = blocks.getOrNull(w.blockIndex) ?: continue
            when (w.kind) {
                TrackerKind.BITS, TrackerKind.GRID -> {
                    if (w.labels.isEmpty()) {
                        for (i in 0 until bytes.size * 8) {
                            out.add(TrackerCell((i + 1).toString(), w.kind, bytes, i))
                        }
                    } else {
                        for ((i, label) in w.labels.withIndex()) {
                            out.add(TrackerCell(label, w.kind, bytes, i))
                        }
                    }
                }
                TrackerKind.METER -> {
                    val label = w.labels.firstOrNull().orEmpty()
                    out.add(TrackerCell(label, w.kind, bytes, 0))
                }
                TrackerKind.LINE -> { }
            }
        }
        return out
    }

    private class LensReadSnap(
        val lensId: String,
        val ok: Boolean,
        val text: String?,
        val blocks: List<ByteArray> = emptyList(),
    )

    private fun raHudEligible(raNetworkCommands: Boolean, surface: SessionSurface): Boolean {
        if (!raNetworkCommands) return false
        val playerId = surface.playerId.orEmpty()
        return playerId.startsWith("ra-") || surface.packageName == RA_PACKAGE
    }

    private fun formatLensText(spec: LensSpec, blocks: List<ByteArray>): String {
        val sb = StringBuilder(spec.title)
        for (i in spec.blocks.indices) {
            val block = spec.blocks[i]
            val data = blocks.getOrNull(i) ?: continue
            sb.append('\n')
            sb.append(formatLensBlock(block, data))
        }
        return sb.toString()
    }

    private fun formatLensBlock(block: LensBlock, data: ByteArray): String {
        return when (block.format.lowercase()) {
            "bitfield" -> {
                if (block.labels.isEmpty()) {
                    data.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }
                } else {
                    val on = ArrayList<String>()
                    for ((idx, label) in block.labels.withIndex()) {
                        val bi = idx / 8
                        val bit = idx % 8
                        if (bi < data.size && (data[bi].toInt() and (1 shl bit)) != 0) {
                            on.add(label)
                        }
                    }
                    if (on.isEmpty()) "—" else on.joinToString(" ")
                }
            }
            else -> data.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }
        }
    }

    private class RaChipSnap(
        val show: Boolean,
        val paused: Boolean,
        val slotsAllowed: Boolean,
    )

    private fun enqueueRaChipWork(
        root: View,
        app: GhostGalleonApp,
        probe: Boolean,
        extra: (RaCommandClient, Int) -> Unit = { _, _ -> },
    ) {
        val port = app.settings.raNetworkCmdPort
        var snap: RaChipSnap? = null
        app.enqueueRaUdp(
            work = { client ->
                extra(client, port)
                snap = sampleRaChip(client, port, probe)
            },
            onMain = {
                if (!root.isAttachedToWindow) return@enqueueRaUdp
                paintRaChip(root, snap)
            },
        )
    }

    private fun sampleRaChip(client: RaCommandClient, port: Int, probe: Boolean): RaChipSnap {
        if (probe && !client.probe(port, SystemClock.elapsedRealtime())) {
            return RaChipSnap(false, false, client.slotStripAllowed())
        }
        if (!client.isLinkUp()) {
            return RaChipSnap(false, false, client.slotStripAllowed())
        }
        val status = client.status(port)
        if (!client.isLinkUp()) {
            return RaChipSnap(false, false, client.slotStripAllowed())
        }
        return RaChipSnap(true, status == RaStatus.PAUSED, client.slotStripAllowed())
    }

    private fun paintRaChip(root: View, snap: RaChipSnap?) {
        val group = root.findViewWithTag<View>(TAG_PLAY_HUD_RA) ?: return
        val pause = root.findViewWithTag<TextView>(TAG_PLAY_HUD_PAUSE) ?: return
        val slots = root.findViewWithTag<View>(TAG_PLAY_HUD_SLOTS)
        if (snap == null || !snap.show) {
            group.visibility = View.GONE
            slots?.visibility = View.GONE
            return
        }
        pause.setText(
            if (snap.paused) R.string.play_hud_resume
            else R.string.play_hud_pause,
        )
        group.visibility = View.VISIBLE
        if (!snap.slotsAllowed) slots?.visibility = View.GONE
    }

    private fun buildRaSlotStrip(
        activity: AppCompatActivity,
        settings: Settings,
        surface: SessionSurface,
        dp: (Int) -> Int,
        compact: Boolean,
        onPick: (Int) -> Unit,
    ): View {
        val cell = dp(if (compact) 36 else 44)
        val statesDir = raStatesDir(surface)
        val pngNames = statesDir?.let { RaStateSlots.pngNamesIn(it) }.orEmpty()
        val thumbs = RaStateSlots.thumbsBySlot(pngNames)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isBaselineAligned = false
        }
        for (slot in RaStateSlots.slotLabels(pngNames)) {
            val thumbName = thumbs[slot]
            val bmp = if (statesDir != null && thumbName != null) {
                decodeSlotThumb(File(statesDir, thumbName), cell)
            } else {
                null
            }
            row.addView(raSlotCell(activity, settings, slot, bmp, cell, dp, onPick))
        }
        return HorizontalScrollView(activity).apply {
            tag = TAG_PLAY_HUD_SLOTS
            visibility = View.GONE
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, 0)
            addView(
                row,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER_HORIZONTAL },
            )
        }
    }

    private fun raSlotCell(
        activity: AppCompatActivity,
        settings: Settings,
        slot: Int,
        thumb: Bitmap?,
        sizePx: Int,
        dp: (Int) -> Int,
        onPick: (Int) -> Unit,
    ): View {
        val cell = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                if (slot > RaStateSlots.SLOTS.first()) marginStart = dp(6)
            }
            background = TileBackgrounds.chip(activity)
            setOnClickListener { onPick(slot) }
        }
        if (thumb != null) {
            cell.addView(ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(thumb)
            })
        }
        cell.addView(TextView(activity).apply {
            text = slot.toString()
            setTextColor(
                if (thumb != null) Color.WHITE
                else ThemePack.accentTextColor(
                    settings.accentColor,
                    TileBackgrounds.chipIdleColor(activity),
                ),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            if (thumb != null) setBackgroundColor(0x66000000)
        })
        return cell
    }

    private fun cinemaPlayHostAllowed(
        app: GhostGalleonApp,
        activity: Context,
        surface: SessionSurface,
    ): Boolean {
        val hostId = (activity as? AppCompatActivity)?.currentDisplayId()
        return PlayHostPolicy.playHostAllowed(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            policy = surface.policy,
            greedy = surface.greedy,
            hostDisplayId = hostId,
            launchDisplayId = surface.launchDisplayId,
        )
    }

    private fun cinemaPaintKey(frames: List<CinemaFrame>): String =
        frames.joinToString(",") { "${it.slot}:${it.savedAtMs}:${it.thumbKey ?: ""}" }

    private fun paintPlayHudCinema(
        strip: ViewGroup,
        app: GhostGalleonApp,
        activity: Context,
        surface: SessionSurface,
    ) {
        val row = (strip.getChildAt(0) as? LinearLayout) ?: return
        val key = cinemaPaintKey(app.cinemaFrames)
        if (strip.visibility == View.VISIBLE &&
            row.childCount == CinemaPolicy.BAND.count() &&
            (row.tag as? String) == key
        ) {
            return
        }
        val density = strip.resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }
        val compact = strip.rootView.height > 0 && strip.rootView.height < 500f * density
        val cell = dp(if (compact) 36 else 44)
        val statesDir = raStatesDir(surface)
        val settings = app.settings
        val port = settings.raNetworkCmdPort
        val surfaceKey = surface.key
        row.removeAllViews()
        row.tag = key
        for (slot in CinemaPolicy.BAND) {
            val thumbFile = cinemaThumbFile(statesDir, slot)
            val bmp = thumbFile?.let { decodeSlotThumb(it, cell) }
            row.addView(
                cinemaSlotCell(
                    activity,
                    settings,
                    slot,
                    bmp,
                    cell,
                    dp,
                    first = slot == CinemaPolicy.BAND.first,
                    onTap = { picked ->
                        if (CinemaPolicy.inBand(picked)) {
                            app.enqueueRaUdp(
                                work = { client -> client.loadStateSlot(port, picked) },
                                onMain = {
                                    if (app.sessionSurface?.key != surfaceKey) return@enqueueRaUdp
                                    if (app.raCommandClient?.slotStripAllowed() == false) {
                                        hidePlayHudCinema(strip)
                                    }
                                },
                            )
                        }
                    },
                    onPin = { pinned ->
                        if (CinemaPolicy.inBand(pinned)) app.cinemaPinnedSlot = pinned
                    },
                ),
            )
        }
        if (strip.visibility != View.VISIBLE) strip.visibility = View.VISIBLE
    }

    private fun cinemaSlotCell(
        activity: Context,
        settings: Settings,
        slot: Int,
        thumb: Bitmap?,
        sizePx: Int,
        dp: (Int) -> Int,
        first: Boolean,
        onTap: (Int) -> Unit,
        onPin: (Int) -> Unit,
    ): View {
        val cell = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                if (!first) marginStart = dp(6)
            }
            background = TileBackgrounds.chip(activity)
            setOnClickListener { onTap(slot) }
            setOnLongClickListener {
                onPin(slot)
                true
            }
        }
        if (thumb != null) {
            cell.addView(ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(thumb)
            })
        }
        cell.addView(TextView(activity).apply {
            text = slot.toString()
            setTextColor(
                if (thumb != null) Color.WHITE
                else ThemePack.accentTextColor(
                    settings.accentColor,
                    TileBackgrounds.chipIdleColor(activity),
                ),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            if (thumb != null) setBackgroundColor(0x66000000)
        })
        return cell
    }

    private fun cinemaThumbFile(statesDir: File?, slot: Int): File? {
        if (statesDir == null) return null
        val suffix = ".state$slot.png"
        val name = RaStateSlots.pngNamesIn(statesDir).firstOrNull {
            it.endsWith(suffix, ignoreCase = true)
        } ?: return null
        return File(statesDir, name)
    }

    private fun raStatesDir(surface: SessionSurface): File? {
        val players = Platforms.ALL.flatMap { it.players }
        val extras = players.firstOrNull { it.id == surface.playerId }?.extras
            ?: players.firstOrNull { it.id.startsWith("ra-") }?.extras
        val external = extras?.get("EXTERNAL")?.trim().orEmpty()
        if (external.isEmpty()) return null
        return File("$external/states")
    }

    private fun decodeSlotThumb(file: File, targetPx: Int): Bitmap? {
        if (!file.isFile || !file.canRead()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            val w = bounds.outWidth
            val h = bounds.outHeight
            while (w / sample > targetPx * 2 && h / sample > targetPx * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }
}
