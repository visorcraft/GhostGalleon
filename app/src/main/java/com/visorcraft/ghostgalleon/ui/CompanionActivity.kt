package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.library.SessionTracker
import com.visorcraft.ghostgalleon.rom.OracleTally
import com.visorcraft.ghostgalleon.rom.OracleTallyLogic
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.ui.deck.CompanionPanel

/**
 * Secondary panel (Sugar bottom by default). SECONDARY_HOME redelivery must
 * not thrash [setContentView] or open the all-apps drawer — see [DualPaintPolicy].
 */
class CompanionActivity : BaseDeckActivity() {

    private var selfClosing = false
    private var absorbDuplicate = false
    /** At most one display redirect per instance (prevents redirect loops). */
    private var didRedirect = false
    /** True after a successful [app.tryClaimCompanionSeat]. */
    private var holdsCompanionSeat = false
    private val playHudHandler = Handler(Looper.getMainLooper())
    private val playHudTick = object : Runnable {
        override fun run() {
            val delay = tickPlayHudClock(hudRoot(), app, this@CompanionActivity)
            if (delay != null) playHudHandler.postDelayed(this, delay)
        }
    }
    private val oracle = PixelOracle(this) { isFullRenderInFlight }

    override fun skipExitCascade(): Boolean = true

    override fun shouldRenderOnCreate(): Boolean =
        !absorbDuplicate && !sessionOwnsCompanionDisplay()

    private fun sessionOwnsCompanionDisplay(): Boolean =
        DualPaintPolicy.sessionOwnsCompanionDisplay(
            app.sessionSurface?.policy,
            app.sessionSurface?.greedy == true,
        )

    fun closeQuietly() {
        selfClosing = true
        releaseSeat()
        // Clear focus-lock flag before the window goes away (owner NONE).
        applyPlayHostFocusLock()
        finish()
    }

    fun isHealthyCompanion(targetDisplayId: Int): Boolean {
        if (isFinishing || isDestroyed) return false
        val id = currentDisplayId() ?: return false
        if (id != targetDisplayId) return false
        return lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Process-wide seat claim BEFORE any peer lookup: lifecycle callbacks
        // only register this instance after onCreate returns, so liveCompanions()
        // cannot see in-flight SECONDARY_HOME twins. Seat claim is the only
        // reliable single-winner gate under MULTIPLE_TASK storms (ANR + black
        // top panel when hundreds of Companions finish-churn on the main thread).
        if (DualPaintPolicy.shouldAbsorbSeat(seatHeldByOther = !app.tryClaimCompanionSeat(this))) {
            absorbDuplicate = true
            selfClosing = true
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        holdsCompanionSeat = true

        // Prefer cached topology on the hot absorb/redirect path; full probe
        // only when still uninitialized (avoids DisplayManager spam in storms).
        val topo = if (app.displayConfig.reason == "uninitialized") {
            app.refreshDisplayConfig()
        } else {
            app.displayConfig
        }
        if (topo.mode != SurfaceMode.DUAL) {
            selfClosing = true
            releaseSeat()
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        val target = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (currentDisplayId() ?: -1) }
            ?: run {
                selfClosing = true
                releaseSeat()
                super.onCreate(savedInstanceState)
                finish()
                return
            }

        val existing = app.liveCompanions().filter { !it.isFinishing && it !== this }
        // Keep any live peer already on the target — do not require STARTED
        // yet (race with attach). Prefer not to kill/recreate.
        val keepOnTarget = existing.firstOrNull {
            it.currentDisplayId() == target || it.isHealthyCompanion(target)
        }
        if (DualPaintPolicy.shouldAbsorbDuplicate(hasPeerOnTarget = keepOnTarget != null)) {
            absorbDuplicate = true
            selfClosing = true
            releaseSeat()
            super.onCreate(savedInstanceState)
            // Absorb is silent (DualPaintPolicy.absorbMayOpenDrawer() == false):
            // no All-apps, no peer massacre, no re-paint storm on survivor.
            finish()
            return
        }

        if (sessionOwnsCompanionDisplay()) {
            super.onCreate(savedInstanceState)
            closeQuietly()
            return
        }

        super.onCreate(savedInstanceState)

        val currentDisplay = currentDisplayId()
        if (currentDisplay != null && currentDisplay != target) {
            redirectToSecondary(target)
        } else {
            window.decorView.post {
                if (isFinishing || selfClosing) return@post
                val now = currentDisplayId()
                if (now != null && now != target &&
                    AndroidDisplayProbe.hasDisplay(this, target)
                ) {
                    redirectToSecondary(target)
                }
            }
        }
    }

    private fun redirectToSecondary(target: Int) {
        if (selfClosing || isFinishing || didRedirect) return
        if (sessionOwnsCompanionDisplay()) {
            closeQuietly()
            return
        }
        if (!AndroidDisplayProbe.hasDisplay(this, target)) return
        didRedirect = true
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(target)
        selfClosing = true
        // Free the seat so the display-correct instance can claim; storm
        // twins still lose tryClaimCompanionSeat once the new one holds it.
        releaseSeat()
        Log.i(PAINT_TAG, "Companion redirect → display $target")
        runCatching { startActivity(intent, options.toBundle()) }
        finish()
    }

    private fun releaseSeat() {
        if (!holdsCompanionSeat) return
        holdsCompanionSeat = false
        app.releaseCompanionSeat(this)
    }

    override fun onDestroy() {
        playHudHandler.removeCallbacks(playHudTick)
        oracle.stop()
        releaseSeat()
        super.onDestroy()
    }

    override fun onResume() {
        if (sessionOwnsCompanionDisplay()) {
            playHudHandler.removeCallbacks(playHudTick)
            oracle.stop()
            closeQuietly()
            super.onResume()
            return
        }
        super.onResume()
        armPlayHudTick()
        oracle.start()
        applyPlayHostFocusLock()
    }

    override fun onContentRebuilt() {
        armPlayHudTick()
        oracle.start()
        applyPlayHostFocusLock()
    }

    private fun armPlayHudTick() {
        playHudHandler.removeCallbacks(playHudTick)
        playHudHandler.post(playHudTick)
    }

    private fun hudRoot(): View =
        findViewById(android.R.id.content) ?: window.decorView

    override fun onPause() {
        playHudHandler.removeCallbacks(playHudTick)
        oracle.stop()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (sessionOwnsCompanionDisplay()) {
            closeQuietly()
            return
        }
        // All-apps is Main-only (AGENTS + DualPaintPolicy). Companion must
        // never open the drawer — SECONDARY_HOME redelivery storms would
        // flash/glitch All-apps and thrash paints.
    }
}

/**
 * 32×32 PixelCopy of this activity's [android.view.Window] (never Display).
 * One copy in flight; dest bitmap reused. Heal via [MainActivity.restartCompanionPanel].
 */
internal class PixelOracle(
    private val activity: BaseDeckActivity,
    private val renderInFlight: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pixels = IntArray(SIZE * SIZE)
    private var dest: Bitmap? = null
    private var copyPending = false
    private var tally = OracleTally()
    private val tick = Runnable { onTick() }

    fun start() {
        handler.removeCallbacks(tick)
        if (!shouldSchedule()) return
        handler.postDelayed(tick, DualPaintPolicy.MIN_HEAL_GAP_MS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
    }

    private fun shouldSchedule(): Boolean {
        val app = activity.application as GhostGalleonApp
        val displayId = activity.currentDisplayId() ?: 0
        return PlayHostPolicy.oracleShouldSchedule(
            detectEnabled = app.settings.detectBlackCompanion,
            companionSurface = DisplayRole.roleFor(displayId, app.deckState) ==
                DisplayRole.COMPANION,
        )
    }

    private fun onTick() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!shouldSchedule()) return
        handler.postDelayed(tick, DualPaintPolicy.MIN_HEAL_GAP_MS)
        if (copyPending) return
        if (renderInFlight()) return
        val app = activity.application as GhostGalleonApp
        val surface = app.sessionSurface
        val windowId = activity.currentDisplayId()
        val may = PlayHostPolicy.oracleMaySample(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            ownsCompanionDisplay = DualPaintPolicy.sessionOwnsCompanionDisplay(
                surface?.policy,
                surface?.greedy == true,
            ),
            windowDisplayId = windowId,
            launchDisplayId = surface?.launchDisplayId,
            sessionOpen = surface != null,
        )
        if (!may) return
        val bmp = destBitmap() ?: return
        val win = activity.window ?: return
        if (win.peekDecorView() == null) return
        copyPending = true
        try {
            PixelCopy.request(win, bmp, { result -> onCopyFinished(result, bmp, windowId) }, handler)
        } catch (_: RuntimeException) {
            copyPending = false
            onCopyFinished(PixelCopy.ERROR_UNKNOWN, bmp, windowId)
        }
    }

    private fun onCopyFinished(result: Int, bmp: Bitmap, windowId: Int?) {
        copyPending = false
        if (activity.isFinishing || activity.isDestroyed) return
        val failed = result != PixelCopy.SUCCESS
        if (failed && !copyFailLogged) {
            copyFailLogged = true
            Log.w(ORACLE_TAG, "PixelCopy failed result=$result")
        }
        val luma = if (failed || bmp.isRecycled) null else maxLuma(bmp)
        val now = SystemClock.uptimeMillis()
        val (next, requestHeal) = OracleTallyLogic.onSample(
            tally, maxLuma = luma, copyFailed = failed, nowMs = now,
        )
        tally = next
        if (!requestHeal) return
        Log.i(ORACLE_TAG, "miss n=3 display=$windowId maxLuma=$luma")
        maybeHeal(windowId)
    }

    private fun maybeHeal(windowId: Int?) {
        val app = activity.application as GhostGalleonApp
        val surface = app.sessionSurface
        if (DualPaintPolicy.sessionOwnsCompanionDisplay(
                surface?.policy,
                surface?.greedy == true,
            )
        ) {
            return
        }
        if (surface?.policy == SessionPolicy.KEEP_COMPANION &&
            !PlayHostPolicy.playHostAllowed(
                dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
                policy = surface.policy,
                greedy = surface.greedy,
                hostDisplayId = windowId,
                launchDisplayId = surface.launchDisplayId,
            )
        ) {
            return
        }
        val main = activity as? MainActivity
            ?: app.liveDeckActivities().filterIsInstance<MainActivity>().firstOrNull()
        if (main == null || main.isFinishing || main.isDestroyed) return
        Log.i(ORACLE_TAG, "heal reason=oracle-black")
        main.restartCompanionPanel("oracle-black")
    }

    private fun destBitmap(): Bitmap? {
        val existing = dest
        if (existing != null && !existing.isRecycled &&
            existing.width == SIZE && existing.height == SIZE &&
            existing.config == Bitmap.Config.RGB_565
        ) {
            return existing
        }
        if (existing != null && !existing.isRecycled) existing.recycle()
        return runCatching {
            Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.RGB_565)
        }.getOrNull()?.also { dest = it }
    }

    private fun maxLuma(bmp: Bitmap): Int {
        bmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        var max = 0
        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r + r + b + g + g + g) / 6
            if (luma > max) max = luma
        }
        return max
    }

    companion object {
        const val ORACLE_TAG = "GGOracle"
        private const val SIZE = 32
        private var copyFailLogged = false
    }
}

/**
 * In-place KEEP clock. No SETTINGS / SELECTION / notifyChanged.
 * @return next delay in ms, or null when no clock is bound (disarm).
 */
internal fun tickPlayHudClock(root: View?, app: GhostGalleonApp, activity: Context): Long? {
    val clock = root?.findViewWithTag<TextView>("play_hud_clock") ?: return null
    val session = app.openSession
    if (session == null) return PlayHostPolicy.playHudClockDelayMs(0L)
    val elapsed = SessionTracker.activeElapsedMs(session, System.currentTimeMillis())
    val next = activity.getString(
        if (session.isActive) R.string.format_session else R.string.format_session_paused,
        activity.resolveText(SessionMath.formatPlaytime(elapsed)),
    )
    if (PlayHostPolicy.playHudClockNeedsWrite(clock.text, next)) {
        clock.text = next
    }
    CompanionPanel.tickPlayHudRa(root, app, activity)
    return PlayHostPolicy.playHudTickDelayMs(
        elapsed,
        watchRa = app.settings.raNetworkCommands,
        raProbeMs = com.visorcraft.ghostgalleon.rom.RaCommand.PROBE_INTERVAL_MS,
    )
}
