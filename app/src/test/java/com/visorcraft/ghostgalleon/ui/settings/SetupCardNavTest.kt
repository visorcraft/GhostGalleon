package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.library.SetupNeeds
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCardNavTest {

    @Test
    fun `vertical nav wraps and page jumps clamp across setup buttons`() {
        // 6 buttons (full setup card)
        assertEquals(
            SetupCardNav.StepResult(5, SetupCardNav.Outcome.MOVED),
            SetupCardNav.step(0, count = 6, action = Action.NAV_UP),
        )
        assertEquals(
            SetupCardNav.StepResult(0, SetupCardNav.Outcome.MOVED),
            SetupCardNav.step(5, count = 6, action = Action.NAV_DOWN),
        )
        assertEquals(
            SetupCardNav.StepResult(3, SetupCardNav.Outcome.MOVED),
            SetupCardNav.step(0, count = 6, action = Action.PAGE_NEXT),
        )
        assertEquals(
            SetupCardNav.StepResult(5, SetupCardNav.Outcome.MOVED),
            SetupCardNav.step(4, count = 6, action = Action.PAGE_NEXT),
        )
        assertEquals(
            SetupCardNav.StepResult(0, SetupCardNav.Outcome.MOVED),
            SetupCardNav.step(2, count = 6, action = Action.PAGE_PREV),
        )
    }

    @Test
    fun `confirm and back emit expected outcomes and handle empty lists safely`() {
        assertEquals(
            SetupCardNav.StepResult(2, SetupCardNav.Outcome.CONFIRM),
            SetupCardNav.step(2, count = 6, action = Action.CONFIRM),
        )
        assertEquals(
            SetupCardNav.StepResult(1, SetupCardNav.Outcome.DISMISS),
            SetupCardNav.step(1, count = 2, action = Action.BACK),
        )
        assertEquals(
            SetupCardNav.StepResult(0, SetupCardNav.Outcome.DISMISS),
            SetupCardNav.step(0, count = 0, action = Action.BACK),
        )
        assertEquals(
            SetupCardNav.StepResult(0, SetupCardNav.Outcome.NONE),
            SetupCardNav.step(0, count = 0, action = Action.CONFIRM),
        )
        assertEquals(
            SetupCardNav.StepResult(0, SetupCardNav.Outcome.NONE),
            SetupCardNav.step(0, count = 0, action = Action.NAV_DOWN),
        )
        assertEquals(
            SetupCardNav.StepResult(1, SetupCardNav.Outcome.NONE),
            SetupCardNav.step(99, count = 2, action = Action.SWAP_SCREENS),
        )
    }

    @Test
    fun `primary and secondary button states meet WCAG AA contrast across all themes`() {
        for (tokens in ThemePack.BUILTINS) {
            for (role in SetupCardNav.ButtonRole.entries) {
                for (selected in listOf(false, true)) {
                    val (bg, fg) = SetupCardNav.buttonColors(
                        role = role,
                        selected = selected,
                        accent = tokens.accentColor,
                        tokens = tokens,
                    )
                    val ratio = ThemePack.contrastRatio(fg, bg)
                    assertTrue(
                        "Low contrast $ratio for theme=${tokens.id} role=$role selected=$selected " +
                            "fg=${Integer.toHexString(fg)} bg=${Integer.toHexString(bg)}",
                        ratio >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
            }
        }
    }

    @Test
    fun `progress computes accurate done counts, fractions, and percentages across snapshots`() {
        val empty = SetupCardNav.progress(emptyList())
        assertEquals(0, empty.done)
        assertEquals(0, empty.total)
        assertEquals(0f, empty.fraction, 0.0001f)
        assertEquals(0, empty.percent)
        assertFalse(empty.allDone)

        val partialSnap = SetupNeeds.Snapshot(
            setupDismissed = false,
            romTreeCount = 0,
            romEntryCount = 0,
            installedPlayerCount = 2,
            hasSgdbKey = true,
            resumeChip = false,
            statusPill = false,
        )
        val partial = SetupCardNav.progress(SetupNeeds.checklist(partialSnap))
        assertEquals(2, partial.done)
        assertEquals(5, partial.total)
        assertEquals(0.4f, partial.fraction, 0.0001f)
        assertEquals(40, partial.percent)
        assertFalse(partial.allDone)

        val chromeSnap = SetupNeeds.Snapshot(
            setupDismissed = false,
            romTreeCount = 1,
            romEntryCount = 12,
            installedPlayerCount = 2,
            hasSgdbKey = false,
            resumeChip = true,
            statusPill = false,
        )
        val chromeProgress = SetupCardNav.progress(SetupNeeds.checklist(chromeSnap))
        assertEquals(1, chromeProgress.done)
        assertEquals(2, chromeProgress.total)
        assertEquals(50, chromeProgress.percent)
        assertFalse(chromeProgress.allDone)

        val allDone = SetupCardNav.progress(listOf("a" to true, "b" to true))
        assertTrue(allDone.allDone)
        assertEquals(100, allDone.percent)
        assertEquals(1f to 0f, SetupCardNav.progressTrackWeights(allDone.fraction))
        assertEquals(0f to 1f, SetupCardNav.progressTrackWeights(-0.5f))
    }

    @Test
    fun `checkRowTone distinguishes required vs optional items and meets WCAG AA across themes`() {
        assertEquals(
            SetupCardNav.CheckRowTone.REQUIRED_PENDING,
            SetupCardNav.checkRowTone(index = 0, done = false, chromeOnly = false),
        )
        assertEquals(
            SetupCardNav.CheckRowTone.OPTIONAL_PENDING,
            SetupCardNav.checkRowTone(index = 2, done = false, chromeOnly = false),
        )
        assertEquals(
            SetupCardNav.CheckRowTone.REQUIRED_PENDING,
            SetupCardNav.checkRowTone(index = 1, done = false, chromeOnly = true),
        )
        assertEquals(
            SetupCardNav.CheckRowTone.DONE,
            SetupCardNav.checkRowTone(index = 0, done = true, chromeOnly = false),
        )

        for (tokens in ThemePack.BUILTINS) {
            val sunken = ThemePack.surfaceSunken(tokens)
            for (tone in SetupCardNav.CheckRowTone.entries) {
                val fg = SetupCardNav.checkRowTextColor(tone, tokens.accentColor, tokens)
                val effectiveFg = blendOverOpaque(fg, sunken)
                val ratio = ThemePack.contrastRatio(effectiveFg, sunken)
                assertTrue(
                    "Low checklist contrast $ratio for theme=${tokens.id} tone=$tone",
                    ratio >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }

    private fun blendOverOpaque(fg: Int, bg: Int): Int {
        val a = ((fg ushr 24) and 0xFF) / 255.0
        val fr = (fg shr 16) and 0xFF
        val fgCh = (fg shr 8) and 0xFF
        val fb = fg and 0xFF
        val br = (bg shr 16) and 0xFF
        val bgCh = (bg shr 8) and 0xFF
        val bb = bg and 0xFF
        val r = (fr * a + br * (1.0 - a)).toInt().coerceIn(0, 255)
        val g = (fgCh * a + bgCh * (1.0 - a)).toInt().coerceIn(0, 255)
        val b = (fb * a + bb * (1.0 - a)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `dirtyButtonIndices paints all on initial bind and strictly at most two buttons per nav step`() {
        assertEquals(emptyList<Int>(), SetupCardNav.dirtyButtonIndices(0, 1, count = 0))
        // Initial paint (prev = -1) covers every button once.
        assertEquals(listOf(0, 1, 2, 3, 4, 5), SetupCardNav.dirtyButtonIndices(-1, 0, count = 6))
        // No-op transition touches 0 buttons.
        assertEquals(emptyList<Int>(), SetupCardNav.dirtyButtonIndices(2, 2, count = 6))
        // Any nav move mutates at most 2 buttons.
        val step = SetupCardNav.dirtyButtonIndices(1, 2, count = 6)
        assertEquals(listOf(1, 2), step)
        assertTrue(step.size <= 2)
        val wrap = SetupCardNav.dirtyButtonIndices(0, 5, count = 6)
        assertEquals(listOf(0, 5), wrap)
        assertTrue(wrap.size <= 2)
    }

    @Test
    fun `buttonFocusStroke and QUIET buttonRoleFor meet contrast matrix across built-in and custom accents`() {
        assertEquals(SetupCardNav.ButtonRole.PRIMARY, SetupCardNav.buttonRoleFor(isPrimary = true, isSkip = false))
        assertEquals(SetupCardNav.ButtonRole.QUIET, SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = true))
        assertEquals(SetupCardNav.ButtonRole.SECONDARY, SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = false))

        val themes = ThemePack.BUILTINS + listOf(
            ThemeTokens("custom-dark", "Dark", 0xFF1A237E.toInt(), panelLift = 0xFF12141E.toInt(), chipIdle = 0xFF1B1E2E.toInt()),
            ThemeTokens("custom-white", "White", 0xFFFFFDF5.toInt(), panelLift = 0xFF181818.toInt(), chipIdle = 0xFF242424.toInt()),
        )
        for (tokens in themes) {
            val accent = tokens.accentColor
            val card = ThemePack.cardFill(tokens)

            // Primary ring distinguishes from both accent fill AND dark card (fixes OLED white-on-white).
            val primaryStroke = SetupCardNav.buttonFocusStroke(SetupCardNav.ButtonRole.PRIMARY, accent, tokens)
            assertTrue(
                "Primary focus ring blends with accent on ${tokens.id}",
                ThemePack.contrastRatio(primaryStroke, accent) >= 2.5,
            )
            assertTrue(
                "Primary focus ring blends with card on ${tokens.id}",
                ThemePack.contrastRatio(primaryStroke, card) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )

            // Secondary & Quiet rings meet WCAG 1.4.11 (>= 3.0:1) over card and chipIdle.
            for (role in listOf(SetupCardNav.ButtonRole.SECONDARY, SetupCardNav.ButtonRole.QUIET)) {
                val stroke = SetupCardNav.buttonFocusStroke(role, accent, tokens)
                assertTrue(
                    "$role focus ring too dim on card for ${tokens.id}",
                    ThemePack.contrastRatio(stroke, card) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
                )
                assertTrue(
                    "$role focus ring too dim on chipIdle for ${tokens.id}",
                    ThemePack.contrastRatio(stroke, tokens.chipIdle) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
                )
            }

            // Quiet Skip button text meets WCAG AA even when alpha-blended over dark card.
            for (selected in listOf(false, true)) {
                val (bg, fg) = SetupCardNav.buttonColors(SetupCardNav.ButtonRole.QUIET, selected, accent, tokens)
                val blendedFg = blendOverOpaque(fg, bg)
                assertTrue(
                    "QUIET button contrast too low on ${tokens.id} selected=$selected",
                    ThemePack.contrastRatio(blendedFg, bg) >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }
}
