package com.visorcraft.ghostgalleon.ui

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.visorcraft.ghostgalleon.display.AndroidDisplayProbe
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.settings.CompanionRole

class MainActivity : BaseDeckActivity() {

    private var lastHealUptimeMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchCompanionIfPresent()
    }

    override fun onResume() {
        // Capture before super clears the flag at end of BaseDeckActivity.onResume.
        val returningFromElsewhere = leftHomeSinceResume()
        super.onResume()
        if (isHomeRole()) {
            com.visorcraft.ghostgalleon.ui.deck.HomeWallpaper.applyWindowFallback(this)
            app.maybeSealHomeWallpaper()
        }
        app.refreshDisplayConfig(debounce = true)
        val surface = app.sessionSurface
        val pinReady = app.settings.companionRole == CompanionRole.PINNED_APP.name &&
            !app.settings.companionPinnedPackage.isNullOrBlank()
        val action = DualPaintPolicy.resumeCompanionAction(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            returningFromElsewhere = returningFromElsewhere,
            policy = surface?.policy,
            greedy = surface?.greedy == true,
            pinReady = pinReady,
        )
        when (action) {
            DualPaintPolicy.ResumeCompanionAction.NONE -> { }
            DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING ->
                healCompanionIfMissing(returningFromElsewhere)
            DualPaintPolicy.ResumeCompanionAction.RESTART ->
                restartCompanionPanel(
                    if (surface?.policy == SessionPolicy.YIELD_BOTH) "return-from-yield"
                    else "return-from-app",
                )
        }
        if (returningFromElsewhere) {
            // After the action so YIELD return still sees the policy.
            // Playtime stays on endOpenSession / noteReturnToLauncher.
            app.clearSessionSurface()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val returningFromElsewhere = leftHomeSinceResume()
        // Heal only when policy says HEAL_IF_MISSING. A HOME return is
        // onNewIntent then onResume; consuming allowHeal here would reject
        // onResume's RESTART (YIELD / greedy KEEP / no-session).
        // Already-resumed HOME redelivery never re-fires onResume.
        val surface = app.sessionSurface
        val pinReady = app.settings.companionRole == CompanionRole.PINNED_APP.name &&
            !app.settings.companionPinnedPackage.isNullOrBlank()
        val action = DualPaintPolicy.resumeCompanionAction(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            returningFromElsewhere = returningFromElsewhere,
            policy = surface?.policy,
            greedy = surface?.greedy == true,
            pinReady = pinReady,
        )
        if (action == DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING) {
            healCompanionIfMissing(returningFromElsewhere)
        }
        // Still on home (never onStop'd): swipe-up / re-HOME opens all-apps.
        if (!returningFromElsewhere) {
            requestAppDrawer(allowToggle = true)
        }
    }

    private fun healCompanionIfMissing(returningFromElsewhere: Boolean) {
        // YIELD owns both panels until HOME return; do not spawn onto a live DS.
        if (app.sessionSurface?.policy == SessionPolicy.YIELD_BOTH &&
            !returningFromElsewhere
        ) {
            return
        }
        if (!isHomeRole()) return
        val now = SystemClock.uptimeMillis()
        if (!DualPaintPolicy.allowHeal(now, lastHealUptimeMs)) return
        lastHealUptimeMs = now

        val topo = app.refreshDisplayConfig(debounce = true)
        if (topo.mode != SurfaceMode.DUAL) return
        val target = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (currentDisplayId() ?: -1) }
            ?: return
        val live = app.liveCompanions().filter { !it.isFinishing }
        val seat = app.companionSeatHolder()
        // Only a STARTED peer on the secondary target counts as healthy.
        // Seat claim alone is not enough — a stuck READY_TO_SHOW window
        // (surface never presented) left the secondary panel pure black while
        // heal thought a companion was already live.
        val healthyOnTarget = live.any { it.isHealthyCompanion(target) } ||
            seat?.isHealthyCompanion(target) == true
        val anyPeerClaiming = live.any {
            it.currentDisplayId() == target || it.isHealthyCompanion(target)
        } || seat != null
        when (
            DualPaintPolicy.companionHealAction(
                dualMode = true,
                anyPeerClaiming = anyPeerClaiming,
                healthyOnTarget = healthyOnTarget,
            )
        ) {
            DualPaintPolicy.HealAction.NONE -> return
            DualPaintPolicy.HealAction.LAUNCH -> launchCompanionIfPresent()
            DualPaintPolicy.HealAction.RESTART -> {
                // Peer exists but never reached STARTED on target — recreate.
                // (Inline: restartCompanionPanel would re-check heal debounce we
                // just consumed.)
                Log.i(PAINT_TAG, "restartCompanion reason=heal-unhealthy")
                live.forEach { it.closeQuietly() }
                seat?.takeIf { !it.isFinishing }?.closeQuietly()
                mainHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) launchCompanionIfPresent()
                }, 180L)
            }
        }
    }

    /**
     * Close every Companion and launch a fresh one on the secondary target.
     * Recovery for pure-black secondary panels without system Force Stop.
     * Debounced with the same heal window so storms cannot thrash paint.
     */
    fun restartCompanionPanel(reason: String) {
        if (!isHomeRole()) return
        val now = SystemClock.uptimeMillis()
        if (!DualPaintPolicy.allowHeal(now, lastHealUptimeMs)) return
        lastHealUptimeMs = now
        val topo = app.refreshDisplayConfig(debounce = true)
        if (topo.mode != SurfaceMode.DUAL) return
        Log.i(PAINT_TAG, "restartCompanion reason=$reason")
        app.liveCompanions().toList().forEach { it.closeQuietly() }
        // Let finish() detach before launching a peer on the same display.
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) launchCompanionIfPresent()
        }, 180L)
    }

    private fun launchCompanionIfPresent() {
        // YIELD owns both panels; starting Companion here would steal one.
        if (app.sessionSurface?.policy == SessionPolicy.YIELD_BOTH) return
        val topo = app.refreshDisplayConfig()
        if (topo.mode != SurfaceMode.DUAL) return
        val secondaryHomeId = topo.secondaryHomeDisplayId
            ?: topo.allIds.firstOrNull { it != (currentDisplayId() ?: -1) }
            ?: return
        if (!AndroidDisplayProbe.hasDisplay(this, secondaryHomeId)) return
        // Plain component + setLaunchDisplayId only (no SECONDARY_HOME category).
        val intent = Intent(this, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(secondaryHomeId)
        startActivity(intent, options.toBundle())
    }
}
