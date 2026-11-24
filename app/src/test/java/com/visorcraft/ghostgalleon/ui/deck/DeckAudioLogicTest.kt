package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.audio.AudioPolicy
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckAudioLogicTest {

    @Test
    fun `gridNavigationEvent distinguishes normal panned tick, page flip, and edge bump`() {
        // 5 columns, 10 cells per page, 20 slots (2 pages)
        val moveLeftCol = DeckAudioLogic.gridNavigationEvent(
            action = Action.NAV_LEFT,
            fromSlot = 1,
            toSlot = 0,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
            repeatStreak = 3,
        )!!
        assertEquals(SoundCue.NAV_TICK, moveLeftCol.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, moveLeftCol.pan, 1e-6f)
        assertEquals(3, moveLeftCol.repeatStreak)

        val moveCenterCol = DeckAudioLogic.gridNavigationEvent(
            action = Action.NAV_RIGHT,
            fromSlot = 1,
            toSlot = 2,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
        )!!
        assertEquals(SoundCue.NAV_TICK, moveCenterCol.cue)
        assertEquals(0f, moveCenterCol.pan, 1e-6f)

        val moveRightCol = DeckAudioLogic.gridNavigationEvent(
            action = Action.NAV_RIGHT,
            fromSlot = 3,
            toSlot = 4,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
        )!!
        assertEquals(SoundCue.NAV_TICK, moveRightCol.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, moveRightCol.pan, 1e-6f)

        // Crossing into next page emits NAV_PAGE_TURN panned right (streak 0)
        val pageNext = DeckAudioLogic.gridNavigationEvent(
            action = Action.PAGE_NEXT,
            fromSlot = 2,
            toSlot = 12,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
            repeatStreak = 4,
        )!!
        assertEquals(SoundCue.NAV_PAGE_TURN, pageNext.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, pageNext.pan, 1e-6f)
        assertEquals(0, pageNext.repeatStreak)

        // Crossing back via NAV_LEFT from slot 10 -> 9 emits NAV_PAGE_TURN panned left
        val crossPrev = DeckAudioLogic.gridNavigationEvent(
            action = Action.NAV_LEFT,
            fromSlot = 10,
            toSlot = 9,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
        )!!
        assertEquals(SoundCue.NAV_PAGE_TURN, crossPrev.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, crossPrev.pan, 1e-6f)

        // Left wall hit emits NAV_EDGE_BUMP panned left
        val leftWall = DeckAudioLogic.gridNavigationEvent(
            action = Action.NAV_LEFT,
            fromSlot = 0,
            toSlot = 0,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, leftWall.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, leftWall.pan, 1e-6f)

        // Top wall hit emits NAV_EDGE_BUMP centered
        val topWall = DeckAudioLogic.gridNavigationEvent(
            action = Action.NAV_UP,
            fromSlot = 2,
            toSlot = 2,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, topWall.cue)
        assertEquals(0f, topWall.pan, 1e-6f)

        // Right page-next wall on final page emits NAV_EDGE_BUMP panned right
        val endPageWall = DeckAudioLogic.gridNavigationEvent(
            action = Action.PAGE_NEXT,
            fromSlot = 19,
            toSlot = 19,
            columns = 5,
            cellsPerPage = 10,
            slotCount = 20,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, endPageWall.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, endPageWall.pan, 1e-6f)
    }

    @Test
    fun `gridPageJumpEvent handles page dot taps and chevron edge clamps`() {
        // Jumping from page 0 to page 2 -> NAV_PAGE_TURN right
        val forward = DeckAudioLogic.gridPageJumpEvent(
            fromSlot = 0,
            toSlot = 20,
            cellsPerPage = 10,
            slotCount = 30,
        )!!
        assertEquals(SoundCue.NAV_PAGE_TURN, forward.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, forward.pan, 1e-6f)

        // Jumping from page 2 to page 0 -> NAV_PAGE_TURN left
        val back = DeckAudioLogic.gridPageJumpEvent(
            fromSlot = 22,
            toSlot = 2,
            cellsPerPage = 10,
            slotCount = 30,
        )!!
        assertEquals(SoundCue.NAV_PAGE_TURN, back.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, back.pan, 1e-6f)

        // Tapping the already-active page dot -> null (no spurious sound)
        assertNull(
            DeckAudioLogic.gridPageJumpEvent(
                fromSlot = 3,
                toSlot = 3,
                cellsPerPage = 10,
                slotCount = 30,
                deltaDirection = null,
            ),
        )

        // Clamped chevron delta at start wall -> NAV_EDGE_BUMP left
        val clampStart = DeckAudioLogic.gridPageJumpEvent(
            fromSlot = 0,
            toSlot = 0,
            cellsPerPage = 10,
            slotCount = 30,
            deltaDirection = -1,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, clampStart.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, clampStart.pan, 1e-6f)
    }

    @Test
    fun `gridMoveStepEvent and move lifecycle cues cover pickup, swap, wall bump, drop, and cancel`() {
        val pickup = DeckAudioLogic.movePickupEvent(DeckAudioLogic.panForGridSlot(0, 4))
        assertEquals(SoundCue.MOVE_PICKUP, pickup.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, pickup.pan, 1e-6f)

        val swap = DeckAudioLogic.gridMoveStepEvent(
            action = Action.NAV_RIGHT,
            fromSlot = 0,
            toSlot = 3,
            columns = 4,
            cellsPerPage = 8,
            slotCount = 16,
            repeatStreak = 2,
        )!!
        assertEquals(SoundCue.MOVE_SWAP, swap.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, swap.pan, 1e-6f)
        assertEquals(2, swap.repeatStreak)

        val bump = DeckAudioLogic.gridMoveStepEvent(
            action = Action.NAV_LEFT,
            fromSlot = 0,
            toSlot = 0,
            columns = 4,
            cellsPerPage = 8,
            slotCount = 16,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, bump.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, bump.pan, 1e-6f)

        val drop = DeckAudioLogic.moveDropEvent(DeckAudioLogic.panForGridSlot(3, 4))
        assertEquals(SoundCue.MOVE_DROP, drop.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, drop.pan, 1e-6f)

        val cancel = DeckAudioLogic.moveCancelEvent()
        assertEquals(SoundCue.BACK, cancel.cue)
    }

    @Test
    fun `dock cues cover enter, exit, panned walk, boundary edge bump, and dock move swaps`() {
        val enter = DeckAudioLogic.dockEnterEvent(dockIndex = 0, visibleCount = 5)
        assertEquals(SoundCue.DOCK_ENTER, enter.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, enter.pan, 1e-6f)

        val exit = DeckAudioLogic.dockExitEvent(dockIndex = 4, visibleCount = 5)
        assertEquals(SoundCue.DOCK_EXIT, exit.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, exit.pan, 1e-6f)

        // Walking dock slots -> panned NAV_TICK with streak
        val walk = DeckAudioLogic.dockNavigationEvent(
            action = Action.NAV_RIGHT,
            fromIndex = 1,
            toIndex = 2,
            visibleCount = 5,
            repeatStreak = 2,
        )!!
        assertEquals(SoundCue.NAV_TICK, walk.cue)
        assertEquals(0f, walk.pan, 1e-6f)
        assertEquals(2, walk.repeatStreak)

        // Dock start/end clamp -> NAV_EDGE_BUMP panned toward bumped end
        val leftClamp = DeckAudioLogic.dockNavigationEvent(
            action = Action.NAV_LEFT,
            fromIndex = 0,
            toIndex = 0,
            visibleCount = 5,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, leftClamp.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, leftClamp.pan, 1e-6f)

        val rightClamp = DeckAudioLogic.dockNavigationEvent(
            action = Action.NAV_RIGHT,
            fromIndex = 4,
            toIndex = 4,
            visibleCount = 5,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, rightClamp.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, rightClamp.pan, 1e-6f)

        // Dock move swap & wall clamp
        val moveSwap = DeckAudioLogic.dockMoveStepEvent(
            action = Action.NAV_RIGHT,
            fromIndex = 0,
            toIndex = 1,
            visibleCount = 4,
            repeatStreak = 1,
        )!!
        assertEquals(SoundCue.MOVE_SWAP, moveSwap.cue)
        assertTrue(moveSwap.pan < 0f && moveSwap.pan > -AudioPolicy.MAX_HANDHELD_PAN)
        assertEquals(1, moveSwap.repeatStreak)

        val moveBump = DeckAudioLogic.dockMoveStepEvent(
            action = Action.NAV_RIGHT,
            fromIndex = 2,
            toIndex = 2,
            visibleCount = 4,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, moveBump.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, moveBump.pan, 1e-6f)
    }

    @Test
    fun `carousel cues pan by direction, honor edge bumps, and map multi-select toggles`() {
        val right = DeckAudioLogic.carouselNavigationEvent(
            action = Action.NAV_RIGHT,
            fromIndex = 2,
            toIndex = 3,
            bumpDirection = 0,
            repeatStreak = 5,
        )!!
        assertEquals(SoundCue.NAV_TICK, right.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, right.pan, 1e-6f)
        assertEquals(5, right.repeatStreak)

        val left = DeckAudioLogic.carouselNavigationEvent(
            action = Action.NAV_LEFT,
            fromIndex = 3,
            toIndex = 2,
            bumpDirection = 0,
        )!!
        assertEquals(SoundCue.NAV_TICK, left.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, left.pan, 1e-6f)

        val bumpLeft = DeckAudioLogic.carouselNavigationEvent(
            action = Action.NAV_LEFT,
            fromIndex = 0,
            toIndex = 0,
            bumpDirection = -1,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, bumpLeft.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, bumpLeft.pan, 1e-6f)

        val bumpRight = DeckAudioLogic.carouselNavigationEvent(
            action = Action.PAGE_NEXT,
            fromIndex = 9,
            toIndex = 9,
            bumpDirection = +1,
        )!!
        assertEquals(SoundCue.NAV_EDGE_BUMP, bumpRight.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, bumpRight.pan, 1e-6f)

        // Empty rail (no bump, no index change) -> null
        assertNull(
            DeckAudioLogic.carouselNavigationEvent(
                action = Action.NAV_RIGHT,
                fromIndex = 0,
                toIndex = 0,
                bumpDirection = 0,
            ),
        )

        // Touch card focus pans toward tapped neighbor
        val tapRight = DeckAudioLogic.carouselCardFocusEvent(currentIndex = 2, tappedIndex = 4)
        assertEquals(SoundCue.NAV_TICK, tapRight.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, tapRight.pan, 1e-6f)
        val tapLeft = DeckAudioLogic.carouselCardFocusEvent(currentIndex = 2, tappedIndex = 1)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, tapLeft.pan, 1e-6f)
        val tapSame = DeckAudioLogic.carouselCardFocusEvent(currentIndex = 2, tappedIndex = 2)
        assertEquals(0f, tapSame.pan, 1e-6f)

        // Tap-to-focus grid slot
        val gridTap = DeckAudioLogic.gridSlotFocusEvent(slot = 9, columns = 5)
        assertNotNull(gridTap)
        assertEquals(SoundCue.NAV_TICK, gridTap.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, gridTap.pan, 1e-6f)

        // Multi-select toggle cues
        assertEquals(SoundCue.CONFIRM, DeckAudioLogic.multiSelectToggleEvent(nowSelected = true).cue)
        assertEquals(SoundCue.BACK, DeckAudioLogic.multiSelectToggleEvent(nowSelected = false).cue)
    }
}
