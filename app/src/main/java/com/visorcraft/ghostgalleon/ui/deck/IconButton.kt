package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.widget.ImageView
import com.visorcraft.ghostgalleon.ui.dp

/**
 * 40dp circular chrome button: 24dp vector with 8dp padding over a translucent
 * scrim plate with hairline border, touch ripple, and optional focus ring.
 */
internal fun iconButton(
    context: Context,
    iconRes: Int,
    description: String,
    focusable: Boolean = false,
    onClick: () -> Unit,
): ImageView {
    val pad = context.dp(8)
    return ImageView(context).apply {
        setImageResource(iconRes)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = description
        background = TileBackgrounds.chromeButton(context)
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = focusable
        setOnClickListener { onClick() }
    }
}
