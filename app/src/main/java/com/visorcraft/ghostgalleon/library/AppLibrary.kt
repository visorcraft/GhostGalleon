package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.displayName

class AppLibrary(private val source: InstalledAppsSource) {

    private val cache: List<AppEntry> by lazy {
        source.query().sortedBy { it.label.lowercase() }
    }

    // Memoize filtered lists — selection/hero/browse hit visible() often
    // with the same Settings snapshot (or equal hidden/names maps).
    private var visibleKeyHidden: Set<String>? = null
    private var visibleKeyNames: Map<String, String>? = null
    private var visibleCached: List<AppEntry>? = null
    private var allKeyNames: Map<String, String>? = null
    private var allCached: List<AppEntry>? = null
    private var byPackageCached: Map<String, AppEntry>? = null
    private var byPackageNames: Map<String, String>? = null

    /** Force PackageManager query off the first UI paint when pre-warmed. */
    fun warm() {
        cache.size
    }

    fun visible(settings: Settings): List<AppEntry> {
        val hidden = settings.hiddenPackages
        val names = settings.customNames
        visibleCached?.let { hit ->
            if (visibleKeyHidden == hidden && visibleKeyNames == names) return hit
        }
        val next = cache.filter { it.packageName !in hidden }
            .map { it.displayName(settings) }
        visibleKeyHidden = hidden
        visibleKeyNames = names
        visibleCached = next
        return next
    }

    // Everything installed, including hidden apps: grid slots stay
    // launchable (and rendered) even when their app is hidden from the
    // picker. Custom names apply here too, so slots/hero show them.
    fun all(settings: Settings): List<AppEntry> {
        val names = settings.customNames
        allCached?.let { hit ->
            if (allKeyNames == names) return hit
        }
        val next = cache.map { it.displayName(settings) }
        allKeyNames = names
        allCached = next
        byPackageCached = null
        byPackageNames = null
        return next
    }

    /** O(1) package lookup over [all] (names-aware). */
    fun byPackage(settings: Settings): Map<String, AppEntry> {
        val names = settings.customNames
        byPackageCached?.let { hit ->
            if (byPackageNames == names) return hit
        }
        val map = all(settings).associateBy { it.packageName }
        byPackageCached = map
        byPackageNames = names
        return map
    }

    // Game mode carousel: the curated grid content — non-null slots in
    // slot order, resolved through the full cache (hidden apps stay;
    // uninstalled ones drop out). Blank slots never appear.
    fun curated(settings: Settings): List<AppEntry> {
        val byPkg = byPackage(settings)
        return settings.gridSlots.mapNotNull { it?.let(byPkg::get) }
    }

    private var dockVisibleRef: List<AppEntry>? = null
    private var dockSlotsRef: List<String?>? = null
    private var dockCached: List<AppEntry>? = null

    fun dock(settings: Settings): List<AppEntry> {
        val vis = visible(settings)
        val slots = settings.dockSlots
        dockCached?.let { hit ->
            if (vis === dockVisibleRef && slots === dockSlotsRef) return hit
        }
        val visibleByPkg = vis.associateBy { it.packageName }
        val next = slots.filterNotNull().mapNotNull { visibleByPkg[it] }
        dockVisibleRef = vis
        dockSlotsRef = slots
        dockCached = next
        return next
    }
}
