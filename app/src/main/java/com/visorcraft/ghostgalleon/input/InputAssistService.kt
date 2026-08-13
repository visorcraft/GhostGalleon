package com.visorcraft.ghostgalleon.input

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.ui.DualPaintPolicy
import com.visorcraft.ghostgalleon.ui.PlayHostPolicy

/**
 * Optional key filter: consumes CLAIM_HOST / RELEASE_HOST only while the pad
 * owner is GAME and the session does not own the companion display. Gameplay
 * keys pass through. Never logs key codes.
 */
class InputAssistService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFilterOn: Boolean? = null

    private val app: GhostGalleonApp
        get() = application as GhostGalleonApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        app.inputAssistConnected = true
        logFilterState(computeMayFilter())
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        app.inputAssistConnected = false
        logFilterState(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        app.inputAssistConnected = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Key filter only; no event stream handling.
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val surface = app.sessionSurface
        val sessionOwns = DualPaintPolicy.sessionOwnsCompanionDisplay(
            surface?.policy,
            surface?.greedy == true,
        )
        if (sessionOwns) {
            logFilterState(false)
            return false
        }

        val owner = currentEffectiveOwner()
        val mayFilter = InputAssistPolicy.mayFilterKeys(
            assistConnected = app.inputAssistConnected,
            owner = owner,
            sessionOwnsCompanion = false,
        )
        logFilterState(mayFilter)
        if (!mayFilter) return false

        val action = KeyMap.resolve(event.keyCode, app.settings)
        if (action != Action.CLAIM_HOST && action != Action.RELEASE_HOST) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            mainHandler.post {
                when (action) {
                    Action.CLAIM_HOST -> app.claimHost()
                    Action.RELEASE_HOST -> app.releaseHost()
                    else -> return@post
                }
                app.liveDeckActivities().forEach { it.applyPlayHostFocusLock() }
            }
        }
        return true
    }

    private fun computeMayFilter(): Boolean {
        val surface = app.sessionSurface
        val sessionOwns = DualPaintPolicy.sessionOwnsCompanionDisplay(
            surface?.policy,
            surface?.greedy == true,
        )
        return InputAssistPolicy.mayFilterKeys(
            assistConnected = app.inputAssistConnected,
            owner = currentEffectiveOwner(),
            sessionOwnsCompanion = sessionOwns,
        )
    }

    private fun currentEffectiveOwner(): InputOwner {
        val surface = app.sessionSurface
        val dual = app.displayConfig.mode == SurfaceMode.DUAL
        val launchId = surface?.launchDisplayId
        val hostId = launchId?.let { lid ->
            app.displayConfig.allIds.firstOrNull { it != lid }
        }
        val allowed = PlayHostPolicy.playHostAllowed(
            dualMode = dual,
            policy = surface?.policy,
            greedy = surface?.greedy == true,
            hostDisplayId = hostId,
            launchDisplayId = launchId,
        )
        val base = InputOwnerPolicy.inputOwner(
            dualMode = dual,
            policy = surface?.policy,
            greedy = surface?.greedy == true,
            playHostAllowed = allowed,
        )
        return InputOwnerPolicy.effectiveOwner(base, app.hostClaimed)
    }

    private fun logFilterState(on: Boolean) {
        if (lastFilterOn == on) return
        lastFilterOn = on
        Log.i(TAG, "assist filter=${if (on) "on" else "off"}")
    }

    companion object {
        private const val TAG = "GGInput"
    }
}
