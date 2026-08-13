package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.visorcraft.ghostgalleon.ui.dp

/**
 * Compact session-ring overlay (Views, no Compose). Quick Panel card chrome.
 * Confirm / tap picks; End / long-press removes from the ring only; Back or
 * empty scrim tap closes. Does not launch or persist.
 */
object SessionSwitcherView {

    const val TAG = "session_switcher"

    fun attach(
        host: ViewGroup,
        entries: List<SessionRingEntry>,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
        onClose: () -> Unit,
    ) {
        detach(host)
        host.addView(
            build(host, entries.take(SessionRing.CAP), onPick, onRemove, onClose),
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
            Action.NAV_UP -> overlay.moveSelection(-1)
            Action.NAV_DOWN -> overlay.moveSelection(1)
            Action.CONFIRM -> overlay.pickSelected()
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
    ) : FrameLayout(context) {
        var selection: Int = 0
        val rows = mutableListOf<View>()

        fun moveSelection(delta: Int) {
            if (entries.isEmpty()) return
            selection = (selection + delta + entries.size) % entries.size
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
            val sel = selection.coerceIn(0, rows.lastIndex)
            rows.forEachIndexed { index, row ->
                row.background = if (index == sel) {
                    TileBackgrounds.selected(context, accent)
                } else {
                    null
                }
            }
            rows.getOrNull(sel)?.let { focused ->
                val scroll = focused.parent as? View
                val scroller = scroll?.parent as? ScrollView
                scroller?.post {
                    val y = focused.top - scroller.height / 3
                    scroller.smoothScrollTo(0, y.coerceAtLeast(0))
                }
            }
        }
    }

    private fun build(
        host: ViewGroup,
        entries: List<SessionRingEntry>,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
        onClose: () -> Unit,
    ): SwitcherOverlay {
        val context: Context = host.context
        val accent = tokens(context).accentColor
        val overlay = SwitcherOverlay(
            context, entries, onPick, onRemove, onClose, accent,
        ).apply {
            tag = TAG
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = TileBackgrounds.card(context)
            setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(20))
            isClickable = true
        }

        sheet.addView(TextView(context).apply {
            setText(R.string.session_switcher_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, context.dp(16))
        })

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (entries.isEmpty()) {
            list.addView(TextView(context).apply {
                setText(R.string.session_ring_empty)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(context.dp(12), context.dp(20), context.dp(12), context.dp(20))
            })
        } else {
            val yieldHint = context.getString(R.string.settings_player_uses_both_screens)
            entries.forEachIndexed { index, entry ->
                val row = rowView(context, entry, yieldHint, onPick, onRemove)
                overlay.rows.add(row)
                list.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index > 0) topMargin = context.dp(4)
                    },
                )
            }
        }

        val screenH = context.resources.displayMetrics.heightPixels
        val maxListH = (screenH * 0.56f).toInt().coerceAtLeast(context.dp(120))
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isClickable = true
            addView(
                list,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        sheet.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        scroll.post {
            val lp = scroll.layoutParams
            lp.height = list.height.coerceAtMost(maxListH)
            scroll.layoutParams = lp
        }

        overlay.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = context.dp(24)
                marginEnd = context.dp(24)
            },
        )
        overlay.paint()
        return overlay
    }

    private fun rowView(
        context: Context,
        entry: SessionRingEntry,
        yieldHint: String,
        onPick: (SessionRingEntry) -> Unit,
        onRemove: (String) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val subtitle = playerLine(entry, yieldHint)
        if (subtitle.isNotEmpty()) {
            texts.addView(TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0xBBFFFFFF.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            TextView(context).apply {
                setText(R.string.action_remove)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFFFF8A80.toInt())
                gravity = Gravity.CENTER
                setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
                isClickable = true
                setOnClickListener { onRemove(entry.key) }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(8) },
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

    private fun tokens(context: Context) =
        (context.applicationContext as? GhostGalleonApp)?.let { ThemePack.resolve(it.settings) }
            ?: ThemePack.GHOST
}
