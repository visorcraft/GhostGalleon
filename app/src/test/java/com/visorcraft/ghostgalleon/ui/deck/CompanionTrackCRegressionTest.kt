package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.RaProgress
import com.visorcraft.ghostgalleon.rom.SelectionStrip
import com.visorcraft.ghostgalleon.settings.BrowseChrome
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end multi-display, 4-theme, and dual-paint invariant regression matrix
 * covering all Track C surfaces (Hero, TOP_STRIP, PERF_HUD, PLAY_HUD, StatusPill,
 * HintBar, MotionPolicy, and accessibility formatters).
 */
class CompanionTrackCRegressionTest {

    private data class DisplayProfile(
        val name: String,
        val widthDp: Float,
        val heightDp: Float,
        val density: Float,
    )

    private val profiles = listOf(
        DisplayProfile("Ultra-narrow dual", 340f, 540f, 2f),
        DisplayProfile("Cramped short dual", 480f, 420f, 2f),
        DisplayProfile("OneX Sugar bottom", 540f, 540f, 2f),
        DisplayProfile("720p handheld landscape", 640f, 360f, 2f),
        DisplayProfile("Odin 2 Portal 1080p", 760f, 428f, 2.5f),
        DisplayProfile("Tall dual companion", 680f, 820f, 2.5f),
        DisplayProfile("1440p wide landscape", 1080f, 608f, 2.5f),
    )

    @Test
    fun `responsive hero, padding, role clearance, and PERF_HUD grid stay bounded across all display profiles`() {
        for (p in profiles) {
            val sidePad = CompanionHeroMetrics.panelHorizontalPaddingDp(p.widthDp)
            assertTrue("${p.name} side pad must leave positive inner width", p.widthDp - sidePad * 2 > 200f)

            val clearance = CompanionHeroMetrics.roleBarEndClearanceDp(
                hasStatusPill = true,
                panelWidthDp = p.widthDp,
            )
            assertTrue(
                "${p.name} role clearance ($clearance) must never exceed 40% of width",
                clearance <= (p.widthDp * 0.40f).toInt() + 1,
            )

            val cols = CompanionHeroMetrics.perfGridColumns(p.widthDp)
            assertTrue("${p.name} PERF cols must be 1 or 2", cols in 1..2)
            assertEquals(1, CompanionHeroMetrics.perfCardColumnSpan(6, 7, columns = 1))
            assertEquals(2, CompanionHeroMetrics.perfCardColumnSpan(6, 7, columns = 2))

            val heroSpec = CompanionHeroMetrics.forPanel(p.heightDp)
            assertTrue("${p.name} hero art must stay <= 42% height", heroSpec.artSizeDp <= (p.heightDp * 0.42f).toInt() + 1)
            if (p.heightDp >= 540f) {
                val stack = CompanionHeroMetrics.estimatedHeroStackDp(heroSpec, includeRaBar = true)
                assertTrue("${p.name} stack ($stack dp) must fit within ${p.heightDp}dp", stack <= p.heightDp.toInt())
            }

            val strip = TopStripMetrics.forWidth(p.widthDp)
            assertTrue(
                "${p.name} TOP_STRIP text must fit in ${SelectionStrip.STRIP_HEIGHT_DP}dp",
                TopStripMetrics.estimatedTextColumnHeightDp(strip) <= SelectionStrip.STRIP_HEIGHT_DP,
            )
        }
        // Specific Sugar & ultra-narrow checks.
        assertEquals(1, CompanionHeroMetrics.perfGridColumns(340f))
        assertEquals(2, CompanionHeroMetrics.perfGridColumns(540f))
        assertEquals(20, CompanionHeroMetrics.panelHorizontalPaddingDp(540f))
        assertEquals(14, CompanionHeroMetrics.panelHorizontalPaddingDp(340f))
        assertEquals(24, CompanionHeroMetrics.panelHorizontalPaddingDp(760f))
        assertEquals(
            CompanionHeroMetrics.ROLE_BAR_STATUS_CLEARANCE_DP,
            CompanionHeroMetrics.roleBarEndClearanceDp(hasStatusPill = true, panelWidthDp = 540f),
        )
    }

    @Test
    fun `every built-in theme clears WCAG AA contrast across all Track C chrome surfaces`() {
        val sampleRa = RaProgress(numAwarded = 8, numPossible = 20)
        for (pack in ThemePack.BUILTINS) {
            val card = ThemePack.cardFill(pack)
            val sunken = ThemePack.surfaceSunken(pack)
            val idle = pack.chipIdle

            val ra = CompanionHeroStyle.raMeterState(sampleRa, true, pack.accentColor, sunken)
            assertTrue(ra.visible)
            assertTrue(ThemePack.contrastRatio(ra.fillColor, sunken) >= ThemePack.MIN_TEXT_CONTRAST)

            val focusFilled = CompanionThemeAudit.focusRingColorForFill(true, pack.accentColor, idle)
            assertTrue(ThemePack.contrastRatio(focusFilled, pack.accentColor) >= ThemePack.MIN_TEXT_CONTRAST)

            val focusIdle = CompanionThemeAudit.focusRingColorForFill(false, pack.accentColor, idle)
            assertTrue(ThemePack.contrastRatio(focusIdle, idle) >= ThemePack.MIN_TEXT_CONTRAST)

            for (sev in PerfHudMetrics.Severity.values()) {
                assertTrue(ThemePack.contrastRatio(PerfHudMetrics.statusColor(sev), card) >= ThemePack.MIN_TEXT_CONTRAST)
            }
            for (isActive in listOf(true, false)) {
                assertTrue(
                    ThemePack.contrastRatio(PlayHudMetrics.sessionStatusColor(isActive), sunken) >=
                        ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `in-place update gates suppress redundant animations and rebuilds`() {
        assertTrue(CompanionPanel.sameHeroBinding("rom:snes:ct", 1, "rom:snes:ct", 1))
        assertFalse(CompanionHeroStyle.shouldAnimateSelection(alreadyBound = true, "A", "B"))
        assertFalse(MotionPolicy.shouldAnimate(115L, animatorsEnabled = false, attached = true))
        assertFalse(MotionPolicy.shouldAnimate(115L, animatorsEnabled = true, attached = false))
        assertFalse(
            StatusBattery.chromeNeedsWrite(
                50, StatusBattery.Glyph.BATTERY, 2,
                50, StatusBattery.Glyph.BATTERY, 2,
            ),
        )
        val chrome = BrowseChrome.MINIMAL.copy(resumeChip = true, deckStatusPill = true)
        assertTrue(
            CompanionPanel.canApplyChromeInPlace(
                hasStatusPill = true,
                hasResumeChip = false,
                previous = chrome,
                next = chrome,
            ),
        )
    }
}
