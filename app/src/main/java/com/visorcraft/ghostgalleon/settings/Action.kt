package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.input.InputOwner

enum class Action {
    NAV_UP, NAV_DOWN, NAV_LEFT, NAV_RIGHT,
    CONFIRM, BACK, SWAP_SCREENS, TOGGLE_MODE,
    OPEN_SETTINGS, PAGE_PREV, PAGE_NEXT,
    OPEN_QUICK_PANEL, SEARCH_LIBRARY, TOGGLE_FAVORITE, SHOW_DETAILS,
    OPEN_SESSION_SWITCHER, TOGGLE_PLAY_HUD,
    CLAIM_HOST, RELEASE_HOST, TOGGLE_SEAT, NONE
}

// User-facing labels for the settings/remap UI: raw enum names must never
// surface on screen.
fun Action.label(): UiText = text(when (this) {
    Action.NAV_UP -> R.string.direction_up
    Action.NAV_DOWN -> R.string.direction_down
    Action.NAV_LEFT -> R.string.direction_left
    Action.NAV_RIGHT -> R.string.direction_right
    Action.CONFIRM -> R.string.action_confirm_launch
    Action.BACK -> R.string.action_back
    Action.SWAP_SCREENS -> R.string.action_swap_screens
    Action.TOGGLE_MODE -> R.string.action_toggle_mode
    Action.OPEN_SETTINGS -> R.string.action_open_settings
    Action.PAGE_PREV -> R.string.action_page_left
    Action.PAGE_NEXT -> R.string.action_page_right
    Action.OPEN_QUICK_PANEL -> R.string.action_open_quick_panel
    Action.SEARCH_LIBRARY -> R.string.action_search_library
    Action.TOGGLE_FAVORITE -> R.string.action_toggle_favorite
    Action.SHOW_DETAILS -> R.string.action_show_details
    Action.OPEN_SESSION_SWITCHER -> R.string.action_open_session_switcher
    Action.TOGGLE_PLAY_HUD -> R.string.action_toggle_play_hud
    Action.CLAIM_HOST -> R.string.action_claim_host
    Action.RELEASE_HOST -> R.string.action_release_host
    Action.TOGGLE_SEAT -> R.string.action_toggle_seat
    Action.NONE -> R.string.action_none
})

fun InputOwner.hint(): UiText? = when (this) {
    InputOwner.GAME -> text(R.string.input_owner_game)
    InputOwner.HOST -> text(R.string.input_owner_host)
    InputOwner.NONE -> null
}
