package com.visorcraft.ghostgalleon.rom

enum class SwitchToResult { NO_OP, REFUSE_YIELD, LAUNCH }

object SessionSwitch {
    fun decide(
        currentKey: String?,
        currentPlayerId: String?,
        currentPolicy: SessionPolicy?,
        currentGreedy: Boolean,
        target: SessionRingEntry,
    ): SwitchToResult {
        if (currentPolicy == SessionPolicy.YIELD_BOTH || currentGreedy) {
            return SwitchToResult.REFUSE_YIELD
        }
        if (target.key == currentKey && target.playerId == currentPlayerId) {
            return SwitchToResult.NO_OP
        }
        return SwitchToResult.LAUNCH
    }
}
