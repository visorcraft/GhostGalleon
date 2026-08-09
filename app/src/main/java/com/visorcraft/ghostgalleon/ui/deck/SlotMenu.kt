package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.ui.dp

// Small centered modal for a filled grid tile: Move / Pin to dock /
// Remove / Cancel, plus Rename / Custom icon (and their Reset variants) for
// app tiles. Dark dimmed overlay, dark card, accent-highlighted row. Touch
// (row tap) and gamepad (d-pad + A, B cancels) both work.
class SlotMenu(
    private val context: Context,
    private val accentColor: Int,
    // The caller trims the list per tile (rename/icon entries are app-only,
    // reset entries only when an override exists).
    private val choices: List<Choice> = Choice.entries,
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
        REMOVE(R.string.action_remove),
        CANCEL(R.string.action_cancel),
    }

    private var selection = 0
    private val rows = mutableListOf<TextView>()

    val view: View by lazy {

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true // swallow touches so the grid beneath stays inert
            setOnClickListener { onChoice(Choice.CANCEL) }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }
        choices.forEach { choice ->
            val row = TextView(context).apply {
                setText(choice.labelRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(context.dp(24), context.dp(12), context.dp(24), context.dp(12))
                setOnClickListener { onChoice(choice) }
            }
            rows.add(row)
            card.addView(row, LinearLayout.LayoutParams(
                context.dp(220), android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(4); bottomMargin = context.dp(4)
            })
        }
        paintRows()
        overlay.addView(card, FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))
        overlay
    }

    private fun paintRows() {
        rows.forEachIndexed { index, row ->
            row.background = if (index == selection) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
        }
    }

    fun handleAction(action: Action): Boolean {
        when (action) {
            Action.NAV_UP -> {
                selection = (selection + choices.size - 1) % choices.size
                paintRows()
            }
            Action.NAV_DOWN -> {
                selection = (selection + 1) % choices.size
                paintRows()
            }
            Action.CONFIRM -> onChoice(choices[selection])
            Action.BACK -> onChoice(Choice.CANCEL)
            else -> {}
        }
        return true // the modal swallows every action while open
    }
}
