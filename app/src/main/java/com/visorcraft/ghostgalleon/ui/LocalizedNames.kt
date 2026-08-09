package com.visorcraft.ghostgalleon.ui

import android.content.Context
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.settings.CompanionRole

fun Context.browseModeName(mode: LibraryBrowse.Mode): String = getString(when (mode) {
    LibraryBrowse.Mode.ALL -> R.string.label_all
    LibraryBrowse.Mode.RECENT -> R.string.label_recent
    LibraryBrowse.Mode.PLAYED_TODAY -> R.string.label_today
    LibraryBrowse.Mode.PLAYED_THIS_WEEK -> R.string.label_week
    LibraryBrowse.Mode.PLAYED_THIS_MONTH -> R.string.label_month
    LibraryBrowse.Mode.MOST_PLAYED -> R.string.label_top
    LibraryBrowse.Mode.FAVORITES -> R.string.label_favorites_short
    LibraryBrowse.Mode.GAMES -> R.string.label_games
    LibraryBrowse.Mode.RECENTLY_INSTALLED -> R.string.label_installed
    LibraryBrowse.Mode.ALPHA -> R.string.label_alpha_sort
    LibraryBrowse.Mode.UNPLAYED -> R.string.label_new
    LibraryBrowse.Mode.COLLECTION -> R.string.label_collections
})

fun Context.themeName(id: String, fallback: String = id): String = when (id) {
    "ghost" -> getString(R.string.theme_ghost)
    "threeds" -> getString(R.string.theme_threeds)
    "oled" -> getString(R.string.theme_oled)
    "neon" -> getString(R.string.theme_neon)
    else -> fallback
}

fun Context.deviceProfileName(id: String, fallback: String = id): String = when (id) {
    "auto" -> getString(R.string.profile_auto)
    "onex-sugar" -> getString(R.string.profile_sugar)
    "generic-dual" -> getString(R.string.profile_generic_dual)
    "single" -> getString(R.string.profile_single)
    else -> fallback
}

fun Context.companionRoleName(role: CompanionRole): String = getString(when (role) {
    CompanionRole.HERO -> R.string.role_hero
    CompanionRole.NOW_PLAYING -> R.string.role_now_playing
    CompanionRole.PERF_HUD -> R.string.role_perf_hud
    CompanionRole.PINNED_APP -> R.string.role_pinned_app
})
