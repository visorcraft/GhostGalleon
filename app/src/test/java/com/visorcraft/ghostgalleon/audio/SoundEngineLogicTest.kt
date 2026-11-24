package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SoundEngineLogicTest {

    @Test
    fun `resolveEffectivePack honors master enable, explicit packs, AUTO theme mapping, and OFF`() {
        // Master disabled always resolves to OFF regardless of pack/theme
        assertEquals(
            SoundPackId.OFF,
            SoundEngineLogic.resolveEffectivePack(false, SoundPackId.GHOST, ThemePack.NEON.id),
        )
        assertEquals(
            SoundPackId.OFF,
            SoundEngineLogic.resolveEffectivePack(false, SoundPackId.AUTO, ThemePack.THREEDS.id),
        )

        // Explicit OFF stays OFF even when soundEnabled is true
        assertEquals(
            SoundPackId.OFF,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.OFF, ThemePack.GHOST.id),
        )

        // AUTO resolves through ThemePack
        assertEquals(
            SoundPackId.GHOST,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.AUTO, ThemePack.GHOST.id),
        )
        assertEquals(
            SoundPackId.THREEDS,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.AUTO, ThemePack.THREEDS.id),
        )
        assertEquals(
            SoundPackId.OLED,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.AUTO, ThemePack.OLED.id),
        )
        assertEquals(
            SoundPackId.NEON,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.AUTO, ThemePack.NEON.id),
        )
        assertEquals(
            SoundPackId.GHOST,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.AUTO, "unknown-custom-theme"),
        )

        // Explicit concrete pack overrides theme
        assertEquals(
            SoundPackId.NEON,
            SoundEngineLogic.resolveEffectivePack(true, SoundPackId.NEON, ThemePack.OLED.id),
        )
    }

    @Test
    fun `SoundEngineConfig resolvedPack delegates consistently`() {
        val autoNeon = SoundEngineConfig(
            soundEnabled = true,
            soundPackId = SoundPackId.AUTO,
            themePackId = ThemePack.NEON.id,
        )
        assertEquals(SoundPackId.NEON, autoNeon.resolvedPack())
        assertEquals(SoundPackId.OFF, autoNeon.copy(soundEnabled = false).resolvedPack())
        assertEquals(SoundPackId.OFF, autoNeon.copy(soundPackId = SoundPackId.OFF).resolvedPack())
    }

    @Test
    fun `planSync handles cold start, idempotent volume changes, pack switch, and disable`() {
        // Cold start: OFF -> GHOST switches pack
        assertEquals(
            SoundEngineLogic.SyncAction.SWITCH_PACK,
            SoundEngineLogic.planSync(SoundPackId.OFF, SoundPackId.GHOST, hasActiveOrPendingSession = false),
        )
        // Cold start disabled: stays idle NO_OP
        assertEquals(
            SoundEngineLogic.SyncAction.NO_OP,
            SoundEngineLogic.planSync(SoundPackId.OFF, SoundPackId.OFF, hasActiveOrPendingSession = false),
        )
        // Same pack already active or loading (e.g. volume slider change): NO_OP
        assertEquals(
            SoundEngineLogic.SyncAction.NO_OP,
            SoundEngineLogic.planSync(SoundPackId.GHOST, SoundPackId.GHOST, hasActiveOrPendingSession = true),
        )
        // Same targetPack but prior load failed / released (no session): retries SWITCH_PACK
        assertEquals(
            SoundEngineLogic.SyncAction.SWITCH_PACK,
            SoundEngineLogic.planSync(SoundPackId.GHOST, SoundPackId.GHOST, hasActiveOrPendingSession = false),
        )
        // Switching packs: SWITCH_PACK
        assertEquals(
            SoundEngineLogic.SyncAction.SWITCH_PACK,
            SoundEngineLogic.planSync(SoundPackId.GHOST, SoundPackId.NEON, hasActiveOrPendingSession = true),
        )
        // Disabling active pack: RELEASE_TO_IDLE
        assertEquals(
            SoundEngineLogic.SyncAction.RELEASE_TO_IDLE,
            SoundEngineLogic.planSync(SoundPackId.NEON, SoundPackId.OFF, hasActiveOrPendingSession = true),
        )
        // Disabling while session already null but targetPack non-OFF: RELEASE_TO_IDLE
        assertEquals(
            SoundEngineLogic.SyncAction.RELEASE_TO_IDLE,
            SoundEngineLogic.planSync(SoundPackId.NEON, SoundPackId.OFF, hasActiveOrPendingSession = false),
        )
    }

    @Test
    fun `cacheFileName and staleCacheFiles cleanly partition current vs stale files`() {
        assertEquals(
            "ghost_v1_nav_tick.wav",
            SoundEngineLogic.cacheFileName(SoundPackId.GHOST, SoundCue.NAV_TICK, version = 1),
        )
        assertEquals(
            "neon_v3_ra_achievement.wav",
            SoundEngineLogic.cacheFileName(SoundPackId.NEON, SoundCue.RA_ACHIEVEMENT, version = 3),
        )

        // Every concrete pack x cue generates a valid current-version cache file name
        val allCurrent = SoundPackId.CONCRETE_PACKS.flatMap { pack ->
            SoundCue.entries.map { cue ->
                SoundEngineLogic.cacheFileName(pack, cue, version = 2)
            }
        }
        assertEquals(SoundPackId.CONCRETE_PACKS.size * SoundCue.entries.size, allCurrent.size)
        assertTrue(allCurrent.all { SoundEngineLogic.isCurrentCacheFileName(it, currentVersion = 2) })
        assertTrue(SoundEngineLogic.staleCacheFiles(allCurrent, currentVersion = 2).isEmpty())

        // Stale candidates: old version, future version, .tmp leftovers, retired pack, unknown cue, junk
        val mixed = listOf(
            "ghost_v2_confirm.wav",
            "threeds_v2_boot_chime.wav",
            "ghost_v1_confirm.wav",
            "ghost_v3_confirm.wav",
            "ghost_v2_confirm.wav.tmp",
            "auto_v2_confirm.wav",
            "off_v2_confirm.wav",
            "ghost_v2_nonexistent_cue.wav",
            "corrupt_file.txt",
            "_v2_confirm.wav",
        )
        val stale = SoundEngineLogic.staleCacheFiles(mixed, currentVersion = 2)
        assertEquals(
            listOf(
                "ghost_v1_confirm.wav",
                "ghost_v3_confirm.wav",
                "ghost_v2_confirm.wav.tmp",
                "auto_v2_confirm.wav",
                "off_v2_confirm.wav",
                "ghost_v2_nonexistent_cue.wav",
                "corrupt_file.txt",
                "_v2_confirm.wav",
            ),
            stale,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cacheFileName rejects non-concrete AUTO pack`() {
        SoundEngineLogic.cacheFileName(SoundPackId.AUTO, SoundCue.CONFIRM)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cacheFileName rejects OFF pack`() {
        SoundEngineLogic.cacheFileName(SoundPackId.OFF, SoundCue.CONFIRM)
    }

    @Test
    fun `isValidWavHeader accepts valid synthesized WAV and rejects truncated or mismatched headers`() {
        val pcm = PcmSynth.synthesize(SoundCue.NAV_TICK, SoundPackId.GHOST)
        val wav = WavEncoder.encodePcm16(pcm, sampleRate = PcmSynth.DEFAULT_SAMPLE_RATE, channels = 1)
        val header = wav.copyOfRange(0, WavEncoder.HEADER_BYTES)

        assertTrue(SoundEngineLogic.isValidWavHeader(header, wav.size.toLong()))

        // Truncated file on disk (length mismatch)
        assertFalse(SoundEngineLogic.isValidWavHeader(header, (wav.size - 2).toLong()))
        // Header-only zero-data WAV
        val emptyWav = WavEncoder.encodePcm16(ShortArray(0), sampleRate = PcmSynth.DEFAULT_SAMPLE_RATE, channels = 1)
        assertFalse(SoundEngineLogic.isValidWavHeader(emptyWav, emptyWav.size.toLong()))
        // Wrong sample rate
        val wrongRate = WavEncoder.encodePcm16(pcm, sampleRate = 22050, channels = 1)
        assertFalse(
            SoundEngineLogic.isValidWavHeader(
                wrongRate.copyOfRange(0, WavEncoder.HEADER_BYTES),
                wrongRate.size.toLong(),
            ),
        )
        // Wrong channel count (stereo when mono expected)
        val stereoPcm = ShortArray(pcm.size * 2)
        val stereoWav = WavEncoder.encodePcm16(stereoPcm, sampleRate = PcmSynth.DEFAULT_SAMPLE_RATE, channels = 2)
        assertFalse(
            SoundEngineLogic.isValidWavHeader(
                stereoWav.copyOfRange(0, WavEncoder.HEADER_BYTES),
                stereoWav.size.toLong(),
            ),
        )
        // Corrupt header magic
        val corrupt = header.copyOf()
        corrupt[0] = 'X'.code.toByte()
        assertFalse(SoundEngineLogic.isValidWavHeader(corrupt, wav.size.toLong()))

        // Odd dataBytes (not 16-bit PCM frame aligned) rejected even if file length matches
        val oddHeader = header.copyOf()
        val oddDataBytes = 101
        oddHeader[40] = (oddDataBytes and 0xFF).toByte()
        oddHeader[41] = ((oddDataBytes ushr 8) and 0xFF).toByte()
        oddHeader[42] = 0
        oddHeader[43] = 0
        assertFalse(
            SoundEngineLogic.isValidWavHeader(
                oddHeader,
                (WavEncoder.HEADER_BYTES + oddDataBytes).toLong(),
            ),
        )
    }

    @Test
    fun `GenerationGate advances monotonically and invalidates stale generations`() {
        val gate = SoundEngineLogic.GenerationGate()
        assertEquals(0L, gate.current())
        assertTrue(gate.isCurrent(0L))

        val g1 = gate.advance()
        val g2 = gate.advance()
        val g3 = gate.advance()
        assertEquals(1L, g1)
        assertEquals(2L, g2)
        assertEquals(3L, g3)
        assertFalse(gate.isCurrent(0L))
        assertFalse(gate.isCurrent(g1))
        assertFalse(gate.isCurrent(g2))
        assertTrue(gate.isCurrent(g3))
    }

    @Test
    fun `LOAD_ORDER covers every SoundCue exactly once with interactive cues prioritized`() {
        assertEquals(SoundCue.entries.size, SoundEngineLogic.LOAD_ORDER.size)
        assertEquals(SoundCue.entries.toSet(), SoundEngineLogic.LOAD_ORDER.toSet())
        // Core boot & interactive cues load first
        val firstFour = SoundEngineLogic.LOAD_ORDER.take(4)
        assertTrue(SoundCue.BOOT_CHIME in firstFour)
        assertTrue(SoundCue.NAV_TICK in firstFour)
        assertTrue(SoundCue.CONFIRM in firstFour)
        assertTrue(SoundCue.BACK in firstFour)
    }

    @Test
    fun `PREVIEW_MOTIF is a crisp 2-note NAV_TICK to CONFIRM sequence spaced 110ms`() {
        assertEquals(2, SoundEngineLogic.PREVIEW_MOTIF.size)
        val first = SoundEngineLogic.PREVIEW_MOTIF[0]
        val second = SoundEngineLogic.PREVIEW_MOTIF[1]
        assertEquals(SoundCue.NAV_TICK, first.cue)
        assertEquals(0L, first.delayMs)
        assertEquals(SoundCue.CONFIRM, second.cue)
        assertEquals(SoundEngineLogic.PREVIEW_SECOND_NOTE_DELAY_MS, second.delayMs)
        assertTrue("Second note should trail first with gentle opposite pan", first.pan < 0f && second.pan > 0f)
    }

    @Test
    fun `computePlayParams applies perceptual gain, repeat expression, ducking, panning, and safe clamps`() {
        // Zero volume -> null (inaudible)
        assertNull(SoundEngineLogic.computePlayParams(SoundCue.NAV_TICK, sfxVolumePercent = 0))
        // Zero volume multiplier -> null
        assertNull(SoundEngineLogic.computePlayParams(SoundCue.NAV_TICK, sfxVolumePercent = 80, volumeMul = 0f))

        // Center unity playback at 100%
        val full = SoundEngineLogic.computePlayParams(SoundCue.CONFIRM, sfxVolumePercent = 100)
        assertNotNull(full)
        assertEquals(full!!.leftGain, full.rightGain, 1e-6f)
        assertEquals(1.0f, sqrt(full.leftGain * full.leftGain + full.rightGain * full.rightGain), 1e-4f)
        assertEquals(1.0f, full.rate, 1e-6f)
        assertEquals(SoundCue.CONFIRM.priority, full.priority)

        // Repeat streak softens gain and nudges pitch gently within musical bounds
        val streak = SoundEngineLogic.computePlayParams(
            cue = SoundCue.NAV_TICK,
            sfxVolumePercent = 80,
            repeatStreak = 12,
        )!!
        val fresh = SoundEngineLogic.computePlayParams(
            cue = SoundCue.NAV_TICK,
            sfxVolumePercent = 80,
            repeatStreak = 0,
        )!!
        assertTrue(streak.leftGain < fresh.leftGain)
        assertTrue(streak.rate in AudioPolicy.REPEAT_MIN_PITCH..AudioPolicy.REPEAT_MAX_PITCH)

        // External music + KEEP session politeness ducking
        val duckedMusic = SoundEngineLogic.computePlayParams(
            cue = SoundCue.NAV_TICK,
            sfxVolumePercent = 80,
            respectMusic = true,
            externalMusicActive = true,
        )!!
        assertEquals(
            fresh.leftGain * AudioPolicy.DUCK_EXTERNAL_MUSIC_UI,
            duckedMusic.leftGain,
            1e-4f,
        )

        val duckedHud = SoundEngineLogic.computePlayParams(
            cue = SoundCue.CONFIRM,
            sfxVolumePercent = 80,
            hasKeepSession = true,
            playHudQuiet = true,
        )!!
        val freshConfirm = SoundEngineLogic.computePlayParams(SoundCue.CONFIRM, sfxVolumePercent = 80)!!
        assertEquals(
            freshConfirm.leftGain * AudioPolicy.DUCK_PLAY_HUD_QUIET_UI,
            duckedHud.leftGain,
            1e-4f,
        )

        // SoundPool rate bounds clamp [0.5f, 2.0f]
        val verySlow = SoundEngineLogic.computePlayParams(SoundCue.NAV_TICK, 80, pitchMul = 0.1f)!!
        val veryFast = SoundEngineLogic.computePlayParams(SoundCue.NAV_TICK, 80, pitchMul = 9.0f)!!
        assertEquals(SoundEngineLogic.MIN_PLAYBACK_RATE, verySlow.rate, 1e-6f)
        assertEquals(SoundEngineLogic.MAX_PLAYBACK_RATE, veryFast.rate, 1e-6f)

        // Non-finite inputs (NaN / Infinity) recover safely without poisoning gains or rate
        val nonFinite = SoundEngineLogic.computePlayParams(
            cue = SoundCue.NAV_TICK,
            sfxVolumePercent = 80,
            pan = Float.NaN,
            pitchMul = Float.POSITIVE_INFINITY,
            volumeMul = Float.NaN,
        )!!
        assertTrue(nonFinite.leftGain.isFinite() && nonFinite.leftGain in 0f..1f)
        assertTrue(nonFinite.rightGain.isFinite() && nonFinite.rightGain in 0f..1f)
        assertEquals(nonFinite.leftGain, nonFinite.rightGain, 1e-6f)
        assertEquals(1.0f, nonFinite.rate, 1e-6f)
    }

    @Test
    fun `computeAuditionParams ignores KEEP play-hud quiet ducking while still respecting music ducking`() {
        val auditionNormal = SoundEngineLogic.computeAuditionParams(
            cue = SoundCue.CONFIRM,
            sfxVolumePercent = 80,
            respectMusic = true,
            externalMusicActive = false,
        )!!
        val auditionMusic = SoundEngineLogic.computeAuditionParams(
            cue = SoundCue.CONFIRM,
            sfxVolumePercent = 80,
            respectMusic = true,
            externalMusicActive = true,
        )!!
        assertEquals(
            auditionNormal.leftGain * AudioPolicy.DUCK_EXTERNAL_MUSIC_UI,
            auditionMusic.leftGain,
            1e-4f,
        )
        assertEquals(1.0f, auditionNormal.rate, 1e-6f)
    }

    @Test
    fun `shouldAudition enforces master enable, playable pack, volume, screen, foreground, and ringer`() {
        assertTrue(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.NEON,
                sfxVolumePercent = 70,
                screenOff = false,
                backgrounded = false,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.NORMAL,
            ),
        )
        assertFalse(
            SoundEngineLogic.shouldAudition(
                soundEnabled = false,
                targetPack = SoundPackId.NEON,
                sfxVolumePercent = 70,
                screenOff = false,
                backgrounded = false,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.NORMAL,
            ),
        )
        assertFalse(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.OFF,
                sfxVolumePercent = 70,
                screenOff = false,
                backgrounded = false,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.NORMAL,
            ),
        )
        assertFalse(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.GHOST,
                sfxVolumePercent = 0,
                screenOff = false,
                backgrounded = false,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.NORMAL,
            ),
        )
        assertFalse(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.GHOST,
                sfxVolumePercent = 70,
                screenOff = true,
                backgrounded = false,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.NORMAL,
            ),
        )
        assertFalse(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.GHOST,
                sfxVolumePercent = 70,
                screenOff = false,
                backgrounded = true,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.NORMAL,
            ),
        )
        assertFalse(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.GHOST,
                sfxVolumePercent = 70,
                screenOff = false,
                backgrounded = false,
                respectRinger = true,
                ringerState = AudioPolicy.RingerState.SILENT,
            ),
        )
        assertTrue(
            SoundEngineLogic.shouldAudition(
                soundEnabled = true,
                targetPack = SoundPackId.GHOST,
                sfxVolumePercent = 70,
                screenOff = false,
                backgrounded = false,
                respectRinger = false,
                ringerState = AudioPolicy.RingerState.SILENT,
            ),
        )
    }

    @Test
    fun `mapRingerMode and shouldRefreshSystemProbe handle edge cases safely`() {
        assertEquals(
            AudioPolicy.RingerState.SILENT,
            SoundEngineLogic.mapRingerMode(SoundEngineLogic.ANDROID_RINGER_MODE_SILENT),
        )
        assertEquals(
            AudioPolicy.RingerState.VIBRATE,
            SoundEngineLogic.mapRingerMode(SoundEngineLogic.ANDROID_RINGER_MODE_VIBRATE),
        )
        assertEquals(
            AudioPolicy.RingerState.NORMAL,
            SoundEngineLogic.mapRingerMode(SoundEngineLogic.ANDROID_RINGER_MODE_NORMAL),
        )
        assertEquals(AudioPolicy.RingerState.NORMAL, SoundEngineLogic.mapRingerMode(-99))
        assertEquals(AudioPolicy.RingerState.NORMAL, SoundEngineLogic.mapRingerMode(99))

        assertTrue(SoundEngineLogic.shouldRefreshSystemProbe(Long.MIN_VALUE, 1000L, ttlMs = 250L))
        assertFalse(SoundEngineLogic.shouldRefreshSystemProbe(1000L, 1249L, ttlMs = 250L))
        assertTrue(SoundEngineLogic.shouldRefreshSystemProbe(1000L, 1250L, ttlMs = 250L))
        // Clock jump backwards refreshes safely
        assertTrue(SoundEngineLogic.shouldRefreshSystemProbe(1000L, 900L, ttlMs = 250L))
    }
}
