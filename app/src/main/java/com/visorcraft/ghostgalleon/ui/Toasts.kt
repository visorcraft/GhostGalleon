package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.visorcraft.ghostgalleon.i18n.UiText
import java.lang.ref.WeakReference

/**
 * Pure, host-tested coalescing policy for [toast] feedback.
 *
 * Prevents rapid-tap toast pileups (e.g. cycling themes, toggling favorites,
 * or tapping unavailable chips repeatedly) by replacing an active toast
 * immediately and suppressing identical rapid double-taps within a short
 * debounce window.
 */
internal class ToastCoalescer(
    private val duplicateWindowMs: Long = DUPLICATE_SUPPRESS_MS,
) {
    enum class Decision {
        IGNORE_BLANK,
        SHOW_FRESH,
        CANCEL_AND_REPLACE,
        SUPPRESS_DUPLICATE,
    }

    private var lastMessage: String? = null
    private var lastShownAtMs: Long = 0L
    private var lastExpiresAtMs: Long = 0L

    fun decide(message: String, long: Boolean, nowMs: Long): Decision {
        val normalized = message.trim()
        if (normalized.isEmpty()) return Decision.IGNORE_BLANK

        val stillActive = lastMessage != null && nowMs < lastExpiresAtMs
        val elapsed = nowMs - lastShownAtMs
        if (stillActive &&
            lastMessage == normalized &&
            elapsed >= 0L &&
            elapsed < duplicateWindowMs
        ) {
            return Decision.SUPPRESS_DUPLICATE
        }

        val windowMs = if (long) LONG_WINDOW_MS else SHORT_WINDOW_MS
        lastMessage = normalized
        lastShownAtMs = nowMs
        lastExpiresAtMs = nowMs + windowMs
        return if (stillActive) Decision.CANCEL_AND_REPLACE else Decision.SHOW_FRESH
    }

    fun reset() {
        lastMessage = null
        lastShownAtMs = 0L
        lastExpiresAtMs = 0L
    }

    companion object {
        const val SHORT_WINDOW_MS: Long = 2_000L
        const val LONG_WINDOW_MS: Long = 3_500L
        const val DUPLICATE_SUPPRESS_MS: Long = 450L
    }
}

private object ToastCoordinator {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coalescer = ToastCoalescer()
    private var activeRef: WeakReference<Toast>? = null

    fun show(context: Context, message: String, long: Boolean) {
        val showNow = Runnable {
            val decision = coalescer.decide(
                message = message,
                long = long,
                nowMs = SystemClock.elapsedRealtime(),
            )
            when (decision) {
                ToastCoalescer.Decision.IGNORE_BLANK,
                ToastCoalescer.Decision.SUPPRESS_DUPLICATE,
                -> return@Runnable
                ToastCoalescer.Decision.CANCEL_AND_REPLACE -> {
                    activeRef?.get()?.cancel()
                    activeRef = null
                }
                ToastCoalescer.Decision.SHOW_FRESH -> {
                    activeRef?.get()?.cancel()
                    activeRef = null
                }
            }
            // Keep caller Context (not applicationContext) so dual-display
            // activities render toasts on the active display surface.
            val toast = Toast.makeText(
                context,
                message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            )
            activeRef = WeakReference(toast)
            toast.show()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showNow.run()
        } else {
            mainHandler.post(showNow)
        }
    }

    fun cancelActive() {
        val cancelNow = Runnable {
            activeRef?.get()?.cancel()
            activeRef = null
            coalescer.reset()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cancelNow.run()
        } else {
            mainHandler.post(cancelNow)
        }
    }
}

fun cancelActiveToast() = ToastCoordinator.cancelActive()

fun Context.toast(message: String, long: Boolean = false) {
    ToastCoordinator.show(this, message, long)
}

fun Context.toast(message: UiText, long: Boolean = false) =
    toast(resolveText(message), long)

fun Context.toast(resId: Int, vararg args: Any) =
    toast(getString(resId, *args))

fun Context.toastLong(resId: Int, vararg args: Any) =
    toast(getString(resId, *args), long = true)
