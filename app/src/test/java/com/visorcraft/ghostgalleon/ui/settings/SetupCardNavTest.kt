package com.visorcraft.ghostgalleon.ui.settings

import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
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
}
