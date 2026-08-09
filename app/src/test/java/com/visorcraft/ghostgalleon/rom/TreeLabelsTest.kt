package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeLabelsTest {

    @Test
    fun `sd card tree shows last segment with SD card suffix`() {
        assertEquals(
            text(R.string.settings_tree_sd_card, "roms"),
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms"),
        )
    }

    @Test
    fun `nested sd card tree shows deepest segment`() {
        assertEquals(
            text(R.string.settings_tree_sd_card, "nds"),
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3Aroms%2Fnds"),
        )
    }

    @Test
    fun `primary volume tree has no suffix`() {
        assertEquals(
            dynamicText("ROMs"),
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/primary%3AEmulation%2FROMs"),
        )
    }

    @Test
    fun `volume root tree shows the volume`() {
        assertEquals(
            text(R.string.settings_tree_sd_card, "7F7E-2949"),
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/7F7E-2949%3A"),
        )
        assertEquals(
            dynamicText("primary"),
            TreeLabels.label(
                "content://com.android.externalstorage.documents/tree/primary%3A"),
        )
    }

    @Test
    fun `unparseable uri falls back to raw string`() {
        assertEquals(dynamicText("not a tree uri"), TreeLabels.label("not a tree uri"))
    }
}
