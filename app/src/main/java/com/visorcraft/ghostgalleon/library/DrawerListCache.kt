package com.visorcraft.ghostgalleon.library

/**
 * Identity key for the all-apps drawer list. When the key matches a cached
 * build, the host may reuse the previous [PickerItems] list instead of
 * rebuilding from thousands of ROM entries. Pure; host-tested.
 */
data class DrawerListKey(
    val contentEpoch: Int,
    val romCount: Int,
    val hiddenFingerprint: Int,
    val appsFingerprint: Int,
    val hiddenRomsFingerprint: Int = 0,
)

object DrawerListCache {

    /**
     * Build a cache key from the inputs that affect the drawer list.
     * [hiddenPackages], [hiddenRomIds], and [appPackageNames] are hashed
     * order-independently.
     */
    fun key(
        contentEpoch: Int,
        romCount: Int,
        hiddenPackages: Collection<String>,
        appPackageNames: Collection<String>,
        hiddenRomIds: Collection<String> = emptyList(),
    ): DrawerListKey = DrawerListKey(
        contentEpoch = contentEpoch,
        romCount = romCount,
        hiddenFingerprint = stableHash(hiddenPackages),
        appsFingerprint = stableHash(appPackageNames),
        hiddenRomsFingerprint = stableHash(hiddenRomIds),
    )

    fun matches(cached: DrawerListKey?, current: DrawerListKey): Boolean =
        cached != null && cached == current

    /** Order-independent fingerprint — XOR, no sort or extra lists. */
    internal fun stableHash(items: Collection<String>): Int {
        if (items.isEmpty()) return 0
        var hash = 0
        for (item in items) hash = hash xor item.hashCode()
        return hash
    }

    /** Same fingerprint as [stableHash] of each [AppEntry.packageName]. */
    fun appsFingerprint(apps: List<AppEntry>): Int {
        if (apps.isEmpty()) return 0
        var hash = 0
        for (app in apps) hash = hash xor app.packageName.hashCode()
        return hash
    }
}
