package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF

private val iconButtonInterpolator = DecelerateInterpolator()

private val ICON_BUTTON_ROLE_DELEGATE = object : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfo,
    ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
    }
}

/**
 * 40dp circular chrome button: 24dp vector with 8dp padding over a theme-synced
 * translucent scrim plate with hairline border, touch ripple, shared oval
 * elevation outline, unified press+focus scale state machine, localized tooltip,
 * and shared TalkBack Button role delegate.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun iconButton(
    context: Context,
    iconRes: Int,
    description: String,
    focusable: Boolean = false,
    iconTint: Int? = null,
    elevationDp: Float = SystemChromeMetrics.CHROME_ELEVATION_DP,
    pressScale: Float = SystemChromeMetrics.PRESS_SCALE,
    onClick: () -> Unit,
): ImageView {
    val pad = context.dp(8)
    var pressed = false
    var focused = false
    return ImageView(context).apply {
        setImageResource(iconRes)
        if (iconTint != null) {
            setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
        }
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = description
        tooltipText = description
        background = TileBackgrounds.chromeButton(context)
        elevation = context.dpF(elevationDp)
        outlineProvider = OutlineCache.OVAL
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = focusable
        accessibilityDelegate = ICON_BUTTON_ROLE_DELEGATE
        fun updateScale() {
            val scale = SystemChromeMetrics.buttonScale(
                pressed = pressed,
                focused = focused,
                pressScale = pressScale,
                focusScale = SystemChromeMetrics.FOCUS_SCALE,
            )
            animate().cancel()
            if (!MotionPolicy.shouldAnimate(
                    SystemChromeMetrics.PRESS_ANIM_MS,
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
                .setDuration(SystemChromeMetrics.PRESS_ANIM_MS)
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
                pressed = false
                focused = false
                v.animate().cancel()
                v.scaleX = 1f
                v.scaleY = 1f
            }
        })
        setOnClickListener { onClick() }
    }
}
