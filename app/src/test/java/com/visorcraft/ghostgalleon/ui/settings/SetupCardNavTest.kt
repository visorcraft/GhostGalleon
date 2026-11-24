package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.library.SetupNeeds
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
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
}
