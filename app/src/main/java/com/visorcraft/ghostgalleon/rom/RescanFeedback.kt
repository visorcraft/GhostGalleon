package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.i18n.text

/**
 * Pure copy for library rescan progress and completion. Host-tested.
 * Settings resolves [UiText] at the UI boundary.
 */
object RescanFeedback {

    /** While a scan is running: trees completed of total granted. */
    fun progressLabel(doneTrees: Int, totalTrees: Int, force: Boolean): UiText {
        val total = totalTrees.coerceAtLeast(0)
        val done = doneTrees.coerceIn(0, total.coerceAtLeast(doneTrees))
        return if (force) {
            text(R.string.settings_rescan_progress_full, done, total.coerceAtLeast(1))
        } else {
            text(R.string.settings_rescan_progress, done, total.coerceAtLeast(1))
        }
    }

    /** Completion toast body from a [RomLibrary.RescanResult.Success]. */
    fun successMessage(
        entryCount: Int,
        skippedCleanTrees: Int,
        scannedTrees: Int,
        retainedUnreadableTrees: Int,
    ): UiText {
        val entries = entryCount.coerceAtLeast(0)
        return when {
            skippedCleanTrees > 0 && scannedTrees > 0 ->
                text(
                    R.string.settings_rescan_done_mixed,
                    entries,
                    scannedTrees,
                    skippedCleanTrees,
                )
            skippedCleanTrees > 0 && scannedTrees == 0 ->
                // Existing plural: "%1$d ROMs · %2$d folder(s) unchanged"
                quantityText(
                    R.plurals.count_roms_trees_unchanged,
                    skippedCleanTrees,
                    entries,
                    skippedCleanTrees,
                )
            retainedUnreadableTrees > 0 ->
                text(
                    R.string.settings_rescan_done_partial,
                    entries,
                    retainedUnreadableTrees,
                )
            else ->
                quantityText(R.plurals.count_roms_found, entries, entries)
        }
    }

    fun unreadableMessage(): UiText = text(R.string.settings_card_unreadable)
}
