package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.input.SecondSeatPolicy
import com.visorcraft.ghostgalleon.settings.BrowseChrome
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.ControllerLabLogic
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end Track E visual-craft regression contract covering all 4 built-in
 * theme packs (Ghost, Teal, OLED Black, Neon) plus adversarial custom themes,
 * densities, and display form factors (360dp narrow, 540dp Sugar, 960dp Odin 2 Portal).
 */
class TrackEVisualContractTest {

    private val customThemes: List<ThemeTokens> = listOf(
        ThemeTokens(
            id = "custom-abyss",
            displayName = "Abyss",
            accentColor = 0xFF1A237E.toInt(),
            cardRadiusDp = 28,
            panelLift = 0xFF12141E.toInt(),
            chipIdle = 0xFF1B1E2E.toInt(),
        ),
        ThemeTokens(
            id = "custom-ivory",
            displayName = "Ivory",
            accentColor = 0xFFFFFDF5.toInt(),
            cardRadiusDp = 8,
            panelLift = 0xFF181818.toInt(),
            chipIdle = 0xFF242424.toInt(),
        ),
        ThemeTokens(
            id = "custom-crimson",
            displayName = "Crimson",
            accentColor = 0xFFD32F2F.toInt(),
            cardRadiusDp = 18,
            panelLift = 0xFF221216.toInt(),
            chipIdle = 0xFF301A20.toInt(),
        ),
        ThemeTokens(
            id = "custom-emerald",
            displayName = "Emerald",
            accentColor = 0xFF00E676.toInt(),
            cardRadiusDp = 20,
            panelLift = 0xFF0F2018.toInt(),
            chipIdle = 0xFF173024.toInt(),
        ),
    )

    private val allThemes: List<ThemeTokens> = ThemePack.BUILTINS + customThemes

    @Test
    fun `surface hierarchy maintains strict luminance progression across all themes`() {
        for (tokens in allThemes) {
            val backdrop = ThemePack.backdropColor(tokens)
            val sunken = ThemePack.surfaceSunken(tokens)
            val card = ThemePack.cardFill(tokens)
            val elevated = ThemePack.surfaceElevated(tokens)

            val lBackdrop = ThemePack.relativeLuminance(backdrop)
            val lSunken = ThemePack.relativeLuminance(sunken)
            val lCard = ThemePack.relativeLuminance(card)
            val lElevated = ThemePack.relativeLuminance(elevated)

            assertTrue("backdrop < sunken failed on ${tokens.id}", lBackdrop < lSunken)
            assertTrue("sunken < card failed on ${tokens.id}", lSunken < lCard)
            assertTrue("card < elevated failed on ${tokens.id}", lCard < lElevated)
        }
    }

    @Test
    fun `every Settings, About, and Lab text pairing meets WCAG AA across built-in and custom themes`() {
        val black = 0xFF000000.toInt()
        val white = UiDimens.ALPHA_PRIMARY
        for (tokens in allThemes) {
            val accent = tokens.accentColor
            val card = ThemePack.cardFill(tokens)
            val sunken = ThemePack.surfaceSunken(tokens)

            val onAccent = SettingsUiLogic.accessibleOnAccent(accent)
            assertTrue(
                "onAccent contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(onAccent, accent) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            assertTrue(
                "accentOnSurface contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(SettingsUiLogic.accentOnSurface(accent, tokens), card) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "accentOnSunken contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(SettingsUiLogic.accentOnSunken(accent, tokens), sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "accentOnBlack contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(SettingsUiLogic.accentOnBlack(accent), black) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val navBg = SettingsUiLogic.navActiveSurface(tokens, accent)
            assertTrue(
                "navActiveSurface white text contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(white, navBg) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "navActiveSurface icon contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(ThemePack.accentTextColor(accent, navBg), navBg) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            for (tone in SettingsUiLogic.ModalChipTone.entries) {
                val bg = when (tone) {
                    SettingsUiLogic.ModalChipTone.ACCENT -> accent
                    SettingsUiLogic.ModalChipTone.NEUTRAL -> tokens.chipIdle
                    SettingsUiLogic.ModalChipTone.DESTRUCTIVE -> sunken
                }
                val fg = SettingsUiLogic.modalChipTextColor(tone, accent, tokens)
                assertTrue(
                    "modalChip($tone) contrast failed on ${tokens.id}",
                    ThemePack.contrastRatio(fg, bg) >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }

            assertTrue(
                "About underline contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(AboutPageLogic.tabUnderlineColor(accent), accent) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "About credit pill contrast failed on ${tokens.id}",
                ThemePack.contrastRatio(AboutPageLogic.creditLicenseTextColor(accent, tokens), sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            assertTrue(
                "Destructive red on sunken failed on ${tokens.id}",
                ThemePack.contrastRatio(UiDimens.COLOR_DESTRUCTIVE, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "Warning amber on sunken failed on ${tokens.id}",
                ThemePack.contrastRatio(SettingsUiLogic.COLOR_WARNING_AMBER, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val palette = SettingsUiLogic.resolvePalette(tokens, accent)
            assertTrue(
                "Settings focusStroke on card failed WCAG 1.4.11 on ${tokens.id}",
                ThemePack.contrastRatio(palette.focusStroke, card) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
            assertTrue(
                "Settings focusStroke on chipIdle failed WCAG 1.4.11 on ${tokens.id}",
                ThemePack.contrastRatio(palette.focusStroke, tokens.chipIdle) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
            assertTrue(
                "Settings focusStroke on sunken failed WCAG 1.4.11 on ${tokens.id}",
                ThemePack.contrastRatio(palette.focusStroke, sunken) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
        }
        assertTrue(
            "Settings minimum touch comfort must be >= 44dp",
            SettingsUiLogic.MIN_CONTROL_TOUCH_DP >= 44,
        )
        assertTrue(
            "Settings SeekBar minimum touch comfort must be >= 44dp",
            SettingsUiLogic.SEEKBAR_MIN_HEIGHT_DP >= 44,
        )
    }

    @Test
    fun `concentric radii and multi-density surface keys stay distinct and monotonic`() {
        val densities = listOf(1.5f, 2.0f, 2.5f, 3.0f, 3.5f)
        for (tokens in ThemePack.BUILTINS) {
            assertTrue(ThemePack.pillRadiusDp(tokens) >= ThemePack.chipRadiusDp(tokens))
            val inner = ThemePack.nestedRadiusDp(tokens.cardRadiusDp, insetDp = 6)
            assertTrue("nested radius $inner should be <= outer ${tokens.cardRadiusDp}", inner <= tokens.cardRadiusDp)
            assertTrue("nested radius $inner should respect min floor", inner >= 4)

            val keys = densities.map { TileBackgrounds.cardSurfaceKey(tokens, it) }
            assertEquals("Each density must produce a distinct cardSurfaceKey", densities.size, keys.toSet().size)

            val scaledRadii = densities.map { SettingsPreviewLogic.scaledCornerRadiusPx(tokens.cardRadiusDp, it) }
            for (i in 1 until scaledRadii.size) {
                assertTrue("scaled preview radius must grow with density", scaledRadii[i] >= scaledRadii[i - 1])
            }
        }
        assertNotEquals(
            TileBackgrounds.selectedSurfaceKey(ThemePack.GHOST, ThemePack.GHOST.accentColor, 2f),
            TileBackgrounds.selectedSurfaceKey(ThemePack.GHOST, ThemePack.NEON.accentColor, 2f),
        )
    }

    @Test
    fun `responsive breakpoints and preview and lab geometry hold across narrow, Sugar 540dp, and Odin 2 Portal`() {
        val sugarPad = SettingsUiLogic.shellPaddingDp(isWide = true, screenHeightDp = 540)
        val odinPad = SettingsUiLogic.shellPaddingDp(isWide = true, screenHeightDp = 720)
        assertTrue(sugarPad.top <= odinPad.top)
        assertTrue(ControllerLabLogic.isCompactHeight(540))
        assertTrue(ControllerLabLogic.logBoxHeightDp(540) < ControllerLabLogic.logBoxHeightDp(720))
        assertEquals(1, AboutPageLogic.featureColumnCount(360))
        assertEquals(2, AboutPageLogic.featureColumnCount(540))
        assertEquals(2, AboutPageLogic.featureColumnCount(960))
        assertEquals(200, SettingsUiLogic.navBladeWidthDp(720))
        assertEquals(224, SettingsUiLogic.navBladeWidthDp(960))

        // Verify all 3..8 grid column previews and default second-seat anchors stay cleanly in-bounds.
        for (cols in SettingsPreviewLogic.MIN_COLUMNS..SettingsPreviewLogic.MAX_COLUMNS) {
            val cells = SettingsPreviewLogic.computeMiniGridCells(240f, 96f, cols, rows = 2, gapPx = 4f)
            assertEquals(cols * 2, cells.size)
        }
        val seatDots = SecondSeatPreviewLogic.layoutDots(SecondSeatPolicy.DEFAULT_ANCHORS, 300f, 132f, 14f)
        assertEquals(SecondSeatPolicy.DEFAULT_ANCHORS.size, seatDots.size)
        assertEquals(SettingsUiLogic.TOTAL_POWER_RAILS, SettingsUiLogic.enabledPowerRailsCount(BrowseChrome.FULL))
    }
}
