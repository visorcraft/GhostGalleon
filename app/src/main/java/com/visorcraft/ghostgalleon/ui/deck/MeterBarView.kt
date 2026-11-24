package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Lightweight, allocation-free rounded horizontal meter bar updated strictly
 * in-place via [setProgress]. Reuses a single lazy [ValueAnimator] to smoothly
 * interpolate progress deltas over [PerfHudMetrics.METER_ANIM_MS] (bypassed on
 * initial bind, `< 1%` deltas, detach, or reduced motion) and draws a subtle
 * leading-edge specular cap on active partial fills with zero `onDraw()` allocs.
 */
internal class MeterBarView(context: Context) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density.coerceAtLeast(1f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = PerfHudMetrics.METER_CAP_COLOR
    }
    private val trackRect = RectF()
    private val fillRect = RectF()
    private val capRect = RectF()
    private val capWidthPx: Float =
        (PerfHudMetrics.METER_CAP_WIDTH_DP * context.resources.displayMetrics.density).coerceAtLeast(1f)
    private val progressInterpolator = DecelerateInterpolator()

    private var targetFraction: Float = 0f
    private var displayedFraction: Float = 0f
    private var animStartFraction: Float = 0f
    private var hasProgress: Boolean = false
    private var trackColor: Int = 0xFF101016.toInt()
    private var borderColor: Int = 0x1AFFFFFF
    private var fillColor: Int = 0xFFFFFFFF.toInt()
    private var progressAnimator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun ensureAnimator(): ValueAnimator {
        progressAnimator?.let { return it }
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PerfHudMetrics.METER_ANIM_MS
            interpolator = progressInterpolator
            addUpdateListener { anim ->
                val t = (anim.animatedValue as? Float) ?: 1f
                displayedFraction = PerfHudMetrics.lerpFraction(
                    animStartFraction,
                    targetFraction,
                    t,
                )
                invalidate()
            }
        }.also { progressAnimator = it }
    }

    fun setProgress(
        fraction: Float,
        fillColor: Int,
        trackColor: Int = TileBackgrounds.surfaceSunken(context),
        borderColor: Int = TileBackgrounds.hairlineDivider(context),
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (hasProgress &&
            targetFraction == clamped &&
            this.fillColor == fillColor &&
            this.trackColor == trackColor &&
            this.borderColor == borderColor
        ) {
            return
        }
        val shouldAnim = PerfHudMetrics.shouldAnimateMeterDelta(
            fromFraction = displayedFraction,
            toFraction = clamped,
            hasInitialized = hasProgress,
            animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
            attached = isAttachedToWindow,
        )
        this.fillColor = fillColor
        this.trackColor = trackColor
        this.borderColor = borderColor
        targetFraction = clamped
        hasProgress = true
        if (shouldAnim) {
            animStartFraction = displayedFraction
            val anim = ensureAnimator()
            anim.cancel()
            anim.setFloatValues(0f, 1f)
            anim.start()
        } else {
            progressAnimator?.cancel()
            displayedFraction = clamped
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        displayedFraction = targetFraction
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = h * 0.5f

        trackRect.set(0f, 0f, w, h)
        trackPaint.color = trackColor
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        if (displayedFraction > 0f) {
            // Keep non-zero progress visibly rounded (at least pill height) without exceeding width.
            val fillW = (w * displayedFraction).coerceIn(minOf(h, w), w)
            fillRect.set(0f, 0f, fillW, h)
            fillPaint.color = fillColor
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint)

            if (PerfHudMetrics.shouldDrawLeadingCap(displayedFraction) && fillW > capWidthPx * 2f) {
                val capRight = (fillW - borderPaint.strokeWidth).coerceAtLeast(capWidthPx)
                val capLeft = (capRight - capWidthPx).coerceAtLeast(0f)
                val insetY = (h * 0.12f).coerceAtLeast(0.5f)
                if (h - insetY > insetY) {
                    capRect.set(capLeft, insetY, capRight, h - insetY)
                    canvas.drawRoundRect(capRect, capWidthPx * 0.5f, capWidthPx * 0.5f, capPaint)
                }
            }
        }

        val halfStroke = borderPaint.strokeWidth * 0.5f
        trackRect.inset(halfStroke, halfStroke)
        borderPaint.color = borderColor
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint)
    }
}
