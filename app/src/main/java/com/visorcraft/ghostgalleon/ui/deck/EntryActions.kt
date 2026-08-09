package com.visorcraft.ghostgalleon.ui.deck

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.PlayStats
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.rom.isInstalled
import com.visorcraft.ghostgalleon.ui.toast

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
        AlertDialog.Builder(activity)
            .setTitle(R.string.stats_clear_play_stats)
            .setMessage(activity.getString(R.string.stats_clear_confirm, label))
            .setPositiveButton(R.string.action_clear) { _, _ ->
                val next = SessionMath.clearStats(
                    PlayStats(live.lastLaunchedMs, live.playtimeMs),
                    key,
                )
                app.updateSettings(
                    live.copy(
                        lastLaunchedMs = next.lastLaunchedMs,
                        playtimeMs = next.totalPlaytimeMs,
                    ),
                )
                activity.toast(R.string.stats_cleared)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
        if (installed.isEmpty()) {
            activity.toast(R.string.deck_no_players_installed)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.action_open_with)
            .setItems(installed.map { it.displayName }.toTypedArray()) { _, which ->
                val player = installed[which]
                val app = activity.application as GhostGalleonApp
                app.updateSettings(
                    app.settings.copy(
                        defaultPlayers = app.settings.defaultPlayers +
                            (rom.platformId to player.id),
                    ),
                    notify = false,
                )
                onLaunch(player.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun playerProfile(activity: AppCompatActivity, rom: RomEntry) {
        val platform = Platforms.byId(rom.platformId) ?: return
        val players = platform.players
        if (players.isEmpty()) {
            activity.toast(R.string.deck_no_players_platform)
            return
        }
        val app = activity.application as GhostGalleonApp
        val current = app.settings.romProfiles[rom.id]
        val labels = players.map { player ->
            if (player.id == current) {
                activity.getString(R.string.format_selected_check, player.displayName)
            } else {
                player.displayName
            }
        } + activity.getString(
            if (current == null) R.string.deck_platform_default_selected
            else R.string.deck_platform_default,
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.deck_player_profile)
            .setItems(labels.toTypedArray()) { _, which ->
                val live = app.settings
                val nextProfiles = if (which >= players.size) {
                    RomProfiles.clearProfile(live.romProfiles, rom.id)
                } else {
                    RomProfiles.setProfile(live.romProfiles, rom.id, players[which].id)
                }
                app.updateSettings(live.copy(romProfiles = nextProfiles))
                activity.toast(R.string.deck_player_saved)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    fun openAppInfo(activity: AppCompatActivity, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { activity.toast(R.string.deck_cannot_open_app_info) }
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
