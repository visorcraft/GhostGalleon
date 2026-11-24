package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileBackgroundsTest {

    @Test
    fun `cardSurfaceKey and selectedSurfaceKey isolate card from accent and invalidate on surface or density changes`() {
        val base = ThemePack.GHOST
        val c1 = TileBackgrounds.cardSurfaceKey(base, density = 2f)
        assertEquals(c1, TileBackgrounds.cardSurfaceKey(base, density = 2f))

        // Multi-display density difference must not reuse stale stroke/radius px.
        assertNotEquals(c1, TileBackgrounds.cardSurfaceKey(base, density = 3f))

        // Custom JSON edit reusing same theme id with new panelLift must invalidate.
        val customBase = ThemeTokens(
            id = "custom",
            displayName = "Custom",
            accentColor = 0xFF00AAFF.toInt(),
            panelLift = 0xFF181820.toInt(),
        )
        val customUpdated = customBase.copy(panelLift = 0xFF282030.toInt())
        assertNotEquals(
            TileBackgrounds.cardSurfaceKey(customBase, density = 2f),
            TileBackgrounds.cardSurfaceKey(customUpdated, density = 2f),
        )

        // Radius change invalidates card surface key.
        assertNotEquals(
            c1,
            TileBackgrounds.cardSurfaceKey(base.copy(cardRadiusDp = 12), density = 2f),
        )

        // Custom user accent changes selectedSurfaceKey without changing cardSurfaceKey
        // (no cache ping-pong between card() and selected() on NAV ticks).
        val sDefault = TileBackgrounds.selectedSurfaceKey(base, base.accentColor, density = 2f)
        val sCustom = TileBackgrounds.selectedSurfaceKey(base, 0xFFFF0000.toInt(), density = 2f)
        assertEquals(sDefault.surface, sCustom.surface)
        assertEquals(c1, sCustom.surface)
        assertNotEquals(sDefault, sCustom)
    }

    @Test
    fun `statusPillFill preserves RGB and applies translucent alpha`() {
        val fill = TileBackgrounds.statusPillFill(0xFF1C1C22.toInt())
        assertEquals(0x1C1C22, fill and 0x00FFFFFF)
        val alpha = (fill ushr 24) and 0xFF
        assertTrue("expected semi-opaque alpha, got $alpha", alpha in 0xC0..0xE8)
    }

    @Test
    fun `responsiveCardWidthPx clamps for wide 16-9 and narrow short screens`() {
        // 1920px @ 2.0x (960dp wide Odin 2 Portal): caps to maxDp (440dp = 880px).
        val wide = OverlayPrimitives.responsiveCardWidthPx(
            screenWidthPx = 1920,
            density = 2f,
            maxDp = 440,
            widthFrac = 0.88f,
        )
        assertEquals(880, wide)

        // Narrow 720px @ 2.0x (360dp wide): respects 88% fraction (633px) & margins (656px).
        val narrow = OverlayPrimitives.responsiveCardWidthPx(
            screenWidthPx = 720,
            density = 2f,
            maxDp = 440,
            widthFrac = 0.88f,
        )
        assertEquals((720 * 0.88f).toInt(), narrow)
        assertTrue(narrow <= 720 - 64)
    }

    @Test
    fun `maxModalHeightPx caps cleanly on short 540dp Sugar panel and tiny screens`() {
        // 1080px @ 2.0x (540dp height) * 0.72 = 777px
        val sugar = OverlayPrimitives.maxModalHeightPx(
            screenHeightPx = 1080,
            density = 2f,
            heightFrac = 0.72f,
        )
        assertEquals((1080 * 0.72f).toInt(), sugar)

        // Tiny screen never exceeds screenHeightPx.
        val tiny = OverlayPrimitives.maxModalHeightPx(
            screenHeightPx = 200,
            density = 2f,
            heightFrac = 0.72f,
            minDp = 180,
        )
        assertEquals(200, tiny)
    }
}
