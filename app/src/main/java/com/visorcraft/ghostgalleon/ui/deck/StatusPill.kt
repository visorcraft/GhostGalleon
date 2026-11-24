package com.visorcraft.ghostgalleon.ui.deck

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.text.Editable
import android.text.TextWatcher
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
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx
import java.util.WeakHashMap

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
    const val TAG_DIVIDER = "status_divider"
    const val TAG_CLOCK = "status_clock"

    /** Pure white tint/text color (JVM-test-safe constant). */
    const val NORMAL_COLOR = 0xFFFFFFFF.toInt()

    /** Soft emerald tint for charging vector bolt; high contrast on dark pill. */
    const val CHARGING_BOLT = 0xFF69F0AE.toInt()

    /** Warning red for plugged-in net drain; readable on OLED black. */
    const val NET_DRAIN_BOLT = 0xFFFF5252.toInt()

    /** Unplugged battery percent threshold for low-battery warning tint. */
    const val LOW_BATTERY_PERCENT = 15

    /** Unplugged battery percent threshold for critical red tint. */
    const val CRITICAL_BATTERY_PERCENT = 10

    /** Warm amber tint for unplugged 11–15% battery. */
    const val LOW_BATTERY_WARN = 0xFFFFB74D.toInt()

    /** Warning red tint for unplugged <= 10% battery. */
    const val LOW_BATTERY_CRITICAL = 0xFFFF5252.toInt()

    /** Subtle resting hairline border matching [TileBackgrounds.statusPill]. */
    const val DEFAULT_BORDER_COLOR = 0x28FFFFFF

    /** Duration (ms) for cancelable in-place battery state tint crossfade. */
    const val TINT_TRANSITION_MS = 180L

    internal const val PLUGGED_POLL_MS = 1_500L

    private val activeTintAnimators = WeakHashMap<View, ValueAnimator>()
    private val colorEvaluator = ArgbEvaluator()

    data class Metrics(
        val textSp: Float,
        val padHorizontalDp: Int,
        val padVerticalDp: Int,
        val gapDp: Int,
        val iconDp: Int,
        val glyphGapDp: Int,
        val dividerHeightDp: Int,
        val elevationDp: Float,
    )

    /** Pure typography, padding, and optical alignment tokens for compact vs large pills. */
    fun metricsFor(compact: Boolean): Metrics = if (compact) {
        Metrics(
            textSp = 13f,
            padHorizontalDp = 12,
            padVerticalDp = 5,
            gapDp = 7,
            iconDp = 16,
            glyphGapDp = 4,
            dividerHeightDp = 12,
            elevationDp = 4f,
        )
    } else {
        Metrics(
            textSp = 19f,
            padHorizontalDp = 16,
            padVerticalDp = 7,
            gapDp = 9,
            iconDp = 20,
            glyphGapDp = 5,
            dividerHeightDp = 16,
            elevationDp = 6f,
        )
    }

    /**
     * Semantic hairline border tint reflecting charging, net-drain, or
     * low/critical battery; falls back to the active theme's [restingHairline]
     * when battery state is nominal.
     */
    fun pillBorderColor(
        percent: Int,
        glyph: StatusBattery.Glyph,
        restingHairline: Int = DEFAULT_BORDER_COLOR,
    ): Int = when (glyph) {
        StatusBattery.Glyph.CHARGING -> (0x58 shl 24) or (CHARGING_BOLT and 0x00FFFFFF)
        StatusBattery.Glyph.NET_DRAIN -> (0x66 shl 24) or (NET_DRAIN_BOLT and 0x00FFFFFF)
        StatusBattery.Glyph.BATTERY -> when {
            percent in 0..CRITICAL_BATTERY_PERCENT ->
                (0x66 shl 24) or (LOW_BATTERY_CRITICAL and 0x00FFFFFF)
            percent in 0..LOW_BATTERY_PERCENT ->
                (0x58 shl 24) or (LOW_BATTERY_WARN and 0x00FFFFFF)
            else -> restingHairline
        }
    }

    /**
     * Pure TalkBack summary combining localized battery state and current clock
     * reading without hardcoding words.
     */
    fun accessibilitySummary(
        batteryDescription: String?,
        clockText: CharSequence?,
        clockLabel: String,
        fallbackLabel: String,
    ): String {
        val b = batteryDescription?.trim().orEmpty()
        val c = clockText?.toString()?.trim().orEmpty()
        val clockPart = when {
            c.isNotEmpty() && clockLabel.isNotBlank() -> "$clockLabel $c"
            c.isNotEmpty() -> c
            else -> ""
        }
        return when {
            b.isNotEmpty() && clockPart.isNotEmpty() -> "$b, $clockPart"
            b.isNotEmpty() -> b
            clockPart.isNotEmpty() -> clockPart
            else -> fallbackLabel
        }
    }

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

    /** True when battery is unplugged and at or below [LOW_BATTERY_PERCENT]. */
    fun isLowBattery(percent: Int, glyph: StatusBattery.Glyph): Boolean =
        glyph == StatusBattery.Glyph.BATTERY && percent in 0..LOW_BATTERY_PERCENT

    /**
     * Battery percent text color: urgency red during [StatusBattery.Glyph.NET_DRAIN],
     * amber at 11..15% unplugged, red at <= 10% unplugged, white otherwise.
     */
    fun batteryTextColor(percent: Int, glyph: StatusBattery.Glyph): Int = when (glyph) {
        StatusBattery.Glyph.NET_DRAIN -> NET_DRAIN_BOLT
        StatusBattery.Glyph.CHARGING -> NORMAL_COLOR
        StatusBattery.Glyph.BATTERY -> when {
            percent in 0..CRITICAL_BATTERY_PERCENT -> LOW_BATTERY_CRITICAL
            percent in 0..LOW_BATTERY_PERCENT -> LOW_BATTERY_WARN
            else -> NORMAL_COLOR
        }
    }

    /** Vector icon tint matching battery / charging / net-drain state. */
    fun batteryIconTint(percent: Int, glyph: StatusBattery.Glyph): Int = when (glyph) {
        StatusBattery.Glyph.BATTERY -> batteryTextColor(percent, glyph)
        StatusBattery.Glyph.CHARGING -> CHARGING_BOLT
        StatusBattery.Glyph.NET_DRAIN -> NET_DRAIN_BOLT
    }

    /** Vector drawable resource for all three battery states (zero Unicode emoji fallback). */
    fun batteryIconRes(glyph: StatusBattery.Glyph): Int = when (glyph) {
        StatusBattery.Glyph.BATTERY -> R.drawable.ic_status_battery
        StatusBattery.Glyph.CHARGING, StatusBattery.Glyph.NET_DRAIN -> R.drawable.ic_status_bolt
    }

    /**
     * Build a horizontal pill (battery + divider + live [TextClock]). [compact]
     * uses smaller type for overlay on the interactive deck / TOP_STRIP.
     */
    fun build(context: Context, compact: Boolean = true): View {
        val m = metricsFor(compact)

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            background = TileBackgrounds.statusPill(context)
            elevation = context.dpF(m.elevationDp)
            setPadding(
                context.dp(m.padHorizontalDp),
                context.dp(m.padVerticalDp),
                context.dp(m.padHorizontalDp),
                context.dp(m.padVerticalDp),
            )
            tag = TAG
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = context.getString(R.string.deck_status)
        }

        val batteryView = TextView(context).apply {
            tag = TAG_BATTERY
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, m.textSp)
            setTextColor(Color.WHITE)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = context.dp(m.glyphGapDp)
            }
        }
        val icon = ImageView(context).apply {
            tag = TAG_BATTERY_ICON
            visibility = View.GONE
            setImageResource(R.drawable.ic_status_battery)
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val size = context.dp(m.iconDp)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        val divider = View(context).apply {
            tag = TAG_DIVIDER
            visibility = View.GONE
            setBackgroundColor(TileBackgrounds.hairlineDivider(context))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(
                context.strokePx(1),
                context.dp(m.dividerHeightDp),
            ).apply {
                marginStart = context.dp(m.gapDp)
                marginEnd = context.dp(m.gapDp)
            }
        }
        val clock = TextClock(context).apply {
            val locale = context.resources.configuration.locales[0]
            format12Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "hm")
            format24Hour = android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm")
            tag = TAG_CLOCK
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, m.textSp)
            setTextColor(Color.WHITE)
        }
        clock.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                syncPillAccessibility(pill)
            }
        })
        pill.addView(batteryView)
        pill.addView(icon)
        pill.addView(divider)
        pill.addView(clock)
        applyBattery(pill, readBattery(context))
        syncPillAccessibility(pill)
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

    @Suppress("UNUSED_PARAMETER")
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
        val pluggedIn = StatusBattery.isPlugged(plugged)
        val draining = StatusBattery.draining(currentUa, dischargeSign, counterDelta)
        val glyph = StatusBattery.glyph(pluggedIn, draining, percent)
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

    private data class AppliedChrome(
        val percent: Int,
        val glyph: StatusBattery.Glyph,
        val bars: Int,
        val textColor: Int,
        val iconTint: Int,
        val borderColor: Int,
    )

    internal fun applyBattery(pill: View, snapshot: BatterySnapshot) {
        val tv = pill.findViewWithTag<View>(TAG_BATTERY) as? TextView ?: return
        val icon = pill.findViewWithTag<View>(TAG_BATTERY_ICON) as? ImageView
        val divider = pill.findViewWithTag<View>(TAG_DIVIDER)
        val ctx = pill.context
        val restingHairline = TileBackgrounds.hairlineDivider(ctx)
        val label = formatBatteryLabel(snapshot.percent) ?: run {
            cancelTintTransition(pill)
            tv.visibility = View.GONE
            tv.contentDescription = null
            icon?.visibility = View.GONE
            divider?.visibility = View.GONE
            applyPillBorder(pill, restingHairline)
            pill.setTag(R.id.status_chrome, null)
            syncPillAccessibility(pill)
            return
        }
        val prev = pill.getTag(R.id.status_chrome) as? AppliedChrome
        val nextTextColor = batteryTextColor(snapshot.percent, snapshot.glyph)
        val nextIconTint = batteryIconTint(snapshot.percent, snapshot.glyph)
        val nextBorderColor = pillBorderColor(snapshot.percent, snapshot.glyph, restingHairline)
        if (prev != null &&
            prev.borderColor == nextBorderColor &&
            !StatusBattery.chromeNeedsWrite(
                prev.percent,
                prev.glyph,
                prev.bars,
                snapshot.percent,
                snapshot.glyph,
                snapshot.bars,
            )
        ) {
            return
        }
        val resolved = ctx.resolveText(label)
        tv.text = resolved
        tv.contentDescription = when (snapshot.glyph) {
            StatusBattery.Glyph.CHARGING ->
                ctx.getString(R.string.format_battery_charging_accessibility, snapshot.percent)
            StatusBattery.Glyph.NET_DRAIN ->
                ctx.getString(R.string.format_battery_net_drain_accessibility, snapshot.percent)
            StatusBattery.Glyph.BATTERY ->
                ctx.getString(R.string.format_battery_accessibility, resolved)
        }
        tv.visibility = View.VISIBLE
        divider?.visibility = View.VISIBLE
        if (icon != null) {
            if (prev?.glyph != snapshot.glyph) {
                icon.isActivated = snapshot.glyph == StatusBattery.Glyph.NET_DRAIN
                icon.setImageResource(batteryIconRes(snapshot.glyph))
            }
            if (snapshot.glyph == StatusBattery.Glyph.BATTERY &&
                icon.drawable?.level != snapshot.bars
            ) {
                icon.setImageLevel(snapshot.bars)
            }
            icon.visibility = View.VISIBLE
        }
        val tintsChanged = prev != null && (
            prev.textColor != nextTextColor ||
                prev.iconTint != nextIconTint ||
                prev.borderColor != nextBorderColor
            )
        if (tintsChanged &&
            MotionPolicy.shouldAnimate(
                requestedMs = TINT_TRANSITION_MS,
                animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
                attached = pill.isAttachedToWindow,
            )
        ) {
            animateTintTransition(
                pill = pill,
                tv = tv,
                icon = icon,
                fromText = prev!!.textColor,
                toText = nextTextColor,
                fromIcon = prev.iconTint,
                toIcon = nextIconTint,
                fromBorder = prev.borderColor,
                toBorder = nextBorderColor,
            )
        } else {
            cancelTintTransition(pill)
            tv.setTextColor(nextTextColor)
            icon?.setColorFilter(nextIconTint, PorterDuff.Mode.SRC_IN)
            applyPillBorder(pill, nextBorderColor)
        }
        pill.setTag(
            R.id.status_chrome,
            AppliedChrome(
                percent = snapshot.percent,
                glyph = snapshot.glyph,
                bars = snapshot.bars,
                textColor = nextTextColor,
                iconTint = nextIconTint,
                borderColor = nextBorderColor,
            ),
        )
        syncPillAccessibility(pill)
    }

    private fun applyPillBorder(pill: View, color: Int) {
        val bg = (pill.background as? GradientDrawable)?.mutate() as? GradientDrawable ?: return
        bg.setStroke(pill.context.strokePx(1), color)
    }

    private fun cancelTintTransition(pill: View) {
        activeTintAnimators.remove(pill)?.let { anim ->
            anim.removeAllUpdateListeners()
            anim.removeAllListeners()
            anim.cancel()
        }
    }

    private fun animateTintTransition(
        pill: View,
        tv: TextView,
        icon: ImageView?,
        fromText: Int,
        toText: Int,
        fromIcon: Int,
        toIcon: Int,
        fromBorder: Int,
        toBorder: Int,
    ) {
        cancelTintTransition(pill)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionPolicy.effectiveDurationMs(
                TINT_TRANSITION_MS,
                ValueAnimator.areAnimatorsEnabled(),
            )
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                tv.setTextColor(colorEvaluator.evaluate(f, fromText, toText) as Int)
                icon?.setColorFilter(
                    colorEvaluator.evaluate(f, fromIcon, toIcon) as Int,
                    PorterDuff.Mode.SRC_IN,
                )
                applyPillBorder(pill, colorEvaluator.evaluate(f, fromBorder, toBorder) as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (activeTintAnimators[pill] === animation) {
                        activeTintAnimators.remove(pill)
                    }
                    tv.setTextColor(toText)
                    icon?.setColorFilter(toIcon, PorterDuff.Mode.SRC_IN)
                    applyPillBorder(pill, toBorder)
                }
            })
        }
        activeTintAnimators[pill] = animator
        animator.start()
    }

    private fun syncPillAccessibility(pill: View) {
        val tv = pill.findViewWithTag<View>(TAG_BATTERY) as? TextView
        val clock = pill.findViewWithTag<View>(TAG_CLOCK) as? TextView
        val batteryDesc = tv?.takeIf { it.visibility == View.VISIBLE }?.contentDescription?.toString()
        pill.contentDescription = accessibilitySummary(
            batteryDescription = batteryDesc,
            clockText = clock?.text,
            clockLabel = pill.context.getString(R.string.deck_clock),
            fallbackLabel = pill.context.getString(R.string.deck_status),
        )
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
        pill.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                StatusBatteryWatch.attach(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                cancelTintTransition(v)
                StatusBatteryWatch.detach(v)
            }
        })
        if (pill.isAttachedToWindow) StatusBatteryWatch.attach(pill)
    }
}
