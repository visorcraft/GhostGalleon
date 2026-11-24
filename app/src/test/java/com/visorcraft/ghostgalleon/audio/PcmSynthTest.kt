package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class PcmSynthTest {

    @Test
    fun `all 24 cues across all playable packs satisfy peak, DC, edge-click, and duration invariants`() {
        val sr = PcmSynth.DEFAULT_SAMPLE_RATE
        val packsToTest = SoundPackId.CONCRETE_PACKS + SoundPackId.AUTO

        for (pack in packsToTest) {
            for (cue in SoundCue.entries) {
                val floatBuf = PcmSynth.synthesizeMasteredFloat(cue, pack, sr)
                val pcm = PcmSynth.synthesize(cue, pack, sr)

                assertEquals("$cue/$pack size mismatch", floatBuf.size, pcm.size)
                assertTrue("$cue/$pack must be non-empty", pcm.isNotEmpty())

                val durationMs = (pcm.size * 1000.0) / sr
                assertTrue(
                    "$cue/$pack duration ${durationMs}ms out of 15..1400ms bounds",
                    durationMs in 15.0..1400.0,
                )

                var peak = 0.0
                var sum = 0.0
                var sumSq = 0.0
                for (i in floatBuf.indices) {
                    val v = floatBuf[i]
                    assertTrue("$cue/$pack non-finite at $i", v.isFinite())
                    val mag = abs(v)
                    if (mag > peak) peak = mag
                    sum += v
                    sumSq += v * v
                }

                val rms = sqrt(sumSq / floatBuf.size)
                val mean = sum / floatBuf.size
                val pcmPeakNorm = pcm.maxOf { abs(it.toInt()) } / 32767.0
                val firstNorm = abs(pcm.first().toInt()) / 32767.0
                val lastNorm = abs(pcm.last().toInt()) / 32767.0

                assertTrue("$cue/$pack is silent (rms=$rms)", rms >= 0.02)
                assertTrue("$cue/$pack peak $peak exceeds -1.4 dBFS (0.85)", peak <= 0.85)
                assertTrue("$cue/$pack float peak $peak exceeds -1.5 dBFS (0.842)", peak <= 0.842)
                assertTrue("$cue/$pack PCM peak $pcmPeakNorm exceeds 0.85", pcmPeakNorm <= 0.85)
                assertTrue("$cue/$pack DC offset $mean exceeds 0.01", abs(mean) < 0.01)
                assertTrue("$cue/$pack click at start ($firstNorm)", firstNorm < 0.02)
                assertTrue("$cue/$pack click at end ($lastNorm)", lastNorm < 0.02)
            }
        }
    }

    @Test
    fun `OFF pack synthesizes empty silence for every cue`() {
        for (cue in SoundCue.entries) {
            assertEquals(0, PcmSynth.synthesize(cue, SoundPackId.OFF).size)
            assertEquals(0, PcmSynth.synthesizeMasteredFloat(cue, SoundPackId.OFF).size)
            assertEquals(0, PcmSynth.synthesizeStereo(cue, SoundPackId.OFF).size)
        }
    }

    @Test
    fun `synthesis is deterministic across repeated calls`() {
        for (cue in listOf(SoundCue.NAV_TICK, SoundCue.NAV_PAGE_TURN, SoundCue.BOOT_CHIME, SoundCue.RA_ACHIEVEMENT)) {
            val a = PcmSynth.synthesize(cue, SoundPackId.GHOST)
            val b = PcmSynth.synthesize(cue, SoundPackId.GHOST)
            assertArrayEquals("non-deterministic output for $cue", a, b)
        }
    }

    @Test
    fun `distinct concrete packs produce distinct acoustic waveforms`() {
        val renders = SoundPackId.CONCRETE_PACKS.associateWith { pack ->
            PcmSynth.synthesize(SoundCue.CONFIRM, pack)
        }
        val pairs = listOf(
            SoundPackId.GHOST to SoundPackId.THREEDS,
            SoundPackId.GHOST to SoundPackId.OLED,
            SoundPackId.GHOST to SoundPackId.NEON,
            SoundPackId.THREEDS to SoundPackId.NEON,
            SoundPackId.OLED to SoundPackId.NEON,
        )
        for ((a, b) in pairs) {
            assertFalse(
                "expected packs $a and $b to sound distinct",
                renders.getValue(a).contentEquals(renders.getValue(b)),
            )
        }
    }

    @Test
    fun `stereo synthesis shifts energy cleanly with pan`() {
        val leftPanned = PcmSynth.synthesizeStereo(SoundCue.NAV_TICK, SoundPackId.GHOST, pan = -0.28f)
        assertTrue(leftPanned.isNotEmpty() && leftPanned.size % 2 == 0)

        var leftEnergy = 0.0
        var rightEnergy = 0.0
        var i = 0
        while (i < leftPanned.size) {
            val l = leftPanned[i].toDouble()
            val r = leftPanned[i + 1].toDouble()
            leftEnergy += l * l
            rightEnergy += r * r
            i += 2
        }
        assertTrue("negative pan must emphasize left channel", leftEnergy > rightEnergy * 1.5)
    }
}
