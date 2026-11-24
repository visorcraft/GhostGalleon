package com.visorcraft.ghostgalleon.ui.deck

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.PlayStats
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.rom.LaunchFace
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.PlayerTemplate
import com.visorcraft.ghostgalleon.rom.PlotConfirm
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.StagePlot
import com.visorcraft.ghostgalleon.rom.StagePlots
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.toast

/** Pure, host-tested choice and state helpers for [EntryActions] dialogs. */
internal object EntryActionChoices {

    data class PlayerProfileRow(
        val playerId: String?,
        val displayName: String,
        val installed: Boolean,
        val selected: Boolean,
        val isPlatformDefault: Boolean,
    )

    data class StagePlotRow(
        val policy: SessionPolicy?,
        val face: LaunchFace,
        val clear: Boolean,
        val selected: Boolean,
    )

    /**
     * Build per-ROM player profile rows flagging installed vs missing emulators
     * and the currently active selection, ending with the platform-default row.
     */
    fun playerProfileRows(
        players: List<PlayerTemplate>,
        currentProfileId: String?,
        isInstalled: (packageName: String) -> Boolean,
    ): List<PlayerProfileRow> = buildList(players.size + 1) {
        players.forEach { player ->
            add(
                PlayerProfileRow(
                    playerId = player.id,
                    displayName = player.displayName,
                    installed = isInstalled(PlayerResolver.packageName(player)),
                    selected = player.id == currentProfileId,
                    isPlatformDefault = false,
                ),
            )
        }
        add(
            PlayerProfileRow(
                playerId = null,
                displayName = "",
                installed = true,
                selected = currentProfileId == null,
                isPlatformDefault = true,
            ),
        )
    }

    /** Preferred player id currently governing [romId] (per-ROM profile else platform default). */
    fun preferredPlayerId(
        romId: String,
        platformId: String,
        romProfiles: Map<String, String>,
        defaultPlayers: Map<String, String>,
    ): String? = RomProfiles.preferredPlayerId(romId, romProfiles, defaultPlayers[platformId])

    /** Build stage-plot options for single or dual display with exact selection flags. */
    fun stagePlotRows(dual: Boolean, current: StagePlot?): List<StagePlotRow> {
        fun isSelected(policy: SessionPolicy?, face: LaunchFace, clear: Boolean): Boolean = when {
            clear -> current == null
            current == null -> false
            else -> current.policy == policy && current.launchFace == face
        }
        return buildList {
            add(StagePlotRow(null, LaunchFace.AUTO, clear = true, selected = isSelected(null, LaunchFace.AUTO, true)))
            if (dual) {
                listOf(LaunchFace.AUTO, LaunchFace.INTERACTIVE, LaunchFace.OTHER).forEach { face ->
                    add(
                        StagePlotRow(
                            policy = SessionPolicy.KEEP_COMPANION,
                            face = face,
                            clear = false,
                            selected = isSelected(SessionPolicy.KEEP_COMPANION, face, false),
                        ),
                    )
                }
            } else {
                add(
                    StagePlotRow(
                        policy = SessionPolicy.KEEP_COMPANION,
                        face = LaunchFace.AUTO,
                        clear = false,
                        selected = isSelected(SessionPolicy.KEEP_COMPANION, LaunchFace.AUTO, false),
                    ),
                )
            }
            add(
                StagePlotRow(
                    policy = SessionPolicy.YIELD_BOTH,
                    face = LaunchFace.AUTO,
                    clear = false,
                    selected = isSelected(SessionPolicy.YIELD_BOTH, LaunchFace.AUTO, false),
                ),
            )
        }
    }

    /** Resource label describing a player's built-in session policy. */
    @StringRes
    fun builtInPolicyLabelRes(builtIn: SessionPolicy): Int = when (builtIn) {
        SessionPolicy.YIELD_BOTH -> R.string.settings_stage_yield
        SessionPolicy.KEEP_COMPANION -> R.string.settings_stage_keep
    }

    /** First index matching [predicate] (active emulator/profile/stage-plot row), or `-1`. */
    fun <T> selectedRowIndex(rows: List<T>, predicate: (T) -> Boolean): Int =
        rows.indexOfFirst(predicate)
}

/** Shared, locale-safe library entry actions. */
object EntryActions {

    fun markAsPlayed(activity: AppCompatActivity, key: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val next = SessionMath.stampLastPlayed(
            PlayStats(live.lastLaunchedMs, live.playtimeMs),
            key,
            System.currentTimeMillis(),
        )
        app.updateSettings(live.copy(lastLaunchedMs = next.lastLaunchedMs))
        activity.toast(R.string.stats_marked_played)
    }

    fun clearPlayStats(activity: AppCompatActivity, key: String, label: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val stats = PlayStats(live.lastLaunchedMs, live.playtimeMs)
        if (!SessionMath.hasStats(stats, key)) {
            activity.toast(R.string.stats_no_play_stats)
            return
        }
        DeckDialogs.confirm(
            context = activity,
            titleRes = R.string.stats_clear_play_stats,
            message = activity.getString(R.string.stats_clear_confirm, label),
            positiveRes = R.string.action_clear,
            destructive = true,
        ) {
            val current = app.settings
            val next = SessionMath.clearStats(
                PlayStats(current.lastLaunchedMs, current.playtimeMs),
                key,
            )
            app.updateSettings(
                current.copy(
                    lastLaunchedMs = next.lastLaunchedMs,
                    playtimeMs = next.totalPlaytimeMs,
                ),
            )
            activity.toast(R.string.stats_cleared)
        }
    }

    fun toggleFavorite(activity: AppCompatActivity, key: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val result = CollectionsOps.toggleFavoriteWithRail(
            live.favorites,
            live.collections,
            key,
        )
        app.updateSettings(
            live.copy(favorites = result.favorites, collections = result.collections),
        )
        app.playSfx(OverlayAudioLogic.favoriteCueFor(result.added))
        activity.toast(
            if (result.added) R.string.deck_added_favorite
            else R.string.deck_removed_favorite,
        )
    }

    fun openWith(
        activity: AppCompatActivity,
        rom: RomEntry,
        onLaunch: (playerId: String) -> Unit,
    ) {
        val platform = Platforms.byId(rom.platformId) ?: return
        val pm = activity.packageManager
        val installed = PlayerResolver.installedPlayers(platform) { pm.isInstalled(it) }
        val app = activity.application as GhostGalleonApp
        if (installed.isEmpty()) {
            app.playSfx(SoundCue.ERROR_DENY)
            activity.toast(R.string.deck_no_players_installed)
            return
        }
        val preferred = EntryActionChoices.preferredPlayerId(
            romId = rom.id,
            platformId = rom.platformId,
            romProfiles = app.settings.romProfiles,
            defaultPlayers = app.settings.defaultPlayers,
        )
        val labels = installed.map { player ->
            if (player.id == preferred) {
                activity.getString(R.string.format_selected_check, player.displayName)
            } else {
                player.displayName
            }
        }.toTypedArray()
        val checkedIdx = EntryActionChoices.selectedRowIndex(installed) { it.id == preferred }
        DeckDialogs.chooseItem(
            context = activity,
            titleRes = R.string.action_open_with,
            items = labels,
            emitPickSound = false,
            checkedIndex = checkedIdx,
        ) { which ->
            val player = installed.getOrNull(which) ?: return@chooseItem
            app.updateSettings(
                app.settings.copy(
                    defaultPlayers = app.settings.defaultPlayers +
                        (rom.platformId to player.id),
                ),
                notify = false,
            )
            onLaunch(player.id)
        }
    }

    fun playerProfile(activity: AppCompatActivity, rom: RomEntry) {
        val platform = Platforms.byId(rom.platformId) ?: return
        val players = platform.players
        if (players.isEmpty()) {
            activity.toast(R.string.deck_no_players_platform)
            return
        }
        val app = activity.application as GhostGalleonApp
        val pm = activity.packageManager
        val rows = EntryActionChoices.playerProfileRows(
            players = players,
            currentProfileId = app.settings.romProfiles[rom.id],
            isInstalled = { pm.isInstalled(it) },
        )
        val missingLabel = activity.getString(R.string.label_missing)
        val labels = rows.map { row ->
            when {
                row.isPlatformDefault -> activity.getString(
                    if (row.selected) R.string.deck_platform_default_selected
                    else R.string.deck_platform_default,
                )
                else -> {
                    val base = if (row.installed) {
                        row.displayName
                    } else {
                        activity.getString(R.string.format_dot_pair, row.displayName, missingLabel)
                    }
                    if (row.selected) {
                        activity.getString(R.string.format_selected_check, base)
                    } else {
                        base
                    }
                }
            }
        }.toTypedArray()
        val dialogTitle = activity.getString(
            R.string.format_dot_pair,
            activity.getString(R.string.deck_player_profile),
            rom.name,
        )
        val checkedIdx = EntryActionChoices.selectedRowIndex(rows) { it.selected }
        DeckDialogs.chooseItem(
            context = activity,
            title = dialogTitle,
            items = labels,
            checkedIndex = checkedIdx,
        ) { which ->
            val chosen = rows.getOrNull(which) ?: return@chooseItem
            val live = app.settings
            val nextProfiles = if (chosen.playerId == null) {
                RomProfiles.clearProfile(live.romProfiles, rom.id)
            } else {
                RomProfiles.setProfile(live.romProfiles, rom.id, chosen.playerId)
            }
            app.updateSettings(live.copy(romProfiles = nextProfiles))
            activity.toast(R.string.deck_player_saved)
        }
    }

    fun openAppInfo(activity: AppCompatActivity, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { activity.toast(R.string.deck_cannot_open_app_info) }
    }

    /**
     * Per-title stage plot: Default / KEEP / YIELD (+ launch face on dual).
     * Confirm when the plot fights the preferred player's built-in policy.
     */
    fun screensPlot(activity: AppCompatActivity, rom: RomEntry) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val preferred = EntryActionChoices.preferredPlayerId(
            romId = rom.id,
            platformId = rom.platformId,
            romProfiles = live.romProfiles,
            defaultPlayers = live.defaultPlayers,
        )
        val builtIn = SessionPolicy.forPlayerId(preferred)
        val current = live.stagePlots[rom.id]
        val dual = app.displayConfig.mode == SurfaceMode.DUAL
        val rows = EntryActionChoices.stagePlotRows(dual = dual, current = current)

        fun labelFor(row: EntryActionChoices.StagePlotRow): String = when {
            row.clear -> activity.getString(
                R.string.format_dot_pair,
                activity.getString(R.string.label_default),
                activity.getString(EntryActionChoices.builtInPolicyLabelRes(builtIn)),
            )
            row.policy == SessionPolicy.YIELD_BOTH ->
                activity.getString(R.string.settings_stage_yield)
            dual -> {
                val faceLabel = when (row.face) {
                    LaunchFace.AUTO -> activity.getString(R.string.label_auto)
                    LaunchFace.INTERACTIVE -> activity.getString(R.string.settings_stage_face_interactive)
                    LaunchFace.OTHER, LaunchFace.COMPANION -> activity.getString(R.string.settings_stage_face_other)
                }
                activity.getString(
                    R.string.format_dot_pair,
                    activity.getString(R.string.settings_stage_keep),
                    faceLabel,
                )
            }
            else -> activity.getString(R.string.settings_stage_keep)
        }

        val labels = rows.map { row ->
            val base = labelFor(row)
            if (row.selected) activity.getString(R.string.format_selected_check, base) else base
        }.toTypedArray()

        fun writePlot(row: EntryActionChoices.StagePlotRow) {
            val now = app.settings
            val nextPlots = if (row.clear) {
                now.stagePlots - rom.id
            } else {
                now.stagePlots + (rom.id to StagePlot(row.policy, row.face))
            }
            app.updateSettings(now.copy(stagePlots = nextPlots))
            activity.toast(
                R.string.format_colon_pair,
                activity.getString(R.string.settings_stage_plot),
                labelFor(row),
            )
        }

        fun applyOption(row: EntryActionChoices.StagePlotRow) {
            if (row.clear) {
                writePlot(row)
                return
            }
            val confirm = StagePlots.confirmFor(builtIn, row.policy)
            if (confirm == PlotConfirm.NONE) {
                writePlot(row)
                return
            }
            val message = when (confirm) {
                PlotConfirm.KEEP_ON_YIELD_PLAYER -> R.string.confirm_keep_on_yield_player
                PlotConfirm.YIELD_ON_KEEP_PLAYER -> R.string.confirm_yield_on_keep_player
                PlotConfirm.NONE -> return
            }
            DeckDialogs.confirm(
                context = activity,
                titleRes = R.string.settings_stage_plot,
                messageRes = message,
                positiveRes = R.string.action_ok,
            ) { writePlot(row) }
        }

        val dialogTitle = activity.getString(
            R.string.format_dot_pair,
            activity.getString(R.string.settings_stage_plot),
            rom.name,
        )
        val checkedIdx = EntryActionChoices.selectedRowIndex(rows) { it.selected }
        DeckDialogs.chooseItem(
            context = activity,
            title = dialogTitle,
            items = labels,
            checkedIndex = checkedIdx,
        ) { which ->
            rows.getOrNull(which)?.let(::applyOption)
        }
    }

    fun copyTitle(activity: AppCompatActivity, title: String) {
        val text = title.trim().ifEmpty { return }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
            as? ClipboardManager
        if (clipboard == null) {
            activity.toast(R.string.deck_clipboard_unavailable)
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
            activity.getString(R.string.clipboard_title_label),
            text,
        ))
        activity.toast(R.string.deck_copied_title)
    }
}
