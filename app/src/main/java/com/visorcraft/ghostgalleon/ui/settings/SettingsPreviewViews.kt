package com.visorcraft.ghostgalleon.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.visorcraft.ghostgalleon.input.SeatAnchor
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
 * Precomputed cell layout cache for [ThemePreviewView] and
 * [GridDensityPreviewView]. Recomputes only when viewport size or grid
 * dimensions change so `onDraw()` allocates zero objects (including during
 * continuous 120 Hz SeekBar drags). Pure; host-tested.
 */
internal class MiniGridCellCache {
    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private var cachedColumns: Int = -1
    private var cachedRows: Int = -1
    private var cachedGapPx: Float = -1f
    private var cachedFocusedIndex: Int = -1

    var cells: List<MiniCellRect> = emptyList()
        private set

    var recomputeCount: Int = 0
        private set

    fun ensure(
        width: Float,
        height: Float,
        columns: Int,
        rows: Int,
        gapPx: Float,
        focusedIndex: Int = 1,
    ): List<MiniCellRect> {
        if (cachedWidth == width &&
            cachedHeight == height &&
            cachedColumns == columns &&
            cachedRows == rows &&
            cachedGapPx == gapPx &&
            cachedFocusedIndex == focusedIndex
        ) {
            return cells
        }
        cachedWidth = width
        cachedHeight = height
        cachedColumns = columns
        cachedRows = rows
        cachedGapPx = gapPx
        cachedFocusedIndex = focusedIndex
        cells = SettingsPreviewLogic.computeMiniGridCells(
            width = width,
            height = height,
            columns = columns,
            rows = rows,
            gapPx = gapPx,
            focusedIndex = focusedIndex,
        )
        recomputeCount++
        return cells
    }
}

/**
 * Compact live visual preview of the active [ThemeTokens] & accent color:
 * renders sunken canvas, subtle top accent aura, miniature deck cards with
 * optical nested corners, active accent focus ring, accent CTA pill, and dock.
 * Pure vector Canvas drawing — zero extra view inflation or per-frame allocations.
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

    private val density = context.resources.displayMetrics.density
    private val outerRadius = context.dpF(UiDimens.RADIUS_MD)
    private val inset = hairlinePaint.strokeWidth * 0.5f
    private val pad = context.dpF(10)
    private val dockH = context.dpF(14)
    private val gap = context.dpF(8)
    private val dp2 = context.dpF(2)
    private val dp3 = context.dpF(3)
    private val dp35 = context.dpF(3.5f)
    private val dp4 = context.dpF(4)
    private val dp5 = context.dpF(5)
    private val dp6 = context.dpF(6)
    private val dp7 = context.dpF(7)
    private val dp12 = context.dpF(12)
    private val dp16 = context.dpF(16)
    private val dp20 = context.dpF(20)
    private val dp60 = context.dpF(60)
    private val dp64 = context.dpF(64)
    private val dp90 = context.dpF(90)

    private val cellCache = MiniGridCellCache()
    private var lastGradientHeight = -1f
    private var lastGradientAccent = 0
    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedDeckAreaH = 0f
    private var cachedCardsAreaW = 0f
    private var cachedTileRadius = 0f
    private var cachedInnerTileRadius = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        syncPaints()
        syncRadii()
    }

    fun updateTheme(nextTokens: ThemeTokens, nextAccent: Int) {
        if (tokens == nextTokens && accent == nextAccent) return
        tokens = nextTokens
        accent = nextAccent
        lastGradientHeight = -1f
        syncPaints()
        syncRadii()
        if (width > 0 && height > 0) {
            rebuildGeometry(width.toFloat(), height.toFloat())
        }
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

    private fun syncRadii() {
        cachedTileRadius = SettingsPreviewLogic.scaledCornerRadiusPx(
            cardRadiusDp = tokens.cardRadiusDp,
            density = density,
        )
        cachedInnerTileRadius = ThemePack.nestedRadiusDp(
            outerRadiusDp = (cachedTileRadius / density).toInt(),
            insetDp = 3,
            minDp = 2,
        ) * density
    }

    private fun rebuildGeometry(w: Float, h: Float) {
        cachedWidth = w
        cachedHeight = h
        if (w <= 0f || h <= 0f) return
        if (lastGradientHeight != h || lastGradientAccent != accent) {
            lastGradientHeight = h
            lastGradientAccent = accent
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
        cachedDeckAreaH = (h - pad * 2f - dockH - dp6).coerceAtLeast(dp20)
        val ctaAreaW = (w * 0.28f).coerceAtLeast(dp64)
        cachedCardsAreaW = (w - pad * 2f - ctaAreaW - gap).coerceAtLeast(dp60)
        cellCache.ensure(
            width = cachedCardsAreaW,
            height = cachedDeckAreaH,
            columns = 3,
            rows = 1,
            gapPx = gap,
            focusedIndex = 1,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (w != cachedWidth || h != cachedHeight || lastGradientHeight != h) {
            rebuildGeometry(w, h)
        }

        // Sunken stage backdrop + subtle accent sheen at top edge.
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerRadius, outerRadius, bgPaint)
        canvas.drawRoundRect(rect, outerRadius, outerRadius, auraPaint)

        val tileRadius = cachedTileRadius
        val innerTileRadius = cachedInnerTileRadius

        // 3 miniature deck cards; middle card shows the crisp accent focus ring.
        val cells = cellCache.cells
        for (i in 0 until cells.size) {
            val cell = cells[i]
            val l = pad + cell.left
            val t = pad + cell.top
            val r = pad + cell.right
            val b = pad + cell.bottom
            rect.set(l, t, r, b)
            canvas.drawRoundRect(rect, tileRadius, tileRadius, cardPaint)

            // Nested art badge inside card.
            val artInset = dp5
            val artBottom = (b - dp12).coerceAtLeast(t + artInset + dp6)
            rect.set(l + artInset, t + artInset, r - artInset, artBottom)
            canvas.drawRoundRect(
                rect,
                innerTileRadius,
                innerTileRadius,
                if (cell.focused) elevatedPaint else bgPaint,
            )

            // Tiny title line below nested art.
            val lineTop = artBottom + dp3
            val lineH = dp3
            if (lineTop + lineH < b - dp2) {
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
        val ctaLeft = pad + cachedCardsAreaW + gap
        val ctaRight = w - pad
        val ctaTop = pad + dp4
        val barH = dp5
        rect.set(ctaLeft, ctaTop, ctaLeft + (ctaRight - ctaLeft) * 0.82f, ctaTop + barH)
        canvas.drawRoundRect(rect, barH * 0.5f, barH * 0.5f, elevatedPaint)

        val subTop = ctaTop + barH + dp5
        rect.set(ctaLeft, subTop, ctaLeft + (ctaRight - ctaLeft) * 0.56f, subTop + dp35)
        canvas.drawRoundRect(rect, dp2, dp2, mutedPaint)

        val pillH = dp16
        val pillTop = (pad + cachedDeckAreaH - pillH).coerceAtLeast(subTop + dp7)
        val pillR = pillH * 0.5f
        rect.set(ctaLeft, pillTop, ctaRight, pillTop + pillH)
        canvas.drawRoundRect(rect, pillR, pillR, accentPaint)
        // Contrast-checked glyph bar inside accent CTA pill.
        val glyphW = (ctaRight - ctaLeft) * 0.42f
        val glyphH = dp3
        val gx = ctaLeft + ((ctaRight - ctaLeft) - glyphW) * 0.5f
        val gy = pillTop + (pillH - glyphH) * 0.5f
        rect.set(gx, gy, gx + glyphW, gy + glyphH)
        canvas.drawRoundRect(rect, glyphH * 0.5f, glyphH * 0.5f, onAccentPaint)

        // Bottom mini dock strip.
        val dockW = (w * 0.46f).coerceAtLeast(dp90)
        val dockLeft = (w - dockW) * 0.5f
        val dockTop = h - pad - dockH + dp2
        rect.set(dockLeft, dockTop, dockLeft + dockW, dockTop + dockH)
        canvas.drawRoundRect(rect, dockH * 0.5f, dockH * 0.5f, cardPaint)
        canvas.drawRoundRect(rect, dockH * 0.5f, dockH * 0.5f, hairlinePaint)
        val dotCount = 4
        val dotSize = dp6
        val dotGap = gap
        val dotsW = dotCount * dotSize + (dotCount - 1) * dotGap
        var dx = dockLeft + (dockW - dotsW) * 0.5f
        val dy = dockTop + (dockH - dotSize) * 0.5f
        for (i in 0 until dotCount) {
            rect.set(dx, dy, dx + dotSize, dy + dotSize)
            canvas.drawRoundRect(
                rect,
                dp2,
                dp2,
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
 * Mutating via [update] only invalidates this canvas; `onDraw()` allocates zero objects.
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

    private val density = context.resources.displayMetrics.density
    private val outerR = context.dpF(UiDimens.RADIUS_MD)
    private val inset = hairlinePaint.strokeWidth * 0.5f
    private val pad = context.dpF(10)
    private val dividerGap = context.dpF(10)
    private val gap = context.dpF(5)
    private val dp1 = context.dpF(1)
    private val dp2 = context.dpF(2)
    private val dp22 = context.dpF(2.2f)
    private val dp25 = context.dpF(2.5f)
    private val dp4 = context.dpF(4)
    private val dp5 = context.dpF(5)
    private val dp6 = context.dpF(6)
    private val dp7 = context.dpF(7)
    private val dp8 = context.dpF(8)
    private val dp10 = context.dpF(10)
    private val dp22Px = context.dpF(22)
    private val dp24 = context.dpF(24)
    private val dp36 = context.dpF(36)
    private val dp56 = context.dpF(56)
    private val dp60 = context.dpF(60)
    private val dp108 = context.dpF(108)
    private val tileR = SettingsPreviewLogic.scaledCornerRadiusPx(
        cardRadiusDp = tokens.cardRadiusDp,
        density = density,
        scale = 0.34f,
    )
    private val miniCardR = SettingsPreviewLogic.scaledCornerRadiusPx(
        cardRadiusDp = tokens.cardRadiusDp,
        density = density,
        scale = 0.45f,
    )

    private val cellCache = MiniGridCellCache()
    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedGridW = 0f
    private var cachedGridH = 0f
    private var cachedIndicatorRight = 0f

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
            val layoutChanged = next.columns != metrics.columns ||
                next.rows != metrics.rows ||
                next.horizontal != metrics.horizontal
            metrics = next
            if (layoutChanged && width > 0 && height > 0) {
                rebuildGeometry(width.toFloat(), height.toFloat())
            }
            invalidate()
        }
    }

    private fun rebuildGeometry(w: Float, h: Float) {
        cachedWidth = w
        cachedHeight = h
        if (w <= 0f || h <= 0f) return
        val carouselPanelW = (w * 0.24f).coerceIn(dp56, dp108)
        val indicatorBottom = if (metrics.horizontal) dp10 else 0f
        cachedIndicatorRight = if (!metrics.horizontal) dp8 else 0f
        cachedGridW = (w - pad * 2f - carouselPanelW - dividerGap - cachedIndicatorRight).coerceAtLeast(dp60)
        cachedGridH = (h - pad * 2f - indicatorBottom).coerceAtLeast(dp36)
        cellCache.ensure(
            width = cachedGridW,
            height = cachedGridH,
            columns = metrics.columns,
            rows = metrics.rows,
            gapPx = gap,
            focusedIndex = 1.coerceAtMost(metrics.columns - 1),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (w != cachedWidth || h != cachedHeight) {
            rebuildGeometry(w, h)
        }

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerR, outerR, bgPaint)

        val gridW = cachedGridW
        val gridH = cachedGridH
        val cells = cellCache.cells
        val labelReserve = if (metrics.showLabels) dp7 else 0f

        for (i in 0 until cells.size) {
            val cell = cells[i]
            val l = pad + cell.left
            val t = pad + cell.top
            val r = pad + cell.right
            val b = pad + cell.bottom
            rect.set(l, t, r, b)
            canvas.drawRoundRect(rect, tileR, tileR, tilePaint)

            val cellW = r - l
            val cellH = b - t
            val availH = (cellH - labelReserve - dp6).coerceAtLeast(dp6)
            val availW = (cellW - dp6).coerceAtLeast(dp6)
            val iconSize = (minOf(availW, availH) * metrics.iconScale).coerceAtLeast(dp5)
            val ix = l + (cellW - iconSize) * 0.5f
            val iy = t + ((cellH - labelReserve) - iconSize) * 0.5f
            val iconR = (iconSize * 0.24f).coerceAtLeast(dp2)
            rect.set(ix, iy, ix + iconSize, iy + iconSize)
            canvas.drawRoundRect(
                rect,
                iconR,
                iconR,
                if (cell.focused) accentPaint else iconPaint,
            )

            if (metrics.showLabels) {
                val barW = (cellW * 0.58f).coerceAtLeast(dp6)
                val barH = dp25
                val bx = l + (cellW - barW) * 0.5f
                val by = b - dp5
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
            val dotR = dp22
            val dotGap = dp7
            val cy = h - pad + dp1 - dotR
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
            val trackX = pad + gridW + dp4
            val trackW = dp25
            rect.set(trackX, pad + dp4, trackX + trackW, pad + gridH - dp4)
            canvas.drawRoundRect(rect, trackW * 0.5f, trackW * 0.5f, labelPaint)
            rect.set(trackX, pad + dp4, trackX + trackW, pad + gridH * 0.44f)
            canvas.drawRoundRect(rect, trackW * 0.5f, trackW * 0.5f, accentPaint)
        }

        // Vertical hairline divider before Game Mode card-scale miniature.
        val divX = pad + gridW + cachedIndicatorRight + dividerGap * 0.5f
        canvas.drawLine(divX, pad, divX, h - pad, hairlinePaint)

        // Right-side Game Mode carousel card scale silhouette driven by cardSizeDp.
        val cardBoxLeft = divX + dividerGap * 0.5f
        val cardBoxW = (w - pad - cardBoxLeft).coerceAtLeast(dp24)
        val cardBoxH = (h - pad * 2f).coerceAtLeast(dp24)
        val miniCardH = (cardBoxH * metrics.cardRatio).coerceAtLeast(dp22Px)
        val miniCardW = (miniCardH * 0.74f).coerceAtMost(cardBoxW)
        val cl = cardBoxLeft + (cardBoxW - miniCardW) * 0.5f
        val ct = pad + (cardBoxH - miniCardH) * 0.5f
        rect.set(cl, ct, cl + miniCardW, ct + miniCardH)
        canvas.drawRoundRect(rect, miniCardR, miniCardR, tilePaint)
        val innerPad = dp4
        if (miniCardW > innerPad * 2 + dp4 && miniCardH > innerPad * 2 + dp4) {
            val innerR = (miniCardR - dp2).coerceAtLeast(dp2)
            rect.set(cl + innerPad, ct + innerPad, cl + miniCardW - innerPad, ct + miniCardH - innerPad)
            canvas.drawRoundRect(rect, innerR, innerR, iconPaint)
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

/** Pure mapped dot coordinates for [SecondSeatPreviewView]. Host-tested. */
internal data class SeatPreviewDot(
    val id: String,
    val xPx: Float,
    val yPx: Float,
    val glyph: String,
    val isDpad: Boolean,
)

/** Pure host-tested 2D anchor mapping for the Second-Seat touch cluster preview. */
internal object SecondSeatPreviewLogic {

    fun glyphForAnchor(id: String): String = when (id.trim().lowercase()) {
        "up" -> "\u25B2"
        "down" -> "\u25BC"
        "left" -> "\u25C0"
        "right" -> "\u25B6"
        "a" -> "A"
        "b" -> "B"
        "x" -> "X"
        "y" -> "Y"
        "start" -> "ST"
        "select" -> "SL"
        else -> id.trim().take(2).uppercase()
    }

    fun isDpadAnchor(id: String): Boolean = when (id.trim().lowercase()) {
        "up", "down", "left", "right" -> true
        else -> false
    }

    fun layoutDots(
        anchors: List<SeatAnchor>,
        widthPx: Float,
        heightPx: Float,
        padPx: Float,
    ): List<SeatPreviewDot> {
        if (widthPx <= 0f || heightPx <= 0f) return emptyList()
        val safePadX = padPx.coerceIn(0f, widthPx * 0.45f)
        val safePadY = padPx.coerceIn(0f, heightPx * 0.45f)
        val spanW = (widthPx - safePadX * 2f).coerceAtLeast(0f)
        val spanH = (heightPx - safePadY * 2f).coerceAtLeast(0f)
        return anchors.map { anchor ->
            val nx = if (anchor.nx.isNaN() || anchor.nx.isInfinite()) 0.5f else anchor.nx.coerceIn(0f, 1f)
            val ny = if (anchor.ny.isNaN() || anchor.ny.isInfinite()) 0.5f else anchor.ny.coerceIn(0f, 1f)
            SeatPreviewDot(
                id = anchor.id,
                xPx = safePadX + nx * spanW,
                yPx = safePadY + ny * spanH,
                glyph = glyphForAnchor(anchor.id),
                isDpad = isDpadAnchor(anchor.id),
            )
        }
    }
}

/**
 * Reusable precomputed dot cache for [SecondSeatPreviewView]. Recomputes only
 * when anchors or viewport dimensions change so `onDraw()` and highlight-only
 * focus changes allocate zero objects. Pure; host-tested.
 */
internal class SecondSeatDotCache {
    private var cachedAnchors: List<SeatAnchor>? = null
    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private var cachedPadPx: Float = -1f

    var dots: List<SeatPreviewDot> = emptyList()
        private set

    var recomputeCount: Int = 0
        private set

    fun ensure(
        anchors: List<SeatAnchor>,
        widthPx: Float,
        heightPx: Float,
        padPx: Float,
    ): List<SeatPreviewDot> {
        if (cachedWidth == widthPx &&
            cachedHeight == heightPx &&
            cachedPadPx == padPx &&
            (cachedAnchors === anchors || cachedAnchors == anchors)
        ) {
            return dots
        }
        cachedAnchors = anchors
        cachedWidth = widthPx
        cachedHeight = heightPx
        cachedPadPx = padPx
        dots = SecondSeatPreviewLogic.layoutDots(anchors, widthPx, heightPx, padPx)
        recomputeCount++
        return dots
    }
}

/**
 * Live 2D miniature companion-panel canvas displaying every Second-Seat touch
 * anchor at its exact normalized `(nx, ny)` coordinate. Highlights whichever
 * anchor row currently has focus during coordinate editing. Zero allocations
 * inside `onDraw()`.
 */
internal class SecondSeatPreviewView(
    context: Context,
    tokens: ThemeTokens,
    private val accent: Int,
    initialAnchors: List<SeatAnchor>,
) : View(context) {

    private var anchors: List<SeatAnchor> = initialAnchors
    private var highlightedId: String? = null

    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.HAIRLINE_SUBTLE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.hairlineDivider(tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val dpadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceElevated(tokens)
    }
    private val dpadStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.accentHairline(accent, 0x99)
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1.25f)
    }
    private val faceFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.withAlpha(accent, 0xCC)
    }
    private val highlightHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsUiLogic.activePillFocusRing(accent)
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(2)
    }
    private val dpadTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsUiLogic.accentOnSurface(accent, tokens)
        textAlign = Paint.Align.CENTER
        textSize = context.dpF(9)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val faceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsUiLogic.accessibleOnAccent(accent)
        textAlign = Paint.Align.CENTER
        textSize = context.dpF(9)
        typeface = Typeface.DEFAULT_BOLD
    }

    private val outerR = context.dpF(UiDimens.RADIUS_MD)
    private val gridPad = context.dpF(6)
    private val baseRadius = context.dpF(11)
    private val dotPad = baseRadius + context.dpF(5)
    private val highlightHaloOffset = context.dpF(1.5f)
    private val borderInset = borderPaint.strokeWidth * 0.5f
    private val dpadTextOffsetY = -(dpadTextPaint.descent() + dpadTextPaint.ascent()) * 0.5f
    private val faceTextOffsetY = -(faceTextPaint.descent() + faceTextPaint.ascent()) * 0.5f

    private val dotCache = SecondSeatDotCache()
    private var cachedWidth = 0f
    private var cachedHeight = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setAnchors(next: List<SeatAnchor>) {
        if (anchors != next) {
            anchors = next
            if (width > 0 && height > 0) {
                rebuildDots(width.toFloat(), height.toFloat())
            }
            invalidate()
        }
    }

    fun setHighlightedAnchor(id: String?) {
        if (highlightedId != id) {
            highlightedId = id
            invalidate()
        }
    }

    private fun rebuildDots(w: Float, h: Float) {
        cachedWidth = w
        cachedHeight = h
        if (w <= 0f || h <= 0f) return
        dotCache.ensure(anchors, w, h, dotPad)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildDots(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (w != cachedWidth || h != cachedHeight) {
            rebuildDots(w, h)
        }

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerR, outerR, bgPaint)

        // Subtle alignment gridlines (25% / 50% / 75% X and 50% Y).
        val gridBottom = h - gridPad
        for (i in GRIDLINE_FRACTIONS.indices) {
            val x = w * GRIDLINE_FRACTIONS[i]
            canvas.drawLine(x, gridPad, x, gridBottom, gridPaint)
        }
        canvas.drawLine(gridPad, h * 0.5f, w - gridPad, h * 0.5f, gridPaint)

        val dots = dotCache.dots
        for (i in 0 until dots.size) {
            val dot = dots[i]
            val focused = dot.id == highlightedId
            val r = if (focused) baseRadius * 1.22f else baseRadius
            if (dot.isDpad) {
                canvas.drawCircle(dot.xPx, dot.yPx, r, dpadFillPaint)
                canvas.drawCircle(dot.xPx, dot.yPx, r, if (focused) highlightHaloPaint else dpadStrokePaint)
                canvas.drawText(dot.glyph, dot.xPx, dot.yPx + dpadTextOffsetY, dpadTextPaint)
            } else {
                canvas.drawCircle(dot.xPx, dot.yPx, r, faceFillPaint)
                if (focused) {
                    canvas.drawCircle(dot.xPx, dot.yPx, r + highlightHaloOffset, highlightHaloPaint)
                }
                canvas.drawText(dot.glyph, dot.xPx, dot.yPx + faceTextOffsetY, faceTextPaint)
            }
        }

        rect.set(borderInset, borderInset, w - borderInset, h - borderInset)
        canvas.drawRoundRect(rect, outerR, outerR, borderPaint)
    }

    private companion object {
        val GRIDLINE_FRACTIONS = floatArrayOf(0.25f, 0.5f, 0.75f)
    }
}
