package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `tileHeightDp adapts cleanly between compact and expanded browse layouts`() {
        assertEquals(QuickPanelNav.COMPACT_TILE_HEIGHT_DP, QuickPanelNav.tileHeightDp(tileCount = 8, columns = 4))
        assertEquals(QuickPanelNav.COMPACT_TILE_HEIGHT_DP, QuickPanelNav.tileHeightDp(tileCount = 6, columns = 3))
        assertEquals(QuickPanelNav.EXPANDED_TILE_HEIGHT_DP, QuickPanelNav.tileHeightDp(tileCount = 9, columns = 4))
        assertEquals(QuickPanelNav.EXPANDED_TILE_HEIGHT_DP, QuickPanelNav.tileHeightDp(tileCount = 14, columns = 4))
        assertEquals(QuickPanelNav.COMPACT_TILE_HEIGHT_DP, QuickPanelNav.tileHeightDp(tileCount = 0, columns = 0))
    }

    @Test
    fun `iconResFor and programmaticGlyphKind give every QuickPanel cell a distinct non-NONE glyph`() {
        assertEquals(R.drawable.ic_settings, QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_SETTINGS))
        assertEquals(R.drawable.ic_nav_display, QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_DISPLAY))
        assertEquals(R.drawable.ic_chevron_right, QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_CONTINUE))
        assertEquals(R.drawable.ic_close, QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_CLOSE))
        assertNotNull(QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_RAIL))
        assertNull(QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_THEME_SWATCH))
        assertNull(QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.NONE))

        // Programmatic vector glyphs for Wi-Fi, Bluetooth, Random, Controller, Audio (zero Display/Random collision).
        assertEquals(QuickGlyphDrawable.Kind.WIFI, QuickPanelNav.programmaticGlyphKind(QuickPanelNav.TileGlyph.ICON_WIFI))
        assertEquals(QuickGlyphDrawable.Kind.BLUETOOTH, QuickPanelNav.programmaticGlyphKind(QuickPanelNav.TileGlyph.ICON_BLUETOOTH))
        assertEquals(QuickGlyphDrawable.Kind.RANDOM, QuickPanelNav.programmaticGlyphKind(QuickPanelNav.TileGlyph.ICON_RANDOM))
        assertEquals(QuickGlyphDrawable.Kind.CONTROLLER, QuickPanelNav.programmaticGlyphKind(QuickPanelNav.TileGlyph.ICON_CONTROLLER))
        assertEquals(QuickGlyphDrawable.Kind.AUDIO, QuickPanelNav.programmaticGlyphKind(QuickPanelNav.TileGlyph.ICON_AUDIO))
        assertNotEquals(
            QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_DISPLAY),
            QuickPanelNav.iconResFor(QuickPanelNav.TileGlyph.ICON_RANDOM),
        )

        val coreGlyphs = QuickPanelNav.coreCellGlyphs(includeBrowse = true)
        assertFalse(coreGlyphs.contains(QuickPanelNav.TileGlyph.NONE))
        assertEquals(coreGlyphs.size, coreGlyphs.distinct().size)
        for (glyph in coreGlyphs) {
            assertTrue("Cell glyph $glyph must have a visual icon", QuickPanelNav.hasVisualGlyph(glyph))
        }

        assertEquals(R.string.quick_audio_on, QuickPanelNav.audioToggleLabelRes(soundEnabled = true))
        assertEquals(R.string.quick_audio_off, QuickPanelNav.audioToggleLabelRes(soundEnabled = false))

        // OLED light accent gets dark translucent border; dark accents get white translucent border.
        assertEquals(0x66000000, QuickPanelNav.swatchBorderColor(ThemePack.OLED.accentColor))
        assertEquals(0x66FFFFFF, QuickPanelNav.swatchBorderColor(ThemePack.GHOST.accentColor))
    }

    @Test
    fun `focus metrics, adaptive ripple colors, and builtin theme cycling behave consistently`() {
        assertEquals(QuickPanelNav.FOCUS_SCALE, QuickPanelNav.focusScale(true), 0.0001f)
        assertEquals(1f, QuickPanelNav.focusScale(false), 0.0001f)
        assertEquals(QuickPanelNav.FOCUS_ELEVATION_DP, QuickPanelNav.focusElevationDp(true))
        assertEquals(0, QuickPanelNav.focusElevationDp(false))

        assertEquals(0x33000000, QuickPanelNav.tileRippleColor(QuickPanelNav.TileRole.PRIMARY, ThemePack.OLED.accentColor))
        assertEquals(0x33FFFFFF, QuickPanelNav.tileRippleColor(QuickPanelNav.TileRole.PRIMARY, ThemePack.GHOST.accentColor))
        assertEquals(
            QuickPanelNav.STANDARD_RIPPLE_COLOR,
            QuickPanelNav.tileRippleColor(QuickPanelNav.TileRole.STANDARD, ThemePack.OLED.accentColor),
        )

        assertEquals(ThemePack.THREEDS, QuickPanelNav.nextBuiltinTheme(ThemePack.GHOST.id))
        assertEquals(ThemePack.GHOST, QuickPanelNav.nextBuiltinTheme(ThemePack.NEON.id))
        assertEquals(ThemePack.THREEDS, QuickPanelNav.nextBuiltinTheme("unknown-custom"))
    }

    @Test
    fun `dirtyTileIndices repaints at most two tiles per NAV move and all tiles on forceAll`() {
        assertEquals(emptyList<Int>(), QuickPanelNav.dirtyTileIndices(-1, 0, count = 0))
        assertEquals((0 until 12).toList(), QuickPanelNav.dirtyTileIndices(-1, 0, count = 12))
        assertEquals(listOf(0, 4), QuickPanelNav.dirtyTileIndices(0, 4, count = 12))
        assertEquals(listOf(11, 0), QuickPanelNav.dirtyTileIndices(11, 0, count = 12))
        assertEquals(emptyList<Int>(), QuickPanelNav.dirtyTileIndices(5, 5, count = 12))
        assertEquals(
            (0 until 12).toList(),
            QuickPanelNav.dirtyTileIndices(5, 5, count = 12, forceAll = true),
        )
    }

    @Test
    fun `focusStrokeColor meets WCAG 1-4-11, audioTileMuted and continueAvailability resolve cleanly`() {
        val customAccents = listOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFEE00.toInt(),
            0xFF101840.toInt(),
            0xFF00FF88.toInt(),
        )
        for (tokens in ThemePack.BUILTINS) {
            val cardSurface = ThemePack.cardFill(tokens)
            for (accent in listOf(tokens.accentColor) + customAccents) {
                for (role in QuickPanelNav.TileRole.entries) {
                    val stroke = QuickPanelNav.focusStrokeColor(role, accent, tokens)
                    assertTrue(
                        "QuickPanel focus stroke < 3.0 on ${tokens.id} role=$role accent=${Integer.toHexString(accent)}",
                        ThemePack.contrastRatio(stroke, cardSurface) >=
                            OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
                    )
                }
            }
        }
        assertFalse(QuickPanelNav.audioTileMuted(soundEnabled = true))
        assertTrue(QuickPanelNav.audioTileMuted(soundEnabled = false))
        assertEquals(QuickPanelNav.MUTED_AUDIO_IDLE_ALPHA, QuickPanelNav.tileAlpha(selected = false, mutedAudio = true), 0.001f)
        assertEquals(1f, QuickPanelNav.tileAlpha(selected = true, mutedAudio = true), 0.001f)

        val empty = QuickPanelNav.continueAvailability(null)
        assertFalse(empty.available)
        assertEquals(0.55f, empty.idleAlpha, 0.001f)
        val blank = QuickPanelNav.continueAvailability("   ")
        assertFalse(blank.available)
        val ready = QuickPanelNav.continueAvailability("rom:snes:ct")
        assertTrue(ready.available)
        assertEquals(1f, ready.idleAlpha, 0.001f)
    }

    @Test
    fun `sectionGrouping and moveInSections keep full 2D D-pad reachability across 3-col and 4-col grids`() {
        // Compact (no browse rails): single flat section, no library header.
        val compact = QuickPanelNav.sectionGrouping(quickActionCount = 8, browseRailCount = 0)
        assertEquals(0..8, compact.quickActionRange)
        assertNull(compact.libraryRange)
        assertEquals(8, compact.closeIndex)

        // Expanded with 5 browse rails: Quick Actions 0..7, Library + Close 8..13.
        val expanded = QuickPanelNav.sectionGrouping(quickActionCount = 8, browseRailCount = 5)
        assertEquals(0..7, expanded.quickActionRange)
        assertEquals(8..13, expanded.libraryRange)
        assertEquals(8, expanded.libraryStartIndex)
        assertEquals(13, expanded.closeIndex)

        val total = 14
        for (cols in listOf(QuickPanelNav.NARROW_COLUMNS, QuickPanelNav.DEFAULT_COLUMNS)) {
            val rows = QuickPanelNav.visualRows(total, cols, expanded.libraryStartIndex)
            // First section ends cleanly before libraryStartIndex = 8; Library section starts at col 0.
            assertEquals(total, rows.sumOf { it.size })
            assertTrue(rows.any { it.first() == 8 })

            // Verify every single tile is reachable from index 0 via NAV_RIGHT, NAV_LEFT, and 2D NAV_DOWN/NAV_RIGHT.
            val visitedRight = mutableSetOf<Int>()
            var cur = 0
            repeat(total) {
                visitedRight.add(cur)
                cur = QuickPanelNav.moveInSections(cur, total, cols, Action.NAV_RIGHT, expanded.libraryStartIndex)
            }
            assertEquals((0 until total).toSet(), visitedRight)
            assertEquals(0, cur)

            // Vertical traversal across section break stays in bounds and wraps cleanly.
            val visitedDown = mutableSetOf<Int>()
            var vCur = 0
            repeat(rows.size) {
                visitedDown.add(vCur)
                vCur = QuickPanelNav.moveInSections(vCur, total, cols, Action.NAV_DOWN, expanded.libraryStartIndex)
            }
            assertEquals(rows.size, visitedDown.size)
            assertEquals(0, vCur)
            assertTrue("Down traversal from col 0 must cross into Library section", visitedDown.contains(8))
        }
    }
}
