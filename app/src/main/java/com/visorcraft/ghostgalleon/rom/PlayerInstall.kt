package com.visorcraft.ghostgalleon.rom

/**
 * First-run “get an emulator” offers: missing primary players and Play Store
 * (or web) URIs. Pure; host-tested.
 */
object PlayerInstall {

    data class Offer(
        val displayName: String,
        val packageName: String,
    )

    /**
     * Distinct default players from [platforms] whose package is not
     * installed, registry order (RetroArch appears once).
     */
    fun missingPrimaries(
        platforms: List<Platform> = Platforms.BUILTIN,
        installed: (String) -> Boolean,
    ): List<Offer> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<Offer>()
        for (platform in platforms) {
            val player = platform.player
            val pkg = PlayerResolver.packageName(player)
            if (pkg in seen) continue
            seen += pkg
            if (!installed(pkg)) {
                out += Offer(displayName = player.displayName, packageName = pkg)
            }
        }
        return out
    }

    /** Play Store deep link. */
    fun marketUri(packageName: String): String =
        "market://details?id=$packageName"

    /** Browser fallback when the Play Store app is missing. */
    fun webStoreUri(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"
}
