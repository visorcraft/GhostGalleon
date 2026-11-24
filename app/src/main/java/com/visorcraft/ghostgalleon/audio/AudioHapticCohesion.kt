package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.settings.Action

/**
 * Unified tactile + acoustic feedback plan for a single UI or gamepad interaction.
 *
 * Coordinates sound cue selection, handheld stereo panning, hold-repeat expression,
 * politeness volume scaling, and haptic emission cleanly without double-firing.
 */
data class CueFeedbackPlan(
    val cue: SoundCue?,
    val pan: Float = 0f,
    val repeatStreak: Int = 0,
    val volumeMul: Float = 1.0f,
    val playHaptic: Boolean = false,
) {
    /** True when this plan emits an audible cue. */
    val playAudio: Boolean
        get() = cue != null && volumeMul > 0f
}

/**
 * Pure JVM coordination rules for tactile + acoustic synergy across Ghost Galleon's
 * decks, Play-HUD Companion panel, touch/gamepad input channels, and Controller Lab.
 *
 * Zero Android dependencies so every etiquette, deduplication, and key-test rule is
 * exhaustively host-testable.
 */
object AudioHapticCohesion {

    /**
     * Applies Companion Play-HUD quiet-mode etiquette while an emulator KEEP session is active.
     *
     * When [keepSessionActive] and [playHudQuiet] are both `true`:
     * - Suppresses low-priority `NAVIGATION` chatter (`NAV_TICK`, `NAV_EDGE_BUMP`,
     *   `NAV_PAGE_TURN`, `DOCK_ENTER`, `DOCK_EXIT`, `MOVE_SWAP`) and `BOOT_CHIME` so
     *   browsing the Companion Play-HUD never chatters over active game audio.
     * - Preserves intentional user actions (`CONFIRM`, `BACK`, `SWAP_DISPLAY`, `MODE_TOGGLE`,
     *   `MODAL_*`, `FAVORITE_*`, `LAUNCH_*`, `DESTRUCTIVE`, `FILTER_SELECT`, `ERROR_DENY`,
     *   `MOVE_PICKUP`, `MOVE_DROP`) and celebrations (`RA_ACHIEVEMENT`, `SETUP_COMPLETE`).
     */
    fun resolveForPlayHud(
        cue: SoundCue?,
        keepSessionActive: Boolean,
        playHudQuiet: Boolean = true,
    ): SoundCue? {
        if (cue == null) return null
        if (!keepSessionActive || !playHudQuiet) return cue
        return when {
            cue.category == SoundCueCategory.NAVIGATION -> null
            cue == SoundCue.BOOT_CHIME -> null
            else -> cue
        }
    }

    /**
     * Produces a coordinated [CueFeedbackPlan] applying Play-HUD quiet etiquette, safe
     * finite stereo pan & volume bounds, and repeat-streak haptic throttling (keeping
     * sustained hold-scrolls silky via audio expression without buzzing haptic motors).
     */
    fun planFeedback(
        cue: SoundCue?,
        pan: Float = 0f,
        repeatStreak: Int = 0,
        volumeMul: Float = 1.0f,
        hapticsEnabled: Boolean = true,
        keepSessionActive: Boolean = false,
        playHudQuiet: Boolean = true,
        allowHapticOnRepeat: Boolean = false,
    ): CueFeedbackPlan {
        val effectiveCue = resolveForPlayHud(cue, keepSessionActive, playHudQuiet)
        val safePan = if (pan.isFinite()) {
            pan.coerceIn(-AudioPolicy.MAX_HANDHELD_PAN, AudioPolicy.MAX_HANDHELD_PAN)
        } else {
            0f
        }
        val safeStreak = repeatStreak.coerceAtLeast(0)
        val safeVol = if (volumeMul.isFinite()) volumeMul.coerceIn(0f, 1f) else 0f
        val playHaptic = hapticsEnabled &&
            cue != null &&
            (allowHapticOnRepeat || safeStreak == 0) &&
            cue.category != SoundCueCategory.CELEBRATION &&
            cue != SoundCue.BOOT_CHIME

        return CueFeedbackPlan(
            cue = effectiveCue,
            pan = safePan,
            repeatStreak = safeStreak,
            volumeMul = if (effectiveCue != null) safeVol else 0f,
            playHaptic = playHaptic,
        )
    }

    /**
     * Acoustic-haptic deduplication guard for touch/click listeners.
     *
     * Prevents double sound emission when a click listener is triggered synthetically by a
     * gamepad press that already played audio, or during non-user programmatic UI binds.
     */
    fun shouldEmitTouchAudio(
        soundEnabled: Boolean,
        isUserInitiated: Boolean = true,
        alreadyEmittedByGamepad: Boolean = false,
    ): Boolean = soundEnabled && isUserInitiated && !alreadyEmittedByGamepad

    /**
     * Acoustic-haptic deduplication guard for gamepad/key handlers.
     *
     * Ensures audio fires only on handled key-down events and never double-fires alongside
     * a view click handler.
     */
    fun shouldEmitGamepadAudio(
        soundEnabled: Boolean,
        actionHandled: Boolean = true,
        isKeyDown: Boolean = true,
        alreadyEmittedByViewClick: Boolean = false,
    ): Boolean = soundEnabled && actionHandled && isKeyDown && !alreadyEmittedByViewClick

    /**
     * Maps a Controller Lab probed [action] (or `null` for an unbound hardware button)
     * to a distinct, crisp test tone so every gamepad button gives lively acoustic feedback.
     */
    fun cueForKeyTest(action: Action?): SoundCue = when (action) {
        Action.NAV_UP,
        Action.NAV_DOWN,
        Action.NAV_LEFT,
        Action.NAV_RIGHT,
        -> SoundCue.NAV_TICK

        Action.PAGE_PREV,
        Action.PAGE_NEXT,
        -> SoundCue.NAV_PAGE_TURN

        Action.CONFIRM -> SoundCue.CONFIRM
        Action.BACK -> SoundCue.BACK
        Action.SWAP_SCREENS -> SoundCue.SWAP_DISPLAY
        Action.TOGGLE_MODE -> SoundCue.MODE_TOGGLE
        Action.TOGGLE_FAVORITE -> SoundCue.FAVORITE_ON

        Action.OPEN_SETTINGS,
        Action.OPEN_QUICK_PANEL,
        Action.SHOW_DETAILS,
        Action.OPEN_SESSION_SWITCHER,
        Action.TOGGLE_PLAY_HUD,
        -> SoundCue.MODAL_OPEN

        Action.SEARCH_LIBRARY -> SoundCue.FILTER_SELECT

        Action.CLAIM_HOST,
        Action.TOGGLE_SEAT,
        -> SoundCue.DOCK_EXIT

        Action.RELEASE_HOST -> SoundCue.DOCK_ENTER

        Action.NONE,
        null,
        -> SoundCue.MOVE_SWAP
    }

    /**
     * Returns directional stereo pan for Controller Lab key tests (`NAV_LEFT`/`PAGE_PREV`
     * pan left, `NAV_RIGHT`/`PAGE_NEXT` pan right, all others center).
     */
    fun panForKeyTest(action: Action?): Float = when (action) {
        Action.NAV_LEFT, Action.PAGE_PREV -> AudioPolicy.panForDirection(-1)
        Action.NAV_RIGHT, Action.PAGE_NEXT -> AudioPolicy.panForDirection(1)
        else -> 0f
    }
}
