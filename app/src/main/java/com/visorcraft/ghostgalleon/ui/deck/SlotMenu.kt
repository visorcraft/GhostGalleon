package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.strokePx

/**
 * Pure, host-tested navigation and destructive-item helpers for [SlotMenu].
 */
internal object SlotMenuNav {

    const val PAGE_STEP = 5

    /** Indices into [rows] that are actionable (never section headers). */
    fun actionRowIndices(rows: List<SlotMenu.Row>): List<Int> =
        rows.mapIndexedNotNull { index, row -> if (row is SlotMenu.Row.Item) index else null }

    /** Move selection within `0 until actionCount`, supporting wrap & L1/R1 page jumps. */
    fun move(selection: Int, actionCount: Int, action: Action): Int =
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN ->
                GridWrapNav.moveAction(selection, actionCount, columns = 1, action = action)
            Action.PAGE_PREV ->
                GridWrapNav.pageMove(selection, actionCount, pageSize = PAGE_STEP, deltaPages = -1)
            Action.PAGE_NEXT ->
                GridWrapNav.pageMove(selection, actionCount, pageSize = PAGE_STEP, deltaPages = 1)
            else -> GridWrapNav.clampIndex(selection, actionCount)
        }

    fun isDestructive(item: SlotMenu.Row.Item): Boolean =
        item.destructive ||
            item.choice == SlotMenu.Choice.REMOVE ||
            item.choice == SlotMenu.Choice.REMOVE_FROM_GRID ||
            item.choice == SlotMenu.Choice.HIDE
}

/**
 * Centered modal for a filled grid/dock tile, Game Mode entry, or picker
 * confirm action. Uses [OverlayPrimitives] for flicker-free responsive framing
 * and scrolling, optional item title banner, left-aligned sectioned rows,
 * destructive red emphasis, and D-pad + L1/R1 page navigation.
 */
class SlotMenu(
    private val context: Context,
    private val accentColor: Int,
    private val rows: List<Row>,
    private val title: CharSequence? = null,
    private val onChoice: (Choice) -> Unit,
) {
    enum class Choice(val labelRes: Int) {
        MOVE(R.string.action_move),
        PIN_TO_DOCK(R.string.action_pin_to_dock),
        UNPIN_FROM_DOCK(R.string.action_unpin_from_dock),
        RENAME(R.string.action_rename),
        RESET_NAME(R.string.action_reset_name),
        CUSTOM_ICON(R.string.action_custom_icon),
        RESET_ICON(R.string.action_reset_icon),
        FAVORITE(R.string.action_favorite),
        UNFAVORITE(R.string.action_unfavorite),
        OPEN_WITH(R.string.action_open_with),
        PLAYER(R.string.action_player),
        SCREENS(R.string.settings_stage_plot),
        SET_ART(R.string.action_set_artwork),
        DOWNLOAD_ART(R.string.action_download_artwork),
        ADD_TO_GRID(R.string.action_add_to_grid),
        ADD_TO_COLLECTION(R.string.action_add_to_collection),
        REMOVE_FROM_COLLECTION(R.string.action_remove_from_collection),
        MOVE_TO_TOP(R.string.action_move_to_top),
        MOVE_UP(R.string.action_move_up),
        MOVE_DOWN(R.string.action_move_down),
        MOVE_TO_END(R.string.action_move_to_end),
        DETAILS(R.string.action_details),
        COPY_TITLE(R.string.action_copy_title),
        BROWSE_RELATED(R.string.action_browse_related),
        MARK_PLAYED(R.string.action_mark_played),
        CLEAR_PLAY_STATS(R.string.stats_clear_play_stats),
        APP_INFO(R.string.action_app_info),
        HIDE(R.string.action_hide),
        NEW_FOLDER(R.string.action_new_folder),
        ADD_MEMBER(R.string.action_add_member),
        /** Generic remove (dock tile, folder). */
        REMOVE(R.string.action_remove),
        /** Clear this grid slot (app or ROM leaves a blank "+"). */
        REMOVE_FROM_GRID(R.string.action_remove_from_grid),
        /** Switch to Game Mode with this key selected (library power bridge). */
        OPEN_IN_GAME_MODE(R.string.action_open_in_game_mode),
        /** Switch to Game Mode and open library search. */
        SEARCH_LIBRARY(R.string.action_search_library),
        /** Copy folder members into a same-named collection (or vice versa). */
        MIRROR_TO_COLLECTION(R.string.action_mirror_to_collection),
        CANCEL(R.string.action_cancel),
    }

    sealed class Row {
        data class Header(val titleRes: Int) : Row()
        data class Item(
            val choice: Choice,
            val destructive: Boolean = false,
            /** Optional label override resource (else [Choice.labelRes]). */
            val labelRes: Int? = null,
        ) : Row()
    }

    private data class BoundAction(
        val view: TextView,
        val item: Row.Item,
        val destructive: Boolean,
    )

    private var selection = 0
    /** Indices into [rows] that are actionable (not headers). */
    private val actionRowIndices: List<Int> = SlotMenuNav.actionRowIndices(rows)
    private val actionRows = mutableListOf<BoundAction>()
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null

    val view: View by lazy {
        val overlay = OverlayPrimitives.modalBackdrop(context) { onChoice(Choice.CANCEL) }
        val cardWidth = OverlayPrimitives.responsiveCardWidth(
            context = context,
            maxDp = 340,
            widthFrac = 0.86f,
            minMarginDp = UiDimens.SPACE_LG,
        )
        val headerTitle = title?.takeIf { it.isNotBlank() }
        val card = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = UiDimens.SPACE_MD,
            padVerticalDp = 12,
        )

        if (headerTitle != null) {
            card.addView(
                OverlayPrimitives.modalHeader(
                    context = context,
                    title = headerTitle,
                    gravity = Gravity.START,
                    showDivider = true,
                ).apply {
                    setPadding(context.dp(UiDimens.SPACE_XS), 0, context.dp(UiDimens.SPACE_XS), context.dp(6))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val surfaceColor = TileBackgrounds.surfaceColor(context)
        val headerColor = ThemePack.accentTextColor(accentColor, surfaceColor)
        val dividerColor = TileBackgrounds.hairlineDivider(context)
        val locale = context.resources.configuration.locales[0]

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        rows.forEachIndexed { index, row ->
            when (row) {
                is Row.Header -> {
                    if (index > 0) {
                        list.addView(
                            View(context).apply { setBackgroundColor(dividerColor) },
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                context.strokePx(1),
                            ).apply {
                                topMargin = context.dp(UiDimens.SPACE_SM)
                                marginStart = context.dp(UiDimens.SPACE_SM)
                                marginEnd = context.dp(UiDimens.SPACE_SM)
                            },
                        )
                    }
                    val header = TextView(context).apply {
                        text = context.getString(row.titleRes).uppercase(locale)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        setTextColor(headerColor)
                        setTypeface(typeface, Typeface.BOLD)
                        letterSpacing = 0.12f
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(
                            context.dp(UiDimens.SPACE_MD),
                            context.dp(if (index == 0) 4 else 10),
                            context.dp(UiDimens.SPACE_MD),
                            context.dp(UiDimens.SPACE_XS),
                        )
                        isClickable = false
                        isFocusable = false
                    }
                    list.addView(
                        header,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
                is Row.Item -> {
                    val label = row.labelRes ?: row.choice.labelRes
                    val destructive = SlotMenuNav.isDestructive(row)
                    val actionTv = TextView(context).apply {
                        setText(label)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        minHeight = context.dp(44)
                        setPadding(
                            context.dp(UiDimens.SPACE_LG),
                            context.dp(10),
                            context.dp(UiDimens.SPACE_LG),
                            context.dp(10),
                        )
                        setOnClickListener { onChoice(row.choice) }
                        isClickable = true
                    }
                    actionRows.add(BoundAction(actionTv, row, destructive))
                    list.addView(
                        actionTv,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = context.dp(2)
                            bottomMargin = context.dp(2)
                        },
                    )
                }
            }
        }

        val scroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = context,
                heightFrac = if (headerTitle != null) 0.62f else 0.70f,
                minDp = 180,
            ),
        ).apply {
            isClickable = true
            addView(
                list,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        scrollHost = scroll
        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        card.addView(
            OverlayPrimitives.modalHintFooter(context, R.string.deck_hint_folder_close),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        paintRows(scrollToFocused = false)
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        overlay
    }

    private fun paintRows(scrollToFocused: Boolean = true) {
        if (actionRows.isEmpty()) return
        val sel = GridWrapNav.clampIndex(selection, actionRows.size)
        selection = sel
        actionRows.forEachIndexed { index, bound ->
            val selected = index == sel
            bound.view.background = if (selected) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
            val color = when {
                bound.destructive -> UiDimens.COLOR_DESTRUCTIVE
                bound.item.choice == Choice.CANCEL && !selected -> UiDimens.ALPHA_SECONDARY
                else -> UiDimens.ALPHA_PRIMARY
            }
            bound.view.setTextColor(color)
            bound.view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        if (!scrollToFocused) return
        val focused = actionRows.getOrNull(sel)?.view ?: return
        val scroller = scrollHost ?: return
        scroller.post {
            val targetY = (focused.top - (scroller.height - focused.height) / 2)
                .coerceAtLeast(0)
            scroller.smoothScrollTo(0, targetY)
        }
    }

    fun handleAction(action: Action): Boolean {
        if (actionRows.isEmpty()) {
            if (action == Action.BACK) onChoice(Choice.CANCEL)
            return true
        }
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN, Action.PAGE_PREV, Action.PAGE_NEXT -> {
                selection = SlotMenuNav.move(selection, actionRows.size, action)
                paintRows()
            }
            Action.CONFIRM -> {
                val rowIndex = actionRowIndices.getOrNull(selection) ?: return true
                val item = rows[rowIndex] as? Row.Item ?: return true
                onChoice(item.choice)
            }
            Action.BACK -> onChoice(Choice.CANCEL)
            else -> {}
        }
        return true
    }

    companion object {
        /** Flat choice list (dock / simple callers). */
        fun fromChoices(
            context: Context,
            accentColor: Int,
            choices: List<Choice>,
            title: CharSequence? = null,
            onChoice: (Choice) -> Unit,
        ): SlotMenu = SlotMenu(
            context = context,
            accentColor = accentColor,
            rows = choices.map { Row.Item(it) },
            title = title,
            onChoice = onChoice,
        )

        /** Build a sectioned grid tile menu (apps / ROMs). */
        fun gridTileRows(
            isRom: Boolean,
            isApp: Boolean,
            fav: Boolean,
            inDock: Boolean,
            hasCustomName: Boolean,
            hasCustomIcon: Boolean,
            showRelated: Boolean,
            showMarkPlayed: Boolean,
            showClearStats: Boolean,
        ): List<Row> = buildList {
            add(Row.Header(R.string.menu_section_arrange))
            add(Row.Item(Choice.MOVE))
            add(Row.Item(Choice.REMOVE_FROM_GRID, destructive = true))

            add(Row.Header(R.string.menu_section_dock))
            add(
                Row.Item(
                    if (inDock) Choice.UNPIN_FROM_DOCK else Choice.PIN_TO_DOCK,
                ),
            )

            add(Row.Header(R.string.menu_section_library))
            add(Row.Item(if (fav) Choice.UNFAVORITE else Choice.FAVORITE))
            add(Row.Item(Choice.ADD_TO_COLLECTION))
            if (showMarkPlayed) add(Row.Item(Choice.MARK_PLAYED))
            if (showClearStats) add(Row.Item(Choice.CLEAR_PLAY_STATS))

            if (isRom || isApp) {
                add(Row.Header(R.string.menu_section_customize))
                if (isRom) {
                    add(Row.Item(Choice.RENAME))
                    if (hasCustomName) add(Row.Item(Choice.RESET_NAME))
                    add(Row.Item(Choice.OPEN_WITH))
                    add(Row.Item(Choice.PLAYER))
                    add(Row.Item(Choice.SCREENS))
                    add(Row.Item(Choice.SET_ART))
                    add(Row.Item(Choice.DOWNLOAD_ART))
                    add(Row.Item(Choice.HIDE, destructive = true))
                }
                if (isApp) {
                    add(Row.Item(Choice.RENAME))
                    if (hasCustomName) add(Row.Item(Choice.RESET_NAME))
                    add(Row.Item(Choice.CUSTOM_ICON))
                    if (hasCustomIcon) add(Row.Item(Choice.RESET_ICON))
                }
            }

            add(Row.Header(R.string.menu_section_more))
            add(Row.Item(Choice.OPEN_IN_GAME_MODE))
            add(Row.Item(Choice.SEARCH_LIBRARY))
            add(Row.Item(Choice.DETAILS))
            add(Row.Item(Choice.COPY_TITLE))
            if (showRelated) add(Row.Item(Choice.BROWSE_RELATED))
            if (isApp) add(Row.Item(Choice.APP_INFO))
            add(Row.Item(Choice.CANCEL))
        }
    }
}
