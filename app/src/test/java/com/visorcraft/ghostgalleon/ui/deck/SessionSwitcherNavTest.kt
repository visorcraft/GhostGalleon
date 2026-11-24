package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.SessionRingEntry
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSwitcherNavTest {

    private fun entry(key: String) = SessionRingEntry(
        key = key,
        playerId = "retroarch_snes",
        packageName = "com.retroarch",
        policy = SessionPolicy.KEEP_COMPANION,
        launchedAtMs = 1_000L,
        title = key,
    )

    @Test
    fun `vertical navigation wraps and page jumps clamp cleanly`() {
        assertEquals(0, SessionSwitcherNav.move(0, count = 0, action = Action.NAV_DOWN))
        assertEquals(2, SessionSwitcherNav.move(0, count = 3, action = Action.NAV_UP))
        assertEquals(0, SessionSwitcherNav.move(2, count = 3, action = Action.NAV_DOWN))
        assertEquals(1, SessionSwitcherNav.move(0, count = 3, action = Action.NAV_DOWN))
        assertEquals(4, SessionSwitcherNav.move(0, count = 6, action = Action.PAGE_NEXT))
        assertEquals(5, SessionSwitcherNav.move(4, count = 6, action = Action.PAGE_NEXT))
        assertEquals(0, SessionSwitcherNav.move(2, count = 6, action = Action.PAGE_PREV))
    }

    @Test
    fun `active entry detection matches exact non-blank key only`() {
        assertTrue(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", "rom:snes/zelda"))
        assertFalse(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", "rom:snes/mario"))
        assertFalse(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", null))
        assertFalse(SessionSwitcherNav.isActiveEntry("rom:snes/zelda", "   "))
    }

    @Test
    fun `initialSelection preserves clamped previous index or selects active entry`() {
        val list = listOf(entry("a"), entry("b"), entry("c"))
        // Fresh open highlights active entry when present.
        assertEquals(1, SessionSwitcherNav.initialSelection(list, previousSelection = null, activeKey = "b"))
        assertEquals(0, SessionSwitcherNav.initialSelection(list, previousSelection = null, activeKey = "missing"))
        // Re-attach after Y removal keeps cursor at clamped index.
        assertEquals(2, SessionSwitcherNav.initialSelection(list, previousSelection = 3, activeKey = "a"))
        assertEquals(1, SessionSwitcherNav.initialSelection(list, previousSelection = 1, activeKey = "a"))
        assertEquals(0, SessionSwitcherNav.initialSelection(emptyList(), previousSelection = 2, activeKey = "a"))
    }

    @Test
    fun `artworkFor distinguishes ROM platform entries from app packages`() {
        val romEntry = entry("rom:snes:roms/snes/zelda.smc")
        assertEquals(
            SessionSwitcherNav.EntryArtwork.Rom(
                romId = "snes:roms/snes/zelda.smc",
                platformId = "snes",
            ),
            SessionSwitcherNav.artworkFor(romEntry),
        )

        val appEntry = SessionRingEntry(
            key = "org.videolan.vlc",
            playerId = null,
            packageName = "org.videolan.vlc",
            policy = SessionPolicy.KEEP_COMPANION,
            launchedAtMs = 1_000L,
            title = "VLC",
        )
        assertEquals(
            SessionSwitcherNav.EntryArtwork.App("org.videolan.vlc"),
            SessionSwitcherNav.artworkFor(appEntry),
        )
    }

    @Test
    fun `recencyText and metadataParts format timestamps and omit blank fragments`() {
        val now = 10_000_000L
        assertNull(SessionSwitcherNav.recencyText(launchedAtMs = 0L, nowMs = now))
        assertEquals(
            UiText.Resource(R.string.time_just_now),
            SessionSwitcherNav.recencyText(launchedAtMs = now - 10_000L, nowMs = now),
        )
        assertEquals(
            UiText.Resource(R.string.time_minutes_ago, listOf(5L)),
            SessionSwitcherNav.recencyText(launchedAtMs = now - 5 * 60_000L, nowMs = now),
        )
        assertEquals(
            listOf("RetroArch", "5m ago"),
            SessionSwitcherNav.metadataParts("RetroArch", "5m ago"),
        )
        assertEquals(
            listOf("Just now"),
            SessionSwitcherNav.metadataParts("  ", "Just now"),
        )
        assertTrue(SessionSwitcherNav.metadataParts("", null).isEmpty())
    }

    @Test
    fun `rowVisual separates active from background rows and meets WCAG AA across all themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val activeIdle = SessionSwitcherNav.rowVisual(
                isActive = true,
                selected = false,
                accent = tokens.accentColor,
                tokens = tokens,
            )
            val bgIdle = SessionSwitcherNav.rowVisual(
                isActive = false,
                selected = false,
                accent = tokens.accentColor,
                tokens = tokens,
            )
            assertNotEquals(
                "Active session row should visually separate from background on ${tokens.id}",
                bgIdle.fillColor,
                activeIdle.fillColor,
            )

            for (isActive in listOf(false, true)) {
                for (selected in listOf(false, true)) {
                    val visual = SessionSwitcherNav.rowVisual(
                        isActive = isActive,
                        selected = selected,
                        accent = tokens.accentColor,
                        tokens = tokens,
                    )
                    val titleFg = SlotMenuNav.blendOverOpaque(visual.titleColor, visual.fillColor)
                    val titleRatio = ThemePack.contrastRatio(titleFg, visual.fillColor)
                    assertTrue(
                        "Low title contrast $titleRatio on theme=${tokens.id} active=$isActive selected=$selected",
                        titleRatio >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                    val subFg = SlotMenuNav.blendOverOpaque(visual.subtitleColor, visual.fillColor)
                    val subRatio = ThemePack.contrastRatio(subFg, visual.fillColor)
                    assertTrue(
                        "Low subtitle contrast $subRatio on theme=${tokens.id} active=$isActive selected=$selected",
                        subRatio >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
            }
        }
    }

    @Test
    fun `dirtyRowIndices repaints all rows initially and at most two rows per NAV tick`() {
        assertEquals(emptyList<Int>(), SessionSwitcherNav.dirtyRowIndices(-1, 0, count = 0))
        assertEquals(listOf(0, 1, 2, 3), SessionSwitcherNav.dirtyRowIndices(-1, 1, count = 4))
        assertEquals(listOf(1, 2), SessionSwitcherNav.dirtyRowIndices(1, 2, count = 8))
        assertEquals(listOf(7, 0), SessionSwitcherNav.dirtyRowIndices(7, 0, count = 8))
        assertEquals(emptyList<Int>(), SessionSwitcherNav.dirtyRowIndices(2, 2, count = 8))
    }
}
