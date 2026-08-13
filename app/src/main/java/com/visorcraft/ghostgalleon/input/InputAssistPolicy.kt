package com.visorcraft.ghostgalleon.input

object InputAssistPolicy {
    fun mayFilterKeys(
        assistConnected: Boolean,
        owner: InputOwner,
        sessionOwnsCompanion: Boolean,
    ): Boolean = assistConnected && owner == InputOwner.GAME && !sessionOwnsCompanion

    fun mayInjectPointer(
        assistConnected: Boolean,
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        playerId: String?,
    ): Boolean {
        if (!assistConnected || !playHostAllowed || sessionOwnsCompanion) return false
        val id = playerId?.trim().orEmpty()
        return id == "winlator" || id == "winlator-main"
    }

    fun mayInjectSeat(
        assistConnected: Boolean,
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        playerIsRa: Boolean,
        seatEnabled: Boolean,
    ): Boolean =
        assistConnected &&
            playHostAllowed &&
            !sessionOwnsCompanion &&
            playerIsRa &&
            seatEnabled
}
