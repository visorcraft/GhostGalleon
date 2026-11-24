package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, host-tested Android DPI bucket selection and cache-key normalization
 * for [AppIconLoader].
 *
 * Maps a requested icon pixel size against Android's canonical 48dp launcher
 * icon baseline so large Odin 2 Portal grid tiles decode razor-sharp high-DPI
 * assets while tiny folder-mosaic quadrants avoid oversampling overhead.
 */
object IconDensityMetrics {

    const val DENSITY_DEFAULT: Int = 0
    const val DENSITY_MEDIUM: Int = 160
    const val DENSITY_HIGH: Int = 240
    const val DENSITY_XHIGH: Int = 320
    const val DENSITY_XXHIGH: Int = 480
    const val DENSITY_XXXHIGH: Int = 640

    const val DEFAULT_CACHE_CAPACITY: Int = 160

    /**
     * Optimal Android density bucket (dpi) for an icon rendered at [targetPx].
     * Returns [DENSITY_DEFAULT] (`0`) when [targetPx] <= 0.
     */
    fun targetDensityDpi(targetPx: Int): Int = when {
        targetPx <= 0 -> DENSITY_DEFAULT
        targetPx <= 48 -> DENSITY_MEDIUM
        targetPx <= 72 -> DENSITY_HIGH
        targetPx <= 96 -> DENSITY_XHIGH
        targetPx <= 144 -> DENSITY_XXHIGH
        else -> DENSITY_XXXHIGH
    }

    /** Normalized LRU cache key pairing [packageName] with its resolved DPI bucket. */
    fun cacheKey(packageName: String, targetPx: Int): String {
        val dpi = targetDensityDpi(targetPx)
        return if (dpi == DENSITY_DEFAULT) packageName else "$packageName@$dpi"
    }

    /** True when [cacheKey] belongs to [packageName] (default or density-qualified). */
    fun matchesPackage(cacheKey: String, packageName: String): Boolean =
        cacheKey == packageName || cacheKey.startsWith("$packageName@")
}
