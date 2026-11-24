package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.art.ArtTileMetrics
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Track B design-system regression matrix verifying WCAG AA
 * contrast across all 4 built-in themes (+ extreme custom themes), reduced-
 * motion compliance, and responsive geometry across One X Sugar (~540dp),
 * Odin 2 Portal 16:9, and portrait viewports.
 */
class TrackBDesignSystemAuditTest {

    private val allThemes: List<ThemeTokens> = ThemePack.BUILTINS + listOf(
        ThemeTokens(id = "custom_sharp", displayName = "Sharp", accentColor = 0xFF1A237E.toInt(), cardRadiusDp = 4),
        ThemeTokens(id = "custom_bright", displayName = "Bright", accentColor = 0xFFFFF176.toInt(), cardRadiusDp = 28),
    )

    private val platformSample = listOf(
        "nes", "snes", "n64", "gb", "gbc", "gba", "nds", "3ds",
        "psx", "psp", "genesis", "arcade", "switch", "saturn",
    )

    @Test
    fun `all built-in and custom themes pass WCAG AA contrast, concentric radii, and chip adaptation`() {
        for (tokens in allThemes) {
            val accent = tokens.accentColor

            // Concentric inner corner radius never exceeds outer card radius and stays non-negative.
            val inner = CarouselDepthMetrics.opticalInnerArtRadiusDp(tokens.cardRadiusDp, 8)
            assertTrue("theme=${tokens.id} inner=$inner out of range", inner in 0..tokens.cardRadiusDp)

            // Active browse chip focus ring contrasts sharply against accent fill.
            val focusOnActive = BrowseChipStyleLogic.focusStrokeColor(selected = true, accentColor = accent)
            val focusContrast = ThemePack.contrastRatio(focusOnActive, accent)
            assertTrue(
                "theme=${tokens.id} active focus contrast too low: $focusContrast",
                focusContrast >= ThemePack.MIN_TEXT_CONTRAST,
            )

            // Active chip ripple & letter count tint both flip to dark on light fills (e.g. OLED / Bright).
            val isLightFill = ThemePack.onFillTextColor(accent) == 0xFF000000.toInt()
            val expectedRipple = if (isLightFill) {
                BrowseChipStyleLogic.RIPPLE_ON_LIGHT
            } else {
                BrowseChipStyleLogic.RIPPLE_ON_DARK
            }
            assertEquals(expectedRipple, BrowseChipStyleLogic.rippleColor(selected = true, accentColor = accent))
            val countRgb = LetterJumpMetrics.countTextColor(selected = true, accentColor = accent) and 0x00FFFFFF
            assertEquals(if (isLightFill) 0x000000 else 0xFFFFFF, countRgb)

            // Details header platform dots meet WCAG AA on every theme's actual pill surface (chipIdle).
            val pillSurface = tokens.chipIdle
            for (pid in platformSample) {
                val spec = DetailsCardFormatter.headerSpec(
                    title = "Sample",
                    platformId = pid,
                    favorite = true,
                    year = "1996",
                    genre = "Action",
                    surfaceColor = pillSurface,
                )
                val dot = requireNotNull(spec.badgeDotColor)
                val ratio = ThemePack.contrastRatio(dot, pillSurface)
                assertTrue(
                    "theme=${tokens.id} platform=$pid dot contrast $ratio < AA on chipIdle",
                    ratio >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `reduced-motion disables async fades and backdrop crossfades consistently across Track B`() {
        assertEquals(
            0L,
            ArtTileMetrics.revealDurationMs(ArtTileMetrics.RevealMode.FADE, animationsEnabled = false),
        )
        assertTrue(
            ArtTileMetrics.revealDurationMs(ArtTileMetrics.RevealMode.FADE, animationsEnabled = true) > 0L,
        )
        assertFalse(
            DeckWallpaper.shouldFadeIn(wasCacheHit = false, isAttached = true, animationsEnabled = false),
        )
        assertFalse(
            BackdropTransitionLogic.shouldCrossfadeBackdrop(
                previousKey = BackdropTransitionLogic.DEFAULT_BACKDROP_KEY,
                nextKey = "snes",
                attachedToWindow = true,
                animationsEnabled = false,
            ),
        )
    }

    @Test
    fun `responsive viewport matrix keeps Sugar, Odin 2 Portal, and portrait layouts bounded and centered`() {
        data class Viewport(val name: String, val widthDp: Int, val heightDp: Int, val density: Float)
        val viewports = listOf(
            Viewport("Sugar compact", 540, 480, 2.5f),
            Viewport("Sugar short", 480, 410, 2.25f),
            Viewport("Odin 2 Portal 16:9", 860, 480, 2.25f),
            Viewport("Widescreen 1080p", 960, 540, 2.0f),
            Viewport("Portrait narrow", 360, 780, 3.0f),
        )
        for (vp in viewports) {
            val effectiveCard = CarouselLayoutMetrics.effectiveCardSizeDp(200, vp.widthDp, vp.heightDp)
            assertTrue(
                "${vp.name}: effective card $effectiveCard out of bounds",
                effectiveCard in CarouselLayoutMetrics.MIN_CARD_SIZE_DP..200,
            )
            val cardWidthDp = effectiveCard + 16
            val edgePad = CarouselLayoutMetrics.horizontalEdgePaddingDp(vp.widthDp, cardWidthDp)
            assertTrue("${vp.name}: edgePad=$edgePad", edgePad >= CarouselLayoutMetrics.MIN_EDGE_PAD_DP)

            val trackDp = GameDeckChromeLogic.railTrackWidthDp(vp.widthDp)
            assertTrue("${vp.name}: railTrack=$trackDp", trackDp in 72..148)

            // Artwork radius & logo inset scale cleanly on every viewport density.
            val targetPx = (effectiveCard * vp.density).toInt()
            val radiusPx = ArtTileMetrics.clampedCornerRadiusPx(16, vp.density, targetPx)
            assertTrue(radiusPx in 1f..(targetPx * 0.5f))
            val logoFraming = ArtTileMetrics.framingFor(
                kind = ArtTileMetrics.ArtFrameKind.LOGO_OVERLAY,
                bitmapWidth = 800,
                bitmapHeight = 300,
                targetPx = targetPx,
                density = vp.density,
            )
            assertFalse(logoFraming.centerCrop)
            assertTrue(logoFraming.insetPaddingPx > 0)

            // Wallpaper subsampling respects GPU max texture limit for 8K input on every display.
            val sample = DeckWallpaper.computeSampleSize(
                outWidth = 7680,
                outHeight = 4320,
                targetWidth = (vp.widthDp * vp.density).toInt(),
                targetHeight = (vp.heightDp * vp.density).toInt(),
            )
            assertTrue("${vp.name}: 8K sample=$sample still exceeds max tex", 7680 / sample <= DeckWallpaper.MAX_TEXTURE_DIM)
            assertNotNull(CarouselCardBadges.overlaySpec("snes", null, true, false, false).platformDotColor)
        }
    }
}
