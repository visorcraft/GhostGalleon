package com.visorcraft.ghostgalleon.rom

/**
 * Tree walk → RomEntry matching. Pure logic over the DocumentTree
 * abstraction; host-tested with fake trees.
 */
object RomScanner {

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
            val sfoByDir = docs.filter { it.name.equals("param.sfo", ignoreCase = true) }
                .associateBy { it.relativePath.substringBeforeLast('/').lowercase() }
            fun sfoBeside(relPath: String): VitaSfo.Info? {
                if (openStream == null) return null
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
            docs.forEach docs@{ doc ->
                // Dotfiles/junk anywhere in the path: .DS_Store, ._ AppleDouble
                // files, hidden directories.
                if (doc.relativePath.split('/').any { it.startsWith('.') }) return@docs
                if (DiscHygiene.skipPath(doc.relativePath)) return@docs
                if (doc.name.equals("gamelist.xml", ignoreCase = true)) {
                    gamelistDocs.add(doc)
                    return@docs
                }
                val vitaTitle = VitaTitles.titleIdIn(doc.relativePath)
                if (VitaTitles.isEboot(doc.name) &&
                    (rootPlatform?.id == "psvita" || vitaTitle != null)
                ) {
                    val prefix =
                        if (rootPlatform != null) "" else doc.relativePath.substringBefore('/')
                    val sfo = sfoBeside(doc.relativePath)
                    val titleId = sfo?.titleId ?: vitaTitle
                    val title = sfo?.title?.takeIf { it.isNotBlank() } ?: titleId ?: doc.name
                    val artKey = titleId ?: title
                    entries.add(
                        RomEntry(
                            id = "psvita:${titleId ?: doc.relativePath}",
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
                    return@docs
                }
                val dot = doc.name.lastIndexOf('.')
                if (dot <= 0 || dot == doc.name.length - 1) return@docs
                val ext = doc.name.substring(dot + 1).lowercase()
                val platform = rootPlatform
                    ?: Platforms.platformForFolder(doc.relativePath.substringBefore('/'))
                    ?: return@docs
                if (!platform.acceptsExtension(ext)) return@docs
                // The platform folder path prefix that artwork hangs off:
                // "" for a platform-rooted tree, else the first segment.
                val prefix =
                    if (rootPlatform != null) "" else doc.relativePath.substringBefore('/')
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
                    ?: LocalMedia.lookupArt(media, prefix, stem)
                val sfoTitle = vitaSfo?.title?.takeIf { it.isNotBlank() }
                val display = when {
                    platform.id == "arcade" -> ArcadeTitles.displayName(stem)
                    sfoTitle != null -> sfoTitle
                    else -> stem
                }
                val entryId = if (platform.id == "psvita") {
                    "psvita:${vitaSfo?.titleId ?: vitaTitle ?: doc.relativePath}"
                } else {
                    "${platform.id}:${doc.relativePath}"
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
            val seenVita = entries.mapNotNull { e ->
                e.id.takeIf { e.platformId == "psvita" }
            }.toHashSet()
            sfoByDir.forEach folder@{ (dir, sfoDoc) ->
                val titleId = VitaTitles.titleIdIn(dir) ?: return@folder
                val id = "psvita:$titleId"
                if (id in seenVita) return@folder
                val inVita = rootPlatform?.id == "psvita" ||
                    Platforms.platformForFolder(dir.substringBefore('/'))?.id == "psvita"
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
        val sorted = DiscHygiene.preferDiscMasters(
            entries.sortedWith(
                compareBy({ it.platformId }, { it.name.lowercase() }, { it.id }),
            ),
        )
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
