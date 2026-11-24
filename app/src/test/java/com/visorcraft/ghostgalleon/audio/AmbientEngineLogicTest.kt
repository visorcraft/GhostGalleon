package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [AmbientEngineLogic] state transitions, focus etiquette,
 * KEEP session / screen-off / external music / video preview gating, and pack switching.
 */
class AmbientEngineLogicTest {

    private fun baseConfig(
        enabled: Boolean = true,
        soundPackId: SoundPackId = SoundPackId.AUTO,
        themePackId: String = "ghost",
        volumePercent: Int = 40,
        respectMusic: Boolean = true,
        respectRinger: Boolean = true,
        soundEnabled: Boolean = true,
    ) = AmbientConfig(
        enabled = enabled,
        soundPackId = soundPackId,
        themePackId = themePackId,
        volumePercent = volumePercent,
        respectMusic = respectMusic,
        respectRinger = respectRinger,
        soundEnabled = soundEnabled,
    )

    private fun foregroundState(
        activeAppActivityCount: Int = 1,
        hasKeepSession: Boolean = false,
        screenOff: Boolean = false,
        videoPreviewUnmuted: Boolean = false,
        externalMusicActive: Boolean = false,
        ringerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL,
    ) = AmbientRuntimeState(
        activeAppActivityCount = activeAppActivityCount,
        hasKeepSession = hasKeepSession,
        screenOff = screenOff,
        videoPreviewUnmuted = videoPreviewUnmuted,
        externalMusicActive = externalMusicActive,
        ringerState = ringerState,
    )

    @Test
    fun `resolveEffectivePack and mapRingerMode map themes and Android ringer constants cleanly`() {
        assertEquals(SoundPackId.GHOST, AmbientEngineLogic.resolveEffectivePack(baseConfig(themePackId = "ghost")))
        assertEquals(SoundPackId.THREEDS, AmbientEngineLogic.resolveEffectivePack(baseConfig(themePackId = "threeds")))
        assertEquals(SoundPackId.OLED, AmbientEngineLogic.resolveEffectivePack(baseConfig(themePackId = "oled")))
        assertEquals(SoundPackId.NEON, AmbientEngineLogic.resolveEffectivePack(baseConfig(themePackId = "neon")))
        assertEquals(
            SoundPackId.NEON,
            AmbientEngineLogic.resolveEffectivePack(baseConfig(soundPackId = SoundPackId.NEON, themePackId = "oled")),
        )
        assertEquals(
            SoundPackId.OFF,
            AmbientEngineLogic.resolveEffectivePack(baseConfig(soundPackId = SoundPackId.OFF, themePackId = "ghost")),
        )

        assertEquals(AudioPolicy.RingerState.SILENT, AmbientEngineLogic.mapRingerMode(AmbientEngineLogic.ANDROID_RINGER_MODE_SILENT))
        assertEquals(AudioPolicy.RingerState.VIBRATE, AmbientEngineLogic.mapRingerMode(AmbientEngineLogic.ANDROID_RINGER_MODE_VIBRATE))
        assertEquals(AudioPolicy.RingerState.NORMAL, AmbientEngineLogic.mapRingerMode(AmbientEngineLogic.ANDROID_RINGER_MODE_NORMAL))
        assertEquals(AudioPolicy.RingerState.NORMAL, AmbientEngineLogic.mapRingerMode(99))
    }

    @Test
    fun `blocked conditions never start playback and never request audio focus`() {
        val idle = AmbientEngineLogic.PlaybackSnapshot()
        val blockedCases = listOf(
            "ambient disabled" to (baseConfig(enabled = false) to foregroundState()),
            "master sound disabled" to (baseConfig(soundEnabled = false) to foregroundState()),
            "pack OFF" to (baseConfig(soundPackId = SoundPackId.OFF) to foregroundState()),
            "volume 0" to (baseConfig(volumePercent = 0) to foregroundState()),
            "backgrounded" to (baseConfig() to foregroundState(activeAppActivityCount = 0)),
            "KEEP game session" to (baseConfig() to foregroundState(hasKeepSession = true)),
            "screen off" to (baseConfig() to foregroundState(screenOff = true)),
            "external music respected" to (baseConfig(respectMusic = true) to foregroundState(externalMusicActive = true)),
            "ringer silent respected" to (baseConfig(respectRinger = true) to foregroundState(ringerState = AudioPolicy.RingerState.SILENT)),
            "ringer vibrate respected" to (baseConfig(respectRinger = true) to foregroundState(ringerState = AudioPolicy.RingerState.VIBRATE)),
            "unmuted video preview" to (baseConfig() to foregroundState(videoPreviewUnmuted = true)),
        )

        for ((label, pair) in blockedCases) {
            val (cfg, state) = pair
            val plan = AmbientEngineLogic.planTransition(cfg, state, idle)
            assertEquals("$label must stay paused", AmbientEngineLogic.Action.FADE_OUT_PAUSE, plan.action)
            assertFalse("$label must NEVER request audio focus", plan.shouldRequestFocus)
            assertEquals("$label target gain must be 0", 0f, plan.targetVolumeGain, 0f)
        }
    }

    @Test
    fun `eligible idle state plans START with focus request and respects opt-out flags`() {
        val idle = AmbientEngineLogic.PlaybackSnapshot()
        val plan = AmbientEngineLogic.planTransition(baseConfig(themePackId = "neon", volumePercent = 50), foregroundState(), idle)
        assertEquals(AmbientEngineLogic.Action.START, plan.action)
        assertEquals(SoundPackId.NEON, plan.resolvedPack)
        assertEquals(AudioPolicy.volumePercentToGain(50), plan.targetVolumeGain, 1e-6f)
        assertTrue(plan.shouldRequestFocus)
        assertFalse(plan.shouldAbandonFocus)

        // Opting out of respectMusic & respectRinger allows START even with external music and silent ringer
        val optOutPlan = AmbientEngineLogic.planTransition(
            baseConfig(respectMusic = false, respectRinger = false),
            foregroundState(externalMusicActive = true, ringerState = AudioPolicy.RingerState.SILENT),
            idle,
        )
        assertEquals(AmbientEngineLogic.Action.START, optOutPlan.action)
        assertTrue(optOutPlan.shouldRequestFocus)
    }

    @Test
    fun `active playback handles KEEP_PLAYING, UPDATE_GAIN, SWITCH_PACK, and FADE_OUT_PAUSE cleanly`() {
        val gain40 = AudioPolicy.volumePercentToGain(40)
        val playingGhost = AmbientEngineLogic.PlaybackSnapshot(
            isPlaying = true,
            activePack = SoundPackId.GHOST,
            currentVolumeGain = gain40,
            focusState = AmbientEngineLogic.FocusState.GRANTED,
            videoDucked = false,
            appliedDucked = false,
        )

        // Unchanged -> KEEP_PLAYING
        val keep = AmbientEngineLogic.planTransition(baseConfig(volumePercent = 40), foregroundState(), playingGhost)
        assertEquals(AmbientEngineLogic.Action.KEEP_PLAYING, keep.action)
        assertFalse(keep.shouldRequestFocus)
        assertFalse(keep.shouldAbandonFocus)

        // Volume slider moved -> UPDATE_GAIN
        val volChange = AmbientEngineLogic.planTransition(baseConfig(volumePercent = 70), foregroundState(), playingGhost)
        assertEquals(AmbientEngineLogic.Action.UPDATE_GAIN, volChange.action)
        assertEquals(AudioPolicy.volumePercentToGain(70), volChange.targetVolumeGain, 1e-6f)

        // Video duck hint toggled -> UPDATE_GAIN with shouldBeDucked = true
        val videoDuck = AmbientEngineLogic.planTransition(
            baseConfig(volumePercent = 40),
            foregroundState(),
            playingGhost.copy(videoDucked = true),
        )
        assertEquals(AmbientEngineLogic.Action.UPDATE_GAIN, videoDuck.action)
        assertTrue(videoDuck.shouldBeDucked)

        // Theme switched while pack is AUTO -> SWITCH_PACK
        val switchTheme = AmbientEngineLogic.planTransition(
            baseConfig(soundPackId = SoundPackId.AUTO, themePackId = "threeds", volumePercent = 40),
            foregroundState(),
            playingGhost,
        )
        assertEquals(AmbientEngineLogic.Action.SWITCH_PACK, switchTheme.action)
        assertEquals(SoundPackId.THREEDS, switchTheme.resolvedPack)
        assertFalse(switchTheme.shouldAbandonFocus)

        // KEEP session started mid-playback -> FADE_OUT_PAUSE and abandon focus
        val keepGame = AmbientEngineLogic.planTransition(
            baseConfig(volumePercent = 40),
            foregroundState(hasKeepSession = true),
            playingGhost,
        )
        assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, keepGame.action)
        assertTrue(keepGame.shouldAbandonFocus)

        // Screen off mid-playback -> FADE_OUT_PAUSE and abandon focus
        val screenOff = AmbientEngineLogic.planTransition(
            baseConfig(volumePercent = 40),
            foregroundState(screenOff = true),
            playingGhost,
        )
        assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, screenOff.action)
        assertTrue(screenOff.shouldAbandonFocus)
    }

    @Test
    fun `audio focus transitions duck smoothly, hold listener on transient loss, and never re-steal on permanent loss`() {
        val gain40 = AudioPolicy.volumePercentToGain(40)
        val playing = AmbientEngineLogic.PlaybackSnapshot(
            isPlaying = true,
            activePack = SoundPackId.GHOST,
            currentVolumeGain = gain40,
            focusState = AmbientEngineLogic.FocusState.GRANTED,
        )

        // 1. AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> stay playing, smoothly duck
        val duckedPlan = AmbientEngineLogic.planTransition(
            baseConfig(),
            foregroundState(),
            playing.copy(focusState = AmbientEngineLogic.FocusState.DUCKED),
        )
        assertEquals(AmbientEngineLogic.Action.UPDATE_GAIN, duckedPlan.action)
        assertTrue(duckedPlan.shouldBeDucked)
        assertFalse(duckedPlan.shouldAbandonFocus)

        // 2. AUDIOFOCUS_LOSS_TRANSIENT -> fade out & pause WITHOUT abandoning listener
        val transientLoss = AmbientEngineLogic.planTransition(
            baseConfig(),
            foregroundState(),
            playing.copy(focusState = AmbientEngineLogic.FocusState.LOST_TRANSIENT),
        )
        assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, transientLoss.action)
        assertFalse(transientLoss.shouldAbandonFocus)

        // While paused in LOST_TRANSIENT, do NOT re-request focus
        val waitingTransient = AmbientEngineLogic.planTransition(
            baseConfig(),
            foregroundState(),
            AmbientEngineLogic.PlaybackSnapshot(
                isPlaying = false,
                focusState = AmbientEngineLogic.FocusState.LOST_TRANSIENT,
            ),
        )
        assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, waitingTransient.action)
        assertFalse(waitingTransient.shouldRequestFocus)
        assertFalse(waitingTransient.shouldAbandonFocus)

        // When AUDIOFOCUS_GAIN restores GRANTED, resume cleanly WITHOUT redundant requestAudioFocus
        val resumed = AmbientEngineLogic.planTransition(
            baseConfig(),
            foregroundState(),
            AmbientEngineLogic.PlaybackSnapshot(
                isPlaying = false,
                focusState = AmbientEngineLogic.FocusState.GRANTED,
            ),
        )
        assertEquals(AmbientEngineLogic.Action.START, resumed.action)
        assertFalse(resumed.shouldRequestFocus)

        // 3. AUDIOFOCUS_LOSS (permanent) -> fade out, abandon focus, and never re-steal while LOST_PERMANENT
        val permanentLoss = AmbientEngineLogic.planTransition(
            baseConfig(),
            foregroundState(),
            playing.copy(focusState = AmbientEngineLogic.FocusState.LOST_PERMANENT),
        )
        assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, permanentLoss.action)
        assertTrue(permanentLoss.shouldAbandonFocus)

        val pausedPermanent = AmbientEngineLogic.planTransition(
            baseConfig(),
            foregroundState(),
            AmbientEngineLogic.PlaybackSnapshot(
                isPlaying = false,
                focusState = AmbientEngineLogic.FocusState.LOST_PERMANENT,
            ),
        )
        assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, pausedPermanent.action)
        assertFalse(pausedPermanent.shouldRequestFocus)
    }
}
