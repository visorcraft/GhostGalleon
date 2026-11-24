package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckCornerMetricsTest {

    @Test
    fun `nested radius follows outer minus inset and never exceeds outer`() {
        assertEquals(16, DeckCornerMetrics.nestedRadiusDp(outerRadiusDp = 24, insetDp = 8))
        assertEquals(6, DeckCornerMetrics.nestedRadiusDp(outerRadiusDp = 12, insetDp = 8))
        // Tiny custom theme outer radius < default floor must never enlarge inner past outer.
        assertEquals(4, DeckCornerMetrics.nestedRadiusDp(outerRadiusDp = 4, insetDp = 8))
        assertEquals(0, DeckCornerMetrics.nestedRadiusDp(outerRadiusDp = 0, insetDp = 8))
    }

    @Test
    fun `grid tile inner radius is concentric across all built-in themes and icon sizes`() {
        // Ghost/Teal/Neon: 24dp card - 8dp pad = 16dp inner for normal icons.
        assertEquals(16, DeckCornerMetrics.gridTileInnerRadiusDp(ThemePack.GHOST, 8, iconSizeDp = 72))
        assertEquals(16, DeckCornerMetrics.gridTileInnerRadiusDp(ThemePack.THREEDS, 8, iconSizeDp = 72))
        assertEquals(16, DeckCornerMetrics.gridTileInnerRadiusDp(ThemePack.NEON, 8, iconSizeDp = 72))
        // OLED: 12dp card - 8dp pad = 4dp -> floored to crisp 6dp (< 12dp).
        assertEquals(6, DeckCornerMetrics.gridTileInnerRadiusDp(ThemePack.OLED, 8, iconSizeDp = 72))

        // Compact Sugar cell (36dp icon on Ghost) caps softly to squircle fraction.
        val compact = DeckCornerMetrics.gridTileInnerRadiusDp(ThemePack.GHOST, 8, iconSizeDp = 36)
        assertTrue("compact inner radius $compact should stay <= 38% of 36dp", compact <= 14)
        assertTrue("compact inner radius $compact should stay >= 6dp", compact >= 6)

        for (tokens in ThemePack.BUILTINS) {
            for (iconDp in listOf(32, 48, 72, 96, 128)) {
                val inner = DeckCornerMetrics.gridTileInnerRadiusDp(tokens, cellPaddingDp = 8, iconSizeDp = iconDp)
                assertTrue("inner $inner > outer ${tokens.cardRadiusDp}", inner <= tokens.cardRadiusDp)
                assertTrue("inner $inner <= 0", inner > 0)
            }
        }
    }

    @Test
    fun `dock slot and inner content radii form a strictly concentric chain`() {
        val viewports = listOf(
            // Compact Sugar 540dp + chrome tight slot
            34 to 28,
            // Standard Sugar / handheld slot
            48 to 40,
            // Wide Odin 2 Portal slot
            64 to 53,
        )
        for (tokens in ThemePack.BUILTINS) {
            val pillRadius = ThemePack.pillRadiusDp(tokens)
            for ((slotDp, iconDp) in viewports) {
                val slotR = DeckCornerMetrics.dockSlotRadiusDp(tokens, barPadVDp = 8, slotSizeDp = slotDp)
                val innerR = DeckCornerMetrics.dockInnerContentRadiusDp(
                    tokens, barPadVDp = 8, slotSizeDp = slotDp, iconSizeDp = iconDp,
                )
                assertTrue(
                    "${tokens.id} slot=$slotDp: pill($pillRadius) >= slot($slotR) >= inner($innerR)",
                    pillRadius >= slotR && slotR >= innerR && innerR > 0,
                )
                assertTrue(
                    "${tokens.id} slot=$slotDp: slotR($slotR) must not become a full circle",
                    slotR <= (slotDp * DeckCornerMetrics.MAX_TILE_RADIUS_FRACTION).toInt() + 1,
                )
            }
        }
        // Concrete Ghost 48dp / 40dp values: pill 28 -> slot 18 -> inner 14.
        assertEquals(18, DeckCornerMetrics.dockSlotRadiusDp(ThemePack.GHOST, 8, 48))
        assertEquals(14, DeckCornerMetrics.dockInnerContentRadiusDp(ThemePack.GHOST, 8, 48, 40))
        // OLED 48dp / 40dp: pill 16 -> slot 8 -> inner 6.
        assertEquals(8, DeckCornerMetrics.dockSlotRadiusDp(ThemePack.OLED, 8, 48))
        assertEquals(6, DeckCornerMetrics.dockInnerContentRadiusDp(ThemePack.OLED, 8, 48, 40))
    }

    @Test
    fun `folder panel cell and thumb radii nest cleanly across themes`() {
        assertEquals(16, DeckCornerMetrics.folderCellRadiusDp(ThemePack.GHOST))
        assertEquals(12, DeckCornerMetrics.folderThumbRadiusDp(ThemePack.GHOST, cellPaddingDp = 4, thumbSizeDp = 76))
        assertEquals(10, DeckCornerMetrics.folderCellRadiusDp(ThemePack.OLED))
        assertEquals(6, DeckCornerMetrics.folderThumbRadiusDp(ThemePack.OLED, cellPaddingDp = 4, thumbSizeDp = 76))

        val sharpCustom = ThemeTokens(id = "sharp", displayName = "Sharp", accentColor = 0xFF00FFAA.toInt(), cardRadiusDp = 6)
        val cellR = DeckCornerMetrics.folderCellRadiusDp(sharpCustom)
        val thumbR = DeckCornerMetrics.folderThumbRadiusDp(sharpCustom, cellPaddingDp = 4, thumbSizeDp = 64)
        assertTrue("sharp theme chain broken: card=6 >= cell=$cellR >= thumb=$thumbR", 6 >= cellR && cellR >= thumbR)
    }

    @Test
    fun `pixel-space nested radius clamps cleanly`() {
        assertEquals(12f, DeckCornerMetrics.nestedRadiusPx(20f, 8f, minRadiusPx = 4f), 0.001f)
        assertEquals(4f, DeckCornerMetrics.nestedRadiusPx(10f, 8f, minRadiusPx = 4f), 0.001f)
        assertEquals(2f, DeckCornerMetrics.nestedRadiusPx(2f, 8f, minRadiusPx = 4f), 0.001f)
    }
}
