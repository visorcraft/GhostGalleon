package com.visorcraft.ghostgalleon.ui.deck

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerMatchTest {

    @Test
    fun `findMatchSpans locates case-insensitive and repeated non-overlapping occurrences`() {
        assertEquals(
            listOf(MatchSpan(6, 11)),
            AppPickerMatch.findMatchSpans("Super Mario World", "mario"),
        )
        assertEquals(
            listOf(MatchSpan(0, 2), MatchSpan(3, 5)),
            AppPickerMatch.findMatchSpans("ha-ha", "HA"),
        )
        // Non-overlapping: "aa" in "aaa" matches once at [0, 2).
        assertEquals(
            listOf(MatchSpan(0, 2)),
            AppPickerMatch.findMatchSpans("aaa", "aa"),
        )
    }

    @Test
    fun `findMatchSpans handles blank queries, trimming, and regex metacharacters safely`() {
        assertTrue(AppPickerMatch.findMatchSpans("Super Metroid", "").isEmpty())
        assertTrue(AppPickerMatch.findMatchSpans("Super Metroid", "   ").isEmpty())
        assertTrue(AppPickerMatch.findMatchSpans("", "zelda").isEmpty())
        assertEquals(
            listOf(MatchSpan(0, 5)),
            AppPickerMatch.findMatchSpans("Zelda", "  zelda  "),
        )
        assertEquals(
            listOf(MatchSpan(6, 11)),
            AppPickerMatch.findMatchSpans("Hades (USA) [Rev 1]", "(usa)"),
        )
    }

    @Test
    fun `hasQueryChanged ignores surrounding whitespace and detects actual query changes`() {
        assertFalse(AppPickerMatch.hasQueryChanged("", "   "))
        assertFalse(AppPickerMatch.hasQueryChanged("mario", "  mario "))
        assertTrue(AppPickerMatch.hasQueryChanged("", "m"))
        assertTrue(AppPickerMatch.hasQueryChanged("mar", "mario"))
        assertTrue(AppPickerMatch.hasQueryChanged("mario", ""))
    }

    @Test
    fun `canClearFromEmptyState is true only when zero results and query is non-blank`() {
        assertTrue(AppPickerMatch.canClearFromEmptyState(selectableCount = 0, query = "xyz"))
        assertFalse(AppPickerMatch.canClearFromEmptyState(selectableCount = 0, query = ""))
        assertFalse(AppPickerMatch.canClearFromEmptyState(selectableCount = 0, query = "   "))
        assertFalse(AppPickerMatch.canClearFromEmptyState(selectableCount = 3, query = "mario"))
    }

    @Test
    fun `title and badge highlight colors meet WCAG AA contrast across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val titleColor = AppPickerMatch.titleHighlightColor(tokens.accentColor, tokens)
            val titleRatio = ThemePack.contrastRatio(titleColor, ThemePack.cardFill(tokens))
            assertTrue(
                "Low title highlight contrast $titleRatio on theme=${tokens.id}",
                titleRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val badgeColor = AppPickerMatch.badgeHighlightColor(tokens.accentColor, tokens)
            val badgeRatio = ThemePack.contrastRatio(badgeColor, ThemePack.surfaceSunken(tokens))
            assertTrue(
                "Low badge highlight contrast $badgeRatio on theme=${tokens.id}",
                badgeRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `sectionCounts tallies apps and roms independently and excludes headers`() {
        val items = listOf(
            PickerItem.Header(PickerItem.Header.Section.APPS),
            PickerItem.App(AppEntry("a1", "App 1", isGame = false)),
            PickerItem.App(AppEntry("a2", "App 2", isGame = false)),
            PickerItem.Header(PickerItem.Header.Section.ROMS),
            PickerItem.Rom(RomEntry("r1", "Rom 1", "snes", "content://r1", null)),
            PickerItem.Rom(RomEntry("r2", "Rom 2", "snes", "content://r2", null)),
            PickerItem.Rom(RomEntry("r3", "Rom 3", "gb", "content://r3", null)),
        )
        val counts = AppPickerMatch.sectionCounts(items)
        assertEquals(2, counts.apps)
        assertEquals(3, counts.roms)
        assertEquals(2, counts.countFor(PickerItem.Header.Section.APPS))
        assertEquals(3, counts.countFor(PickerItem.Header.Section.ROMS))
        assertEquals(PickerSectionCounts(0, 0), AppPickerMatch.sectionCounts(emptyList()))
    }

    @Test
    fun `isSearchLaunchAction triggers on IME Go, Search, Done, and Enter key down only`() {
        assertTrue(AppPickerMatch.isSearchLaunchAction(EditorInfo.IME_ACTION_GO))
        assertTrue(AppPickerMatch.isSearchLaunchAction(EditorInfo.IME_ACTION_SEARCH))
        assertTrue(AppPickerMatch.isSearchLaunchAction(EditorInfo.IME_ACTION_DONE))
        assertTrue(
            AppPickerMatch.isSearchLaunchAction(
                EditorInfo.IME_NULL,
                keyCode = KeyEvent.KEYCODE_ENTER,
                keyAction = KeyEvent.ACTION_DOWN,
            ),
        )
        assertFalse(
            AppPickerMatch.isSearchLaunchAction(
                EditorInfo.IME_NULL,
                keyCode = KeyEvent.KEYCODE_ENTER,
                keyAction = KeyEvent.ACTION_UP,
            ),
        )
        assertFalse(AppPickerMatch.isSearchLaunchAction(EditorInfo.IME_ACTION_NEXT))
    }

    @Test
    fun `rowVisual applies WCAG 1-4-11 stroke, 8 percent wash, accent bar, and bold lift across all accents`() {
        val customAccents = listOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFEE00.toInt(),
            0xFF101840.toInt(),
            0xFF00FF88.toInt(),
        )
        for (tokens in ThemePack.BUILTINS) {
            val idle = AppPickerHighlight.rowVisual(false, tokens.accentColor, tokens)
            assertEquals(0, idle.fillColor)
            assertEquals(0, idle.strokeDp)
            assertFalse(idle.showAccentBar)
            assertFalse(idle.boldTitle)

            for (accent in listOf(tokens.accentColor) + customAccents) {
                val active = AppPickerHighlight.rowVisual(true, accent, tokens)
                assertTrue(active.showAccentBar)
                assertTrue(active.boldTitle)
                assertEquals(3, active.strokeDp)
                assertTrue(
                    "Highlight stroke contrast too low on ${tokens.id} accent=${Integer.toHexString(accent)}",
                    ThemePack.contrastRatio(active.strokeColor, active.effectiveSurface) >=
                        OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
                )
                assertTrue(
                    "Highlighted title contrast too low on ${tokens.id} accent=${Integer.toHexString(accent)}",
                    ThemePack.contrastRatio(UiDimens.ALPHA_PRIMARY, active.effectiveSurface) >=
                        ThemePack.MIN_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `searchBarStrokeState and clearButtonVisible respond to focus and query state`() {
        assertEquals(
            AppPickerMatch.SearchStrokeState.IDLE,
            AppPickerMatch.searchBarStrokeState(hasFocus = false, query = ""),
        )
        assertEquals(
            AppPickerMatch.SearchStrokeState.IDLE,
            AppPickerMatch.searchBarStrokeState(hasFocus = false, query = "   "),
        )
        assertEquals(
            AppPickerMatch.SearchStrokeState.ACTIVE_QUERY,
            AppPickerMatch.searchBarStrokeState(hasFocus = false, query = "zelda"),
        )
        assertEquals(
            AppPickerMatch.SearchStrokeState.FOCUSED,
            AppPickerMatch.searchBarStrokeState(hasFocus = true, query = ""),
        )
        assertEquals(
            AppPickerMatch.SearchStrokeState.FOCUSED,
            AppPickerMatch.searchBarStrokeState(hasFocus = true, query = "mario"),
        )
        assertFalse(AppPickerMatch.clearButtonVisible(null))
        assertFalse(AppPickerMatch.clearButtonVisible(""))
        assertTrue(AppPickerMatch.clearButtonVisible(" "))
        assertTrue(AppPickerMatch.clearButtonVisible("snes"))

        for (tokens in ThemePack.BUILTINS) {
            val (focusColor, focusDp) = AppPickerMatch.searchBarStroke(
                AppPickerMatch.SearchStrokeState.FOCUSED,
                tokens.accentColor,
                tokens,
            )
            assertEquals(2, focusDp)
            assertTrue(
                ThemePack.contrastRatio(focusColor, ThemePack.surfaceSunken(tokens)) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
            val (_, idleDp) = AppPickerMatch.searchBarStroke(
                AppPickerMatch.SearchStrokeState.IDLE,
                tokens.accentColor,
                tokens,
            )
            assertEquals(1, idleDp)
        }
    }

    @Test
    fun `pageStep scales across library size tiers and centeredOffsetPx keeps look-ahead context`() {
        assertEquals(5, AppPickerHighlight.pageStep(0))
        assertEquals(5, AppPickerHighlight.pageStep(40))
        assertEquals(10, AppPickerHighlight.pageStep(41))
        assertEquals(10, AppPickerHighlight.pageStep(200))
        assertEquals(25, AppPickerHighlight.pageStep(201))
        assertEquals(25, AppPickerHighlight.pageStep(6500))

        assertEquals(0, AppPickerHighlight.centeredOffsetPx(viewportH = 0, rowH = 120))
        assertEquals(0, AppPickerHighlight.centeredOffsetPx(viewportH = 100, rowH = 140))
        assertEquals(140, AppPickerHighlight.centeredOffsetPx(viewportH = 400, rowH = 120))
    }

    @Test
    fun `platformPillBorderColor preserves platform RGB hue at 0x66 alpha`() {
        val snes = PlatformTile.colorFor("snes")
        val border = AppPickerMatch.platformPillBorderColor(snes)
        assertEquals(0x66, (border ushr 24) and 0xFF)
        assertEquals(snes and 0x00FFFFFF, border and 0x00FFFFFF)
        assertNotEquals(
            AppPickerMatch.platformPillBorderColor(PlatformTile.colorFor("gba")),
            border,
        )
    }

    @Test
    fun `emptyClearFocusRing stays high-contrast across OLED, dark, and bright accents`() {
        assertEquals(44, AppPickerMatch.EMPTY_CLEAR_MIN_HEIGHT_DP)
        val brightAccents = listOf(
            ThemePack.OLED.accentColor,
            0xFFFFFFFF.toInt(),
            0xFFFFEE00.toInt(),
            0xFF00FF88.toInt(),
        )
        for (bright in brightAccents) {
            val ring = AppPickerMatch.emptyClearFocusRing(bright)
            assertNotEquals(UiDimens.ALPHA_PRIMARY, ring)
            assertTrue(
                "Bright accent ring must contrast against fill=${Integer.toHexString(bright)}",
                ThemePack.contrastRatio(ring, bright) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
            assertTrue(
                "Bright accent ring must contrast against OLED card",
                ThemePack.contrastRatio(ring, ThemePack.cardFill(ThemePack.OLED)) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
        }
        val darkAccents = listOf(
            ThemePack.GHOST.accentColor,
            ThemePack.THREEDS.accentColor,
            ThemePack.NEON.accentColor,
            0xFF000000.toInt(),
            0xFF101840.toInt(),
        )
        for (dark in darkAccents) {
            val ring = AppPickerMatch.emptyClearFocusRing(dark)
            assertEquals(UiDimens.ALPHA_PRIMARY, ring)
            assertTrue(
                ThemePack.contrastRatio(ring, ThemePack.cardFill(ThemePack.GHOST)) >=
                    OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
        }
    }
}
