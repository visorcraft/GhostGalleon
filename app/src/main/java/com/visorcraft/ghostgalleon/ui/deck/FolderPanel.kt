package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

/** Focus state for [FolderPanel]: either a member cell or the Mirror footer button. */
internal data class FolderNavState(
    val selectedCell: Int = 0,
    val mirrorFocused: Boolean = false,
)

/**
 * Pure, host-tested navigation state machine for [FolderPanel]'s 3-column
 * member grid and optional "Mirror to collection" footer button.
 */
internal object FolderPanelNav {

    const val COLUMNS = 3

    fun step(
        state: FolderNavState,
        memberCount: Int,
        columns: Int = COLUMNS,
        hasMirror: Boolean,
        action: Action,
    ): FolderNavState {
        if (memberCount <= 0) return FolderNavState(selectedCell = 0, mirrorFocused = false)
        val cols = columns.coerceAtLeast(1)
        val cur = GridWrapNav.clampIndex(state.selectedCell, memberCount)
        if (!hasMirror) {
            val next = when (action) {
                Action.NAV_LEFT, Action.NAV_RIGHT, Action.NAV_UP, Action.NAV_DOWN ->
                    GridWrapNav.moveAction(cur, memberCount, cols, action)
                Action.PAGE_PREV -> GridWrapNav.pageMove(cur, memberCount, cols * 2, deltaPages = -1)
                Action.PAGE_NEXT -> GridWrapNav.pageMove(cur, memberCount, cols * 2, deltaPages = 1)
                else -> cur
            }
            return FolderNavState(selectedCell = next, mirrorFocused = false)
        }

        val totalRows = (memberCount + cols - 1) / cols
        val curRow = cur / cols
        val curCol = cur % cols
        if (state.mirrorFocused) {
            return when (action) {
                Action.NAV_UP -> FolderNavState(
                    selectedCell = ((totalRows - 1) * cols + curCol).coerceAtMost(memberCount - 1),
                    mirrorFocused = false,
                )
                Action.NAV_DOWN -> FolderNavState(
                    selectedCell = curCol.coerceAtMost(memberCount - 1),
                    mirrorFocused = false,
                )
                Action.PAGE_PREV, Action.PAGE_NEXT ->
                    FolderNavState(selectedCell = cur, mirrorFocused = false)
                else -> FolderNavState(selectedCell = cur, mirrorFocused = true)
            }
        }

        return when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> FolderNavState(
                selectedCell = GridWrapNav.moveAction(cur, memberCount, cols, action),
                mirrorFocused = false,
            )
            Action.NAV_DOWN -> {
                if (curRow == totalRows - 1) {
                    FolderNavState(selectedCell = cur, mirrorFocused = true)
                } else {
                    FolderNavState(
                        selectedCell = ((curRow + 1) * cols + curCol).coerceAtMost(memberCount - 1),
                        mirrorFocused = false,
                    )
                }
            }
            Action.NAV_UP -> {
                if (curRow == 0) {
                    FolderNavState(selectedCell = cur, mirrorFocused = true)
                } else {
                    FolderNavState(
                        selectedCell = (curRow - 1) * cols + curCol,
                        mirrorFocused = false,
                    )
                }
            }
            Action.PAGE_PREV -> FolderNavState(
                selectedCell = GridWrapNav.pageMove(cur, memberCount, cols * 2, deltaPages = -1),
                mirrorFocused = false,
            )
            Action.PAGE_NEXT -> FolderNavState(
                selectedCell = GridWrapNav.pageMove(cur, memberCount, cols * 2, deltaPages = 1),
                mirrorFocused = false,
            )
            else -> FolderNavState(selectedCell = cur, mirrorFocused = false)
        }
    }

    /** Clamp selection safely after removing the currently selected member. */
    fun afterRemoval(
        state: FolderNavState,
        remainingCount: Int,
    ): FolderNavState {
        if (remainingCount <= 0) return FolderNavState(selectedCell = 0, mirrorFocused = false)
        return FolderNavState(
            selectedCell = GridWrapNav.clampIndex(state.selectedCell, remainingCount),
            mirrorFocused = false,
        )
    }
}

/**
 * Modal folder of members as a responsive, scrollable art grid. A launches
 * (or activates the Mirror footer when focused); B closes; long-press or Y
 * removes the selected member without closing.
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
    private var nav = FolderNavState()
    private val cells = mutableListOf<View>()
    private var memberKeys: MutableList<Pair<String, String>> = members.toMutableList()
    private var gridHost: LinearLayout? = null
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null
    private var topScrollCue: View? = null
    private var bottomScrollCue: View? = null
    private var subtitleView: TextView? = null
    private var mirrorButton: TextView? = null
    private var hintFooterView: TextView? = null
    private var thumbSizePx: Int = 0
    private var activeColumns: Int = FolderPanelNav.COLUMNS
    private var themeTokens: ThemeTokens = ThemePack.GHOST

    private val hasMirror: Boolean
        get() = onMirrorToCollection != null && memberKeys.isNotEmpty()

    val view: View by lazy {
        themeTokens = resolveTokens(context)
        val dm = context.resources.displayMetrics
        val layoutSpec = FolderPanelMetrics.compute(
            screenWidthPx = dm.widthPixels,
            screenHeightPx = dm.heightPixels,
            density = dm.density,
            memberCount = memberKeys.size,
        )
        activeColumns = layoutSpec.columns
        thumbSizePx = layoutSpec.thumbSizePx
        val cardWidth = layoutSpec.cardWidthPx

        val overlay = OverlayPrimitives.modalBackdrop(context, onClose).apply {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    cancelAnimations()
                }
            })
        }

        val card = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = UiDimens.SPACE_LG,
            padVerticalDp = 14,
        ).also { ViewCompat.setAccessibilityPaneTitle(it, title) }
        val header = OverlayPrimitives.modalHeader(
            context = context,
            title = title,
            subtitle = countSubtitle(memberKeys.size),
        )
        header.getChildAt(0)?.let { ViewCompat.setAccessibilityHeading(it, true) }
        subtitleView = (header.getChildAt(1) as? TextView)?.apply {
            fontFeatureSettings = "tnum"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        card.addView(
            header,
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
        gridHost = grid

        val scroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = layoutSpec.maxScrollHeightPx,
        ).apply {
            isVerticalFadingEdgeEnabled = true
            isScrollbarFadingEnabled = true
            setFadingEdgeLength(
                context.dp(
                    GridScrollFeedback.fadingEdgeLengthDp(layoutSpec.maxScrollHeightPx, dm.density),
                ),
            )
            setOnScrollChangeListener { _, _, _, _, _ -> updateScrollCues() }
            addView(
                grid,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        scrollHost = scroll

        val cardSurface = TileBackgrounds.surfaceColor(context)
        val cueHeightPx = context.dp(12)
        val topCue = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(cardSurface, Color.TRANSPARENT),
            )
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        val bottomCue = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(cardSurface, Color.TRANSPARENT),
            )
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        topScrollCue = topCue
        bottomScrollCue = bottomCue

        val scrollContainer = FrameLayout(context).apply {
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
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
        card.addView(
            scrollContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        if (onMirrorToCollection != null) {
            val mirrorRadius = themeTokens.cardRadiusDp.coerceAtMost(16)
            val mirrorRipple = GridTileFeedbackMetrics.rippleColorForSurface(
                surfaceFill = TileBackgrounds.surfaceColor(context),
                accent = accentColor,
            )
            val mirror = TextView(context).apply {
                setText(R.string.action_mirror_to_collection)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minHeight = context.dp(FolderPanelMetrics.MIRROR_MIN_HEIGHT_DP)
                foreground = roundedRipple(mirrorRadius, mirrorRipple)
                accessibilityDelegate = BUTTON_ROLE_DELEGATE
                setPadding(
                    context.dp(UiDimens.SPACE_MD),
                    context.dp(10),
                    context.dp(UiDimens.SPACE_MD),
                    context.dp(10),
                )
                setOnClickListener {
                    nav = nav.copy(mirrorFocused = true)
                    paintSelection()
                    onMirrorToCollection.invoke()
                }
            }
            mirrorButton = mirror
            card.addView(
                mirror,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(UiDimens.SPACE_SM) },
            )
        }

        val footer = OverlayPrimitives.modalHintFooter(context, hintResForState())
        hintFooterView = footer
        card.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        rebuildGrid()
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

    private fun countSubtitle(count: Int): String =
        context.resources.getQuantityString(R.plurals.count_items, count, count)

    private fun hintResForState(): Int =
        if (onRemoveMember != null && memberKeys.isNotEmpty()) {
            R.string.deck_hint_folder
        } else {
            R.string.deck_hint_folder_close
        }

    private fun rebuildGrid() {
        val grid = gridHost ?: return
        grid.removeAllViews()
        cells.clear()
        subtitleView?.text = countSubtitle(memberKeys.size)
        mirrorButton?.visibility = if (hasMirror) View.VISIBLE else View.GONE
        hintFooterView?.setText(hintResForState())

        if (memberKeys.isEmpty()) {
            nav = FolderNavState(selectedCell = 0, mirrorFocused = false)
            grid.addView(
                buildEmptyStateView(),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            topScrollCue?.visibility = View.GONE
            bottomScrollCue?.visibility = View.GONE
            return
        }

        val cols = activeColumns
        val thumbSize = thumbSizePx.coerceAtLeast(context.dp(FolderPanelMetrics.MIN_THUMB_DP))
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        val cellRadiusDp = DeckCornerMetrics.folderCellRadiusDp(themeTokens)
        val thumbRadiusDp = DeckCornerMetrics.folderThumbRadiusDp(
            tokens = themeTokens,
            cellPaddingDp = UiDimens.SPACE_XS,
            thumbSizeDp = (thumbSize / density).toInt(),
        )
        val thumbRadiusPx = context.dpF(thumbRadiusDp)
        val rippleColor = GridTileFeedbackMetrics.rippleColorForSurface(
            surfaceFill = TileBackgrounds.surfaceColor(context),
            accent = accentColor,
        )
        memberKeys.chunked(cols).forEachIndexed { rowIndex, rowMembers ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                clipChildren = false
                clipToPadding = false
            }
            rowMembers.forEachIndexed { colIndex, (key, label) ->
                val index = rowIndex * cols + colIndex
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    foreground = roundedRipple(cellRadiusDp, rippleColor)
                    accessibilityDelegate = BUTTON_ROLE_DELEGATE
                    contentDescription = context.getString(
                        R.string.format_dot_pair,
                        label,
                        context.getString(R.string.format_progress, index + 1, memberKeys.size),
                    )
                    setPadding(
                        context.dp(UiDimens.SPACE_XS),
                        context.dp(UiDimens.SPACE_XS),
                        context.dp(UiDimens.SPACE_XS),
                        context.dp(UiDimens.SPACE_XS),
                    )
                    setOnClickListener {
                        nav = FolderNavState(selectedCell = index, mirrorFocused = false)
                        paintSelection()
                        onLaunch(key)
                    }
                    setOnLongClickListener {
                        nav = FolderNavState(selectedCell = index, mirrorFocused = false)
                        paintSelection()
                        removeSelected()
                        true
                    }
                }
                val thumbBox = FrameLayout(context).apply {
                    background = TileBackgrounds.sunkenField(context, cornerRadiusDp = thumbRadiusDp)
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, thumbRadiusPx)
                        }
                    }
                    clipToOutline = true
                }
                if (SlotKey.isRom(key)) {
                    thumbBox.addView(
                        PlatformTile.view(
                            context,
                            SlotKey.platformIdOf(key) ?: "rom",
                            cornerRadiusDp = thumbRadiusDp,
                        ),
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                val thumb = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                thumbBox.addView(
                    thumb,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                cell.addView(thumbBox, LinearLayout.LayoutParams(thumbSize, thumbSize))
                onBindThumb?.invoke(thumb, key)
                cell.addView(
                    TextView(context).apply {
                        text = label
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(GridLabelMetrics.LABEL_UNFOCUSED_COLOR)
                        gravity = Gravity.CENTER
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                        setPadding(0, context.dp(UiDimens.SPACE_XS), 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                cells.add(cell)
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (colIndex > 0) marginStart = context.dp(UiDimens.SPACE_SM)
                    },
                )
            }
            // Keep partial last rows aligned to the active column grid.
            repeat(cols - rowMembers.size) {
                row.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = context.dp(UiDimens.SPACE_SM)
                    },
                )
            }
            grid.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) topMargin = context.dp(UiDimens.SPACE_SM)
                },
            )
        }
        paintSelection()
        scrollHost?.post { updateScrollCues() }
    }

    private fun buildEmptyStateView(): View {
        val sunkenFill = TileBackgrounds.surfaceSunken(context)
        val medallionSize = context.dp(FolderPanelMetrics.EMPTY_MEDALLION_SIZE_DP)
        val iconSize = context.dp(FolderPanelMetrics.EMPTY_MEDALLION_ICON_DP)
        val medallion = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(sunkenFill)
                setStroke(
                    context.strokePx(1),
                    TileBackgrounds.hairlineDivider(context),
                    context.dpF(4),
                    context.dpF(3),
                )
            }
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_folder)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setColorFilter(
                        FolderPanelMetrics.emptyMedallionTint(accentColor, sunkenFill),
                        PorterDuff.Mode.SRC_IN,
                    )
                },
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER),
            )
        }
        val message = TextView(context).apply {
            setText(R.string.deck_empty_folder)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            setLineSpacing(0f, 1.25f)
            gravity = Gravity.CENTER
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                context.dp(UiDimens.SPACE_LG),
                context.dp(20),
                context.dp(UiDimens.SPACE_LG),
                context.dp(20),
            )
            addView(
                medallion,
                LinearLayout.LayoutParams(medallionSize, medallionSize).apply {
                    bottomMargin = context.dp(UiDimens.SPACE_MD)
                },
            )
            addView(
                message,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun updateScrollCues() {
        val scroller = scrollHost ?: return
        val contentH = gridHost?.height ?: 0
        val cueState = FolderPanelMetrics.scrollCueState(
            scrollY = scroller.scrollY,
            contentHeightPx = contentH,
            viewportHeightPx = scroller.height,
        )
        topScrollCue?.visibility = if (cueState.showTopCue) View.VISIBLE else View.GONE
        bottomScrollCue?.visibility = if (cueState.showBottomCue) View.VISIBLE else View.GONE
    }

    private fun roundedRipple(cornerRadiusDp: Int, rippleColor: Int): RippleDrawable {
        val mask = GradientDrawable().apply {
            cornerRadius = context.dpF(cornerRadiusDp)
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    private fun cancelAnimations() {
        cells.forEach { it.animate().cancel() }
        mirrorButton?.animate()?.cancel()
        subtitleView?.animate()?.cancel()
        gridHost?.animate()?.cancel()
    }

    private fun paintSelection() {
        val activeCell = if (nav.mirrorFocused || cells.isEmpty()) {
            -1
        } else {
            GridWrapNav.clampIndex(nav.selectedCell, cells.size)
        }
        val cellRadiusDp = DeckCornerMetrics.folderCellRadiusDp(themeTokens)
        val surfaceFill = TileBackgrounds.surfaceColor(context)
        val focusStrokeColor = DeckThemeContrast.accessibleFocusStroke(accentColor, surfaceFill)
        val animatorsEnabled = DeckMotionPolicy.areSystemAnimatorsEnabled()
        cells.forEachIndexed { index, cell ->
            val selected = index == activeCell
            cell.isSelected = selected
            cell.background = if (selected) {
                TileBackgrounds.focusStroke(
                    context = context,
                    accent = focusStrokeColor,
                    fill = surfaceFill,
                    cornerRadiusDp = cellRadiusDp,
                    strokeDp = 3,
                )
            } else {
                null
            }
            val labelView = (cell as? LinearLayout)?.let { ll ->
                if (ll.childCount > 1) ll.getChildAt(ll.childCount - 1) as? TextView else null
            }
            if (labelView != null) {
                val style = GridLabelMetrics.labelStyleForRole(
                    if (selected) MoveChoreography.SlotRole.FOCUSED else MoveChoreography.SlotRole.NORMAL,
                )
                labelView.setTextColor(style.textColor)
                labelView.setTypeface(null, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val scale = if (selected) FolderPanelMetrics.CELL_FOCUS_SCALE else 1f
            cell.translationZ = if (selected) context.dpF(FolderPanelMetrics.CELL_FOCUS_ELEVATION_DP) else 0f
            cell.animate().cancel()
            if (DeckMotionPolicy.shouldAnimate(animatorsEnabled, cell.isLaidOut)) {
                cell.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(FolderPanelMetrics.FOCUS_ANIM_MS)
                    .setInterpolator(FOCUS_INTERPOLATOR)
                    .start()
            } else {
                cell.scaleX = scale
                cell.scaleY = scale
            }
        }

        mirrorButton?.let { btn ->
            val focused = nav.mirrorFocused && hasMirror
            val mirrorRadius = themeTokens.cardRadiusDp.coerceAtMost(16)
            val style = FolderPanelMetrics.mirrorButtonStyle(
                focused = focused,
                accent = accentColor,
                surfaceFill = surfaceFill,
                chipIdleFill = TileBackgrounds.chipIdleColor(context),
                hairlineColor = TileBackgrounds.hairlineDivider(context),
            )
            btn.isSelected = focused
            btn.background = GradientDrawable().apply {
                setColor(style.fillColor)
                cornerRadius = context.dpF(mirrorRadius)
                setStroke(context.strokePx(style.strokeDp), style.strokeColor)
            }
            btn.setTextColor(style.textColor)
            btn.setTypeface(null, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
            btn.translationZ = if (style.elevationDp > 0f) context.dpF(style.elevationDp) else 0f
            btn.animate().cancel()
            if (DeckMotionPolicy.shouldAnimate(animatorsEnabled, btn.isLaidOut)) {
                btn.animate()
                    .scaleX(style.scale)
                    .scaleY(style.scale)
                    .setDuration(FolderPanelMetrics.FOCUS_ANIM_MS)
                    .setInterpolator(FOCUS_INTERPOLATOR)
                    .start()
            } else {
                btn.scaleX = style.scale
                btn.scaleY = style.scale
            }
        }

        if (activeCell >= 0) {
            val focusedRow = cells.getOrNull(activeCell)?.parent as? View ?: return
            val scroller = scrollHost ?: return
            scroller.post {
                val contentH = gridHost?.height ?: 0
                val targetY = EnsureVisibleScroll.centeredScrollY(
                    itemTop = focusedRow.top,
                    itemBottom = focusedRow.bottom,
                    viewportHeight = scroller.height,
                    contentHeight = contentH,
                )
                scroller.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun removeSelected() {
        val remove = onRemoveMember ?: return
        if (memberKeys.isEmpty() || nav.mirrorFocused) return
        val idx = GridWrapNav.clampIndex(nav.selectedCell, memberKeys.size)
        val key = memberKeys[idx].first
        remove(key)
        memberKeys.removeAt(idx)
        nav = FolderPanelNav.afterRemoval(nav, memberKeys.size)
        rebuildGrid()
        if (!DeckMotionPolicy.shouldAnimate(DeckMotionPolicy.areSystemAnimatorsEnabled())) return
        gridHost?.let { grid ->
            grid.animate().cancel()
            grid.alpha = 0.82f
            grid.animate()
                .alpha(1f)
                .setDuration(110L)
                .setInterpolator(FOCUS_INTERPOLATOR)
                .start()
        }
        subtitleView?.let { sub ->
            sub.animate().cancel()
            sub.scaleX = 1.08f
            sub.scaleY = 1.08f
            sub.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(FOCUS_INTERPOLATOR)
                .start()
        }
    }

    fun handleAction(action: Action): Boolean {
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                if (memberKeys.isNotEmpty()) {
                    nav = FolderPanelNav.step(
                        state = nav,
                        memberCount = memberKeys.size,
                        columns = activeColumns,
                        hasMirror = hasMirror,
                        action = action,
                    )
                    paintSelection()
                }
            }
            Action.CONFIRM -> {
                if (nav.mirrorFocused && hasMirror) {
                    onMirrorToCollection?.invoke()
                } else if (memberKeys.isNotEmpty()) {
                    val idx = GridWrapNav.clampIndex(nav.selectedCell, memberKeys.size)
                    onLaunch(memberKeys[idx].first)
                }
            }
            Action.TOGGLE_MODE -> removeSelected()
            Action.BACK -> onClose()
            else -> {}
        }
        return true
    }

    private fun resolveTokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) ThemePack.resolve(app.settings) else ThemePack.GHOST
    }

    private companion object {
        val FOCUS_INTERPOLATOR = DecelerateInterpolator()
        val BUTTON_ROLE_DELEGATE = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
    }
}
