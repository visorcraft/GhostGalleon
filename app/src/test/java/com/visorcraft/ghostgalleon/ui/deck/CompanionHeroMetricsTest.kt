package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionHeroMetricsTest {

    @Test
    fun `sugar bottom short panel shrinks art so title fits`() {
        // ~540dp usable height on Sugar secondary after bars.
        val s = CompanionHeroMetrics.forPanel(panelHeightDp = 540f)
        assertTrue("art must leave room for title, got ${s.artSizeDp}", s.artSizeDp <= 180)
        assertTrue(s.nameSp <= 24f)
        assertFalse(s.showExtraMedia)
        assertTrue(s.nameMaxLines >= 2)
    }

    @Test
    fun `very short panel clamps art under half height`() {
        val s = CompanionHeroMetrics.forPanel(panelHeightDp = 400f)
        assertTrue(s.artSizeDp <= (400 * 0.42f).toInt())
        assertTrue(s.artSizeDp >= 100)
        assertTrue(s.bannerHeightFraction < 0.35f)
    }

    @Test
    fun `tall panel keeps full 240 art`() {
        val s = CompanionHeroMetrics.forPanel(panelHeightDp = 900f)
        assertEquals(240, s.artSizeDp)
        assertEquals(32f, s.nameSp, 0.01f)
        assertTrue(s.showExtraMedia)
        assertTrue(s.showQuickChips)
    }

    @Test
    fun `banner height scales with fraction`() {
        val s = CompanionHeroMetrics.forPanel(540f)
        val px = CompanionHeroMetrics.bannerHeightPx(1080, s)
        assertTrue(px in 1 until 1080)
        assertTrue(px < 1080 * 2 / 5) // smaller than old fixed 2/5 on compact
    }

    @Test
    fun `brand ship size scales with panel height not game-hero art`() {
        val density = 2f
        val tall = CompanionHeroMetrics.brandShipLayout(panelHeightPx = 1080, density = density)
        val short = CompanionHeroMetrics.brandShipLayout(panelHeightPx = 540, density = density)
        // Taller panel → larger ship (until max clamp).
        assertTrue(tall.sizePx > short.sizePx)
        // Independent of forPanel compact art (game selection tiles).
        val compactArt = CompanionHeroMetrics.forPanel(540f).artSizeDp * density
        assertTrue(
            "brand ship must not use compact game art size",
            tall.sizePx != compactArt.toInt() || short.sizePx > compactArt,
        )
        // Min/max clamps at 2x density: 120dp–280dp → 240–560px.
        assertTrue(tall.sizePx in 240..560)
        assertTrue(short.sizePx in 240..560)
    }

    @Test
    fun `brand ship hull sits on waterline of 3-2 scene`() {
        val panelH = 1080
        val layout = CompanionHeroMetrics.brandShipLayout(panelH, density = 2f)
        val hullY = layout.topMarginPx +
            layout.sizePx * CompanionHeroMetrics.BRAND_SHIP_HULL_FRACTION
        val waterline = panelH * CompanionHeroMetrics.BRAND_WATERLINE_FRACTION
        // Integer px layout may be ±1 from pure float waterline.
        assertTrue(
            "hullY=$hullY waterline=$waterline",
            kotlin.math.abs(hullY - waterline) < 1.5f,
        )
        // Not floating in the upper sky (regression: chrome-column topMargin).
        assertTrue("ship top too high: ${layout.topMarginPx}", layout.topMarginPx > panelH / 4)
    }

    @Test
    fun `brand ship layout empty for zero height`() {
        val z = CompanionHeroMetrics.brandShipLayout(0, density = 2f)
        assertEquals(0, z.sizePx)
        assertEquals(0, z.topMarginPx)
        assertEquals(0, CompanionHeroMetrics.brandShipTopMarginPx(0, 480))
        assertEquals(0, CompanionHeroMetrics.brandShipTopMarginPx(1080, 0))
    }

    @Test
    fun `roleBarEndClearanceDp reserves room only on narrow panels with StatusPill`() {
        assertEquals(0, CompanionHeroMetrics.roleBarEndClearanceDp(hasStatusPill = false, panelWidthDp = 540f))
        assertEquals(
            CompanionHeroMetrics.ROLE_BAR_STATUS_CLEARANCE_DP,
            CompanionHeroMetrics.roleBarEndClearanceDp(hasStatusPill = true, panelWidthDp = 540f),
        )
        assertEquals(0, CompanionHeroMetrics.roleBarEndClearanceDp(hasStatusPill = true, panelWidthDp = 800f))
    }

    @Test
    fun `perfCardColumnSpan spans full row for odd trailing card only`() {
        for (i in 0 until 6) {
            assertEquals(1, CompanionHeroMetrics.perfCardColumnSpan(i, 6))
        }
        for (i in 0 until 6) {
            assertEquals(1, CompanionHeroMetrics.perfCardColumnSpan(i, 7))
        }
        assertEquals(2, CompanionHeroMetrics.perfCardColumnSpan(6, 7))
    }

    @Test
    fun `brandCaptionFitsBelowShip allows caption on Sugar 540dp and rejects cramped windows`() {
        val density = 2f
        val sugar = CompanionHeroMetrics.brandShipLayout(panelHeightPx = 1080, density = density)
        assertTrue(
            CompanionHeroMetrics.brandCaptionFitsBelowShip(1080, sugar, density),
        )
        val tiny = CompanionHeroMetrics.brandShipLayout(panelHeightPx = 420, density = density)
        assertFalse(
            CompanionHeroMetrics.brandCaptionFitsBelowShip(420, tiny, density),
        )
    }

    @Test
    fun `logoSizeDp scales cleanly by panel tier and hides on ultra-compact panels`() {
        val ultraCompact = CompanionHeroMetrics.logoSizeDp(CompanionHeroMetrics.forPanel(440f))
        assertFalse(ultraCompact.visible)
        assertEquals(0, ultraCompact.widthDp)
        assertEquals(0, ultraCompact.heightDp)

        val sugar = CompanionHeroMetrics.logoSizeDp(CompanionHeroMetrics.forPanel(540f))
        assertTrue(sugar.visible)
        assertEquals(140, sugar.widthDp)
        assertEquals(34, sugar.heightDp)

        val tall = CompanionHeroMetrics.logoSizeDp(CompanionHeroMetrics.forPanel(720f))
        assertTrue(tall.visible)
        assertEquals(200, tall.widthDp)
        assertEquals(48, tall.heightDp)
    }

    @Test
    fun `hero vertical stack budget fits inside panel height with banner logo RA and quick chips`() {
        for (heightDp in listOf(420f, 540f, 680f, 900f)) {
            val spec = CompanionHeroMetrics.forPanel(heightDp)
            val stackDp = CompanionHeroMetrics.estimatedHeroStackDp(
                spec = spec,
                includeRaBar = true,
                includeLogo = true,
                hasResumeChip = false,
                useBanner = true,
                panelHeightDp = heightDp,
            )
            assertTrue(
                "Stack ($stackDp dp) with banner+logo+RA+chips must fit inside ${heightDp}dp",
                stackDp <= heightDp.toInt(),
            )
        }
    }

    @Test
    fun `sublineMaxLines tightens with Resume chip on compact panels and Sugar stack fits 540dp`() {
        val sugar = CompanionHeroMetrics.forPanel(540f)
        assertEquals(2, CompanionHeroMetrics.sublineMaxLines(sugar, hasResumeChip = false))
        assertEquals(1, CompanionHeroMetrics.sublineMaxLines(sugar, hasResumeChip = true))

        val tall = CompanionHeroMetrics.forPanel(720f)
        assertEquals(2, CompanionHeroMetrics.sublineMaxLines(tall, hasResumeChip = true))

        val sugarWithResume = CompanionHeroMetrics.estimatedHeroStackDp(
            spec = sugar,
            includeRaBar = true,
            includeLogo = true,
            hasResumeChip = true,
            useBanner = false,
            panelHeightDp = 540f,
        )
        assertTrue(
            "Sugar 540dp hero stack with Resume chip active ($sugarWithResume dp) must fit <= 540dp",
            sugarWithResume <= 540,
        )
    }

    @Test
    fun `isSelectedPinned tracks live ROM presence in gridSlots`() {
        val slots = listOf("com.app.a", null, "rom:snes:ct", null)
        assertTrue(CompanionPanel.isSelectedPinned("rom:snes:ct", slots))
        assertFalse(CompanionPanel.isSelectedPinned("rom:snes:sm", slots))
        assertFalse(CompanionPanel.isSelectedPinned(null, slots))
    }
}
