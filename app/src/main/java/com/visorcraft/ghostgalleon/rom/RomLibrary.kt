package com.visorcraft.ghostgalleon.rom

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import com.visorcraft.ghostgalleon.settings.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.util.concurrent.Executors

/**
 * Persistent ROM index: a JSON array in filesDir/rom_library.json, written
 * atomically (tmp + rename) like SettingsStore. Tree fingerprints for
 * incremental rescan live beside the library as rom_tree_fingerprints.json.
 */
class RomLibrary(private val file: File) {

    private val fingerprintFile: File
        get() = File(file.parentFile, "rom_tree_fingerprints.json")

    private val mtimeFile: File
        get() = File(file.parentFile, "rom_tree_mtimes.json")

    /** Outcome of a rescan, so the settings row can toast honestly. */
    sealed class RescanResult {
        /** Fresh index; entries of unreadable/clean trees were retained from
         *  the previous library and [entries] has already been persisted. */
        data class Success(
            val entries: List<RomEntry>,
            /** Trees skipped because their fingerprint matched (incremental). */
            val skippedCleanTrees: Int = 0,
            /** Granted trees that were unreadable this pass (prior entries retained). */
            val retainedUnreadableTrees: Int = 0,
            /** Trees fully walked / re-matched this pass. */
            val scannedTrees: Int = 0,
            /** Granted tree count at start of this pass. */
            val totalTrees: Int = 0,
        ) : RescanResult()

        /** Every granted tree was unreadable (card ejected, provider
         *  failure); the stored library was left untouched. */
        data object Unreadable : RescanResult()
    }

    private val ioLock = Any()

    fun load(): List<RomEntry> = synchronized(ioLock) {
        if (!file.exists()) return emptyList()
        return try {
            val loaded = BufferedInputStream(FileInputStream(file), STREAM_BUF_SIZE).use { input ->
                InputStreamReader(input, Charsets.UTF_8).use { reader ->
                    readEntriesStreaming(reader)
                }
            }
            // Recompute dedupe on load so a library stored by an older
            // build gets its flags without needing a rescan.
            SwitchDedupe.apply(loaded)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(entries: List<RomEntry>) {
        synchronized(ioLock) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            // Stream compact JSON directly to disk (zero DOM JSONArray/JSONObject
            // heap spikes and null optional keys omitted).
            BufferedWriter(
                OutputStreamWriter(
                    BufferedOutputStream(FileOutputStream(tmp), STREAM_BUF_SIZE),
                    Charsets.UTF_8,
                ),
                STREAM_BUF_SIZE,
            ).use { writer ->
                writeEntriesStreaming(entries, writer)
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun loadFingerprints(): Map<String, String> {
        if (!fingerprintFile.exists()) return emptyMap()
        return try {
            val o = JSONObject(fingerprintFile.readText())
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun loadMtimes(): Map<String, Long> {
        if (!mtimeFile.exists()) return emptyMap()
        return try {
            val o = JSONObject(mtimeFile.readText())
            o.keys().asSequence().associateWith { o.optLong(it, 0L) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveMtimes(map: Map<String, Long>) {
        mtimeFile.parentFile?.mkdirs()
        val tmp = File(mtimeFile.parentFile, mtimeFile.name + ".tmp")
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        tmp.writeText(o.toString())
        if (!tmp.renameTo(mtimeFile)) {
            tmp.copyTo(mtimeFile, overwrite = true)
            tmp.delete()
        }
    }

    fun saveFingerprints(map: Map<String, String>) {
        fingerprintFile.parentFile?.mkdirs()
        val tmp = File(fingerprintFile.parentFile, fingerprintFile.name + ".tmp")
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        tmp.writeText(o.toString())
        if (!tmp.renameTo(fingerprintFile)) {
            tmp.copyTo(fingerprintFile, overwrite = true)
            tmp.delete()
        }
    }

    /**
     * SAF-walk every granted tree off the main thread, persist the result
     * and fingerprints, and invoke [onDone] on the main thread. Unreadable
     * trees are skipped with their prior entries retained; clean trees
     * (matching fingerprint) are skipped unless [force] is true. When every
     * granted tree is unreadable the stored library is left untouched.
     */
    fun rescan(
        context: Context,
        settings: Settings,
        force: Boolean = false,
        onProgress: ((doneTrees: Int, totalTrees: Int) -> Unit)? = null,
        prior: List<RomEntry>? = null,
        onDone: (RescanResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        SCAN_EXECUTOR.execute {
            val result = try {
                val priorFp = loadFingerprints()
                val priorMtimes = loadMtimes()
                val nextMtimes = priorMtimes.toMutableMap()
                val total = settings.romTreeUris.size
                val main = Handler(Looper.getMainLooper())
                var done = 0
                main.post { onProgress?.invoke(0, total) }
                val (scanResult, newFp) = rescanBlockingWithFingerprints(
                    treeUris = settings.romTreeUris,
                    prior = prior ?: load(),
                    isReadable = { isTreeReadable(appContext, it) },
                    treeFor = { uriString ->
                        SafDocumentTree(appContext, Uri.parse(uriString)) to
                            (StoragePaths.treeRootName(uriString) ?: "")
                    },
                    priorFingerprints = priorFp,
                    force = force,
                    skipWalk = { uriString ->
                        val now = DocumentFile.fromTreeUri(
                            appContext, Uri.parse(uriString),
                        )?.lastModified() ?: 0L
                        val skip = TreeSkip.skipWalk(
                            priorMtimes[uriString] ?: 0L, now, force,
                        )
                        if (now > 0L) nextMtimes[uriString] = now
                        skip
                    },
                    // Pure meta fingerprint (count+basenames): one SAF walk, then
                    // skip expensive RomScanner.scan when unchanged. quickMeta
                    // walk-skip is available for injectors that can probe without
                    // a full DocumentTree; production uses a single walk here.
                    fingerprintOf = { TreeFingerprint.ofFilesMeta(it) },
                    readText = { uriString ->
                        runCatching {
                            appContext.contentResolver.openInputStream(Uri.parse(uriString))
                                ?.bufferedReader()
                                ?.use { it.readText() }
                        }.getOrNull()
                    },
                    openStream = { uriString ->
                        runCatching {
                            appContext.contentResolver.openInputStream(Uri.parse(uriString))
                        }.getOrNull()
                    },
                    onTreeProcessed = {
                        done++
                        val d = done
                        main.post { onProgress?.invoke(d, total) }
                    },
                )
                if (scanResult is RescanResult.Success) {
                    save(scanResult.entries)
                    saveFingerprints(newFp)
                    saveMtimes(nextMtimes)
                }
                scanResult
            } catch (_: Exception) {
                // Fail soft: leave library untouched, report unreadable so
                // callers clear in-flight flags and can retry on next resume.
                RescanResult.Unreadable
            }
            Handler(Looper.getMainLooper()).post { onDone(result) }
        }
    }

    companion object {
        private val SCAN_EXECUTOR = Executors.newSingleThreadExecutor()
        private const val STREAM_BUF_SIZE = 65536
        private const val MAX_SKIP_DEPTH = 64
        private val HEX_DIGITS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
        )

        // Internal (not private) so the settings export/import bundle uses
        // the exact same entry codec as the on-disk library file.
        // Null optional fields and default `visibleInUi = true` are omitted
        // (100% backward-compatible with optNullableString / optBoolean).
        internal fun entriesToJson(entries: List<RomEntry>): JSONArray {
            val arr = JSONArray()
            entries.forEach { e ->
                val o = JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("platformId", e.platformId)
                    .put("uri", e.uri)
                e.path?.let { o.put("path", it) }
                e.artUri?.let { o.put("artUri", it) }
                if (!e.visibleInUi) o.put("visibleInUi", false)
                e.description?.let { o.put("description", it) }
                e.screenshotUri?.let { o.put("screenshotUri", it) }
                e.logoUri?.let { o.put("logoUri", it) }
                e.videoUri?.let { o.put("videoUri", it) }
                e.year?.let { o.put("year", it) }
                e.genre?.let { o.put("genre", it) }
                e.developer?.let { o.put("developer", it) }
                e.rating?.let { o.put("rating", it) }
                arr.put(o)
            }
            return arr
        }

        internal fun writeEntriesStreaming(entries: List<RomEntry>, out: Writer) {
            out.write('['.code)
            for (i in entries.indices) {
                if (i > 0) out.write(','.code)
                val e = entries[i]
                out.write("{\"id\":")
                writeJsonString(out, e.id)
                out.write(",\"name\":")
                writeJsonString(out, e.name)
                out.write(",\"platformId\":")
                writeJsonString(out, e.platformId)
                out.write(",\"uri\":")
                writeJsonString(out, e.uri)
                e.path?.let {
                    out.write(",\"path\":")
                    writeJsonString(out, it)
                }
                e.artUri?.let {
                    out.write(",\"artUri\":")
                    writeJsonString(out, it)
                }
                if (!e.visibleInUi) {
                    out.write(",\"visibleInUi\":false")
                }
                e.description?.let {
                    out.write(",\"description\":")
                    writeJsonString(out, it)
                }
                e.screenshotUri?.let {
                    out.write(",\"screenshotUri\":")
                    writeJsonString(out, it)
                }
                e.logoUri?.let {
                    out.write(",\"logoUri\":")
                    writeJsonString(out, it)
                }
                e.videoUri?.let {
                    out.write(",\"videoUri\":")
                    writeJsonString(out, it)
                }
                e.year?.let {
                    out.write(",\"year\":")
                    writeJsonString(out, it)
                }
                e.genre?.let {
                    out.write(",\"genre\":")
                    writeJsonString(out, it)
                }
                e.developer?.let {
                    out.write(",\"developer\":")
                    writeJsonString(out, it)
                }
                e.rating?.let {
                    out.write(",\"rating\":")
                    writeJsonString(out, it)
                }
                out.write('}'.code)
            }
            out.write(']'.code)
        }

        private fun writeJsonString(out: Writer, s: String) {
            out.write('"'.code)
            val len = s.length
            var spanStart = 0
            for (i in 0 until len) {
                val c = s[i]
                if (c >= ' ' && c != '"' && c != '\\') continue
                if (i > spanStart) out.write(s, spanStart, i - spanStart)
                when (c) {
                    '"' -> out.write("\\\"")
                    '\\' -> out.write("\\\\")
                    '\b' -> out.write("\\b")
                    '\u000C' -> out.write("\\f")
                    '\n' -> out.write("\\n")
                    '\r' -> out.write("\\r")
                    '\t' -> out.write("\\t")
                    else -> {
                        val code = c.code
                        out.write("\\u00")
                        out.write(HEX_DIGITS[(code ushr 4) and 0xF].code)
                        out.write(HEX_DIGITS[code and 0xF].code)
                    }
                }
                spanStart = i + 1
            }
            if (spanStart < len) out.write(s, spanStart, len - spanStart)
            out.write('"'.code)
        }

        internal fun readEntriesStreaming(reader: Reader): List<RomEntry> =
            StreamingRomJsonReader(reader).readEntries()

        private class StreamingRomJsonReader(private val reader: Reader) {
            private val buf = CharArray(STREAM_BUF_SIZE)
            private var pos = 0
            private var limit = 0
            private val sb = StringBuilder(256)
            private val platformPool = HashMap<String, String>(32)
            private val metaPool = HashMap<String, String>(256)

            private fun refill(): Boolean {
                pos = 0
                limit = reader.read(buf, 0, buf.size)
                return limit > 0
            }

            private fun nextChar(): Int {
                if (pos >= limit && !refill()) return -1
                return buf[pos++].code
            }

            private fun skipWhitespace(): Int {
                while (true) {
                    if (pos >= limit && !refill()) return -1
                    val c = buf[pos]
                    if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                        pos++
                    } else {
                        return c.code
                    }
                }
            }

            private fun poolPlatform(raw: String): String =
                platformPool.getOrPut(raw) { raw }

            private fun poolMeta(raw: String?): String? {
                if (raw == null || raw.length > 64) return raw
                metaPool[raw]?.let { return it }
                if (metaPool.size < 2048) metaPool[raw] = raw
                return raw
            }

            fun readEntries(): List<RomEntry> {
                var c = skipWhitespace()
                if (c == 0xFEFF) {
                    pos++
                    c = skipWhitespace()
                }
                if (c != '['.code) throw IllegalArgumentException("Expected JSON array")
                pos++ // consume '['

                val next = skipWhitespace()
                if (next == ']'.code) {
                    pos++
                    if (skipWhitespace() != -1) {
                        throw IllegalArgumentException("Trailing content after JSON array")
                    }
                    return emptyList()
                }

                val out = ArrayList<RomEntry>(256)
                while (true) {
                    out.add(readEntry())
                    when (skipWhitespace()) {
                        ','.code -> pos++
                        ']'.code -> {
                            pos++
                            break
                        }
                        else -> throw IllegalArgumentException("Expected ',' or ']' in ROM array")
                    }
                }
                if (skipWhitespace() != -1) {
                    throw IllegalArgumentException("Trailing content after JSON array")
                }
                return out
            }

            private fun readEntry(): RomEntry {
                if (skipWhitespace() != '{'.code) {
                    throw IllegalArgumentException("Expected '{' for RomEntry")
                }
                pos++ // consume '{'

                var id: String? = null
                var name: String? = null
                var platformId: String? = null
                var uri: String? = null
                var path: String? = null
                var artUri: String? = null
                var visibleInUi = true
                var description: String? = null
                var screenshotUri: String? = null
                var logoUri: String? = null
                var videoUri: String? = null
                var year: String? = null
                var genre: String? = null
                var developer: String? = null
                var rating: String? = null

                if (skipWhitespace() != '}'.code) {
                    while (true) {
                        val key = readString()
                        if (skipWhitespace() != ':'.code) {
                            throw IllegalArgumentException("Expected ':' after key $key")
                        }
                        pos++ // consume ':'
                        when (key) {
                            "id" -> id = readRequiredString()
                            "name" -> name = readRequiredString()
                            "platformId" -> platformId = poolPlatform(readRequiredString())
                            "uri" -> uri = readRequiredString()
                            "path" -> path = readNullableString()
                            "artUri" -> artUri = readNullableString()
                            "visibleInUi" -> visibleInUi = readBooleanOrDefault(true)
                            "description" -> description = readNullableString()
                            "screenshotUri" -> screenshotUri = readNullableString()
                            "logoUri" -> logoUri = readNullableString()
                            "videoUri" -> videoUri = readNullableString()
                            "year" -> year = poolMeta(readNullableString())
                            "genre" -> genre = poolMeta(readNullableString())
                            "developer" -> developer = poolMeta(readNullableString())
                            "rating" -> rating = poolMeta(readNullableString())
                            else -> skipValue(0)
                        }
                        when (skipWhitespace()) {
                            ','.code -> pos++
                            '}'.code -> {
                                pos++
                                break
                            }
                            else -> throw IllegalArgumentException("Expected ',' or '}' in RomEntry")
                        }
                    }
                } else {
                    pos++
                }

                if (id == null || name == null || platformId == null || uri == null) {
                    throw IllegalArgumentException("Missing required RomEntry field")
                }
                return RomEntry(
                    id = id,
                    name = name,
                    platformId = platformId,
                    uri = uri,
                    path = path,
                    artUri = artUri,
                    visibleInUi = visibleInUi,
                    description = description,
                    screenshotUri = screenshotUri,
                    logoUri = logoUri,
                    videoUri = videoUri,
                    year = year,
                    genre = genre,
                    developer = developer,
                    rating = rating,
                )
            }

            private fun readRequiredString(): String = readString()

            private fun readNullableString(): String? = when (skipWhitespace()) {
                'n'.code -> {
                    expectLiteral("null")
                    null
                }
                '"'.code -> readString()
                else -> throw IllegalArgumentException("Expected JSON string or null")
            }

            private fun readBooleanOrDefault(default: Boolean): Boolean = when (skipWhitespace()) {
                't'.code -> {
                    expectLiteral("true")
                    true
                }
                'f'.code -> {
                    expectLiteral("false")
                    false
                }
                'n'.code -> {
                    expectLiteral("null")
                    default
                }
                '"'.code -> {
                    val s = readString()
                    when {
                        s.equals("true", ignoreCase = true) -> true
                        s.equals("false", ignoreCase = true) -> false
                        else -> default
                    }
                }
                else -> {
                    skipValue(0)
                    default
                }
            }

            private fun readString(): String {
                if (skipWhitespace() != '"'.code) throw IllegalArgumentException("Expected '\"'")
                pos++ // consume opening quote

                val fastStart = pos
                while (pos < limit) {
                    val c = buf[pos]
                    if (c == '"') {
                        val result = String(buf, fastStart, pos - fastStart)
                        pos++
                        return result
                    }
                    if (c == '\\' || c < ' ') break
                    pos++
                }

                sb.setLength(0)
                if (pos > fastStart) sb.append(buf, fastStart, pos - fastStart)
                while (true) {
                    if (pos >= limit && !refill()) {
                        throw IllegalArgumentException("Unterminated JSON string")
                    }
                    val spanStart = pos
                    var hitEscape = false
                    while (pos < limit) {
                        val c = buf[pos]
                        if (c == '"') {
                            if (pos > spanStart) sb.append(buf, spanStart, pos - spanStart)
                            pos++
                            return sb.toString()
                        }
                        if (c == '\\') {
                            if (pos > spanStart) sb.append(buf, spanStart, pos - spanStart)
                            pos++
                            hitEscape = true
                            break
                        }
                        if (c < ' ') {
                            throw IllegalArgumentException("Unescaped control character in JSON string")
                        }
                        pos++
                    }
                    if (hitEscape) {
                        sb.append(readEscapeChar())
                    } else if (pos > spanStart) {
                        sb.append(buf, spanStart, pos - spanStart)
                    }
                }
            }

            private fun readEscapeChar(): Char = when (val esc = nextChar()) {
                '"'.code -> '"'
                '\\'.code -> '\\'
                '/'.code -> '/'
                'b'.code -> '\b'
                'f'.code -> '\u000C'
                'n'.code -> '\n'
                'r'.code -> '\r'
                't'.code -> '\t'
                'u'.code -> {
                    var value = 0
                    repeat(4) {
                        val ch = nextChar()
                        val digit = when (ch) {
                            in '0'.code..'9'.code -> ch - '0'.code
                            in 'a'.code..'f'.code -> ch - 'a'.code + 10
                            in 'A'.code..'F'.code -> ch - 'A'.code + 10
                            else -> throw IllegalArgumentException("Invalid \\u escape")
                        }
                        value = (value shl 4) or digit
                    }
                    value.toChar()
                }
                else -> throw IllegalArgumentException("Invalid JSON escape: $esc")
            }

            private fun expectLiteral(literal: String) {
                for (i in 0 until literal.length) {
                    if (nextChar() != literal[i].code) {
                        throw IllegalArgumentException("Expected literal '$literal'")
                    }
                }
            }

            private fun skipValue(depth: Int) {
                if (depth > MAX_SKIP_DEPTH) throw IllegalArgumentException("JSON nesting too deep")
                when (val c = skipWhitespace()) {
                    '"'.code -> readString()
                    't'.code -> expectLiteral("true")
                    'f'.code -> expectLiteral("false")
                    'n'.code -> expectLiteral("null")
                    '{'.code -> {
                        pos++
                        if (skipWhitespace() == '}'.code) {
                            pos++
                            return
                        }
                        while (true) {
                            readString()
                            if (skipWhitespace() != ':'.code) {
                                throw IllegalArgumentException("Expected ':' in skipped object")
                            }
                            pos++
                            skipValue(depth + 1)
                            when (skipWhitespace()) {
                                ','.code -> pos++
                                '}'.code -> {
                                    pos++
                                    return
                                }
                                else -> throw IllegalArgumentException("Expected ',' or '}' in skipped object")
                            }
                        }
                    }
                    '['.code -> {
                        pos++
                        if (skipWhitespace() == ']'.code) {
                            pos++
                            return
                        }
                        while (true) {
                            skipValue(depth + 1)
                            when (skipWhitespace()) {
                                ','.code -> pos++
                                ']'.code -> {
                                    pos++
                                    return
                                }
                                else -> throw IllegalArgumentException("Expected ',' or ']' in skipped array")
                            }
                        }
                    }
                    '-'.code, in '0'.code..'9'.code -> {
                        pos++
                        while (true) {
                            if (pos >= limit && !refill()) break
                            val ch = buf[pos]
                            if (ch in '0'..'9' || ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
                                pos++
                            } else {
                                break
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Unexpected JSON token: $c")
                }
            }
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key)

        internal fun parseEntries(arr: JSONArray): List<RomEntry> =
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RomEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    platformId = o.getString("platformId"),
                    uri = o.getString("uri"),
                    path = o.optNullableString("path"),
                    artUri = o.optNullableString("artUri"),
                    visibleInUi = o.optBoolean("visibleInUi", true),
                    description = o.optNullableString("description"),
                    screenshotUri = o.optNullableString("screenshotUri"),
                    logoUri = o.optNullableString("logoUri"),
                    videoUri = o.optNullableString("videoUri"),
                    year = o.optNullableString("year"),
                    genre = o.optNullableString("genre"),
                    developer = o.optNullableString("developer"),
                    rating = o.optNullableString("rating"),
                )
            }

        private fun isTreeReadable(context: Context, treeUri: String): Boolean {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            return doc != null && doc.exists() && doc.canRead()
        }

        /**
         * Guard + merge + incremental fingerprint skip. Pure over injected
         * seams so host tests drive it with fakes. Trees that fail
         * [isReadable] are skipped and prior entries retained. Clean trees
         * (fingerprint match, [force] false) retain prior entries after a
         * fingerprint walk (treeFor still runs to list files; only
         * RomScanner.scan is skipped). When every granted tree is unreadable
         * the scan aborts.
         *
         * Prefer [rescanBlockingWithFingerprints] when the caller needs the
         * updated fingerprint map for persistence.
         */
        internal fun rescanBlocking(
            treeUris: List<String>,
            prior: List<RomEntry>,
            isReadable: (String) -> Boolean,
            treeFor: (String) -> Pair<DocumentTree, String>,
            priorFingerprints: Map<String, String> = emptyMap(),
            force: Boolean = true,
            readText: ((String) -> String?)? = null,
            fingerprintOf: (List<DocFile>) -> String = { TreeFingerprint.ofCombined(it) },
            openStream: ((String) -> java.io.InputStream?)? = null,
        ): RescanResult {
            return rescanBlockingWithFingerprints(
                treeUris, prior, isReadable, treeFor,
                priorFingerprints, force, readText, fingerprintOf,
                openStream = openStream,
            ).first
        }

        /**
         * @param quickMeta optional cheap probe per tree URI (e.g. meta
         *  fingerprint from a lightweight listing). When it matches a pure
         *  meta prior and [force] is false, the full [treeFor] walk is
         *  skipped for that tree. Never invents clean: unknown/mismatch
         *  always falls through to a full walk.
         */
        internal fun rescanBlockingWithFingerprints(
            treeUris: List<String>,
            prior: List<RomEntry>? = null,
            isReadable: (String) -> Boolean,
            treeFor: (String) -> Pair<DocumentTree, String>,
            priorFingerprints: Map<String, String> = emptyMap(),
            force: Boolean = true,
            readText: ((String) -> String?)? = null,
            fingerprintOf: (List<DocFile>) -> String = { TreeFingerprint.ofCombined(it) },
            openStream: ((String) -> java.io.InputStream?)? = null,
            quickMeta: ((String) -> String?)? = null,
            /**
             * When true, skip the full [treeFor] walk and keep prior
             * entries (TreeSkip lastModified probe). Never invents clean
             * on its own — caller must only return true when mtimes are
             * known and equal.
             */
            skipWalk: ((String) -> Boolean)? = null,
            /** Invoked once per granted tree after it is classified (clean/dirty/unreadable). */
            onTreeProcessed: (() -> Unit)? = null,
        ): Pair<RescanResult, Map<String, String>> {
            val priorEntries = prior.orEmpty()
            // Evaluate isReadable ONCE per tree (was 3× per tree = 6 SAF IPCs/tree).
            val readable = ArrayList<String>(treeUris.size)
            val skippedUnreadable = ArrayList<String>()
            for (uri in treeUris) {
                if (isReadable(uri)) readable.add(uri) else skippedUnreadable.add(uri)
            }
            if (treeUris.isNotEmpty() && readable.isEmpty()) {
                treeUris.forEach { onTreeProcessed?.invoke() }
                return RescanResult.Unreadable to priorFingerprints
            }
            val freshTrees = mutableListOf<Pair<DocumentTree, String>>()
            val cleanTrees = mutableListOf<String>()
            val newFingerprints = priorFingerprints.toMutableMap()
            // Drop fingerprints for trees no longer granted.
            val granted = treeUris.toSet()
            newFingerprints.keys.filter { it !in granted }.forEach { newFingerprints.remove(it) }
            skippedUnreadable.forEach {
                // keep prior fp for when card returns
                onTreeProcessed?.invoke()
            }

            for (uri in readable) {
                if (!force && skipWalk?.invoke(uri) == true) {
                    cleanTrees.add(uri)
                    onTreeProcessed?.invoke()
                    continue
                }
                // Cheap meta-only short-circuit: pure `m…` prior matches probe.
                if (!force && quickMeta != null) {
                    val probe = quickMeta(uri)
                    val priorFp = priorFingerprints[uri]
                    if (probe != null && priorFp != null &&
                        priorFp.startsWith("m") && !priorFp.startsWith("c") &&
                        priorFp == probe
                    ) {
                        cleanTrees.add(uri)
                        newFingerprints[uri] = probe
                        onTreeProcessed?.invoke()
                        continue
                    }
                }
                val (tree, rootName) = treeFor(uri)
                val files = tree.walk()
                val fp = fingerprintOf(files)
                if (!TreeFingerprint.isDirty(uri, fp, priorFingerprints, force)) {
                    cleanTrees.add(uri)
                    newFingerprints[uri] = fp
                    onTreeProcessed?.invoke()
                    continue
                }
                // Re-wrap the already-walked listing so scan does not walk again.
                val frozen = object : DocumentTree {
                    override fun walk(): List<DocFile> = files
                }
                freshTrees.add(frozen to rootName)
                newFingerprints[uri] = fp
                onTreeProcessed?.invoke()
            }

            val fresh = if (freshTrees.isEmpty()) {
                emptyList()
            } else {
                RomScanner.scan(freshTrees, readText = readText, openStream = openStream)
            }
            val retainedUris = skippedUnreadable + cleanTrees
            val retained = if (retainedUris.isEmpty()) {
                emptyList()
            } else {
                priorEntries.filter { entry ->
                    retainedUris.any { tree -> entry.uri.startsWith(tree) }
                }
            }
            val merged = RomScanner.sortScannedEntries((fresh + retained).distinctBy { it.id })
            val success = RescanResult.Success(
                entries = SwitchDedupe.apply(merged),
                skippedCleanTrees = cleanTrees.size,
                retainedUnreadableTrees = skippedUnreadable.size,
                scannedTrees = freshTrees.size,
                totalTrees = treeUris.size,
            )
            return success to newFingerprints
        }
    }
}
