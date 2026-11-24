package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Bounded LRU loader for Android app icons.
 *
 * Decodes density-matched drawables via [IconDensityMetrics] so large handheld
 * tiles stay sharp, and returns isolated mutable copies (`constantState`) so
 * simultaneous views (grid + dock + folder mosaic) never share mutable alpha
 * or bounds state.
 */
class AppIconLoader(private val pm: PackageManager) {

    private val cache = LruCache<String, Drawable>(IconDensityMetrics.DEFAULT_CACHE_CAPACITY)

    fun load(packageName: String): Drawable = load(packageName, targetPx = 0)

    fun load(packageName: String, targetPx: Int): Drawable {
        val key = IconDensityMetrics.cacheKey(packageName, targetPx)
        val template = cache.get(key) ?: loadUncached(packageName, targetPx).also {
            cache.put(key, it)
        }
        return isolateDrawable(template)
    }

    /** Drop all cached density buckets for [packageName]. */
    fun evict(packageName: String) {
        val keys = cache.snapshot().keys
        for (key in keys) {
            if (IconDensityMetrics.matchesPackage(key, packageName)) {
                cache.remove(key)
            }
        }
    }

    /** Clear all cached drawable templates. */
    fun clear() {
        cache.evictAll()
    }

    private fun loadUncached(packageName: String, targetPx: Int): Drawable {
        val bucketDpi = IconDensityMetrics.targetDensityDpi(targetPx)
        if (bucketDpi > 0) {
            val densityIcon = loadForDensity(packageName, bucketDpi)
            if (densityIcon != null) return densityIcon
        }
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            pm.defaultActivityIcon
        } catch (_: RuntimeException) {
            pm.defaultActivityIcon
        }
    }

    private fun loadForDensity(packageName: String, bucketDpi: Int): Drawable? = try {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        if (appInfo.icon == 0) {
            null
        } else {
            pm.getResourcesForApplication(appInfo)
                .getDrawableForDensity(appInfo.icon, bucketDpi, null)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Resources.NotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun isolateDrawable(template: Drawable): Drawable =
        template.constantState?.newDrawable()?.mutate() ?: template

    companion object {
        @Volatile
        private var sharedInstance: AppIconLoader? = null

        /**
         * Process-shared icon loader backed by [Context.getApplicationContext]'s
         * [PackageManager] so no [android.app.Activity] is ever retained across
         * activities, panels, or modals.
         */
        fun shared(context: Context): AppIconLoader {
            sharedInstance?.let { return it }
            synchronized(this) {
                return sharedInstance ?: AppIconLoader(
                    (context.applicationContext ?: context).packageManager,
                ).also { sharedInstance = it }
            }
        }

        /**
         * Evict one package (or clear the entire shared icon cache when
         * [packageName] is null/blank) after package install/remove/change events.
         */
        fun invalidateShared(packageName: String? = null) {
            val instance = sharedInstance ?: return
            if (packageName.isNullOrBlank()) {
                instance.clear()
            } else {
                instance.evict(packageName)
            }
        }
    }
}
