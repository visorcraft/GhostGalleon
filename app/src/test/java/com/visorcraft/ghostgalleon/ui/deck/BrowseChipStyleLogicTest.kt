package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseChipStyleLogicTest {

    @Test
    fun `rippleColor adapts to dark vs light selected accents across themes`() {
        assertEquals(
            BrowseChipStyleLogic.RIPPLE_ON_DARK,
            BrowseChipStyleLogic.rippleColor(selected = false, accentColor = ThemePack.OLED.accentColor),
        )
        assertEquals(
            BrowseChipStyleLogic.RIPPLE_ON_LIGHT,
            BrowseChipStyleLogic.rippleColor(selected = true, accentColor = ThemePack.OLED.accentColor),
        )
        assertEquals(
            BrowseChipStyleLogic.RIPPLE_ON_DARK,
            BrowseChipStyleLogic.rippleColor(selected = true, accentColor = ThemePack.GHOST.accentColor),
        )
        assertEquals(
            BrowseChipStyleLogic.RIPPLE_ON_DARK,
            BrowseChipStyleLogic.rippleColor(selected = true, accentColor = ThemePack.NEON.accentColor),
        )
    }

    @Test
    fun `focusStrokeColor preserves visibility on both idle and accent-filled chips`() {
        assertEquals(
            ThemePack.GHOST.accentColor,
            BrowseChipStyleLogic.focusStrokeColor(selected = false, accentColor = ThemePack.GHOST.accentColor),
        )
        assertEquals(
            0xFFFFFFFF.toInt(),
            BrowseChipStyleLogic.focusStrokeColor(selected = true, accentColor = ThemePack.GHOST.accentColor),
        )
        assertEquals(
            0xFF000000.toInt(),
            BrowseChipStyleLogic.focusStrokeColor(selected = true, accentColor = ThemePack.OLED.accentColor),
        )
    }

    @Test
    fun `countSuffixColor adapts to dark and light fills`() {
        assertEquals(
            UiDimens.ALPHA_MUTED,
            BrowseChipStyleLogic.countSuffixColor(selected = false, accentColor = ThemePack.GHOST.accentColor),
        )
        assertEquals(
            0xCCFFFFFF.toInt(),
            BrowseChipStyleLogic.countSuffixColor(selected = true, accentColor = ThemePack.GHOST.accentColor),
        )
        assertEquals(
            0xCC000000.toInt(),
            BrowseChipStyleLogic.countSuffixColor(selected = true, accentColor = ThemePack.OLED.accentColor),
        )
    }

    @Test
    fun `countSuffixRange detects numeric counts including locale digits while skipping non-count suffixes`() {
        assertEquals(6 until 11, BrowseChipStyleLogic.countSuffixRange("Recent · 14"))
        assertEquals(4 until 9, BrowseChipStyleLogic.countSuffixRange("SNES · 42"))
        assertEquals(13 until 17, BrowseChipStyleLogic.countSuffixRange("Clear filters · 2"))
        // Thai Unicode digits still recognized as a numeric count suffix.
        assertEquals(6 until 11, BrowseChipStyleLogic.countSuffixRange("Recent · ๑๔"))
        // Non-count suffixes stay full prominence.
        assertNull(BrowseChipStyleLogic.countSuffixRange("All"))
        assertNull(BrowseChipStyleLogic.countSuffixRange("All · A–Z"))
        assertNull(BrowseChipStyleLogic.countSuffixRange("Continue · Zelda"))
        assertNull(BrowseChipStyleLogic.countSuffixRange(" · 5"))
    }
}
