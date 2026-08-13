package com.visorcraft.ghostgalleon.rom

object CockpitPolicy {
    fun cockpitAllowed(
        playHostAllowed: Boolean,
        playerId: String?,
        cockpitEnabled: Boolean,
    ): Boolean {
        if (!playHostAllowed || !cockpitEnabled) return false
        val id = playerId?.trim().orEmpty()
        return id == "winlator" || id == "winlator-main"
    }
}
