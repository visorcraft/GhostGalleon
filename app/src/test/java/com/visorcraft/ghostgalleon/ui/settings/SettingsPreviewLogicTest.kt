package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.audio.SoundPackId
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.input.SeatAnchor
import com.visorcraft.ghostgalleon.input.SecondSeatPolicy
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreviewLogicTest {

    @Test
    fun `miniGridMetrics clamps inputs and scales icon and card proportions monotonically`() {
        val min = SettingsPreviewLogic.miniGridMetrics(
            columns = 1,
            iconSizeDp = 16,
            cardSizeDp = 50,
            showLabels = false,
            gridDirection = "vertical",
        )
        assertEquals(SettingsPreviewLogic.MIN_COLUMNS, min.columns)
        assertEquals(2, min.rows)
        assertFalse(min.showLabels)
        assertFalse(min.horizontal)

        val mid = SettingsPreviewLogic.miniGridMetrics(
            columns = 5,
            iconSizeDp = 88,
            cardSizeDp = 220,
            showLabels = true,
            gridDirection = "horizontal",
        )
        assertEquals(5, mid.columns)
        assertTrue(mid.showLabels)
        assertTrue(mid.horizontal)

        val max = SettingsPreviewLogic.miniGridMetrics(
            columns = 12,
            iconSizeDp = 200,
            cardSizeDp = 500,
            showLabels = true,
            gridDirection = "HORIZONTAL",
        )
        assertEquals(SettingsPreviewLogic.MAX_COLUMNS, max.columns)
        assertTrue(max.horizontal)

        assertTrue("iconScale must increase monotonically", min.iconScale < mid.iconScale && mid.iconScale < max.iconScale)
        assertTrue("cardRatio must increase monotonically", min.cardRatio < mid.cardRatio && mid.cardRatio < max.cardRatio)
    }

    @Test
    fun `computeMiniGridCells produces non-overlapping in-bounds tiles with one focused`() {
        val cells = SettingsPreviewLogic.computeMiniGridCells(
            width = 300f,
            height = 120f,
            columns = 5,
            rows = 2,
            gapPx = 6f,
            focusedIndex = 1,
        )
        assertEquals(10, cells.size)
        assertEquals(1, cells.count { it.focused })
        assertTrue(cells[1].focused)
        for (cell in cells) {
            assertTrue(cell.left >= 0f && cell.right <= 300.01f)
            assertTrue(cell.top >= 0f && cell.bottom <= 120.01f)
            assertTrue(cell.right > cell.left)
            assertTrue(cell.bottom > cell.top)
        }
        // Adjacent cells in row 0 do not overlap.
        assertTrue(cells[0].right <= cells[1].left + 0.01f)
        // Row 0 sits above row 1.
        assertTrue(cells[0].bottom <= cells[5].top + 0.01f)

        assertTrue(
            SettingsPreviewLogic.computeMiniGridCells(0f, 100f, 5, 2, 4f).isEmpty(),
        )
    }

    @Test
    fun `scaledCornerRadiusPx reflects theme cardRadiusDp differences`() {
        val ghostR = SettingsPreviewLogic.scaledCornerRadiusPx(ThemePack.GHOST.cardRadiusDp, density = 2f)
        val oledR = SettingsPreviewLogic.scaledCornerRadiusPx(ThemePack.OLED.cardRadiusDp, density = 2f)
        assertTrue("Ghost ($ghostR) should be rounder than OLED ($oledR)", ghostR > oledR)
        assertTrue(oledR >= 4f)
    }

    @Test
    fun `SecondSeatPreviewLogic maps default anchors into padded viewport and classifies glyphs`() {
        val dots = SecondSeatPreviewLogic.layoutDots(
            anchors = SecondSeatPolicy.DEFAULT_ANCHORS,
            widthPx = 320f,
            heightPx = 180f,
            padPx = 16f,
        )
        assertEquals(SecondSeatPolicy.DEFAULT_ANCHORS.size, dots.size)
        assertEquals(4, dots.count { it.isDpad })
        assertEquals("\u25B2", SecondSeatPreviewLogic.glyphForAnchor("up"))
        assertEquals("A", SecondSeatPreviewLogic.glyphForAnchor("a"))
        assertEquals("ST", SecondSeatPreviewLogic.glyphForAnchor("start"))
        assertEquals("L1", SecondSeatPreviewLogic.glyphForAnchor("l1"))
        for (dot in dots) {
            assertTrue("xPx=${dot.xPx} out of padded bounds", dot.xPx in 16f..304f)
            assertTrue("yPx=${dot.yPx} out of padded bounds", dot.yPx in 16f..164f)
        }

        // Clamps out-of-range and NaN coordinates cleanly.
        val clamped = SecondSeatPreviewLogic.layoutDots(
            anchors = listOf(SeatAnchor("a", -0.5f, 2f), SeatAnchor("b", Float.NaN, Float.POSITIVE_INFINITY)),
            widthPx = 200f,
            heightPx = 100f,
            padPx = 10f,
        )
        assertEquals(10f, clamped[0].xPx, 0.01f)
        assertEquals(90f, clamped[0].yPx, 0.01f)
        assertEquals(100f, clamped[1].xPx, 0.01f)
        assertEquals(50f, clamped[1].yPx, 0.01f)

        assertTrue(SecondSeatPreviewLogic.layoutDots(SecondSeatPolicy.DEFAULT_ANCHORS, 0f, 100f, 10f).isEmpty())
    }

    @Test
    fun `MiniGridCellCache reuses precomputed cells across unchanged frames and invalidates on resize or column change`() {
        val cache = MiniGridCellCache()
        val first = cache.ensure(width = 300f, height = 120f, columns = 5, rows = 2, gapPx = 5f, focusedIndex = 1)
        assertEquals(10, first.size)
        assertEquals(1, cache.recomputeCount)

        // Repeated frames (e.g. 120 Hz icon/card slider drags) reuse the exact cached list with zero recomputation.
        repeat(60) {
            val hit = cache.ensure(width = 300f, height = 120f, columns = 5, rows = 2, gapPx = 5f, focusedIndex = 1)
            assertSame(first, hit)
        }
        assertEquals(1, cache.recomputeCount)

        // Changing columns recomputes cleanly.
        val nextCols = cache.ensure(width = 300f, height = 120f, columns = 6, rows = 2, gapPx = 5f, focusedIndex = 1)
        assertNotSame(first, nextCols)
        assertEquals(12, nextCols.size)
        assertEquals(2, cache.recomputeCount)

        // Resizing viewport recomputes cleanly.
        val resized = cache.ensure(width = 360f, height = 120f, columns = 6, rows = 2, gapPx = 5f, focusedIndex = 1)
        assertNotSame(nextCols, resized)
        assertEquals(3, cache.recomputeCount)
        assertTrue(resized.last().right > nextCols.last().right)
    }

    @Test
    fun `SecondSeatDotCache reuses precomputed dots across redraws and recomputes on anchor or viewport changes`() {
        val cache = SecondSeatDotCache()
        val initial = SecondSeatPolicy.DEFAULT_ANCHORS
        val first = cache.ensure(initial, widthPx = 320f, heightPx = 180f, padPx = 16f)
        assertEquals(initial.size, first.size)
        assertEquals(1, cache.recomputeCount)

        // Same reference or structurally equal anchor list hits cache (zero allocations on highlight/redraw).
        assertSame(first, cache.ensure(initial, widthPx = 320f, heightPx = 180f, padPx = 16f))
        assertSame(first, cache.ensure(initial.toList(), widthPx = 320f, heightPx = 180f, padPx = 16f))
        assertEquals(1, cache.recomputeCount)

        // Mutating an anchor coordinate invalidates and moves that dot.
        val moved = initial.map { if (it.id == "a") SeatAnchor("a", 0.1f, 0.2f) else it }
        val afterMove = cache.ensure(moved, widthPx = 320f, heightPx = 180f, padPx = 16f)
        assertNotSame(first, afterMove)
        assertEquals(2, cache.recomputeCount)
        val dotA = afterMove.first { it.id == "a" }
        assertEquals(16f + 0.1f * 288f, dotA.xPx, 0.01f)
        assertEquals(16f + 0.2f * 148f, dotA.yPx, 0.01f)

        // Verify DPAD classification handles whitespace and uppercase cleanly.
        assertTrue(SecondSeatPreviewLogic.isDpadAnchor(" UP "))
        assertTrue(SecondSeatPreviewLogic.isDpadAnchor("left"))
        assertFalse(SecondSeatPreviewLogic.isDpadAnchor("a"))
        assertFalse(SecondSeatPreviewLogic.isDpadAnchor("start"))
    }

    @Test
    fun `scaledBarHeightPx and previewFocusStroke scale with fontScale and meet WCAG 1_4_11`() {
        val small = SettingsPreviewLogic.scaledBarHeightPx(5f, fontScale = 0.85f, density = 2f)
        val normal = SettingsPreviewLogic.scaledBarHeightPx(5f, fontScale = 1.0f, density = 2f)
        val large = SettingsPreviewLogic.scaledBarHeightPx(5f, fontScale = 1.25f, density = 2f)
        val clampedHigh = SettingsPreviewLogic.scaledBarHeightPx(5f, fontScale = 2.0f, density = 2f)
        val clampedLow = SettingsPreviewLogic.scaledBarHeightPx(5f, fontScale = 0.2f, density = 2f)
        val nanScale = SettingsPreviewLogic.scaledBarHeightPx(5f, fontScale = Float.NaN, density = 2f)

        assertTrue(small < normal && normal < large)
        assertEquals(large, clampedHigh, 0.001f)
        assertEquals(small, clampedLow, 0.001f)
        assertEquals(normal, nanScale, 0.001f)

        for (tokens in ThemePack.BUILTINS) {
            val stroke = SettingsPreviewLogic.previewFocusStroke(tokens.accentColor, tokens)
            assertTrue(
                "previewFocusStroke contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(stroke, ThemePack.cardFill(tokens)) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
            assertTrue(
                "previewFocusStroke contrast on elevated too low on ${tokens.id}",
                ThemePack.contrastRatio(stroke, ThemePack.surfaceElevated(tokens)) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
        }
    }

    @Test
    fun `flankingCardScale and clampedLabelReservePx balance dense grids and carousel preview`() {
        val sMin = SettingsPreviewLogic.flankingCardScale(0.52f)
        val sMax = SettingsPreviewLogic.flankingCardScale(0.98f)
        assertTrue("flankingCardScale out of depth band: $sMin", sMin in 0.72f..0.80f)
        assertTrue("flankingCardScale out of depth band: $sMax", sMax in 0.72f..0.80f)
        assertTrue("flanking scale should deepen slightly as center card grows", sMin >= sMax)
        assertTrue(SettingsPreviewLogic.flankingCardScale(Float.NaN) in 0.72f..0.80f)

        // Hidden labels reserve zero height.
        assertEquals(0f, SettingsPreviewLogic.clampedLabelReservePx(cellH = 40f, showLabels = false, density = 2f), 0.001f)
        // Tall cell caps at 7dp.
        assertEquals(14f, SettingsPreviewLogic.clampedLabelReservePx(cellH = 120f, showLabels = true, density = 2f), 0.001f)
        // Compact 8-column cell caps proportionally at 22% of cellH.
        assertEquals(6.6f, SettingsPreviewLogic.clampedLabelReservePx(cellH = 30f, showLabels = true, density = 2f), 0.01f)
    }

    @Test
    fun `audioSpectrumProfile bounds values, zeroes when muted or off, and differs per pack`() {
        // Muted or OFF flattens cleanly to all zeros.
        val muted = SettingsPreviewLogic.audioSpectrumProfile(SoundPackId.GHOST, sfxVol = 80, ambientVol = 50, enabled = false)
        assertEquals(16, muted.size)
        assertTrue(muted.all { it == 0f })

        val offPack = SettingsPreviewLogic.audioSpectrumProfile(SoundPackId.OFF, sfxVol = 80, ambientVol = 50, enabled = true)
        assertTrue(offPack.all { it == 0f })

        assertEquals(0f, SettingsPreviewLogic.audioAmbientFloor(SoundPackId.GHOST, 50, soundEnabled = false, ambientEnabled = true), 0.001f)
        assertEquals(0f, SettingsPreviewLogic.audioAmbientFloor(SoundPackId.GHOST, 50, soundEnabled = true, ambientEnabled = false), 0.001f)
        assertEquals(0.5f, SettingsPreviewLogic.audioAmbientFloor(SoundPackId.GHOST, 50, soundEnabled = true, ambientEnabled = true), 0.001f)

        // Distinct acoustic envelopes across all 4 concrete packs, bounded within [0..1].
        val profiles = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            SettingsPreviewLogic.audioSpectrumProfile(pack, sfxVol = 85, ambientVol = 25, enabled = true, barCount = 16)
        }
        for ((pack, bars) in profiles) {
            assertEquals(16, bars.size)
            assertTrue("$pack must have non-zero energy", bars.any { it > 0.2f })
            assertTrue("$pack bars out of [0..1]", bars.all { it in 0f..1f })
        }
        val keys = SoundPackId.CONCRETE_PACKS
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                val a = profiles.getValue(keys[i])
                val b = profiles.getValue(keys[j])
                assertFalse(
                    "Packs ${keys[i]} and ${keys[j]} must have distinct spectrum envelopes",
                    a.contentEquals(b),
                )
            }
        }
    }

    @Test
    fun `SecondSeatPreviewLogic hit-tests nearest anchor, detects collisions, and clamps coordinates`() {
        assertEquals(0f, SecondSeatPreviewLogic.clampNormalizedCoord(-0.4f), 0.001f)
        assertEquals(1f, SecondSeatPreviewLogic.clampNormalizedCoord(1.8f), 0.001f)
        assertEquals(0.5f, SecondSeatPreviewLogic.clampNormalizedCoord(Float.NaN), 0.001f)
        assertEquals(0.42f, SecondSeatPreviewLogic.clampNormalizedCoord(0.42f), 0.001f)

        val dots = SecondSeatPreviewLogic.layoutDots(
            anchors = listOf(SeatAnchor("a", 0.2f, 0.5f), SeatAnchor("b", 0.8f, 0.5f)),
            widthPx = 220f,
            heightPx = 120f,
            padPx = 10f,
        )
        // Touch near 'a' (x = 10 + 0.2 * 200 = 50, y = 60).
        val hitA = SecondSeatPreviewLogic.findNearestAnchor(dots, touchX = 56f, touchY = 64f, maxRadiusPx = 28f)
        assertEquals("a", hitA?.id)
        // Touch far away returns null.
        assertNull(SecondSeatPreviewLogic.findNearestAnchor(dots, touchX = 110f, touchY = 60f, maxRadiusPx = 20f))

        // Default anchors have zero collisions; overlapping pair triggers warning set for both IDs.
        assertTrue(SecondSeatPreviewLogic.overlappingAnchorIds(SecondSeatPolicy.DEFAULT_ANCHORS).isEmpty())
        val colliding = SecondSeatPreviewLogic.overlappingAnchorIds(
            listOf(
                SeatAnchor("a", 0.50f, 0.50f),
                SeatAnchor("b", 0.53f, 0.52f),
                SeatAnchor("x", 0.10f, 0.10f),
            ),
        )
        assertEquals(setOf("a", "b"), colliding)
    }

    @Test
    fun `topologyDiagramState models arbitrary display IDs cleanly for DUAL and SINGLE modes`() {
        // Sugar-style non-zero primary (primary=1, companion=0, launch=0).
        val sugar = SettingsPreviewLogic.topologyDiagramState(
            surfaceMode = SurfaceMode.DUAL,
            primaryId = 1,
            companionId = 0,
            launchId = 0,
        )
        assertTrue(sugar.isDual)
        assertEquals(2, sugar.panels.size)
        assertEquals("#0", sugar.panels[0].badgeText)
        assertTrue(sugar.panels[0].isCompanion)
        assertTrue(sugar.panels[0].isLaunchTarget)
        assertFalse(sugar.panels[0].isInteractive)
        assertEquals("#1", sugar.panels[1].badgeText)
        assertTrue(sugar.panels[1].isInteractive)
        assertFalse(sugar.panels[1].isCompanion)

        // Arbitrary display IDs (zero hardcoded 0/1 assumptions).
        val customDual = SettingsPreviewLogic.topologyDiagramState(
            surfaceMode = SurfaceMode.DUAL,
            primaryId = 7,
            companionId = 4,
            launchId = 4,
        )
        assertTrue(customDual.isDual)
        assertEquals(4, customDual.panels[0].displayId)
        assertEquals("#4", customDual.panels[0].badgeText)
        assertEquals(7, customDual.panels[1].displayId)
        assertEquals("#7", customDual.panels[1].badgeText)
        assertNotEquals(customDual.panels[0].displayId, customDual.panels[1].displayId)

        // Single-screen Odin 2 Portal TOP_STRIP mode.
        val single = SettingsPreviewLogic.topologyDiagramState(
            surfaceMode = SurfaceMode.SINGLE,
            primaryId = 3,
            companionId = null,
            launchId = 3,
        )
        assertFalse(single.isDual)
        assertEquals(1, single.panels.size)
        assertEquals("#3", single.panels[0].badgeText)
        assertTrue(single.panels[0].isInteractive)
        assertTrue(single.panels[0].isLaunchTarget)
    }
}
