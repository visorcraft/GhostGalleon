package com.visorcraft.ghostgalleon.rom

data class SessionSurface(
    val key: String,
    val playerId: String?,
    val packageName: String,
    val policy: SessionPolicy,
    val launchDisplayId: Int?,
    val greedy: Boolean = false,
) {
    companion object {
        fun forLaunch(
            key: String,
            playerId: String?,
            packageName: String,
            launchDisplayId: Int?,
            packageYield: Boolean = false,
            romOverride: SessionPolicy? = null,
        ): SessionSurface = SessionSurface(
            key = key,
            playerId = playerId,
            packageName = packageName,
            policy = SessionPolicy.resolve(
                playerId = playerId,
                romOverride = romOverride,
                packageYield = packageYield,
            ),
            launchDisplayId = launchDisplayId,
        )
    }
}
