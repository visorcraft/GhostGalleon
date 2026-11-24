package com.visorcraft.ghostgalleon.ui.deck

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.audio.SoundPackId
import com.visorcraft.ghostgalleon.library.HiddenRoms
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.LaunchReason
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.ControllerLabActivity
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.browseModeName
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.themeName
import com.visorcraft.ghostgalleon.ui.toast

/**
 * Zero-allocation programmatic vector glyph drawable for [QuickPanel] tiles
 * (Wi-Fi, Bluetooth, Random shuffle, Controller gamepad, Audio speaker + mute slash).
 * Preallocates all [Paint], [RectF], and [Path] fields; rebuilds paths only on
 * bounds changes and mutates tint / muted slash strictly in place.
 */
internal class QuickGlyphDrawable(
    val kind: Kind,
    initialMuted: Boolean = false,
) : Drawable() {

    enum class Kind {
        WIFI,
        BLUETOOTH,
        RANDOM,
        CONTROLLER,
        AUDIO,
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = UiDimens.ALPHA_PRIMARY
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = UiDimens.ALPHA_PRIMARY
    }
    private val mutedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = UiDimens.ALPHA_MUTED
    }
    private val arcRect = RectF()
    private val primaryPath = Path()
    private val secondaryPath = Path()
    private var tintColor: Int = UiDimens.ALPHA_PRIMARY
    private var drawableAlpha: Int = 0xFF
    private var muted: Boolean = initialMuted
    private var dotCx: Float = 0f
    private var dotCy: Float = 0f
    private var dotRadius: Float = 0f
    private var rightDot1X: Float = 0f
    private var rightDot1Y: Float = 0f
    private var rightDot2X: Float = 0f
    private var rightDot2Y: Float = 0f

    fun setTintColor(color: Int) {
        if (tintColor == color) return
        tintColor = color
        syncPaintColors()
        invalidateSelf()
    }

    fun setMuted(isMuted: Boolean) {
        if (muted == isMuted) return
        muted = isMuted
        invalidateSelf()
    }

    private fun syncPaintColors() {
        val baseAlpha = ((tintColor ushr 24) and 0xFF) * drawableAlpha / 255
        val resolved = ThemePack.withAlpha(tintColor, baseAlpha)
        strokePaint.color = resolved
        fillPaint.color = resolved
        mutedFillPaint.color = ThemePack.withAlpha(tintColor, (baseAlpha * 0.55f).toInt())
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val s = minOf(w, h)
        val ox = bounds.left + (w - s) * 0.5f
        val oy = bounds.top + (h - s) * 0.5f
        strokePaint.strokeWidth = (s * 0.105f).coerceAtLeast(1.5f)
        primaryPath.reset()
        secondaryPath.reset()
        when (kind) {
            Kind.WIFI -> {
                val cx = ox + s * 0.50f
                val cy = oy + s * 0.78f
                val r1 = s * 0.24f
                val r2 = s * 0.44f
                val r3 = s * 0.64f
                arcRect.set(cx - r1, cy - r1, cx + r1, cy + r1)
                primaryPath.addArc(arcRect, -135f, 90f)
                arcRect.set(cx - r2, cy - r2, cx + r2, cy + r2)
                primaryPath.addArc(arcRect, -135f, 90f)
                arcRect.set(cx - r3, cy - r3, cx + r3, cy + r3)
                primaryPath.addArc(arcRect, -135f, 90f)
                dotCx = cx
                dotCy = cy
                dotRadius = (s * 0.085f).coerceAtLeast(1.5f)
            }
            Kind.BLUETOOTH -> {
                primaryPath.moveTo(ox + s * 0.30f, oy + s * 0.32f)
                primaryPath.lineTo(ox + s * 0.70f, oy + s * 0.68f)
                primaryPath.lineTo(ox + s * 0.50f, oy + s * 0.88f)
                primaryPath.lineTo(ox + s * 0.50f, oy + s * 0.12f)
                primaryPath.lineTo(ox + s * 0.70f, oy + s * 0.32f)
                primaryPath.lineTo(ox + s * 0.30f, oy + s * 0.68f)
            }
            Kind.RANDOM -> {
                // Crossing shuffle tracks
                primaryPath.moveTo(ox + s * 0.14f, oy + s * 0.28f)
                primaryPath.lineTo(ox + s * 0.36f, oy + s * 0.28f)
                primaryPath.lineTo(ox + s * 0.64f, oy + s * 0.72f)
                primaryPath.lineTo(ox + s * 0.84f, oy + s * 0.72f)
                primaryPath.moveTo(ox + s * 0.14f, oy + s * 0.72f)
                primaryPath.lineTo(ox + s * 0.36f, oy + s * 0.72f)
                primaryPath.lineTo(ox + s * 0.64f, oy + s * 0.28f)
                primaryPath.lineTo(ox + s * 0.84f, oy + s * 0.28f)
                // Arrowheads
                primaryPath.moveTo(ox + s * 0.74f, oy + s * 0.17f)
                primaryPath.lineTo(ox + s * 0.86f, oy + s * 0.28f)
                primaryPath.lineTo(ox + s * 0.74f, oy + s * 0.39f)
                primaryPath.moveTo(ox + s * 0.74f, oy + s * 0.61f)
                primaryPath.lineTo(ox + s * 0.86f, oy + s * 0.72f)
                primaryPath.lineTo(ox + s * 0.74f, oy + s * 0.83f)
            }
            Kind.CONTROLLER -> {
                // Rounded handheld gamepad silhouette + left D-pad cross + right face buttons
                arcRect.set(ox + s * 0.08f, oy + s * 0.24f, ox + s * 0.92f, oy + s * 0.76f)
                primaryPath.addRoundRect(arcRect, s * 0.24f, s * 0.24f, Path.Direction.CW)
                // Left D-pad cross
                primaryPath.moveTo(ox + s * 0.23f, oy + s * 0.50f)
                primaryPath.lineTo(ox + s * 0.41f, oy + s * 0.50f)
                primaryPath.moveTo(ox + s * 0.32f, oy + s * 0.41f)
                primaryPath.lineTo(ox + s * 0.32f, oy + s * 0.59f)
                // Right face button dots
                dotRadius = (s * 0.055f).coerceAtLeast(1.25f)
                rightDot1X = ox + s * 0.64f
                rightDot1Y = oy + s * 0.55f
                rightDot2X = ox + s * 0.76f
                rightDot2Y = oy + s * 0.45f
            }
            Kind.AUDIO -> {
                // Speaker trapezoid cone (filled)
                primaryPath.moveTo(ox + s * 0.14f, oy + s * 0.38f)
                primaryPath.lineTo(ox + s * 0.32f, oy + s * 0.38f)
                primaryPath.lineTo(ox + s * 0.52f, oy + s * 0.20f)
                primaryPath.lineTo(ox + s * 0.52f, oy + s * 0.80f)
                primaryPath.lineTo(ox + s * 0.32f, oy + s * 0.62f)
                primaryPath.lineTo(ox + s * 0.14f, oy + s * 0.62f)
                primaryPath.close()
                // Unmuted sound wave arcs
                val waveCx = ox + s * 0.54f
                val waveCy = oy + s * 0.50f
                val w1 = s * 0.16f
                val w2 = s * 0.30f
                arcRect.set(waveCx - w1, waveCy - w1, waveCx + w1, waveCy + w1)
                secondaryPath.addArc(arcRect, -45f, 90f)
                arcRect.set(waveCx - w2, waveCy - w2, waveCx + w2, waveCy + w2)
                secondaryPath.addArc(arcRect, -45f, 90f)
                // Muted diagonal slash endpoints stored in rightDot1/2
                rightDot1X = ox + s * 0.16f
                rightDot1Y = oy + s * 0.16f
                rightDot2X = ox + s * 0.84f
                rightDot2Y = oy + s * 0.84f
            }
        }
    }

    override fun draw(canvas: Canvas) {
        when (kind) {
            Kind.WIFI -> {
                canvas.drawPath(primaryPath, strokePaint)
                canvas.drawCircle(dotCx, dotCy, dotRadius, fillPaint)
            }
            Kind.BLUETOOTH, Kind.RANDOM -> {
                canvas.drawPath(primaryPath, strokePaint)
            }
            Kind.CONTROLLER -> {
                canvas.drawPath(primaryPath, strokePaint)
                canvas.drawCircle(rightDot1X, rightDot1Y, dotRadius, fillPaint)
                canvas.drawCircle(rightDot2X, rightDot2Y, dotRadius, fillPaint)
            }
            Kind.AUDIO -> {
                canvas.drawPath(primaryPath, if (muted) mutedFillPaint else fillPaint)
                if (muted) {
                    canvas.drawLine(rightDot1X, rightDot1Y, rightDot2X, rightDot2Y, strokePaint)
                } else {
                    canvas.drawPath(secondaryPath, strokePaint)
                }
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        syncPaintColors()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        mutedFillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Pure, host-tested navigation, iconography, section grouping, grid rhythm,
 * and contrast style helpers for [QuickPanel].
 */
internal object QuickPanelNav {

    enum class TileRole { STANDARD, PRIMARY, CLOSE }

    enum class TileGlyph {
        NONE,
        ICON_WIFI,
        ICON_BLUETOOTH,
        ICON_SETTINGS,
        ICON_DISPLAY,
        ICON_CONTINUE,
        ICON_RANDOM,
        ICON_RAIL,
        ICON_THEME_SWATCH,
        ICON_AUDIO,
        ICON_CONTROLLER,
        ICON_CLOSE,
    }

    data class ContinueAvailability(
        val available: Boolean,
        val idleAlpha: Float,
    )

    data class TileSectionGrouping(
        val quickActionRange: IntRange,
        val libraryRange: IntRange?,
        val closeIndex: Int,
    ) {
        val libraryStartIndex: Int?
            get() = libraryRange?.first
    }

    const val DEFAULT_COLUMNS = 4
    const val NARROW_COLUMNS = 3
    const val NARROW_WIDTH_THRESHOLD_DP = 360
    const val COMPACT_TILE_HEIGHT_DP = 74
    const val EXPANDED_TILE_HEIGHT_DP = 64
    const val FOCUS_SCALE = 1.04f
    const val FOCUS_ELEVATION_DP = 4
    const val FOCUS_ANIM_MS = 90L
    const val MUTED_AUDIO_IDLE_ALPHA: Float = 0.62f
    const val UNAVAILABLE_CONTINUE_IDLE_ALPHA: Float = 0.55f
    internal const val STANDARD_RIPPLE_COLOR: Int = 0x28FFFFFF
    private const val PRIMARY_LIGHT_RING_FALLBACK: Int = 0xFF707A8A.toInt()

    fun focusScale(selected: Boolean): Float = if (selected) FOCUS_SCALE else 1f

    fun focusElevationDp(selected: Boolean): Int = if (selected) FOCUS_ELEVATION_DP else 0

    /** Adaptive ripple color so light accents (OLED) keep a visible dark press wash. */
    fun tileRippleColor(role: TileRole, accent: Int): Int =
        if (role == TileRole.PRIMARY) {
            val onAccent = accessibleOnAccent(accent)
            (0x33 shl 24) or (onAccent and 0x00FFFFFF)
        } else {
            STANDARD_RIPPLE_COLOR
        }

    /**
     * WCAG 1.4.11 (`>= 3.0:1`) focus border color for a focused QuickPanel tile.
     * Focused `PRIMARY` tiles are filled with [accent], so they use an OLED-safe
     * active pill focus ring; Standard and Close tiles lift [accent] against `cardFill`.
     * Host-tested.
     */
    fun focusStrokeColor(role: TileRole, accent: Int, tokens: ThemeTokens): Int =
        if (role == TileRole.PRIMARY) {
            if (ThemePack.contrastRatio(UiDimens.ALPHA_PRIMARY, accent) >= 2.0) {
                UiDimens.ALPHA_PRIMARY
            } else {
                PRIMARY_LIGHT_RING_FALLBACK
            }
        } else {
            OverlayPrimitives.accessibleFocusStroke(accent, ThemePack.cardFill(tokens))
        }

    /** True when the Audio tile should render muted slash + dimmed idle styling. Host-tested. */
    fun audioTileMuted(soundEnabled: Boolean): Boolean = !soundEnabled

    /** Continue tile availability and idle alpha from the most recent launch key. Host-tested. */
    fun continueAvailability(continueKey: String?): ContinueAvailability {
        val available = !continueKey.isNullOrBlank()
        return ContinueAvailability(
            available = available,
            idleAlpha = if (available) 1f else UNAVAILABLE_CONTINUE_IDLE_ALPHA,
        )
    }

    /** Effective tile container alpha across focus, muted-audio, and empty-Continue states. */
    fun tileAlpha(
        selected: Boolean,
        mutedAudio: Boolean = false,
        unavailableContinue: Boolean = false,
    ): Float = when {
        selected -> 1f
        unavailableContinue -> UNAVAILABLE_CONTINUE_IDLE_ALPHA
        mutedAudio -> MUTED_AUDIO_IDLE_ALPHA
        else -> 1f
    }

    /** Next built-in theme token in rotation order (defaults to index 1 on unknown id). */
    fun nextBuiltinTheme(
        currentId: String,
        builtins: List<ThemeTokens> = ThemePack.BUILTINS,
    ): ThemeTokens {
        if (builtins.isEmpty()) return ThemePack.GHOST
        val idx = builtins.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        return builtins[(idx + 1) % builtins.size]
    }

    fun columnsForCardWidthDp(cardWidthDp: Int): Int =
        if (cardWidthDp < NARROW_WIDTH_THRESHOLD_DP) NARROW_COLUMNS else DEFAULT_COLUMNS

    /**
     * Balanced tile height across compact (<= 2 rows) and expanded
     * `quickPanelBrowse` (3+ rows) grids so tall panels stay scannable on
     * short 540dp handheld screens.
     */
    fun tileHeightDp(tileCount: Int, columns: Int): Int {
        val rows = GridWrapNav.rowCount(tileCount, columns).coerceAtLeast(1)
        return if (rows <= 2) COMPACT_TILE_HEIGHT_DP else EXPANDED_TILE_HEIGHT_DP
    }

    @DrawableRes
    fun iconResFor(glyph: TileGlyph): Int? = when (glyph) {
        TileGlyph.ICON_SETTINGS -> R.drawable.ic_settings
        TileGlyph.ICON_DISPLAY -> R.drawable.ic_nav_display
        TileGlyph.ICON_CONTINUE -> R.drawable.ic_chevron_right
        TileGlyph.ICON_RAIL -> R.drawable.ic_folder
        TileGlyph.ICON_CLOSE -> R.drawable.ic_close
        TileGlyph.ICON_WIFI,
        TileGlyph.ICON_BLUETOOTH,
        TileGlyph.ICON_RANDOM,
        TileGlyph.ICON_CONTROLLER,
        TileGlyph.ICON_AUDIO,
        TileGlyph.ICON_THEME_SWATCH,
        TileGlyph.NONE,
        -> null
    }

    /** Programmatic [QuickGlyphDrawable.Kind] for tiles backed by crisp custom vector glyphs. */
    fun programmaticGlyphKind(glyph: TileGlyph): QuickGlyphDrawable.Kind? = when (glyph) {
        TileGlyph.ICON_WIFI -> QuickGlyphDrawable.Kind.WIFI
        TileGlyph.ICON_BLUETOOTH -> QuickGlyphDrawable.Kind.BLUETOOTH
        TileGlyph.ICON_RANDOM -> QuickGlyphDrawable.Kind.RANDOM
        TileGlyph.ICON_CONTROLLER -> QuickGlyphDrawable.Kind.CONTROLLER
        TileGlyph.ICON_AUDIO -> QuickGlyphDrawable.Kind.AUDIO
        else -> null
    }

    /** True when [glyph] resolves to a real drawable, programmatic glyph, or live swatch. */
    fun hasVisualGlyph(glyph: TileGlyph): Boolean =
        iconResFor(glyph) != null ||
            programmaticGlyphKind(glyph) != null ||
            glyph == TileGlyph.ICON_THEME_SWATCH

    /** Canonical ordered glyphs for core QuickPanel cells (used by unit tests). */
    fun coreCellGlyphs(includeBrowse: Boolean = false): List<TileGlyph> = buildList {
        add(TileGlyph.ICON_WIFI)
        add(TileGlyph.ICON_BLUETOOTH)
        add(TileGlyph.ICON_DISPLAY)
        add(TileGlyph.ICON_SETTINGS)
        add(TileGlyph.ICON_CONTINUE)
        add(TileGlyph.ICON_THEME_SWATCH)
        add(TileGlyph.ICON_AUDIO)
        add(TileGlyph.ICON_CONTROLLER)
        if (includeBrowse) {
            add(TileGlyph.ICON_RANDOM)
            add(TileGlyph.ICON_RAIL)
        }
        add(TileGlyph.ICON_CLOSE)
    }

    /** Localized label resource for the Quick Panel instant sound toggle tile. */
    fun audioToggleLabelRes(soundEnabled: Boolean): Int =
        if (soundEnabled) R.string.quick_audio_on else R.string.quick_audio_off

    /** Crisp translucent rim for the Theme tile's live accent swatch dot. */
    fun swatchBorderColor(accent: Int): Int {
        val onAccent = accessibleOnAccent(accent)
        return (0x66 shl 24) or (onAccent and 0x00FFFFFF)
    }

    /**
     * Split QuickPanel tiles into Quick Actions (System + Continue + Theme/Audio/Controller)
     * and an optional Library section (browse rails + bottom-right Close) when opt-in
     * `quickPanelBrowse` has at least one rail. Host-tested.
     */
    fun sectionGrouping(quickActionCount: Int, browseRailCount: Int): TileSectionGrouping {
        val safeQuick = quickActionCount.coerceAtLeast(0)
        val safeBrowse = browseRailCount.coerceAtLeast(0)
        val closeIdx = safeQuick + safeBrowse
        return if (safeBrowse > 0) {
            TileSectionGrouping(
                quickActionRange = 0 until safeQuick,
                libraryRange = safeQuick..closeIdx,
                closeIndex = closeIdx,
            )
        } else {
            TileSectionGrouping(
                quickActionRange = 0..closeIdx,
                libraryRange = null,
                closeIndex = closeIdx,
            )
        }
    }

    /** Visual rows of tile indices respecting an optional [libraryStartIndex] section break. */
    fun visualRows(
        count: Int,
        columns: Int,
        libraryStartIndex: Int? = null,
    ): List<List<Int>> {
        if (count <= 0) return emptyList()
        val cols = columns.coerceAtLeast(1)
        if (libraryStartIndex == null || libraryStartIndex <= 0 || libraryStartIndex >= count) {
            return (0 until count).chunked(cols)
        }
        return buildList {
            addAll((0 until libraryStartIndex).chunked(cols))
            addAll((libraryStartIndex until count).chunked(cols))
        }
    }

    fun move(selection: Int, count: Int, columns: Int, action: Action): Int =
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT, Action.NAV_UP, Action.NAV_DOWN ->
                GridWrapNav.moveAction(selection, count, columns, action)
            Action.PAGE_PREV -> GridWrapNav.pageMove(selection, count, columns, deltaPages = -1)
            Action.PAGE_NEXT -> GridWrapNav.pageMove(selection, count, columns, deltaPages = 1)
            else -> GridWrapNav.clampIndex(selection, count)
        }

    /**
     * Section-aware 2D D-pad and L1/R1 navigation preserving full reachability
     * across both 3-column and 4-column QuickPanel grids when a Library section
     * divider splits the layout. Host-tested.
     */
    fun moveInSections(
        selection: Int,
        count: Int,
        columns: Int,
        action: Action,
        libraryStartIndex: Int?,
    ): Int {
        if (count <= 0) return 0
        if (libraryStartIndex == null || libraryStartIndex <= 0 || libraryStartIndex >= count) {
            return move(selection, count, columns, action)
        }
        val cur = GridWrapNav.clampIndex(selection, count)
        val rows = visualRows(count, columns, libraryStartIndex)
        if (rows.isEmpty()) return cur
        var rowIdx = 0
        var colIdx = 0
        outer@ for (r in rows.indices) {
            val idx = rows[r].indexOf(cur)
            if (idx >= 0) {
                rowIdx = r
                colIdx = idx
                break@outer
            }
        }
        return when (action) {
            Action.NAV_LEFT -> (cur - 1 + count) % count
            Action.NAV_RIGHT -> (cur + 1) % count
            Action.NAV_UP -> {
                val targetRow = rows[(rowIdx - 1 + rows.size) % rows.size]
                targetRow[colIdx.coerceAtMost(targetRow.lastIndex)]
            }
            Action.NAV_DOWN -> {
                val targetRow = rows[(rowIdx + 1) % rows.size]
                targetRow[colIdx.coerceAtMost(targetRow.lastIndex)]
            }
            Action.PAGE_PREV -> {
                val targetRow = rows[(rowIdx - 1).coerceAtLeast(0)]
                targetRow[colIdx.coerceAtMost(targetRow.lastIndex)]
            }
            Action.PAGE_NEXT -> {
                val targetRow = rows[(rowIdx + 1).coerceAtMost(rows.lastIndex)]
                targetRow[colIdx.coerceAtMost(targetRow.lastIndex)]
            }
            else -> cur
        }
    }

    /**
     * Accessible foreground color on a filled [accent] pill. Prefers
     * [ThemePack.onFillTextColor] when it meets WCAG AA (`>= 4.5:1`), else
     * picks whichever of pure white or black maximizes contrast so every
     * built-in and custom theme stays legible.
     */
    fun accessibleOnAccent(accent: Int): Int {
        val preferred = ThemePack.onFillTextColor(accent)
        if (ThemePack.contrastRatio(preferred, accent) >= ThemePack.MIN_TEXT_CONTRAST) {
            return preferred
        }
        val white = UiDimens.ALPHA_PRIMARY
        val black = 0xFF000000.toInt()
        return if (ThemePack.contrastRatio(black, accent) >= ThemePack.contrastRatio(white, accent)) {
            black
        } else {
            white
        }
    }

    /** Resolved (backgroundFill, textColor) for unit-testing contrast across all states. */
    fun tileColors(
        role: TileRole,
        selected: Boolean,
        accent: Int,
        tokens: ThemeTokens,
    ): Pair<Int, Int> = when {
        selected && role == TileRole.PRIMARY ->
            accent to accessibleOnAccent(accent)
        selected ->
            ThemePack.cardFill(tokens) to UiDimens.ALPHA_PRIMARY
        role == TileRole.PRIMARY ->
            tokens.chipIdle to ThemePack.accentTextColor(accent, tokens.chipIdle)
        role == TileRole.CLOSE ->
            ThemePack.surfaceSunken(tokens) to UiDimens.ALPHA_SECONDARY
        else ->
            tokens.chipIdle to UiDimens.ALPHA_PRIMARY
    }

    /**
     * Tile indices to repaint when moving focus from [previousIndex] to
     * [nextIndex], or all tiles when [forceAll] is true (initial build or live
     * theme cycle). Emits at most 2 indices (`<= 2`) on incremental NAV ticks.
     */
    fun dirtyTileIndices(
        previousIndex: Int,
        nextIndex: Int,
        count: Int,
        forceAll: Boolean = false,
    ): List<Int> {
        if (count <= 0) return emptyList()
        val next = GridWrapNav.clampIndex(nextIndex, count)
        if (forceAll || previousIndex < 0) return (0 until count).toList()
        val prev = GridWrapNav.clampIndex(previousIndex, count)
        return if (prev == next) emptyList() else listOf(prev, next)
    }
}

/**
 * Full-screen dim (~80%) overlay with a responsive, scrollable chip grid
 * (Wi‑Fi, Bluetooth, Display, Settings, Continue, optional browse rails,
 * Theme, Controller lab, Close). Browse shortcuts opt in via BrowseChrome.
 * D-pad + A navigate / activate; B / Select / Close dismisses.
 */
class QuickPanel(
    private val activity: AppCompatActivity,
    private val state: DeckState,
    private val roms: List<RomEntry>,
    private val onClose: () -> Unit,
) {
    private data class Cell(
        val label: String,
        val subline: String? = null,
        val role: QuickPanelNav.TileRole = QuickPanelNav.TileRole.STANDARD,
        val glyph: QuickPanelNav.TileGlyph = QuickPanelNav.TileGlyph.NONE,
        val onClick: () -> Unit,
    )

    private data class BoundTile(
        val container: LinearLayout,
        val bgDrawable: GradientDrawable,
        val labelView: TextView,
        val sublineView: TextView?,
        val iconView: ImageView?,
        val quickGlyph: QuickGlyphDrawable?,
        val swatchDrawable: GradientDrawable?,
        val cell: Cell,
    )

    private var columns = QuickPanelNav.DEFAULT_COLUMNS
    private var selection = 0
    private var paintedSelection = -1
    private var libraryStartIndex: Int? = null
    private var audioMuted: Boolean = false
    private var continueAvailable: Boolean = false
    private val tileViews = mutableListOf<BoundTile>()
    private lateinit var cells: List<Cell>
    private var accent: Int = 0
    private var themeTokens: ThemeTokens = ThemePack.GHOST
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null
    private var sheetContainer: LinearLayout? = null
    private var headerSubtitleView: TextView? = null
    private var libraryHeaderView: TextView? = null
    private var libraryDividerView: View? = null
    private var topScrollCue: View? = null
    private var bottomScrollCue: View? = null
    private var keycapFooter: LinearLayout? = null

    val view: View by lazy { build() }

    private fun appOrNull(): GhostGalleonApp? =
        activity.application as? GhostGalleonApp

    private fun closeWithAudio() {
        appOrNull()?.playSfx(SoundCue.MODAL_CLOSE)
        onClose()
    }

    private fun activateCell(cell: Cell) {
        OverlayAudioLogic.quickPanelActionCue(cell.role, cell.glyph)?.let {
            appOrNull()?.playSfx(it)
        }
        cell.onClick()
    }

    fun handleAction(action: Action): Boolean {
        ensureBuilt()
        if (cells.isEmpty()) {
            if (action == Action.BACK || action == Action.OPEN_QUICK_PANEL) closeWithAudio()
            return true
        }
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT, Action.NAV_UP, Action.NAV_DOWN,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                val next = QuickPanelNav.moveInSections(
                    selection = selection,
                    count = cells.size,
                    columns = columns,
                    action = action,
                    libraryStartIndex = libraryStartIndex,
                )
                if (next != selection) {
                    appOrNull()?.playSfx(
                        cue = SoundCue.NAV_TICK,
                        pan = OverlayAudioLogic.quickPanelTilePan(next, columns),
                    )
                }
                selection = next
                paint()
            }
            Action.CONFIRM -> cells.getOrNull(selection)?.let(::activateCell)
            Action.BACK, Action.OPEN_QUICK_PANEL -> closeWithAudio()
            else -> {}
        }
        return true
    }

    private fun ensureBuilt() {
        // Force lazy view construction so cells/rowViews exist.
        view
    }

    private fun build(): View {
        val context = activity
        val app = activity.application as GhostGalleonApp
        themeTokens = ThemePack.resolve(app.settings)
        accent = app.settings.accentColor
        audioMuted = QuickPanelNav.audioTileMuted(app.settings.soundEnabled)

        val continueKey = LibraryBrowse.continueKey(
            app.settings.lastLaunchedMs.keys.toList(),
            app.settings.lastLaunchedMs,
        )
        continueAvailable = QuickPanelNav.continueAvailability(continueKey).available
        val continueTitle = continueKey?.let { key ->
            app.settings.customNames[key]?.takeIf { it.isNotBlank() } ?: run {
                val romId = SlotKey.romId(key)
                if (romId != null) {
                    (app.romById[romId] ?: roms.firstOrNull { it.id == romId })?.name
                } else {
                    app.appLibrary().byPackage(app.settings)[key]?.label
                }
            }
        }

        // Quick Actions (System + Continue + Theme/Audio/Controller) always stay
        // cleanly grouped at the top; opt-in Library browse rails follow under a
        // dedicated section band with Close pinned at bottom-right.
        val chrome = app.settings.browseChrome
        val quickActions = mutableListOf(
            Cell(
                label = context.getString(R.string.label_wifi),
                glyph = QuickPanelNav.TileGlyph.ICON_WIFI,
            ) {
                openSystem(Settings.ACTION_WIFI_SETTINGS)
            },
            Cell(
                label = context.getString(R.string.label_bluetooth),
                glyph = QuickPanelNav.TileGlyph.ICON_BLUETOOTH,
            ) {
                openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)
            },
            Cell(
                label = context.getString(R.string.quick_display),
                glyph = QuickPanelNav.TileGlyph.ICON_DISPLAY,
            ) {
                openSystem(Settings.ACTION_DISPLAY_SETTINGS)
            },
            Cell(
                label = context.getString(R.string.quick_settings),
                glyph = QuickPanelNav.TileGlyph.ICON_SETTINGS,
            ) {
                launchOnOtherDisplay(
                    activity, state, Intent(activity, SettingsActivity::class.java))
                onClose()
            },
            Cell(
                label = context.getString(R.string.label_continue),
                subline = continueTitle,
                role = QuickPanelNav.TileRole.PRIMARY,
                glyph = QuickPanelNav.TileGlyph.ICON_CONTINUE,
            ) {
                launchContinue(app)
                onClose()
            },
            Cell(
                label = context.getString(R.string.quick_theme),
                glyph = QuickPanelNav.TileGlyph.ICON_THEME_SWATCH,
            ) { cycleTheme(app) },
            Cell(
                label = context.getString(QuickPanelNav.audioToggleLabelRes(app.settings.soundEnabled)),
                glyph = QuickPanelNav.TileGlyph.ICON_AUDIO,
            ) { toggleAudio(app) },
            Cell(
                label = context.getString(R.string.quick_controller),
                glyph = QuickPanelNav.TileGlyph.ICON_CONTROLLER,
            ) {
                activity.startActivity(Intent(activity, ControllerLabActivity::class.java))
                onClose()
            },
        )
        val browseRails = mutableListOf<Cell>()
        if (chrome.quickPanelBrowse) {
            if (chrome.randomChip) {
                browseRails.add(
                    Cell(
                        label = context.getString(R.string.label_random),
                        glyph = QuickPanelNav.TileGlyph.ICON_RANDOM,
                    ) {
                        launchRandom(app)
                        onClose()
                    },
                )
            }
            if (chrome.topRail) {
                browseRails.add(
                    Cell(
                        label = context.getString(R.string.label_top),
                        glyph = QuickPanelNav.TileGlyph.ICON_RAIL,
                    ) {
                        openTopPlayed()
                        onClose()
                    },
                )
            }
            // Fav / Games / Installed / Week / Month / A–Z / New — each rail
            // still gated by its own BrowseChrome flag via quickPanelRailShortcuts.
            chrome.quickPanelRailShortcuts().forEach { mode ->
                val label = context.browseModeName(mode)
                browseRails.add(
                    Cell(
                        label = label,
                        glyph = QuickPanelNav.TileGlyph.ICON_RAIL,
                    ) {
                        openGameRail(mode, label)
                        onClose()
                    },
                )
            }
        }
        val closeCell = Cell(
            label = context.getString(R.string.action_close),
            role = QuickPanelNav.TileRole.CLOSE,
            glyph = QuickPanelNav.TileGlyph.ICON_CLOSE,
        ) { closeWithAudio() }

        val grouping = QuickPanelNav.sectionGrouping(quickActions.size, browseRails.size)
        libraryStartIndex = grouping.libraryStartIndex
        cells = buildList(quickActions.size + browseRails.size + 1) {
            addAll(quickActions)
            addAll(browseRails)
            add(closeCell)
        }

        val root = OverlayPrimitives.modalBackdrop(context) { closeWithAudio() }
        OverlayPrimitives.bindModalPaneTitle(root, context.getString(R.string.deck_quick_panel))
        val cardWidth = OverlayPrimitives.responsiveCardWidth(
            context = context,
            maxDp = 560,
            widthFrac = 0.90f,
            minMarginDp = UiDimens.SPACE_LG,
        )
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        columns = QuickPanelNav.columnsForCardWidthDp((cardWidth / density).toInt())

        val sheet = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = UiDimens.SPACE_LG,
            padVerticalDp = UiDimens.SPACE_LG,
        )
        sheetContainer = sheet

        val themeSubtitle = context.getString(
            R.string.format_theme,
            context.themeName(themeTokens.id, themeTokens.displayName),
        )
        val header = OverlayPrimitives.modalHeader(
            context = context,
            titleRes = R.string.deck_quick_panel,
            subtitle = themeSubtitle,
        )
        headerSubtitleView = (header.getChildAt(1) as? TextView)?.also {
            OverlayPrimitives.markPoliteLiveRegion(it)
        }
        sheet.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val gridPad = context.dp(UiDimens.SPACE_XS)
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(gridPad, gridPad, gridPad, gridPad)
        }

        val tileHeightPx = context.dp(QuickPanelNav.tileHeightDp(cells.size, columns))
        val locale = context.resources.configuration.locales[0]

        fun buildTileView(index: Int, cell: Cell): LinearLayout {
            var iconView: ImageView? = null
            var quickGlyph: QuickGlyphDrawable? = null
            var swatchDrawable: GradientDrawable? = null
            val tileRadius = themeTokens.cardRadiusDp
            val tileBg = GradientDrawable().apply {
                cornerRadius = context.dpF(tileRadius)
            }
            val tileDesc = if (!cell.subline.isNullOrBlank()) {
                context.getString(R.string.format_dot_pair, cell.label, cell.subline)
            } else {
                cell.label
            }
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = tileDesc
                outlineProvider = ViewOutlineProvider.BACKGROUND
                background = OverlayPrimitives.roundedRipple(
                    context = context,
                    content = tileBg,
                    cornerRadiusDp = tileRadius,
                    rippleColor = QuickPanelNav.tileRippleColor(cell.role, accent),
                )
                isClickable = true
                setPadding(
                    context.dp(UiDimens.SPACE_SM),
                    context.dp(UiDimens.SPACE_XS + 2),
                    context.dp(UiDimens.SPACE_SM),
                    context.dp(UiDimens.SPACE_XS + 2),
                )
                setOnClickListener {
                    selection = index
                    paint(animateFocus = false, scrollToFocused = false)
                    activateCell(cell)
                }
            }
            val programmaticKind = QuickPanelNav.programmaticGlyphKind(cell.glyph)
            val iconRes = QuickPanelNav.iconResFor(cell.glyph)
            if (cell.glyph == QuickPanelNav.TileGlyph.ICON_THEME_SWATCH) {
                val dotBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                }
                val dot = View(context).apply {
                    background = dotBg
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                swatchDrawable = dotBg
                tile.addView(
                    dot,
                    LinearLayout.LayoutParams(context.dp(15), context.dp(15)).apply {
                        bottomMargin = context.dp(5)
                    },
                )
            } else if (programmaticKind != null) {
                val drawable = QuickGlyphDrawable(
                    kind = programmaticKind,
                    initialMuted = cell.glyph == QuickPanelNav.TileGlyph.ICON_AUDIO && audioMuted,
                )
                quickGlyph = drawable
                val iv = ImageView(context).apply {
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                iconView = iv
                tile.addView(
                    iv,
                    LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
                        bottomMargin = context.dp(4)
                    },
                )
            } else if (iconRes != null) {
                val iv = ImageView(context).apply {
                    setImageResource(iconRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                iconView = iv
                tile.addView(
                    iv,
                    LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
                        bottomMargin = context.dp(4)
                    },
                )
            }
            val hasSubline = !cell.subline.isNullOrBlank()
            val labelTv = TextView(context).apply {
                text = cell.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                maxLines = if (hasSubline) 1 else 2
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            tile.addView(
                labelTv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            val subTv = if (hasSubline) {
                TextView(context).apply {
                    text = cell.subline
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.also { tv ->
                    tile.addView(
                        tv,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = context.dp(1) },
                    )
                }
            } else {
                null
            }
            tileViews.add(
                BoundTile(
                    container = tile,
                    bgDrawable = tileBg,
                    labelView = labelTv,
                    sublineView = subTv,
                    iconView = iconView,
                    quickGlyph = quickGlyph,
                    swatchDrawable = swatchDrawable,
                    cell = cell,
                ),
            )
            return tile
        }

        fun appendSectionRows(
            indices: IntRange,
            pinTrailingCloseRight: Boolean,
            firstRowHasTopMargin: Boolean,
        ) {
            val rowGroups = indices.toList().chunked(columns)
            rowGroups.forEachIndexed { rowIdx, rowIndices ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    clipChildren = false
                    clipToPadding = false
                }
                val isLastRow = rowIdx == rowGroups.lastIndex
                val missingCols = columns - rowIndices.size
                val pinClose = pinTrailingCloseRight &&
                    isLastRow &&
                    missingCols > 0 &&
                    rowIndices.isNotEmpty() &&
                    cells[rowIndices.last()].role == QuickPanelNav.TileRole.CLOSE
                if (pinClose) {
                    var visualCol = 0
                    for (pos in 0 until rowIndices.lastIndex) {
                        val idx = rowIndices[pos]
                        row.addView(
                            buildTileView(idx, cells[idx]),
                            LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                                if (visualCol > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                            },
                        )
                        visualCol++
                    }
                    repeat(missingCols) {
                        row.addView(
                            View(context),
                            LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                                if (visualCol > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                            },
                        )
                        visualCol++
                    }
                    val closeIdx = rowIndices.last()
                    row.addView(
                        buildTileView(closeIdx, cells[closeIdx]),
                        LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                            if (visualCol > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                        },
                    )
                } else {
                    rowIndices.forEachIndexed { colIndex, idx ->
                        row.addView(
                            buildTileView(idx, cells[idx]),
                            LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                                if (colIndex > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                            },
                        )
                    }
                    repeat(missingCols) {
                        row.addView(
                            View(context),
                            LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                                marginStart = context.dp(UiDimens.SPACE_SM)
                            },
                        )
                    }
                }
                grid.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (rowIdx > 0 || firstRowHasTopMargin) {
                            topMargin = context.dp(UiDimens.SPACE_SM)
                        }
                    },
                )
            }
        }

        appendSectionRows(
            indices = grouping.quickActionRange,
            pinTrailingCloseRight = grouping.libraryRange == null,
            firstRowHasTopMargin = false,
        )
        grouping.libraryRange?.let { libRange ->
            val divider = View(context).apply {
                setBackgroundColor(ThemePack.hairlineDivider(themeTokens))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            libraryDividerView = divider
            grid.addView(
                divider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.strokePx(1),
                ).apply {
                    topMargin = context.dp(10)
                    bottomMargin = context.dp(6)
                },
            )
            val sectionHeader = TextView(context).apply {
                text = context.getString(R.string.settings_page_library).uppercase(locale)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(ThemePack.accentTextColor(accent, ThemePack.cardFill(themeTokens)))
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.12f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                OverlayPrimitives.markHeading(this)
            }
            libraryHeaderView = sectionHeader
            grid.addView(
                sectionHeader,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = context.dp(UiDimens.SPACE_XS)
                },
            )
            appendSectionRows(
                indices = libRange,
                pinTrailingCloseRight = true,
                firstRowHasTopMargin = false,
            )
        }

        val cardSurface = ThemePack.cardFill(themeTokens)
        val scrollCueColor = OverlayPrimitives.scrollCueTint(accent, cardSurface)
        val topCue = OverlayPrimitives.scrollCueBar(
            context = context,
            color = scrollCueColor,
            isTop = true,
            surfaceColor = cardSurface,
        )
        val bottomCue = OverlayPrimitives.scrollCueBar(
            context = context,
            color = scrollCueColor,
            isTop = false,
            surfaceColor = cardSurface,
        )
        topScrollCue = topCue
        bottomScrollCue = bottomCue

        val scroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = context,
                heightFrac = 0.56f,
                minDp = 160,
            ),
        ).apply {
            addView(
                grid,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val footer = OverlayPrimitives.modalKeycapFooter(
            context = context,
            accent = accent,
            tokens = themeTokens,
            cues = listOf(
                OverlayPrimitives.FooterCue(OverlayPrimitives.KEYCAP_CONFIRM, R.string.action_open),
                OverlayPrimitives.FooterCue(
                    keycap = OverlayPrimitives.KEYCAP_PAGE,
                    scrollOnly = true,
                    keycapSymbol = "\u2195",
                ),
                OverlayPrimitives.FooterCue(OverlayPrimitives.KEYCAP_CLOSE, R.string.action_close),
            ),
            compactWidthDp = (cardWidth / density).toInt(),
            isScrollable = cells.size > columns * 2,
        )
        keycapFooter = footer
        OverlayPrimitives.bindScrollCues(scroll, topCue, bottomCue, keycapFooter = footer)
        scrollHost = scroll

        sheet.addView(
            OverlayPrimitives.scrollViewportWithCues(
                context = context,
                scroll = scroll,
                topCue = topCue,
                bottomCue = bottomCue,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        sheet.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        paint(animateFocus = false, scrollToFocused = false, forceAll = true)
        root.addView(sheet, FrameLayout.LayoutParams(
            cardWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        return root
    }

    private fun paintTile(
        bound: BoundTile,
        selected: Boolean,
        animateFocus: Boolean,
        tileRadiusDp: Int,
        tileRadiusPx: Float,
        hairlineColor: Int,
        stroke1Px: Int,
        stroke3Px: Int,
        stroke4Px: Int,
        swatchBorder: Int,
        refreshThemeChrome: Boolean,
    ) {
        val role = bound.cell.role
        val (fillColor, textColor) = QuickPanelNav.tileColors(role, selected, accent, themeTokens)
        bound.bgDrawable.cornerRadius = tileRadiusPx
        bound.bgDrawable.setColor(fillColor)
        when {
            selected && role == QuickPanelNav.TileRole.PRIMARY ->
                bound.bgDrawable.setStroke(
                    stroke3Px,
                    QuickPanelNav.focusStrokeColor(role, accent, themeTokens),
                )
            selected ->
                bound.bgDrawable.setStroke(
                    stroke4Px,
                    QuickPanelNav.focusStrokeColor(role, accent, themeTokens),
                )
            else ->
                bound.bgDrawable.setStroke(stroke1Px, hairlineColor)
        }
        if (refreshThemeChrome) {
            bound.container.background = OverlayPrimitives.roundedRipple(
                context = activity,
                content = bound.bgDrawable,
                cornerRadiusDp = tileRadiusDp,
                rippleColor = QuickPanelNav.tileRippleColor(role, accent),
            )
            bound.swatchDrawable?.apply {
                setColor(accent)
                setStroke(stroke1Px, swatchBorder)
            }
        }
        bound.labelView.setTextColor(textColor)
        bound.labelView.setTypeface(
            null,
            if (selected || role == QuickPanelNav.TileRole.PRIMARY) {
                Typeface.BOLD
            } else {
                Typeface.NORMAL
            },
        )
        bound.sublineView?.setTextColor(ThemePack.withAlpha(textColor, 0xD4))
        val isMutedAudio = bound.cell.glyph == QuickPanelNav.TileGlyph.ICON_AUDIO && audioMuted
        val isUnavailableContinue = bound.cell.glyph == QuickPanelNav.TileGlyph.ICON_CONTINUE && !continueAvailable
        if (bound.quickGlyph != null) {
            bound.quickGlyph.setTintColor(textColor)
            bound.quickGlyph.setMuted(isMutedAudio)
        } else {
            bound.iconView?.imageTintList = ColorStateList.valueOf(textColor)
        }
        bound.container.isSelected = selected
        bound.container.alpha = QuickPanelNav.tileAlpha(
            selected = selected,
            mutedAudio = isMutedAudio,
            unavailableContinue = isUnavailableContinue,
        )
        bound.container.elevation = activity.dpF(QuickPanelNav.focusElevationDp(selected))
        OverlayPrimitives.applyFocusScale(
            view = bound.container,
            selected = selected,
            focusedScale = QuickPanelNav.FOCUS_SCALE,
            animate = animateFocus,
            durationMs = QuickPanelNav.FOCUS_ANIM_MS,
        )
    }

    private fun paint(
        animateFocus: Boolean = true,
        scrollToFocused: Boolean = true,
        forceAll: Boolean = false,
        refreshThemeChrome: Boolean = false,
    ) {
        if (tileViews.isEmpty()) return
        val sel = GridWrapNav.clampIndex(selection, tileViews.size)
        selection = sel
        val dirty = QuickPanelNav.dirtyTileIndices(paintedSelection, sel, tileViews.size, forceAll)
        if (dirty.isNotEmpty()) {
            val tileRadiusDp = themeTokens.cardRadiusDp
            val tileRadiusPx = activity.dpF(tileRadiusDp)
            val hairlineColor = ThemePack.hairlineDivider(themeTokens)
            val stroke1Px = activity.strokePx(1)
            val stroke3Px = activity.strokePx(3)
            val stroke4Px = activity.strokePx(4)
            val swatchBorder = QuickPanelNav.swatchBorderColor(accent)
            val updateSwatch = forceAll || refreshThemeChrome
            for (index in dirty) {
                val bound = tileViews[index]
                paintTile(
                    bound = bound,
                    selected = index == sel,
                    animateFocus = animateFocus,
                    tileRadiusDp = tileRadiusDp,
                    tileRadiusPx = tileRadiusPx,
                    hairlineColor = hairlineColor,
                    stroke1Px = stroke1Px,
                    stroke3Px = stroke3Px,
                    stroke4Px = stroke4Px,
                    swatchBorder = swatchBorder,
                    refreshThemeChrome = refreshThemeChrome,
                )
                if (updateSwatch && !refreshThemeChrome) {
                    bound.swatchDrawable?.apply {
                        setColor(accent)
                        setStroke(stroke1Px, swatchBorder)
                    }
                }
            }
        }
        paintedSelection = sel
        if (!scrollToFocused) return
        val focusedRow = tileViews.getOrNull(sel)?.container?.parent as? View ?: return
        scrollHost?.smoothScrollToCenter(focusedRow)
    }

    private fun openSystem(action: String) {
        runCatching {
            activity.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            activity.toast(R.string.deck_unavailable)
        }
        onClose()
    }

    private fun launchContinue(app: GhostGalleonApp) {
        val keys = app.settings.lastLaunchedMs.keys.toList()
        val cont = LibraryBrowse.continueKey(keys, app.settings.lastLaunchedMs)
        if (cont == null) {
            app.playSfx(SoundCue.ERROR_DENY)
            activity.toast(R.string.browse_nothing_to_continue)
            return
        }
        val idx = app.settings.gridSlots.indexOf(cont)
        if (idx >= 0) state.selectSlot(idx, cont) else state.select(cont)
        launchSlotKey(activity, state, roms, cont, reason = LaunchReason.CONTINUE)
    }

    /** Pick a random curated app or visible ROM and launch it immediately. */
    private fun launchRandom(app: GhostGalleonApp) {
        val pool = buildList {
            addAll(app.settings.gridSlots.filterNotNull())
            addAll(app.settings.dockSlots.filterNotNull())
            addAll(
                HiddenRoms.listed(roms, app.settings.hiddenRomIds)
                    .map { SlotKey.rom(it.id) },
            )
        }.distinct()
        val key = LibraryBrowse.pickRandom(pool) { size ->
            java.util.concurrent.ThreadLocalRandom.current().nextInt(size)
        }
        if (key == null) {
            app.playSfx(SoundCue.ERROR_DENY)
            activity.toast(R.string.browse_library_empty)
            return
        }
        val idx = app.settings.gridSlots.indexOf(key)
        if (idx >= 0) state.selectSlot(idx, key) else state.select(key, force = true)
        launchSlotKey(activity, state, roms, key)
    }

    /**
     * Jump into Game Mode on a named browse rail so Fav / Games / Installed /
     * Top / Week / Month / A–Z / New are reachable from Grid Mode via Select.
     */
    private fun openGameRail(
        mode: LibraryBrowse.Mode,
        toast: String,
        selectKey: String? = null,
    ) {
        state.setMode(com.visorcraft.ghostgalleon.state.UIMode.GAME)
        state.setLibraryBrowse(LibraryBrowse.railQuery(mode), force = true)
        if (selectKey != null) state.select(selectKey, force = true)
        activity.toast(toast)
    }

    /**
     * Most Played rail + select the top title when playtime exists.
     */
    private fun openTopPlayed() {
        val live = (activity.application as GhostGalleonApp).settings
        val top = LibraryBrowse.topPlayedKey(live.playtimeMs)
        openGameRail(
            LibraryBrowse.Mode.MOST_PLAYED,
            activity.getString(
                if (top != null) R.string.browse_top_played else R.string.browse_no_playtime_yet,
            ),
            selectKey = top,
        )
    }

    private fun cycleTheme(app: GhostGalleonApp) {
        val current = ThemePack.resolve(app.settings).id
        val next = QuickPanelNav.nextBuiltinTheme(current)
        app.updateSettings(ThemePack.applyToSettings(app.settings, next))
        if (SoundPackId.fromId(app.settings.soundPack) == SoundPackId.AUTO) {
            app.soundEngine.syncWithSettings()
            app.soundEngine.previewPack(SoundPackId.AUTO)
        } else {
            app.playSfx(SoundCue.CONFIRM)
        }
        themeTokens = next
        accent = next.accentColor
        sheetContainer?.background = OverlayPrimitives.modalCardBackground(activity)
        val label = activity.getString(
            R.string.format_theme,
            activity.themeName(next.id, next.displayName),
        )
        headerSubtitleView?.text = label
        val cardSurface = ThemePack.cardFill(themeTokens)
        libraryHeaderView?.setTextColor(ThemePack.accentTextColor(accent, cardSurface))
        libraryDividerView?.setBackgroundColor(ThemePack.hairlineDivider(themeTokens))
        OverlayPrimitives.updateScrollCueBar(topScrollCue, cardSurface, accent, isTop = true)
        OverlayPrimitives.updateScrollCueBar(bottomScrollCue, cardSurface, accent, isTop = false)
        OverlayPrimitives.restyleKeycapFooter(keycapFooter, accent, themeTokens)
        paint(
            animateFocus = false,
            scrollToFocused = false,
            forceAll = true,
            refreshThemeChrome = true,
        )
        activity.toast(label)
    }

    private fun toggleAudio(app: GhostGalleonApp) {
        val nextEnabled = !app.settings.soundEnabled
        audioMuted = QuickPanelNav.audioTileMuted(nextEnabled)
        app.updateSettings(app.settings.copy(soundEnabled = nextEnabled), notify = false)
        val label = activity.getString(QuickPanelNav.audioToggleLabelRes(nextEnabled))
        val audioIdx = tileViews.indexOfFirst { it.cell.glyph == QuickPanelNav.TileGlyph.ICON_AUDIO }
        if (audioIdx >= 0) {
            val bound = tileViews[audioIdx]
            bound.labelView.text = label
            bound.container.contentDescription = label
            bound.quickGlyph?.setMuted(audioMuted)
            bound.container.alpha = QuickPanelNav.tileAlpha(
                selected = audioIdx == selection,
                mutedAudio = audioMuted,
            )
        }
        if (nextEnabled) {
            app.soundEngine.audition(SoundCue.CONFIRM)
        }
        activity.toast(label)
    }
}
