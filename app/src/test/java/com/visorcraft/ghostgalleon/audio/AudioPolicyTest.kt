package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AudioPolicyTest {

    @Test
    fun `volumePercentToGain is perceptually tapered, strictly monotonic, and exact at endpoints`() {
        assertEquals(0f, AudioPolicy.volumePercentToGain(-10), 0f)
        assertEquals(0f, AudioPolicy.volumePercentToGain(0), 0f)
        assertEquals(1f, AudioPolicy.volumePercentToGain(100), 0f)
        assertEquals(1f, AudioPolicy.volumePercentToGain(150), 0f)

        var prev = AudioPolicy.volumePercentToGain(0)
        for (pct in 1..100) {
            val curr = AudioPolicy.volumePercentToGain(pct)
            assertTrue("gain must strictly increase at $pct%: prev=$prev curr=$curr", curr > prev)
            assertTrue("gain out of range at $pct%: $curr", curr in 0f..1f)
            prev = curr
        }

        // Logarithmic taper: 35% feels naturally gentle (well below linear 0.35)
        val g35 = AudioPolicy.volumePercentToGain(35)
        val g50 = AudioPolicy.volumePercentToGain(50)
        assertTrue("35% gain ($g35) should be gentle (< 0.12)", g35 < 0.12f)
        assertTrue("50% gain ($g50) should be concave (< 0.25)", g50 < 0.25f)
    }

    @Test
    fun `CooldownTracker enforces per-cue spacing while letting high-priority cues bypass nav throttle`() {
        val tracker = AudioPolicy.CooldownTracker(navGlobalFloorMs = 20L)

        // First NAV_TICK succeeds, rapid duplicate fails, boundary succeeds
        assertTrue(tracker.tryAcquire(SoundCue.NAV_TICK, nowMs = 1000L))
        assertFalse(tracker.tryAcquire(SoundCue.NAV_TICK, nowMs = 1010L))
        assertTrue(tracker.tryAcquire(SoundCue.NAV_TICK, nowMs = 1000L + SoundCue.NAV_TICK.defaultCooldownMs))

        // High-priority cues bypass nav throttling immediately after a NAV_TICK
        assertTrue(tracker.tryAcquire(SoundCue.NAV_TICK, nowMs = 2000L))
        assertTrue(tracker.tryAcquire(SoundCue.CONFIRM, nowMs = 2002L))
        assertTrue(tracker.tryAcquire(SoundCue.LAUNCH_GAME, nowMs = 2004L))
        assertTrue(tracker.tryAcquire(SoundCue.RA_ACHIEVEMENT, nowMs = 2005L))

        // Higher-priority nav cue (EDGE_BUMP) preempts lower-priority NAV_TICK global floor
        assertTrue(tracker.tryAcquire(SoundCue.NAV_TICK, nowMs = 3000L))
        assertTrue(tracker.tryAcquire(SoundCue.NAV_EDGE_BUMP, nowMs = 3005L))
        // Immediately after EDGE_BUMP, lower-priority NAV_TICK respects global nav floor
        assertFalse(tracker.tryAcquire(SoundCue.NAV_TICK, nowMs = 3010L))

        // Reset clears all timestamps
        tracker.reset()
        assertTrue(tracker.tryAcquire(SoundCue.LAUNCH_GAME, nowMs = 2005L))
    }

    @Test
    fun `repeatExpression attenuates velocity gently and keeps pitch musical`() {
        val fresh = AudioPolicy.repeatExpression(0)
        assertEquals(1.0f, fresh.gainMultiplier, 1e-6f)
        assertEquals(1.0f, fresh.pitchRate, 1e-6f)

        val negative = AudioPolicy.repeatExpression(-3)
        assertEquals(fresh, negative)

        var prevGain = fresh.gainMultiplier
        for (streak in 1..40) {
            val expr = AudioPolicy.repeatExpression(streak)
            assertTrue("gain must stay in [0.72, 1.0], got ${expr.gainMultiplier}", expr.gainMultiplier in 0.72f..1.0f)
            assertTrue("gain should monotonically soften at streak=$streak", expr.gainMultiplier <= prevGain + 1e-6f)
            assertTrue("pitch must stay in [0.97, 1.05], got ${expr.pitchRate}", expr.pitchRate in 0.97f..1.05f)
            prevGain = expr.gainMultiplier
        }
        assertTrue("long hold should attenuate near 0.72", AudioPolicy.repeatExpression(30).gainMultiplier < 0.74f)
    }

    @Test
    fun `stereo panning is symmetric, bounded to handheld spread, and constant-power`() {
        assertEquals(0f, AudioPolicy.panForColumn(0, 1), 1e-6f)
        assertEquals(0f, AudioPolicy.panForColumn(0, 0), 1e-6f)
        assertEquals(-0.28f, AudioPolicy.panForColumn(0, 4), 1e-6f)
        assertEquals(0.28f, AudioPolicy.panForColumn(3, 4), 1e-6f)
        assertEquals(0f, AudioPolicy.panForColumn(2, 5), 1e-6f)
        assertEquals(-AudioPolicy.panForColumn(1, 4), AudioPolicy.panForColumn(2, 4), 1e-6f)

        assertEquals(-0.28f, AudioPolicy.panForDirection(-1), 1e-6f)
        assertEquals(0f, AudioPolicy.panForDirection(0), 1e-6f)
        assertEquals(0.28f, AudioPolicy.panForDirection(1), 1e-6f)

        val center = AudioPolicy.stereoGains(0.8f, 0f)
        assertEquals(center.left, center.right, 1e-6f)
        assertEquals(0.8f, sqrt(center.left * center.left + center.right * center.right), 1e-4f)

        val left = AudioPolicy.stereoGains(0.8f, -0.28f)
        val right = AudioPolicy.stereoGains(0.8f, 0.28f)
        assertEquals(left.left, right.right, 1e-6f)
        assertEquals(left.right, right.left, 1e-6f)
        assertTrue(left.left > left.right)
        assertEquals(0.8f, sqrt(left.left * left.left + left.right * left.right), 1e-4f)

        val silent = AudioPolicy.stereoGains(0f, 0.28f)
        assertEquals(0f, silent.left, 0f)
        assertEquals(0f, silent.right, 0f)

        // Non-finite masterGain returns safe zero; non-finite pan returns safe center
        for (badGain in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val zeroed = AudioPolicy.stereoGains(badGain, 0.25f)
            assertEquals("left must be 0f for gain=$badGain", 0f, zeroed.left, 0f)
            assertEquals("right must be 0f for gain=$badGain", 0f, zeroed.right, 0f)
        }
        for (badPan in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val centered = AudioPolicy.stereoGains(0.8f, badPan)
            assertTrue(centered.left.isFinite() && centered.right.isFinite())
            assertEquals("left must match center for pan=$badPan", center.left, centered.left, 1e-6f)
            assertEquals("right must match center for pan=$badPan", center.right, centered.right, 1e-6f)
        }
    }

    @Test
    fun `shouldPlaySfx and sfxDuckFactor enforce ringer, music, KEEP session, and cue toggles`() {
        assertTrue(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.NAV_TICK,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = false,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.NAV_TICK,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.OFF,
                sfxVolumePercent = 75,
                cue = SoundCue.NAV_TICK,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 0,
                cue = SoundCue.NAV_TICK,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.NAV_TICK,
                activeAppActivityCount = 0,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.NAV_TICK,
                screenOff = true,
            ),
        )

        // Ringer mode etiquette
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.CONFIRM,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.SILENT,
            ),
        )
        assertTrue(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.CONFIRM,
                respectRinger = false,
                ringerState = AudioPolicy.RingerState.VIBRATE,
            ),
        )

        // Boot chime yields to external music and KEEP sessions, and respects toggle
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.BOOT_CHIME,
                bootChimeEnabled = false,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.BOOT_CHIME,
                respectMusic = true,
                externalMusicActive = true,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.BOOT_CHIME,
                hasKeepSession = true,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlaySfx(
                soundEnabled = true,
                pack = SoundPackId.GHOST,
                sfxVolumePercent = 75,
                cue = SoundCue.RA_ACHIEVEMENT,
                achievementChimeEnabled = false,
            ),
        )

        // Ducking factors
        assertEquals(1.0f, AudioPolicy.sfxDuckFactor(SoundCue.NAV_TICK), 1e-6f)
        assertEquals(
            AudioPolicy.DUCK_EXTERNAL_MUSIC_UI,
            AudioPolicy.sfxDuckFactor(SoundCue.NAV_TICK, respectMusic = true, externalMusicActive = true),
            1e-6f,
        )
        assertEquals(
            1.0f,
            AudioPolicy.sfxDuckFactor(SoundCue.NAV_TICK, respectMusic = false, externalMusicActive = true),
            1e-6f,
        )
        assertEquals(
            AudioPolicy.DUCK_VIDEO_PREVIEW_UI,
            AudioPolicy.sfxDuckFactor(SoundCue.CONFIRM, videoPreviewUnmuted = true),
            1e-6f,
        )
        assertEquals(
            AudioPolicy.DUCK_PLAY_HUD_QUIET_UI,
            AudioPolicy.sfxDuckFactor(SoundCue.NAV_TICK, hasKeepSession = true, playHudQuiet = true),
            1e-6f,
        )
        assertEquals(
            AudioPolicy.DUCK_PLAY_HUD_ACHIEVEMENT,
            AudioPolicy.sfxDuckFactor(SoundCue.RA_ACHIEVEMENT, hasKeepSession = true, playHudQuiet = true),
            1e-6f,
        )
    }

    @Test
    fun `shouldPlayAmbient strictly gates on foreground, KEEP session, screen, music, ringer, video, and focus`() {
        assertTrue(
            AudioPolicy.shouldPlayAmbient(
                soundEnabled = true,
                ambientEnabled = true,
                pack = SoundPackId.GHOST,
                ambientVolumePercent = 50,
                activeAppActivityCount = 1,
            ),
        )

        // Each blocking condition alone must halt ambient playback
        assertFalse(AudioPolicy.shouldPlayAmbient(false, true, SoundPackId.GHOST, 50, 1))
        assertFalse(AudioPolicy.shouldPlayAmbient(true, false, SoundPackId.GHOST, 50, 1))
        assertFalse(AudioPolicy.shouldPlayAmbient(true, true, SoundPackId.OFF, 50, 1))
        assertFalse(AudioPolicy.shouldPlayAmbient(true, true, SoundPackId.GHOST, 0, 1))
        assertFalse(AudioPolicy.shouldPlayAmbient(true, true, SoundPackId.GHOST, 50, 0))
        assertFalse(AudioPolicy.shouldPlayAmbient(true, true, SoundPackId.GHOST, 50, 1, hasKeepSession = true))
        assertFalse(AudioPolicy.shouldPlayAmbient(true, true, SoundPackId.GHOST, 50, 1, screenOff = true))
        assertFalse(
            AudioPolicy.shouldPlayAmbient(
                true, true, SoundPackId.GHOST, 50, 1,
                respectRinger = true, ringerState = AudioPolicy.RingerState.VIBRATE,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlayAmbient(
                true, true, SoundPackId.GHOST, 50, 1,
                respectMusic = true, externalMusicActive = true,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlayAmbient(
                true, true, SoundPackId.GHOST, 50, 1,
                videoPreviewUnmuted = true,
            ),
        )
        assertFalse(
            AudioPolicy.shouldPlayAmbient(
                true, true, SoundPackId.GHOST, 50, 1,
                audioFocusGranted = false,
            ),
        )

        // Opting out of respectMusic / respectRinger allows ambient to continue
        assertTrue(
            AudioPolicy.shouldPlayAmbient(
                true, true, SoundPackId.GHOST, 50, 1,
                respectRinger = false, ringerState = AudioPolicy.RingerState.SILENT,
                respectMusic = false, externalMusicActive = true,
            ),
        )

        assertEquals(1.0f, AudioPolicy.ambientDuckFactor(false), 1e-6f)
        assertEquals(AudioPolicy.DUCK_AMBIENT_TRANSIENT, AudioPolicy.ambientDuckFactor(true), 1e-6f)
    }
}
