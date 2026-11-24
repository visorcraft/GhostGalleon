package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackTest {

    @Test
    fun `byId returns all four builtins and defaults to GHOST`() {
        assertEquals(ThemePack.GHOST, ThemePack.byId("ghost"))
        assertEquals(ThemePack.THREEDS, ThemePack.byId("threeds"))
        assertEquals(ThemePack.OLED, ThemePack.byId("OLED"))
        assertEquals(ThemePack.NEON, ThemePack.byId(" neon "))
        assertEquals(ThemePack.GHOST, ThemePack.byId(null))
        assertEquals(ThemePack.GHOST, ThemePack.byId("unknown"))
        assertEquals(4, ThemePack.BUILTINS.size)
    }

    @Test
    fun `parseJson accepts valid accent hex`() {
        val tokens = ThemePack.parseJson(
            """{"id":"custom","displayName":"Custom","accentColor":"#FF2D95"}""",
        )
        assertNotNull(tokens)
        assertEquals("custom", tokens!!.id)
        assertEquals("Custom", tokens.displayName)
        assertEquals(0xFFFF2D95.toInt(), tokens.accentColor)
    }

    @Test
    fun `applyToSettings changes accent and pack id`() {
        val base = Settings.DEFAULT.copy(accentColor = 0xFF000000.toInt(), themePackId = "ghost")
        val applied = ThemePack.applyToSettings(base, ThemePack.NEON)
        assertEquals(ThemePack.NEON.id, applied.themePackId)
        assertEquals(ThemePack.NEON.accentColor, applied.accentColor)
        assertNull(applied.themeCustomJson)
    }

    @Test
    fun `parseJson null on garbage`() {
        assertNull(ThemePack.parseJson("not json"))
        assertNull(ThemePack.parseJson("{}"))
        assertNull(ThemePack.parseJson("""{"id":"x"}"""))
        assertNull(ThemePack.parseJson("""{"accentColor":"#FF00FF"}"""))
    }

    @Test
    fun `resolve uses custom JSON when set`() {
        val customJson =
            """{"id":"mine","displayName":"Mine","accentColor":"#00FF00"}"""
        val settings = Settings.DEFAULT.copy(
            themePackId = ThemePack.OLED.id,
            themeCustomJson = customJson,
        )
        val tokens = ThemePack.resolve(settings)
        assertEquals("mine", tokens.id)
        assertEquals(0xFF00FF00.toInt(), tokens.accentColor)

        val builtinOnly = Settings.DEFAULT.copy(
            themePackId = ThemePack.OLED.id,
            themeCustomJson = null,
        )
        assertEquals(ThemePack.OLED, ThemePack.resolve(builtinOnly))

        val badCustom = Settings.DEFAULT.copy(
            themePackId = ThemePack.NEON.id,
            themeCustomJson = "garbage",
        )
        assertEquals(ThemePack.NEON, ThemePack.resolve(badCustom))
    }

    @Test
    fun `onFillTextColor is white on dark accents and black on OLED`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(white, ThemePack.onFillTextColor(ThemePack.GHOST.accentColor))
        assertEquals(white, ThemePack.onFillTextColor(ThemePack.THREEDS.accentColor))
        assertEquals(white, ThemePack.onFillTextColor(ThemePack.NEON.accentColor))
        assertEquals(black, ThemePack.onFillTextColor(ThemePack.OLED.accentColor))
        assertEquals(white, ThemePack.onFillTextColor(0xFF1C1C22.toInt()))
    }

    @Test
    fun `accentTextColor lifts dark Ghost indigo to WCAG AA while preserving blue hue`() {
        val surface = ThemePack.DEFAULT_DARK_SURFACE
        val lifted = ThemePack.accentTextColor(ThemePack.GHOST.accentColor, surface)
        val ratio = ThemePack.contrastRatio(lifted, surface)
        org.junit.Assert.assertTrue("expected >= 4.5 contrast, got $ratio", ratio >= 4.5)
        // Must remain opaque and keep blue as the dominant channel (indigo hue).
        assertEquals(0xFF, (lifted ushr 24) and 0xFF)
        val r = (lifted shr 16) and 0xFF
        val g = (lifted shr 8) and 0xFF
        val b = lifted and 0xFF
        org.junit.Assert.assertTrue("expected blue dominant, got rgb($r,$g,$b)", b > g && g >= r)
    }

    @Test
    fun `accentTextColor keeps already legible accents unchanged and lifts arbitrary dark colors`() {
        val surface = ThemePack.DEFAULT_DARK_SURFACE
        for (builtin in listOf(ThemePack.THREEDS, ThemePack.OLED, ThemePack.NEON)) {
            val result = ThemePack.accentTextColor(builtin.accentColor, surface)
            assertEquals(builtin.accentColor, result)
            org.junit.Assert.assertTrue(ThemePack.contrastRatio(result, surface) >= 4.5)
        }
        val darkSamples = listOf(
            0xFF000000.toInt(),
            0xFF101010.toInt(),
            0xFF000080.toInt(),
            0xFF400000.toInt(),
            0xFF004000.toInt(),
            0xFF2B1B4D.toInt(),
            0x803F51B5.toInt(), // translucent input should become opaque + legible
        )
        for (sample in darkSamples) {
            val lifted = ThemePack.accentTextColor(sample, surface)
            assertEquals(0xFF, (lifted ushr 24) and 0xFF)
            val ratio = ThemePack.contrastRatio(lifted, surface)
            org.junit.Assert.assertTrue(
                "sample=${Integer.toHexString(sample)} lifted=${Integer.toHexString(lifted)} ratio=$ratio",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `surface token helpers derive darker sunken fill and valid radii for every builtin`() {
        for (tokens in ThemePack.BUILTINS) {
            val card = ThemePack.cardFill(tokens)
            val sunken = ThemePack.surfaceSunken(tokens)
            org.junit.Assert.assertTrue(
                "sunken should be darker than card for ${tokens.id}",
                ThemePack.relativeLuminance(sunken) < ThemePack.relativeLuminance(card),
            )
            org.junit.Assert.assertTrue(ThemePack.pillRadiusDp(tokens) in 12..32)
            org.junit.Assert.assertTrue(ThemePack.chipRadiusDp(tokens) in 8..14)
            org.junit.Assert.assertTrue((ThemePack.hairlineDivider(tokens) ushr 24) in 0x10..0x40)
        }
        assertEquals(ThemePack.DEFAULT_CARD_FILL, ThemePack.cardFill(ThemePack.GHOST))
        assertEquals(ThemePack.OLED.panelLift, ThemePack.cardFill(ThemePack.OLED))
        val customGhost = ThemePack.GHOST.copy(panelLift = 0xFF282034.toInt())
        assertEquals(0xFF282034.toInt(), ThemePack.cardFill(customGhost))
    }

    @Test
    fun `nestedRadiusDp computes optical concentric radius with floor`() {
        assertEquals(16, ThemePack.nestedRadiusDp(outerRadiusDp = 24, insetDp = 8))
        assertEquals(4, ThemePack.nestedRadiusDp(outerRadiusDp = 12, insetDp = 20))
        assertEquals(6, ThemePack.nestedRadiusDp(outerRadiusDp = 10, insetDp = 20, minDp = 6))
        assertEquals(20, ThemePack.nestedRadiusDp(outerRadiusDp = 20, insetDp = -4))
    }

    @Test
    fun `surfaceElevated stays brighter than cardFill while preserving high white-text contrast`() {
        val white = 0xFFFFFFFF.toInt()
        for (tokens in ThemePack.BUILTINS) {
            val card = ThemePack.cardFill(tokens)
            val elevated = ThemePack.surfaceElevated(tokens)
            org.junit.Assert.assertTrue(
                "elevated should be lighter than cardFill for ${tokens.id}",
                ThemePack.relativeLuminance(elevated) > ThemePack.relativeLuminance(card),
            )
            val contrast = ThemePack.contrastRatio(white, elevated)
            org.junit.Assert.assertTrue(
                "white on elevated contrast=$contrast too low for ${tokens.id}",
                contrast >= 10.0,
            )
        }
    }

    @Test
    fun `withAlpha and luminance-adaptive glow and hairline clamp cleanly`() {
        assertEquals(0x003F51B5, ThemePack.withAlpha(0xFF3F51B5.toInt(), -10))
        assertEquals(0xFF3F51B5.toInt(), ThemePack.withAlpha(0x103F51B5, 999))

        val ghostGlowAlpha = (ThemePack.accentGlowColor(ThemePack.GHOST.accentColor, 0x44) ushr 24) and 0xFF
        val oledGlowAlpha = (ThemePack.accentGlowColor(ThemePack.OLED.accentColor, 0x44) ushr 24) and 0xFF
        assertEquals(0x44, ghostGlowAlpha)
        org.junit.Assert.assertTrue("OLED bright accent glow should dampen alpha ($oledGlowAlpha)", oledGlowAlpha < ghostGlowAlpha)

        val ghostHairAlpha = (ThemePack.accentHairline(ThemePack.GHOST.accentColor, 0x66) ushr 24) and 0xFF
        val oledHairAlpha = (ThemePack.accentHairline(ThemePack.OLED.accentColor, 0x66) ushr 24) and 0xFF
        assertEquals(0x66, ghostHairAlpha)
        org.junit.Assert.assertTrue("OLED bright accent hairline should dampen alpha ($oledHairAlpha)", oledHairAlpha < ghostHairAlpha)

        assertEquals(0xFF000000.toInt(), ThemePack.blendColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), ThemePack.blendColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF7F7F7F.toInt(), ThemePack.blendColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))

        org.junit.Assert.assertTrue(ThemePack.isHighLuminanceAccent(ThemePack.OLED.accentColor))
        org.junit.Assert.assertFalse(ThemePack.isHighLuminanceAccent(ThemePack.GHOST.accentColor))
        org.junit.Assert.assertFalse(ThemePack.isHighLuminanceAccent(ThemePack.THREEDS.accentColor))
        org.junit.Assert.assertFalse(ThemePack.isHighLuminanceAccent(ThemePack.NEON.accentColor))
        for (tokens in ThemePack.BUILTINS) {
            assertEquals(0xFF000000.toInt(), ThemePack.backdropColor(tokens))
        }
    }

    private fun referenceRelativeLuminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun referenceContrastRatio(fg: Int, bg: Int): Double {
        val l1 = referenceRelativeLuminance(fg)
        val l2 = referenceRelativeLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun referenceAccentTextColor(accent: Int, surface: Int): Int {
        val opaque = (0xFF shl 24) or (accent and 0x00FFFFFF)
        val bg = (0xFF shl 24) or (surface and 0x00FFFFFF)
        if (referenceContrastRatio(opaque, bg) >= ThemePack.MIN_TEXT_CONTRAST) return opaque

        val r0 = (opaque shr 16) and 0xFF
        val g0 = (opaque shr 8) and 0xFF
        val b0 = opaque and 0xFF
        val maxCh = maxOf(r0, maxOf(g0, b0))
        val target = 4.6

        fun packRgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        val baseColor = if (maxCh > 0) {
            val maxScale = 255.0 / maxCh
            val brightest = packRgb(
                kotlin.math.ceil(r0 * maxScale).toInt(),
                kotlin.math.ceil(g0 * maxScale).toInt(),
                kotlin.math.ceil(b0 * maxScale).toInt(),
            )
            if (referenceContrastRatio(brightest, bg) >= ThemePack.MIN_TEXT_CONTRAST) {
                var lo = 1.0
                var hi = maxScale
                var best = brightest
                repeat(18) {
                    val mid = (lo + hi) * 0.5
                    val cand = packRgb(
                        kotlin.math.ceil(r0 * mid).toInt(),
                        kotlin.math.ceil(g0 * mid).toInt(),
                        kotlin.math.ceil(b0 * mid).toInt(),
                    )
                    if (referenceContrastRatio(cand, bg) >= target) {
                        best = cand
                        hi = mid
                    } else {
                        lo = mid
                    }
                }
                return if (referenceContrastRatio(best, bg) >= ThemePack.MIN_TEXT_CONTRAST) best else brightest
            }
            brightest
        } else {
            opaque
        }

        val br = (baseColor shr 16) and 0xFF
        val bgCh = (baseColor shr 8) and 0xFF
        val bb = baseColor and 0xFF
        var lo = 0.0
        var hi = 1.0
        var best = 0xFFFFFFFF.toInt()
        repeat(18) {
            val mid = (lo + hi) * 0.5
            val cand = packRgb(
                kotlin.math.ceil(br + (255 - br) * mid).toInt(),
                kotlin.math.ceil(bgCh + (255 - bgCh) * mid).toInt(),
                kotlin.math.ceil(bb + (255 - bb) * mid).toInt(),
            )
            if (referenceContrastRatio(cand, bg) >= target) {
                best = cand
                hi = mid
            } else {
                lo = mid
            }
        }
        return if (referenceContrastRatio(best, bg) >= ThemePack.MIN_TEXT_CONTRAST) best else 0xFFFFFFFF.toInt()
    }

    @Test
    fun `LUT relativeLuminance and accentTextColor match Math pow reference bit-for-bit`() {
        ThemePack.clearCachesForTest()

        // Exhaustive check across all 256 sRGB channel values on R, G, B and gray ramp.
        for (c in 0..255) {
            val samples = intArrayOf(
                (0xFF shl 24) or (c shl 16) or (c shl 8) or c,
                (0xFF shl 24) or (c shl 16),
                (0xFF shl 24) or (c shl 8),
                (0xFF shl 24) or c,
            )
            for (color in samples) {
                assertEquals(
                    "luminance bit mismatch for 0x${Integer.toHexString(color)}",
                    java.lang.Double.doubleToLongBits(referenceRelativeLuminance(color)),
                    java.lang.Double.doubleToLongBits(ThemePack.relativeLuminance(color)),
                )
            }
        }

        val accents = ThemePack.BUILTINS.map { it.accentColor } + listOf(
            0xFF000000.toInt(),
            0xFF010101.toInt(),
            0xFF0A0A0A.toInt(),
            0xFF101010.toInt(),
            0xFF000080.toInt(),
            0xFF400000.toInt(),
            0xFF004000.toInt(),
            0xFF2B1B4D.toInt(),
            0xFF1E3A8A.toInt(),
            0xFF7C2D12.toInt(),
            0xFF065F46.toInt(),
            0xFF7F7F7F.toInt(),
            0xFFFFFFFF.toInt(),
            0x803F51B5.toInt(),
            0x00FF2D95,
        )
        val surfaces = ThemePack.BUILTINS.map { ThemePack.cardFill(it) } +
            ThemePack.BUILTINS.map { ThemePack.surfaceSunken(it) } +
            listOf(0xFF000000.toInt(), 0xFF33333D.toInt(), 0x401C1C22)

        for (surface in surfaces) {
            for (accent in accents) {
                val expected = referenceAccentTextColor(accent, surface)
                val first = ThemePack.accentTextColor(accent, surface)
                val cached = ThemePack.accentTextColor(accent, surface)
                assertEquals(
                    "ARGB mismatch accent=0x${Integer.toHexString(accent)} surface=0x${Integer.toHexString(surface)}",
                    expected,
                    first,
                )
                assertEquals(expected, cached)
            }
        }
    }

    @Test
    fun `resolve memoizes custom JSON and invalidates cleanly on packId or customJson changes`() {
        ThemePack.clearCachesForTest()

        val jsonA = """{"id":"custom_a","displayName":"Custom A","accentColor":"#3F51B5","cardRadiusDp":20}"""
        val jsonB = """{"id":"custom_b","displayName":"Custom B","accentColor":"#00FFAA","cardRadiusDp":14}"""

        val sA = Settings.DEFAULT.copy(themePackId = ThemePack.GHOST.id, themeCustomJson = jsonA)
        val firstA = ThemePack.resolve(sA)
        val secondA = ThemePack.resolve(sA)
        // Same Settings -> cache hit returns exact cached ThemeTokens instance without JSON re-parse.
        assertSame(firstA, secondA)
        assertEquals("custom_a", firstA.id)

        // Fresh String with identical content also hits cache without re-parsing.
        val sACopy = Settings.DEFAULT.copy(
            themePackId = String(ThemePack.GHOST.id.toCharArray()),
            themeCustomJson = String(jsonA.toCharArray()),
        )
        assertSame(firstA, ThemePack.resolve(sACopy))

        // Changing customJson with the same themePackId must invalidate immediately.
        val sB = sA.copy(themeCustomJson = jsonB)
        val resolvedB = ThemePack.resolve(sB)
        assertNotSame(firstA, resolvedB)
        assertEquals("custom_b", resolvedB.id)
        assertEquals(0xFF00FFAA.toInt(), resolvedB.accentColor)
        assertEquals(14, resolvedB.cardRadiusDp)
        assertSame(resolvedB, ThemePack.resolve(sB))

        // Clearing customJson back to null falls back to builtin themePackId.
        val sBuiltin = sB.copy(themePackId = ThemePack.OLED.id, themeCustomJson = null)
        assertEquals(ThemePack.OLED, ThemePack.resolve(sBuiltin))
        assertSame(ThemePack.OLED, ThemePack.resolve(sBuiltin))

        // Invalid customJson must still respect themePackId changes without stale caching.
        val sBadNeon = Settings.DEFAULT.copy(themePackId = ThemePack.NEON.id, themeCustomJson = "{invalid")
        val sBadTeal = Settings.DEFAULT.copy(themePackId = ThemePack.THREEDS.id, themeCustomJson = "{invalid")
        assertEquals(ThemePack.NEON, ThemePack.resolve(sBadNeon))
        assertEquals(ThemePack.THREEDS, ThemePack.resolve(sBadTeal))
        assertNotEquals(ThemePack.resolve(sBadNeon), ThemePack.resolve(sBadTeal))
    }

    @Test
    fun `accentTextColor cache stays strictly bounded under arbitrary color floods`() {
        ThemePack.clearCachesForTest()
        val surface = ThemePack.DEFAULT_DARK_SURFACE
        for (i in 0 until ThemePack.ACCENT_TEXT_CACHE_MAX_SIZE + 200) {
            val accent = (0xFF shl 24) or ((i * 73) and 0xFF shl 16) or ((i * 37) and 0xFF shl 8) or (i and 0xFF)
            val result = ThemePack.accentTextColor(accent, surface)
            assertEquals(referenceAccentTextColor(accent, surface), result)
            assertTrue(
                "accentTextCache exceeded cap: ${ThemePack.accentTextCacheSize()}",
                ThemePack.accentTextCacheSize() <= ThemePack.ACCENT_TEXT_CACHE_MAX_SIZE,
            )
        }
    }

    @Test
    fun `benchmark 10000 cached resolve custom JSON and accentTextColor calls complete in milliseconds`() {
        ThemePack.clearCachesForTest()
        val customSettings = Settings.DEFAULT.copy(
            themePackId = "custom",
            themeCustomJson = """{"id":"bench","displayName":"Bench","accentColor":"#3F51B5","cardRadiusDp":24,"panelLift":"#1C1C22","chipIdle":"#2A2A32"}""",
        )
        val surfaces = intArrayOf(
            ThemePack.DEFAULT_DARK_SURFACE,
            ThemePack.surfaceSunken(ThemePack.GHOST),
            ThemePack.cardFill(ThemePack.OLED),
            ThemePack.cardFill(ThemePack.NEON),
        )
        val accents = intArrayOf(
            ThemePack.GHOST.accentColor,
            ThemePack.THREEDS.accentColor,
            ThemePack.OLED.accentColor,
            ThemePack.NEON.accentColor,
            0xFF0D1B4C.toInt(),
        )

        // Warm up JIT + populate caches.
        repeat(200) { i ->
            ThemePack.resolve(customSettings)
            ThemePack.accentTextColor(accents[i % accents.size], surfaces[i % surfaces.size])
        }

        val startNs = System.nanoTime()
        var sink = 0
        repeat(10_000) { i ->
            val tokens = ThemePack.resolve(customSettings)
            val lifted = ThemePack.accentTextColor(
                accents[i % accents.size],
                surfaces[i % surfaces.size],
            )
            sink = sink xor tokens.cardRadiusDp xor lifted
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        assertNotEquals(0, sink or 1)
        assertTrue(
            "expected 10,000 memoized ThemePack calls < 250ms, took ${"%.2f".format(elapsedMs)}ms",
            elapsedMs < 250.0,
        )
    }
}
