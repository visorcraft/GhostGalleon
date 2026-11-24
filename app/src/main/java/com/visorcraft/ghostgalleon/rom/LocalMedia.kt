package com.visorcraft.ghostgalleon.rom

/**
 * Pure local media matching for box art and screenshots beside ROM trees
 * (romm / ES-DE conventions). Host-tested; no Android types.
 *
 * Supports flat layouts (`images/stem.png`) and deeper ES-DE-style paths
 * (`media/screenshots/stem.png`, `media/images/stem-image.png`,
 * `boxfront/stem.png`).
 */
object LocalMedia {

    val ART_DIRS = listOf(
        "images", "media", "art", "boxfront", "covers", "screenshots",
        "titlescreens", "wheels", "marquee", "logos", "videos", "video",
    )
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv")
    val VIDEO_DIRS = listOf("videos", "video", "snaps", "movies")

    /** Box-art stem suffixes tried after exact stem match. */
    val ART_SUFFIXES = listOf(
        "", "-image", "_thumb", "-boxart", "-cover", "-poster", "-logo", "-wheel",
    )

    /** Screenshot stem suffixes (exact first, then these). */
    val SCREENSHOT_SUFFIXES = listOf(
        "-screenshot",
        "_screenshot",
        "-ss",
        "_ss",
        "-snap",
        "_snap",
        "-screen",
        "_screen",
        "-title",
        "_title",
    )

    // Nested media subfolders (ES-DE): media/screenshots/x.png
    private val NESTED_MEDIA = setOf(
        "screenshots", "images", "covers", "boxfront", "titlescreens",
        "wheels", "marquee", "logos", "boxart",
    )

    // Precomputed static lookup sets/maps so per-file walks never rebuild Sets/Lists.
    private val ART_DIRS_SET: Set<String> = ART_DIRS.map { it.lowercase() }.toSet()
    private val ART_DIR_RANK: Map<String, Int> = ART_DIRS.withIndex().associate { (i, d) -> d.lowercase() to i }
    private val VIDEO_DIRS_SET: Set<String> = VIDEO_DIRS.map { it.lowercase() }.toSet()
    private val ART_SUFFIXES_NON_EMPTY: List<String> = ART_SUFFIXES.drop(1)
    private val LOGO_SUFFIXES: List<String> = listOf(
        "-logo", "_logo", "-wheel", "_wheel", "-marquee", "_marquee",
    )

    /**
     * Zero-allocation check equivalent to `relativePath.split('/').any { it.startsWith('.') }`.
     * Rejects `.DS_Store`, `._*` AppleDouble files, and hidden dot-directories.
     */
    internal fun hasDotSegment(relativePath: String): Boolean {
        val len = relativePath.length
        var i = 0
        while (i < len) {
            if (relativePath[i] == '.') return true
            val nextSlash = relativePath.indexOf('/', i)
            if (nextSlash < 0) return false
            i = nextSlash + 1
        }
        return false
    }

    /**
     * Index image files under art dirs. [rootIsPlatform] matches
     * [RomScanner] tree layout (platform-rooted vs container grant).
     * Keys are `"$prefix|$lowercaseStem"`.
     */
    fun indexImages(docs: List<DocFile>, rootIsPlatform: Boolean): Map<String, String> {
        data class Candidate(val key: String, val uri: String, val dirRank: Int, val suffixed: Boolean)
        val candidates = ArrayList<Candidate>()
        for (doc in docs) {
            if (hasDotSegment(doc.relativePath)) continue
            val parsed = parseMediaPath(doc.relativePath, rootIsPlatform) ?: continue
            val (prefix, dir, fileName) = parsed
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) continue
            if (fileName.substring(dot + 1).lowercase() !in IMAGE_EXTENSIONS) continue
            val dirRank = ART_DIR_RANK[dir.lowercase()] ?: 50
            val stem = fileName.substring(0, dot).lowercase()
            var suffixed = false
            for (s in ART_SUFFIXES_NON_EMPTY) {
                if (stem.endsWith(s)) {
                    suffixed = true
                    break
                }
            }
            if (!suffixed) {
                for (s in SCREENSHOT_SUFFIXES) {
                    if (stem.endsWith(s)) {
                        suffixed = true
                        break
                    }
                }
            }
            candidates.add(Candidate("$prefix|$stem", doc.uri, dirRank, suffixed))
        }
        if (candidates.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>(candidates.size)
        candidates.sortWith(compareBy({ it.suffixed }, { it.dirRank }))
        for (c in candidates) map.putIfAbsent(c.key, c.uri)
        return map
    }

    /**
     * Parse a relative path into (platformPrefix, mediaDir, fileName).
     * Accepts:
     * - root platform: `images/x.png`, `media/screenshots/x.png`
     * - container: `snes/images/x.png`, `snes/media/screenshots/x.png`
     */
    fun parseMediaPath(
        relativePath: String,
        rootIsPlatform: Boolean,
    ): Triple<String, String, String>? {
        val len = relativePath.length
        var s0Start = 0; var s0End = 0
        var s1Start = 0; var s1End = 0
        var s2Start = 0; var s2End = 0
        var s3Start = 0; var s3End = 0
        var count = 0
        var i = 0
        while (i < len) {
            while (i < len && relativePath[i] == '/') i++
            if (i >= len) break
            val start = i
            while (i < len && relativePath[i] != '/') i++
            when (count) {
                0 -> { s0Start = start; s0End = i }
                1 -> { s1Start = start; s1End = i }
                2 -> { s2Start = start; s2End = i }
                3 -> { s3Start = start; s3End = i }
                else -> return null
            }
            count++
        }
        return if (rootIsPlatform) {
            when (count) {
                2 -> {
                    val dir = relativePath.substring(s0Start, s0End)
                    val dirLower = dir.lowercase()
                    if (dirLower !in ART_DIRS_SET && dirLower !in NESTED_MEDIA) return null
                    Triple("", dir, relativePath.substring(s1Start, s1End))
                }
                3 -> {
                    // media/screenshots/file.png
                    val mid = relativePath.substring(s0Start, s0End).lowercase()
                    val sub = relativePath.substring(s1Start, s1End).lowercase()
                    if (mid == "media" && sub in NESTED_MEDIA) {
                        Triple("", sub, relativePath.substring(s2Start, s2End))
                    } else if (mid in ART_DIRS_SET) {
                        // images/extra/file.png — treat mid as dir
                        Triple("", mid, relativePath.substring(s2Start, s2End))
                    } else {
                        null
                    }
                }
                else -> null
            }
        } else {
            when (count) {
                3 -> {
                    val dir = relativePath.substring(s1Start, s1End)
                    val dirLower = dir.lowercase()
                    if (dirLower !in ART_DIRS_SET && dirLower !in NESTED_MEDIA) return null
                    Triple(
                        relativePath.substring(s0Start, s0End),
                        dir,
                        relativePath.substring(s2Start, s2End),
                    )
                }
                4 -> {
                    // snes/media/screenshots/file.png
                    val mid = relativePath.substring(s1Start, s1End).lowercase()
                    if (mid != "media") return null
                    val sub = relativePath.substring(s2Start, s2End).lowercase()
                    if (sub !in NESTED_MEDIA) return null
                    Triple(
                        relativePath.substring(s0Start, s0End),
                        sub,
                        relativePath.substring(s3Start, s3End),
                    )
                }
                else -> null
            }
        }
    }

    fun lookupArt(index: Map<String, String>, prefix: String, stem: String): String? {
        if (index.isEmpty()) return null
        val base = "$prefix|${stem.lowercase()}"
        index[base]?.let { return it }
        for (suffix in ART_SUFFIXES_NON_EMPTY) {
            index[base + suffix]?.let { return it }
        }
        return null
    }

    fun lookupScreenshot(index: Map<String, String>, prefix: String, stem: String): String? {
        if (index.isEmpty()) return null
        val base = "$prefix|${stem.lowercase()}"
        for (suffix in SCREENSHOT_SUFFIXES) {
            index[base + suffix]?.let { return it }
        }
        return index[base]
    }

    /**
     * Logo / wheel art (titlescreen-style), separate from box art.
     * Prefers `-logo` / `-wheel` suffixes then bare stem in logos/wheels dirs
     * (already merged into the flat index by stem).
     */
    fun lookupLogo(index: Map<String, String>, prefix: String, stem: String): String? {
        if (index.isEmpty()) return null
        val base = "$prefix|${stem.lowercase()}"
        for (suffix in LOGO_SUFFIXES) {
            index[base + suffix]?.let { return it }
        }
        // Bare stem: ES-DE media/logos/Game.png indexes as prefix|game.
        return index[base]
    }

    /**
     * Screenshot URI for [stem], preferring screenshot suffixes. When the
     * only match is the same URI already used as box art, returns null so
     * the hero does not double-show the cover as a "screenshot".
     */
    fun screenshotUri(
        index: Map<String, String>,
        prefix: String,
        stem: String,
        artUri: String?,
    ): String? {
        if (index.isEmpty()) return null
        val base = "$prefix|${stem.lowercase()}"
        for (suffix in SCREENSHOT_SUFFIXES) {
            index[base + suffix]?.let { return it }
        }
        val bare = index[base]
        return if (bare != null && bare != artUri) bare else null
    }

    /**
     * Index video snaps under conventional dirs. Keys `"$prefix|$lowercaseStem"`.
     * Accepts flat `videos/stem.mp4`, `media/videos/stem.mp4`, and platform-
     * prefixed `snes/videos/stem.mp4`.
     */
    fun indexVideos(docs: List<DocFile>, rootIsPlatform: Boolean): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (doc in docs) {
            if (hasDotSegment(doc.relativePath)) continue
            val parsed = parseVideoPath(doc.relativePath, rootIsPlatform) ?: continue
            val (prefix, fileName) = parsed
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) continue
            if (fileName.substring(dot + 1).lowercase() !in VIDEO_EXTENSIONS) continue
            val stem = fileName.substring(0, dot).lowercase()
            map.putIfAbsent("$prefix|$stem", doc.uri)
        }
        return map
    }

    fun lookupVideo(index: Map<String, String>, prefix: String, stem: String): String? =
        if (index.isEmpty()) null else index["$prefix|${stem.lowercase()}"]

    /**
     * Parse video relative path → (platformPrefix, fileName).
     * - root platform: `videos/x.mp4`, `media/videos/x.mp4`
     * - container: `snes/videos/x.mp4`, `snes/media/videos/x.mp4`
     */
    fun parseVideoPath(
        relativePath: String,
        rootIsPlatform: Boolean,
    ): Pair<String, String>? {
        val len = relativePath.length
        var s0Start = 0; var s0End = 0
        var s1Start = 0; var s1End = 0
        var s2Start = 0; var s2End = 0
        var s3Start = 0; var s3End = 0
        var count = 0
        var i = 0
        while (i < len) {
            while (i < len && relativePath[i] == '/') i++
            if (i >= len) break
            val start = i
            while (i < len && relativePath[i] != '/') i++
            when (count) {
                0 -> { s0Start = start; s0End = i }
                1 -> { s1Start = start; s1End = i }
                2 -> { s2Start = start; s2End = i }
                3 -> { s3Start = start; s3End = i }
                else -> return null
            }
            count++
        }
        return if (rootIsPlatform) {
            when (count) {
                2 -> {
                    if (relativePath.substring(s0Start, s0End).lowercase() !in VIDEO_DIRS_SET) return null
                    "" to relativePath.substring(s1Start, s1End)
                }
                3 -> {
                    if (relativePath.substring(s0Start, s0End).lowercase() == "media" &&
                        relativePath.substring(s1Start, s1End).lowercase() in VIDEO_DIRS_SET
                    ) {
                        "" to relativePath.substring(s2Start, s2End)
                    } else {
                        null
                    }
                }
                else -> null
            }
        } else {
            when (count) {
                3 -> {
                    if (relativePath.substring(s1Start, s1End).lowercase() !in VIDEO_DIRS_SET) return null
                    relativePath.substring(s0Start, s0End) to relativePath.substring(s2Start, s2End)
                }
                4 -> {
                    if (relativePath.substring(s1Start, s1End).lowercase() == "media" &&
                        relativePath.substring(s2Start, s2End).lowercase() in VIDEO_DIRS_SET
                    ) {
                        relativePath.substring(s0Start, s0End) to relativePath.substring(s3Start, s3End)
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }
}
