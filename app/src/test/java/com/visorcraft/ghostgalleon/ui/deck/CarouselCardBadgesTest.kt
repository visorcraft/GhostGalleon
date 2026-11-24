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

class CarouselCardBadgesTest {

    @Test
    fun `platform badge appears on unfiltered rails and hides when same platform filter is active`() {
        val unfiltered = CarouselCardBadges.overlaySpec(
            platformId = "snes",
            activePlatformFilter = null,
            favorite = false,
            multiSelectEnabled = false,
            multiSelected = false,
        )
        assertEquals("SNES", unfiltered.platformBadgeText)
        assertNotNull(unfiltered.platformDotColor)

        val sameFilter = CarouselCardBadges.overlaySpec(
            platformId = "snes",
            activePlatformFilter = "snes",
            favorite = false,
            multiSelectEnabled = false,
            multiSelected = false,
        )
        assertNull(sameFilter.platformBadgeText)
        assertNull(sameFilter.platformDotColor)

        val appEntry = CarouselCardBadges.overlaySpec(
            platformId = null,
            activePlatformFilter = null,
            favorite = true,
            multiSelectEnabled = false,
            multiSelected = false,
        )
        assertNull(appEntry.platformBadgeText)
        assertTrue(appEntry.showFavoriteBadge)
    }

    @Test
    fun `favorite star badge yields to multi-select check badge without collision`() {
        val normalFav = CarouselCardBadges.overlaySpec(
            platformId = "gba",
            activePlatformFilter = null,
            favorite = true,
            multiSelectEnabled = false,
            multiSelected = false,
        )
        assertTrue(normalFav.showFavoriteBadge)
        assertFalse(normalFav.showMultiCheck)

        val multiFavSelected = CarouselCardBadges.overlaySpec(
            platformId = "gba",
            activePlatformFilter = null,
            favorite = true,
            multiSelectEnabled = true,
            multiSelected = true,
        )
        assertFalse(multiFavSelected.showFavoriteBadge)
        assertTrue(multiFavSelected.showMultiCheck)

        val multiFavUnselected = CarouselCardBadges.overlaySpec(
            platformId = "gba",
            activePlatformFilter = null,
            favorite = true,
            multiSelectEnabled = true,
            multiSelected = false,
        )
        assertFalse(multiFavUnselected.showFavoriteBadge)
        assertFalse(multiFavUnselected.showMultiCheck)
    }

    @Test
    fun `platform dot and favorite tint meet WCAG AA contrast on badge backdrop`() {
        val bg = CarouselCardBadges.PILL_BACKDROP_COLOR
        val favRatio = ThemePack.contrastRatio(CarouselCardBadges.FAVORITE_BADGE_TINT, bg)
        assertTrue("favorite star contrast too low: $favRatio", favRatio >= ThemePack.MIN_TEXT_CONTRAST)

        val platforms = listOf(
            "nes", "snes", "n64", "gb", "gbc", "gba", "nds", "3ds",
            "psx", "psp", "genesis", "arcade", "switch", "custom_dark",
        )
        for (pid in platforms) {
            val spec = CarouselCardBadges.overlaySpec(
                platformId = pid,
                activePlatformFilter = null,
                favorite = false,
                multiSelectEnabled = false,
                multiSelected = false,
            )
            val dot = requireNotNull(spec.platformDotColor) { "missing dot for $pid" }
            val ratio = ThemePack.contrastRatio(dot, bg)
            assertTrue("platform $pid dot contrast too low: $ratio", ratio >= ThemePack.MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `art bottom scrim transitions smoothly from transparent to dark`() {
        val stops = CarouselCardBadges.artBottomScrimColors()
        assertEquals(2, stops.size)
        assertEquals(0, (stops[0] ushr 24) and 0xFF)
        assertTrue(((stops[1] ushr 24) and 0xFF) in 0x40..0xB0)
    }

    @Test
    fun `cardAccessibilityText composes localized TalkBack labels cleanly`() {
        assertEquals(
            dynamicText("Spotify"),
            CarouselCardBadges.cardAccessibilityText(
                title = "Spotify",
                platformBadge = null,
                favorite = false,
                multiSelected = false,
            ),
        )
        assertEquals(
            text(R.string.format_dot_pair, dynamicText("Chrono Trigger"), dynamicText("SNES")),
            CarouselCardBadges.cardAccessibilityText(
                title = "Chrono Trigger",
                platformBadge = "SNES",
                favorite = false,
                multiSelected = false,
            ),
        )
        assertEquals(
            text(
                R.string.format_selected_check,
                text(
                    R.string.format_dot_pair,
                    text(R.string.format_dot_pair, dynamicText("Metroid Fusion"), dynamicText("GBA")),
                    text(R.string.action_favorite),
                ),
            ),
            CarouselCardBadges.cardAccessibilityText(
                title = "Metroid Fusion",
                platformBadge = "GBA",
                favorite = true,
                multiSelected = true,
            ),
        )
    }
}
