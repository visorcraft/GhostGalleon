package com.visorcraft.ghostgalleon.ui.settings

import android.view.KeyEvent
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.audio.SoundPackId
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.BrowseChrome
import com.visorcraft.ghostgalleon.settings.SettingsCatalog
import com.visorcraft.ghostgalleon.settings.SettingsJump
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiLogicTest {

    @Test
    fun `segmented layout stacks when narrow or three or more options`() {
        // 2-option rows stay inline in wide mode, stack on narrow panels.
        assertFalse(SettingsUiLogic.shouldStackSegmented(optionCount = 2, isWide = true))
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 2, isWide = false))

        // 3+ option rows always stack so localized labels never crowd or truncate.
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 3, isWide = true))
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 4, isWide = true))
        assertTrue(SettingsUiLogic.shouldStackSegmented(optionCount = 4, isWide = false))
    }

    @Test
    fun `settings filled pills and accent labels meet WCAG AA contrast across all themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val accent = tokens.accentColor

            val onAccent = SettingsUiLogic.accessibleOnAccent(accent)
            val fillRatio = ThemePack.contrastRatio(onAccent, accent)
            assertTrue(
                "Low on-accent contrast $fillRatio on theme=${tokens.id}",
                fillRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val onCard = SettingsUiLogic.accentOnSurface(accent, tokens)
            val cardRatio = ThemePack.contrastRatio(onCard, ThemePack.cardFill(tokens))
            assertTrue(
                "Low accent-on-card contrast $cardRatio on theme=${tokens.id}",
                cardRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val onSunken = SettingsUiLogic.accentOnSunken(accent, tokens)
            val sunkenRatio = ThemePack.contrastRatio(onSunken, ThemePack.surfaceSunken(tokens))
            assertTrue(
                "Low accent-on-sunken contrast $sunkenRatio on theme=${tokens.id}",
                sunkenRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val ring = SettingsUiLogic.activePillFocusRing(accent)
            val ringVsFill = ThemePack.contrastRatio(ring, accent)
            val ringVsCard = ThemePack.contrastRatio(ring, ThemePack.cardFill(tokens))
            assertTrue(
                "Active pill focus ring blends with accent ($ringVsFill) on theme=${tokens.id}",
                ringVsFill >= 2.5,
            )
            assertTrue(
                "Active pill focus ring blends with card ($ringVsCard) on theme=${tokens.id}",
                ringVsCard >= 3.0,
            )
        }
    }

    @Test
    fun `second seat layout visibility mirrors toggle state`() {
        assertFalse(SettingsUiLogic.isSecondSeatLayoutVisible(false))
        assertTrue(SettingsUiLogic.isSecondSeatLayoutVisible(true))
    }

    @Test
    fun `stepPageIndex wraps forward and backward cleanly across settings tabs`() {
        org.junit.Assert.assertEquals(1, SettingsUiLogic.stepPageIndex(0, 1, 8))
        org.junit.Assert.assertEquals(7, SettingsUiLogic.stepPageIndex(0, -1, 8))
        org.junit.Assert.assertEquals(0, SettingsUiLogic.stepPageIndex(7, 1, 8))
        org.junit.Assert.assertEquals(6, SettingsUiLogic.stepPageIndex(7, -1, 8))
        org.junit.Assert.assertEquals(0, SettingsUiLogic.stepPageIndex(0, 1, 1))
        org.junit.Assert.assertEquals(0, SettingsUiLogic.stepPageIndex(5, -1, 0))
    }

    @Test
    fun `navActiveSurface maintains high contrast for active text and accent icon across all themes`() {
        val white = 0xFFFFFFFF.toInt()
        for (tokens in ThemePack.BUILTINS) {
            val activeBg = SettingsUiLogic.navActiveSurface(tokens, tokens.accentColor)
            val textContrast = ThemePack.contrastRatio(white, activeBg)
            assertTrue(
                "Active nav white text contrast=$textContrast too low on theme=${tokens.id}",
                textContrast >= 8.0,
            )
            val iconColor = ThemePack.accentTextColor(tokens.accentColor, activeBg)
            val iconContrast = ThemePack.contrastRatio(iconColor, activeBg)
            assertTrue(
                "Active nav icon contrast=$iconContrast too low on theme=${tokens.id}",
                iconContrast >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `friendlyKeycap formats standard gamepad keys and returns null for unknown keycodes`() {
        assertEquals("A", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("L1", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals("L3", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertEquals("START", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals("\u25B2", SettingsUiLogic.friendlyKeycap(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(SettingsUiLogic.friendlyKeycap(999))

        val map = mapOf(KeyEvent.KEYCODE_BUTTON_A to Action.CONFIRM)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, SettingsUiLogic.boundKeyCodeFor(Action.CONFIRM, map))
        assertNull(SettingsUiLogic.boundKeyCodeFor(Action.SWAP_SCREENS, map))
    }

    @Test
    fun `scrapeProgressFraction clamps zero, partial, complete, and overflow totals`() {
        assertEquals(0f, SettingsUiLogic.scrapeProgressFraction(0, 0), 0.0001f)
        assertEquals(0f, SettingsUiLogic.scrapeProgressFraction(5, -2), 0.0001f)
        assertEquals(0f, SettingsUiLogic.scrapeProgressFraction(-3, 10), 0.0001f)
        assertEquals(0.25f, SettingsUiLogic.scrapeProgressFraction(5, 20), 0.0001f)
        assertEquals(1f, SettingsUiLogic.scrapeProgressFraction(20, 20), 0.0001f)
        assertEquals(1f, SettingsUiLogic.scrapeProgressFraction(25, 20), 0.0001f)
    }

    @Test
    fun `credential readiness helpers detect SGDB and RetroAchievements configuration`() {
        assertFalse(SettingsUiLogic.isConfiguredStatus(null))
        assertFalse(SettingsUiLogic.isConfiguredStatus("   "))
        assertTrue(SettingsUiLogic.isConfiguredStatus("abc"))

        assertFalse(SettingsUiLogic.hasRaCredentials("user", null))
        assertFalse(SettingsUiLogic.hasRaCredentials(null, "key"))
        assertTrue(SettingsUiLogic.hasRaCredentials("user", "key"))

        assertFalse(SettingsUiLogic.hasAnyArtCredentials(null, null, null))
        assertFalse(SettingsUiLogic.hasAnyArtCredentials("", "user", ""))
        assertTrue(SettingsUiLogic.hasAnyArtCredentials("sgdb-token", null, null))
        assertTrue(SettingsUiLogic.hasAnyArtCredentials(null, "user", "ra-key"))
    }

    @Test
    fun `enabledPowerRailsCount and chevronRotationDegrees reflect BrowseChrome presets`() {
        assertEquals(0, SettingsUiLogic.enabledPowerRailsCount(BrowseChrome.MINIMAL))
        assertEquals(
            SettingsUiLogic.TOTAL_POWER_RAILS,
            SettingsUiLogic.enabledPowerRailsCount(BrowseChrome.FULL),
        )
        val custom = BrowseChrome.MINIMAL.copy(deckStatusPill = true, resumeChip = true)
        assertEquals(2, SettingsUiLogic.enabledPowerRailsCount(custom))
        // Core platformChips / collectionRails toggles do not alter the power-rail count.
        val coreOff = BrowseChrome.MINIMAL.copy(platformChips = false, collectionRails = false)
        assertEquals(0, SettingsUiLogic.enabledPowerRailsCount(coreOff))

        assertEquals(0f, SettingsUiLogic.chevronRotationDegrees(false), 0.001f)
        assertEquals(180f, SettingsUiLogic.chevronRotationDegrees(true), 0.001f)
    }

    @Test
    fun `modalChipTextColor meets WCAG AA contrast across all tones and built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val accent = tokens.accentColor

            val accentFg = SettingsUiLogic.modalChipTextColor(
                SettingsUiLogic.ModalChipTone.ACCENT, accent, tokens,
            )
            assertTrue(
                "Modal ACCENT chip contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(accentFg, accent) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val neutralFg = SettingsUiLogic.modalChipTextColor(
                SettingsUiLogic.ModalChipTone.NEUTRAL, accent, tokens,
            )
            assertTrue(
                "Modal NEUTRAL chip contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(neutralFg, tokens.chipIdle) >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val destructiveFg = SettingsUiLogic.modalChipTextColor(
                SettingsUiLogic.ModalChipTone.DESTRUCTIVE, accent, tokens,
            )
            assertTrue(
                "Modal DESTRUCTIVE chip contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(destructiveFg, ThemePack.surfaceSunken(tokens)) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `playtimeBarFraction and totalPlaytimeMs normalize stats rows cleanly`() {
        assertEquals(0f, SettingsUiLogic.playtimeBarFraction(0L, 1000L), 0.0001f)
        assertEquals(0f, SettingsUiLogic.playtimeBarFraction(500L, 0L), 0.0001f)
        assertEquals(0.04f, SettingsUiLogic.playtimeBarFraction(1L, 100_000L), 0.0001f)
        assertEquals(0.5f, SettingsUiLogic.playtimeBarFraction(500L, 1000L), 0.0001f)
        assertEquals(1f, SettingsUiLogic.playtimeBarFraction(1000L, 1000L), 0.0001f)
        assertEquals(1f, SettingsUiLogic.playtimeBarFraction(2000L, 1000L), 0.0001f)

        assertEquals(0L, SettingsUiLogic.totalPlaytimeMs(emptyMap()))
        assertEquals(
            90_000L,
            SettingsUiLogic.totalPlaytimeMs(mapOf("a" to 60_000L, "b" to 30_000L, "bad" to -50L)),
        )
    }

    @Test
    fun `system resource meter fractions and semantic tones clamp and meet contrast`() {
        assertEquals(0f, SettingsUiLogic.freeToUsedFraction(100L, 0L), 0.0001f)
        assertEquals(0.25f, SettingsUiLogic.freeToUsedFraction(750L, 1000L), 0.0001f)
        assertEquals(1f, SettingsUiLogic.freeToUsedFraction(-10L, 1000L), 0.0001f)
        assertEquals(0f, SettingsUiLogic.freeToUsedFraction(2000L, 1000L), 0.0001f)

        assertEquals(0f, SettingsUiLogic.batteryFraction(null), 0.0001f)
        assertEquals(0.55f, SettingsUiLogic.batteryFraction(55), 0.0001f)
        assertEquals(1f, SettingsUiLogic.batteryFraction(150), 0.0001f)

        val accent = ThemePack.GHOST.accentColor
        assertEquals(accent, SettingsUiLogic.meterToneColor(0.5f, accent, lowIsWarn = false))
        assertEquals(SettingsUiLogic.COLOR_WARNING_AMBER, SettingsUiLogic.meterToneColor(0.85f, accent, lowIsWarn = false))
        assertEquals(com.visorcraft.ghostgalleon.ui.UiDimens.COLOR_DESTRUCTIVE, SettingsUiLogic.meterToneColor(0.95f, accent, lowIsWarn = false))

        assertEquals(accent, SettingsUiLogic.meterToneColor(0.6f, accent, lowIsWarn = true))
        assertEquals(SettingsUiLogic.COLOR_WARNING_AMBER, SettingsUiLogic.meterToneColor(0.20f, accent, lowIsWarn = true))
        assertEquals(com.visorcraft.ghostgalleon.ui.UiDimens.COLOR_DESTRUCTIVE, SettingsUiLogic.meterToneColor(0.10f, accent, lowIsWarn = true))

        for (tokens in ThemePack.BUILTINS) {
            val sunken = ThemePack.surfaceSunken(tokens)
            assertTrue(
                "Warning amber contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(SettingsUiLogic.COLOR_WARNING_AMBER, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `empty folders prompt and custom deadzone highlight respond to state`() {
        assertTrue(SettingsUiLogic.shouldShowEmptyFoldersPrompt(0))
        assertFalse(SettingsUiLogic.shouldShowEmptyFoldersPrompt(1))
        assertFalse(SettingsUiLogic.shouldShowEmptyFoldersPrompt(4))

        assertFalse(SettingsUiLogic.isCustomDeadzone(50))
        assertTrue(SettingsUiLogic.isCustomDeadzone(35))
        assertTrue(SettingsUiLogic.isCustomDeadzone(75))
    }

    @Test
    fun `header breadcrumb accent meets WCAG AA on black and shoulder cue gates on wide screens`() {
        assertTrue(SettingsUiLogic.shouldShowShoulderCue(isWide = true))
        assertFalse(SettingsUiLogic.shouldShowShoulderCue(isWide = false))
        assertTrue(SettingsUiLogic.SHOULDER_PAGE_HINT.contains("L1"))
        assertTrue(SettingsUiLogic.SHOULDER_PAGE_HINT.contains("R1"))

        for (tokens in ThemePack.BUILTINS) {
            val fg = SettingsUiLogic.accentOnBlack(tokens.accentColor)
            val ratio = ThemePack.contrastRatio(fg, 0xFF000000.toInt())
            assertTrue(
                "Header breadcrumb contrast=$ratio too low on ${tokens.id}",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `responsive shell padding and nav blade width tighten for compact 540dp panels`() {
        val narrow = SettingsUiLogic.shellPaddingDp(isWide = false, screenHeightDp = 540)
        val compactWide = SettingsUiLogic.shellPaddingDp(isWide = true, screenHeightDp = 540)
        val spaciousWide = SettingsUiLogic.shellPaddingDp(isWide = true, screenHeightDp = 720)
        assertTrue(narrow.horizontal < compactWide.horizontal)
        assertTrue(compactWide.top < spaciousWide.top)
        assertEquals(200, SettingsUiLogic.navBladeWidthDp(720))
        assertEquals(224, SettingsUiLogic.navBladeWidthDp(960))
    }

    @Test
    fun `UiDimens isMotionEnabled disables transitions when animator duration scale is zero or invalid`() {
        assertFalse(com.visorcraft.ghostgalleon.ui.UiDimens.isMotionEnabled(0f))
        assertFalse(com.visorcraft.ghostgalleon.ui.UiDimens.isMotionEnabled(-1f))
        assertFalse(com.visorcraft.ghostgalleon.ui.UiDimens.isMotionEnabled(Float.NaN))
        assertTrue(com.visorcraft.ghostgalleon.ui.UiDimens.isMotionEnabled(0.5f))
        assertTrue(com.visorcraft.ghostgalleon.ui.UiDimens.isMotionEnabled(1f))
    }

    @Test
    fun `audio page test pad cues, responsive columns, and active status badges behave cleanly`() {
        assertEquals(
            listOf(
                SoundCue.NAV_TICK,
                SoundCue.CONFIRM,
                SoundCue.LAUNCH_GAME,
                SoundCue.SWAP_DISPLAY,
                SoundCue.FAVORITE_ON,
                SoundCue.RA_ACHIEVEMENT,
            ),
            SettingsUiLogic.AUDIO_TEST_PAD_CUES,
        )
        assertEquals(3, SettingsUiLogic.audioTestPadColumns(isWide = true))
        assertEquals(2, SettingsUiLogic.audioTestPadColumns(isWide = false))

        assertTrue(SettingsUiLogic.isSfxAudioActive(true, SoundPackId.AUTO.id, 75))
        assertTrue(SettingsUiLogic.isSfxAudioActive(true, SoundPackId.NEON.id, 1))
        assertFalse(SettingsUiLogic.isSfxAudioActive(false, SoundPackId.GHOST.id, 75))
        assertFalse(SettingsUiLogic.isSfxAudioActive(true, SoundPackId.OFF.id, 75))
        assertFalse(SettingsUiLogic.isSfxAudioActive(true, SoundPackId.GHOST.id, 0))

        assertTrue(SettingsUiLogic.isAmbientAudioActive(true, true, SoundPackId.AUTO.id, 35))
        assertFalse(SettingsUiLogic.isAmbientAudioActive(false, true, SoundPackId.AUTO.id, 35))
        assertFalse(SettingsUiLogic.isAmbientAudioActive(true, false, SoundPackId.AUTO.id, 35))
        assertFalse(SettingsUiLogic.isAmbientAudioActive(true, true, SoundPackId.OFF.id, 35))
        assertFalse(SettingsUiLogic.isAmbientAudioActive(true, true, SoundPackId.GHOST.id, 0))

        // 9-page Settings navigation wraps smoothly with AUDIO inserted
        assertEquals(8, SettingsUiLogic.stepPageIndex(0, -1, 9))
        assertEquals(0, SettingsUiLogic.stepPageIndex(8, 1, 9))
    }

    @Test
    fun `SettingsPageCache builds each page lazily once until invalidate bumps generation`() {
        val cache = SettingsPageCache<String, Any>()
        var buildCalls = 0
        val builtOrder = mutableListOf<String>()
        fun build(page: String): Any {
            buildCalls++
            builtOrder.add(page)
            return Any()
        }

        assertEquals(0, cache.generation)
        assertEquals(0, cache.builtCount)
        assertFalse(cache.isBuilt("DISPLAY_GRID"))
        assertNull(cache.peek("SYSTEM"))

        // Initial open builds ONLY the active page.
        val firstDisplay = cache.getOrBuild("DISPLAY_GRID", ::build)
        assertEquals(1, buildCalls)
        assertEquals(1, cache.builtCount)
        assertTrue(cache.isBuilt("DISPLAY_GRID"))
        assertFalse(cache.isBuilt("SYSTEM"))
        assertFalse(cache.isBuilt("ABOUT"))

        // Revisiting an already-built page returns the exact cached instance with zero rebuilds.
        assertSame(firstDisplay, cache.getOrBuild("DISPLAY_GRID", ::build))
        assertSame(firstDisplay, cache.peek("DISPLAY_GRID"))
        assertEquals(1, buildCalls)

        // Switching to a second page lazily inflates only that page.
        val firstStats = cache.getOrBuild("STATS", ::build)
        assertEquals(2, buildCalls)
        assertEquals(2, cache.builtCount)
        assertEquals(listOf("DISPLAY_GRID", "STATS"), builtOrder)
        assertSame(firstStats, cache.getOrBuild("STATS", ::build))
        assertEquals(2, buildCalls)

        // refreshSettingsUi() invalidates all cached pages and increments generation for async guards.
        val gen1 = cache.invalidate()
        assertEquals(1, gen1)
        assertEquals(1, cache.generation)
        assertEquals(0, cache.builtCount)
        assertFalse(cache.isBuilt("DISPLAY_GRID"))
        assertNull(cache.peek("DISPLAY_GRID"))

        val rebuiltDisplay = cache.getOrBuild("DISPLAY_GRID", ::build)
        assertNotSame(firstDisplay, rebuiltDisplay)
        assertEquals(3, buildCalls)
        assertEquals(1, cache.builtCount)

        val gen2 = cache.invalidate()
        assertEquals(2, gen2)
        assertEquals(2, cache.generation)
    }

    @Test
    fun `resolvePalette snapshots every WCAG-checked color consistently across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val accent = tokens.accentColor
            val palette = SettingsUiLogic.resolvePalette(tokens, accent)
            assertEquals(accent, palette.accent)
            assertEquals(tokens, palette.tokens)
            assertEquals(SettingsUiLogic.accentOnSurface(accent, tokens), palette.accentOnCard)
            assertEquals(SettingsUiLogic.accentOnSunken(accent, tokens), palette.accentOnSunken)
            assertEquals(SettingsUiLogic.accentOnBlack(accent), palette.accentOnBlack)
            assertEquals(SettingsUiLogic.accessibleOnAccent(accent), palette.onAccentFill)
            assertEquals(SettingsUiLogic.activePillFocusRing(accent), palette.activeFocusRing)
            val expectedNavBg = SettingsUiLogic.navActiveSurface(tokens, accent)
            assertEquals(expectedNavBg, palette.navActiveBg)
            assertEquals(ThemePack.accentTextColor(accent, expectedNavBg), palette.navActiveIconTint)
            assertEquals(ThemePack.accentHairline(accent), palette.navActiveBorder)
            assertEquals(ThemePack.hairlineDivider(tokens), palette.hairline)
            assertTrue(
                "Palette focusStroke contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(palette.focusStroke, ThemePack.cardFill(tokens)) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
        }
    }

    @Test
    fun `navScrollTargetY keeps active nav item visible and pageTransitionAlpha gates reduced motion`() {
        // Already visible: leaves scrollY unchanged.
        assertEquals(0, SettingsUiLogic.navScrollTargetY(10, 60, scrollY = 0, viewportH = 200))
        assertEquals(40, SettingsUiLogic.navScrollTargetY(60, 120, scrollY = 40, viewportH = 200))
        // Below viewport: scrolls down just enough to reveal bottom edge.
        assertEquals(70, SettingsUiLogic.navScrollTargetY(220, 270, scrollY = 0, viewportH = 200))
        assertEquals(78, SettingsUiLogic.navScrollTargetY(220, 270, scrollY = 0, viewportH = 200, marginPx = 8))
        // Above viewport: scrolls up to top edge.
        assertEquals(30, SettingsUiLogic.navScrollTargetY(30, 80, scrollY = 100, viewportH = 200))
        // Item taller than viewport: aligns top edge.
        assertEquals(50, SettingsUiLogic.navScrollTargetY(50, 320, scrollY = 0, viewportH = 200))
        // Unmeasured viewport: clamps scrollY safely.
        assertEquals(20, SettingsUiLogic.navScrollTargetY(10, 50, scrollY = 20, viewportH = 0))
        assertEquals(0, SettingsUiLogic.navScrollTargetY(10, 50, scrollY = -10, viewportH = 0))

        // Page crossfade micro-fade only runs when animations enabled AND user-initiated.
        val animatedStart = SettingsUiLogic.pageTransitionAlpha(animatorsEnabled = true, userInitiated = true)
        assertTrue(animatedStart in 0f..0.99f)
        assertEquals(1f, SettingsUiLogic.pageTransitionAlpha(animatorsEnabled = false, userInitiated = true), 0.0001f)
        assertEquals(1f, SettingsUiLogic.pageTransitionAlpha(animatorsEnabled = true, userInitiated = false), 0.0001f)
        assertEquals(1f, SettingsUiLogic.pageTransitionAlpha(animatorsEnabled = false, userInitiated = false), 0.0001f)
    }

    @Test
    fun `rankSettingsMatches returns full directory on blank query and ranks prefix and keywords`() {
        val jumps = listOf(
            SettingsJump(SettingsCatalog.PAGE_DISPLAY, "Display & Grid", "theme display grid wallpaper"),
            SettingsJump(SettingsCatalog.PAGE_APPS, "Apps", "apps hidden dock helper"),
            SettingsJump(SettingsCatalog.PAGE_CONTROLS, "Controls", "controls remap deadzone haptics lab"),
            SettingsJump(SettingsCatalog.PAGE_AUDIO, "Audio", "audio sound sfx volume ambient"),
            SettingsJump(SettingsCatalog.PAGE_LIBRARY, "Library", "library rom folder rescan collections"),
        )

        // Empty query shows complete directory with null matched keyword snippets.
        val all = SettingsUiLogic.rankSettingsMatches("   ", jumps)
        assertEquals(jumps.size, all.size)
        assertEquals(jumps.map { it.pageId }, all.map { it.jump.pageId })
        assertTrue(all.all { it.matchedKeyword == null })

        // Label prefix beats keyword match.
        val audioHits = SettingsUiLogic.rankSettingsMatches("au", jumps)
        assertEquals(SettingsCatalog.PAGE_AUDIO, audioHits.first().jump.pageId)

        // Keyword prefix extracts matched term snippet.
        val deadzoneHits = SettingsUiLogic.rankSettingsMatches("dead", jumps)
        assertEquals(1, deadzoneHits.size)
        assertEquals(SettingsCatalog.PAGE_CONTROLS, deadzoneHits[0].jump.pageId)
        assertEquals("deadzone", deadzoneHits[0].matchedKeyword)

        // Keyword contains extracts full token containing query.
        val zoneHits = SettingsUiLogic.rankSettingsMatches("zone", jumps)
        assertEquals(1, zoneHits.size)
        assertEquals("deadzone", zoneHits[0].matchedKeyword)

        // Label-contains outranks keyword-only match.
        val gridHits = SettingsUiLogic.rankSettingsMatches("grid", jumps)
        assertEquals(SettingsCatalog.PAGE_DISPLAY, gridHits.first().jump.pageId)

        // Unknown query returns empty list.
        assertTrue(SettingsUiLogic.rankSettingsMatches("zzzznotfound", jumps).isEmpty())
    }

    @Test
    fun `dropdown shoulder cue, minimum touch comfort, and focus-after-remove behave cleanly`() {
        assertTrue(SettingsUiLogic.shouldShowDropdownShoulderCue(isWide = false))
        assertFalse(SettingsUiLogic.shouldShowDropdownShoulderCue(isWide = true))
        assertTrue(SettingsUiLogic.MIN_CONTROL_TOUCH_DP >= 44)
        assertTrue(SettingsUiLogic.SEEKBAR_MIN_HEIGHT_DP >= 44)
        assertTrue(
            SettingsUiLogic.SEGMENTED_PILL_HEIGHT_DP + SettingsUiLogic.SEGMENTED_TRACK_PADDING_DP * 2 >= 44,
        )

        // Clamped next focus index after removal in management dialogs.
        assertEquals(0, SettingsUiLogic.nextFocusIndexAfterRemove(removedIndex = 0, remainingCount = 3))
        assertEquals(1, SettingsUiLogic.nextFocusIndexAfterRemove(removedIndex = 1, remainingCount = 2))
        assertEquals(1, SettingsUiLogic.nextFocusIndexAfterRemove(removedIndex = 2, remainingCount = 2))
        assertEquals(0, SettingsUiLogic.nextFocusIndexAfterRemove(removedIndex = -1, remainingCount = 1))
        assertEquals(-1, SettingsUiLogic.nextFocusIndexAfterRemove(removedIndex = 0, remainingCount = 0))
        assertEquals(-1, SettingsUiLogic.nextFocusIndexAfterRemove(removedIndex = 2, remainingCount = -1))
    }

    @Test
    fun `controlGroupFor classifies all 20 remappable actions and audioSubControlAlpha dims when bypassed`() {
        val remappable = listOf(
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.CONFIRM, Action.BACK, Action.SWAP_SCREENS,
            Action.TOGGLE_MODE, Action.OPEN_SETTINGS, Action.PAGE_PREV, Action.PAGE_NEXT,
            Action.OPEN_QUICK_PANEL,
            Action.SEARCH_LIBRARY, Action.TOGGLE_FAVORITE, Action.SHOW_DETAILS,
            Action.OPEN_SESSION_SWITCHER, Action.TOGGLE_PLAY_HUD,
            Action.CLAIM_HOST, Action.RELEASE_HOST, Action.TOGGLE_SEAT,
        )
        val grouped = remappable.groupBy(SettingsUiLogic::controlGroupFor)
        assertEquals(6, grouped[SettingsUiLogic.ControlActionGroup.NAVIGATION]?.size)
        assertEquals(10, grouped[SettingsUiLogic.ControlActionGroup.DECK_SHORTCUTS]?.size)
        assertEquals(4, grouped[SettingsUiLogic.ControlActionGroup.DUAL_SCREEN_HOST]?.size)
        assertEquals(
            SettingsUiLogic.ControlActionGroup.NAVIGATION,
            SettingsUiLogic.controlGroupFor(Action.CONFIRM),
        )
        assertEquals(
            SettingsUiLogic.ControlActionGroup.DUAL_SCREEN_HOST,
            SettingsUiLogic.controlGroupFor(Action.SWAP_SCREENS),
        )
        assertEquals(
            SettingsUiLogic.ControlActionGroup.DECK_SHORTCUTS,
            SettingsUiLogic.controlGroupFor(Action.OPEN_QUICK_PANEL),
        )

        assertEquals(1f, SettingsUiLogic.audioSubControlAlpha(masterOn = true, sectionOn = true), 0.0001f)
        assertEquals(0.50f, SettingsUiLogic.audioSubControlAlpha(masterOn = false, sectionOn = true), 0.0001f)
        assertEquals(0.50f, SettingsUiLogic.audioSubControlAlpha(masterOn = true, sectionOn = false), 0.0001f)
        assertEquals(0.50f, SettingsUiLogic.audioSubControlAlpha(masterOn = false, sectionOn = false), 0.0001f)
    }
}
