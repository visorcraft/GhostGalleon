package com.visorcraft.ghostgalleon.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtTileMetricsTest {

    @Test
    fun `revealMode snaps on sync memory hits and pre-attach, fades only on attached async loads`() {
        assertEquals(
            ArtTileMetrics.RevealMode.SNAP,
            ArtTileMetrics.revealMode(wasSyncMemoryHit = true, isAttached = true),
        )
        assertEquals(
            ArtTileMetrics.RevealMode.SNAP,
            ArtTileMetrics.revealMode(wasSyncMemoryHit = true, isAttached = false),
        )
        assertEquals(
            ArtTileMetrics.RevealMode.SNAP,
            ArtTileMetrics.revealMode(wasSyncMemoryHit = false, isAttached = false),
        )
        assertEquals(
            ArtTileMetrics.RevealMode.FADE,
            ArtTileMetrics.revealMode(wasSyncMemoryHit = false, isAttached = true),
        )

        assertEquals(0L, ArtTileMetrics.revealDurationMs(ArtTileMetrics.RevealMode.SNAP))
        assertTrue(ArtTileMetrics.revealDurationMs(ArtTileMetrics.RevealMode.FADE) in 80L..250L)
    }

    @Test
    fun `clampedCornerRadiusPx keeps normal card radius and prevents small-tile overspill`() {
        // Carousel card: 180dp @ 2x = 360px, 16dp inner radius = 32px (unclamped).
        assertEquals(
            32f,
            ArtTileMetrics.clampedCornerRadiusPx(cornerRadiusDp = 16, density = 2f, targetPx = 360),
            0.001f,
        )
        // Small dock/strip tile: 48px target with 24dp @ 2x = 48px clamps safely to 24px.
        assertEquals(
            24f,
            ArtTileMetrics.clampedCornerRadiusPx(cornerRadiusDp = 24, density = 2f, targetPx = 48),
            0.001f,
        )
        // Zero/negative inputs clamp safely.
        assertEquals(
            0f,
            ArtTileMetrics.clampedCornerRadiusPx(cornerRadiusDp = -4, density = 2f, targetPx = 100),
            0.001f,
        )
        // Unknown/zero targetPx preserves raw dp radius.
        assertEquals(
            36f,
            ArtTileMetrics.clampedCornerRadiusPx(cornerRadiusDp = 12, density = 3f, targetPx = 0),
            0.001f,
        )
    }

    @Test
    fun `backingStrokePx stays at least 1px across low and high display densities`() {
        assertEquals(1, ArtTileMetrics.backingStrokePx(0.75f))
        assertEquals(1, ArtTileMetrics.backingStrokePx(1.0f))
        assertEquals(2, ArtTileMetrics.backingStrokePx(2.0f))
        assertEquals(3, ArtTileMetrics.backingStrokePx(3.0f))
    }

    @Test
    fun `isNearSquareAspect and framingFor adapt cleanly between square covers, 2-to-3 boxes, and logos`() {
        assertTrue(ArtTileMetrics.isNearSquareAspect(512, 512))
        assertTrue(ArtTileMetrics.isNearSquareAspect(600, 500))
        assertFalse(ArtTileMetrics.isNearSquareAspect(600, 900))
        assertFalse(ArtTileMetrics.isNearSquareAspect(1920, 620))
        assertFalse(ArtTileMetrics.isNearSquareAspect(0, 512))

        // Near-square cover uses full-bleed CENTER_CROP inset 1px inside the backing hairline.
        val squareCover = ArtTileMetrics.framingFor(
            kind = ArtTileMetrics.ArtFrameKind.COVER_ART,
            bitmapWidth = 512,
            bitmapHeight = 512,
            targetPx = 400,
            density = 2f,
        )
        assertTrue(squareCover.centerCrop)
        assertEquals(ArtTileMetrics.COVER_HAIRLINE_INSET_PX, squareCover.insetPaddingPx)
        assertFalse(squareCover.roundBitmapCorners)

        // Portrait 2:3 cover preserves full artwork via FIT_CENTER inset 1px + refined inner corners.
        val portraitCover = ArtTileMetrics.framingFor(
            kind = ArtTileMetrics.ArtFrameKind.COVER_ART,
            bitmapWidth = 600,
            bitmapHeight = 900,
            targetPx = 400,
            density = 2f,
        )
        assertFalse(portraitCover.centerCrop)
        assertEquals(ArtTileMetrics.COVER_HAIRLINE_INSET_PX, portraitCover.insetPaddingPx)
        assertTrue(portraitCover.roundBitmapCorners)

        // Logo fallback uses FIT_CENTER + proportional clamped inset padding without clipping corners.
        val carouselLogo = ArtTileMetrics.framingFor(
            kind = ArtTileMetrics.ArtFrameKind.LOGO_OVERLAY,
            bitmapWidth = 600,
            bitmapHeight = 200,
            targetPx = 400,
            density = 2f,
        )
        val dockLogo = ArtTileMetrics.framingFor(
            kind = ArtTileMetrics.ArtFrameKind.LOGO_OVERLAY,
            bitmapWidth = 600,
            bitmapHeight = 200,
            targetPx = 96,
            density = 2f,
        )
        assertFalse(carouselLogo.centerCrop)
        assertFalse(carouselLogo.roundBitmapCorners)
        assertTrue(carouselLogo.insetPaddingPx > dockLogo.insetPaddingPx)
        assertTrue(dockLogo.insetPaddingPx >= 8)
    }

    @Test
    fun `innerCoverCornerRadiusPx refines 2-to-3, 4-to-3, and wide covers while preserving 1-to-1 outer radius`() {
        val density = 2f
        val outerGhost = 48f // 24dp @ 2x
        val outerMid = 32f   // 16dp @ 2x
        val minPx = ArtTileMetrics.LETTERBOX_CORNER_MIN_DP * density // 8px
        val maxPx = ArtTileMetrics.LETTERBOX_CORNER_MAX_DP * density // 20px

        // 1:1 near-square keeps full outer radius.
        assertEquals(
            outerMid,
            ArtTileMetrics.innerCoverCornerRadiusPx(outerMid, bmpW = 512, bmpH = 512, targetPx = 400, density = density),
            0.001f,
        )
        // 2:3 portrait cover scales to 0.45 * outer, clamped cleanly inside 4..10dp (8..20px).
        val portraitGhost = ArtTileMetrics.innerCoverCornerRadiusPx(outerGhost, bmpW = 600, bmpH = 900, targetPx = 400, density = density)
        assertEquals(maxPx, portraitGhost, 0.001f)
        val portraitMid = ArtTileMetrics.innerCoverCornerRadiusPx(outerMid, bmpW = 600, bmpH = 900, targetPx = 360, density = density)
        assertEquals(outerMid * ArtTileMetrics.LETTERBOX_CORNER_FRAC, portraitMid, 0.001f)
        assertTrue(portraitMid in minPx..maxPx)

        // 4:3 (1.33) and wide (1920x620) covers also get refined physical box-art corners < outerRadiusPx.
        val fourByThree = ArtTileMetrics.innerCoverCornerRadiusPx(outerMid, bmpW = 640, bmpH = 480, targetPx = 360, density = density)
        val wideBanner = ArtTileMetrics.innerCoverCornerRadiusPx(outerMid, bmpW = 1920, bmpH = 620, targetPx = 360, density = density)
        assertEquals(portraitMid, fourByThree, 0.001f)
        assertEquals(portraitMid, wideBanner, 0.001f)
        assertTrue(fourByThree < outerMid)

        // Tiny dock tile clamps safely to <= outerRadiusPx and <= targetPx * 0.25f.
        val tiny = ArtTileMetrics.innerCoverCornerRadiusPx(12f, bmpW = 600, bmpH = 900, targetPx = 64, density = density)
        assertTrue(tiny in minPx..12f)
        assertEquals(0f, ArtTileMetrics.innerCoverCornerRadiusPx(0f, 600, 900, 360), 0.001f)
    }
}
