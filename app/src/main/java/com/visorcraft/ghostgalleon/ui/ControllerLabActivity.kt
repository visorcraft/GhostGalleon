package com.visorcraft.ghostgalleon.ui

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.label
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.resolveText

/**
 * Live gamepad/key probe for remapping verification. Shows last key code,
 * action, and stick axes.
 */
class ControllerLabActivity : AppCompatActivity() {

    private lateinit var keyLine: TextView
    private lateinit var actionLine: TextView
    private lateinit var axisLine: TextView
    private lateinit var log: TextView
    private val recent = ArrayDeque<String>()
    private var lastKeyCode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar(window)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(this.dp(24), this.dp(24), this.dp(24), this.dp(24))
        }
        root.addView(TextView(this).apply {
            setText(R.string.controller_lab_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            setText(R.string.controller_lab_intro)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, this.dp(8), 0, this.dp(16))
        })
        keyLine = line(this, getString(R.string.controller_key_empty))
        actionLine = line(this, getString(R.string.controller_action_empty))
        axisLine = line(this, getString(R.string.controller_axes_empty))
        root.addView(keyLine)
        root.addView(actionLine)
        root.addView(axisLine)
        root.addView(TextView(this).apply {
            setText(R.string.controller_bind_last)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, this.dp(16), 0, this.dp(8))
            setOnClickListener { bindLastKey() }
        })
        log = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x88FFFFFF.toInt())
            setPadding(0, this.dp(16), 0, 0)
        }
        val scroll = ScrollView(this).apply {
            addView(log, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        setContentView(root)
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
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            axisLine.text = getString(R.string.controller_axes_values, x, y, hx, hy)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun bindLastKey() {
        val code = lastKeyCode
        if (code == null) {
            android.widget.Toast.makeText(this, R.string.controller_no_key, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val actions = listOf(
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.CONFIRM, Action.BACK, Action.SWAP_SCREENS, Action.TOGGLE_MODE,
            Action.OPEN_SETTINGS, Action.PAGE_PREV, Action.PAGE_NEXT, Action.OPEN_QUICK_PANEL,
            Action.SEARCH_LIBRARY, Action.TOGGLE_FAVORITE, Action.SHOW_DETAILS,
        )
        val labels = actions.map { resolveText(it.label()) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.controller_bind_title)
            .setItems(labels) { _, which ->
                if (which !in actions.indices) return@setItems
                val app = application as GhostGalleonApp
                val next = app.settings.keyMap.toMutableMap()
                next.entries.removeAll { it.value == actions[which] }
                next[code] = actions[which]
                app.updateSettings(app.settings.copy(keyMap = next), notify = false)
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.controller_bound, resolveText(actions[which].label()), code),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
        pushLog(getString(
            R.string.controller_log_entry,
            actionName,
            keyCode,
            KeyEvent.keyCodeToString(keyCode),
        ))
    }

    private fun pushLog(line: String) {
        recent.addFirst(line)
        while (recent.size > 24) recent.removeLast()
        log.text = recent.joinToString("\n")
    }

    private fun line(ctx: android.content.Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 8)
        }
}
