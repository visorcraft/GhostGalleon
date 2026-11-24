package com.visorcraft.ghostgalleon.ui.deck

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

/**
 * Best-effort embed of another app into a companion panel via hidden
 * `android.app.ActivityView` / `android.window.TaskView` when the ROM
 * exposes them. Returns false and leaves [host] empty when the API is
 * missing or start fails — caller keeps a launch chip.
 */
object ActivityEmbed {

    /** Rounded viewport corner radius (dp) framing embedded companion surfaces. */
    const val VIEWPORT_CORNER_RADIUS_DP: Int = 16

    /** Pure pre-check rejecting blank or malformed package names before reflection. */
    fun canAttemptAttach(packageName: String?): Boolean {
        val trimmed = packageName?.trim().orEmpty()
        return trimmed.isNotEmpty() && '.' in trimmed && trimmed.none { it.isWhitespace() }
    }

    /**
     * Frame an embed viewport host with rounded clip outline, sunken surface fill,
     * and a crisp 1dp hairline border so embedded apps harmonize with OLED cards.
     */
    fun applyViewportFrame(
        host: ViewGroup,
        accentColor: Int? = null,
        cornerRadiusDp: Int = VIEWPORT_CORNER_RADIUS_DP,
    ) {
        val context = host.context
        val radiusPx = context.dpF(cornerRadiusDp)
        host.background = GradientDrawable().apply {
            setColor(TileBackgrounds.surfaceSunken(context))
            cornerRadius = radiusPx
        }
        host.clipToOutline = true
        host.outlineProvider = OutlineCache.roundedRect(radiusPx)
        val rim = accentColor?.let { CompanionHeroStyle.artRimColor(it) }
            ?: TileBackgrounds.hairlineDivider(context)
        host.foreground = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = radiusPx
            setStroke(context.strokePx(1), rim)
        }
    }

    fun available(): Boolean = resolveClass() != null

    fun attach(
        host: ViewGroup,
        context: Context,
        packageName: String,
    ): Boolean {
        if (!canAttemptAttach(packageName)) return false
        val cls = resolveClass() ?: return false
        val view = runCatching {
            cls.getConstructor(Context::class.java).newInstance(context) as View
        }.getOrNull() ?: return false
        host.removeAllViews()
        host.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false.also { host.removeAllViews() }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = startOn(view, context, intent)
        if (!started) {
            runCatching { cls.getMethod("release").invoke(view) }
            host.removeAllViews()
        }
        return started
    }

    fun release(host: ViewGroup) {
        val child = host.getChildAt(0) ?: return
        runCatching { child.javaClass.getMethod("release").invoke(child) }
        host.removeAllViews()
    }

    private fun resolveClass(): Class<*>? {
        listOf("android.app.ActivityView", "android.window.TaskView").forEach { name ->
            runCatching { return Class.forName(name) }
        }
        return null
    }

    private fun startOn(view: View, context: Context, intent: Intent): Boolean {
        val cls = view.javaClass
        runCatching {
            val method = cls.getMethod("startActivity", Intent::class.java, UserHandle::class.java)
            method.invoke(view, intent, android.os.Process.myUserHandle())
            return true
        }
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                val pending = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                cls.getMethod("startActivity", PendingIntent::class.java).invoke(view, pending)
                return true
            }
        }
        return runCatching {
            cls.getMethod("startActivity", Intent::class.java).invoke(view, intent)
            true
        }.getOrDefault(false)
    }
}
