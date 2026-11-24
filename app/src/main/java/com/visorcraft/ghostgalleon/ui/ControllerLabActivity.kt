package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.AudioHapticCohesion
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.input.StickThresholds
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.settings.SettingsUiLogic
import java.text.NumberFormat
import java.util.EnumMap
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure bipolar center-zero bar geometry (`[0..1]` fractions of track width). */
internal data class AxisBarSpan(
    val startFrac: Float,
    val widthFrac: Float,
    val clampedValue: Float,
)

/** Symmetric deadzone release/engage marker positions (`[0..1]` of track width). */
internal data class DeadzoneMarkers(
    val releaseFracNeg: Float,
    val releaseFracPos: Float,
    val engageFracNeg: Float,
    val engageFracPos: Float,
)

/** Accumulated peak negative/positive deflections in `[-1..0]` and `[0..1]`. */
internal data class AxisPeakState(
    val minNeg: Float = 0f,
    val maxPos: Float = 0f,
)

/** Pure 2D radar dot coordinate inside [StickRadarView]. Host-tested. */
internal data class RadarPoint(
    val xPx: Float,
    val yPx: Float,
)

/** Tested button coverage progress (`0/16 -> 16/16`). Host-tested. */
internal data class PadCoverage(
    val seen: Int,
    val total: Int,
    val complete: Boolean,
)

/** Standard 16 handheld gamepad indicators illuminated live in Controller Lab (8+8 symmetry). */
internal enum class PadIndicator(val keyCode: Int, val glyph: String) {
    L2(KeyEvent.KEYCODE_BUTTON_L2, "L2"),
    L1(KeyEvent.KEYCODE_BUTTON_L1, "L1"),
    L3(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3"),
    SELECT(KeyEvent.KEYCODE_BUTTON_SELECT, "SL"),
    START(KeyEvent.KEYCODE_BUTTON_START, "ST"),
    R3(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3"),
    R1(KeyEvent.KEYCODE_BUTTON_R1, "R1"),
    R2(KeyEvent.KEYCODE_BUTTON_R2, "R2"),
    DPAD_LEFT(KeyEvent.KEYCODE_DPAD_LEFT, "\u25C0"),
    DPAD_UP(KeyEvent.KEYCODE_DPAD_UP, "\u25B2"),
    DPAD_DOWN(KeyEvent.KEYCODE_DPAD_DOWN, "\u25BC"),
    DPAD_RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, "\u25B6"),
    Y(KeyEvent.KEYCODE_BUTTON_Y, "Y"),
    X(KeyEvent.KEYCODE_BUTTON_X, "X"),
    B(KeyEvent.KEYCODE_BUTTON_B, "B"),
    A(KeyEvent.KEYCODE_BUTTON_A, "A"),
}

/**
 * Pure, host-tested diagnostics math for [ControllerLabActivity].
 */
internal object ControllerLabLogic {

    const val CLEAR_BUTTON_MIN_HEIGHT_DP: Int = UiDimens.TOUCH_MIN_DP
    const val TWO_COLUMN_BREAKPOINT_DP: Int = 720
    const val TRIGGER_PRESS_THRESHOLD: Float = 0.5f
    const val BIND_IDLE_ALPHA: Float = 0.55f

    fun clampAxis(raw: Float): Float =
        if (raw.isNaN() || raw.isInfinite()) 0f else raw.coerceIn(-1f, 1f)

    fun clampTrigger(raw: Float): Float =
        if (raw.isNaN() || raw.isInfinite()) 0f else raw.coerceIn(0f, 1f)

    /** Resolve unipolar `[0..1]` trigger pull from primary axis (`LTRIGGER`/`RTRIGGER`) or fallback (`BRAKE`/`GAS`). */
    fun triggerValue(primary: Float, fallback: Float): Float =
        maxOf(clampTrigger(primary), clampTrigger(fallback))

    /** Quantize unipolar trigger `[0..1]` to integer hundredths (`0..100`). Pure; host-tested. */
    fun quantizeTriggerHundredths(raw: Float): Int =
        (clampTrigger(raw) * 100f).roundToInt().coerceIn(0, 100)

    /** True when analog trigger pull meets or exceeds digital press [threshold]. Pure; host-tested. */
    fun isTriggerPressed(value: Float, threshold: Float = TRIGGER_PRESS_THRESHOLD): Boolean =
        clampTrigger(value) >= threshold.coerceIn(0.01f, 1f)

    /** Format quantized unipolar trigger (`0..100`) as `"0.00"`..`"1.00"` with zero regex/printf allocs. */
    fun formatQuantizedTrigger(hundredths: Int): String {
        val clamped = hundredths.coerceIn(0, 100)
        val whole = clamped / 100
        val frac = clamped % 100
        return buildString(4) {
            append(whole)
            append('.')
            append(frac / 10)
            append(frac % 10)
        }
    }

    /** Map clamped `[-1..1]` stick `(rawX, rawY)` to pixel point relative to radar center `(cx, cy)` & radius `r`. */
    fun stickRadarPoint(
        rawX: Float,
        rawY: Float,
        cx: Float,
        cy: Float,
        r: Float,
    ): RadarPoint {
        val safeR = if (r.isNaN() || r.isInfinite()) 0f else r.coerceAtLeast(0f)
        val x = clampAxis(rawX)
        val y = clampAxis(rawY)
        return RadarPoint(
            xPx = cx + x * safeR,
            yPx = cy + y * safeR,
        )
    }

    /** True when either stick axis passes the [engage] deadzone threshold. Pure; host-tested. */
    fun isStickEngaged(x: Float, y: Float, engage: Float): Boolean {
        val thr = if (engage.isNaN() || engage.isInfinite()) 0.7f else engage.coerceIn(0f, 1f)
        return abs(clampAxis(x)) >= thr || abs(clampAxis(y)) >= thr
    }

    /** Responsive 2D stick radar diameter: `88dp` compact (~540dp) / `108dp` tall displays. */
    fun radarSizeDp(screenHeightDp: Int): Int = if (isCompactHeight(screenHeightDp)) 88 else 108

    /** True when a single bipolar stick axis has deflected past the live deadzone engage threshold. */
    fun axisEngaged(raw: Float, deadzonePercent: Int): Boolean =
        abs(clampAxis(raw)) >= StickThresholds.engage(deadzonePercent)

    /** Bind CTA opacity: muted `0.55f` until a key is captured, `1.0f` once ready. */
    fun bindButtonAlpha(hasLastKey: Boolean): Float = if (hasLastKey) 1f else BIND_IDLE_ALPHA

    /** Tested-button coverage summary (`0/16 -> 16/16`) flipping [PadCoverage.complete] when all pass. */
    fun padCoverage(seenCount: Int, totalCount: Int = PadIndicator.entries.size): PadCoverage {
        val safeTotal = totalCount.coerceAtLeast(0)
        val safeSeen = seenCount.coerceIn(0, safeTotal)
        return PadCoverage(
            seen = safeSeen,
            total = safeTotal,
            complete = safeTotal > 0 && safeSeen == safeTotal,
        )
    }

    /** Side-by-side 2-column lab dashboard on wide handhelds (`>= 720dp`, e.g. Odin 2 Portal). */
    fun useTwoColumnDashboard(screenWidthDp: Int): Boolean = screenWidthDp >= TWO_COLUMN_BREAKPOINT_DP

    fun valueToBarFrac(raw: Float): Float = 0.5f + clampAxis(raw) * 0.5f

    fun axisBarSpan(raw: Float): AxisBarSpan {
        val v = clampAxis(raw)
        return if (v >= 0f) {
            AxisBarSpan(startFrac = 0.5f, widthFrac = v * 0.5f, clampedValue = v)
        } else {
            AxisBarSpan(startFrac = 0.5f + v * 0.5f, widthFrac = -v * 0.5f, clampedValue = v)
        }
    }

    fun deadzoneMarkers(deadzonePercent: Int): DeadzoneMarkers {
        val release = StickThresholds.release(deadzonePercent)
        val engage = StickThresholds.engage(deadzonePercent)
        return DeadzoneMarkers(
            releaseFracNeg = 0.5f - release * 0.5f,
            releaseFracPos = 0.5f + release * 0.5f,
            engageFracNeg = 0.5f - engage * 0.5f,
            engageFracPos = 0.5f + engage * 0.5f,
        )
    }

    fun updatePeaks(current: AxisPeakState, raw: Float): AxisPeakState {
        val v = clampAxis(raw)
        return AxisPeakState(
            minNeg = minOf(current.minNeg, v),
            maxPos = maxOf(current.maxPos, v),
        )
    }

    fun padIndicatorForKeyCode(keyCode: Int): PadIndicator? =
        PadIndicator.entries.firstOrNull { it.keyCode == keyCode }

    fun padIndicatorsForHat(
        hatX: Float,
        hatY: Float,
        threshold: Float = 0.5f,
    ): Set<PadIndicator> {
        val out = EnumSet.noneOf(PadIndicator::class.java)
        fillPadIndicatorsForHat(hatX, hatY, out, threshold)
        return out
    }

    /**
     * Populate [out] in place with active HAT directions so 120–250 Hz motion
     * packets allocate zero temporary [Set]s. Pure; host-tested.
     */
    fun fillPadIndicatorsForHat(
        hatX: Float,
        hatY: Float,
        out: MutableSet<PadIndicator>,
        threshold: Float = 0.5f,
    ) {
        out.clear()
        val x = clampAxis(hatX)
        val y = clampAxis(hatY)
        if (x <= -threshold) out.add(PadIndicator.DPAD_LEFT)
        if (x >= threshold) out.add(PadIndicator.DPAD_RIGHT)
        if (y <= -threshold) out.add(PadIndicator.DPAD_UP)
        if (y >= threshold) out.add(PadIndicator.DPAD_DOWN)
    }

    /**
     * Quantize a clamped `[-1f..1f]` axis reading to integer hundredths
     * (`-100..100`), matching the `0.01` readout precision so unchanged ticks
     * skip string formatting and `TextView.setText` relayouts. Pure; host-tested.
     */
    fun quantizeAxisHundredths(raw: Float): Int {
        val clamped = clampAxis(raw)
        val rounded = (clamped * 100f).roundToInt()
        return if (rounded == 0) 0 else rounded.coerceIn(-100, 100)
    }

    /** True when the quantized `0.01` axis readout differs from [previousHundredths]. */
    fun axisReadoutChanged(previousHundredths: Int, raw: Float): Boolean =
        previousHundredths != quantizeAxisHundredths(raw)

    /** Format a quantized hundredths reading (`-100..100`) as `"+0.00"` / `"-0.75"`. */
    fun formatQuantizedAxis(hundredths: Int): String {
        val clamped = hundredths.coerceIn(-100, 100)
        val sign = if (clamped < 0) '-' else '+'
        val mag = abs(clamped)
        val whole = mag / 100
        val frac = mag % 100
        return buildString(5) {
            append(sign)
            append(whole)
            append('.')
            append(frac / 10)
            append(frac % 10)
        }
    }

    fun mappedAction(keyCode: Int, keyMap: Map<Int, Action>): Action? = keyMap[keyCode]

    /** True on short secondary panels (e.g. ~540dp Sugar bottom display). */
    fun isCompactHeight(screenHeightDp: Int): Boolean = screenHeightDp < 560

    /** Adaptive scrollable log box height that avoids pushing cards off-screen on short panels. */
    fun logBoxHeightDp(screenHeightDp: Int): Int = if (isCompactHeight(screenHeightDp)) 104 else 140
}

/**
 * Live gamepad/key probe for remapping verification. Displays framed live key
 * + mapped Ghost Galleon action badge, live hardware button matrix, bipolar
 * Stick/HAT visual axis gauges with deadzone & peak-deflection markers,
 * accent-pill bind CTA, and monospace event log.
 */
class ControllerLabActivity : AppCompatActivity() {

    private lateinit var keyLine: TextView
    private lateinit var actionLine: TextView
    private lateinit var mappedBadge: TextView
    private lateinit var deviceNameChip: TextView
    private lateinit var padCoverageBadge: TextView
    private lateinit var padCoverageBg: GradientDrawable
    private lateinit var bindBtn: TextView
    private lateinit var axisLine: TextView
    private lateinit var stickRadar: StickRadarView
    private lateinit var stickXBar: AxisBarView
    private lateinit var stickYBar: AxisBarView
    private lateinit var stickZBar: AxisBarView
    private lateinit var stickRzBar: AxisBarView
    private lateinit var hatXBar: AxisBarView
    private lateinit var hatYBar: AxisBarView
    private lateinit var l2TriggerBar: TriggerBarView
    private lateinit var r2TriggerBar: TriggerBarView
    private lateinit var stickXVal: TextView
    private lateinit var stickYVal: TextView
    private lateinit var stickZVal: TextView
    private lateinit var stickRzVal: TextView
    private lateinit var hatXVal: TextView
    private lateinit var hatYVal: TextView
    private lateinit var l2TriggerVal: TextView
    private lateinit var r2TriggerVal: TextView
    private lateinit var logCountBadge: TextView
    private lateinit var log: TextView
    private val recent = ArrayDeque<String>()
    private var totalLogEvents: Int = 0
    private var lastKeyCode: Int? = null
    private var accentColor: Int = ThemePack.GHOST.accentColor
    private var focusStrokeColor: Int = ThemePack.GHOST.accentColor
    private var accentOnCard: Int = UiDimens.ALPHA_PRIMARY
    private var accentOnSunken: Int = UiDimens.ALPHA_PRIMARY
    private var onAccent: Int = UiDimens.ALPHA_PRIMARY
    private var cardSurface: Int = ThemePack.DEFAULT_CARD_FILL
    private var sunkenSurface: Int = 0xFF101014.toInt()
    private var hairline: Int = ThemePack.HAIRLINE_LIGHT
    private var accentBorder: Int = ThemePack.HAIRLINE_LIGHT
    private var stickDeadzonePercent: Int = StickThresholds.DEFAULT_PERCENT
    private var stickReleaseThreshold: Float = 0.5f
    private var motionEnabled: Boolean = true
    private val padViews = EnumMap<PadIndicator, TextView>(PadIndicator::class.java)
    private val pressedButtons: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val pressedHatDirs: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val pressedAnalogTriggers: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val seenIndicators: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val scratchNextHat: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val scratchChangedHat: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val scratchNextTriggers: MutableSet<PadIndicator> = EnumSet.noneOf(PadIndicator::class.java)
    private val deviceNameCache = SparseArray<String>()
    private var lastShownDeviceId: Int = Int.MIN_VALUE
    private var lastRenderedSeenCount: Int = -1
    private var lastAxisLineXQ: Int = Int.MIN_VALUE
    private var lastAxisLineYQ: Int = Int.MIN_VALUE
    private var lastAxisLineHatXQ: Int = Int.MIN_VALUE
    private var lastAxisLineHatYQ: Int = Int.MIN_VALUE
    private val lastAxisQuantized = IntArray(8)
    private val lastAxisHighlighted = BooleanArray(8)

    private val integerFormat by lazy(LazyThreadSafetyMode.NONE) {
        NumberFormat.getIntegerInstance(resources.configuration.locales[0])
    }

    private val app get() = application as GhostGalleonApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyThemeFontScale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar(window)

        val tokens = ThemePack.resolve(app.settings)
        accentColor = app.settings.accentColor
        cardSurface = ThemePack.cardFill(tokens)
        sunkenSurface = ThemePack.surfaceSunken(tokens)
        hairline = ThemePack.hairlineDivider(tokens)
        accentBorder = ThemePack.accentHairline(accentColor)
        accentOnCard = ThemePack.accentTextColor(accentColor, cardSurface)
        accentOnSunken = ThemePack.accentTextColor(accentColor, sunkenSurface)
        onAccent = SettingsUiLogic.accessibleOnAccent(accentColor)
        val focusSurface = if (ThemePack.relativeLuminance(tokens.chipIdle) >
            ThemePack.relativeLuminance(cardSurface)
        ) {
            tokens.chipIdle
        } else {
            cardSurface
        }
        focusStrokeColor = OverlayPrimitives.accessibleFocusStroke(accentColor, focusSurface)
        val activeFocusRing = SettingsUiLogic.activePillFocusRing(accentColor)
        stickDeadzonePercent = app.settings.stickDeadzone
        stickReleaseThreshold = StickThresholds.release(stickDeadzonePercent)
        motionEnabled = UiDimens.areAnimationsEnabled(this)

        val dm = resources.displayMetrics
        val screenWidthDp = (dm.widthPixels / dm.density).toInt()
        val screenHeightDp = (dm.heightPixels / dm.density).toInt()
        val compact = ControllerLabLogic.isCompactHeight(screenHeightDp)
        val twoColumn = ControllerLabLogic.useTwoColumnDashboard(screenWidthDp)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            val hp = dp(if (compact) 14 else 20)
            val vp = dp(if (compact) 10 else 16)
            setPaddingRelative(hp, vp, hp, vp)
        }

        // Settings-style header with focusable vector Back button.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_PRIMARY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            background = TileBackgrounds.chromeButton(this@ControllerLabActivity, focusStrokeColor)
            val backLabel = getString(R.string.action_back)
            contentDescription = backLabel
            tooltipText = backLabel
            isFocusable = true
            setOnClickListener {
                app.playSfx(SoundCue.BACK)
                finish()
            }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headerTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(12), 0, 0, 0)
        }
        headerTexts.addView(TextView(this).apply {
            setText(R.string.controller_lab_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        headerTexts.addView(TextView(this).apply {
            setText(R.string.controller_lab_intro)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(UiDimens.ALPHA_MUTED)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        header.addView(headerTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            View(this).apply { setBackgroundColor(accentColor) },
            LinearLayout.LayoutParams(dp(40), dp(2)).apply {
                marginStart = dp(56)
                topMargin = dp(6)
                bottomMargin = dp(12)
            },
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Card 1: Live Key + Mapped Action badge + Bind CTA.
        val keyCard = labCard()
        val keyTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        keyLine = TextView(this).apply {
            text = getString(R.string.controller_key_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        keyTopRow.addView(keyLine, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mappedBadge = TextView(this).apply {
            setText(R.string.settings_unbound)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_MUTED)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = TileBackgrounds.sunkenField(this@ControllerLabActivity, cornerRadiusDp = 10)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        keyTopRow.addView(
            mappedBadge,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(UiDimens.SPACE_SM) },
        )
        keyCard.addView(
            keyTopRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        actionLine = TextView(this).apply {
            text = getString(R.string.controller_action_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            useTabularNumerals()
            setPadding(0, dp(6), 0, dp(10))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        keyCard.addView(actionLine)

        // Cached active controller name chip + 0/16 pad coverage pill above the 8+8 matrix.
        val matrixStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        deviceNameChip = TextView(this).apply {
            setText(R.string.glyph_dash)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = TileBackgrounds.sunkenField(this@ControllerLabActivity, cornerRadiusDp = 9)
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
        matrixStatusRow.addView(
            deviceNameChip,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(UiDimens.SPACE_SM)
            },
        )
        padCoverageBg = GradientDrawable().apply {
            cornerRadius = dpF(9)
        }
        padCoverageBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            useTabularNumerals()
            gravity = Gravity.CENTER
            minWidth = dp(48)
            background = padCoverageBg
            setPadding(dp(9), dp(4), dp(9), dp(4))
            OverlayPrimitives.markPoliteLiveRegion(this)
        }
        matrixStatusRow.addView(padCoverageBadge)
        keyCard.addView(
            matrixStatusRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) },
        )

        // Live 16-button hardware indicator matrix (8+8 symmetry: shoulders/thumbs/system + D-pad/face).
        padViews.clear()
        val matrixContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.sunkenField(this@ControllerLabActivity, cornerRadiusDp = 12)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun buildPadRow(items: List<PadIndicator>): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            items.forEachIndexed { idx, indicator ->
                val badge = TextView(this@ControllerLabActivity).apply {
                    text = indicator.glyph
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(typeface, Typeface.BOLD)
                    useTabularNumerals()
                    gravity = Gravity.CENTER
                    maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                padViews[indicator] = badge
                addView(
                    badge,
                    LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                        if (idx > 0) marginStart = dp(4)
                    },
                )
            }
        }
        val topPadRow = listOf(
            PadIndicator.L2,
            PadIndicator.L1,
            PadIndicator.L3,
            PadIndicator.SELECT,
            PadIndicator.START,
            PadIndicator.R3,
            PadIndicator.R1,
            PadIndicator.R2,
        )
        val bottomPadRow = listOf(
            PadIndicator.DPAD_LEFT,
            PadIndicator.DPAD_UP,
            PadIndicator.DPAD_DOWN,
            PadIndicator.DPAD_RIGHT,
            PadIndicator.Y,
            PadIndicator.X,
            PadIndicator.B,
            PadIndicator.A,
        )
        matrixContainer.addView(
            buildPadRow(topPadRow),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) },
        )
        matrixContainer.addView(
            buildPadRow(bottomPadRow),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        refreshAllPadIndicators()
        refreshPadCoverageBadge()
        keyCard.addView(
            matrixContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) },
        )

        bindBtn = TextView(this).apply {
            id = View.generateViewId()
            setText(R.string.controller_bind_last)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(onAccent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isFocusable = true
            alpha = ControllerLabLogic.bindButtonAlpha(hasLastKey = false)
            val bindLabel = getString(R.string.controller_bind_last)
            contentDescription = bindLabel
            tooltipText = bindLabel
            minHeight = dp(UiDimens.TOUCH_MIN_DP)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = TileBackgrounds.pressableRow(
                context = this@ControllerLabActivity,
                accent = activeFocusRing,
                idleBackground = TileBackgrounds.accentPill(
                    context = this@ControllerLabActivity,
                    accent = accentColor,
                    cornerRadiusDp = 14,
                ),
                cornerRadiusDp = 14,
                strokeDp = 2,
            )
            setOnClickListener { bindLastKey() }
        }
        backBtn.nextFocusDownId = bindBtn.id
        bindBtn.nextFocusUpId = backBtn.id
        keyCard.addView(
            bindBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Card 2: 2D Analog Stick XY Radar + Left/Right Stick, HAT & L2/R2 Trigger gauges.
        val axisCard = labCard()
        axisLine = TextView(this).apply {
            text = getString(R.string.controller_axes_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.MONOSPACE
            useTabularNumerals()
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8))
        }
        axisCard.addView(axisLine)

        fun buildAxisCell(
            axisConst: Int,
            showDeadzone: Boolean,
        ): Triple<LinearLayout, AxisBarView, TextView> {
            val axisTag = MotionEvent.axisToString(axisConst).removePrefix("AXIS_")
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(
                TextView(this).apply {
                    text = axisTag
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            val valTv = TextView(this).apply {
                text = ControllerLabLogic.formatQuantizedAxis(0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(UiDimens.ALPHA_SECONDARY)
                typeface = Typeface.MONOSPACE
                useTabularNumerals()
            }
            top.addView(valTv)
            col.addView(
                top,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(3) },
            )
            val bar = AxisBarView(
                context = this,
                trackColor = sunkenSurface,
                borderColor = hairline,
                fillColor = accentColor,
                hasDeadzone = showDeadzone,
                deadzonePercent = stickDeadzonePercent,
                engageTickColor = accentOnSunken,
            )
            col.addView(
                bar,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)),
            )
            return Triple(col, bar, valTv)
        }

        fun buildTriggerCell(indicator: PadIndicator): Triple<LinearLayout, TriggerBarView, TextView> {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(
                TextView(this).apply {
                    text = indicator.glyph
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            val valTv = TextView(this).apply {
                text = ControllerLabLogic.formatQuantizedTrigger(0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(UiDimens.ALPHA_SECONDARY)
                typeface = Typeface.MONOSPACE
                useTabularNumerals()
            }
            top.addView(valTv)
            col.addView(
                top,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(3) },
            )
            val bar = TriggerBarView(
                context = this,
                trackColor = sunkenSurface,
                borderColor = hairline,
                fillColor = accentColor,
                thresholdTickColor = accentOnSunken,
            )
            col.addView(
                bar,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)),
            )
            return Triple(col, bar, valTv)
        }

        val (sxCell, sxBar, sxVal) = buildAxisCell(MotionEvent.AXIS_X, showDeadzone = true)
        val (syCell, syBar, syVal) = buildAxisCell(MotionEvent.AXIS_Y, showDeadzone = true)
        val (szCell, szBar, szVal) = buildAxisCell(MotionEvent.AXIS_Z, showDeadzone = true)
        val (srzCell, srzBar, srzVal) = buildAxisCell(MotionEvent.AXIS_RZ, showDeadzone = true)
        val (hxCell, hxBar, hxVal) = buildAxisCell(MotionEvent.AXIS_HAT_X, showDeadzone = false)
        val (hyCell, hyBar, hyVal) = buildAxisCell(MotionEvent.AXIS_HAT_Y, showDeadzone = false)
        val (l2Cell, l2Bar, l2Val) = buildTriggerCell(PadIndicator.L2)
        val (r2Cell, r2Bar, r2Val) = buildTriggerCell(PadIndicator.R2)
        stickXBar = sxBar; stickXVal = sxVal
        stickYBar = syBar; stickYVal = syVal
        stickZBar = szBar; stickZVal = szVal
        stickRzBar = srzBar; stickRzVal = srzVal
        hatXBar = hxBar; hatXVal = hxVal
        hatYBar = hyBar; hatYVal = hyVal
        l2TriggerBar = l2Bar; l2TriggerVal = l2Val
        r2TriggerBar = r2Bar; r2TriggerVal = r2Val

        fun axisRow(left: View, right: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                right,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                },
            )
        }

        val radarSizePx = dp(ControllerLabLogic.radarSizeDp(screenHeightDp))
        stickRadar = StickRadarView(
            context = this,
            trackColor = sunkenSurface,
            borderColor = hairline,
            accentColor = accentColor,
            haloColor = activeFocusRing,
            deadzonePercent = stickDeadzonePercent,
        )
        val sticksStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                axisRow(sxCell, syCell),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) },
            )
            addView(
                axisRow(szCell, srzCell),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val radarAndSticksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                stickRadar,
                LinearLayout.LayoutParams(radarSizePx, radarSizePx).apply {
                    marginEnd = dp(12)
                },
            )
            addView(
                sticksStack,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        axisCard.addView(
            radarAndSticksRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) },
        )
        axisCard.addView(
            axisRow(hxCell, hyCell),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) },
        )
        axisCard.addView(
            axisRow(l2Cell, r2Cell),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Card 3: Monospace scrollable event log inside sunken field with count & 44dp Clear pill.
        val logCard = labCard()
        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        logCountBadge = TextView(this).apply {
            text = integerFormat.format(0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(accentOnSunken)
            setTypeface(typeface, Typeface.BOLD)
            useTabularNumerals()
            gravity = Gravity.CENTER
            minWidth = dp(36)
            background = TileBackgrounds.valueBadge(this@ControllerLabActivity, accentColor)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        logHeader.addView(logCountBadge)
        logHeader.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        val clearLogBtn = TextView(this).apply {
            id = View.generateViewId()
            setText(R.string.action_clear)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isFocusable = true
            val clearLabel = getString(R.string.action_clear)
            contentDescription = clearLabel
            tooltipText = clearLabel
            minHeight = dp(ControllerLabLogic.CLEAR_BUTTON_MIN_HEIGHT_DP)
            background = TileBackgrounds.pressableRow(
                context = this@ControllerLabActivity,
                accent = focusStrokeColor,
                idleBackground = TileBackgrounds.chipIdle(this@ControllerLabActivity, cornerRadiusDp = 12),
                cornerRadiusDp = 12,
                strokeDp = 2,
            )
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener {
                app.playSfx(SoundCue.DESTRUCTIVE)
                clearLabState()
            }
        }
        if (twoColumn) {
            backBtn.nextFocusRightId = clearLogBtn.id
            bindBtn.nextFocusRightId = clearLogBtn.id
            clearLogBtn.nextFocusLeftId = bindBtn.id
            clearLogBtn.nextFocusUpId = backBtn.id
        } else {
            bindBtn.nextFocusDownId = clearLogBtn.id
            clearLogBtn.nextFocusUpId = bindBtn.id
        }
        logHeader.addView(clearLogBtn)
        logCard.addView(
            logHeader,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) },
        )
        log = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.MONOSPACE
            useTabularNumerals()
            setLineSpacing(dpF(2), 1f)
            text = getString(R.string.glyph_dash)
        }
        val logBox = ScrollView(this).apply {
            background = TileBackgrounds.sunkenField(this@ControllerLabActivity, cornerRadiusDp = 12)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(14))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minimumHeight = dp(96)
            addView(
                log,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        logCard.addView(
            logBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(ControllerLabLogic.logBoxHeightDp(screenHeightDp)),
            ),
        )

        if (twoColumn) {
            val dashboardRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    keyCard,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val rightCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    axisCard,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(UiDimens.SPACE_MD) },
                )
                addView(
                    logCard,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            dashboardRow.addView(
                leftCol,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(UiDimens.SPACE_MD)
                },
            )
            dashboardRow.addView(
                rightCol,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f),
            )
            content.addView(
                dashboardRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            content.addView(
                keyCard,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(UiDimens.SPACE_MD) },
            )
            content.addView(
                axisCard,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(UiDimens.SPACE_MD) },
            )
            content.addView(
                logCard,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val outerScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(16))
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            outerScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    private fun labCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(this@ControllerLabActivity)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar(window)
        motionEnabled = UiDimens.areAnimationsEnabled(this)
        val liveDeadzone = app.settings.stickDeadzone
        stickDeadzonePercent = liveDeadzone
        stickReleaseThreshold = StickThresholds.release(liveDeadzone)
        stickRadar.updateDeadzone(liveDeadzone)
        stickXBar.updateDeadzoneMarkers(liveDeadzone)
        stickYBar.updateDeadzoneMarkers(liveDeadzone)
        stickZBar.updateDeadzoneMarkers(liveDeadzone)
        stickRzBar.updateDeadzoneMarkers(liveDeadzone)
        lastKeyCode?.let(::refreshMappedBadge)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0) app.playSfx(SoundCue.BACK)
            finish()
            return true
        }
        paintKey(keyCode, event.action, event)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        paintKey(keyCode, event.action, event)
        return true
    }

    private fun noteInputDevice(deviceId: Int) {
        if (deviceId <= 0 || deviceId == lastShownDeviceId) return
        lastShownDeviceId = deviceId
        val cached = deviceNameCache.get(deviceId)
        val resolved = if (cached != null) {
            cached
        } else {
            val fetched = runCatching {
                InputDevice.getDevice(deviceId)?.name?.trim().orEmpty()
            }.getOrDefault("")
            deviceNameCache.put(deviceId, fetched)
            fetched
        }
        if (resolved.isNotEmpty()) {
            deviceNameChip.text = resolved
            deviceNameChip.setTextColor(UiDimens.ALPHA_PRIMARY)
        }
    }

    private fun refreshPadCoverageBadge() {
        val seenCount = seenIndicators.size
        if (seenCount == lastRenderedSeenCount) return
        lastRenderedSeenCount = seenCount
        val cov = ControllerLabLogic.padCoverage(seenCount, PadIndicator.entries.size)
        padCoverageBadge.text = getString(R.string.format_progress, cov.seen, cov.total)
        when {
            cov.complete -> {
                padCoverageBg.setColor(accentColor)
                padCoverageBg.setStroke(0, Color.TRANSPARENT)
                padCoverageBadge.setTextColor(onAccent)
            }
            cov.seen > 0 -> {
                padCoverageBg.setColor(sunkenSurface)
                padCoverageBg.setStroke(strokePx(1), accentBorder)
                padCoverageBadge.setTextColor(accentOnSunken)
            }
            else -> {
                padCoverageBg.setColor(sunkenSurface)
                padCoverageBg.setStroke(strokePx(1), hairline)
                padCoverageBadge.setTextColor(UiDimens.ALPHA_MUTED)
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        ) {
            noteInputDevice(event.deviceId)
            val x = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_X))
            val y = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_Y))
            val z = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_Z))
            val rz = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_RZ))
            val hx = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_HAT_X))
            val hy = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
            val l2 = ControllerLabLogic.triggerValue(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
            )
            val r2 = ControllerLabLogic.triggerValue(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS),
            )
            val qx = ControllerLabLogic.quantizeAxisHundredths(x)
            val qy = ControllerLabLogic.quantizeAxisHundredths(y)
            val qz = ControllerLabLogic.quantizeAxisHundredths(z)
            val qrz = ControllerLabLogic.quantizeAxisHundredths(rz)
            val qhx = ControllerLabLogic.quantizeAxisHundredths(hx)
            val qhy = ControllerLabLogic.quantizeAxisHundredths(hy)
            val ql2 = ControllerLabLogic.quantizeTriggerHundredths(l2)
            val qr2 = ControllerLabLogic.quantizeTriggerHundredths(r2)
            if (qx != lastAxisLineXQ || qy != lastAxisLineYQ ||
                qhx != lastAxisLineHatXQ || qhy != lastAxisLineHatYQ
            ) {
                lastAxisLineXQ = qx
                lastAxisLineYQ = qy
                lastAxisLineHatXQ = qhx
                lastAxisLineHatYQ = qhy
                axisLine.text = getString(
                    R.string.controller_axes_values,
                    qx * 0.01f,
                    qy * 0.01f,
                    qhx * 0.01f,
                    qhy * 0.01f,
                )
            }
            stickRadar.setStick(x, y)
            updateAxisReadout(0, stickXBar, stickXVal, x, qx, stickReleaseThreshold)
            updateAxisReadout(1, stickYBar, stickYVal, y, qy, stickReleaseThreshold)
            updateAxisReadout(2, stickZBar, stickZVal, z, qz, stickReleaseThreshold)
            updateAxisReadout(3, stickRzBar, stickRzVal, rz, qrz, stickReleaseThreshold)
            updateAxisReadout(4, hatXBar, hatXVal, hx, qhx, 0.5f)
            updateAxisReadout(5, hatYBar, hatYVal, hy, qhy, 0.5f)
            updateTriggerReadout(6, l2TriggerBar, l2TriggerVal, l2, ql2)
            updateTriggerReadout(7, r2TriggerBar, r2TriggerVal, r2, qr2)

            // Sync analog L2/R2 triggers onto the 16-button pad matrix when >= 0.5f.
            scratchNextTriggers.clear()
            if (ControllerLabLogic.isTriggerPressed(l2)) scratchNextTriggers.add(PadIndicator.L2)
            if (ControllerLabLogic.isTriggerPressed(r2)) scratchNextTriggers.add(PadIndicator.R2)
            if (scratchNextTriggers != pressedAnalogTriggers) {
                val l2Changed = (PadIndicator.L2 in scratchNextTriggers) != (PadIndicator.L2 in pressedAnalogTriggers)
                val r2Changed = (PadIndicator.R2 in scratchNextTriggers) != (PadIndicator.R2 in pressedAnalogTriggers)
                pressedAnalogTriggers.clear()
                pressedAnalogTriggers.addAll(scratchNextTriggers)
                seenIndicators.addAll(scratchNextTriggers)
                if (l2Changed) stylePadIndicator(PadIndicator.L2)
                if (r2Changed) stylePadIndicator(PadIndicator.R2)
                refreshPadCoverageBadge()
            }

            ControllerLabLogic.fillPadIndicatorsForHat(hx, hy, scratchNextHat)
            if (scratchNextHat != pressedHatDirs) {
                var firstNew: PadIndicator? = null
                scratchChangedHat.clear()
                val all = PadIndicator.entries
                for (i in 0 until all.size) {
                    val ind = all[i]
                    val wasPressed = ind in pressedHatDirs
                    val isPressed = ind in scratchNextHat
                    if (wasPressed != isPressed) {
                        scratchChangedHat.add(ind)
                        if (isPressed && firstNew == null) {
                            firstNew = ind
                        }
                    }
                }
                pressedHatDirs.clear()
                pressedHatDirs.addAll(scratchNextHat)
                seenIndicators.addAll(scratchNextHat)
                for (i in 0 until all.size) {
                    val ind = all[i]
                    if (ind in scratchChangedHat) stylePadIndicator(ind)
                }
                refreshPadCoverageBadge()
                if (firstNew != null && AudioHapticCohesion.shouldEmitGamepadAudio(
                        soundEnabled = app.settings.soundEnabled,
                        actionHandled = true,
                        isKeyDown = true,
                    )
                ) {
                    val hatAction = ControllerLabLogic.mappedAction(firstNew.keyCode, app.settings.keyMap)
                    app.playSfx(
                        cue = AudioHapticCohesion.cueForKeyTest(hatAction),
                        pan = AudioHapticCohesion.panForKeyTest(hatAction),
                    )
                }
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun updateAxisReadout(
        slot: Int,
        bar: AxisBarView,
        label: TextView,
        value: Float,
        quantized: Int,
        highlightThreshold: Float,
    ) {
        bar.setAxisValue(value)
        if (lastAxisQuantized[slot] != quantized) {
            lastAxisQuantized[slot] = quantized
            label.text = ControllerLabLogic.formatQuantizedAxis(quantized)
        }
        val highlighted = abs(value) >= highlightThreshold
        if (lastAxisHighlighted[slot] != highlighted) {
            lastAxisHighlighted[slot] = highlighted
            label.setTextColor(
                if (highlighted) accentOnCard else UiDimens.ALPHA_SECONDARY,
            )
        }
    }

    private fun updateTriggerReadout(
        slot: Int,
        bar: TriggerBarView,
        label: TextView,
        value: Float,
        quantized: Int,
    ) {
        bar.setTriggerValue(value)
        if (lastAxisQuantized[slot] != quantized) {
            lastAxisQuantized[slot] = quantized
            label.text = ControllerLabLogic.formatQuantizedTrigger(quantized)
        }
        val highlighted = ControllerLabLogic.isTriggerPressed(value)
        if (lastAxisHighlighted[slot] != highlighted) {
            lastAxisHighlighted[slot] = highlighted
            label.setTextColor(
                if (highlighted) accentOnCard else UiDimens.ALPHA_SECONDARY,
            )
        }
    }

    private fun clearLabState() {
        recent.clear()
        totalLogEvents = 0
        logCountBadge.text = integerFormat.format(0)
        log.text = getString(R.string.glyph_dash)
        stickRadar.reset()
        stickXBar.resetPeaks()
        stickYBar.resetPeaks()
        stickZBar.resetPeaks()
        stickRzBar.resetPeaks()
        hatXBar.resetPeaks()
        hatYBar.resetPeaks()
        l2TriggerBar.resetPeaks()
        r2TriggerBar.resetPeaks()
        seenIndicators.clear()
        refreshAllPadIndicators()
        refreshPadCoverageBadge()
    }

    private fun refreshAllPadIndicators() {
        PadIndicator.entries.forEach(::stylePadIndicator)
    }

    private fun stylePadIndicator(indicator: PadIndicator) {
        val tv = padViews[indicator] ?: return
        val active = indicator in pressedButtons ||
            indicator in pressedHatDirs ||
            indicator in pressedAnalogTriggers
        val seen = indicator in seenIndicators
        val bg = (tv.background as? GradientDrawable)?.apply { mutate() }
            ?: GradientDrawable().also {
                it.cornerRadius = dpF(8)
                tv.background = it
            }
        when {
            active -> {
                bg.setColor(accentColor)
                bg.setStroke(0, Color.TRANSPARENT)
            }
            seen -> {
                bg.setColor(cardSurface)
                bg.setStroke(strokePx(1), accentBorder)
            }
            else -> {
                bg.setColor(sunkenSurface)
                bg.setStroke(strokePx(1), hairline)
            }
        }
        tv.setTextColor(
            when {
                active -> onAccent
                seen -> accentOnCard
                else -> UiDimens.ALPHA_MUTED
            },
        )
        val scale = if (active && motionEnabled) 1.06f else 1f
        tv.scaleX = scale
        tv.scaleY = scale
    }

    private fun bindLastKey() {
        val code = lastKeyCode
        if (code == null) {
            app.playSfx(SoundCue.ERROR_DENY)
            toast(R.string.controller_no_key)
            return
        }
        app.playSfx(SoundCue.MODAL_OPEN)
        val actions = listOf(
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.CONFIRM, Action.BACK, Action.SWAP_SCREENS, Action.TOGGLE_MODE,
            Action.OPEN_SETTINGS, Action.PAGE_PREV, Action.PAGE_NEXT, Action.OPEN_QUICK_PANEL,
            Action.SEARCH_LIBRARY, Action.TOGGLE_FAVORITE, Action.SHOW_DETAILS,
            Action.OPEN_SESSION_SWITCHER, Action.TOGGLE_PLAY_HUD,
            Action.CLAIM_HOST, Action.RELEASE_HOST, Action.TOGGLE_SEAT,
        )
        val labels = actions.map { resolveText(it.label()) }.toTypedArray()
        DeckDialogs.chooseItem(
            context = this,
            titleRes = R.string.controller_bind_title,
            items = labels,
        ) { which ->
            if (which !in actions.indices) return@chooseItem
            val next = app.settings.keyMap.toMutableMap()
            next.entries.removeAll { it.value == actions[which] }
            next[code] = actions[which]
            app.updateSettings(app.settings.copy(keyMap = next), notify = false)
            refreshMappedBadge(code)
            toast(R.string.controller_bound, resolveText(actions[which].label()), code)
        }
    }

    private fun paintKey(keyCode: Int, action: Int, event: KeyEvent) {
        noteInputDevice(event.deviceId)
        lastKeyCode = keyCode
        bindBtn.alpha = ControllerLabLogic.bindButtonAlpha(hasLastKey = true)
        if (AudioHapticCohesion.shouldEmitGamepadAudio(
                soundEnabled = app.settings.soundEnabled,
                actionHandled = true,
                isKeyDown = action == KeyEvent.ACTION_DOWN && event.repeatCount == 0,
            )
        ) {
            val mapped = ControllerLabLogic.mappedAction(keyCode, app.settings.keyMap)
            app.playSfx(
                cue = AudioHapticCohesion.cueForKeyTest(mapped),
                pan = AudioHapticCohesion.panForKeyTest(mapped),
            )
        }
        ControllerLabLogic.padIndicatorForKeyCode(keyCode)?.let { indicator ->
            if (action == KeyEvent.ACTION_DOWN) {
                pressedButtons.add(indicator)
                seenIndicators.add(indicator)
            } else if (action == KeyEvent.ACTION_UP) {
                pressedButtons.remove(indicator)
            }
            stylePadIndicator(indicator)
            refreshPadCoverageBadge()
        }
        val actionName = when (action) {
            KeyEvent.ACTION_DOWN -> getString(R.string.controller_action_down)
            KeyEvent.ACTION_UP -> getString(R.string.controller_action_up)
            else -> action.toString()
        }
        keyLine.text = getString(
            R.string.controller_key_values,
            keyCode,
            KeyEvent.keyCodeToString(keyCode),
        )
        actionLine.text = getString(
            R.string.controller_action_values,
            actionName,
            event.source,
            event.deviceId,
        )
        refreshMappedBadge(keyCode)
        pushLog(getString(
            R.string.controller_log_entry,
            actionName,
            keyCode,
            KeyEvent.keyCodeToString(keyCode),
        ))
    }

    private fun refreshMappedBadge(keyCode: Int) {
        val mapped = ControllerLabLogic.mappedAction(keyCode, app.settings.keyMap)
        if (mapped != null) {
            mappedBadge.text = resolveText(mapped.label())
            mappedBadge.setTextColor(accentOnSunken)
        } else {
            mappedBadge.setText(R.string.settings_unbound)
            mappedBadge.setTextColor(UiDimens.ALPHA_MUTED)
        }
    }

    private fun pushLog(line: String) {
        totalLogEvents++
        logCountBadge.text = integerFormat.format(totalLogEvents)
        recent.addFirst(line)
        while (recent.size > 24) recent.removeLast()
        log.text = recent.joinToString("\n")
    }

    /**
     * Lightweight center-zero bipolar gauge driven by [ControllerLabLogic.axisBarSpan],
     * with live-updatable stick deadzone release/engage markers, muted deadzone vs
     * full-bright engaged fill, and live peak-deflection ticks.
     * Preallocates all paints/rects so 120–250 Hz updates allocate zero objects.
     */
    private class AxisBarView(
        context: Context,
        trackColor: Int,
        borderColor: Int,
        fillColor: Int,
        private val hasDeadzone: Boolean,
        deadzonePercent: Int,
        engageTickColor: Int,
    ) : View(context) {
        private var deadzonePct: Int = StickThresholds.clamp(deadzonePercent)
        private var deadzoneMarkers: DeadzoneMarkers? =
            if (hasDeadzone) ControllerLabLogic.deadzoneMarkers(deadzonePct) else null
        private var clampedValue: Float = 0f
        private var startFrac: Float = 0.5f
        private var widthFrac: Float = 0f
        private var minNeg: Float = 0f
        private var maxPos: Float = 0f
        private val radius = context.dpF(7)
        private val rect = RectF()
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiDimens.ALPHA_SUBTLE
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val releaseTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val engageTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePack.withAlpha(engageTickColor, 0xAA)
            strokeWidth = context.dpF(1.5f)
        }
        private val peakTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiDimens.ALPHA_PRIMARY
            strokeWidth = context.dpF(2)
            strokeCap = Paint.Cap.ROUND
        }
        private val accessibleFill = OverlayPrimitives.accessibleFocusStroke(fillColor, trackColor)
        private val engagedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accessibleFill }
        private val deadzoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePack.withAlpha(accessibleFill, 0x73)
        }

        fun updateDeadzoneMarkers(nextDeadzonePercent: Int) {
            if (!hasDeadzone) return
            val clamped = StickThresholds.clamp(nextDeadzonePercent)
            if (clamped != deadzonePct) {
                deadzonePct = clamped
                deadzoneMarkers = ControllerLabLogic.deadzoneMarkers(clamped)
                invalidate()
            }
        }

        fun setAxisValue(raw: Float) {
            val v = ControllerLabLogic.clampAxis(raw)
            val nextMin = minOf(minNeg, v)
            val nextMax = maxOf(maxPos, v)
            if (v != clampedValue || nextMin != minNeg || nextMax != maxPos) {
                clampedValue = v
                if (v >= 0f) {
                    startFrac = 0.5f
                    widthFrac = v * 0.5f
                } else {
                    startFrac = 0.5f + v * 0.5f
                    widthFrac = -v * 0.5f
                }
                minNeg = nextMin
                maxPos = nextMax
                invalidate()
            }
        }

        fun resetPeaks() {
            if (minNeg != 0f || maxPos != 0f) {
                minNeg = 0f
                maxPos = 0f
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)
            if (widthFrac > 0.005f) {
                val left = w * startFrac
                val right = w * (startFrac + widthFrac)
                rect.set(left, 0f, right, h)
                val activePaint = if (!hasDeadzone || ControllerLabLogic.axisEngaged(clampedValue, deadzonePct)) {
                    engagedFillPaint
                } else {
                    deadzoneFillPaint
                }
                canvas.drawRoundRect(rect, radius, radius, activePaint)
            }
            deadzoneMarkers?.let { dz ->
                val insetY = h * 0.22f
                canvas.drawLine(w * dz.releaseFracNeg, insetY, w * dz.releaseFracNeg, h - insetY, releaseTickPaint)
                canvas.drawLine(w * dz.releaseFracPos, insetY, w * dz.releaseFracPos, h - insetY, releaseTickPaint)
                val engInsetY = h * 0.14f
                canvas.drawLine(w * dz.engageFracNeg, engInsetY, w * dz.engageFracNeg, h - engInsetY, engageTickPaint)
                canvas.drawLine(w * dz.engageFracPos, engInsetY, w * dz.engageFracPos, h - engInsetY, engageTickPaint)
            }
            val midX = w * 0.5f
            canvas.drawLine(midX, 0f, midX, h, centerPaint)
            val peakPad = h * 0.16f
            if (minNeg < -0.02f) {
                val px = w * ControllerLabLogic.valueToBarFrac(minNeg)
                canvas.drawLine(px, peakPad, px, h - peakPad, peakTickPaint)
            }
            if (maxPos > 0.02f) {
                val px = w * ControllerLabLogic.valueToBarFrac(maxPos)
                canvas.drawLine(px, peakPad, px, h - peakPad, peakTickPaint)
            }
            val inset = borderPaint.strokeWidth * 0.5f
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    /**
     * Zero-allocation unipolar `[0..1]` trigger pull bar (`L2`/`R2`) with press
     * threshold marker (`0.50`) and peak-pull tick.
     */
    private class TriggerBarView(
        context: Context,
        trackColor: Int,
        borderColor: Int,
        fillColor: Int,
        thresholdTickColor: Int,
    ) : View(context) {
        private var clampedValue: Float = 0f
        private var maxPull: Float = 0f
        private val radius = context.dpF(7)
        private val rect = RectF()
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePack.withAlpha(thresholdTickColor, 0x99)
            strokeWidth = context.dpF(1.25f)
        }
        private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiDimens.ALPHA_PRIMARY
            strokeWidth = context.dpF(2)
            strokeCap = Paint.Cap.ROUND
        }
        private val accessibleFill = OverlayPrimitives.accessibleFocusStroke(fillColor, trackColor)
        private val pressedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accessibleFill }
        private val partialFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePack.withAlpha(accessibleFill, 0x73)
        }

        fun setTriggerValue(raw: Float) {
            val v = ControllerLabLogic.clampTrigger(raw)
            val nextPeak = maxOf(maxPull, v)
            if (v != clampedValue || nextPeak != maxPull) {
                clampedValue = v
                maxPull = nextPeak
                invalidate()
            }
        }

        fun resetPeaks() {
            if (maxPull != 0f) {
                maxPull = 0f
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)
            if (clampedValue > 0.005f) {
                val fillW = (w * clampedValue).coerceAtLeast(h * 0.5f).coerceAtMost(w)
                rect.set(0f, 0f, fillW, h)
                val paint = if (ControllerLabLogic.isTriggerPressed(clampedValue)) {
                    pressedFillPaint
                } else {
                    partialFillPaint
                }
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            val thrX = w * ControllerLabLogic.TRIGGER_PRESS_THRESHOLD
            canvas.drawLine(thrX, h * 0.16f, thrX, h * 0.84f, thresholdPaint)
            if (maxPull > 0.02f) {
                val px = (w * maxPull).coerceIn(borderPaint.strokeWidth, w - borderPaint.strokeWidth)
                canvas.drawLine(px, h * 0.16f, px, h * 0.84f, peakPaint)
            }
            val inset = borderPaint.strokeWidth * 0.5f
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    /**
     * Zero-allocation 2D analog stick XY radar & circularity visualizer:
     * outer unit range ring, crosshairs, shaded release/engage deadzone box,
     * preallocated 8-point fading trail ring buffer, and live accent dot.
     */
    private class StickRadarView(
        context: Context,
        trackColor: Int,
        borderColor: Int,
        accentColor: Int,
        haloColor: Int,
        deadzonePercent: Int,
    ) : View(context) {
        private var releaseFrac: Float = StickThresholds.release(deadzonePercent)
        private var engageFrac: Float = StickThresholds.engage(deadzonePercent)
        private var currentX: Float = 0f
        private var currentY: Float = 0f
        private val trailX = FloatArray(TRAIL_CAPACITY)
        private val trailY = FloatArray(TRAIL_CAPACITY)
        private var trailHead: Int = 0
        private var trailCount: Int = 0

        private val rect = RectF()
        private val cornerR = context.dpF(12)
        private val pad = context.dpF(8)
        private val dotR = context.dpF(4.5f)
        private val trailDotR = context.dpF(2.5f)
        private val haloOffset = context.dpF(1.5f)

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x26FFFFFF
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val releaseBoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x16FFFFFF
            style = Paint.Style.FILL
        }
        private val accessibleAccent = OverlayPrimitives.accessibleFocusStroke(accentColor, trackColor)
        private val engageBoxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePack.withAlpha(accessibleAccent, 0x66)
            style = Paint.Style.STROKE
            strokeWidth = context.strokePx(1).toFloat()
        }
        private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accessibleAccent }
        private val dotIdlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemePack.withAlpha(accessibleAccent, 0x88)
        }
        private val dotEngagedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accessibleAccent
        }
        private val dotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = haloColor
            style = Paint.Style.STROKE
            strokeWidth = context.dpF(1.5f)
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun updateDeadzone(nextDeadzonePercent: Int) {
            val nextRel = StickThresholds.release(nextDeadzonePercent)
            val nextEng = StickThresholds.engage(nextDeadzonePercent)
            if (nextRel != releaseFrac || nextEng != engageFrac) {
                releaseFrac = nextRel
                engageFrac = nextEng
                invalidate()
            }
        }

        fun setStick(rawX: Float, rawY: Float) {
            val nx = ControllerLabLogic.clampAxis(rawX)
            val ny = ControllerLabLogic.clampAxis(rawY)
            if (abs(nx - currentX) < 0.004f && abs(ny - currentY) < 0.004f) return
            currentX = nx
            currentY = ny
            trailX[trailHead] = nx
            trailY[trailHead] = ny
            trailHead = (trailHead + 1) % TRAIL_CAPACITY
            if (trailCount < TRAIL_CAPACITY) trailCount++
            invalidate()
        }

        fun reset() {
            currentX = 0f
            currentY = 0f
            trailHead = 0
            trailCount = 0
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, cornerR, cornerR, bgPaint)

            val cx = w * 0.5f
            val cy = h * 0.5f
            val r = (minOf(w, h) * 0.5f - pad).coerceAtLeast(1f)

            // Hairline crosshairs and outer circularity range ring.
            canvas.drawLine(cx - r, cy, cx + r, cy, crosshairPaint)
            canvas.drawLine(cx, cy - r, cx, cy + r, crosshairPaint)
            canvas.drawCircle(cx, cy, r, ringPaint)

            // Shaded release deadzone box + engage threshold box.
            val relHalf = r * releaseFrac
            rect.set(cx - relHalf, cy - relHalf, cx + relHalf, cy + relHalf)
            canvas.drawRect(rect, releaseBoxFillPaint)
            val engHalf = r * engageFrac
            rect.set(cx - engHalf, cy - engHalf, cx + engHalf, cy + engHalf)
            canvas.drawRect(rect, engageBoxStrokePaint)

            // Fading 8-point sweep trail (oldest to newest, zero heap allocations).
            if (trailCount > 1) {
                val oldest = (trailHead - trailCount + TRAIL_CAPACITY) % TRAIL_CAPACITY
                for (i in 0 until trailCount - 1) {
                    val idx = (oldest + i) % TRAIL_CAPACITY
                    val frac = (i + 1).toFloat() / trailCount.toFloat()
                    trailPaint.alpha = (frac * 150f).toInt().coerceIn(20, 160)
                    val px = cx + trailX[idx] * r
                    val py = cy + trailY[idx] * r
                    canvas.drawCircle(px, py, trailDotR * (0.65f + 0.35f * frac), trailPaint)
                }
            }

            // Live stick position dot (muted inside deadzone, full bright + halo when engaged).
            val dotX = cx + currentX * r
            val dotY = cy + currentY * r
            val engaged = ControllerLabLogic.isStickEngaged(currentX, currentY, engageFrac)
            canvas.drawCircle(dotX, dotY, dotR, if (engaged) dotEngagedPaint else dotIdlePaint)
            if (engaged) {
                canvas.drawCircle(dotX, dotY, dotR + haloOffset, dotHaloPaint)
            }

            val inset = borderPaint.strokeWidth * 0.5f
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)
        }

        private companion object {
            const val TRAIL_CAPACITY = 8
        }
    }
}
