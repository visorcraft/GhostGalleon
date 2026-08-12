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
 * a PNG stream. [applyWindowFallback] still paints the activity black
 * when SET_WALLPAPER is denied.
 */
object HomeWallpaper {

    fun seal(context: Context): Boolean {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565)
        bmp.eraseColor(Color.BLACK)
        val wm = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= 24) {
            val both = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            if (trySet(wm, bmp, both)) return true
            if (trySet(wm, bmp, WallpaperManager.FLAG_SYSTEM)) return true
            if (trySet(wm, bmp, WallpaperManager.FLAG_LOCK)) return true
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
    }

    fun applyWindowFallback(activity: Activity) {
        activity.window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        activity.window.decorView.setBackgroundColor(Color.BLACK)
    }

    private fun trySet(wm: WallpaperManager, bmp: Bitmap, flags: Int): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        return runCatching {
            wm.setBitmap(bmp, null, true, flags)
            true
        }.getOrDefault(false)
    }
}
