package com.visorcraft.ghostgalleon.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    }
}
