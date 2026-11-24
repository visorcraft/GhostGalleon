package com.visorcraft.ghostgalleon.rom

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque

/**
 * One file under a scanned tree. `relativePath` is '/'-separated, relative
 * to the tree root ("snes/2020 Super Baseball (U).smc").
 */
data class DocFile(val name: String, val uri: String, val relativePath: String)

/** A walkable SAF tree (or a host-test fake). */
interface DocumentTree {
    fun walk(): List<DocFile>
}

/**
 * SAF-backed tree walker using direct [DocumentsContract] child projection
 * queries (1 Binder IPC per directory instead of 3–4 IPCs per file via
 * `DocumentFile.listFiles()`), falling back cleanly to `DocumentFile` if a
 * provider returns a null cursor. Runs off the UI thread.
 */
class SafDocumentTree(context: Context, private val treeUri: Uri) : DocumentTree {

    private val appContext: Context = context.applicationContext

    override fun walk(): List<DocFile> {
        val rootDocId = resolveRootDocId() ?: return walkFallback()
        val out = ArrayList<DocFile>(256)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.addLast(rootDocId to "")
        var rootQueried = false

        while (queue.isNotEmpty()) {
            val (dirDocId, prefix) = queue.removeFirst()
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
            } catch (_: Exception) {
                if (!rootQueried) return walkFallback()
                continue
            }
            val cursor = try {
                appContext.contentResolver.query(
                    childrenUri,
                    CHILD_PROJECTION,
                    null,
                    null,
                    null,
                )
            } catch (_: Exception) {
                if (!rootQueried) return walkFallback()
                null
            }
            if (cursor == null) {
                if (!rootQueried) return walkFallback()
                continue
            }
            var fallbackNeeded = false
            try {
                cursor.use { c ->
                    val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) {
                        if (!rootQueried) fallbackNeeded = true
                        return@use
                    }
                    rootQueried = true
                    while (c.moveToNext()) {
                        if (c.isNull(idIdx) || c.isNull(nameIdx)) continue
                        val childId = c.getString(idIdx)
                        val name = c.getString(nameIdx)
                        if (childId.isNullOrEmpty() || name.isNullOrEmpty()) continue
                        val mime = if (mimeIdx >= 0 && !c.isNull(mimeIdx)) c.getString(mimeIdx) else null
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.addLast(childId to "$prefix$name/")
                        } else if (!mime.isNullOrEmpty()) {
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            out.add(DocFile(name, childUri.toString(), prefix + name))
                        }
                    }
                }
            } catch (_: Exception) {
                if (!rootQueried) return walkFallback()
            }
            if (fallbackNeeded) return walkFallback()
        }
        return out
    }

    private fun resolveRootDocId(): String? = try {
        if (DocumentsContract.isDocumentUri(appContext, treeUri)) {
            DocumentsContract.getDocumentId(treeUri)
        } else {
            DocumentsContract.getTreeDocumentId(treeUri)
        }
    } catch (_: Exception) {
        null
    }

    private fun walkFallback(): List<DocFile> {
        val out = mutableListOf<DocFile>()
        fun recurse(dir: DocumentFile, prefix: String) {
            dir.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                when {
                    child.isDirectory -> recurse(child, "$prefix$name/")
                    child.isFile -> out.add(DocFile(name, child.uri.toString(), prefix + name))
                }
            }
        }
        runCatching { DocumentFile.fromTreeUri(appContext, treeUri) }.getOrNull()
            ?.let { recurse(it, "") }
        return out
    }

    companion object {
        private val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
