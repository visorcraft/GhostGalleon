package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.deck.HintBarFormat.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselMultiSelectLogicTest {

    @Test
    fun `cardCheckStyle transitions cleanly between hidden, unchecked, and checked`() {
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.HIDDEN,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = false, multiSelected = false),
        )
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.HIDDEN,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = false, multiSelected = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.UNCHECKED,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = true, multiSelected = false),
        )
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = true, multiSelected = true),
        )
    }

    @Test
    fun `bannerCountText clamps negative values and uses count_selected plural`() {
        assertEquals(
            quantityText(R.plurals.count_selected, 0, 0),
            CarouselMultiSelectLogic.bannerCountText(-3),
        )
        assertEquals(
            quantityText(R.plurals.count_selected, 1, 1),
            CarouselMultiSelectLogic.bannerCountText(1),
        )
        assertEquals(
            quantityText(R.plurals.count_selected, 7, 7),
            CarouselMultiSelectLogic.bannerCountText(7),
        )
    }

    @Test
    fun `showSelectAllPill hides when rail is empty or all items are already selected`() {
        assertFalse(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 0, railTotal = 0))
        assertTrue(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 0, railTotal = 5))
        assertTrue(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 4, railTotal = 5))
        assertFalse(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 5, railTotal = 5))
        assertFalse(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 8, railTotal = 5))
    }

    @Test
    fun `banner tint helpers preserve RGB channels and damp alpha on bright OLED accents`() {
        val ghost = ThemePack.GHOST.accentColor
        val ghostFill = CarouselMultiSelectLogic.bannerTintFill(ghost)
        val ghostStroke = CarouselMultiSelectLogic.bannerTintStroke(ghost)
        assertEquals(ghost and 0x00FFFFFF, ghostFill and 0x00FFFFFF)
        assertEquals(ghost and 0x00FFFFFF, ghostStroke and 0x00FFFFFF)
        assertEquals(CarouselMultiSelectLogic.BANNER_FILL_ALPHA, (ghostFill ushr 24) and 0xFF)
        assertEquals(CarouselMultiSelectLogic.BANNER_STROKE_ALPHA, (ghostStroke ushr 24) and 0xFF)

        val oled = ThemePack.OLED.accentColor
        val oledFill = CarouselMultiSelectLogic.bannerTintFill(oled)
        val oledStroke = CarouselMultiSelectLogic.bannerTintStroke(oled)
        assertEquals(oled and 0x00FFFFFF, oledFill and 0x00FFFFFF)
        assertEquals(oled and 0x00FFFFFF, oledStroke and 0x00FFFFFF)
        assertTrue(((oledFill ushr 24) and 0xFF) < ((ghostFill ushr 24) and 0xFF))
        assertTrue(((oledStroke ushr 24) and 0xFF) < ((ghostStroke ushr 24) and 0xFF))
        assertEquals(34, CarouselMultiSelectLogic.BANNER_BUTTON_MIN_HEIGHT_DP)
    }

    @Test
    fun `cardSelectedOverlayColors ensures 3 to 1 stroke contrast and damps bright OLED wash`() {
        for (pack in ThemePack.BUILTINS) {
            val surface = ThemePack.surfaceElevated(pack)
            val colors = CarouselMultiSelectLogic.cardSelectedOverlayColors(pack.accentColor, surface)
            val ratio = ThemePack.contrastRatio(colors.strokeColor, surface)
            assertTrue(
                "Multi-select overlay stroke contrast too low ($ratio) on ${pack.id}",
                ratio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
            )
            val fillAlpha = (colors.fillColor ushr 24) and 0xFF
            assertTrue("Unexpected overlay alpha $fillAlpha on ${pack.id}", fillAlpha in 0x18..0x38)
        }
        val ghostOverlay = CarouselMultiSelectLogic.cardSelectedOverlayColors(
            ThemePack.GHOST.accentColor,
            ThemePack.surfaceElevated(ThemePack.GHOST),
        )
        val oledOverlay = CarouselMultiSelectLogic.cardSelectedOverlayColors(
            ThemePack.OLED.accentColor,
            ThemePack.surfaceElevated(ThemePack.OLED),
        )
        assertTrue(
            "OLED overlay fill alpha should be damped below Ghost",
            ((oledOverlay.fillColor ushr 24) and 0xFF) < ((ghostOverlay.fillColor ushr 24) and 0xFF),
        )

        // Checkmark pop fires strictly on animated UNCHECKED -> CHECKED transitions.
        assertTrue(
            CarouselMultiSelectLogic.shouldPopCheckmark(
                previousStyle = CarouselMultiSelectLogic.CardCheckStyle.UNCHECKED,
                nextStyle = CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
                animate = true,
                animatorsEnabled = true,
            ),
        )
        assertFalse(
            CarouselMultiSelectLogic.shouldPopCheckmark(
                previousStyle = CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
                nextStyle = CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
                animate = true,
                animatorsEnabled = true,
            ),
        )
        assertFalse(
            CarouselMultiSelectLogic.shouldPopCheckmark(
                previousStyle = null,
                nextStyle = CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
                animate = true,
                animatorsEnabled = true,
            ),
        )
        assertFalse(
            CarouselMultiSelectLogic.shouldPopCheckmark(
                previousStyle = CarouselMultiSelectLogic.CardCheckStyle.UNCHECKED,
                nextStyle = CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
                animate = true,
                animatorsEnabled = false,
            ),
        )
    }

    @Test
    fun `gamepadOutcome routes CONFIRM, BACK, and SHOW_DETAILS safely during multi-select`() {
        // Inactive multi-select passes every action through unchanged.
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.PASS_THROUGH,
            CarouselMultiSelectLogic.gamepadOutcome(false, Action.CONFIRM, hasFocusedEntry = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.PASS_THROUGH,
            CarouselMultiSelectLogic.gamepadOutcome(false, Action.BACK, hasFocusedEntry = true),
        )

        // Active multi-select toggles focused card on CONFIRM, opens bulk actions when empty/details, exits on BACK.
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.TOGGLE_CURRENT_CARD,
            CarouselMultiSelectLogic.gamepadOutcome(true, Action.CONFIRM, hasFocusedEntry = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.OPEN_BULK_ACTIONS,
            CarouselMultiSelectLogic.gamepadOutcome(true, Action.CONFIRM, hasFocusedEntry = false),
        )
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.EXIT_MULTI_SELECT,
            CarouselMultiSelectLogic.gamepadOutcome(true, Action.BACK, hasFocusedEntry = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.OPEN_BULK_ACTIONS,
            CarouselMultiSelectLogic.gamepadOutcome(true, Action.SHOW_DETAILS, hasFocusedEntry = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.PASS_THROUGH,
            CarouselMultiSelectLogic.gamepadOutcome(true, Action.NAV_RIGHT, hasFocusedEntry = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.GamepadActionOutcome.PASS_THROUGH,
            CarouselMultiSelectLogic.gamepadOutcome(true, Action.NAV_DOWN, hasFocusedEntry = true),
        )
    }

    @Test
    fun `multiSelectHintText builds keycap-aware prefix and A-B tokens cleanly across locales`() {
        val expected = text(
            R.string.format_dot_pair,
            joinText(listOf(text(R.string.format_select_count, 4), dynamicText("A")), " - "),
            text(
                R.string.format_dot_pair,
                text(R.string.bulk_actions),
                joinText(listOf(dynamicText("B"), text(R.string.action_cancel)), " "),
            ),
        )
        assertEquals(expected, CarouselMultiSelectLogic.multiSelectHintText(4))

        val expectedZero = text(
            R.string.format_dot_pair,
            joinText(listOf(text(R.string.format_select_count, 0), dynamicText("A")), " - "),
            text(
                R.string.format_dot_pair,
                text(R.string.bulk_actions),
                joinText(listOf(dynamicText("B"), text(R.string.action_cancel)), " "),
            ),
        )
        assertEquals(expectedZero, CarouselMultiSelectLogic.multiSelectHintText(-2))

        // Verify HintBarFormat tokenizes resolved multi-select hints into PREFIX + KEYCAP(A, B) with zero fake keycaps.
        val resolvedSamples = listOf(
            "Select (3) - A · Bulk actions · B Cancel" to "Select (3)",
            "Auswählen (3) - A · Massenaktionen · B Abbrechen" to "Auswählen (3)",
            "Seleccionar (3) - A · Acciones en lote · B Cancelar" to "Seleccionar (3)",
            "Sélectionner (3) - A · Actions groupées · B Annuler" to "Sélectionner (3)",
            "เลือก (3) - A · ดำเนินการหลายรายการ · B ยกเลิก" to "เลือก (3)",
        )
        for ((raw, expectedPrefix) in resolvedSamples) {
            val tokens = HintBarFormat.tokenize(raw)
            val prefixes = tokens.filter { it.kind == TokenKind.PREFIX }.map { raw.substring(it.start, it.end) }
            val keycaps = tokens.filter { it.kind == TokenKind.KEYCAP }.map { raw.substring(it.start, it.end) }
            val actions = tokens.filter { it.kind == TokenKind.ACTION }.map { raw.substring(it.start, it.end) }
            assertEquals("prefix mismatch for: $raw", listOf(expectedPrefix), prefixes)
            assertEquals("keycaps mismatch for: $raw", listOf("A", "B"), keycaps)
            assertEquals("actions count mismatch for: $raw", 2, actions.size)
        }
    }
}
