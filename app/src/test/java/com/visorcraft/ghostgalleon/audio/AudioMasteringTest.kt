package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioMasteringTest {

    @Test
    fun `hot clipping signal is soft-limited strictly below -1_5 dBFS`() {
        val sr = AudioDsp.SAMPLE_RATE
        val hot = DoubleArray(sr / 10) { i ->
            10.0 * sin(2.0 * PI * 880.0 * i / sr)
        }
        val mastered = AudioMastering.masterMono(hot, sampleRate = sr)

        var maxPeak = 0.0
        for (s in mastered) {
            assertTrue("sample must be finite", s.isFinite())
            val mag = abs(s)
            if (mag > maxPeak) maxPeak = mag
        }
        assertTrue("peak $maxPeak exceeded 0.842 ceiling", maxPeak <= 0.842)
        assertTrue("signal should remain audible, peak=$maxPeak", maxPeak > 0.25)
    }

    @Test
    fun `110 Hz high-pass suppresses sub-bass speaker rattle while preserving midrange`() {
        val sr = AudioDsp.SAMPLE_RATE
        val length = sr / 4 // 250 ms
        val subBass = DoubleArray(length) { i -> sin(2.0 * PI * 40.0 * i / sr) }
        val midTone = DoubleArray(length) { i -> sin(2.0 * PI * 1000.0 * i / sr) }

        val config = AudioMastering.MasteringConfig(
            highPassHz = 110.0,
            lowPassHz = 11_000.0,
            targetRms = 0.0, // disable RMS boost to measure raw filter attenuation
        )
        val filteredSub = AudioMastering.masterMono(subBass, sampleRate = sr, config = config)
        val filteredMid = AudioMastering.masterMono(midTone, sampleRate = sr, config = config)

        val subRms = AudioMastering.computeRms(filteredSub)
        val midRms = AudioMastering.computeRms(filteredMid)
        assertTrue(
            "expected 40 Hz ($subRms) to be strongly attenuated vs 1 kHz ($midRms)",
            subRms < midRms * 0.25,
        )
    }

    @Test
    fun `11 kHz low-pass softens ultrasonic harshness relative to midrange`() {
        val sr = AudioDsp.SAMPLE_RATE
        val length = sr / 10
        val highTone = DoubleArray(length) { i -> sin(2.0 * PI * 18_000.0 * i / sr) }
        val midTone = DoubleArray(length) { i -> sin(2.0 * PI * 1000.0 * i / sr) }

        val config = AudioMastering.MasteringConfig(
            highPassHz = 110.0,
            lowPassHz = 11_000.0,
            targetRms = 0.0,
        )
        val filteredHigh = AudioMastering.masterMono(highTone, sampleRate = sr, config = config)
        val filteredMid = AudioMastering.masterMono(midTone, sampleRate = sr, config = config)

        val highRms = AudioMastering.computeRms(filteredHigh)
        val midRms = AudioMastering.computeRms(filteredMid)
        assertTrue(
            "expected 18 kHz ($highRms) to be attenuated vs 1 kHz ($midRms)",
            highRms < midRms * 0.35,
        )
    }

    @Test
    fun `DC offset and non-finite samples are neutralized with click-free edges`() {
        val sr = AudioDsp.SAMPLE_RATE
        val biased = DoubleArray(sr / 10) { i ->
            0.8 + 0.4 * sin(2.0 * PI * 523.25 * i / sr)
        }
        biased[10] = Double.NaN
        biased[20] = Double.POSITIVE_INFINITY
        biased[30] = Double.NEGATIVE_INFINITY

        val mastered = AudioMastering.masterMono(biased, sampleRate = sr)

        for (s in mastered) {
            assertTrue("output must be finite", s.isFinite())
        }
        val mean = AudioMastering.computeMean(mastered)
        assertTrue("DC offset too large: $mean", abs(mean) < 0.002)
        assertEquals(0.0, mastered.first(), 1e-9)
        assertEquals(0.0, mastered.last(), 1e-9)
        assertTrue("fade-in step should stay click-free", abs(mastered[1]) < 0.02)
        assertTrue("fade-out step should stay click-free", abs(mastered[mastered.lastIndex - 1]) < 0.02)
    }

    @Test
    fun `pure constant DC input collapses cleanly without NaN or clicks`() {
        val dc = DoubleArray(2000) { 1.0 }
        val mastered = AudioMastering.masterMono(dc)
        for (s in mastered) {
            assertTrue(s.isFinite())
            assertTrue(abs(s) <= 0.842)
        }
        assertTrue(abs(AudioMastering.computeMean(mastered)) < 0.002)
        assertEquals(0.0, mastered.first(), 1e-9)
        assertEquals(0.0, mastered.last(), 1e-9)
    }

    @Test
    fun `empty and silent buffers remain safe and silent`() {
        assertEquals(0, AudioMastering.masterMono(DoubleArray(0)).size)
        assertEquals(0.0, AudioMastering.masterMono(DoubleArray(1) { 0.9 })[0], 1e-9)
        val silent = AudioMastering.masterMono(DoubleArray(512))
        assertEquals(0.0, AudioMastering.computeRms(silent), 1e-12)
    }

    @Test
    fun `dsp oscillators, seeded noise, micro-room, and constant-power pan behave cleanly`() {
        // Constant-power pan preserves L^2 + R^2 == 1.0 across [-1, +1]
        for (step in -10..10) {
            val pan = step / 10f
            val (l, r) = AudioDsp.constantPowerPan(pan)
            val power = l * l + r * r
            assertEquals("pan=$pan power", 1.0f, power, 1e-4f)
            assertTrue(l in 0f..1f && r in 0f..1f)
        }

        // SeededNoise is bit-exact deterministic and bounded
        val n1 = AudioDsp.SeededNoise(42L)
        val n2 = AudioDsp.SeededNoise(42L)
        val w1 = DoubleArray(256) { n1.nextWhite() }
        val w2 = DoubleArray(256) { n2.nextWhite() }
        assertArrayEquals(w1, w2, 0.0)
        assertTrue(w1.all { it in -1.0..1.0 })

        val p1 = DoubleArray(256) { n1.nextPink() }
        val p2 = DoubleArray(256) { n2.nextPink() }
        assertArrayEquals(p1, p2, 0.0)
        assertTrue(p1.all { it in -1.0..1.0 })

        // Soft triangle & warm pulse stay bounded and smooth
        for (i in 0 until 100) {
            val ph = i / 100.0
            assertTrue(abs(AudioDsp.softTriangle(ph)) <= 1.0)
            assertTrue(abs(AudioDsp.warmPulse(ph, duty = 0.4, freqHz = 880.0)) <= 1.25)
            assertTrue(abs(AudioDsp.fmTone(ph, ph * 2.0, 1.2)) <= 1.0)
        }

        // Micro-room preserves length and adds reflection energy after an impulse
        val impulse = DoubleArray(1200)
        impulse[0] = 1.0
        val wet = AudioDsp.applyMicroRoom(impulse, wetMix = 0.25, decay = 0.3)
        assertEquals(impulse.size, wet.size)
        val tailEnergy = sqrt(wet.drop(250).sumOf { it * it })
        assertTrue("expected early-reflection energy in tail, got $tailEnergy", tailEnergy > 0.01)
    }
}
