package com.visorcraft.ghostgalleon.input

import com.visorcraft.ghostgalleon.rom.SessionPolicy

enum class InputOwner { GAME, HOST, NONE }

object InputOwnerPolicy {
    fun inputOwner(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        playHostAllowed: Boolean,
    ): InputOwner {
        if (!dualMode) return InputOwner.NONE
        if (policy == SessionPolicy.YIELD_BOTH || greedy) return InputOwner.NONE
        if (policy == SessionPolicy.KEEP_COMPANION && playHostAllowed) return InputOwner.GAME
        return InputOwner.NONE
    }

    fun effectiveOwner(base: InputOwner, hostClaimed: Boolean): InputOwner {
        if (base == InputOwner.NONE) return InputOwner.NONE
        if (base == InputOwner.HOST) return InputOwner.HOST
        return if (hostClaimed) InputOwner.HOST else InputOwner.GAME
    }

    fun focusLockAllowed(owner: InputOwner, playHostAllowed: Boolean): Boolean =
        owner == InputOwner.GAME && playHostAllowed
}
