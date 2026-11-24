package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtCache
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.useTabularNumerals

/** Result of moving highlight inside [AppPicker] — O(1) changed indices only. */
internal data class PickerHighlightMove(
    val previous: Int,
    val current: Int,
    val changedPositions: List<Int>,
)

/** Pure visual tokens for an [AppPicker] row in highlighted vs idle state. */
internal data class PickerRowVisual(
    val fillColor: Int,
    val effectiveSurface: Int,
    val strokeColor: Int,
    val strokeDp: Int,
    val accentBarColor: Int,
    val showAccentBar: Boolean,
    val boldTitle: Boolean,
)

/**
 * Pure, host-tested highlight-transition, row-visual, and result-count math for [AppPicker].
 * Guarantees highlight never rests on a section header and emits only the
 * changed positions (`<= 2`) so 6,384+ ROM lists never trigger O(N) rebinds.
 */
internal object AppPickerHighlight {

    /**
     * Max combined old + new picker row count for synchronous Myers DiffUtil
     * on the UI thread. Above this cap, search/filter dataset replacement swaps
     * directly (never used on NAV highlight moves, which notify <= 2 rows).
     */
    const val MAX_SYNC_DIFF_ITEMS: Int = 512

    /** Subtle 8% accent wash alpha blended over the card surface on highlighted rows. */
    internal const val HIGHLIGHT_WASH_ALPHA: Int = 0x14

    const val PAGE_STEP_SMALL: Int = 5
    const val PAGE_STEP_MEDIUM: Int = 10
    const val PAGE_STEP_LARGE: Int = 25

    /**
     * Precomputed WCAG 1.4.11 focus stroke, subtle 8% accent wash fill, leading
     * accent bar color, and bold title lift for [AppPicker] rows. Host-tested.
     */
    fun rowVisual(
        highlighted: Boolean,
        accent: Int,
        tokens: ThemeTokens,
    ): PickerRowVisual {
        val surface = ThemePack.cardFill(tokens)
        if (!highlighted) {
            return PickerRowVisual(
                fillColor = 0,
                effectiveSurface = surface,
                strokeColor = 0,
                strokeDp = 0,
                accentBarColor = 0,
                showAccentBar = false,
                boldTitle = false,
            )
        }
        val fill = SlotMenuNav.blendOverOpaque(
            (HIGHLIGHT_WASH_ALPHA shl 24) or (accent and 0x00FFFFFF),
            surface,
        )
        val focusStroke = OverlayPrimitives.accessibleFocusStroke(accent, fill)
        return PickerRowVisual(
            fillColor = fill,
            effectiveSurface = fill,
            strokeColor = focusStroke,
            strokeDp = 3,
            accentBarColor = focusStroke,
            showAccentBar = true,
            boldTitle = true,
        )
    }

    /**
     * Library-scaled L1/R1 page-jump stride (`5` for `<= 40`, `10` for `41..200`,
     * `25` for `> 200`) so large ROM libraries traverse quickly. Host-tested.
     */
    fun pageStep(selectableCount: Int): Int = when {
        selectableCount <= 40 -> PAGE_STEP_SMALL
        selectableCount <= 200 -> PAGE_STEP_MEDIUM
        else -> PAGE_STEP_LARGE
    }

    /**
     * Vertical top offset (px) for `LinearLayoutManager.scrollToPositionWithOffset`
     * centering a focused row inside [viewportH] with 1–2 rows of look-ahead context.
     * Host-tested.
     */
    fun centeredOffsetPx(viewportH: Int, rowH: Int): Int {
        if (viewportH <= 0) return 0
        val safeRowH = rowH.coerceAtLeast(0)
        return ((viewportH - safeRowH) / 2).coerceAtLeast(0)
    }

    fun shouldUseDiffUtil(oldSize: Int, newSize: Int): Boolean {
        val safeOld = oldSize.coerceAtLeast(0).toLong()
        val safeNew = newSize.coerceAtLeast(0).toLong()
        return safeOld + safeNew <= MAX_SYNC_DIFF_ITEMS.toLong()
    }

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
    // Set false when onPick launches directly (Deck.launchSlotKey emits LAUNCH_*).
    private val emitPickConfirmSound: Boolean = true,
    private val onPick: (String) -> Unit,
    private val onHide: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val app get() = activity.application as GhostGalleonApp
    private val hiddenRomIds get() = app.settings.hiddenRomIds
    // Lazy & app-cached so prebuilt drawer opens do zero ROM sorting and
    // slot/dock pickers reuse GhostGalleonApp.sortedPickerRoms() in O(1).
    private val sortedRoms: List<RomEntry> by lazy(LazyThreadSafetyMode.NONE) {
        val liveApp = app
        if (liveApp.romEntries === roms) {
            liveApp.sortedPickerRoms(hiddenRomIds)
        } else {
            PickerItems.sortedRoms(roms, hiddenRomIds)
        }
    }
    private var emptyQueryItems: List<PickerItem> =
        prebuiltItems ?: PickerItems.build(
            allEntries, roms, "",
            hiddenRomIds = hiddenRomIds,
            preSortedRoms = sortedRoms,
        )
    private var items: List<PickerItem> = emptyQueryItems
    // The highlight only ever rests on data rows, never section headers.
    private var highlight: Int = AppPickerHighlight.firstSelectable(items)
    private var selectableCount: Int = AppPickerHighlight.selectableCount(items)
    private var query = ""
    private var lastFilterQuery = ""
    private var lastFilterItems: List<PickerItem>? = null
    private var listViewport: FrameLayout? = null
    private var topScrollCue: View? = null
    private var bottomScrollCue: View? = null
    private var lastTopCueAlpha: Float = -1f
    private var lastBottomCueAlpha: Float = -1f
    private var recycler: RecyclerView? = null
    private var layoutManager: LinearLayoutManager? = null
    private var adapter: PickerAdapter? = null
    private var searchInput: EditText? = null
    private var emptyContainer: LinearLayout? = null
    private var emptyLabel: TextView? = null
    private var emptyClearBtn: TextView? = null
    private var countBadge: TextView? = null
    private var keycapFooter: LinearLayout? = null
    private var overlayView: FrameLayout? = null
    private var hideMenu: SlotMenu? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private fun cancelPendingSearch() {
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
    }

    private fun dismissImeNow() {
        DeckOverlays.hideIme(activity, searchInput ?: overlayView)
    }

    private fun clearSearch() {
        app.playSfx(SoundCue.FILTER_SELECT)
        searchInput?.setText("")
        cancelPendingSearch()
        applySearch("")
    }

    private fun applySearch(next: String) {
        val prevHighlight = highlight
        val prevKey = items.getOrNull(highlight)?.let { PickerItems.rowKey(it) }
        val queryChanged = AppPickerMatch.hasQueryChanged(query, next)
        query = next
        val nextTrimmed = query.trim()
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
        adapter?.setActiveQuery(nextTrimmed)
        val usedDiff = adapter?.submit(items, queryChanged = queryChanged) == true
        if (usedDiff) {
            AppPickerHighlight.positionsToRefreshAfterFilter(
                previousHighlight = prevHighlight,
                nextHighlight = highlight,
                newSize = items.size,
            ).forEach { pos ->
                adapter?.notifyItemChanged(pos, PAYLOAD_HIGHLIGHT)
            }
        }
        refreshEmpty()
        if (kept) {
            scrollToCentered(highlight)
        } else {
            recycler?.scrollToPosition(0)
        }
        recycler?.post { syncScrollCues() }
    }

    private fun applyScrollCueAlpha(cue: View?, target: Float, previous: Float): Float {
        if (cue == null || target == previous) return previous
        cue.animate().cancel()
        val durationMs = OverlayPrimitives.scrollCueTransitionMs(ValueAnimator.areAnimatorsEnabled())
        if (durationMs > 0L && cue.isAttachedToWindow && previous >= 0f) {
            cue.animate()
                .alpha(target)
                .setDuration(durationMs)
                .setInterpolator(CUE_INTERPOLATOR)
                .start()
        } else {
            cue.alpha = target
        }
        return target
    }

    private fun syncScrollCues() {
        val rv = recycler ?: return
        val visible = rv.visibility == View.VISIBLE && listViewport?.visibility == View.VISIBLE
        val canUp = visible && rv.canScrollVertically(-1)
        val canDown = visible && rv.canScrollVertically(1)
        val (topAlpha, bottomAlpha) = OverlayPrimitives.scrollCueAlphas(canUp, canDown)
        lastTopCueAlpha = applyScrollCueAlpha(topScrollCue, topAlpha, lastTopCueAlpha)
        lastBottomCueAlpha = applyScrollCueAlpha(bottomScrollCue, bottomAlpha, lastBottomCueAlpha)
        OverlayPrimitives.updateKeycapFooterScrollable(keycapFooter, isScrollable = canUp || canDown)
    }

    private fun scrollToCentered(position: Int) {
        val list = recycler ?: return
        val lm = layoutManager ?: return
        val viewportH = list.height - list.paddingTop - list.paddingBottom
        val sampleRowH = lm.findViewByPosition(position)?.height
            ?: list.getChildAt(0)?.height
            ?: activity.dp(60)
        val offset = AppPickerHighlight.centeredOffsetPx(viewportH, sampleRowH)
        lm.scrollToPositionWithOffset(position, offset)
    }

    val view: View by lazy {
        val context: Context = activity
        val tokens = ThemePack.resolve(app.settings)
        val surfaceColor = TileBackgrounds.surfaceColor(context)
        val sunkenColor = TileBackgrounds.surfaceSunken(context)
        val accentText = ThemePack.accentTextColor(accentColor, sunkenColor)
        val matchTitleColor = AppPickerMatch.titleHighlightColor(accentColor, tokens)
        val matchBadgeColor = AppPickerMatch.badgeHighlightColor(accentColor, tokens)

        val overlay = OverlayPrimitives.modalBackdrop(context) {
            app.playSfx(SoundCue.MODAL_CLOSE)
            dismissImeNow()
            cancelPendingSearch()
            onClose()
        }.apply {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    dismissImeNow()
                    cancelPendingSearch()
                    topScrollCue?.animate()?.cancel()
                    bottomScrollCue?.animate()?.cancel()
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
        val resolvedTitle = OverlayPrimitives.resolvePaneTitle(
            explicitTitle = title,
            fallbackTitle = context.getString(R.string.action_add_to_grid),
        )
        OverlayPrimitives.bindModalPaneTitle(overlay, resolvedTitle)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(
            TextView(context).apply {
                text = resolvedTitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                OverlayPrimitives.markHeading(this)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val badge = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(accentText)
            setTypeface(typeface, Typeface.BOLD)
            useTabularNumerals()
            maxLines = 1
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 10)
            setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
            OverlayPrimitives.markPoliteLiveRegion(this)
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
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.strokePx(1),
            ).apply {
                topMargin = context.dp(10)
                bottomMargin = context.dp(10)
            },
        )

        // Reactive sunken search bar with persistent GradientDrawable focus ring & 44dp Clear.
        val searchBoxBg = GradientDrawable().apply {
            setColor(sunkenColor)
            cornerRadius = context.dpF(24)
        }
        var searchFocused = false
        var searchDraftText = ""
        fun syncSearchBoxStroke() {
            val state = AppPickerMatch.searchBarStrokeState(searchFocused, searchDraftText)
            val (strokeColor, strokeDp) = AppPickerMatch.searchBarStroke(state, accentColor, tokens)
            searchBoxBg.setStroke(context.strokePx(strokeDp), strokeColor)
        }
        syncSearchBoxStroke()

        val searchBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = searchBoxBg
            minimumHeight = context.dp(UiDimens.TOUCH_MIN_DP)
            setPaddingRelative(context.dp(14), context.dp(2), context.dp(4), context.dp(2))
        }
        searchBox.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_MUTED)
                scaleType = ImageView.ScaleType.FIT_CENTER
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
            imeOptions = EditorInfo.IME_ACTION_GO
            background = null
            setPadding(0, context.dp(6), 0, context.dp(6))
            setOnFocusChangeListener { _, hasFocus ->
                searchFocused = hasFocus
                syncSearchBoxStroke()
            }
            setOnEditorActionListener { _, actionId, event ->
                if (AppPickerMatch.isSearchLaunchAction(actionId, event?.keyCode, event?.action)) {
                    if (pendingSearch != null) {
                        cancelPendingSearch()
                        applySearch(text?.toString().orEmpty())
                    }
                    confirmHighlighted()
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val next = s?.toString().orEmpty()
                    searchDraftText = next
                    clearBtn.visibility = if (AppPickerMatch.clearButtonVisible(next)) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    syncSearchBoxStroke()
                    // Debounce: typing in a 6k-ROM library re-filters only,
                    // never re-sorts (see preSortedRoms).
                    cancelPendingSearch()
                    val run = Runnable { applySearch(next) }
                    pendingSearch = run
                    searchHandler.postDelayed(run, SEARCH_DEBOUNCE_MS)
                }
            })
        }
        searchInput = search
        searchBox.addView(
            search,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val clearTouchPx = context.dp(UiDimens.TOUCH_MIN_DP)
        clearBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_SECONDARY)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = context.getString(R.string.action_clear)
            isFocusable = false
            isClickable = true
            visibility = View.GONE
            background = OverlayPrimitives.roundedRipple(
                context = context,
                content = null,
                cornerRadiusDp = 22,
            )
            val pad = context.dp(12)
            setPadding(pad, pad, pad, pad)
            setOnClickListener { clearSearch() }
        }
        searchBox.addView(
            clearBtn,
            LinearLayout.LayoutParams(clearTouchPx, clearTouchPx).apply {
                marginStart = context.dp(2)
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
            sunkenColor = sunkenColor,
            hairlineColor = TileBackgrounds.hairlineDivider(context),
            matchTitleColor = matchTitleColor,
            matchBadgeColor = matchBadgeColor,
            rowCornerRadiusDp = tokens.cardRadiusDp,
            highlightedVisual = AppPickerHighlight.rowVisual(true, accentColor, tokens),
        )
        val list = RecyclerView(context).apply {
            val lm = LinearLayoutManager(context)
            layoutManager = lm
            adapter = pickerAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(
                OverlayPrimitives.fadingEdgeLengthPx(context.resources.displayMetrics.density),
            )
            clipToPadding = false
            setPadding(0, 0, 0, context.dp(UiDimens.SPACE_XS))
        }
        val scrollCueColor = OverlayPrimitives.scrollCueTint(accentColor, surfaceColor)
        val topCue = OverlayPrimitives.scrollCueBar(
            context = context,
            color = scrollCueColor,
            isTop = true,
            surfaceColor = surfaceColor,
        )
        val bottomCue = OverlayPrimitives.scrollCueBar(
            context = context,
            color = scrollCueColor,
            isTop = false,
            surfaceColor = surfaceColor,
        )
        topScrollCue = topCue
        bottomScrollCue = bottomCue
        val cueHeightPx = context.dp(OverlayPrimitives.SCROLL_CUE_HEIGHT_DP)
        val viewport = FrameLayout(context).apply {
            addView(
                list,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                topCue,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cueHeightPx,
                    Gravity.TOP,
                ),
            )
            addView(
                bottomCue,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cueHeightPx,
                    Gravity.BOTTOM,
                ),
            )
        }
        listViewport = viewport
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                syncScrollCues()
            }
        })
        // Row taps are handled by each row's own OnClickListener: the
        // long-press listener (hide menu) makes rows consume touches.
        val emptyBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                context.dp(UiDimens.SPACE_LG),
                context.dp(UiDimens.SPACE_LG),
                context.dp(UiDimens.SPACE_LG),
                context.dp(UiDimens.SPACE_LG),
            )
        }
        val medallionSize = context.dp(AppPickerMatch.EMPTY_MEDALLION_SIZE_DP)
        val medallionIconSize = context.dp(AppPickerMatch.EMPTY_MEDALLION_ICON_DP)
        val emptyMedallion = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(sunkenColor)
                setStroke(
                    context.strokePx(1),
                    TileBackgrounds.hairlineDivider(context),
                    context.dpF(4),
                    context.dpF(3),
                )
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_search)
                    imageTintList = ColorStateList.valueOf(accentText)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(medallionIconSize, medallionIconSize, Gravity.CENTER),
            )
        }
        emptyBox.addView(
            emptyMedallion,
            LinearLayout.LayoutParams(medallionSize, medallionSize).apply {
                bottomMargin = context.dp(UiDimens.SPACE_MD)
            },
        )
        val emptyText = TextView(context).apply {
            setText(R.string.browse_no_matches_short)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            OverlayPrimitives.markPoliteLiveRegion(this)
        }
        emptyBox.addView(
            emptyText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val clearPillBg = GradientDrawable().apply {
            setColor(accentColor)
            cornerRadius = context.dpF(22)
            setStroke(context.strokePx(2), AppPickerMatch.emptyClearFocusRing(accentColor))
        }
        val clearEmptyAction = TextView(context).apply {
            setText(R.string.action_clear)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(QuickPanelNav.accessibleOnAccent(accentColor))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minimumHeight = context.dp(AppPickerMatch.EMPTY_CLEAR_MIN_HEIGHT_DP)
            minWidth = context.dp(120)
            background = OverlayPrimitives.roundedRipple(
                context = context,
                content = clearPillBg,
                cornerRadiusDp = 22,
                rippleColor = QuickPanelNav.tileRippleColor(
                    QuickPanelNav.TileRole.PRIMARY,
                    accentColor,
                ),
            )
            setPadding(context.dp(22), context.dp(10), context.dp(22), context.dp(10))
            isClickable = true
            visibility = View.GONE
            setOnClickListener { clearSearch() }
        }
        emptyBox.addView(
            clearEmptyAction,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(UiDimens.SPACE_MD) },
        )
        card.addView(
            viewport,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        card.addView(
            emptyBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        val cardWidthDp = (cardWidth / context.resources.displayMetrics.density.coerceAtLeast(0.1f)).toInt()
        val footer = OverlayPrimitives.modalKeycapFooter(
            context = context,
            accent = accentColor,
            tokens = tokens,
            cues = listOf(
                OverlayPrimitives.FooterCue(
                    keycap = OverlayPrimitives.KEYCAP_CONFIRM,
                    labelRes = if (emitPickConfirmSound) R.string.label_select else R.string.action_open,
                ),
                OverlayPrimitives.FooterCue(
                    keycap = OverlayPrimitives.KEYCAP_PAGE,
                    scrollOnly = true,
                    keycapSymbol = "\u2195",
                ),
                OverlayPrimitives.FooterCue(OverlayPrimitives.KEYCAP_CLOSE, R.string.action_close),
            ),
            compactWidthDp = cardWidthDp,
            isScrollable = items.size > 6,
        )
        keycapFooter = footer
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncScrollCues()
        }
        card.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        recycler = list
        layoutManager = list.layoutManager as LinearLayoutManager
        adapter = pickerAdapter
        emptyContainer = emptyBox
        emptyLabel = emptyText
        emptyClearBtn = clearEmptyAction
        pickerAdapter.submit(items)
        refreshEmpty()
        overlayView = overlay
        val metrics = context.resources.displayMetrics
        val topMarginPx = context.dp(UiDimens.SPACE_LG)
        val minCardHeightPx = context.dp(220)
        // Top-anchored. Slot pickers stay short so the IME does not cover
        // results; the swipe-up drawer uses a taller card and shrinks smoothly
        // above the soft keyboard if the user taps search.
        val cardParams = FrameLayout.LayoutParams(
            cardWidth,
            OverlayPrimitives.imeAwareCardHeightPx(
                screenHeightPx = metrics.heightPixels,
                heightFrac = heightFraction,
                bottomInsetPx = 0,
                topMarginPx = topMarginPx,
                minHeightPx = minCardHeightPx,
            ),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            topMargin = topMarginPx
        }
        overlay.addView(card, cardParams)
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val safeBottom = OverlayPrimitives.safeBottomInsetPx(imeBottom, navBottom)
            val nextHeight = OverlayPrimitives.imeAwareCardHeightPx(
                screenHeightPx = context.resources.displayMetrics.heightPixels,
                heightFrac = heightFraction,
                bottomInsetPx = safeBottom,
                topMarginPx = topMarginPx,
                minHeightPx = minCardHeightPx,
            )
            if (cardParams.height != nextHeight) {
                cardParams.height = nextHeight
                card.layoutParams = cardParams
            }
            insets
        }
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
        app.playSfx(SoundCue.MODAL_OPEN)
        overlay
    }

    fun handleAction(action: Action): Boolean {
        hideMenu?.let { return it.handleAction(action) }
        when (action) {
            Action.NAV_UP -> moveHighlight(-1)
            Action.NAV_DOWN -> moveHighlight(1)
            Action.PAGE_PREV -> moveHighlight(-AppPickerHighlight.pageStep(selectableCount))
            Action.PAGE_NEXT -> moveHighlight(AppPickerHighlight.pageStep(selectableCount))
            Action.CONFIRM -> confirmHighlighted()
            Action.BACK -> {
                app.playSfx(SoundCue.MODAL_CLOSE)
                dismissImeNow()
                cancelPendingSearch()
                onClose()
            }
            else -> {}
        }
        return true // the modal swallows every action while open
    }

    private fun confirmHighlighted() {
        when (val item = items.getOrNull(highlight)) {
            is PickerItem.App -> {
                if (emitPickConfirmSound) app.playSfx(SoundCue.CONFIRM)
                dismissImeNow()
                cancelPendingSearch()
                onPick(item.entry.packageName)
            }
            is PickerItem.Rom -> {
                if (emitPickConfirmSound) app.playSfx(SoundCue.CONFIRM)
                dismissImeNow()
                cancelPendingSearch()
                onPick(SlotKey.rom(item.entry.id))
            }
            else -> {
                if (AppPickerMatch.canClearFromEmptyState(
                        AppPickerHighlight.selectableCount(items),
                        query,
                    )
                ) {
                    clearSearch()
                }
            }
        }
    }

    // The highlight only ever rests on data rows: section headers are skipped
    // in both directions, and only the previous + next positions are notified.
    private fun moveHighlight(delta: Int) {
        val move = AppPickerHighlight.move(items, highlight, delta)
        val ev = OverlayAudioLogic.appPickerMoveEvent(move.changedPositions.size)
        app.playSfx(ev.cue, pan = ev.pan)
        if (move.changedPositions.isEmpty()) return
        highlight = move.current
        move.changedPositions.forEach { pos ->
            if (pos in items.indices) {
                adapter?.notifyItemChanged(pos, PAYLOAD_HIGHLIGHT)
            }
        }
        val lm = layoutManager ?: return
        val firstComplete = lm.findFirstCompletelyVisibleItemPosition()
        val lastComplete = lm.findLastCompletelyVisibleItemPosition()
        val nearEdge = firstComplete == RecyclerView.NO_POSITION ||
            move.current <= firstComplete ||
            move.current >= lastComplete
        if (nearEdge) {
            scrollToCentered(move.current)
        }
    }

    private fun refreshEmpty() {
        val selectable = AppPickerHighlight.selectableCount(items)
        selectableCount = selectable
        val none = selectable == 0
        emptyContainer?.visibility = if (none) View.VISIBLE else View.GONE
        listViewport?.visibility = if (none) View.GONE else View.VISIBLE
        recycler?.visibility = if (none) View.GONE else View.VISIBLE
        if (none) {
            syncScrollCues()
        } else {
            recycler?.post { syncScrollCues() }
        }
        if (none) {
            val trimmed = query.trim()
            emptyLabel?.text = if (trimmed.isNotEmpty()) {
                activity.getString(R.string.browse_no_matches, trimmed)
            } else {
                activity.getString(R.string.browse_no_matches_short)
            }
            emptyClearBtn?.visibility = if (AppPickerMatch.canClearFromEmptyState(selectable, query)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
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
                dismissImeNow()
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
        private const val PAYLOAD_QUERY = "query"
        private const val ROW_RIPPLE_COLOR = 0x26FFFFFF
        private val CUE_INTERPOLATOR = DecelerateInterpolator()
    }

    private fun styledMatchText(text: String, activeQuery: String, color: Int): CharSequence {
        val spans = AppPickerMatch.findMatchSpans(text, activeQuery)
        if (spans.isEmpty()) return text
        return SpannableString(text).apply {
            spans.forEach { span ->
                setSpan(
                    ForegroundColorSpan(color),
                    span.start,
                    span.endExclusive,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    span.start,
                    span.endExclusive,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    // Row views are truly recycled (the ROM section can hold 6k–20k rows):
    // persistent app icon + ROM platform tile children toggle visibility in
    // place with zero thumb.removeAllViews() and reusable row/ripple drawables.
    private class RowHolder(
        val accentBar: View,
        val thumb: FrameLayout,
        val appIcon: ImageView,
        val romTile: TextView,
        val appThumbBg: Drawable,
        val name: TextView,
        val tag: TextView,
        val tagBg: GradientDrawable,
    )

    private inner class HeaderVH(val text: TextView) : RecyclerView.ViewHolder(text)

    private inner class RowVH(
        val row: LinearLayout,
        val rowBg: GradientDrawable,
        val cells: RowHolder,
    ) : RecyclerView.ViewHolder(row) {
        var boundItem: PickerItem? = null
        var boundIsApp: Boolean? = null
        var boundThumbKey: String? = null
        var boundPlatformId: String? = null
        var paintedHighlighted: Boolean? = null
    }

    private inner class PickerAdapter(
        private val context: Context,
        private val iconSize: Int,
        private val rowPadH: Int,
        private val rowPadV: Int,
        private val headerColor: Int,
        private val sunkenColor: Int,
        private val hairlineColor: Int,
        private val matchTitleColor: Int,
        private val matchBadgeColor: Int,
        private val rowCornerRadiusDp: Int,
        private val highlightedVisual: PickerRowVisual,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<PickerItem> = emptyList()
        private var sectionCounts: PickerSectionCounts = PickerSectionCounts(0, 0)
        private var activeQuery: String = ""
        private val locale = context.resources.configuration.locales[0]
        private val iconPadPx = context.dp(2)
        private val innerIconSizePx = (iconSize - iconPadPx * 2).coerceAtLeast(1)
        private val thumbClipRadiusPx = context.dpF(10)
        private val rowCornerRadiusPx = context.dpF(rowCornerRadiusDp)
        private val selectedStrokePx = context.strokePx(highlightedVisual.strokeDp)
        private val tagStrokePx = context.strokePx(1)
        private val tagCornerRadiusPx = context.dpF(8)
        private val barWidthPx = context.dp(3)
        private val barHeightPx = context.dp(24)
        private val barGapPx = context.dp(8)
        private val rowStartPadPx = context.dp(8)

        init {
            setHasStableIds(true)
        }

        fun setActiveQuery(trimmedQuery: String) {
            activeQuery = trimmedQuery
        }

        /**
         * Replace adapter rows. Uses Myers DiffUtil only when `old.size + next.size`
         * stays within [AppPickerHighlight.MAX_SYNC_DIFF_ITEMS]; larger dataset
         * filter swaps use `notifyDataSetChanged()` (`itemAnimator == null`).
         * @return true when DiffUtil dispatched incremental updates.
         */
        fun submit(next: List<PickerItem>, queryChanged: Boolean = false): Boolean {
            val old = rows
            val prevCounts = sectionCounts
            val nextCounts = AppPickerMatch.sectionCounts(next)
            val countsChanged = prevCounts != nextCounts
            sectionCounts = nextCounts
            if (AppPickerHighlight.shouldUseDiffUtil(old.size, next.size)) {
                val diff = DiffUtil.calculateDiff(
                    PickerDiff(old, next, queryChanged || countsChanged),
                    false,
                )
                rows = next
                diff.dispatchUpdatesTo(this)
                return true
            }
            rows = next
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
            return false
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
                    useTabularNumerals()
                    letterSpacing = 0.12f
                    setPadding(rowPadH, context.dp(12), rowPadH, context.dp(UiDimens.SPACE_XS))
                    OverlayPrimitives.markHeading(this)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                return HeaderVH(header)
            }
            val rowBg = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = rowCornerRadiusPx
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = OverlayPrimitives.roundedRipple(
                    context = context,
                    content = rowBg,
                    cornerRadiusDp = rowCornerRadiusDp,
                    rippleColor = ROW_RIPPLE_COLOR,
                )
                setPaddingRelative(rowStartPadPx, rowPadV, rowPadH, rowPadV)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val accentBar = View(context).apply {
                background = TileBackgrounds.activeAccentBar(
                    context = context,
                    accent = highlightedVisual.accentBarColor,
                    cornerRadiusDp = 2,
                )
                visibility = View.INVISIBLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(
                accentBar,
                LinearLayout.LayoutParams(barWidthPx, barHeightPx).apply {
                    marginEnd = barGapPx
                },
            )
            val appThumbBg = TileBackgrounds.sunkenField(context, cornerRadiusDp = 10)
            val appIcon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
            }
            val romTile = PlatformTile.view(context, "", cornerRadiusDp = 12).apply {
                setTag(R.id.platform_tile_id, null)
                visibility = View.GONE
            }
            val thumb = FrameLayout(context).apply {
                outlineProvider = OutlineCache.roundedRect(thumbClipRadiusPx)
                clipToOutline = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(
                    appIcon,
                    FrameLayout.LayoutParams(innerIconSizePx, innerIconSizePx, Gravity.CENTER),
                )
                addView(
                    romTile,
                    FrameLayout.LayoutParams(iconSize, iconSize),
                )
            }
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
            val tagBg = GradientDrawable().apply {
                setColor(sunkenColor)
                cornerRadius = tagCornerRadiusPx
                setStroke(tagStrokePx, hairlineColor)
            }
            val tag = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(UiDimens.ALPHA_SECONDARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = tagBg
                setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
                visibility = View.GONE
            }
            row.addView(
                tag,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            val holder = RowVH(
                row = row,
                rowBg = rowBg,
                cells = RowHolder(accentBar, thumb, appIcon, romTile, appThumbBg, name, tag, tagBg),
            )
            row.setOnClickListener {
                when (val bound = holder.boundItem) {
                    is PickerItem.App -> {
                        performPickHaptic(row)
                        if (emitPickConfirmSound) app.playSfx(SoundCue.CONFIRM)
                        dismissImeNow()
                        cancelPendingSearch()
                        onPick(bound.entry.packageName)
                    }
                    is PickerItem.Rom -> {
                        performPickHaptic(row)
                        if (emitPickConfirmSound) app.playSfx(SoundCue.CONFIRM)
                        dismissImeNow()
                        cancelPendingSearch()
                        onPick(SlotKey.rom(bound.entry.id))
                    }
                    else -> Unit
                }
            }
            row.setOnLongClickListener {
                val bound = holder.boundItem as? PickerItem.App ?: return@setOnLongClickListener false
                openHideMenu(bound.entry)
                true
            }
            return holder
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
            payloads: MutableList<Any>,
        ) {
            if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_HIGHLIGHT || it == PAYLOAD_QUERY }) {
                if (holder is HeaderVH) {
                    val item = rows.getOrNull(position) as? PickerItem.Header
                    if (item != null && payloads.contains(PAYLOAD_QUERY)) {
                        bindHeader(holder, item)
                    }
                    return
                }
                if (holder is RowVH) {
                    if (payloads.contains(PAYLOAD_QUERY)) {
                        bindRowText(holder, rows[position])
                    }
                    if (payloads.contains(PAYLOAD_HIGHLIGHT)) {
                        paintHighlight(holder, position)
                    }
                }
                return
            }
            onBindViewHolder(holder, position)
        }

        private fun bindHeader(holder: HeaderVH, item: PickerItem.Header) {
            val res = if (item.section == PickerItem.Header.Section.APPS) {
                R.string.label_apps
            } else {
                R.string.label_roms
            }
            val titleUpper = context.getString(res).uppercase(locale)
            val count = sectionCounts.countFor(item.section)
            holder.text.text = context.getString(
                R.string.format_dot_pair,
                titleUpper,
                count.toString(),
            )
            holder.text.background = null
        }

        private fun bindRowText(holder: RowVH, item: PickerItem) {
            val cells = holder.cells
            when (item) {
                is PickerItem.App -> {
                    cells.name.text = styledMatchText(item.entry.label, activeQuery, matchTitleColor)
                    cells.tag.text = ""
                    cells.tag.visibility = View.GONE
                    holder.row.contentDescription = item.entry.label
                }
                is PickerItem.Rom -> {
                    val platformLabel = Platforms.byId(item.entry.platformId)?.displayName
                        ?: item.entry.platformId
                    cells.name.text = styledMatchText(item.entry.name, activeQuery, matchTitleColor)
                    cells.tag.text = styledMatchText(platformLabel, activeQuery, matchBadgeColor)
                    cells.tag.visibility = View.VISIBLE
                    holder.row.contentDescription = context.getString(
                        R.string.format_dot_pair,
                        item.entry.name,
                        platformLabel,
                    )
                }
                is PickerItem.Header -> Unit
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = rows[position]
            if (holder is HeaderVH && item is PickerItem.Header) {
                bindHeader(holder, item)
                return
            }
            if (holder !is RowVH) return
            holder.boundItem = item
            val row = holder.row
            val cells = holder.cells
            when (item) {
                is PickerItem.App -> {
                    if (holder.boundIsApp != true) {
                        cells.romTile.visibility = View.GONE
                        cells.appIcon.visibility = View.VISIBLE
                        cells.thumb.background = cells.appThumbBg
                        holder.boundIsApp = true
                    }
                    val bindKey = "a:${item.entry.packageName}"
                    if (holder.boundThumbKey != bindKey) {
                        CustomIcon.bind(
                            cells.appIcon,
                            iconLoader,
                            app.artCache,
                            app.settings,
                            item.entry.packageName,
                            innerIconSizePx,
                        )
                        holder.boundThumbKey = bindKey
                    }
                    bindRowText(holder, item)
                    row.isLongClickable = true
                }
                is PickerItem.Rom -> {
                    if (holder.boundIsApp != false) {
                        if (holder.boundIsApp == true) {
                            ArtCache.dropDisplayed(cells.appIcon)
                            cells.appIcon.setTag(R.id.custom_icon_key, null)
                            cells.appIcon.setImageDrawable(null)
                        }
                        cells.appIcon.visibility = View.GONE
                        cells.romTile.visibility = View.VISIBLE
                        cells.thumb.background = null
                        holder.boundIsApp = false
                    }
                    val pid = item.entry.platformId
                    PlatformTile.restyle(cells.romTile, context, pid, cornerRadiusDp = 12)
                    if (holder.boundPlatformId != pid) {
                        holder.boundPlatformId = pid
                        cells.tagBg.setStroke(
                            tagStrokePx,
                            AppPickerMatch.platformPillBorderColor(PlatformTile.colorFor(pid)),
                        )
                    }
                    holder.boundThumbKey = "r:$pid"
                    bindRowText(holder, item)
                    row.isLongClickable = false
                }
                is PickerItem.Header -> {}
            }
            paintHighlight(holder, position)
        }

        private fun paintHighlight(holder: RowVH, position: Int) {
            val isHighlighted = position == highlight
            if (holder.paintedHighlighted == isHighlighted) return
            holder.paintedHighlighted = isHighlighted
            holder.row.isSelected = isHighlighted
            holder.cells.accentBar.visibility = if (isHighlighted) View.VISIBLE else View.INVISIBLE
            holder.cells.name.setTypeface(null, if (isHighlighted) Typeface.BOLD else Typeface.NORMAL)
            if (isHighlighted) {
                holder.rowBg.setColor(highlightedVisual.fillColor)
                holder.rowBg.setStroke(selectedStrokePx, highlightedVisual.strokeColor)
            } else {
                holder.rowBg.setColor(Color.TRANSPARENT)
                holder.rowBg.setStroke(0, Color.TRANSPARENT)
            }
        }
    }

    private class PickerDiff(
        private val old: List<PickerItem>,
        private val next: List<PickerItem>,
        private val queryChanged: Boolean = false,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = next.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            PickerItems.itemId(old[oldItemPosition]) == PickerItems.itemId(next[newItemPosition])
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            !queryChanged && old[oldItemPosition] == next[newItemPosition]
        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
            if (queryChanged && old[oldItemPosition] == next[newItemPosition]) {
                PAYLOAD_QUERY
            } else {
                null
            }
    }
}
