package com.visorcraft.ghostgalleon.ui.deck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.resolveText

/**
 * Compact battery + clock chrome for immersive decks (system status bar is
 * hidden). Companion already shows a larger pill; Grid/Game Mode overlay a
 * compact one so single-display and interactive panels always show time.
 *
 * [formatBatteryLabel] is pure and host-tested; [build] is the Android view.
 */
object StatusPill {

    const val TAG = "status_pill"
    const val TAG_BATTERY = "status_battery"
    const val TAG_CLOCK = "status_clock"

    /**
     * Battery label for a capacity percent. Null when [pct] is outside 0..100.
     * When [charging] is true, appends a lightning mark after the percent.
     */
    fun formatBatteryLabel(pct: Int, charging: Boolean = false): UiText? {
        if (pct !in 0..100) return null
        return text(
            if (charging) R.string.battery_percent_charging else R.string.system_battery_percent,
            pct,
        )
    }

    /**
     * Build a horizontal pill (battery + live [TextClock]). [compact] uses
     * smaller type for overlay on the interactive deck.
     */
    fun build(context: Context, compact: Boolean = true): View {
        val textSp = if (compact) 13f else 20f
        val padH = if (compact) 12 else 20
        val padV = if (compact) 4 else 8
        val gap = if (compact) 8 else 12

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = TileBackgrounds.pill(context)
            setPadding(context.dp(padH), context.dp(padV), context.dp(padH), context.dp(padV))
            tag = TAG
            contentDescription = context.getString(R.string.deck_status)
        }

        val batteryView = TextView(context).apply {
            tag = TAG_BATTERY
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(Color.WHITE)
            setPadding(0, 0, context.dp(gap), 0)
        }
        pill.addView(batteryView)
        pill.addView(TextClock(context).apply {
            val locale = context.resources.configuration.locales[0]
            format12Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "hm")
            format24Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm")
            tag = TAG_CLOCK
            contentDescription = context.getString(R.string.deck_clock)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(Color.WHITE)
        })
        applyBattery(pill, readBattery(context))
        wireLiveBattery(pill)
        return pill
    }

    /**
     * Overlay insets in dp. [flushCorner] is the companion large panel
     * (true top-end); Grid/Game keep a slightly looser overlay.
     */
    fun overlayInsetDp(flushCorner: Boolean): Pair<Int, Int> =
        if (flushCorner) 4 to 4 else 8 to 12

    /** Overlay params: top-end. [flushCorner] hugs the companion corner. */
    fun overlayLayoutParams(
        context: Context,
        flushCorner: Boolean = false,
    ): android.widget.FrameLayout.LayoutParams {
        val (top, end) = overlayInsetDp(flushCorner)
        return android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ).apply {
            topMargin = context.dp(top)
            marginEnd = context.dp(end)
        }
    }

    data class BatterySnapshot(val percent: Int, val charging: Boolean)

    /**
     * HUD percent from ACTION_BATTERY_CHANGED extras. Level/scale matches
     * the system reading; [capacityProperty] is only a fallback because some
     * fuel gauges freeze BATTERY_PROPERTY_CAPACITY mid-charge.
     */
    fun percentFrom(level: Int, scale: Int, capacityProperty: Int): Int {
        if (level >= 0 && scale > 0) {
            return ((level * 100f) / scale).toInt().coerceIn(0, 100)
        }
        return if (capacityProperty in 0..100) capacityProperty else -1
    }

    /**
     * Power connected wins over EXTRA_STATUS. On the One X Sugar, status
     * flaps charging/discharging every second while AC is plugged, which
     * would flicker the lightning mark if status were used alone.
     */
    fun isCharging(status: Int, plugged: Int): Boolean {
        if (plugged != 0) return true
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun snapshotFrom(
        level: Int,
        scale: Int,
        status: Int,
        plugged: Int,
        capacityProperty: Int = -1,
    ): BatterySnapshot = BatterySnapshot(
        percent = percentFrom(level, scale, capacityProperty),
        charging = isCharging(status, plugged),
    )

    fun batteryLabelNeedsWrite(previous: CharSequence?, next: String): Boolean =
        previous?.toString() != next

    fun readBattery(context: Context): BatterySnapshot {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val capacity = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return snapshotFromIntent(intent, capacity)
    }

    internal fun applyBattery(pill: View, snapshot: BatterySnapshot) {
        val tv = pill.findViewWithTag<View>(TAG_BATTERY) as? TextView ?: return
        val ctx = pill.context
        val label = formatBatteryLabel(snapshot.percent, snapshot.charging)
        if (label == null) {
            tv.visibility = View.GONE
            return
        }
        val resolved = ctx.resolveText(label)
        if (tv.visibility == View.VISIBLE &&
            !batteryLabelNeedsWrite(tv.text, resolved)
        ) {
            return
        }
        tv.text = resolved
        tv.contentDescription = ctx.getString(
            R.string.format_battery_accessibility,
            resolved,
        )
        tv.visibility = View.VISIBLE
    }

    private fun snapshotFromIntent(intent: Intent?, capacityProperty: Int): BatterySnapshot =
        snapshotFrom(
            level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
            scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
            plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            capacityProperty = capacityProperty,
        )

    private fun wireLiveBattery(pill: View) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!pill.isAttachedToWindow) return
                if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
                applyBattery(pill, snapshotFromIntent(intent, capacityProperty = -1))
            }
        }
        pill.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val app = v.context.applicationContext
                runCatching { app.unregisterReceiver(receiver) }
                val sticky = registerBatteryReceiver(app, receiver)
                applyBattery(pill, snapshotFromIntent(sticky, capacityProperty = -1))
            }

            override fun onViewDetachedFromWindow(v: View) {
                runCatching {
                    v.context.applicationContext.unregisterReceiver(receiver)
                }
            }
        })
    }

    private fun registerBatteryReceiver(
        context: Context,
        receiver: BroadcastReceiver,
    ): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}
