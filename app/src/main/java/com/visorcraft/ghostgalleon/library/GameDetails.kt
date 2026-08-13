package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.localizedListText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.rom.HeroDetail
import com.visorcraft.ghostgalleon.rom.IdentityStack
import com.visorcraft.ghostgalleon.rom.RomIdentity

/** Pure detail-sheet model and translation-safe text. */
object GameDetails {

    enum class Kind { APP, ROM }

    data class Input(
        val title: String,
        val key: String,
        val kind: Kind,
        val platformId: String? = null,
        val genre: String? = null,
        val developer: String? = null,
        val year: String? = null,
        val rating: String? = null,
        val description: String? = null,
        val lastLaunchedMs: Long? = null,
        val playtimeMs: Long = 0L,
        val favorite: Boolean = false,
        val collections: List<String> = emptyList(),
        val nowMs: Long = 0L,
        val identity: RomIdentity? = null,
    )

    data class RelatedOption(
        val platformId: String? = null,
        val genre: String? = null,
        val developer: String? = null,
        val yearDecade: String? = null,
    )

    fun relatedOptionLabel(option: RelatedOption): UiText = when {
        option.platformId != null -> text(R.string.format_platform_badge, option.platformId)
        option.genre != null -> text(R.string.format_genre_badge, option.genre)
        option.developer != null -> text(R.string.format_developer_badge, option.developer)
        option.yearDecade != null -> text(
            R.string.format_decade_badge,
            LibraryBrowse.decadeText(option.yearDecade),
        )
        else -> dynamicText("")
    }

    fun collectionsContaining(
        collections: Map<String, List<String>>,
        key: String,
    ): List<String> {
        val k = key.trim()
        if (k.isEmpty()) return emptyList()
        return collections.entries
            .filter { (_, members) -> k in members }
            .map { it.key }
            .sortedBy { it.lowercase() }
    }

    /** Multi-line details body, resolved by Android only at the UI seam. */
    fun body(input: Input): UiText {
        val lines = mutableListOf<UiText>()
        lines += dynamicText(input.title.trim().ifEmpty { input.key })
        HeroDetail.descriptionText(input.description)?.let { desc ->
            lines += dynamicText("")
            lines += dynamicText(desc)
        }
        lines += dynamicText("")
        lines += text(
            R.string.details_type,
            text(if (input.kind == Kind.ROM) R.string.label_rom else R.string.label_app),
        )
        input.platformId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines += text(R.string.details_platform, it)
        }
        input.year?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines += text(R.string.details_year, it)
        }
        input.genre?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines += text(R.string.details_genre, it)
        }
        input.developer?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines += text(R.string.details_developer, it)
        }
        input.rating?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lines += text(R.string.details_rating, it)
        }
        lines += text(R.string.details_key, input.key)
        appendIdentity(lines, input.identity)
        lines += dynamicText("")
        lines += text(
            R.string.details_last_played,
            SessionMath.formatLastPlayed(input.lastLaunchedMs, input.nowMs)
                ?: text(R.string.label_never),
        )
        lines += text(R.string.details_playtime, SessionMath.formatPlaytime(input.playtimeMs))
        lines += text(
            R.string.details_favorite,
            text(if (input.favorite) R.string.label_yes else R.string.label_no),
        )
        lines += text(
            R.string.details_collections,
            if (input.collections.isEmpty()) {
                text(R.string.glyph_dash)
            } else {
                localizedListText(input.collections.map(::dynamicText))
            },
        )
        return joinText(lines, "\n")
    }

    /** Full content hash when ready (for clipboard); null otherwise. */
    fun copyableHash(identity: RomIdentity?): String? =
        identity?.takeIf { it.ready }?.hash?.takeIf { it.isNotBlank() }

    private fun appendIdentity(lines: MutableList<UiText>, identity: RomIdentity?) {
        if (identity == null) return
        lines += dynamicText("")
        if (!identity.ready) {
            lines += text(R.string.identity_not_ready)
            return
        }
        identity.algo.trim().takeIf { it.isNotEmpty() }?.let { lines += dynamicText(it) }
        identity.hash?.trim()?.takeIf { it.isNotEmpty() }?.let { hash ->
            lines += text(R.string.identity_hash, IdentityStack.shortHash(hash))
        }
        identity.groupId?.trim()?.takeIf { it.isNotEmpty() }?.let { lines += dynamicText(it) }
        identity.discIndex?.let { lines += dynamicText(it.toString()) }
    }

    fun relatedOptions(
        platformId: String? = null,
        genre: String? = null,
        developer: String? = null,
        year: String? = null,
        allowPlatform: Boolean = true,
        allowGenre: Boolean = false,
        allowDeveloper: Boolean = false,
        allowYear: Boolean = false,
        maxGenreTokens: Int = 3,
    ): List<RelatedOption> {
        val out = mutableListOf<RelatedOption>()
        if (allowPlatform) {
            platformId?.trim()?.takeIf { it.isNotEmpty() }?.let { pid ->
                out += RelatedOption(platformId = pid)
            }
        }
        if (allowGenre) {
            val limit = maxGenreTokens.coerceAtLeast(0)
            val seen = linkedSetOf<String>()
            LibraryBrowse.genreTokens(genre).forEach { token ->
                if (out.size >= 20) return@forEach
                val key = token.lowercase()
                if (key in seen || seen.size >= limit) return@forEach
                seen += key
                out += RelatedOption(genre = token)
            }
        }
        if (allowDeveloper) {
            developer?.trim()?.takeIf { it.isNotEmpty() }?.let { dev ->
                out += RelatedOption(developer = dev)
            }
        }
        if (allowYear) {
            LibraryBrowse.yearDecadeOf(year)?.let { decade ->
                out += RelatedOption(yearDecade = decade)
            }
        }
        return out
    }

    fun relatedOptions(
        input: Input,
        allowPlatform: Boolean = true,
        allowGenre: Boolean = false,
        allowDeveloper: Boolean = false,
        allowYear: Boolean = false,
        maxGenreTokens: Int = 3,
    ): List<RelatedOption> = relatedOptions(
        platformId = input.platformId,
        genre = input.genre,
        developer = input.developer,
        year = input.year,
        allowPlatform = allowPlatform,
        allowGenre = allowGenre,
        allowDeveloper = allowDeveloper,
        allowYear = allowYear,
        maxGenreTokens = maxGenreTokens,
    )

    fun toBrowseQuery(
        option: RelatedOption,
        sort: LibraryBrowse.Sort = LibraryBrowse.Sort.DEFAULT,
    ): LibraryBrowse.BrowseQuery = LibraryBrowse.BrowseQuery(
        mode = LibraryBrowse.Mode.ALL,
        platformId = option.platformId?.trim()?.takeIf { it.isNotEmpty() },
        genre = option.genre?.trim()?.takeIf { it.isNotEmpty() },
        developer = option.developer?.trim()?.takeIf { it.isNotEmpty() },
        yearDecade = option.yearDecade?.trim()?.takeIf { it.isNotEmpty() },
        text = "",
        collectionName = null,
        sort = sort,
    )
}
