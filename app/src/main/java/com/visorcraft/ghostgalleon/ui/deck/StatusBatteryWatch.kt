package com.visorcraft.ghostgalleon.ui.deck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * One battery listener for every status pill. Dual displays must not each
 * register ACTION_BATTERY_CHANGED and poll CURRENT_NOW on the main thread.
 */
internal object StatusBatteryWatch {

    private val pills = LinkedHashSet<View>()
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var shown: StatusBattery.Glyph? = null
    private var pending: StatusBattery.Glyph? = null
    private var hits = 0

    private val poll = object : Runnable {
        override fun run() {
            val ctx = pills.firstOrNull()?.context?.applicationContext ?: return
            dispatch(ctx)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            handler.removeCallbacks(poll)
            dispatch(context.applicationContext)
        }
    }

    fun attach(pill: View) {
        pills.add(pill)
        val app = pill.context.applicationContext
        ensureReceiver(app)
        handler.removeCallbacks(poll)
        dispatch(app)
    }

    fun detach(pill: View) {
        pills.remove(pill)
        if (pills.isNotEmpty()) return
        handler.removeCallbacks(poll)
        unregister(pill.context.applicationContext)
        shown = null
        pending = null
        hits = 0
    }

    private fun dispatch(app: Context) {
        if (pills.isEmpty()) return
        val raw = StatusPill.readBattery(app)
        val stabilized = StatusBattery.stabilize(
            shown = shown ?: raw.glyph,
            candidate = raw.glyph,
            pending = pending,
            hits = hits,
        )
        shown = stabilized.first
        pending = stabilized.second
        hits = stabilized.third
        val snap = raw.copy(glyph = shown!!)
        for (pill in pills) {
            if (pill.isAttachedToWindow) StatusPill.applyBattery(pill, snap)
        }
        if (shown != StatusBattery.Glyph.BATTERY) {
            handler.postDelayed(poll, StatusPill.PLUGGED_POLL_MS)
        }
    }

    private fun ensureReceiver(app: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregister(app: Context) {
        if (!receiverRegistered) return
        runCatching { app.unregisterReceiver(receiver) }
        receiverRegistered = false
    }
}
