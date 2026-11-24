package com.visorcraft.ghostgalleon.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Handheld-speaker-safe mastering chain for procedurally synthesized UI audio.
 *
 * Tuned for compact dual-screen handheld speakers (e.g., One X Sugar, AYN Odin 2 Portal):
 * 1. Sanitize non-finite values and strip initial DC offset.
 * 2. 110 Hz Butterworth high-pass filter (eliminates micro-speaker sub-bass buzz/rattle).
 * 3. Gentle 11 kHz low-pass air polish (prevents digital harshness on small drivers).
 * 4. Bounded RMS target scaling.
 * 5. Smooth `tanh` soft-knee true-peak limiter strictly capped at `-1.5 dBFS` (`<= 0.842`).
 * 6. Click-free half-cosine edge fade-in (`>= 1.5 ms`) and fade-out (`>= 4 ms`).
 * 7. Windowed final DC-offset neutralization preserving exact-zero boundary samples.
 */
object AudioMastering {

    /** Strict `-1.5 dBFS` ceiling (`10^(-1.5 / 20) ≈ 0.841395 <= 0.842`). */
    const val DEFAULT_PEAK_CEILING: Double = 0.841395141645195
    const val DEFAULT_HIGH_PASS_HZ: Double = 110.0
    const val DEFAULT_LOW_PASS_HZ: Double = 11_000.0
    const val DEFAULT_TARGET_RMS: Double = 0.16
    const val MIN_FADE_IN_MS: Double = 1.5
    const val MIN_FADE_OUT_MS: Double = 4.0

    /** Floor returned by [computePerceivedLoudnessDb] for empty or completely silent buffers. */
    const val SILENCE_LOUDNESS_DB: Double = -120.0

    /** Perceptual K-weighting / compact-speaker loudness weighting filter anchors. */
    private const val PERCEPTUAL_HPF_HZ: Double = 140.0
    private const val PERCEPTUAL_PRESENCE_HZ: Double = 1_800.0
    private const val PERCEPTUAL_PRESENCE_Q: Double = 0.65
    private const val PERCEPTUAL_PRESENCE_GAIN_DB: Double = 3.5

    data class MasteringConfig(
        val highPassHz: Double = DEFAULT_HIGH_PASS_HZ,
        val lowPassHz: Double = DEFAULT_LOW_PASS_HZ,
        val targetRms: Double = DEFAULT_TARGET_RMS,
        val peakCeiling: Double = DEFAULT_PEAK_CEILING,
        val softKneeRatio: Double = 0.70,
        val fadeInMs: Double = 2.0,
        val fadeOutMs: Double = 6.0,
        val minGain: Double = 0.05,
        val maxGain: Double = 5.0,
    )

    val DEFAULT_CONFIG: MasteringConfig = MasteringConfig()

    /**
     * Applies the full handheld mastering chain to a mono buffer, returning a new
     * finite buffer strictly bounded by `[-peakCeiling, +peakCeiling]`.
     */
    fun masterMono(
        input: DoubleArray,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
        config: MasteringConfig = DEFAULT_CONFIG,
    ): DoubleArray {
        if (input.isEmpty()) return DoubleArray(0)
        if (input.size == 1) return DoubleArray(1)

        val buf = DoubleArray(input.size) { i ->
            val v = input[i]
            if (v.isFinite()) v else 0.0
        }

        // 1. Initial DC removal before IIR filters to avoid step transients.
        removeDcInPlace(buf)

        // 2. 110 Hz high-pass filter protecting small handheld drivers.
        if (config.highPassHz > 0.0 && config.highPassHz < sampleRate * 0.45) {
            AudioDsp.BiquadFilter
                .highPass(config.highPassHz, AudioDsp.BiquadFilter.BUTTERWORTH_Q, sampleRate)
                .processInPlace(buf)
        }

        // 3. Subtle 11 kHz gentle low-pass air polish.
        if (config.lowPassHz > 0.0 && config.lowPassHz < sampleRate * 0.45) {
            AudioDsp.BiquadFilter
                .lowPass(config.lowPassHz, q = 0.65, sampleRate = sampleRate)
                .processInPlace(buf)
        }

        // 4. Bounded RMS normalization.
        val rms = computeRms(buf)
        if (rms > 1e-9 && config.targetRms > 0.0) {
            val gain = (config.targetRms / rms).coerceIn(config.minGain, config.maxGain)
            for (i in buf.indices) {
                buf[i] *= gain
            }
        }

        // 5. Smooth soft-knee tanh peak limiter capped at <= -1.5 dBFS.
        val ceiling = config.peakCeiling.coerceIn(0.1, DEFAULT_PEAK_CEILING)
        val knee = config.softKneeRatio.coerceIn(0.3, 0.95)
        applySoftLimiterInPlace(buf, ceiling, knee)

        // 6. Click-free half-cosine edge fade-in (>= 1.5 ms) and fade-out (>= 4 ms).
        val (fadeInN, fadeOutN) = computeFadeCounts(
            sampleCount = buf.size,
            sampleRate = sampleRate,
            fadeInMs = config.fadeInMs.coerceAtLeast(MIN_FADE_IN_MS),
            fadeOutMs = config.fadeOutMs.coerceAtLeast(MIN_FADE_OUT_MS),
        )
        applyEdgeFadesInPlace(buf, fadeInN, fadeOutN)

        // 7. Windowed final DC neutralization that preserves exact zero edges.
        removeDcPreservingEdgesInPlace(buf, fadeInN, fadeOutN)

        // 8. Final safety soft-limit + exact boundary zero guarantee.
        applySoftLimiterInPlace(buf, ceiling, knee)
        buf[0] = 0.0
        buf[buf.lastIndex] = 0.0

        return buf
    }

    /**
     * Masters a mono buffer and quantizes it to signed 16-bit PCM.
     */
    fun masterMonoToPcm16(
        input: DoubleArray,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
        config: MasteringConfig = DEFAULT_CONFIG,
    ): ShortArray = toPcm16(masterMono(input, sampleRate, config))

    /**
     * Smooth soft-knee `tanh` peak limiter.
     *
     * Linear (slope = 1) below `knee * ceiling`, continuously C1-smooth above the knee,
     * and asymptotically bounded strictly within `(-ceiling, +ceiling)`.
     */
    fun softLimitSample(x: Double, ceiling: Double = DEFAULT_PEAK_CEILING, kneeRatio: Double = 0.70): Double {
        if (!x.isFinite()) return 0.0
        val c = ceiling.coerceIn(0.05, 0.999)
        val threshold = c * kneeRatio.coerceIn(0.1, 0.98)
        val mag = abs(x)
        if (mag <= threshold) return x
        val headroom = c - threshold
        val shaped = threshold + headroom * tanh((mag - threshold) / headroom)
        return sign(x) * shaped.coerceAtMost(c)
    }

    /** Quantizes normalized `[-1.0, 1.0]` floating-point samples into 16-bit signed PCM. */
    fun toPcm16(samples: DoubleArray): ShortArray = ShortArray(samples.size) { i ->
        val v = samples[i]
        val safe = if (v.isFinite()) v.coerceIn(-1.0, 1.0) else 0.0
        (safe * 32767.0).roundToInt().coerceIn(-32767, 32767).toShort()
    }

    /** Computes root-mean-square level of [samples]. */
    fun computeRms(samples: DoubleArray): Double {
        if (samples.isEmpty()) return 0.0
        var sumSq = 0.0
        for (s in samples) {
            if (s.isFinite()) sumSq += s * s
        }
        return sqrt(sumSq / samples.size)
    }

    /** Computes arithmetic mean (DC offset) of [samples]. */
    fun computeMean(samples: DoubleArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            if (s.isFinite()) sum += s
        }
        return sum / samples.size
    }

    /**
     * Computes K-weighted / handheld-speaker perceived loudness in dB (`dBFS`-referenced)
     * using a 2-stage perceptual biquad cascade:
     * 1. `140 Hz` Butterworth high-pass rolling off sub-bass where human equal-loudness
     *    contours and compact handheld speakers lose sensitivity.
     * 2. `+3.5 dB` presence peaking filter centered at `1,800 Hz` (`Q = 0.65`) matching
     *    ITU-R BS.1770 / ear-canal sensitivity across `400 Hz – 4 kHz`.
     *
     * Returns [SILENCE_LOUDNESS_DB] (`-120.0`) for empty or silent buffers.
     */
    fun computePerceivedLoudnessDb(
        samples: DoubleArray,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
    ): Double {
        require(sampleRate >= 8000) { "sampleRate must be >= 8000 Hz, got $sampleRate" }
        if (samples.isEmpty()) return SILENCE_LOUDNESS_DB

        val hpf = AudioDsp.BiquadFilter.highPass(
            cutoffHz = PERCEPTUAL_HPF_HZ,
            q = AudioDsp.BiquadFilter.BUTTERWORTH_Q,
            sampleRate = sampleRate,
        )
        val presence = AudioDsp.BiquadFilter.peakingEq(
            centerHz = PERCEPTUAL_PRESENCE_HZ,
            q = PERCEPTUAL_PRESENCE_Q,
            gainDb = PERCEPTUAL_PRESENCE_GAIN_DB,
            sampleRate = sampleRate,
        )

        var sumSq = 0.0
        for (s in samples) {
            val safe = if (s.isFinite()) s else 0.0
            val w = presence.processSample(hpf.processSample(safe))
            sumSq += w * w
        }
        val weightedRms = sqrt(sumSq / samples.size)
        if (!weightedRms.isFinite() || weightedRms <= 1e-6) return SILENCE_LOUDNESS_DB
        return (20.0 * log10(weightedRms)).coerceAtLeast(SILENCE_LOUDNESS_DB)
    }

    /** Convenience overload computing perceived loudness in dB for signed 16-bit PCM [samples]. */
    fun computePerceivedLoudnessDb(
        samples: ShortArray,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
    ): Double {
        if (samples.isEmpty()) return SILENCE_LOUDNESS_DB
        val normalized = DoubleArray(samples.size) { i -> samples[i].toDouble() / 32767.0 }
        return computePerceivedLoudnessDb(normalized, sampleRate)
    }

    /**
     * Measures fraction (`0.0..1.0`) of signal energy residing below [cutoffHz] (default `110 Hz`).
     * Used to verify handheld micro-speaker sub-bass rattle rejection.
     */
    fun computeSubBassEnergyRatio(
        samples: DoubleArray,
        cutoffHz: Double = DEFAULT_HIGH_PASS_HZ,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
    ): Double {
        if (samples.isEmpty()) return 0.0
        val lp = AudioDsp.BiquadFilter.lowPass(
            cutoffHz = cutoffHz,
            q = AudioDsp.BiquadFilter.BUTTERWORTH_Q,
            sampleRate = sampleRate,
        )
        var total = 0.0
        var low = 0.0
        for (s in samples) {
            val safe = if (s.isFinite()) s else 0.0
            val f = lp.processSample(safe)
            total += safe * safe
            low += f * f
        }
        return if (total > 1e-12) (low / total).coerceIn(0.0, 1.0) else 0.0
    }

    /**
     * Measures fraction (`0.0..1.0`) of signal energy residing above [cutoffHz] (default `10 kHz`).
     * Used to verify control of harsh ultrasonic sibilance on compact drivers.
     */
    fun computeHighBandEnergyRatio(
        samples: DoubleArray,
        cutoffHz: Double = 10_000.0,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
    ): Double {
        if (samples.isEmpty()) return 0.0
        val hp = AudioDsp.BiquadFilter.highPass(
            cutoffHz = cutoffHz,
            q = AudioDsp.BiquadFilter.BUTTERWORTH_Q,
            sampleRate = sampleRate,
        )
        var total = 0.0
        var high = 0.0
        for (s in samples) {
            val safe = if (s.isFinite()) s else 0.0
            val f = hp.processSample(safe)
            total += safe * safe
            high += f * f
        }
        return if (total > 1e-12) (high / total).coerceIn(0.0, 1.0) else 0.0
    }

    private fun removeDcInPlace(buf: DoubleArray) {
        val mean = computeMean(buf)
        if (abs(mean) > 1e-12) {
            for (i in buf.indices) {
                buf[i] -= mean
            }
        }
    }

    private fun applySoftLimiterInPlace(buf: DoubleArray, ceiling: Double, kneeRatio: Double) {
        for (i in buf.indices) {
            buf[i] = softLimitSample(buf[i], ceiling, kneeRatio)
        }
    }

    private fun computeFadeCounts(
        sampleCount: Int,
        sampleRate: Int,
        fadeInMs: Double,
        fadeOutMs: Double,
    ): Pair<Int, Int> {
        if (sampleCount <= 2) return 1 to 1
        var fadeIn = ((fadeInMs / 1000.0) * sampleRate).roundToInt().coerceAtLeast(2)
        var fadeOut = ((fadeOutMs / 1000.0) * sampleRate).roundToInt().coerceAtLeast(2)
        val maxIn = (sampleCount / 3).coerceAtLeast(1)
        val maxOut = (sampleCount / 2).coerceAtLeast(1)
        fadeIn = fadeIn.coerceAtMost(maxIn)
        fadeOut = fadeOut.coerceAtMost(maxOut)
        if (fadeIn + fadeOut > sampleCount) {
            fadeOut = (sampleCount - fadeIn).coerceAtLeast(1)
        }
        return fadeIn to fadeOut
    }

    private fun fadeWindowWeight(index: Int, length: Int, fadeInN: Int, fadeOutN: Int): Double {
        var w = 1.0
        if (index < fadeInN) {
            val p = index.toDouble() / fadeInN.toDouble()
            w *= 0.5 * (1.0 - cos(PI * p))
        }
        val distFromEnd = length - 1 - index
        if (distFromEnd < fadeOutN) {
            val p = distFromEnd.toDouble() / fadeOutN.toDouble()
            w *= 0.5 * (1.0 - cos(PI * p))
        }
        return w
    }

    private fun applyEdgeFadesInPlace(buf: DoubleArray, fadeInN: Int, fadeOutN: Int) {
        val n = buf.size
        for (i in 0 until fadeInN) {
            val p = i.toDouble() / fadeInN.toDouble()
            buf[i] *= 0.5 * (1.0 - cos(PI * p))
        }
        for (k in 0 until fadeOutN) {
            val idx = n - 1 - k
            val p = k.toDouble() / fadeOutN.toDouble()
            buf[idx] *= 0.5 * (1.0 - cos(PI * p))
        }
    }

    private fun removeDcPreservingEdgesInPlace(buf: DoubleArray, fadeInN: Int, fadeOutN: Int) {
        val n = buf.size
        var sum = 0.0
        for (s in buf) sum += s
        val mean = sum / n
        if (abs(mean) <= 1e-6) return

        var weightSum = 0.0
        for (i in 0 until n) {
            weightSum += fadeWindowWeight(i, n, fadeInN, fadeOutN)
        }
        if (weightSum <= 1e-9) return

        val coeff = sum / weightSum
        for (i in 0 until n) {
            buf[i] -= coeff * fadeWindowWeight(i, n, fadeInN, fadeOutN)
        }
    }
}
