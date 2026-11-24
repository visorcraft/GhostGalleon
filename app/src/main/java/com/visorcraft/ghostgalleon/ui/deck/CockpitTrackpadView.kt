package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Allocation-free virtual trackpad surface for the Winlator Cockpit HUD.
 * Draws a sunken rounded pad, subtle 3×3 spatial reference dots/crosshair,
 * and a live accent cursor reticle that blooms while finger-dragging.
 */
internal class CockpitTrackpadView(
    context: Context,
    accentColor: Int,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val surfaceColor = TileBackgrounds.surfaceSunken(context)
    private val idleBorderColor = TileBackgrounds.hairlineDivider(context)
    private val liftedAccent = ThemePack.accentTextColor(accentColor, surfaceColor)
    private val activeBorderColor = (0xAA shl 24) or (liftedAccent and 0x00FFFFFF)
    private val haloColor = (0x33 shl 24) or (liftedAccent and 0x00FFFFFF)
    private val ringIdleColor = (0x77 shl 24) or (liftedAccent and 0x00FFFFFF)
    private val cornerRadiusPx = 14f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density.coerceAtLeast(1f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density.coerceAtLeast(1f)
        color = 0x16FFFFFF
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x24FFFFFF
    }
    private val reticleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1.5f * density).coerceAtLeast(1.5f)
    }
    private val rect = RectF()

    private var normX: Float = 0.5f
    private var normY: Float = 0.5f
    private var touching: Boolean = false

    fun updatePointer(xNorm: Float, yNorm: Float, isTouching: Boolean) {
        val nx = xNorm.coerceIn(0f, 1f)
        val ny = yNorm.coerceIn(0f, 1f)
        if (nx == normX && ny == normY && isTouching == touching) return
        normX = nx
        normY = ny
        touching = isTouching
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Sunken surface + hairline / active accent border.
        rect.set(0f, 0f, w, h)
        fillPaint.color = surfaceColor
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)

        // Subtle center crosshair and 3x3 spatial orientation dots.
        val cx = w * 0.5f
        val cy = h * 0.5f
        val pad = 14f * density
        if (w > pad * 2f && h > pad * 2f) {
            canvas.drawLine(pad, cy, w - pad, cy, guidePaint)
            canvas.drawLine(cx, pad, cx, h - pad, guidePaint)
            val dotR = 1.5f * density
            for (gx in 1..3) {
                for (gy in 1..3) {
                    canvas.drawCircle(w * gx * 0.25f, h * gy * 0.25f, dotR, dotPaint)
                }
            }
        }

        // Live cursor reticle at normalized launch-display coordinate.
        val px = (normX * w).coerceIn(8f * density, (w - 8f * density).coerceAtLeast(8f * density))
        val py = (normY * h).coerceIn(8f * density, (h - 8f * density).coerceAtLeast(8f * density))
        if (touching) {
            fillPaint.color = haloColor
            canvas.drawCircle(px, py, 16f * density, fillPaint)
            reticleRingPaint.color = liftedAccent
            canvas.drawCircle(px, py, 10.5f * density, reticleRingPaint)
            fillPaint.color = Color.WHITE
            canvas.drawCircle(px, py, 4f * density, fillPaint)
        } else {
            reticleRingPaint.color = ringIdleColor
            canvas.drawCircle(px, py, 8.5f * density, reticleRingPaint)
            fillPaint.color = liftedAccent
            canvas.drawCircle(px, py, 2.5f * density, fillPaint)
        }

        val halfStroke = strokePaint.strokeWidth * 0.5f
        rect.inset(halfStroke, halfStroke)
        strokePaint.color = if (touching) activeBorderColor else idleBorderColor
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint)
    }
}
