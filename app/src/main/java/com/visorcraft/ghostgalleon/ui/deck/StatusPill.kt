package com.visorcraft.ghostgalleon.ui.deck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.BatteryManager
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
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
 * Charge glyph policy lives in [StatusBattery].
 */
object StatusPill {

    const val TAG = "status_pill"
    const val TAG_BATTERY = "status_battery"
    const val TAG_BATTERY_BOLT = "status_battery_bolt"
    const val TAG_BATTERY_ICON = "status_battery_icon"
    const val TAG_CLOCK = "status_clock"

    /** Warning red for plugged-in net drain; readable on OLED black. */
    const val NET_DRAIN_BOLT = 0xFFFF5252.toInt()

    private const val PLUGGED_POLL_MS = 1_500L

    /** Learned CURRENT_NOW discharge sign. Shared by both pills. */
    internal var dischargeSign: Int = StatusBattery.AOSP_DISCHARGE_SIGN
    private var lastChargeCounter: Long? = null

    /**
     * Battery label for a capacity percent. Null when [pct] is outside 0..100.
     * Charge state is a separate glyph, not baked into this string.
     */
    fun formatBatteryLabel(pct: Int): UiText? {
        if (pct !in 0..100) return null
        return text(R.string.system_battery_percent, pct)
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
        val iconDp = if (compact) 14 else 20
        val glyphPad = if (compact) 2 else 4

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
            includeFontPadding = false
            setPadding(0, 0, context.dp(glyphPad), 0)
        }
        val bolt = TextView(context).apply {
            tag = TAG_BATTERY_BOLT
            visibility = View.GONE
            text = context.getString(R.string.glyph_bolt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(Color.WHITE)
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, 0, context.dp(gap), 0)
        }
        val icon = ImageView(context).apply {
            tag = TAG_BATTERY_ICON
            visibility = View.GONE
            setImageResource(R.drawable.ic_status_battery)
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val size = context.dp(iconDp)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = context.dp(gap)
            }
        }
        pill.addView(batteryView)
        pill.addView(bolt)
        pill.addView(icon)
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

    data class BatterySnapshot(
        val percent: Int,
        val plugged: Boolean,
        val glyph: StatusBattery.Glyph,
        val bars: Int,
    ) {
        val charging: Boolean get() = plugged
    }

    fun percentFrom(level: Int, scale: Int, capacityProperty: Int): Int =
        StatusBattery.percentFrom(level, scale, capacityProperty)

    fun isCharging(status: Int, plugged: Int): Boolean {
        if (StatusBattery.isPlugged(plugged)) return true
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun snapshotFrom(
        level: Int,
        scale: Int,
        status: Int,
        plugged: Int,
        capacityProperty: Int = -1,
        currentUa: Long? = null,
        dischargeSign: Int = StatusBattery.AOSP_DISCHARGE_SIGN,
        counterDelta: Long? = null,
    ): BatterySnapshot {
        val percent = percentFrom(level, scale, capacityProperty)
        val pluggedIn = StatusBattery.isPlugged(plugged) ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val draining = StatusBattery.draining(currentUa, dischargeSign, counterDelta)
        val glyph = StatusBattery.glyph(pluggedIn, draining)
        return BatterySnapshot(
            percent = percent,
            plugged = pluggedIn,
            glyph = glyph,
            bars = StatusBattery.batteryBars(percent),
        )
    }

    fun batteryLabelNeedsWrite(previous: CharSequence?, next: String): Boolean =
        previous?.toString() != next

    fun readBattery(context: Context): BatterySnapshot {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val bm = context.getSystemService(BatteryManager::class.java)
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val currentUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Long.MIN_VALUE }
        val counter = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Long.MIN_VALUE && it > 0L }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        if (!StatusBattery.isPlugged(plugged)) {
            currentUa?.let { StatusBattery.learnDischargeSign(it) }?.let { dischargeSign = it }
        }
        val delta = if (counter != null && lastChargeCounter != null) {
            counter - lastChargeCounter!!
        } else {
            null
        }
        if (counter != null) lastChargeCounter = counter
        return snapshotFromIntent(intent, capacity, currentUa, dischargeSign, delta)
    }

    internal fun applyBattery(pill: View, snapshot: BatterySnapshot) {
        val tv = pill.findViewWithTag<View>(TAG_BATTERY) as? TextView ?: return
        val bolt = pill.findViewWithTag<View>(TAG_BATTERY_BOLT) as? TextView
        val icon = pill.findViewWithTag<View>(TAG_BATTERY_ICON) as? ImageView
        val ctx = pill.context
        val label = formatBatteryLabel(snapshot.percent) ?: run {
            tv.visibility = View.GONE
            bolt?.visibility = View.GONE
            icon?.visibility = View.GONE
            return
        }
        val resolved = ctx.resolveText(label)
        val a11y = when (snapshot.glyph) {
            StatusBattery.Glyph.CHARGING ->
                ctx.getString(R.string.format_battery_charging_accessibility, snapshot.percent)
            StatusBattery.Glyph.NET_DRAIN ->
                ctx.getString(R.string.format_battery_net_drain_accessibility, snapshot.percent)
            StatusBattery.Glyph.BATTERY ->
                ctx.getString(R.string.format_battery_accessibility, resolved)
        }
        val skip = tv.visibility == View.VISIBLE &&
            bolt != null &&
            icon != null &&
            !StatusBattery.chromeNeedsWrite(
                previousPercent = tv.text.toString().filter { it.isDigit() }.toIntOrNull() ?: Int.MIN_VALUE,
                previousGlyph = visibleGlyph(bolt, icon),
                previousBars = icon.drawable?.level ?: Int.MIN_VALUE,
                nextPercent = snapshot.percent,
                nextGlyph = snapshot.glyph,
                nextBars = snapshot.bars,
            )
        if (skip) return
        tv.text = resolved
        tv.contentDescription = a11y
        tv.visibility = View.VISIBLE
        when (snapshot.glyph) {
            StatusBattery.Glyph.BATTERY -> {
                bolt?.visibility = View.GONE
                icon?.isActivated = false
                icon?.setImageResource(R.drawable.ic_status_battery)
                icon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                icon?.setImageLevel(snapshot.bars)
                icon?.visibility = View.VISIBLE
            }
            StatusBattery.Glyph.CHARGING -> {
                icon?.visibility = View.GONE
                icon?.isActivated = false
                bolt?.setTextColor(Color.WHITE)
                bolt?.visibility = View.VISIBLE
            }
            StatusBattery.Glyph.NET_DRAIN -> {
                bolt?.visibility = View.GONE
                icon?.isActivated = true
                icon?.setImageResource(R.drawable.ic_status_bolt)
                icon?.setColorFilter(NET_DRAIN_BOLT, PorterDuff.Mode.SRC_IN)
                icon?.visibility = View.VISIBLE
            }
        }
    }

    private fun visibleGlyph(bolt: TextView, icon: ImageView): StatusBattery.Glyph = when {
        bolt.visibility == View.VISIBLE -> StatusBattery.Glyph.CHARGING
        icon.visibility == View.VISIBLE && icon.isActivated -> StatusBattery.Glyph.NET_DRAIN
        icon.visibility == View.VISIBLE -> StatusBattery.Glyph.BATTERY
        else -> StatusBattery.Glyph.CHARGING
    }

    private fun snapshotFromIntent(
        intent: Intent?,
        capacityProperty: Int,
        currentUa: Long?,
        dischargeSign: Int,
        counterDelta: Long?,
    ): BatterySnapshot = snapshotFrom(
        level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
        status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
        plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
        capacityProperty = capacityProperty,
        currentUa = currentUa,
        dischargeSign = dischargeSign,
        counterDelta = counterDelta,
    )

    private fun wireLiveBattery(pill: View) {
        var shown: StatusBattery.Glyph? = null
        var pending: StatusBattery.Glyph? = null
        var hits = 0

        fun applyLive() {
            if (!pill.isAttachedToWindow) return
            val raw = readBattery(pill.context)
            val stabilized = StatusBattery.stabilize(
                shown = shown ?: raw.glyph,
                candidate = raw.glyph,
                pending = pending,
                hits = hits,
            )
            shown = stabilized.first
            pending = stabilized.second
            hits = stabilized.third
            applyBattery(pill, raw.copy(glyph = shown!!))
        }

        val poll = object : Runnable {
            override fun run() {
                applyLive()
                val plugged = shown != null && shown != StatusBattery.Glyph.BATTERY
                if (pill.isAttachedToWindow && plugged) {
                    pill.postDelayed(this, PLUGGED_POLL_MS)
                }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!pill.isAttachedToWindow) return
                if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
                pill.removeCallbacks(poll)
                poll.run()
            }
        }
        pill.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val app = v.context.applicationContext
                runCatching { app.unregisterReceiver(receiver) }
                registerBatteryReceiver(app, receiver)
                pill.removeCallbacks(poll)
                poll.run()
            }

            override fun onViewDetachedFromWindow(v: View) {
                pill.removeCallbacks(poll)
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
