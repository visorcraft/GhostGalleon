package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, host-tested ergonomics logic for [GameDeck]'s library search dialog:
 * live inline clear button visibility alongside history access, plus IME and
 * hardware Enter submission handling.
 */
object SearchDialogLogic {

    /** Android `EditorInfo.IME_ACTION_*` constants mirrored for pure host tests. */
    const val IME_ACTION_GO: Int = 2
    const val IME_ACTION_SEARCH: Int = 3
    const val IME_ACTION_DONE: Int = 6

    /** Android `KeyEvent.KEYCODE_*` Enter constants mirrored for pure host tests. */
    const val KEYCODE_ENTER: Int = 66
    const val KEYCODE_NUMPAD_ENTER: Int = 160

    /** Inline clear button & input end-clearance geometry (dp) inside container padding. */
    const val INLINE_CLEAR_SIZE_DP: Int = 36
    const val INLINE_CLEAR_END_MARGIN_DP: Int = 4
    const val INPUT_END_PADDING_DP: Int = 44

    enum class NeutralButtonKind {
        CLEAR,
        HISTORY,
    }

    data class SearchDialogPlan(
        val showInlineClear: Boolean,
        val neutralButton: NeutralButtonKind,
    )

    /**
     * Decide inline-clear visibility and neutral dialog button role so users
     * retain instant one-tap query clearing even when recent search history
     * occupies the dialog's neutral button.
     */
    fun plan(draftText: String, hasHistory: Boolean): SearchDialogPlan = SearchDialogPlan(
        showInlineClear = draftText.isNotEmpty(),
        neutralButton = if (hasHistory) NeutralButtonKind.HISTORY else NeutralButtonKind.CLEAR,
    )

    /** True when an IME action or Enter key-down should submit the search query. */
    fun shouldSubmitFromEditor(
        actionId: Int,
        keyCode: Int?,
        isKeyDown: Boolean,
    ): Boolean {
        if (actionId == IME_ACTION_SEARCH ||
            actionId == IME_ACTION_DONE ||
            actionId == IME_ACTION_GO
        ) {
            return true
        }
        return isKeyDown && (keyCode == KEYCODE_ENTER || keyCode == KEYCODE_NUMPAD_ENTER)
    }

    /** Trim surrounding whitespace from user-entered search text. */
    fun normalizeQuery(raw: String): String = raw.trim()
}
