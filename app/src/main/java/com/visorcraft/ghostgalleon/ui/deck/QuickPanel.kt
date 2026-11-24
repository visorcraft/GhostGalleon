package com.visorcraft.ghostgalleon.ui.deck

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
 * Pure, host-tested navigation, iconography, grid rhythm, and contrast style
 * helpers for [QuickPanel].
 */
internal object QuickPanelNav {

    enum class TileRole { STANDARD, PRIMARY, CLOSE }

    enum class TileGlyph {
        NONE,
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

    const val DEFAULT_COLUMNS = 4
    const val NARROW_COLUMNS = 3
    const val NARROW_WIDTH_THRESHOLD_DP = 360
    const val COMPACT_TILE_HEIGHT_DP = 74
    const val EXPANDED_TILE_HEIGHT_DP = 64
    const val FOCUS_SCALE = 1.04f
    const val FOCUS_ELEVATION_DP = 4
    const val FOCUS_ANIM_MS = 90L
    internal const val STANDARD_RIPPLE_COLOR: Int = 0x28FFFFFF

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
        TileGlyph.ICON_DISPLAY -> R.drawable.ic_swap
        TileGlyph.ICON_CONTINUE -> R.drawable.ic_chevron_right
        TileGlyph.ICON_RANDOM -> R.drawable.ic_swap
        TileGlyph.ICON_RAIL -> R.drawable.ic_folder
        TileGlyph.ICON_AUDIO -> R.drawable.ic_nav_audio
        TileGlyph.ICON_CONTROLLER -> R.drawable.ic_status_bolt
        TileGlyph.ICON_CLOSE -> R.drawable.ic_close
        TileGlyph.ICON_THEME_SWATCH, TileGlyph.NONE -> null
    }

    /** Localized label resource for the Quick Panel instant sound toggle tile. */
    fun audioToggleLabelRes(soundEnabled: Boolean): Int =
        if (soundEnabled) R.string.quick_audio_on else R.string.quick_audio_off

    /** Crisp translucent rim for the Theme tile's live accent swatch dot. */
    fun swatchBorderColor(accent: Int): Int {
        val onAccent = accessibleOnAccent(accent)
        return (0x66 shl 24) or (onAccent and 0x00FFFFFF)
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
        val role: QuickPanelNav.TileRole = QuickPanelNav.TileRole.STANDARD,
        val glyph: QuickPanelNav.TileGlyph = QuickPanelNav.TileGlyph.NONE,
        val onClick: () -> Unit,
    )

    private data class BoundTile(
        val container: LinearLayout,
        val bgDrawable: GradientDrawable,
        val labelView: TextView,
        val iconView: ImageView?,
        val swatchDrawable: GradientDrawable?,
        val cell: Cell,
    )

    private var columns = QuickPanelNav.DEFAULT_COLUMNS
    private var selection = 0
    private var paintedSelection = -1
    private val tileViews = mutableListOf<BoundTile>()
    private lateinit var cells: List<Cell>
    private var accent: Int = 0
    private var themeTokens: ThemeTokens = ThemePack.GHOST
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null
    private var sheetContainer: LinearLayout? = null
    private var headerSubtitleView: TextView? = null
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
                val next = QuickPanelNav.move(selection, cells.size, columns, action)
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

        // Minimal Quick Panel: system tiles + Continue + Theme + lab + Close.
        // Browse rails (Random/Top/Fav/Games/Installed) opt in via Settings.
        val chrome = app.settings.browseChrome
        val core = mutableListOf(
            Cell(context.getString(R.string.label_wifi)) {
                openSystem(Settings.ACTION_WIFI_SETTINGS)
            },
            Cell(context.getString(R.string.label_bluetooth)) {
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
                role = QuickPanelNav.TileRole.PRIMARY,
                glyph = QuickPanelNav.TileGlyph.ICON_CONTINUE,
            ) {
                launchContinue(app)
                onClose()
            },
        )
        if (chrome.quickPanelBrowse) {
            if (chrome.randomChip) {
                core.add(
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
                core.add(
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
                core.add(
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
        core.add(
            Cell(
                label = context.getString(R.string.quick_theme),
                glyph = QuickPanelNav.TileGlyph.ICON_THEME_SWATCH,
            ) { cycleTheme(app) },
        )
        core.add(
            Cell(
                label = context.getString(QuickPanelNav.audioToggleLabelRes(app.settings.soundEnabled)),
                glyph = QuickPanelNav.TileGlyph.ICON_AUDIO,
            ) { toggleAudio(app) },
        )
        core.add(
            Cell(
                label = context.getString(R.string.quick_controller),
                glyph = QuickPanelNav.TileGlyph.ICON_CONTROLLER,
            ) {
                activity.startActivity(Intent(activity, ControllerLabActivity::class.java))
                onClose()
            },
        )
        core.add(
            Cell(
                label = context.getString(R.string.action_close),
                role = QuickPanelNav.TileRole.CLOSE,
                glyph = QuickPanelNav.TileGlyph.ICON_CLOSE,
            ) { closeWithAudio() },
        )
        cells = core

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
        cells.chunked(columns).forEachIndexed { rowIndex, rowCells ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }
            rowCells.forEachIndexed { colIndex, cell ->
                val index = rowIndex * columns + colIndex
                var iconView: ImageView? = null
                var swatchDrawable: GradientDrawable? = null
                val tileRadius = themeTokens.cardRadiusDp
                val tileBg = GradientDrawable().apply {
                    cornerRadius = context.dpF(tileRadius)
                }
                val tile = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    contentDescription = cell.label
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
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(UiDimens.SPACE_SM),
                    )
                    setOnClickListener {
                        selection = index
                        paint(animateFocus = false, scrollToFocused = false)
                        activateCell(cell)
                    }
                }
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
                val labelTv = TextView(context).apply {
                    text = cell.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    maxLines = 2
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
                tileViews.add(BoundTile(tile, tileBg, labelTv, iconView, swatchDrawable, cell))
                row.addView(tile, LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                    if (colIndex > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                })
            }
            // Keep partial last row aligned to the same column width as rows above.
            repeat(columns - rowCells.size) {
                row.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, tileHeightPx, 1f).apply {
                        marginStart = context.dp(UiDimens.SPACE_SM)
                    },
                )
            }
            grid.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (rowIndex > 0) topMargin = context.dp(UiDimens.SPACE_SM)
            })
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
                bound.bgDrawable.setStroke(0, Color.TRANSPARENT)
            selected ->
                bound.bgDrawable.setStroke(stroke4Px, accent)
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
        bound.iconView?.imageTintList = ColorStateList.valueOf(textColor)
        bound.container.isSelected = selected
        bound.container.alpha = 1f
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
        app.updateSettings(app.settings.copy(soundEnabled = nextEnabled), notify = false)
        val label = activity.getString(QuickPanelNav.audioToggleLabelRes(nextEnabled))
        tileViews.firstOrNull { it.cell.glyph == QuickPanelNav.TileGlyph.ICON_AUDIO }?.let { bound ->
            bound.labelView.text = label
            bound.container.contentDescription = label
        }
        if (nextEnabled) {
            app.soundEngine.audition(SoundCue.CONFIRM)
        }
        activity.toast(label)
    }
}
