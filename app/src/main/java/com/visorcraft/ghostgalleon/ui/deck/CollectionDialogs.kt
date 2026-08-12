package com.visorcraft.ghostgalleon.ui.deck

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.FolderCollectionBridge
import com.visorcraft.ghostgalleon.library.LibraryBrowse
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
        AlertDialog.Builder(context)
            .setTitle(R.string.action_add_to_collection)
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(context).apply {
                        setHint(R.string.settings_collection_name_hint)
                    }
                    AlertDialog.Builder(context)
                        .setTitle(R.string.settings_new_collection)
                        .setView(input)
                        .setPositiveButton(R.string.action_create) { _, _ ->
                            val name = input.text?.toString().orEmpty()
                            var cols = CollectionsOps.createCollection(live.collections, name)
                            cols = CollectionsOps.bulkAddToCollection(cols, name, keys)
                            commitCollection(app, cols, name)
                            onDone?.invoke()
                            context.toast(R.string.format_added_to_named, name)
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                } else {
                    val name = names[which]
                    val cols = CollectionsOps.bulkAddToCollection(
                        live.collections, name, keys,
                    )
                    commitCollection(app, cols, name)
                    onDone?.invoke()
                    context.toast(R.string.format_added_to_named, name)
                }
            }
            .show()
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
        AlertDialog.Builder(context)
            .setTitle(name)
            .setItems(labels) { _, which ->
                val key = members.getOrNull(which) ?: return@setItems
                AlertDialog.Builder(context)
                    .setTitle(labelOf(key))
                    .setItems(
                        arrayOf(
                            context.getString(R.string.action_move_to_top),
                            context.getString(R.string.action_move_up),
                            context.getString(R.string.action_move_down),
                            context.getString(R.string.action_move_to_end),
                            context.getString(R.string.action_remove),
                        ),
                    ) { _, action ->
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
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }
}
