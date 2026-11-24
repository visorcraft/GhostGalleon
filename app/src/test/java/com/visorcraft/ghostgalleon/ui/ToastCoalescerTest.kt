package com.visorcraft.ghostgalleon.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ToastCoalescerTest {

    @Test
    fun `blank messages are ignored without disturbing active toast state`() {
        val coalescer = ToastCoalescer()
        assertEquals(
            ToastCoalescer.Decision.IGNORE_BLANK,
            coalescer.decide("", long = false, nowMs = 1_000L),
        )
        assertEquals(
            ToastCoalescer.Decision.IGNORE_BLANK,
            coalescer.decide("   \n\t  ", long = true, nowMs = 1_100L),
        )
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Theme: Teal", long = false, nowMs = 1_200L),
        )
        // Blank toast while active must not clear active state.
        assertEquals(
            ToastCoalescer.Decision.IGNORE_BLANK,
            coalescer.decide(" ", long = false, nowMs = 1_300L),
        )
        assertEquals(
            ToastCoalescer.Decision.CANCEL_AND_REPLACE,
            coalescer.decide("Theme: OLED", long = false, nowMs = 1_400L),
        )
    }

    @Test
    fun `rapid distinct messages cancel and replace while active`() {
        val coalescer = ToastCoalescer()
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Theme: Ghost", long = false, nowMs = 10_000L),
        )
        assertEquals(
            ToastCoalescer.Decision.CANCEL_AND_REPLACE,
            coalescer.decide("Theme: Teal", long = false, nowMs = 10_150L),
        )
        assertEquals(
            ToastCoalescer.Decision.CANCEL_AND_REPLACE,
            coalescer.decide("Theme: Neon", long = false, nowMs = 10_300L),
        )
        // After short window (2000ms) expires, next toast shows fresh.
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Theme: OLED", long = false, nowMs = 12_400L),
        )
    }

    @Test
    fun `identical message within duplicate window is suppressed then replaced once elapsed`() {
        val coalescer = ToastCoalescer(duplicateWindowMs = 450L)
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Added to favorites", long = false, nowMs = 5_000L),
        )
        assertEquals(
            ToastCoalescer.Decision.SUPPRESS_DUPLICATE,
            coalescer.decide("  Added to favorites  ", long = false, nowMs = 5_200L),
        )
        assertEquals(
            ToastCoalescer.Decision.SUPPRESS_DUPLICATE,
            coalescer.decide("Added to favorites", long = false, nowMs = 5_449L),
        )
        // At 450ms boundary (still inside 2000ms active window), replaces cleanly.
        assertEquals(
            ToastCoalescer.Decision.CANCEL_AND_REPLACE,
            coalescer.decide("Added to favorites", long = false, nowMs = 5_450L),
        )
    }

    @Test
    fun `long toast extends active window and reset clears state`() {
        val coalescer = ToastCoalescer()
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Long notice", long = true, nowMs = 1_000L),
        )
        // Still active after 2500ms (< 3500ms long window).
        assertEquals(
            ToastCoalescer.Decision.CANCEL_AND_REPLACE,
            coalescer.decide("Follow-up", long = false, nowMs = 3_500L),
        )
        coalescer.reset()
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Follow-up", long = false, nowMs = 3_600L),
        )
    }

    @Test
    fun `identical message within 450ms on a different display is never suppressed`() {
        val coalescer = ToastCoalescer(duplicateWindowMs = 450L)
        // Toast on Sugar top screen (display 0).
        assertEquals(
            ToastCoalescer.Decision.SHOW_FRESH,
            coalescer.decide("Swapped displays", long = false, nowMs = 2_000L, displayId = 0),
        )
        // Same message on same display within 450ms is still suppressed.
        assertEquals(
            ToastCoalescer.Decision.SUPPRESS_DUPLICATE,
            coalescer.decide("Swapped displays", long = false, nowMs = 2_100L, displayId = 0),
        )
        // Same message on Sugar opposite display (display 1) within 450ms replaces cleanly so active panel gets feedback.
        assertEquals(
            ToastCoalescer.Decision.CANCEL_AND_REPLACE,
            coalescer.decide("Swapped displays", long = false, nowMs = 2_200L, displayId = 1),
        )
        // Immediate duplicate on display 1 is now suppressed on display 1.
        assertEquals(
            ToastCoalescer.Decision.SUPPRESS_DUPLICATE,
            coalescer.decide("Swapped displays", long = false, nowMs = 2_300L, displayId = 1),
        )
    }
}
