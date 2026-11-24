package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Seal the system wallpaper to solid black so the Quickstep HOME gesture
 * does not flash the stock robot image. Tries system+lock bitmaps, then
 * a PNG stream, always recycling the temporary bitmap afterward.
 * [applyWindowFallback] still paints the activity black when SET_WALLPAPER
 * is denied.
 */
object HomeWallpaper {

    /** Tiny solid-black seal bitmap dimension (px) minimizing wallpaper memory. */
    internal const val SEAL_BITMAP_SIZE_PX: Int = 8

    /** API 24+ WallpaperManager flag constants mirrored for pure host tests. */
    internal const val FLAG_SYSTEM_MASK: Int = 1
    internal const val FLAG_LOCK_MASK: Int = 2

    /**
     * Ordered API 24+ wallpaper flag ladder: both system+lock first, then
     * system-only, then lock-only. Empty on pre-API-24 devices. Pure.
     */
    internal fun sealFlagLadder(sdkInt: Int): List<Int> =
        if (sdkInt >= 24) {
            listOf(
                FLAG_SYSTEM_MASK or FLAG_LOCK_MASK,
                FLAG_SYSTEM_MASK,
                FLAG_LOCK_MASK,
            )
        } else {
            emptyList()
        }

    fun seal(context: Context): Boolean {
        val bmp = Bitmap.createBitmap(
            SEAL_BITMAP_SIZE_PX,
            SEAL_BITMAP_SIZE_PX,
            Bitmap.Config.RGB_565,
        )
        try {
            bmp.eraseColor(Color.BLACK)
            val wm = WallpaperManager.getInstance(context)
            for (flags in sealFlagLadder(Build.VERSION.SDK_INT)) {
                if (trySet(wm, bmp, flags)) return true
            }
            if (runCatching { wm.setBitmap(bmp); true }.getOrDefault(false)) return true
            val png = ByteArrayOutputStream()
            if (bmp.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                val bytes = png.toByteArray()
                if (runCatching {
                        wm.setStream(ByteArrayInputStream(bytes))
                        true
                    }.getOrDefault(false)
                ) {
                    return true
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    val both = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    val ok = runCatching {
                        wm.setStream(ByteArrayInputStream(bytes), null, true, both)
                        true
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            return false
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    fun applyWindowFallback(activity: Activity) {
        val win = activity.window ?: return
        win.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        win.decorView.setBackgroundColor(Color.BLACK)
    }

    private fun trySet(wm: WallpaperManager, bmp: Bitmap, flags: Int): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        return runCatching {
            wm.setBitmap(bmp, null, true, flags)
            true
        }.getOrDefault(false)
    }
}
