package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [AudioHapticCohesion] covering Play-HUD quiet etiquette,
 * unified tactile/acoustic planning, touch/gamepad deduplication, and Controller Lab key tones.
 */
class AudioHapticCohesionTest {

    @Test
    fun `resolveForPlayHud suppresses navigation chatter and boot chime during KEEP session while keeping actions and achievements`() {
        // When no KEEP session or quiet mode off, every cue passes through unchanged
        for (cue in SoundCue.entries) {
            assertEquals(cue, AudioHapticCohesion.resolveForPlayHud(cue, keepSessionActive = false, playHudQuiet = true))
            assertEquals(cue, AudioHapticCohesion.resolveForPlayHud(cue, keepSessionActive = true, playHudQuiet = false))
        }
        assertNull(AudioHapticCohesion.resolveForPlayHud(null, keepSessionActive = false))

        // During active KEEP session with playHudQuiet = true:
        val suppressedExpected = setOf(
            SoundCue.NAV_TICK,
            SoundCue.NAV_EDGE_BUMP,
            SoundCue.NAV_PAGE_TURN,
            SoundCue.DOCK_ENTER,
            SoundCue.DOCK_EXIT,
            SoundCue.MOVE_SWAP,
            SoundCue.BOOT_CHIME,
        )
        for (cue in suppressedExpected) {
            assertNull(
                "expected $cue to be suppressed during Play-HUD quiet mode",
                AudioHapticCohesion.resolveForPlayHud(cue, keepSessionActive = true, playHudQuiet = true),
            )
        }

        // Intentional actions & celebrations remain audible
        val preservedExpected = listOf(
            SoundCue.CONFIRM,
            SoundCue.BACK,
            SoundCue.SWAP_DISPLAY,
            SoundCue.MODE_TOGGLE,
            SoundCue.MODAL_OPEN,
            SoundCue.MODAL_CLOSE,
            SoundCue.FAVORITE_ON,
            SoundCue.LAUNCH_GAME,
            SoundCue.DESTRUCTIVE,
            SoundCue.ERROR_DENY,
            SoundCue.RA_ACHIEVEMENT,
            SoundCue.SETUP_COMPLETE,
        )
        for (cue in preservedExpected) {
            assertEquals(
                "expected $cue to remain audible during Play-HUD quiet mode",
                cue,
                AudioHapticCohesion.resolveForPlayHud(cue, keepSessionActive = true, playHudQuiet = true),
            )
        }
    }

    @Test
    fun `planFeedback coordinates pan bounds, repeat haptic throttling, Play-HUD silencing, and non-finite guards`() {
        // Standard first-press navigation emits both audio and haptic with clamped pan
        val nav = AudioHapticCohesion.planFeedback(
            cue = SoundCue.NAV_TICK,
            pan = -0.95f,
            repeatStreak = 0,
            volumeMul = 1.0f,
            hapticsEnabled = true,
        )
        assertEquals(SoundCue.NAV_TICK, nav.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, nav.pan, 1e-6f)
        assertTrue(nav.playAudio)
        assertTrue(nav.playHaptic)

        // Sustained hold repeat keeps audio silky while throttling haptic buzz by default
        val repeatNav = AudioHapticCohesion.planFeedback(
            cue = SoundCue.NAV_TICK,
            pan = 0.20f,
            repeatStreak = 4,
            hapticsEnabled = true,
            allowHapticOnRepeat = false,
        )
        assertTrue(repeatNav.playAudio)
        assertFalse(repeatNav.playHaptic)
        assertEquals(4, repeatNav.repeatStreak)

        // Passive chimes (BOOT_CHIME / RA_ACHIEVEMENT) never fire spurious click haptics
        val trophy = AudioHapticCohesion.planFeedback(
            cue = SoundCue.RA_ACHIEVEMENT,
            hapticsEnabled = true,
            keepSessionActive = true,
            playHudQuiet = true,
        )
        assertEquals(SoundCue.RA_ACHIEVEMENT, trophy.cue)
        assertTrue(trophy.playAudio)
        assertFalse(trophy.playHaptic)

        // Play-HUD suppressed nav still gives subtle tactile haptic if hapticsEnabled, while audio is silent
        val quietNav = AudioHapticCohesion.planFeedback(
            cue = SoundCue.NAV_TICK,
            hapticsEnabled = true,
            keepSessionActive = true,
            playHudQuiet = true,
        )
        assertNull(quietNav.cue)
        assertFalse(quietNav.playAudio)
        assertEquals(0f, quietNav.volumeMul, 0f)
        assertTrue(quietNav.playHaptic)

        // Non-finite pan and volume sanitize cleanly
        val bad = AudioHapticCohesion.planFeedback(
            cue = SoundCue.CONFIRM,
            pan = Float.NaN,
            volumeMul = Float.POSITIVE_INFINITY,
            repeatStreak = -3,
            hapticsEnabled = false,
        )
        assertEquals(0f, bad.pan, 0f)
        assertEquals(0f, bad.volumeMul, 0f)
        assertEquals(0, bad.repeatStreak)
        assertFalse(bad.playAudio)
        assertFalse(bad.playHaptic)
    }

    @Test
    fun `touch and gamepad audio deduplication helpers prevent double firing`() {
        assertTrue(AudioHapticCohesion.shouldEmitTouchAudio(soundEnabled = true))
        assertFalse(AudioHapticCohesion.shouldEmitTouchAudio(soundEnabled = false))
        assertFalse(AudioHapticCohesion.shouldEmitTouchAudio(soundEnabled = true, isUserInitiated = false))
        assertFalse(AudioHapticCohesion.shouldEmitTouchAudio(soundEnabled = true, alreadyEmittedByGamepad = true))

        assertTrue(AudioHapticCohesion.shouldEmitGamepadAudio(soundEnabled = true))
        assertFalse(AudioHapticCohesion.shouldEmitGamepadAudio(soundEnabled = false))
        assertFalse(AudioHapticCohesion.shouldEmitGamepadAudio(soundEnabled = true, actionHandled = false))
        assertFalse(AudioHapticCohesion.shouldEmitGamepadAudio(soundEnabled = true, isKeyDown = false))
        assertFalse(AudioHapticCohesion.shouldEmitGamepadAudio(soundEnabled = true, alreadyEmittedByViewClick = true))
    }

    @Test
    fun `cueForKeyTest and panForKeyTest map every Action and unbound key to distinct lively feedback`() {
        // Exhaustively verify every Action produces a valid SoundCue and finite pan
        for (action in Action.entries) {
            val cue = AudioHapticCohesion.cueForKeyTest(action)
            val pan = AudioHapticCohesion.panForKeyTest(action)
            assertNotNull(cue)
            assertTrue(pan.isFinite() && pan in -AudioPolicy.MAX_HANDHELD_PAN..AudioPolicy.MAX_HANDHELD_PAN)
        }

        assertEquals(SoundCue.NAV_TICK, AudioHapticCohesion.cueForKeyTest(Action.NAV_LEFT))
        assertEquals(SoundCue.NAV_TICK, AudioHapticCohesion.cueForKeyTest(Action.NAV_RIGHT))
        assertEquals(SoundCue.NAV_PAGE_TURN, AudioHapticCohesion.cueForKeyTest(Action.PAGE_PREV))
        assertEquals(SoundCue.NAV_PAGE_TURN, AudioHapticCohesion.cueForKeyTest(Action.PAGE_NEXT))
        assertEquals(SoundCue.CONFIRM, AudioHapticCohesion.cueForKeyTest(Action.CONFIRM))
        assertEquals(SoundCue.BACK, AudioHapticCohesion.cueForKeyTest(Action.BACK))
        assertEquals(SoundCue.SWAP_DISPLAY, AudioHapticCohesion.cueForKeyTest(Action.SWAP_SCREENS))
        assertEquals(SoundCue.MODE_TOGGLE, AudioHapticCohesion.cueForKeyTest(Action.TOGGLE_MODE))
        assertEquals(SoundCue.FAVORITE_ON, AudioHapticCohesion.cueForKeyTest(Action.TOGGLE_FAVORITE))
        assertEquals(SoundCue.MODAL_OPEN, AudioHapticCohesion.cueForKeyTest(Action.OPEN_SETTINGS))
        assertEquals(SoundCue.FILTER_SELECT, AudioHapticCohesion.cueForKeyTest(Action.SEARCH_LIBRARY))
        assertEquals(SoundCue.MOVE_SWAP, AudioHapticCohesion.cueForKeyTest(Action.NONE))
        assertEquals(SoundCue.MOVE_SWAP, AudioHapticCohesion.cueForKeyTest(null))

        // Directional stereo pan for horizontal Controller Lab probes
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, AudioHapticCohesion.panForKeyTest(Action.NAV_LEFT), 1e-6f)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, AudioHapticCohesion.panForKeyTest(Action.NAV_RIGHT), 1e-6f)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, AudioHapticCohesion.panForKeyTest(Action.PAGE_PREV), 1e-6f)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, AudioHapticCohesion.panForKeyTest(Action.PAGE_NEXT), 1e-6f)
        assertEquals(0f, AudioHapticCohesion.panForKeyTest(Action.CONFIRM), 1e-6f)
        assertEquals(0f, AudioHapticCohesion.panForKeyTest(null), 1e-6f)
    }
}
