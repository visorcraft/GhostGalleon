package com.visorcraft.ghostgalleon.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

/** Pure normalized layout state for [GridDensityPreviewView]. Host-tested. */
internal data class MiniGridMetrics(
    val columns: Int,
    val rows: Int,
    val iconScale: Float,
    val cardRatio: Float,
    val showLabels: Boolean,
    val horizontal: Boolean,
)

/** Pure cell rectangle inside the mini grid preview viewport. Host-tested. */
internal data class MiniCellRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val focused: Boolean,
)

/**
 * Pure host-tested geometry math for live Settings preview canvases.
 */
internal object SettingsPreviewLogic {

    const val MIN_COLUMNS = 3
    const val MAX_COLUMNS = 8
    const val MIN_ICON_DP = 48
    const val MAX_ICON_DP = 128
    const val MIN_CARD_DP = 120
    const val MAX_CARD_DP = 320

    fun miniGridMetrics(
        columns: Int,
        iconSizeDp: Int,
        cardSizeDp: Int,
        showLabels: Boolean,
        gridDirection: String,
    ): MiniGridMetrics {
        val cols = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val iconNorm = (iconSizeDp.coerceIn(MIN_ICON_DP, MAX_ICON_DP) - MIN_ICON_DP) /
            (MAX_ICON_DP - MIN_ICON_DP).toFloat()
        val cardNorm = (cardSizeDp.coerceIn(MIN_CARD_DP, MAX_CARD_DP) - MIN_CARD_DP) /
            (MAX_CARD_DP - MIN_CARD_DP).toFloat()
        return MiniGridMetrics(
            columns = cols,
            rows = 2,
            iconScale = 0.42f + iconNorm * 0.42f,
            cardRatio = 0.52f + cardNorm * 0.46f,
            showLabels = showLabels,
            horizontal = gridDirection.equals("horizontal", ignoreCase = true),
        )
    }

    fun computeMiniGridCells(
        width: Float,
        height: Float,
        columns: Int,
        rows: Int,
        gapPx: Float,
        focusedIndex: Int = 1,
    ): List<MiniCellRect> {
        if (width <= 0f || height <= 0f || columns <= 0 || rows <= 0) return emptyList()
        val safeGap = gapPx.coerceAtLeast(0f)
        val totalGapX = safeGap * (columns - 1)
        val totalGapY = safeGap * (rows - 1)
        val cellW = ((width - totalGapX) / columns).coerceAtLeast(1f)
        val cellH = ((height - totalGapY) / rows).coerceAtLeast(1f)
        val out = ArrayList<MiniCellRect>(columns * rows)
        var idx = 0
        for (r in 0 until rows) {
            val top = r * (cellH + safeGap)
            for (c in 0 until columns) {
                val left = c * (cellW + safeGap)
                out.add(
                    MiniCellRect(
                        left = left,
                        top = top,
                        right = left + cellW,
                        bottom = top + cellH,
                        focused = idx == focusedIndex,
                    ),
                )
                idx++
            }
        }
        return out
    }

    fun scaledCornerRadiusPx(
        cardRadiusDp: Int,
        density: Float,
        scale: Float = 0.45f,
    ): Float {
        val safeDensity = density.coerceAtLeast(0.5f)
        return (cardRadiusDp.coerceIn(4, 48) * scale * safeDensity)
            .coerceIn(2f * safeDensity, 14f * safeDensity)
    }
}

/**
 * Compact live visual preview of the active [ThemeTokens] & accent color:
 * renders sunken canvas, subtle top accent aura, miniature deck cards with
 * optical nested corners, active accent focus ring, accent CTA pill, and dock.
 * Pure vector Canvas drawing — zero extra view inflation or rebuild churn.
 */
internal class ThemePreviewView(
    context: Context,
    private var tokens: ThemeTokens,
    private var accent: Int,
) : View(context) {

    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val elevatedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val onAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val focusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(2)
    }

    private var lastGradientHeight = -1f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        syncPaints()
    }

    fun updateTheme(nextTokens: ThemeTokens, nextAccent: Int) {
        if (tokens == nextTokens && accent == nextAccent) return
        tokens = nextTokens
        accent = nextAccent
        lastGradientHeight = -1f
        syncPaints()
        invalidate()
    }

    private fun syncPaints() {
        bgPaint.color = ThemePack.surfaceSunken(tokens)
        cardPaint.color = ThemePack.cardFill(tokens)
        elevatedPaint.color = ThemePack.surfaceElevated(tokens)
        accentPaint.color = accent
        onAccentPaint.color = SettingsUiLogic.accessibleOnAccent(accent)
        mutedPaint.color = UiDimens.ALPHA_SUBTLE
        hairlinePaint.color = ThemePack.hairlineDivider(tokens)
        focusStrokePaint.color = accent
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val outerRadius = dpF(UiDimens.RADIUS_MD)
        val inset = hairlinePaint.strokeWidth * 0.5f

        // Sunken stage backdrop + subtle accent sheen at top edge.
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerRadius, outerRadius, bgPaint)
        if (lastGradientHeight != h) {
            lastGradientHeight = h
            auraPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                h * 0.65f,
                ThemePack.accentGlowColor(accent, 0x36),
                ThemePack.withAlpha(accent, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, outerRadius, outerRadius, auraPaint)

        val pad = dpF(10)
        val dockH = dpF(14)
        val gap = dpF(8)
        val deckAreaH = (h - pad * 2f - dockH - dpF(6)).coerceAtLeast(dpF(20))
        val ctaAreaW = (w * 0.28f).coerceAtLeast(dpF(64))
        val cardsAreaW = (w - pad * 2f - ctaAreaW - gap).coerceAtLeast(dpF(60))

        val tileRadius = SettingsPreviewLogic.scaledCornerRadiusPx(
            cardRadiusDp = tokens.cardRadiusDp,
            density = resources.displayMetrics.density,
        )
        val innerTileRadius = ThemePack.nestedRadiusDp(
            outerRadiusDp = (tileRadius / resources.displayMetrics.density).toInt(),
            insetDp = 3,
            minDp = 2,
        ) * resources.displayMetrics.density

        // 3 miniature deck cards; middle card shows the crisp accent focus ring.
        val cells = SettingsPreviewLogic.computeMiniGridCells(
            width = cardsAreaW,
            height = deckAreaH,
            columns = 3,
            rows = 1,
            gapPx = gap,
            focusedIndex = 1,
        )
        for (cell in cells) {
            val l = pad + cell.left
            val t = pad + cell.top
            val r = pad + cell.right
            val b = pad + cell.bottom
            rect.set(l, t, r, b)
            canvas.drawRoundRect(rect, tileRadius, tileRadius, cardPaint)

            // Nested art badge inside card.
            val artInset = dpF(5)
            val artBottom = (b - dpF(12)).coerceAtLeast(t + artInset + dpF(6))
            rect.set(l + artInset, t + artInset, r - artInset, artBottom)
            canvas.drawRoundRect(
                rect,
                innerTileRadius,
                innerTileRadius,
                if (cell.focused) elevatedPaint else bgPaint,
            )

            // Tiny title line below nested art.
            val lineTop = artBottom + dpF(3)
            val lineH = dpF(3)
            if (lineTop + lineH < b - dpF(2)) {
                rect.set(l + artInset, lineTop, l + (r - l) * 0.65f, lineTop + lineH)
                canvas.drawRoundRect(rect, lineH * 0.5f, lineH * 0.5f, mutedPaint)
            }

            rect.set(l, t, r, b)
            canvas.drawRoundRect(
                rect,
                tileRadius,
                tileRadius,
                if (cell.focused) focusStrokePaint else hairlinePaint,
            )
        }

        // Right-side mini hero preview: title bar, subtitle bar, filled accent CTA pill.
        val ctaLeft = pad + cardsAreaW + gap
        val ctaRight = w - pad
        val ctaTop = pad + dpF(4)
        val barH = dpF(5)
        rect.set(ctaLeft, ctaTop, ctaLeft + (ctaRight - ctaLeft) * 0.82f, ctaTop + barH)
        canvas.drawRoundRect(rect, barH * 0.5f, barH * 0.5f, elevatedPaint)

        val subTop = ctaTop + barH + dpF(5)
        rect.set(ctaLeft, subTop, ctaLeft + (ctaRight - ctaLeft) * 0.56f, subTop + dpF(3.5f))
        canvas.drawRoundRect(rect, dpF(2), dpF(2), mutedPaint)

        val pillH = dpF(16)
        val pillTop = (pad + deckAreaH - pillH).coerceAtLeast(subTop + dpF(7))
        val pillR = pillH * 0.5f
        rect.set(ctaLeft, pillTop, ctaRight, pillTop + pillH)
        canvas.drawRoundRect(rect, pillR, pillR, accentPaint)
        // Contrast-checked glyph bar inside accent CTA pill.
        val glyphW = (ctaRight - ctaLeft) * 0.42f
        val glyphH = dpF(3)
        val gx = ctaLeft + ((ctaRight - ctaLeft) - glyphW) * 0.5f
        val gy = pillTop + (pillH - glyphH) * 0.5f
        rect.set(gx, gy, gx + glyphW, gy + glyphH)
        canvas.drawRoundRect(rect, glyphH * 0.5f, glyphH * 0.5f, onAccentPaint)

        // Bottom mini dock strip.
        val dockW = (w * 0.46f).coerceAtLeast(dpF(90))
        val dockLeft = (w - dockW) * 0.5f
        val dockTop = h - pad - dockH + dpF(2)
        rect.set(dockLeft, dockTop, dockLeft + dockW, dockTop + dockH)
        canvas.drawRoundRect(rect, dockH * 0.5f, dockH * 0.5f, cardPaint)
        canvas.drawRoundRect(rect, dockH * 0.5f, dockH * 0.5f, hairlinePaint)
        val dotCount = 4
        val dotSize = dpF(6)
        val dotGap = dpF(8)
        val dotsW = dotCount * dotSize + (dotCount - 1) * dotGap
        var dx = dockLeft + (dockW - dotsW) * 0.5f
        val dy = dockTop + (dockH - dotSize) * 0.5f
        for (i in 0 until dotCount) {
            rect.set(dx, dy, dx + dotSize, dy + dotSize)
            canvas.drawRoundRect(
                rect,
                dpF(2),
                dpF(2),
                if (i == 0) accentPaint else mutedPaint,
            )
            dx += dotSize + dotGap
        }

        // Outer hairline border.
        rect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rect, outerRadius, outerRadius, hairlinePaint)
    }
}

/**
 * Live zero-rebuild preview of Grid columns, icon size, Game Mode card scale,
 * app name labels, and scroll axis (vertical rail vs horizontal page dots).
 * Mutating via [update] only invalidates this canvas.
 */
internal class GridDensityPreviewView(
    context: Context,
    private val tokens: ThemeTokens,
    private val accent: Int,
    columns: Int,
    iconSizeDp: Int,
    cardSizeDp: Int,
    showLabels: Boolean,
    gridDirection: String,
) : View(context) {

    private var metrics = SettingsPreviewLogic.miniGridMetrics(
        columns = columns,
        iconSizeDp = iconSizeDp,
        cardSizeDp = cardSizeDp,
        showLabels = showLabels,
        gridDirection = gridDirection,
    )

    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.cardFill(tokens)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceElevated(tokens)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiDimens.ALPHA_SUBTLE
    }
    private val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.hairlineDivider(tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1.75f)
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(
        columns: Int,
        iconSizeDp: Int,
        cardSizeDp: Int,
        showLabels: Boolean,
        gridDirection: String,
    ) {
        val next = SettingsPreviewLogic.miniGridMetrics(
            columns = columns,
            iconSizeDp = iconSizeDp,
            cardSizeDp = cardSizeDp,
            showLabels = showLabels,
            gridDirection = gridDirection,
        )
        if (next != metrics) {
            metrics = next
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val outerR = dpF(UiDimens.RADIUS_MD)
        val inset = hairlinePaint.strokeWidth * 0.5f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerR, outerR, bgPaint)

        val pad = dpF(10)
        val dividerGap = dpF(10)
        val carouselPanelW = (w * 0.24f).coerceIn(dpF(56), dpF(108))
        val indicatorBottom = if (metrics.horizontal) dpF(10) else 0f
        val indicatorRight = if (!metrics.horizontal) dpF(8) else 0f
        val gridW = (w - pad * 2f - carouselPanelW - dividerGap - indicatorRight).coerceAtLeast(dpF(60))
        val gridH = (h - pad * 2f - indicatorBottom).coerceAtLeast(dpF(36))

        val gap = dpF(5)
        val cells = SettingsPreviewLogic.computeMiniGridCells(
            width = gridW,
            height = gridH,
            columns = metrics.columns,
            rows = metrics.rows,
            gapPx = gap,
            focusedIndex = 1.coerceAtMost(metrics.columns - 1),
        )
        val tileR = SettingsPreviewLogic.scaledCornerRadiusPx(
            cardRadiusDp = tokens.cardRadiusDp,
            density = resources.displayMetrics.density,
            scale = 0.34f,
        )

        for (cell in cells) {
            val l = pad + cell.left
            val t = pad + cell.top
            val r = pad + cell.right
            val b = pad + cell.bottom
            rect.set(l, t, r, b)
            canvas.drawRoundRect(rect, tileR, tileR, tilePaint)

            val cellW = r - l
            val cellH = b - t
            val labelReserve = if (metrics.showLabels) dpF(7) else 0f
            val availH = (cellH - labelReserve - dpF(6)).coerceAtLeast(dpF(6))
            val availW = (cellW - dpF(6)).coerceAtLeast(dpF(6))
            val iconSize = (minOf(availW, availH) * metrics.iconScale).coerceAtLeast(dpF(5))
            val ix = l + (cellW - iconSize) * 0.5f
            val iy = t + ((cellH - labelReserve) - iconSize) * 0.5f
            val iconR = (iconSize * 0.24f).coerceAtLeast(dpF(2))
            rect.set(ix, iy, ix + iconSize, iy + iconSize)
            canvas.drawRoundRect(
                rect,
                iconR,
                iconR,
                if (cell.focused) accentPaint else iconPaint,
            )

            if (metrics.showLabels) {
                val barW = (cellW * 0.58f).coerceAtLeast(dpF(6))
                val barH = dpF(2.5f)
                val bx = l + (cellW - barW) * 0.5f
                val by = b - dpF(5)
                rect.set(bx, by, bx + barW, by + barH)
                canvas.drawRoundRect(rect, barH * 0.5f, barH * 0.5f, labelPaint)
            }

            rect.set(l, t, r, b)
            canvas.drawRoundRect(
                rect,
                tileR,
                tileR,
                if (cell.focused) focusPaint else hairlinePaint,
            )
        }

        // Scroll direction cue: horizontal page dots below grid vs vertical thumb on right.
        if (metrics.horizontal) {
            val dotR = dpF(2.2f)
            val dotGap = dpF(7)
            val cy = h - pad + dpF(1) - dotR
            val centerX = pad + gridW * 0.5f
            for (i in -1..1) {
                canvas.drawCircle(
                    centerX + i * dotGap,
                    cy,
                    if (i == 0) dotR * 1.25f else dotR,
                    if (i == 0) accentPaint else labelPaint,
                )
            }
        } else {
            val trackX = pad + gridW + dpF(4)
            val trackW = dpF(2.5f)
            rect.set(trackX, pad + dpF(4), trackX + trackW, pad + gridH - dpF(4))
            canvas.drawRoundRect(rect, trackW * 0.5f, trackW * 0.5f, labelPaint)
            rect.set(trackX, pad + dpF(4), trackX + trackW, pad + gridH * 0.44f)
            canvas.drawRoundRect(rect, trackW * 0.5f, trackW * 0.5f, accentPaint)
        }

        // Vertical hairline divider before Game Mode card-scale miniature.
        val divX = pad + gridW + indicatorRight + dividerGap * 0.5f
        canvas.drawLine(divX, pad, divX, h - pad, hairlinePaint)

        // Right-side Game Mode carousel card scale silhouette driven by cardSizeDp.
        val cardBoxLeft = divX + dividerGap * 0.5f
        val cardBoxW = (w - pad - cardBoxLeft).coerceAtLeast(dpF(24))
        val cardBoxH = (h - pad * 2f).coerceAtLeast(dpF(24))
        val miniCardH = (cardBoxH * metrics.cardRatio).coerceAtLeast(dpF(22))
        val miniCardW = (miniCardH * 0.74f).coerceAtMost(cardBoxW)
        val cl = cardBoxLeft + (cardBoxW - miniCardW) * 0.5f
        val ct = pad + (cardBoxH - miniCardH) * 0.5f
        val miniCardR = SettingsPreviewLogic.scaledCornerRadiusPx(
            cardRadiusDp = tokens.cardRadiusDp,
            density = resources.displayMetrics.density,
            scale = 0.45f,
        )
        rect.set(cl, ct, cl + miniCardW, ct + miniCardH)
        canvas.drawRoundRect(rect, miniCardR, miniCardR, tilePaint)
        val innerPad = dpF(4)
        if (miniCardW > innerPad * 2 + dpF(4) && miniCardH > innerPad * 2 + dpF(4)) {
            rect.set(cl + innerPad, ct + innerPad, cl + miniCardW - innerPad, ct + miniCardH - innerPad)
            canvas.drawRoundRect(rect, (miniCardR - dpF(2)).coerceAtLeast(dpF(2)), (miniCardR - dpF(2)).coerceAtLeast(dpF(2)), iconPaint)
        }
        rect.set(cl, ct, cl + miniCardW, ct + miniCardH)
        canvas.drawRoundRect(rect, miniCardR, miniCardR, focusPaint)

        // Outer border.
        rect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rect, outerR, outerR, hairlinePaint)
    }
}

/**
 * Lightweight zero-allocation determinate progress bar for Settings jobs
 * (artwork scraping, storage/playtime meters). Updates strictly in-place via
 * [setFraction].
 */
internal class DeterminateProgressBarView(
    context: Context,
    tokens: ThemeTokens,
    accent: Int,
) : View(context) {

    private var fraction: Float = 0f
    private val rect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.hairlineDivider(tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setFraction(next: Float) {
        val clamped = if (next.isNaN() || next.isInfinite()) 0f else next.coerceIn(0f, 1f)
        if (kotlin.math.abs(clamped - fraction) >= 0.0005f) {
            fraction = clamped
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h * 0.5f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, trackPaint)
        if (fraction > 0.002f) {
            val fillW = (w * fraction).coerceAtLeast(h)
            rect.set(0f, 0f, fillW.coerceAtMost(w), h)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
        val inset = borderPaint.strokeWidth * 0.5f
        rect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(rect, r, r, borderPaint)
    }
}
