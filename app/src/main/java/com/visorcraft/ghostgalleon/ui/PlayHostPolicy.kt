package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy

object PlayHostPolicy {
    fun playHostAllowed(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        hostDisplayId: Int?,
        launchDisplayId: Int?,
    ): Boolean {
        if (!dualMode) return false
        if (policy != SessionPolicy.KEEP_COMPANION) return false
        if (greedy) return false
        if (hostDisplayId == null || launchDisplayId == null) return false
        return hostDisplayId != launchDisplayId
    }
}
