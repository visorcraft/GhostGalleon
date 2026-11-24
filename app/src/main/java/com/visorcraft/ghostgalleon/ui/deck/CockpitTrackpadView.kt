package com.visorcraft.ghostgalleon.ui.deck

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.visorcraft.ghostgalleon.settings.ThemePack

/**
 * Allocation-free virtual trackpad surface for the Winlator Cockpit HUD.
 * Draws a sunken theme-concentric rounded pad, subtle 3×3 spatial reference
 * dots/crosshair, precision corner brackets, a live accent cursor reticle, and
 * a single-animator expanding click-confirmation ring on LMB/RMB/MMB taps.
 */
internal class CockpitTrackpadView(
    context: Context,
    accentColor: Int,
    cardRadiusDp: Int = ThemePack.GHOST.cardRadiusDp,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val surfaceColor = TileBackgrounds.surfaceSunken(context)
    private val idleBorderColor = TileBackgrounds.hairlineDivider(context)
    private val liftedAccent = ThemePack.accentTextColor(accentColor, surfaceColor)
    private val activeBorderColor = (0xAA shl 24) or (liftedAccent and 0x00FFFFFF)
    private val haloColor = (0x33 shl 24) or (liftedAccent and 0x00FFFFFF)
    private val ringIdleColor = (0x77 shl 24) or (liftedAccent and 0x00FFFFFF)
    private val cornerRadiusPx = PlayHudMetrics.trackpadCornerRadiusDp(cardRadiusDp) * density
    private val bracketLenPx = PlayHudMetrics.TRACKPAD_BRACKET_LEN_DP * density
    private val pulseInterpolator = DecelerateInterpolator()

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
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1.75f * density).coerceAtLeast(1.5f)
    }
    private val rect = RectF()

    private var normX: Float = 0.5f
    private var normY: Float = 0.5f
    private var touching: Boolean = false
    private var clickPulseProgress: Float = -1f
    private var clickPulseAnimator: ValueAnimator? = null

    fun updatePointer(xNorm: Float, yNorm: Float, isTouching: Boolean) {
        val nx = xNorm.coerceIn(0f, 1f)
        val ny = yNorm.coerceIn(0f, 1f)
        if (nx == normX && ny == normY && isTouching == touching) return
        normX = nx
        normY = ny
        touching = isTouching
        invalidate()
    }

    /** Trigger a single reused `140ms` expanding click-pulse ring on LMB/RMB/MMB tap (`MotionPolicy`-gated). */
    fun flashClick() {
        if (!MotionPolicy.shouldAnimate(
                PlayHudMetrics.TRACKPAD_CLICK_PULSE_MS,
                ValueAnimator.areAnimatorsEnabled(),
                isAttachedToWindow,
            )
        ) {
            return
        }
        val anim = clickPulseAnimator ?: ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PlayHudMetrics.TRACKPAD_CLICK_PULSE_MS
            interpolator = pulseInterpolator
            addUpdateListener { a ->
                clickPulseProgress = (a.animatedValue as? Float) ?: 1f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    clickPulseProgress = -1f
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    clickPulseProgress = -1f
                }
            })
        }.also { clickPulseAnimator = it }
        anim.cancel()
        clickPulseProgress = 0f
        anim.setFloatValues(0f, 1f)
        anim.start()
    }

    override fun onDetachedFromWindow() {
        clickPulseAnimator?.removeAllUpdateListeners()
        clickPulseAnimator?.removeAllListeners()
        clickPulseAnimator?.cancel()
        clickPulseAnimator = null
        clickPulseProgress = -1f
        super.onDetachedFromWindow()
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

        // Subtle center crosshair, 3x3 spatial orientation dots, and 8dp precision corner brackets.
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
        val bInset = (cornerRadiusPx * 0.65f).coerceAtLeast(8f * density)
        if (w > (bInset + bracketLenPx) * 2f && h > (bInset + bracketLenPx) * 2f) {
            // Top-left
            canvas.drawLine(bInset, bInset, bInset + bracketLenPx, bInset, guidePaint)
            canvas.drawLine(bInset, bInset, bInset, bInset + bracketLenPx, guidePaint)
            // Top-right
            canvas.drawLine(w - bInset - bracketLenPx, bInset, w - bInset, bInset, guidePaint)
            canvas.drawLine(w - bInset, bInset, w - bInset, bInset + bracketLenPx, guidePaint)
            // Bottom-left
            canvas.drawLine(bInset, h - bInset, bInset + bracketLenPx, h - bInset, guidePaint)
            canvas.drawLine(bInset, h - bInset - bracketLenPx, bInset, h - bInset, guidePaint)
            // Bottom-right
            canvas.drawLine(w - bInset - bracketLenPx, h - bInset, w - bInset, h - bInset, guidePaint)
            canvas.drawLine(w - bInset, h - bInset - bracketLenPx, w - bInset, h - bInset, guidePaint)
        }

        // Live cursor reticle at normalized launch-display coordinate.
        val px = (normX * w).coerceIn(8f * density, (w - 8f * density).coerceAtLeast(8f * density))
        val py = (normY * h).coerceIn(8f * density, (h - 8f * density).coerceAtLeast(8f * density))
        if (clickPulseProgress in 0f..1f) {
            val pulseRadius = 22f * density * PlayHudMetrics.clickPulseRadiusFrac(clickPulseProgress)
            val pulseAlpha = ((1f - clickPulseProgress) * 220f).toInt().coerceIn(0, 255)
            pulsePaint.color = ThemePack.withAlpha(liftedAccent, pulseAlpha)
            canvas.drawCircle(px, py, pulseRadius, pulsePaint)
        }
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
