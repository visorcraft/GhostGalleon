package com.visorcraft.ghostgalleon.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin procedural stereo ambient soundscape synthesizer and chunk-safe
 * amplitude fade envelope running at 44,100 Hz.
 *
 * Zero Android dependencies so loop synthesis, spectral voicing, seam continuity,
 * and smooth fade/duck ramps remain deterministic and host-testable on the JVM.
 */
object AmbientSynthesizer {

    const val SAMPLE_RATE: Int = AudioDsp.SAMPLE_RATE
    const val CHANNELS: Int = 2

    /** Default seamless stereo loop length (`6.0 s` = `264,600` stereo frames at 44.1 kHz). */
    const val DEFAULT_DURATION_SEC: Double = 6.0

    /** Circular overlap-add crossfade duration (`1.2 s`) for stochastic noise layers. */
    private const val NOISE_OVERLAP_SEC: Double = 1.2
    private const val FILTER_WARMUP_SEC: Double = 0.35

    /** Default gentle background mastering ceiling (`-7.5 dBFS`, well below UI SFX peaks). */
    const val AMBIENT_PEAK_CEILING: Double = 0.42

    /** Default smooth ramp durations for [AmbientFadeEnvelope]. */
    const val FADE_IN_MS: Int = 600
    const val FADE_OUT_MS: Int = 220
    const val DUCK_RAMP_MS: Int = 180
    const val VOLUME_RAMP_MS: Int = 80

    // Musical pitch anchors (12-TET, A4 = 440 Hz)
    private const val D3 = 146.83
    private const val E3 = 164.81
    private const val F3 = 174.61
    private const val A3 = 220.00
    private const val B3 = 246.94

    private const val C4 = 261.63
    private const val D4 = 293.66
    private const val E4 = 329.63
    private const val F4 = 349.23
    private const val G4 = 392.00
    private const val GS4 = 415.30
    private const val A4 = 440.00
    private const val B4 = 493.88

    private const val D5 = 587.33
    private const val E5 = 659.25
    private const val FS5 = 739.99
    private const val A5 = 880.00

    /**
     * Synthesizes a seamlessly looping 16-bit interleaved stereo PCM ambient bed
     * (`[L0, R0, L1, R1, ...]`) for [pack].
     *
     * Directly quantizes split L/R channels into the target [ShortArray] without
     * allocating an intermediate 4.24 MiB interleaved `DoubleArray`.
     *
     * Returns an empty [ShortArray] when [pack] is [SoundPackId.OFF].
     * [SoundPackId.AUTO] resolves to [SoundPackId.GHOST].
     */
    fun synthesizeLoop(
        pack: SoundPackId,
        durationSec: Double = DEFAULT_DURATION_SEC,
        sampleRate: Int = SAMPLE_RATE,
    ): ShortArray {
        if (pack == SoundPackId.OFF) return ShortArray(0)
        val (left, right) = synthesizeStereoChannels(pack, durationSec, sampleRate)
        val loopFrames = left.size
        val pcm = ShortArray(loopFrames * CHANNELS)
        var outIdx = 0
        for (i in 0 until loopFrames) {
            val l = left[i]
            val r = right[i]
            val safeL = if (l.isFinite()) l.coerceIn(-1.0, 1.0) else 0.0
            val safeR = if (r.isFinite()) r.coerceIn(-1.0, 1.0) else 0.0
            pcm[outIdx] = (safeL * 32767.0).roundToInt().coerceIn(-32767, 32767).toShort()
            pcm[outIdx + 1] = (safeR * 32767.0).roundToInt().coerceIn(-32767, 32767).toShort()
            outIdx += 2
        }
        return pcm
    }

    /**
     * Synthesizes a seamlessly looping normalized floating-point interleaved stereo
     * buffer (`[L0, R0, L1, R1, ...]`) for [pack].
     *
     * Guarantees:
     * - Exact integer cycles for all periodic harmonic/LFO layers (zero phase discontinuity).
     * - Constant-power circular overlap-add crossfading for all filtered noise layers.
     * - Circularly primed IIR EQ filters so filter state at `frame 0` matches `frame N`.
     * - Gentle handheld-safe background RMS (`~0.045..0.080`), peak `<= 0.42`, near-zero DC.
     */
    fun synthesizeLoopFloat(
        pack: SoundPackId,
        durationSec: Double = DEFAULT_DURATION_SEC,
        sampleRate: Int = SAMPLE_RATE,
    ): DoubleArray {
        require(sampleRate >= 8000) { "sampleRate must be >= 8000 Hz, got $sampleRate" }
        require(durationSec in 2.0..16.0) { "durationSec must be in 2.0..16.0 s, got $durationSec" }
        if (pack == SoundPackId.OFF) return DoubleArray(0)

        val (left, right) = synthesizeStereoChannels(pack, durationSec, sampleRate)
        val loopFrames = left.size
        val interleaved = DoubleArray(loopFrames * CHANNELS)
        for (i in 0 until loopFrames) {
            interleaved[i * 2] = left[i]
            interleaved[i * 2 + 1] = right[i]
        }
        return interleaved
    }

    private fun synthesizeStereoChannels(
        pack: SoundPackId,
        durationSec: Double,
        sampleRate: Int,
    ): Pair<DoubleArray, DoubleArray> {
        require(sampleRate >= 8000) { "sampleRate must be >= 8000 Hz, got $sampleRate" }
        require(durationSec in 2.0..16.0) { "durationSec must be in 2.0..16.0 s, got $durationSec" }

        val resolved = if (pack == SoundPackId.AUTO) SoundPackId.GHOST else pack
        val loopFrames = (durationSec * sampleRate).roundToInt().coerceAtLeast(1024)
        val exactDur = loopFrames.toDouble() / sampleRate.toDouble()

        val left = DoubleArray(loopFrames)
        val right = DoubleArray(loopFrames)

        val seedBase = 0x414D_4249_454E_5400L xor (resolved.ordinal.toLong() * 0x9E3779B97F4A7C15UL.toLong())
        val noiseL = AudioDsp.SeededNoise(seedBase xor 0x1111_2222_3333_4444L)
        val noiseR = AudioDsp.SeededNoise(seedBase xor 0x5555_6666_7777_8888L)

        val targetRms: Double
        val highPassHz: Double
        val lowPassHz: Double
        when (resolved) {
            SoundPackId.GHOST, SoundPackId.AUTO, SoundPackId.OFF -> {
                renderGhostHarbor(left, right, exactDur, sampleRate, noiseL, noiseR)
                targetRms = 0.068
                highPassHz = 105.0
                lowPassHz = 4_800.0
            }
            SoundPackId.THREEDS -> {
                renderThreeDsHorizon(left, right, exactDur, sampleRate, noiseL, noiseR)
                targetRms = 0.064
                highPassHz = 110.0
                lowPassHz = 5_600.0
            }
            SoundPackId.OLED -> {
                renderOledMidnightHush(left, right, exactDur, sampleRate, noiseL, noiseR)
                targetRms = 0.048
                highPassHz = 150.0
                lowPassHz = 2_200.0
            }
            SoundPackId.NEON -> {
                renderNeonSynthDrone(left, right, exactDur, sampleRate, noiseL, noiseR)
                targetRms = 0.074
                highPassHz = 105.0
                lowPassHz = 6_800.0
            }
        }

        // Circularly primed gentle handheld speaker EQ + DC neutralization + low-RMS mastering
        masterSeamlessStereoLoop(
            left = left,
            right = right,
            sampleRate = sampleRate,
            highPassHz = highPassHz,
            lowPassHz = lowPassHz,
            targetRms = targetRms,
            peakCeiling = AMBIENT_PEAK_CEILING,
        )
        return left to right
    }

    // =========================================================================
    // PACK 1: GHOST — Nocturnal Sea & Harbor (D Dorian ocean swell + oak hull + sea-mist pad)
    // =========================================================================

    private fun renderGhostHarbor(
        left: DoubleArray,
        right: DoubleArray,
        durSec: Double,
        sr: Int,
        noiseL: AudioDsp.SeededNoise,
        noiseR: AudioDsp.SeededNoise,
    ) {
        val n = left.size
        // 1. Slow LFO-modulated filtered pink-noise ocean swell gently panning L <-> R
        addSeamlessOceanNoise(
            left = left,
            right = right,
            durSec = durSec,
            sr = sr,
            noiseL = noiseL,
            noiseR = noiseR,
            baseHz = 360.0,
            sweepHz = 210.0,
            q = 1.05,
            swellCycles = 1,
            panCycles = 1,
            panDepth = 0.40,
            amp = 0.62,
            brownian = false,
        )

        // 2. Subtle deep warm wooden-hull resonance (D3 + A3) & airy D-Dorian sea-mist pad
        val d3 = snapHz(D3, durSec)
        val a3 = snapHz(A3, durSec)
        val d4 = snapHz(D4, durSec)
        val f4 = snapHz(F4, durSec)
        val a4 = snapHz(A4, durSec)
        val e5 = snapHz(E5, durSec)
        val invSr = 1.0 / sr
        val twoPiInvDur = AudioDsp.TWO_PI / durSec
        val twoPi2InvDur = AudioDsp.TWO_PI * (2.0 / durSec)

        for (i in 0 until n) {
            val t = i * invSr
            val lfoPhase = twoPiInvDur * t
            val lfo1 = 0.5 + 0.5 * sin(lfoPhase)
            val lfo2 = 0.5 + 0.5 * sin(twoPi2InvDur * t + 1.1)
            val panCos = 0.15 * cos(lfoPhase)
            val panL = 0.85 + panCos
            val panR = 0.85 - panCos

            // Warm oak hull hum
            val hull = 0.24 * AudioDsp.sine(d3 * t) + 0.14 * AudioDsp.sine(a3 * t + 0.12)
            // D-Dorian sea-mist pad (D4 + F4 + A4 + E5 9th shimmer)
            val mistL = (0.20 + 0.10 * lfo1) * AudioDsp.sine(d4 * t) +
                (0.11 + 0.08 * lfo2) * AudioDsp.sine(f4 * t + 0.07) +
                (0.14 + 0.07 * lfo1) * AudioDsp.sine(a4 * t + 0.19) +
                (0.07 + 0.06 * lfo2) * AudioDsp.sine(e5 * t + 0.31)
            val mistR = (0.20 + 0.10 * lfo1) * AudioDsp.sine(d4 * t + 0.05) +
                (0.11 + 0.08 * lfo2) * AudioDsp.sine(f4 * t + 0.23) +
                (0.14 + 0.07 * lfo1) * AudioDsp.sine(a4 * t + 0.41) +
                (0.07 + 0.06 * lfo2) * AudioDsp.sine(e5 * t + 0.14)

            left[i] += (hull * 0.60 + mistL * panL) * 0.58
            right[i] += (hull * 0.60 + mistR * panR) * 0.58
        }
    }

    // =========================================================================
    // PACK 2: THREEDS — Handheld Horizon Pad (Airy C-major pentatonic shimmer + soft breeze)
    // =========================================================================

    private fun renderThreeDsHorizon(
        left: DoubleArray,
        right: DoubleArray,
        durSec: Double,
        sr: Int,
        noiseL: AudioDsp.SeededNoise,
        noiseR: AudioDsp.SeededNoise,
    ) {
        val n = left.size
        // 1. Soft sunlit breeze air layer
        addSeamlessOceanNoise(
            left = left,
            right = right,
            durSec = durSec,
            sr = sr,
            noiseL = noiseL,
            noiseR = noiseR,
            baseHz = 1150.0,
            sweepHz = 380.0,
            q = 0.85,
            swellCycles = 1,
            panCycles = 1,
            panDepth = 0.28,
            amp = 0.22,
            brownian = false,
        )

        // 2. Airy Wii/3DS-inspired warm C-major pentatonic shimmer pad (C4, G4, D5, E5, A5)
        val c4 = snapHz(C4, durSec)
        val g4 = snapHz(G4, durSec)
        val d5 = snapHz(D5, durSec)
        val e5 = snapHz(E5, durSec)
        val a5 = snapHz(A5, durSec)
        val invSr = 1.0 / sr

        for (i in 0 until n) {
            val t = i * invSr
            val b1 = 0.55 + 0.45 * sin(AudioDsp.TWO_PI * (1.0 / durSec) * t)
            val b2 = 0.55 + 0.45 * sin(AudioDsp.TWO_PI * (2.0 / durSec) * t + 1.8)
            val b3 = 0.50 + 0.50 * sin(AudioDsp.TWO_PI * (3.0 / durSec) * t + 0.7)

            val padL = 0.30 * AudioDsp.softTriangle(c4 * t, 0.86) * (0.78 + 0.22 * b1) +
                0.24 * AudioDsp.sine(g4 * t + 0.08) * (0.72 + 0.28 * b2) +
                0.15 * AudioDsp.sine(d5 * t + 0.17) * b1 +
                0.16 * AudioDsp.sine(e5 * t + 0.03) * b2 +
                0.06 * AudioDsp.sine(a5 * t + 0.29) * b3

            val padR = 0.30 * AudioDsp.softTriangle(c4 * t + 0.04, 0.86) * (0.78 + 0.22 * b1) +
                0.24 * AudioDsp.sine(g4 * t + 0.27) * (0.72 + 0.28 * b2) +
                0.15 * AudioDsp.sine(d5 * t + 0.39) * b2 +
                0.16 * AudioDsp.sine(e5 * t + 0.21) * b1 +
                0.06 * AudioDsp.sine(a5 * t + 0.52) * b3

            left[i] += padL * 0.72
            right[i] += padR * 0.72
        }
    }

    // =========================================================================
    // PACK 3: OLED — Midnight Velvet Hush (Dark brownian room air + whisper F-fifth breath)
    // =========================================================================

    private fun renderOledMidnightHush(
        left: DoubleArray,
        right: DoubleArray,
        durSec: Double,
        sr: Int,
        noiseL: AudioDsp.SeededNoise,
        noiseR: AudioDsp.SeededNoise,
    ) {
        val n = left.size
        // 1. Ultra-gentle dark brownian/low-pass midnight room air (cleanly above micro-speaker rattle band)
        addSeamlessOceanNoise(
            left = left,
            right = right,
            durSec = durSec,
            sr = sr,
            noiseL = noiseL,
            noiseR = noiseR,
            baseHz = 250.0,
            sweepHz = 55.0,
            q = 0.85,
            swellCycles = 1,
            panCycles = 1,
            panDepth = 0.14,
            amp = 0.52,
            brownian = true,
        )

        // 2. Whisper-soft warm F-fifth sub-harmonic breath (F3 + C4 + whisper F4, pure sines only)
        val f3 = snapHz(F3, durSec)
        val c4 = snapHz(C4, durSec)
        val f4 = snapHz(F4, durSec)
        val invSr = 1.0 / sr

        for (i in 0 until n) {
            val t = i * invSr
            val breath = 0.60 + 0.40 * sin(AudioDsp.TWO_PI * (1.0 / durSec) * t)
            val breathAlt = 0.65 + 0.35 * cos(AudioDsp.TWO_PI * (1.0 / durSec) * t)

            val toneL = (0.34 * AudioDsp.sine(f3 * t) + 0.22 * AudioDsp.sine(c4 * t + 0.09)) * breath +
                0.07 * AudioDsp.sine(f4 * t + 0.18) * breathAlt
            val toneR = (0.34 * AudioDsp.sine(f3 * t + 0.04) + 0.22 * AudioDsp.sine(c4 * t + 0.26)) * breathAlt +
                0.07 * AudioDsp.sine(f4 * t + 0.33) * breath

            left[i] += toneL * 0.55
            right[i] += toneR * 0.55
        }
    }

    // =========================================================================
    // PACK 4: NEON — Dreamy Lo-Fi Synth Drone (Detuned E fifth/ninth analog pad + resonant LPF sweep)
    // =========================================================================

    private fun renderNeonSynthDrone(
        left: DoubleArray,
        right: DoubleArray,
        durSec: Double,
        sr: Int,
        noiseL: AudioDsp.SeededNoise,
        noiseR: AudioDsp.SeededNoise,
    ) {
        val n = left.size
        // Exact integer-cycle detuned stereo analog voices (±1 cycle/loop = ±0.167 Hz lush chorus!)
        val e3L = snapHz(E3, durSec)
        val e3R = snapHz(E3, durSec, cycleOffset = 1)
        val b3L = snapHz(B3, durSec)
        val b3R = snapHz(B3, durSec, cycleOffset = -1)
        val e4L = snapHz(E4, durSec, cycleOffset = -1)
        val e4R = snapHz(E4, durSec, cycleOffset = 1)
        val gs4L = snapHz(GS4, durSec)
        val gs4R = snapHz(GS4, durSec, cycleOffset = 1)
        val b4L = snapHz(B4, durSec, cycleOffset = -1)
        val b4R = snapHz(B4, durSec, cycleOffset = 1)
        val fs5L = snapHz(FS5, durSec)
        val fs5R = snapHz(FS5, durSec, cycleOffset = 1)

        // Synthesize directly into zeroed [left]/[right] with precomputed harmonic weights
        // (eliminating both 4.24 MiB transient rawL/rawR arrays and ~34M redundant trig calls).
        val sawE3L = AudioDsp.sawHarmonicWeights(maxHarmonics = 6, freqHz = e3L, sampleRate = sr)
        val sawE3R = AudioDsp.sawHarmonicWeights(maxHarmonics = 6, freqHz = e3R, sampleRate = sr)
        val pulseB3L = AudioDsp.pulseHarmonicWeights(duty = 0.42, maxHarmonics = 5, freqHz = b3L, sampleRate = sr)
        val pulseB3R = AudioDsp.pulseHarmonicWeights(duty = 0.42, maxHarmonics = 5, freqHz = b3R, sampleRate = sr)
        val sawE4L = AudioDsp.sawHarmonicWeights(maxHarmonics = 7, freqHz = e4L, sampleRate = sr)
        val sawE4R = AudioDsp.sawHarmonicWeights(maxHarmonics = 7, freqHz = e4R, sampleRate = sr)
        val pulseGs4L = AudioDsp.pulseHarmonicWeights(duty = 0.32, maxHarmonics = 6, freqHz = gs4L, sampleRate = sr)
        val pulseGs4R = AudioDsp.pulseHarmonicWeights(duty = 0.32, maxHarmonics = 6, freqHz = gs4R, sampleRate = sr)
        val sawB4L = AudioDsp.sawHarmonicWeights(maxHarmonics = 6, freqHz = b4L, sampleRate = sr)
        val sawB4R = AudioDsp.sawHarmonicWeights(maxHarmonics = 6, freqHz = b4R, sampleRate = sr)

        val invSr = 1.0 / sr
        val shimmerRate = AudioDsp.TWO_PI * (2.0 / durSec)

        for (i in 0 until n) {
            val t = i * invSr
            val shimmer = 0.55 + 0.45 * sin(shimmerRate * t)

            val bassL = AudioDsp.evalSawHarmonics(e3L * t, sawE3L) * 0.26 +
                AudioDsp.evalPulseHarmonics(b3L * t, pulseB3L) * 0.18
            val bassR = AudioDsp.evalSawHarmonics(e3R * t + 0.13, sawE3R) * 0.26 +
                AudioDsp.evalPulseHarmonics(b3R * t + 0.21, pulseB3R) * 0.18

            val midL = AudioDsp.evalSawHarmonics(e4L * t, sawE4L) * 0.24 +
                AudioDsp.evalPulseHarmonics(gs4L * t, pulseGs4L) * 0.14 +
                AudioDsp.evalSawHarmonics(b4L * t, sawB4L) * 0.18 +
                AudioDsp.sine(fs5L * t) * 0.12 * shimmer

            val midR = AudioDsp.evalSawHarmonics(e4R * t + 0.09, sawE4R) * 0.24 +
                AudioDsp.evalPulseHarmonics(gs4R * t + 0.17, pulseGs4R) * 0.14 +
                AudioDsp.evalSawHarmonics(b4R * t + 0.25, sawB4R) * 0.18 +
                AudioDsp.sine(fs5R * t + 0.31) * 0.12 * shimmer

            left[i] = bassL + midL
            right[i] = bassR + midR
        }

        // Slow circular-primed resonant LPF sweep (520 Hz <-> 1,850 Hz) for dreamy lo-fi analog warmth
        applyCircularSweepLowPass(left, durSec, sr, minHz = 520.0, maxHz = 1850.0, q = 1.55, phaseOffset = 0.0)
        applyCircularSweepLowPass(right, durSec, sr, minHz = 520.0, maxHz = 1850.0, q = 1.55, phaseOffset = 0.35)

        for (i in 0 until n) {
            left[i] *= 0.78
            right[i] *= 0.78
        }

        // Subtle warm lo-fi synth air
        addSeamlessOceanNoise(
            left = left,
            right = right,
            durSec = durSec,
            sr = sr,
            noiseL = noiseL,
            noiseR = noiseR,
            baseHz = 1650.0,
            sweepHz = 450.0,
            q = 1.1,
            swellCycles = 2,
            panCycles = 1,
            panDepth = 0.25,
            amp = 0.14,
            brownian = false,
        )
    }

    // =========================================================================
    // Seamless Loop Helpers
    // =========================================================================

    /** Snaps [targetHz] to an exact integer number of cycles over [durSec] (plus optional detune [cycleOffset]). */
    private fun snapHz(targetHz: Double, durSec: Double, cycleOffset: Int = 0): Double {
        val cycles = ((targetHz * durSec).roundToInt() + cycleOffset).coerceAtLeast(1)
        return cycles.toDouble() / durSec
    }

    /**
     * Synthesizes stereo LFO-modulated filtered pink/brownian noise with filter warmup
     * and constant-power circular overlap-add crossfade so loop wrap-around has exact
     * 1-sample continuity and zero volume dip across the seam.
     */
    private fun addSeamlessOceanNoise(
        left: DoubleArray,
        right: DoubleArray,
        durSec: Double,
        sr: Int,
        noiseL: AudioDsp.SeededNoise,
        noiseR: AudioDsp.SeededNoise,
        baseHz: Double,
        sweepHz: Double,
        q: Double,
        swellCycles: Int,
        panCycles: Int,
        panDepth: Double,
        amp: Double,
        brownian: Boolean,
    ) {
        val loopFrames = left.size
        val overlapFrames = (NOISE_OVERLAP_SEC * sr).roundToInt().coerceIn(256, loopFrames / 2)
        val warmupFrames = (FILTER_WARMUP_SEC * sr).roundToInt().coerceAtLeast(128)
        val totalFrames = warmupFrames + loopFrames + overlapFrames

        val bpL = AudioDsp.BiquadFilter.bandPass(baseHz, q, sr)
        val bpR = AudioDsp.BiquadFilter.bandPass(baseHz, q, sr)
        val invSr = 1.0 / sr
        val swellOmega = AudioDsp.TWO_PI * (swellCycles / durSec)
        val panOmega = AudioDsp.TWO_PI * (panCycles / durSec)
        val sameCycles = swellCycles == panCycles
        val halfPiInvOverlap = (0.5 * PI) / overlapFrames.toDouble()
        val updateStep = 64
        var brownStateL = 0.0
        var brownStateR = 0.0

        // Stream filtered noise directly into [left]/[right] with inline constant-power
        // circular overlap-add (eliminating 5.08 MiB of transient rawL/rawR arrays).
        for (i in 0 until totalFrames) {
            val logicalIdx = i - warmupFrames
            val wrappedIdx = if (logicalIdx >= 0) {
                if (logicalIdx < loopFrames) logicalIdx else logicalIdx - loopFrames
            } else {
                ((logicalIdx % loopFrames) + loopFrames) % loopFrames
            }
            val tMod = wrappedIdx * invSr
            if ((i and (updateStep - 1)) == 0) {
                val swell = sin(swellOmega * tMod)
                val swellR = sin(swellOmega * tMod + 0.55)
                val fcL = (baseHz + sweepHz * swell).coerceIn(80.0, sr * 0.40)
                val fcR = (baseHz + sweepHz * swellR).coerceIn(80.0, sr * 0.40)
                bpL.updateBandPass(fcL, q, sr)
                bpR.updateBandPass(fcR, q, sr)
            }

            var sL = noiseL.nextPink()
            var sR = noiseR.nextPink()
            if (brownian) {
                brownStateL = 0.965 * brownStateL + 0.035 * sL
                brownStateR = 0.965 * brownStateR + 0.035 * sR
                sL = brownStateL * 3.8
                sR = brownStateR * 3.8
            }

            val fL = bpL.processSample(sL)
            val fR = bpR.processSample(sR)

            if (logicalIdx >= 0) {
                val swellSin = sin(swellOmega * tMod)
                val swellEnv = 0.72 + 0.28 * swellSin
                val panOsc = panDepth * if (sameCycles) swellSin else sin(panOmega * tMod)
                val gL = (1.0 - panOsc * 0.5) * swellEnv * amp
                val gR = (1.0 + panOsc * 0.5) * swellEnv * amp
                when {
                    logicalIdx < overlapFrames -> {
                        val wIn = sin(logicalIdx * halfPiInvOverlap)
                        left[logicalIdx] += fL * gL * wIn
                        right[logicalIdx] += fR * gR * wIn
                    }
                    logicalIdx < loopFrames -> {
                        left[logicalIdx] += fL * gL
                        right[logicalIdx] += fR * gR
                    }
                    else -> {
                        val k = logicalIdx - loopFrames
                        val wOut = cos(k * halfPiInvOverlap)
                        left[k] += fL * gL * wOut
                        right[k] += fR * gR * wOut
                    }
                }
            }
        }
    }

    /**
     * Circularly-primed stateful resonant low-pass sweep so both filter coefficients and
     * delay state `(z1, z2)` stay continuous across LFO updates and across `frame N -> 0`.
     */
    private fun applyCircularSweepLowPass(
        buf: DoubleArray,
        durSec: Double,
        sr: Int,
        minHz: Double,
        maxHz: Double,
        q: Double,
        phaseOffset: Double,
    ) {
        val n = buf.size
        val warmup = minOf(n, (FILTER_WARMUP_SEC * sr).roundToInt().coerceAtLeast(256))
        val invSr = 1.0 / sr
        val updateStep = 64
        val midHz = 0.5 * (minHz + maxHz)
        val halfSpan = 0.5 * (maxHz - minHz)

        val filter = SweepableLowPass(midHz, q, sr)
        val startWarm = n - warmup
        for (k in 0 until warmup) {
            val idx = startWarm + k
            if (k % updateStep == 0) {
                val fc = midHz + halfSpan * sin(AudioDsp.TWO_PI * (1.0 / durSec) * (idx * invSr) + phaseOffset)
                filter.update(fc, q, sr)
            }
            filter.processSample(buf[idx])
        }
        for (i in 0 until n) {
            if (i % updateStep == 0) {
                val fc = midHz + halfSpan * sin(AudioDsp.TWO_PI * (1.0 / durSec) * (i * invSr) + phaseOffset)
                filter.update(fc, q, sr)
            }
            buf[i] = filter.processSample(buf[i])
        }
    }

    /** Direct Form II Transposed RBJ low-pass filter supporting click-free cutoff sweeps. */
    private class SweepableLowPass(cutoffHz: Double, q: Double, sr: Int) {
        private var b0 = 0.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var z1 = 0.0
        private var z2 = 0.0

        init {
            update(cutoffHz, q, sr)
        }

        fun update(cutoffHz: Double, q: Double, sr: Int) {
            val fc = cutoffHz.coerceIn(20.0, sr * 0.42)
            val safeQ = q.coerceIn(0.3, 12.0)
            val w0 = AudioDsp.TWO_PI * fc / sr
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * safeQ)
            val a0 = 1.0 + alpha
            b0 = ((1.0 - cosW0) * 0.5) / a0
            b1 = (1.0 - cosW0) / a0
            b2 = ((1.0 - cosW0) * 0.5) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun processSample(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    /**
     * Applies circularly-primed handheld HPF/LPF, per-channel DC removal, shared stereo
     * low-RMS normalization, and smooth `tanh` soft-limiting while preserving exact
     * loop wrap-around continuity (`last -> first`).
     */
    private fun masterSeamlessStereoLoop(
        left: DoubleArray,
        right: DoubleArray,
        sampleRate: Int,
        highPassHz: Double,
        lowPassHz: Double,
        targetRms: Double,
        peakCeiling: Double,
    ) {
        val n = left.size
        if (n == 0) return

        // 1. Sanitize non-finite samples & strip pre-filter DC
        sanitizeAndStripDc(left)
        sanitizeAndStripDc(right)

        // 2. Circularly-primed HPF & LPF so delay states match across loop boundary
        circularBiquadInPlace(left, sampleRate) { AudioDsp.BiquadFilter.highPass(highPassHz, sampleRate = sampleRate) }
        circularBiquadInPlace(right, sampleRate) { AudioDsp.BiquadFilter.highPass(highPassHz, sampleRate = sampleRate) }
        circularBiquadInPlace(left, sampleRate) { AudioDsp.BiquadFilter.lowPass(lowPassHz, q = 0.65, sampleRate = sampleRate) }
        circularBiquadInPlace(right, sampleRate) { AudioDsp.BiquadFilter.lowPass(lowPassHz, q = 0.65, sampleRate = sampleRate) }

        // 3. Shared stereo RMS normalization to gentle ambient level (~ -24 dBFS)
        val rmsL = AudioMastering.computeRms(left)
        val rmsR = AudioMastering.computeRms(right)
        val combinedRms = sqrt(0.5 * (rmsL * rmsL + rmsR * rmsR))
        if (combinedRms > 1e-9 && targetRms > 0.0) {
            val gain = (targetRms / combinedRms).coerceIn(0.05, 6.0)
            for (i in 0 until n) {
                left[i] *= gain
                right[i] *= gain
            }
        }

        // 4. Symmetric smooth tanh soft-limiting (strictly <= peakCeiling) + final DC strip
        for (i in 0 until n) {
            left[i] = AudioMastering.softLimitSample(left[i], ceiling = peakCeiling, kneeRatio = 0.72)
            right[i] = AudioMastering.softLimitSample(right[i], ceiling = peakCeiling, kneeRatio = 0.72)
        }
        sanitizeAndStripDc(left)
        sanitizeAndStripDc(right)
        // Final safety clamp after DC subtraction
        for (i in 0 until n) {
            left[i] = AudioMastering.softLimitSample(left[i], ceiling = peakCeiling, kneeRatio = 0.85)
            right[i] = AudioMastering.softLimitSample(right[i], ceiling = peakCeiling, kneeRatio = 0.85)
        }
    }

    private fun circularBiquadInPlace(
        buf: DoubleArray,
        sampleRate: Int,
        factory: () -> AudioDsp.BiquadFilter,
    ) {
        val n = buf.size
        if (n < 4) return
        val warmup = minOf(n, (FILTER_WARMUP_SEC * sampleRate).roundToInt().coerceAtLeast(256))
        val filter = factory()
        val start = n - warmup
        for (i in start until n) {
            filter.processSample(buf[i])
        }
        for (i in 0 until n) {
            buf[i] = filter.processSample(buf[i])
        }
    }

    private fun sanitizeAndStripDc(buf: DoubleArray) {
        var sum = 0.0
        for (i in buf.indices) {
            val v = buf[i]
            val safe = if (v.isFinite()) v else 0.0
            buf[i] = safe
            sum += safe
        }
        val mean = sum / buf.size.coerceAtLeast(1)
        if (kotlin.math.abs(mean) > 1e-12) {
            for (i in buf.indices) {
                buf[i] -= mean
            }
        }
    }
}

/**
 * Sample-accurate, click-free amplitude envelope for streaming ambient playback.
 *
 * Manages three orthogonal smooth half-cosine ramps:
 * - **Playback fade** (`600 ms` fade-in on start/resume, `220 ms` fade-out before pause/stop)
 * - **Focus / video ducking** (`180 ms` smooth ramp toward [AudioPolicy.ambientDuckFactor])
 * - **Volume gain** (`80 ms` anti-zipper ramp on live volume adjustments)
 *
 * Advancing frame-by-frame guarantees bit-identical, discontinuity-free output across
 * arbitrary streaming chunk sizes and mid-ramp target reversals.
 */
class AmbientFadeEnvelope(
    private val sampleRate: Int = AmbientSynthesizer.SAMPLE_RATE,
    initialVolumeGain: Float = 1.0f,
) {
    init {
        require(sampleRate >= 8000) { "sampleRate must be >= 8000 Hz, got $sampleRate" }
    }

    private class SmoothRamp(initialValue: Double) {
        var current: Double = initialValue
            private set
        var target: Double = initialValue
            private set
        private var start: Double = initialValue
        private var totalFrames: Int = 0
        private var elapsedFrames: Int = 0

        val isComplete: Boolean
            get() = elapsedFrames >= totalFrames

        fun setTarget(newTarget: Double, durationFrames: Int) {
            val clamped = newTarget.coerceIn(0.0, 1.0)
            if (durationFrames <= 0) {
                current = clamped
                start = clamped
                target = clamped
                totalFrames = 0
                elapsedFrames = 0
                return
            }
            if (kotlin.math.abs(clamped - target) <= 1e-7 && !isComplete) {
                return
            }
            if (kotlin.math.abs(clamped - current) <= 1e-7) {
                current = clamped
                start = clamped
                target = clamped
                totalFrames = 0
                elapsedFrames = 0
                return
            }
            start = current
            target = clamped
            totalFrames = durationFrames
            elapsedFrames = 0
        }

        fun reset(value: Double) {
            val clamped = value.coerceIn(0.0, 1.0)
            current = clamped
            start = clamped
            target = clamped
            totalFrames = 0
            elapsedFrames = 0
        }

        fun nextFrame(): Double {
            if (elapsedFrames >= totalFrames) {
                current = target
                return current
            }
            elapsedFrames++
            val p = elapsedFrames.toDouble() / totalFrames.toDouble()
            val eased = 0.5 * (1.0 - cos(PI * p))
            current = start + (target - start) * eased
            if (elapsedFrames >= totalFrames) {
                current = target
            }
            return current
        }
    }

    private val playbackRamp = SmoothRamp(0.0)
    private val duckRamp = SmoothRamp(1.0)
    private val volumeRamp = SmoothRamp(
        if (initialVolumeGain.isFinite()) initialVolumeGain.coerceIn(0f, 1f).toDouble() else 0.0,
    )

    /** Current playback fade multiplier (`0.0..1.0`). */
    val playbackGain: Double get() = playbackRamp.current

    /** Current ducking multiplier (`0.25..1.0`). */
    val duckGain: Double get() = duckRamp.current

    /** Current linear volume multiplier (`0.0..1.0`). */
    val volumeGain: Double get() = volumeRamp.current

    /** Current combined linear gain (`playbackGain * duckGain * volumeGain`). */
    val currentGain: Double get() = (playbackRamp.current * duckRamp.current * volumeRamp.current).coerceIn(0.0, 1.0)

    /** True while actively fading out toward `0.0`. */
    val isFadingOut: Boolean
        get() = playbackRamp.target <= 1e-6 && playbackRamp.current > 1e-6

    /** True when playback fade has settled at silence (`0.0`). */
    val isSilent: Boolean
        get() = playbackRamp.target <= 1e-6 && playbackRamp.current <= 1e-6

    /** True when all three ramps (playback fade, ducking, volume) have settled at their targets. */
    val isSettled: Boolean
        get() = playbackRamp.isComplete && duckRamp.isComplete && volumeRamp.isComplete

    /** Starts a smooth half-cosine fade-in toward `1.0` (default `600 ms`). */
    fun fadeIn(durationMs: Int = AmbientSynthesizer.FADE_IN_MS) {
        playbackRamp.setTarget(1.0, msToFrames(durationMs))
    }

    /** Starts a smooth half-cosine fade-out toward `0.0` (default `220 ms`). */
    fun fadeOut(durationMs: Int = AmbientSynthesizer.FADE_OUT_MS) {
        playbackRamp.setTarget(0.0, msToFrames(durationMs))
    }

    /** Smoothly ramps focus/video ducking toward [AudioPolicy.ambientDuckFactor] (default `180 ms`). */
    fun setDucked(ducked: Boolean, durationMs: Int = AmbientSynthesizer.DUCK_RAMP_MS) {
        val target = AudioPolicy.ambientDuckFactor(ducked).toDouble()
        duckRamp.setTarget(target, msToFrames(durationMs))
    }

    /** Smoothly ramps master ambient volume gain (`0f..1f`, default `80 ms`). */
    fun setVolumeGain(gain: Float, durationMs: Int = AmbientSynthesizer.VOLUME_RAMP_MS) {
        val safe = if (gain.isFinite()) gain.coerceIn(0f, 1f).toDouble() else 0.0
        volumeRamp.setTarget(safe, msToFrames(durationMs))
    }

    /** Resets playback fade immediately to silence (`0.0`) before starting a fresh fade-in. */
    fun resetSilent() {
        playbackRamp.reset(0.0)
    }

    /** Advances all envelope ramps by 1 stereo frame and returns the combined linear gain. */
    fun nextFrameGain(): Double {
        val p = playbackRamp.nextFrame()
        val d = duckRamp.nextFrame()
        val v = volumeRamp.nextFrame()
        return (p * d * v).coerceIn(0.0, 1.0)
    }

    /** Scales interleaved stereo PCM-16 frames in-place using frame-accurate envelope gains. */
    fun applyStereoInPlace(
        interleavedPcm: ShortArray,
        frameOffset: Int = 0,
        frameCount: Int = (interleavedPcm.size / 2) - frameOffset,
    ) {
        val safeStart = frameOffset.coerceAtLeast(0)
        val maxFrames = (interleavedPcm.size / 2) - safeStart
        val count = frameCount.coerceIn(0, maxFrames)
        var idx = safeStart * 2
        repeat(count) {
            val g = nextFrameGain()
            val l = (interleavedPcm[idx] * g).roundToInt().coerceIn(-32767, 32767).toShort()
            val r = (interleavedPcm[idx + 1] * g).roundToInt().coerceIn(-32767, 32767).toShort()
            interleavedPcm[idx] = l
            interleavedPcm[idx + 1] = r
            idx += 2
        }
    }

    /**
     * Copies stereo frames from circular [loopStereoPcm] starting at [loopFrameCursor] into
     * [outChunk], applying sample-accurate envelope scaling, and returns the updated cursor.
     */
    fun renderLoopChunk(
        loopStereoPcm: ShortArray,
        loopFrameCursor: Int,
        outChunk: ShortArray,
        chunkFrames: Int = outChunk.size / 2,
    ): Int {
        val loopFrames = loopStereoPcm.size / 2
        val safeFrames = chunkFrames.coerceIn(0, outChunk.size / 2)
        if (loopFrames <= 0 || safeFrames <= 0) {
            for (i in 0 until safeFrames * 2) {
                outChunk[i] = 0
                if (i % 2 == 1) nextFrameGain()
            }
            return 0
        }
        var cursor = ((loopFrameCursor % loopFrames) + loopFrames) % loopFrames
        var outIdx = 0
        if (isSettled) {
            val g = currentGain
            if (g <= 0.0) {
                outChunk.fill(0, 0, safeFrames * 2)
                return (cursor + safeFrames) % loopFrames
            }
            var remaining = safeFrames
            while (remaining > 0) {
                val span = minOf(remaining, loopFrames - cursor)
                var srcIdx = cursor * 2
                var i = 0
                while (i < span) {
                    outChunk[outIdx] = (loopStereoPcm[srcIdx] * g).roundToInt().coerceIn(-32767, 32767).toShort()
                    outChunk[outIdx + 1] = (loopStereoPcm[srcIdx + 1] * g).roundToInt().coerceIn(-32767, 32767).toShort()
                    srcIdx += 2
                    outIdx += 2
                    i++
                }
                cursor += span
                if (cursor >= loopFrames) cursor = 0
                remaining -= span
            }
            return cursor
        }
        repeat(safeFrames) {
            val g = nextFrameGain()
            val srcIdx = cursor * 2
            outChunk[outIdx] = (loopStereoPcm[srcIdx] * g).roundToInt().coerceIn(-32767, 32767).toShort()
            outChunk[outIdx + 1] = (loopStereoPcm[srcIdx + 1] * g).roundToInt().coerceIn(-32767, 32767).toShort()
            outIdx += 2
            cursor++
            if (cursor >= loopFrames) cursor = 0
        }
        return cursor
    }

    private fun msToFrames(ms: Int): Int =
        if (ms <= 0) 0 else ((ms / 1000.0) * sampleRate).roundToInt().coerceAtLeast(1)
}
