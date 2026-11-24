package com.visorcraft.ghostgalleon.rom

import java.util.Locale

/**
 * Pure offline helpers for ES-DE / EmulationStation-style `gamelist.xml`.
 *
 * Parses game entries from XML text (path/stem, name, description, optional
 * image path tags), matches a ROM filename or stem to an entry, and builds
 * conventional media-folder relative paths for boxfront/image/screenshot.
 *
 * No Android types — host-testable on the JVM. RomScanner already discovers
 * local art via sibling `images/`/`media/`/`art/`/`boxfront/`/`covers/`
 * folders (stem + common suffixes); this module is the metadata layer that
 * can later attach titles/descriptions when a gamelist is present.
 */
data class GamelistEntry(
    /** Raw `<path>` text from the XML (e.g. `./Chrono Trigger.smc`). */
    val path: String,
    /** Filename stem derived from [path] (no directory, no extension). */
    val stem: String,
    /** Display title from `<name>` / `<title>`, or null when absent. */
    val name: String?,
    /** Blurb from `<desc>` / `<description>`, or null when absent. */
    val description: String?,
    /** Optional relative/absolute art path from `<image>`, or null. */
    val image: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val developer: String? = null,
    val rating: String? = null,
)

/** Conventional ES-DE-ish media subfolders under a platform's `media/`. */
enum class MediaFolder(val folderName: String) {
    BOXFRONT("boxfront"),
    IMAGE("images"),
    SCREENSHOT("screenshots"),
}

object GamelistMeta {

    private val GAME_BLOCK = Regex(
        """<game\b[^>]*>(.*?)</game>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG = Regex(
        """<(\w+)\b[^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val CDATA = Regex(
        """<!\[CDATA\[(.*?)]]>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val RESIDUAL_TAG = Regex("""<[^>]+>""")

    /**
     * Parse every `<game>` block in [xml]. Malformed or empty input yields
     * an empty list (never throws). Games without a usable `<path>` are
     * skipped; missing name/desc simply leave those fields null.
     */
    fun parse(xml: String): List<GamelistEntry> {
        if (xml.isBlank()) return emptyList()
        return GAME_BLOCK.findAll(xml).mapNotNull { match ->
            val body = match.groupValues[1]
            val tags = linkedMapOf<String, String>()
            TAG.findAll(body).forEach { tag ->
                val key = tag.groupValues[1].lowercase()
                // First occurrence wins (mirrors typical gamelist tools).
                if (key !in tags) {
                    tags[key] = decodeXmlText(tag.groupValues[2])
                }
            }
            val path = tags["path"]?.trim().orEmpty()
            if (path.isEmpty()) return@mapNotNull null
            val stem = stemOf(path)
            if (stem.isEmpty()) return@mapNotNull null
            GamelistEntry(
                path = path,
                stem = stem,
                name = tags["name"]?.ifBlank { null }
                    ?: tags["title"]?.ifBlank { null },
                description = tags["desc"]?.ifBlank { null }
                    ?: tags["description"]?.ifBlank { null },
                image = tags["image"]?.ifBlank { null },
                year = tags["releasedate"]?.take(4)?.ifBlank { null }
                    ?: tags["year"]?.ifBlank { null },
                genre = tags["genre"]?.ifBlank { null },
                developer = tags["developer"]?.ifBlank { null }
                    ?: tags["publisher"]?.ifBlank { null },
                rating = tags["rating"]?.ifBlank { null }
                    ?: tags["players"]?.ifBlank { null },
            )
        }.toList()
    }

    /**
     * Case-insensitive stem match of [romFilenameOrStem] against [entries].
     * Accepts a bare stem (`Chrono Trigger`), a filename
     * (`Chrono Trigger.smc`), or a path (`./hacks/smw.smc`). Returns the
     * first matching entry, or null.
     */
    fun matchByStem(
        entries: List<GamelistEntry>,
        romFilenameOrStem: String,
    ): GamelistEntry? {
        val target = stemOf(romFilenameOrStem).lowercase()
        if (target.isEmpty()) return null
        return entries.firstOrNull { it.stem.equals(target, ignoreCase = true) }
    }

    /**
     * Filename stem of a path or name: last path segment, extension
     * stripped. `"snes/hacks/smw.smc"` → `"smw"`; `"Tetris"` → `"Tetris"`.
     * Leading `./` is ignored. Empty / trailing-dot-only inputs → `""`.
     */
    fun stemOf(pathOrFilename: String): String {
        val trimmed = pathOrFilename.trim().removePrefix("./").trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val file = trimmed.substringAfterLast('/').substringAfterLast('\\')
        if (file.isEmpty() || file == "." || file == "..") return ""
        val dot = file.lastIndexOf('.')
        // No extension, or leading-dot hidden file with no stem beyond the
        // dot (e.g. ".smc") — keep the whole name as the stem.
        if (dot <= 0) return file
        return file.substring(0, dot)
    }

    /**
     * Relative media path for [stem] under the conventional [folder]:
     * `"media/boxfront/Chrono Trigger.png"`. Extension defaults to `png`
     * (case preserved as given). Empty stem → empty string.
     */
    fun mediaRelativePath(
        stem: String,
        folder: MediaFolder,
        extension: String = "png",
    ): String {
        val clean = stem.trim()
        if (clean.isEmpty()) return ""
        val ext = extension.trimStart('.')
        return "media/${folder.folderName}/$clean.$ext"
    }

    /** Folder key only: [MediaFolder.BOXFRONT] → `"boxfront"`. */
    fun mediaFolderKey(folder: MediaFolder): String = folder.folderName

    /**
     * Apply gamelist titles/descriptions onto [roms] by stem match.
     * When [preferGamelistName] is true and the entry has a name, it replaces
     * the filename stem as [RomEntry.name]. Description is always filled when
     * present and the ROM has none.
     */
    fun enrichRoms(
        roms: List<RomEntry>,
        gamelistEntries: List<GamelistEntry>,
        preferGamelistName: Boolean = true,
    ): List<RomEntry> {
        if (gamelistEntries.isEmpty() || roms.isEmpty()) return roms
        // O(M) index with putIfAbsent preserving exact first-match precedence.
        val byStem = HashMap<String, GamelistEntry>(gamelistEntries.size * 4 / 3 + 1)
        for (entry in gamelistEntries) {
            val key = entry.stem.lowercase(Locale.ROOT)
            if (key.isNotEmpty()) {
                byStem.putIfAbsent(key, entry)
            }
        }
        if (byStem.isEmpty()) return roms
        return roms.map { rom ->
            val target = stemOf(rom.name).lowercase(Locale.ROOT)
            if (target.isEmpty()) return@map rom
            val hit = byStem[target] ?: return@map rom
            rom.copy(
                name = if (preferGamelistName && !hit.name.isNullOrBlank()) {
                    hit.name
                } else {
                    rom.name
                },
                description = hit.description?.takeIf { it.isNotBlank() }
                    ?: rom.description,
                year = hit.year?.takeIf { it.isNotBlank() } ?: rom.year,
                genre = hit.genre?.takeIf { it.isNotBlank() } ?: rom.genre,
                developer = hit.developer?.takeIf { it.isNotBlank() } ?: rom.developer,
                rating = hit.rating?.takeIf { it.isNotBlank() } ?: rom.rating,
            )
        }
    }

    private fun decodeXmlText(raw: String): String {
        val trimmed = raw.trim()
        // Unwrap a top-level CDATA section; content is literal (keep <>&).
        if (trimmed.startsWith("<![CDATA[")) {
            CDATA.matchEntire(trimmed)?.let { return it.groupValues[1].trim() }
        }
        // Plain text: drop residual nested markup, then decode entities.
        // &amp; first so sequences like &amp;lt; become &lt; then <.
        var out = if ('<' in trimmed) RESIDUAL_TAG.replace(trimmed, "") else trimmed
        if ('&' in out) {
            out = out
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
        }
        return out.trim()
    }
}
