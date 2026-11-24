package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionHeroStyleTest {

    @Test
    fun `raMeterState hides bar when credentials missing or game has no achievements`() {
        val noCreds = CompanionHeroStyle.raMeterState(
            progress = RaProgress(numAwarded = 5, numPossible = 10),
            hasCredentials = false,
            accentColor = ThemePack.GHOST.accentColor,
        )
        assertFalse(noCreds.visible)
        assertEquals(0f, noCreds.fraction, 0.001f)

        val empty = CompanionHeroStyle.raMeterState(
            progress = RaProgress(numAwarded = 0, numPossible = 0),
            hasCredentials = true,
            accentColor = ThemePack.GHOST.accentColor,
        )
        assertFalse(empty.visible)

        val nullProg = CompanionHeroStyle.raMeterState(
            progress = null,
            hasCredentials = true,
            accentColor = ThemePack.GHOST.accentColor,
        )
        assertFalse(nullProg.visible)
    }

    @Test
    fun `raMeterState computes fraction and highlights 100 percent mastery in gold`() {
        val partial = CompanionHeroStyle.raMeterState(
            progress = RaProgress(numAwarded = 7, numPossible = 20),
            hasCredentials = true,
            accentColor = ThemePack.GHOST.accentColor,
        )
        assertTrue(partial.visible)
        assertFalse(partial.mastered)
        assertEquals(0.35f, partial.fraction, 0.001f)

        val mastered = CompanionHeroStyle.raMeterState(
            progress = RaProgress(numAwarded = 25, numPossible = 25),
            hasCredentials = true,
            accentColor = ThemePack.GHOST.accentColor,
        )
        assertTrue(mastered.visible)
        assertTrue(mastered.mastered)
        assertEquals(1f, mastered.fraction, 0.001f)
        assertEquals(CompanionHeroStyle.MASTERY_GOLD, mastered.fillColor)
    }

    @Test
    fun `raMeterState fill color meets WCAG AA contrast across all built-in theme packs`() {
        val progress = RaProgress(numAwarded = 4, numPossible = 10)
        for (pack in ThemePack.BUILTINS) {
            val sunken = ThemePack.surfaceSunken(pack)
            val state = CompanionHeroStyle.raMeterState(
                progress = progress,
                hasCredentials = true,
                accentColor = pack.accentColor,
                surfaceColor = sunken,
            )
            val ratio = ThemePack.contrastRatio(state.fillColor, sunken)
            assertTrue(
                "RA bar fill contrast for ${pack.id} must be >= 4.5, was $ratio",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
            val goldRatio = ThemePack.contrastRatio(CompanionHeroStyle.MASTERY_GOLD, sunken)
            assertTrue(
                "Mastery gold contrast for ${pack.id} must be >= 4.5, was $goldRatio",
                goldRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `shouldAnimateSelection skips same-ROM updates and empty text`() {
        assertTrue(CompanionHeroStyle.shouldAnimateSelection(false, "Chrono Trigger", "Super Metroid"))
        assertFalse(CompanionHeroStyle.shouldAnimateSelection(true, "Chrono Trigger", "Super Metroid"))
        assertFalse(CompanionHeroStyle.shouldAnimateSelection(false, "Chrono Trigger", "Chrono Trigger"))
        assertFalse(CompanionHeroStyle.shouldAnimateSelection(false, "Chrono Trigger", ""))
    }

    @Test
    fun `art elevation and rim colors scale cleanly across compact and tall panels`() {
        val compactElevation = CompanionHeroStyle.artElevationDp(132)
        val mediumElevation = CompanionHeroStyle.artElevationDp(160)
        val tallElevation = CompanionHeroStyle.artElevationDp(240)
        assertTrue(compactElevation < mediumElevation)
        assertTrue(mediumElevation < tallElevation)

        val rim = CompanionHeroStyle.artRimColor(ThemePack.GHOST.accentColor)
        assertEquals(0x3D, (rim ushr 24) and 0xFF)
        val shadow = CompanionHeroStyle.artShadowColor(ThemePack.NEON.accentColor)
        assertEquals(0xA6, (shadow ushr 24) and 0xFF)
        assertEquals(ThemePack.NEON.accentColor and 0x00FFFFFF, shadow and 0x00FFFFFF)
    }

    @Test
    fun `sugar 540dp hero stack with RA mini bar fits comfortably inside panel height`() {
        val sugar = CompanionHeroMetrics.forPanel(540f)
        val stackDp = CompanionHeroMetrics.estimatedHeroStackDp(sugar, includeRaBar = true)
        assertTrue(
            "Sugar 540dp hero stack ($stackDp dp) must fit within 540dp",
            stackDp <= 540,
        )
    }

    @Test
    fun `app pedestal inset and brand horizon palette keep opaque dark surfaces and subtle hairline`() {
        assertTrue(CompanionHeroStyle.appPedestalInsetDp(132) in 12..26)
        assertTrue(CompanionHeroStyle.appPedestalInsetDp(240) in 12..26)
        assertTrue(CompanionHeroStyle.appPedestalInsetDp(132) < CompanionHeroStyle.appPedestalInsetDp(240))

        for (pack in ThemePack.BUILTINS) {
            val palette = CompanionHeroStyle.brandHorizonPalette(pack.panelLift, pack.accentColor)
            assertEquals(0xFF, (palette.skyMid ushr 24) and 0xFF)
            assertEquals(0xFF, (palette.skyHorizon ushr 24) and 0xFF)
            assertEquals(0xFF, (palette.seaTop ushr 24) and 0xFF)
            assertEquals(0x33, (palette.horizonLine ushr 24) and 0xFF)
            assertTrue(
                "Horizon sea surface for ${pack.id} must stay dark for muted prompt legibility",
                ThemePack.contrastRatio(0x99FFFFFF.toInt(), palette.seaTop) >= 4.5,
            )
        }
    }

    @Test
    fun `ActivityEmbed pre-check validates package names before reflection`() {
        assertTrue(ActivityEmbed.canAttemptAttach("org.mozilla.firefox"))
        assertFalse(ActivityEmbed.canAttemptAttach(null))
        assertFalse(ActivityEmbed.canAttemptAttach(""))
        assertFalse(ActivityEmbed.canAttemptAttach("invalidpkg"))
        assertFalse(ActivityEmbed.canAttemptAttach("com.bad pkg"))
        assertTrue(ActivityEmbed.VIEWPORT_CORNER_RADIUS_DP >= 12)
    }
}
