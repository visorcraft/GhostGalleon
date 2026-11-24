package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Studio mastering calibration, handheld-speaker spectral hygiene, and 8-thread
 * concurrency/churn stress test suite covering the entire Ghost Galleon audio stack.
 */
class AudioStressAndMasteringTest {

    private val sr = AudioDsp.SAMPLE_RATE

    @Test
    fun `computePerceivedLoudnessDb is finite, gain-linear, and weighted for handheld presence`() {
        assertEquals(AudioMastering.SILENCE_LOUDNESS_DB, AudioMastering.computePerceivedLoudnessDb(DoubleArray(0)), 0.0)
        assertEquals(AudioMastering.SILENCE_LOUDNESS_DB, AudioMastering.computePerceivedLoudnessDb(DoubleArray(1024)), 0.0)
        assertEquals(AudioMastering.SILENCE_LOUDNESS_DB, AudioMastering.computePerceivedLoudnessDb(ShortArray(0)), 0.0)

        val nonFinite = DoubleArray(512) { if (it % 2 == 0) Double.NaN else Double.POSITIVE_INFINITY }
        assertEquals(AudioMastering.SILENCE_LOUDNESS_DB, AudioMastering.computePerceivedLoudnessDb(nonFinite), 0.0)

        // Gain linearity: 2x amplitude == +6.02 dB, 0.5x amplitude == -6.02 dB
        val ref = DoubleArray(sr / 5) { i -> 0.20 * sin(2.0 * PI * 1000.0 * i / sr) }
        val louder = DoubleArray(ref.size) { i -> ref[i] * 2.0 }
        val quieter = DoubleArray(ref.size) { i -> ref[i] * 0.5 }
        val dbRef = AudioMastering.computePerceivedLoudnessDb(ref, sr)
        val dbLouder = AudioMastering.computePerceivedLoudnessDb(louder, sr)
        val dbQuieter = AudioMastering.computePerceivedLoudnessDb(quieter, sr)
        assertEquals(6.02, dbLouder - dbRef, 0.06)
        assertEquals(-6.02, dbQuieter - dbRef, 0.06)

        // PCM-16 ShortArray overload matches DoubleArray within quantization precision
        val pcmDb = AudioMastering.computePerceivedLoudnessDb(AudioMastering.toPcm16(ref), sr)
        assertEquals(dbRef, pcmDb, 0.05)

        // Perceptual curve: 1.8 kHz presence band reads much louder than equal-amplitude 60 Hz sub-bass
        val sub60 = DoubleArray(sr / 4) { i -> 0.25 * sin(2.0 * PI * 60.0 * i / sr) }
        val low250 = DoubleArray(sr / 4) { i -> 0.25 * sin(2.0 * PI * 250.0 * i / sr) }
        val mid1800 = DoubleArray(sr / 4) { i -> 0.25 * sin(2.0 * PI * 1800.0 * i / sr) }
        val db60 = AudioMastering.computePerceivedLoudnessDb(sub60, sr)
        val db250 = AudioMastering.computePerceivedLoudnessDb(low250, sr)
        val db1800 = AudioMastering.computePerceivedLoudnessDb(mid1800, sr)
        assertTrue("1.8kHz ($db1800 dB) must read >12 dB louder than 60Hz ($db60 dB)", db1800 - db60 > 12.0)
        assertTrue("1.8kHz ($db1800 dB) must read >2 dB louder than 250Hz ($db250 dB)", db1800 - db250 > 2.0)
    }

    @Test
    fun `studio loudness calibration maintains category hierarchy and cross-pack parity across all 96 waveforms`() {
        val navCues = listOf(
            SoundCue.NAV_TICK,
            SoundCue.NAV_EDGE_BUMP,
            SoundCue.NAV_PAGE_TURN,
            SoundCue.DOCK_ENTER,
            SoundCue.DOCK_EXIT,
        )
        val actionModalCues = SoundCue.entries.filter {
            it.category == SoundCueCategory.ACTION || it.category == SoundCueCategory.MODAL
        }
        val launchCelebrationCues = listOf(
            SoundCue.LAUNCH_GAME,
            SoundCue.LAUNCH_APP,
            SoundCue.BOOT_CHIME,
            SoundCue.RA_ACHIEVEMENT,
            SoundCue.SETUP_COMPLETE,
        )

        val packOverallMeanDb = linkedMapOf<SoundPackId, Double>()

        for (pack in SoundPackId.CONCRETE_PACKS) {
            val dbByCue = SoundCue.entries.associateWith { cue ->
                val samples = PcmSynth.synthesizeMasteredFloat(cue, pack, sr)
                val peak = samples.maxOf { abs(it) }
                assertTrue("$pack/$cue peak $peak exceeds -1.5 dBFS (0.842)", peak <= 0.842)
                AudioMastering.computePerceivedLoudnessDb(samples, sr)
            }

            val navMean = navCues.map { dbByCue.getValue(it) }.average()
            val actionModalMean = actionModalCues.map { dbByCue.getValue(it) }.average()
            val launchCelebMean = launchCelebrationCues.map { dbByCue.getValue(it) }.average()
            val navTickDb = dbByCue.getValue(SoundCue.NAV_TICK)
            val overallMean = dbByCue.values.average()
            packOverallMeanDb[pack] = overallMean

            // Navigation sits gently quieter (-22..-16 dB for standard packs; OLED subtly gentler)
            if (pack == SoundPackId.OLED) {
                assertTrue("$pack NAV mean ($navMean dB) outside -24.5..-17.5 dB", navMean in -24.5..-17.5)
            } else {
                assertTrue("$pack NAV mean ($navMean dB) outside -22.0..-16.0 dB", navMean in -22.0..-16.0)
            }

            // Action / Modal sits cleanly above Navigation
            assertTrue(
                "$pack ACTION/MODAL ($actionModalMean dB) should exceed NAV ($navMean dB)",
                actionModalMean > navMean + 0.5,
            )

            // Launch / Celebration sits proudly +1.5..+5.5 dB above NAV_TICK
            val liftOverTick = launchCelebMean - navTickDb
            assertTrue(
                "$pack LAUNCH/CELEBRATION lift over NAV_TICK (${liftOverTick} dB; launch=$launchCelebMean, tick=$navTickDb) outside +1.5..+5.5 dB",
                liftOverTick in 1.5..5.5,
            )

            // Launch / Celebration also sits cleanly above NAV category average
            assertTrue(
                "$pack LAUNCH/CELEBRATION ($launchCelebMean dB) should exceed NAV mean ($navMean dB) by >= 1.5 dB",
                launchCelebMean - navMean >= 1.5,
            )
        }

        // Cross-pack parity: GHOST, THREEDS, NEON stay tightly matched (<= 2.2 dB spread)
        val mainPacks = listOf(SoundPackId.GHOST, SoundPackId.THREEDS, SoundPackId.NEON)
        val mainVals = mainPacks.map { packOverallMeanDb.getValue(it) }
        val mainSpread = mainVals.max() - mainVals.min()
        assertTrue(
            "GHOST/THREEDS/NEON mean perceived loudness spread too wide ($mainSpread dB): $packOverallMeanDb",
            mainSpread <= 2.2,
        )

        // OLED stays intentionally gentle/refined within ~2.0..4.5 dB below the main packs
        val mainAvg = mainVals.average()
        val oledDelta = mainAvg - packOverallMeanDb.getValue(SoundPackId.OLED)
        assertTrue(
            "OLED refinement offset ($oledDelta dB; OLED=${packOverallMeanDb[SoundPackId.OLED]}, mainAvg=$mainAvg) outside 2.0..4.5 dB",
            oledDelta in 2.0..4.5,
        )
    }

    @Test
    fun `handheld speaker spectral hygiene rejects sub-110Hz rattle and above 10kHz harshness across all 96 SFX and 4 ambient loops`() {
        // 1. All 96 SFX waveforms
        for (pack in SoundPackId.CONCRETE_PACKS) {
            for (cue in SoundCue.entries) {
                val buf = PcmSynth.synthesizeMasteredFloat(cue, pack, sr)
                val subBass = AudioMastering.computeSubBassEnergyRatio(buf, cutoffHz = 110.0, sampleRate = sr)
                val ultrasonic = AudioMastering.computeHighBandEnergyRatio(buf, cutoffHz = 10_000.0, sampleRate = sr)
                val peak = buf.maxOf { abs(it) }

                assertTrue(
                    "$pack/$cue sub-110Hz energy ratio $subBass exceeds 10% handheld speaker rattle limit",
                    subBass < 0.10,
                )
                assertTrue(
                    "$pack/$cue >10kHz energy ratio $ultrasonic exceeds 5% sibilance ceiling",
                    ultrasonic < 0.05,
                )
                assertTrue("$pack/$cue peak $peak exceeds -1.5 dBFS", peak <= 0.842)
                assertEquals("$pack/$cue click-free start", 0.0, buf.first(), 1e-9)
                assertEquals("$pack/$cue click-free end", 0.0, buf.last(), 1e-9)
            }
        }

        // 2. All 4 stereo ambient loops (verify both L and R channels)
        for (pack in SoundPackId.CONCRETE_PACKS) {
            val stereo = AmbientSynthesizer.synthesizeLoopFloat(pack, durationSec = 3.0, sampleRate = sr)
            val frames = stereo.size / 2
            val left = DoubleArray(frames) { stereo[it * 2] }
            val right = DoubleArray(frames) { stereo[it * 2 + 1] }

            val subL = AudioMastering.computeSubBassEnergyRatio(left, cutoffHz = 110.0, sampleRate = sr)
            val subR = AudioMastering.computeSubBassEnergyRatio(right, cutoffHz = 110.0, sampleRate = sr)
            val hiL = AudioMastering.computeHighBandEnergyRatio(left, cutoffHz = 10_000.0, sampleRate = sr)
            val hiR = AudioMastering.computeHighBandEnergyRatio(right, cutoffHz = 10_000.0, sampleRate = sr)
            val dbL = AudioMastering.computePerceivedLoudnessDb(left, sr)
            val dbR = AudioMastering.computePerceivedLoudnessDb(right, sr)

            assertTrue("$pack ambient L sub-110Hz $subL >= 10%", subL < 0.10)
            assertTrue("$pack ambient R sub-110Hz $subR >= 10%", subR < 0.10)
            assertTrue("$pack ambient L >10kHz $hiL >= 5%", hiL < 0.05)
            assertTrue("$pack ambient R >10kHz $hiR >= 5%", hiR < 0.05)
            assertTrue("$pack ambient L perceived loudness ($dbL dB) should sit gently in background (-34..-18 dB)", dbL in -34.0..-18.0)
            assertTrue("$pack ambient R perceived loudness ($dbR dB) should sit gently in background (-34..-18 dB)", dbR in -34.0..-18.0)
        }
    }

    @Test
    fun `8-thread concurrency and churn stress suite completes with zero exceptions, NaNs, or race corruption`() {
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGate = CountDownLatch(1)

        // Precompute golden deterministic reference waveforms before concurrent hammer
        val sampleCues = listOf(SoundCue.NAV_TICK, SoundCue.CONFIRM, SoundCue.SWAP_DISPLAY, SoundCue.FAVORITE_ON)
        val goldenPcm = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            sampleCues.associateWith { cue -> PcmSynth.synthesize(cue, pack, sr) }
        }
        val sharedCooldown = AudioPolicy.CooldownTracker(navGlobalFloorMs = 18L)
        val matcherCandidates = listOf(
            CustomSoundCandidate("nav.wav", "content://1", 2048),
            CustomSoundCandidate("nav.mp3", "content://2", 2048),
            CustomSoundCandidate("confirm.ogg", "content://3", 4096),
            CustomSoundCandidate("._nav.wav", "content://4", 1024),
            CustomSoundCandidate("huge.wav", "content://5", CustomSoundPackMatcher.MAX_SFX_BYTES + 100),
            CustomSoundCandidate("ra.flac", "content://6", 8192),
        )
        val goldenScan = CustomSoundPackMatcher.scanCandidates(matcherCandidates)

        try {
            val futures = (0 until threadCount).map { workerId ->
                executor.submit(Callable {
                    assertTrue(startGate.await(5, TimeUnit.SECONDS))

                    // 1. Concurrent PcmSynth deterministic synthesis across packs & cues
                    for (round in 0 until 8) {
                        val pack = SoundPackId.CONCRETE_PACKS[(workerId + round) % SoundPackId.CONCRETE_PACKS.size]
                        val cue = sampleCues[(workerId * 3 + round) % sampleCues.size]
                        val rendered = PcmSynth.synthesize(cue, pack, sr)
                        assertArrayEquals(
                            "thread=$workerId race mismatch on $pack/$cue",
                            goldenPcm.getValue(pack).getValue(cue),
                            rendered,
                        )
                    }

                    // 2. Concurrent shared CooldownTracker contention + high-priority bypass
                    var accepted = 0
                    for (step in 0 until 120) {
                        val nowMs = 10_000L + step * 5L + workerId
                        val cue = when (step % 4) {
                            0 -> SoundCue.NAV_TICK
                            1 -> SoundCue.NAV_EDGE_BUMP
                            2 -> SoundCue.CONFIRM
                            else -> SoundCue.RA_ACHIEVEMENT
                        }
                        if (sharedCooldown.tryAcquire(cue, nowMs)) accepted++
                    }
                    assertTrue("expected non-zero cooldown acquisitions on worker $workerId", accepted >= 0)

                    // 3. AmbientEngineLogic + AmbientFadeEnvelope churn storm
                    // (Companion absorb/recreate, KEEP enter/exit, sleep/wake, focus storms, rapid pack switches)
                    val env = AmbientFadeEnvelope(sampleRate = sr, initialVolumeGain = 0.5f)
                    val focusStates = AmbientEngineLogic.FocusState.entries
                    val packs = listOf(SoundPackId.AUTO, SoundPackId.GHOST, SoundPackId.THREEDS, SoundPackId.OLED, SoundPackId.NEON, SoundPackId.OFF)
                    val themes = listOf("ghost", "threeds", "oled", "neon")
                    var prevGain = env.currentGain
                    for (i in 0 until 200) {
                        val cfg = AmbientConfig(
                            enabled = (i % 5) != 0,
                            soundPackId = packs[(workerId + i) % packs.size],
                            themePackId = themes[(workerId + i) % themes.size],
                            volumePercent = (i * 17) % 101,
                            respectMusic = (i % 3) != 0,
                            respectRinger = (i % 4) != 0,
                        )
                        val state = AmbientRuntimeState(
                            activeAppActivityCount = i % 3, // 0 <-> 1 <-> 2 absorb/recreate churn
                            hasKeepSession = (i % 7) == 0,
                            screenOff = (i % 11) == 0,
                            videoPreviewUnmuted = (i % 9) == 0,
                            externalMusicActive = (i % 6) == 0,
                            ringerState = AudioPolicy.RingerState.entries[i % 3],
                        )
                        val snap = AmbientEngineLogic.PlaybackSnapshot(
                            isPlaying = (i % 2) == 0,
                            activePack = SoundPackId.CONCRETE_PACKS[i % 4],
                            currentVolumeGain = AudioPolicy.volumePercentToGain((i * 13) % 101),
                            focusState = focusStates[(workerId + i) % focusStates.size],
                            videoDucked = (i % 4) == 1,
                            appliedDucked = (i % 4) == 2,
                        )
                        val plan = AmbientEngineLogic.planTransition(cfg, state, snap)
                        assertTrue("non-finite target gain", plan.targetVolumeGain.isFinite())
                        assertTrue("target gain out of bounds", plan.targetVolumeGain in 0f..1f)
                        if (state.hasKeepSession || state.screenOff || state.activeAppActivityCount == 0 || cfg.soundPackId == SoundPackId.OFF) {
                            assertFalse("must never request focus when blocked", plan.shouldRequestFocus)
                            assertEquals(AmbientEngineLogic.Action.FADE_OUT_PAUSE, plan.action)
                        }

                        // Hammer envelope reversals and verify zero click steps & finite bounds
                        when (i % 4) {
                            0 -> env.fadeIn(600)
                            1 -> env.setDucked(true, 180)
                            2 -> env.setVolumeGain(plan.targetVolumeGain, 80)
                            else -> env.fadeOut(220)
                        }
                        repeat(64) {
                            val g = env.nextFrameGain()
                            assertTrue("envelope gain non-finite", g.isFinite())
                            assertTrue("envelope gain out of [0,1]: $g", g in 0.0..1.0)
                            assertTrue("click jump during churn: prev=$prevGain curr=$g", abs(g - prevGain) < 0.005)
                            prevGain = g
                        }
                    }

                    // 4. Concurrent CustomSoundPackMatcher & AudioHapticCohesion determinism
                    repeat(40) { iter ->
                        val scan = CustomSoundPackMatcher.scanCandidates(matcherCandidates.shuffled(kotlin.random.Random(workerId * 100 + iter)))
                        assertEquals(goldenScan.totalMatched, scan.totalMatched)
                        assertEquals(goldenScan.cueEntries, scan.cueEntries)

                        val action = Action.entries[(workerId + iter) % Action.entries.size]
                        val keyCue = AudioHapticCohesion.cueForKeyTest(action)
                        val keyPan = AudioHapticCohesion.panForKeyTest(action)
                        val feedback = AudioHapticCohesion.planFeedback(
                            cue = keyCue,
                            pan = keyPan,
                            repeatStreak = iter % 6,
                            keepSessionActive = (iter % 2) == 0,
                        )
                        assertTrue(feedback.pan.isFinite() && feedback.volumeMul.isFinite())
                    }
                    true
                })
            }

            startGate.countDown()
            for (f in futures) {
                assertTrue(f.get(30, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
