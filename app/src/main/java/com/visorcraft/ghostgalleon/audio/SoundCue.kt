package com.visorcraft.ghostgalleon.audio

/**
 * High-level semantic category for UI and system audio cues.
 * Used by [AudioPolicy] for cooldown arbitration, ducking, and future
 * category-level user toggles.
 */
enum class SoundCueCategory {
    NAVIGATION,
    ACTION,
    LAUNCH,
    MODAL,
    SYSTEM,
    CELEBRATION,
}

/**
 * Complete semantic sound cue catalog for Ghost Galleon.
 *
 * Pure Kotlin metadata only — synthesis recipes live in [PcmSynth] and
 * playback arbitration lives in [AudioPolicy].
 */
enum class SoundCue(
    val category: SoundCueCategory,
    val priority: Int,
    val defaultCooldownMs: Long,
    val bypassesNavThrottle: Boolean = priority >= 5,
) {
    NAV_TICK(
        category = SoundCueCategory.NAVIGATION,
        priority = 1,
        defaultCooldownMs = 22L,
        bypassesNavThrottle = false,
    ),
    NAV_EDGE_BUMP(
        category = SoundCueCategory.NAVIGATION,
        priority = 2,
        defaultCooldownMs = 80L,
        bypassesNavThrottle = false,
    ),
    NAV_PAGE_TURN(
        category = SoundCueCategory.NAVIGATION,
        priority = 3,
        defaultCooldownMs = 70L,
        bypassesNavThrottle = false,
    ),
    DOCK_ENTER(
        category = SoundCueCategory.NAVIGATION,
        priority = 3,
        defaultCooldownMs = 60L,
        bypassesNavThrottle = false,
    ),
    DOCK_EXIT(
        category = SoundCueCategory.NAVIGATION,
        priority = 3,
        defaultCooldownMs = 60L,
        bypassesNavThrottle = false,
    ),
    CONFIRM(
        category = SoundCueCategory.ACTION,
        priority = 5,
        defaultCooldownMs = 35L,
        bypassesNavThrottle = true,
    ),
    LAUNCH_GAME(
        category = SoundCueCategory.LAUNCH,
        priority = 9,
        defaultCooldownMs = 150L,
        bypassesNavThrottle = true,
    ),
    LAUNCH_APP(
        category = SoundCueCategory.LAUNCH,
        priority = 9,
        defaultCooldownMs = 150L,
        bypassesNavThrottle = true,
    ),
    BACK(
        category = SoundCueCategory.ACTION,
        priority = 4,
        defaultCooldownMs = 40L,
        bypassesNavThrottle = true,
    ),
    SWAP_DISPLAY(
        category = SoundCueCategory.SYSTEM,
        priority = 7,
        defaultCooldownMs = 120L,
        bypassesNavThrottle = true,
    ),
    MODE_TOGGLE(
        category = SoundCueCategory.SYSTEM,
        priority = 7,
        defaultCooldownMs = 120L,
        bypassesNavThrottle = true,
    ),
    MODAL_OPEN(
        category = SoundCueCategory.MODAL,
        priority = 5,
        defaultCooldownMs = 75L,
        bypassesNavThrottle = true,
    ),
    MODAL_CLOSE(
        category = SoundCueCategory.MODAL,
        priority = 5,
        defaultCooldownMs = 75L,
        bypassesNavThrottle = true,
    ),
    FAVORITE_ON(
        category = SoundCueCategory.ACTION,
        priority = 6,
        defaultCooldownMs = 60L,
        bypassesNavThrottle = true,
    ),
    FAVORITE_OFF(
        category = SoundCueCategory.ACTION,
        priority = 5,
        defaultCooldownMs = 60L,
        bypassesNavThrottle = true,
    ),
    MOVE_PICKUP(
        category = SoundCueCategory.ACTION,
        priority = 6,
        defaultCooldownMs = 60L,
        bypassesNavThrottle = true,
    ),
    MOVE_SWAP(
        category = SoundCueCategory.NAVIGATION,
        priority = 3,
        defaultCooldownMs = 35L,
        bypassesNavThrottle = false,
    ),
    MOVE_DROP(
        category = SoundCueCategory.ACTION,
        priority = 6,
        defaultCooldownMs = 60L,
        bypassesNavThrottle = true,
    ),
    DESTRUCTIVE(
        category = SoundCueCategory.ACTION,
        priority = 7,
        defaultCooldownMs = 100L,
        bypassesNavThrottle = true,
    ),
    FILTER_SELECT(
        category = SoundCueCategory.ACTION,
        priority = 4,
        defaultCooldownMs = 40L,
        bypassesNavThrottle = true,
    ),
    ERROR_DENY(
        category = SoundCueCategory.SYSTEM,
        priority = 8,
        defaultCooldownMs = 120L,
        bypassesNavThrottle = true,
    ),
    BOOT_CHIME(
        category = SoundCueCategory.SYSTEM,
        priority = 10,
        defaultCooldownMs = 1000L,
        bypassesNavThrottle = true,
    ),
    RA_ACHIEVEMENT(
        category = SoundCueCategory.CELEBRATION,
        priority = 10,
        defaultCooldownMs = 500L,
        bypassesNavThrottle = true,
    ),
    SETUP_COMPLETE(
        category = SoundCueCategory.CELEBRATION,
        priority = 9,
        defaultCooldownMs = 500L,
        bypassesNavThrottle = true,
    ),
}
