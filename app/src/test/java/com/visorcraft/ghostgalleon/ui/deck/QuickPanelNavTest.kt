package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPanelNavTest {

    @Test
    fun `columns adapt to narrow and standard modal widths`() {
        assertEquals(QuickPanelNav.NARROW_COLUMNS, QuickPanelNav.columnsForCardWidthDp(320))
        assertEquals(QuickPanelNav.NARROW_COLUMNS, QuickPanelNav.columnsForCardWidthDp(359))
        assertEquals(QuickPanelNav.DEFAULT_COLUMNS, QuickPanelNav.columnsForCardWidthDp(360))
        assertEquals(QuickPanelNav.DEFAULT_COLUMNS, QuickPanelNav.columnsForCardWidthDp(560))
    }

    @Test
    fun `grid navigation stays in bounds for small and partial QuickPanel counts`() {
        // Default 8-tile panel (2 full rows of 4):
        assertEquals(4, QuickPanelNav.move(0, count = 8, columns = 4, action = Action.NAV_DOWN))
        assertEquals(4, QuickPanelNav.move(0, count = 8, columns = 4, action = Action.NAV_UP))
        assertEquals(7, QuickPanelNav.move(0, count = 8, columns = 4, action = Action.NAV_LEFT))
        assertEquals(0, QuickPanelNav.move(7, count = 8, columns = 4, action = Action.NAV_RIGHT))

        // Partial last row (e.g. 9 tiles across 4 columns: [0..3], [4..7], [8]):
        assertEquals(8, QuickPanelNav.move(6, count = 9, columns = 4, action = Action.NAV_DOWN))
        assertEquals(8, QuickPanelNav.move(3, count = 9, columns = 4, action = Action.NAV_UP))
        assertEquals(4, QuickPanelNav.move(8, count = 9, columns = 4, action = Action.NAV_UP))
        assertEquals(0, QuickPanelNav.move(8, count = 9, columns = 4, action = Action.NAV_DOWN))

        // Small grids (< columns) never underflow:
        for (count in 0..3) {
            for (action in listOf(
                Action.NAV_LEFT, Action.NAV_RIGHT, Action.NAV_UP, Action.NAV_DOWN,
                Action.PAGE_PREV, Action.PAGE_NEXT,
            )) {
                val next = QuickPanelNav.move(0, count = count, columns = 4, action = action)
                if (count == 0) {
                    assertEquals(0, next)
                } else {
                    assertTrue("out of bounds: $next for count=$count", next in 0 until count)
                }
            }
        }
    }

    @Test
    fun `page prev and next jump one row at a time and clamp`() {
        assertEquals(4, QuickPanelNav.move(0, count = 11, columns = 4, action = Action.PAGE_NEXT))
        assertEquals(10, QuickPanelNav.move(8, count = 11, columns = 4, action = Action.PAGE_NEXT))
        assertEquals(0, QuickPanelNav.move(2, count = 11, columns = 4, action = Action.PAGE_PREV))
    }

    @Test
    fun `every QuickPanel tile role and focus state meets WCAG AA contrast across all themes`() {
        for (tokens in ThemePack.BUILTINS) {
            for (role in QuickPanelNav.TileRole.entries) {
                for (selected in listOf(false, true)) {
                    val (bg, fg) = QuickPanelNav.tileColors(
                        role = role,
                        selected = selected,
                        accent = tokens.accentColor,
                        tokens = tokens,
                    )
                    // Resolve translucent white alpha against the opaque dark bg.
                    val effectiveFg = blendOverOpaque(fg, bg)
                    val ratio = ThemePack.contrastRatio(effectiveFg, bg)
                    assertTrue(
                        "Low contrast $ratio for theme=${tokens.id} role=$role selected=$selected " +
                            "fg=${Integer.toHexString(fg)} bg=${Integer.toHexString(bg)}",
                        ratio >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
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
