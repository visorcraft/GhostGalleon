package com.visorcraft.ghostgalleon.ui.deck

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.DisplayTopology
import com.visorcraft.ghostgalleon.display.LayoutMetricsResolver
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.display.currentDisplayId
import com.visorcraft.ghostgalleon.settings.DockSlots
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

    // Swap is a dual-display action; on SINGLE it can only ever toast
    // "only one display" — hide it there instead of teasing a dead button.
    val singleDisplay = topoMode == SurfaceMode.SINGLE
    if (!singleDisplay) {
        val swap = iconButton(
            context,
            R.drawable.ic_swap,
            context.getString(R.string.action_swap_screens),
        ) {
            if (app != null && !app.swapInteractiveDisplay()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.deck_only_one_display),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        root.addView(
            swap,
            FrameLayout.LayoutParams(chrome.buttonSizePx, chrome.buttonSizePx).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(chrome.edgeMarginPx, 0, 0, chrome.bottomMarginPx)
            },
        )
    }

    val settingsBtn = iconButton(
        context,
        R.drawable.ic_settings,
        context.getString(R.string.label_settings),
    ) {
        launchOnOtherDisplay(
            activity,
            state,
            Intent(activity, SettingsActivity::class.java),
        )
    }
    root.addView(
        settingsBtn,
        FrameLayout.LayoutParams(chrome.buttonSizePx, chrome.buttonSizePx).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, chrome.edgeMarginPx, chrome.bottomMarginPx)
        },
    )
}
