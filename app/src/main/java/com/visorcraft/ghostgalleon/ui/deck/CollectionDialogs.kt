package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.FolderCollectionBridge
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.toast

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
        val names = LibraryBrowse.presentCollectionRails(live.collections).toMutableList()
        names.add(0, context.getString(R.string.settings_new_collection))
        DeckDialogs.chooseItem(
            context = context,
            titleRes = R.string.action_add_to_collection,
            items = names.toTypedArray(),
        ) { which ->
            if (which == 0) {
                DeckDialogs.promptText(
                    context = context,
                    titleRes = R.string.settings_new_collection,
                    hintRes = R.string.settings_collection_name_hint,
                    positiveRes = R.string.action_create,
                ) { rawName ->
                    val name = rawName.trim()
                    if (name.isEmpty()) return@promptText
                    val current = app.settings.collections
                    var cols = CollectionsOps.createCollection(current, name)
                    cols = CollectionsOps.bulkAddToCollection(cols, name, keys)
                    commitCollection(app, cols, name)
                    onDone?.invoke()
                    context.toast(R.string.format_added_to_named, name)
                }
            } else {
                val name = names.getOrNull(which) ?: return@chooseItem
                val cols = CollectionsOps.bulkAddToCollection(
                    app.settings.collections, name, keys,
                )
                commitCollection(app, cols, name)
                onDone?.invoke()
                context.toast(R.string.format_added_to_named, name)
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
        DeckDialogs.chooseItem(
            context = context,
            title = name,
            items = labels,
            negativeRes = R.string.action_close,
        ) { which ->
            val key = members.getOrNull(which) ?: return@chooseItem
            DeckDialogs.chooseItem(
                context = context,
                title = labelOf(key),
                items = arrayOf(
                    context.getString(R.string.action_move_to_top),
                    context.getString(R.string.action_move_up),
                    context.getString(R.string.action_move_down),
                    context.getString(R.string.action_move_to_end),
                    context.getString(R.string.action_remove),
                ),
                negativeRes = R.string.action_close,
            ) { action ->
                val live = app.settings.collections
                val next = when (action) {
                    0 -> CollectionsOps.moveMemberToEdge(live, name, key, toFront = true)
                    1 -> CollectionsOps.moveMemberBy(live, name, key, -1)
                    2 -> CollectionsOps.moveMemberBy(live, name, key, 1)
                    3 -> CollectionsOps.moveMemberToEdge(live, name, key, toFront = false)
                    else -> CollectionsOps.removeFromCollection(live, name, key)
                }
                commitCollection(app, next, name)
                onChanged?.invoke()
            }
        }
    }
}
