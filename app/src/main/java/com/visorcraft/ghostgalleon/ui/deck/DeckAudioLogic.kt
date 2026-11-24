package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.audio.AudioPolicy
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.settings.Action

/**
 * Pure, host-tested audio choreography helpers for [GridDeck] and [GameDeck].
 *
 * Maps grid/carousel navigation, page flips, boundary edge-bumps, dock transitions,
 * multi-select toggles, and move-mode steps to semantic [SoundCue]s, gentle handheld
 * stereo pan, and hold-repeat streak values. Completely decoupled from system animator
 * scale / reduced-motion guards so audio feedback always plays reliably.
 */
object DeckAudioLogic {

    data class SoundEvent(
        val cue: SoundCue,
        val pan: Float = 0f,
        val repeatStreak: Int = 0,
    )

    /**
     * Classifies a [GridDeck] directional or page-step navigation transition from
     * [fromSlot] to [toSlot] (independent of reduced-motion animation guards).
     */
    fun gridNavigationEvent(
        action: Action,
        fromSlot: Int,
        toSlot: Int,
        columns: Int,
        cellsPerPage: Int,
        slotCount: Int,
        repeatStreak: Int = 0,
    ): SoundEvent? {
        val safeCols = columns.coerceAtLeast(1)
        val scrollCue = GridScrollFeedback.classify(
            action = action,
            fromSlot = fromSlot,
            toSlot = toSlot,
            cellsPerPage = cellsPerPage,
            slotCount = slotCount,
        )
        return when (scrollCue) {
            is GridScrollFeedback.Cue.PageFlipped -> SoundEvent(
                cue = SoundCue.NAV_PAGE_TURN,
                pan = AudioPolicy.panForDirection(scrollCue.direction),
                repeatStreak = 0,
            )
            is GridScrollFeedback.Cue.EdgeBump -> SoundEvent(
                cue = SoundCue.NAV_EDGE_BUMP,
                pan = edgeBumpPanForAction(action, scrollCue.direction),
                repeatStreak = 0,
            )
            GridScrollFeedback.Cue.None -> {
                if (toSlot == fromSlot) return null
                SoundEvent(
                    cue = SoundCue.NAV_TICK,
                    pan = panForGridSlot(toSlot, safeCols),
                    repeatStreak = repeatStreak.coerceAtLeast(0),
                )
            }
        }
    }

    /**
     * Classifies a [GridDeck] page-dot tap or page-chevron delta jump from [fromSlot] to [toSlot].
     * Emits [SoundCue.NAV_PAGE_TURN] when changing pages, [SoundCue.NAV_EDGE_BUMP] on clamped
     * delta jump, or `null` when tapping the already-active page indicator dot.
     */
    fun gridPageJumpEvent(
        fromSlot: Int,
        toSlot: Int,
        cellsPerPage: Int,
        slotCount: Int,
        deltaDirection: Int? = null,
    ): SoundEvent? {
        val safeCells = cellsPerPage.coerceAtLeast(1)
        val safeSlots = slotCount.coerceAtLeast(1)
        val fromPage = GridPageIndicatorMetrics.stripState(fromSlot, safeSlots, safeCells).currentPage
        val toPage = GridPageIndicatorMetrics.stripState(toSlot, safeSlots, safeCells).currentPage
        return when {
            toPage != fromPage -> SoundEvent(
                cue = SoundCue.NAV_PAGE_TURN,
                pan = AudioPolicy.panForDirection(if (toPage > fromPage) +1 else -1),
                repeatStreak = 0,
            )
            deltaDirection != null && toSlot == fromSlot -> SoundEvent(
                cue = SoundCue.NAV_EDGE_BUMP,
                pan = AudioPolicy.panForDirection(deltaDirection),
                repeatStreak = 0,
            )
            else -> null
        }
    }

    /** Touch tap-to-focus on an unfocused grid slot. */
    fun gridSlotFocusEvent(slot: Int, columns: Int): SoundEvent = SoundEvent(
        cue = SoundCue.NAV_TICK,
        pan = panForGridSlot(slot, columns),
        repeatStreak = 0,
    )

    /**
     * Classifies a [GridDeck] move-mode step (`NAV_*` / `PAGE_*`) from [fromSlot] to [toSlot].
     * Emits [SoundCue.NAV_EDGE_BUMP] when clamped at a wall, or [SoundCue.MOVE_SWAP] panned
     * to the destination column when the lifted tile swaps slots.
     */
    fun gridMoveStepEvent(
        action: Action,
        fromSlot: Int,
        toSlot: Int,
        columns: Int,
        cellsPerPage: Int,
        slotCount: Int,
        repeatStreak: Int = 0,
    ): SoundEvent? {
        val scrollCue = GridScrollFeedback.classify(
            action = action,
            fromSlot = fromSlot,
            toSlot = toSlot,
            cellsPerPage = cellsPerPage,
            slotCount = slotCount,
        )
        return when {
            toSlot == fromSlot && scrollCue is GridScrollFeedback.Cue.EdgeBump -> SoundEvent(
                cue = SoundCue.NAV_EDGE_BUMP,
                pan = edgeBumpPanForAction(action, scrollCue.direction),
                repeatStreak = 0,
            )
            toSlot != fromSlot -> SoundEvent(
                cue = SoundCue.MOVE_SWAP,
                pan = panForGridSlot(toSlot, columns),
                repeatStreak = repeatStreak.coerceAtLeast(0),
            )
            else -> null
        }
    }

    /** Dock entry from grid or carousel. */
    fun dockEnterEvent(dockIndex: Int, visibleCount: Int): SoundEvent = SoundEvent(
        cue = SoundCue.DOCK_ENTER,
        pan = panForDockSlot(dockIndex, visibleCount),
        repeatStreak = 0,
    )

    /** Dock exit back to grid or carousel. */
    fun dockExitEvent(dockIndex: Int, visibleCount: Int): SoundEvent = SoundEvent(
        cue = SoundCue.DOCK_EXIT,
        pan = panForDockSlot(dockIndex, visibleCount),
        repeatStreak = 0,
    )

    /**
     * Dock horizontal navigation (`NAV_LEFT`/`NAV_RIGHT`) from [fromIndex] to [toIndex].
     * Emits [SoundCue.NAV_EDGE_BUMP] panned toward the bumped end when `toIndex == fromIndex`,
     * or [SoundCue.NAV_TICK] panned to [toIndex] with [repeatStreak] when moving.
     */
    fun dockNavigationEvent(
        action: Action,
        fromIndex: Int,
        toIndex: Int,
        visibleCount: Int,
        repeatStreak: Int = 0,
    ): SoundEvent? = when {
        action != Action.NAV_LEFT && action != Action.NAV_RIGHT -> null
        toIndex == fromIndex -> SoundEvent(
            cue = SoundCue.NAV_EDGE_BUMP,
            pan = AudioPolicy.panForDirection(if (action == Action.NAV_LEFT) -1 else +1),
            repeatStreak = 0,
        )
        else -> SoundEvent(
            cue = SoundCue.NAV_TICK,
            pan = panForDockSlot(toIndex, visibleCount),
            repeatStreak = repeatStreak.coerceAtLeast(0),
        )
    }

    /** Dock move-mode step (`NAV_LEFT`/`NAV_RIGHT`). */
    fun dockMoveStepEvent(
        action: Action,
        fromIndex: Int,
        toIndex: Int,
        visibleCount: Int,
        repeatStreak: Int = 0,
    ): SoundEvent? = when {
        action != Action.NAV_LEFT && action != Action.NAV_RIGHT -> null
        toIndex == fromIndex -> SoundEvent(
            cue = SoundCue.NAV_EDGE_BUMP,
            pan = AudioPolicy.panForDirection(if (action == Action.NAV_LEFT) -1 else +1),
            repeatStreak = 0,
        )
        else -> SoundEvent(
            cue = SoundCue.MOVE_SWAP,
            pan = panForDockSlot(toIndex, visibleCount),
            repeatStreak = repeatStreak.coerceAtLeast(0),
        )
    }

    /**
     * Classifies a [GameDeck] carousel step (`NAV_LEFT`/`NAV_RIGHT`/`PAGE_PREV`/`PAGE_NEXT`),
     * independent of reduced-motion animation guards.
     */
    fun carouselNavigationEvent(
        action: Action,
        fromIndex: Int,
        toIndex: Int,
        bumpDirection: Int,
        repeatStreak: Int = 0,
    ): SoundEvent? {
        if (bumpDirection != 0) {
            return SoundEvent(
                cue = SoundCue.NAV_EDGE_BUMP,
                pan = AudioPolicy.panForDirection(bumpDirection),
                repeatStreak = 0,
            )
        }
        if (toIndex == fromIndex) return null
        val dx = when {
            toIndex > fromIndex -> +1
            toIndex < fromIndex -> -1
            action == Action.NAV_LEFT || action == Action.PAGE_PREV -> -1
            action == Action.NAV_RIGHT || action == Action.PAGE_NEXT -> +1
            else -> 0
        }
        return SoundEvent(
            cue = SoundCue.NAV_TICK,
            pan = AudioPolicy.panForDirection(dx),
            repeatStreak = repeatStreak.coerceAtLeast(0),
        )
    }

    /** Touch tap-to-focus on an unfocused carousel card relative to [currentIndex]. */
    fun carouselCardFocusEvent(currentIndex: Int, tappedIndex: Int): SoundEvent = SoundEvent(
        cue = SoundCue.NAV_TICK,
        pan = AudioPolicy.panForDirection(tappedIndex.compareTo(currentIndex)),
        repeatStreak = 0,
    )

    /** Multi-select card toggle cue (`CONFIRM` when selected, `BACK` when deselected). */
    fun multiSelectToggleEvent(nowSelected: Boolean): SoundEvent = SoundEvent(
        cue = if (nowSelected) SoundCue.CONFIRM else SoundCue.BACK,
        pan = 0f,
        repeatStreak = 0,
    )

    /** Move-mode lifecycle cues. */
    fun movePickupEvent(pan: Float = 0f): SoundEvent = SoundEvent(SoundCue.MOVE_PICKUP, pan)
    fun moveDropEvent(pan: Float = 0f): SoundEvent = SoundEvent(SoundCue.MOVE_DROP, pan)
    fun moveCancelEvent(pan: Float = 0f): SoundEvent = SoundEvent(SoundCue.BACK, pan)

    /** Maps a zero-based grid [slot] into gentle handheld stereo pan across [columns]. */
    fun panForGridSlot(slot: Int, columns: Int): Float {
        val safeCols = columns.coerceAtLeast(1)
        val col = if (slot >= 0) slot % safeCols else 0
        return AudioPolicy.panForColumn(col, safeCols)
    }

    /** Maps a zero-based [dockIndex] into gentle handheld stereo pan across [visibleCount] slots. */
    fun panForDockSlot(dockIndex: Int, visibleCount: Int): Float =
        AudioPolicy.panForColumn(dockIndex, visibleCount.coerceAtLeast(1))

    private fun edgeBumpPanForAction(action: Action, fallbackDirection: Int): Float = when (action) {
        Action.NAV_LEFT, Action.PAGE_PREV -> AudioPolicy.panForDirection(-1)
        Action.NAV_RIGHT, Action.PAGE_NEXT -> AudioPolicy.panForDirection(+1)
        Action.NAV_UP, Action.NAV_DOWN -> 0f
        else -> AudioPolicy.panForDirection(fallbackDirection)
    }
}
