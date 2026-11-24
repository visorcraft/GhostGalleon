package com.visorcraft.ghostgalleon.audio

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure, host-testable audio policy engine for Ghost Galleon.
 *
 * Centralizes perceptual loudness curves, anti-spam cooldown arbitration,
 * hold-repeat musical expression, handheld stereo panning, and audio etiquette
 * (external music, ringer mode, KEEP play-host sessions, screen power, video preview).
 */
object AudioPolicy {

    /** Perceptual taper span in dB (~38 dB makes 35% sound naturally gentle). */
    const val VOLUME_TAPER_DB: Double = 38.0

    /** Gentle stereo pan limit tailored for compact handheld dual speakers. */
    const val MAX_HANDHELD_PAN: Float = 0.28f

    /** Minimum spacing between low-priority navigation cues across rapid inputs. */
    const val DEFAULT_NAV_GLOBAL_FLOOR_MS: Long = 18L

    /** Repeat-streak expression bounds. */
    const val REPEAT_MIN_GAIN: Float = 0.72f
    const val REPEAT_MIN_PITCH: Float = 0.97f
    const val REPEAT_MAX_PITCH: Float = 1.05f

    /** Ducking gain factors for polite coexistence with other audio sources. */
    const val DUCK_EXTERNAL_MUSIC_UI: Float = 0.45f
    const val DUCK_EXTERNAL_MUSIC_ACHIEVEMENT: Float = 0.65f
    const val DUCK_VIDEO_PREVIEW_UI: Float = 0.45f
    const val DUCK_PLAY_HUD_QUIET_UI: Float = 0.35f
    const val DUCK_PLAY_HUD_ACHIEVEMENT: Float = 0.85f
    const val DUCK_AMBIENT_TRANSIENT: Float = 0.25f

    /** Platform-independent abstraction over Android ringer modes. */
    enum class RingerState {
        NORMAL,
        VIBRATE,
        SILENT,
    }

    /** Velocity and pitch modulation for sustained directional hold repeats. */
    data class RepeatExpression(
        val gainMultiplier: Float,
        val pitchRate: Float,
    )

    /** Left/right linear gains produced by constant-power stereo panning. */
    data class StereoGains(
        val left: Float,
        val right: Float,
    )

    /**
     * Converts a UI volume percentage (`0..100`) into linear amplitude (`0f..1f`)
     * along a smooth 38 dB perceptual logarithmic taper with exact endpoints (`0 -> 0f`, `100 -> 1f`).
     */
    fun volumePercentToGain(percent: Int): Float {
        val clamped = percent.coerceIn(0, 100)
        if (clamped == 0) return 0f
        if (clamped == 100) return 1f
        val p = clamped / 100.0
        val minLinear = 10.0.pow(-VOLUME_TAPER_DB / 20.0)
        val raw = 10.0.pow((p - 1.0) * VOLUME_TAPER_DB / 20.0)
        return ((raw - minLinear) / (1.0 - minLinear)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Musical velocity attenuation (`1.0f -> 0.72f`) and subtle pitch variation
     * (`0.97f..1.05f`) during sustained `NavRepeater` hold streaks.
     *
     * Fresh single presses (`streak <= 0`) play at exact unity (`1.0f, 1.0f`).
     */
    fun repeatExpression(streak: Int): RepeatExpression {
        if (streak <= 0) return RepeatExpression(gainMultiplier = 1.0f, pitchRate = 1.0f)
        val s = streak.toDouble()
        val gain = (REPEAT_MIN_GAIN + (1.0 - REPEAT_MIN_GAIN) * exp(-s / 4.5))
            .toFloat()
            .coerceIn(REPEAT_MIN_GAIN, 1.0f)
        // Gentle upward lift as the user glides + tiny organic micro-undulation
        val lift = 0.032 * (1.0 - exp(-s / 5.0))
        val microWave = 0.012 * sin(s * 1.35)
        val pitch = (1.0 + lift + microWave)
            .toFloat()
            .coerceIn(REPEAT_MIN_PITCH, REPEAT_MAX_PITCH)
        return RepeatExpression(gainMultiplier = gain, pitchRate = pitch)
    }

    /**
     * Maps a zero-based column index [col] within [cols] to a gentle handheld stereo pan
     * in `[-MAX_HANDHELD_PAN, +MAX_HANDHELD_PAN]` (`-0.28f..+0.28f`).
     */
    fun panForColumn(col: Int, cols: Int): Float {
        if (cols <= 1) return 0f
        val clampedCol = col.coerceIn(0, cols - 1)
        val normalized = (clampedCol.toFloat() / (cols - 1).toFloat()) * 2f - 1f
        return (normalized * MAX_HANDHELD_PAN).coerceIn(-MAX_HANDHELD_PAN, MAX_HANDHELD_PAN)
    }

    /**
     * Maps a horizontal navigation step [dx] (`< 0` left, `0` center, `> 0` right)
     * to a gentle handheld stereo pan in `[-MAX_HANDHELD_PAN, +MAX_HANDHELD_PAN]`.
     */
    fun panForDirection(dx: Int): Float = when {
        dx < 0 -> -MAX_HANDHELD_PAN
        dx > 0 -> MAX_HANDHELD_PAN
        else -> 0f
    }

    /**
     * Computes left/right linear gains for [masterGain] (`0f..1f`) and [pan] (`-1f..+1f`)
     * using exact constant-power stereo panning (`left^2 + right^2 == masterGain^2`).
     */
    fun stereoGains(masterGain: Float, pan: Float): StereoGains {
        if (!masterGain.isFinite()) return StereoGains(0f, 0f)
        val g = masterGain.coerceIn(0f, 1f)
        if (g <= 0f) return StereoGains(0f, 0f)
        val safePan = if (pan.isFinite()) pan else 0f
        val (l, r) = AudioDsp.constantPowerPan(safePan)
        return StereoGains(left = g * l, right = g * r)
    }

    /**
     * Determines whether a one-shot UI/system [cue] is allowed to play under current
     * user settings, lifecycle, and system audio etiquette.
     */
    fun shouldPlaySfx(
        soundEnabled: Boolean,
        pack: SoundPackId,
        sfxVolumePercent: Int,
        cue: SoundCue,
        respectRinger: Boolean = true,
        ringerState: RingerState = RingerState.NORMAL,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
        bootChimeEnabled: Boolean = true,
        achievementChimeEnabled: Boolean = true,
        activeAppActivityCount: Int = 1,
        hasKeepSession: Boolean = false,
        screenOff: Boolean = false,
    ): Boolean {
        if (!soundEnabled || !pack.isPlayable || sfxVolumePercent <= 0) return false
        if (screenOff || activeAppActivityCount <= 0) return false
        if (respectRinger && ringerState != RingerState.NORMAL) return false

        if (cue == SoundCue.BOOT_CHIME) {
            if (!bootChimeEnabled) return false
            if (hasKeepSession) return false
            if (respectMusic && externalMusicActive) return false
        }
        if (cue == SoundCue.RA_ACHIEVEMENT && !achievementChimeEnabled) {
            return false
        }
        return true
    }

    /**
     * Computes an extra politeness ducking multiplier (`0f..1f`) for one-shot SFX when
     * external music, unmuted video previews, or a KEEP play-host emulator are active.
     */
    fun sfxDuckFactor(
        cue: SoundCue,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
        videoPreviewUnmuted: Boolean = false,
        hasKeepSession: Boolean = false,
        playHudQuiet: Boolean = true,
    ): Float {
        var factor = 1.0f
        if (respectMusic && externalMusicActive) {
            val musicDuck = if (cue == SoundCue.RA_ACHIEVEMENT) {
                DUCK_EXTERNAL_MUSIC_ACHIEVEMENT
            } else {
                DUCK_EXTERNAL_MUSIC_UI
            }
            factor = minOf(factor, musicDuck)
        }
        if (videoPreviewUnmuted && cue != SoundCue.RA_ACHIEVEMENT) {
            factor = minOf(factor, DUCK_VIDEO_PREVIEW_UI)
        }
        if (hasKeepSession && playHudQuiet) {
            val hudDuck = if (cue == SoundCue.RA_ACHIEVEMENT) {
                DUCK_PLAY_HUD_ACHIEVEMENT
            } else {
                DUCK_PLAY_HUD_QUIET_UI
            }
            factor = minOf(factor, hudDuck)
        }
        return factor.coerceIn(0f, 1f)
    }

    /**
     * Determines whether the continuous ambient soundscape is eligible to run.
     *
     * Strictly yields whenever the app is backgrounded, the screen is off, a KEEP game
     * session is active on the launch panel, external music is playing (when respected),
     * an unmuted hero video preview is active, or audio focus is not held.
     */
    fun shouldPlayAmbient(
        soundEnabled: Boolean,
        ambientEnabled: Boolean,
        pack: SoundPackId,
        ambientVolumePercent: Int,
        activeAppActivityCount: Int,
        hasKeepSession: Boolean = false,
        screenOff: Boolean = false,
        respectRinger: Boolean = true,
        ringerState: RingerState = RingerState.NORMAL,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
        videoPreviewUnmuted: Boolean = false,
        audioFocusGranted: Boolean = true,
    ): Boolean {
        if (!soundEnabled || !ambientEnabled || !pack.isPlayable) return false
        if (ambientVolumePercent <= 0) return false
        if (activeAppActivityCount <= 0) return false
        if (hasKeepSession || screenOff) return false
        if (respectRinger && ringerState != RingerState.NORMAL) return false
        if (respectMusic && externalMusicActive) return false
        if (videoPreviewUnmuted) return false
        if (!audioFocusGranted) return false
        return true
    }

    /** Gain multiplier for transient audio-focus ducking on the ambient stream. */
    fun ambientDuckFactor(focusDucked: Boolean): Float =
        if (focusDucked) DUCK_AMBIENT_TRANSIENT else 1.0f

    /**
     * Thread-safe per-cue cooldown & navigation anti-spam gate.
     *
     * High-priority cues ([SoundCue.bypassesNavThrottle], e.g. `CONFIRM`, `LAUNCH_*`,
     * `RA_ACHIEVEMENT`) are never blocked by rapid navigation ticks, while still
     * respecting their own per-cue double-trigger spacing.
     */
    class CooldownTracker(
        private val navGlobalFloorMs: Long = DEFAULT_NAV_GLOBAL_FLOOR_MS,
    ) {
        private val lock = Any()
        private val lastPlayedByCue = LongArray(SoundCue.entries.size) { Long.MIN_VALUE }
        private var lastNavPlayedMs: Long = Long.MIN_VALUE
        private var lastNavPriority: Int = 0

        /**
         * Attempts to reserve a playback slot for [cue] at [nowMs].
         * Returns `true` and records the timestamp if permitted, or `false` if throttled.
         */
        fun tryAcquire(
            cue: SoundCue,
            nowMs: Long,
            cooldownOverrideMs: Long? = null,
        ): Boolean = synchronized(lock) {
            val minSpacing = (cooldownOverrideMs ?: cue.defaultCooldownMs).coerceAtLeast(0L)
            val prevCueMs = lastPlayedByCue[cue.ordinal]
            if (prevCueMs != Long.MIN_VALUE && nowMs >= prevCueMs) {
                if (nowMs - prevCueMs < minSpacing) {
                    return false
                }
            }

            if (!cue.bypassesNavThrottle && cue.category == SoundCueCategory.NAVIGATION) {
                if (lastNavPlayedMs != Long.MIN_VALUE && nowMs >= lastNavPlayedMs) {
                    val sinceNav = nowMs - lastNavPlayedMs
                    // Lower or equal priority nav cues observe the global floor;
                    // higher-priority cues (e.g. EDGE_BUMP / PAGE_TURN over NAV_TICK) preempt cleanly.
                    if (sinceNav < navGlobalFloorMs && cue.priority <= lastNavPriority) {
                        return false
                    }
                }
                lastNavPlayedMs = nowMs
                lastNavPriority = cue.priority
            }

            lastPlayedByCue[cue.ordinal] = nowMs
            true
        }

        /** Clears all recorded timestamps. */
        fun reset() = synchronized(lock) {
            lastPlayedByCue.fill(Long.MIN_VALUE)
            lastNavPlayedMs = Long.MIN_VALUE
            lastNavPriority = 0
        }
    }
}
