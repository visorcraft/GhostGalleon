package com.visorcraft.ghostgalleon.ui.deck

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Best-effort embed of another app into a companion panel via hidden
 * `android.app.ActivityView` / `android.window.TaskView` when the ROM
 * exposes them. Returns false and leaves [host] empty when the API is
 * missing or start fails — caller keeps a launch chip.
 */
object ActivityEmbed {

    fun available(): Boolean = resolveClass() != null

    fun attach(
        host: ViewGroup,
        context: Context,
        packageName: String,
    ): Boolean {
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
