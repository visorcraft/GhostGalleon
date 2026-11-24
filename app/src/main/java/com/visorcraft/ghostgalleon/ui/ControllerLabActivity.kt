package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.settings.SettingsUiLogic
import java.util.Locale

/** Pure bipolar center-zero bar geometry (`[0..1]` fractions of track width). */
internal data class AxisBarSpan(
    val startFrac: Float,
    val widthFrac: Float,
    val clampedValue: Float,
)

/**
 * Pure, host-tested diagnostics math for [ControllerLabActivity].
 */
internal object ControllerLabLogic {

    fun clampAxis(raw: Float): Float =
        if (raw.isNaN() || raw.isInfinite()) 0f else raw.coerceIn(-1f, 1f)

    fun axisBarSpan(raw: Float): AxisBarSpan {
        val v = clampAxis(raw)
        return if (v >= 0f) {
            AxisBarSpan(startFrac = 0.5f, widthFrac = v * 0.5f, clampedValue = v)
        } else {
            AxisBarSpan(startFrac = 0.5f + v * 0.5f, widthFrac = -v * 0.5f, clampedValue = v)
        }
    }

    fun mappedAction(keyCode: Int, keyMap: Map<Int, Action>): Action? = keyMap[keyCode]
}

/**
 * Live gamepad/key probe for remapping verification. Displays framed live key
 * + mapped Ghost Galleon action badge, bipolar Stick/HAT visual axis gauges,
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
    private lateinit var log: TextView
    private val recent = ArrayDeque<String>()
    private var lastKeyCode: Int? = null
    private var accentColor: Int = ThemePack.GHOST.accentColor
    private var accentOnSunken: Int = UiDimens.ALPHA_PRIMARY

    private val app get() = application as GhostGalleonApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyThemeFontScale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar(window)

        val tokens = ThemePack.resolve(app.settings)
        accentColor = app.settings.accentColor
        val sunkenSurface = ThemePack.surfaceSunken(tokens)
        val hairline = ThemePack.hairlineDivider(tokens)
        accentOnSunken = ThemePack.accentTextColor(accentColor, sunkenSurface)
        val onAccent = SettingsUiLogic.accessibleOnAccent(accentColor)
        val activeFocusRing = SettingsUiLogic.activePillFocusRing(accentColor)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        // Settings-style header with focusable vector Back button.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_PRIMARY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            background = TileBackgrounds.chromeButton(this@ControllerLabActivity, accentColor)
            contentDescription = getString(R.string.action_back)
            isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headerTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
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
            setPadding(0, dp(6), 0, dp(12))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        keyCard.addView(actionLine)
        val bindBtn = TextView(this).apply {
            setText(R.string.controller_bind_last)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(onAccent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isFocusable = true
            minHeight = dp(44)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = TileBackgrounds.focusableRow(
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

        // Card 2: Analog & HAT visual center-zero bars.
        val axisCard = labCard()
        axisLine = TextView(this).apply {
            text = getString(R.string.controller_axes_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        }
        axisCard.addView(axisLine)

        fun buildAxisCell(axisConst: Int): Triple<LinearLayout, AxisBarView, TextView> {
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
            }
            top.addView(valTv)
            col.addView(
                top,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(4) },
            )
            val bar = AxisBarView(this, sunkenSurface, hairline, accentColor)
            col.addView(
                bar,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)),
            )
            return Triple(col, bar, valTv)
        }

        val (sxCell, sxBar, sxVal) = buildAxisCell(MotionEvent.AXIS_X)
        val (syCell, syBar, syVal) = buildAxisCell(MotionEvent.AXIS_Y)
        val (hxCell, hxBar, hxVal) = buildAxisCell(MotionEvent.AXIS_HAT_X)
        val (hyCell, hyBar, hyVal) = buildAxisCell(MotionEvent.AXIS_HAT_Y)
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

        // Card 3: Monospace scrollable event log inside sunken field.
        val logCard = labCard()
        log = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dpF(2), 1f)
            text = getString(R.string.glyph_dash)
        }
        val logBox = ScrollView(this).apply {
            background = TileBackgrounds.sunkenField(this@ControllerLabActivity, cornerRadiusDp = 12)
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)),
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
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
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
            stickXBar.setAxisValue(x); stickXVal.text = formatAxisValue(x)
            stickYBar.setAxisValue(y); stickYVal.text = formatAxisValue(y)
            hatXBar.setAxisValue(hx); hatXVal.text = formatAxisValue(hx)
            hatYBar.setAxisValue(hy); hatYVal.text = formatAxisValue(hy)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun bindLastKey() {
        val code = lastKeyCode
        if (code == null) {
            Toast.makeText(this, R.string.controller_no_key, Toast.LENGTH_SHORT).show()
            return
        }
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
        recent.addFirst(line)
        while (recent.size > 24) recent.removeLast()
        log.text = recent.joinToString("\n")
    }

    private fun formatAxisValue(value: Float): String =
        String.format(Locale.US, "%+.2f", value)

    /** Lightweight center-zero bipolar gauge driven by [ControllerLabLogic.axisBarSpan]. */
    private class AxisBarView(
        context: Context,
        trackColor: Int,
        borderColor: Int,
        fillColor: Int,
    ) : View(context) {
        private var span = ControllerLabLogic.axisBarSpan(0f)
        private val radius = context.dpF(6)
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
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }

        fun setAxisValue(raw: Float) {
            val next = ControllerLabLogic.axisBarSpan(raw)
            if (next != span) {
                span = next
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
            val midX = w * 0.5f
            canvas.drawLine(midX, 0f, midX, h, centerPaint)
            val inset = borderPaint.strokeWidth * 0.5f
            rect.set(inset, inset, w - inset, h - inset)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }
}
