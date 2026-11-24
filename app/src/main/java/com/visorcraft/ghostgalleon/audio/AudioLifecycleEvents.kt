package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.library.RaTheater
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-once cold-start boot chime gate.
 *
 * Ensures the boot chime triggers at most once per process, strictly from `MainActivity`'s
 * first real painted frame (never from `CompanionActivity` absorb/redirect/restart churn and
 * never belatedly on a later activity recreation).
 */
class BootChimeGate {
    private val consumed = AtomicBoolean(false)

    /** True once `MainActivity` has reached its first real paint and consumed the cold-start slot. */
    val hasConsumed: Boolean get() = consumed.get()

    /**
     * Evaluates whether the cold-start boot chime should trigger.
     *
     * Non-`MainActivity` callers return `false` WITHOUT consuming the gate so a companion panel
     * painting first can never steal or suppress `MainActivity`'s boot chime.
     */
    fun tryClaim(
        isMainActivity: Boolean,
        soundEnabled: Boolean,
        bootChimeEnabled: Boolean,
        hasKeepSession: Boolean = false,
        screenOff: Boolean = false,
        activeAppActivityCount: Int = 1,
    ): Boolean {
        if (!isMainActivity) return false
        if (!consumed.compareAndSet(false, true)) return false
        if (!soundEnabled || !bootChimeEnabled) return false
        if (hasKeepSession || screenOff || activeAppActivityCount <= 0) return false
        return true
    }
}

/**
 * Thread-safe reference counter tracking active unmuted hero video previews (`0 -> 1 -> 0`).
 *
 * Allows multiple video hosts or rapid rebinds to overlap cleanly without prematurely restoring
 * [AmbientEngine] or dropping the count below zero on redundant detach/release callbacks.
 */
class VideoPreviewAudioTracker {
    private val activeCount = AtomicInteger(0)

    /** Current number of active unmuted video previews. */
    val count: Int get() = activeCount.get()

    /** True while at least one unmuted hero video preview is actively playing. */
    val isActive: Boolean get() = activeCount.get() > 0

    /**
     * Increments the active count.
     * @return `true` when transitioning `0 -> 1` (caller should pause/fade ambient).
     */
    fun acquire(): Boolean = activeCount.incrementAndGet() == 1

    /**
     * Decrements the active count (clamped safely at `0`).
     * @return `true` when transitioning `1 -> 0` (caller should restore ambient).
     */
    fun release(): Boolean {
        while (true) {
            val cur = activeCount.get()
            if (cur <= 0) return false
            val next = cur - 1
            if (activeCount.compareAndSet(cur, next)) {
                return next == 0
            }
        }
    }

    /**
     * Clears the counter back to `0` (e.g. full launcher exit).
     * @return `true` if the tracker was previously active (`> 0`).
     */
    fun reset(): Boolean = activeCount.getAndSet(0) > 0
}

/**
 * Pure, host-testable lifecycle event helpers for RetroAchievements live unlock fanfare
 * and opt-in unmuted hero video preview audio gain/fade-in math. Zero Android imports.
 */
object AudioLifecycleEvents {

    /** Smooth opt-in video preview fade-in duration (ms) avoiding sudden blasts. */
    const val VIDEO_PREVIEW_FADE_IN_MS: Long = 250L

    /** Comfortable ceiling gain for opt-in hero video previews (~-3.7 dB headroom). */
    const val VIDEO_PREVIEW_MAX_GAIN: Float = 0.65f

    /**
     * Determines whether a RetroAchievements theater snapshot update should trigger a single
     * [SoundCue.RA_ACHIEVEMENT] unlock fanfare.
     *
     * Strictly silent on the initial snapshot load (`previousUnlockedIds == null`), silent when
     * no new achievement IDs appeared, and gated on both master sound & achievement chime toggles.
     */
    fun shouldPlayRaUnlockFanfare(
        previousUnlockedIds: Set<Int>?,
        currentUnlockedIds: Set<Int>,
        soundEnabled: Boolean,
        raAchievementChimeEnabled: Boolean,
    ): Boolean {
        if (previousUnlockedIds == null) return false
        if (!soundEnabled || !raAchievementChimeEnabled) return false
        return RaTheater.newlyUnlocked(previousUnlockedIds, currentUnlockedIds).isNotEmpty()
    }

    /**
     * Masks [android.media.AudioManager.isMusicActive] when the active music stream belongs to
     * Ghost Galleon's own [AmbientEngine] or an already-unmuted hero video preview.
     */
    fun isExternalMusicActiveForVideoPreview(
        systemMusicActive: Boolean,
        ownAmbientPlaying: Boolean,
        ownVideoPreviewUnmuted: Boolean,
    ): Boolean = systemMusicActive && !(ownAmbientPlaying || ownVideoPreviewUnmuted)

    /**
     * Computes the calibrated target stereo volume (`0f..[VIDEO_PREVIEW_MAX_GAIN]`) for an
     * opt-in unmuted hero video preview, enforcing full audio etiquette (foreground, screen-on,
     * no active KEEP session, ringer mode, external music) and scaling by
     * [AudioPolicy.volumePercentToGain].
     */
    fun videoPreviewTargetGain(
        soundEnabled: Boolean,
        videoPreviewAudio: Boolean,
        sfxVolumePercent: Int,
        activeAppActivityCount: Int = 1,
        screenOff: Boolean = false,
        hasKeepSession: Boolean = false,
        respectRinger: Boolean = true,
        ringerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
    ): Float {
        if (!soundEnabled || !videoPreviewAudio) return 0f
        if (activeAppActivityCount <= 0 || screenOff || hasKeepSession) return 0f
        if (respectRinger && ringerState != AudioPolicy.RingerState.NORMAL) return 0f
        if (respectMusic && externalMusicActive) return 0f
        val base = AudioPolicy.volumePercentToGain(sfxVolumePercent)
        return (base * VIDEO_PREVIEW_MAX_GAIN).coerceIn(0f, VIDEO_PREVIEW_MAX_GAIN)
    }

    /** True when opt-in hero video preview audio should unmute with an audible target gain. */
    fun shouldEnableVideoPreviewAudio(
        soundEnabled: Boolean,
        videoPreviewAudio: Boolean,
        sfxVolumePercent: Int,
        activeAppActivityCount: Int = 1,
        screenOff: Boolean = false,
        hasKeepSession: Boolean = false,
        respectRinger: Boolean = true,
        ringerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
    ): Boolean = videoPreviewTargetGain(
        soundEnabled = soundEnabled,
        videoPreviewAudio = videoPreviewAudio,
        sfxVolumePercent = sfxVolumePercent,
        activeAppActivityCount = activeAppActivityCount,
        screenOff = screenOff,
        hasKeepSession = hasKeepSession,
        respectRinger = respectRinger,
        ringerState = ringerState,
        respectMusic = respectMusic,
        externalMusicActive = externalMusicActive,
    ) > SoundEngineLogic.MIN_AUDIBLE_GAIN

    /**
     * Computes instantaneous fade-in gain for [targetGain] at normalized progress [fraction]
     * (`0f..1f`) using a gentle quadratic perceptual ease-in curve.
     */
    fun videoPreviewFadeGain(targetGain: Float, fraction: Float): Float {
        val safeTarget = if (targetGain.isFinite()) targetGain.coerceIn(0f, 1f) else 0f
        val t = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return (safeTarget * t * t).coerceIn(0f, 1f)
    }
}
