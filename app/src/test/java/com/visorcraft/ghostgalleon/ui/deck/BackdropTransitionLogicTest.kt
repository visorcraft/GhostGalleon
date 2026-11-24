package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTransitionLogicTest {

    @Test
    fun `wallpaperAction reconciles null, blank, identical, and changed URIs cleanly`() {
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.KEEP,
            BackdropTransitionLogic.wallpaperAction(null, null),
        )
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.KEEP,
            BackdropTransitionLogic.wallpaperAction("   ", ""),
        )
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.ATTACH,
            BackdropTransitionLogic.wallpaperAction(null, "content://wall/1"),
        )
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.KEEP,
            BackdropTransitionLogic.wallpaperAction("content://wall/1", "content://wall/1"),
        )
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.REPLACE,
            BackdropTransitionLogic.wallpaperAction("content://wall/1", "content://wall/2"),
        )
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.REMOVE,
            BackdropTransitionLogic.wallpaperAction("content://wall/1", null),
        )
        assertEquals(
            BackdropTransitionLogic.WallpaperAction.REMOVE,
            BackdropTransitionLogic.wallpaperAction("content://wall/1", "  "),
        )
        assertNull(BackdropTransitionLogic.normalizeUri("  "))
    }

    @Test
    fun `shouldCrossfadeBackdrop triggers only on attached changes after initial paint`() {
        val black = BackdropTransitionLogic.backdropKey(null)
        val snes = BackdropTransitionLogic.backdropKey("snes")
        val gba = BackdropTransitionLogic.backdropKey("gba")

        assertEquals(BackdropTransitionLogic.DEFAULT_BACKDROP_KEY, black)
        assertFalse(BackdropTransitionLogic.shouldCrossfadeBackdrop(null, black, attachedToWindow = true))
        assertFalse(BackdropTransitionLogic.shouldCrossfadeBackdrop(black, black, attachedToWindow = true))
        assertFalse(BackdropTransitionLogic.shouldCrossfadeBackdrop(black, snes, attachedToWindow = false))
        assertTrue(BackdropTransitionLogic.shouldCrossfadeBackdrop(black, snes, attachedToWindow = true))
        assertTrue(BackdropTransitionLogic.shouldCrossfadeBackdrop(snes, gba, attachedToWindow = true))
        assertTrue(BackdropTransitionLogic.shouldCrossfadeBackdrop(snes, black, attachedToWindow = true))
    }

    @Test
    fun `platform backdrop color math stays OLED-dark opaque lift and translucent radial glow`() {
        val accent = 0xFF6482C8.toInt()
        val lift = BackdropTransitionLogic.platformLiftBandColor(accent)
        val glow = BackdropTransitionLogic.platformRadialGlowColor(accent)

        assertEquals(0xFF, (lift ushr 24) and 0xFF)
        assertTrue("lift red too bright", ((lift shr 16) and 0xFF) <= 60)
        assertTrue("lift green too bright", ((lift shr 8) and 0xFF) <= 60)
        assertTrue("lift blue too bright", (lift and 0xFF) <= 60)

        assertEquals(BackdropTransitionLogic.RADIAL_GLOW_ALPHA, (glow ushr 24) and 0xFF)
        assertEquals(accent and 0x00FFFFFF, glow and 0x00FFFFFF)
    }

    @Test
    fun `normalizedPlatformGlow bounds luminance variance across all platforms and all 4 themes`() {
        for (pack in ThemePack.BUILTINS) {
            val isOled = pack.id == ThemePack.OLED.id
            var minPeakLum = Double.MAX_VALUE
            var maxPeakLum = Double.MIN_VALUE
            for (platform in Platforms.ALL) {
                val palette = BackdropTransitionLogic.normalizedPlatformGlow(
                    platformAccent = PlatformLook.accentColor(platform.id),
                    themePanelLift = pack.panelLift,
                    isOled = isOled,
                )
                if (isOled) {
                    assertEquals(
                        "OLED backdrop edge must anchor to pure black for ${platform.id}",
                        BackdropTransitionLogic.OLED_BLACK,
                        palette.edgeColor,
                    )
                }
                val peakLum = ThemePack.relativeLuminance(palette.peakCompositedColor)
                assertTrue(
                    "Peak luminance too bright ($peakLum) for ${platform.id} on ${pack.id}",
                    peakLum <= BackdropTransitionLogic.MAX_PEAK_BACKDROP_LUMINANCE,
                )
                // White card text keeps AAA contrast over every normalized platform backdrop.
                assertTrue(
                    ThemePack.contrastRatio(0xFFFFFFFF.toInt(), palette.peakCompositedColor) >= 10.0,
                )
                if (peakLum < minPeakLum) minPeakLum = peakLum
                if (peakLum > maxPeakLum) maxPeakLum = peakLum
            }
            val spread = maxPeakLum - minPeakLum
            assertTrue(
                "Backdrop luminance variance too wide ($spread) on theme ${pack.id}",
                spread <= 0.025,
            )
        }
    }
}
