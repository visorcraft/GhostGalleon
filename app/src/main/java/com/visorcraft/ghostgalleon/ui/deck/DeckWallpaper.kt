package com.visorcraft.ghostgalleon.ui.deck

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

    /** Vertical vignette stops: dark top/bottom chrome edges, open center. */
    internal const val SCRIM_TOP: Int = 0xB3000000.toInt()
    internal const val SCRIM_CENTER: Int = 0x1A000000
    internal const val SCRIM_BOTTOM: Int = 0xCC000000.toInt()

    /** Maximum decoded edge (px) to guard GPU texture limits and prevent OOMs. */
    internal const val MAX_TEXTURE_DIM: Int = 2048

    /** Smooth crossfade duration (ms) when a wallpaper decodes asynchronously. */
    internal const val FADE_IN_MS: Long = 220L

    private val FADE_INTERPOLATOR = DecelerateInterpolator()
    private val EXECUTOR = Executors.newSingleThreadExecutor()

    @Volatile
    private var cachedUri: String? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    /** Drop the process cache (wallpaper cleared or URI changed). */
    fun dropCache() {
        cachedUri = null
        cachedBitmap = null
    }

    /** True when a cached decode may be applied without I/O. Pure. */
    internal fun cacheSatisfies(cachedUri: String?, wantUri: String): Boolean =
        !wantUri.isBlank() && cachedUri == wantUri

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
     * attached view; instant snap on cache hits so deck switches never flicker.
     */
    internal fun shouldFadeIn(wasCacheHit: Boolean, isAttached: Boolean): Boolean =
        !wasCacheHit && isAttached

    /** Clamp requested wallpaper opacity cleanly to `[0f, 1f]`. */
    internal fun clampAlpha(alpha: Float): Float = alpha.coerceIn(0f, 1f)

    /** Pure gradient color stops for the wallpaper top/bottom vignette scrim. */
    internal fun scrimColors(): IntArray = intArrayOf(SCRIM_TOP, SCRIM_CENTER, SCRIM_BOTTOM)

    /** Reusable vertical vignette scrim drawable anchoring top & bottom deck chrome. */
    fun scrimDrawable(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        scrimColors(),
    )

    /**
     * Insert a dimmed wallpaper ImageView + top/bottom vignette scrim behind
     * deck content when [uriString] is non-null. No-op when null/blank
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
        val hasWarmHit = cachedBitmap
            ?.takeIf { !it.isRecycled && cacheSatisfies(cachedUri, uri) } != null
        val wallpaperView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            this.alpha = if (hasWarmHit) safeAlpha else 0f
            setBackgroundColor(Color.BLACK)
        }
        val scrimView = View(context).apply {
            background = scrimDrawable()
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
        val hit = cachedBitmap?.takeIf { !it.isRecycled && cacheSatisfies(cachedUri, uriString) }
        if (hit != null) {
            target.animate().cancel()
            target.alpha = safeAlpha
            target.setImageBitmap(hit)
            return
        }
        EXECUTOR.execute {
            val bitmap = decode(context, uriString) ?: return@execute
            cachedUri = uriString
            cachedBitmap = bitmap
            target.post {
                val app = context.applicationContext as? GhostGalleonApp
                val current = app?.settings?.wallpaperUri
                if (target.isAttachedToWindow && current == uriString && !bitmap.isRecycled) {
                    target.animate().cancel()
                    target.setImageBitmap(bitmap)
                    if (shouldFadeIn(wasCacheHit = false, isAttached = target.isAttachedToWindow)) {
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
