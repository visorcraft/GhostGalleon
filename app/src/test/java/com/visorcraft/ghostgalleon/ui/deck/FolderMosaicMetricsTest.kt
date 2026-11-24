package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderMosaicMetricsTest {

    @Test
    fun `mosaic quadrants always fit strictly inside container across Sugar and Odin viewports`() {
        val cases = listOf(
            // Sugar compact cell (44dp @ 2.0)
            Triple(88, 2.0f, ThemePack.GHOST),
            // Standard cell (72dp @ 2.0)
            Triple(144, 2.0f, ThemePack.GHOST),
            // OLED standard cell (72dp @ 2.0)
            Triple(144, 2.0f, ThemePack.OLED),
            // Odin 2 Portal large cell (112dp @ 2.5)
            Triple(280, 2.5f, ThemePack.NEON),
            // Teal 96dp @ 2.0
            Triple(192, 2.0f, ThemePack.THREEDS),
        )
        for ((iconPx, density, tokens) in cases) {
            val iconDp = (iconPx / density).toInt()
            val outerRadiusDp = DeckCornerMetrics.gridTileInnerRadiusDp(tokens, 8, iconDp)
            val outerRadiusPx = outerRadiusDp * density
            val spec = FolderMosaicMetrics.compute(iconPx, outerRadiusPx, density)
            val footprint = spec.innerPadPx * 2 + spec.miniTilePx * 2 + spec.gapPx
            assertTrue(
                "mosaic overflow on iconPx=$iconPx theme=${tokens.id}: $footprint > $iconPx",
                footprint <= iconPx,
            )
            assertTrue("expected mosaic enabled on iconPx=$iconPx", spec.showMosaic)
            assertTrue(
                "mini radius ${spec.miniCornerRadiusPx} exceeded outer $outerRadiusPx",
                spec.miniCornerRadiusPx <= outerRadiusPx + 0.01f,
            )
            assertTrue(
                "mini radius ${spec.miniCornerRadiusPx} too circular for tile ${spec.miniTilePx}",
                spec.miniCornerRadiusPx <= spec.miniTilePx * 0.36f + 0.01f,
            )
        }
    }

    @Test
    fun `degenerate tiny folder box falls back cleanly from mosaic`() {
        val spec = FolderMosaicMetrics.compute(iconSizePx = 20, outerCornerRadiusPx = 8f, density = 2.0f)
        assertFalse("microscopic tile must disable mosaic", spec.showMosaic)
        assertTrue(spec.miniTilePx >= 1)
    }

    @Test
    fun `previewMembers takes at most 4 distinct non-blank keys in order`() {
        assertEquals(emptyList<String>(), FolderMosaicMetrics.previewMembers(emptyList()))
        assertEquals(
            listOf("rom:a", "com.app.one", "rom:b", "com.app.two"),
            FolderMosaicMetrics.previewMembers(
                listOf("rom:a", " ", "com.app.one", "rom:a", "rom:b", "com.app.two", "rom:c"),
            ),
        )
    }

    @Test
    fun `countBadgeText handles zero, normal counts, and large overflow`() {
        assertEquals("0", FolderMosaicMetrics.countBadgeText(-2))
        assertEquals("0", FolderMosaicMetrics.countBadgeText(0))
        assertEquals("7", FolderMosaicMetrics.countBadgeText(7))
        assertEquals("99", FolderMosaicMetrics.countBadgeText(99))
        assertEquals("99+", FolderMosaicMetrics.countBadgeText(100))
        assertEquals("99+", FolderMosaicMetrics.countBadgeText(512))
    }

    @Test
    fun `badgeSpec hides empty folders, derives half-height pill radius, and scales sp across 48 to 128dp`() {
        val empty = FolderMosaicMetrics.badgeSpec(iconSizePx = 144, count = 0, density = 2f)
        assertFalse("empty folder should hide count badge", empty.visible)
        assertEquals("", empty.text)

        val negative = FolderMosaicMetrics.badgeSpec(iconSizePx = 144, count = -3, density = 2f)
        assertFalse(negative.visible)

        var prevSp = 0f
        for (iconDp in listOf(48, 64, 80, 96, 128)) {
            val spec = FolderMosaicMetrics.badgeSpec(
                iconSizePx = (iconDp * 2f).toInt(),
                count = 12,
                density = 2f,
            )
            assertTrue(spec.visible)
            assertEquals("12", spec.text)
            assertTrue(
                "textSp ${spec.textSp} out of bounds at ${iconDp}dp",
                spec.textSp in FolderMosaicMetrics.BADGE_MIN_TEXT_SP..FolderMosaicMetrics.BADGE_MAX_TEXT_SP,
            )
            assertTrue("textSp should scale monotonically at ${iconDp}dp", spec.textSp >= prevSp)
            assertEquals(
                "capsule cornerRadius must be half of pillHeightPx",
                spec.pillHeightPx * 0.5f,
                spec.cornerRadiusPx,
                0.001f,
            )
            assertTrue(spec.minWidthPx >= spec.pillHeightPx)
            prevSp = spec.textSp
        }
        val capped = FolderMosaicMetrics.badgeSpec(iconSizePx = 192, count = 140, density = 2f)
        assertEquals("99+", capped.text)
    }
}
