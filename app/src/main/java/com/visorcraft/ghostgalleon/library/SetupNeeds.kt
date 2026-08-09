package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text

/**
 * Pure decision for first-run / empty-library setup polish.
 * Host-tested; no Android types.
 */
object SetupNeeds {

    data class Snapshot(
        val setupDismissed: Boolean,
        val romTreeCount: Int,
        val romEntryCount: Int,
        val installedPlayerCount: Int,
        val hasSgdbKey: Boolean,
    )

    /** True when the guided setup surface should be offered. */
    fun shouldShow(s: Snapshot): Boolean {
        if (s.setupDismissed) return false
        // Already has library trees or entries → configured enough.
        if (s.romTreeCount > 0 || s.romEntryCount > 0) return false
        return true
    }

    /** Translation-safe checklist rows for the setup card (label, done). */
    fun checklist(s: Snapshot): List<Pair<UiText, Boolean>> = listOf(
        text(R.string.setup_check_rom_folder) to (s.romTreeCount > 0),
        text(R.string.setup_check_emulator) to (s.installedPlayerCount > 0),
        text(R.string.setup_check_sgdb) to s.hasSgdbKey,
    )

    fun allRequiredDone(s: Snapshot): Boolean =
        s.romTreeCount > 0
}
