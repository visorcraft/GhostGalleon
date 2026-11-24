package com.visorcraft.ghostgalleon.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Pure descriptor of a candidate file discovered inside a user-selected SAF folder.
 */
data class CustomSoundCandidate(
    val displayName: String,
    val uriString: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long = 0L,
    val isDirectory: Boolean = false,
)

/**
 * Validated custom sound file matched to a [SoundCue] (or custom ambient loop).
 */
data class CustomSoundEntry(
    val displayName: String,
    val uriString: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long = 0L,
)

/**
 * Result of scanning a user's custom sound pack folder.
 *
 * Designed so Settings can display matched cue counts (e.g. via `R.plurals.count_items`)
 * and `SoundEngine` can fall back per-cue to the active procedural pack for unmatched cues.
 */
data class CustomPackScanResult(
    val cueEntries: Map<SoundCue, CustomSoundEntry>,
    val totalMatched: Int = cueEntries.size,
    val skippedOversize: Int = 0,
    val skippedUnsupported: Int = 0,
    val ambientEntry: CustomSoundEntry? = null,
)

/**
 * Result of copying validated custom SAF sound files into app-private cache storage
 * so `SoundPool.load(path, 1)` never holds long-lived SAF file descriptors open.
 */
data class MaterializedCustomPack(
    val scan: CustomPackScanResult,
    val cachedCuePaths: Map<SoundCue, String>,
    val cachedAmbientPath: String? = null,
    val cacheDirPath: String? = null,
)

/**
 * Pure, host-testable filename matcher, format-precedence resolver, and cache-key helper
 * for user-provided SAF sound pack folders. Zero Android runtime dependencies.
 */
object CustomSoundPackMatcher {

    /** Maximum allowed file size for a single UI SFX override (`2 MiB`). */
    const val MAX_SFX_BYTES: Long = 2L * 1024L * 1024L

    /** Maximum allowed file size for a custom ambient loop override (`16 MiB`). */
    const val MAX_AMBIENT_BYTES: Long = 16L * 1024L * 1024L

    /** Supported audio extensions ordered by deterministic quality/latency precedence. */
    val SUPPORTED_EXTENSIONS: List<String> = listOf("wav", "ogg", "flac", "mp3", "m4a")

    private val EXTENSION_RANK: Map<String, Int> =
        SUPPORTED_EXTENSIONS.withIndex().associate { (index, ext) -> ext to index }

    private val SEPARATOR_REGEX = Regex("[\\s\\-_]+")

    /**
     * Forgiving user-friendly stem aliases mapped cleanly to all 24 [SoundCue]s.
     * Exact lowercase [SoundCue.name] matches are always included and win over shorthand aliases
     * when extensions tie.
     */
    val CUE_ALIASES: Map<SoundCue, List<String>> = mapOf(
        SoundCue.NAV_TICK to listOf("nav_tick", "nav", "tick", "cursor", "move", "focus", "click"),
        SoundCue.NAV_EDGE_BUMP to listOf("nav_edge_bump", "edge_bump", "bump", "edge", "wall", "boundary"),
        SoundCue.NAV_PAGE_TURN to listOf("nav_page_turn", "page_turn", "page", "flip", "page_flip"),
        SoundCue.DOCK_ENTER to listOf("dock_enter", "dock", "dock_in"),
        SoundCue.DOCK_EXIT to listOf("dock_exit", "dock_out", "undock"),
        SoundCue.CONFIRM to listOf("confirm", "select", "ok", "accept", "enter"),
        SoundCue.LAUNCH_GAME to listOf("launch_game", "launch", "start", "play", "boot_game"),
        SoundCue.LAUNCH_APP to listOf("launch_app", "app", "open_app"),
        SoundCue.BACK to listOf("back", "cancel", "return", "escape"),
        SoundCue.SWAP_DISPLAY to listOf("swap_display", "swap", "swap_screens", "display_swap"),
        SoundCue.MODE_TOGGLE to listOf("mode_toggle", "mode", "toggle", "toggle_mode"),
        SoundCue.MODAL_OPEN to listOf("modal_open", "open", "dialog_open", "menu_open", "popup_open"),
        SoundCue.MODAL_CLOSE to listOf("modal_close", "close", "dialog_close", "menu_close", "popup_close"),
        SoundCue.FAVORITE_ON to listOf("favorite_on", "fav", "favorite", "fav_on", "star", "star_on"),
        SoundCue.FAVORITE_OFF to listOf("favorite_off", "unfav", "fav_off", "unfavorite", "star_off"),
        SoundCue.MOVE_PICKUP to listOf("move_pickup", "pickup", "pick_up", "grab"),
        SoundCue.MOVE_SWAP to listOf("move_swap", "reorder", "tile_swap"),
        SoundCue.MOVE_DROP to listOf("move_drop", "drop", "place", "put"),
        SoundCue.DESTRUCTIVE to listOf("destructive", "delete", "remove", "trash", "clear"),
        SoundCue.FILTER_SELECT to listOf("filter_select", "filter", "chip", "tab"),
        SoundCue.ERROR_DENY to listOf("error_deny", "error", "deny", "invalid", "nope", "fail"),
        SoundCue.BOOT_CHIME to listOf("boot_chime", "boot", "startup", "intro", "welcome"),
        SoundCue.RA_ACHIEVEMENT to listOf("ra_achievement", "achievement", "ra", "trophy", "unlock"),
        SoundCue.SETUP_COMPLETE to listOf("setup_complete", "setup", "complete", "finish", "done"),
    )

    /** Accepted filename stems for an optional custom ambient loop file. */
    val AMBIENT_ALIASES: Set<String> = setOf("ambient", "ambience", "bgm", "bg_music", "loop", "background")

    private val STEM_TO_CUE: Map<String, SoundCue> = buildMap {
        for (cue in SoundCue.entries) {
            put(cue.name.lowercase(Locale.ROOT), cue)
        }
        for ((cue, aliases) in CUE_ALIASES) {
            for (alias in aliases) {
                putIfAbsent(alias, cue)
            }
        }
    }

    /** True if [displayName] is blank, a hidden dotfile (e.g. `._nav.wav`, `.DS_Store`), or contains path/control chars. */
    fun isHiddenOrInvalidName(displayName: String): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.startsWith(".")) return true
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) return true
        if (trimmed.any { it.code < 0x20 }) return true
        return false
    }

    /** Extracts a lowercase supported extension (without leading dot), or `null` if unsupported. */
    fun extractSupportedExtension(displayName: String): String? {
        if (isHiddenOrInvalidName(displayName)) return null
        val trimmed = displayName.trim()
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0 || dot >= trimmed.lastIndex) return null
        val ext = trimmed.substring(dot + 1).lowercase(Locale.ROOT)
        return if (ext in EXTENSION_RANK) ext else null
    }

    /** Normalizes the filename stem (strip extension, lowercase, collapse spaces/hyphens/underscores to `_`). */
    fun normalizeStem(displayName: String): String {
        val trimmed = displayName.trim()
        val dot = trimmed.lastIndexOf('.')
        val rawStem = if (dot > 0) trimmed.substring(0, dot) else trimmed
        return rawStem
            .trim()
            .lowercase(Locale.ROOT)
            .replace(SEPARATOR_REGEX, "_")
            .trim('_')
    }

    /** Matches [displayName] to a [SoundCue], or `null` if hidden, unsupported extension, or unrecognized name. */
    fun matchCue(displayName: String): SoundCue? {
        if (extractSupportedExtension(displayName) == null) return null
        val stem = normalizeStem(displayName)
        if (stem.isEmpty()) return null
        return STEM_TO_CUE[stem]
    }

    /** True if [displayName] is a supported audio file matching an ambient loop alias. */
    fun matchesAmbient(displayName: String): Boolean {
        if (extractSupportedExtension(displayName) == null) return false
        return normalizeStem(displayName) in AMBIENT_ALIASES
    }

    /** Returns deterministic format priority (`0` = `.wav` highest, `Int.MAX_VALUE` = unsupported). */
    fun extensionPriority(extension: String): Int =
        EXTENSION_RANK[extension.lowercase(Locale.ROOT)] ?: Int.MAX_VALUE

    /**
     * Scans [candidates], rejecting hidden files, unsupported formats, unrecognized names,
     * and oversize files, while deterministically breaking ties via extension precedence
     * (`.wav > .ogg > .flac > .mp3 > .m4a`), canonical cue name preference, and filename order.
     */
    fun scanCandidates(candidates: List<CustomSoundCandidate>): CustomPackScanResult {
        val chosenCues = linkedMapOf<SoundCue, RankedEntry>()
        var chosenAmbient: RankedEntry? = null
        var skippedOversize = 0
        var skippedUnsupported = 0

        for (candidate in candidates) {
            if (candidate.isDirectory) continue
            val name = candidate.displayName.trim()
            val ext = extractSupportedExtension(name)
            if (ext == null) {
                skippedUnsupported++
                continue
            }

            val stem = normalizeStem(name)
            val cue = STEM_TO_CUE[stem]
            val isAmbient = cue == null && stem in AMBIENT_ALIASES

            if (cue == null && !isAmbient) {
                skippedUnsupported++
                continue
            }

            val maxBytes = if (isAmbient) MAX_AMBIENT_BYTES else MAX_SFX_BYTES
            if (candidate.sizeBytes > maxBytes) {
                skippedOversize++
                continue
            }
            if (candidate.sizeBytes <= 0L) {
                skippedUnsupported++
                continue
            }

            val entry = CustomSoundEntry(
                displayName = name,
                uriString = candidate.uriString,
                extension = ext,
                sizeBytes = candidate.sizeBytes,
                lastModifiedMs = candidate.lastModifiedMs,
            )
            val isCanonical = if (cue != null) {
                stem == cue.name.lowercase(Locale.ROOT)
            } else {
                stem == "ambient"
            }
            val ranked = RankedEntry(
                entry = entry,
                extRank = extensionPriority(ext),
                isCanonical = isCanonical,
                sortName = name.lowercase(Locale.ROOT),
            )

            if (cue != null) {
                val existing = chosenCues[cue]
                if (existing == null || ranked.beats(existing)) {
                    chosenCues[cue] = ranked
                }
            } else {
                val existing = chosenAmbient
                if (existing == null || ranked.beats(existing)) {
                    chosenAmbient = ranked
                }
            }
        }

        // Preserve canonical SoundCue enum order in output map for determinism
        val ordered = linkedMapOf<SoundCue, CustomSoundEntry>()
        for (cue in SoundCue.entries) {
            chosenCues[cue]?.let { ordered[cue] = it.entry }
        }
        return CustomPackScanResult(
            cueEntries = ordered,
            totalMatched = ordered.size,
            skippedOversize = skippedOversize,
            skippedUnsupported = skippedUnsupported,
            ambientEntry = chosenAmbient?.entry,
        )
    }

    /**
     * Merges materialized custom cue paths over procedural fallback paths so every missing
     * cue gracefully falls back to the active built-in sound pack.
     */
    fun resolveWithFallback(
        customPaths: Map<SoundCue, String>,
        fallbackPaths: Map<SoundCue, String>,
    ): Map<SoundCue, String> {
        val merged = linkedMapOf<SoundCue, String>()
        for (cue in SoundCue.entries) {
            val path = customPaths[cue]?.takeIf { it.isNotBlank() }
                ?: fallbackPaths[cue]?.takeIf { it.isNotBlank() }
            if (path != null) {
                merged[cue] = path
            }
        }
        return merged
    }

    /** Deterministic, path-safe cache subdirectory name (`custom_<16-hex-sha256>`) for [treeUri]. */
    fun cacheFolderName(treeUri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(treeUri.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(16)
        for (i in 0 until 8) {
            val b = digest[i].toInt() and 0xFF
            hex.append(HEX_CHARS[b ushr 4])
            hex.append(HEX_CHARS[b and 0x0F])
        }
        return "$CUSTOM_DIR_PREFIX$hex"
    }

    /** Deterministic cached filename for a cue entry encoding size + mtime for automatic invalidation. */
    fun cachedCueFileName(cue: SoundCue, entry: CustomSoundEntry): String {
        val safeExt = extractSupportedExtension("a.${entry.extension}") ?: "wav"
        val safeMtime = entry.lastModifiedMs.coerceAtLeast(0L)
        return "${cue.name.lowercase(Locale.ROOT)}_${entry.sizeBytes}_${safeMtime}.$safeExt"
    }

    /** Deterministic cached filename for an optional custom ambient file. */
    fun cachedAmbientFileName(entry: CustomSoundEntry): String {
        val safeExt = extractSupportedExtension("a.${entry.extension}") ?: "ogg"
        val safeMtime = entry.lastModifiedMs.coerceAtLeast(0L)
        return "ambient_${entry.sizeBytes}_${safeMtime}.$safeExt"
    }

    internal const val CUSTOM_DIR_PREFIX: String = "custom_"
    private const val HEX_CHARS = "0123456789abcdef"

    private data class RankedEntry(
        val entry: CustomSoundEntry,
        val extRank: Int,
        val isCanonical: Boolean,
        val sortName: String,
    ) {
        fun beats(other: RankedEntry): Boolean {
            if (extRank != other.extRank) return extRank < other.extRank
            if (isCanonical != other.isCanonical) return isCanonical
            return sortName < other.sortName
        }
    }
}

/**
 * Android Storage Access Framework (`DocumentFile`) scanner and local-cache materializer
 * for user-selected custom sound pack folders.
 *
 * All public methods perform SAF I/O and MUST be called from a background thread (e.g.
 * `SoundEngine`'s `AUDIO_SYNTH` executor), NEVER from the UI thread.
 */
object CustomSoundPackLoader {

    const val CACHE_ROOT_SUBDIR: String = "gg_sfx"

    /** Upper cap on directory entries inspected so accidentally picking huge folders stays fast. */
    const val MAX_SCAN_FILES: Int = 256

    private const val COPY_BUFFER_BYTES: Int = 16 * 1024

    private val PACK_CHILD_PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    /**
     * Scans the SAF tree at [treeUriString] without copying any audio bytes.
     * Uses a single [DocumentsContract] projection cursor (1 Binder IPC instead
     * of 5 IPCs/file via `DocumentFile`), falling back to `DocumentFile` only
     * if the provider returns a null cursor. Safe against blank, malformed,
     * revoked, or inaccessible URIs.
     */
    fun scanTree(context: Context, treeUriString: String?): CustomPackScanResult {
        val rawUri = treeUriString?.trim().orEmpty()
        if (rawUri.isEmpty()) {
            return CustomPackScanResult(cueEntries = emptyMap(), totalMatched = 0)
        }
        val treeUri = runCatching { Uri.parse(rawUri) }.getOrNull()
            ?: return CustomPackScanResult(cueEntries = emptyMap(), totalMatched = 0)

        val projectionCandidates = scanTreeProjection(context, treeUri)
        if (projectionCandidates != null) {
            return CustomSoundPackMatcher.scanCandidates(projectionCandidates)
        }

        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return CustomPackScanResult(cueEntries = emptyMap(), totalMatched = 0)

        val readableDir = runCatching { root.exists() && root.isDirectory && root.canRead() }.getOrDefault(false)
        if (!readableDir) {
            return CustomPackScanResult(cueEntries = emptyMap(), totalMatched = 0)
        }

        val files = runCatching { root.listFiles() }.getOrNull().orEmpty()
        val limit = minOf(files.size, MAX_SCAN_FILES)
        val candidates = ArrayList<CustomSoundCandidate>(limit)
        for (i in 0 until limit) {
            val doc = files[i]
            val candidate = runCatching {
                CustomSoundCandidate(
                    displayName = doc.name.orEmpty(),
                    uriString = doc.uri.toString(),
                    sizeBytes = doc.length(),
                    lastModifiedMs = doc.lastModified(),
                    isDirectory = doc.isDirectory,
                )
            }.getOrNull()
            if (candidate != null) {
                candidates.add(candidate)
            }
        }
        return CustomSoundPackMatcher.scanCandidates(candidates)
    }

    private fun scanTreeProjection(context: Context, treeUri: Uri): List<CustomSoundCandidate>? {
        val rootDocId = runCatching {
            if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
        }.getOrNull() ?: return null

        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        }.getOrNull() ?: return null

        val cursor = runCatching {
            context.contentResolver.query(childrenUri, PACK_CHILD_PROJECTION, null, null, null)
        }.getOrNull() ?: return null

        return runCatching {
            cursor.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val mtimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idIdx < 0 || nameIdx < 0) return@runCatching null
                val candidates = ArrayList<CustomSoundCandidate>(minOf(c.count.coerceAtLeast(0), MAX_SCAN_FILES))
                while (candidates.size < MAX_SCAN_FILES && c.moveToNext()) {
                    val docId = if (!c.isNull(idIdx)) c.getString(idIdx) else null
                    if (docId.isNullOrEmpty()) continue
                    val displayName = if (!c.isNull(nameIdx)) c.getString(nameIdx).orEmpty() else ""
                    val mime = if (mimeIdx >= 0 && !c.isNull(mimeIdx)) c.getString(mimeIdx) else null
                    val sizeBytes = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                    val lastModifiedMs = if (mtimeIdx >= 0 && !c.isNull(mtimeIdx)) c.getLong(mtimeIdx) else 0L
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    candidates.add(
                        CustomSoundCandidate(
                            displayName = displayName,
                            uriString = childUri.toString(),
                            sizeBytes = sizeBytes,
                            lastModifiedMs = lastModifiedMs,
                            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        ),
                    )
                }
                candidates
            }
        }.getOrNull()
    }

    /**
     * Scans [treeUriString] and copies validated custom audio files into
     * `cacheDir/gg_sfx/custom_<hash>/` so `SoundPool.load(path, 1)` can load local files
     * reliably without holding open SAF file descriptors.
     *
     * Any individual cue that fails to read or exceeds [CustomSoundPackMatcher.MAX_SFX_BYTES]
     * during streaming is skipped cleanly so the caller falls back per-cue to procedural audio.
     */
    fun materializeToCache(
        context: Context,
        treeUriString: String?,
        includeAmbient: Boolean = false,
    ): MaterializedCustomPack {
        val rawUri = treeUriString?.trim().orEmpty()
        if (rawUri.isEmpty()) {
            return MaterializedCustomPack(
                scan = CustomPackScanResult(cueEntries = emptyMap(), totalMatched = 0),
                cachedCuePaths = emptyMap(),
            )
        }

        val scan = scanTree(context, rawUri)
        if (scan.cueEntries.isEmpty() && (!includeAmbient || scan.ambientEntry == null)) {
            clearCache(context)
            return MaterializedCustomPack(scan = scan, cachedCuePaths = emptyMap())
        }

        val cacheRoot = File(context.cacheDir, CACHE_ROOT_SUBDIR)
        val folderName = CustomSoundPackMatcher.cacheFolderName(rawUri)
        val packDir = File(cacheRoot, folderName)
        if (!packDir.exists() && !packDir.mkdirs()) {
            return MaterializedCustomPack(scan = scan, cachedCuePaths = emptyMap())
        }

        pruneOtherCustomFolders(cacheRoot, keepFolderName = folderName)

        val expectedNames = HashSet<String>()
        val cachedCuePaths = linkedMapOf<SoundCue, String>()

        for ((cue, entry) in scan.cueEntries) {
            val fileName = CustomSoundPackMatcher.cachedCueFileName(cue, entry)
            val target = File(packDir, fileName)
            val ready = ensureCachedFile(
                context = context,
                uriString = entry.uriString,
                target = target,
                maxBytes = CustomSoundPackMatcher.MAX_SFX_BYTES,
            )
            if (ready) {
                expectedNames.add(fileName)
                cachedCuePaths[cue] = target.absolutePath
            }
        }

        var cachedAmbientPath: String? = null
        val ambientEntry = scan.ambientEntry
        if (includeAmbient && ambientEntry != null) {
            val fileName = CustomSoundPackMatcher.cachedAmbientFileName(ambientEntry)
            val target = File(packDir, fileName)
            val ready = ensureCachedFile(
                context = context,
                uriString = ambientEntry.uriString,
                target = target,
                maxBytes = CustomSoundPackMatcher.MAX_AMBIENT_BYTES,
            )
            if (ready) {
                expectedNames.add(fileName)
                cachedAmbientPath = target.absolutePath
            }
        }

        pruneStaleFilesInDir(packDir, expectedNames)

        return MaterializedCustomPack(
            scan = scan,
            cachedCuePaths = cachedCuePaths,
            cachedAmbientPath = cachedAmbientPath,
            cacheDirPath = packDir.absolutePath,
        )
    }

    /** Deletes all cached `custom_*` sound pack directories under `cacheDir/gg_sfx/`. */
    fun clearCache(context: Context) {
        val cacheRoot = File(context.cacheDir, CACHE_ROOT_SUBDIR)
        val children = runCatching { cacheRoot.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (child.isDirectory && child.name.startsWith(CustomSoundPackMatcher.CUSTOM_DIR_PREFIX)) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun ensureCachedFile(
        context: Context,
        uriString: String,
        target: File,
        maxBytes: Long,
    ): Boolean {
        if (target.isFile && target.length() in 1L..maxBytes) {
            return true
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val tmp = File(target.parentFile, "${target.name}.tmp")
        var copiedBytes = 0L
        val success = runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
            input.use { stream ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        copiedBytes += read
                        if (copiedBytes > maxBytes) {
                            return@runCatching false
                        }
                        out.write(buf, 0, read)
                    }
                    out.flush()
                }
            }
            if (copiedBytes <= 0L) return@runCatching false
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }.getOrDefault(false)

        if (!success) {
            runCatching { tmp.delete() }
        }
        return success && target.isFile
    }

    private fun pruneOtherCustomFolders(cacheRoot: File, keepFolderName: String) {
        val children = runCatching { cacheRoot.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (child.isDirectory &&
                child.name.startsWith(CustomSoundPackMatcher.CUSTOM_DIR_PREFIX) &&
                child.name != keepFolderName
            ) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun pruneStaleFilesInDir(dir: File, keepNames: Set<String>) {
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in files) {
            if (f.name !in keepNames) {
                runCatching { f.delete() }
            }
        }
    }
}
