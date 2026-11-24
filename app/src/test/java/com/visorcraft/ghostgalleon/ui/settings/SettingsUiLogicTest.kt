package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertFalse
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
}
