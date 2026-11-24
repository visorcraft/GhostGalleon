package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.audio.AudioPolicy
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.settings.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayAudioLogicTest {

    @Test
    fun `launchCueFor routes ROM, app, failure, and folder no-op cleanly`() {
        assertEquals(
            SoundCue.LAUNCH_GAME,
            OverlayAudioLogic.launchCueFor(LaunchGuardLogic.LaunchTargetKind.ROM, succeeded = true),
        )
        assertEquals(
            SoundCue.ERROR_DENY,
            OverlayAudioLogic.launchCueFor(LaunchGuardLogic.LaunchTargetKind.ROM, succeeded = false),
        )
        assertEquals(
            SoundCue.LAUNCH_APP,
            OverlayAudioLogic.launchCueFor(LaunchGuardLogic.LaunchTargetKind.APP, succeeded = true),
        )
        assertEquals(
            SoundCue.ERROR_DENY,
            OverlayAudioLogic.launchCueFor(LaunchGuardLogic.LaunchTargetKind.APP, succeeded = false),
        )
        assertNull(
            OverlayAudioLogic.launchCueFor(LaunchGuardLogic.LaunchTargetKind.FOLDER_NOOP, succeeded = true),
        )
    }

    @Test
    fun `favoriteCueFor and swapCueFor select exact semantic cues`() {
        assertEquals(SoundCue.FAVORITE_ON, OverlayAudioLogic.favoriteCueFor(added = true))
        assertEquals(SoundCue.FAVORITE_OFF, OverlayAudioLogic.favoriteCueFor(added = false))

        assertEquals(SoundCue.SWAP_DISPLAY, OverlayAudioLogic.swapCueFor(swapped = true))
        assertEquals(
            SoundCue.SWAP_DISPLAY,
            OverlayAudioLogic.swapCueFor(swapped = false, companionRestarted = true),
        )
        assertEquals(
            SoundCue.ERROR_DENY,
            OverlayAudioLogic.swapCueFor(swapped = false, companionRestarted = false),
        )
        assertEquals(
            SoundCue.ERROR_DENY,
            OverlayAudioLogic.swapCueFor(swapped = true, refusedByYield = true),
        )
    }

    @Test
    fun `slotMenu cues route destructive, cancel, standard, and suppress double-fire rows`() {
        assertEquals(SoundCue.NAV_TICK, OverlayAudioLogic.slotMenuNavCue(Action.NAV_UP))
        assertEquals(SoundCue.NAV_TICK, OverlayAudioLogic.slotMenuNavCue(Action.NAV_DOWN))
        assertEquals(SoundCue.NAV_PAGE_TURN, OverlayAudioLogic.slotMenuNavCue(Action.PAGE_PREV))
        assertEquals(SoundCue.NAV_PAGE_TURN, OverlayAudioLogic.slotMenuNavCue(Action.PAGE_NEXT))
        assertNull(OverlayAudioLogic.slotMenuNavCue(Action.CONFIRM))

        assertEquals(
            SoundCue.DESTRUCTIVE,
            OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE_FROM_GRID)),
        )
        assertEquals(
            SoundCue.DESTRUCTIVE,
            OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.REMOVE)),
        )
        assertEquals(
            SoundCue.DESTRUCTIVE,
            OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.HIDE)),
        )
        assertEquals(
            SoundCue.DESTRUCTIVE,
            OverlayAudioLogic.slotMenuConfirmCue(
                SlotMenu.Row.Item(SlotMenu.Choice.CLEAR_PLAY_STATS, destructive = true),
            ),
        )
        assertEquals(
            SoundCue.BACK,
            OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.CANCEL)),
        )
        assertEquals(
            SoundCue.CONFIRM,
            OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.DETAILS)),
        )

        // Suppressed so EntryActions.toggleFavorite / GridDeck.startMove own the single cue
        assertNull(OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.FAVORITE)))
        assertNull(OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.UNFAVORITE)))
        assertNull(OverlayAudioLogic.slotMenuConfirmCue(SlotMenu.Row.Item(SlotMenu.Choice.MOVE)))
    }

    @Test
    fun `quickPanel, appPicker, folderPanel, setup, dialog, and settings cues behave cleanly`() {
        // QuickPanel column panning & deduped action cues
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, OverlayAudioLogic.quickPanelTilePan(0, 3), 1e-6f)
        assertEquals(0f, OverlayAudioLogic.quickPanelTilePan(1, 3), 1e-6f)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, OverlayAudioLogic.quickPanelTilePan(2, 3), 1e-6f)
        assertEquals(
            SoundCue.CONFIRM,
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.STANDARD,
                QuickPanelNav.TileGlyph.ICON_SETTINGS,
            ),
        )
        assertEquals(
            SoundCue.CONFIRM,
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.STANDARD,
                QuickPanelNav.TileGlyph.ICON_WIFI,
            ),
        )
        assertEquals(
            SoundCue.CONFIRM,
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.STANDARD,
                QuickPanelNav.TileGlyph.ICON_BLUETOOTH,
            ),
        )
        assertNull(
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.CLOSE,
                QuickPanelNav.TileGlyph.ICON_CLOSE,
            ),
        )
        assertNull(
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.STANDARD,
                QuickPanelNav.TileGlyph.ICON_THEME_SWATCH,
            ),
        )
        assertNull(
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.PRIMARY,
                QuickPanelNav.TileGlyph.ICON_CONTINUE,
            ),
        )
        assertNull(
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.STANDARD,
                QuickPanelNav.TileGlyph.ICON_RANDOM,
            ),
        )
        assertNull(
            OverlayAudioLogic.quickPanelActionCue(
                QuickPanelNav.TileRole.STANDARD,
                QuickPanelNav.TileGlyph.ICON_AUDIO,
            ),
        )

        // AppPicker highlight vs edge bump
        assertEquals(SoundCue.NAV_EDGE_BUMP, OverlayAudioLogic.appPickerMoveEvent(changedCount = 0).cue)
        assertEquals(SoundCue.NAV_TICK, OverlayAudioLogic.appPickerMoveEvent(changedCount = 2).cue)

        // FolderPanel column pan vs Mirror footer center
        val folderLeft = OverlayAudioLogic.folderPanelNavEvent(
            FolderNavState(selectedCell = 0, mirrorFocused = false),
            columns = 4,
        )
        assertEquals(SoundCue.NAV_TICK, folderLeft.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, folderLeft.pan, 1e-6f)
        val folderMirror = OverlayAudioLogic.folderPanelNavEvent(
            FolderNavState(selectedCell = 3, mirrorFocused = true),
            columns = 4,
        )
        assertEquals(SoundCue.NAV_TICK, folderMirror.cue)
        assertEquals(0f, folderMirror.pan, 1e-6f)

        // Setup dismiss & dialog confirm
        assertEquals(SoundCue.SETUP_COMPLETE, OverlayAudioLogic.setupDismissCue(completed = true))
        assertEquals(SoundCue.BACK, OverlayAudioLogic.setupDismissCue(completed = false))
        assertEquals(SoundCue.CONFIRM, OverlayAudioLogic.dialogConfirmCue(destructive = false))
        assertEquals(SoundCue.DESTRUCTIVE, OverlayAudioLogic.dialogConfirmCue(destructive = true))

        // Settings page switch
        assertNull(OverlayAudioLogic.settingsPageSwitchEvent(2, 2))
        val next = OverlayAudioLogic.settingsPageSwitchEvent(2, 3)!!
        assertEquals(SoundCue.NAV_PAGE_TURN, next.cue)
        assertEquals(AudioPolicy.MAX_HANDHELD_PAN, next.pan, 1e-6f)
        val prevWrap = OverlayAudioLogic.settingsPageSwitchEvent(0, 8, explicitDirection = -1)!!
        assertEquals(SoundCue.NAV_PAGE_TURN, prevWrap.cue)
        assertEquals(-AudioPolicy.MAX_HANDHELD_PAN, prevWrap.pan, 1e-6f)
    }
}
