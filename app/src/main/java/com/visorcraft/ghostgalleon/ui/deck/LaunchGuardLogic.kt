package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.SlotKey

/**
 * Pure, host-tested slot-key classification and dual-display launch routing
 * guards for [Deck.kt].
 */
object LaunchGuardLogic {

    enum class LaunchTargetKind {
        FOLDER_NOOP,
        ROM,
        APP,
    }

    /** Classify a deck slot key into folder no-op, ROM, or Android app package. */
    fun classifySlotKey(key: String): LaunchTargetKind = when {
        SlotKey.isFolder(key) -> LaunchTargetKind.FOLDER_NOOP
        SlotKey.isRom(key) -> LaunchTargetKind.ROM
        else -> LaunchTargetKind.APP
    }

    /**
     * Resolve the explicit target `displayId` for `ActivityOptions.setLaunchDisplayId`,
     * or null when the activity should launch on [currentDisplayId].
     *
     * Priority: [overrideDisplayId] (stage-plot face) → [topologyLaunchDisplayId]
     * → first available display differing from [primaryDisplayId]. Returns null
     * when the chosen display is detached or already equals [currentDisplayId].
     */
    fun resolveLaunchDisplayId(
        overrideDisplayId: Int?,
        topologyLaunchDisplayId: Int?,
        primaryDisplayId: Int,
        currentDisplayId: Int?,
        availableDisplayIds: List<Int>,
    ): Int? {
        val candidate = overrideDisplayId
            ?: topologyLaunchDisplayId
            ?: availableDisplayIds.firstOrNull { it != primaryDisplayId }
        if (candidate == null || (currentDisplayId != null && candidate == currentDisplayId)) {
            return null
        }
        return if (candidate in availableDisplayIds) candidate else null
    }
}
