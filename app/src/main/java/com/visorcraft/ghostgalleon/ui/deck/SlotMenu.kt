package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

/**
 * Pure, host-tested navigation, destructive-row styling, and scroll-cue helpers
 * for [SlotMenu].
 */
internal object SlotMenuNav {

    enum class RowTone { STANDARD, DESTRUCTIVE, CANCEL }

    data class RowVisualSpec(
        /** Background fill to paint on the row (`0` means transparent over modal card). */
        val fillColor: Int,
        /** Effective opaque surface color underneath text (for WCAG contrast verification). */
        val effectiveSurface: Int,
        val strokeColor: Int,
        val strokeDp: Int,
        val textColor: Int,
        val rippleColor: Int,
    )

    const val PAGE_STEP = 5
    const val FOCUS_SCALE = 1.015f
    const val FOCUS_ANIM_MS = 90L

    internal const val DESTRUCTIVE_WASH_IDLE: Int = 0x14FF5252
    internal const val DESTRUCTIVE_WASH_SELECTED: Int = 0x2EFF5252
    internal const val DESTRUCTIVE_TEXT_SELECTED: Int = 0xFFFFB4AB.toInt()
    internal const val DESTRUCTIVE_RIPPLE: Int = 0x38FF5252
    internal const val STANDARD_RIPPLE: Int = 0x26FFFFFF

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

    fun rowTone(item: SlotMenu.Row.Item): RowTone = when {
        isDestructive(item) -> RowTone.DESTRUCTIVE
        item.choice == SlotMenu.Choice.CANCEL -> RowTone.CANCEL
        else -> RowTone.STANDARD
    }

    /** Pure alpha-composite of translucent [overlayArgb] over opaque [baseOpaqueRgb]. */
    fun blendOverOpaque(overlayArgb: Int, baseOpaqueRgb: Int): Int {
        val a = ((overlayArgb ushr 24) and 0xFF) / 255.0
        val orCh = (overlayArgb shr 16) and 0xFF
        val ogCh = (overlayArgb shr 8) and 0xFF
        val obCh = overlayArgb and 0xFF
        val br = (baseOpaqueRgb shr 16) and 0xFF
        val bg = (baseOpaqueRgb shr 8) and 0xFF
        val bb = baseOpaqueRgb and 0xFF
        val r = (orCh * a + br * (1.0 - a)).toInt().coerceIn(0, 255)
        val g = (ogCh * a + bg * (1.0 - a)).toInt().coerceIn(0, 255)
        val b = (obCh * a + bb * (1.0 - a)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Resolve row fill, stroke, text, and ripple tokens for every tone and
     * focus state so destructive rows receive unmistakable red tint + red focus
     * border while preserving WCAG AA (`>= 4.5:1`) contrast across all themes.
     */
    fun rowVisual(
        tone: RowTone,
        selected: Boolean,
        accent: Int,
        tokens: ThemeTokens,
    ): RowVisualSpec {
        val surface = ThemePack.cardFill(tokens)
        val focusStroke = OverlayPrimitives.accessibleFocusStroke(accent, surface)
        return when (tone) {
            RowTone.DESTRUCTIVE -> if (selected) {
                val fill = blendOverOpaque(DESTRUCTIVE_WASH_SELECTED, surface)
                RowVisualSpec(
                    fillColor = fill,
                    effectiveSurface = fill,
                    strokeColor = UiDimens.COLOR_DESTRUCTIVE,
                    strokeDp = 3,
                    textColor = DESTRUCTIVE_TEXT_SELECTED,
                    rippleColor = DESTRUCTIVE_RIPPLE,
                )
            } else {
                val fill = blendOverOpaque(DESTRUCTIVE_WASH_IDLE, surface)
                RowVisualSpec(
                    fillColor = fill,
                    effectiveSurface = fill,
                    strokeColor = 0,
                    strokeDp = 0,
                    textColor = UiDimens.COLOR_DESTRUCTIVE,
                    rippleColor = DESTRUCTIVE_RIPPLE,
                )
            }
            RowTone.CANCEL -> if (selected) {
                RowVisualSpec(
                    fillColor = surface,
                    effectiveSurface = surface,
                    strokeColor = focusStroke,
                    strokeDp = 3,
                    textColor = UiDimens.ALPHA_PRIMARY,
                    rippleColor = STANDARD_RIPPLE,
                )
            } else {
                RowVisualSpec(
                    fillColor = 0,
                    effectiveSurface = surface,
                    strokeColor = 0,
                    strokeDp = 0,
                    textColor = UiDimens.ALPHA_SECONDARY,
                    rippleColor = STANDARD_RIPPLE,
                )
            }
            RowTone.STANDARD -> if (selected) {
                RowVisualSpec(
                    fillColor = surface,
                    effectiveSurface = surface,
                    strokeColor = focusStroke,
                    strokeDp = 3,
                    textColor = UiDimens.ALPHA_PRIMARY,
                    rippleColor = STANDARD_RIPPLE,
                )
            } else {
                RowVisualSpec(
                    fillColor = 0,
                    effectiveSurface = surface,
                    strokeColor = 0,
                    strokeDp = 0,
                    textColor = UiDimens.ALPHA_PRIMARY,
                    rippleColor = STANDARD_RIPPLE,
                )
            }
        }
    }

    /** Opacity pair `(topCueAlpha, bottomCueAlpha)` signaling scrollable overflow. */
    fun scrollCueAlphas(canScrollUp: Boolean, canScrollDown: Boolean): Pair<Float, Float> =
        OverlayPrimitives.scrollCueAlphas(canScrollUp, canScrollDown)

    /** Trailing cue icon for drill-down/sub-modal actions and destructive rows. */
    @DrawableRes
    fun trailingGlyphRes(choice: SlotMenu.Choice, destructive: Boolean): Int? {
        if (destructive) return R.drawable.ic_close
        return when (choice) {
            SlotMenu.Choice.RENAME,
            SlotMenu.Choice.CUSTOM_ICON,
            SlotMenu.Choice.OPEN_WITH,
            SlotMenu.Choice.PLAYER,
            SlotMenu.Choice.SCREENS,
            SlotMenu.Choice.SET_ART,
            SlotMenu.Choice.ADD_TO_COLLECTION,
            SlotMenu.Choice.DETAILS,
            SlotMenu.Choice.BROWSE_RELATED,
            SlotMenu.Choice.APP_INFO,
            SlotMenu.Choice.NEW_FOLDER,
            SlotMenu.Choice.ADD_MEMBER,
            SlotMenu.Choice.OPEN_IN_GAME_MODE,
            SlotMenu.Choice.SEARCH_LIBRARY,
            -> R.drawable.ic_chevron_right
            else -> null
        }
    }

    /** Subtle tint for a row's trailing drill-down or destructive glyph. */
    fun trailingGlyphTint(tone: RowTone, selected: Boolean): Int = when (tone) {
        RowTone.DESTRUCTIVE ->
            if (selected) DESTRUCTIVE_TEXT_SELECTED else UiDimens.COLOR_DESTRUCTIVE
        RowTone.STANDARD, RowTone.CANCEL ->
            if (selected) UiDimens.ALPHA_SECONDARY else UiDimens.ALPHA_SUBTLE
    }

    /**
     * True when a direct touch tap should trigger a tactile `VIRTUAL_KEY`
     * pulse (gamepad nav/confirm haptics are emitted centrally by
     * `BaseDeckActivity.handleAction` so controller input never double-buzzes).
     */
    fun shouldTriggerTouchHaptic(
        hapticsEnabled: Boolean,
        isCancel: Boolean = false,
    ): Boolean = hapticsEnabled && !isCancel

    /**
     * Row indices to repaint when moving focus from [previousIndex] to
     * [nextIndex]. Emits at most 2 indices (`<= 2`) on incremental D-pad moves
     * and all `0 until count` on initial paint ([previousIndex] < 0).
     */
    fun dirtyIndices(previousIndex: Int, nextIndex: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val next = GridWrapNav.clampIndex(nextIndex, count)
        if (previousIndex < 0) return (0 until count).toList()
        val prev = GridWrapNav.clampIndex(previousIndex, count)
        return if (prev == next) emptyList() else listOf(prev, next)
    }
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
        val container: LinearLayout,
        val bgDrawable: GradientDrawable,
        val labelView: TextView,
        val glyphView: ImageView?,
        val item: Row.Item,
        val tone: SlotMenuNav.RowTone,
    )

    private var selection = 0
    private var paintedSelection = -1
    /** Indices into [rows] that are actionable (not headers). */
    private val actionRowIndices: List<Int> = SlotMenuNav.actionRowIndices(rows)
    private val actionRows = mutableListOf<BoundAction>()
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null
    private var themeTokens: ThemeTokens = ThemePack.GHOST

    private fun resolveTokens(): ThemeTokens {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) ThemePack.resolve(app.settings) else ThemePack.GHOST
    }

    private fun hapticsEnabled(): Boolean {
        val app = context.applicationContext
        return app is GhostGalleonApp && app.settings.haptics
    }

    @Suppress("DEPRECATION")
    private fun performHaptic(target: View?, constant: Int) {
        if (!hapticsEnabled() || target == null) return
        target.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    private fun playCue(cue: SoundCue?) {
        if (cue == null) return
        (context.applicationContext as? GhostGalleonApp)?.playSfx(cue)
    }

    val view: View by lazy {
        themeTokens = resolveTokens()
        val overlay = OverlayPrimitives.modalBackdrop(context) {
            playCue(SoundCue.BACK)
            onChoice(Choice.CANCEL)
        }
        OverlayPrimitives.bindModalPaneTitle(
            overlay,
            OverlayPrimitives.resolvePaneTitle(title, context.getString(R.string.label_select)),
        )
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
        val scrollCueColor = OverlayPrimitives.scrollCueTint(accentColor, surfaceColor)
        val locale = context.resources.configuration.locales[0]

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val hInset = context.dp(UiDimens.SPACE_XS)
            setPadding(hInset, context.dp(2), hInset, context.dp(2))
        }
        rows.forEachIndexed { index, row ->
            when (row) {
                is Row.Header -> {
                    if (index > 0) {
                        list.addView(
                            View(context).apply {
                                setBackgroundColor(dividerColor)
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            },
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
                        OverlayPrimitives.markHeading(this)
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
                    val tone = SlotMenuNav.rowTone(row)
                    val actionIdx = actionRows.size
                    val labelText = context.getString(label)
                    val cornerDp = themeTokens.cardRadiusDp.coerceAtMost(14)
                    val rowBg = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        cornerRadius = context.dpF(cornerDp)
                    }
                    val rippleColor = if (tone == SlotMenuNav.RowTone.DESTRUCTIVE) {
                        SlotMenuNav.DESTRUCTIVE_RIPPLE
                    } else {
                        SlotMenuNav.STANDARD_RIPPLE
                    }
                    val rowContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = context.dp(44)
                        contentDescription = labelText
                        background = OverlayPrimitives.roundedRipple(
                            context = context,
                            content = rowBg,
                            cornerRadiusDp = cornerDp,
                            rippleColor = rippleColor,
                        )
                        setPadding(
                            context.dp(UiDimens.SPACE_MD),
                            context.dp(10),
                            context.dp(UiDimens.SPACE_MD),
                            context.dp(10),
                        )
                        isClickable = true
                        setOnClickListener {
                            selection = actionIdx
                            paintRows(scrollToFocused = false, animateFocus = false)
                            if (SlotMenuNav.shouldTriggerTouchHaptic(
                                    hapticsEnabled(),
                                    isCancel = row.choice == Choice.CANCEL,
                                )
                            ) {
                                performHaptic(this, HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            playCue(OverlayAudioLogic.slotMenuConfirmCue(row))
                            onChoice(row.choice)
                        }
                    }
                    val actionTv = TextView(context).apply {
                        text = labelText
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    rowContainer.addView(
                        actionTv,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    val glyphRes = SlotMenuNav.trailingGlyphRes(
                        choice = row.choice,
                        destructive = tone == SlotMenuNav.RowTone.DESTRUCTIVE,
                    )
                    val glyphIv = glyphRes?.let { resId ->
                        val sizeDp = if (tone == SlotMenuNav.RowTone.DESTRUCTIVE) 14 else 16
                        ImageView(context).apply {
                            setImageResource(resId)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }.also { iv ->
                            rowContainer.addView(
                                iv,
                                LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp)).apply {
                                    marginStart = context.dp(UiDimens.SPACE_SM)
                                },
                            )
                        }
                    }
                    actionRows.add(BoundAction(rowContainer, rowBg, actionTv, glyphIv, row, tone))
                    list.addView(
                        rowContainer,
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

        val topScrollCue = OverlayPrimitives.scrollCueBar(context, scrollCueColor)
        val bottomScrollCue = OverlayPrimitives.scrollCueBar(context, scrollCueColor)

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
        OverlayPrimitives.bindScrollCues(scroll, topScrollCue, bottomScrollCue)
        scrollHost = scroll

        card.addView(
            topScrollCue,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.strokePx(2),
            ).apply {
                marginStart = context.dp(UiDimens.SPACE_SM)
                marginEnd = context.dp(UiDimens.SPACE_SM)
            },
        )
        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(
            bottomScrollCue,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.strokePx(2),
            ).apply {
                marginStart = context.dp(UiDimens.SPACE_SM)
                marginEnd = context.dp(UiDimens.SPACE_SM)
            },
        )

        card.addView(
            OverlayPrimitives.modalHintFooter(context, R.string.deck_hint_folder_close),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        paintRows(scrollToFocused = false, animateFocus = false)
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        playCue(SoundCue.MODAL_OPEN)
        overlay
    }

    private fun paintRow(bound: BoundAction, selected: Boolean, animateFocus: Boolean) {
        val spec = SlotMenuNav.rowVisual(
            tone = bound.tone,
            selected = selected,
            accent = accentColor,
            tokens = themeTokens,
        )
        bound.bgDrawable.setColor(spec.fillColor)
        if (spec.strokeDp > 0 && spec.strokeColor != 0) {
            bound.bgDrawable.setStroke(context.strokePx(spec.strokeDp), spec.strokeColor)
        } else {
            bound.bgDrawable.setStroke(0, Color.TRANSPARENT)
        }
        bound.container.isSelected = selected
        bound.labelView.setTextColor(spec.textColor)
        bound.labelView.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        bound.glyphView?.imageTintList = ColorStateList.valueOf(
            SlotMenuNav.trailingGlyphTint(bound.tone, selected),
        )
        OverlayPrimitives.applyFocusScale(
            view = bound.container,
            selected = selected,
            focusedScale = SlotMenuNav.FOCUS_SCALE,
            animate = animateFocus,
            durationMs = SlotMenuNav.FOCUS_ANIM_MS,
        )
    }

    private fun paintRows(
        scrollToFocused: Boolean = true,
        animateFocus: Boolean = true,
    ) {
        if (actionRows.isEmpty()) return
        val sel = GridWrapNav.clampIndex(selection, actionRows.size)
        selection = sel
        val dirty = SlotMenuNav.dirtyIndices(paintedSelection, sel, actionRows.size)
        for (index in dirty) {
            paintRow(actionRows[index], selected = index == sel, animateFocus = animateFocus)
        }
        paintedSelection = sel
        if (!scrollToFocused) return
        val focused = actionRows.getOrNull(sel)?.container ?: return
        scrollHost?.smoothScrollToCenter(focused)
    }

    fun handleAction(action: Action): Boolean {
        if (actionRows.isEmpty()) {
            if (action == Action.BACK) {
                playCue(SoundCue.BACK)
                onChoice(Choice.CANCEL)
            }
            return true
        }
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN, Action.PAGE_PREV, Action.PAGE_NEXT -> {
                val next = SlotMenuNav.move(selection, actionRows.size, action)
                if (next != selection) {
                    playCue(OverlayAudioLogic.slotMenuNavCue(action))
                }
                selection = next
                paintRows()
            }
            Action.CONFIRM -> {
                val rowIndex = actionRowIndices.getOrNull(selection) ?: return true
                val item = rows[rowIndex] as? Row.Item ?: return true
                playCue(OverlayAudioLogic.slotMenuConfirmCue(item))
                onChoice(item.choice)
            }
            Action.BACK -> {
                playCue(SoundCue.BACK)
                onChoice(Choice.CANCEL)
            }
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
