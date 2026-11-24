package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import java.util.concurrent.Executors

/**
 * Shared SAF wallpaper load for Grid and Game Mode. Decode off the UI thread
 * with OOM-safe subsampling; apply only when still attached and [wallpaperUri]
 * unchanged. Cache hits snap immediately (zero deck-switch flicker) while async
 * decodes fade in smoothly over a vertical vignette scrim.
 */
object DeckWallpaper {

    /** 4-stop chrome-safe handheld vignette stops (standard height). */
    internal const val SCRIM_TOP: Int = 0xBF000000.toInt()
    internal const val SCRIM_UPPER_CENTER: Int = 0x22000000
    internal const val SCRIM_LOWER_CENTER: Int = 0x40000000
    internal const val SCRIM_BOTTOM: Int = 0xE0000000.toInt()

    /** Slightly denser 4-stop vignette for compact-height panels (e.g. Sugar ~540dp). */
    internal const val SCRIM_COMPACT_TOP: Int = 0xCC000000.toInt()
    internal const val SCRIM_COMPACT_UPPER_CENTER: Int = 0x2E000000
    internal const val SCRIM_COMPACT_LOWER_CENTER: Int = 0x52000000
    internal const val SCRIM_COMPACT_BOTTOM: Int = 0xE6000000.toInt()

    /** Viewport height cutoff (dp) below which compact chrome protection activates. */
    internal const val COMPACT_HEIGHT_THRESHOLD_DP: Float = 560f

    /** Maximum decoded edge (px) to guard GPU texture limits and prevent OOMs. */
    internal const val MAX_TEXTURE_DIM: Int = 2048

    /** Display-dimension quantization step (px) for dual-display cache isolation. */
    internal const val DIM_BUCKET_STEP_PX: Int = 256

    /** Strictly bounded process cache capacity (covers dual displays without thrash). */
    internal const val MAX_CACHE_ENTRIES: Int = 2

    /** Smooth crossfade duration (ms) when a wallpaper decodes asynchronously. */
    internal const val FADE_IN_MS: Long = 220L

    /** Pure dual-display cache key isolating top/bottom display resolutions. Host-tested. */
    internal data class CacheKey(
        val uri: String,
        val bucketedMaxDim: Int,
    )

    private val FADE_INTERPOLATOR = DecelerateInterpolator()
    private val DETACH_ANIM_CANCEL = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            v.animate().cancel()
        }
    }
    private val EXECUTOR = Executors.newSingleThreadExecutor()

    private val cacheLock = Any()
    private val bitmapCache = object : LinkedHashMap<CacheKey, Bitmap>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Bitmap>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /** Drop the process cache (wallpaper cleared or URI changed). */
    fun dropCache() {
        synchronized(cacheLock) {
            bitmapCache.clear()
        }
    }

    /** True when a cached decode may be applied without I/O. Pure. */
    internal fun cacheSatisfies(cachedUri: String?, wantUri: String): Boolean =
        !wantUri.isBlank() && cachedUri == wantUri

    /** True when [cachedKey] matches [wantKey] with a non-blank URI. Pure. */
    internal fun cacheKeySatisfies(cachedKey: CacheKey?, wantKey: CacheKey): Boolean =
        wantKey.uri.isNotBlank() && cachedKey == wantKey

    /** Bucket display max edge (px) into `256px` steps up to [MAX_TEXTURE_DIM]. */
    internal fun bucketMaxDim(widthPx: Int, heightPx: Int): Int {
        val longest = maxOf(widthPx, heightPx).coerceAtLeast(DIM_BUCKET_STEP_PX)
        val clamped = longest.coerceAtMost(MAX_TEXTURE_DIM)
        return ((clamped + DIM_BUCKET_STEP_PX - 1) / DIM_BUCKET_STEP_PX) * DIM_BUCKET_STEP_PX
    }

    /** Build a dual-display-isolated [CacheKey] for [uri] and display bounds. */
    internal fun cacheKeyFor(uri: String, widthPx: Int, heightPx: Int): CacheKey =
        CacheKey(uri = uri, bucketedMaxDim = bucketMaxDim(widthPx, heightPx))

    /** True when viewport height (dp) needs stronger compact chrome vignette protection. */
    internal fun isCompactHeightDp(heightDp: Float): Boolean =
        !heightDp.isNaN() && heightDp > 0f && heightDp < COMPACT_HEIGHT_THRESHOLD_DP

    private fun cacheKeyForContext(context: Context, uri: String): CacheKey {
        val metrics = context.resources.displayMetrics
        return cacheKeyFor(uri, metrics.widthPixels, metrics.heightPixels)
    }

    private fun getCachedBitmap(key: CacheKey): Bitmap? = synchronized(cacheLock) {
        val bmp = bitmapCache[key] ?: return null
        if (!isUsable(bmp)) {
            bitmapCache.remove(key)
            return null
        }
        bmp
    }

    private fun putCachedBitmap(key: CacheKey, bitmap: Bitmap) {
        synchronized(cacheLock) {
            // When the active wallpaper URI changes, drop stale URIs immediately
            // while keeping up to MAX_CACHE_ENTRIES display buckets for the active URI.
            val iter = bitmapCache.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().key.uri != key.uri) {
                    iter.remove()
                }
            }
            bitmapCache[key] = bitmap
        }
    }

    /**
     * Pure power-of-two `inSampleSize` calculator. Subsamples down toward the
     * target display size while strictly enforcing [maxTextureDim] on BOTH axes
     * so extreme panoramic or ultra-high-res wallpapers never cause OOM or GL
     * texture upload failures.
     */
    internal fun computeSampleSize(
        outWidth: Int,
        outHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        maxTextureDim: Int = MAX_TEXTURE_DIM,
    ): Int {
        if (outWidth <= 0 || outHeight <= 0) return 1
        val safeTargetW = targetWidth.coerceAtLeast(1)
        val safeTargetH = targetHeight.coerceAtLeast(1)
        val safeMaxDim = maxTextureDim.coerceAtLeast(64)
        var sample = 1
        while (outWidth / (sample * 2) >= safeTargetW &&
            outHeight / (sample * 2) >= safeTargetH
        ) {
            sample *= 2
        }
        while (outWidth / sample > safeMaxDim || outHeight / sample > safeMaxDim) {
            sample *= 2
        }
        return sample
    }

    /**
     * Fade in only for genuine asynchronous decodes landing on an already-
     * attached view when system animations are enabled; instant snap on cache
     * hits or reduced-motion so deck switches never flicker.
     */
    internal fun shouldFadeIn(
        wasCacheHit: Boolean,
        isAttached: Boolean,
        animationsEnabled: Boolean = true,
    ): Boolean = !wasCacheHit && isAttached && animationsEnabled

    /** Clamp requested wallpaper opacity cleanly to `[0f, 1f]`. */
    internal fun clampAlpha(alpha: Float): Float = alpha.coerceIn(0f, 1f)

    /** Pure safety guard rejecting recycled or zero-dimension bitmaps before draw/cache. */
    internal fun isBitmapUsable(recycled: Boolean, width: Int, height: Int): Boolean =
        !recycled && width > 0 && height > 0

    private fun isUsable(bitmap: Bitmap?): Boolean =
        bitmap != null && isBitmapUsable(bitmap.isRecycled, bitmap.width, bitmap.height)

    /** Pure 4-stop chrome-safe gradient stops for compact vs standard heights. */
    internal fun scrimColorsFor(compactHeight: Boolean): IntArray = if (compactHeight) {
        intArrayOf(
            SCRIM_COMPACT_TOP,
            SCRIM_COMPACT_UPPER_CENTER,
            SCRIM_COMPACT_LOWER_CENTER,
            SCRIM_COMPACT_BOTTOM,
        )
    } else {
        intArrayOf(
            SCRIM_TOP,
            SCRIM_UPPER_CENTER,
            SCRIM_LOWER_CENTER,
            SCRIM_BOTTOM,
        )
    }

    /** Pure gradient color stops for the wallpaper top/bottom 4-stop vignette scrim. */
    internal fun scrimColors(): IntArray = scrimColorsFor(compactHeight = false)

    /** Reusable vertical vignette scrim drawable anchoring top & bottom deck chrome. */
    fun scrimDrawable(compactHeight: Boolean = false): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        scrimColorsFor(compactHeight),
    )

    /**
     * Insert a dimmed wallpaper ImageView + 4-stop top/bottom vignette scrim
     * behind deck content when [uriString] is non-null. No-op when null/blank
     * (caller keeps solid black / tint).
     */
    fun attachIfConfigured(
        root: FrameLayout,
        context: Context,
        uriString: String?,
        alpha: Float = 0.35f,
    ) {
        val uri = uriString?.takeIf { it.isNotBlank() } ?: return
        val safeAlpha = clampAlpha(alpha)
        val cacheKey = cacheKeyForContext(context, uri)
        val hasWarmHit = getCachedBitmap(cacheKey) != null
        val dm = context.resources.displayMetrics
        val heightDp = dm.heightPixels / dm.density.coerceAtLeast(0.1f)
        val wallpaperView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            this.alpha = if (hasWarmHit) safeAlpha else 0f
            setBackgroundColor(Color.BLACK)
            addOnAttachStateChangeListener(DETACH_ANIM_CANCEL)
        }
        val scrimView = View(context).apply {
            background = scrimDrawable(compactHeight = isCompactHeightDp(heightDp))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val layer = FrameLayout(context).apply {
            addView(
                wallpaperView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                scrimView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        root.addView(
            layer,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        loadAsync(context, uri, wallpaperView, targetAlpha = safeAlpha)
    }

    fun loadAsync(
        context: Context,
        uriString: String,
        target: ImageView,
        targetAlpha: Float = 0.35f,
    ) {
        val safeAlpha = clampAlpha(targetAlpha)
        val cacheKey = cacheKeyForContext(context, uriString)
        val hit = getCachedBitmap(cacheKey)
        if (hit != null) {
            target.animate().cancel()
            target.alpha = safeAlpha
            target.setImageBitmap(hit)
            return
        }
        EXECUTOR.execute {
            val bitmap = decode(context, uriString)?.takeIf { isUsable(it) } ?: return@execute
            putCachedBitmap(cacheKey, bitmap)
            target.post {
                val app = context.applicationContext as? GhostGalleonApp
                val current = app?.settings?.wallpaperUri
                if (target.isAttachedToWindow && current == uriString && isUsable(bitmap)) {
                    target.animate().cancel()
                    target.setImageBitmap(bitmap)
                    if (shouldFadeIn(
                            wasCacheHit = false,
                            isAttached = target.isAttachedToWindow,
                            animationsEnabled = ValueAnimator.areAnimatorsEnabled(),
                        )
                    ) {
                        target.alpha = 0f
                        target.animate()
                            .alpha(safeAlpha)
                            .setDuration(FADE_IN_MS)
                            .setInterpolator(FADE_INTERPOLATOR)
                            .start()
                    } else {
                        target.alpha = safeAlpha
                    }
                }
            }
        }
    }

    fun decode(context: Context, uriString: String): Bitmap? =
        runCatching {
            val uri = Uri.parse(uriString)
            val metrics = context.resources.displayMetrics
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = computeSampleSize(
                outWidth = bounds.outWidth,
                outHeight = bounds.outHeight,
                targetWidth = metrics.widthPixels,
                targetHeight = metrics.heightPixels,
            )
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
}
