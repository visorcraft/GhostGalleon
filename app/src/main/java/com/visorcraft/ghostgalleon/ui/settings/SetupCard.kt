package com.visorcraft.ghostgalleon.ui.settings

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.SetupNeeds
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.resolveText

/**
 * First-run / empty-library guided card, plus one-time companion chrome
 * discoverability (Resume + status pill). Hosted as a full-screen overlay
 * on the primary deck when [SetupNeeds.shouldShow] is true.
 */
object SetupCard {

    fun snapshot(app: GhostGalleonApp, installed: (String) -> Boolean): SetupNeeds.Snapshot {
        val players = Platforms.ALL.flatMap { it.players }
            .map { PlayerResolver.packageName(it) }
            .distinct()
            .count { installed(it) }
        val chrome = app.settings.browseChrome
        return SetupNeeds.Snapshot(
            setupDismissed = app.settings.setupDismissed,
            romTreeCount = app.settings.romTreeUris.size,
            romEntryCount = app.romEntries.size,
            installedPlayerCount = players,
            hasSgdbKey = !app.settings.sgdbApiKey.isNullOrBlank(),
            resumeChip = chrome.resumeChip,
            statusPill = chrome.deckStatusPill,
            chromeDiscoverDismissed = app.settings.chromeDiscoverDismissed,
        )
    }

    fun build(
        activity: AppCompatActivity,
        accent: Int,
        snap: SetupNeeds.Snapshot,
        onAddRomFolder: () -> Unit,
        onSgdbKey: () -> Unit,
        onGetEmulator: () -> Unit,
        onOpenSettings: () -> Unit,
        onEnableCompanionChrome: () -> Unit,
        onDismiss: () -> Unit,
    ): View {
        val chromeOnly = SetupNeeds.isChromeDiscoverOnly(snap)
        val overlay = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xEE000000.toInt())
            setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24))
            isClickable = true
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.card(activity)
            setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(18))
        }
        card.addView(TextView(activity).apply {
            setText(
                if (chromeOnly) R.string.setup_chrome_title else R.string.setup_welcome,
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        card.addView(TextView(activity).apply {
            setText(
                if (chromeOnly) R.string.setup_chrome_intro else R.string.setup_intro,
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(8), 0, activity.dp(16))
        })
        SetupNeeds.checklist(snap).forEach { (label, done) ->
            card.addView(TextView(activity).apply {
                text = activity.getString(
                    R.string.format_prefixed,
                    activity.getString(
                        if (done) R.string.glyph_check_prefix else R.string.glyph_open_prefix,
                    ),
                    activity.resolveText(label),
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (done) accent else Color.WHITE)
                setPadding(0, activity.dp(6), 0, activity.dp(6))
            })
        }
        fun actionBtn(label: String, filled: Boolean, onClick: () -> Unit) =
            TextView(activity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(if (filled) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12))
                background = if (filled) {
                    TileBackgrounds.selected(activity, accent)
                } else {
                    TileBackgrounds.card(activity)
                }
                setOnClickListener { onClick() }
            }
        if (chromeOnly) {
            card.addView(
                actionBtn(
                    activity.getString(R.string.setup_enable_companion_chrome),
                    true,
                    onEnableCompanionChrome,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(16) },
            )
            card.addView(
                actionBtn(
                    activity.getString(R.string.action_skip_for_now),
                    false,
                    onDismiss,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(8) },
            )
        } else {
            card.addView(
                actionBtn(
                    activity.getString(R.string.setup_add_rom_folder),
                    true,
                    onAddRomFolder,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(16) },
            )
            if (snap.installedPlayerCount == 0) {
                card.addView(
                    actionBtn(
                        activity.getString(R.string.setup_get_emulator),
                        false,
                        onGetEmulator,
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = activity.dp(8) },
                )
            }
            card.addView(
                actionBtn(
                    activity.getString(
                        if (snap.hasSgdbKey) R.string.setup_sgdb_set
                        else R.string.setup_sgdb_optional,
                    ),
                    false,
                    onSgdbKey,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(8) },
            )
            card.addView(
                actionBtn(
                    activity.getString(R.string.setup_enable_companion_chrome),
                    false,
                    onEnableCompanionChrome,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(8) },
            )
            card.addView(
                actionBtn(
                    activity.getString(R.string.setup_open_settings),
                    false,
                    onOpenSettings,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(8) },
            )
            card.addView(
                actionBtn(
                    activity.getString(R.string.action_skip_for_now),
                    false,
                    onDismiss,
                ),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = activity.dp(8) },
            )
        }
        overlay.addView(
            card,
            LinearLayout.LayoutParams(
                minOf(
                    activity.dp(420),
                    (activity.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return overlay
    }
}
