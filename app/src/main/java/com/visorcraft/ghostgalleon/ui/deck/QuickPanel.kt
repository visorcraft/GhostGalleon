package com.visorcraft.ghostgalleon.ui.deck

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
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
        ICON_CONTROLLER,
        ICON_CLOSE,
    }

    const val DEFAULT_COLUMNS = 4
    const val NARROW_COLUMNS = 3
    const val NARROW_WIDTH_THRESHOLD_DP = 360
    const val COMPACT_TILE_HEIGHT_DP = 74
    const val EXPANDED_TILE_HEIGHT_DP = 64

    fun columnsForCardWidthDp(cardWidthDp: Int): Int =
        if (cardWidthDp < NARROW_WIDTH_THRESHOLD_DP) NARROW_COLUMNS else DEFAULT_COLUMNS

    /**
     * Balanced tile height across compact (<= 2 rows) and expanded
     * `quickPanelBrowse` (3+ rows) grids so tall panels stay scannable on
     * short 540dp handheld screens.
     */
    fun tileHeightDp(tileCount: Int, columns: Int): Int {
        val cols = columns.coerceAtLeast(1)
        val rows = if (tileCount <= 0) 1 else (tileCount + cols - 1) / cols
        return if (rows <= 2) COMPACT_TILE_HEIGHT_DP else EXPANDED_TILE_HEIGHT_DP
    }

    @DrawableRes
    fun iconResFor(glyph: TileGlyph): Int? = when (glyph) {
        TileGlyph.ICON_SETTINGS -> R.drawable.ic_settings
        TileGlyph.ICON_DISPLAY -> R.drawable.ic_swap
        TileGlyph.ICON_CONTINUE -> R.drawable.ic_chevron_right
        TileGlyph.ICON_RANDOM -> R.drawable.ic_swap
        TileGlyph.ICON_RAIL -> R.drawable.ic_folder
        TileGlyph.ICON_CONTROLLER -> R.drawable.ic_status_bolt
        TileGlyph.ICON_CLOSE -> R.drawable.ic_close
        TileGlyph.ICON_THEME_SWATCH, TileGlyph.NONE -> null
    }

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
        val labelView: TextView,
        val iconView: ImageView?,
        val swatchView: View?,
        val cell: Cell,
    )

    private var columns = QuickPanelNav.DEFAULT_COLUMNS
    private var selection = 0
    private val tileViews = mutableListOf<BoundTile>()
    private lateinit var cells: List<Cell>
    private var accent: Int = 0
    private var themeTokens: ThemeTokens = ThemePack.GHOST
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null

    val view: View by lazy { build() }

    fun handleAction(action: Action): Boolean {
        ensureBuilt()
        if (cells.isEmpty()) {
            if (action == Action.BACK || action == Action.OPEN_QUICK_PANEL) onClose()
            return true
        }
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT, Action.NAV_UP, Action.NAV_DOWN,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                selection = QuickPanelNav.move(selection, cells.size, columns, action)
                paint()
            }
            Action.CONFIRM -> cells.getOrNull(selection)?.onClick?.invoke()
            Action.BACK, Action.OPEN_QUICK_PANEL -> onClose()
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
            ) { onClose() },
        )
        cells = core

        val root = OverlayPrimitives.modalBackdrop(context, onClose)
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

        val themeSubtitle = context.getString(
            R.string.format_theme,
            context.themeName(themeTokens.id, themeTokens.displayName),
        )
        sheet.addView(
            OverlayPrimitives.modalHeader(
                context = context,
                titleRes = R.string.deck_quick_panel,
                subtitle = themeSubtitle,
            ),
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
                var swatchView: View? = null
                val tile = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    contentDescription = cell.label
                    isClickable = true
                    setPadding(
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(UiDimens.SPACE_SM),
                    )
                    setOnClickListener {
                        selection = index
                        paint()
                        cell.onClick()
                    }
                }
                val iconRes = QuickPanelNav.iconResFor(cell.glyph)
                if (cell.glyph == QuickPanelNav.TileGlyph.ICON_THEME_SWATCH) {
                    val dot = View(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    swatchView = dot
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
                tileViews.add(BoundTile(tile, labelTv, iconView, swatchView, cell))
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
        scrollHost = scroll
        sheet.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        sheet.addView(
            OverlayPrimitives.modalHintFooter(context, R.string.deck_hint_folder_close),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        paint()
        root.addView(sheet, FrameLayout.LayoutParams(
            cardWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        return root
    }

    private fun paint() {
        if (tileViews.isEmpty()) return
        val sel = GridWrapNav.clampIndex(selection, tileViews.size)
        selection = sel
        val tileRadius = themeTokens.cardRadiusDp
        val swatchBorder = QuickPanelNav.swatchBorderColor(accent)
        tileViews.forEachIndexed { index, bound ->
            val selected = index == sel
            val role = bound.cell.role
            val (_, textColor) = QuickPanelNav.tileColors(role, selected, accent, themeTokens)
            bound.container.background = when {
                selected && role == QuickPanelNav.TileRole.PRIMARY ->
                    TileBackgrounds.accentPill(activity, accent, cornerRadiusDp = tileRadius)
                selected ->
                    TileBackgrounds.selected(activity, accent)
                role == QuickPanelNav.TileRole.CLOSE ->
                    TileBackgrounds.sunkenField(activity, cornerRadiusDp = tileRadius)
                else ->
                    TileBackgrounds.chipIdle(activity, cornerRadiusDp = tileRadius)
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
            bound.swatchView?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
                setStroke(activity.strokePx(1), swatchBorder)
            }
            bound.container.isSelected = selected
            bound.container.alpha = 1f
            val scale = if (selected) 1.04f else 1f
            bound.container.scaleX = scale
            bound.container.scaleY = scale
        }
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
        val builtins = ThemePack.BUILTINS
        val current = ThemePack.resolve(app.settings).id
        val i = builtins.indexOfFirst { it.id == current }.let { if (it < 0) 0 else it }
        val next = builtins[(i + 1) % builtins.size]
        app.updateSettings(ThemePack.applyToSettings(app.settings, next))
        activity.toast(
            R.string.format_theme,
            activity.themeName(next.id, next.displayName),
        )
    }
}
