package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure JVM tests for [AmbientSynthesizer] and [AmbientFadeEnvelope].
 *
 * Verifies gentle background RMS, peak safety, DC offset, seamless circular wrap-around
 * continuity, stereo width, pairwise pack differentiation, and click-free chunk-boundary
 * independence across all 4 concrete ambient soundscapes (`GHOST`, `THREEDS`, `OLED`, `NEON`).
 */
class AmbientSynthesizerTest {

    private val sr = AmbientSynthesizer.SAMPLE_RATE

    @Test
    fun `all 4 concrete ambient loops satisfy stereo length, gentle RMS, peak, DC, stereo width, and seamless wrap-around`() {
        val expectedFrames = (AmbientSynthesizer.DEFAULT_DURATION_SEC * sr).toInt()
        val expectedSamples = expectedFrames * AmbientSynthesizer.CHANNELS

        for (pack in SoundPackId.CONCRETE_PACKS) {
            val floatStereo = AmbientSynthesizer.synthesizeLoopFloat(pack)
            val pcmStereo = AmbientSynthesizer.synthesizeLoop(pack)

            assertEquals("$pack stereo float length", expectedSamples, floatStereo.size)
            assertEquals("$pack stereo PCM length", expectedSamples, pcmStereo.size)

            var peak = 0.0
            var sumL = 0.0
            var sumR = 0.0
            var sumSqL = 0.0
            var sumSqR = 0.0
            var sumSqDiff = 0.0

            for (f in 0 until expectedFrames) {
                val l = floatStereo[f * 2]
                val r = floatStereo[f * 2 + 1]
                assertTrue("$pack non-finite L at $f", l.isFinite())
                assertTrue("$pack non-finite R at $f", r.isFinite())
                val ml = abs(l)
                val mr = abs(r)
                if (ml > peak) peak = ml
                if (mr > peak) peak = mr
                sumL += l
                sumR += r
                sumSqL += l * l
                sumSqR += r * r
                val diff = l - r
                sumSqDiff += diff * diff
            }

            val rmsL = sqrt(sumSqL / expectedFrames)
            val rmsR = sqrt(sumSqR / expectedFrames)
            val rmsCombined = sqrt(0.5 * (rmsL * rmsL + rmsR * rmsR))
            val meanL = sumL / expectedFrames
            val meanR = sumR / expectedFrames
            val stereoDiffRms = sqrt(sumSqDiff / expectedFrames)
            val pcmPeakNorm = pcmStereo.maxOf { abs(it.toInt()) } / 32767.0

            // Gentle background RMS (~ -24 dBFS, strictly in 0.04..0.09 and 0.02..0.14)
            assertTrue("$pack RMS $rmsCombined outside gentle ambient range 0.04..0.09", rmsCombined in 0.04..0.09)
            assertTrue("$pack peak $peak exceeds 0.45 ceiling", peak <= 0.45)
            assertTrue("$pack PCM peak $pcmPeakNorm exceeds 0.50 ceiling", pcmPeakNorm <= 0.50)
            assertTrue("$pack L DC offset too large: $meanL", abs(meanL) < 0.005)
            assertTrue("$pack R DC offset too large: $meanR", abs(meanR) < 0.005)
            assertTrue("$pack expected genuine stereo width, diffRms=$stereoDiffRms", stereoDiffRms > 0.003)

            // Seamless circular wrap-around continuity: |last - first| < 0.015 on both channels
            val firstL = floatStereo[0]
            val firstR = floatStereo[1]
            val lastL = floatStereo[(expectedFrames - 1) * 2]
            val lastR = floatStereo[(expectedFrames - 1) * 2 + 1]
            val seamJumpL = abs(lastL - firstL)
            val seamJumpR = abs(lastR - firstR)
            assertTrue("$pack L seam discontinuity too large: $seamJumpL", seamJumpL < 0.015)
            assertTrue("$pack R seam discontinuity too large: $seamJumpR", seamJumpR < 0.015)

            // PCM-level seam check as well
            val pcmSeamL = abs(pcmStereo[(expectedFrames - 1) * 2].toInt() - pcmStereo[0].toInt()) / 32767.0
            val pcmSeamR = abs(pcmStereo[(expectedFrames - 1) * 2 + 1].toInt() - pcmStereo[1].toInt()) / 32767.0
            assertTrue("$pack PCM L seam discontinuity too large: $pcmSeamL", pcmSeamL < 0.015)
            assertTrue("$pack PCM R seam discontinuity too large: $pcmSeamR", pcmSeamR < 0.015)
        }
    }

    @Test
    fun `OFF synthesizes empty silence, AUTO resolves to GHOST, and synthesis is bit-exact deterministic`() {
        assertEquals(0, AmbientSynthesizer.synthesizeLoop(SoundPackId.OFF).size)
        assertEquals(0, AmbientSynthesizer.synthesizeLoopFloat(SoundPackId.OFF).size)

        val ghostA = AmbientSynthesizer.synthesizeLoop(SoundPackId.GHOST, durationSec = 2.5)
        val ghostB = AmbientSynthesizer.synthesizeLoop(SoundPackId.GHOST, durationSec = 2.5)
        val autoLoop = AmbientSynthesizer.synthesizeLoop(SoundPackId.AUTO, durationSec = 2.5)

        assertArrayEquals("ambient loop must be deterministic", ghostA, ghostB)
        assertArrayEquals("AUTO must match GHOST loop", ghostA, autoLoop)
    }

    @Test
    fun `all 4 concrete ambient packs are pairwise differentiated with distinct spectral profiles`() {
        val packs = SoundPackId.CONCRETE_PACKS
        val renders = packs.associateWith { pack ->
            AmbientSynthesizer.synthesizeLoopFloat(pack, durationSec = 3.0)
        }

        for (i in packs.indices) {
            for (j in i + 1 until packs.size) {
                val a = renders.getValue(packs[i])
                val b = renders.getValue(packs[j])
                val dist = normalizedDistance(a, b)
                assertTrue(
                    "ambient packs ${packs[i]} and ${packs[j]} too similar (distance=$dist)",
                    dist > 0.70,
                )
            }
        }

        // OLED Midnight Velvet Hush is the gentlest (lowest RMS) and darkest (lowest >1.2kHz energy)
        val rmsByPack = renders.mapValues { (_, buf) -> AudioMastering.computeRms(buf) }
        val highByPack = renders.mapValues { (_, buf) -> highBandEnergyRatioLeft(buf, sr, splitHz = 1200.0) }

        assertTrue(
            "OLED RMS (${rmsByPack[SoundPackId.OLED]}) < GHOST (${rmsByPack[SoundPackId.GHOST]})",
            rmsByPack.getValue(SoundPackId.OLED) < rmsByPack.getValue(SoundPackId.GHOST),
        )
        assertTrue(
            "OLED RMS (${rmsByPack[SoundPackId.OLED]}) < THREEDS (${rmsByPack[SoundPackId.THREEDS]})",
            rmsByPack.getValue(SoundPackId.OLED) < rmsByPack.getValue(SoundPackId.THREEDS),
        )
        assertTrue(
            "OLED RMS (${rmsByPack[SoundPackId.OLED]}) < NEON (${rmsByPack[SoundPackId.NEON]})",
            rmsByPack.getValue(SoundPackId.OLED) < rmsByPack.getValue(SoundPackId.NEON),
        )
        assertTrue(
            "OLED >1.2kHz energy (${highByPack[SoundPackId.OLED]}) < NEON (${highByPack[SoundPackId.NEON]})",
            highByPack.getValue(SoundPackId.OLED) < highByPack.getValue(SoundPackId.NEON),
        )
        assertTrue(
            "OLED >1.2kHz energy (${highByPack[SoundPackId.OLED]}) < THREEDS (${highByPack[SoundPackId.THREEDS]})",
            highByPack.getValue(SoundPackId.OLED) < highByPack.getValue(SoundPackId.THREEDS),
        )
    }

    @Test
    fun `AmbientFadeEnvelope ramps smoothly, handles mid-ramp reversals, and is bit-exact across arbitrary chunk sizes`() {
        val env = AmbientFadeEnvelope(sampleRate = sr, initialVolumeGain = 1.0f)
        assertTrue(env.isSilent)

        // 1. 600 ms fade-in is strictly monotonic and click-free
        val fadeInFrames = (AmbientSynthesizer.FADE_IN_MS * sr) / 1000
        env.fadeIn()
        var prev = 0.0
        var maxStep = 0.0
        repeat(fadeInFrames) {
            val g = env.nextFrameGain()
            assertTrue("fade-in must be non-decreasing: prev=$prev curr=$g", g >= prev - 1e-12)
            val step = abs(g - prev)
            if (step > maxStep) maxStep = step
            prev = g
        }
        assertEquals(1.0, env.currentGain, 1e-6)
        assertTrue("fade-in per-frame step too sharp: $maxStep", maxStep < 0.0002)

        // 2. 180 ms duck ramp reaches AudioPolicy.ambientDuckFactor(true) smoothly
        val duckFrames = (AmbientSynthesizer.DUCK_RAMP_MS * sr) / 1000
        env.setDucked(true)
        prev = env.currentGain
        repeat(duckFrames) {
            val g = env.nextFrameGain()
            assertTrue("duck ramp must be non-increasing: prev=$prev curr=$g", g <= prev + 1e-12)
            prev = g
        }
        assertEquals(AudioPolicy.ambientDuckFactor(true).toDouble(), env.duckGain, 1e-5)

        // Unduck returns smoothly to 1.0
        env.setDucked(false)
        repeat(duckFrames) { env.nextFrameGain() }
        assertEquals(1.0, env.duckGain, 1e-5)

        // 3. Mid-ramp reversal (fade-out triggered mid-fade-in) has ZERO step discontinuity
        env.resetSilent()
        env.fadeIn(600)
        repeat(fadeInFrames / 3) { env.nextFrameGain() }
        val midLevel = env.currentGain
        assertTrue("expected mid fade-in level, got $midLevel", midLevel in 0.15..0.55)
        env.fadeOut(220)
        assertTrue(env.isFadingOut)
        val firstAfterReversal = env.nextFrameGain()
        assertTrue(
            "mid-ramp reversal discontinuity: mid=$midLevel next=$firstAfterReversal",
            abs(firstAfterReversal - midLevel) < 0.001,
        )
        val fadeOutFrames = (AmbientSynthesizer.FADE_OUT_MS * sr) / 1000
        repeat(fadeOutFrames) { env.nextFrameGain() }
        assertTrue("envelope must reach silence after fade-out", env.isSilent)
        assertFalse(env.isFadingOut)
        assertEquals(0.0, env.currentGain, 1e-9)

        // 4. Chunk-size independence: single 4096-frame render == ragged chunk renders bit-for-bit
        val loop = AmbientSynthesizer.synthesizeLoop(SoundPackId.OLED, durationSec = 2.0)
        val totalFrames = 4096
        val oneShotOut = ShortArray(totalFrames * 2)
        val envOneShot = AmbientFadeEnvelope(sampleRate = sr, initialVolumeGain = 0.8f).apply {
            fadeIn(100)
            setDucked(true, durationMs = 60)
        }
        val cursorOneShot = envOneShot.renderLoopChunk(loop, loopFrameCursor = (loop.size / 2) - 500, outChunk = oneShotOut)

        val raggedSizes = intArrayOf(137, 511, 777, 1024, 1647)
        assertEquals(totalFrames, raggedSizes.sum())
        val raggedOut = ShortArray(totalFrames * 2)
        val envRagged = AmbientFadeEnvelope(sampleRate = sr, initialVolumeGain = 0.8f).apply {
            fadeIn(100)
            setDucked(true, durationMs = 60)
        }
        var cursorRagged = (loop.size / 2) - 500
        var offsetFrames = 0
        for (sz in raggedSizes) {
            val chunk = ShortArray(sz * 2)
            cursorRagged = envRagged.renderLoopChunk(loop, cursorRagged, chunk, sz)
            System.arraycopy(chunk, 0, raggedOut, offsetFrames * 2, chunk.size)
            offsetFrames += sz
        }

        assertEquals("loop cursor must match across chunking", cursorOneShot, cursorRagged)
        assertArrayEquals("ragged chunks must be bit-identical to single chunk", oneShotOut, raggedOut)

        // 5. Non-finite volume gains safely clamp to 0.0
        val badEnv = AmbientFadeEnvelope(sampleRate = sr, initialVolumeGain = Float.NaN)
        assertEquals(0.0, badEnv.volumeGain, 0.0)
        badEnv.setVolumeGain(Float.POSITIVE_INFINITY, durationMs = 0)
        assertEquals(0.0, badEnv.volumeGain, 0.0)
    }

    private fun normalizedDistance(a: DoubleArray, b: DoubleArray): Double {
        val rmsA = AudioMastering.computeRms(a).coerceAtLeast(1e-9)
        val rmsB = AudioMastering.computeRms(b).coerceAtLeast(1e-9)
        val n = minOf(a.size, b.size).coerceAtLeast(1)
        var sumSq = 0.0
        for (i in 0 until n) {
            val d = (a[i] / rmsA) - (b[i] / rmsB)
            sumSq += d * d
        }
        return sqrt(sumSq / n)
    }

    private fun highBandEnergyRatioLeft(interleaved: DoubleArray, sampleRate: Int, splitHz: Double): Double {
        val frames = interleaved.size / 2
        val hp = AudioDsp.BiquadFilter.highPass(splitHz, sampleRate = sampleRate)
        var total = 0.0
        var high = 0.0
        for (f in 0 until frames) {
            val s = interleaved[f * 2]
            val h = hp.processSample(s)
            total += s * s
            high += h * h
        }
        return if (total > 1e-12) high / total else 0.0
    }
}
