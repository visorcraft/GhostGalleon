package com.visorcraft.ghostgalleon.audio

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure Kotlin floating-point DSP primitives running at 44,100 Hz.
 *
 * Zero Android dependencies so synthesis and mastering stay fast,
 * deterministic, and host-testable under standard JVM unit tests.
 */
object AudioDsp {

    const val SAMPLE_RATE: Int = 44100
    const val TWO_PI: Double = 2.0 * PI

    /** Pure sine oscillator for normalized phase in cycles (`0.0..1.0`, wrapping seamlessly). */
    fun sine(phaseCycles: Double): Double = sin(TWO_PI * phaseCycles)

    /**
     * Alias-free soft triangle oscillator.
     *
     * Uses `(2 / pi) * asin(k * sin(2 * pi * p))` with `k = 0.96` to produce a warm,
     * rounded triangle spectrum without sharp corners or high-frequency aliasing.
     */
    fun softTriangle(phaseCycles: Double, softness: Double = 0.96): Double {
        val k = softness.coerceIn(0.1, 0.999)
        return (2.0 / PI) * asin(k * sin(TWO_PI * phaseCycles))
    }

    /**
     * Band-limited warm pulse oscillator built via Lanczos-windowed Fourier harmonics.
     *
     * Limiting harmonics against Nyquist prevents metallic foldover aliasing on
     * handheld speakers while keeping classic retro console character.
     */
    fun warmPulse(
        phaseCycles: Double,
        duty: Double = 0.5,
        maxHarmonics: Int = 7,
        freqHz: Double = 440.0,
        sampleRate: Int = SAMPLE_RATE,
    ): Double {
        val d = duty.coerceIn(0.08, 0.92)
        val nyquistLimit = if (freqHz > 1.0) ((sampleRate * 0.45) / freqHz).toInt() else 1
        val harmonics = minOf(maxHarmonics.coerceAtLeast(1), nyquistLimit.coerceAtLeast(1))
        var acc = 0.0
        val normFactor = 1.0 / (2.0 * sin(PI * d).coerceAtLeast(0.15) / PI).coerceAtLeast(0.5)
        for (k in 1..harmonics) {
            val sigmaArg = PI * k / (harmonics + 1.0)
            val sigma = sin(sigmaArg) / sigmaArg
            val amp = (2.0 / (k * PI)) * sin(k * PI * d) * sigma
            acc += amp * cos(TWO_PI * k * phaseCycles)
        }
        return (acc * normFactor).coerceIn(-1.25, 1.25)
    }

    /** Two-operator phase-modulation (FM) bell/chime oscillator. */
    fun fmTone(
        carrierPhaseCycles: Double,
        modPhaseCycles: Double,
        modIndex: Double,
    ): Double = sin(TWO_PI * carrierPhaseCycles + modIndex * sin(TWO_PI * modPhaseCycles))

    /**
     * Deterministic SplitMix64/Xorshift64* PRNG producing repeatable white noise
     * and Paul Kellet 7-stage Voss-McCartney pink noise (`-3 dB/octave`).
     */
    class SeededNoise(seed: Long) {
        private var state: Long = if (seed == 0L) 0x9E3779B97F4A7C15UL.toLong() else seed
        private var b0: Double = 0.0
        private var b1: Double = 0.0
        private var b2: Double = 0.0
        private var b3: Double = 0.0
        private var b4: Double = 0.0
        private var b5: Double = 0.0
        private var b6: Double = 0.0

        /** Deterministic white noise sample in `[-1.0, 1.0]`. */
        fun nextWhite(): Double {
            var x = state
            x = x xor (x ushr 12)
            x = x xor (x shl 25)
            x = x xor (x ushr 27)
            state = x
            val raw = (x * 0x2545F4914F6CDD1DL) ushr 11
            val unit = raw.toDouble() * (1.0 / (1L shl 53).toDouble())
            return unit * 2.0 - 1.0
        }

        /** Deterministic pink noise (`-3 dB/octave`) normalized near `[-1.0, 1.0]`. */
        fun nextPink(): Double {
            val white = nextWhite()
            b0 = 0.99886 * b0 + white * 0.0555179
            b1 = 0.99332 * b1 + white * 0.0750759
            b2 = 0.96900 * b2 + white * 0.1538520
            b3 = 0.86650 * b3 + white * 0.3104856
            b4 = 0.55000 * b4 + white * 0.5329522
            b5 = -0.7616 * b5 - white * 0.0168980
            val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
            b6 = white * 0.115926
            return pink.coerceIn(-1.0, 1.0)
        }
    }

    /**
     * Smooth ADSR amplitude envelope in `[0.0, 1.0]`.
     *
     * Attack and release use half-cosine curves so transient slopes stay click-free.
     */
    fun adsr(
        timeSec: Double,
        attackSec: Double,
        decaySec: Double,
        sustainLevel: Double,
        sustainEndSec: Double,
        releaseSec: Double,
    ): Double {
        if (timeSec < 0.0) return 0.0
        val sus = sustainLevel.coerceIn(0.0, 1.0)
        val atk = attackSec.coerceAtLeast(0.0)
        val dec = decaySec.coerceAtLeast(0.0)
        val rel = releaseSec.coerceAtLeast(0.0)
        val gateEnd = sustainEndSec.coerceAtLeast(atk + dec)

        return when {
            atk > 0.0 && timeSec < atk -> {
                val p = timeSec / atk
                0.5 * (1.0 - cos(PI * p))
            }
            dec > 0.0 && timeSec < atk + dec -> {
                val p = (timeSec - atk) / dec
                sus + (1.0 - sus) * (1.0 - p) * (1.0 - p)
            }
            timeSec < gateEnd -> sus
            rel > 0.0 && timeSec < gateEnd + rel -> {
                val p = (timeSec - gateEnd) / rel
                sus * 0.5 * (1.0 + cos(PI * p))
            }
            else -> 0.0
        }
    }

    /** Percussive attack + exponential decay envelope in `[0.0, 1.0]`. */
    fun expDecay(
        timeSec: Double,
        attackSec: Double,
        decayTauSec: Double,
    ): Double {
        if (timeSec < 0.0) return 0.0
        val atk = attackSec.coerceAtLeast(0.0)
        val tau = decayTauSec.coerceAtLeast(1e-4)
        return if (atk > 0.0 && timeSec < atk) {
            0.5 * (1.0 - cos(PI * (timeSec / atk)))
        } else {
            exp(-(timeSec - atk) / tau)
        }
    }

    /**
     * Smooth frequency glide from [startHz] to [endHz] over normalized [progress] (`0..1`).
     * Uses musical logarithmic interpolation by default (`exponential = true`).
     */
    fun freqGlide(
        startHz: Double,
        endHz: Double,
        progress: Double,
        exponential: Boolean = true,
    ): Double {
        val p = progress.coerceIn(0.0, 1.0)
        return if (exponential && startHz > 0.0 && endHz > 0.0) {
            startHz * (endHz / startHz).pow(p)
        } else {
            startHz + (endHz - startHz) * p
        }
    }

    /**
     * Second-order biquad filter (RBJ Audio EQ Cookbook, Direct Form II Transposed).
     */
    class BiquadFilter private constructor(
        private var b0: Double,
        private var b1: Double,
        private var b2: Double,
        private var a1: Double,
        private var a2: Double,
    ) {
        private var z1: Double = 0.0
        private var z2: Double = 0.0

        fun processSample(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun processInPlace(buffer: DoubleArray) {
            for (i in buffer.indices) {
                buffer[i] = processSample(buffer[i])
            }
        }

        /** Updates band-pass coefficients smoothly without resetting delay state (click-free sweeps). */
        fun updateBandPass(
            centerHz: Double,
            q: Double = 1.0,
            sampleRate: Int = SAMPLE_RATE,
        ) {
            val fc = centerHz.coerceIn(1.0, sampleRate * 0.45)
            val safeQ = q.coerceIn(0.1, 25.0)
            val w0 = TWO_PI * fc / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * safeQ)
            val a0 = 1.0 + alpha
            b0 = alpha / a0
            b1 = 0.0
            b2 = -alpha / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        companion object {
            const val BUTTERWORTH_Q: Double = 0.7071067811865476

            fun highPass(
                cutoffHz: Double,
                q: Double = BUTTERWORTH_Q,
                sampleRate: Int = SAMPLE_RATE,
            ): BiquadFilter {
                val fc = cutoffHz.coerceIn(1.0, sampleRate * 0.45)
                val safeQ = q.coerceIn(0.1, 20.0)
                val w0 = TWO_PI * fc / sampleRate
                val cosW0 = cos(w0)
                val alpha = sin(w0) / (2.0 * safeQ)
                val a0 = 1.0 + alpha
                return BiquadFilter(
                    b0 = ((1.0 + cosW0) * 0.5) / a0,
                    b1 = -(1.0 + cosW0) / a0,
                    b2 = ((1.0 + cosW0) * 0.5) / a0,
                    a1 = (-2.0 * cosW0) / a0,
                    a2 = (1.0 - alpha) / a0,
                )
            }

            fun lowPass(
                cutoffHz: Double,
                q: Double = BUTTERWORTH_Q,
                sampleRate: Int = SAMPLE_RATE,
            ): BiquadFilter {
                val fc = cutoffHz.coerceIn(1.0, sampleRate * 0.45)
                val safeQ = q.coerceIn(0.1, 20.0)
                val w0 = TWO_PI * fc / sampleRate
                val cosW0 = cos(w0)
                val alpha = sin(w0) / (2.0 * safeQ)
                val a0 = 1.0 + alpha
                return BiquadFilter(
                    b0 = ((1.0 - cosW0) * 0.5) / a0,
                    b1 = (1.0 - cosW0) / a0,
                    b2 = ((1.0 - cosW0) * 0.5) / a0,
                    a1 = (-2.0 * cosW0) / a0,
                    a2 = (1.0 - alpha) / a0,
                )
            }

            fun bandPass(
                centerHz: Double,
                q: Double = 1.0,
                sampleRate: Int = SAMPLE_RATE,
            ): BiquadFilter {
                val fc = centerHz.coerceIn(1.0, sampleRate * 0.45)
                val safeQ = q.coerceIn(0.1, 25.0)
                val w0 = TWO_PI * fc / sampleRate
                val cosW0 = cos(w0)
                val alpha = sin(w0) / (2.0 * safeQ)
                val a0 = 1.0 + alpha
                return BiquadFilter(
                    b0 = alpha / a0,
                    b1 = 0.0,
                    b2 = -alpha / a0,
                    a1 = (-2.0 * cosW0) / a0,
                    a2 = (1.0 - alpha) / a0,
                )
            }

            fun peakingEq(
                centerHz: Double,
                q: Double = 1.0,
                gainDb: Double = 0.0,
                sampleRate: Int = SAMPLE_RATE,
            ): BiquadFilter {
                val fc = centerHz.coerceIn(1.0, sampleRate * 0.45)
                val safeQ = q.coerceIn(0.1, 20.0)
                val a = 10.0.pow(gainDb.coerceIn(-24.0, 24.0) / 40.0)
                val w0 = TWO_PI * fc / sampleRate
                val cosW0 = cos(w0)
                val alpha = sin(w0) / (2.0 * safeQ)
                val a0 = 1.0 + alpha / a
                return BiquadFilter(
                    b0 = (1.0 + alpha * a) / a0,
                    b1 = (-2.0 * cosW0) / a0,
                    b2 = (1.0 - alpha * a) / a0,
                    a1 = (-2.0 * cosW0) / a0,
                    a2 = (1.0 - alpha / a) / a0,
                )
            }
        }
    }

    /**
     * Intimate micro-room early-reflection comb delay.
     *
     * Adds three mutually-prime short reflections (`6.7 ms`, `11.3 ms`, `17.9 ms`)
     * damped through a gentle one-pole low-pass so UI cues sound natural and spacious
     * without smearing rapid navigation transients.
     */
    fun applyMicroRoom(
        samples: DoubleArray,
        sampleRate: Int = SAMPLE_RATE,
        wetMix: Double = 0.14,
        decay: Double = 0.28,
    ): DoubleArray {
        val mix = wetMix.coerceIn(0.0, 0.5)
        if (samples.isEmpty() || mix <= 0.0) return samples.copyOf()
        val fb = decay.coerceIn(0.0, 0.55)
        val taps = intArrayOf(
            (sampleRate * 0.0067).toInt().coerceAtLeast(1),
            (sampleRate * 0.0113).toInt().coerceAtLeast(1),
            (sampleRate * 0.0179).toInt().coerceAtLeast(1),
        )
        val weights = doubleArrayOf(0.52, 0.32, 0.16)
        val wet = DoubleArray(samples.size)
        var lpState = 0.0
        for (i in samples.indices) {
            var sum = 0.0
            for (t in taps.indices) {
                val idx = i - taps[t]
                if (idx >= 0) {
                    sum += weights[t] * (samples[idx] + fb * wet[idx])
                }
            }
            // Gentle one-pole damping (~4.5 kHz warmth) on reflections
            lpState = 0.62 * lpState + 0.38 * sum
            wet[i] = lpState
        }
        val out = DoubleArray(samples.size)
        val dry = 1.0 - mix * 0.35
        for (i in samples.indices) {
            out[i] = samples[i] * dry + wet[i] * mix
        }
        return out
    }

    /**
     * Constant-power stereo pan law (`left^2 + right^2 == 1.0`) for [pan] in `-1f..+1f`.
     *
     * At center (`pan = 0f`), both channels receive `sqrt(2)/2 ≈ 0.7071f` (`-3 dB` per
     * channel, preserving constant combined acoustic power across pan positions).
     */
    fun constantPowerPan(pan: Float): Pair<Float, Float> {
        val clamped = pan.coerceIn(-1f, 1f).toDouble()
        val angle = (PI * 0.25) * (clamped + 1.0)
        return cos(angle).toFloat() to sin(angle).toFloat()
    }

    /** Converts mono [samples] into interleaved stereo `[L0, R0, L1, R1, ...]` using [constantPowerPan]. */
    fun panMonoToStereo(samples: DoubleArray, pan: Float): DoubleArray {
        val (left, right) = constantPowerPan(pan)
        val out = DoubleArray(samples.size * 2)
        val l = left.toDouble()
        val r = right.toDouble()
        for (i in samples.indices) {
            val s = samples[i]
            out[i * 2] = s * l
            out[i * 2 + 1] = s * r
        }
        return out
    }
}
