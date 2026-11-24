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

        // Near-square cover uses full-bleed CENTER_CROP with 0 inset padding.
        val squareCover = ArtTileMetrics.framingFor(
            kind = ArtTileMetrics.ArtFrameKind.COVER_ART,
            bitmapWidth = 512,
            bitmapHeight = 512,
            targetPx = 400,
            density = 2f,
        )
        assertTrue(squareCover.centerCrop)
        assertEquals(0, squareCover.insetPaddingPx)
        assertFalse(squareCover.roundBitmapCorners)

        // Portrait 2:3 cover preserves full artwork via FIT_CENTER + rounded bitmap corners.
        val portraitCover = ArtTileMetrics.framingFor(
            kind = ArtTileMetrics.ArtFrameKind.COVER_ART,
            bitmapWidth = 600,
            bitmapHeight = 900,
            targetPx = 400,
            density = 2f,
        )
        assertFalse(portraitCover.centerCrop)
        assertEquals(0, portraitCover.insetPaddingPx)
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
}
