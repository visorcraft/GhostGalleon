package com.visorcraft.ghostgalleon.ui.deck

import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.visorcraft.ghostgalleon.ui.themeName

/**
 * Pure, host-tested navigation and contrast style helpers for [QuickPanel].
 */
internal object QuickPanelNav {

    enum class TileRole { STANDARD, PRIMARY, CLOSE }

    const val DEFAULT_COLUMNS = 4
    const val NARROW_COLUMNS = 3
    const val NARROW_WIDTH_THRESHOLD_DP = 360

    fun columnsForCardWidthDp(cardWidthDp: Int): Int =
        if (cardWidthDp < NARROW_WIDTH_THRESHOLD_DP) NARROW_COLUMNS else DEFAULT_COLUMNS

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
        val onClick: () -> Unit,
    )

    private var columns = QuickPanelNav.DEFAULT_COLUMNS
    private var selection = 0
    private val rowViews = mutableListOf<TextView>()
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
            Cell(context.getString(R.string.quick_display)) {
                openSystem(Settings.ACTION_DISPLAY_SETTINGS)
            },
            Cell(context.getString(R.string.quick_settings)) {
                launchOnOtherDisplay(
                    activity, state, Intent(activity, SettingsActivity::class.java))
                onClose()
            },
            Cell(
                label = context.getString(R.string.label_continue),
                role = QuickPanelNav.TileRole.PRIMARY,
            ) {
                launchContinue(app)
                onClose()
            },
        )
        if (chrome.quickPanelBrowse) {
            if (chrome.randomChip) {
                core.add(Cell(context.getString(R.string.label_random)) {
                    launchRandom(app)
                    onClose()
                })
            }
            if (chrome.topRail) {
                core.add(Cell(context.getString(R.string.label_top)) {
                    openTopPlayed()
                    onClose()
                })
            }
            // Fav / Games / Installed / Week / Month / A–Z / New — each rail
            // still gated by its own BrowseChrome flag via quickPanelRailShortcuts.
            chrome.quickPanelRailShortcuts().forEach { mode ->
                val label = context.browseModeName(mode)
                core.add(Cell(label) {
                    openGameRail(mode, label)
                    onClose()
                })
            }
        }
        core.add(Cell(context.getString(R.string.quick_theme)) { cycleTheme(app) })
        core.add(Cell(context.getString(R.string.quick_controller)) {
            activity.startActivity(Intent(activity, ControllerLabActivity::class.java))
            onClose()
        })
        core.add(
            Cell(
                label = context.getString(R.string.action_close),
                role = QuickPanelNav.TileRole.CLOSE,
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

        cells.chunked(columns).forEachIndexed { rowIndex, rowCells ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }
            rowCells.forEachIndexed { colIndex, cell ->
                val index = rowIndex * columns + colIndex
                val btn = TextView(context).apply {
                    text = cell.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(10),
                        context.dp(UiDimens.SPACE_SM),
                        context.dp(10),
                    )
                    setOnClickListener {
                        selection = index
                        paint()
                        cell.onClick()
                    }
                }
                rowViews.add(btn)
                row.addView(btn, LinearLayout.LayoutParams(0, context.dp(68), 1f).apply {
                    if (colIndex > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                })
            }
            // Keep partial last row aligned to the same column width as rows above.
            repeat(columns - rowCells.size) {
                row.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, context.dp(68), 1f).apply {
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
        if (rowViews.isEmpty()) return
        val sel = GridWrapNav.clampIndex(selection, rowViews.size)
        selection = sel
        val tileRadius = themeTokens.cardRadiusDp
        rowViews.forEachIndexed { index, tv ->
            val selected = index == sel
            val role = cells.getOrNull(index)?.role ?: QuickPanelNav.TileRole.STANDARD
            val (_, textColor) = QuickPanelNav.tileColors(role, selected, accent, themeTokens)
            tv.background = when {
                selected && role == QuickPanelNav.TileRole.PRIMARY ->
                    TileBackgrounds.accentPill(activity, accent, cornerRadiusDp = tileRadius)
                selected ->
                    TileBackgrounds.selected(activity, accent)
                role == QuickPanelNav.TileRole.CLOSE ->
                    TileBackgrounds.sunkenField(activity, cornerRadiusDp = tileRadius)
                else ->
                    TileBackgrounds.chipIdle(activity, cornerRadiusDp = tileRadius)
            }
            tv.setTextColor(textColor)
            tv.setTypeface(
                null,
                if (selected || role == QuickPanelNav.TileRole.PRIMARY) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                },
            )
            tv.alpha = 1f
            val scale = if (selected) 1.04f else 1f
            tv.scaleX = scale
            tv.scaleY = scale
        }
        val focusedRow = rowViews.getOrNull(sel)?.parent as? View ?: return
        val scroller = scrollHost ?: return
        scroller.post {
            val targetY = (focusedRow.top - (scroller.height - focusedRow.height) / 2)
                .coerceAtLeast(0)
            scroller.smoothScrollTo(0, targetY)
        }
    }

    private fun openSystem(action: String) {
        runCatching {
            activity.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(activity, R.string.deck_unavailable, Toast.LENGTH_SHORT).show()
        }
        onClose()
    }

    private fun launchContinue(app: GhostGalleonApp) {
        val keys = app.settings.lastLaunchedMs.keys.toList()
        val cont = LibraryBrowse.continueKey(keys, app.settings.lastLaunchedMs)
        if (cont == null) {
            Toast.makeText(
                activity,
                R.string.browse_nothing_to_continue,
                Toast.LENGTH_SHORT,
            ).show()
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
            Toast.makeText(activity, R.string.browse_library_empty, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(
            activity,
            activity.getString(R.string.format_theme, activity.themeName(next.id, next.displayName)),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
