package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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

    @Test
    fun `template keys cover blankSlot chipIdle sunkenField statusPill and artBacking across densities and themes`() {
        val ghost = ThemePack.GHOST
        val oled = ThemePack.OLED

        // blankSlot defaults to theme cardRadiusDp and overrides cleanly.
        val bDefault = TileBackgrounds.blankSlotKey(ghost, density = 2f)
        val bExplicit = TileBackgrounds.blankSlotKey(ghost, density = 2f, cornerRadiusDp = 12)
        assertEquals(ThemePack.surfaceSunken(ghost), bDefault.fill)
        assertEquals(ThemePack.HAIRLINE_SUBTLE, bDefault.strokeColor)
        assertEquals(48f, bDefault.radiusPx, 0.001f)
        assertEquals(24f, bExplicit.radiusPx, 0.001f)
        assertNotEquals(bDefault, bExplicit)
        assertNotEquals(bDefault, TileBackgrounds.blankSlotKey(ghost, density = 3f))

        // chipIdle uses theme chipIdle fill and hairline divider.
        val chip2x = TileBackgrounds.chipIdleKey(ghost, density = 2f, cornerRadiusDp = 12)
        assertEquals(ghost.chipIdle, chip2x.fill)
        assertEquals(ThemePack.hairlineDivider(ghost), chip2x.strokeColor)
        assertNotEquals(chip2x, TileBackgrounds.chipIdleKey(oled, density = 2f, cornerRadiusDp = 12))

        // sunkenField and artBacking distinguish densities, radii, and themes; negative/NaN radii normalize to 0f.
        val sunken = TileBackgrounds.sunkenFieldKey(ghost, density = 2f, cornerRadiusDp = 12)
        assertEquals(ThemePack.surfaceSunken(ghost), sunken.fill)
        assertEquals(ThemePack.hairlineDivider(ghost), sunken.strokeColor)

        val status = TileBackgrounds.statusPillKey(ghost, density = 2f)
        assertEquals(TileBackgrounds.statusPillFill(ThemePack.cardFill(ghost)), status.fill)
        assertEquals(48f, status.radiusPx, 0.001f)
        assertNotEquals(status, TileBackgrounds.statusPillKey(oled, density = 2f))

        val artDp = TileBackgrounds.artBackingKey(ghost, density = 2f, cornerRadiusDp = 24)
        val artPx = TileBackgrounds.artBackingPxKey(ghost, density = 2f, cornerRadiusPx = 48f)
        assertEquals(artDp, artPx)
        val artZeroNeg = TileBackgrounds.artBackingPxKey(ghost, density = 2f, cornerRadiusPx = -0.0f)
        val artZeroNan = TileBackgrounds.artBackingPxKey(ghost, density = 2f, cornerRadiusPx = Float.NaN)
        assertEquals(artZeroNeg, artZeroNan)
        assertEquals(0f, artZeroNan.radiusPx, 0f)
    }

    @Test
    fun `BoundedTemplateCache prevents dual-display density and accent thrash and enforces strict LRU cap`() {
        val cardCache = TileBackgrounds.BoundedTemplateCache<TileBackgrounds.CardSurfaceKey, String>(
            TileBackgrounds.CARD_CACHE_MAX_ENTRIES,
        )
        val selectedCache = TileBackgrounds.BoundedTemplateCache<TileBackgrounds.SelectedSurfaceKey, String>(
            TileBackgrounds.SELECTED_CACHE_MAX_ENTRIES,
        )

        // Simulate One X Sugar dual displays (e.g. top 2.0x + bottom 2.625x) alternating 500 times
        // with two accents (raw accent + WCAG lifted focus stroke) — 1-slot cache would miss 1000x!
        val topCard = TileBackgrounds.cardSurfaceKey(ThemePack.GHOST, density = 2.0f)
        val bottomCard = TileBackgrounds.cardSurfaceKey(ThemePack.GHOST, density = 2.625f)
        val selA = TileBackgrounds.selectedSurfaceKey(ThemePack.GHOST, ThemePack.GHOST.accentColor, density = 2.0f)
        val selB = TileBackgrounds.selectedSurfaceKey(
            ThemePack.GHOST,
            DeckThemeContrast.accessibleFocusStroke(ThemePack.GHOST.accentColor, ThemePack.cardFill(ThemePack.GHOST)),
            density = 2.625f,
        )

        val expectedTop = cardCache.getOrPut(topCard) { "card-top" }
        val expectedBottom = cardCache.getOrPut(bottomCard) { "card-bottom" }
        val expectedSelA = selectedCache.getOrPut(selA) { "sel-a" }
        val expectedSelB = selectedCache.getOrPut(selB) { "sel-b" }

        repeat(500) {
            assertSame(expectedTop, cardCache.getOrPut(topCard) { error("must not miss") })
            assertSame(expectedBottom, cardCache.getOrPut(bottomCard) { error("must not miss") })
            assertSame(expectedSelA, selectedCache.getOrPut(selA) { error("must not miss") })
            assertSame(expectedSelB, selectedCache.getOrPut(selB) { error("must not miss") })
        }
        assertEquals(2L, cardCache.missCount())
        assertEquals(1000L, cardCache.hitCount())
        assertEquals(2L, selectedCache.missCount())
        assertEquals(1000L, selectedCache.hitCount())

        // Verify strict LRU cap bound: flooding 100 entries never exceeds maxEntries,
        // and recently-touched entries stay resident while oldest entries evict.
        val bounded = TileBackgrounds.BoundedTemplateCache<Int, String>(maxEntries = 8)
        for (i in 0 until 8) {
            bounded.getOrPut(i) { "v$i" }
        }
        // Touch key 0 so key 1 becomes eldest LRU entry.
        assertEquals("v0", bounded.getOrPut(0) { error("key 0 should be cached") })
        bounded.getOrPut(8) { "v8" }
        assertEquals(8, bounded.size())
        assertTrue(bounded.containsKey(0))
        assertFalse(bounded.containsKey(1))
        assertTrue(bounded.containsKey(8))

        for (i in 9 until 100) {
            bounded.getOrPut(i) { "v$i" }
            assertTrue("cache exceeded cap: ${bounded.size()}", bounded.size() <= 8)
        }
        assertEquals(8, bounded.size())
    }
}
