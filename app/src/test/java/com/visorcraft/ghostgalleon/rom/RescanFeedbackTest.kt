package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.resourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RescanFeedbackTest {

    @Test
    fun `progressLabel uses full vs incremental string ids`() {
        val inc = RescanFeedback.progressLabel(1, 3, force = false)
        assertEquals(listOf(R.string.settings_rescan_progress), inc.resourceIds())
        val full = RescanFeedback.progressLabel(2, 4, force = true)
        assertEquals(listOf(R.string.settings_rescan_progress_full), full.resourceIds())
    }

    @Test
    fun `successMessage all clean vs mixed vs scanned only`() {
        val allClean = RescanFeedback.successMessage(
            entryCount = 100,
            skippedCleanTrees = 2,
            scannedTrees = 0,
            retainedUnreadableTrees = 0,
        )
        assertEquals(listOf(R.plurals.count_roms_trees_unchanged), allClean.resourceIds())

        val mixed = RescanFeedback.successMessage(
            entryCount = 50,
            skippedCleanTrees = 1,
            scannedTrees = 2,
            retainedUnreadableTrees = 0,
        )
        assertEquals(listOf(R.string.settings_rescan_done_mixed), mixed.resourceIds())

        val partial = RescanFeedback.successMessage(
            entryCount = 10,
            skippedCleanTrees = 0,
            scannedTrees = 1,
            retainedUnreadableTrees = 1,
        )
        assertEquals(listOf(R.string.settings_rescan_done_partial), partial.resourceIds())

        val plain = RescanFeedback.successMessage(
            entryCount = 7,
            skippedCleanTrees = 0,
            scannedTrees = 1,
            retainedUnreadableTrees = 0,
        )
        assertTrue(plain is UiText.Quantity)
        assertEquals(listOf(R.plurals.count_roms_found), plain.resourceIds())
    }

    @Test
    fun `unreadable message is card unreadable string`() {
        val u = RescanFeedback.unreadableMessage()
        assertEquals(listOf(R.string.settings_card_unreadable), u.resourceIds())
    }
}
