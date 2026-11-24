package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Lightweight, allocation-free rounded horizontal meter bar updated strictly
 * in-place via [setProgress] (calls [invalidate] only when value or colors
 * change — zero layout passes or drawable churn).
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
    private val trackRect = RectF()
    private val fillRect = RectF()

    private var fraction: Float = 0f
    private var trackColor: Int = 0xFF101016.toInt()
    private var borderColor: Int = 0x1AFFFFFF
    private var fillColor: Int = 0xFFFFFFFF.toInt()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setProgress(
        fraction: Float,
        fillColor: Int,
        trackColor: Int = TileBackgrounds.surfaceSunken(context),
        borderColor: Int = TileBackgrounds.hairlineDivider(context),
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (this.fraction == clamped &&
            this.fillColor == fillColor &&
            this.trackColor == trackColor &&
            this.borderColor == borderColor
        ) {
            return
        }
        this.fraction = clamped
        this.fillColor = fillColor
        this.trackColor = trackColor
        this.borderColor = borderColor
        invalidate()
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

        if (fraction > 0f) {
            // Keep non-zero progress visibly rounded (at least pill height) without exceeding width.
            val fillW = (w * fraction).coerceIn(minOf(h, w), w)
            fillRect.set(0f, 0f, fillW, h)
            fillPaint.color = fillColor
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
        }

        val halfStroke = borderPaint.strokeWidth * 0.5f
        trackRect.inset(halfStroke, halfStroke)
        borderPaint.color = borderColor
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint)
    }
}
