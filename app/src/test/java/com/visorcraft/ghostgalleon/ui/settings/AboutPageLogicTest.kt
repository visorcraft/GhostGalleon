package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertFalse(AboutPageLogic.shouldStackHero(540))
    }

    @Test
    fun `formatBuildCopyText and legalButtonsEqualWeight format cleanly across inputs and widths`() {
        assertEquals(
            "Ghost Galleon v0.16.0 (b77dc04)",
            AboutPageLogic.formatBuildCopyText("Ghost Galleon", "0.16.0", "b77dc04"),
        )
        assertEquals(
            "Ghost Galleon v0.16.0 (b77dc04)",
            AboutPageLogic.formatBuildCopyText("  Ghost Galleon ", "v0.16.0", " b77dc04 "),
        )
        assertEquals(
            "v0.16.0 (b77dc04)",
            AboutPageLogic.formatBuildCopyText("", "0.16.0", "b77dc04"),
        )
        assertEquals(
            "Ghost Galleon v0.16.0",
            AboutPageLogic.formatBuildCopyText("Ghost Galleon", "0.16.0", ""),
        )
        assertTrue(AboutPageLogic.legalButtonsEqualWeight(360))
        assertTrue(AboutPageLogic.legalButtonsEqualWeight(540))
        assertTrue(AboutPageLogic.legalButtonsEqualWeight(960))
    }

    @Test
    fun `stepTabIndex wraps cleanly and tabScrollCenterX centers and clamps horizontal scroll`() {
        assertTrue(AboutPageLogic.LICENSE_TAB_MIN_HEIGHT_DP >= 44)
        assertEquals(1, AboutPageLogic.stepTabIndex(0, 1, 4))
        assertEquals(3, AboutPageLogic.stepTabIndex(0, -1, 4))
        assertEquals(0, AboutPageLogic.stepTabIndex(3, 1, 4))
        assertEquals(2, AboutPageLogic.stepTabIndex(3, -1, 4))
        assertEquals(0, AboutPageLogic.stepTabIndex(0, 1, 1))
        assertEquals(0, AboutPageLogic.stepTabIndex(2, -1, 0))

        // First tab clamps at 0.
        assertEquals(0, AboutPageLogic.tabScrollCenterX(tabLeft = 10, tabW = 100, viewportW = 300, contentW = 700))
        // Middle tab centers inside viewport.
        assertEquals(200, AboutPageLogic.tabScrollCenterX(tabLeft = 300, tabW = 100, viewportW = 300, contentW = 700))
        // Last tab clamps at maxScroll (contentW - viewportW = 400).
        assertEquals(400, AboutPageLogic.tabScrollCenterX(tabLeft = 580, tabW = 120, viewportW = 300, contentW = 700))
        // Content narrower than viewport clamps to 0.
        assertEquals(0, AboutPageLogic.tabScrollCenterX(tabLeft = 120, tabW = 80, viewportW = 400, contentW = 280))
    }

    @Test
    fun `creditGroupCounts clamps counts and featureIconResForIndex maps 4 distinct icons`() {
        val counts = AboutPageLogic.creditGroupCounts(runtimeCount = 6, bundledCount = 7)
        assertEquals(6, counts.runtimeCount)
        assertEquals(7, counts.bundledCount)
        assertEquals(13, counts.totalCount)

        val clamped = AboutPageLogic.creditGroupCounts(runtimeCount = -2, bundledCount = 4)
        assertEquals(0, clamped.runtimeCount)
        assertEquals(4, clamped.bundledCount)
        assertEquals(4, clamped.totalCount)

        val icons = (0..3).map(AboutPageLogic::featureIconResForIndex)
        assertEquals(4, icons.toSet().size)
        assertEquals(R.drawable.ic_nav_apps, icons[0])
        assertEquals(R.drawable.ic_nav_display, icons[1])
        assertEquals(R.drawable.ic_nav_library, icons[2])
        assertEquals(R.drawable.ic_swap, icons[3])
    }
}
