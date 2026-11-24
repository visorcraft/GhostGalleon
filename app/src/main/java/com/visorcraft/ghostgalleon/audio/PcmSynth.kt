package com.visorcraft.ghostgalleon.audio

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Primary waveform flavor defining a [SoundPackId]'s acoustic character.
 */
enum class TimbreWave {
    /** Glassy FM-inflected sine harmonics (Ghost maritime/crystalline identity). */
    SINE_GLASS,
    /** Warm rounded triangle wood/marimba body (Teal / 3DS handheld feel). */
    WARM_TRIANGLE,
    /** Pure minimal sine precision with ultra-clean transient (OLED stealth feel). */
    CRISP_MINIMAL,
    /** Band-limited warm pulse + FM sparkle (Neon retro-arcade feel). */
    RETRO_FM,
}

/**
 * Pack-wide timbral parameters delegated by [PcmSynth].
 * Structured cleanly so Iteration 4 can deepen per-pack voicings without altering cue APIs.
 */
data class PackTimbre(
    val pitchScale: Double,
    val brightnessHz: Double,
    val harmonicMix: Double,
    val roomMix: Double,
    val attackScale: Double,
    val decayScale: Double,
    val noiseMix: Double,
    val targetRmsScale: Double,
    val waveform: TimbreWave,
)

/**
 * Procedural 16-bit PCM UI sound synthesizer.
 *
 * Generates cohesive, musical, handheld-mastered audio for all 24 [SoundCue]s
 * using pure Kotlin DSP ([AudioDsp] + [AudioMastering]) with zero audio asset files.
 */
object PcmSynth {

    const val DEFAULT_SAMPLE_RATE: Int = AudioDsp.SAMPLE_RATE
    private const val MIN_DURATION_SEC: Double = 0.020
    private const val MAX_DURATION_SEC: Double = 1.350

    /** Musical tuning anchors (D-major / pentatonic sonic universe, A4 = 440 Hz). */
    private const val D4 = 293.66
    private const val E4 = 329.63
    private const val FS4 = 369.99
    private const val A4 = 440.00
    private const val B4 = 493.88
    private const val D5 = 587.33
    private const val E5 = 659.25
    private const val FS5 = 739.99
    private const val G5 = 783.99
    private const val A5 = 880.00
    private const val B5 = 987.77
    private const val C6 = 1046.50
    private const val D6 = 1174.66
    private const val FS6 = 1479.98
    private const val G6 = 1567.98

    private val GHOST_TIMBRE = PackTimbre(
        pitchScale = 1.00,
        brightnessHz = 10_500.0,
        harmonicMix = 0.34,
        roomMix = 0.16,
        attackScale = 1.00,
        decayScale = 1.00,
        noiseMix = 0.18,
        targetRmsScale = 1.00,
        waveform = TimbreWave.SINE_GLASS,
    )

    private val THREEDS_TIMBRE = PackTimbre(
        pitchScale = 1.05,
        brightnessHz = 9_200.0,
        harmonicMix = 0.28,
        roomMix = 0.12,
        attackScale = 0.90,
        decayScale = 0.94,
        noiseMix = 0.22,
        targetRmsScale = 1.02,
        waveform = TimbreWave.WARM_TRIANGLE,
    )

    private val OLED_TIMBRE = PackTimbre(
        pitchScale = 0.96,
        brightnessHz = 8_200.0,
        harmonicMix = 0.12,
        roomMix = 0.06,
        attackScale = 0.85,
        decayScale = 0.82,
        noiseMix = 0.10,
        targetRmsScale = 0.86,
        waveform = TimbreWave.CRISP_MINIMAL,
    )

    private val NEON_TIMBRE = PackTimbre(
        pitchScale = 1.08,
        brightnessHz = 11_000.0,
        harmonicMix = 0.48,
        roomMix = 0.14,
        attackScale = 0.80,
        decayScale = 0.96,
        noiseMix = 0.16,
        targetRmsScale = 1.04,
        waveform = TimbreWave.RETRO_FM,
    )

    /** Returns the [PackTimbre] for [pack] ([SoundPackId.AUTO] defaults to [GHOST_TIMBRE]). */
    fun timbreFor(pack: SoundPackId): PackTimbre = when (pack) {
        SoundPackId.GHOST, SoundPackId.AUTO, SoundPackId.OFF -> GHOST_TIMBRE
        SoundPackId.THREEDS -> THREEDS_TIMBRE
        SoundPackId.OLED -> OLED_TIMBRE
        SoundPackId.NEON -> NEON_TIMBRE
    }

    /**
     * Synthesizes 16-bit signed mono PCM for [cue] and [pack].
     * Returns an empty [ShortArray] when [pack] is [SoundPackId.OFF].
     */
    fun synthesize(
        cue: SoundCue,
        pack: SoundPackId = SoundPackId.GHOST,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
    ): ShortArray {
        if (pack == SoundPackId.OFF) return ShortArray(0)
        return AudioMastering.toPcm16(synthesizeMasteredFloat(cue, pack, sampleRate))
    }

    /**
     * Synthesizes 16-bit interleaved stereo PCM (`[L0, R0, ...]`) with constant-power [pan].
     * Returns an empty [ShortArray] when [pack] is [SoundPackId.OFF].
     */
    fun synthesizeStereo(
        cue: SoundCue,
        pack: SoundPackId = SoundPackId.GHOST,
        pan: Float = 0f,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
    ): ShortArray {
        if (pack == SoundPackId.OFF) return ShortArray(0)
        val mono = synthesizeMasteredFloat(cue, pack, sampleRate)
        return AudioMastering.toPcm16(AudioDsp.panMonoToStereo(mono, pan))
    }

    /**
     * Synthesizes and masters floating-point mono PCM for [cue] and [pack].
     * Returns an empty [DoubleArray] when [pack] is [SoundPackId.OFF].
     */
    fun synthesizeMasteredFloat(
        cue: SoundCue,
        pack: SoundPackId = SoundPackId.GHOST,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
    ): DoubleArray {
        require(sampleRate >= 8000) { "sampleRate must be >= 8000 Hz, got $sampleRate" }
        if (pack == SoundPackId.OFF) return DoubleArray(0)

        val timbre = timbreFor(pack)
        val noise = AudioDsp.SeededNoise(0x4747_4155_4449_4F00L xor (cue.ordinal.toLong() * 0x9E3779B9L) xor pack.ordinal.toLong())
        val (raw, cueRms, useRoom) = renderRawCue(cue, timbre, noise, sampleRate)

        val roomed = if (useRoom && timbre.roomMix > 0.0) {
            AudioDsp.applyMicroRoom(raw, sampleRate = sampleRate, wetMix = timbre.roomMix)
        } else {
            raw
        }

        val fadeOutMs = if (roomed.size.toDouble() / sampleRate < 0.045) 4.2 else 6.5
        val masteringConfig = AudioMastering.MasteringConfig(
            highPassHz = AudioMastering.DEFAULT_HIGH_PASS_HZ,
            lowPassHz = timbre.brightnessHz,
            targetRms = (cueRms * timbre.targetRmsScale).coerceIn(0.06, 0.24),
            peakCeiling = AudioMastering.DEFAULT_PEAK_CEILING,
            fadeInMs = 1.8,
            fadeOutMs = fadeOutMs,
        )
        return AudioMastering.masterMono(roomed, sampleRate = sampleRate, config = masteringConfig)
    }

    private data class RawCueResult(
        val buffer: DoubleArray,
        val targetRms: Double,
        val useMicroRoom: Boolean,
    )

    private fun renderRawCue(
        cue: SoundCue,
        t: PackTimbre,
        noise: AudioDsp.SeededNoise,
        sr: Int,
    ): RawCueResult = when (cue) {
        SoundCue.NAV_TICK -> {
            val buf = alloc(0.032 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.028 * t.decayScale, 890.0 * t.pitchScale, 710.0 * t.pitchScale, 0.0018 * t.attackScale, 0.0085 * t.decayScale, 0.90, t)
            addNoiseBurst(buf, sr, noise, 0.0, 0.012, 2400.0, 1.8, 0.22 * t.noiseMix, pink = true)
            RawCueResult(buf, targetRms = 0.11, useMicroRoom = false)
        }

        SoundCue.NAV_EDGE_BUMP -> {
            val buf = alloc(0.080 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.072 * t.decayScale, 215.0 * t.pitchScale, 132.0 * t.pitchScale, 0.0025 * t.attackScale, 0.022 * t.decayScale, 0.95, t, forceWarmSine = true)
            addGlideVoice(buf, sr, 0.016, 0.045 * t.decayScale, 320.0 * t.pitchScale, 220.0 * t.pitchScale, 0.002, 0.014 * t.decayScale, 0.35, t, forceWarmSine = true)
            addNoiseBurst(buf, sr, noise, 0.0, 0.022, 420.0, 1.4, 0.20 * t.noiseMix, pink = true)
            RawCueResult(buf, targetRms = 0.14, useMicroRoom = false)
        }

        SoundCue.NAV_PAGE_TURN -> {
            val buf = alloc(0.115 * t.decayScale, sr)
            addNoiseSweep(buf, sr, noise, 0.0, 0.100 * t.decayScale, 750.0, 2500.0, 1.5, 0.36 * max(0.5, t.noiseMix * 2.5))
            addGlideVoice(buf, sr, 0.008, 0.095 * t.decayScale, D5 * t.pitchScale, A5 * t.pitchScale, 0.012 * t.attackScale, 0.032 * t.decayScale, 0.55, t)
            RawCueResult(buf, targetRms = 0.12, useMicroRoom = true)
        }

        SoundCue.DOCK_ENTER -> {
            val buf = alloc(0.092 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.045 * t.decayScale, G5 * t.pitchScale, 0.002 * t.attackScale, 0.016 * t.decayScale, 0.70, t)
            addVoice(buf, sr, 0.026, 0.062 * t.decayScale, D5 * t.pitchScale, 0.002 * t.attackScale, 0.022 * t.decayScale, 0.85, t)
            RawCueResult(buf, targetRms = 0.13, useMicroRoom = true)
        }

        SoundCue.DOCK_EXIT -> {
            val buf = alloc(0.092 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.045 * t.decayScale, D5 * t.pitchScale, 0.002 * t.attackScale, 0.016 * t.decayScale, 0.70, t)
            addVoice(buf, sr, 0.026, 0.062 * t.decayScale, G5 * t.pitchScale, 0.002 * t.attackScale, 0.022 * t.decayScale, 0.85, t)
            RawCueResult(buf, targetRms = 0.13, useMicroRoom = true)
        }

        SoundCue.CONFIRM -> {
            val buf = alloc(0.120 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.110 * t.decayScale, D5 * t.pitchScale, 0.002 * t.attackScale, 0.036 * t.decayScale, 0.82, t)
            addVoice(buf, sr, 0.014, 0.100 * t.decayScale, A5 * t.pitchScale, 0.002 * t.attackScale, 0.034 * t.decayScale, 0.65, t)
            addNoiseBurst(buf, sr, noise, 0.0, 0.014, 3000.0, 1.6, 0.14 * t.noiseMix, pink = true)
            RawCueResult(buf, targetRms = 0.16, useMicroRoom = true)
        }

        SoundCue.LAUNCH_GAME -> {
            val buf = alloc(0.440 * t.decayScale, sr)
            val notes = doubleArrayOf(D5, FS5, A5, D6)
            val starts = doubleArrayOf(0.0, 0.048, 0.096, 0.148)
            val amps = doubleArrayOf(0.68, 0.72, 0.80, 0.92)
            for (i in notes.indices) {
                addFmBell(
                    buf, sr, starts[i], (0.280 - i * 0.015) * t.decayScale,
                    notes[i] * t.pitchScale, modRatio = 2.0, modIndex = 0.7 + 0.9 * t.harmonicMix,
                    attackSec = 0.004 * t.attackScale, decayTauSec = 0.095 * t.decayScale, amp = amps[i],
                )
            }
            addVoice(buf, sr, 0.0, 0.380 * t.decayScale, D4 * t.pitchScale, 0.025, 0.150 * t.decayScale, 0.42, t, forceWarmSine = true)
            addNoiseSweep(buf, sr, noise, 0.02, 0.24 * t.decayScale, 900.0, 3800.0, 1.4, 0.14 * max(0.4, t.noiseMix * 2.0))
            RawCueResult(buf, targetRms = 0.19, useMicroRoom = true)
        }

        SoundCue.LAUNCH_APP -> {
            val buf = alloc(0.270 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.160 * t.decayScale, A4 * t.pitchScale, 0.003 * t.attackScale, 0.055 * t.decayScale, 0.75, t)
            addVoice(buf, sr, 0.040, 0.190 * t.decayScale, E5 * t.pitchScale, 0.003 * t.attackScale, 0.068 * t.decayScale, 0.82, t)
            addFmBell(buf, sr, 0.082, 0.180 * t.decayScale, A5 * t.pitchScale, 2.0, 0.55 + 0.6 * t.harmonicMix, 0.003 * t.attackScale, 0.062 * t.decayScale, 0.70)
            RawCueResult(buf, targetRms = 0.18, useMicroRoom = true)
        }

        SoundCue.BACK -> {
            val buf = alloc(0.095 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.088 * t.decayScale, FS5 * t.pitchScale, D5 * t.pitchScale, 0.0025 * t.attackScale, 0.028 * t.decayScale, 0.85, t)
            RawCueResult(buf, targetRms = 0.14, useMicroRoom = true)
        }

        SoundCue.SWAP_DISPLAY -> {
            val buf = alloc(0.225 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.190 * t.decayScale, A4 * t.pitchScale, A5 * t.pitchScale, 0.010 * t.attackScale, 0.070 * t.decayScale, 0.68, t)
            addGlideVoice(buf, sr, 0.035, 0.185 * t.decayScale, A5 * t.pitchScale, D5 * t.pitchScale, 0.010 * t.attackScale, 0.068 * t.decayScale, 0.68, t)
            addNoiseSweep(buf, sr, noise, 0.0, 0.180 * t.decayScale, 1000.0, 2800.0, 1.8, 0.20 * max(0.5, t.noiseMix * 2.0))
            RawCueResult(buf, targetRms = 0.16, useMicroRoom = true)
        }

        SoundCue.MODE_TOGGLE -> {
            val buf = alloc(0.205 * t.decayScale, sr)
            addNoiseBurst(buf, sr, noise, 0.0, 0.015, 2200.0, 1.8, 0.25 * max(0.5, t.noiseMix * 2.0), pink = true)
            addVoice(buf, sr, 0.008, 0.120 * t.decayScale, D5 * t.pitchScale, 0.003 * t.attackScale, 0.042 * t.decayScale, 0.72, t)
            addVoice(buf, sr, 0.052, 0.145 * t.decayScale, G5 * t.pitchScale, 0.003 * t.attackScale, 0.050 * t.decayScale, 0.75, t)
            addFmBell(buf, sr, 0.095, 0.105 * t.decayScale, A5 * t.pitchScale, 2.0, 0.5 + 0.5 * t.harmonicMix, 0.003, 0.038 * t.decayScale, 0.58)
            RawCueResult(buf, targetRms = 0.16, useMicroRoom = true)
        }

        SoundCue.MODAL_OPEN -> {
            val buf = alloc(0.145 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.120 * t.decayScale, E5 * t.pitchScale, 0.010 * t.attackScale, 0.044 * t.decayScale, 0.72, t)
            addVoice(buf, sr, 0.028, 0.112 * t.decayScale, B5 * t.pitchScale, 0.008 * t.attackScale, 0.042 * t.decayScale, 0.65, t)
            RawCueResult(buf, targetRms = 0.14, useMicroRoom = true)
        }

        SoundCue.MODAL_CLOSE -> {
            val buf = alloc(0.125 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.090 * t.decayScale, B5 * t.pitchScale, 0.004 * t.attackScale, 0.032 * t.decayScale, 0.62, t)
            addVoice(buf, sr, 0.026, 0.092 * t.decayScale, E5 * t.pitchScale, 0.004 * t.attackScale, 0.034 * t.decayScale, 0.72, t)
            RawCueResult(buf, targetRms = 0.13, useMicroRoom = true)
        }

        SoundCue.FAVORITE_ON -> {
            val buf = alloc(0.245 * t.decayScale, sr)
            addFmBell(buf, sr, 0.0, 0.160 * t.decayScale, A5 * t.pitchScale, 2.0, 0.6 + 0.7 * t.harmonicMix, 0.0025 * t.attackScale, 0.056 * t.decayScale, 0.78)
            addFmBell(buf, sr, 0.055, 0.185 * t.decayScale, D6 * t.pitchScale, 2.0, 0.75 + 0.8 * t.harmonicMix, 0.0025 * t.attackScale, 0.068 * t.decayScale, 0.88)
            addVoice(buf, sr, 0.088, 0.140 * t.decayScale, FS6 * t.pitchScale, 0.004, 0.045 * t.decayScale, 0.36, t, forceWarmSine = true)
            RawCueResult(buf, targetRms = 0.17, useMicroRoom = true)
        }

        SoundCue.FAVORITE_OFF -> {
            val buf = alloc(0.155 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.088 * t.decayScale, A5 * t.pitchScale, 0.003 * t.attackScale, 0.030 * t.decayScale, 0.66, t)
            addVoice(buf, sr, 0.042, 0.108 * t.decayScale, E5 * t.pitchScale, 0.004 * t.attackScale, 0.038 * t.decayScale, 0.72, t)
            RawCueResult(buf, targetRms = 0.13, useMicroRoom = true)
        }

        SoundCue.MOVE_PICKUP -> {
            val buf = alloc(0.130 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.118 * t.decayScale, A4 * t.pitchScale, E5 * t.pitchScale, 0.0025 * t.attackScale, 0.040 * t.decayScale, 0.85, t)
            addNoiseBurst(buf, sr, noise, 0.0, 0.015, 2100.0, 1.5, 0.18 * t.noiseMix, pink = true)
            RawCueResult(buf, targetRms = 0.15, useMicroRoom = true)
        }

        SoundCue.MOVE_SWAP -> {
            val buf = alloc(0.058 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.050 * t.decayScale, 620.0 * t.pitchScale, 770.0 * t.pitchScale, 0.002 * t.attackScale, 0.016 * t.decayScale, 0.82, t)
            addNoiseBurst(buf, sr, noise, 0.0, 0.018, 1900.0, 1.8, 0.22 * t.noiseMix, pink = true)
            RawCueResult(buf, targetRms = 0.13, useMicroRoom = false)
        }

        SoundCue.MOVE_DROP -> {
            val buf = alloc(0.140 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.120 * t.decayScale, E5 * t.pitchScale, A4 * t.pitchScale, 0.002 * t.attackScale, 0.040 * t.decayScale, 0.82, t)
            addVoice(buf, sr, 0.012, 0.115 * t.decayScale, D4 * t.pitchScale, 0.003, 0.042 * t.decayScale, 0.55, t, forceWarmSine = true)
            RawCueResult(buf, targetRms = 0.16, useMicroRoom = true)
        }

        SoundCue.DESTRUCTIVE -> {
            val buf = alloc(0.225 * t.decayScale, sr)
            addGlideVoice(buf, sr, 0.0, 0.105 * t.decayScale, FS4 * t.pitchScale, E4 * t.pitchScale, 0.003 * t.attackScale, 0.038 * t.decayScale, 0.82, t)
            addGlideVoice(buf, sr, 0.080, 0.138 * t.decayScale, 285.0 * t.pitchScale, 225.0 * t.pitchScale, 0.003 * t.attackScale, 0.048 * t.decayScale, 0.88, t)
            RawCueResult(buf, targetRms = 0.16, useMicroRoom = true)
        }

        SoundCue.FILTER_SELECT -> {
            val buf = alloc(0.078 * t.decayScale, sr)
            addVoice(buf, sr, 0.0, 0.070 * t.decayScale, C6 * t.pitchScale, 0.0018 * t.attackScale, 0.022 * t.decayScale, 0.80, t)
            addVoice(buf, sr, 0.006, 0.058 * t.decayScale, G6 * t.pitchScale, 0.002, 0.018 * t.decayScale, 0.34, t, forceWarmSine = true)
            RawCueResult(buf, targetRms = 0.13, useMicroRoom = false)
        }

        SoundCue.ERROR_DENY -> {
            val buf = alloc(0.195 * t.decayScale, sr)
            addFmBell(buf, sr, 0.0, 0.075 * t.decayScale, 265.0 * t.pitchScale, 1.5, 0.75, 0.0025, 0.024 * t.decayScale, 0.82)
            addFmBell(buf, sr, 0.078, 0.108 * t.decayScale, 218.0 * t.pitchScale, 1.5, 0.85, 0.0025, 0.036 * t.decayScale, 0.88)
            RawCueResult(buf, targetRms = 0.16, useMicroRoom = false)
        }

        SoundCue.BOOT_CHIME -> {
            val dur = (1.140 * t.decayScale).coerceIn(0.820, MAX_DURATION_SEC)
            val buf = alloc(dur, sr)
            // Warm nautical anchor chord
            addVoice(buf, sr, 0.0, dur * 0.92, D4 * t.pitchScale, 0.045, dur * 0.38, 0.44, t, forceWarmSine = true)
            addVoice(buf, sr, 0.04, dur * 0.88, A4 * t.pitchScale, 0.040, dur * 0.35, 0.40, t, forceWarmSine = true)
            // Ethereal staggered crystalline arpeggio (D5 -> E5 -> F#5 -> A5 -> D6)
            val arpNotes = doubleArrayOf(D5, E5, FS5, A5, D6)
            val arpStarts = doubleArrayOf(0.06, 0.16, 0.27, 0.38, 0.50)
            val arpAmps = doubleArrayOf(0.56, 0.54, 0.62, 0.72, 0.80)
            for (i in arpNotes.indices) {
                val start = arpStarts[i]
                val rem = (dur - start).coerceAtLeast(0.15)
                addFmBell(
                    buf, sr, start, rem,
                    arpNotes[i] * t.pitchScale, modRatio = 2.0, modIndex = 0.65 + 0.75 * t.harmonicMix,
                    attackSec = 0.008 * t.attackScale, decayTauSec = rem * 0.36, amp = arpAmps[i],
                )
            }
            // Soft ocean-breeze swell underneath
            addNoiseSweep(buf, sr, noise, 0.02, dur * 0.70, 500.0, 2200.0, 1.3, 0.10 * max(0.5, t.noiseMix * 2.0))
            RawCueResult(buf, targetRms = 0.18, useMicroRoom = true)
        }

        SoundCue.RA_ACHIEVEMENT -> {
            val dur = (0.940 * t.decayScale).coerceIn(0.720, MAX_DURATION_SEC)
            val buf = alloc(dur, sr)
            // Triumphant rising cadence: D5 -> F#5 -> A5 -> D6 + sparkling F#6 crown
            val notes = doubleArrayOf(D5, FS5, A5, D6)
            val starts = doubleArrayOf(0.0, 0.095, 0.190, 0.300)
            val taus = doubleArrayOf(0.085, 0.090, 0.110, 0.280)
            val amps = doubleArrayOf(0.72, 0.76, 0.82, 0.94)
            for (i in notes.indices) {
                val start = starts[i]
                val rem = (dur - start).coerceAtLeast(0.12)
                addFmBell(
                    buf, sr, start, rem,
                    notes[i] * t.pitchScale, modRatio = 2.0, modIndex = 0.85 + 0.95 * t.harmonicMix,
                    attackSec = 0.003 * t.attackScale, decayTauSec = taus[i] * t.decayScale, amp = amps[i],
                )
            }
            // Harmony bloom on final peak
            addVoice(buf, sr, 0.300, (dur - 0.300).coerceAtLeast(0.15), A5 * t.pitchScale, 0.008, 0.240 * t.decayScale, 0.50, t, forceWarmSine = true)
            addFmBell(buf, sr, 0.330, (dur - 0.330).coerceAtLeast(0.12), FS6 * t.pitchScale, 2.0, 0.45, 0.005, 0.180 * t.decayScale, 0.44)
            RawCueResult(buf, targetRms = 0.20, useMicroRoom = true)
        }

        SoundCue.SETUP_COMPLETE -> {
            val dur = (0.780 * t.decayScale).coerceIn(0.580, MAX_DURATION_SEC)
            val buf = alloc(dur, sr)
            val notes = doubleArrayOf(D5, G5, A5, D6)
            val starts = doubleArrayOf(0.0, 0.090, 0.185, 0.295)
            val amps = doubleArrayOf(0.68, 0.72, 0.78, 0.88)
            for (i in notes.indices) {
                val start = starts[i]
                val rem = (dur - start).coerceAtLeast(0.14)
                addFmBell(
                    buf, sr, start, rem,
                    notes[i] * t.pitchScale, modRatio = 2.0, modIndex = 0.55 + 0.65 * t.harmonicMix,
                    attackSec = 0.005 * t.attackScale, decayTauSec = 0.165 * t.decayScale, amp = amps[i],
                )
            }
            addVoice(buf, sr, 0.295, (dur - 0.295).coerceAtLeast(0.14), FS5 * t.pitchScale, 0.012, 0.190 * t.decayScale, 0.46, t, forceWarmSine = true)
            RawCueResult(buf, targetRms = 0.18, useMicroRoom = true)
        }
    }

    private fun alloc(durationSec: Double, sr: Int): DoubleArray {
        val clamped = durationSec.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
        return DoubleArray((clamped * sr).roundToInt().coerceAtLeast(16))
    }

    private fun sampleWaveform(
        phase: Double,
        freqHz: Double,
        t: PackTimbre,
        sr: Int,
        forceWarmSine: Boolean,
    ): Double {
        if (forceWarmSine) {
            return 0.88 * AudioDsp.sine(phase) + 0.12 * AudioDsp.sine(phase * 2.0)
        }
        return when (t.waveform) {
            TimbreWave.SINE_GLASS -> {
                val base = AudioDsp.sine(phase)
                val shimmer = AudioDsp.fmTone(phase, phase * 2.0, 0.55 * t.harmonicMix)
                (1.0 - t.harmonicMix) * base + t.harmonicMix * shimmer
            }
            TimbreWave.WARM_TRIANGLE -> {
                val tri = AudioDsp.softTriangle(phase, 0.94)
                val fundamental = AudioDsp.sine(phase)
                0.72 * tri + 0.28 * fundamental
            }
            TimbreWave.CRISP_MINIMAL -> {
                val base = AudioDsp.sine(phase)
                val second = AudioDsp.sine(phase * 2.0)
                (1.0 - t.harmonicMix) * base + t.harmonicMix * second
            }
            TimbreWave.RETRO_FM -> {
                val pulse = AudioDsp.warmPulse(phase, duty = 0.35, maxHarmonics = 6, freqHz = freqHz, sampleRate = sr)
                val fm = AudioDsp.fmTone(phase, phase * 2.0, 0.80 * t.harmonicMix)
                0.55 * pulse + 0.45 * fm
            }
        }
    }

    private fun addVoice(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        attackSec: Double,
        decayTauSec: Double,
        amp: Double,
        t: PackTimbre,
        forceWarmSine: Boolean = false,
    ) {
        addGlideVoice(buf, sr, startSec, durationSec, freqHz, freqHz, attackSec, decayTauSec, amp, t, forceWarmSine)
    }

    private fun addGlideVoice(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        attackSec: Double,
        decayTauSec: Double,
        amp: Double,
        t: PackTimbre,
        forceWarmSine: Boolean = false,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx) return

        var phase = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(startHz, endHz, progress, exponential = true)
            phase += hz * invSr
            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            val tailFade = if (progress > 0.85) (1.0 - progress) / 0.15 else 1.0
            buf[i] += sampleWaveform(phase, hz, t, sr, forceWarmSine) * env * tailFade * amp
        }
    }

    private fun addFmBell(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        carrierHz: Double,
        modRatio: Double,
        modIndex: Double,
        attackSec: Double,
        decayTauSec: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx) return

        var carrierPhase = 0.0
        var modPhase = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            carrierPhase += carrierHz * invSr
            modPhase += (carrierHz * modRatio) * invSr
            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            // Brighter attack harmonic index that mellows as the chime rings out
            val dynIndex = modIndex * (0.25 + 0.75 * env)
            val tailFade = if (progress > 0.88) (1.0 - progress) / 0.12 else 1.0
            buf[i] += AudioDsp.fmTone(carrierPhase, modPhase, dynIndex) * env * tailFade * amp
        }
    }

    private fun addNoiseBurst(
        buf: DoubleArray,
        sr: Int,
        noise: AudioDsp.SeededNoise,
        startSec: Double,
        durationSec: Double,
        centerHz: Double,
        q: Double,
        amp: Double,
        pink: Boolean,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        val bp = AudioDsp.BiquadFilter.bandPass(centerHz, q, sr)
        val invSr = 1.0 / sr
        val tau = (durationSec * 0.35).coerceAtLeast(0.002)
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val raw = if (pink) noise.nextPink() else noise.nextWhite()
            val env = AudioDsp.expDecay(localSec, 0.001, tau)
            buf[i] += bp.processSample(raw) * env * amp
        }
    }

    private fun addNoiseSweep(
        buf: DoubleArray,
        sr: Int,
        noise: AudioDsp.SeededNoise,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        q: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        val invSr = 1.0 / sr
        val total = (endIdx - startIdx).coerceAtLeast(1)
        val bp = AudioDsp.BiquadFilter.bandPass(startHz, q, sr)
        val updateStep = 32
        for (i in startIdx until endIdx) {
            val k = i - startIdx
            val progress = k.toDouble() / total.toDouble()
            if (k > 0 && k % updateStep == 0) {
                val hz = AudioDsp.freqGlide(startHz, endHz, progress, exponential = true)
                bp.updateBandPass(hz, q, sr)
            }
            val localSec = k * invSr
            val env = AudioDsp.adsr(
                timeSec = localSec,
                attackSec = durationSec * 0.25,
                decaySec = durationSec * 0.20,
                sustainLevel = 0.65,
                sustainEndSec = durationSec * 0.55,
                releaseSec = durationSec * 0.45,
            )
            buf[i] += bp.processSample(noise.nextPink()) * env * amp
        }
    }
}
