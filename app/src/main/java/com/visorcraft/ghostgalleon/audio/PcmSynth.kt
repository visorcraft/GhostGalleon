package com.visorcraft.ghostgalleon.audio

/**
 * Primary waveform flavor defining a [SoundPackId]'s acoustic character.
 */
enum class TimbreWave {
    /** Glassy FM-inflected maritime bell harmonics (`2.76x` partial) + warm oak deck knocks (`Ghost`). */
    SINE_GLASS,
    /** Warm woody marimba/kalimba triangle + rosewood bar overtone (`Teal / 3DS`). */
    WARM_TRIANGLE,
    /** Pure minimal sine precision with ultra-short damped transients (`OLED`). */
    CRISP_MINIMAL,
    /** Band-limited warm pulse + crisp 2-operator FM synthesis (`Neon`). */
    RETRO_FM,
}

/**
 * Pack-wide timbral summary exposed by [PcmSynth.timbreFor].
 * Detailed per-cue multi-harmonic synthesis recipes live in [SoundPackRecipes].
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
 * Generates cohesive, theme-synced, handheld-mastered audio for all 24 [SoundCue]s
 * across all 4 concrete [SoundPackId]s (`GHOST`, `THREEDS`, `OLED`, `NEON`) using
 * pure Kotlin DSP ([AudioDsp] + [SoundPackRecipes] + [AudioMastering]) with zero
 * audio asset files or Android dependencies.
 */
object PcmSynth {

    const val DEFAULT_SAMPLE_RATE: Int = AudioDsp.SAMPLE_RATE

    private val GHOST_TIMBRE = PackTimbre(
        pitchScale = 1.00,
        brightnessHz = SoundPackRecipes.GHOST_PROFILE.lowPassHz,
        harmonicMix = 0.38,
        roomMix = SoundPackRecipes.GHOST_PROFILE.baseRoomMix,
        attackScale = 1.00,
        decayScale = 1.08,
        noiseMix = 0.20,
        targetRmsScale = SoundPackRecipes.GHOST_PROFILE.rmsScale,
        waveform = TimbreWave.SINE_GLASS,
    )

    private val THREEDS_TIMBRE = PackTimbre(
        pitchScale = 1.04,
        brightnessHz = SoundPackRecipes.THREEDS_PROFILE.lowPassHz,
        harmonicMix = 0.26,
        roomMix = SoundPackRecipes.THREEDS_PROFILE.baseRoomMix,
        attackScale = 0.90,
        decayScale = 0.92,
        noiseMix = 0.26,
        targetRmsScale = SoundPackRecipes.THREEDS_PROFILE.rmsScale,
        waveform = TimbreWave.WARM_TRIANGLE,
    )

    private val OLED_TIMBRE = PackTimbre(
        pitchScale = 0.92,
        brightnessHz = SoundPackRecipes.OLED_PROFILE.lowPassHz,
        harmonicMix = 0.04,
        roomMix = SoundPackRecipes.OLED_PROFILE.baseRoomMix,
        attackScale = 0.80,
        decayScale = 0.62,
        noiseMix = 0.08,
        targetRmsScale = SoundPackRecipes.OLED_PROFILE.rmsScale,
        waveform = TimbreWave.CRISP_MINIMAL,
    )

    private val NEON_TIMBRE = PackTimbre(
        pitchScale = 1.12,
        brightnessHz = SoundPackRecipes.NEON_PROFILE.lowPassHz,
        harmonicMix = 0.56,
        roomMix = SoundPackRecipes.NEON_PROFILE.baseRoomMix,
        attackScale = 0.78,
        decayScale = 0.90,
        noiseMix = 0.16,
        targetRmsScale = SoundPackRecipes.NEON_PROFILE.rmsScale,
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

        val resolvedPack = if (pack == SoundPackId.AUTO) SoundPackId.GHOST else pack
        val seed = 0x4747_4155_4449_4F00L xor
            (cue.ordinal.toLong() * 0x9E3779B9L) xor
            (resolvedPack.ordinal.toLong() * 0x517CC1B7L)
        val noise = AudioDsp.SeededNoise(seed)

        val rendered = SoundPackRecipes.renderRawCue(cue, resolvedPack, noise, sampleRate)
        val spatialized = applyRoom(rendered, sampleRate)

        val masteringConfig = AudioMastering.MasteringConfig(
            highPassHz = rendered.highPassHz,
            lowPassHz = rendered.lowPassHz,
            targetRms = rendered.targetRms.coerceIn(0.055, 0.24),
            peakCeiling = AudioMastering.DEFAULT_PEAK_CEILING,
            fadeInMs = rendered.fadeInMs,
            fadeOutMs = rendered.fadeOutMs,
        )
        return AudioMastering.masterMono(spatialized, sampleRate = sampleRate, config = masteringConfig)
    }

    private fun applyRoom(rendered: RenderedCue, sampleRate: Int): DoubleArray {
        if (rendered.roomWetMix <= 0.0 || rendered.roomStyle == RoomStyle.NONE) {
            return rendered.samples
        }
        return when (rendered.roomStyle) {
            RoomStyle.NONE -> rendered.samples
            RoomStyle.NAUTICAL_CHAMBER -> AudioDsp.applyResonantRoom(
                samples = rendered.samples,
                sampleRate = sampleRate,
                wetMix = rendered.roomWetMix,
                decay = 0.36,
                tapMs = AudioDsp.NAUTICAL_CHAMBER_TAPS_MS,
                dampingAlpha = 0.34,
            )
            RoomStyle.WOODEN_MICRO -> AudioDsp.applyMicroRoom(
                samples = rendered.samples,
                sampleRate = sampleRate,
                wetMix = rendered.roomWetMix,
                decay = 0.24,
            )
            RoomStyle.WHISPER_DRY -> AudioDsp.applyMicroRoom(
                samples = rendered.samples,
                sampleRate = sampleRate,
                wetMix = rendered.roomWetMix,
                decay = 0.14,
            )
            RoomStyle.ARCADE_PLATE -> AudioDsp.applyResonantRoom(
                samples = rendered.samples,
                sampleRate = sampleRate,
                wetMix = rendered.roomWetMix,
                decay = 0.22,
                tapMs = AudioDsp.ARCADE_PLATE_TAPS_MS,
                dampingAlpha = 0.58,
            )
        }
    }
}
