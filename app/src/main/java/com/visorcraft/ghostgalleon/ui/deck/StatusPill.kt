package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
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

        val battery = readBattery(context)
        formatBatteryLabel(battery.percent, battery.charging)?.let { label ->
            val resolved = context.resolveText(label)
            pill.addView(TextView(context).apply {
                text = resolved
                tag = TAG_BATTERY
                contentDescription = context.getString(
                    R.string.format_battery_accessibility,
                    resolved,
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
                setTextColor(Color.WHITE)
                setPadding(0, 0, context.dp(gap), 0)
            })
        }
        pill.addView(TextClock(context).apply {
            val locale = context.resources.configuration.locales[0]
            format12Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "hm")
            format24Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm")
            tag = TAG_CLOCK
            contentDescription = context.getString(R.string.deck_clock)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextColor(Color.WHITE)
        })
        return pill
    }

    /** Overlay params: top-end with small margin (interactive decks). */
    fun overlayLayoutParams(context: Context): android.widget.FrameLayout.LayoutParams {
        return android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ).apply {
            topMargin = context.dp(8)
            marginEnd = context.dp(12)
        }
    }

    data class BatterySnapshot(val percent: Int, val charging: Boolean)

    fun readBattery(context: Context): BatterySnapshot {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(pct, charging)
    }
}
