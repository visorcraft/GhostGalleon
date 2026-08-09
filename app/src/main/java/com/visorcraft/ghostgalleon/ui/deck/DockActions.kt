package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.ui.toast

/** Shared dock mutation and translation-safe feedback. */
object DockActions {

    fun pin(
        context: Context,
        app: GhostGalleonApp,
        key: String,
        onSlots: (List<String?>, UiText) -> Unit,
    ) {
        val result = DockSlots.pinKey(app.settings.dockSlots, key)
        when (result.status) {
            DockSlots.PinStatus.ALREADY -> context.toast(R.string.deck_already_in_dock)
            DockSlots.PinStatus.FULL -> context.toast(R.string.deck_dock_full)
            DockSlots.PinStatus.PINNED -> onSlots(
                result.slots,
                text(R.string.deck_pinned_to_dock),
            )
        }
    }

    fun unpin(
        context: Context,
        app: GhostGalleonApp,
        key: String,
        onSlots: (List<String?>, UiText) -> Unit,
    ) {
        if (!DockSlots.containsKey(app.settings.dockSlots, key)) {
            context.toast(R.string.deck_not_in_dock)
            return
        }
        onSlots(
            DockSlots.unpinKey(app.settings.dockSlots, key),
            text(R.string.deck_unpinned_from_dock),
        )
    }

    fun removeAt(app: GhostGalleonApp, index: Int): List<String?> =
        DockSlots.remove(app.settings.dockSlots, index)

    fun fill(app: GhostGalleonApp, slot: Int, key: String): List<String?> =
        DockSlots.fill(app.settings.dockSlots, slot, key)

    fun persist(
        context: Context,
        app: GhostGalleonApp,
        slots: List<String?>,
        feedback: UiText? = null,
    ) {
        app.updateSettings(app.settings.copy(dockSlots = slots))
        feedback?.let { context.toast(it) }
    }

    fun clampFocus(focused: Int?, next: List<String?>): Int? {
        if (focused == null) return null
        val last = DockSlots.visibleCount(next) - 1
        return if (last < 0) null else focused.coerceAtMost(last)
    }
}
