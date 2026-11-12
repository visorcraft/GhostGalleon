package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.SurfaceMode

object HintBar {

    /** SINGLE has no second display: X cannot swap, so the hint omits it. */
    private fun isSingleDisplay(context: Context): Boolean =
        (context.applicationContext as? GhostGalleonApp)
            ?.displayConfig?.mode == SurfaceMode.SINGLE

    fun textFor(context: Context, dockFocused: Boolean): String = when {
        dockFocused -> context.getString(R.string.deck_hint_dock)
        isSingleDisplay(context) ->
            context.getString(R.string.deck_hint_default_single)
        else -> context.getString(R.string.deck_hint_default)
    }

    fun moveText(context: Context): String = context.getString(R.string.deck_hint_move)

    fun build(context: Context): View {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = textFor(context, dockFocused = false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (12 * density).toInt())
        }
    }
}
