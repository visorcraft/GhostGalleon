package com.visorcraft.ghostgalleon.audio

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Spatial early-reflection character applied after raw cue synthesis.
 */
enum class RoomStyle {
    /** Completely dry (used for whisper-quiet OLED micro-ticks and crisp dry clicks). */
    NONE,
    /** Spacious warm nautical chamber with longer mutually-prime taps (`GHOST`). */
    NAUTICAL_CHAMBER,
    /** Intimate warm wooden tabletop room (`THREEDS`). */
    WOODEN_MICRO,
    /** Subtle low-profile reflection tail for late-night long cues (`OLED`). */
    WHISPER_DRY,
    /** Tight bright early-reflection arcade plate (`NEON`). */
    ARCADE_PLATE,
}

/**
 * High-level acoustic profile describing each concrete [SoundPackId]'s sonic architecture.
 */
data class PackAcousticProfile(
    val pack: SoundPackId,
    val lowPassHz: Double,
    val highPassHz: Double,
    val defaultRoomStyle: RoomStyle,
    val baseRoomMix: Double,
    val rmsScale: Double,
    val fadeInMs: Double,
    val fadeOutMs: Double,
)

/**
 * Unmastered cue buffer paired with per-cue mastering & room directives.
 */
data class RenderedCue(
    val samples: DoubleArray,
    val targetRms: Double,
    val roomStyle: RoomStyle,
    val roomWetMix: Double,
    val lowPassHz: Double,
    val highPassHz: Double = AudioMastering.DEFAULT_HIGH_PASS_HZ,
    val fadeInMs: Double = 1.8,
    val fadeOutMs: Double = 6.0,
)

/**
 * Theme-synced multi-harmonic synthesis recipes for all 4 built-in sound packs
 * across all 24 [SoundCue]s.
 *
 * Pure Kotlin DSP (zero Android dependencies):
 * - **GHOST (Nautical OLED Console)**: D Dorian / D minor-9 shimmer, glassy maritime bell
 *   harmonics (`2.76x` damped upper partial), warm resonant oak deck knocks, oceanic horizon
 *   sweeps, and celebratory brass-bell galleon chimes.
 * - **THREEDS (Classic Handheld Tactile — Teal)**: C / G Major Pentatonic, warm woody
 *   marimba & kalimba bars (`softTriangle` + `4.0x` tuned bar attack + crisp mallet tap),
 *   breezy filtered-noise page swooshes, elastic pop-and-drop moves, and uplifting 3-note arpeggios.
 * - **OLED (Whisper-Quiet Minimalist Precision)**: F Major / pure fifths, tight sub-30 ms
 *   damped precision micro-ticks, pure-sine restrained intervals, minimal reverb tail, and
 *   low-profile late-night modal breaths.
 * - **NEON (Retro 16-bit Arcade FM / Pulse)**: E Major / Arcade Mixolydian, punchy band-limited
 *   pulse (`12.5%` / `25%` / `50%`) + crisp 2-op FM blips, rapid arcade arpeggios, classic
 *   `B5 -> E6` coin sparkle, band-limited saw/FM laser sweeps, and 16-bit victory fanfares.
 */
object SoundPackRecipes {

    private const val MIN_DURATION_SEC: Double = 0.020
    private const val MAX_DURATION_SEC: Double = 1.350

    /** Glassy ship's bell inharmonic upper partial ratio (classic maritime bell strike partial). */
    const val MARITIME_BELL_PARTIAL_RATIO: Double = 2.76

    /** Tuned rosewood marimba/kalimba bar overtone ratio (`2nd bending mode`, fast-damped). */
    const val MARIMBA_BAR_OVERTONE_RATIO: Double = 4.0

    // Musical pitch anchors (12-TET, A4 = 440 Hz); all fundamentals sit above 110 Hz HPF.
    private const val F3 = 174.61
    private const val A3 = 220.00
    private const val B3 = 246.94

    private const val C4 = 261.63
    private const val CS4 = 277.18
    private const val D4 = 293.66
    private const val DS4 = 311.13
    private const val E4 = 329.63
    private const val F4 = 349.23
    private const val G4 = 392.00
    private const val A4 = 440.00
    private const val B4 = 493.88

    private const val C5 = 523.25
    private const val D5 = 587.33
    private const val E5 = 659.25
    private const val F5 = 698.46
    private const val FS5 = 739.99
    private const val G5 = 783.99
    private const val GS5 = 830.61
    private const val A5 = 880.00
    private const val B5 = 987.77

    private const val C6 = 1046.50
    private const val CS6 = 1108.73
    private const val D6 = 1174.66
    private const val E6 = 1318.51
    private const val FS6 = 1479.98
    private const val GS6 = 1661.22
    private const val B6 = 1975.53

    val GHOST_PROFILE = PackAcousticProfile(
        pack = SoundPackId.GHOST,
        lowPassHz = 10_500.0,
        highPassHz = AudioMastering.DEFAULT_HIGH_PASS_HZ,
        defaultRoomStyle = RoomStyle.NAUTICAL_CHAMBER,
        baseRoomMix = 0.24,
        rmsScale = 1.00,
        fadeInMs = 1.8,
        fadeOutMs = 7.0,
    )

    val THREEDS_PROFILE = PackAcousticProfile(
        pack = SoundPackId.THREEDS,
        lowPassHz = 8_400.0,
        highPassHz = AudioMastering.DEFAULT_HIGH_PASS_HZ,
        defaultRoomStyle = RoomStyle.WOODEN_MICRO,
        baseRoomMix = 0.12,
        rmsScale = 0.98,
        fadeInMs = 1.6,
        fadeOutMs = 5.5,
    )

    val OLED_PROFILE = PackAcousticProfile(
        pack = SoundPackId.OLED,
        lowPassHz = 6_200.0,
        highPassHz = 125.0,
        defaultRoomStyle = RoomStyle.WHISPER_DRY,
        baseRoomMix = 0.03,
        rmsScale = 0.70,
        fadeInMs = 1.5,
        fadeOutMs = 4.2,
    )

    val NEON_PROFILE = PackAcousticProfile(
        pack = SoundPackId.NEON,
        lowPassHz = 11_600.0,
        highPassHz = AudioMastering.DEFAULT_HIGH_PASS_HZ,
        defaultRoomStyle = RoomStyle.ARCADE_PLATE,
        baseRoomMix = 0.10,
        rmsScale = 0.96,
        fadeInMs = 1.5,
        fadeOutMs = 5.0,
    )

    /** Returns the [PackAcousticProfile] for [pack] (`AUTO`/`OFF` resolve to [GHOST_PROFILE]). */
    fun profileFor(pack: SoundPackId): PackAcousticProfile = when (pack) {
        SoundPackId.GHOST, SoundPackId.AUTO, SoundPackId.OFF -> GHOST_PROFILE
        SoundPackId.THREEDS -> THREEDS_PROFILE
        SoundPackId.OLED -> OLED_PROFILE
        SoundPackId.NEON -> NEON_PROFILE
    }

    /**
     * Renders an unmastered mono [RenderedCue] for [cue] and [pack] at [sampleRate] Hz.
     * Callers (`PcmSynth`) apply room early reflections and handheld mastering.
     */
    fun renderRawCue(
        cue: SoundCue,
        pack: SoundPackId,
        noise: AudioDsp.SeededNoise,
        sampleRate: Int,
    ): RenderedCue = when (pack) {
        SoundPackId.THREEDS -> renderThreeDs(cue, noise, sampleRate)
        SoundPackId.OLED -> renderOled(cue, noise, sampleRate)
        SoundPackId.NEON -> renderNeon(cue, noise, sampleRate)
        SoundPackId.GHOST, SoundPackId.AUTO, SoundPackId.OFF -> renderGhost(cue, noise, sampleRate)
    }

    // =========================================================================
    // PACK 1: GHOST — Nautical OLED Console (D Dorian / D minor-9, 2.76x Glassy Bell + Oak Deck)
    // =========================================================================

    private fun renderGhost(cue: SoundCue, noise: AudioDsp.SeededNoise, sr: Int): RenderedCue {
        val p = GHOST_PROFILE
        return when (cue) {
            SoundCue.NAV_TICK -> {
                // Warm resonant oak deck knock + subtle glassy glint
                val buf = alloc(0.044, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.036, bodyStartHz = 620.0, bodyEndHz = 455.0, woodCenterHz = 560.0, bodyTauSec = 0.012, amp = 0.92)
                addMaritimeBell(buf, sr, 0.0, 0.024, D6, attackSec = 0.0012, decayTauSec = 0.006, upperPartialMix = 0.35, upperDecayTauSec = 0.0035, amp = 0.16)
                cue(buf, 0.120, RoomStyle.NAUTICAL_CHAMBER, 0.08, p, fadeOutMs = 4.8)
            }

            SoundCue.NAV_EDGE_BUMP -> {
                // Deep oak hull thud with warm resonant timber rib (above handheld micro-speaker rattle band)
                val buf = alloc(0.098, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.086, bodyStartHz = 224.0, bodyEndHz = 152.0, woodCenterHz = 410.0, bodyTauSec = 0.026, amp = 0.96)
                addPureGlide(buf, sr, 0.015, 0.056, 325.0, 224.0, 0.002, 0.018, secondHarmonic = 0.14, amp = 0.36)
                cue(buf, 0.140, RoomStyle.NAUTICAL_CHAMBER, 0.12, p)
            }

            SoundCue.NAV_PAGE_TURN -> {
                // Oceanic sail breeze + glassy D-Dorian fifth glide
                val buf = alloc(0.142, sr)
                addNoiseSweep(buf, sr, noise, 0.0, 0.125, 580.0, 2350.0, q = 1.4, amp = 0.44, pink = true)
                addMaritimeBellGlide(buf, sr, 0.010, 0.118, D5, A5, attackSec = 0.010, decayTauSec = 0.045, upperPartialMix = 0.32, upperDecayTauSec = 0.016, amp = 0.62)
                cue(buf, 0.132, RoomStyle.NAUTICAL_CHAMBER, 0.24, p)
            }

            SoundCue.DOCK_ENTER -> {
                // Stepping down to the lower deck: oak tap + descending maritime bell (A5 -> D5)
                val buf = alloc(0.122, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.028, 520.0, 410.0, 580.0, 0.009, 0.32)
                addMaritimeBell(buf, sr, 0.0, 0.068, A5, 0.002, 0.026, upperPartialMix = 0.36, upperDecayTauSec = 0.010, amp = 0.66)
                addMaritimeBell(buf, sr, 0.028, 0.088, D5, 0.002, 0.036, upperPartialMix = 0.30, upperDecayTauSec = 0.012, amp = 0.86)
                cue(buf, 0.140, RoomStyle.NAUTICAL_CHAMBER, 0.22, p)
            }

            SoundCue.DOCK_EXIT -> {
                // Rising back to the helm: ascending maritime bell (D5 -> A5)
                val buf = alloc(0.122, sr)
                addMaritimeBell(buf, sr, 0.0, 0.064, D5, 0.002, 0.024, upperPartialMix = 0.30, upperDecayTauSec = 0.010, amp = 0.68)
                addMaritimeBell(buf, sr, 0.026, 0.090, A5, 0.002, 0.036, upperPartialMix = 0.38, upperDecayTauSec = 0.014, amp = 0.86)
                cue(buf, 0.140, RoomStyle.NAUTICAL_CHAMBER, 0.22, p)
            }

            SoundCue.CONFIRM -> {
                // Crystalline oceanic confirm: D-minor/Dorian warm 5th + 9th shimmer (D5 + A5 + E6)
                val buf = alloc(0.185, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.020, 480.0, 390.0, 620.0, 0.007, 0.22)
                addMaritimeBell(buf, sr, 0.0, 0.172, D5, 0.0022, 0.066, partial2Mix = 0.22, upperPartialMix = 0.42, upperDecayTauSec = 0.015, amp = 0.80)
                addMaritimeBell(buf, sr, 0.015, 0.160, A5, 0.0022, 0.062, partial2Mix = 0.20, upperPartialMix = 0.44, upperDecayTauSec = 0.014, amp = 0.68)
                addMaritimeBell(buf, sr, 0.032, 0.145, E6, 0.0040, 0.056, partial2Mix = 0.14, upperPartialMix = 0.36, upperDecayTauSec = 0.012, amp = 0.42)
                addPureGlide(buf, sr, 0.0, 0.155, D4, D4, 0.008, 0.065, secondHarmonic = 0.12, amp = 0.32)
                cue(buf, 0.165, RoomStyle.NAUTICAL_CHAMBER, 0.26, p)
            }

            SoundCue.LAUNCH_GAME -> {
                // Deep resonant galleon launch chord: low D4+A4 hull swell + Dorian-9 maritime bell bloom + ocean wind
                val buf = alloc(0.540, sr)
                addBrassBell(buf, sr, 0.0, 0.500, D4, modIndex = 0.65, attackSec = 0.024, decayTauSec = 0.220, amp = 0.52)
                addPureGlide(buf, sr, 0.020, 0.470, A4, A4, 0.028, 0.200, secondHarmonic = 0.18, amp = 0.38)
                val chord = doubleArrayOf(D5, F5, A5, D6, E6)
                val starts = doubleArrayOf(0.0, 0.045, 0.095, 0.148, 0.205)
                val amps = doubleArrayOf(0.66, 0.60, 0.74, 0.84, 0.56)
                for (i in chord.indices) {
                    val s = starts[i]
                    addMaritimeBell(
                        buf, sr, s, (0.520 - s).coerceAtLeast(0.18),
                        chord[i], attackSec = 0.0035, decayTauSec = 0.155,
                        partial2Mix = 0.24, upperPartialMix = 0.44, upperDecayTauSec = 0.020, amp = amps[i],
                    )
                }
                addNoiseSweep(buf, sr, noise, 0.01, 0.34, 480.0, 3100.0, q = 1.35, amp = 0.18, pink = true)
                cue(buf, 0.190, RoomStyle.NAUTICAL_CHAMBER, 0.28, p, fadeOutMs = 9.0)
            }

            SoundCue.LAUNCH_APP -> {
                // Crisp nautical compass chime (D5 -> A5 -> E6 over warm A4 anchor)
                val buf = alloc(0.320, sr)
                addPureGlide(buf, sr, 0.0, 0.270, A4, A4, 0.010, 0.105, secondHarmonic = 0.15, amp = 0.40)
                addMaritimeBell(buf, sr, 0.0, 0.240, D5, 0.0025, 0.085, upperPartialMix = 0.38, upperDecayTauSec = 0.016, amp = 0.72)
                addMaritimeBell(buf, sr, 0.048, 0.240, A5, 0.0025, 0.092, upperPartialMix = 0.40, upperDecayTauSec = 0.016, amp = 0.80)
                addMaritimeBell(buf, sr, 0.098, 0.210, E6, 0.0035, 0.088, upperPartialMix = 0.34, upperDecayTauSec = 0.014, amp = 0.54)
                cue(buf, 0.175, RoomStyle.NAUTICAL_CHAMBER, 0.25, p)
            }

            SoundCue.BACK -> {
                // Gentle receding tide glide (F5 -> D5) + muted oak tap
                val buf = alloc(0.118, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.022, 430.0, 340.0, 500.0, 0.008, 0.20)
                addMaritimeBellGlide(buf, sr, 0.0, 0.108, F5, D5, 0.0025, 0.038, upperPartialMix = 0.28, upperDecayTauSec = 0.010, amp = 0.85)
                cue(buf, 0.142, RoomStyle.NAUTICAL_CHAMBER, 0.20, p)
            }

            SoundCue.SWAP_DISPLAY -> {
                // Sweeping nautical horizon: counter-gliding ocean tones + dual-stage horizon breeze
                val buf = alloc(0.275, sr)
                addMaritimeBellGlide(buf, sr, 0.0, 0.235, D4, A5, 0.012, 0.092, upperPartialMix = 0.34, upperDecayTauSec = 0.022, amp = 0.68)
                addMaritimeBellGlide(buf, sr, 0.038, 0.228, A5, E5, 0.012, 0.088, upperPartialMix = 0.32, upperDecayTauSec = 0.020, amp = 0.64)
                addNoiseSweep(buf, sr, noise, 0.0, 0.130, 520.0, 2900.0, q = 1.5, amp = 0.26, pink = true)
                addNoiseSweep(buf, sr, noise, 0.105, 0.145, 2700.0, 820.0, q = 1.6, amp = 0.20, pink = true)
                cue(buf, 0.165, RoomStyle.NAUTICAL_CHAMBER, 0.27, p)
            }

            SoundCue.MODE_TOGGLE -> {
                // Brass binnacle latch + ship's telegraph chime (D5 -> G5 -> A5)
                val buf = alloc(0.240, sr)
                addNoiseBurst(buf, sr, noise, 0.0, 0.018, 1750.0, q = 2.0, amp = 0.28, pink = true)
                addMaritimeBell(buf, sr, 0.008, 0.150, D5, 0.0025, 0.052, upperPartialMix = 0.42, upperDecayTauSec = 0.014, amp = 0.70)
                addMaritimeBell(buf, sr, 0.056, 0.165, G5, 0.0025, 0.058, upperPartialMix = 0.42, upperDecayTauSec = 0.015, amp = 0.74)
                addBrassBell(buf, sr, 0.106, 0.130, A5, modIndex = 0.72, attackSec = 0.003, decayTauSec = 0.052, amp = 0.66)
                cue(buf, 0.162, RoomStyle.NAUTICAL_CHAMBER, 0.24, p)
            }

            SoundCue.MODAL_OPEN -> {
                // Glassy Dorian veil lifting (D5 + A5 + E6 soft bloom + mist)
                val buf = alloc(0.175, sr)
                addMaritimeBell(buf, sr, 0.0, 0.160, D5, 0.012, 0.062, upperPartialMix = 0.30, upperDecayTauSec = 0.018, amp = 0.68)
                addMaritimeBell(buf, sr, 0.026, 0.142, A5, 0.010, 0.056, upperPartialMix = 0.32, upperDecayTauSec = 0.016, amp = 0.62)
                addPureGlide(buf, sr, 0.044, 0.120, E6, E6, 0.012, 0.048, secondHarmonic = 0.08, amp = 0.28)
                addNoiseSweep(buf, sr, noise, 0.0, 0.110, 900.0, 2100.0, q = 1.3, amp = 0.12, pink = true)
                cue(buf, 0.145, RoomStyle.NAUTICAL_CHAMBER, 0.26, p)
            }

            SoundCue.MODAL_CLOSE -> {
                // Graceful descending glass fold (A5 -> E5 -> D5)
                val buf = alloc(0.150, sr)
                addMaritimeBell(buf, sr, 0.0, 0.100, A5, 0.004, 0.038, upperPartialMix = 0.24, upperDecayTauSec = 0.012, amp = 0.60)
                addMaritimeBell(buf, sr, 0.028, 0.112, E5, 0.005, 0.044, upperPartialMix = 0.22, upperDecayTauSec = 0.012, amp = 0.64)
                addPureGlide(buf, sr, 0.050, 0.092, D5, D5, 0.005, 0.036, secondHarmonic = 0.10, amp = 0.50)
                cue(buf, 0.135, RoomStyle.NAUTICAL_CHAMBER, 0.23, p)
            }

            SoundCue.FAVORITE_ON -> {
                // Starlit maritime lantern bell (D5 -> A5 -> D6 -> E6 glassy 2.76x sparkle)
                val buf = alloc(0.285, sr)
                addMaritimeBell(buf, sr, 0.0, 0.190, A5, 0.0022, 0.068, upperPartialMix = 0.44, upperDecayTauSec = 0.016, amp = 0.74)
                addMaritimeBell(buf, sr, 0.058, 0.215, D6, 0.0022, 0.080, upperPartialMix = 0.46, upperDecayTauSec = 0.018, amp = 0.86)
                addMaritimeBell(buf, sr, 0.102, 0.175, E6, 0.0035, 0.068, upperPartialMix = 0.36, upperDecayTauSec = 0.015, amp = 0.52)
                cue(buf, 0.170, RoomStyle.NAUTICAL_CHAMBER, 0.27, p)
            }

            SoundCue.FAVORITE_OFF -> {
                // Dimming lantern descending bell (A5 -> F5 -> D5)
                val buf = alloc(0.180, sr)
                addMaritimeBell(buf, sr, 0.0, 0.102, A5, 0.003, 0.038, upperPartialMix = 0.26, upperDecayTauSec = 0.012, amp = 0.64)
                addMaritimeBell(buf, sr, 0.045, 0.125, D5, 0.004, 0.046, upperPartialMix = 0.22, upperDecayTauSec = 0.012, amp = 0.72)
                cue(buf, 0.135, RoomStyle.NAUTICAL_CHAMBER, 0.22, p)
            }

            SoundCue.MOVE_PICKUP -> {
                // Lifting oak tile: resonant oak knock + buoyant upward glass glide (D5 -> A5)
                val buf = alloc(0.152, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.032, 520.0, 440.0, 620.0, 0.010, 0.42)
                addMaritimeBellGlide(buf, sr, 0.006, 0.132, D5, A5, 0.003, 0.048, upperPartialMix = 0.34, upperDecayTauSec = 0.014, amp = 0.82)
                cue(buf, 0.152, RoomStyle.NAUTICAL_CHAMBER, 0.20, p)
            }

            SoundCue.MOVE_SWAP -> {
                // Sliding carved oak tile on deck
                val buf = alloc(0.068, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.038, 600.0, 710.0, 1250.0, 0.013, 0.62)
                addMaritimeBellGlide(buf, sr, 0.0, 0.058, E5, G5, 0.002, 0.020, upperPartialMix = 0.26, upperDecayTauSec = 0.008, amp = 0.66)
                cue(buf, 0.134, RoomStyle.NAUTICAL_CHAMBER, 0.12, p)
            }

            SoundCue.MOVE_DROP -> {
                // Settling solidly on oak deck: warm oak thump + confirming A4->D5 bell settle
                val buf = alloc(0.168, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.072, 290.0, 168.0, 460.0, 0.024, 0.78)
                addMaritimeBellGlide(buf, sr, 0.010, 0.145, A5, D5, 0.0025, 0.052, upperPartialMix = 0.30, upperDecayTauSec = 0.014, amp = 0.74)
                cue(buf, 0.160, RoomStyle.NAUTICAL_CHAMBER, 0.22, p)
            }

            SoundCue.DESTRUCTIVE -> {
                // Solemn ship's bronze fog-bell caution (F4 -> E4, then D4 -> C4)
                val buf = alloc(0.255, sr)
                addMaritimeBellGlide(buf, sr, 0.0, 0.118, F4, E4, 0.0035, 0.046, upperPartialMix = 0.28, upperDecayTauSec = 0.016, amp = 0.80)
                addBrassBell(buf, sr, 0.088, 0.158, D4, modIndex = 0.75, attackSec = 0.004, decayTauSec = 0.060, amp = 0.86)
                addPureGlide(buf, sr, 0.095, 0.145, D4, C4, 0.006, 0.055, secondHarmonic = 0.10, amp = 0.42)
                cue(buf, 0.162, RoomStyle.NAUTICAL_CHAMBER, 0.23, p)
            }

            SoundCue.FILTER_SELECT -> {
                // Crisp brass compass notch (A5 + E6 glassy glint)
                val buf = alloc(0.092, sr)
                addMaritimeBell(buf, sr, 0.0, 0.082, A5, 0.0018, 0.028, upperPartialMix = 0.44, upperDecayTauSec = 0.010, amp = 0.78)
                addMaritimeBell(buf, sr, 0.008, 0.070, E6, 0.0022, 0.024, upperPartialMix = 0.34, upperDecayTauSec = 0.008, amp = 0.40)
                cue(buf, 0.136, RoomStyle.NAUTICAL_CHAMBER, 0.16, p)
            }

            SoundCue.ERROR_DENY -> {
                // Muted double hull-knock + hollow bronze bell (E4 -> D4)
                val buf = alloc(0.215, sr)
                addOakKnock(buf, sr, noise, 0.0, 0.052, 260.0, 195.0, 420.0, 0.018, 0.45)
                addRetroFm(buf, sr, 0.0, 0.082, E4, modRatio = 1.48, startModIndex = 0.85, endModIndex = 0.20, attackSec = 0.0025, decayTauSec = 0.028, amp = 0.72)
                addRetroFm(buf, sr, 0.084, 0.120, D4, modRatio = 1.48, startModIndex = 0.90, endModIndex = 0.22, attackSec = 0.0025, decayTauSec = 0.042, amp = 0.82)
                cue(buf, 0.158, RoomStyle.NAUTICAL_CHAMBER, 0.14, p)
            }

            SoundCue.BOOT_CHIME -> {
                // Celebratory brass-bell galleon departure (deep D4+A4 hull + brass-bell Dorian-9 arc + ocean swell)
                val dur = 1.220
                val buf = alloc(dur, sr)
                addBrassBell(buf, sr, 0.0, dur * 0.94, D4, modIndex = 0.72, attackSec = 0.045, decayTauSec = 0.440, amp = 0.46)
                addBrassBell(buf, sr, 0.045, dur * 0.88, A4, modIndex = 0.62, attackSec = 0.040, decayTauSec = 0.400, amp = 0.40)
                val arpNotes = doubleArrayOf(D5, E5, F5, A5, D6, E6)
                val arpStarts = doubleArrayOf(0.06, 0.16, 0.26, 0.37, 0.49, 0.62)
                val arpAmps = doubleArrayOf(0.56, 0.54, 0.58, 0.70, 0.82, 0.62)
                for (i in arpNotes.indices) {
                    val start = arpStarts[i]
                    val rem = (dur - start).coerceAtLeast(0.18)
                    if (i >= 3) {
                        addBrassBell(buf, sr, start, rem, arpNotes[i], modIndex = 0.88, attackSec = 0.007, decayTauSec = rem * 0.40, amp = arpAmps[i] * 0.65)
                    }
                    addMaritimeBell(buf, sr, start, rem, arpNotes[i], attackSec = 0.006, decayTauSec = rem * 0.38, partial2Mix = 0.22, upperPartialMix = 0.42, upperDecayTauSec = 0.026, amp = arpAmps[i] * 0.75)
                }
                addNoiseSweep(buf, sr, noise, 0.02, dur * 0.72, 460.0, 2350.0, q = 1.3, amp = 0.14, pink = true)
                cue(buf, 0.185, RoomStyle.NAUTICAL_CHAMBER, 0.28, p, fadeOutMs = 12.0)
            }

            SoundCue.RA_ACHIEVEMENT -> {
                // Celebratory brass-bell trophy peal (D5 -> F#5 -> A5 -> D6 Picardy triumph + brass harmony + glassy crown)
                val dur = 1.020
                val buf = alloc(dur, sr)
                addBrassBell(buf, sr, 0.0, 0.900, D4, modIndex = 0.65, attackSec = 0.018, decayTauSec = 0.340, amp = 0.36)
                val notes = doubleArrayOf(D5, FS5, A5, D6)
                val starts = doubleArrayOf(0.0, 0.095, 0.195, 0.310)
                val taus = doubleArrayOf(0.095, 0.100, 0.125, 0.310)
                val amps = doubleArrayOf(0.70, 0.74, 0.82, 0.92)
                for (i in notes.indices) {
                    val start = starts[i]
                    val rem = (dur - start).coerceAtLeast(0.16)
                    addBrassBell(buf, sr, start, rem, notes[i], modIndex = 0.95, attackSec = 0.0035, decayTauSec = taus[i], amp = amps[i] * 0.68)
                    addMaritimeBell(buf, sr, start, rem, notes[i], attackSec = 0.0028, decayTauSec = taus[i] * 1.08, partial2Mix = 0.24, upperPartialMix = 0.48, upperDecayTauSec = 0.024, amp = amps[i] * 0.68)
                }
                addBrassBell(buf, sr, 0.310, dur - 0.310, A5, modIndex = 0.60, attackSec = 0.010, decayTauSec = 0.270, amp = 0.48)
                addMaritimeBell(buf, sr, 0.340, dur - 0.340, FS6, attackSec = 0.005, decayTauSec = 0.220, upperPartialMix = 0.42, upperDecayTauSec = 0.022, amp = 0.48)
                cue(buf, 0.198, RoomStyle.NAUTICAL_CHAMBER, 0.28, p, fadeOutMs = 10.0)
            }

            SoundCue.SETUP_COMPLETE -> {
                // Horizon-cleared maritime brass-glass cadence (D5 -> G5 -> A5 -> D6 + E6 shimmer)
                val dur = 0.840
                val buf = alloc(dur, sr)
                val notes = doubleArrayOf(D5, G5, A5, D6)
                val starts = doubleArrayOf(0.0, 0.095, 0.195, 0.305)
                val amps = doubleArrayOf(0.66, 0.70, 0.78, 0.88)
                for (i in notes.indices) {
                    val start = starts[i]
                    val rem = (dur - start).coerceAtLeast(0.16)
                    addBrassBell(buf, sr, start, rem, notes[i], modIndex = 0.74, attackSec = 0.005, decayTauSec = 0.185, amp = amps[i] * 0.55)
                    addMaritimeBell(buf, sr, start, rem, notes[i], attackSec = 0.004, decayTauSec = 0.190, upperPartialMix = 0.38, upperDecayTauSec = 0.020, amp = amps[i] * 0.65)
                }
                addMaritimeBell(buf, sr, 0.315, dur - 0.315, FS5, 0.010, 0.210, upperPartialMix = 0.24, upperDecayTauSec = 0.016, amp = 0.44)
                addMaritimeBell(buf, sr, 0.355, dur - 0.355, E6, 0.008, 0.180, upperPartialMix = 0.34, upperDecayTauSec = 0.015, amp = 0.34)
                cue(buf, 0.182, RoomStyle.NAUTICAL_CHAMBER, 0.26, p, fadeOutMs = 9.0)
            }
        }
    }

    // =========================================================================
    // PACK 2: THREEDS — Classic Handheld Tactile (Teal: C/G Major Pentatonic, Woody Marimba/Kalimba)
    // =========================================================================

    private fun renderThreeDs(cue: SoundCue, noise: AudioDsp.SeededNoise, sr: Int): RenderedCue {
        val p = THREEDS_PROFILE
        return when (cue) {
            SoundCue.NAV_TICK -> {
                // Warm woody marimba/kalimba tick: soft triangle + crisp rosewood mallet transient
                val buf = alloc(0.034, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.030, freqHz = 740.0, endHz = 675.0, decayTauSec = 0.0095, malletAmp = 0.28, amp = 0.94)
                cue(buf, 0.125, RoomStyle.NONE, 0.0, p, fadeOutMs = 4.5)
            }

            SoundCue.NAV_EDGE_BUMP -> {
                // Hollow wooden toy-block double bounce
                val buf = alloc(0.078, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.062, freqHz = 245.0, endHz = 158.0, decayTauSec = 0.020, malletAmp = 0.24, barOvertoneMix = 0.14, amp = 0.94)
                addMarimbaBar(buf, sr, noise, 0.022, 0.048, freqHz = 198.0, endHz = 152.0, decayTauSec = 0.015, malletAmp = 0.12, barOvertoneMix = 0.10, amp = 0.42)
                cue(buf, 0.142, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.NAV_PAGE_TURN -> {
                // Breezy filtered-noise page swoosh + playful kalimba fifth flick (C5 -> G5)
                val buf = alloc(0.128, sr)
                addNoiseSweep(buf, sr, noise, 0.0, 0.115, 620.0, 2650.0, q = 1.25, amp = 0.68, pink = true)
                addMarimbaGlide(buf, sr, noise, 0.012, 0.098, C5, G5, decayTauSec = 0.032, malletAmp = 0.18, amp = 0.56)
                cue(buf, 0.135, RoomStyle.WOODEN_MICRO, 0.12, p)
            }

            SoundCue.DOCK_ENTER -> {
                // Two-bar descending marimba step (G5 -> C5)
                val buf = alloc(0.092, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.048, G5, G5, decayTauSec = 0.017, malletAmp = 0.24, amp = 0.72)
                addMarimbaBar(buf, sr, noise, 0.026, 0.062, C5, C5, decayTauSec = 0.022, malletAmp = 0.26, amp = 0.88)
                cue(buf, 0.138, RoomStyle.WOODEN_MICRO, 0.10, p)
            }

            SoundCue.DOCK_EXIT -> {
                // Two-bar ascending marimba hop (C5 -> G5)
                val buf = alloc(0.092, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.048, C5, C5, decayTauSec = 0.017, malletAmp = 0.24, amp = 0.72)
                addMarimbaBar(buf, sr, noise, 0.026, 0.062, G5, G5, decayTauSec = 0.022, malletAmp = 0.28, amp = 0.88)
                cue(buf, 0.138, RoomStyle.WOODEN_MICRO, 0.10, p)
            }

            SoundCue.CONFIRM -> {
                // Joyful rising major-pentatonic / fifth marimba confirm (C5 -> G5 -> E6 sparkle)
                val buf = alloc(0.132, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.112, C5, C5, decayTauSec = 0.036, malletAmp = 0.28, amp = 0.84)
                addMarimbaBar(buf, sr, noise, 0.018, 0.106, G5, G5, decayTauSec = 0.036, malletAmp = 0.26, amp = 0.76)
                addMarimbaBar(buf, sr, noise, 0.038, 0.088, E6, E6, decayTauSec = 0.028, malletAmp = 0.16, barOvertoneMix = 0.16, amp = 0.40)
                cue(buf, 0.162, RoomStyle.WOODEN_MICRO, 0.12, p)
            }

            SoundCue.LAUNCH_GAME -> {
                // Uplifting 3-note handheld arpeggio (C5 -> E5 -> G5 -> resolving C6) + warm wooden bass + breeze
                val buf = alloc(0.415, sr)
                addPureGlide(buf, sr, 0.0, 0.360, C4, C4, 0.016, 0.140, secondHarmonic = 0.16, amp = 0.42)
                addMarimbaBar(buf, sr, noise, 0.0, 0.210, C5, C5, decayTauSec = 0.072, malletAmp = 0.26, amp = 0.74)
                addMarimbaBar(buf, sr, noise, 0.062, 0.220, E5, E5, decayTauSec = 0.078, malletAmp = 0.26, amp = 0.78)
                addMarimbaBar(buf, sr, noise, 0.124, 0.240, G5, G5, decayTauSec = 0.086, malletAmp = 0.28, amp = 0.84)
                addMarimbaBar(buf, sr, noise, 0.186, 0.220, C6, C6, decayTauSec = 0.095, malletAmp = 0.24, barOvertoneMix = 0.22, amp = 0.88)
                addNoiseSweep(buf, sr, noise, 0.015, 0.210, 720.0, 2900.0, q = 1.35, amp = 0.18, pink = true)
                cue(buf, 0.185, RoomStyle.WOODEN_MICRO, 0.14, p)
            }

            SoundCue.LAUNCH_APP -> {
                // Cheerful 3-note kalimba hop (G4 -> C5 -> G5)
                val buf = alloc(0.255, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.150, G4, G4, decayTauSec = 0.052, malletAmp = 0.24, amp = 0.74)
                addMarimbaBar(buf, sr, noise, 0.048, 0.170, C5, C5, decayTauSec = 0.062, malletAmp = 0.26, amp = 0.82)
                addMarimbaBar(buf, sr, noise, 0.098, 0.150, G5, G5, decayTauSec = 0.060, malletAmp = 0.24, amp = 0.80)
                cue(buf, 0.172, RoomStyle.WOODEN_MICRO, 0.12, p)
            }

            SoundCue.BACK -> {
                // Soft wooden step-back (E5 -> C5 gentle marimba pair)
                val buf = alloc(0.092, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.084, E5, C5, decayTauSec = 0.026, malletAmp = 0.22, amp = 0.86)
                cue(buf, 0.140, RoomStyle.WOODEN_MICRO, 0.10, p)
            }

            SoundCue.SWAP_DISPLAY -> {
                // Breezy dual-screen flip swoosh + playful crossed marimba flips
                val buf = alloc(0.210, sr)
                addNoiseSweep(buf, sr, noise, 0.0, 0.175, 820.0, 2600.0, q = 1.4, amp = 0.34, pink = true)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.165, G4, G5, decayTauSec = 0.056, malletAmp = 0.20, amp = 0.66)
                addMarimbaGlide(buf, sr, noise, 0.036, 0.165, E5, C5, decayTauSec = 0.056, malletAmp = 0.20, amp = 0.66)
                cue(buf, 0.158, RoomStyle.WOODEN_MICRO, 0.13, p)
            }

            SoundCue.MODE_TOGGLE -> {
                // Tactile wooden switch click + cheerful 3-step kalimba toggle (C5 -> D5 -> G5)
                val buf = alloc(0.188, sr)
                addNoiseBurst(buf, sr, noise, 0.0, 0.012, 1650.0, q = 2.2, amp = 0.28, pink = true)
                addMarimbaBar(buf, sr, noise, 0.008, 0.105, C5, C5, decayTauSec = 0.036, malletAmp = 0.24, amp = 0.72)
                addMarimbaBar(buf, sr, noise, 0.048, 0.115, D5, D5, decayTauSec = 0.040, malletAmp = 0.24, amp = 0.74)
                addMarimbaBar(buf, sr, noise, 0.090, 0.095, G5, G5, decayTauSec = 0.036, malletAmp = 0.26, amp = 0.76)
                cue(buf, 0.158, RoomStyle.WOODEN_MICRO, 0.11, p)
            }

            SoundCue.MODAL_OPEN -> {
                // Buoyant wooden bubble-pop open (D5 -> A5 elastic pop + kalimba resonance)
                val buf = alloc(0.134, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.108, D5, A5, decayTauSec = 0.038, malletAmp = 0.22, amp = 0.78)
                addMarimbaBar(buf, sr, noise, 0.030, 0.098, A5, A5, decayTauSec = 0.036, malletAmp = 0.16, amp = 0.56)
                cue(buf, 0.145, RoomStyle.WOODEN_MICRO, 0.12, p)
            }

            SoundCue.MODAL_CLOSE -> {
                // Gentle tuck-away pop (A5 -> D5)
                val buf = alloc(0.114, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.098, A5, D5, decayTauSec = 0.032, malletAmp = 0.20, amp = 0.78)
                cue(buf, 0.136, RoomStyle.WOODEN_MICRO, 0.11, p)
            }

            SoundCue.FAVORITE_ON -> {
                // Bright kalimba twinkle (G5 -> A5 -> C6 -> E6)
                val buf = alloc(0.235, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.135, G5, G5, decayTauSec = 0.046, malletAmp = 0.26, amp = 0.74)
                addMarimbaBar(buf, sr, noise, 0.048, 0.150, C6, C6, decayTauSec = 0.056, malletAmp = 0.26, amp = 0.84)
                addMarimbaBar(buf, sr, noise, 0.092, 0.135, E6, E6, decayTauSec = 0.052, malletAmp = 0.20, amp = 0.56)
                cue(buf, 0.168, RoomStyle.WOODEN_MICRO, 0.13, p)
            }

            SoundCue.FAVORITE_OFF -> {
                // Gentle downward kalimba step (G5 -> E5 -> C5)
                val buf = alloc(0.145, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.082, G5, G5, decayTauSec = 0.028, malletAmp = 0.22, amp = 0.66)
                addMarimbaBar(buf, sr, noise, 0.042, 0.098, C5, C5, decayTauSec = 0.034, malletAmp = 0.22, amp = 0.74)
                cue(buf, 0.136, RoomStyle.WOODEN_MICRO, 0.10, p)
            }

            SoundCue.MOVE_PICKUP -> {
                // Playful elastic pop pickup (C5 -> G5 quick upward pop + woody tap!)
                val buf = alloc(0.118, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.095, C5, G5, decayTauSec = 0.030, malletAmp = 0.30, amp = 0.88)
                addMarimbaBar(buf, sr, noise, 0.024, 0.084, G5, G5, decayTauSec = 0.026, malletAmp = 0.18, amp = 0.48)
                cue(buf, 0.152, RoomStyle.WOODEN_MICRO, 0.10, p)
            }

            SoundCue.MOVE_SWAP -> {
                // Bouncy tactile tile hop (E5 -> G5 quick wooden blip)
                val buf = alloc(0.054, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.048, E5, G5, decayTauSec = 0.015, malletAmp = 0.28, amp = 0.86)
                cue(buf, 0.135, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.MOVE_DROP -> {
                // Playful pop-and-drop settle (G5 -> C5 elastic drop + hollow C4 wooden bounce!)
                val buf = alloc(0.134, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.096, G5, C5, decayTauSec = 0.032, malletAmp = 0.26, amp = 0.80)
                addMarimbaBar(buf, sr, noise, 0.016, 0.110, C4, 210.0, decayTauSec = 0.036, malletAmp = 0.22, barOvertoneMix = 0.14, amp = 0.66)
                cue(buf, 0.158, RoomStyle.WOODEN_MICRO, 0.11, p)
            }

            SoundCue.DESTRUCTIVE -> {
                // Firm wooden double-thud caution (E4 -> D4, then C4 -> A3)
                val buf = alloc(0.208, sr)
                addMarimbaGlide(buf, sr, noise, 0.0, 0.096, E4, D4, decayTauSec = 0.034, malletAmp = 0.24, amp = 0.80)
                addMarimbaGlide(buf, sr, noise, 0.076, 0.125, C4, A3, decayTauSec = 0.044, malletAmp = 0.26, amp = 0.88)
                cue(buf, 0.158, RoomStyle.WOODEN_MICRO, 0.10, p)
            }

            SoundCue.FILTER_SELECT -> {
                // Crisp high kalimba pluck (A5 + E6 quick overtone ping)
                val buf = alloc(0.072, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.064, A5, A5, decayTauSec = 0.020, malletAmp = 0.26, amp = 0.82)
                addPureGlide(buf, sr, 0.006, 0.052, E6, E6, 0.002, 0.016, secondHarmonic = 0.08, amp = 0.34)
                cue(buf, 0.135, RoomStyle.WOODEN_MICRO, 0.08, p)
            }

            SoundCue.ERROR_DENY -> {
                // Playful muted wooden "boop-boop" (D4 -> B3)
                val buf = alloc(0.184, sr)
                addMarimbaBar(buf, sr, noise, 0.0, 0.072, D4, 275.0, decayTauSec = 0.024, malletAmp = 0.26, barOvertoneMix = 0.16, amp = 0.82)
                addMarimbaBar(buf, sr, noise, 0.076, 0.100, B3, 226.0, decayTauSec = 0.032, malletAmp = 0.26, barOvertoneMix = 0.16, amp = 0.86)
                cue(buf, 0.155, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.BOOT_CHIME -> {
                // Uplifting 3-note handheld arpeggio welcome (C5 -> E5 -> G5, then sweet A5 -> C6 kalimba sparkle)
                val dur = 0.940
                val buf = alloc(dur, sr)
                addPureGlide(buf, sr, 0.0, dur * 0.88, C4, C4, 0.035, 0.340, secondHarmonic = 0.14, amp = 0.42)
                addPureGlide(buf, sr, 0.04, dur * 0.84, G4, G4, 0.032, 0.310, secondHarmonic = 0.12, amp = 0.36)
                val notes = doubleArrayOf(C5, E5, G5, A5, C6)
                val starts = doubleArrayOf(0.05, 0.16, 0.27, 0.41, 0.52)
                val amps = doubleArrayOf(0.70, 0.74, 0.80, 0.66, 0.88)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.16)
                    addMarimbaBar(buf, sr, noise, s, rem, notes[i], notes[i], decayTauSec = rem * 0.34, malletAmp = 0.22, barOvertoneMix = 0.24, amp = amps[i])
                }
                addNoiseSweep(buf, sr, noise, 0.02, 0.55, 600.0, 2200.0, q = 1.25, amp = 0.12, pink = true)
                cue(buf, 0.178, RoomStyle.WOODEN_MICRO, 0.15, p, fadeOutMs = 8.0)
            }

            SoundCue.RA_ACHIEVEMENT -> {
                // Joyful handheld medal fanfare (C5 -> D5 -> E5 -> G5 -> C6 + E6 kalimba sparkle)
                val dur = 0.860
                val buf = alloc(dur, sr)
                val notes = doubleArrayOf(C5, D5, E5, G5, C6)
                val starts = doubleArrayOf(0.0, 0.075, 0.150, 0.235, 0.335)
                val taus = doubleArrayOf(0.072, 0.075, 0.085, 0.105, 0.240)
                val amps = doubleArrayOf(0.70, 0.72, 0.76, 0.82, 0.92)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.14)
                    addMarimbaBar(buf, sr, noise, s, rem, notes[i], notes[i], decayTauSec = taus[i], malletAmp = 0.26, barOvertoneMix = 0.26, amp = amps[i])
                }
                addMarimbaBar(buf, sr, noise, 0.335, dur - 0.335, G5, G5, decayTauSec = 0.210, malletAmp = 0.14, amp = 0.50)
                addMarimbaBar(buf, sr, noise, 0.375, dur - 0.375, E6, E6, decayTauSec = 0.180, malletAmp = 0.18, amp = 0.48)
                cue(buf, 0.188, RoomStyle.WOODEN_MICRO, 0.15, p, fadeOutMs = 8.0)
            }

            SoundCue.SETUP_COMPLETE -> {
                // Sunny handheld completion cadence (C5 -> E5 -> G5 -> C6)
                val dur = 0.720
                val buf = alloc(dur, sr)
                val notes = doubleArrayOf(C5, E5, G5, C6)
                val starts = doubleArrayOf(0.0, 0.082, 0.168, 0.265)
                val amps = doubleArrayOf(0.68, 0.74, 0.80, 0.88)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.14)
                    addMarimbaBar(buf, sr, noise, s, rem, notes[i], notes[i], decayTauSec = 0.145, malletAmp = 0.24, barOvertoneMix = 0.24, amp = amps[i])
                }
                addPureGlide(buf, sr, 0.265, dur - 0.265, E5, E5, 0.008, 0.160, secondHarmonic = 0.12, amp = 0.44)
                cue(buf, 0.175, RoomStyle.WOODEN_MICRO, 0.14, p, fadeOutMs = 7.5)
            }
        }
    }

    // =========================================================================
    // PACK 3: OLED — Whisper-Quiet Minimalist Precision (F Major / Pure Fifths, Sub-30ms Ticks)
    // =========================================================================

    private fun renderOled(cue: SoundCue, noise: AudioDsp.SeededNoise, sr: Int): RenderedCue {
        val p = OLED_PROFILE
        return when (cue) {
            SoundCue.NAV_TICK -> {
                // Ultra-clean short damped micro-tick (24 ms < 30 ms!): high-precision woody-glass click
                val buf = alloc(0.024, sr)
                addPureGlide(buf, sr, 0.0, 0.022, 920.0, 740.0, attackSec = 0.0012, decayTauSec = 0.0046, secondHarmonic = 0.04, amp = 0.92)
                addNoiseBurst(buf, sr, noise, 0.0, 0.004, 1850.0, q = 2.6, amp = 0.09, pink = true)
                cue(buf, 0.082, RoomStyle.NONE, 0.0, p, fadeInMs = 1.5, fadeOutMs = 4.0)
            }

            SoundCue.NAV_EDGE_BUMP -> {
                // Whisper-gentle edge thump (velvet pure-sine cushion tuned cleanly above micro-speaker rattle band)
                val buf = alloc(0.052, sr)
                addPureGlide(buf, sr, 0.0, 0.048, 220.0, 162.0, attackSec = 0.003, decayTauSec = 0.013, secondHarmonic = 0.04, amp = 0.94)
                cue(buf, 0.094, RoomStyle.NONE, 0.0, p, fadeInMs = 1.6, fadeOutMs = 4.5)
            }

            SoundCue.NAV_PAGE_TURN -> {
                // Whisper-quiet satin page breath
                val buf = alloc(0.078, sr)
                addNoiseSweep(buf, sr, noise, 0.0, 0.068, 520.0, 1320.0, q = 1.2, amp = 0.16, pink = true)
                addPureGlide(buf, sr, 0.006, 0.068, F4, C5, attackSec = 0.008, decayTauSec = 0.022, secondHarmonic = 0.03, amp = 0.74)
                cue(buf, 0.090, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.DOCK_ENTER -> {
                // Quiet descending pure-sine fifth (C5 -> F4)
                val buf = alloc(0.066, sr)
                addPureGlide(buf, sr, 0.0, 0.034, C5, C5, 0.002, 0.012, secondHarmonic = 0.03, amp = 0.68)
                addPureGlide(buf, sr, 0.020, 0.044, F4, F4, 0.002, 0.016, secondHarmonic = 0.03, amp = 0.84)
                cue(buf, 0.092, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.DOCK_EXIT -> {
                // Quiet ascending pure-sine fifth (F4 -> C5)
                val buf = alloc(0.066, sr)
                addPureGlide(buf, sr, 0.0, 0.034, F4, F4, 0.002, 0.012, secondHarmonic = 0.03, amp = 0.68)
                addPureGlide(buf, sr, 0.020, 0.044, C5, C5, 0.002, 0.016, secondHarmonic = 0.03, amp = 0.84)
                cue(buf, 0.092, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.CONFIRM -> {
                // Restrained pure-sine fifth confirm (F4 -> C5, ultra-clean)
                val buf = alloc(0.086, sr)
                addPureGlide(buf, sr, 0.0, 0.078, F4, F4, 0.002, 0.026, secondHarmonic = 0.04, amp = 0.82)
                addPureGlide(buf, sr, 0.012, 0.070, C5, C5, 0.002, 0.024, secondHarmonic = 0.04, amp = 0.68)
                addNoiseBurst(buf, sr, noise, 0.0, 0.003, 1900.0, q = 2.5, amp = 0.05, pink = true)
                cue(buf, 0.106, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.LAUNCH_GAME -> {
                // Serene late-night pure-sine OLED triad bloom (F4 -> A4 -> C5 -> F5)
                val buf = alloc(0.285, sr)
                val notes = doubleArrayOf(F4, A4, C5, F5)
                val starts = doubleArrayOf(0.0, 0.042, 0.084, 0.128)
                val amps = doubleArrayOf(0.68, 0.72, 0.78, 0.84)
                for (i in notes.indices) {
                    val s = starts[i]
                    addPureGlide(buf, sr, s, (0.270 - s).coerceAtLeast(0.09), notes[i], notes[i], 0.008, 0.072, secondHarmonic = 0.04, amp = amps[i])
                }
                cue(buf, 0.124, RoomStyle.WHISPER_DRY, 0.03, p)
            }

            SoundCue.LAUNCH_APP -> {
                // Minimalist pure-sine fifth + octave (F4 -> C5 -> F5)
                val buf = alloc(0.185, sr)
                addPureGlide(buf, sr, 0.0, 0.120, F4, F4, 0.005, 0.042, secondHarmonic = 0.03, amp = 0.74)
                addPureGlide(buf, sr, 0.036, 0.130, C5, C5, 0.005, 0.048, secondHarmonic = 0.03, amp = 0.80)
                addPureGlide(buf, sr, 0.074, 0.105, F5, F5, 0.005, 0.042, secondHarmonic = 0.04, amp = 0.64)
                cue(buf, 0.118, RoomStyle.WHISPER_DRY, 0.02, p)
            }

            SoundCue.BACK -> {
                // Minimalist downward pure-sine sigh (A4 -> F4)
                val buf = alloc(0.068, sr)
                addPureGlide(buf, sr, 0.0, 0.062, A4, F4, 0.0025, 0.019, secondHarmonic = 0.03, amp = 0.84)
                cue(buf, 0.098, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.SWAP_DISPLAY -> {
                // Subtle phase-clean pure-sine cross-glide + whisper breath
                val buf = alloc(0.148, sr)
                addPureGlide(buf, sr, 0.0, 0.128, F4, C5, 0.008, 0.046, secondHarmonic = 0.03, amp = 0.70)
                addPureGlide(buf, sr, 0.024, 0.118, C5, G4, 0.008, 0.044, secondHarmonic = 0.03, amp = 0.66)
                addNoiseSweep(buf, sr, noise, 0.0, 0.110, 680.0, 1480.0, q = 1.4, amp = 0.09, pink = true)
                cue(buf, 0.108, RoomStyle.WHISPER_DRY, 0.02, p)
            }

            SoundCue.MODE_TOGGLE -> {
                // Precision micro-switch tick + clean pure-sine step (F4 -> G4 -> C5)
                val buf = alloc(0.132, sr)
                addNoiseBurst(buf, sr, noise, 0.0, 0.005, 1800.0, q = 2.4, amp = 0.08, pink = true)
                addPureGlide(buf, sr, 0.006, 0.072, F4, F4, 0.003, 0.026, secondHarmonic = 0.03, amp = 0.72)
                addPureGlide(buf, sr, 0.044, 0.082, C5, C5, 0.003, 0.030, secondHarmonic = 0.04, amp = 0.78)
                cue(buf, 0.108, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.MODAL_OPEN -> {
                // Low-profile modal breath (soft cosine swell F4 + C5 + whisper mist)
                val buf = alloc(0.098, sr)
                addPureGlide(buf, sr, 0.0, 0.090, F4, F4, 0.014, 0.032, secondHarmonic = 0.02, amp = 0.72)
                addPureGlide(buf, sr, 0.020, 0.074, C5, C5, 0.012, 0.028, secondHarmonic = 0.02, amp = 0.58)
                addNoiseSweep(buf, sr, noise, 0.0, 0.070, 560.0, 1100.0, q = 1.2, amp = 0.07, pink = true)
                cue(buf, 0.100, RoomStyle.WHISPER_DRY, 0.03, p)
            }

            SoundCue.MODAL_CLOSE -> {
                // Low-profile modal exhale (C5 -> F4 gentle fade)
                val buf = alloc(0.086, sr)
                addPureGlide(buf, sr, 0.0, 0.058, C5, C5, 0.005, 0.020, secondHarmonic = 0.02, amp = 0.60)
                addPureGlide(buf, sr, 0.018, 0.064, F4, F4, 0.006, 0.024, secondHarmonic = 0.02, amp = 0.72)
                cue(buf, 0.096, RoomStyle.WHISPER_DRY, 0.02, p)
            }

            SoundCue.FAVORITE_ON -> {
                // Delicate pure-sine gleam (C5 -> F5 -> A5)
                val buf = alloc(0.162, sr)
                addPureGlide(buf, sr, 0.0, 0.105, C5, C5, 0.003, 0.036, secondHarmonic = 0.04, amp = 0.74)
                addPureGlide(buf, sr, 0.042, 0.114, F5, F5, 0.003, 0.042, secondHarmonic = 0.05, amp = 0.84)
                addPureGlide(buf, sr, 0.072, 0.084, A5, A5, 0.004, 0.032, secondHarmonic = 0.03, amp = 0.36)
                cue(buf, 0.112, RoomStyle.WHISPER_DRY, 0.03, p)
            }

            SoundCue.FAVORITE_OFF -> {
                // Soft descending pure-sine pair (C5 -> G4)
                val buf = alloc(0.105, sr)
                addPureGlide(buf, sr, 0.0, 0.062, C5, C5, 0.003, 0.022, secondHarmonic = 0.02, amp = 0.64)
                addPureGlide(buf, sr, 0.032, 0.068, G4, G4, 0.004, 0.024, secondHarmonic = 0.02, amp = 0.72)
                cue(buf, 0.096, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.MOVE_PICKUP -> {
                // Minimal precision lift (F4 -> C5 pure-sine glide)
                val buf = alloc(0.082, sr)
                addPureGlide(buf, sr, 0.0, 0.076, F4, C5, 0.0025, 0.024, secondHarmonic = 0.03, amp = 0.84)
                cue(buf, 0.102, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.MOVE_SWAP -> {
                // Tight sub-40ms micro-shift click (580 -> 670 Hz pure sine)
                val buf = alloc(0.036, sr)
                addPureGlide(buf, sr, 0.0, 0.032, 580.0, 670.0, 0.0016, 0.009, secondHarmonic = 0.03, amp = 0.86)
                cue(buf, 0.088, RoomStyle.NONE, 0.0, p, fadeOutMs = 4.2)
            }

            SoundCue.MOVE_DROP -> {
                // Soft damped pure-sine settle (C5 -> F4 + gentle 205 Hz cushion)
                val buf = alloc(0.090, sr)
                addPureGlide(buf, sr, 0.0, 0.078, C5, F4, 0.002, 0.024, secondHarmonic = 0.03, amp = 0.78)
                addPureGlide(buf, sr, 0.010, 0.072, 210.0, 165.0, 0.003, 0.024, secondHarmonic = 0.02, amp = 0.46)
                cue(buf, 0.104, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.DESTRUCTIVE -> {
                // Understated low pure-sine two-step caution (G4 -> F4, then D4 -> C4)
                val buf = alloc(0.148, sr)
                addPureGlide(buf, sr, 0.0, 0.068, G4, F4, 0.003, 0.024, secondHarmonic = 0.03, amp = 0.78)
                addPureGlide(buf, sr, 0.056, 0.086, D4, C4, 0.003, 0.030, secondHarmonic = 0.03, amp = 0.84)
                cue(buf, 0.108, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.FILTER_SELECT -> {
                // High-precision glass micro-pip (C5 -> G5 pure sine)
                val buf = alloc(0.048, sr)
                addPureGlide(buf, sr, 0.0, 0.042, C5, C5, 0.0015, 0.013, secondHarmonic = 0.04, amp = 0.80)
                addPureGlide(buf, sr, 0.005, 0.036, G5, G5, 0.0018, 0.011, secondHarmonic = 0.02, amp = 0.34)
                cue(buf, 0.094, RoomStyle.NONE, 0.0, p, fadeOutMs = 4.2)
            }

            SoundCue.ERROR_DENY -> {
                // Gentle late-night muted pure-sine double pulse (E4 -> D4)
                val buf = alloc(0.128, sr)
                addPureGlide(buf, sr, 0.0, 0.052, E4, E4, 0.003, 0.017, secondHarmonic = 0.05, amp = 0.78)
                addPureGlide(buf, sr, 0.056, 0.066, D4, D4, 0.003, 0.022, secondHarmonic = 0.05, amp = 0.84)
                cue(buf, 0.106, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.BOOT_CHIME -> {
                // Serene late-night OLED wake (calm F3+C4 pure-sine pad + F4 -> G4 -> A4 -> C5 -> F5 arc)
                val dur = 0.680
                val buf = alloc(dur, sr)
                addPureGlide(buf, sr, 0.0, dur * 0.90, F3, F3, 0.040, 0.240, secondHarmonic = 0.03, amp = 0.40)
                addPureGlide(buf, sr, 0.035, dur * 0.84, C4, C4, 0.035, 0.220, secondHarmonic = 0.03, amp = 0.36)
                val notes = doubleArrayOf(F4, G4, A4, C5, F5)
                val starts = doubleArrayOf(0.05, 0.13, 0.21, 0.30, 0.40)
                val amps = doubleArrayOf(0.56, 0.58, 0.64, 0.72, 0.78)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.12)
                    addPureGlide(buf, sr, s, rem, notes[i], notes[i], 0.010, rem * 0.34, secondHarmonic = 0.04, amp = amps[i])
                }
                cue(buf, 0.126, RoomStyle.WHISPER_DRY, 0.04, p, fadeOutMs = 6.5)
            }

            SoundCue.RA_ACHIEVEMENT -> {
                // Refined minimalist trophy chime (pure-sine F4 -> A4 -> C5 -> F5 -> A5)
                val dur = 0.620
                val buf = alloc(dur, sr)
                val notes = doubleArrayOf(F4, A4, C5, F5)
                val starts = doubleArrayOf(0.0, 0.072, 0.145, 0.225)
                val taus = doubleArrayOf(0.062, 0.068, 0.080, 0.165)
                val amps = doubleArrayOf(0.66, 0.70, 0.76, 0.86)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.12)
                    addPureGlide(buf, sr, s, rem, notes[i], notes[i], 0.004, taus[i], secondHarmonic = 0.05, amp = amps[i])
                }
                addPureGlide(buf, sr, 0.250, dur - 0.250, A5, A5, 0.006, 0.135, secondHarmonic = 0.03, amp = 0.38)
                cue(buf, 0.132, RoomStyle.WHISPER_DRY, 0.04, p, fadeOutMs = 6.0)
            }

            SoundCue.SETUP_COMPLETE -> {
                // Calm pure-sine resolution (F4 -> G4 -> C5 -> F5)
                val dur = 0.520
                val buf = alloc(dur, sr)
                val notes = doubleArrayOf(F4, G4, C5, F5)
                val starts = doubleArrayOf(0.0, 0.068, 0.140, 0.220)
                val amps = doubleArrayOf(0.64, 0.68, 0.74, 0.82)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.11)
                    addPureGlide(buf, sr, s, rem, notes[i], notes[i], 0.006, 0.110, secondHarmonic = 0.04, amp = amps[i])
                }
                cue(buf, 0.122, RoomStyle.WHISPER_DRY, 0.04, p, fadeOutMs = 6.0)
            }
        }
    }

    // =========================================================================
    // PACK 4: NEON — Retro 16-bit Arcade FM / Pulse (E Major, B5->E6 Coin, Laser Sweeps, Victory Fanfare)
    // =========================================================================

    private fun renderNeon(cue: SoundCue, noise: AudioDsp.SeededNoise, sr: Int): RenderedCue {
        val p = NEON_PROFILE
        return when (cue) {
            SoundCue.NAV_TICK -> {
                // Punchy band-limited 25% pulse + crisp 2-op FM blip (K-weighted calibrated for fatigue-free browsing)
                val buf = alloc(0.030, sr)
                addRetroPulse(buf, sr, 0.0, 0.026, 1175.0, 1340.0, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.0075, amp = 0.66)
                addRetroFm(buf, sr, 0.0, 0.024, 1318.5, modRatio = 3.0, startModIndex = 2.0, endModIndex = 0.35, attackSec = 0.0010, decayTauSec = 0.0065, amp = 0.60)
                cue(buf, 0.106, RoomStyle.NONE, 0.0, p, fadeOutMs = 4.2)
            }

            SoundCue.NAV_EDGE_BUMP -> {
                // Retro square-pulse + FM arcade wall bump
                val buf = alloc(0.078, sr)
                addRetroPulse(buf, sr, 0.0, 0.070, 270.0, 152.0, duty = 0.50, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.022, amp = 0.68)
                addRetroFm(buf, sr, 0.0, 0.062, 240.0, modRatio = 1.5, startModIndex = 1.8, endModIndex = 0.4, attackSec = 0.0015, decayTauSec = 0.018, amp = 0.54)
                cue(buf, 0.130, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.NAV_PAGE_TURN -> {
                // Arcade synth zip: rapid 3-step pulse micro-arp + bright white-noise laser air
                val buf = alloc(0.118, sr)
                addNoiseSweep(buf, sr, noise, 0.0, 0.105, 1300.0, 5200.0, q = 1.8, amp = 0.36, pink = false)
                addRetroPulse(buf, sr, 0.0, 0.044, E5, E5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.016, amp = 0.58)
                addRetroPulse(buf, sr, 0.026, 0.048, GS5, GS5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.018, amp = 0.62)
                addRetroFm(buf, sr, 0.052, 0.060, B5, modRatio = 2.0, startModIndex = 1.7, endModIndex = 0.3, attackSec = 0.0012, decayTauSec = 0.024, amp = 0.68)
                cue(buf, 0.120, RoomStyle.ARCADE_PLATE, 0.10, p)
            }

            SoundCue.DOCK_ENTER -> {
                // Snappy 16-bit descending two-blip (B5 -> E5)
                val buf = alloc(0.088, sr)
                addRetroPulse(buf, sr, 0.0, 0.042, B5, B5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.015, amp = 0.62)
                addRetroFm(buf, sr, 0.026, 0.058, E5, modRatio = 2.0, startModIndex = 1.8, endModIndex = 0.35, attackSec = 0.0012, decayTauSec = 0.020, amp = 0.78)
                cue(buf, 0.120, RoomStyle.ARCADE_PLATE, 0.08, p)
            }

            SoundCue.DOCK_EXIT -> {
                // Snappy 16-bit ascending two-blip (E5 -> B5)
                val buf = alloc(0.088, sr)
                addRetroPulse(buf, sr, 0.0, 0.042, E5, E5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.015, amp = 0.62)
                addRetroFm(buf, sr, 0.026, 0.058, B5, modRatio = 2.0, startModIndex = 1.8, endModIndex = 0.35, attackSec = 0.0012, decayTauSec = 0.020, amp = 0.78)
                cue(buf, 0.120, RoomStyle.ARCADE_PLATE, 0.08, p)
            }

            SoundCue.CONFIRM -> {
                // Snappy retro arpeggiated confirm (E5 -> G#5 -> B5 crisp 25% pulse + 2-op FM!)
                val buf = alloc(0.124, sr)
                addRetroPulse(buf, sr, 0.0, 0.054, E5, E5, duty = 0.25, maxHarmonics = 10, attackSec = 0.0012, decayTauSec = 0.020, amp = 0.68)
                addRetroPulse(buf, sr, 0.022, 0.058, GS5, GS5, duty = 0.25, maxHarmonics = 10, attackSec = 0.0012, decayTauSec = 0.022, amp = 0.70)
                addRetroPulse(buf, sr, 0.044, 0.076, B5, B5, duty = 0.125, maxHarmonics = 10, attackSec = 0.0012, decayTauSec = 0.030, amp = 0.62)
                addRetroFm(buf, sr, 0.044, 0.076, B5, modRatio = 2.0, startModIndex = 2.1, endModIndex = 0.4, attackSec = 0.0012, decayTauSec = 0.032, amp = 0.72)
                cue(buf, 0.150, RoomStyle.ARCADE_PLATE, 0.10, p)
            }

            SoundCue.LAUNCH_GAME -> {
                // Snappy retro 16-bit arpeggiated "PRESS START" (E5 -> G#5 -> B5 -> E6 -> G#6 + FM bass punch)
                val buf = alloc(0.435, sr)
                addRetroFm(buf, sr, 0.0, 0.340, E4, modRatio = 1.0, startModIndex = 2.2, endModIndex = 0.4, attackSec = 0.003, decayTauSec = 0.130, amp = 0.50)
                val arp = doubleArrayOf(E5, GS5, B5, E6, GS6)
                val starts = doubleArrayOf(0.0, 0.042, 0.084, 0.126, 0.172)
                val amps = doubleArrayOf(0.64, 0.68, 0.74, 0.84, 0.76)
                for (i in arp.indices) {
                    val s = starts[i]
                    val rem = (0.420 - s).coerceAtLeast(0.11)
                    addRetroPulse(buf, sr, s, rem * 0.75, arp[i], arp[i], duty = 0.25, maxHarmonics = 9, attackSec = 0.0014, decayTauSec = 0.068, amp = amps[i] * 0.56)
                    addRetroFm(buf, sr, s, rem, arp[i], modRatio = 2.0, startModIndex = 2.0, endModIndex = 0.35, attackSec = 0.0015, decayTauSec = 0.088, amp = amps[i] * 0.66)
                }
                addLaserSweep(buf, sr, 0.02, 0.18, 620.0, 2900.0, amp = 0.24)
                cue(buf, 0.182, RoomStyle.ARCADE_PLATE, 0.12, p)
            }

            SoundCue.LAUNCH_APP -> {
                // Arcade warp-in 4-note pulse + FM burst (B4 -> E5 -> G#5 -> B5)
                val buf = alloc(0.258, sr)
                val notes = doubleArrayOf(B4, E5, GS5, B5)
                val starts = doubleArrayOf(0.0, 0.036, 0.072, 0.110)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (0.248 - s).coerceAtLeast(0.09)
                    addRetroPulse(buf, sr, s, rem * 0.8, notes[i], notes[i], duty = 0.25, maxHarmonics = 9, attackSec = 0.0014, decayTauSec = 0.046, amp = 0.56)
                    addRetroFm(buf, sr, s, rem, notes[i], modRatio = 2.0, startModIndex = 1.8, endModIndex = 0.3, attackSec = 0.0015, decayTauSec = 0.056, amp = 0.62)
                }
                cue(buf, 0.168, RoomStyle.ARCADE_PLATE, 0.10, p)
            }

            SoundCue.BACK -> {
                // Crisp retro descending blip (G#5 -> E5)
                val buf = alloc(0.090, sr)
                addRetroPulse(buf, sr, 0.0, 0.042, GS5, GS5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.015, amp = 0.64)
                addRetroPulse(buf, sr, 0.024, 0.062, E5, E5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.022, amp = 0.72)
                addRetroFm(buf, sr, 0.024, 0.062, E5, modRatio = 2.0, startModIndex = 1.5, endModIndex = 0.3, attackSec = 0.0012, decayTauSec = 0.022, amp = 0.50)
                cue(buf, 0.136, RoomStyle.ARCADE_PLATE, 0.08, p)
            }

            SoundCue.SWAP_DISPLAY -> {
                // Clean synth laser-sweep: dual crossed saw+FM neon beams!
                val buf = alloc(0.220, sr)
                addLaserSweep(buf, sr, 0.0, 0.185, 1950.0, 480.0, amp = 0.62)
                addLaserSweep(buf, sr, 0.028, 0.182, 520.0, 2650.0, amp = 0.62)
                addNoiseSweep(buf, sr, noise, 0.0, 0.170, 1400.0, 4600.0, q = 2.0, amp = 0.20, pink = false)
                cue(buf, 0.156, RoomStyle.ARCADE_PLATE, 0.11, p)
            }

            SoundCue.MODE_TOGGLE -> {
                // Clean synth laser-sweep locking into punchy E5 -> B5 pulse power chord!
                val buf = alloc(0.198, sr)
                addLaserSweep(buf, sr, 0.0, 0.095, 680.0, 2750.0, amp = 0.56)
                addRetroPulse(buf, sr, 0.062, 0.128, E5, E5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0014, decayTauSec = 0.044, amp = 0.62)
                addRetroFm(buf, sr, 0.088, 0.105, B5, modRatio = 2.0, startModIndex = 2.0, endModIndex = 0.35, attackSec = 0.0014, decayTauSec = 0.042, amp = 0.72)
                cue(buf, 0.156, RoomStyle.ARCADE_PLATE, 0.10, p)
            }

            SoundCue.MODAL_OPEN -> {
                // Neon holo-panel power-up (E5 -> B5 -> E6 crisp 12.5% pulse + FM)
                val buf = alloc(0.134, sr)
                addRetroPulse(buf, sr, 0.0, 0.064, E5, E5, duty = 0.125, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.024, amp = 0.56)
                addRetroPulse(buf, sr, 0.026, 0.074, B5, B5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.028, amp = 0.62)
                addRetroFm(buf, sr, 0.052, 0.078, E6, modRatio = 2.0, startModIndex = 1.6, endModIndex = 0.3, attackSec = 0.0015, decayTauSec = 0.030, amp = 0.64)
                cue(buf, 0.140, RoomStyle.ARCADE_PLATE, 0.10, p)
            }

            SoundCue.MODAL_CLOSE -> {
                // Neon holo-panel power-down (E6 -> B5 -> E5)
                val buf = alloc(0.114, sr)
                addRetroPulse(buf, sr, 0.0, 0.048, E6, E6, duty = 0.125, maxHarmonics = 8, attackSec = 0.0015, decayTauSec = 0.018, amp = 0.52)
                addRetroPulse(buf, sr, 0.024, 0.054, B5, B5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.020, amp = 0.58)
                addRetroFm(buf, sr, 0.046, 0.064, E5, modRatio = 2.0, startModIndex = 1.4, endModIndex = 0.25, attackSec = 0.0015, decayTauSec = 0.024, amp = 0.64)
                cue(buf, 0.132, RoomStyle.ARCADE_PLATE, 0.09, p)
            }

            SoundCue.FAVORITE_ON -> {
                // Classic coin-sparkle: iconic B5 (987.77 Hz) -> E6 (1318.51 Hz) crisp pulse/FM sparkle!
                val buf = alloc(0.255, sr)
                // Crisp B5 strike (0..64ms)
                addRetroPulse(buf, sr, 0.0, 0.064, B5, B5, duty = 0.25, maxHarmonics = 10, attackSec = 0.0012, decayTauSec = 0.032, amp = 0.68)
                addRetroFm(buf, sr, 0.0, 0.064, B5, modRatio = 2.0, startModIndex = 2.0, endModIndex = 0.5, attackSec = 0.0012, decayTauSec = 0.030, amp = 0.62)
                // Sparkling E6 ring-out (62..250ms)
                addRetroPulse(buf, sr, 0.062, 0.188, E6, E6, duty = 0.25, maxHarmonics = 10, attackSec = 0.0012, decayTauSec = 0.072, amp = 0.64)
                addRetroFm(buf, sr, 0.062, 0.188, E6, modRatio = 2.0, startModIndex = 2.2, endModIndex = 0.35, attackSec = 0.0012, decayTauSec = 0.078, amp = 0.78)
                addRetroFm(buf, sr, 0.095, 0.130, B6, modRatio = 2.0, startModIndex = 1.2, endModIndex = 0.2, attackSec = 0.002, decayTauSec = 0.044, amp = 0.30)
                cue(buf, 0.160, RoomStyle.ARCADE_PLATE, 0.12, p)
            }

            SoundCue.FAVORITE_OFF -> {
                // Retro coin-drop / un-star (E6 -> B5 -> G#5)
                val buf = alloc(0.145, sr)
                addRetroPulse(buf, sr, 0.0, 0.054, E6, E6, duty = 0.25, maxHarmonics = 8, attackSec = 0.0015, decayTauSec = 0.020, amp = 0.58)
                addRetroPulse(buf, sr, 0.038, 0.056, B5, B5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.022, amp = 0.62)
                addRetroFm(buf, sr, 0.074, 0.068, GS5, modRatio = 2.0, startModIndex = 1.4, endModIndex = 0.25, attackSec = 0.0015, decayTauSec = 0.026, amp = 0.64)
                cue(buf, 0.134, RoomStyle.ARCADE_PLATE, 0.09, p)
            }

            SoundCue.MOVE_PICKUP -> {
                // Arcade grab blip: fast stepped pulse+FM rise (E5 -> G#5 -> B5)
                val buf = alloc(0.116, sr)
                addRetroPulse(buf, sr, 0.0, 0.042, E5, E5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.015, amp = 0.62)
                addRetroPulse(buf, sr, 0.024, 0.046, GS5, GS5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.018, amp = 0.64)
                addRetroFm(buf, sr, 0.048, 0.064, B5, modRatio = 2.0, startModIndex = 1.9, endModIndex = 0.35, attackSec = 0.0012, decayTauSec = 0.024, amp = 0.72)
                cue(buf, 0.144, RoomStyle.ARCADE_PLATE, 0.09, p)
            }

            SoundCue.MOVE_SWAP -> {
                // Rapid 16-bit cursor zip (B5 -> E6 chirp)
                val buf = alloc(0.052, sr)
                addRetroPulse(buf, sr, 0.0, 0.048, B5, E6, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.016, amp = 0.64)
                addRetroFm(buf, sr, 0.0, 0.044, B5, modRatio = 2.0, startModIndex = 1.7, endModIndex = 0.3, attackSec = 0.0012, decayTauSec = 0.014, amp = 0.56)
                cue(buf, 0.118, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.MOVE_DROP -> {
                // Arcade lock-in thud: downward FM lock (B5 -> E5) + punchy E4 FM bass impact
                val buf = alloc(0.128, sr)
                addRetroPulse(buf, sr, 0.0, 0.056, B5, E5, duty = 0.25, maxHarmonics = 9, attackSec = 0.0012, decayTauSec = 0.022, amp = 0.60)
                addRetroFm(buf, sr, 0.014, 0.108, E4, modRatio = 1.0, startModIndex = 2.4, endModIndex = 0.35, attackSec = 0.0015, decayTauSec = 0.036, amp = 0.76)
                cue(buf, 0.152, RoomStyle.ARCADE_PLATE, 0.09, p)
            }

            SoundCue.DESTRUCTIVE -> {
                // Retro hazard buzz-drop (G4 -> F4, then E4 -> C#4 saw+FM arcade warning)
                val buf = alloc(0.218, sr)
                addLaserSweep(buf, sr, 0.0, 0.094, G4, F4, amp = 0.62)
                addRetroPulse(buf, sr, 0.0, 0.090, G4, F4, duty = 0.50, maxHarmonics = 8, attackSec = 0.0015, decayTauSec = 0.034, amp = 0.48)
                addLaserSweep(buf, sr, 0.078, 0.132, E4, CS4, amp = 0.68)
                addRetroFm(buf, sr, 0.078, 0.132, CS4, modRatio = 1.5, startModIndex = 1.9, endModIndex = 0.4, attackSec = 0.002, decayTauSec = 0.048, amp = 0.56)
                cue(buf, 0.154, RoomStyle.ARCADE_PLATE, 0.08, p)
            }

            SoundCue.FILTER_SELECT -> {
                // Crisp high-tech 12.5% pulse pip (B5 -> E6)
                val buf = alloc(0.068, sr)
                addRetroPulse(buf, sr, 0.0, 0.034, B5, B5, duty = 0.125, maxHarmonics = 10, attackSec = 0.001, decayTauSec = 0.012, amp = 0.64)
                addRetroFm(buf, sr, 0.014, 0.050, E6, modRatio = 2.0, startModIndex = 1.8, endModIndex = 0.3, attackSec = 0.001, decayTauSec = 0.018, amp = 0.70)
                cue(buf, 0.132, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.ERROR_DENY -> {
                // Classic 16-bit "nope" double-buzz (D#4 -> C4 FM + square buzz)
                val buf = alloc(0.188, sr)
                addRetroPulse(buf, sr, 0.0, 0.070, DS4, DS4, duty = 0.50, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.028, amp = 0.56)
                addRetroFm(buf, sr, 0.0, 0.070, DS4, modRatio = 1.414, startModIndex = 2.0, endModIndex = 0.6, attackSec = 0.0015, decayTauSec = 0.026, amp = 0.56)
                addRetroPulse(buf, sr, 0.080, 0.102, C4, C4, duty = 0.50, maxHarmonics = 9, attackSec = 0.0015, decayTauSec = 0.038, amp = 0.60)
                addRetroFm(buf, sr, 0.080, 0.102, C4, modRatio = 1.414, startModIndex = 2.1, endModIndex = 0.65, attackSec = 0.0015, decayTauSec = 0.036, amp = 0.60)
                cue(buf, 0.150, RoomStyle.NONE, 0.0, p)
            }

            SoundCue.BOOT_CHIME -> {
                // Nostalgic 16-bit console boot jingle (E4+B4 FM bass + sparkling E5->G#5->B5->C#6->E6->G#6 arp!)
                val dur = 0.980
                val buf = alloc(dur, sr)
                addRetroFm(buf, sr, 0.0, dur * 0.88, E4, modRatio = 1.0, startModIndex = 1.8, endModIndex = 0.35, attackSec = 0.015, decayTauSec = 0.340, amp = 0.42)
                addRetroPulse(buf, sr, 0.03, dur * 0.82, B4, B4, duty = 0.50, maxHarmonics = 7, attackSec = 0.020, decayTauSec = 0.300, amp = 0.30)
                val arp = doubleArrayOf(E5, GS5, B5, CS6, E6, GS6)
                val starts = doubleArrayOf(0.04, 0.12, 0.20, 0.29, 0.39, 0.50)
                val amps = doubleArrayOf(0.58, 0.62, 0.68, 0.72, 0.84, 0.76)
                for (i in arp.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.16)
                    addRetroPulse(buf, sr, s, rem * 0.75, arp[i], arp[i], duty = 0.25, maxHarmonics = 9, attackSec = 0.002, decayTauSec = rem * 0.32, amp = amps[i] * 0.50)
                    addRetroFm(buf, sr, s, rem, arp[i], modRatio = 2.0, startModIndex = 2.1, endModIndex = 0.35, attackSec = 0.002, decayTauSec = rem * 0.36, amp = amps[i] * 0.66)
                }
                cue(buf, 0.175, RoomStyle.ARCADE_PLATE, 0.13, p, fadeOutMs = 8.0)
            }

            SoundCue.RA_ACHIEVEMENT -> {
                // Nostalgic 16-bit victory fanfare! (Triplet pickup E5-E5-E5 -> G#5 -> B5 -> triumphant E6+B5+G#6 chord!)
                val dur = 0.960
                val buf = alloc(dur, sr)
                // Iconic rhythmic triplet pickup
                val pickupStarts = doubleArrayOf(0.0, 0.070, 0.140)
                for (s in pickupStarts) {
                    addRetroPulse(buf, sr, s, 0.062, E5, E5, duty = 0.25, maxHarmonics = 10, attackSec = 0.0012, decayTauSec = 0.024, amp = 0.62)
                    addRetroFm(buf, sr, s, 0.062, E5, modRatio = 2.0, startModIndex = 1.9, endModIndex = 0.4, attackSec = 0.0012, decayTauSec = 0.024, amp = 0.62)
                }
                // Heroic leap: G#5 -> B5 -> soaring E6 chord
                addRetroPulse(buf, sr, 0.215, 0.108, GS5, GS5, duty = 0.25, maxHarmonics = 10, attackSec = 0.0015, decayTauSec = 0.048, amp = 0.66)
                addRetroFm(buf, sr, 0.215, 0.108, GS5, modRatio = 2.0, startModIndex = 2.0, endModIndex = 0.4, attackSec = 0.0015, decayTauSec = 0.052, amp = 0.68)

                addRetroPulse(buf, sr, 0.325, 0.120, B5, B5, duty = 0.25, maxHarmonics = 10, attackSec = 0.0015, decayTauSec = 0.056, amp = 0.72)
                addRetroFm(buf, sr, 0.325, 0.120, B5, modRatio = 2.0, startModIndex = 2.1, endModIndex = 0.4, attackSec = 0.0015, decayTauSec = 0.060, amp = 0.72)

                // Triumphant final chord (E4 bass + B5 + E6 + G#6 sparkle)
                val finalStart = 0.445
                val finalRem = dur - finalStart
                addRetroFm(buf, sr, finalStart, finalRem, E4, modRatio = 1.0, startModIndex = 1.9, endModIndex = 0.4, attackSec = 0.003, decayTauSec = 0.250, amp = 0.44)
                addRetroPulse(buf, sr, finalStart, finalRem, B5, B5, duty = 0.25, maxHarmonics = 9, attackSec = 0.002, decayTauSec = 0.220, amp = 0.48)
                addRetroPulse(buf, sr, finalStart, finalRem, E6, E6, duty = 0.25, maxHarmonics = 10, attackSec = 0.002, decayTauSec = 0.240, amp = 0.66)
                addRetroFm(buf, sr, finalStart, finalRem, E6, modRatio = 2.0, startModIndex = 2.2, endModIndex = 0.35, attackSec = 0.002, decayTauSec = 0.260, amp = 0.76)
                addRetroFm(buf, sr, finalStart + 0.030, finalRem - 0.030, GS6, modRatio = 2.0, startModIndex = 1.6, endModIndex = 0.25, attackSec = 0.003, decayTauSec = 0.190, amp = 0.46)
                cue(buf, 0.184, RoomStyle.ARCADE_PLATE, 0.14, p, fadeOutMs = 8.0)
            }

            SoundCue.SETUP_COMPLETE -> {
                // Nostalgic 16-bit stage-clear fanfare (E5 -> F#5 -> G#5 -> B5 -> E6 + pulse harmony)
                val dur = 0.820
                val buf = alloc(dur, sr)
                val notes = doubleArrayOf(E5, FS5, GS5, B5, E6)
                val starts = doubleArrayOf(0.0, 0.075, 0.150, 0.235, 0.330)
                val taus = doubleArrayOf(0.068, 0.072, 0.078, 0.095, 0.220)
                val amps = doubleArrayOf(0.64, 0.68, 0.72, 0.80, 0.90)
                for (i in notes.indices) {
                    val s = starts[i]
                    val rem = (dur - s).coerceAtLeast(0.14)
                    addRetroPulse(buf, sr, s, rem * 0.8, notes[i], notes[i], duty = 0.25, maxHarmonics = 10, attackSec = 0.0015, decayTauSec = taus[i], amp = amps[i] * 0.54)
                    addRetroFm(buf, sr, s, rem, notes[i], modRatio = 2.0, startModIndex = 2.0, endModIndex = 0.35, attackSec = 0.0015, decayTauSec = taus[i] * 1.08, amp = amps[i] * 0.66)
                }
                addRetroPulse(buf, sr, 0.330, dur - 0.330, B4, B4, duty = 0.50, maxHarmonics = 8, attackSec = 0.003, decayTauSec = 0.200, amp = 0.42)
                addRetroFm(buf, sr, 0.360, dur - 0.360, GS6, modRatio = 2.0, startModIndex = 1.5, endModIndex = 0.25, attackSec = 0.003, decayTauSec = 0.165, amp = 0.42)
                cue(buf, 0.176, RoomStyle.ARCADE_PLATE, 0.13, p, fadeOutMs = 8.0)
            }
        }
    }

    // =========================================================================
    // Shared Pure-Kotlin Multi-Harmonic Synthesis Voices
    // =========================================================================

    private fun cue(
        buf: DoubleArray,
        targetRms: Double,
        roomStyle: RoomStyle,
        roomWetMix: Double,
        profile: PackAcousticProfile,
        fadeInMs: Double = profile.fadeInMs,
        fadeOutMs: Double = profile.fadeOutMs,
    ): RenderedCue = RenderedCue(
        samples = buf,
        targetRms = targetRms,
        roomStyle = roomStyle,
        roomWetMix = roomWetMix,
        lowPassHz = profile.lowPassHz,
        highPassHz = profile.highPassHz,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs,
    )

    private fun alloc(durationSec: Double, sr: Int): DoubleArray {
        val clamped = durationSec.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
        return DoubleArray((clamped * sr).roundToInt().coerceAtLeast(16))
    }

    /**
     * Glassy maritime bell voice with fundamental (`1.0x`), warm octave (`2.0x`), and
     * rapidly damped inharmonic upper partial (`2.76x`).
     */
    private fun addMaritimeBell(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        attackSec: Double,
        decayTauSec: Double,
        partial2Mix: Double = 0.22,
        upperPartialMix: Double = 0.38,
        upperDecayTauSec: Double = 0.016,
        amp: Double,
    ) {
        addMaritimeBellGlide(
            buf = buf,
            sr = sr,
            startSec = startSec,
            durationSec = durationSec,
            startHz = freqHz,
            endHz = freqHz,
            attackSec = attackSec,
            decayTauSec = decayTauSec,
            partial2Mix = partial2Mix,
            upperPartialMix = upperPartialMix,
            upperDecayTauSec = upperDecayTauSec,
            amp = amp,
        )
    }

    private fun addMaritimeBellGlide(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        attackSec: Double,
        decayTauSec: Double,
        partial2Mix: Double = 0.22,
        upperPartialMix: Double = 0.38,
        upperDecayTauSec: Double = 0.016,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        var p1 = 0.0
        var p2 = 0.0
        var pUpper = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        val safeUpperTau = upperDecayTauSec.coerceAtLeast(0.0015)
        val p2Tau = (decayTauSec * 0.65).coerceAtLeast(0.004)
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(startHz, endHz, progress, exponential = true)
            p1 += hz * invSr
            p2 += (hz * 2.0) * invSr
            pUpper += (hz * MARITIME_BELL_PARTIAL_RATIO) * invSr

            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            val dynP2 = partial2Mix * exp(-localSec / p2Tau)
            // Inharmonic 2.76x partial shimmers briskly on attack and damps cleanly
            val dynUpper = upperPartialMix * exp(-localSec / safeUpperTau)
            val tailFade = if (progress > 0.86) (1.0 - progress) / 0.14 else 1.0
            val sample = AudioDsp.inharmonicBell(p1, p2, pUpper, dynP2, dynUpper)
            buf[i] += sample * env * tailFade * amp
        }
    }

    /**
     * Celebratory nautical brass-bell voice combining warm FM brass body with a
     * rapidly damped `2.76x` ship's-bell strike glint.
     */
    private fun addBrassBell(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        modIndex: Double,
        attackSec: Double,
        decayTauSec: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        var carrier = 0.0
        var mod = 0.0
        var upper = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            carrier += freqHz * invSr
            mod += (freqHz * 2.0) * invSr
            upper += (freqHz * MARITIME_BELL_PARTIAL_RATIO) * invSr

            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            val dynIndex = modIndex * (0.28 + 0.72 * env)
            val brass = AudioDsp.fmTone(carrier, mod, dynIndex)
            val glint = AudioDsp.sine(upper) * 0.24 * exp(-localSec / 0.018)
            val tailFade = if (progress > 0.88) (1.0 - progress) / 0.12 else 1.0
            buf[i] += (0.82 * brass + glint) * env * tailFade * amp
        }
    }

    /**
     * Warm resonant oak deck knock combining a pitched wooden fundamental body
     * and a low-mid bandpass pink-noise impact transient.
     */
    private fun addOakKnock(
        buf: DoubleArray,
        sr: Int,
        noise: AudioDsp.SeededNoise,
        startSec: Double,
        durationSec: Double,
        bodyStartHz: Double,
        bodyEndHz: Double,
        woodCenterHz: Double,
        bodyTauSec: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        val bp = AudioDsp.BiquadFilter.bandPass(woodCenterHz, q = 2.1, sampleRate = sr)
        var phase = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        val noiseTau = (bodyTauSec * 0.45).coerceAtLeast(0.0025)
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(bodyStartHz, bodyEndHz, progress, exponential = true)
            phase += hz * invSr
            val bodyEnv = AudioDsp.expDecay(localSec, 0.0014, bodyTauSec)
            val body = 0.80 * AudioDsp.sine(phase) + 0.20 * AudioDsp.softTriangle(phase * 2.0, 0.85)
            val knockNoise = bp.processSample(noise.nextPink()) * AudioDsp.expDecay(localSec, 0.0008, noiseTau)
            val tailFade = if (progress > 0.85) (1.0 - progress) / 0.15 else 1.0
            buf[i] += (0.76 * body * bodyEnv + 0.58 * knockNoise) * tailFade * amp
        }
    }

    /**
     * Warm woody marimba/kalimba bar voice (`softTriangle` + sine body + rapidly damped
     * `4.0x` tuned rosewood bar overtone + crisp bandpass mallet click).
     */
    private fun addMarimbaBar(
        buf: DoubleArray,
        sr: Int,
        noise: AudioDsp.SeededNoise,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        endHz: Double = freqHz,
        decayTauSec: Double,
        malletAmp: Double = 0.24,
        barOvertoneMix: Double = 0.24,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        val malletCenter = (freqHz * 2.6).coerceIn(1100.0, 3400.0)
        val bp = AudioDsp.BiquadFilter.bandPass(malletCenter, q = 2.4, sampleRate = sr)
        var p1 = 0.0
        var p4 = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        val overtoneTau = (decayTauSec * 0.22).coerceIn(0.0025, 0.011)
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(freqHz, endHz, progress, exponential = true)
            p1 += hz * invSr
            p4 += (hz * MARIMBA_BAR_OVERTONE_RATIO) * invSr

            val env = AudioDsp.expDecay(localSec, 0.0014, decayTauSec)
            val body = 0.66 * AudioDsp.softTriangle(p1, 0.92) + 0.34 * AudioDsp.sine(p1)
            val barKnock = AudioDsp.sine(p4) * barOvertoneMix * exp(-localSec / overtoneTau)
            val mallet = if (localSec < 0.012 && malletAmp > 0.0) {
                bp.processSample(noise.nextPink()) * AudioDsp.expDecay(localSec, 0.0006, 0.0024) * malletAmp
            } else {
                0.0
            }
            val tailFade = if (progress > 0.85) (1.0 - progress) / 0.15 else 1.0
            buf[i] += ((body + barKnock) * env + mallet) * tailFade * amp
        }
    }

    /** Playful pitch-bent marimba/kalimba glide voice (for pops, drops, and bounces). */
    private fun addMarimbaGlide(
        buf: DoubleArray,
        sr: Int,
        noise: AudioDsp.SeededNoise,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        decayTauSec: Double,
        malletAmp: Double = 0.22,
        amp: Double,
    ) {
        addMarimbaBar(
            buf = buf,
            sr = sr,
            noise = noise,
            startSec = startSec,
            durationSec = durationSec,
            freqHz = startHz,
            endHz = endHz,
            decayTauSec = decayTauSec,
            malletAmp = malletAmp,
            barOvertoneMix = 0.18,
            amp = amp,
        )
    }

    /**
     * Ultra-clean pure-sine glide voice with optional restrained 2nd harmonic warmth
     * (core oscillator for whisper-quiet `OLED` and warm bass anchors).
     */
    private fun addPureGlide(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        attackSec: Double,
        decayTauSec: Double,
        secondHarmonic: Double = 0.03,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        var phase = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        val h2 = secondHarmonic.coerceIn(0.0, 0.35)
        val h1 = 1.0 - h2
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(startHz, endHz, progress, exponential = true)
            phase += hz * invSr
            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            val tailFade = if (progress > 0.85) (1.0 - progress) / 0.15 else 1.0
            val sig = h1 * AudioDsp.sine(phase) + h2 * AudioDsp.sine(phase * 2.0)
            buf[i] += sig * env * tailFade * amp
        }
    }

    /** Punchy band-limited retro pulse voice (`12.5%` / `25%` / `50%` duty) for `NEON`. */
    private fun addRetroPulse(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        duty: Double = 0.25,
        maxHarmonics: Int = 9,
        attackSec: Double = 0.0014,
        decayTauSec: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        var phase = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(startHz, endHz, progress, exponential = true)
            phase += hz * invSr
            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            val tailFade = if (progress > 0.86) (1.0 - progress) / 0.14 else 1.0
            val sig = AudioDsp.warmPulse(phase, duty = duty, maxHarmonics = maxHarmonics, freqHz = hz, sampleRate = sr)
            buf[i] += sig * env * tailFade * amp
        }
    }

    /** Crisp 2-operator FM synth voice with dynamic attack-to-sustain modulation index. */
    private fun addRetroFm(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        carrierHz: Double,
        modRatio: Double,
        startModIndex: Double,
        endModIndex: Double,
        attackSec: Double,
        decayTauSec: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        var carrier = 0.0
        var mod = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            carrier += carrierHz * invSr
            mod += (carrierHz * modRatio) * invSr
            val env = AudioDsp.expDecay(localSec, attackSec, decayTauSec)
            val dynIndex = endModIndex + (startModIndex - endModIndex) * env
            val tailFade = if (progress > 0.87) (1.0 - progress) / 0.13 else 1.0
            buf[i] += AudioDsp.fmTone(carrier, mod, dynIndex) * env * tailFade * amp
        }
    }

    /** Clean retro synth laser-sweep blending band-limited sawtooth + FM harmonic shimmer. */
    private fun addLaserSweep(
        buf: DoubleArray,
        sr: Int,
        startSec: Double,
        durationSec: Double,
        startHz: Double,
        endHz: Double,
        amp: Double,
    ) {
        val startIdx = (startSec * sr).roundToInt().coerceAtLeast(0)
        val count = (durationSec * sr).roundToInt().coerceAtLeast(1)
        val endIdx = minOf(buf.size, startIdx + count)
        if (startIdx >= endIdx || amp <= 0.0) return

        var phase = 0.0
        var modPhase = 0.0
        val invSr = 1.0 / sr
        val totalDur = (endIdx - startIdx) * invSr
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val progress = if (totalDur > 0.0) localSec / totalDur else 1.0
            val hz = AudioDsp.freqGlide(startHz, endHz, progress, exponential = true)
            phase += hz * invSr
            modPhase += (hz * 2.0) * invSr
            val env = AudioDsp.adsr(
                timeSec = localSec,
                attackSec = durationSec * 0.08,
                decaySec = durationSec * 0.25,
                sustainLevel = 0.65,
                sustainEndSec = durationSec * 0.60,
                releaseSec = durationSec * 0.40,
            )
            val saw = AudioDsp.bandLimitedSaw(phase, maxHarmonics = 8, freqHz = hz, sampleRate = sr)
            val fm = AudioDsp.fmTone(phase, modPhase, 1.35 * (0.4 + 0.6 * env))
            buf[i] += (0.58 * saw + 0.42 * fm) * env * amp
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
        val tau = (durationSec * 0.35).coerceAtLeast(0.0018)
        for (i in startIdx until endIdx) {
            val localSec = (i - startIdx) * invSr
            val raw = if (pink) noise.nextPink() else noise.nextWhite()
            val env = AudioDsp.expDecay(localSec, 0.0008, tau)
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
        pink: Boolean = true,
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
                attackSec = durationSec * 0.22,
                decaySec = durationSec * 0.22,
                sustainLevel = 0.65,
                sustainEndSec = durationSec * 0.56,
                releaseSec = durationSec * 0.44,
            )
            val raw = if (pink) noise.nextPink() else noise.nextWhite()
            buf[i] += bp.processSample(raw) * env * amp
        }
    }
}
