package com.visorcraft.ghostgalleon.ui.deck

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtCache
import com.visorcraft.ghostgalleon.settings.Settings

/**
 * App icon binding with per-app custom-icon override: the package icon goes
 * in immediately as the placeholder; when settings.customIcons has a SAF
 * image URI for the package, the async ArtCache path (memory → disk →
 * downscaled decode, keyed "app:<package>") replaces it. Reset simply drops
 * the map entry — the package icon then binds again on the next render.
 *
 * When [cornerRadiusPx] > 0, custom SAF bitmaps render with `CENTER_CROP` and
 * hardware-clipped optical rounded corners matching sibling ROM art tiles,
 * while transparent stock Android launcher drawables stay unclipped
 * (`FIT_CENTER`) so non-square silhouettes are never cropped.
 */
object CustomIcon {

    /** ArtCache key namespace for app icons (ROM art keys are entry ids). */
    fun cacheKey(packageName: String): String = "app:$packageName"

    fun bind(
        image: ImageView,
        iconLoader: AppIconLoader,
        cache: ArtCache,
        settings: Settings,
        packageName: String,
        targetPx: Int,
        cornerRadiusPx: Float = 0f,
    ) {
        ArtCache.dropDisplayed(image)
        applyStockFraming(image)
        image.setImageDrawable(iconLoader.load(packageName, targetPx))
        val uri = settings.customIcons[packageName]
        if (uri == null) {
            image.setTag(R.id.custom_icon_key, null)
            return
        }
        image.setTag(R.id.custom_icon_key, packageName)
        var syncHit = true
        cache.loadUri(
            image.context,
            key = cacheKey(packageName),
            uriString = uri,
            maxDimension = targetPx,
            isStillValid = { image.getTag(R.id.custom_icon_key) == packageName },
        ) { bitmap ->
            if (bitmap != null &&
                image.getTag(R.id.custom_icon_key) == packageName &&
                (syncHit || image.isAttachedToWindow)
            ) {
                applyCustomFraming(image, cornerRadiusPx)
                ArtCache.showDisplayed(image, bitmap)
            }
        }
        syncHit = false
    }

    private fun applyStockFraming(image: ImageView) {
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        if (image.clipToOutline) {
            image.clipToOutline = false
            image.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
    }

    private fun applyCustomFraming(image: ImageView, cornerRadiusPx: Float) {
        if (cornerRadiusPx > 0f) {
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            image.clipToOutline = true
        } else {
            image.scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
}
