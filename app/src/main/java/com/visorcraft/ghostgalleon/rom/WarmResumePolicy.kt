package com.visorcraft.ghostgalleon.rom

enum class LaunchReason { CONTINUE, SLOT, SWITCHER, OTHER }

object WarmResumePolicy {
    const val PROBE_GAP_MS = 60_000L
    const val LOAD_BUDGET_MS = 400L

    fun mayProbe(
        warmEnabled: Boolean,
        sessionOpen: Boolean,
        continueKey: String?,
        playerIsRa: Boolean,
        raNetworkCommands: Boolean,
        lastProbeMs: Long,
        nowMs: Long,
    ): Boolean {
        if (!warmEnabled || sessionOpen || continueKey.isNullOrBlank()) return false
        if (!playerIsRa || !raNetworkCommands) return false
        return nowMs - lastProbeMs >= PROBE_GAP_MS
    }

    fun mayAutoload(
        warmLoadEnabled: Boolean,
        reason: LaunchReason,
        playerIsRa: Boolean,
        slot: Int?,
        sessionOwnsCompanion: Boolean,
    ): Boolean {
        if (!warmLoadEnabled || !playerIsRa || sessionOwnsCompanion) return false
        return reason == LaunchReason.CONTINUE && slot != null
    }

    fun loadSlot(pinned: Int?, lastCinema: Int?, lastUser: Int?): Int? {
        if (pinned != null) return pinned
        if (lastCinema != null) return lastCinema
        return lastUser?.takeIf { it in 1..8 }
    }
}
