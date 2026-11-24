package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.DockSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockBarMetricsTest {

    @Test
    fun `wide 16-9 Odin 2 Portal keeps dock strictly clear of chrome buttons across 4 to 9 slots`() {
        // 1920px @ 320dpi (density 2.0, 960dp wide) and 1920px @ 400dpi (density 2.5, 768dp wide).
        val cases = listOf(
            Triple(1920, 2.0f, 72),
            Triple(1920, 2.5f, 64),
        )
        for ((widthPx, density, suggestedDp) in cases) {
            for (visible in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
                val spec = DockBarMetrics.compute(
                    windowWidthPx = widthPx,
                    density = density,
                    suggestedSlotDp = suggestedDp,
                    visibleSlots = visible,
                    hasChromeIcons = true,
                )
                val totalFootprint = spec.barWidthPx(visible) + spec.sideInsetPx * 2
                assertTrue(
                    "overlap on w=$widthPx d=$density slots=$visible: footprint=$totalFootprint",
                    totalFootprint <= widthPx,
                )
                assertEquals((DockBarMetrics.CHROME_SIDE_INSET_DP * density).toInt(), spec.sideInsetPx)
                assertTrue(
                    "slot too small on wide panel: ${spec.slotSizePx}",
                    spec.slotSizePx >= (48 * density).toInt(),
                )
            }
        }
    }

    @Test
    fun `narrow One X Sugar secondary panel without chrome icons fits all 9 slots comfortably`() {
        // 1080px @ 320dpi (density 2.0 = 540dp wide), no chrome buttons on smaller dual panel.
        val widthPx = 1080
        val density = 2.0f
        for (visible in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
            val spec = DockBarMetrics.compute(
                windowWidthPx = widthPx,
                density = density,
                suggestedSlotDp = 48,
                visibleSlots = visible,
                hasChromeIcons = false,
            )
            val totalFootprint = spec.barWidthPx(visible) + spec.sideInsetPx * 2
            assertTrue("overflow on Sugar bottom: $totalFootprint > $widthPx", totalFootprint <= widthPx)
            assertEquals((DockBarMetrics.EDGE_MARGIN_DP * density).toInt(), spec.sideInsetPx)
            assertTrue(
                "expected >= 40dp slots on Sugar bottom, got ${spec.slotSizePx / density}dp",
                spec.slotSizePx >= (40 * density).toInt(),
            )
        }
    }

    @Test
    fun `narrow 540dp panel WITH chrome icons never collides with Swap or Settings`() {
        // Worst case: 540dp wide with bottom chrome buttons ON, 4..9 visible slots.
        val widthPx = 1080
        val density = 2.0f
        for (visible in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
            val spec = DockBarMetrics.compute(
                windowWidthPx = widthPx,
                density = density,
                suggestedSlotDp = 48,
                visibleSlots = visible,
                hasChromeIcons = true,
            )
            val totalFootprint = spec.barWidthPx(visible) + spec.sideInsetPx * 2
            assertTrue(
                "collision on 540dp+chrome slots=$visible: footprint=$totalFootprint > $widthPx",
                totalFootprint <= widthPx,
            )
            assertTrue("slot collapsed too small: ${spec.slotSizePx}", spec.slotSizePx >= (32 * density).toInt())
        }
    }

    @Test
    fun `floating dock bottom margin is positive and consistent`() {
        val spec = DockBarMetrics.compute(
            windowWidthPx = 1920,
            density = 2.0f,
            suggestedSlotDp = 64,
            visibleSlots = 5,
            hasChromeIcons = true,
        )
        assertEquals(16, spec.bottomMarginPx)
    }

    @Test
    fun `iconSizePx and blankPlusTextSp scale optically across Sugar and Odin slots`() {
        val compactSpec = DockBarMetrics.compute(
            windowWidthPx = 1080,
            density = 2.0f,
            suggestedSlotDp = 48,
            visibleSlots = 9,
            hasChromeIcons = true,
        )
        val compactIcon = compactSpec.iconSizePx()
        assertTrue("compact icon $compactIcon must stay strictly inside slot ${compactSpec.slotSizePx}", compactIcon < compactSpec.slotSizePx)
        assertTrue(compactIcon >= 1)

        val compactSp = DockBarMetrics.blankPlusTextSp((compactSpec.slotSizePx / 2.0f).toInt())
        val wideSp = DockBarMetrics.blankPlusTextSp(64)
        assertTrue("compact sp $compactSp in [15, 26]", compactSp in 15f..26f)
        assertTrue("wide sp $wideSp > compact sp $compactSp", wideSp > compactSp)
        assertEquals(DockBarMetrics.MAX_BLANK_PLUS_SP, wideSp, 0.01f)
        assertEquals(DockBarMetrics.MIN_BLANK_PLUS_SP, DockBarMetrics.blankPlusTextSp(28), 0.01f)
        // Degenerate zero/negative slot sizes stay safely bounded.
        assertEquals(1, DockBarMetrics.iconSizePx(0))
        assertEquals(1, DockBarMetrics.iconSizePx(-10))
        assertEquals(DockBarMetrics.MIN_BLANK_PLUS_SP, DockBarMetrics.blankPlusTextSp(0), 0.01f)
    }

    @Test
    fun `barFocusChrome lifts elevation and tints accessible accent hairline on focus or move`() {
        for (tokens in com.visorcraft.ghostgalleon.settings.ThemePack.BUILTINS) {
            val surface = com.visorcraft.ghostgalleon.settings.ThemePack.cardFill(tokens)
            val hairline = com.visorcraft.ghostgalleon.settings.ThemePack.hairlineDivider(tokens)

            val idle = DockBarMetrics.barFocusChrome(null, null, tokens.accentColor, surface, hairline)
            assertEquals(false, idle.active)
            assertEquals(hairline, idle.strokeColor)
            assertEquals(DockBarMetrics.BAR_ELEVATION_IDLE_DP, idle.elevationDp, 0.001f)

            val focused = DockBarMetrics.barFocusChrome(2, null, tokens.accentColor, surface, hairline)
            val moving = DockBarMetrics.barFocusChrome(null, 1, tokens.accentColor, surface, hairline)
            for (activeChrome in listOf(focused, moving)) {
                assertTrue(activeChrome.active)
                assertEquals(DockBarMetrics.BAR_ELEVATION_ACTIVE_DP, activeChrome.elevationDp, 0.001f)
                assertTrue(activeChrome.elevationDp > idle.elevationDp)
                val opaqueStroke = (0xFF shl 24) or (activeChrome.strokeColor and 0x00FFFFFF)
                val ratio = com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(opaqueStroke, surface)
                assertTrue(
                    "${tokens.id} active dock border contrast $ratio < 3.0",
                    ratio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `blankPlusColorForRole highlights focused and lifted slots with WCAG AA contrast`() {
        for (tokens in com.visorcraft.ghostgalleon.settings.ThemePack.BUILTINS) {
            val surface = com.visorcraft.ghostgalleon.settings.ThemePack.cardFill(tokens)
            val normal = DockBarMetrics.blankPlusColorForRole(
                MoveChoreography.SlotRole.NORMAL,
                tokens.accentColor,
                surface,
            )
            assertEquals(com.visorcraft.ghostgalleon.ui.UiDimens.ALPHA_SUBTLE, normal)

            val focused = DockBarMetrics.blankPlusColorForRole(
                MoveChoreography.SlotRole.FOCUSED,
                tokens.accentColor,
                surface,
            )
            val lifted = DockBarMetrics.blankPlusColorForRole(
                MoveChoreography.SlotRole.LIFTED,
                tokens.accentColor,
                surface,
            )
            val drop = DockBarMetrics.blankPlusColorForRole(
                MoveChoreography.SlotRole.DROP_TARGET,
                tokens.accentColor,
                surface,
            )
            assertTrue(
                "${tokens.id} focused blank '+' contrast < 4.5",
                com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(focused, surface) >= 4.5,
            )
            assertTrue(
                "${tokens.id} lifted blank '+' contrast < 4.5",
                com.visorcraft.ghostgalleon.settings.ThemePack.contrastRatio(lifted, surface) >= 4.5,
            )
            assertEquals(
                MoveChoreography.dropTargetStrokeColor(tokens.accentColor, surface),
                drop,
            )
        }
    }

    @Test
    fun `edgeBumpDirection fires only on horizontal wall clamp`() {
        assertEquals(-1, DockBarMetrics.edgeBumpDirection(0, 0, com.visorcraft.ghostgalleon.settings.Action.NAV_LEFT))
        assertEquals(+1, DockBarMetrics.edgeBumpDirection(8, 8, com.visorcraft.ghostgalleon.settings.Action.NAV_RIGHT))
        assertEquals(0, DockBarMetrics.edgeBumpDirection(2, 1, com.visorcraft.ghostgalleon.settings.Action.NAV_LEFT))
        assertEquals(0, DockBarMetrics.edgeBumpDirection(2, 3, com.visorcraft.ghostgalleon.settings.Action.NAV_RIGHT))
        assertEquals(0, DockBarMetrics.edgeBumpDirection(0, 0, com.visorcraft.ghostgalleon.settings.Action.NAV_UP))
        assertEquals(0, DockBarMetrics.edgeBumpDirection(0, 0, com.visorcraft.ghostgalleon.settings.Action.CONFIRM))
    }

    @Test
    fun `adaptive tight padding and capped focus scale prevent neighbor ring collisions across 540dp and 1080dp`() {
        assertEquals(DockBarMetrics.FOCUS_SCALE_TIGHT, DockBarMetrics.focusScaleForSlot(32), 0.0001f)
        assertEquals(DockBarMetrics.FOCUS_SCALE_TIGHT, DockBarMetrics.focusScaleForSlot(36), 0.0001f)
        assertEquals(DockBarMetrics.FOCUS_SCALE_COMFY, DockBarMetrics.focusScaleForSlot(40), 0.0001f)

        val viewports = listOf(
            Triple(720, 2.0f, 44),   // 360dp compact (slotSizeDp <= 36 tight cap exercised!)
            Triple(1080, 2.0f, 48),  // 540dp Sugar
            Triple(2160, 2.0f, 72),  // 1080dp wide
        )
        for ((widthPx, density, suggestedDp) in viewports) {
            for (hasChrome in listOf(false, true)) {
                for (slots in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
                    val spec = DockBarMetrics.compute(
                        windowWidthPx = widthPx,
                        density = density,
                        suggestedSlotDp = suggestedDp,
                        visibleSlots = slots,
                        hasChromeIcons = hasChrome,
                    )
                    val slotDp = (spec.slotSizePx / density).toInt()
                    val scale = DockBarMetrics.focusScaleForSlot(slotDp)
                    val focusedHalfW = (spec.slotSizePx * scale) / 2f
                    val halfBudget = (spec.slotSizePx / 2f) + spec.slotMarginHPx
                    assertTrue(
                        "ring collision on w=$widthPx d=$density chrome=$hasChrome slots=$slots: focusedHalf=$focusedHalfW > budget=$halfBudget",
                        focusedHalfW <= halfBudget + 0.01f,
                    )
                    assertTrue(
                        "barPadHPx must be tight (6dp) or comfy (10dp)",
                        spec.barPadHPx == (DockBarMetrics.BAR_PAD_H_TIGHT_DP * density).toInt() ||
                            spec.barPadHPx == (DockBarMetrics.BAR_PAD_H_COMFY_DP * density).toInt(),
                    )
                }
            }
        }
    }
}
