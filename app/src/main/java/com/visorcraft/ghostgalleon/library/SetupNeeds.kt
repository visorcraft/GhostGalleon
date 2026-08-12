package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text

/**
 * Pure decision for first-run / empty-library setup polish and one-time
 * companion-chrome discoverability (Resume + status pill).
 * Host-tested; no Android types.
 */
object SetupNeeds {

    data class Snapshot(
        val setupDismissed: Boolean,
        val romTreeCount: Int,
        val romEntryCount: Int,
        val installedPlayerCount: Int,
        val hasSgdbKey: Boolean,
        /** Browse chrome: companion Resume chip enabled. */
        val resumeChip: Boolean = false,
        /** Browse chrome: clock/battery status pill enabled. */
        val statusPill: Boolean = false,
        /** User dismissed the one-time Resume/status discover card. */
        val chromeDiscoverDismissed: Boolean = false,
    )

    /** Empty-library guided setup (ROM folder first). */
    fun shouldShowLibrarySetup(s: Snapshot): Boolean {
        if (s.setupDismissed) return false
        if (s.romTreeCount > 0 || s.romEntryCount > 0) return false
        return true
    }

    /**
     * One-time nudge when the library exists but Resume / status pill are
     * still off (both default off under Minimal chrome).
     */
    fun shouldShowChromeDiscover(s: Snapshot): Boolean {
        if (s.chromeDiscoverDismissed) return false
        if (s.romTreeCount == 0 && s.romEntryCount == 0) return false
        return !s.resumeChip || !s.statusPill
    }

    /** True when either guided surface should be offered. */
    fun shouldShow(s: Snapshot): Boolean =
        shouldShowLibrarySetup(s) || shouldShowChromeDiscover(s)

    /** Chrome-discover-only mode (library already present). */
    fun isChromeDiscoverOnly(s: Snapshot): Boolean =
        !shouldShowLibrarySetup(s) && shouldShowChromeDiscover(s)

    /** Translation-safe checklist rows for the setup card (label, done). */
    fun checklist(s: Snapshot): List<Pair<UiText, Boolean>> {
        if (isChromeDiscoverOnly(s)) {
            return listOf(
                text(R.string.setup_check_resume_chip) to s.resumeChip,
                text(R.string.setup_check_status_pill) to s.statusPill,
            )
        }
        return listOf(
            text(R.string.setup_check_rom_folder) to (s.romTreeCount > 0),
            text(R.string.setup_check_emulator) to (s.installedPlayerCount > 0),
            text(R.string.setup_check_sgdb) to s.hasSgdbKey,
            text(R.string.setup_check_resume_chip) to s.resumeChip,
            text(R.string.setup_check_status_pill) to s.statusPill,
        )
    }

    fun allRequiredDone(s: Snapshot): Boolean =
        s.romTreeCount > 0

    /** Next browse chrome after user accepts the discover nudge. */
    fun enableCompanionChrome(
        resumeChip: Boolean,
        statusPill: Boolean,
        currentResume: Boolean,
        currentStatus: Boolean,
    ): Pair<Boolean, Boolean> = Pair(
        currentResume || resumeChip,
        currentStatus || statusPill,
    )
}
