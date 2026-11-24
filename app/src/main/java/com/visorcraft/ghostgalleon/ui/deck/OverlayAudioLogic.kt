package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.audio.AudioPolicy
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.settings.Action

/**
 * Pure, host-tested audio cue routing for launches, favorites, global chrome,
 * modals/overlays, dialogs, and Settings page navigation. Zero Android dependencies.
 */
object OverlayAudioLogic {

    /**
     * Resolves the launch sound cue for [targetKind] and [succeeded].
     * Folder tiles return `null` (they open [FolderPanel] instead of launching).
     */
    fun launchCueFor(
        targetKind: LaunchGuardLogic.LaunchTargetKind,
        succeeded: Boolean,
    ): SoundCue? = when (targetKind) {
        LaunchGuardLogic.LaunchTargetKind.FOLDER_NOOP -> null
        LaunchGuardLogic.LaunchTargetKind.ROM ->
            if (succeeded) SoundCue.LAUNCH_GAME else SoundCue.ERROR_DENY
        LaunchGuardLogic.LaunchTargetKind.APP ->
            if (succeeded) SoundCue.LAUNCH_APP else SoundCue.ERROR_DENY
    }

    /** Resolves favorite toggle audio feedback (`FAVORITE_ON` vs `FAVORITE_OFF`). */
    fun favoriteCueFor(added: Boolean): SoundCue =
        if (added) SoundCue.FAVORITE_ON else SoundCue.FAVORITE_OFF

    /**
     * Resolves display-swap feedback: `SWAP_DISPLAY` when a swap or dual recovery ran,
     * `ERROR_DENY` on single-display or session-yielded refusal.
     */
    fun swapCueFor(
        swapped: Boolean,
        companionRestarted: Boolean = false,
        refusedByYield: Boolean = false,
    ): SoundCue = when {
        refusedByYield -> SoundCue.ERROR_DENY
        swapped || companionRestarted -> SoundCue.SWAP_DISPLAY
        else -> SoundCue.ERROR_DENY
    }

    /** Navigation cue inside [SlotMenu] (`NAV_TICK` for Up/Down, `NAV_PAGE_TURN` for L1/R1). */
    fun slotMenuNavCue(action: Action): SoundCue? = when (action) {
        Action.NAV_UP, Action.NAV_DOWN -> SoundCue.NAV_TICK
        Action.PAGE_PREV, Action.PAGE_NEXT -> SoundCue.NAV_PAGE_TURN
        else -> null
    }

    /**
     * Confirm cue for a [SlotMenu] row.
     *
     * Returns `null` for `FAVORITE`, `UNFAVORITE`, and `MOVE` because their immediate
     * handlers emit richer semantic cues (`FAVORITE_ON`/`FAVORITE_OFF`/`MOVE_PICKUP`),
     * preventing double-firing.
     */
    internal fun slotMenuConfirmCue(item: SlotMenu.Row.Item): SoundCue? = when (item.choice) {
        SlotMenu.Choice.FAVORITE,
        SlotMenu.Choice.UNFAVORITE,
        SlotMenu.Choice.MOVE,
        -> null
        else -> when (SlotMenuNav.rowTone(item)) {
            SlotMenuNav.RowTone.DESTRUCTIVE -> SoundCue.DESTRUCTIVE
            SlotMenuNav.RowTone.CANCEL -> SoundCue.BACK
            SlotMenuNav.RowTone.STANDARD -> SoundCue.CONFIRM
        }
    }

    /** Positional stereo pan for [QuickPanel] tile grid traversal. */
    fun quickPanelTilePan(index: Int, columns: Int): Float =
        DeckAudioLogic.panForGridSlot(index, columns)

    /**
     * Action cue for activating a [QuickPanel] tile.
     *
     * Close, Theme, Continue, Random, and Audio toggle return `null` because their own handlers
     * emit `MODAL_CLOSE`, theme-pack preview/`CONFIRM`, `LAUNCH_*`/`ERROR_DENY`, or unmute `CONFIRM`.
     */
    internal fun quickPanelActionCue(
        role: QuickPanelNav.TileRole,
        glyph: QuickPanelNav.TileGlyph,
    ): SoundCue? = when {
        role == QuickPanelNav.TileRole.CLOSE -> null
        glyph == QuickPanelNav.TileGlyph.ICON_THEME_SWATCH -> null
        glyph == QuickPanelNav.TileGlyph.ICON_CONTINUE -> null
        glyph == QuickPanelNav.TileGlyph.ICON_RANDOM -> null
        glyph == QuickPanelNav.TileGlyph.ICON_AUDIO -> null
        glyph == QuickPanelNav.TileGlyph.ICON_WIFI ||
            glyph == QuickPanelNav.TileGlyph.ICON_BLUETOOTH ||
            glyph == QuickPanelNav.TileGlyph.ICON_DISPLAY ||
            glyph == QuickPanelNav.TileGlyph.ICON_SETTINGS ||
            glyph == QuickPanelNav.TileGlyph.ICON_CONTROLLER ||
            glyph == QuickPanelNav.TileGlyph.ICON_RAIL
        -> SoundCue.CONFIRM
        else -> SoundCue.CONFIRM
    }

    /** Highlight movement cue inside [AppPicker] (`NAV_EDGE_BUMP` at list bounds, else `NAV_TICK`). */
    fun appPickerMoveEvent(changedCount: Int): DeckAudioLogic.SoundEvent =
        if (changedCount <= 0) {
            DeckAudioLogic.SoundEvent(SoundCue.NAV_EDGE_BUMP, pan = 0f)
        } else {
            DeckAudioLogic.SoundEvent(SoundCue.NAV_TICK, pan = 0f)
        }

    /** Navigation cue inside [FolderPanel] (column-panned across members, centered on Mirror). */
    internal fun folderPanelNavEvent(
        state: FolderNavState,
        columns: Int,
    ): DeckAudioLogic.SoundEvent = if (state.mirrorFocused) {
        DeckAudioLogic.SoundEvent(SoundCue.NAV_TICK, pan = 0f)
    } else {
        DeckAudioLogic.SoundEvent(
            cue = SoundCue.NAV_TICK,
            pan = DeckAudioLogic.panForGridSlot(state.selectedCell, columns),
        )
    }

    /** Dismiss cue for [com.visorcraft.ghostgalleon.ui.settings.SetupCard]. */
    fun setupDismissCue(completed: Boolean): SoundCue =
        if (completed) SoundCue.SETUP_COMPLETE else SoundCue.BACK

    /** Positive-button confirmation cue for [com.visorcraft.ghostgalleon.ui.DeckDialogs]. */
    fun dialogConfirmCue(destructive: Boolean): SoundCue =
        if (destructive) SoundCue.DESTRUCTIVE else SoundCue.CONFIRM

    /** Page-switch cue for `SettingsActivity` nav blade & `L1`/`R1` stepping. */
    fun settingsPageSwitchEvent(
        fromIndex: Int,
        toIndex: Int,
        explicitDirection: Int? = null,
    ): DeckAudioLogic.SoundEvent? {
        if (fromIndex == toIndex) return null
        val dir = explicitDirection ?: toIndex.compareTo(fromIndex)
        return DeckAudioLogic.SoundEvent(
            cue = SoundCue.NAV_PAGE_TURN,
            pan = AudioPolicy.panForDirection(dir),
        )
    }
}
