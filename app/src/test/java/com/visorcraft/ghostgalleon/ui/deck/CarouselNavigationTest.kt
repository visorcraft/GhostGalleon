package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselNavigationTest {

    private val nav = CarouselNavigation(itemCount = 8)

    @Test
    fun `left and right clamp at both ends`() {
        assertEquals(0, nav.move(0, Action.NAV_LEFT))
        assertEquals(1, nav.move(0, Action.NAV_RIGHT))
        assertEquals(7, nav.move(7, Action.NAV_RIGHT))
    }

    @Test
    fun `page actions mirror left and right`() {
        assertEquals(2, nav.move(3, Action.PAGE_PREV))
        assertEquals(4, nav.move(3, Action.PAGE_NEXT))
    }

    @Test
    fun `vertical and unrelated actions do not move`() {
        assertEquals(3, nav.move(3, Action.NAV_UP))
        assertEquals(3, nav.move(3, Action.NAV_DOWN))
        assertEquals(3, nav.move(3, Action.CONFIRM))
    }

    @Test
    fun `edgeBumpDirection flags left wall at start and right wall at end only`() {
        assertEquals(-1, nav.edgeBumpDirection(0, Action.NAV_LEFT))
        assertEquals(-1, nav.edgeBumpDirection(0, Action.PAGE_PREV))
        assertEquals(0, nav.edgeBumpDirection(0, Action.NAV_RIGHT))
        assertEquals(0, nav.edgeBumpDirection(3, Action.NAV_LEFT))
        assertEquals(0, nav.edgeBumpDirection(3, Action.NAV_RIGHT))
        assertEquals(1, nav.edgeBumpDirection(7, Action.NAV_RIGHT))
        assertEquals(1, nav.edgeBumpDirection(7, Action.PAGE_NEXT))
        assertEquals(0, nav.edgeBumpDirection(7, Action.NAV_LEFT))
        assertEquals(0, nav.edgeBumpDirection(0, Action.CONFIRM))

        val empty = CarouselNavigation(itemCount = 0)
        assertEquals(0, empty.edgeBumpDirection(0, Action.NAV_LEFT))
        assertEquals(0, empty.edgeBumpDirection(0, Action.NAV_RIGHT))
    }

    @Test
    fun `shouldCommitSnapSelection commits only on idle user drag to a distinct non-empty key`() {
        assertTrue(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_IDLE,
                snappedKey = "rom:snes-chrono",
                currentKey = "rom:snes-metroid",
            ),
        )
        // Initial selection (currentKey == null) also commits cleanly.
        assertTrue(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_IDLE,
                snappedKey = "rom:snes-chrono",
                currentKey = null,
            ),
        )
        // Programmatic scroll (D-pad / chip jump) never echoes back into DeckState.
        assertFalse(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = false,
                scrollState = CarouselNavigation.SCROLL_STATE_IDLE,
                snappedKey = "rom:snes-chrono",
                currentKey = "rom:snes-metroid",
            ),
        )
        // Mid-drag and fling-settling frames never commit prematurely.
        assertFalse(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_DRAGGING,
                snappedKey = "rom:snes-chrono",
                currentKey = "rom:snes-metroid",
            ),
        )
        assertFalse(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_SETTLING,
                snappedKey = "rom:snes-chrono",
                currentKey = "rom:snes-metroid",
            ),
        )
        // No-op when snapped card is already selected or missing/blank.
        assertFalse(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_IDLE,
                snappedKey = "rom:snes-chrono",
                currentKey = "rom:snes-chrono",
            ),
        )
        assertFalse(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_IDLE,
                snappedKey = null,
                currentKey = "rom:snes-chrono",
            ),
        )
        assertFalse(
            CarouselNavigation.shouldCommitSnapSelection(
                wasUserDrag = true,
                scrollState = CarouselNavigation.SCROLL_STATE_IDLE,
                snappedKey = "",
                currentKey = "rom:snes-chrono",
            ),
        )
    }
}
