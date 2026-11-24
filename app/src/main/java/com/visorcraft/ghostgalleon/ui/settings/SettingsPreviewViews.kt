package com.visorcraft.ghostgalleon.ui.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.audio.SoundPackId
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.input.SeatAnchor
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pure panel role specification inside [DisplayTopologyPreviewView]. Host-tested. */
internal data class TopologyPanelSpec(
    val displayId: Int,
    val isInteractive: Boolean,
    val isLaunchTarget: Boolean,
    val isCompanion: Boolean,
    val badgeText: String,
)

/** Pure topology schematic state for [DisplayTopologyPreviewView]. Host-tested. */
internal data class TopologyDiagramState(
    val isDual: Boolean,
    val panels: List<TopologyPanelSpec>,
)

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
    const val MIN_FONT_SCALE = 0.85f
    const val MAX_FONT_SCALE = 1.25f
    const val AUDIO_SPECTRUM_BAR_COUNT = 16
    const val AUDIO_PULSE_DURATION_MS = 240L

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

    /**
     * Scale miniature typography glyph bar thickness by `tokens.fontScale`
     * clamped to `[0.85f..1.25f]`. Pure; host-tested.
     */
    fun scaledBarHeightPx(
        baseDp: Float,
        fontScale: Float,
        density: Float,
    ): Float {
        val safeScale = if (fontScale.isNaN() || fontScale.isInfinite()) {
            1f
        } else {
            fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
        val safeBase = if (baseDp.isNaN() || baseDp.isInfinite()) 1f else baseDp.coerceAtLeast(0.5f)
        val safeDensity = if (density.isNaN() || density.isInfinite()) 1f else density.coerceAtLeast(0.5f)
        return safeBase * safeScale * safeDensity
    }

    /**
     * WCAG 1.4.11 (`>= 3.0:1`) preview focus stroke color computed against
     * `surfaceElevated(tokens)` so the ring stays clearly visible over sunken,
     * card, and elevated preview cards across Ghost, Teal, OLED, Neon, and custom packs.
     */
    fun previewFocusStroke(accent: Int, tokens: ThemeTokens): Int =
        OverlayPrimitives.accessibleFocusStroke(accent, ThemePack.surfaceElevated(tokens))

    /**
     * Proportional height scale (~0.78x) for flanking Game Mode neighbor cards
     * behind the centered card silhouette, subtly deepening as [cardRatio] grows.
     * Pure; host-tested.
     */
    fun flankingCardScale(cardRatio: Float): Float {
        val safeRatio = if (cardRatio.isNaN() || cardRatio.isInfinite()) {
            0.75f
        } else {
            cardRatio.coerceIn(0.4f, 1.0f)
        }
        return (0.81f - safeRatio * 0.06f).coerceIn(0.72f, 0.80f)
    }

    /**
     * Proportional vertical label reserve for mini grid tiles so 7–8 column
     * dense grids never crowd their icon squares (`minOf(7dp, cellH * 0.22f)`).
     * Pure; host-tested.
     */
    fun clampedLabelReservePx(
        cellH: Float,
        showLabels: Boolean,
        density: Float,
    ): Float {
        if (!showLabels || cellH <= 0f || cellH.isNaN() || cellH.isInfinite()) return 0f
        val safeDensity = if (density.isNaN() || density.isInfinite()) 1f else density.coerceAtLeast(0.5f)
        return minOf(7f * safeDensity, cellH * 0.22f).coerceAtLeast(0f)
    }

    /**
     * Populate [out] in-place with normalized `[0f..1f]` harmonic spectrum bar
     * amplitudes characterizing [packId] scaled by [sfxVol] and [ambientVol].
     * Zero heap allocations when called with a preallocated array. Pure; host-tested.
     */
    fun fillAudioSpectrumProfile(
        packId: SoundPackId,
        sfxVol: Int,
        ambientVol: Int,
        enabled: Boolean,
        out: FloatArray,
    ) {
        val count = out.size
        if (count == 0) return
        if (!enabled || !packId.isPlayable) {
            out.fill(0f)
            return
        }
        val sfx = sfxVol.coerceIn(0, 100) / 100f
        val amb = ambientVol.coerceIn(0, 100) / 100f
        if (sfx <= 0f && amb <= 0f) {
            out.fill(0f)
            return
        }
        val denom = (count - 1).coerceAtLeast(1).toFloat()
        for (i in 0 until count) {
            val t = i / denom
            val env = when (packId) {
                // Warm nautical low-mid swell with mellow upper harmonics.
                SoundPackId.GHOST, SoundPackId.AUTO ->
                    (0.86 * gaussian(t, 0.28, 0.22) + 0.44 * gaussian(t, 0.66, 0.20)).toFloat()
                // Crisp playful dual mid-high chime peaks.
                SoundPackId.THREEDS ->
                    (0.74 * gaussian(t, 0.42, 0.14) + 0.88 * gaussian(t, 0.78, 0.15)).toFloat()
                // Minimalist tight precision center impulse with quiet shoulders.
                SoundPackId.OLED ->
                    (0.95 * gaussian(t, 0.50, 0.11) + 0.22 * gaussian(t, 0.22, 0.12) +
                        0.22 * gaussian(t, 0.78, 0.12)).toFloat()
                // Energetic synthwave sub-bass + sparkling treble scoop.
                SoundPackId.NEON ->
                    (0.94 * gaussian(t, 0.14, 0.14) + 0.34 * gaussian(t, 0.50, 0.16) +
                        0.90 * gaussian(t, 0.85, 0.14)).toFloat()
                SoundPackId.OFF -> 0f
            }
            val ambientBed = amb * 0.20f * (0.55f + 0.45f * sin(t * PI).toFloat())
            val bar = maxOf(env * sfx, ambientBed)
            out[i] = bar.coerceIn(0f, 1f)
        }
    }

    /** Allocates a fresh spectrum array for host unit testing of [fillAudioSpectrumProfile]. */
    fun audioSpectrumProfile(
        packId: SoundPackId,
        sfxVol: Int,
        ambientVol: Int,
        enabled: Boolean,
        barCount: Int = AUDIO_SPECTRUM_BAR_COUNT,
    ): FloatArray {
        val out = FloatArray(barCount.coerceAtLeast(0))
        fillAudioSpectrumProfile(packId, sfxVol, ambientVol, enabled, out)
        return out
    }

    /** Normalized `[0f..1f]` ambient soundscape ribbon amplitude. Pure; host-tested. */
    fun audioAmbientFloor(
        packId: SoundPackId,
        ambientVol: Int,
        soundEnabled: Boolean,
        ambientEnabled: Boolean,
    ): Float {
        if (!soundEnabled || !ambientEnabled || !packId.isPlayable) return 0f
        return (ambientVol.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
    }

    /**
     * Resolve pure schematic panel layout for [DisplayTopologyPreviewView]
     * without assuming display IDs are hardcoded to `0` or `1`. Pure; host-tested.
     */
    fun topologyDiagramState(
        surfaceMode: SurfaceMode,
        primaryId: Int,
        companionId: Int?,
        launchId: Int,
    ): TopologyDiagramState {
        if (surfaceMode == SurfaceMode.DUAL && companionId != null && companionId != primaryId) {
            return TopologyDiagramState(
                isDual = true,
                panels = listOf(
                    TopologyPanelSpec(
                        displayId = companionId,
                        isInteractive = false,
                        isLaunchTarget = launchId == companionId,
                        isCompanion = true,
                        badgeText = "#$companionId",
                    ),
                    TopologyPanelSpec(
                        displayId = primaryId,
                        isInteractive = true,
                        isLaunchTarget = launchId == primaryId,
                        isCompanion = false,
                        badgeText = "#$primaryId",
                    ),
                ),
            )
        }
        return TopologyDiagramState(
            isDual = false,
            panels = listOf(
                TopologyPanelSpec(
                    displayId = primaryId,
                    isInteractive = true,
                    isLaunchTarget = launchId == primaryId,
                    isCompanion = false,
                    badgeText = "#$primaryId",
                ),
            ),
        )
    }

    private fun gaussian(x: Float, mean: Double, sigma: Double): Double {
        val d = (x - mean) / sigma
        return exp(-0.5 * d * d)
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
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x28FFFFFF
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val statusPillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val density = context.resources.displayMetrics.density
    private val outerRadius = context.dpF(UiDimens.RADIUS_MD)
    private val inset = hairlinePaint.strokeWidth * 0.5f
    private val pad = context.dpF(10)
    private val dockH = context.dpF(14)
    private val gap = context.dpF(8)
    private val dp2 = context.dpF(2)
    private val dp3 = context.dpF(3)
    private val dp4 = context.dpF(4)
    private val dp5 = context.dpF(5)
    private val dp6 = context.dpF(6)
    private val dp7 = context.dpF(7)
    private val dp9 = context.dpF(9)
    private val dp12 = context.dpF(12)
    private val dp16 = context.dpF(16)
    private val dp20 = context.dpF(20)
    private val dp28 = context.dpF(28)
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
    private var cachedTitleBarH = 0f
    private var cachedSubBarH = 0f
    private var cachedCardLineH = 0f

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
        val cardFill = ThemePack.cardFill(tokens)
        cardPaint.color = cardFill
        elevatedPaint.color = ThemePack.surfaceElevated(tokens)
        accentPaint.color = accent
        onAccentPaint.color = SettingsUiLogic.accessibleOnAccent(accent)
        mutedPaint.color = UiDimens.ALPHA_SUBTLE
        hairlinePaint.color = ThemePack.hairlineDivider(tokens)
        focusStrokePaint.color = SettingsPreviewLogic.previewFocusStroke(accent, tokens)
        statusPillPaint.color = TileBackgrounds.statusPillFill(cardFill)
        statusDotPaint.color = SettingsUiLogic.accentOnSunken(accent, tokens)
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
        cachedTitleBarH = SettingsPreviewLogic.scaledBarHeightPx(5f, tokens.fontScale, density)
        cachedSubBarH = SettingsPreviewLogic.scaledBarHeightPx(3.5f, tokens.fontScale, density)
        cachedCardLineH = SettingsPreviewLogic.scaledBarHeightPx(3f, tokens.fontScale, density)
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

            // Tiny fontScale-responsive title line below nested art.
            val lineTop = artBottom + dp3
            val lineH = cachedCardLineH
            if (lineTop + lineH < b - dp2) {
                rect.set(l + artInset, lineTop, l + (r - l) * 0.65f, lineTop + lineH)
                canvas.drawRoundRect(rect, lineH * 0.5f, lineH * 0.5f, mutedPaint)
            }

            // Subtle top specular rim on focused card + WCAG-lifted focus ring.
            if (cell.focused) {
                val rimInset = tileRadius.coerceAtMost((r - l) * 0.35f)
                canvas.drawLine(l + rimInset, t + dp2, r - rimInset, t + dp2, rimPaint)
            }
            rect.set(l, t, r, b)
            canvas.drawRoundRect(
                rect,
                tileRadius,
                tileRadius,
                if (cell.focused) focusStrokePaint else hairlinePaint,
            )
        }

        // Right-side mini hero preview: top-right mini statusPill, fontScale title/subtitle bars, CTA pill.
        val ctaLeft = pad + cachedCardsAreaW + gap
        val ctaRight = w - pad
        val statusW = minOf(dp28, (ctaRight - ctaLeft) * 0.46f)
        val statusH = dp9
        val statusL = ctaRight - statusW
        val statusT = pad
        rect.set(statusL, statusT, ctaRight, statusT + statusH)
        canvas.drawRoundRect(rect, statusH * 0.5f, statusH * 0.5f, statusPillPaint)
        canvas.drawRoundRect(rect, statusH * 0.5f, statusH * 0.5f, hairlinePaint)
        // Mini clock bar + battery dot inside statusPill silhouette.
        val clockBarH = dp2
        val clockBarW = (statusW * 0.42f).coerceAtLeast(dp4)
        val statusMidY = statusT + statusH * 0.5f
        rect.set(statusL + dp4, statusMidY - clockBarH * 0.5f, statusL + dp4 + clockBarW, statusMidY + clockBarH * 0.5f)
        canvas.drawRoundRect(rect, clockBarH * 0.5f, clockBarH * 0.5f, mutedPaint)
        canvas.drawCircle(ctaRight - dp4, statusMidY, dp2, statusDotPaint)

        val ctaTop = statusT + statusH + dp4
        val barH = cachedTitleBarH
        rect.set(ctaLeft, ctaTop, ctaLeft + (ctaRight - ctaLeft) * 0.82f, ctaTop + barH)
        canvas.drawRoundRect(rect, barH * 0.5f, barH * 0.5f, elevatedPaint)

        val subTop = ctaTop + barH + dp4
        val subH = cachedSubBarH
        rect.set(ctaLeft, subTop, ctaLeft + (ctaRight - ctaLeft) * 0.56f, subTop + subH)
        canvas.drawRoundRect(rect, subH * 0.5f, subH * 0.5f, mutedPaint)

        val pillH = dp16
        val pillTop = (pad + cachedDeckAreaH - pillH).coerceAtLeast(subTop + subH + dp4)
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
        color = SettingsPreviewLogic.previewFocusStroke(accent, tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1.75f)
    }
    private val flankTilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.blendColors(ThemePack.surfaceSunken(tokens), ThemePack.cardFill(tokens), 0.58f)
    }
    private val flankHairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.HAIRLINE_SUBTLE
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
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
    private val flankCardR = (miniCardR * 0.78f).coerceAtLeast(dp2)

    private val cellCache = MiniGridCellCache()
    private val carouselClipRect = RectF()
    private val centerCardRect = RectF()
    private val centerInnerRect = RectF()
    private val leftFlankRect = RectF()
    private val rightFlankRect = RectF()
    private var hasCenterInner = false
    private var cachedDivX = 0f
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
                next.horizontal != metrics.horizontal ||
                next.cardRatio != metrics.cardRatio
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

        // Precompute Game Mode center card + flanking depth neighbors (zero onDraw() allocs).
        cachedDivX = pad + cachedGridW + cachedIndicatorRight + dividerGap * 0.5f
        val cardBoxLeft = cachedDivX + dividerGap * 0.5f
        val cardBoxRight = (w - pad).coerceAtLeast(cardBoxLeft + dp24)
        val cardBoxW = cardBoxRight - cardBoxLeft
        val cardBoxH = (h - pad * 2f).coerceAtLeast(dp24)
        carouselClipRect.set(cardBoxLeft, pad, cardBoxRight, h - pad)

        val miniCardH = (cardBoxH * metrics.cardRatio).coerceAtLeast(dp22Px)
        val miniCardW = (miniCardH * 0.74f).coerceAtMost(cardBoxW * 0.72f)
        val cl = cardBoxLeft + (cardBoxW - miniCardW) * 0.5f
        val ct = pad + (cardBoxH - miniCardH) * 0.5f
        centerCardRect.set(cl, ct, cl + miniCardW, ct + miniCardH)

        val innerPad = dp4
        hasCenterInner = miniCardW > innerPad * 2 + dp4 && miniCardH > innerPad * 2 + dp4
        if (hasCenterInner) {
            centerInnerRect.set(
                cl + innerPad,
                ct + innerPad,
                cl + miniCardW - innerPad,
                ct + miniCardH - innerPad,
            )
        }

        val flankScale = SettingsPreviewLogic.flankingCardScale(metrics.cardRatio)
        val flankH = miniCardH * flankScale
        val flankW = miniCardW * flankScale
        val flankTop = pad + (cardBoxH - flankH) * 0.5f
        val flankStep = miniCardW * 0.56f + dp4
        val centerMidX = cl + miniCardW * 0.5f
        leftFlankRect.set(
            centerMidX - flankStep - flankW * 0.5f,
            flankTop,
            centerMidX - flankStep + flankW * 0.5f,
            flankTop + flankH,
        )
        rightFlankRect.set(
            centerMidX + flankStep - flankW * 0.5f,
            flankTop,
            centerMidX + flankStep + flankW * 0.5f,
            flankTop + flankH,
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
            val labelReserve = SettingsPreviewLogic.clampedLabelReservePx(
                cellH = cellH,
                showLabels = metrics.showLabels,
                density = density,
            )
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

            if (metrics.showLabels && labelReserve > dp2) {
                val barW = (cellW * 0.58f).coerceAtLeast(dp6)
                val barH = minOf(dp25, (labelReserve * 0.42f).coerceAtLeast(dp1))
                val bx = l + (cellW - barW) * 0.5f
                val by = b - (labelReserve + barH) * 0.5f
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
        canvas.drawLine(cachedDivX, pad, cachedDivX, h - pad, hairlinePaint)

        // Right-side Game Mode carousel: dimmed flanking neighbor cards behind centered card.
        val save = canvas.save()
        canvas.clipRect(carouselClipRect)
        canvas.drawRoundRect(leftFlankRect, flankCardR, flankCardR, flankTilePaint)
        canvas.drawRoundRect(leftFlankRect, flankCardR, flankCardR, flankHairlinePaint)
        canvas.drawRoundRect(rightFlankRect, flankCardR, flankCardR, flankTilePaint)
        canvas.drawRoundRect(rightFlankRect, flankCardR, flankCardR, flankHairlinePaint)
        canvas.drawRoundRect(centerCardRect, miniCardR, miniCardR, tilePaint)
        if (hasCenterInner) {
            val innerR = (miniCardR - dp2).coerceAtLeast(dp2)
            canvas.drawRoundRect(centerInnerRect, innerR, innerR, iconPaint)
        }
        canvas.drawRoundRect(centerCardRect, miniCardR, miniCardR, focusPaint)
        canvas.restoreToCount(save)

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

/** Pure host-tested 2D anchor mapping, touch hit-testing, and collision checks. */
internal object SecondSeatPreviewLogic {

    const val COLLISION_THRESHOLD_NORM: Float = 0.06f

    fun clampNormalizedCoord(raw: Float): Float =
        if (raw.isNaN() || raw.isInfinite()) 0.5f else raw.coerceIn(0f, 1f)

    fun pixelToNormalized(px: Float, sizePx: Float, padPx: Float): Float {
        if (sizePx <= 0f || px.isNaN() || px.isInfinite()) return 0.5f
        val safePad = padPx.coerceIn(0f, sizePx * 0.45f)
        val span = (sizePx - safePad * 2f).coerceAtLeast(1f)
        return clampNormalizedCoord((px - safePad) / span)
    }

    /**
     * Find the nearest preview dot within [maxRadiusPx] of `(touchX, touchY)`,
     * or `null` when no anchor falls within hit range. Pure; host-tested.
     */
    fun findNearestAnchor(
        dots: List<SeatPreviewDot>,
        touchX: Float,
        touchY: Float,
        maxRadiusPx: Float,
    ): SeatPreviewDot? {
        if (dots.isEmpty() || maxRadiusPx <= 0f || touchX.isNaN() || touchY.isNaN()) return null
        val maxDistSq = maxRadiusPx * maxRadiusPx
        var best: SeatPreviewDot? = null
        var bestDistSq = Float.MAX_VALUE
        for (i in 0 until dots.size) {
            val dot = dots[i]
            val dx = dot.xPx - touchX
            val dy = dot.yPx - touchY
            val dSq = dx * dx + dy * dy
            if (dSq <= maxDistSq && dSq < bestDistSq) {
                bestDistSq = dSq
                best = dot
            }
        }
        return best
    }

    /**
     * Detect IDs of any anchors whose normalized Euclidean separation is
     * strictly below [minDistNorm] (`0.06` by default). Pure; host-tested.
     */
    fun overlappingAnchorIds(
        anchors: List<SeatAnchor>,
        minDistNorm: Float = COLLISION_THRESHOLD_NORM,
    ): Set<String> {
        if (anchors.size < 2 || minDistNorm <= 0f) return emptySet()
        val thresholdSq = minDistNorm * minDistNorm
        val out = LinkedHashSet<String>()
        for (i in 0 until anchors.size) {
            val a = anchors[i]
            val ax = clampNormalizedCoord(a.nx)
            val ay = clampNormalizedCoord(a.ny)
            for (j in i + 1 until anchors.size) {
                val b = anchors[j]
                val dx = ax - clampNormalizedCoord(b.nx)
                val dy = ay - clampNormalizedCoord(b.ny)
                if (dx * dx + dy * dy < thresholdSq) {
                    out.add(a.id)
                    out.add(b.id)
                }
            }
        }
        return out
    }

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
            val nx = clampNormalizedCoord(anchor.nx)
            val ny = clampNormalizedCoord(anchor.ny)
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

    var onAnchorDragged: ((id: String, nx: Float, ny: Float, committed: Boolean) -> Unit)? = null

    private var anchors: List<SeatAnchor> = initialAnchors
    private var highlightedId: String? = null
    private var draggingId: String? = null
    private var overlappingIds: Set<String> = SecondSeatPreviewLogic.overlappingAnchorIds(initialAnchors)
    private var cachedCoordLabel: String = ""

    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.HAIRLINE_SUBTLE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.accentHairline(SettingsPreviewLogic.previewFocusStroke(accent, tokens), 0x88)
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
    private val warningRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsUiLogic.COLOR_WARNING_AMBER
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(2)
    }
    private val coordBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.withAlpha(ThemePack.cardFill(tokens), 0xE6)
    }
    private val coordTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiDimens.ALPHA_PRIMARY
        textAlign = Paint.Align.RIGHT
        textSize = context.dpF(9.5f)
        typeface = Typeface.MONOSPACE
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
    private val hitRadiusPx = context.dpF(28)
    private val highlightHaloOffset = context.dpF(1.5f)
    private val warningRingOffset = context.dpF(3.5f)
    private val badgePadH = context.dpF(6)
    private val badgePadV = context.dpF(3)
    private val badgeR = context.dpF(6)
    private val borderInset = borderPaint.strokeWidth * 0.5f
    private val dpadTextOffsetY = -(dpadTextPaint.descent() + dpadTextPaint.ascent()) * 0.5f
    private val faceTextOffsetY = -(faceTextPaint.descent() + faceTextPaint.ascent()) * 0.5f
    private val coordTextHeight = coordTextPaint.descent() - coordTextPaint.ascent()
    private val coordTextOffsetY = -(coordTextPaint.descent() + coordTextPaint.ascent()) * 0.5f

    private val dotCache = SecondSeatDotCache()
    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedCoordTextWidth = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setAnchors(next: List<SeatAnchor>) {
        if (anchors != next) {
            anchors = next
            overlappingIds = SecondSeatPreviewLogic.overlappingAnchorIds(next)
            syncCoordLabel()
            if (width > 0 && height > 0) {
                rebuildDots(width.toFloat(), height.toFloat())
            }
            invalidate()
        }
    }

    fun setHighlightedAnchor(id: String?) {
        if (highlightedId != id) {
            highlightedId = id
            syncCoordLabel()
            invalidate()
        }
    }

    private fun syncCoordLabel() {
        val activeId = highlightedId
        val anchor = if (activeId != null) anchors.firstOrNull { it.id == activeId } else null
        if (anchor == null) {
            cachedCoordLabel = ""
            cachedCoordTextWidth = 0f
            return
        }
        val glyph = SecondSeatPreviewLogic.glyphForAnchor(anchor.id)
        val nx = SecondSeatPreviewLogic.clampNormalizedCoord(anchor.nx)
        val ny = SecondSeatPreviewLogic.clampNormalizedCoord(anchor.ny)
        cachedCoordLabel = String.format(Locale.US, "%s %.2f, %.2f", glyph, nx, ny)
        cachedCoordTextWidth = coordTextPaint.measureText(cachedCoordLabel)
    }

    private fun rebuildDots(w: Float, h: Float) {
        cachedWidth = w
        cachedHeight = h
        if (w <= 0f || h <= 0f) return
        dotCache.ensure(anchors, w, h, dotPad)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (w != cachedWidth || h != cachedHeight) rebuildDots(w, h)
                val hit = SecondSeatPreviewLogic.findNearestAnchor(
                    dots = dotCache.dots,
                    touchX = event.x,
                    touchY = event.y,
                    maxRadiusPx = hitRadiusPx,
                ) ?: return false
                draggingId = hit.id
                highlightedId = hit.id
                parent?.requestDisallowInterceptTouchEvent(true)
                syncCoordLabel()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = draggingId ?: return false
                val nx = quantizeCoord(SecondSeatPreviewLogic.pixelToNormalized(event.x, w, dotPad))
                val ny = quantizeCoord(SecondSeatPreviewLogic.pixelToNormalized(event.y, h, dotPad))
                applyDraggedAnchor(id, nx, ny, committed = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val id = draggingId ?: return false
                draggingId = null
                parent?.requestDisallowInterceptTouchEvent(false)
                val nx = quantizeCoord(SecondSeatPreviewLogic.pixelToNormalized(event.x, w, dotPad))
                val ny = quantizeCoord(SecondSeatPreviewLogic.pixelToNormalized(event.y, h, dotPad))
                applyDraggedAnchor(id, nx, ny, committed = true)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (draggingId != null) {
                    draggingId = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun quantizeCoord(value: Float): Float =
        ((SecondSeatPreviewLogic.clampNormalizedCoord(value) * 100f).roundToInt() / 100f)

    private fun applyDraggedAnchor(id: String, nx: Float, ny: Float, committed: Boolean) {
        val updated = anchors.map { a -> if (a.id == id) SeatAnchor(id, nx, ny) else a }
        anchors = updated
        overlappingIds = SecondSeatPreviewLogic.overlappingAnchorIds(updated)
        syncCoordLabel()
        rebuildDots(width.toFloat(), height.toFloat())
        invalidate()
        onAnchorDragged?.invoke(id, nx, ny, committed)
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
        // Focus crosshairs intersecting active/dragged anchor.
        val activeId = highlightedId
        if (activeId != null) {
            for (i in 0 until dots.size) {
                val dot = dots[i]
                if (dot.id == activeId) {
                    canvas.drawLine(gridPad, dot.yPx, w - gridPad, dot.yPx, crosshairPaint)
                    canvas.drawLine(dot.xPx, gridPad, dot.xPx, gridBottom, crosshairPaint)
                    break
                }
            }
        }

        for (i in 0 until dots.size) {
            val dot = dots[i]
            val focused = dot.id == activeId
            val colliding = dot.id in overlappingIds
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
            if (colliding) {
                canvas.drawCircle(dot.xPx, dot.yPx, r + warningRingOffset, warningRingPaint)
            }
        }

        // Top-right coordinate readout pill while an anchor is focused or dragged.
        if (cachedCoordLabel.isNotEmpty()) {
            val pillW = cachedCoordTextWidth + badgePadH * 2f
            val pillH = coordTextHeight + badgePadV * 2f
            val pr = w - gridPad
            val pt = gridPad
            rect.set(pr - pillW, pt, pr, pt + pillH)
            canvas.drawRoundRect(rect, badgeR, badgeR, coordBgPaint)
            canvas.drawRoundRect(rect, badgeR, badgeR, borderPaint)
            canvas.drawText(
                cachedCoordLabel,
                pr - badgePadH,
                pt + pillH * 0.5f + coordTextOffsetY,
                coordTextPaint,
            )
        }

        rect.set(borderInset, borderInset, w - borderInset, h - borderInset)
        canvas.drawRoundRect(rect, outerR, outerR, borderPaint)
    }

    private companion object {
        val GRIDLINE_FRACTIONS = floatArrayOf(0.25f, 0.5f, 0.75f)
    }
}

/**
 * Live zero-allocation 16-bar harmonic spectrum & ambient ribbon preview for
 * Settings → Audio. Reflects each [SoundPackId]'s acoustic timbre scaled by
 * SFX/Ambient volumes, flattens cleanly when muted/OFF, and plays a cancelable
 * `240ms` motion-gated pulse when auditioning cues on the Sound Test Pad.
 */
internal class AudioSoundscapePreviewView(
    context: Context,
    tokens: ThemeTokens,
    accent: Int,
    initialPack: SoundPackId,
    initialSfxVol: Int,
    initialAmbientVol: Int,
    initialSoundEnabled: Boolean,
    initialAmbientEnabled: Boolean,
) : View(context) {

    private val bars = FloatArray(SettingsPreviewLogic.AUDIO_SPECTRUM_BAR_COUNT)
    private val rect = RectF()
    private val ambientPath = Path()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.hairlineDivider(tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.HAIRLINE_SUBTLE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsPreviewLogic.previewFocusStroke(accent, tokens)
    }
    private val barMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.withAlpha(UiDimens.ALPHA_MUTED, 0x44)
    }
    private val ambientFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.accentGlowColor(accent, 0x3A)
        style = Paint.Style.FILL
    }
    private val ambientStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.accentHairline(SettingsPreviewLogic.previewFocusStroke(accent, tokens), 0xAA)
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1.5f)
    }

    private val outerR = context.dpF(UiDimens.RADIUS_MD)
    private val padH = context.dpF(12)
    private val padV = context.dpF(10)
    private val barGap = context.dpF(4)
    private val minBarH = context.dpF(3)
    private val borderInset = borderPaint.strokeWidth * 0.5f

    private var packId: SoundPackId = initialPack
    private var sfxVol: Int = initialSfxVol
    private var ambientVol: Int = initialAmbientVol
    private var soundEnabled: Boolean = initialSoundEnabled
    private var ambientEnabled: Boolean = initialAmbientEnabled
    private var ambientFloor: Float = 0f
    private var pulseAmount: Float = 0f
    private var pulseFocusBin: Float = 0.5f
    private var pulseAnim: ValueAnimator? = null
    private var cachedWidth: Float = 0f
    private var cachedHeight: Float = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        syncProfile()
    }

    fun update(
        nextPack: SoundPackId,
        nextSfxVol: Int,
        nextAmbientVol: Int,
        nextSoundEnabled: Boolean,
        nextAmbientEnabled: Boolean,
    ) {
        if (packId == nextPack &&
            sfxVol == nextSfxVol &&
            ambientVol == nextAmbientVol &&
            soundEnabled == nextSoundEnabled &&
            ambientEnabled == nextAmbientEnabled
        ) {
            return
        }
        packId = nextPack
        sfxVol = nextSfxVol
        ambientVol = nextAmbientVol
        soundEnabled = nextSoundEnabled
        ambientEnabled = nextAmbientEnabled
        syncProfile()
        if (width > 0 && height > 0) {
            rebuildAmbientPath(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    fun pulseAudition(cue: SoundCue) {
        pulseAnim?.cancel()
        pulseAnim = null
        if (!soundEnabled || !packId.isPlayable || sfxVol <= 0) {
            pulseAmount = 0f
            invalidate()
            return
        }
        pulseFocusBin = when (cue) {
            SoundCue.NAV_TICK -> 0.52f
            SoundCue.CONFIRM -> 0.62f
            SoundCue.LAUNCH_GAME -> 0.24f
            SoundCue.SWAP_DISPLAY -> 0.40f
            SoundCue.FAVORITE_ON -> 0.76f
            SoundCue.RA_ACHIEVEMENT -> 0.84f
            else -> 0.50f
        }
        if (!UiDimens.areAnimationsEnabled(context)) {
            pulseAmount = 0f
            invalidate()
            return
        }
        pulseAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SettingsPreviewLogic.AUDIO_PULSE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                pulseAmount = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        pulseAnim?.cancel()
        pulseAnim = null
        pulseAmount = 0f
        super.onDetachedFromWindow()
    }

    private fun syncProfile() {
        SettingsPreviewLogic.fillAudioSpectrumProfile(
            packId = packId,
            sfxVol = sfxVol,
            ambientVol = if (ambientEnabled) ambientVol else 0,
            enabled = soundEnabled,
            out = bars,
        )
        ambientFloor = SettingsPreviewLogic.audioAmbientFloor(
            packId = packId,
            ambientVol = ambientVol,
            soundEnabled = soundEnabled,
            ambientEnabled = ambientEnabled,
        )
    }

    private fun rebuildAmbientPath(w: Float, h: Float) {
        cachedWidth = w
        cachedHeight = h
        ambientPath.reset()
        if (w <= padH * 2f || h <= padV * 2f || ambientFloor <= 0.005f) return
        val innerW = w - padH * 2f
        val baseY = h - padV
        val maxRibbonH = (h - padV * 2f) * 0.42f * ambientFloor
        ambientPath.moveTo(padH, baseY)
        val steps = 16
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = padH + innerW * t
            val wave = 0.68f + 0.32f * sin(t * PI).toFloat()
            val y = baseY - maxRibbonH * wave
            ambientPath.lineTo(x, y)
        }
        ambientPath.lineTo(padH + innerW, baseY)
        ambientPath.close()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildAmbientPath(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (w != cachedWidth || h != cachedHeight) {
            rebuildAmbientPath(w, h)
        }

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerR, outerR, bgPaint)

        // Subtle mid-level reference hairline.
        val midY = h * 0.5f
        canvas.drawLine(padH, midY, w - padH, midY, gridLinePaint)

        // Soft ambient soundscape floor ribbon.
        if (ambientFloor > 0.005f) {
            canvas.drawPath(ambientPath, ambientFillPaint)
            canvas.drawPath(ambientPath, ambientStrokePaint)
        }

        // 16 harmonic spectrum bars scaled live by sfxVolume & audition pulse.
        val count = bars.size
        val innerW = (w - padH * 2f).coerceAtLeast(1f)
        val innerH = (h - padV * 2f).coerceAtLeast(minBarH)
        val barW = ((innerW - barGap * (count - 1)) / count).coerceAtLeast(2f)
        val baseY = h - padV
        val barR = barW * 0.45f
        val denom = (count - 1).coerceAtLeast(1).toFloat()
        val active = soundEnabled && packId.isPlayable

        for (i in 0 until count) {
            val baseAmp = bars[i]
            val pulseBoost = if (pulseAmount > 0.001f) {
                val dist = abs(i / denom - pulseFocusBin)
                val falloff = (1f - dist * 2.2f).coerceAtLeast(0.15f)
                pulseAmount * 0.28f * falloff
            } else {
                0f
            }
            val amp = (baseAmp + pulseBoost).coerceIn(0f, 1f)
            val barH = if (amp > 0.005f) {
                (innerH * amp).coerceAtLeast(minBarH)
            } else {
                minBarH
            }
            val left = padH + i * (barW + barGap)
            rect.set(left, baseY - barH, left + barW, baseY)
            canvas.drawRoundRect(
                rect,
                barR,
                barR,
                if (active && amp > 0.005f) barPaint else barMutedPaint,
            )
        }

        rect.set(borderInset, borderInset, w - borderInset, h - borderInset)
        canvas.drawRoundRect(rect, outerR, outerR, borderPaint)
    }
}

/**
 * Live zero-allocation schematic preview of Dual-Screen (clamshell top/bottom)
 * vs Single-Screen (`TOP_STRIP` 16:9) display routing. Highlights the active
 * Interactive panel with a WCAG focus ring + mini grid, and the Companion /
 * Launch panel with a hero silhouette and `#id` display badges.
 */
internal class DisplayTopologyPreviewView(
    context: Context,
    tokens: ThemeTokens,
    accent: Int,
    private var state: TopologyDiagramState,
) : View(context) {

    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.cardFill(tokens)
    }
    private val elevatedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceElevated(tokens)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
    }
    private val focusStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsPreviewLogic.previewFocusStroke(accent, tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1.75f)
    }
    private val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.hairlineDivider(tokens)
        style = Paint.Style.STROKE
        strokeWidth = context.strokePx(1).toFloat()
    }
    private val hingePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiDimens.ALPHA_SUBTLE
    }
    private val mutedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiDimens.ALPHA_SUBTLE
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemePack.surfaceSunken(tokens)
    }
    private val badgeActiveTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SettingsUiLogic.accentOnSunken(accent, tokens)
        textAlign = Paint.Align.CENTER
        textSize = context.dpF(8.5f)
        typeface = Typeface.MONOSPACE
    }
    private val badgeMutedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiDimens.ALPHA_SECONDARY
        textAlign = Paint.Align.CENTER
        textSize = context.dpF(8.5f)
        typeface = Typeface.MONOSPACE
    }

    private val outerR = context.dpF(UiDimens.RADIUS_MD)
    private val panelR = context.dpF(7)
    private val tileR = context.dpF(2.5f)
    private val badgeR = context.dpF(4.5f)
    private val pad = context.dpF(7)
    private val dp2 = context.dpF(2)
    private val dp3 = context.dpF(3)
    private val dp4 = context.dpF(4)
    private val dp5 = context.dpF(5)
    private val dp6 = context.dpF(6)
    private val dp12 = context.dpF(12)
    private val dp14 = context.dpF(14)
    private val dp24 = context.dpF(24)
    private val dp32 = context.dpF(32)
    private val dp140 = context.dpF(140)
    private val dp192 = context.dpF(192)
    private val borderInset = hairlinePaint.strokeWidth * 0.5f
    private val badgeTextOffsetY =
        -(badgeActiveTextPaint.descent() + badgeActiveTextPaint.ascent()) * 0.5f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun updateState(next: TopologyDiagramState) {
        if (state != next) {
            state = next
            invalidate()
        }
    }

    private fun drawDisplayBadge(
        canvas: Canvas,
        right: Float,
        top: Float,
        text: String,
        highlight: Boolean,
    ) {
        val badgeW = dp24
        val badgeH = dp12
        val left = right - badgeW
        rect.set(left, top, right, top + badgeH)
        canvas.drawRoundRect(rect, badgeR, badgeR, badgeBgPaint)
        canvas.drawRoundRect(rect, badgeR, badgeR, hairlinePaint)
        canvas.drawText(
            text,
            left + badgeW * 0.5f,
            top + badgeH * 0.5f + badgeTextOffsetY,
            if (highlight) badgeActiveTextPaint else badgeMutedTextPaint,
        )
    }

    private fun drawMiniGridRow(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val count = 4
        val gap = dp4
        val spanW = (right - left).coerceAtLeast(dp12)
        val cellW = ((spanW - gap * (count - 1)) / count).coerceAtLeast(dp4)
        val cellH = (bottom - top).coerceAtLeast(dp4)
        for (i in 0 until count) {
            val l = left + i * (cellW + gap)
            rect.set(l, top, l + cellW, top + cellH)
            canvas.drawRoundRect(rect, tileR, tileR, if (i == 1) accentPaint else elevatedPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, outerR, outerR, bgPaint)

        if (state.isDual && state.panels.size >= 2) {
            val topPanel = state.panels[0]
            val bottomPanel = state.panels[1]
            val panelW = minOf(w - pad * 2f, dp192)
            val left = (w - panelW) * 0.5f
            val right = left + panelW
            val hingeGap = dp5
            val panelH = ((h - pad * 2f - hingeGap) * 0.5f).coerceAtLeast(dp14)

            // Top Companion / Launch panel.
            val topT = pad
            val topB = topT + panelH
            rect.set(left, topT, right, topB)
            canvas.drawRoundRect(rect, panelR, panelR, panelPaint)
            // Hero artwork silhouette + title/sub bars.
            val artSize = (panelH - dp6).coerceAtLeast(dp6)
            rect.set(left + dp5, topT + dp3, left + dp5 + artSize * 1.35f, topT + dp3 + artSize)
            canvas.drawRoundRect(rect, tileR, tileR, elevatedPaint)
            val textL = left + dp5 + artSize * 1.35f + dp5
            val textR = (right - dp32).coerceAtLeast(textL + dp6)
            rect.set(textL, topT + dp6, textL + (textR - textL) * 0.85f, topT + dp6 + dp3)
            canvas.drawRoundRect(rect, dp2, dp2, elevatedPaint)
            rect.set(textL, topT + dp12, textL + (textR - textL) * 0.55f, topT + dp12 + dp2)
            canvas.drawRoundRect(rect, dp2, dp2, mutedBarPaint)
            drawDisplayBadge(canvas, right - dp4, topT + dp4, topPanel.badgeText, topPanel.isLaunchTarget)
            rect.set(left, topT, right, topB)
            canvas.drawRoundRect(
                rect,
                panelR,
                panelR,
                if (topPanel.isInteractive) focusStrokePaint else hairlinePaint,
            )

            // Centered clamshell hinge bar.
            val hingeW = dp32
            val hingeL = (w - hingeW) * 0.5f
            val hingeMidY = topB + hingeGap * 0.5f
            rect.set(hingeL, hingeMidY - dp2 * 0.5f, hingeL + hingeW, hingeMidY + dp2 * 0.5f)
            canvas.drawRoundRect(rect, dp2, dp2, hingePaint)

            // Bottom Interactive deck panel.
            val botT = topB + hingeGap
            val botB = botT + panelH
            rect.set(left, botT, right, botB)
            canvas.drawRoundRect(rect, panelR, panelR, panelPaint)
            drawMiniGridRow(
                canvas = canvas,
                left = left + dp6,
                top = botT + dp5,
                right = right - dp32,
                bottom = botB - dp5,
            )
            drawDisplayBadge(canvas, right - dp4, botT + dp4, bottomPanel.badgeText, bottomPanel.isInteractive)
            rect.set(left, botT, right, botB)
            canvas.drawRoundRect(
                rect,
                panelR,
                panelR,
                if (bottomPanel.isInteractive) focusStrokePaint else hairlinePaint,
            )
        } else {
            // Single-screen 16:9 TOP_STRIP schematic.
            val panel = state.panels.firstOrNull()
            val frameH = (h - pad * 2f).coerceAtLeast(dp24)
            val frameW = minOf(w - pad * 2f, maxOf(dp140, frameH * 1.95f))
            val left = (w - frameW) * 0.5f
            val right = left + frameW
            val top = pad
            val bottom = top + frameH

            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, panelR, panelR, panelPaint)

            // Top companion strip inside single-screen frame.
            val stripH = frameH * 0.30f
            val stripBottom = top + stripH
            val thumbH = (stripH - dp4).coerceAtLeast(dp4)
            rect.set(left + dp6, top + dp2, left + dp6 + thumbH * 1.4f, top + dp2 + thumbH)
            canvas.drawRoundRect(rect, tileR, tileR, elevatedPaint)
            val barL = left + dp6 + thumbH * 1.4f + dp5
            val barR = (right - dp32).coerceAtLeast(barL + dp6)
            rect.set(barL, top + dp5, barL + (barR - barL) * 0.72f, top + dp5 + dp3)
            canvas.drawRoundRect(rect, dp2, dp2, elevatedPaint)
            if (panel != null) {
                drawDisplayBadge(canvas, right - dp4, top + dp3, panel.badgeText, highlight = true)
            }
            canvas.drawLine(left + dp4, stripBottom, right - dp4, stripBottom, hairlinePaint)

            // Bottom interactive grid strip.
            drawMiniGridRow(
                canvas = canvas,
                left = left + dp6,
                top = stripBottom + dp5,
                right = right - dp6,
                bottom = bottom - dp6,
            )

            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, panelR, panelR, focusStrokePaint)
        }

        rect.set(borderInset, borderInset, w - borderInset, h - borderInset)
        canvas.drawRoundRect(rect, outerR, outerR, hairlinePaint)
    }
}
