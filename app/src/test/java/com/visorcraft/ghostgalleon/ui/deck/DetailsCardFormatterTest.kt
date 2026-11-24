package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsCardFormatterTest {

    @Test
    fun `headerSpec builds WCAG-legible platform badge and year-genre subtitle for ROMs`() {
        val pillSurface = ThemePack.GHOST.chipIdle
        val spec = DetailsCardFormatter.headerSpec(
            title = "Chrono Trigger",
            platformId = "snes",
            favorite = true,
            year = "1995-03-11",
            genre = "RPG / Adventure",
            surfaceColor = pillSurface,
        )
        assertEquals("Chrono Trigger", spec.title)
        assertEquals(dynamicText("SNES"), spec.badgeText)
        assertNotNull(spec.badgeDotColor)
        assertTrue(
            ThemePack.contrastRatio(spec.badgeDotColor!!, pillSurface) >= ThemePack.MIN_TEXT_CONTRAST,
        )
        assertTrue(spec.showFavoriteStar)
        assertEquals(
            text(R.string.format_dot_pair, dynamicText("1995"), dynamicText("RPG")),
            spec.subtitle,
        )
    }

    @Test
    fun `headerSpec builds clean App badge and handles partial or missing metadata`() {
        val surface = ThemePack.cardFill(ThemePack.OLED)
        val appSpec = DetailsCardFormatter.headerSpec(
            title = "RetroArch",
            platformId = null,
            favorite = false,
            year = null,
            genre = null,
            surfaceColor = surface,
        )
        assertEquals(text(R.string.label_app), appSpec.badgeText)
        assertNull(appSpec.badgeDotColor)
        assertFalse(appSpec.showFavoriteStar)
        assertNull(appSpec.subtitle)

        val genreOnly = DetailsCardFormatter.headerSpec(
            title = "Tetris",
            platformId = "gb",
            favorite = false,
            year = "  ",
            genre = "Puzzle",
            surfaceColor = surface,
        )
        assertEquals(dynamicText("Puzzle"), genreOnly.subtitle)
    }

    @Test
    fun `helperRowAccessibilityText pairs label and current helper value`() {
        assertEquals(
            text(
                R.string.format_colon_pair,
                text(R.string.settings_play_host_helper),
                dynamicText("Discord"),
            ),
            DetailsCardFormatter.helperRowAccessibilityText("Discord"),
        )
        assertEquals(44, DetailsCardFormatter.HELPER_ROW_MIN_HEIGHT_DP)
    }

    @Test
    fun `stripRedundantHeaderLines removes duplicate leading title while preserving descriptions and single-line bodies`() {
        val withDesc = "Chrono Trigger\n\nA time-traveling RPG.\n\nType: ROM\nPlatform: snes"
        assertEquals(
            "A time-traveling RPG.\n\nType: ROM\nPlatform: snes",
            DetailsCardFormatter.stripRedundantHeaderLines(withDesc, "Chrono Trigger"),
        )

        val metaOnly = "Spotify\n\nType: App\nKey: com.spotify.music"
        assertEquals(
            "Type: App\nKey: com.spotify.music",
            DetailsCardFormatter.stripRedundantHeaderLines(metaOnly, "spotify"),
        )

        // Single-line bodies or title-only bodies are never emptied.
        assertEquals(
            "Chrono Trigger",
            DetailsCardFormatter.stripRedundantHeaderLines("Chrono Trigger", "Chrono Trigger"),
        )
        assertEquals(
            "Chrono Trigger\n\n",
            DetailsCardFormatter.stripRedundantHeaderLines("Chrono Trigger\n\n", "Chrono Trigger"),
        )

        // Non-matching first line stays untouched.
        val distinct = "Type: ROM\nPlatform: gba"
        assertEquals(
            distinct,
            DetailsCardFormatter.stripRedundantHeaderLines(distinct, "Metroid Fusion"),
        )
    }
}
