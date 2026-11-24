package com.visorcraft.ghostgalleon.ui.deck

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.PorterDuff
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.display.DisplayTopology
import com.visorcraft.ghostgalleon.display.LayoutMetricsResolver
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.settings.SettingsActivity

/** Swap + Settings on the larger DUAL panel; SINGLE shows Settings only. */
internal fun shouldHostSystemChromeIcons(activity: Activity): Boolean {
    val app = activity.application as? GhostGalleonApp ?: return true
    val topo = app.displayConfig
    return DisplayTopology.shouldShowSystemChromeIcons(
        mode = topo.mode,
        thisDisplayId = activity.currentDisplayId(),
        largerDisplayId = topo.largerDisplayId,
    )
}

/** Overlay Swap (bottom-start) and Settings (bottom-end) above deck chrome. */
internal fun attachSystemChromeOverlay(
    root: FrameLayout,
    context: Context,
    activity: AppCompatActivity,
    state: DeckState,
) {
    val app = activity.application as? GhostGalleonApp
    val dm = context.resources.displayMetrics
    val topoMode = app?.displayConfig?.mode ?: SurfaceMode.SINGLE
    val layout = LayoutMetricsResolver.fromWindow(
        windowWidthPx = dm.widthPixels,
        windowHeightPx = dm.heightPixels,
        densityDpi = dm.densityDpi,
        topologyMode = topoMode,
        isCompanionRole = false,
    )
    val visibleSlots = app?.settings?.dockSlots?.let(DockSlots::visibleCount)
        ?: DockSlots.MIN_VISIBLE
    val chrome = SystemChromeMetrics.computeForWindow(
        windowWidthPx = dm.widthPixels,
        density = dm.density,
        suggestedSlotDp = layout.suggestedDockSlotDp,
        visibleSlots = visibleSlots,
    )
    val accent = app?.settings?.accentColor ?: ThemePack.GHOST.accentColor

    // Swap is a dual-display action; on SINGLE it can only ever toast
    // "only one display" — hide it there instead of teasing a dead button.
    val singleDisplay = topoMode == SurfaceMode.SINGLE
    if (!singleDisplay) {
        val swapLabel = context.getString(R.string.action_swap_screens)
        val swap = iconButton(
            context,
            R.drawable.ic_swap,
            swapLabel,
        ) {
            val swapped = app?.swapInteractiveDisplay() == true
            app?.playSfx(OverlayAudioLogic.swapCueFor(swapped = swapped))
            if (app != null && !swapped) {
                Toast.makeText(
                    context,
                    context.getString(R.string.deck_only_one_display),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        polishChromeButton(
            button = swap,
            label = swapLabel,
            elevationPx = chrome.elevationPx,
            iconTint = SystemChromeMetrics.swapIconTint(accent),
        )
        root.addView(
            swap,
            FrameLayout.LayoutParams(chrome.buttonSizePx, chrome.buttonSizePx).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(chrome.edgeMarginPx, 0, 0, chrome.bottomMarginPx)
            },
        )
    }

    val settingsLabel = context.getString(R.string.label_settings)
    val settingsBtn = iconButton(
        context,
        R.drawable.ic_settings,
        settingsLabel,
    ) {
        app?.playSfx(SoundCue.MODAL_OPEN)
        launchOnOtherDisplay(
            activity,
            state,
            Intent(activity, SettingsActivity::class.java),
        )
    }
    polishChromeButton(
        button = settingsBtn,
        label = settingsLabel,
        elevationPx = chrome.elevationPx,
        iconTint = SystemChromeMetrics.settingsIconTint(),
    )
    root.addView(
        settingsBtn,
        FrameLayout.LayoutParams(chrome.buttonSizePx, chrome.buttonSizePx).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, chrome.edgeMarginPx, chrome.bottomMarginPx)
        },
    )
}

@SuppressLint("ClickableViewAccessibility")
private fun polishChromeButton(
    button: ImageView,
    label: String,
    elevationPx: Float,
    iconTint: Int,
) {
    button.elevation = elevationPx
    button.outlineProvider = CHROME_OVAL_OUTLINE
    button.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        button.tooltipText = label
    }
    button.accessibilityDelegate = CHROME_BUTTON_ROLE_DELEGATE
    button.setOnTouchListener { v, event ->
        if (!DeckMotionPolicy.shouldAnimate(DeckMotionPolicy.areSystemAnimatorsEnabled())) {
            v.animate().cancel()
            v.scaleX = 1f
            v.scaleY = 1f
            return@setOnTouchListener false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(SystemChromeMetrics.PRESS_SCALE)
                    .scaleY(SystemChromeMetrics.PRESS_SCALE)
                    .setDuration(SystemChromeMetrics.PRESS_ANIM_MS)
                    .setInterpolator(CHROME_PRESS_INTERPOLATOR)
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(SystemChromeMetrics.PRESS_ANIM_MS)
                    .setInterpolator(CHROME_PRESS_INTERPOLATOR)
                    .start()
            }
        }
        false
    }
    button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            v.animate().cancel()
            v.scaleX = 1f
            v.scaleY = 1f
        }
    })
}

private val CHROME_PRESS_INTERPOLATOR = DecelerateInterpolator()

private val CHROME_OVAL_OUTLINE = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setOval(0, 0, view.width, view.height)
    }
}

private val CHROME_BUTTON_ROLE_DELEGATE = object : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfo,
    ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
    }
}
