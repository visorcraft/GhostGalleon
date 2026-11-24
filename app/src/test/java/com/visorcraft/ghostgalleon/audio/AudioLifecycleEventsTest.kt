package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLifecycleEventsTest {

    @Test
    fun `BootChimeGate fires once for MainActivity and ignores CompanionActivity churn without consuming`() {
        val gate = BootChimeGate()
        assertFalse(gate.hasConsumed)

        // CompanionActivity absorb/redirect/restart churn must never trigger or consume the gate
        assertFalse(
            gate.tryClaim(
                isMainActivity = false,
                soundEnabled = true,
                bootChimeEnabled = true,
            ),
        )
        assertFalse(gate.hasConsumed)

        // First real MainActivity frame claims the process-once slot
        assertTrue(
            gate.tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = true,
            ),
        )
        assertTrue(gate.hasConsumed)

        // Subsequent MainActivity rebuilds/recreations are permanently suppressed
        assertFalse(
            gate.tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = true,
            ),
        )
    }

    @Test
    fun `BootChimeGate suppresses disabled settings, KEEP sessions, screen-off, and backgrounded cold starts`() {
        // Disabled boot chime at cold start returns false AND consumes so toggling later doesn't fire belatedly
        val disabledBoot = BootChimeGate()
        assertFalse(
            disabledBoot.tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = false,
            ),
        )
        assertTrue(disabledBoot.hasConsumed)
        assertFalse(
            disabledBoot.tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = true,
            ),
        )

        // Master sound disabled
        val disabledSound = BootChimeGate()
        assertFalse(
            disabledSound.tryClaim(
                isMainActivity = true,
                soundEnabled = false,
                bootChimeEnabled = true,
            ),
        )

        // Active KEEP session suppresses boot chime
        val inKeep = BootChimeGate()
        assertFalse(
            inKeep.tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = true,
                hasKeepSession = true,
            ),
        )

        // Screen off or zero active activities suppresses boot chime
        assertFalse(
            BootChimeGate().tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = true,
                screenOff = true,
            ),
        )
        assertFalse(
            BootChimeGate().tryClaim(
                isMainActivity = true,
                soundEnabled = true,
                bootChimeEnabled = true,
                activeAppActivityCount = 0,
            ),
        )
    }

    @Test
    fun `shouldPlayRaUnlockFanfare stays silent on initial load or empty diff and fires on newly unlocked batch`() {
        // Initial snapshot load (prev == null) must stay silent even when user already has unlocks
        assertFalse(
            AudioLifecycleEvents.shouldPlayRaUnlockFanfare(
                previousUnlockedIds = null,
                currentUnlockedIds = setOf(101, 102, 103),
                soundEnabled = true,
                raAchievementChimeEnabled = true,
            ),
        )

        // Identical snapshot (no new unlocks) stays silent
        assertFalse(
            AudioLifecycleEvents.shouldPlayRaUnlockFanfare(
                previousUnlockedIds = setOf(101, 102),
                currentUnlockedIds = setOf(101, 102),
                soundEnabled = true,
                raAchievementChimeEnabled = true,
            ),
        )

        // Single new unlock triggers fanfare
        assertTrue(
            AudioLifecycleEvents.shouldPlayRaUnlockFanfare(
                previousUnlockedIds = setOf(101, 102),
                currentUnlockedIds = setOf(101, 102, 103),
                soundEnabled = true,
                raAchievementChimeEnabled = true,
            ),
        )

        // Batch of multiple simultaneous new unlocks returns true once for the batch
        assertTrue(
            AudioLifecycleEvents.shouldPlayRaUnlockFanfare(
                previousUnlockedIds = emptySet(),
                currentUnlockedIds = setOf(201, 202, 203),
                soundEnabled = true,
                raAchievementChimeEnabled = true,
            ),
        )

        // Disabled toggles suppress fanfare
        assertFalse(
            AudioLifecycleEvents.shouldPlayRaUnlockFanfare(
                previousUnlockedIds = setOf(101),
                currentUnlockedIds = setOf(101, 102),
                soundEnabled = false,
                raAchievementChimeEnabled = true,
            ),
        )
        assertFalse(
            AudioLifecycleEvents.shouldPlayRaUnlockFanfare(
                previousUnlockedIds = setOf(101),
                currentUnlockedIds = setOf(101, 102),
                soundEnabled = true,
                raAchievementChimeEnabled = false,
            ),
        )
    }

    @Test
    fun `video preview target gain and fade-in ramp stay calibrated, monotonic, and non-finite safe`() {
        // Disabled or muted returns exact 0f
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(false, true, 80),
            1e-6f,
        )
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(true, false, 80),
            1e-6f,
        )
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(true, true, 0),
            1e-6f,
        )
        assertFalse(AudioLifecycleEvents.shouldEnableVideoPreviewAudio(true, false, 80))
        assertFalse(AudioLifecycleEvents.shouldEnableVideoPreviewAudio(true, true, 0))

        // Calibrated monotonic scaling with AudioPolicy.volumePercentToGain
        val low = AudioLifecycleEvents.videoPreviewTargetGain(true, true, 30)
        val mid = AudioLifecycleEvents.videoPreviewTargetGain(true, true, 75)
        val full = AudioLifecycleEvents.videoPreviewTargetGain(true, true, 100)
        assertTrue(AudioLifecycleEvents.shouldEnableVideoPreviewAudio(true, true, 75))
        assertTrue(low > 0f && mid > low && full > mid)
        assertEquals(AudioLifecycleEvents.VIDEO_PREVIEW_MAX_GAIN, full, 1e-6f)
        assertEquals(
            AudioPolicy.volumePercentToGain(75) * AudioLifecycleEvents.VIDEO_PREVIEW_MAX_GAIN,
            mid,
            1e-6f,
        )

        // Fade-in curve starts at 0, eases smoothly, reaches target at 1.0, and guards NaN/Inf
        assertEquals(0f, AudioLifecycleEvents.videoPreviewFadeGain(mid, 0f), 1e-6f)
        val half = AudioLifecycleEvents.videoPreviewFadeGain(mid, 0.5f)
        assertTrue(half > 0f && half < mid)
        assertEquals(mid, AudioLifecycleEvents.videoPreviewFadeGain(mid, 1f), 1e-6f)
        assertEquals(0f, AudioLifecycleEvents.videoPreviewFadeGain(Float.NaN, 0.5f), 1e-6f)
        assertEquals(0f, AudioLifecycleEvents.videoPreviewFadeGain(mid, Float.NaN), 1e-6f)
        assertEquals(mid, AudioLifecycleEvents.videoPreviewFadeGain(mid, 5f), 1e-6f)
    }

    @Test
    fun `VideoPreviewAudioTracker reference-counts overlapping previews and clamps cleanly at zero`() {
        val tracker = VideoPreviewAudioTracker()
        assertEquals(0, tracker.count)
        assertFalse(tracker.isActive)

        // Redundant release at 0 is a safe no-op
        assertFalse(tracker.release())
        assertEquals(0, tracker.count)

        // 0 -> 1 signals ambient pause edge
        assertTrue(tracker.acquire())
        assertEquals(1, tracker.count)
        assertTrue(tracker.isActive)

        // 1 -> 2 does not re-trigger edge
        assertFalse(tracker.acquire())
        assertEquals(2, tracker.count)
        assertTrue(tracker.isActive)

        // 2 -> 1 keeps ambient paused
        assertFalse(tracker.release())
        assertEquals(1, tracker.count)
        assertTrue(tracker.isActive)

        // 1 -> 0 signals ambient restore edge
        assertTrue(tracker.release())
        assertEquals(0, tracker.count)
        assertFalse(tracker.isActive)

        // Extra release stays clamped at 0
        assertFalse(tracker.release())
        assertEquals(0, tracker.count)

        // Reset clears active previews and reports transition
        assertTrue(tracker.acquire())
        assertFalse(tracker.acquire())
        assertTrue(tracker.reset())
        assertEquals(0, tracker.count)
        assertFalse(tracker.isActive)
        assertFalse(tracker.reset())
    }

    @Test
    fun `videoPreviewTargetGain enforces ringer, external music, KEEP session, screen-off, and foreground etiquette`() {
        // Baseline eligible
        val baseline = AudioLifecycleEvents.videoPreviewTargetGain(
            soundEnabled = true,
            videoPreviewAudio = true,
            sfxVolumePercent = 80,
            activeAppActivityCount = 1,
            screenOff = false,
            hasKeepSession = false,
            respectRinger = true,
            ringerState = AudioPolicy.RingerState.NORMAL,
            respectMusic = true,
            externalMusicActive = false,
        )
        assertTrue(baseline > 0f)

        // Backgrounded, screen off, or active KEEP session mutes cleanly
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                activeAppActivityCount = 0,
            ),
            1e-6f,
        )
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                screenOff = true,
            ),
            1e-6f,
        )
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                hasKeepSession = true,
            ),
            1e-6f,
        )

        // Silent / vibrate ringer suppresses when respectRinger=true, allows when false
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.SILENT,
            ),
            1e-6f,
        )
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.VIBRATE,
            ),
            1e-6f,
        )
        assertEquals(
            baseline,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                respectRinger = false,
                ringerState = AudioPolicy.RingerState.SILENT,
            ),
            1e-6f,
        )

        // Real external music suppresses when respectMusic=true, allows when false
        assertEquals(
            0f,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                respectMusic = true,
                externalMusicActive = true,
            ),
            1e-6f,
        )
        assertEquals(
            baseline,
            AudioLifecycleEvents.videoPreviewTargetGain(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                respectMusic = false,
                externalMusicActive = true,
            ),
            1e-6f,
        )
        assertFalse(
            AudioLifecycleEvents.shouldEnableVideoPreviewAudio(
                soundEnabled = true,
                videoPreviewAudio = true,
                sfxVolumePercent = 80,
                externalMusicActive = true,
            ),
        )

        // Own ambient / own active preview masking for AudioManager.isMusicActive
        assertFalse(
            AudioLifecycleEvents.isExternalMusicActiveForVideoPreview(
                systemMusicActive = true,
                ownAmbientPlaying = true,
                ownVideoPreviewUnmuted = false,
            ),
        )
        assertFalse(
            AudioLifecycleEvents.isExternalMusicActiveForVideoPreview(
                systemMusicActive = true,
                ownAmbientPlaying = false,
                ownVideoPreviewUnmuted = true,
            ),
        )
        assertTrue(
            AudioLifecycleEvents.isExternalMusicActiveForVideoPreview(
                systemMusicActive = true,
                ownAmbientPlaying = false,
                ownVideoPreviewUnmuted = false,
            ),
        )
        assertFalse(
            AudioLifecycleEvents.isExternalMusicActiveForVideoPreview(
                systemMusicActive = false,
                ownAmbientPlaying = false,
                ownVideoPreviewUnmuted = false,
            ),
        )
    }
}
