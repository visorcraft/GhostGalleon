package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.ui.dp

/**
 * Centered modal for a filled grid/dock tile (or Game Mode entry).
 *
 * Long option lists used to overflow the short Sugar panel with no scroll,
 * burying **Remove** off-screen. Rows are sectioned, scrollable, and
 * destructive actions (remove) are called out in accent red.
 */
class SlotMenu(
    private val context: Context,
    private val accentColor: Int,
    private val rows: List<Row>,
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

    private var selection = 0
    /** Indices into [rows] that are actionable (not headers). */
    private val actionRowIndices: List<Int> =
        rows.mapIndexedNotNull { i, row -> if (row is Row.Item) i else null }
    private val actionRows = mutableListOf<TextView>()

    val view: View by lazy {
        val density = context.resources.displayMetrics.density
        val screenH = context.resources.displayMetrics.heightPixels
        // Leave room for system chrome; never cover the whole short panel.
        val maxCardH = (screenH * 0.72f).toInt().coerceAtLeast((200 * density).toInt())
        val cardW = (260 * density).toInt()

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            setOnClickListener { onChoice(Choice.CANCEL) }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        }
        rows.forEachIndexed { index, row ->
            when (row) {
                is Row.Header -> {
                    val header = TextView(context).apply {
                        setText(row.titleRes)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(0x88FFFFFF.toInt())
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(
                            context.dp(12),
                            context.dp(if (index == 0) 4 else 12),
                            context.dp(12),
                            context.dp(4),
                        )
                        isClickable = false
                        isFocusable = false
                    }
                    card.addView(
                        header,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
                is Row.Item -> {
                    val label = row.labelRes ?: row.choice.labelRes
                    val isDestructive = row.destructive ||
                        row.choice == Choice.REMOVE ||
                        row.choice == Choice.REMOVE_FROM_GRID ||
                        row.choice == Choice.HIDE
                    val actionTv = TextView(context).apply {
                        setText(label)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        setTextColor(
                            if (isDestructive) 0xFFFF8A80.toInt() else Color.WHITE,
                        )
                        gravity = Gravity.CENTER
                        setPadding(
                            context.dp(20),
                            context.dp(11),
                            context.dp(20),
                            context.dp(11),
                        )
                        setOnClickListener { onChoice(row.choice) }
                        // Stop overlay cancel when tapping a row.
                        isClickable = true
                    }
                    actionRows.add(actionTv)
                    card.addView(
                        actionTv,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = context.dp(2)
                            bottomMargin = context.dp(2)
                        },
                    )
                }
            }
        }
        paintRows()

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            // Don't let the overlay steal the first scroll touch.
            isClickable = true
            setOnClickListener { /* swallow — card children handle taps */ }
            addView(
                card,
                FrameLayout.LayoutParams(
                    cardW,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        overlay.addView(
            scroll,
            FrameLayout.LayoutParams(
                cardW,
                maxCardH,
                Gravity.CENTER,
            ),
        )
        // Size scroll height to content when short; cap when long.
        scroll.post {
            val contentH = card.height
            val lp = scroll.layoutParams as FrameLayout.LayoutParams
            lp.height = contentH.coerceAtMost(maxCardH)
            scroll.layoutParams = lp
        }
        overlay
    }

    private fun paintRows() {
        if (actionRows.isEmpty()) return
        val sel = selection.coerceIn(0, actionRows.lastIndex)
        actionRows.forEachIndexed { index, row ->
            row.background = if (index == sel) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
        }
        // Keep focused row visible when d-pad navigating a long list.
        actionRows.getOrNull(sel)?.let { focused ->
            val scroll = (focused.parent as? View)?.parent as? ScrollView
            scroll?.post {
                val y = focused.top - scroll.height / 3
                scroll.smoothScrollTo(0, y.coerceAtLeast(0))
            }
        }
    }

    fun handleAction(action: Action): Boolean {
        if (actionRows.isEmpty()) {
            if (action == Action.BACK) onChoice(Choice.CANCEL)
            return true
        }
        when (action) {
            Action.NAV_UP -> {
                selection = (selection + actionRows.size - 1) % actionRows.size
                paintRows()
            }
            Action.NAV_DOWN -> {
                selection = (selection + 1) % actionRows.size
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
            onChoice: (Choice) -> Unit,
        ): SlotMenu = SlotMenu(
            context,
            accentColor,
            choices.map { Row.Item(it) },
            onChoice,
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
                    add(Row.Item(Choice.OPEN_WITH))
                    add(Row.Item(Choice.PLAYER))
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
