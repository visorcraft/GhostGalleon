package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMotionAndContrastTest {

    @Test
    fun `DeckMotionPolicy gates transitions and continuous pulses cleanly`() {
        assertTrue(DeckMotionPolicy.shouldAnimate(animatorsEnabled = true, isLaidOut = true))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = true, isLaidOut = false))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = false, isLaidOut = true))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = false, isLaidOut = false))

        assertTrue(DeckMotionPolicy.shouldPulse(animatorsEnabled = true))
        assertFalse(DeckMotionPolicy.shouldPulse(animatorsEnabled = false))
    }

    @Test
    fun `accessibleFocusStroke preserves passing accents and lifts dark accents to WCAG 1_4_11`() {
        // Teal, OLED, and Neon accents already exceed 3.0:1 over their card fill -> returned unchanged.
        for (tokens in listOf(ThemePack.THREEDS, ThemePack.OLED, ThemePack.NEON)) {
            val fill = ThemePack.cardFill(tokens)
            assertEquals(
                "expected ${tokens.id} accent untouched",
                tokens.accentColor,
                DeckThemeContrast.accessibleFocusStroke(tokens.accentColor, fill),
            )
        }

        // Ghost indigo (#3F51B5) + adversarial dark accents must all clear >= 3.0:1.
        val testAccents = ThemePack.BUILTINS.map { it.accentColor } + listOf(
            0xFF0D1B4C.toInt(), // deep navy
            0xFF2B0033.toInt(), // deep plum
            0xFF000000.toInt(), // pure black
            0xFFFFFFFF.toInt(), // pure white
        )
        for (tokens in ThemePack.BUILTINS) {
            val fill = ThemePack.cardFill(tokens)
            for (accent in testAccents) {
                val stroke = DeckThemeContrast.accessibleFocusStroke(accent, fill)
                val ratio = ThemePack.contrastRatio(stroke, fill)
                assertTrue(
                    "theme=${tokens.id} accent=${accent.toUInt().toString(16)} ratio=$ratio < 3.0",
                    ratio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `accessibleFocusStroke darkens light accents on custom light surfaces`() {
        val lightSurface = 0xFFF2F2F7.toInt()
        val stroke = DeckThemeContrast.accessibleFocusStroke(0xFFE0E0E0.toInt(), lightSurface)
        val ratio = ThemePack.contrastRatio(stroke, lightSurface)
        assertTrue("light surface focus ratio $ratio < 3.0", ratio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST)
    }

    private fun refLuminance(color: Int): Double {
        fun ch(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch((color shr 16) and 0xFF) +
            0.7152 * ch((color shr 8) and 0xFF) +
            0.0722 * ch(color and 0xFF)
    }

    private fun refContrast(fg: Int, bg: Int): Double {
        val l1 = refLuminance(fg)
        val l2 = refLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun refAccessibleFocusStroke(
        accent: Int,
        surfaceFill: Int,
        minRatio: Double = DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
    ): Int {
        val opaque = (0xFF shl 24) or (accent and 0x00FFFFFF)
        val bg = (0xFF shl 24) or (surfaceFill and 0x00FFFFFF)
        if (refContrast(opaque, bg) >= minRatio) return opaque
        val target = maxOf(minRatio, 3.15)
        if (refLuminance(bg) >= GridTileFeedbackMetrics.LIGHT_SURFACE_LUMINANCE) {
            var lo = 0.0
            var hi = 1.0
            var best = 0xFF000000.toInt()
            repeat(16) {
                val mid = (lo + hi) * 0.5
                val cand = MoveChoreography.blendRgb(opaque, 0xFF000000.toInt(), mid.toFloat())
                if (refContrast(cand, bg) >= target) {
                    best = cand
                    hi = mid
                } else {
                    lo = mid
                }
            }
            return if (refContrast(best, bg) >= minRatio) best else 0xFF000000.toInt()
        }
        val r0 = (opaque shr 16) and 0xFF
        val g0 = (opaque shr 8) and 0xFF
        val b0 = opaque and 0xFF
        val maxCh = maxOf(r0, maxOf(g0, b0))
        fun pack(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        val base = if (maxCh > 0) {
            val maxScale = 255.0 / maxCh
            val brightest = pack(
                kotlin.math.ceil(r0 * maxScale).toInt(),
                kotlin.math.ceil(g0 * maxScale).toInt(),
                kotlin.math.ceil(b0 * maxScale).toInt(),
            )
            if (refContrast(brightest, bg) >= minRatio) {
                var lo = 1.0
                var hi = maxScale
                var best = brightest
                repeat(16) {
                    val mid = (lo + hi) * 0.5
                    val cand = pack(
                        kotlin.math.ceil(r0 * mid).toInt(),
                        kotlin.math.ceil(g0 * mid).toInt(),
                        kotlin.math.ceil(b0 * mid).toInt(),
                    )
                    if (refContrast(cand, bg) >= target) {
                        best = cand
                        hi = mid
                    } else {
                        lo = mid
                    }
                }
                return if (refContrast(best, bg) >= minRatio) best else brightest
            }
            brightest
        } else {
            opaque
        }
        val br = (base shr 16) and 0xFF
        val bgCh = (base shr 8) and 0xFF
        val bb = base and 0xFF
        var lo = 0.0
        var hi = 1.0
        var best = 0xFFFFFFFF.toInt()
        repeat(16) {
            val mid = (lo + hi) * 0.5
            val cand = pack(
                kotlin.math.ceil(br + (255 - br) * mid).toInt(),
                kotlin.math.ceil(bgCh + (255 - bgCh) * mid).toInt(),
                kotlin.math.ceil(bb + (255 - bb) * mid).toInt(),
            )
            if (refContrast(cand, bg) >= target) {
                best = cand
                hi = mid
            } else {
                lo = mid
            }
        }
        return if (refContrast(best, bg) >= minRatio) best else 0xFFFFFFFF.toInt()
    }

    @Test
    fun `accessibleFocusStroke matches reference bit-for-bit and isolates custom minRatio from default cache`() {
        DeckThemeContrast.clearCacheForTest()

        val accents = ThemePack.BUILTINS.map { it.accentColor } + listOf(
            0xFF000000.toInt(),
            0xFF0D1B4C.toInt(),
            0xFF2B0033.toInt(),
            0xFF441100.toInt(),
            0xFF003311.toInt(),
            0xFF707070.toInt(),
            0xFFE0E0E0.toInt(),
            0xFFFFFFFF.toInt(),
            0x663F51B5,
        )
        val surfaces = ThemePack.BUILTINS.map { ThemePack.cardFill(it) } + listOf(
            0xFF000000.toInt(),
            0xFFF2F2F7.toInt(),
            0xFFFFFFFF.toInt(),
        )

        for (surface in surfaces) {
            for (accent in accents) {
                val expected = refAccessibleFocusStroke(accent, surface)
                assertEquals(expected, DeckThemeContrast.accessibleFocusStroke(accent, surface))
                assertEquals(expected, DeckThemeContrast.accessibleFocusStroke(accent, surface))
            }
        }

        // Custom minRatio must neither read from nor poison the default 3.0:1 cache.
        val ghost = ThemePack.GHOST.accentColor
        val fill = ThemePack.cardFill(ThemePack.GHOST)
        val stroke3 = DeckThemeContrast.accessibleFocusStroke(ghost, fill)
        val stroke7 = DeckThemeContrast.accessibleFocusStroke(ghost, fill, minRatio = 7.0)
        assertEquals(refAccessibleFocusStroke(ghost, fill, 7.0), stroke7)
        assertTrue(ThemePack.contrastRatio(stroke7, fill) >= 7.0)
        assertEquals(stroke3, DeckThemeContrast.accessibleFocusStroke(ghost, fill))
    }

    @Test
    fun `accessibleFocusStroke cache stays bounded and 10000 calls complete in milliseconds`() {
        DeckThemeContrast.clearCacheForTest()
        val surface = ThemePack.cardFill(ThemePack.GHOST)
        for (i in 0 until DeckThemeContrast.FOCUS_STROKE_CACHE_MAX_SIZE + 200) {
            val accent = (0xFF shl 24) or ((i * 53) and 0xFF shl 16) or ((i * 29) and 0xFF shl 8) or (i and 0xFF)
            assertEquals(
                refAccessibleFocusStroke(accent, surface),
                DeckThemeContrast.accessibleFocusStroke(accent, surface),
            )
            assertTrue(
                "focusStrokeCache exceeded cap: ${DeckThemeContrast.focusStrokeCacheSize()}",
                DeckThemeContrast.focusStrokeCacheSize() <= DeckThemeContrast.FOCUS_STROKE_CACHE_MAX_SIZE,
            )
        }

        DeckThemeContrast.clearCacheForTest()
        val hotAccents = intArrayOf(
            ThemePack.GHOST.accentColor,
            ThemePack.THREEDS.accentColor,
            ThemePack.OLED.accentColor,
            ThemePack.NEON.accentColor,
            0xFF0D1B4C.toInt(),
        )
        val hotSurfaces = intArrayOf(
            ThemePack.cardFill(ThemePack.GHOST),
            ThemePack.cardFill(ThemePack.OLED),
            ThemePack.surfaceSunken(ThemePack.GHOST),
        )
        repeat(200) { i ->
            DeckThemeContrast.accessibleFocusStroke(
                hotAccents[i % hotAccents.size],
                hotSurfaces[i % hotSurfaces.size],
            )
        }
        val startNs = System.nanoTime()
        var sink = 0
        repeat(10_000) { i ->
            sink = sink xor DeckThemeContrast.accessibleFocusStroke(
                hotAccents[i % hotAccents.size],
                hotSurfaces[i % hotSurfaces.size],
            )
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        assertTrue(sink != 0 || sink == 0)
        assertTrue(
            "expected 10,000 cached accessibleFocusStroke calls < 200ms, took ${"%.2f".format(elapsedMs)}ms",
            elapsedMs < 200.0,
        )
    }
}
