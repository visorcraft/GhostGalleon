package com.visorcraft.ghostgalleon.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
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
 *
 * Pointer inject (Winlator cockpit): maps normalized pad coords onto the
 * launch display and dispatches gestures there (API 30+ displayId only).
 */
class InputAssistService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFilterOn: Boolean? = null
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var lastStrokeX: Float = 0f
    private var lastStrokeY: Float = 0f

    private val app: GhostGalleonApp
        get() = application as GhostGalleonApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        app.inputAssistConnected = true
        app.inputAssistService = this
        logFilterState(computeMayFilter())
        app.refreshSeatChrome()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (app.inputAssistService === this) {
            app.inputAssistService = null
        }
        app.inputAssistConnected = false
        activeStroke = null
        logFilterState(false)
        app.refreshSeatChrome()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (app.inputAssistService === this) {
            app.inputAssistService = null
        }
        app.inputAssistConnected = false
        activeStroke = null
        app.refreshSeatChrome()
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

    /**
     * Absolute pointer on [launchDisplayId]. Caller already applied policy
     * gates. No-ops without display-targeted gestures (API 30+).
     */
    fun injectOnLaunchDisplay(normX: Float, normY: Float, down: Boolean, launchDisplayId: Int) {
        if (!supportsDisplayGesture()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val bounds = launchDisplayBounds(launchDisplayId) ?: return
        val (px, py) = LaunchPointer.mapNormToDisplay(
            normX, normY,
            bounds.left, bounds.top, bounds.width(), bounds.height(),
        )
        dispatchOnLaunchDisplay(px.toFloat(), py.toFloat(), down, launchDisplayId)
    }

    /** Same launch-display rectangle the cockpit pointer uses. */
    fun launchDisplaySize(launchDisplayId: Int): Pair<Int, Int>? {
        val bounds = launchDisplayBounds(launchDisplayId) ?: return null
        return bounds.width() to bounds.height()
    }

    /**
     * Pixel tap/hold on [launchDisplayId]. Fail closed if [setDisplayId] is
     * missing — never gesture onto the play host as if it were the game.
     */
    fun injectSeatTap(x: Float, y: Float, down: Boolean, launchDisplayId: Int) {
        if (!supportsDisplayGesture()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        dispatchOnLaunchDisplay(x, y, down, launchDisplayId, seat = true)
    }

    private fun dispatchOnLaunchDisplay(
        x: Float,
        y: Float,
        down: Boolean,
        launchDisplayId: Int,
        seat: Boolean = false,
    ) {
        val stroke: GestureDescription.StrokeDescription = if (down) {
            if (activeStroke == null) {
                val path = Path().apply { moveTo(x, y) }
                GestureDescription.StrokeDescription(path, 0L, STROKE_MS, true).also {
                    activeStroke = it
                    lastStrokeX = x
                    lastStrokeY = y
                }
            } else {
                val path = Path().apply {
                    moveTo(lastStrokeX, lastStrokeY)
                    lineTo(x, y)
                }
                activeStroke!!.continueStroke(path, 0L, STROKE_MS, true).also {
                    activeStroke = it
                    lastStrokeX = x
                    lastStrokeY = y
                }
            }
        } else {
            val end = if (activeStroke != null) {
                val path = Path().apply {
                    moveTo(lastStrokeX, lastStrokeY)
                    lineTo(x, y)
                }
                activeStroke!!.continueStroke(path, 0L, STROKE_MS, false)
            } else {
                val path = Path().apply { moveTo(x, y) }
                GestureDescription.StrokeDescription(path, 0L, TAP_MS, false)
            }
            activeStroke = null
            lastStrokeX = x
            lastStrokeY = y
            end
        }

        val builder = GestureDescription.Builder().addStroke(stroke)
        if (!applyDisplayId(builder, launchDisplayId)) {
            // Fail closed: never dispatch without a targeted display.
            activeStroke = null
            if (seat) Log.i(SEAT_TAG, "inject skipped: no displayId")
            return
        }
        dispatchGesture(builder.build(), null, null)
    }

    private fun launchDisplayBounds(launchDisplayId: Int): Rect? {
        val dm = getSystemService(DisplayManager::class.java) ?: return null
        val display = dm.getDisplay(launchDisplayId) ?: return null
        // Gesture coords with setDisplayId are relative to that display.
        val rect = Rect()
        @Suppress("DEPRECATION")
        display.getRectSize(rect)
        if (rect.width() <= 0 || rect.height() <= 0) {
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            if (size.x <= 0 || size.y <= 0) return null
            rect.set(0, 0, size.x, size.y)
        } else {
            // getRectSize is size-only (0,0 origin) on most devices; keep origin 0.
            rect.offsetTo(0, 0)
        }
        return rect
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
        private const val SEAT_TAG = "GGSeat"
        private const val STROKE_MS = 16L
        private const val TAP_MS = 50L

        /** True when Builder.setDisplayId is available (API 30+). */
        fun supportsDisplayGesture(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            return setDisplayIdMethod != null
        }

        private val setDisplayIdMethod: java.lang.reflect.Method? by lazy {
            try {
                GestureDescription.Builder::class.java.getMethod(
                    "setDisplayId",
                    Int::class.javaPrimitiveType,
                )
            } catch (_: Throwable) {
                null
            }
        }

        /** @return true when [displayId] was applied; false → skip dispatch. */
        private fun applyDisplayId(builder: GestureDescription.Builder, displayId: Int): Boolean {
            val method = setDisplayIdMethod ?: return false
            return try {
                method.invoke(builder, displayId)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
