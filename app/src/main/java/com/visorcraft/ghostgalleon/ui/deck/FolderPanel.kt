package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.ui.dp

/**
 * Modal folder of members as a small art grid. A launches; B closes.
 * Long-press or Y removes the selected member without closing.
 */
class FolderPanel(
    private val context: Context,
    private val accentColor: Int,
    private val title: String,
    private val members: List<Pair<String, String>>, // key -> label
    private val onLaunch: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onRemoveMember: ((String) -> Unit)? = null,
    /** Mirror folder members into a same-named Game Mode collection. */
    private val onMirrorToCollection: (() -> Unit)? = null,
    /** Bind thumbnail art into the cell [ImageView] for [key]. */
    private val onBindThumb: ((ImageView, String) -> Unit)? = null,
) {
    private var selection = 0
    private val cells = mutableListOf<View>()
    private var memberKeys: MutableList<Pair<String, String>> = members.toMutableList()
    private var gridHost: LinearLayout? = null

    val view: View by lazy {

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            setOnClickListener { onClose() }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }
        card.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, context.dp(8))
        })
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        gridHost = grid
        rebuildGrid()
        val scroll = ScrollView(context).apply { addView(grid) }
        card.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(320),
        ))
        if (onMirrorToCollection != null && memberKeys.isNotEmpty()) {
            card.addView(TextView(context).apply {
                setText(R.string.action_mirror_to_collection)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = TileBackgrounds.selected(context, accentColor)
                setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
                setOnClickListener { onMirrorToCollection.invoke() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(8) })
        }
        card.addView(TextView(context).apply {
            setText(
                if (onRemoveMember != null) R.string.deck_hint_folder
                else R.string.deck_hint_folder_close,
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, context.dp(8), 0, 0)
        })
        overlay.addView(card, FrameLayout.LayoutParams(
            context.dp(360),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        overlay
    }

    private fun rebuildGrid() {
        val grid = gridHost ?: return
        grid.removeAllViews()
        cells.clear()
        if (memberKeys.isEmpty()) {
            grid.addView(TextView(context).apply {
                setText(R.string.deck_empty_folder)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(context.dp(16), context.dp(20), context.dp(16), context.dp(20))
            })
            return
        }
        val cols = 3
        val cellSize = context.dp(96)
        var row: LinearLayout? = null
        memberKeys.forEachIndexed { index, (key, label) ->
            if (index % cols == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                grid.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = context.dp(8) })
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
                setOnClickListener {
                    selection = index
                    paintCells()
                    onLaunch(key)
                }
                setOnLongClickListener {
                    selection = index
                    paintCells()
                    removeSelected()
                    true
                }
            }
            val thumb = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cell.addView(thumb, LinearLayout.LayoutParams(cellSize, cellSize))
            onBindThumb?.invoke(thumb, key)
            cell.addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(cellSize, ViewGroup.LayoutParams.WRAP_CONTENT))
            cells.add(cell)
            row?.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paintCells()
    }

    private fun paintCells() {
        cells.forEachIndexed { index, cell ->
            cell.background = if (index == selection) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
        }
    }

    private fun removeSelected() {
        val remove = onRemoveMember ?: return
        if (memberKeys.isEmpty()) return
        val idx = selection.coerceIn(0, memberKeys.lastIndex)
        val key = memberKeys[idx].first
        remove(key)
        memberKeys.removeAt(idx)
        if (memberKeys.isNotEmpty()) {
            selection = idx.coerceAtMost(memberKeys.lastIndex)
        }
        rebuildGrid()
    }

    fun handleAction(action: Action): Boolean {
        when (action) {
            Action.NAV_UP, Action.NAV_LEFT -> {
                if (memberKeys.isNotEmpty()) {
                    val step = if (action == Action.NAV_UP) 3 else 1
                    selection = (selection + memberKeys.size - step) % memberKeys.size
                    paintCells()
                }
            }
            Action.NAV_DOWN, Action.NAV_RIGHT -> {
                if (memberKeys.isNotEmpty()) {
                    val step = if (action == Action.NAV_DOWN) 3 else 1
                    selection = (selection + step) % memberKeys.size
                    paintCells()
                }
            }
            Action.CONFIRM -> {
                if (memberKeys.isNotEmpty()) {
                    onLaunch(memberKeys[selection].first)
                }
            }
            Action.TOGGLE_MODE -> removeSelected()
            Action.BACK -> onClose()
            else -> {}
        }
        return true
    }
}
