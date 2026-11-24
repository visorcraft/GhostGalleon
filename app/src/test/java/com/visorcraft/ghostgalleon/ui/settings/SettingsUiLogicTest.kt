package com.visorcraft.ghostgalleon.ui.settings

import android.view.KeyEvent
import com.visorcraft.ghostgalleon.settings.Action
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
}
