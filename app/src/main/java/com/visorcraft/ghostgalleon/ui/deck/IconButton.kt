package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Outline
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF

private val iconButtonInterpolator = DecelerateInterpolator()

/**
 * 40dp circular chrome button: 24dp vector with 8dp padding over a translucent
 * scrim plate with hairline border, touch ripple, circular elevation shadow,
 * tactile press/focus scale feedback, localized tooltip, and TalkBack Button role.
 */
internal fun iconButton(
    context: Context,
    iconRes: Int,
    description: String,
    focusable: Boolean = false,
    onClick: () -> Unit,
): ImageView {
    val pad = context.dp(8)
    var pressed = false
    var focused = false
    return ImageView(context).apply {
        setImageResource(iconRes)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = description
        tooltipText = description
        background = TileBackgrounds.chromeButton(context)
        elevation = context.dpF(4)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = focusable
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
        fun updateScale() {
            val scale = CompanionChromeErgonomics.targetScale(
                pressed = pressed,
                focused = focused,
                pressScale = CompanionChromeErgonomics.ICON_PRESS_SCALE,
                focusScale = CompanionChromeErgonomics.ICON_FOCUS_SCALE,
            )
            animate().cancel()
            if (!MotionPolicy.shouldAnimate(
                    CompanionChromeErgonomics.SCALE_ANIM_MS,
                    ValueAnimator.areAnimatorsEnabled(),
                    isAttachedToWindow,
                )
            ) {
                scaleX = scale
                scaleY = scale
                return
            }
            animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(CompanionChromeErgonomics.SCALE_ANIM_MS)
                .setInterpolator(iconButtonInterpolator)
                .start()
        }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true
                    updateScale()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pressed) {
                        pressed = false
                        updateScale()
                    }
                }
            }
            false
        }
        setOnFocusChangeListener { _, hasFocus ->
            focused = hasFocus
            updateScale()
        }
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.animate().cancel()
                v.scaleX = 1f
                v.scaleY = 1f
            }
        })
        setOnClickListener { onClick() }
    }
}
