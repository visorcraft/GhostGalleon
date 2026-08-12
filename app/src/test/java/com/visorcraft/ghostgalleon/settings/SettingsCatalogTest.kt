package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    private val items = listOf(
        SettingsJump(SettingsCatalog.PAGE_LIBRARY, "Library", "rom folder rescan"),
        SettingsJump(SettingsCatalog.PAGE_ART, "Artwork & backup", "steamgriddb export"),
        SettingsJump(SettingsCatalog.PAGE_CONTROLS, "Controls", "remap deadzone"),
    )

    @Test
    fun `matches label and keywords`() {
        assertEquals(
            listOf(SettingsCatalog.PAGE_ART),
            SettingsCatalog.matches("steam", items).map { it.pageId },
        )
        assertEquals(
            listOf(SettingsCatalog.PAGE_LIBRARY),
            SettingsCatalog.matches("Library", items).map { it.pageId },
        )
        assertTrue(SettingsCatalog.matches("  ", items).isEmpty())
    }
}
