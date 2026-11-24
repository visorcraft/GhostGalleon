package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
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
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.strokePx

/** Result of moving highlight inside [AppPicker] — O(1) changed indices only. */
internal data class PickerHighlightMove(
    val previous: Int,
    val current: Int,
    val changedPositions: List<Int>,
)

/**
 * Pure, host-tested highlight-transition and result-count math for [AppPicker].
 * Guarantees highlight never rests on a section header and emits only the
 * changed positions (`<= 2`) so 6,384+ ROM lists never trigger O(N) rebinds.
 */
internal object AppPickerHighlight {

    fun firstSelectable(items: List<PickerItem>): Int =
        items.indexOfFirst { it !is PickerItem.Header }.coerceAtLeast(0)

    fun selectableCount(items: List<PickerItem>): Int =
        items.count { it !is PickerItem.Header }

    fun move(items: List<PickerItem>, current: Int, delta: Int): PickerHighlightMove {
        if (items.isEmpty() || delta == 0) {
            val safe = if (items.isEmpty()) 0 else current.coerceIn(0, items.lastIndex)
            return PickerHighlightMove(previous = safe, current = safe, changedPositions = emptyList())
        }
        val start = current.coerceIn(0, items.lastIndex)
        var cursor = start
        val step = if (delta > 0) 1 else -1
        var remaining = kotlin.math.abs(delta)
        var lastValid = if (items[start] !is PickerItem.Header) start else -1
        while (remaining > 0) {
            val candidate = cursor + step
            if (candidate !in items.indices) break
            cursor = candidate
            if (items[cursor] !is PickerItem.Header) {
                lastValid = cursor
                remaining--
            }
        }
        if (lastValid < 0 || lastValid == start) {
            return PickerHighlightMove(previous = start, current = start, changedPositions = emptyList())
        }
        return PickerHighlightMove(
            previous = start,
            current = lastValid,
            changedPositions = listOf(start, lastValid),
        )
    }

    fun afterFilter(items: List<PickerItem>, previousKey: String?): Int {
        if (items.isEmpty()) return 0
        val kept = previousKey?.let { key ->
            items.indexOfFirst { PickerItems.rowKey(it) == key }
        } ?: -1
        return if (kept >= 0) kept else firstSelectable(items)
    }

    /**
     * Positions to notify with `PAYLOAD_HIGHLIGHT` after a list update when
     * `DiffUtil` moved or kept rows whose highlight state flipped.
     */
    fun positionsToRefreshAfterFilter(
        previousHighlight: Int,
        nextHighlight: Int,
        newSize: Int,
    ): List<Int> {
        if (newSize <= 0) return emptyList()
        val out = ArrayList<Int>(2)
        if (previousHighlight in 0 until newSize && previousHighlight != nextHighlight) {
            out.add(previousHighlight)
        }
        if (nextHighlight in 0 until newSize) {
            out.add(nextHighlight)
        }
        return out
    }
}

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
    private var highlight: Int = AppPickerHighlight.firstSelectable(items)
    private var query = ""
    private var lastFilterQuery = ""
    private var lastFilterItems: List<PickerItem>? = null
    private var recycler: RecyclerView? = null
    private var layoutManager: LinearLayoutManager? = null
    private var adapter: PickerAdapter? = null
    private var emptyView: TextView? = null
    private var countBadge: TextView? = null
    private var overlayView: FrameLayout? = null
    private var hideMenu: SlotMenu? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private fun cancelPendingSearch() {
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
    }

    private fun applySearch(next: String) {
        val prevHighlight = highlight
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
        val kept = prevKey != null && items.any { PickerItems.rowKey(it) == prevKey }
        highlight = AppPickerHighlight.afterFilter(items, prevKey)
        adapter?.submit(items)
        AppPickerHighlight.positionsToRefreshAfterFilter(
            previousHighlight = prevHighlight,
            nextHighlight = highlight,
            newSize = items.size,
        ).forEach { pos ->
            adapter?.notifyItemChanged(pos, PAYLOAD_HIGHLIGHT)
        }
        refreshEmpty()
        recycler?.scrollToPosition(if (kept) highlight else 0)
    }

    val view: View by lazy {
        val context: Context = activity
        val surfaceColor = TileBackgrounds.surfaceColor(context)
        val sunkenColor = TileBackgrounds.surfaceSunken(context)
        val accentText = ThemePack.accentTextColor(accentColor, sunkenColor)

        val overlay = OverlayPrimitives.modalBackdrop(context) {
            cancelPendingSearch()
            onClose()
        }.apply {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    cancelPendingSearch()
                }
            })
        }

        val cardWidth = OverlayPrimitives.responsiveCardWidth(
            context = context,
            maxDp = 600,
            widthFrac = 0.86f,
            minMarginDp = UiDimens.SPACE_LG,
        )
        val card = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = UiDimens.SPACE_LG,
            padVerticalDp = 14,
        )

        // Header row: modal title + live result-count pill + 1dp hairline divider.
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(
            TextView(context).apply {
                text = title ?: context.getString(R.string.action_add_to_grid)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val badge = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(accentText)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 10)
            setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
        }
        countBadge = badge
        headerRow.addView(
            badge,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(UiDimens.SPACE_SM) },
        )
        card.addView(
            headerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(
            View(context).apply {
                setBackgroundColor(TileBackgrounds.hairlineDivider(context))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.strokePx(1),
            ).apply {
                topMargin = context.dp(10)
                bottomMargin = context.dp(10)
            },
        )

        // Sunken search bar with leading ic_search and trailing ic_close clear trigger.
        val searchBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 24)
            minimumHeight = context.dp(44)
            setPadding(context.dp(12), context.dp(2), context.dp(6), context.dp(2))
        }
        searchBox.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_MUTED)
                scaleType = ImageView.ScaleType.FIT_CENTER
                isFocusable = false
            },
            LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply {
                marginEnd = context.dp(UiDimens.SPACE_SM)
            },
        )
        lateinit var clearBtn: ImageView
        val search = EditText(context).apply {
            setHint(R.string.deck_search_apps_roms)
            setHintTextColor(UiDimens.ALPHA_SUBTLE)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setSingleLine()
            background = null
            setPadding(0, context.dp(6), 0, context.dp(6))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val next = s?.toString().orEmpty()
                    clearBtn.visibility = if (next.isNotEmpty()) View.VISIBLE else View.GONE
                    // Debounce: typing in a 6k-ROM library re-filters only,
                    // never re-sorts (see preSortedRoms).
                    cancelPendingSearch()
                    val run = Runnable { applySearch(next) }
                    pendingSearch = run
                    searchHandler.postDelayed(run, SEARCH_DEBOUNCE_MS)
                }
            })
        }
        searchBox.addView(
            search,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        clearBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_SECONDARY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(R.string.action_clear)
            isFocusable = false
            visibility = View.GONE
            val pad = context.dp(6)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                search.setText("")
                cancelPendingSearch()
                applySearch("")
            }
        }
        searchBox.addView(
            clearBtn,
            LinearLayout.LayoutParams(context.dp(32), context.dp(32)).apply {
                marginStart = context.dp(UiDimens.SPACE_XS)
            },
        )
        card.addView(
            searchBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = context.dp(10) },
        )

        val pickerAdapter = PickerAdapter(
            context = context,
            iconSize = context.dp(40),
            rowPadH = context.dp(UiDimens.SPACE_MD),
            rowPadV = context.dp(10),
            headerColor = ThemePack.accentTextColor(accentColor, surfaceColor),
        )
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(UiDimens.ALPHA_MUTED)
            gravity = Gravity.CENTER
        }
        card.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        card.addView(
            empty,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        card.addView(
            OverlayPrimitives.modalHintFooter(context, R.string.deck_hint_folder_close),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

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
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                cardWidth,
                (metrics.heightPixels * heightFraction).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = context.dp(UiDimens.SPACE_LG)
            },
        )
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
                is PickerItem.App -> {
                    cancelPendingSearch()
                    onPick(item.entry.packageName)
                }
                is PickerItem.Rom -> {
                    cancelPendingSearch()
                    onPick(SlotKey.rom(item.entry.id))
                }
                else -> {}
            }
            Action.BACK -> {
                cancelPendingSearch()
                onClose()
            }
            else -> {}
        }
        return true // the modal swallows every action while open
    }

    // The highlight only ever rests on data rows: section headers are skipped
    // in both directions, and only the previous + next positions are notified.
    private fun moveHighlight(delta: Int) {
        val move = AppPickerHighlight.move(items, highlight, delta)
        if (move.changedPositions.isEmpty()) return
        highlight = move.current
        move.changedPositions.forEach { pos ->
            if (pos in items.indices) {
                adapter?.notifyItemChanged(pos, PAYLOAD_HIGHLIGHT)
            }
        }
        val list = recycler ?: return
        val lm = layoutManager
        val first = lm?.findFirstVisibleItemPosition() ?: 0
        val last = lm?.findLastVisibleItemPosition() ?: 0
        if (move.current < first || move.current > last) {
            list.smoothScrollToPosition(move.current)
        }
    }

    private fun refreshEmpty() {
        val selectable = AppPickerHighlight.selectableCount(items)
        val none = selectable == 0
        emptyView?.visibility = if (none) View.VISIBLE else View.GONE
        recycler?.visibility = if (none) View.GONE else View.VISIBLE
        countBadge?.text = activity.resources.getQuantityString(
            R.plurals.count_items,
            selectable,
            selectable,
        )
    }

    @Suppress("DEPRECATION") // Picker haptics intentionally override OS touch feedback.
    private fun performPickHaptic(row: View) {
        if (app.settings.haptics) {
            row.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
    }

    // Long-press an app row opens a destructive SlotMenu ("Hide from library" /
    // "Cancel") for that entry on top of the picker overlay. ROM rows stay
    // apps-only for hide.
    private fun openHideMenu(entry: AppEntry) {
        if (hideMenu != null) return
        val overlay = overlayView ?: return
        val menu = SlotMenu(
            context = activity,
            accentColor = accentColor,
            rows = listOf(
                SlotMenu.Row.Item(SlotMenu.Choice.HIDE, destructive = true),
                SlotMenu.Row.Item(SlotMenu.Choice.CANCEL),
            ),
            title = entry.label,
        ) { choice ->
            closeHideMenu()
            if (choice == SlotMenu.Choice.HIDE) {
                cancelPendingSearch()
                onHide(entry.packageName)
            }
        }
        hideMenu = menu
        overlay.addView(
            menu.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun closeHideMenu() {
        hideMenu?.let { overlayView?.removeView(it.view) }
        hideMenu = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 60L
        private const val PAYLOAD_HIGHLIGHT = "highlight"
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
        private val headerColor: Int,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<PickerItem> = emptyList()
        private val locale = context.resources.configuration.locales[0]

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
            if (viewType == 0) {
                val header = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setTextColor(headerColor)
                    setTypeface(typeface, Typeface.BOLD)
                    letterSpacing = 0.12f
                    setPadding(rowPadH, context.dp(12), rowPadH, context.dp(UiDimens.SPACE_XS))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                return HeaderVH(header)
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val thumb = FrameLayout(context)
            row.addView(thumb, LinearLayout.LayoutParams(iconSize, iconSize))
            val name = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(context.dp(UiDimens.SPACE_MD), 0, context.dp(UiDimens.SPACE_SM), 0)
            }
            row.addView(
                name,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            val tag = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(UiDimens.ALPHA_MUTED)
                maxLines = 1
            }
            row.addView(
                tag,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
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

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = rows[position]
            if (holder is HeaderVH && item is PickerItem.Header) {
                val res = if (item.section == PickerItem.Header.Section.APPS) {
                    R.string.label_apps
                } else {
                    R.string.label_roms
                }
                holder.text.text = context.getString(res).uppercase(locale)
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
                        performPickHaptic(row)
                        cancelPendingSearch()
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
                    row.setOnClickListener {
                        performPickHaptic(row)
                        cancelPendingSearch()
                        onPick(SlotKey.rom(item.entry.id))
                    }
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
