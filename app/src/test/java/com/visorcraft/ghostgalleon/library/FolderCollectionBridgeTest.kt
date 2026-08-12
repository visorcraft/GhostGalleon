package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.settings.FolderSpec
import com.visorcraft.ghostgalleon.settings.Folders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderCollectionBridgeTest {

    @Test
    fun `mirrorFolderToCollection copies members into same-named collection`() {
        val folders = Folders.create(
            emptyMap(),
            "f1",
            "Indie",
            listOf("rom:snes:a.sfc", "com.example.game"),
        )
        val cols = FolderCollectionBridge.mirrorFolderToCollection(
            folders,
            "f1",
            emptyMap(),
        )
        assertEquals(
            listOf("rom:snes:a.sfc", "com.example.game"),
            CollectionsOps.members(cols, "Indie"),
        )
        // Merge is additive
        val again = FolderCollectionBridge.mirrorFolderToCollection(
            folders,
            "f1",
            cols + ("Indie" to listOf("already.there")),
        )
        assertTrue(CollectionsOps.members(again, "Indie").contains("already.there"))
        assertTrue(CollectionsOps.members(again, "Indie").contains("rom:snes:a.sfc"))
    }

    @Test
    fun `mirrorCollectionToFolder creates folder and members`() {
        val cols = mapOf("RPGs" to listOf("rom:snes:x.sfc", "rom:gba:y.gba"))
        val folders = FolderCollectionBridge.mirrorCollectionToFolder(
            cols,
            "RPGs",
            emptyMap(),
            folderId = "f9",
        )
        assertEquals("RPGs", folders["f9"]!!.name)
        assertEquals(
            listOf("rom:snes:x.sfc", "rom:gba:y.gba"),
            Folders.members(folders, "f9"),
        )
    }

    @Test
    fun `contains helpers`() {
        val folders = mapOf(
            "f1" to FolderSpec("f1", "A", listOf("k1")),
        )
        assertTrue(FolderCollectionBridge.folderContains(folders, "f1", "k1"))
        assertFalse(FolderCollectionBridge.folderContains(folders, "f1", "k2"))
        val cols = mapOf("A" to listOf("k1"))
        assertTrue(FolderCollectionBridge.collectionContains(cols, "A", "k1"))
        assertFalse(FolderCollectionBridge.collectionContains(cols, "A", "k2"))
    }

    @Test
    fun `live sync folder members replace same-named collection`() {
        val folders = mapOf(
            "f1" to FolderSpec("f1", "Indie", listOf("rom:a", "rom:b")),
        )
        val cols = FolderCollectionBridge.syncCollectionFromFolder(
            folders, "f1", mapOf("Indie" to listOf("old")),
        )
        assertEquals(listOf("rom:a", "rom:b"), cols["Indie"])
    }

    @Test
    fun `live sync collection members replace same-named folder`() {
        val folders = mapOf(
            "f1" to FolderSpec("f1", "Indie", listOf("old")),
        )
        val next = FolderCollectionBridge.syncFolderFromCollection(
            mapOf("Indie" to listOf("rom:a")),
            "Indie",
            folders,
        )
        assertEquals(listOf("rom:a"), next["f1"]!!.members)
    }
}
