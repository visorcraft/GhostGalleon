package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import java.util.concurrent.Executors

/**
 * Shared SAF wallpaper load for Grid and Game Mode. Decode off the UI thread;
 * apply only when still attached and [wallpaperUri] unchanged.
 */
object DeckWallpaper {

    private val EXECUTOR = Executors.newSingleThreadExecutor()

    /**
     * Insert a dimmed wallpaper ImageView behind deck content when [uriString]
     * is non-null. No-op when null/blank (caller keeps solid black / tint).
     */
    fun attachIfConfigured(
        root: FrameLayout,
        context: Context,
        uriString: String?,
        alpha: Float = 0.35f,
    ) {
        val uri = uriString?.takeIf { it.isNotBlank() } ?: return
        val wallpaperView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            this.alpha = alpha
            setBackgroundColor(Color.BLACK)
        }
        root.addView(
            wallpaperView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        loadAsync(context, uri, wallpaperView)
    }

    fun loadAsync(context: Context, uriString: String, target: ImageView) {
        EXECUTOR.execute {
            val bitmap = decode(context, uriString) ?: return@execute
            target.post {
                val app = context.applicationContext as? GhostGalleonApp
                val current = app?.settings?.wallpaperUri
                if (target.isAttachedToWindow && current == uriString) {
                    target.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun decode(context: Context, uriString: String): android.graphics.Bitmap? =
        runCatching {
            val uri = Uri.parse(uriString)
            val metrics = context.resources.displayMetrics
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= metrics.widthPixels &&
                bounds.outHeight / (sample * 2) >= metrics.heightPixels
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
}
