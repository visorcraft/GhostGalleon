package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialog
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.input.KeyMap
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.FolderCollectionBridge
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.hideStatusBar
import com.visorcraft.ghostgalleon.ui.toast

/** Pure, host-tested option-building and validation helpers for [CollectionDialogs]. */
internal object CollectionDialogRows {

    data class CollectionOption(
        val name: String,
        val memberCount: Int,
        val alreadyContainsAll: Boolean,
        val alreadyContainsSome: Boolean,
    )

    /**
     * Build ordered existing collection options with live member counts and
     * membership flags for [keys], matching [LibraryBrowse.presentCollectionRails].
     */
    fun buildAddOptions(
        collections: Map<String, List<String>>,
        keys: List<String>,
    ): List<CollectionOption> {
        val targetKeys = keys.distinct()
        return LibraryBrowse.presentCollectionRails(collections).map { name ->
            val members = CollectionsOps.members(collections, name)
            val memberSet = members.toSet()
            CollectionOption(
                name = name,
                memberCount = members.size,
                alreadyContainsAll = targetKeys.isNotEmpty() && targetKeys.all { it in memberSet },
                alreadyContainsSome = targetKeys.any { it in memberSet },
            )
        }
    }

    /** Trim and reject blank collection names. */
    fun normalizeCollectionName(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() }

    /**
     * Row index inside [CollectionDialogs.promptAdd] (offset by +1 for row 0
     * `"New collection…"`) of the first collection already containing all keys,
     * or `-1` when none match. Host-tested.
     */
    fun firstCheckedAddOptionRowIndex(options: List<CollectionOption>): Int {
        val idx = options.indexOfFirst { it.alreadyContainsAll }
        return if (idx >= 0) idx + 1 else -1
    }

    enum class MemberAction(
        @StringRes val labelRes: Int,
        @StringRes val feedbackRes: Int?,
    ) {
        MOVE_TO_TOP(R.string.action_move_to_top, R.string.deck_moved_top),
        MOVE_UP(R.string.action_move_up, R.string.deck_moved_up),
        MOVE_DOWN(R.string.action_move_down, R.string.deck_moved_down),
        MOVE_TO_END(R.string.action_move_to_end, R.string.deck_moved_end),
        REMOVE(R.string.action_remove, null),
    }

    /**
     * Context-aware member actions that omit no-op and redundant moves
     * (e.g. no Move-Up on index 0, no separate Move-to-Top on index 1, no
     * Move-Down on the last item, and Remove-only for single-item collections).
     */
    fun availableMemberActions(index: Int, totalCount: Int): List<MemberAction> {
        if (totalCount <= 1 || index !in 0 until totalCount) {
            return listOf(MemberAction.REMOVE)
        }
        return buildList(5) {
            if (index > 1) add(MemberAction.MOVE_TO_TOP)
            if (index > 0) add(MemberAction.MOVE_UP)
            if (index < totalCount - 1) add(MemberAction.MOVE_DOWN)
            if (index < totalCount - 2) add(MemberAction.MOVE_TO_END)
            add(MemberAction.REMOVE)
        }
    }

    /**
     * Build [SlotMenu.Row] entries for a collection member at [index]/[totalCount]
     * using existing [SlotMenu.Choice] items with destructive red emphasis on
     * [SlotMenu.Choice.REMOVE_FROM_COLLECTION]. Host-tested.
     */
    fun toSlotMenuRows(index: Int, totalCount: Int): List<SlotMenu.Row> {
        val actions = availableMemberActions(index, totalCount)
        return buildList(actions.size + 1) {
            actions.forEach { action ->
                val choice = when (action) {
                    MemberAction.MOVE_TO_TOP -> SlotMenu.Choice.MOVE_TO_TOP
                    MemberAction.MOVE_UP -> SlotMenu.Choice.MOVE_UP
                    MemberAction.MOVE_DOWN -> SlotMenu.Choice.MOVE_DOWN
                    MemberAction.MOVE_TO_END -> SlotMenu.Choice.MOVE_TO_END
                    MemberAction.REMOVE -> SlotMenu.Choice.REMOVE_FROM_COLLECTION
                }
                add(
                    SlotMenu.Row.Item(
                        choice = choice,
                        destructive = action == MemberAction.REMOVE,
                    ),
                )
            }
            add(SlotMenu.Row.Item(SlotMenu.Choice.CANCEL))
        }
    }
}

/** Shared “Add to collection” picker for Grid and Game decks. */
object CollectionDialogs {

    fun commitCollection(
        app: GhostGalleonApp,
        collections: Map<String, List<String>>,
        name: String,
    ) {
        val folders = FolderCollectionBridge.syncFolderFromCollection(
            collections, name, app.settings.folders,
        )
        app.updateSettings(app.settings.copy(collections = collections, folders = folders))
    }

    fun promptAdd(
        context: Context,
        app: GhostGalleonApp,
        keys: List<String>,
        onDone: (() -> Unit)? = null,
    ) {
        if (keys.isEmpty()) {
            context.toast(R.string.deck_nothing_selected)
            return
        }
        val live = app.settings
        val options = CollectionDialogRows.buildAddOptions(live.collections, keys)
        val rows = buildList(options.size + 1) {
            add(context.getString(R.string.settings_new_collection_row))
            options.forEach { opt ->
                val countLabel = context.resources.getQuantityString(
                    R.plurals.count_items,
                    opt.memberCount,
                    opt.memberCount,
                )
                val base = context.getString(R.string.format_dot_pair, opt.name, countLabel)
                add(
                    if (opt.alreadyContainsAll) {
                        context.getString(R.string.format_selected_check, base)
                    } else {
                        base
                    },
                )
            }
        }
        val baseTitle = context.getString(R.string.action_add_to_collection)
        val dialogTitle = if (keys.size > 1) {
            context.getString(
                R.string.format_dot_pair,
                baseTitle,
                context.resources.getQuantityString(R.plurals.count_items, keys.size, keys.size),
            )
        } else {
            baseTitle
        }
        val checkedIdx = CollectionDialogRows.firstCheckedAddOptionRowIndex(options)
        DeckDialogs.chooseItem(
            context = context,
            title = dialogTitle,
            items = rows.toTypedArray(),
            checkedIndex = checkedIdx,
        ) { which ->
            if (which == 0) {
                DeckDialogs.promptText(
                    context = context,
                    titleRes = R.string.settings_new_collection,
                    hintRes = R.string.settings_collection_name_hint,
                    positiveRes = R.string.action_create,
                    requireNonBlank = true,
                ) { rawName ->
                    val name = CollectionDialogRows.normalizeCollectionName(rawName)
                        ?: return@promptText
                    val current = app.settings.collections
                    var cols = CollectionsOps.createCollection(current, name)
                    cols = CollectionsOps.bulkAddToCollection(cols, name, keys)
                    commitCollection(app, cols, name)
                    onDone?.invoke()
                    context.toast(R.string.format_added_to_named, name)
                }
            } else {
                val opt = options.getOrNull(which - 1) ?: return@chooseItem
                val cols = CollectionsOps.bulkAddToCollection(
                    app.settings.collections, opt.name, keys,
                )
                commitCollection(app, cols, opt.name)
                onDone?.invoke()
                context.toast(R.string.format_added_to_named, opt.name)
            }
        }
    }

    fun promptMembers(
        context: Context,
        app: GhostGalleonApp,
        name: String,
        labelOf: (String) -> String,
        onChanged: (() -> Unit)? = null,
    ) {
        val members = CollectionsOps.members(app.settings.collections, name)
        if (members.isEmpty()) {
            context.toast(R.string.deck_empty_collection)
            return
        }
        val labels = members.map { labelOf(it) }.toTypedArray()
        val listTitle = context.getString(
            R.string.format_dot_pair,
            name,
            context.resources.getQuantityString(R.plurals.count_items, members.size, members.size),
        )
        DeckDialogs.chooseItem(
            context = context,
            title = listTitle,
            items = labels,
            negativeRes = R.string.action_close,
        ) { which ->
            val key = members.getOrNull(which) ?: return@chooseItem
            val slotRows = CollectionDialogRows.toSlotMenuRows(which, members.size)
            val memberTitle = context.getString(
                R.string.format_dot_pair,
                labelOf(key),
                context.getString(R.string.format_progress, which + 1, members.size),
            )
            lateinit var menuDialog: AppCompatDialog
            val menu = SlotMenu(
                context = context,
                accentColor = app.settings.accentColor,
                rows = slotRows,
                title = memberTitle,
            ) { choice ->
                menuDialog.dismiss()
                val live = app.settings.collections
                val action = when (choice) {
                    SlotMenu.Choice.MOVE_TO_TOP -> CollectionDialogRows.MemberAction.MOVE_TO_TOP
                    SlotMenu.Choice.MOVE_UP -> CollectionDialogRows.MemberAction.MOVE_UP
                    SlotMenu.Choice.MOVE_DOWN -> CollectionDialogRows.MemberAction.MOVE_DOWN
                    SlotMenu.Choice.MOVE_TO_END -> CollectionDialogRows.MemberAction.MOVE_TO_END
                    SlotMenu.Choice.REMOVE_FROM_COLLECTION -> CollectionDialogRows.MemberAction.REMOVE
                    else -> null
                } ?: return@SlotMenu
                val next = when (action) {
                    CollectionDialogRows.MemberAction.MOVE_TO_TOP ->
                        CollectionsOps.moveMemberToEdge(live, name, key, toFront = true)
                    CollectionDialogRows.MemberAction.MOVE_UP ->
                        CollectionsOps.moveMemberBy(live, name, key, -1)
                    CollectionDialogRows.MemberAction.MOVE_DOWN ->
                        CollectionsOps.moveMemberBy(live, name, key, 1)
                    CollectionDialogRows.MemberAction.MOVE_TO_END ->
                        CollectionsOps.moveMemberToEdge(live, name, key, toFront = false)
                    CollectionDialogRows.MemberAction.REMOVE ->
                        CollectionsOps.removeFromCollection(live, name, key)
                }
                commitCollection(app, next, name)
                onChanged?.invoke()
                val feedback = action.feedbackRes
                if (feedback != null) {
                    context.toast(feedback)
                } else {
                    context.toast(R.string.format_removed_from_named, name)
                }
            }
            menuDialog = AppCompatDialog(context, R.style.Theme_GhostGalleon_Dialog).apply {
                setContentView(menu.view)
                setCancelable(true)
                setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    val mapped = KeyMap.resolve(keyCode, app.settings).takeIf { it != Action.NONE }
                        ?: when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> Action.NAV_UP
                            KeyEvent.KEYCODE_DPAD_DOWN -> Action.NAV_DOWN
                            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_PAGE_UP -> Action.PAGE_PREV
                            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_PAGE_DOWN -> Action.PAGE_NEXT
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_BUTTON_A,
                            -> Action.CONFIRM
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> Action.BACK
                            else -> Action.NONE
                        }
                    if (mapped != Action.NONE) {
                        menu.handleAction(mapped)
                    } else {
                        false
                    }
                }
                window?.let { win ->
                    win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    win.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    win.setDimAmount(0f)
                    hideStatusBar(win)
                }
            }
            menuDialog.show()
        }
    }
}
