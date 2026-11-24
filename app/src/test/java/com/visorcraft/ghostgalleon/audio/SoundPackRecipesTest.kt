package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spectral, timbral, and acoustic-differentiation unit tests across all 4 concrete
 * sound packs (`GHOST`, `THREEDS`, `OLED`, `NEON`) × all 24 [SoundCue]s (96 waveforms).
 */
class SoundPackRecipesTest {

    private val sr = PcmSynth.DEFAULT_SAMPLE_RATE

    @Test
    fun `all 96 concrete pack waveforms satisfy peak ceiling, DC, click-free edge, duration, and determinism invariants`() {
        var totalChecked = 0
        for (pack in SoundPackId.CONCRETE_PACKS) {
            for (cue in SoundCue.entries) {
                val floatBuf = PcmSynth.synthesizeMasteredFloat(cue, pack, sr)
                val pcmA = PcmSynth.synthesize(cue, pack, sr)
                val pcmB = PcmSynth.synthesize(cue, pack, sr)
                assertArrayEquals("non-deterministic output for $cue/$pack", pcmA, pcmB)

                val durationMs = floatBuf.size * 1000.0 / sr
                assertTrue(
                    "$cue/$pack duration ${durationMs}ms outside 18..1360ms",
                    durationMs in 18.0..1360.0,
                )

                var peak = 0.0
                for (i in floatBuf.indices) {
                    val v = floatBuf[i]
                    assertTrue("$cue/$pack non-finite at $i", v.isFinite())
                    val mag = abs(v)
                    if (mag > peak) peak = mag
                }
                val rms = AudioMastering.computeRms(floatBuf)
                val mean = AudioMastering.computeMean(floatBuf)
                val pcmPeak = pcmA.maxOf { abs(it.toInt()) } / 32767.0

                // Strictly <= -1.4 dBFS (0.8511) and <= -1.5 dBFS mastering ceiling (0.842)
                assertTrue("$cue/$pack peak $peak exceeds -1.4 dBFS", peak <= 0.85)
                assertTrue("$cue/$pack peak $peak exceeds -1.5 dBFS ceiling", peak <= 0.842)
                assertTrue("$cue/$pack PCM peak $pcmPeak exceeds -1.4 dBFS", pcmPeak <= 0.85)
                assertTrue("$cue/$pack too quiet (rms=$rms)", rms >= 0.025)
                assertTrue("$cue/$pack DC offset too large ($mean)", abs(mean) < 0.005)

                // Exact-zero boundaries and smooth half-cosine edge slopes
                assertEquals("$cue/$pack start edge", 0.0, floatBuf.first(), 1e-9)
                assertEquals("$cue/$pack end edge", 0.0, floatBuf.last(), 1e-9)
                assertTrue("$cue/$pack start click (${floatBuf[1]})", abs(floatBuf[1]) < 0.02)
                assertTrue("$cue/$pack end click (${floatBuf[floatBuf.lastIndex - 1]})", abs(floatBuf[floatBuf.lastIndex - 1]) < 0.02)
                totalChecked++
            }
        }
        assertEquals(96, totalChecked)
    }

    @Test
    fun `all 6 concrete pack pairs are measurably differentiated across all 24 cues`() {
        val packs = SoundPackId.CONCRETE_PACKS
        var pairCount = 0
        var totalDistance = 0.0

        for (cue in SoundCue.entries) {
            val renders = packs.associateWith { pack -> PcmSynth.synthesizeMasteredFloat(cue, pack, sr) }
            for (i in packs.indices) {
                for (j in i + 1 until packs.size) {
                    val a = renders.getValue(packs[i])
                    val b = renders.getValue(packs[j])
                    val dist = normalizedWaveformDistance(a, b)
                    totalDistance += dist
                    pairCount++
                    assertTrue(
                        "packs ${packs[i]} and ${packs[j]} too similar on $cue (distance=$dist)",
                        dist > 0.45,
                    )
                }
            }
        }
        assertEquals(24 * 6, pairCount)
        val meanDistance = totalDistance / pairCount
        assertTrue("expected strong average pairwise differentiation (> 0.90), got $meanDistance", meanDistance > 0.90)
    }

    @Test
    fun `OLED pack is the gentlest, shortest, and most whisper-quiet with sub-30ms NAV_TICK`() {
        val oledNavTick = PcmSynth.synthesizeMasteredFloat(SoundCue.NAV_TICK, SoundPackId.OLED, sr)
        val oledNavMs = oledNavTick.size * 1000.0 / sr
        assertTrue("OLED NAV_TICK must be sub-30ms, got ${oledNavMs}ms", oledNavMs < 30.0)

        val avgDurationMs = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            SoundCue.entries.map { cue ->
                PcmSynth.synthesizeMasteredFloat(cue, pack, sr).size * 1000.0 / sr
            }.average()
        }
        val avgRms = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            SoundCue.entries.map { cue ->
                AudioMastering.computeRms(PcmSynth.synthesizeMasteredFloat(cue, pack, sr))
            }.average()
        }

        // OLED shortest average duration across all 24 cues
        assertTrue("OLED avg duration (${avgDurationMs[SoundPackId.OLED]}) < THREEDS (${avgDurationMs[SoundPackId.THREEDS]})",
            avgDurationMs.getValue(SoundPackId.OLED) < avgDurationMs.getValue(SoundPackId.THREEDS))
        assertTrue("OLED avg duration (${avgDurationMs[SoundPackId.OLED]}) < NEON (${avgDurationMs[SoundPackId.NEON]})",
            avgDurationMs.getValue(SoundPackId.OLED) < avgDurationMs.getValue(SoundPackId.NEON))
        assertTrue("OLED avg duration (${avgDurationMs[SoundPackId.OLED]}) < GHOST (${avgDurationMs[SoundPackId.GHOST]})",
            avgDurationMs.getValue(SoundPackId.OLED) < avgDurationMs.getValue(SoundPackId.GHOST))

        // OLED quietest / gentlest average RMS across all 24 cues
        assertTrue("OLED avg RMS (${avgRms[SoundPackId.OLED]}) significantly softer than THREEDS (${avgRms[SoundPackId.THREEDS]})",
            avgRms.getValue(SoundPackId.OLED) < avgRms.getValue(SoundPackId.THREEDS) * 0.78)
        assertTrue("OLED avg RMS (${avgRms[SoundPackId.OLED]}) significantly softer than GHOST (${avgRms[SoundPackId.GHOST]})",
            avgRms.getValue(SoundPackId.OLED) < avgRms.getValue(SoundPackId.GHOST) * 0.78)
        assertTrue("OLED avg RMS (${avgRms[SoundPackId.OLED]}) significantly softer than NEON (${avgRms[SoundPackId.NEON]})",
            avgRms.getValue(SoundPackId.OLED) < avgRms.getValue(SoundPackId.NEON) * 0.72)
    }

    @Test
    fun `spectral brightness and ZCR ordering reflects NEON bright harmonic, GHOST resonant, THREEDS warm, and OLED pure gentle`() {
        val harmonicCues = listOf(
            SoundCue.NAV_TICK,
            SoundCue.CONFIRM,
            SoundCue.LAUNCH_GAME,
            SoundCue.FAVORITE_ON,
            SoundCue.MODE_TOGGLE,
            SoundCue.RA_ACHIEVEMENT,
            SoundCue.BOOT_CHIME,
        )

        val avgZcr = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            harmonicCues.map { cue ->
                zeroCrossingRateHz(PcmSynth.synthesizeMasteredFloat(cue, pack, sr), sr)
            }.average()
        }
        val avgHighBandRatio = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            harmonicCues.map { cue ->
                highFrequencyEnergyRatio(PcmSynth.synthesizeMasteredFloat(cue, pack, sr), sr, splitHz = 2000.0)
            }.average()
        }

        // NEON is the brightest / highest harmonic density pack
        assertTrue("NEON ZCR (${avgZcr[SoundPackId.NEON]}) > GHOST (${avgZcr[SoundPackId.GHOST]})",
            avgZcr.getValue(SoundPackId.NEON) > avgZcr.getValue(SoundPackId.GHOST))
        assertTrue("NEON ZCR (${avgZcr[SoundPackId.NEON]}) > THREEDS (${avgZcr[SoundPackId.THREEDS]})",
            avgZcr.getValue(SoundPackId.NEON) > avgZcr.getValue(SoundPackId.THREEDS))
        assertTrue("NEON ZCR (${avgZcr[SoundPackId.NEON]}) > OLED (${avgZcr[SoundPackId.OLED]})",
            avgZcr.getValue(SoundPackId.NEON) > avgZcr.getValue(SoundPackId.OLED))

        assertTrue("NEON >2kHz energy (${avgHighBandRatio[SoundPackId.NEON]}) > GHOST (${avgHighBandRatio[SoundPackId.GHOST]})",
            avgHighBandRatio.getValue(SoundPackId.NEON) > avgHighBandRatio.getValue(SoundPackId.GHOST))
        assertTrue("NEON >2kHz energy (${avgHighBandRatio[SoundPackId.NEON]}) > THREEDS (${avgHighBandRatio[SoundPackId.THREEDS]})",
            avgHighBandRatio.getValue(SoundPackId.NEON) > avgHighBandRatio.getValue(SoundPackId.THREEDS))
        assertTrue("NEON >2kHz energy (${avgHighBandRatio[SoundPackId.NEON]}) > OLED (${avgHighBandRatio[SoundPackId.OLED]})",
            avgHighBandRatio.getValue(SoundPackId.NEON) > avgHighBandRatio.getValue(SoundPackId.OLED))

        // OLED pure-sine minimal profile has strictly lowest high-frequency harmonic energy
        assertTrue("OLED >2kHz energy (${avgHighBandRatio[SoundPackId.OLED]}) < THREEDS (${avgHighBandRatio[SoundPackId.THREEDS]})",
            avgHighBandRatio.getValue(SoundPackId.OLED) < avgHighBandRatio.getValue(SoundPackId.THREEDS))
        assertTrue("OLED >2kHz energy (${avgHighBandRatio[SoundPackId.OLED]}) < GHOST (${avgHighBandRatio[SoundPackId.GHOST]})",
            avgHighBandRatio.getValue(SoundPackId.OLED) < avgHighBandRatio.getValue(SoundPackId.GHOST))

        // GHOST is the most resonant / longest average decay ring-out
        val resonantCues = listOf(SoundCue.CONFIRM, SoundCue.LAUNCH_GAME, SoundCue.MODAL_OPEN, SoundCue.BOOT_CHIME, SoundCue.RA_ACHIEVEMENT)
        val ghostAvgDur = resonantCues.map { PcmSynth.synthesizeMasteredFloat(it, SoundPackId.GHOST, sr).size }.average()
        val threeDsAvgDur = resonantCues.map { PcmSynth.synthesizeMasteredFloat(it, SoundPackId.THREEDS, sr).size }.average()
        val neonAvgDur = resonantCues.map { PcmSynth.synthesizeMasteredFloat(it, SoundPackId.NEON, sr).size }.average()
        val oledAvgDur = resonantCues.map { PcmSynth.synthesizeMasteredFloat(it, SoundPackId.OLED, sr).size }.average()

        assertTrue("GHOST resonant cues ($ghostAvgDur) longer than THREEDS ($threeDsAvgDur)", ghostAvgDur > threeDsAvgDur)
        assertTrue("GHOST resonant cues ($ghostAvgDur) longer than NEON ($neonAvgDur)", ghostAvgDur > neonAvgDur)
        assertTrue("GHOST resonant cues ($ghostAvgDur) longer than OLED ($oledAvgDur)", ghostAvgDur > oledAvgDur)
    }

    @Test
    fun `signature pack motifs exhibit their exact acoustic traits`() {
        // 1. NEON FAVORITE_ON coin sparkle: B5 (987.77 Hz) attack -> E6 (1318.51 Hz) sustain
        val neonFav = PcmSynth.synthesizeMasteredFloat(SoundCue.FAVORITE_ON, SoundPackId.NEON, sr)
        val earlyEnd = (0.060 * sr).toInt()
        val lateStart = (0.070 * sr).toInt()
        val lateEnd = min(neonFav.size, (0.210 * sr).toInt())

        val earlyB5 = goertzelMagnitude(neonFav, 0, earlyEnd, 987.77, sr)
        val earlyE6 = goertzelMagnitude(neonFav, 0, earlyEnd, 1318.51, sr)
        val lateB5 = goertzelMagnitude(neonFav, lateStart, lateEnd, 987.77, sr)
        val lateE6 = goertzelMagnitude(neonFav, lateStart, lateEnd, 1318.51, sr)
        assertTrue("NEON FAVORITE_ON early segment should emphasize B5 ($earlyB5) over E6 ($earlyE6)", earlyB5 > earlyE6 * 1.5)
        assertTrue("NEON FAVORITE_ON sustain segment should emphasize E6 ($lateE6) over B5 ($lateB5)", lateE6 > lateB5 * 2.0)

        // 2. GHOST CONFIRM glassy maritime bell: 2.76x upper partial of D5 (~1621 Hz) present on strike and damps faster than D5 fundamental
        val ghostConfirm = PcmSynth.synthesizeMasteredFloat(SoundCue.CONFIRM, SoundPackId.GHOST, sr)
        val strikeEnd = (0.045 * sr).toInt()
        val tailStart = (0.065 * sr).toInt()
        val tailEnd = min(ghostConfirm.size, (0.155 * sr).toInt())

        val d5Hz = 587.33
        val partial276Hz = d5Hz * SoundPackRecipes.MARITIME_BELL_PARTIAL_RATIO
        val strikeFund = goertzelMagnitude(ghostConfirm, 0, strikeEnd, d5Hz, sr)
        val strikeUpper = goertzelMagnitude(ghostConfirm, 0, strikeEnd, partial276Hz, sr)
        val tailFund = goertzelMagnitude(ghostConfirm, tailStart, tailEnd, d5Hz, sr)
        val tailUpper = goertzelMagnitude(ghostConfirm, tailStart, tailEnd, partial276Hz, sr)

        assertTrue("GHOST CONFIRM strike should contain measurable 2.76x bell partial ($strikeUpper)", strikeUpper > 0.01)
        val strikeRatio = strikeUpper / strikeFund.coerceAtLeast(1e-9)
        val tailRatio = tailUpper / tailFund.coerceAtLeast(1e-9)
        assertTrue(
            "GHOST 2.76x bell partial should damp faster than fundamental (strikeRatio=$strikeRatio, tailRatio=$tailRatio)",
            strikeRatio > tailRatio * 1.6,
        )

        // 3. THREEDS CONFIRM joyful C5/G5 fifth anchors vs GHOST D5
        val threeDsConfirm = PcmSynth.synthesizeMasteredFloat(SoundCue.CONFIRM, SoundPackId.THREEDS, sr)
        val threeDsC5 = goertzelMagnitude(threeDsConfirm, 0, threeDsConfirm.size, 523.25, sr)
        val threeDsG5 = goertzelMagnitude(threeDsConfirm, 0, threeDsConfirm.size, 783.99, sr)
        val threeDsD5 = goertzelMagnitude(threeDsConfirm, 0, threeDsConfirm.size, 587.33, sr)
        assertTrue("THREEDS CONFIRM should center on C5 ($threeDsC5) & G5 ($threeDsG5) rather than D5 ($threeDsD5)",
            threeDsC5 > threeDsD5 * 2.0 && threeDsG5 > threeDsD5 * 2.0)

        // 4. THREEDS NAV_PAGE_TURN has breezy higher-band airflow vs quiet OLED NAV_PAGE_TURN
        val threeDsPage = PcmSynth.synthesizeMasteredFloat(SoundCue.NAV_PAGE_TURN, SoundPackId.THREEDS, sr)
        val oledPage = PcmSynth.synthesizeMasteredFloat(SoundCue.NAV_PAGE_TURN, SoundPackId.OLED, sr)
        val threeDsAir = highFrequencyEnergyRatio(threeDsPage, sr, splitHz = 1200.0)
        val oledAir = highFrequencyEnergyRatio(oledPage, sr, splitHz = 1200.0)
        assertTrue("THREEDS NAV_PAGE_TURN air energy ($threeDsAir) > OLED ($oledAir)", threeDsAir > oledAir)
    }

    @Test
    fun `new DSP primitives and non-finite constantPowerPan guards behave safely`() {
        // Non-finite pan values safely return center (matching pan = 0f)
        val center = AudioDsp.constantPowerPan(0f)
        for (badPan in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val (l, r) = AudioDsp.constantPowerPan(badPan)
            assertTrue("left gain must be finite for pan=$badPan", l.isFinite())
            assertTrue("right gain must be finite for pan=$badPan", r.isFinite())
            assertEquals("left gain for pan=$badPan", center.first, l, 1e-6f)
            assertEquals("right gain for pan=$badPan", center.second, r, 1e-6f)
            assertEquals("constant power for pan=$badPan", 1.0f, l * l + r * r, 1e-4f)
        }

        // bandLimitedSaw and inharmonicBell remain bounded and finite
        for (i in 0 until 200) {
            val ph = i / 200.0
            val saw = AudioDsp.bandLimitedSaw(ph, maxHarmonics = 8, freqHz = 880.0, sampleRate = sr)
            assertTrue("saw out of bounds: $saw", saw.isFinite() && abs(saw) <= 1.25)
            val bell = AudioDsp.inharmonicBell(ph, ph * 2.0, ph * 2.76, partial2Mix = 0.25, upperPartialMix = 0.40)
            assertTrue("bell out of bounds: $bell", bell.isFinite() && abs(bell) <= 1.0 + 1e-9)
        }

        // applyResonantRoom preserves length, is deterministic, and adds reflection tail after impulse
        val impulse = DoubleArray(2400)
        impulse[0] = 1.0
        val roomA = AudioDsp.applyResonantRoom(impulse, sampleRate = sr, wetMix = 0.26, decay = 0.36)
        val roomB = AudioDsp.applyResonantRoom(impulse, sampleRate = sr, wetMix = 0.26, decay = 0.36)
        assertEquals(impulse.size, roomA.size)
        assertArrayEquals(roomA, roomB, 0.0)
        val tailEnergy = sqrt(roomA.drop(400).sumOf { it * it })
        assertTrue("expected nautical chamber reflections after 9ms tap, got $tailEnergy", tailEnergy > 0.02)
    }

    // =========================================================================
    // Pure JVM Acoustic Measurement Helpers
    // =========================================================================

    private fun normalizedWaveformDistance(a: DoubleArray, b: DoubleArray): Double {
        val rmsA = AudioMastering.computeRms(a).coerceAtLeast(1e-9)
        val rmsB = AudioMastering.computeRms(b).coerceAtLeast(1e-9)
        val maxLen = maxOf(a.size, b.size).coerceAtLeast(1)
        var sumSqDiff = 0.0
        for (i in 0 until maxLen) {
            val va = if (i < a.size) a[i] / rmsA else 0.0
            val vb = if (i < b.size) b[i] / rmsB else 0.0
            val d = va - vb
            sumSqDiff += d * d
        }
        return sqrt(sumSqDiff / maxLen)
    }

    private fun zeroCrossingRateHz(samples: DoubleArray, sampleRate: Int): Double {
        if (samples.size < 2) return 0.0
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0.0 && samples[i] < 0.0) || (samples[i - 1] < 0.0 && samples[i] >= 0.0)) {
                crossings++
            }
        }
        val durationSec = (samples.size - 1).toDouble() / sampleRate
        return (crossings * 0.5) / durationSec.coerceAtLeast(1e-9)
    }

    private fun highFrequencyEnergyRatio(samples: DoubleArray, sampleRate: Int, splitHz: Double): Double {
        if (samples.isEmpty()) return 0.0
        val hp = AudioDsp.BiquadFilter.highPass(splitHz, sampleRate = sampleRate)
        var totalEnergy = 0.0
        var highEnergy = 0.0
        for (s in samples) {
            val h = hp.processSample(s)
            totalEnergy += s * s
            highEnergy += h * h
        }
        return if (totalEnergy > 1e-12) highEnergy / totalEnergy else 0.0
    }

    /** Normalized single-bin Goertzel DFT magnitude for [targetHz] over `[startIdx, endIdx)`. */
    private fun goertzelMagnitude(
        samples: DoubleArray,
        startIdx: Int,
        endIdx: Int,
        targetHz: Double,
        sampleRate: Int,
    ): Double {
        val n = (endIdx - startIdx).coerceAtLeast(1)
        val omega = 2.0 * PI * targetHz / sampleRate
        val coeff = 2.0 * cos(omega)
        var s1 = 0.0
        var s2 = 0.0
        for (i in startIdx until endIdx) {
            val w = 0.5 * (1.0 - cos(2.0 * PI * (i - startIdx) / n))
            val s0 = samples[i] * w + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val real = s1 - s2 * cos(omega)
        val imag = s2 * sin(omega)
        return (2.0 * sqrt(real * real + imag * imag)) / n
    }
}
