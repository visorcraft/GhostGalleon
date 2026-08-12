package com.visorcraft.ghostgalleon.rom

import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * True when [packageName] is installed for the current user.
 * Process-wide cache — PackageManager queries are expensive and launchable-
 * only browse rebuilds hit many packages per chip tap.
 */
private val installedCache = ConcurrentHashMap<String, Boolean>(64)

fun PackageManager.isInstalled(packageName: String): Boolean {
    installedCache[packageName]?.let { return it }
    val ok = try {
        getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }
    installedCache[packageName] = ok
    return ok
}

/** Drop cache after install/uninstall (or when the app catalog is invalidated). */
fun clearInstalledPackageCache() {
    installedCache.clear()
}
