package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey

// Full-screen picker overlay: centered dark card with a live-filter search
// field above a two-section list — "Apps" (installed launchable, non-hidden)
// then "ROMs" (the scanned library, sorted by platform then name).
// Used for "Add to grid/dock" (pick fills a slot) and the swipe-up
// "All apps" drawer (pick launches). ROM rows show a platform tile thumb,
// the ROM name, and the platform label. Long-press hide stays apps-only.
// Touch (tap a row) and gamepad (d-pad highlight, A pick, B cancel) both
// work; d-pad/A/B keys are intercepted by BaseDeckActivity before the
// focused search field can see them, letters fall through to the field.
class AppPicker(
    private val activity: AppCompatActivity,
    private val accentColor: Int,
    private val allEntries: List<AppEntry>,
    private val roms: List<RomEntry>,
    private val iconLoader: AppIconLoader,
    // "Add to grid" / "Add to dock" / "All apps" (launch drawer).
    private val title: String? = null,
    // Swipe-up drawer must not pop the IME (keyboard flash); slot pickers
    // still focus search so typing to filter is one tap less.
    private val autoShowKeyboard: Boolean = true,
    // Fraction of screen height for the card (drawer wants taller).
    private val heightFraction: Float = 0.62f,
    // Prebuilt empty-query list (all-apps drawer cache). Null → build now.
    private val prebuiltItems: List<PickerItem>? = null,
    private val onPick: (String) -> Unit,
    private val onHide: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val app get() = activity.application as GhostGalleonApp
    private val hiddenRomIds get() = app.settings.hiddenRomIds
    // Sort once for the lifetime of this picker — keystroke filters only.
    private val sortedRoms: List<RomEntry> =
        PickerItems.sortedRoms(roms, hiddenRomIds)
    private var emptyQueryItems: List<PickerItem> =
        prebuiltItems ?: PickerItems.build(
            allEntries, roms, "",
            hiddenRomIds = hiddenRomIds,
            preSortedRoms = sortedRoms,
        )
    private var items: List<PickerItem> = emptyQueryItems
    // The highlight only ever rests on data rows, never section headers.
    private var highlight: Int =
        items.indexOfFirst { it !is PickerItem.Header }.coerceAtLeast(0)
    private var query = ""
    private var lastFilterQuery = ""
    private var lastFilterItems: List<PickerItem>? = null
    private var recycler: RecyclerView? = null
    private var layoutManager: LinearLayoutManager? = null
    private var adapter: PickerAdapter? = null
    private var emptyView: TextView? = null
    private var overlayView: FrameLayout? = null
    private var hideMenu: HideMenu? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private fun applySearch(next: String) {
        val prevKey = items.getOrNull(highlight)?.let { PickerItems.rowKey(it) }
        query = next
        items = if (query.isBlank()) {
            emptyQueryItems
        } else {
            PickerItems.build(
                allEntries, roms, query,
                hiddenRomIds = hiddenRomIds,
                preSortedRoms = sortedRoms,
                previousQuery = lastFilterQuery,
                previousItems = lastFilterItems,
            )
        }
        lastFilterQuery = query
        lastFilterItems = items
        val kept = prevKey?.let { key ->
            items.indexOfFirst { PickerItems.rowKey(it) == key }
        } ?: -1
        highlight = if (kept >= 0) {
            kept
        } else {
            items.indexOfFirst { it !is PickerItem.Header }.coerceAtLeast(0)
        }
        adapter?.submit(items)
        refreshEmpty()
        recycler?.scrollToPosition(if (kept >= 0) highlight else 0)
    }

    val view: View by lazy {
        val context: Context = activity
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
            setOnClickListener { onClose() }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(context)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        card.addView(TextView(context).apply {
            text = title ?: context.getString(R.string.action_add_to_grid)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val search = EditText(context).apply {
            setHint(R.string.deck_search_apps_roms)
            setHintTextColor(0x66FFFFFF)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine()
            // Darker than the card so the field reads as a field.
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF101014.toInt())
                cornerRadius = 28 * density
                setStroke((1 * density).toInt(), 0x33FFFFFF)
            }
            setPadding(dp(16), dp(4), dp(16), dp(4))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // Debounce: typing in a 6k-ROM library re-filters only,
                    // never re-sorts (see preSortedRoms).
                    pendingSearch?.let { searchHandler.removeCallbacks(it) }
                    val next = s?.toString().orEmpty()
                    val run = Runnable { applySearch(next) }
                    pendingSearch = run
                    searchHandler.postDelayed(run, SEARCH_DEBOUNCE_MS)
                }
            })
        }
        card.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })

        val pickerAdapter = PickerAdapter(context, dp(40), dp(12), dp(10))
        val list = RecyclerView(context).apply {
            val lm = LinearLayoutManager(context)
            layoutManager = lm
            adapter = pickerAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        // Row taps are handled by each row's own OnClickListener: the
        // long-press listener (hide menu) makes rows consume touches.
        val empty = TextView(context).apply {
            setText(R.string.browse_no_matches_short)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0x66FFFFFF)
            gravity = Gravity.CENTER
        }
        card.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        card.addView(empty, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        recycler = list
        layoutManager = list.layoutManager as LinearLayoutManager
        adapter = pickerAdapter
        emptyView = empty
        pickerAdapter.submit(items)
        refreshEmpty()
        overlayView = overlay
        val metrics = context.resources.displayMetrics
        // Top-anchored. Slot pickers stay short so the IME does not cover
        // results; the swipe-up drawer uses a taller card and no auto-IME.
        overlay.addView(card, FrameLayout.LayoutParams(
            minOf(dp(600), (metrics.widthPixels * 0.85f).toInt()),
            (metrics.heightPixels * heightFraction).toInt(),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(16)
        })
        // Only auto-focus search when requested (slot "Add to …" pickers).
        // The all-apps drawer leaves focus on the list so the keyboard does
        // not flash open/closed on every swipe-up.
        if (autoShowKeyboard) {
            search.post {
                search.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            // Prevent the EditText from stealing focus on attach.
            search.isFocusable = true
            search.isFocusableInTouchMode = true
            list.isFocusable = true
            list.isFocusableInTouchMode = true
            list.post { list.requestFocus() }
        }
        overlay
    }

    fun handleAction(action: Action): Boolean {
        hideMenu?.let { return it.handleAction(action) }
        when (action) {
            Action.NAV_UP -> moveHighlight(-1)
            Action.NAV_DOWN -> moveHighlight(1)
            Action.PAGE_PREV -> moveHighlight(-5)
            Action.PAGE_NEXT -> moveHighlight(5)
            Action.CONFIRM -> when (val item = items.getOrNull(highlight)) {
                is PickerItem.App -> onPick(item.entry.packageName)
                is PickerItem.Rom -> onPick(SlotKey.rom(item.entry.id))
                else -> {}
            }
            Action.BACK -> onClose()
            else -> {}
        }
        return true // the modal swallows every action while open
    }

    // The highlight only ever rests on data rows: section headers are
    // skipped in both directions.
    private fun moveHighlight(delta: Int) {
        if (items.isEmpty()) return
        var next = highlight
        val step = if (delta > 0) 1 else -1
        var remaining = kotlin.math.abs(delta)
        while (remaining > 0) {
            val candidate = next + step
            if (candidate !in items.indices) break
            next = candidate
            if (items[next] !is PickerItem.Header) remaining--
        }
        if (next == highlight || items[next] is PickerItem.Header) return
        highlight = next
        adapter?.notifyItemRangeChanged(0, items.size, PAYLOAD_HIGHLIGHT)
        val list = recycler ?: return
        val lm = layoutManager
        val first = lm?.findFirstVisibleItemPosition() ?: 0
        val last = lm?.findLastVisibleItemPosition() ?: 0
        if (next < first || next > last) {
            list.smoothScrollToPosition(next)
        }
    }

    // Moves the accent ring to the highlighted row by updating the visible
    // rows in place; rows (re)attached while scrolling are bound with the
    // current highlight by getView.
    private fun refreshEmpty() {
        val none = items.isEmpty()
        emptyView?.visibility = if (none) View.VISIBLE else View.GONE
        recycler?.visibility = if (none) View.GONE else View.VISIBLE
    }

    // Long-press an app row opens the confirm menu ("Hide app" / "Cancel")
    // for that entry; the menu sits on top of the picker overlay. ROM rows
    // have no hide menu (hiding stays apps-only).
    private fun openHideMenu(entry: AppEntry) {
        if (hideMenu != null) return
        val overlay = overlayView ?: return
        val menu = HideMenu(entry)
        hideMenu = menu
        overlay.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun closeHideMenu() {
        hideMenu?.let { overlayView?.removeView(it.view) }
        hideMenu = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 110L
        private const val PAYLOAD_HIGHLIGHT = "highlight"
    }

    // Confirm-style modal for a picker row: "Hide app" adds the package to
    // settings.hiddenPackages (via onHide) so it leaves the picker and the
    // all-apps lists; a grid slot that already holds it stays launchable.
    private inner class HideMenu(private val entry: AppEntry) {
        private val choices = intArrayOf(R.string.action_hide, R.string.action_cancel)
        private var selection = 0
        private val rows = mutableListOf<TextView>()

        val view: View by lazy {
            val density = activity.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            val menuOverlay = FrameLayout(activity).apply {
                setBackgroundColor(0x99000000.toInt())
                isClickable = true
                setOnClickListener { closeHideMenu() }
            }
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = TileBackgrounds.card(activity)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            card.addView(TextView(activity).apply {
                text = entry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(24), 0, dp(24), dp(4))
            }, LinearLayout.LayoutParams(
                dp(220), ViewGroup.LayoutParams.WRAP_CONTENT))
            choices.forEachIndexed { index, labelRes ->
                val row = TextView(activity).apply {
                    setText(labelRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dp(24), dp(12), dp(24), dp(12))
                    setOnClickListener { choose(index) }
                }
                rows.add(row)
                card.addView(row, LinearLayout.LayoutParams(
                    dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4); bottomMargin = dp(4)
                })
            }
            paintRows()
            menuOverlay.addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER))
            menuOverlay
        }

        private fun choose(index: Int) {
            closeHideMenu()
            if (index == 0) onHide(entry.packageName)
        }

        private fun paintRows() {
            rows.forEachIndexed { index, row ->
                row.background = if (index == selection) {
                    TileBackgrounds.selected(activity, accentColor)
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
                Action.CONFIRM -> choose(selection)
                Action.BACK -> closeHideMenu()
                else -> {}
            }
            return true
        }
    }

    // Row views are recycled (the ROM section can hold thousands of rows):
    // a shared row layout whose thumb container swaps app icon <-> platform
    // tile and whose text views are simply re-bound.
    private class RowHolder(
        val thumb: FrameLayout,
        val name: TextView,
        val tag: TextView,
    )

    private inner class HeaderVH(val text: TextView) : RecyclerView.ViewHolder(text)

    private inner class RowVH(
        val row: LinearLayout,
        val cells: RowHolder,
    ) : RecyclerView.ViewHolder(row)

    private inner class PickerAdapter(
        private val context: Context,
        private val iconSize: Int,
        private val rowPadH: Int,
        private val rowPadV: Int,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<PickerItem> = emptyList()

        init {
            setHasStableIds(true)
        }

        fun submit(next: List<PickerItem>) {
            val old = rows
            val diff = DiffUtil.calculateDiff(PickerDiff(old, next), false)
            rows = next
            diff.dispatchUpdatesTo(this)
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemId(position: Int): Long =
            PickerItems.itemId(rows[position]).hashCode().toLong()

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is PickerItem.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            if (viewType == 0) {
                val header = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(0x66FFFFFF)
                    setPadding(rowPadH, dp(10), rowPadH, dp(2))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                return HeaderVH(header)
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val thumb = FrameLayout(context)
            row.addView(thumb, LinearLayout.LayoutParams(iconSize, iconSize))
            val name = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(0xFFFFFFFF.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(12), 0, dp(8), 0)
            }
            row.addView(name, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val tag = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0x66FFFFFF)
                maxLines = 1
            }
            row.addView(tag, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            return RowVH(row, RowHolder(thumb, name, tag))
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
            payloads: MutableList<Any>,
        ) {
            if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_HIGHLIGHT }) {
                if (holder is RowVH) paintHighlight(holder.row, position)
                return
            }
            onBindViewHolder(holder, position)
        }

        @Suppress("DEPRECATION") // Picker haptics intentionally override OS touch feedback.
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = rows[position]
            if (holder is HeaderVH && item is PickerItem.Header) {
                holder.text.setText(
                    if (item.section == PickerItem.Header.Section.APPS) R.string.label_apps
                    else R.string.label_roms,
                )
                holder.text.background = null
                return
            }
            if (holder !is RowVH) return
            val row = holder.row
            val cells = holder.cells
            when (item) {
                is PickerItem.App -> {
                    val bindKey = "a:${item.entry.packageName}"
                    val existing = cells.thumb.getChildAt(0) as? ImageView
                    if (existing == null || cells.thumb.tag != bindKey) {
                        cells.thumb.removeAllViews()
                        val icon = ImageView(context)
                        cells.thumb.addView(
                            icon, FrameLayout.LayoutParams(iconSize, iconSize),
                        )
                        CustomIcon.bind(
                            icon, iconLoader, app.artCache, app.settings,
                            item.entry.packageName, iconSize)
                        cells.thumb.tag = bindKey
                    }
                    cells.name.text = item.entry.label
                    cells.tag.text = ""
                    row.setOnClickListener {
                        if (app.settings.haptics) {
                            row.performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                        }
                        onPick(item.entry.packageName)
                    }
                    row.setOnLongClickListener {
                        openHideMenu(item.entry)
                        true
                    }
                    row.isLongClickable = true
                }
                is PickerItem.Rom -> {
                    val pid = item.entry.platformId
                    val existing = cells.thumb.getChildAt(0) as? TextView
                    if (existing != null && cells.thumb.childCount == 1) {
                        PlatformTile.restyle(
                            existing, context, pid, cornerRadiusDp = 12,
                        )
                    } else {
                        cells.thumb.removeAllViews()
                        cells.thumb.addView(
                            PlatformTile.view(context, pid, cornerRadiusDp = 12),
                            FrameLayout.LayoutParams(iconSize, iconSize),
                        )
                    }
                    cells.thumb.tag = "r:$pid"
                    cells.name.text = item.entry.name
                    cells.tag.text = Platforms.byId(pid)?.displayName ?: pid
                    row.setOnClickListener { onPick(SlotKey.rom(item.entry.id)) }
                    row.setOnLongClickListener(null)
                    row.isLongClickable = false
                }
                is PickerItem.Header -> {}
            }
            paintHighlight(row, position)
        }

        private fun paintHighlight(row: View, position: Int) {
            row.background = if (position == highlight) {
                TileBackgrounds.selected(context, accentColor)
            } else {
                null
            }
        }
    }

    private class PickerDiff(
        private val old: List<PickerItem>,
        private val next: List<PickerItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = next.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            PickerItems.itemId(old[oldItemPosition]) == PickerItems.itemId(next[newItemPosition])
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == next[newItemPosition]
    }
}
