package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.library.LibraryBrowse
import java.util.Arrays

/**
 * Tree walk → RomEntry matching. Pure logic over the DocumentTree
 * abstraction; host-tested with fake trees.
 */
object RomScanner {

    private class RomSortNode(
        val platformId: String,
        val nameLower: String,
        val id: String,
        val entry: RomEntry,
    )

    internal fun sortScannedEntries(entries: List<RomEntry>): List<RomEntry> {
        val n = entries.size
        if (n <= 1) return entries
        val arr = Array(n) { i ->
            val e = entries[i]
            RomSortNode(e.platformId, LibraryBrowse.fastLowercase(e.name), e.id, e)
        }
        Arrays.sort(arr) { a, b ->
            val c1 = a.platformId.compareTo(b.platformId)
            if (c1 != 0) return@sort c1
            val c2 = a.nameLower.compareTo(b.nameLower)
            if (c2 != 0) return@sort c2
            a.id.compareTo(b.id)
        }
        val out = ArrayList<RomEntry>(n)
        for (node in arr) out.add(node.entry)
        return out
    }

    /**
     * Walk every tree and match files against the platform registry.
     *
     * @param trees pairs of a tree and its root folder name (e.g. "roms" for
     *  a container grant, or "snes" when the grant points straight at a
     *  platform folder). Platform is decided by the tree root name first,
     *  then by the first relative-path segment.
     * @param readText optional reader for document URIs (SAF contentResolver
     *  in production). When present, every `gamelist.xml` found during the
     *  walk is parsed and [GamelistMeta.enrichRoms] applies offline titles
     *  and descriptions before the list is returned.
     * @param openStream optional binary opener. Used to read Vita
     *  `param.sfo` and `.vpk` archives for title ids.
     */
    fun scan(
        trees: List<Pair<DocumentTree, String>>,
        readText: ((uri: String) -> String?)? = null,
        openStream: ((uri: String) -> java.io.InputStream?)? = null,
    ): List<RomEntry> {
        val entries = mutableListOf<RomEntry>()
        val gamelistDocs = mutableListOf<DocFile>()
        trees.forEach { (tree, rootName) ->
            val rootPlatform = Platforms.platformForFolder(rootName)
            val docs = tree.walk()
            val media = LocalMedia.indexImages(docs, rootPlatform != null)
            val videos = LocalMedia.indexVideos(docs, rootPlatform != null)
            val sfoByDir = LinkedHashMap<String, DocFile>()
            for (d in docs) {
                if (d.name.equals("param.sfo", ignoreCase = true)) {
                    sfoByDir[d.relativePath.substringBeforeLast('/').lowercase()] = d
                }
            }
            val folderPlatformCache = HashMap<String, Platform?>(32)
            fun platformForFirstSegment(firstSeg: String): Platform? =
                folderPlatformCache.getOrPut(firstSeg) { Platforms.platformForFolder(firstSeg) }

            fun sfoBeside(relPath: String): VitaSfo.Info? {
                if (openStream == null || sfoByDir.isEmpty()) return null
                val dir = relPath.substringBeforeLast('/')
                val dirs = listOf("$dir/sce_sys", dir)
                for (candidate in dirs) {
                    val sfoDoc = sfoByDir[candidate.lowercase()] ?: continue
                    val bytes = runCatching {
                        openStream(sfoDoc.uri)?.use { it.readBytes() }
                    }.getOrNull() ?: continue
                    VitaSfo.parse(bytes)?.let { return it }
                }
                return null
            }
            for (doc in docs) {
                val relPath = doc.relativePath
                // Dotfiles/junk anywhere in the path: .DS_Store, ._ AppleDouble
                // files, hidden directories (zero-alloc index scan).
                if (LocalMedia.hasDotSegment(relPath)) continue
                if (DiscHygiene.skipPath(relPath)) continue
                if (doc.name.equals("gamelist.xml", ignoreCase = true)) {
                    gamelistDocs.add(doc)
                    continue
                }
                val slashIdx = relPath.indexOf('/')
                val firstSeg = if (slashIdx >= 0) relPath.substring(0, slashIdx) else relPath
                if (VitaTitles.isEboot(doc.name)) {
                    val vitaTitle = VitaTitles.titleIdIn(relPath)
                    if (rootPlatform?.id == "psvita" || vitaTitle != null) {
                        val prefix = if (rootPlatform != null) "" else firstSeg
                        val sfo = sfoBeside(relPath)
                        val titleId = sfo?.titleId ?: vitaTitle
                        val title = sfo?.title?.takeIf { it.isNotBlank() } ?: titleId ?: doc.name
                        val artKey = titleId ?: title
                        entries.add(
                            RomEntry(
                                id = "psvita:${titleId ?: relPath}",
                                name = title,
                                platformId = "psvita",
                                uri = doc.uri,
                                path = StoragePaths.filesystemPath(doc.uri),
                                artUri = LocalMedia.lookupArt(media, prefix, artKey)
                                    ?: LocalMedia.lookupArt(media, prefix, title),
                                screenshotUri = LocalMedia.screenshotUri(
                                    media, prefix, artKey, null,
                                ),
                                logoUri = LocalMedia.lookupLogo(media, prefix, artKey),
                                videoUri = LocalMedia.lookupVideo(videos, prefix, artKey),
                            ),
                        )
                        continue
                    }
                }
                val dot = doc.name.lastIndexOf('.')
                if (dot <= 0 || dot == doc.name.length - 1) continue
                val ext = doc.name.substring(dot + 1).lowercase()
                val platform = rootPlatform ?: platformForFirstSegment(firstSeg) ?: continue
                if (!platform.acceptsExtension(ext)) continue
                // The platform folder path prefix that artwork hangs off:
                // "" for a platform-rooted tree, else the first segment.
                val prefix = if (rootPlatform != null) "" else firstSeg
                val stem = doc.name.substring(0, dot)
                val vitaSfo = if (platform.id == "psvita" && openStream != null &&
                    (ext == "vpk" || ext == "zip")
                ) {
                    runCatching {
                        openStream(doc.uri)?.use { stream ->
                            VitaVpk.paramSfo(stream)?.let { VitaSfo.parse(it) }
                        }
                    }.getOrNull()
                } else {
                    null
                }
                val artStem = vitaSfo?.titleId ?: stem
                val artUri = LocalMedia.lookupArt(media, prefix, artStem)
                    ?: if (artStem != stem) LocalMedia.lookupArt(media, prefix, stem) else null
                val sfoTitle = vitaSfo?.title?.takeIf { it.isNotBlank() }
                val display = when {
                    platform.id == "arcade" -> ArcadeTitles.displayName(stem)
                    sfoTitle != null -> sfoTitle
                    else -> stem
                }
                val entryId = if (platform.id == "psvita") {
                    "psvita:${vitaSfo?.titleId ?: VitaTitles.titleIdIn(relPath) ?: relPath}"
                } else {
                    "${platform.id}:$relPath"
                }
                entries.add(
                    RomEntry(
                        id = entryId,
                        name = display,
                        platformId = platform.id,
                        uri = doc.uri,
                        path = StoragePaths.filesystemPath(doc.uri),
                        artUri = artUri,
                        screenshotUri = LocalMedia.screenshotUri(media, prefix, artStem, artUri),
                        logoUri = LocalMedia.lookupLogo(media, prefix, artStem),
                        videoUri = LocalMedia.lookupVideo(videos, prefix, artStem),
                    ),
                )
            }
            if (sfoByDir.isNotEmpty()) {
                val seenVita = entries.mapNotNull { e ->
                    e.id.takeIf { e.platformId == "psvita" }
                }.toHashSet()
                sfoByDir.forEach folder@{ (dir, sfoDoc) ->
                    val titleId = VitaTitles.titleIdIn(dir) ?: return@folder
                    val id = "psvita:$titleId"
                    if (id in seenVita) return@folder
                    val inVita = rootPlatform?.id == "psvita" ||
                        platformForFirstSegment(dir.substringBefore('/'))?.id == "psvita"
                    if (!inVita) return@folder
                    val prefix =
                        if (rootPlatform != null) "" else sfoDoc.relativePath.substringBefore('/')
                    val info = sfoBeside(sfoDoc.relativePath)
                    val title = info?.title?.takeIf { it.isNotBlank() } ?: titleId
                    entries.add(
                        RomEntry(
                            id = id,
                            name = title,
                            platformId = "psvita",
                            uri = sfoDoc.uri,
                            path = StoragePaths.filesystemPath(sfoDoc.uri),
                            artUri = LocalMedia.lookupArt(media, prefix, titleId)
                                ?: LocalMedia.lookupArt(media, prefix, title),
                            screenshotUri = LocalMedia.screenshotUri(media, prefix, titleId, null),
                            logoUri = LocalMedia.lookupLogo(media, prefix, titleId),
                            videoUri = LocalMedia.lookupVideo(videos, prefix, titleId),
                        ),
                    )
                    seenVita.add(id)
                }
            }
        }
        val sorted = DiscHygiene.preferDiscMasters(sortScannedEntries(entries))
        if (readText == null || gamelistDocs.isEmpty()) return applyFilenameYears(sorted)
        val meta = gamelistDocs.flatMap { doc ->
            val xml = readText(doc.uri) ?: return@flatMap emptyList()
            GamelistMeta.parse(xml)
        }
        if (meta.isEmpty()) return applyFilenameYears(sorted)
        return applyFilenameYears(GamelistMeta.enrichRoms(sorted, meta))
    }

    /** Fill blank years from `(1991)` in the display name. */
    internal fun applyFilenameYears(entries: List<RomEntry>): List<RomEntry> =
        entries.map { rom ->
            if (!rom.year.isNullOrBlank()) rom
            else FilenameMeta.yearFromLabel(rom.name)?.let { rom.copy(year = it) } ?: rom
        }

    /**
     * Scan pre-walked file lists (incremental rescan path: fingerprint walk
     * already produced [docs]). Same matching as [scan].
     */
    fun scanDocs(
        trees: List<Triple<List<DocFile>, String, Boolean>>,
        readText: ((uri: String) -> String?)? = null,
        openStream: ((uri: String) -> java.io.InputStream?)? = null,
    ): List<RomEntry> {
        val fake = trees.map { (docs, rootName, _) ->
            object : DocumentTree {
                override fun walk(): List<DocFile> = docs
            } to rootName
        }
        return scan(fake, readText, openStream)
    }
}
