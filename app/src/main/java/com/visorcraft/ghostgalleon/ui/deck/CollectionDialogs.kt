package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import androidx.annotation.StringRes
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.FolderCollectionBridge
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.ui.DeckDialogs
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
        DeckDialogs.chooseItem(
            context = context,
            title = dialogTitle,
            items = rows.toTypedArray(),
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
            val actions = CollectionDialogRows.availableMemberActions(which, members.size)
            val actionLabels = actions.map { context.getString(it.labelRes) }.toTypedArray()
            val memberTitle = context.getString(
                R.string.format_dot_pair,
                labelOf(key),
                context.getString(R.string.format_progress, which + 1, members.size),
            )
            DeckDialogs.chooseItem(
                context = context,
                title = memberTitle,
                items = actionLabels,
                negativeRes = R.string.action_close,
            ) { actionIndex ->
                val chosen = actions.getOrNull(actionIndex)
                if (chosen != null) {
                    val live = app.settings.collections
                    val next = when (chosen) {
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
                    val feedback = chosen.feedbackRes
                    if (feedback != null) {
                        context.toast(feedback)
                    } else {
                        context.toast(R.string.format_removed_from_named, name)
                    }
                }
            }
        }
    }
}
