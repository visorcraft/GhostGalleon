package com.visorcraft.ghostgalleon.rom

/**
 * Package-installed is not the same as playable: RetroArch templates point
 * at a hard-coded LIBRETRO `.so`. Pure; host-tested.
 */
object PlayerReadiness {

    /** Absolute core path from a RetroArch-style template, or null. */
    fun libretroCorePath(template: PlayerTemplate): String? {
        val path = template.extras["LIBRETRO"]?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (!path.contains("_libretro_", ignoreCase = true)) return null
        return path
    }

    /**
     * True when [template] can be offered as ready: non-RA templates are
     * ready when the package is installed; RA templates also need the core
     * file to exist.
     */
    fun isReady(
        template: PlayerTemplate,
        installed: (String) -> Boolean,
        fileExists: (String) -> Boolean,
    ): Boolean {
        if (!installed(PlayerResolver.packageName(template))) return false
        val core = libretroCorePath(template) ?: return true
        return fileExists(core)
    }

    /**
     * Prefer [preferredPlayerId] when that player is ready; otherwise the
     * first ready player in registry order.
     */
    fun resolveReady(
        platform: Platform,
        preferredPlayerId: String?,
        installed: (String) -> Boolean,
        fileExists: (String) -> Boolean,
    ): PlayerTemplate? {
        if (preferredPlayerId != null) {
            PlayerResolver.byId(platform, preferredPlayerId)?.let { pref ->
                if (isReady(pref, installed, fileExists)) return pref
            }
        }
        return platform.players.firstOrNull { isReady(it, installed, fileExists) }
    }
}
