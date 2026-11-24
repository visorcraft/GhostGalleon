package com.visorcraft.ghostgalleon.library

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class PackageManagerAppsSource(
    private val pm: PackageManager,
    private val selfPackage: String,
) : InstalledAppsSource {

    @Suppress("DEPRECATION") // FLAG_IS_GAME keeps pre-category game apps classified.
    override fun query(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != selfPackage }
        if (apps.isEmpty()) return emptyList()

        val installTimes = resolveInstallTimes(
            packageNames = apps.map { it.packageName },
            fetchBatch = {
                val installed = pm.getInstalledPackages(0)
                val map = HashMap<String, Long>(installed.size)
                for (pkgInfo in installed) {
                    val pkgName = pkgInfo.packageName ?: continue
                    map[pkgName] = pkgInfo.firstInstallTime
                }
                map
            },
            fetchSingle = { pkg ->
                pm.getPackageInfo(pkg, 0).firstInstallTime
            },
        )
        return apps.map { info ->
            AppEntry(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                    (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0,
                firstInstallMs = installTimes[info.packageName] ?: 0L,
            )
        }
    }

    companion object {
        /**
         * Resolve per-package `firstInstallTime` with a single batch
         * `getInstalledPackages(0)` IPC, falling back to per-package
         * `getPackageInfo` lookups only when the batch call throws (e.g. OEM
         * `TransactionTooLargeException`) or returns empty for non-empty apps.
         * Pure over injected seams so host tests verify both paths.
         */
        internal fun resolveInstallTimes(
            packageNames: Collection<String>,
            fetchBatch: () -> Map<String, Long>,
            fetchSingle: (String) -> Long,
        ): Map<String, Long> {
            if (packageNames.isEmpty()) return emptyMap()
            val batch = try {
                fetchBatch()
            } catch (_: Exception) {
                null
            }
            if (batch != null && batch.isNotEmpty()) return batch
            val fallback = HashMap<String, Long>(packageNames.size)
            for (pkg in packageNames) {
                fallback[pkg] = try {
                    fetchSingle(pkg)
                } catch (_: Exception) {
                    0L
                }
            }
            return fallback
        }
    }
}
