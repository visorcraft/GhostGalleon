package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.rom.PlatformLook
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `platformDotColor clears 3 to 1 WCAG contrast across all platforms and all 4 built-in themes`() {
        for (pack in ThemePack.BUILTINS) {
            for (platform in Platforms.ALL) {
                val baseAccent = PlatformLook.accentColor(platform.id)
                val idleDot = BrowseChipStyleLogic.platformDotColor(
                    platformAccent = baseAccent,
                    selected = false,
                    themeAccent = pack.accentColor,
                    chipIdle = pack.chipIdle,
                )
                val idleRatio = ThemePack.contrastRatio(idleDot, pack.chipIdle)
                assertTrue(
                    "Idle dot contrast too low ($idleRatio) for ${platform.id} on ${pack.id}",
                    idleRatio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
                )
                val selectedDot = BrowseChipStyleLogic.platformDotColor(
                    platformAccent = baseAccent,
                    selected = true,
                    themeAccent = pack.accentColor,
                    chipIdle = pack.chipIdle,
                )
                val selectedRatio = ThemePack.contrastRatio(selectedDot, pack.accentColor)
                assertTrue(
                    "Selected dot contrast too low ($selectedRatio) for ${platform.id} on ${pack.id}",
                    selectedRatio >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `group divider placement and ergonomic 36dp chip touch height behave cleanly`() {
        assertEquals(36, BrowseChipStyleLogic.CHIP_MIN_HEIGHT_DP)
        assertFalse(
            BrowseChipStyleLogic.shouldInsertGroupDivider(
                previousGroup = null,
                nextGroup = BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
            ),
        )
        assertFalse(
            BrowseChipStyleLogic.shouldInsertGroupDivider(
                previousGroup = BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
                nextGroup = BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
            ),
        )
        assertTrue(
            BrowseChipStyleLogic.shouldInsertGroupDivider(
                previousGroup = BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
                nextGroup = BrowseChipStyleLogic.BrowseChipGroup.COLLECTIONS,
            ),
        )
        assertTrue(
            BrowseChipStyleLogic.shouldInsertGroupDivider(
                previousGroup = BrowseChipStyleLogic.BrowseChipGroup.PRIMARY_RAILS,
                nextGroup = BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
            ),
        )
        assertTrue(
            BrowseChipStyleLogic.shouldInsertGroupDivider(
                previousGroup = BrowseChipStyleLogic.BrowseChipGroup.FILTERS,
                nextGroup = BrowseChipStyleLogic.BrowseChipGroup.ACTIONS,
            ),
        )
        // Contrast/ripple tokens stay consistent across all 4 built-in themes.
        for (pack in ThemePack.BUILTINS) {
            val selectedStroke = BrowseChipStyleLogic.focusStrokeColor(selected = true, accentColor = pack.accentColor)
            assertTrue(
                "Selected focus stroke contrast too low on ${pack.id}",
                ThemePack.contrastRatio(selectedStroke, pack.accentColor) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            val ripple = BrowseChipStyleLogic.rippleColor(selected = true, accentColor = pack.accentColor)
            assertTrue(((ripple ushr 24) and 0xFF) in 0x20..0x40)
        }
    }

    @Test
    fun `continue chip width clamp and accent hairline cue adapt cleanly`() {
        val sugarMax = BrowseChipStyleLogic.maxContinueChipWidthDp(540)
        val odinMax = BrowseChipStyleLogic.maxContinueChipWidthDp(960)
        val tinyMax = BrowseChipStyleLogic.maxContinueChipWidthDp(320)
        val hugeMax = BrowseChipStyleLogic.maxContinueChipWidthDp(1920)
        assertTrue("Sugar max width out of bounds: $sugarMax", sugarMax in 160..220)
        assertTrue("Odin max width out of bounds: $odinMax", odinMax in 160..220)
        assertEquals(160, tinyMax)
        assertEquals(220, hugeMax)
        assertTrue(odinMax >= sugarMax)

        for (pack in ThemePack.BUILTINS) {
            val resting = ThemePack.hairlineDivider(pack)
            assertEquals(
                resting,
                BrowseChipStyleLogic.continueChipAccentBorder(
                    hasTarget = false,
                    accent = pack.accentColor,
                    restingHairline = resting,
                ),
            )
            assertEquals(
                ThemePack.accentHairline(pack.accentColor),
                BrowseChipStyleLogic.continueChipAccentBorder(
                    hasTarget = true,
                    accent = pack.accentColor,
                    restingHairline = resting,
                ),
            )
        }
    }
}
