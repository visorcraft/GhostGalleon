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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.AudioHapticCohesion
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.input.StickThresholds
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.settings.SettingsUiLogic
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

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

/** Standard handheld gamepad indicators illuminated live in Controller Lab. */
internal enum class PadIndicator(val keyCode: Int, val glyph: String) {
    L2(KeyEvent.KEYCODE_BUTTON_L2, "L2"),
    L1(KeyEvent.KEYCODE_BUTTON_L1, "L1"),
    SELECT(KeyEvent.KEYCODE_BUTTON_SELECT, "SL"),
    START(KeyEvent.KEYCODE_BUTTON_START, "ST"),
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

    fun clampAxis(raw: Float): Float =
        if (raw.isNaN() || raw.isInfinite()) 0f else raw.coerceIn(-1f, 1f)

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
        val x = clampAxis(hatX)
        val y = clampAxis(hatY)
        val out = mutableSetOf<PadIndicator>()
        if (x <= -threshold) out.add(PadIndicator.DPAD_LEFT)
        if (x >= threshold) out.add(PadIndicator.DPAD_RIGHT)
        if (y <= -threshold) out.add(PadIndicator.DPAD_UP)
        if (y >= threshold) out.add(PadIndicator.DPAD_DOWN)
        return out
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
    private lateinit var axisLine: TextView
    private lateinit var stickXBar: AxisBarView
    private lateinit var stickYBar: AxisBarView
    private lateinit var hatXBar: AxisBarView
    private lateinit var hatYBar: AxisBarView
    private lateinit var stickXVal: TextView
    private lateinit var stickYVal: TextView
    private lateinit var hatXVal: TextView
    private lateinit var hatYVal: TextView
    private lateinit var logCountBadge: TextView
    private lateinit var log: TextView
    private val recent = ArrayDeque<String>()
    private var totalLogEvents: Int = 0
    private var lastKeyCode: Int? = null
    private var accentColor: Int = ThemePack.GHOST.accentColor
    private var accentOnCard: Int = UiDimens.ALPHA_PRIMARY
    private var accentOnSunken: Int = UiDimens.ALPHA_PRIMARY
    private var onAccent: Int = UiDimens.ALPHA_PRIMARY
    private var cardSurface: Int = ThemePack.DEFAULT_CARD_FILL
    private var sunkenSurface: Int = 0xFF101014.toInt()
    private var hairline: Int = ThemePack.HAIRLINE_LIGHT
    private var accentBorder: Int = ThemePack.HAIRLINE_LIGHT
    private var stickReleaseThreshold: Float = 0.5f
    private var motionEnabled: Boolean = true
    private val padViews = mutableMapOf<PadIndicator, TextView>()
    private val pressedButtons = mutableSetOf<PadIndicator>()
    private val pressedHatDirs = mutableSetOf<PadIndicator>()
    private val seenIndicators = mutableSetOf<PadIndicator>()

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
        val activeFocusRing = SettingsUiLogic.activePillFocusRing(accentColor)
        val deadzonePercent = app.settings.stickDeadzone
        stickReleaseThreshold = StickThresholds.release(deadzonePercent)
        motionEnabled = UiDimens.areAnimationsEnabled(this)

        val dm = resources.displayMetrics
        val screenHeightDp = (dm.heightPixels / dm.density).toInt()
        val compact = ControllerLabLogic.isCompactHeight(screenHeightDp)
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
            background = TileBackgrounds.chromeButton(this@ControllerLabActivity, accentColor)
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

        // Live hardware button indicator matrix (shoulders/system + D-pad/face).
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
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
                        if (idx > 0) marginStart = dp(5)
                    },
                )
            }
        }
        val topPadRow = listOf(
            PadIndicator.L2,
            PadIndicator.L1,
            PadIndicator.SELECT,
            PadIndicator.START,
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
        keyCard.addView(
            matrixContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) },
        )

        val bindBtn = TextView(this).apply {
            id = View.generateViewId()
            setText(R.string.controller_bind_last)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(onAccent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isFocusable = true
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
        content.addView(
            keyCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(UiDimens.SPACE_MD) },
        )

        // Card 2: Analog & HAT visual center-zero bars with deadzone & peak markers.
        val axisCard = labCard()
        axisLine = TextView(this).apply {
            text = getString(R.string.controller_axes_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.MONOSPACE
            useTabularNumerals()
            setPadding(0, 0, 0, dp(10))
        }
        axisCard.addView(axisLine)

        val deadzoneTicks = ControllerLabLogic.deadzoneMarkers(deadzonePercent)

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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    setTypeface(typeface, Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            val valTv = TextView(this).apply {
                text = formatAxisValue(0f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
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
                ).apply { bottomMargin = dp(4) },
            )
            val bar = AxisBarView(
                context = this,
                trackColor = sunkenSurface,
                borderColor = hairline,
                fillColor = accentColor,
                deadzoneMarkers = if (showDeadzone) deadzoneTicks else null,
                engageTickColor = accentOnSunken,
            )
            col.addView(
                bar,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)),
            )
            return Triple(col, bar, valTv)
        }

        val (sxCell, sxBar, sxVal) = buildAxisCell(MotionEvent.AXIS_X, showDeadzone = true)
        val (syCell, syBar, syVal) = buildAxisCell(MotionEvent.AXIS_Y, showDeadzone = true)
        val (hxCell, hxBar, hxVal) = buildAxisCell(MotionEvent.AXIS_HAT_X, showDeadzone = false)
        val (hyCell, hyBar, hyVal) = buildAxisCell(MotionEvent.AXIS_HAT_Y, showDeadzone = false)
        stickXBar = sxBar; stickXVal = sxVal
        stickYBar = syBar; stickYVal = syVal
        hatXBar = hxBar; hatXVal = hxVal
        hatYBar = hyBar; hatYVal = hyVal

        fun axisRow(left: View, right: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                right,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(UiDimens.SPACE_MD)
                },
            )
        }

        axisCard.addView(
            axisRow(sxCell, syCell),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) },
        )
        axisCard.addView(
            axisRow(hxCell, hyCell),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            axisCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(UiDimens.SPACE_MD) },
        )

        // Card 3: Monospace scrollable event log inside sunken field with count & Clear pill.
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
            minHeight = dp(36)
            background = TileBackgrounds.pressableRow(
                context = this@ControllerLabActivity,
                accent = accentColor,
                idleBackground = TileBackgrounds.chipIdle(this@ControllerLabActivity, cornerRadiusDp = 12),
                cornerRadiusDp = 12,
                strokeDp = 2,
            )
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                app.playSfx(SoundCue.DESTRUCTIVE)
                clearLabState()
            }
        }
        bindBtn.nextFocusDownId = clearLogBtn.id
        clearLogBtn.nextFocusUpId = bindBtn.id
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
        content.addView(
            logCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

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
        stickReleaseThreshold = StickThresholds.release(app.settings.stickDeadzone)
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        ) {
            val x = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_X))
            val y = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_Y))
            val hx = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_HAT_X))
            val hy = ControllerLabLogic.clampAxis(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
            axisLine.text = getString(R.string.controller_axes_values, x, y, hx, hy)
            updateAxisReadout(stickXBar, stickXVal, x, stickReleaseThreshold)
            updateAxisReadout(stickYBar, stickYVal, y, stickReleaseThreshold)
            updateAxisReadout(hatXBar, hatXVal, hx, 0.5f)
            updateAxisReadout(hatYBar, hatYVal, hy, 0.5f)
            val nextHat = ControllerLabLogic.padIndicatorsForHat(hx, hy)
            if (nextHat != pressedHatDirs) {
                val newlyPressed = nextHat - pressedHatDirs
                val changed = (pressedHatDirs union nextHat)
                pressedHatDirs.clear()
                pressedHatDirs.addAll(nextHat)
                seenIndicators.addAll(nextHat)
                changed.forEach(::stylePadIndicator)
                val firstNew = newlyPressed.firstOrNull()
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
        bar: AxisBarView,
        label: TextView,
        value: Float,
        highlightThreshold: Float,
    ) {
        bar.setAxisValue(value)
        label.text = formatAxisValue(value)
        label.setTextColor(
            if (abs(value) >= highlightThreshold) accentOnCard else UiDimens.ALPHA_SECONDARY,
        )
    }

    private fun clearLabState() {
        recent.clear()
        totalLogEvents = 0
        logCountBadge.text = integerFormat.format(0)
        log.text = getString(R.string.glyph_dash)
        stickXBar.resetPeaks()
        stickYBar.resetPeaks()
        hatXBar.resetPeaks()
        hatYBar.resetPeaks()
        seenIndicators.clear()
        refreshAllPadIndicators()
    }

    private fun refreshAllPadIndicators() {
        PadIndicator.entries.forEach(::stylePadIndicator)
    }

    private fun stylePadIndicator(indicator: PadIndicator) {
        val tv = padViews[indicator] ?: return
        val active = indicator in pressedButtons || indicator in pressedHatDirs
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
            Toast.makeText(this, R.string.controller_no_key, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(
                this,
                getString(R.string.controller_bound, resolveText(actions[which].label()), code),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun paintKey(keyCode: Int, action: Int, event: KeyEvent) {
        lastKeyCode = keyCode
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

    private fun formatAxisValue(value: Float): String =
        String.format(Locale.US, "%+.2f", value)

    /**
     * Lightweight center-zero bipolar gauge driven by [ControllerLabLogic.axisBarSpan],
     * with symmetric stick deadzone release/engage markers and live peak-deflection ticks.
     */
    private class AxisBarView(
        context: Context,
        trackColor: Int,
        borderColor: Int,
        fillColor: Int,
        private val deadzoneMarkers: DeadzoneMarkers?,
        engageTickColor: Int,
    ) : View(context) {
        private var span = ControllerLabLogic.axisBarSpan(0f)
        private var peaks = AxisPeakState()
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
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }

        fun setAxisValue(raw: Float) {
            val next = ControllerLabLogic.axisBarSpan(raw)
            val nextPeaks = ControllerLabLogic.updatePeaks(peaks, raw)
            if (next != span || nextPeaks != peaks) {
                span = next
                peaks = nextPeaks
                invalidate()
            }
        }

        fun resetPeaks() {
            if (peaks != AxisPeakState()) {
                peaks = AxisPeakState()
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, radius, radius, trackPaint)
            if (span.widthFrac > 0.005f) {
                val left = w * span.startFrac
                val right = w * (span.startFrac + span.widthFrac)
                rect.set(left, 0f, right, h)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
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
            if (peaks.minNeg < -0.02f) {
                val px = w * ControllerLabLogic.valueToBarFrac(peaks.minNeg)
                canvas.drawLine(px, peakPad, px, h - peakPad, peakTickPaint)
            }
            if (peaks.maxPos > 0.02f) {
                val px = w * ControllerLabLogic.valueToBarFrac(peaks.maxPos)
                canvas.drawLine(px, peakPad, px, h - peakPad, peakTickPaint)
            }
            val inset = borderPaint.strokeWidth * 0.5f
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }
}
