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
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.SessionRing
import com.visorcraft.ghostgalleon.rom.SessionRingEntry
import com.visorcraft.ghostgalleon.rom.playerSettingsLabel
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp

/**
 * Pure, host-tested navigation and active-entry helpers for [SessionSwitcherView].
 */
internal object SessionSwitcherNav {

    fun move(selection: Int, count: Int, action: Action): Int =
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN ->
                GridWrapNav.moveAction(selection, count, columns = 1, action = action)
            Action.PAGE_PREV -> GridWrapNav.pageMove(selection, count, pageSize = 4, deltaPages = -1)
            Action.PAGE_NEXT -> GridWrapNav.pageMove(selection, count, pageSize = 4, deltaPages = 1)
            else -> GridWrapNav.clampIndex(selection, count)
        }

    fun isActiveEntry(entryKey: String, activeKey: String?): Boolean =
        !activeKey.isNullOrBlank() && entryKey == activeKey

    fun initialSelection(
        entries: List<SessionRingEntry>,
        previousSelection: Int?,
        activeKey: String?,
    ): Int {
        if (entries.isEmpty()) return 0
        if (previousSelection != null) {
            return GridWrapNav.clampIndex(previousSelection, entries.size)
        }
        if (!activeKey.isNullOrBlank()) {
            val activeIdx = entries.indexOfFirst { it.key == activeKey }
            if (activeIdx >= 0) return activeIdx
        }
        return 0
    }
}

/**
 * Compact session-ring overlay (Views, no Compose) using shared
 * [OverlayPrimitives] framing. Confirm / tap picks; Y / Remove / long-press
 * removes from the ring only; Back or empty scrim tap closes.
 */
object SessionSwitcherView {

    const val TAG = "session_switcher"

    fun attach(
        host: ViewGroup,
        entries: List<SessionRingEntry>,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
        onClose: () -> Unit,
        activeKey: String? = activeSessionKey(host.context),
    ) {
        val prevSelection = (host.findViewWithTag<View>(TAG) as? SwitcherOverlay)?.selection
        detach(host)
        val capped = entries.take(SessionRing.CAP)
        host.addView(
            build(
                host = host,
                entries = capped,
                onPick = onPick,
                onRemove = onRemove,
                onClose = onClose,
                activeKey = activeKey,
                initialSelection = SessionSwitcherNav.initialSelection(
                    entries = capped,
                    previousSelection = prevSelection,
                    activeKey = activeKey,
                ),
            ),
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun detach(host: ViewGroup) {
        val existing = host.findViewWithTag<View>(TAG) ?: return
        host.removeView(existing)
    }

    fun handleAction(root: View, action: Action): Boolean {
        val overlay = overlay(root) ?: return false
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN, Action.PAGE_PREV, Action.PAGE_NEXT ->
                overlay.applyNavAction(action)
            Action.CONFIRM -> overlay.pickSelected()
            Action.TOGGLE_MODE -> overlay.removeSelected()
            Action.BACK, Action.OPEN_QUICK_PANEL -> overlay.onClose()
            else -> {}
        }
        return true
    }

    fun overlay(root: View): SwitcherOverlay? =
        root.findViewWithTag(TAG)

    class SwitcherOverlay(
        context: Context,
        val entries: List<SessionRingEntry>,
        val onPick: (SessionRingEntry) -> Unit,
        val onRemove: (String) -> Unit,
        val onClose: () -> Unit,
        private val accent: Int,
        private val rowRadiusDp: Int = 14,
        initialSelection: Int = 0,
    ) : FrameLayout(context) {
        var selection: Int = GridWrapNav.clampIndex(initialSelection, entries.size)
        val rows = mutableListOf<View>()
        internal var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null

        fun moveSelection(delta: Int) {
            if (entries.isEmpty()) return
            selection = GridWrapNav.move(
                index = selection,
                count = entries.size,
                columns = 1,
                dx = 0,
                dy = delta,
            )
            paint()
        }

        internal fun applyNavAction(action: Action) {
            if (entries.isEmpty()) return
            selection = SessionSwitcherNav.move(selection, entries.size, action)
            paint()
        }

        fun pickSelected() {
            entries.getOrNull(selection)?.let(onPick)
        }

        fun removeSelected() {
            entries.getOrNull(selection)?.let { onRemove(it.key) }
        }

        fun paint() {
            if (rows.isEmpty()) return
            val sel = GridWrapNav.clampIndex(selection, rows.size)
            selection = sel
            rows.forEachIndexed { index, row ->
                row.background = if (index == sel) {
                    TileBackgrounds.focusStroke(
                        context = context,
                        accent = accent,
                        fill = TileBackgrounds.surfaceColor(context),
                        cornerRadiusDp = rowRadiusDp,
                        strokeDp = 3,
                    )
                } else {
                    TileBackgrounds.chipIdle(context, cornerRadiusDp = rowRadiusDp)
                }
            }
            val focused = rows.getOrNull(sel) ?: return
            val scroller = scrollHost ?: return
            scroller.post {
                val targetY = (focused.top - (scroller.height - focused.height) / 2)
                    .coerceAtLeast(0)
                scroller.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun build(
        host: ViewGroup,
        entries: List<SessionRingEntry>,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
        onClose: () -> Unit,
        activeKey: String?,
        initialSelection: Int,
    ): SwitcherOverlay {
        val context: Context = host.context
        val themeTokens = tokens(context)
        val accent = themeTokens.accentColor
        val rowRadiusDp = themeTokens.cardRadiusDp.coerceAtMost(16)
        val overlay = SwitcherOverlay(
            context = context,
            entries = entries,
            onPick = onPick,
            onRemove = onRemove,
            onClose = onClose,
            accent = accent,
            rowRadiusDp = rowRadiusDp,
            initialSelection = initialSelection,
        ).apply {
            tag = TAG
            setBackgroundColor(OverlayPrimitives.MODAL_SCRIM_COLOR)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }

        val cardWidth = OverlayPrimitives.responsiveCardWidth(
            context = context,
            maxDp = 500,
            widthFrac = 0.88f,
            minMarginDp = UiDimens.SPACE_LG,
        )
        val sheet = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = UiDimens.SPACE_LG,
            padVerticalDp = 14,
        )

        val countSubtitle = if (entries.isEmpty()) {
            null
        } else {
            context.resources.getQuantityString(R.plurals.count_items, entries.size, entries.size)
        }
        sheet.addView(
            OverlayPrimitives.modalHeader(
                context = context,
                titleRes = R.string.session_switcher_title,
                subtitle = countSubtitle,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                setText(R.string.session_ring_empty)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(UiDimens.ALPHA_MUTED)
                gravity = Gravity.CENTER
                setPadding(
                    context.dp(UiDimens.SPACE_MD),
                    context.dp(20),
                    context.dp(UiDimens.SPACE_MD),
                    context.dp(20),
                )
            })
        } else {
            val yieldHint = context.getString(R.string.settings_player_uses_both_screens)
            entries.forEachIndexed { index, entry ->
                val isActive = SessionSwitcherNav.isActiveEntry(entry.key, activeKey)
                val row = rowView(
                    context = context,
                    entry = entry,
                    yieldHint = yieldHint,
                    isActive = isActive,
                    accent = accent,
                    themeTokens = themeTokens,
                    onPick = onPick,
                    onRemove = onRemove,
                )
                overlay.rows.add(row)
                list.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index > 0) topMargin = context.dp(UiDimens.SPACE_SM)
                    },
                )
            }
        }

        val scroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = context,
                heightFrac = 0.56f,
                minDp = 120,
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
        overlay.scrollHost = scroll
        sheet.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        sheet.addView(
            OverlayPrimitives.modalHintFooter(
                context = context,
                hintRes = if (entries.isEmpty()) {
                    R.string.deck_hint_folder_close
                } else {
                    R.string.deck_hint_folder
                },
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        overlay.addView(
            sheet,
            FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        overlay.paint()
        return overlay
    }

    private fun rowView(
        context: Context,
        entry: SessionRingEntry,
        yieldHint: String,
        isActive: Boolean,
        accent: Int,
        themeTokens: ThemeTokens,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                context.dp(UiDimens.SPACE_MD),
                context.dp(10),
                context.dp(UiDimens.SPACE_MD),
                context.dp(10),
            )
            isClickable = true
            setOnClickListener { onPick(entry) }
            setOnLongClickListener {
                onRemove(entry.key)
                true
            }
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        texts.addView(TextView(context).apply {
            text = entry.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val subtitle = playerLine(entry, yieldHint)
        if (isActive || subtitle.isNotEmpty()) {
            val metaRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (isActive) {
                metaRow.addView(
                    TextView(context).apply {
                        setText(R.string.label_now_playing)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTextColor(QuickPanelNav.accessibleOnAccent(accent))
                        setTypeface(typeface, Typeface.BOLD)
                        letterSpacing = 0.06f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        background = TileBackgrounds.accentPill(
                            context = context,
                            accent = accent,
                            cornerRadiusDp = 8,
                        )
                        setPadding(context.dp(8), context.dp(2), context.dp(8), context.dp(2))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (subtitle.isNotEmpty()) marginEnd = context.dp(UiDimens.SPACE_SM)
                    },
                )
            }
            if (subtitle.isNotEmpty()) {
                metaRow.addView(
                    TextView(context).apply {
                        text = subtitle
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(
                            if (isActive) {
                                ThemePack.accentTextColor(accent, ThemePack.cardFill(themeTokens))
                            } else {
                                UiDimens.ALPHA_MUTED
                            },
                        )
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            }
            texts.addView(
                metaRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(UiDimens.SPACE_XS) },
            )
        }
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            TextView(context).apply {
                setText(R.string.action_remove)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(UiDimens.COLOR_DESTRUCTIVE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 10)
                setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
                isClickable = true
                setOnClickListener { onRemove(entry.key) }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(UiDimens.SPACE_SM) },
        )
        return row
    }

    private fun playerLine(entry: SessionRingEntry, yieldHint: String): String {
        val id = entry.playerId?.trim().orEmpty()
        val name = if (id.isEmpty()) {
            ""
        } else {
            Platforms.ALL.asSequence()
                .flatMap { it.players.asSequence() }
                .firstOrNull { it.id == id }
                ?.displayName
                ?: id
        }
        return when {
            name.isNotEmpty() -> playerSettingsLabel(name, entry.policy, yieldHint)
            entry.policy == SessionPolicy.YIELD_BOTH -> yieldHint
            else -> ""
        }
    }

    private fun activeSessionKey(context: Context): String? =
        (context.applicationContext as? GhostGalleonApp)?.let {
            it.sessionSurface?.key ?: it.openSession?.key
        }

    private fun tokens(context: Context): ThemeTokens =
        (context.applicationContext as? GhostGalleonApp)?.let { ThemePack.resolve(it.settings) }
            ?: ThemePack.GHOST
}
