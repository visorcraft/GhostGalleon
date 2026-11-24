package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutPageLogicTest {

    @Test
    fun `clampTabIndex guards negative, overflow, and empty bounds`() {
        assertEquals(0, AboutPageLogic.clampTabIndex(-3))
        assertEquals(0, AboutPageLogic.clampTabIndex(0))
        assertEquals(3, AboutPageLogic.clampTabIndex(3))
        assertEquals(3, AboutPageLogic.clampTabIndex(99))
        assertEquals(0, AboutPageLogic.clampTabIndex(2, count = 0))
    }

    @Test
    fun `hero sheen, active tab underline, and credit license pills meet contrast budgets across all themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val accent = tokens.accentColor

            val sheenAlpha = AboutPageLogic.heroSheenAlpha(accent)
            assertTrue("sheenAlpha=$sheenAlpha out of subtle range on ${tokens.id}", sheenAlpha in 0x10..0x30)

            val underline = AboutPageLogic.tabUnderlineColor(accent)
            val underlineRatio = ThemePack.contrastRatio(underline, accent)
            assertTrue(
                "Active license tab underline contrast=$underlineRatio too low on ${tokens.id}",
                underlineRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val licenseFg = AboutPageLogic.creditLicenseTextColor(accent, tokens)
            val licenseRatio = ThemePack.contrastRatio(licenseFg, ThemePack.surfaceSunken(tokens))
            assertTrue(
                "Credit license pill contrast=$licenseRatio too low on ${tokens.id}",
                licenseRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `responsive feature columns and hero stacking adapt cleanly to narrow vs wide panels`() {
        assertEquals(1, AboutPageLogic.featureColumnCount(360))
        assertEquals(1, AboutPageLogic.featureColumnCount(480))
        assertEquals(2, AboutPageLogic.featureColumnCount(540))
        assertEquals(2, AboutPageLogic.featureColumnCount(960))

        assertTrue(AboutPageLogic.shouldStackHero(360))
        org.junit.Assert.assertFalse(AboutPageLogic.shouldStackHero(540))
    }
}
