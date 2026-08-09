package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.visorcraft.ghostgalleon.R

object HintBar {

    fun textFor(context: Context, dockFocused: Boolean): String = context.getString(
        if (dockFocused) R.string.deck_hint_dock else R.string.deck_hint_default,
    )

    fun moveText(context: Context): String = context.getString(R.string.deck_hint_move)

    fun build(context: Context): View {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            setText(R.string.deck_hint_default)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (12 * density).toInt())
        }
    }
}
