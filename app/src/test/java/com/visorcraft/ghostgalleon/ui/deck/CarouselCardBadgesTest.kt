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

    @Test
    fun `cardRippleColor adapts cleanly across dark built-in themes, OLED, and light surfaces`() {
        for (pack in ThemePack.BUILTINS) {
            val surface = ThemePack.cardFill(pack)
            val ripple = CarouselCardBadges.cardRippleColor(surface, pack.accentColor)
            val alpha = (ripple ushr 24) and 0xFF
            assertTrue("Ripple alpha out of range for ${pack.id}: $alpha", alpha in 0x20..0x44)
            // Opaque tint on dark surfaces remains distinctly bright against dark cards.
            val opaqueTint = (0xFF shl 24) or (ripple and 0x00FFFFFF)
            assertTrue(
                "Ripple tint too dark on ${pack.id}",
                ThemePack.relativeLuminance(opaqueTint) > 0.5,
            )
        }
        // OLED bright accent uses damped alpha compared to Ghost.
        val ghostRipple = CarouselCardBadges.cardRippleColor(
            ThemePack.cardFill(ThemePack.GHOST),
            ThemePack.GHOST.accentColor,
        )
        val oledRipple = CarouselCardBadges.cardRippleColor(
            ThemePack.cardFill(ThemePack.OLED),
            ThemePack.OLED.accentColor,
        )
        assertTrue(((oledRipple ushr 24) and 0xFF) < ((ghostRipple ushr 24) and 0xFF))

        // Light custom card surface switches to a subtle dark ripple.
        val lightRipple = CarouselCardBadges.cardRippleColor(
            surfaceFill = 0xFFF2F2F5.toInt(),
            accent = ThemePack.GHOST.accentColor,
        )
        assertEquals(0x000000, lightRipple and 0x00FFFFFF)
        assertEquals(CarouselCardBadges.CARD_RIPPLE_LIGHT_SURFACE_ALPHA, (lightRipple ushr 24) and 0xFF)
    }

    @Test
    fun `cardForegroundPolicy keeps touch ripple mounted beneath multi-select overlay`() {
        val hidden = CarouselCardBadges.cardForegroundPolicy(
            CarouselMultiSelectLogic.CardCheckStyle.HIDDEN,
        )
        val unchecked = CarouselCardBadges.cardForegroundPolicy(
            CarouselMultiSelectLogic.CardCheckStyle.UNCHECKED,
        )
        val checked = CarouselCardBadges.cardForegroundPolicy(
            CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
        )

        for (policy in listOf(hidden, unchecked, checked)) {
            assertEquals(0, policy.rippleLayerIndex)
            assertEquals(1, policy.multiSelectLayerIndex)
            assertTrue(policy.rippleLayerIndex < policy.multiSelectLayerIndex)
        }
        assertFalse(hidden.showMultiSelectOverlay)
        assertFalse(unchecked.showMultiSelectOverlay)
        assertTrue(checked.showMultiSelectOverlay)
    }

    @Test
    fun `showArtScrim hides bottom scrim on transparent app cards and badgeLayout scales across 96 to 260dp`() {
        assertTrue(CarouselCardBadges.showArtScrim(isRom = true))
        assertFalse(CarouselCardBadges.showArtScrim(isRom = false))

        val density = 2f
        val compact = CarouselCardBadges.badgeLayout(
            cardSizePx = (96 * density).toInt(),
            innerArtRadiusDp = 6,
            density = density,
        )
        val medium = CarouselCardBadges.badgeLayout(
            cardSizePx = (160 * density).toInt(),
            innerArtRadiusDp = 16,
            density = density,
        )
        val large = CarouselCardBadges.badgeLayout(
            cardSizePx = (260 * density).toInt(),
            innerArtRadiusDp = 16,
            density = density,
        )

        for (layout in listOf(compact, medium, large)) {
            assertTrue("favSizeDp out of [18, 24]: ${layout.favSizeDp}", layout.favSizeDp in 18..24)
            assertTrue("insetDp out of [5, 9]: ${layout.insetDp}", layout.insetDp in 5..9)
            assertTrue(layout.favSizePx > 0 && layout.insetPx > 0)
        }
        assertTrue(compact.favSizeDp <= medium.favSizeDp)
        assertTrue(medium.favSizeDp <= large.favSizeDp)
        assertTrue(compact.insetDp <= large.insetDp)
        // Larger Ghost inner corner radius (16dp) receives larger bisector inset than sharp OLED (6dp).
        val ghostSmall = CarouselCardBadges.badgeLayout(
            cardSizePx = (96 * density).toInt(),
            innerArtRadiusDp = 16,
            density = density,
        )
        assertTrue(ghostSmall.insetDp > compact.insetDp)
    }
}
