package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.settings.FolderSpec
import com.visorcraft.ghostgalleon.settings.Folders

/**
 * Pure bridge between Grid folders and Game Mode named collections.
 * Same membership intent; two storage models — mirror without schema bump.
 */
object FolderCollectionBridge {

    /** Collection name used when mirroring a folder (stable, user-visible). */
    fun collectionNameForFolder(folder: FolderSpec): String =
        folder.name.trim().ifEmpty { folder.id }

    /**
     * Copy every folder member into a same-named collection (create or merge).
     * Returns updated collections map; favorites rail untouched.
     */
    fun mirrorFolderToCollection(
        folders: Map<String, FolderSpec>,
        folderId: String,
        collections: Map<String, List<String>>,
    ): Map<String, List<String>> {
        val folder = folders[folderId] ?: return collections
        val name = collectionNameForFolder(folder)
        var next = collections
        for (key in folder.members) {
            next = CollectionsOps.addToCollection(next, name, key)
        }
        return next
    }

    /**
     * Copy every collection member into a folder (create folder if missing).
     * [folderId] is the target folder id (existing or new).
     */
    fun mirrorCollectionToFolder(
        collections: Map<String, List<String>>,
        collectionName: String,
        folders: Map<String, FolderSpec>,
        folderId: String,
        folderDisplayName: String = collectionName,
    ): Map<String, FolderSpec> {
        val members = CollectionsOps.members(collections, collectionName)
        if (members.isEmpty() && folderId !in folders) return folders
        val base = if (folderId in folders) {
            folders
        } else {
            Folders.create(folders, folderId, folderDisplayName, emptyList())
        }
        var next = base
        for (key in members) {
            next = Folders.addMember(next, folderId, key)
        }
        return next
    }

    /** True when [key] is a member of the folder. */
    fun folderContains(
        folders: Map<String, FolderSpec>,
        folderId: String,
        key: String,
    ): Boolean = key in Folders.members(folders, folderId)

    /** True when [key] is a member of the named collection. */
    fun collectionContains(
        collections: Map<String, List<String>>,
        name: String,
        key: String,
    ): Boolean = key in CollectionsOps.members(collections, name)

    /**
     * Keep the same-named collection in lockstep with [folderId] members.
     * Creates the collection when missing. Pure.
     */
    fun syncCollectionFromFolder(
        folders: Map<String, FolderSpec>,
        folderId: String,
        collections: Map<String, List<String>>,
    ): Map<String, List<String>> {
        val folder = folders[folderId] ?: return collections
        val name = collectionNameForFolder(folder)
        if (name.isEmpty()) return collections
        return collections + (name to folder.members.toList())
    }

    /**
     * Keep the folder whose display name equals [collectionName] in lockstep
     * with that collection's members. No folder → unchanged. Pure.
     */
    fun syncFolderFromCollection(
        collections: Map<String, List<String>>,
        collectionName: String,
        folders: Map<String, FolderSpec>,
    ): Map<String, FolderSpec> {
        val name = collectionName.trim()
        if (name.isEmpty()) return folders
        val folder = folders.values.firstOrNull {
            collectionNameForFolder(it).equals(name, ignoreCase = true)
        } ?: return folders
        val members = CollectionsOps.members(collections, name)
        return folders + (folder.id to folder.copy(members = members))
    }
}
