package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import kotlin.math.roundToInt

/**
 * Pure, host-tested scroll fading-edge sizing and page-flip / boundary-bump
 * feedback classifier for [GridDeck].
 */
object GridScrollFeedback {

    sealed class Cue {
        object None : Cue()

        /** Selection crossed into an adjacent or distant page (`-1` prev, `+1` next). */
        data class PageFlipped(val direction: Int) : Cue()

        /** Input hit a clamped grid/page boundary (`-1` start edge, `+1` end edge). */
        data class EdgeBump(val direction: Int) : Cue()
    }

    const val MIN_FADING_EDGE_DP: Int = 12
    const val MAX_FADING_EDGE_DP: Int = 22

    const val EDGE_BUMP_HORIZONTAL_DP: Float = 7f
    const val EDGE_BUMP_VERTICAL_DP: Float = 5.5f
    const val STRIP_NUDGE_DP: Float = 4f
    const val CHEVRON_PULSE_SCALE: Float = 1.24f

    const val BUMP_OUT_MS: Long = 70L
    const val BUMP_BACK_MS: Long = 120L

    /**
     * Classify visual motion feedback for a grid navigation or L1/R1 page action
     * transitioning from [fromSlot] to [toSlot].
     */
    fun classify(
        action: Action,
        fromSlot: Int,
        toSlot: Int,
        cellsPerPage: Int,
        slotCount: Int,
    ): Cue {
        val safeCells = cellsPerPage.coerceAtLeast(1)
        val safeSlots = slotCount.coerceAtLeast(1)
        val fromPage = GridPageIndicatorMetrics.stripState(fromSlot, safeSlots, safeCells).currentPage
        val toPage = GridPageIndicatorMetrics.stripState(toSlot, safeSlots, safeCells).currentPage

        return when (action) {
            Action.PAGE_PREV -> when {
                toPage < fromPage -> Cue.PageFlipped(-1)
                toSlot == fromSlot -> Cue.EdgeBump(-1)
                else -> Cue.None
            }
            Action.PAGE_NEXT -> when {
                toPage > fromPage -> Cue.PageFlipped(+1)
                toSlot == fromSlot -> Cue.EdgeBump(+1)
                else -> Cue.None
            }
            Action.NAV_LEFT, Action.NAV_UP -> when {
                toPage != fromPage -> Cue.PageFlipped(if (toPage > fromPage) +1 else -1)
                toSlot == fromSlot -> Cue.EdgeBump(-1)
                else -> Cue.None
            }
            Action.NAV_RIGHT, Action.NAV_DOWN -> when {
                toPage != fromPage -> Cue.PageFlipped(if (toPage > fromPage) +1 else -1)
                toSlot == fromSlot -> Cue.EdgeBump(+1)
                else -> Cue.None
            }
            else -> Cue.None
        }
    }

    /**
     * Soft top/bottom fading-edge height in dp scaled from viewport height so
     * compact ~540dp Sugar panels stay airy while tall 16:9 panels get deeper fades.
     */
    fun fadingEdgeLengthDp(viewportHeightPx: Int, density: Float): Int {
        val d = density.coerceAtLeast(0.1f)
        val viewportDp = (viewportHeightPx.coerceAtLeast(1) / d)
        return (viewportDp * 0.04f).roundToInt().coerceIn(MIN_FADING_EDGE_DP, MAX_FADING_EDGE_DP)
    }
}
