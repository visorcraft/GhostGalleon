package com.visorcraft.ghostgalleon.rom

enum class HandoffPrep { NONE, RA_PAUSE_SAVE }

data class HandoffPlan(val result: SwitchToResult, val prep: HandoffPrep)

object SessionHandoff {
    const val RA_PACKAGE = "com.retroarch.aarch64"
    const val PREP_BUDGET_MS = 400L

    fun isRaPlayer(playerId: String?, packageName: String?): Boolean {
        if (playerId?.startsWith("ra-") == true) return true
        return packageName == RA_PACKAGE
    }

    fun plan(
        current: SessionSurface?,
        target: SessionRingEntry,
        raNetworkCommands: Boolean,
        raHandoffSave: Boolean,
    ): HandoffPlan {
        val result = SessionSwitch.decide(
            current?.key,
            current?.playerId,
            current?.policy,
            current?.greedy == true,
            target,
        )
        if (result != SwitchToResult.LAUNCH) return HandoffPlan(result, HandoffPrep.NONE)
        if (current == null || current.policy != SessionPolicy.KEEP_COMPANION || current.greedy) {
            return HandoffPlan(result, HandoffPrep.NONE)
        }
        val prep =
            if (raNetworkCommands && raHandoffSave && isRaPlayer(current.playerId, current.packageName)) {
                HandoffPrep.RA_PAUSE_SAVE
            } else {
                HandoffPrep.NONE
            }
        return HandoffPlan(result, prep)
    }
}
