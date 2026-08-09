package com.visorcraft.ghostgalleon.ui.deck

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.ui.toast

/** Shared “Add to collection” picker for Grid and Game decks. */
object CollectionDialogs {

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
                            app.updateSettings(app.settings.copy(collections = cols))
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
                    app.updateSettings(app.settings.copy(collections = cols))
                    onDone?.invoke()
                    context.toast(R.string.format_added_to_named, name)
                }
            }
            .show()
    }
}
