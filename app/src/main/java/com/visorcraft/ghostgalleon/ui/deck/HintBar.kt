package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp

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
        val padH = context.dp(UiDimens.SPACE_LG)
        return TextView(context).apply {
            text = textFor(context, dockFocused = false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            minimumHeight = context.dp(20)
            setPadding(padH, context.dp(6), padH, context.dp(8))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                10,
                13,
                1,
                TypedValue.COMPLEX_UNIT_SP,
            )
        }
    }
}
