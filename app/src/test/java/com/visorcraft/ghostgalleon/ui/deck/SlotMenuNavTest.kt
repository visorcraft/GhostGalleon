package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotMenuNavTest {

    @Test
    fun `actionRowIndices skips all section headers cleanly`() {
        val rows = listOf(
            SlotMenu.Row.Header(R.string.menu_section_arrange),
            SlotMenu.Row.Item(SlotMenu.Choice.MOVE),
            SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID, destructive = true),
            SlotMenu.Row.Header(R.string.menu_section_more),
            SlotMenu.Row.Item(SlotMenu.Choice.CANCEL),
        )
        assertEquals(listOf(1, 2, 4), SlotMenuNav.actionRowIndices(rows))
    }

    @Test
    fun `move wraps on NAV_UP and NAV_DOWN and clamps on L1 R1 page jumps`() {
        assertEquals(0, SlotMenuNav.move(0, actionCount = 0, action = Action.NAV_DOWN))
        assertEquals(9, SlotMenuNav.move(0, actionCount = 10, action = Action.NAV_UP))
        assertEquals(0, SlotMenuNav.move(9, actionCount = 10, action = Action.NAV_DOWN))
        assertEquals(5, SlotMenuNav.move(0, actionCount = 10, action = Action.PAGE_NEXT))
        assertEquals(9, SlotMenuNav.move(7, actionCount = 10, action = Action.PAGE_NEXT))
        assertEquals(0, SlotMenuNav.move(2, actionCount = 10, action = Action.PAGE_PREV))
        assertEquals(4, SlotMenuNav.move(9, actionCount = 10, action = Action.PAGE_PREV))
    }

    @Test
    fun `isDestructive flags remove, remove from grid, hide, and explicit overrides`() {
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE)))
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID)))
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.HIDE)))
        assertTrue(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.MOVE, destructive = true)))
        assertFalse(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.MOVE)))
        assertFalse(SlotMenuNav.isDestructive(SlotMenu.Row.Item(SlotMenu.Choice.CANCEL)))
    }

    @Test
    fun `rowTone classifies destructive, cancel, and standard items accurately`() {
        assertEquals(
            SlotMenuNav.RowTone.DESTRUCTIVE,
            SlotMenuNav.rowTone(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID)),
        )
        assertEquals(
            SlotMenuNav.RowTone.CANCEL,
            SlotMenuNav.rowTone(SlotMenu.Row.Item(SlotMenu.Choice.CANCEL)),
        )
        assertEquals(
            SlotMenuNav.RowTone.STANDARD,
            SlotMenuNav.rowTone(SlotMenu.Row.Item(SlotMenu.Choice.MOVE)),
        )
    }

    @Test
    fun `destructive focused row uses red stroke and tinted wash while all tones meet WCAG AA`() {
        for (tokens in ThemePack.BUILTINS) {
            val destructiveSelected = SlotMenuNav.rowVisual(
                tone = SlotMenuNav.RowTone.DESTRUCTIVE,
                selected = true,
                accent = tokens.accentColor,
                tokens = tokens,
            )
            assertEquals(UiDimens.COLOR_DESTRUCTIVE, destructiveSelected.strokeColor)
            assertTrue(destructiveSelected.strokeDp > 0)
            assertNotEquals(ThemePack.cardFill(tokens), destructiveSelected.fillColor)

            for (tone in SlotMenuNav.RowTone.entries) {
                for (selected in listOf(false, true)) {
                    val spec = SlotMenuNav.rowVisual(
                        tone = tone,
                        selected = selected,
                        accent = tokens.accentColor,
                        tokens = tokens,
                    )
                    val effectiveFg = SlotMenuNav.blendOverOpaque(spec.textColor, spec.effectiveSurface)
                    val ratio = ThemePack.contrastRatio(effectiveFg, spec.effectiveSurface)
                    assertTrue(
                        "Low SlotMenu contrast $ratio for theme=${tokens.id} tone=$tone selected=$selected",
                        ratio >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
            }
        }
    }

    @Test
    fun `scrollCueAlphas reflect vertical overflow cleanly`() {
        assertEquals(0f to 0f, SlotMenuNav.scrollCueAlphas(canScrollUp = false, canScrollDown = false))
        assertEquals(0f to 1f, SlotMenuNav.scrollCueAlphas(canScrollUp = false, canScrollDown = true))
        assertEquals(1f to 0f, SlotMenuNav.scrollCueAlphas(canScrollUp = true, canScrollDown = false))
        assertEquals(1f to 1f, SlotMenuNav.scrollCueAlphas(canScrollUp = true, canScrollDown = true))
    }

    @Test
    fun `trailingGlyphRes maps drill-down chevrons, destructive close icons, and unadorned toggles`() {
        assertEquals(
            R.drawable.ic_chevron_right,
            SlotMenuNav.trailingGlyphRes(SlotMenu.Choice.ADD_TO_COLLECTION, destructive = false),
        )
        assertEquals(
            R.drawable.ic_chevron_right,
            SlotMenuNav.trailingGlyphRes(SlotMenu.Choice.SCREENS, destructive = false),
        )
        assertEquals(
            R.drawable.ic_close,
            SlotMenuNav.trailingGlyphRes(SlotMenu.Choice.REMOVE_FROM_GRID, destructive = true),
        )
        assertNull(SlotMenuNav.trailingGlyphRes(SlotMenu.Choice.MOVE, destructive = false))
        assertNull(SlotMenuNav.trailingGlyphRes(SlotMenu.Choice.CANCEL, destructive = false))

        assertTrue(SlotMenuNav.shouldTriggerTouchHaptic(hapticsEnabled = true, isCancel = false))
        assertFalse(SlotMenuNav.shouldTriggerTouchHaptic(hapticsEnabled = true, isCancel = true))
        assertFalse(SlotMenuNav.shouldTriggerTouchHaptic(hapticsEnabled = false, isCancel = false))
    }

    @Test
    fun `dirtyIndices repaints all rows on first paint and at most two rows per NAV tick`() {
        assertEquals(emptyList<Int>(), SlotMenuNav.dirtyIndices(-1, 0, count = 0))
        assertEquals(listOf(0, 1, 2, 3, 4), SlotMenuNav.dirtyIndices(-1, 0, count = 5))
        assertEquals(listOf(0, 1), SlotMenuNav.dirtyIndices(0, 1, count = 15))
        assertEquals(listOf(14, 0), SlotMenuNav.dirtyIndices(14, 0, count = 15))
        assertEquals(emptyList<Int>(), SlotMenuNav.dirtyIndices(3, 3, count = 15))
        assertEquals(emptyList<Int>(), SlotMenuNav.dirtyIndices(0, 99, count = 1))
    }

    @Test
    fun `standard focused row uses 8 percent accent wash and headerIndexForAction reveals preceding headers`() {
        for (tokens in ThemePack.BUILTINS) {
            val stdSelected = SlotMenuNav.rowVisual(
                tone = SlotMenuNav.RowTone.STANDARD,
                selected = true,
                accent = tokens.accentColor,
                tokens = tokens,
            )
            assertNotEquals(0, stdSelected.fillColor)
            val ratio = ThemePack.contrastRatio(stdSelected.textColor, stdSelected.effectiveSurface)
            assertTrue(
                "Standard wash contrast too low ($ratio) on ${tokens.id}",
                ratio >= ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "Standard wash focus stroke contrast too low on ${tokens.id}",
                ThemePack.contrastRatio(stdSelected.strokeColor, stdSelected.effectiveSurface) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
        }

        val rows = listOf(
            SlotMenu.Row.Header(R.string.menu_section_arrange), // 0
            SlotMenu.Row.Item(SlotMenu.Choice.MOVE), // action 0 -> row 1 (follows header 0)
            SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID, destructive = true), // action 1 -> row 2
            SlotMenu.Row.Header(R.string.menu_section_dock), // 3
            SlotMenu.Row.Item(SlotMenu.Choice.PIN_TO_DOCK), // action 2 -> row 4 (follows header 3)
        )
        assertEquals(0, SlotMenuNav.headerIndexForAction(rows, actionIdx = 0))
        assertEquals(-1, SlotMenuNav.headerIndexForAction(rows, actionIdx = 1))
        assertEquals(3, SlotMenuNav.headerIndexForAction(rows, actionIdx = 2))
        assertEquals(-1, SlotMenuNav.headerIndexForAction(rows, actionIdx = 9))
    }

    @Test
    fun `leadingGlyphRes covers every Choice and rowTopMarginDp adds 6dp buffer above destructive rows`() {
        for (choice in SlotMenu.Choice.entries) {
            val res = SlotMenuNav.leadingGlyphRes(choice)
            assertTrue("Missing leading glyph for $choice", res != 0)
        }
        assertEquals(R.drawable.ic_swap, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.MOVE))
        assertEquals(R.drawable.ic_nav_art, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.SET_ART))
        assertEquals(R.drawable.ic_folder, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.ADD_TO_COLLECTION))
        assertEquals(R.drawable.ic_search, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.SEARCH_LIBRARY))
        assertEquals(R.drawable.ic_settings, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.RENAME))
        assertEquals(R.drawable.ic_nav_display, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.SCREENS))
        assertEquals(R.drawable.ic_nav_about, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.DETAILS))
        assertEquals(R.drawable.ic_close, SlotMenuNav.leadingGlyphRes(SlotMenu.Choice.REMOVE_FROM_GRID))

        assertEquals(
            6,
            SlotMenuNav.rowTopMarginDp(SlotMenuNav.RowTone.DESTRUCTIVE, prevIsHeader = false),
        )
        assertEquals(
            2,
            SlotMenuNav.rowTopMarginDp(SlotMenuNav.RowTone.DESTRUCTIVE, prevIsHeader = true),
        )
        assertEquals(
            2,
            SlotMenuNav.rowTopMarginDp(SlotMenuNav.RowTone.STANDARD, prevIsHeader = false),
        )
        assertEquals(
            2,
            SlotMenuNav.rowTopMarginDp(SlotMenuNav.RowTone.CANCEL, prevIsHeader = false),
        )
    }
}
