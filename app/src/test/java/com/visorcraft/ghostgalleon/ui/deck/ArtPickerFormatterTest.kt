package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtPickerFormatterTest {

    @Test
    fun `previewKindForTitleRes and previewSpec tailor dimensions and alpha to art kind`() {
        assertEquals(
            ArtPickerFormatter.PreviewKind.GRID,
            ArtPickerFormatter.previewKindForTitleRes(R.string.artwork_pick_grid),
        )
        assertEquals(
            ArtPickerFormatter.PreviewKind.HERO,
            ArtPickerFormatter.previewKindForTitleRes(R.string.artwork_pick_hero),
        )
        assertEquals(
            ArtPickerFormatter.PreviewKind.LOGO,
            ArtPickerFormatter.previewKindForTitleRes(R.string.artwork_pick_logo),
        )

        val grid = ArtPickerFormatter.previewSpec(ArtPickerFormatter.PreviewKind.GRID)
        val hero = ArtPickerFormatter.previewSpec(ArtPickerFormatter.PreviewKind.HERO)
        val logo = ArtPickerFormatter.previewSpec(ArtPickerFormatter.PreviewKind.LOGO)

        assertTrue("Grid preview should be portrait", grid.heightDp > grid.widthDp)
        assertTrue("Hero preview should be wide", hero.widthDp > hero.heightDp * 2)
        assertTrue("Logo preview should use fitCenter", logo.fitCenter)
        assertTrue("Logo preview should preserve alpha channel", logo.preserveAlpha)
        assertFalse(grid.preserveAlpha)

        assertTrue(ArtPickerFormatter.decodeTargetPx(ArtPickerFormatter.PreviewKind.GRID, 2f) in 128..320)
        assertTrue(ArtPickerFormatter.decodeTargetPx(ArtPickerFormatter.PreviewKind.HERO, 3f) in 128..320)
    }

    @Test
    fun `extractFileFormat and formatDimensions parse CDN URLs and bounds cleanly`() {
        assertEquals("PNG", ArtPickerFormatter.extractFileFormat("https://cdn2.steamgriddb.com/grid/abc123.png"))
        assertEquals("JPG", ArtPickerFormatter.extractFileFormat("https://cdn2.steamgriddb.com/hero/foo.jpeg?v=2"))
        assertEquals("WEBP", ArtPickerFormatter.extractFileFormat("https://cdn2.steamgriddb.com/logo/x.webp#top"))
        assertNull(ArtPickerFormatter.extractFileFormat("https://cdn2.steamgriddb.com/grid/noext"))

        assertEquals("600×900", ArtPickerFormatter.formatDimensions(600, 900))
        assertNull(ArtPickerFormatter.formatDimensions(0, 900))
        assertNull(ArtPickerFormatter.formatDimensions(600, -1))
    }

    @Test
    fun `cleanFileName, candidateTitleText, and candidateSublineText format readable labels`() {
        assertEquals("cover_art", ArtPickerFormatter.cleanFileName("https://cdn.example/cover_art.png?size=lg"))
        val longName = ArtPickerFormatter.cleanFileName(
            "https://cdn.example/super_long_steamgriddb_hash_identifier_0123456789.png",
            maxChars = 16,
        )
        assertEquals(16, longName.length)
        assertTrue(longName.endsWith("…"))

        val title = ArtPickerFormatter.candidateTitleText(2, 6, "https://cdn.example/boxart.png")
        assertEquals(
            text(R.string.format_dot_pair, text(R.string.format_progress, 2, 6), dynamicText("boxart")),
            title,
        )

        val subline = ArtPickerFormatter.candidateSublineText(600, 900, "https://cdn.example/boxart.png")
        assertEquals(
            text(R.string.format_dot_pair, dynamicText("600×900"), dynamicText("PNG")),
            subline,
        )
        assertEquals(
            text(R.string.format_dot_pair, title, subline!!),
            ArtPickerFormatter.candidateAccessibilityText(title, subline),
        )
        assertNull(ArtPickerFormatter.candidateSublineText(0, 0, "https://cdn.example/noext"))
    }

    @Test
    fun `gridColumns uses 2 responsive box-art columns on 540dp Sugar and 960dp Odin and 1-col otherwise`() {
        val gridSpec = ArtPickerFormatter.previewSpec(ArtPickerFormatter.PreviewKind.GRID)
        assertEquals(68, gridSpec.widthDp)
        assertEquals(98, gridSpec.heightDp)

        // 360dp narrow portrait stays 1-col even for GRID.
        assertEquals(1, ArtPickerFormatter.gridColumns(ArtPickerFormatter.PreviewKind.GRID, 360))
        // 500dp / 540dp Sugar & 960dp Odin 2 Portal promote GRID box art to 2 columns.
        assertEquals(2, ArtPickerFormatter.gridColumns(ArtPickerFormatter.PreviewKind.GRID, 500))
        assertEquals(2, ArtPickerFormatter.gridColumns(ArtPickerFormatter.PreviewKind.GRID, 540))
        assertEquals(2, ArtPickerFormatter.gridColumns(ArtPickerFormatter.PreviewKind.GRID, 960))

        // Wide HERO banners and LOGO tiles always stay 1-col across every viewport.
        for (widthDp in listOf(360, 540, 960)) {
            assertEquals(1, ArtPickerFormatter.gridColumns(ArtPickerFormatter.PreviewKind.HERO, widthDp))
            assertEquals(1, ArtPickerFormatter.gridColumns(ArtPickerFormatter.PreviewKind.LOGO, widthDp))
        }
    }
}
