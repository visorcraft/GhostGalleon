package com.visorcraft.ghostgalleon.ui.settings

import android.view.KeyEvent
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.BrowseChrome
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiLogicTest {

    @Test
    fun `segmented layout stacks when narrow or three or more options`() {
        // 2-option rows stay inline in wide mode, stack on narrow panels.
        assertFalse(SettingsUiLogic.shouldStackSegmented(optionCount = 2, isWide = true))
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 2, isWide = false))

        // 3+ option rows always stack so localized labels never crowd or truncate.
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 3, isWide = true))
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 4, isWide = true))
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 4, isWide = false))
    }

    @Test
    fun `settings filled pills and accent labels meet WCAG AA contrast across all themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val accent = tokens.accentColor

            val onAccent = SettingsUiLogic.accessibleOnAccent(accent)
            val fillRatio = ThemePack.contrastRatio(onAccent, accent)
            assertTrue(
                "Low on-accent contrast $fillRatio on theme=${tokens.id}",
                fillRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val onCard = SettingsUiLogic.accentOnSurface(accent, tokens)
            val cardRatio = ThemePack.contrastRatio(onCard, ThemePack.cardFill(tokens))
            assertTrue(
                "Low accent-on-card contrast $cardRatio on theme=${tokens.id}",
                cardRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val onSunken = SettingsUiLogic.accentOnSunken(accent, tokens)
            val sunkenRatio = ThemePack.contrastRatio(onSunken, ThemePack.surfaceSunken(tokens))
            assertTrue(
                "Low accent-on-sunken contrast $sunkenRatio on theme=${tokens.id}",
                sunkenRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val ring = SettingsUiLogic.activePillFocusRing(accent)
            val ringVsFill = ThemePack.contrastRatio(ring, accent)
            val ringVsCard = ThemePack.contrastRatio(ring, ThemePack.cardFill(tokens))
            assertTrue(
                "Active pill focus ring blends with accent ($ringVsFill) on theme=${tokens.id}",
                ringVsFill >= 2.5,
            )
            assertTrue(
                "Active pill focus ring blends with card ($ringVsCard) on theme=${tokens.id}",
                ringVsCard >= 3.0,
            )
        }
    }

    @Test
    fun `second seat layout visibility mirrors toggle state`() {
        assertFalse(SettingsUiLogic.isSecondSeatLayoutVisible(false))
        assertTrue(SettingsUiLogic.isSecondSeatLayoutVisible(true))
    }

    @Test
    fun `stepPageIndex wraps forward and backward cleanly across settings tabs`() {
        org.junit.Assert.assertEquals(1, SettingsUiLogic.stepPageIndex(0, 1, 8))
        org.junit.Assert.assertEquals(7, SettingsUiLogic.stepPageIndex(0, -1, 8))
        org.junit.Assert.assertEquals(0, SettingsUiLogic.stepPageIndex(7, 1, 8))
        org.junit.Assert.assertEquals(6, SettingsUiLogic.stepPageIndex(7, -1, 8))
        org.junit.Assert.assertEquals(0, SettingsUiLogic.stepPageIndex(0, 1, 1))
        org.junit.Assert.assertEquals(0, SettingsUiLogic.stepPageIndex(5, -1, 0))
    }

    @Test
    fun `navActiveSurface maintains high contrast for active text and accent icon across all themes`() {
        val white = 0xFFFFFFFF.toInt()
        for (tokens in ThemePack.BUILTINS) {
            val activeBg = SettingsUiLogic.navActiveSurface(tokens, tokens.accentColor)
            val textContrast = ThemePack.contrastRatio(white, activeBg)
            assertTrue(
                "Active nav white text contrast=$textContrast too low on theme=${tokens.id}",
                textContrast >= 8.0,
            )
            val iconColor = ThemePack.accentTextColor(tokens.accentColor, activeBg)
            val iconContrast = ThemePack.contrastRatio(iconColor, activeBg)
            assertTrue(
                "Active nav icon contrast=$iconContrast too low on theme=${tokens.id}",
                iconContrast >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `friendlyKeycap formats standard gamepad keys and returns null for unknown keycodes`() {
        assertEquals("A", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("L1", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals("L3", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertEquals("START", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals("\u25B2", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(SettingsUiLogic.friendlyKeycap(999))

        val map = mapOf(KeyEvent.KEYCODE_BUTTON_A to Action.CONFIRM)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, SettingsUiLogic.boundKeyCodeFor(Action.CONFIRM, map))
        assertNull(SettingsUiLogic.boundKeyCodeFor(Action.SWAP_SCREENS, map))
    }

    @Test
    fun `scrapeProgressFraction clamps zero, partial, complete, and overflow totals`() {
        assertEquals(0f, SettingsUiLogic.scrapeProgressFraction(0, 0), 0.0001f)
        assertEquals(0f, SettingsUiLogic.scrapeProgressFraction(5, -2), 0.0001f)
        assertEquals(0f, SettingsUiLogic.scrapeProgressFraction(-3, 10), 0.0001f)
        assertEquals(0.25f, SettingsUiLogic.scrapeProgressFraction(5, 20), 0.0001f)
        assertEquals(1f, SettingsUiLogic.scrapeProgressFraction(20, 20), 0.0001f)
        assertEquals(1f, SettingsUiLogic.scrapeProgressFraction(25, 20), 0.0001f)
    }

    @Test
    fun `credential readiness helpers detect SGDB and RetroAchievements configuration`() {
        assertFalse(SettingsUiLogic.isConfiguredStatus(null))
        assertFalse(SettingsUiLogic.isConfiguredStatus("   "))
        assertTrue(SettingsUiLogic.isConfiguredStatus("abc"))

        assertFalse(SettingsUiLogic.hasRaCredentials("user", null))
        assertFalse(SettingsUiLogic.hasRaCredentials(null, "key"))
        assertTrue(SettingsUiLogic.hasRaCredentials("user", "key"))

        assertFalse(SettingsUiLogic.hasAnyArtCredentials(null, null, null))
        assertFalse(SettingsUiLogic.hasAnyArtCredentials("", "user", ""))
        assertTrue(SettingsUiLogic.hasAnyArtCredentials("sgdb-token", null, null))
        assertTrue(SettingsUiLogic.hasAnyArtCredentials(null, "user", "ra-key"))
    }

    @Test
    fun `enabledPowerRailsCount and chevronRotationDegrees reflect BrowseChrome presets`() {
        assertEquals(0, SettingsUiLogic.enabledPowerRailsCount(BrowseChrome.MINIMAL))
        assertEquals(
            SettingsUiLogic.TOTAL_POWER_RAILS,
            SettingsUiLogic.enabledPowerRailsCount(BrowseChrome.FULL),
        )
        val custom = BrowseChrome.MINIMAL.copy(deckStatusPill = true, resumeChip = true)
        assertEquals(2, SettingsUiLogic.enabledPowerRailsCount(custom))
        // Core platformChips / collectionRails toggles do not alter the power-rail count.
        val coreOff = BrowseChrome.MINIMAL.copy(platformChips = false, collectionRails = false)
        assertEquals(0, SettingsUiLogic.enabledPowerRailsCount(coreOff))

        assertEquals(0f, SettingsUiLogic.chevronRotationDegrees(false), 0.001f)
        assertEquals(180f, SettingsUiLogic.chevronRotationDegrees(true), 0.001f)
    }

    @Test
    fun `modalChipTextColor meets WCAG AA contrast across all tones and built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val accent = tokens.accentColor

            val accentFg = SettingsUiLogic.modalChipTextColor(
                SettingsUiLogic.ModalChipTone.ACCENT, accent, tokens,
            )
            assertTrue(
                "Modal ACCENT chip contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(accentFg, accent) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val neutralFg = SettingsUiLogic.modalChipTextColor(
                SettingsUiLogic.ModalChipTone.NEUTRAL, accent, tokens,
            )
            assertTrue(
                "Modal NEUTRAL chip contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(neutralFg, tokens.chipIdle) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val destructiveFg = SettingsUiLogic.modalChipTextColor(
                SettingsUiLogic.ModalChipTone.DESTRUCTIVE, accent, tokens,
            )
            assertTrue(
                "Modal DESTRUCTIVE chip contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(destructiveFg, ThemePack.surfaceSunken(tokens)) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `playtimeBarFraction and totalPlaytimeMs normalize stats rows cleanly`() {
        assertEquals(0f, SettingsUiLogic.playtimeBarFraction(0L, 1000L), 0.0001f)
        assertEquals(0f, SettingsUiLogic.playtimeBarFraction(500L, 0L), 0.0001f)
        assertEquals(0.04f, SettingsUiLogic.playtimeBarFraction(1L, 100_000L), 0.0001f)
        assertEquals(0.5f, SettingsUiLogic.playtimeBarFraction(500L, 1000L), 0.0001f)
        assertEquals(1f, SettingsUiLogic.playtimeBarFraction(1000L, 1000L), 0.0001f)
        assertEquals(1f, SettingsUiLogic.playtimeBarFraction(2000L, 1000L), 0.0001f)

        assertEquals(0L, SettingsUiLogic.totalPlaytimeMs(emptyMap()))
        assertEquals(
            90_000L,
            SettingsUiLogic.totalPlaytimeMs(mapOf("a" to 60_000L, "b" to 30_000L, "bad" to -50L)),
        )
    }
}
