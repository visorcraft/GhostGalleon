package com.visorcraft.ghostgalleon.ui.deck

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.rom.LaunchReason
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.Folders
import com.visorcraft.ghostgalleon.settings.GridSlots
import com.visorcraft.ghostgalleon.settings.RomNames
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.BaseDeckActivity
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx

// The curated grid: renders settings.gridSlots — ordered, null = blank
// ("+") cell — padded up to a whole number of rows (GridSlots.paddedCount)
// so an incomplete row never shows. Padded cells are ordinary blank slots:
// focusable, they open the app picker, and filling one extends the stored
// list. Blank slots open the app picker; filled slots launch on confirm
// and offer Move/Remove on long-press.
//
// settings.gridDirection picks the viewport axis: "vertical" (default) is
// one scrolling GridView; "horizontal" (3DS-style) lays fixed-size page
// panels left to right in a page-aligned horizontal scroller. Slot
// indices, GridNavigation math, and page-dot semantics are identical in
// both — only the scroll axis changes.
class GridDeck(
    private val activity: AppCompatActivity,
    private val state: DeckState,
    private val settings: Settings,
    private val library: AppLibrary,
    private val iconLoader: AppIconLoader,
    private val roms: List<RomEntry>,
    private val showStatusPill: Boolean = true,
) : Deck {

    // Rendered slot count: the stored list padded up to complete rows.
    // Navigation, page dots, and ensure-visible all use this padded count.
    private val slotCount get() =
        GridSlots.paddedCount(settings.gridSlots.size, settings.gridColumns)
    private val isHorizontal get() = settings.gridDirection == "horizontal"
    private val nav get() = GridNavigation(
        slotCount, settings.gridColumns, pageGeometry?.visibleRows ?: 1)
    // Slot tiles resolve through the FULL cache: a hidden app that already
    // occupies a slot stays rendered and launchable there (hiding only
    // removes it from the picker and other all-apps lists).
    private val visibleByPkg by lazy {
        library.all(settings).associateBy { it.packageName }
    }
    // ROM slots hold "rom:<entry id>" values (SlotKey); resolve them against
    // the ROM index snapshot. A ROM that drops out of the library renders as
    // a dimmed "Missing" tile but keeps its slot value (removable via
    // long-press).
    private val romByKey by lazy { roms.associateBy { SlotKey.rom(it.id) } }

    // Move mode: a lifted tile swaps contents slot-by-slot until dropped
    // (saved to settings) or cancelled (working copy discarded).
    private var moveIndex: Int? = null
    private var moveWorking: MutableList<String?>? = null
    private var pulseAnimator: AnimatorSet? = null

    // Modals (at most one at a time).
    private var slotMenu: SlotMenu? = null
    private var picker: AppPicker? = null
    private var folderPanel: FolderPanel? = null
    private var rootView: FrameLayout? = null

    // Live view state captured by primaryView() so updateSelection() can
    // move the selection ring without rebuilding the whole view tree.
    private var gridView: GridView? = null
    private var gridAdapter: GridAdapter? = null
    private var pagerScroll: HorizontalScrollView? = null
    private var pageGrids: List<GridView> = emptyList()
    private var pageDotsView: LinearLayout? = null
    private var pagePillViews: List<View> = emptyList()
    private var pagePrevButton: ImageView? = null
    private var pageNextButton: ImageView? = null
    private val pageDotAnimators = mutableListOf<ValueAnimator>()
    private var feedbackAnimator: AnimatorSet? = null
    private var lastPaintedPage: Int = -1
    private var pageGeometry: PageGeometry? = null
    private var dockBar: DockBar? = null
    private var hintView: TextView? = null

    private val dockMove = DockMoveState()

    private val dockNav get() = DockNavigation(
        DockSlots.visibleCount(dockMove.working ?: settings.dockSlots),
        slotCount, settings.gridColumns)

    private class PageGeometry(
        val cellsPerPage: Int,
        val visibleRows: Int,
        val cellHeight: Int,
        val spacing: Int,
        val paddingV: Int,
    )

    // During move mode the working copy is what the grid renders.
    private fun slotPackages(): List<String?> = moveWorking ?: settings.gridSlots

    private fun selectedIndex(): Int =
        (moveIndex ?: state.selectedSlot).coerceIn(0, (slotCount - 1).coerceAtLeast(0))

    override fun updateSelection(): Boolean {
        pageGeometry ?: return false
        if (isHorizontal) {
            pagerScroll ?: return false
        } else {
            gridView ?: return false
            gridAdapter ?: return false
        }
        // Keep the selected tile on screen, then move the ring/scale to it.
        ensureSelectionVisible()
        rebindVisibleCells()
        updatePageDots()
        // Dock focus is a selection change too: repaint the dock ring (and
        // the lifted tile's pulse during a dock move) and switch the hint
        // bar between grid and dock actions.
        dockBar?.updateFocus(state.dockSlot, dockMove.index)
        hintView?.text = if (dockMove.active) {
            HintBar.moveText(activity)
        } else {
            HintBar.textFor(activity, state.dockSlot != null)
        }
        return true
    }

    // Moves the ring/scale to the new selection by updating the visible
    // cells in place. Deliberately NOT adapter.notifyDataSetChanged():
    // every notify arms AdapterView.rememberSyncState, and because the grid
    // auto-resurrects an internal selection at position 0 (checkFocus), the
    // data-change layout's LAYOUT_SYNC restores position 0's stale top —
    // reverting any scroll that detached row 0 and stranding our selection
    // off-screen. Cells (re)attached while scrolling are bound with the
    // current selection by getView, so only the already-visible cells need
    // this pass. In move mode the cell CONTENTS may also have swapped, so
    // visible cells are fully re-populated.
    private fun rebindVisibleCells(repopulate: Boolean = moveIndex != null) {
        cancelPulseAnimator()
        val selected = selectedIndex()
        forEachAttachedCell { frame, position, adapter ->
            if (repopulate) {
                adapter.populateFrame(frame, position)
            } else {
                applySelectionVisuals(frame, position, position == selected)
            }
            if (position == moveIndex) startPulse(frame)
        }
    }

    private fun cancelPulseAnimator() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun cancelPageDotAnimators() {
        pageDotAnimators.forEach { it.cancel() }
        pageDotAnimators.clear()
    }

    private fun cancelFeedbackAnimator() {
        feedbackAnimator?.cancel()
        feedbackAnimator = null
        gridView?.translationY = 0f
        pagerScroll?.translationX = 0f
        pageDotsView?.translationX = 0f
        pagePrevButton?.apply {
            scaleX = 1f
            scaleY = 1f
        }
        pageNextButton?.apply {
            scaleX = 1f
            scaleY = 1f
        }
    }

    private fun cancelAllAnimations() {
        cancelPulseAnimator()
        cancelPageDotAnimators()
        cancelFeedbackAnimator()
        pagePrevButton?.animate()?.cancel()
        pageNextButton?.animate()?.cancel()
        forEachAttachedCell { frame, _, _ ->
            frame.animate().cancel()
            frame.translationY = 0f
            frame.alpha = 1f
        }
    }

    private fun triggerScrollFeedback(cue: GridScrollFeedback.Cue) {
        when (cue) {
            GridScrollFeedback.Cue.None -> return
            is GridScrollFeedback.Cue.PageFlipped -> {
                val strip = pageDotsView?.takeIf { it.visibility == View.VISIBLE } ?: return
                cancelFeedbackAnimator()
                val dir = if (cue.direction >= 0) 1f else -1f
                val nudgePx = strip.dpF(GridScrollFeedback.STRIP_NUDGE_DP) * dir
                val stripAnim = ObjectAnimator.ofFloat(strip, View.TRANSLATION_X, 0f, nudgePx, 0f)
                val chevron = if (cue.direction >= 0) pageNextButton else pagePrevButton
                val anims = mutableListOf<android.animation.Animator>(stripAnim)
                if (chevron != null) {
                    anims.add(
                        ObjectAnimator.ofFloat(
                            chevron, View.SCALE_X,
                            1f, GridScrollFeedback.CHEVRON_PULSE_SCALE, 1f,
                        ),
                    )
                    anims.add(
                        ObjectAnimator.ofFloat(
                            chevron, View.SCALE_Y,
                            1f, GridScrollFeedback.CHEVRON_PULSE_SCALE, 1f,
                        ),
                    )
                }
                feedbackAnimator = AnimatorSet().apply {
                    playTogether(anims)
                    duration = GridScrollFeedback.BUMP_OUT_MS + GridScrollFeedback.BUMP_BACK_MS
                    interpolator = FOCUS_INTERPOLATOR
                    start()
                }
            }
            is GridScrollFeedback.Cue.EdgeBump -> {
                cancelFeedbackAnimator()
                val dir = if (cue.direction >= 0) 1f else -1f
                val anims = mutableListOf<android.animation.Animator>()
                val target: View? = if (isHorizontal) pagerScroll else gridView
                if (target != null) {
                    if (isHorizontal) {
                        val dx = -dir * target.dpF(GridScrollFeedback.EDGE_BUMP_HORIZONTAL_DP)
                        anims.add(ObjectAnimator.ofFloat(target, View.TRANSLATION_X, 0f, dx, 0f))
                    } else {
                        val dy = -dir * target.dpF(GridScrollFeedback.EDGE_BUMP_VERTICAL_DP)
                        anims.add(ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, 0f, dy, 0f))
                    }
                }
                pageDotsView?.takeIf { it.visibility == View.VISIBLE }?.let { strip ->
                    val dx = dir * strip.dpF(GridScrollFeedback.STRIP_NUDGE_DP)
                    anims.add(ObjectAnimator.ofFloat(strip, View.TRANSLATION_X, 0f, dx, 0f))
                }
                if (anims.isNotEmpty()) {
                    feedbackAnimator = AnimatorSet().apply {
                        playTogether(anims)
                        duration = GridScrollFeedback.BUMP_OUT_MS + GridScrollFeedback.BUMP_BACK_MS
                        interpolator = FOCUS_INTERPOLATOR
                        start()
                    }
                }
            }
        }
    }

    // Iterates the currently attached cells with their absolute slot index,
    // across either the single vertical grid or every horizontal page panel
    // (page panels fit exactly, so all their cells stay attached).
    private fun forEachAttachedCell(block: (FrameLayout, Int, GridAdapter) -> Unit) {
        if (isHorizontal) {
            val cellsPerPage = pageGeometry?.cellsPerPage ?: return
            pageGrids.forEachIndexed { page, grid ->
                val adapter = grid.adapter as? GridAdapter ?: return@forEachIndexed
                val first = page * cellsPerPage + grid.firstVisiblePosition
                for (i in 0 until grid.childCount) {
                    val position = first + i
                    if (position >= slotCount) break
                    val frame = grid.getChildAt(i) as? FrameLayout ?: continue
                    block(frame, position, adapter)
                }
            }
        } else {
            val grid = gridView ?: return
            val adapter = gridAdapter ?: return
            val first = grid.firstVisiblePosition
            for (i in 0 until grid.childCount) {
                val position = first + i
                if (position >= slotCount) break
                val frame = grid.getChildAt(i) as? FrameLayout ?: continue
                block(frame, position, adapter)
            }
        }
    }

    // Accent ring + 1.08 scale + Z-lift for the selected cell; lifted luminous
    // halo + elevation for the tile in hand during move mode; dashed drop-target
    // affordance on sibling slots while moving; recessed blank surface for empty
    // "+" slots; standard card for unfocused filled tiles. Shared by adapter
    // bind (snap) and in-place rebind (cancelable settle). While the dock holds
    // focus the grid shows NO ring.
    private fun applySelectionVisuals(
        frame: FrameLayout,
        position: Int,
        selected: Boolean,
        animate: Boolean = frame.isLaidOut,
    ) {
        val cell = frame.getChildAt(0) ?: return
        val ctx = frame.context
        val isBlank = cell is TextView
        val role = MoveChoreography.resolveRole(
            position = position,
            selectedIndex = if (selected) position else null,
            movingIndex = moveIndex,
            surfaceFocused = state.dockSlot == null,
        )
        val spec = MoveChoreography.gridVisualSpec(role, isBlank)
        val tokens = ThemePack.resolve(settings)
        val radiusPx = ctx.dpF(tokens.cardRadiusDp)
        val surfaceFill = TileBackgrounds.surfaceColor(ctx)

        cell.background = when (role) {
            MoveChoreography.SlotRole.LIFTED -> GradientDrawable().apply {
                setColor(MoveChoreography.liftedWashFill(settings.accentColor, surfaceFill))
                cornerRadius = radiusPx
                setStroke(
                    ctx.strokePx(4),
                    MoveChoreography.liftedBorderColor(settings.accentColor, surfaceFill),
                )
            }
            MoveChoreography.SlotRole.DROP_TARGET -> GradientDrawable().apply {
                setColor(if (isBlank) TileBackgrounds.surfaceSunken(ctx) else surfaceFill)
                cornerRadius = radiusPx
                setStroke(
                    ctx.strokePx(if (isBlank) 2 else 1),
                    MoveChoreography.dropTargetStrokeColor(settings.accentColor, surfaceFill),
                    ctx.dpF(6),
                    ctx.dpF(4),
                )
            }
            MoveChoreography.SlotRole.FOCUSED ->
                TileBackgrounds.selected(ctx, settings.accentColor)
            MoveChoreography.SlotRole.NORMAL ->
                if (isBlank) TileBackgrounds.blankSlot(ctx) else TileBackgrounds.card(ctx)
        }
        if (cell is TextView) {
            cell.setTextColor(
                when (role) {
                    MoveChoreography.SlotRole.LIFTED ->
                        MoveChoreography.liftedBorderColor(settings.accentColor, surfaceFill)
                    MoveChoreography.SlotRole.DROP_TARGET ->
                        MoveChoreography.dropTargetStrokeColor(settings.accentColor, surfaceFill)
                    MoveChoreography.SlotRole.FOCUSED -> UiDimens.ALPHA_SECONDARY
                    MoveChoreography.SlotRole.NORMAL -> BLANK_PLUS_IDLE_COLOR
                },
            )
        } else if (cell is LinearLayout && cell.childCount > 1) {
            val labelView = cell.getChildAt(cell.childCount - 1) as? TextView
            if (labelView != null) {
                val key = slotPackages().getOrNull(position)
                val isMissing = key != null && SlotKey.isRom(key) && romByKey[key] == null
                val style = GridLabelMetrics.labelStyleForRole(role, isMissing)
                labelView.setTextColor(style.textColor)
                labelView.setTypeface(null, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        frame.animate().cancel()
        frame.translationZ = if (spec.elevationDp > 0f) frame.dpF(spec.elevationDp) else 0f
        if (role == MoveChoreography.SlotRole.LIFTED) {
            // Breathing pulse drives scale/float/alpha for the lifted tile.
            frame.scaleX = spec.scale
            frame.scaleY = spec.scale
            frame.translationY = 0f
            frame.alpha = spec.alpha
        } else if (animate) {
            frame.animate()
                .scaleX(spec.scale)
                .scaleY(spec.scale)
                .translationY(0f)
                .alpha(spec.alpha)
                .setDuration(
                    if (role == MoveChoreography.SlotRole.DROP_TARGET) {
                        MoveChoreography.SETTLE_DURATION_MS
                    } else {
                        FOCUS_ANIM_MS
                    },
                )
                .setInterpolator(FOCUS_INTERPOLATOR)
                .start()
        } else {
            frame.scaleX = spec.scale
            frame.scaleY = spec.scale
            frame.translationY = 0f
            frame.alpha = spec.alpha
        }
    }

    // The lifted tile breathes gently in scale, hover elevation, and luminance
    // so it reads unmistakably as "in hand" without harsh opacity washout.
    private fun startPulse(frame: FrameLayout) {
        cancelPulseAnimator()
        val floatPx = frame.dpF(MoveChoreography.GRID_FLOAT_DP)
        val scaleX = ObjectAnimator.ofFloat(
            frame, View.SCALE_X,
            MoveChoreography.GRID_PULSE_SCALE_MIN, MoveChoreography.GRID_PULSE_SCALE_MAX,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(
            frame, View.SCALE_Y,
            MoveChoreography.GRID_PULSE_SCALE_MIN, MoveChoreography.GRID_PULSE_SCALE_MAX,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val hoverY = ObjectAnimator.ofFloat(frame, View.TRANSLATION_Y, 0f, floatPx).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(
            frame, View.ALPHA, 1f, MoveChoreography.PULSE_ALPHA_MIN,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, hoverY, alpha)
            duration = MoveChoreography.PULSE_DURATION_MS
            interpolator = PULSE_INTERPOLATOR
            start()
        }
    }

    // Keeps the selected cell on screen. Vertical: scroll minimally so the
    // selected cell is fully visible (above the viewport -> up just enough,
    // below -> down just enough, already visible -> no scroll at all),
    // derived from the live grid so a stale cached offset can never strand
    // the selection. Horizontal: snap the viewport so the selected slot's
    // whole page is in view (ensure-visible on X, page-aligned).
    private fun ensureSelectionVisible() {
        val geometry = pageGeometry ?: return
        if (isHorizontal) {
            val pager = pagerScroll ?: return
            if (pager.width == 0) return
            val target = (selectedIndex() / geometry.cellsPerPage) * pager.width
            if (pager.scrollX != target) pager.smoothScrollTo(target, 0)
            return
        }
        val grid = gridView ?: return
        val columns = settings.gridColumns
        val row = selectedIndex() / columns
        val stride = geometry.cellHeight + geometry.spacing
        val rowTop = geometry.paddingV + row * stride
        val firstChild = grid.getChildAt(0)
        val scrollPx = if (firstChild != null) {
            (grid.firstVisiblePosition / columns) * stride -
                firstChild.top + geometry.paddingV
        } else {
            0
        }
        val delta = EnsureVisibleScroll.delta(
            rowTop, rowTop + geometry.cellHeight, scrollPx, scrollPx + grid.height)
        if (delta != 0) grid.scrollListBy(delta)
    }

    // Repaints the interactive page indicator strip in place: smooth cancelable
    // pill width/color morph for the active page + edge-chevron boundary dimming.
    private fun updatePageDots(animate: Boolean = pageDotsView?.isLaidOut == true) {
        val dots = pageDotsView ?: return
        val geometry = pageGeometry ?: return
        if (pagePillViews.isEmpty()) return
        val stripState = GridPageIndicatorMetrics.stripState(
            selectedSlot = selectedIndex(),
            slotCount = slotCount,
            cellsPerPage = geometry.cellsPerPage,
        )
        val prevAlpha = if (stripState.canPagePrev) {
            GridPageIndicatorMetrics.CHEVRON_ENABLED_ALPHA
        } else {
            GridPageIndicatorMetrics.CHEVRON_DISABLED_ALPHA
        }
        val nextAlpha = if (stripState.canPageNext) {
            GridPageIndicatorMetrics.CHEVRON_ENABLED_ALPHA
        } else {
            GridPageIndicatorMetrics.CHEVRON_DISABLED_ALPHA
        }
        pagePrevButton?.let { btn ->
            btn.isEnabled = stripState.canPagePrev
            btn.isClickable = stripState.canPagePrev
            btn.animate().cancel()
            if (animate) {
                btn.animate().alpha(prevAlpha).setDuration(GridPageIndicatorMetrics.PILL_ANIM_MS).start()
            } else {
                btn.alpha = prevAlpha
            }
        }
        pageNextButton?.let { btn ->
            btn.isEnabled = stripState.canPageNext
            btn.isClickable = stripState.canPageNext
            btn.animate().cancel()
            if (animate) {
                btn.animate().alpha(nextAlpha).setDuration(GridPageIndicatorMetrics.PILL_ANIM_MS).start()
            } else {
                btn.alpha = nextAlpha
            }
        }

        if (stripState.currentPage == lastPaintedPage) return
        val prevPage = lastPaintedPage
        lastPaintedPage = stripState.currentPage

        val density = dots.resources.displayMetrics.density
        val activeW = (GridPageIndicatorMetrics.ACTIVE_PILL_WIDTH_DP * density).toInt()
        val inactiveW = (GridPageIndicatorMetrics.INACTIVE_DOT_WIDTH_DP * density).toInt()
        val pillH = (GridPageIndicatorMetrics.PILL_HEIGHT_DP * density).toInt()
        val radius = GridPageIndicatorMetrics.PILL_CORNER_RADIUS_DP * density
        val activeColor = GridPageIndicatorMetrics.activePillColor(settings.accentColor)
        val inactiveColor = GridPageIndicatorMetrics.INACTIVE_DOT_COLOR

        cancelPageDotAnimators()
        pagePillViews.forEachIndexed { pageIndex, pill ->
            val isCurrent = pageIndex == stripState.currentPage
            val targetW = if (isCurrent) activeW else inactiveW
            val targetColor = if (isCurrent) activeColor else inactiveColor
            (pill.parent as? View)?.isSelected = isCurrent

            val bg = (pill.background as? GradientDrawable)?.also {
                it.mutate()
                it.cornerRadius = radius
            } ?: GradientDrawable().apply {
                cornerRadius = radius
                setColor(targetColor)
                pill.background = this
            }

            val lp = pill.layoutParams as? FrameLayout.LayoutParams ?: return@forEachIndexed
            val startW = lp.width.takeIf { it > 0 } ?: if (pageIndex == prevPage) activeW else inactiveW
            val startColor = if (pageIndex == prevPage) activeColor else inactiveColor
            val changed = startW != targetW || startColor != targetColor

            if (animate && changed && prevPage >= 0) {
                val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = GridPageIndicatorMetrics.PILL_ANIM_MS
                    interpolator = FOCUS_INTERPOLATOR
                    addUpdateListener { va ->
                        val frac = va.animatedValue as Float
                        lp.width = GridPageIndicatorMetrics.interpolateWidthPx(startW, targetW, frac)
                        lp.height = pillH
                        pill.layoutParams = lp
                        bg.setColor(GridPageIndicatorMetrics.interpolateArgb(startColor, targetColor, frac))
                    }
                    start()
                }
                pageDotAnimators.add(anim)
            } else {
                bg.setColor(targetColor)
                if (lp.width != targetW || lp.height != pillH) {
                    lp.width = targetW
                    lp.height = pillH
                    pill.layoutParams = lp
                }
            }
        }
    }

    // Centered interactive page indicator strip: prev chevron cue, tappable
    // page pills (expanded 20×6dp luminous accent pill for current page, 6dp
    // dots for others), and next chevron cue. Hidden on a single-page grid.
    private fun buildPageDots(context: Context, dots: LinearLayout, totalPages: Int) {
        cancelPageDotAnimators()
        lastPaintedPage = -1
        dots.removeAllViews()
        if (totalPages <= 1) {
            pagePillViews = emptyList()
            pagePrevButton = null
            pageNextButton = null
            dots.visibility = View.GONE
            return
        }
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val cellsPerPage = pageGeometry?.cellsPerPage ?: 1
        val stripState = GridPageIndicatorMetrics.stripState(
            selectedSlot = selectedIndex(),
            slotCount = slotCount,
            cellsPerPage = cellsPerPage,
        )
        val activeColor = GridPageIndicatorMetrics.activePillColor(settings.accentColor)
        val radius = GridPageIndicatorMetrics.PILL_CORNER_RADIUS_DP * density
        val touchH = dp(24)

        dots.visibility = View.VISIBLE

        val prevBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(UiDimens.ALPHA_PRIMARY, PorterDuff.Mode.SRC_IN)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = pageStripRipple(context, cornerRadiusDp = 12)
            contentDescription = context.getString(R.string.action_page_left)
            alpha = if (stripState.canPagePrev) {
                GridPageIndicatorMetrics.CHEVRON_ENABLED_ALPHA
            } else {
                GridPageIndicatorMetrics.CHEVRON_DISABLED_ALPHA
            }
            isEnabled = stripState.canPagePrev
            isClickable = stripState.canPagePrev
            setOnClickListener { jumpToPageDelta(-1) }
        }
        pagePrevButton = prevBtn
        dots.addView(prevBtn, LinearLayout.LayoutParams(dp(28), touchH).apply {
            marginEnd = dp(4)
        })

        val pills = mutableListOf<View>()
        for (pageIndex in 0 until totalPages) {
            val isCurrent = pageIndex == stripState.currentPage
            val pill = View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(if (isCurrent) activeColor else GridPageIndicatorMetrics.INACTIVE_DOT_COLOR)
                }
            }
            val hitTarget = FrameLayout(context).apply {
                setPadding(dp(4), 0, dp(4), 0)
                background = pageStripRipple(context, cornerRadiusDp = 12)
                isClickable = true
                isSelected = isCurrent
                contentDescription = context.getString(
                    R.string.format_progress,
                    pageIndex + 1,
                    totalPages,
                )
                setOnClickListener { jumpToPage(pageIndex) }
                addView(
                    pill,
                    FrameLayout.LayoutParams(
                        dp(
                            if (isCurrent) {
                                GridPageIndicatorMetrics.ACTIVE_PILL_WIDTH_DP
                            } else {
                                GridPageIndicatorMetrics.INACTIVE_DOT_WIDTH_DP
                            },
                        ),
                        dp(GridPageIndicatorMetrics.PILL_HEIGHT_DP),
                        Gravity.CENTER,
                    ),
                )
            }
            pills.add(pill)
            dots.addView(
                hitTarget,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, touchH),
            )
        }
        pagePillViews = pills

        val nextBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(UiDimens.ALPHA_PRIMARY, PorterDuff.Mode.SRC_IN)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = pageStripRipple(context, cornerRadiusDp = 12)
            contentDescription = context.getString(R.string.action_page_right)
            alpha = if (stripState.canPageNext) {
                GridPageIndicatorMetrics.CHEVRON_ENABLED_ALPHA
            } else {
                GridPageIndicatorMetrics.CHEVRON_DISABLED_ALPHA
            }
            isEnabled = stripState.canPageNext
            isClickable = stripState.canPageNext
            setOnClickListener { jumpToPageDelta(+1) }
        }
        pageNextButton = nextBtn
        dots.addView(nextBtn, LinearLayout.LayoutParams(dp(28), touchH).apply {
            marginStart = dp(4)
        })
        lastPaintedPage = stripState.currentPage
    }

    private fun pageStripRipple(context: Context, cornerRadiusDp: Int): RippleDrawable {
        val mask = GradientDrawable().apply {
            cornerRadius = context.dpF(cornerRadiusDp)
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(PAGE_STRIP_RIPPLE_COLOR), null, mask)
    }

    private fun jumpToPage(targetPage: Int) {
        val geometry = pageGeometry ?: return
        val current = selectedIndex()
        val target = GridPageIndicatorMetrics.targetSlotForPage(
            targetPage = targetPage,
            currentSlot = current,
            slotCount = slotCount,
            cellsPerPage = geometry.cellsPerPage,
        )
        applyPageJumpSlot(current, target)
    }

    private fun jumpToPageDelta(deltaPages: Int) {
        val geometry = pageGeometry ?: return
        val current = selectedIndex()
        val target = GridPageIndicatorMetrics.stepPageSlot(
            deltaPages = deltaPages,
            currentSlot = current,
            slotCount = slotCount,
            cellsPerPage = geometry.cellsPerPage,
        )
        val action = if (deltaPages < 0) Action.PAGE_PREV else Action.PAGE_NEXT
        triggerScrollFeedback(
            GridScrollFeedback.classify(action, current, target, geometry.cellsPerPage, slotCount),
        )
        applyPageJumpSlot(current, target)
    }

    private fun applyPageJumpSlot(from: Int, target: Int) {
        if (target == from) return
        val moving = moveIndex
        if (moving != null) {
            val working = moveWorking ?: return
            if (target in working.indices) {
                val tmp = working[moving]
                working[moving] = working[target]
                working[target] = tmp
                moveIndex = target
                ensureSelectionVisible()
                rebindVisibleCells()
                updatePageDots()
            }
            return
        }
        state.selectSlot(target, settings.gridSlots.getOrNull(target))
    }

    // Tap-to-focus: first tap on an unfocused slot only moves the
    // selection; tapping the selected filled slot launches it, and a blank
    // slot opens the picker on first tap when focused. During move mode a
    // tap drops the lifted tile on that slot. Folder tiles open the member list.
    private fun onSlotTap(position: Int) {
        when {
            moveIndex != null -> dropMove(tapSlot = position)
            state.selectedSlot == position -> {
                val key = settings.gridSlots.getOrNull(position)
                when {
                    key == null -> openPicker(position)
                    SlotKey.isFolder(key) -> openFolder(key)
                    else -> launchSlotKey(
                        activity, state, roms, key, reason = LaunchReason.SLOT,
                    )
                }
            }
            else -> state.selectSlot(
                position, settings.gridSlots.getOrNull(position))
        }
    }

    // Long-press opens Move/Remove (filled) or New folder (blank).
    private fun onSlotLongPress(position: Int) {
        if (moveIndex == null) openSlotMenu(position)
    }

    private fun bindFolderThumb(image: ImageView, key: String) {
        val app = activity.application as GhostGalleonApp
        val px = (96 * activity.resources.displayMetrics.density).toInt()
        val thumbRadiusPx = activity.dpF(
            DeckCornerMetrics.folderThumbRadiusDp(ThemePack.resolve(settings)),
        )
        SlotKey.romId(key)?.let { id ->
            val rom = app.romEntry(id) ?: return
            image.tag = key
            var syncHit = true
            app.artCache.load(
                activity, rom, px,
                artOverrides = app.settings.artOverrides,
                isStillValid = { image.tag == key && (syncHit || image.isAttachedToWindow) },
            ) { bmp ->
                if (bmp != null && image.tag == key && (syncHit || image.isAttachedToWindow)) {
                    image.setBackgroundColor(TileBackgrounds.surfaceSunken(activity))
                    com.visorcraft.ghostgalleon.art.ArtCache.showDisplayed(image, bmp)
                }
            }
            syncHit = false
            return
        }
        if (!SlotKey.isFolder(key) && !SlotKey.isRom(key)) {
            CustomIcon.bind(
                image, iconLoader, app.artCache, settings, key, px,
                cornerRadiusPx = thumbRadiusPx,
            )
        }
    }

    private fun memberLabel(key: String): String {
        SlotKey.romId(key)?.let { id ->
            return (activity.application as GhostGalleonApp).romEntry(id)?.name ?: key
        }
        if (SlotKey.isFolder(key)) {
            val fid = SlotKey.folderId(key)
            return settings.folders[fid]?.name ?: key
        }
        return visibleByPkg[key]?.label ?: key
    }

    private fun openFolder(folderKey: String) {
        val fid = SlotKey.folderId(folderKey) ?: return
        val spec = settings.folders[fid]
        val name = spec?.name ?: fid
        val members = Folders.members(settings.folders, fid).map { k ->
            k to memberLabel(k)
        }
        closeFolderPanel()
        val app = activity.application as GhostGalleonApp
        val panel = FolderPanel(
            activity,
            settings.accentColor,
            title = name,
            members = members,
            onLaunch = { memberKey ->
                closeFolderPanel()
                launchSlotKey(
                    activity, state, roms, memberKey, reason = LaunchReason.SLOT,
                )
            },
            onClose = { closeFolderPanel() },
            onRemoveMember = { memberKey ->
                val folders = Folders.removeMember(app.settings.folders, fid, memberKey)
                val collections = com.visorcraft.ghostgalleon.library.FolderCollectionBridge
                    .syncCollectionFromFolder(folders, fid, app.settings.collections)
                app.updateSettings(app.settings.copy(folders = folders, collections = collections))
                Toast.makeText(
                    activity,
                    R.string.deck_removed_from_folder,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onBindThumb = { image, key ->
                bindFolderThumb(image, key)
            },
            onMirrorToCollection = {
                val next = com.visorcraft.ghostgalleon.library.FolderCollectionBridge
                    .mirrorFolderToCollection(
                        app.settings.folders,
                        fid,
                        app.settings.collections,
                    )
                app.updateSettings(app.settings.copy(collections = next))
                Toast.makeText(
                    activity,
                    R.string.deck_mirrored_to_collection,
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
        folderPanel = panel
        rootView?.addView(panel.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun closeFolderPanel() {
        folderPanel?.let { rootView?.removeView(it.view) }
        folderPanel = null
    }

    override fun primaryView(context: Context): View {
        // FrameLayout root so an optional dimmed wallpaper can sit behind
        // the whole grid stack; content stays transparent when one is set.
        // Modals (slot menu, app picker) are added on top of this root.
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = false
            clipToPadding = false
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    cancelAllAnimations()
                }
            })
        }
        rootView = root
        DeckWallpaper.attachIfConfigured(root, context, settings.wallpaperUri)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        // Slim centered interactive page indicator strip above the dock
        // (hidden when 1 page), keeping the dock centered across Grid & Game.
        val pageDots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        // The frame clips the grid's partial over-drawn bottom row while
        // the grid itself stays unclipped so selected cells can scale over
        // their neighbours into the grid padding.
        val gridFrame = FrameLayout(context)
        val gridArea = if (isHorizontal) {
            buildHorizontalPager(context, pageDots)
        } else {
            buildVerticalGrid(context, pageDots)
        }
        gridFrame.addView(gridArea, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(gridFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // Bottom stack: centered page dots -> HintBar -> floating centered dock.
        content.addView(pageDots, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (settings.showHints) {
            val hints = HintBar.build(context) as TextView
            hints.text = HintBar.textFor(activity, state.dockSlot != null)
            hintView = hints
            content.addView(hints, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val hostChromeIcons = shouldHostSystemChromeIcons(activity)
        val bar = DockBar(
            activity, settings, library, iconLoader, roms,
            slots = { dockMove.working ?: settings.dockSlots },
            onTap = ::onDockTap,
            onLongPress = ::onDockLongPress,
        )
        dockBar = bar
        content.addView(bar.build(context, pageDots = null, reserveChromeInset = hostChromeIcons))
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Optional clock/battery overlay (omitted when single-screen TOP_STRIP hosts it).
        if (showStatusPill && settings.browseChrome.deckStatusPill) {
            root.addView(
                StatusPill.build(context, compact = true),
                StatusPill.overlayLayoutParams(context),
            )
        }
        // Dual: Swap/Settings only on the larger panel, pinned bottom-left /
        // bottom-right so dock+hints never push them mid-screen. Single: same.
        if (hostChromeIcons) {
            attachSystemChromeOverlay(root, context, activity, state)
        }
        // A rebuild while the dock holds focus (settings save, mode
        // toggle) must repaint the ring immediately — updateFocus
        // otherwise only runs on selection-only updates.
        bar.updateFocus(state.dockSlot)
        return root
    }

    // Default "vertical" mode: one GridView scrolling top to bottom.
    private fun buildVerticalGrid(context: Context, pageDots: LinearLayout): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val columns = settings.gridColumns
        val gridPadH = dp(16)
        val gridPadV = dp(16)
        // Extra top room so the selected cell's 4dp stroke + 1.08 scale
        // over-draw never hits the grid's top edge on the first row.
        val gridPadTop = gridPadV + dp(8)
        val spacing = dp(12)
        val cellPadding = dp(8)
        val labelHeight = if (settings.showLabels) dp(24) else 0

        val grid = GridView(context).apply {
            numColumns = columns
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = spacing
            verticalSpacing = spacing
            setPadding(gridPadH, gridPadTop, gridPadH, gridPadV)
            clipChildren = false
            clipToPadding = false
            isVerticalFadingEdgeEnabled = true
            isScrollbarFadingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            // Items stay non-focusable so d-pad routing is unchanged.
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                onSlotTap(position)
            }
            onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
                onSlotLongPress(position)
                true
            }
        }
        // Size cells from the grid's measured bounds so rows always fit
        // exactly: no half-clipped row and no overlap with the bottom bars.
        // The listener STAYS registered: a rebuild that races the closing
        // IME (picker -> settings update) lays out once with the window
        // still shrunk by the keyboard, then again at full height. Only a
        // changed height recomputes, so this converges without loops.
        grid.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            private var lastHeight = -1
            override fun onGlobalLayout() {
                if (grid.height == 0 || grid.height == lastHeight) return
                lastHeight = grid.height
                grid.setFadingEdgeLength(
                    dp(GridScrollFeedback.fadingEdgeLengthDp(grid.height, density)),
                )
                val cellWidth =
                    (grid.width - gridPadH * 2 - spacing * (columns - 1)) / columns
                val guessIconSize = GridIconMetrics.iconSizePx(
                    cellWidth, settings.iconSizeDp, density,
                )
                val guessCellHeight = guessIconSize + labelHeight + cellPadding * 2
                val visibleRows =
                    ((grid.height - gridPadTop - gridPadV + spacing) /
                        (guessCellHeight + spacing)).coerceAtLeast(1)
                val cellHeight =
                    (grid.height - gridPadTop - gridPadV -
                        spacing * (visibleRows - 1)) / visibleRows
                val safeDensity = density.coerceAtLeast(0.1f)
                val tokens = ThemePack.resolve(settings)
                val labelSpec = GridLabelMetrics.compute(
                    cellWidthDp = (cellWidth / safeDensity).toInt(),
                    cellHeightDp = (cellHeight / safeDensity).toInt(),
                    iconSizeDp = (guessIconSize / safeDensity).toInt(),
                    cellPaddingDp = 8,
                    showLabels = settings.showLabels,
                    hasWallpaper = !settings.wallpaperUri.isNullOrBlank(),
                    fontScale = tokens.fontScale,
                )
                val resolvedLabelHeight = (labelSpec.reservedHeightDp * safeDensity).toInt()
                val iconSize = GridIconMetrics.iconSizePx(
                    cellWidthPx = cellWidth,
                    cellHeightPx = cellHeight,
                    cellPaddingPx = cellPadding,
                    labelHeightPx = resolvedLabelHeight,
                    iconSizeDp = settings.iconSizeDp,
                    density = density,
                )
                val adapter = (grid.adapter as? GridAdapter)
                    ?: GridAdapter(context, cellWidth, iconSize, cellHeight, cellPadding, labelSpec)
                        .also { grid.adapter = it }
                adapter.updateMetrics(cellWidth, iconSize, cellHeight, labelSpec)
                // Cells already attached carry the previous height in their
                // LayoutParams; restamp them so a geometry change (IME
                // race) actually resizes the visible tiles.
                for (i in 0 until grid.childCount) {
                    grid.getChildAt(i).layoutParams =
                        AbsListView.LayoutParams(cellWidth, cellHeight)
                }
                val cellsPerPage = columns * visibleRows
                // Capture live view state for selection-only updates.
                gridView = grid
                gridAdapter = adapter
                pageDotsView = pageDots
                pageGeometry = PageGeometry(
                    cellsPerPage, visibleRows, cellHeight, spacing, gridPadTop)
                val totalPages =
                    ((slotCount + cellsPerPage - 1) / cellsPerPage).coerceAtLeast(1)
                buildPageDots(context, pageDots, totalPages)
                grid.requestLayout()
                // Show the selection: scroll minimally (after children
                // attach) so the selected cell is fully visible, wherever
                // it sits in the grid.
                grid.post { ensureSelectionVisible() }
            }
        })
        return grid
    }

    // "horizontal" mode (3DS-style): fixed-size page panels laid out left
    // to right inside a horizontal scroller. Each page panel is a
    // GridView of columns x visibleRows cells, exactly viewport-sized and
    // non-scrolling. Selection changes snap the viewport a whole page so
    // the selected slot's page is in view. Touch scrolling is disabled: the
    // viewport is always page-aligned, so the dots never lie.
    private fun buildHorizontalPager(context: Context, pageDots: LinearLayout): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val columns = settings.gridColumns
        val gridPadH = dp(16)
        val gridPadV = dp(16)
        val gridPadTop = gridPadV + dp(8)
        val spacing = dp(12)
        val cellPadding = dp(8)
        val labelHeight = if (settings.showLabels) dp(24) else 0

        val pager = object : HorizontalScrollView(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false
            override fun onTouchEvent(ev: MotionEvent?): Boolean = false
        }
        pager.isHorizontalFadingEdgeEnabled = false
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        pager.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Same persistent-listener discipline as the vertical grid, keyed
        // on the measured page bounds: an IME-race rebuild re-lays out at
        // full height and recomputes pages/dots exactly once.
        pager.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            private var lastWidth = -1
            private var lastHeight = -1
            override fun onGlobalLayout() {
                if (pager.width == 0 || pager.height == 0) return
                if (pager.width == lastWidth && pager.height == lastHeight) return
                lastWidth = pager.width
                lastHeight = pager.height
                val pageWidth = pager.width
                val cellWidth =
                    (pageWidth - gridPadH * 2 - spacing * (columns - 1)) / columns
                val guessIconSize = GridIconMetrics.iconSizePx(
                    cellWidth, settings.iconSizeDp, density,
                )
                val guessCellHeight = guessIconSize + labelHeight + cellPadding * 2
                val visibleRows =
                    ((pager.height - gridPadTop - gridPadV + spacing) /
                        (guessCellHeight + spacing)).coerceAtLeast(1)
                val cellHeight =
                    (pager.height - gridPadTop - gridPadV -
                        spacing * (visibleRows - 1)) / visibleRows
                val safeDensity = density.coerceAtLeast(0.1f)
                val tokens = ThemePack.resolve(settings)
                val labelSpec = GridLabelMetrics.compute(
                    cellWidthDp = (cellWidth / safeDensity).toInt(),
                    cellHeightDp = (cellHeight / safeDensity).toInt(),
                    iconSizeDp = (guessIconSize / safeDensity).toInt(),
                    cellPaddingDp = 8,
                    showLabels = settings.showLabels,
                    hasWallpaper = !settings.wallpaperUri.isNullOrBlank(),
                    fontScale = tokens.fontScale,
                )
                val resolvedLabelHeight = (labelSpec.reservedHeightDp * safeDensity).toInt()
                val iconSize = GridIconMetrics.iconSizePx(
                    cellWidthPx = cellWidth,
                    cellHeightPx = cellHeight,
                    cellPaddingPx = cellPadding,
                    labelHeightPx = resolvedLabelHeight,
                    iconSizeDp = settings.iconSizeDp,
                    density = density,
                )
                val cellsPerPage = columns * visibleRows
                val totalPages =
                    ((slotCount + cellsPerPage - 1) / cellsPerPage).coerceAtLeast(1)
                row.removeAllViews()
                val grids = mutableListOf<GridView>()
                for (page in 0 until totalPages) {
                    val offset = page * cellsPerPage
                    val count = (slotCount - offset).coerceAtMost(cellsPerPage)
                    val pageGrid = GridView(context).apply {
                        numColumns = columns
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        horizontalSpacing = spacing
                        verticalSpacing = spacing
                        setPadding(gridPadH, gridPadTop, gridPadH, gridPadV)
                        clipChildren = false
                        clipToPadding = false
                        selector = android.graphics.drawable.ColorDrawable(
                            Color.TRANSPARENT)
                        adapter = GridAdapter(
                            context, cellWidth, iconSize, cellHeight, cellPadding,
                            labelSpec = labelSpec,
                            slotOffset = offset, fixedCount = count)
                        onItemClickListener =
                            AdapterView.OnItemClickListener { _, _, position, _ ->
                                onSlotTap(offset + position)
                            }
                        onItemLongClickListener =
                            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                                onSlotLongPress(offset + position)
                                true
                            }
                    }
                    grids.add(pageGrid)
                    row.addView(pageGrid, LinearLayout.LayoutParams(
                        pageWidth, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                // Capture live view state for selection-only updates.
                pagerScroll = pager
                pageGrids = grids
                pageDotsView = pageDots
                pageGeometry = PageGeometry(
                    cellsPerPage, visibleRows, cellHeight, spacing, gridPadTop)
                buildPageDots(context, pageDots, totalPages)
                // Snap to the selected slot's page once laid out.
                pager.post { ensureSelectionVisible() }
            }
        })
        return pager
    }

    private fun updateDockSlots(slots: List<String?>, feedback: UiText? = null) {
        val app = activity.application as GhostGalleonApp
        DockActions.persist(activity, app, slots, feedback)
    }

    private fun onDockTap(index: Int) {
        val bar = dockBar ?: return
        when {
            dockMove.active -> dropDockMove(tapSlot = index)
            bar.isBlank(index) -> openDockPicker(index)
            else -> bar.keyAt(index)?.let { launchSlotKey(activity, state, roms, it) }
        }
    }

    private fun onDockLongPress(index: Int) {
        if (!dockMove.active && moveIndex == null &&
            dockBar?.isBlank(index) == false
        ) {
            openDockSlotMenu(index)
        }
    }

    private fun openDockSlotMenu(index: Int) {
        state.focusDock(index)
        val choices = listOf(
            SlotMenu.Choice.MOVE, SlotMenu.Choice.REMOVE, SlotMenu.Choice.CANCEL)
        val dockTitle = dockBar?.keyAt(index)?.let(::gridEntryLabel)
        val menu = SlotMenu.fromChoices(
            activity,
            settings.accentColor,
            choices,
            title = dockTitle,
        ) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.MOVE -> startDockMove(index)
                SlotMenu.Choice.REMOVE -> {
                    val app = activity.application as GhostGalleonApp
                    val next = DockActions.removeAt(app, index)
                    updateDockSlots(next, text(R.string.deck_removed_from_dock))
                    DockActions.clampFocus(state.dockSlot, next)?.let { state.focusDock(it) }
                }
                else -> {}
            }
        }
        slotMenu = menu
        DeckOverlays.attach(rootView, menu.view)
    }

    private fun startDockMove(index: Int) {
        dockMove.start(index, settings.dockSlots)
        hintView?.text = HintBar.moveText(activity)
        state.focusDock(index)
        dockBar?.updateFocus(index, moving = index)
    }

    private fun handleDockMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> {
                val to = dockNav.move(from, action)
                if (to != from) {
                    val next = dockMove.swap(from, to) ?: return true
                    dockBar?.rebind()
                    state.focusDock(next)
                    dockBar?.updateFocus(next, moving = next)
                }
            }
            Action.CONFIRM -> dropDockMove()
            Action.BACK -> cancelDockMove()
            else -> {}
        }
        return true
    }

    private fun dropDockMove(tapSlot: Int? = null) {
        val result = dockMove.drop(tapSlot) ?: return
        hintView?.text = HintBar.textFor(activity, state.dockSlot != null)
        updateDockSlots(result.compacted)
        state.focusDock(result.focusIndex)
    }

    private fun cancelDockMove() {
        dockMove.clear()
        hintView?.text = HintBar.textFor(activity, state.dockSlot != null)
        dockBar?.rebind()
        dockBar?.updateFocus(state.dockSlot)
    }

    private fun handleDockAction(action: Action, dockIndex: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT ->
                state.focusDock(dockNav.move(dockIndex, action))
            Action.NAV_UP, Action.BACK -> {
                val target = dockNav.exitToGrid(dockIndex)
                state.selectSlot(target, settings.gridSlots.getOrNull(target))
            }
            Action.CONFIRM -> {
                val bar = dockBar
                if (bar == null || bar.isBlank(dockIndex)) {
                    openDockPicker(dockIndex)
                } else {
                    bar.keyAt(dockIndex)?.let { launchSlotKey(activity, state, roms, it) }
                }
            }
            else -> {}
        }
        return true
    }

    private fun openDockPicker(slot: Int) {
        val app = activity.application as GhostGalleonApp
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = activity.getString(R.string.action_add_to_dock),
            onPick = { key ->
                closePicker()
                updateDockSlots(DockActions.fill(app, slot, key))
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun pinToDock(key: String) {
        val app = activity.application as GhostGalleonApp
        DockActions.pin(activity, app, key) { slots, toast -> updateDockSlots(slots, toast) }
    }

    private fun unpinFromDock(key: String) {
        val app = activity.application as GhostGalleonApp
        DockActions.unpin(activity, app, key) { slots, toast -> updateDockSlots(slots, toast) }
    }

    private fun promptAddToCollection(key: String) {
        val app = activity.application as GhostGalleonApp
        CollectionDialogs.promptAdd(activity, app, listOf(key))
    }

    private fun updateGridSlots(slots: List<String?>, focusSlot: Int) {
        val app = activity.application as GhostGalleonApp
        app.updateSettings(app.settings.copy(gridSlots = slots))
        state.selectSlot(focusSlot, slots.getOrNull(focusSlot))
    }

    private fun openWithForRom(rom: RomEntry) {
        EntryActions.openWith(activity, rom) { playerId ->
            launchSlotKey(
                activity, state, roms, SlotKey.rom(rom.id),
                playerId = playerId,
            )
        }
    }

    private fun showPlayerProfileMenu(rom: RomEntry) {
        EntryActions.playerProfile(activity, rom)
    }

    private fun showScreensPlotMenu(rom: RomEntry) {
        EntryActions.screensPlot(activity, rom)
    }

    private fun createFolderAt(slot: Int) {
        val app = activity.application as GhostGalleonApp
        val id = Folders.nextId(app.settings.folders)
        val folders = Folders.create(
            app.settings.folders,
            id,
            activity.getString(R.string.label_folder),
        )
        val key = Folders.key(id)
        val slots = GridSlots.fill(app.settings.gridSlots, slot, key)
        app.updateSettings(app.settings.copy(folders = folders, gridSlots = slots))
        state.selectSlot(slot, key)
        Toast.makeText(activity, R.string.deck_folder_created, Toast.LENGTH_SHORT).show()
    }

    private fun renameFolder(folderId: String) {
        val current = settings.folders[folderId]?.name ?: folderId
        DeckDialogs.promptText(
            context = activity,
            titleRes = R.string.deck_rename_folder,
            initialText = current,
            hintRes = R.string.deck_folder_name_hint,
            positiveRes = R.string.action_save,
        ) { raw ->
            val name = raw.trim()
            if (name.isEmpty()) return@promptText
            val app = activity.application as GhostGalleonApp
            app.updateSettings(app.settings.copy(
                folders = Folders.rename(app.settings.folders, folderId, name)))
        }
    }

    private fun addFolderMember(folderId: String) {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = activity.getString(R.string.action_add_to_folder),
            onPick = { key ->
                closePicker()
                val app = activity.application as GhostGalleonApp
                val folders = Folders.addMember(app.settings.folders, folderId, key)
                val collections = com.visorcraft.ghostgalleon.library.FolderCollectionBridge
                    .syncCollectionFromFolder(folders, folderId, app.settings.collections)
                app.updateSettings(app.settings.copy(folders = folders, collections = collections))
                Toast.makeText(activity, R.string.deck_added_to_folder, Toast.LENGTH_SHORT).show()
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun removeFolderSlot(slot: Int, folderKey: String) {
        val folderId = SlotKey.folderId(folderKey)
        val app = activity.application as GhostGalleonApp
        val slots = GridSlots.remove(app.settings.gridSlots, slot)
        val folders = if (folderId != null) {
            Folders.delete(app.settings.folders, folderId)
        } else {
            app.settings.folders
        }
        app.updateSettings(app.settings.copy(gridSlots = slots, folders = folders))
        state.selectSlot(slot, null)
    }

    // --- Slot menu (sectioned: Arrange / Dock / Library / Customize / More) ---

    private fun openSlotMenu(slot: Int) {
        state.selectSlot(slot, settings.gridSlots.getOrNull(slot))
        val key = settings.gridSlots.getOrNull(slot)
        val isFolder = SlotKey.isFolder(key)
        val isRom = key != null && SlotKey.isRom(key)
        val isApp = key != null && !isRom && !isFolder
        val fav = key != null && key in settings.favorites
        val rows: List<SlotMenu.Row> = when {
            key == null -> listOf(
                SlotMenu.Row.Item(SlotMenu.Choice.NEW_FOLDER),
                SlotMenu.Row.Item(SlotMenu.Choice.CANCEL),
            )
            isFolder -> listOf(
                SlotMenu.Row.Header(R.string.menu_section_arrange),
                SlotMenu.Row.Item(SlotMenu.Choice.MOVE),
                SlotMenu.Row.Item(SlotMenu.Choice.RENAME),
                SlotMenu.Row.Item(SlotMenu.Choice.ADD_MEMBER),
                SlotMenu.Row.Item(SlotMenu.Choice.MIRROR_TO_COLLECTION),
                SlotMenu.Row.Item(SlotMenu.Choice.REMOVE, destructive = true),
                SlotMenu.Row.Item(SlotMenu.Choice.CANCEL),
            )
            else -> {
                val stats = com.visorcraft.ghostgalleon.library.PlayStats(
                    lastLaunchedMs = settings.lastLaunchedMs,
                    totalPlaytimeMs = settings.playtimeMs,
                )
                SlotMenu.gridTileRows(
                    isRom = isRom,
                    isApp = isApp,
                    fav = fav,
                    inDock = DockSlots.containsKey(settings.dockSlots, key),
                    hasCustomName = key in settings.customNames ||
                        SlotKey.romId(key)?.let { it in settings.romNames } == true,
                    hasCustomIcon = key in settings.customIcons,
                    showRelated = isRom && gridRelatedOptions(key).isNotEmpty(),
                    showMarkPlayed = com.visorcraft.ghostgalleon.library.LibraryBrowse
                        .isUnplayed(key, settings.lastLaunchedMs),
                    showClearStats = com.visorcraft.ghostgalleon.library.SessionMath
                        .hasStats(stats, key),
                )
            }
        }
        val menuTitle = key?.let(::gridEntryLabel)
        val menu = SlotMenu(activity, settings.accentColor, rows, title = menuTitle) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.DETAILS -> key?.let { k -> showGridDetails(k) }
                SlotMenu.Choice.COPY_TITLE -> key?.let { k ->
                    copyGridTitleToClipboard(gridEntryLabel(k))
                }
                SlotMenu.Choice.BROWSE_RELATED -> key?.let { k ->
                    showGridBrowseRelated(k)
                }
                SlotMenu.Choice.MARK_PLAYED -> key?.let { k -> markGridAsPlayed(k) }
                SlotMenu.Choice.CLEAR_PLAY_STATS -> key?.let { k -> clearGridPlayStats(k) }
                SlotMenu.Choice.APP_INFO -> key?.let { k ->
                    if (!SlotKey.isRom(k) && !SlotKey.isFolder(k)) openAppInfo(k)
                }
                SlotMenu.Choice.MOVE -> startMove(slot)
                SlotMenu.Choice.PIN_TO_DOCK ->
                    settings.gridSlots.getOrNull(slot)?.let(::pinToDock)
                SlotMenu.Choice.UNPIN_FROM_DOCK ->
                    settings.gridSlots.getOrNull(slot)?.let(::unpinFromDock)
                SlotMenu.Choice.FAVORITE, SlotMenu.Choice.UNFAVORITE -> key?.let { k ->
                    EntryActions.toggleFavorite(activity, k)
                }
                SlotMenu.Choice.ADD_TO_COLLECTION -> key?.let { k ->
                    promptAddToCollection(k)
                }
                SlotMenu.Choice.OPEN_WITH -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val rom = roms.firstOrNull { it.id == id } ?: return@let
                    openWithForRom(rom)
                }
                SlotMenu.Choice.PLAYER -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val rom = roms.firstOrNull { it.id == id } ?: return@let
                    showPlayerProfileMenu(rom)
                }
                SlotMenu.Choice.SCREENS -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val rom = roms.firstOrNull { it.id == id } ?: return@let
                    showScreensPlotMenu(rom)
                }
                SlotMenu.Choice.SET_ART -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    (activity as? BaseDeckActivity)?.requestCustomIcon { uri ->
                        val app = activity.application as GhostGalleonApp
                        // Drop mem+disk so the override is re-decoded.
                        app.artCache.invalidate(id)
                        app.updateSettings(app.settings.copy(
                            artOverrides = app.settings.artOverrides + (id to uri.toString())))
                    }
                }
                SlotMenu.Choice.HIDE -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val app = activity.application as GhostGalleonApp
                    val next = com.visorcraft.ghostgalleon.library.HiddenRoms
                        .hide(app.settings.hiddenRomIds, id)
                    app.updateSettings(app.settings.copy(hiddenRomIds = next))
                    Toast.makeText(
                        activity,
                        R.string.deck_hidden_from_library,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                SlotMenu.Choice.NEW_FOLDER -> createFolderAt(slot)
                SlotMenu.Choice.ADD_MEMBER -> key?.let { k ->
                    SlotKey.folderId(k)?.let(::addFolderMember)
                }
                SlotMenu.Choice.RENAME -> when {
                    isFolder -> SlotKey.folderId(key)?.let(::renameFolder)
                    else -> key?.let(::showRenameDialog)
                }
                SlotMenu.Choice.RESET_NAME -> key?.let { k ->
                    val app = activity.application as GhostGalleonApp
                    val romId = SlotKey.romId(k)
                    app.updateSettings(
                        if (romId != null) {
                            app.settings.copy(
                                romNames = com.visorcraft.ghostgalleon.settings.RomNames
                                    .clear(app.settings.romNames, romId),
                            )
                        } else {
                            app.settings.copy(customNames = app.settings.customNames - k)
                        },
                    )
                }
                SlotMenu.Choice.CUSTOM_ICON -> key?.let { pkg ->
                    (activity as? BaseDeckActivity)?.requestCustomIcon { uri ->
                        val app = activity.application as GhostGalleonApp
                        app.updateSettings(app.settings.copy(
                            customIcons = app.settings.customIcons +
                                (pkg to uri.toString())))
                    }
                }
                SlotMenu.Choice.RESET_ICON -> key?.let { pkg ->
                    val app = activity.application as GhostGalleonApp
                    app.updateSettings(app.settings.copy(
                        customIcons = app.settings.customIcons - pkg))
                }
                SlotMenu.Choice.REMOVE, SlotMenu.Choice.REMOVE_FROM_GRID -> when {
                    isFolder && key != null -> removeFolderSlot(slot, key)
                    else -> {
                        updateGridSlots(GridSlots.remove(settings.gridSlots, slot), slot)
                        Toast.makeText(
                            activity,
                            R.string.deck_removed_from_grid,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                SlotMenu.Choice.OPEN_IN_GAME_MODE -> {
                    key?.let { state.select(it, force = true) }
                    state.setMode(com.visorcraft.ghostgalleon.state.UIMode.GAME)
                }
                SlotMenu.Choice.SEARCH_LIBRARY -> openLibrarySearch()
                SlotMenu.Choice.DOWNLOAD_ART -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val rom = roms.firstOrNull { it.id == id } ?: return@let
                    requestMissingArtwork(activity, rom)
                }
                SlotMenu.Choice.MIRROR_TO_COLLECTION -> key?.let { k ->
                    val fid = SlotKey.folderId(k) ?: return@let
                    val app = activity.application as GhostGalleonApp
                    val next = com.visorcraft.ghostgalleon.library.FolderCollectionBridge
                        .mirrorFolderToCollection(
                            app.settings.folders,
                            fid,
                            app.settings.collections,
                        )
                    app.updateSettings(app.settings.copy(collections = next))
                    Toast.makeText(
                        activity,
                        R.string.deck_mirrored_to_collection,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                SlotMenu.Choice.CANCEL,
                SlotMenu.Choice.ADD_TO_GRID,
                SlotMenu.Choice.REMOVE_FROM_COLLECTION,
                SlotMenu.Choice.MOVE_TO_TOP,
                SlotMenu.Choice.MOVE_UP,
                SlotMenu.Choice.MOVE_DOWN,
                SlotMenu.Choice.MOVE_TO_END,
                -> {}
            }
        }
        slotMenu = menu
        rootView?.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun gridEntryLabel(key: String): String {
        SlotKey.folderId(key)?.let { fid ->
            settings.folders[fid]?.name?.takeIf { it.isNotBlank() }?.let { return it }
        }
        SlotKey.romId(key)?.let { id ->
            roms.firstOrNull { it.id == id }?.let { rom ->
                return RomNames.display(rom, settings.romNames)
            }
        }
        return settings.customNames[key]
            ?: visibleByPkg[key]?.label
            ?: key.substringAfterLast(':').ifBlank { key }
    }

    private fun copyGridTitleToClipboard(title: String) {
        EntryActions.copyTitle(activity, title)
    }

    private fun markGridAsPlayed(key: String) {
        EntryActions.markAsPlayed(activity, key)
    }

    private fun clearGridPlayStats(key: String) {
        EntryActions.clearPlayStats(activity, key, gridEntryLabel(key))
    }

    private fun gridRelatedOptions(key: String): List<com.visorcraft.ghostgalleon.library.GameDetails.RelatedOption> {
        val rom = SlotKey.romId(key)?.let { id -> roms.firstOrNull { it.id == id } }
            ?: return emptyList()
        val chrome = settings.browseChrome
        return com.visorcraft.ghostgalleon.library.GameDetails.relatedOptions(
            platformId = rom.platformId,
            genre = rom.genre,
            developer = rom.developer,
            year = rom.year,
            allowPlatform = chrome.platformChips,
            allowGenre = chrome.genreChips,
            allowDeveloper = chrome.developerChips,
            allowYear = chrome.yearChips,
        )
    }

    /**
     * Jump to Game Mode with a related meta filter (platform/genre/…). Used
     * from Grid long-press / Details so Browse related works in both decks.
     */
    private fun applyGridRelated(
        option: com.visorcraft.ghostgalleon.library.GameDetails.RelatedOption,
    ) {
        val next = settings.browseChrome.sanitize(
            com.visorcraft.ghostgalleon.library.GameDetails.toBrowseQuery(
                option,
                sort = state.libraryBrowse.sort,
            ),
        )
        state.setLibraryBrowse(next, force = true)
        state.setMode(com.visorcraft.ghostgalleon.state.UIMode.GAME)
        Toast.makeText(
            activity,
            activity.resolveText(
                com.visorcraft.ghostgalleon.library.GameDetails.relatedOptionLabel(option),
            ),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showGridBrowseRelated(key: String) {
        val options = gridRelatedOptions(key)
        if (options.isEmpty()) return
        if (options.size == 1) {
            applyGridRelated(options[0])
            return
        }
        val labels = options.map {
            activity.resolveText(
                com.visorcraft.ghostgalleon.library.GameDetails.relatedOptionLabel(it),
            )
        }.toTypedArray()
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.details_browse_related,
            items = labels,
        ) { which ->
            if (which in options.indices) applyGridRelated(options[which])
        }
    }

    private fun showGridDetails(key: String) {
        val rom = SlotKey.romId(key)?.let { id -> roms.firstOrNull { it.id == id } }
        val identity = rom?.let {
            (activity.application as GhostGalleonApp).romIdentities[it.id]
        }
        val title = when {
            rom != null -> RomNames.display(rom, settings.romNames)
            else -> settings.customNames[key]
                ?: visibleByPkg[key]?.label
                ?: key
        }
        val body = com.visorcraft.ghostgalleon.library.GameDetails.body(
            com.visorcraft.ghostgalleon.library.GameDetails.Input(
                title = title,
                key = key,
                kind = if (rom != null) {
                    com.visorcraft.ghostgalleon.library.GameDetails.Kind.ROM
                } else {
                    com.visorcraft.ghostgalleon.library.GameDetails.Kind.APP
                },
                platformId = rom?.platformId,
                genre = rom?.genre,
                developer = rom?.developer,
                year = rom?.year,
                rating = rom?.rating,
                description = rom?.description,
                lastLaunchedMs = settings.lastLaunchedMs[key],
                playtimeMs = settings.playtimeMs[key] ?: 0L,
                favorite = key in settings.favorites,
                collections = com.visorcraft.ghostgalleon.library.GameDetails
                    .collectionsContaining(settings.collections, key),
                nowMs = System.currentTimeMillis(),
                identity = identity,
            ),
        )
        val related = if (rom != null) gridRelatedOptions(key) else emptyList()
        val builder = DeckDialogs.builder(activity)
            .setTitle(R.string.details_title)
            .setMessage(activity.resolveText(body))
            .setPositiveButton(R.string.action_ok, null)
            .setNeutralButton(R.string.action_copy_title) { _, _ ->
                copyGridTitleToClipboard(title)
            }
        when {
            rom == null && !SlotKey.isRom(key) && !SlotKey.isFolder(key) ->
                builder.setNegativeButton(R.string.action_app_info) { _, _ -> openAppInfo(key) }
            related.isNotEmpty() ->
                builder.setNegativeButton(R.string.action_browse_related) { _, _ ->
                    showGridBrowseRelated(key)
                }
        }
        val dialog = DeckDialogs.show(builder, activity)
        val fullHash = com.visorcraft.ghostgalleon.library.GameDetails.copyableHash(identity)
        if (fullHash != null) {
            dialog.findViewById<TextView>(android.R.id.message)?.setOnLongClickListener {
                copyGridHashToClipboard(fullHash)
                true
            }
        }
    }

    private fun copyGridHashToClipboard(hash: String) {
        val text = hash.trim().ifEmpty { return }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        if (clipboard == null) {
            Toast.makeText(activity, R.string.deck_clipboard_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                activity.getString(
                    R.string.identity_hash,
                    com.visorcraft.ghostgalleon.rom.IdentityStack.shortHash(text),
                ),
                text,
            ),
        )
        Toast.makeText(activity, R.string.action_copy, Toast.LENGTH_SHORT).show()
    }

    private fun openAppInfo(packageName: String) {
        EntryActions.openAppInfo(activity, packageName)
    }

    // Rename dialog: dark modal with an EditText prefilled with the current
    // display name (custom name when set, else the app label). Saving an
    // EMPTY field removes the override (same as "Reset name"); Cancel keeps
    // everything as-is. The settings update hot-reloads every surface.
    private fun showRenameDialog(key: String) {
        val romId = SlotKey.romId(key)
        val rom = romId?.let { id -> roms.firstOrNull { it.id == id } }
        val current = if (rom != null) {
            RomNames.display(rom, settings.romNames)
        } else {
            settings.customNames[key] ?: visibleByPkg[key]?.label ?: key
        }
        val original = rom?.name ?: visibleByPkg[key]?.label ?: key
        DeckDialogs.promptText(
            context = activity,
            title = activity.getString(R.string.deck_rename_named, original),
            initialText = current,
            hintRes = R.string.deck_display_name_hint,
            positiveRes = R.string.action_save,
        ) { raw ->
            val name = raw.trim()
            val app = activity.application as GhostGalleonApp
            if (rom != null) {
                app.updateSettings(
                    app.settings.copy(
                        romNames = RomNames.set(
                            app.settings.romNames,
                            rom.id,
                            name.takeIf { it.isNotEmpty() && it != rom.name },
                        ),
                    ),
                )
            } else {
                val names = app.settings.customNames
                app.updateSettings(app.settings.copy(
                    customNames = if (name.isEmpty() || name == current) {
                        names - key
                    } else {
                        names + (key to name)
                    }))
            }
        }
    }

    private fun closeSlotMenu() {
        DeckOverlays.detach(rootView, slotMenu?.view)
        slotMenu = null
    }

    // --- Move mode ---

    private fun startMove(slot: Int) {
        moveIndex = slot
        // The working copy covers the PADDED slot range so the lifted tile
        // can swap into a padded blank; dropping persists the extension.
        moveWorking = MutableList(slotCount) { settings.gridSlots.getOrNull(it) }
        dockBar?.showMoveHint(true)
        rebindVisibleCells()
    }

    private fun handleMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                val to = nav.move(from, action)
                pageGeometry?.let { geo ->
                    triggerScrollFeedback(
                        GridScrollFeedback.classify(action, from, to, geo.cellsPerPage, slotCount),
                    )
                }
                if (to != from) {
                    // 3DS behavior: the lifted tile swaps contents with
                    // whatever is in the target slot.
                    val working = moveWorking ?: return true
                    val tmp = working[from]
                    working[from] = working[to]
                    working[to] = tmp
                    moveIndex = to
                    ensureSelectionVisible()
                    rebindVisibleCells()
                    updatePageDots()
                }
            }
            Action.CONFIRM -> dropMove()
            Action.BACK -> cancelMove()
            else -> {}
        }
        return true // move mode swallows every action
    }

    // Drops the lifted tile: optionally after one last swap to a tapped
    // slot, then persists the working copy (settings -> full hot reload).
    private fun dropMove(tapSlot: Int? = null) {
        val from = moveIndex ?: return
        val working = moveWorking ?: return
        val finalSlot = if (tapSlot != null && tapSlot in working.indices) {
            if (tapSlot != from) {
                val tmp = working[from]
                working[from] = working[tapSlot]
                working[tapSlot] = tmp
            }
            tapSlot
        } else {
            from
        }
        val slots = working.toList()
        endMoveVisuals()
        updateGridSlots(slots, finalSlot)
    }

    private fun cancelMove() {
        endMoveVisuals()
        // Visible cells still show the discarded working copy: repopulate.
        rebindVisibleCells(repopulate = true)
        updatePageDots()
    }

    private fun endMoveVisuals() {
        cancelPulseAnimator()
        moveIndex = null
        moveWorking = null
        dockBar?.showMoveHint(false)
    }

    // --- App picker ---

    /** Search apps + ROMs without leaving Grid Mode. */
    private fun openLibrarySearch() {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = activity.getString(R.string.browse_search_library),
            onPick = { key ->
                closePicker()
                val existing = settings.gridSlots.indexOf(key)
                if (existing >= 0) {
                    state.selectSlot(existing, key)
                    return@AppPicker
                }
                val blank = GridSlots.firstEmptyIndex(settings.gridSlots)
                updateGridSlots(GridSlots.fill(settings.gridSlots, blank, key), blank)
                state.selectSlot(blank, key)
                Toast.makeText(activity, R.string.deck_added_to_grid, Toast.LENGTH_SHORT).show()
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun openPicker(slot: Int) {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            onPick = { key ->
                closePicker()
                updateGridSlots(GridSlots.fill(settings.gridSlots, slot, key), slot)
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun closePicker() {
        DeckOverlays.detach(rootView, picker?.view)
        DeckOverlays.hideIme(activity, rootView)
        picker = null
    }

    override fun handleAction(action: Action): Boolean {
        slotMenu?.let { return it.handleAction(action) }
        folderPanel?.let { return it.handleAction(action) }
        picker?.let { return it.handleAction(action) }
        moveIndex?.let { return handleMoveAction(action, it) }
        dockMove.index?.let { return handleDockMoveAction(action, it) }
        state.dockSlot?.let { return handleDockAction(action, it) }
        return when (action) {
            Action.CONFIRM -> {
                val slot = selectedIndex()
                val key = settings.gridSlots.getOrNull(slot)
                when {
                    key == null -> openPicker(slot)
                    SlotKey.isFolder(key) -> openFolder(key)
                    else -> launchSlotKey(
                        activity, state, roms, key, reason = LaunchReason.SLOT,
                    )
                }
                true
            }
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                // NAV DOWN on the last row leaves the grid and focuses the
                // dock (same column, clamped into the dock row).
                val current = selectedIndex()
                if (action == Action.NAV_DOWN && dockNav.isLastRow(current)) {
                    state.focusDock(dockNav.enterFromGrid(current))
                    return true
                }
                val target = nav.move(current, action)
                pageGeometry?.let { geo ->
                    triggerScrollFeedback(
                        GridScrollFeedback.classify(action, current, target, geo.cellsPerPage, slotCount),
                    )
                }
                state.selectSlot(target, settings.gridSlots.getOrNull(target))
                true
            }
            Action.SEARCH_LIBRARY -> {
                openLibrarySearch()
                true
            }
            Action.TOGGLE_FAVORITE -> {
                settings.gridSlots.getOrNull(selectedIndex())?.let { key ->
                    EntryActions.toggleFavorite(activity, key)
                }
                true
            }
            Action.SHOW_DETAILS -> {
                settings.gridSlots.getOrNull(selectedIndex())?.let { key ->
                    showGridDetails(key)
                }
                true
            }
            else -> false
        }
    }

    private inner class GridAdapter(
        private val context: Context,
        cellWidth: Int,
        iconSize: Int,
        cellHeight: Int,
        private val cellPadding: Int,
        labelSpec: GridLabelMetrics.Spec,
        // Horizontal page panels bind a fixed slice of the slot list; the
        // vertical grid uses the defaults (whole list from slot 0).
        private val slotOffset: Int = 0,
        private val fixedCount: Int? = null,
    ) : BaseAdapter() {
        // Mutable: the layout listener restamps these when the grid's
        // measured height changes (IME-race rebuild) without needing an
        // adapter notify.
        private var cellWidth = cellWidth
        private var iconSize = iconSize
        private var cellHeight = cellHeight
        private var labelSpec = labelSpec

        fun updateMetrics(
            cellWidth: Int,
            iconSize: Int,
            cellHeight: Int,
            labelSpec: GridLabelMetrics.Spec,
        ) {
            this.cellWidth = cellWidth
            this.iconSize = iconSize
            this.cellHeight = cellHeight
            this.labelSpec = labelSpec
        }

        override fun getCount() = fixedCount ?: slotCount
        // Padded tail cells are past the end of the stored list: null.
        override fun getItem(position: Int) = slotPackages().getOrNull(slotOffset + position)
        override fun getItemId(position: Int) = (slotOffset + position).toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val absPos = slotOffset + position
            val key = slotPackages().getOrNull(absPos)
            val frame = (convertView as? FrameLayout) ?: FrameLayout(context).apply {
                foreground = buildTileRipple(context)
            }
            val boundKey = frame.getTag(R.id.grid_cell_key) as? String
            // Reuse convertView when the slot key is unchanged and we are not
            // in move mode (contents may have swapped). Selection-only path
            // already uses applySelectionVisuals — mirror that for scroll.
            if (convertView != null &&
                boundKey == key &&
                moveIndex == null &&
                frame.childCount > 0
            ) {
                applySelectionVisuals(frame, absPos, absPos == selectedIndex(), animate = false)
            } else {
                populateFrame(frame, absPos)
                frame.setTag(R.id.grid_cell_key, key)
            }
            frame.layoutParams = AbsListView.LayoutParams(cellWidth, cellHeight)
            return frame
        }

        // (Re)builds a cell for the slot at [position]: a filled tile
        // (app icon or ROM cover/platform tile + optional label + scrimmed
        // corner badges) or a recessed blank "+" tile, then selection visuals.
        fun populateFrame(frame: FrameLayout, position: Int) {
            val density = context.resources.displayMetrics.density

            frame.removeAllViews()
            val key = slotPackages().getOrNull(position)
            val romEntry = key?.takeIf(SlotKey::isRom)?.let { romByKey[it] }
            val isFolder = SlotKey.isFolder(key)
            val appEntry = key
                ?.takeUnless { SlotKey.isRom(it) || SlotKey.isFolder(it) }
                ?.let { visibleByPkg[it] }
            when {
                romEntry != null -> {
                    frame.addView(buildRomCell(romEntry), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                isFolder && key != null -> {
                    frame.addView(buildFolderCell(key), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                appEntry != null -> {
                    frame.addView(buildFilledCell(appEntry), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                SlotKey.isRom(key) -> {
                    // Unresolvable ROM slot (deleted ROM, card out, library
                    // changed): the platform tile at 40% alpha with a dim
                    // "Missing" label. The slot stays filled — long-press
                    // still offers Move/Remove and launch toasts.
                    frame.addView(buildMissingCell(key!!), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                else -> {
                    // Blank slot (or a value that no longer resolves): recessed
                    // outline surface with a clean subtle "+".
                    frame.addView(TextView(context).apply {
                        setText(R.string.glyph_add)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                        setTextColor(BLANK_PLUS_IDLE_COLOR)
                        gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            val filled = key != null && (romEntry != null || appEntry != null || isFolder)
            val tokens = ThemePack.resolve(settings)
            val badgeSpec = GridBadgeMetrics.compute(
                cellWidthPx = cellWidth,
                cardRadiusDp = tokens.cardRadiusDp,
                density = density,
            )
            val badgePlacement = if (filled && key != null) {
                GridBadgeMetrics.placement(
                    isFavorite = key in settings.favorites,
                    isPinnedToDock = key in settings.dockSlots,
                )
            } else {
                GridBadgeMetrics.Placement(
                    GridBadgeMetrics.BadgeCorner.NONE,
                    GridBadgeMetrics.BadgeCorner.NONE,
                )
            }
            // Dock pin badge: luminous accent dot centered inside an 80% dark
            // circular scrim so it stays legible over bright cover artwork.
            if (badgePlacement.dockCorner != GridBadgeMetrics.BadgeCorner.NONE) {
                val scrim = FrameLayout(context).apply {
                    background = cornerBadgeScrim(context)
                    elevation = context.dpF(2)
                }
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(GridBadgeMetrics.dockDotColor(settings.accentColor))
                    }
                }
                scrim.addView(
                    dot,
                    FrameLayout.LayoutParams(
                        badgeSpec.dockDotSizePx,
                        badgeSpec.dockDotSizePx,
                        Gravity.CENTER,
                    ),
                )
                val isEnd = badgePlacement.dockCorner == GridBadgeMetrics.BadgeCorner.TOP_END
                val dockGravity = Gravity.TOP or if (isEnd) Gravity.END else Gravity.START
                frame.addView(scrim, FrameLayout.LayoutParams(
                    badgeSpec.badgeSizePx, badgeSpec.badgeSizePx, dockGravity).apply {
                    topMargin = badgeSpec.insetPx
                    if (isEnd) marginEnd = badgeSpec.insetPx else marginStart = badgeSpec.insetPx
                })
            }
            // Favorite badge: ★ inside a matching dark circular scrim at top-end.
            if (badgePlacement.favoriteCorner != GridBadgeMetrics.BadgeCorner.NONE) {
                val star = TextView(context).apply {
                    setText(R.string.glyph_favorite)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, badgeSpec.starTextSp)
                    setTextColor(GridBadgeMetrics.FAVORITE_STAR_COLOR)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    background = cornerBadgeScrim(context)
                    elevation = context.dpF(2)
                }
                frame.addView(star, FrameLayout.LayoutParams(
                    badgeSpec.badgeSizePx,
                    badgeSpec.badgeSizePx,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = badgeSpec.insetPx
                    marginEnd = badgeSpec.insetPx
                })
            }
            applySelectionVisuals(frame, position, position == selectedIndex(), animate = false)
        }

        private fun buildTileRipple(ctx: Context): RippleDrawable {
            val tokens = ThemePack.resolve(settings)
            val radiusPx = ctx.dpF(GridTileFeedbackMetrics.gridTileRippleRadiusDp(tokens))
            val rippleColor = GridTileFeedbackMetrics.rippleColorForSurface(
                surfaceFill = TileBackgrounds.surfaceColor(ctx),
                accent = settings.accentColor,
            )
            val mask = GradientDrawable().apply {
                cornerRadius = radiusPx
                setColor(Color.WHITE)
            }
            return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
        }

        private fun cornerBadgeScrim(ctx: Context): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(GridBadgeMetrics.BADGE_SCRIM_FILL)
                setStroke(ctx.strokePx(1), GridBadgeMetrics.BADGE_SCRIM_STROKE)
            }

        private fun buildCellLabel(
            labelText: String,
            color: Int = GridLabelMetrics.LABEL_UNFOCUSED_COLOR,
        ): TextView = TextView(context).apply {
            text = labelText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSpec.textSp)
            setTextColor(color)
            gravity = Gravity.CENTER
            maxLines = labelSpec.maxLines
            setLineSpacing(0f, 1.05f)
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            if (labelSpec.useReadabilityScrim) {
                setPadding(context.dp(4), context.dp(1.5f), context.dp(4), context.dp(1.5f))
                background = GradientDrawable().apply {
                    setColor(GridLabelMetrics.WALLPAPER_SCRIM_FILL)
                    cornerRadius = context.dpF(6)
                }
                setShadowLayer(
                    context.dpF(2),
                    0f,
                    context.dpF(1),
                    GridLabelMetrics.WALLPAPER_SHADOW_COLOR,
                )
            }
        }

        private fun labelParams(): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(labelSpec.topMarginDp) }

        private fun innerArtRadiusDp(): Int {
            val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
            val padDp = (cellPadding / density).toInt()
            val iconDp = (iconSize / density).toInt()
            return DeckCornerMetrics.gridTileInnerRadiusDp(
                tokens = ThemePack.resolve(settings),
                cellPaddingDp = padDp,
                iconSizeDp = iconDp,
            )
        }

        private fun buildFolderCell(key: String): View {
            val folderId = SlotKey.folderId(key)
            val spec = folderId?.let { settings.folders[it] }
            val name = spec?.name ?: folderId ?: context.getString(R.string.label_folder)
            val members = spec?.members.orEmpty()
            val count = members.size
            val previewKeys = FolderMosaicMetrics.previewMembers(members)
            val countLabel = context.resources.getQuantityString(
                R.plurals.count_items,
                count,
                count,
            )
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
                contentDescription = listOf(name, countLabel).joinToString(", ")
            }
            val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
            val innerRadiusDp = innerArtRadiusDp()
            val innerRadiusPx = context.dpF(innerRadiusDp)
            val sunkenFill = TileBackgrounds.surfaceSunken(context)
            val mosaic = FolderMosaicMetrics.compute(
                iconSizePx = iconSize,
                outerCornerRadiusPx = innerRadiusPx,
                density = density,
            )
            val isEmpty = previewKeys.isEmpty()
            val showMosaic = !isEmpty && mosaic.showMosaic
            val iconBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(sunkenFill)
                    cornerRadius = innerRadiusPx
                    if (isEmpty) {
                        setStroke(
                            context.strokePx(1),
                            TileBackgrounds.hairlineDivider(context),
                            context.dpF(4),
                            context.dpF(3),
                        )
                    } else {
                        setStroke(context.strokePx(1), TileBackgrounds.hairlineDivider(context))
                    }
                }
            }
            if (showMosaic) {
                iconBox.addView(
                    buildFolderMosaic(previewKeys, mosaic),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            } else {
                val vectorSize = (iconSize * 0.50f).toInt().coerceAtLeast(context.dp(20))
                val folderIcon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_folder)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    alpha = if (isEmpty) 0.72f else 1f
                    setColorFilter(
                        ThemePack.accentTextColor(settings.accentColor, sunkenFill),
                        PorterDuff.Mode.SRC_IN,
                    )
                }
                iconBox.addView(
                    folderIcon,
                    FrameLayout.LayoutParams(vectorSize, vectorSize, Gravity.CENTER),
                )
            }
            // Compact dark-scrimmed count pill inside the folder box corner so
            // the folder title fits cleanly in the single-line label budget.
            val countBadge = TextView(context).apply {
                text = FolderMosaicMetrics.countBadgeText(count)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(if (isEmpty) UiDimens.ALPHA_MUTED else UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                fontFeatureSettings = "tnum"
                gravity = Gravity.CENTER
                includeFontPadding = false
                minWidth = context.dp(18)
                setPadding(context.dp(5), context.dp(2), context.dp(5), context.dp(2))
                background = GradientDrawable().apply {
                    setColor(GridBadgeMetrics.BADGE_SCRIM_FILL)
                    cornerRadius = context.dpF(8)
                    setStroke(context.strokePx(1), GridBadgeMetrics.BADGE_SCRIM_STROKE)
                }
            }
            iconBox.addView(
                countBadge,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END,
                ).apply {
                    marginEnd = context.dp(4)
                    bottomMargin = context.dp(4)
                },
            )
            cell.addView(iconBox, LinearLayout.LayoutParams(iconSize, iconSize))
            if (settings.showLabels) {
                cell.addView(buildCellLabel(name), labelParams())
            }
            return cell
        }

        private fun buildFolderMosaic(
            previewKeys: List<String>,
            mosaic: FolderMosaicMetrics.Spec,
        ): View {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(mosaic.innerPadPx, mosaic.innerPadPx, mosaic.innerPadPx, mosaic.innerPadPx)
            }
            val tilePx = mosaic.miniTilePx
            for (rowIndex in 0 until FolderMosaicMetrics.GRID_COLUMNS) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                for (colIndex in 0 until FolderMosaicMetrics.GRID_COLUMNS) {
                    val slotIdx = rowIndex * FolderMosaicMetrics.GRID_COLUMNS + colIndex
                    val memberKey = previewKeys.getOrNull(slotIdx)
                    val quadrant = buildFolderMiniTile(memberKey, mosaic)
                    row.addView(
                        quadrant,
                        LinearLayout.LayoutParams(tilePx, tilePx).apply {
                            if (colIndex > 0) marginStart = mosaic.gapPx
                        },
                    )
                }
                container.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (rowIndex > 0) topMargin = mosaic.gapPx
                    },
                )
            }
            return container
        }

        private fun buildFolderMiniTile(
            memberKey: String?,
            mosaic: FolderMosaicMetrics.Spec,
        ): View {
            if (memberKey == null) {
                return View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(FOLDER_GHOST_WELL_FILL)
                        cornerRadius = mosaic.miniCornerRadiusPx
                        setStroke(context.strokePx(1), FOLDER_GHOST_WELL_STROKE)
                    }
                }
            }
            val app = activity.application as GhostGalleonApp
            val rom = memberKey.takeIf(SlotKey::isRom)?.let { romByKey[it] }
            if (rom != null) {
                return ArtTile.view(
                    context = context,
                    cache = app.artCache,
                    rom = rom,
                    targetPx = mosaic.miniTilePx,
                    cornerRadiusDp = mosaic.miniCornerRadiusDp,
                    artOverrides = settings.artOverrides,
                )
            }
            if (SlotKey.isRom(memberKey)) {
                return PlatformTile.view(
                    context = context,
                    platformId = SlotKey.platformIdOf(memberKey) ?: "rom",
                    cornerRadiusDp = mosaic.miniCornerRadiusDp,
                ).apply { alpha = 0.45f }
            }
            if (SlotKey.isFolder(memberKey)) {
                return FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(FOLDER_GHOST_WELL_FILL)
                        cornerRadius = mosaic.miniCornerRadiusPx
                    }
                    val glyphSize = (mosaic.miniTilePx * 0.65f).toInt().coerceAtLeast(context.dp(10))
                    addView(
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_folder)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setColorFilter(
                                ThemePack.accentTextColor(settings.accentColor),
                                PorterDuff.Mode.SRC_IN,
                            )
                        },
                        FrameLayout.LayoutParams(glyphSize, glyphSize, Gravity.CENTER),
                    )
                }
            }
            val radiusPx = mosaic.miniCornerRadiusPx
            return ImageView(context).apply {
                CustomIcon.bind(
                    this,
                    iconLoader,
                    app.artCache,
                    settings,
                    memberKey,
                    mosaic.miniTilePx,
                    cornerRadiusPx = radiusPx,
                )
            }
        }

        // ROM tile: cached artwork over the platform placeholder (async
        // fill; placeholder shows until/unless art arrives) plus the custom or
        // original ROM display name under the same showLabels setting as apps.
        private fun buildRomCell(entry: RomEntry): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            cell.addView(
                ArtTile.view(
                    context,
                    (activity.application as GhostGalleonApp).artCache,
                    entry,
                    targetPx = iconSize,
                    cornerRadiusDp = innerArtRadiusDp(),
                    artOverrides = settings.artOverrides,
                ),
                LinearLayout.LayoutParams(iconSize, iconSize))
            if (settings.showLabels) {
                val displayName = RomNames.display(entry, settings.romNames)
                cell.addView(buildCellLabel(displayName), labelParams())
            }
            return cell
        }

        // "Missing" tile for a rom:<id> slot whose entry no longer resolves
        // (deleted ROM, card out, library changed): the platform placeholder
        // at 40% alpha with a dim "Missing" label, so it reads as a broken
        // reference instead of a blank "+" while staying a normal filled
        // slot (long-press menu, launch toast, selection ring).
        private fun buildMissingCell(key: String): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            val innerRadiusDp = innerArtRadiusDp()
            val innerRadiusPx = context.dpF(innerRadiusDp)
            val missingBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(GridBadgeMetrics.MISSING_FRAME_FILL)
                    cornerRadius = innerRadiusPx
                    setStroke(
                        context.strokePx(1),
                        GridBadgeMetrics.MISSING_FRAME_STROKE,
                        context.dpF(4),
                        context.dpF(3),
                    )
                }
            }
            val tileInset = context.dp(2)
            val tile = PlatformTile.view(
                context,
                SlotKey.platformIdOf(key) ?: "rom",
                cornerRadiusDp = DeckCornerMetrics.nestedRadiusDp(
                    outerRadiusDp = innerRadiusDp,
                    insetDp = 2,
                    minRadiusDp = 4,
                ),
            )
            tile.alpha = 0.42f
            missingBox.addView(
                tile,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply { setMargins(tileInset, tileInset, tileInset, tileInset) },
            )
            cell.addView(missingBox, LinearLayout.LayoutParams(iconSize, iconSize))
            cell.addView(
                buildCellLabel(
                    context.getString(R.string.label_missing),
                    GridBadgeMetrics.MISSING_LABEL_COLOR,
                ),
                labelParams(),
            )
            return cell
        }

        private fun buildFilledCell(entry: AppEntry): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            val icon = ImageView(context)
            CustomIcon.bind(
                icon, iconLoader,
                (activity.application as GhostGalleonApp).artCache,
                settings, entry.packageName, iconSize,
                cornerRadiusPx = context.dpF(innerArtRadiusDp()),
            )
            cell.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            // Optional app-name label ("Show app names" setting).
            if (settings.showLabels) {
                cell.addView(buildCellLabel(entry.label), labelParams())
            }
            return cell
        }
    }

    private companion object {
        const val FOCUS_ANIM_MS = 80L
        val FOCUS_INTERPOLATOR = DecelerateInterpolator()
        val PULSE_INTERPOLATOR = AccelerateDecelerateInterpolator()
        const val BLANK_PLUS_IDLE_COLOR = 0x3DFFFFFF
        const val FOLDER_GHOST_WELL_FILL = 0x12FFFFFF
        const val FOLDER_GHOST_WELL_STROKE = 0x10FFFFFF
        const val PAGE_STRIP_RIPPLE_COLOR = 0x33FFFFFF
    }
}
