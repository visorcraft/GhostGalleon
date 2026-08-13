package com.visorcraft.ghostgalleon.rom

enum class SessionPolicy {
    KEEP_COMPANION,
    YIELD_BOTH,
    ;

    companion object {
        private val YIELD_PLAYER_IDS = setOf("melondualds", "azahar")

        fun parse(raw: String?): SessionPolicy {
            val key = raw?.trim()?.uppercase().orEmpty()
            return if (key == YIELD_BOTH.name) YIELD_BOTH else KEEP_COMPANION
        }

        fun forPlayerId(playerId: String?): SessionPolicy =
            if (playerId?.trim() in YIELD_PLAYER_IDS) YIELD_BOTH else KEEP_COMPANION

        fun resolve(
            playerId: String?,
            romOverride: SessionPolicy? = null,
            packageYield: Boolean = false,
        ): SessionPolicy {
            if (romOverride != null) return romOverride
            if (packageYield) return YIELD_BOTH
            return forPlayerId(playerId)
        }
    }
}

/** Settings player-row label: append [yieldHint] only for [SessionPolicy.YIELD_BOTH]. */
fun playerSettingsLabel(displayName: String, policy: SessionPolicy, yieldHint: String): String {
    if (policy != SessionPolicy.YIELD_BOTH) return displayName
    return "$displayName · $yieldHint"
}
