package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionThemeAuditTest {

    @Test
    fun `filled focus ring never matches pill fill and switches to dark inset on OLED light accent`() {
        for (pack in ThemePack.BUILTINS) {
            val filledRing = CompanionThemeAudit.focusRingColorForFill(
                filled = true,
                accentColor = pack.accentColor,
                surfaceColor = pack.chipIdle,
            )
            assertNotEquals(
                "Filled focus ring for ${pack.id} must never match fill color",
                pack.accentColor,
                filledRing,
            )
            val ratio = ThemePack.contrastRatio(filledRing, pack.accentColor)
            assertTrue(
                "Filled focus ring contrast on ${pack.id} must be >= 4.5, was $ratio",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val idleRing = CompanionThemeAudit.focusRingColorForFill(
                filled = false,
                accentColor = pack.accentColor,
                surfaceColor = pack.chipIdle,
            )
            val idleRatio = ThemePack.contrastRatio(idleRing, pack.chipIdle)
            assertTrue(
                "Idle focus ring contrast on ${pack.id} chipIdle must be >= 4.5, was $idleRatio",
                idleRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
        // OLED light accent uses dark focus inset + dark ripple.
        assertEquals(
            0xFF000000.toInt(),
            CompanionThemeAudit.focusRingColorForFill(
                filled = true,
                accentColor = ThemePack.OLED.accentColor,
                surfaceColor = ThemePack.OLED.chipIdle,
            ),
        )
        assertEquals(
            CompanionThemeAudit.RIPPLE_ON_LIGHT,
            CompanionThemeAudit.rippleColorForFill(ThemePack.OLED.accentColor),
        )
        assertEquals(
            CompanionThemeAudit.RIPPLE_ON_DARK,
            CompanionThemeAudit.rippleColorForFill(ThemePack.GHOST.accentColor),
        )
    }

    @Test
    fun `composited primary, secondary, and muted text tokens clear WCAG AA across all four themes`() {
        val textTokens = listOf(
            "PRIMARY" to UiDimens.ALPHA_PRIMARY,
            "SECONDARY" to UiDimens.ALPHA_SECONDARY,
            "MUTED" to UiDimens.ALPHA_MUTED,
        )
        for (pack in ThemePack.BUILTINS) {
            val surfaces = listOf(
                "card" to ThemePack.cardFill(pack),
                "panelLift" to pack.panelLift,
                "chipIdle" to pack.chipIdle,
                "sunken" to ThemePack.surfaceSunken(pack),
            )
            for ((surfName, bg) in surfaces) {
                for ((tokName, fg) in textTokens) {
                    val rendered = CompanionThemeAudit.compositeAlphaOver(fg, bg)
                    val ratio = ThemePack.contrastRatio(rendered, bg)
                    assertTrue(
                        "$tokName on ${pack.id}/$surfName contrast must be >= 4.5, was $ratio",
                        ratio >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
            }
        }
    }

    @Test
    fun `hintKeycapTheme renders WCAG AA keycap labels across every theme pack`() {
        for (pack in ThemePack.BUILTINS) {
            val kt = CompanionThemeAudit.hintKeycapTheme(pack.id)
            val pillSurface = CompanionThemeAudit.compositeAlphaOver(kt.fillColor, pack.panelLift)
            val ratio = ThemePack.contrastRatio(kt.textColor, pillSurface)
            assertTrue(
                "HintBar keycap contrast on ${pack.id} must be >= 4.5, was $ratio",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
        val oled = CompanionThemeAudit.hintKeycapTheme(ThemePack.OLED.id)
        val ghost = CompanionThemeAudit.hintKeycapTheme(ThemePack.GHOST.id)
        assertTrue(
            "OLED keycap hairline alpha should be crisper than Ghost",
            ((oled.strokeColor ushr 24) and 0xFF) > ((ghost.strokeColor ushr 24) and 0xFF),
        )
    }
}
