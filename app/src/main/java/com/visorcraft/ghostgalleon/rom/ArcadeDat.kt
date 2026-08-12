package com.visorcraft.ghostgalleon.rom

/**
 * MAME / FBNeo XML DAT or ClrMame Pro text DAT → short-name to
 * description map. Pure; host-tested.
 */
object ArcadeDat {

    private val BLOCK = Regex(
        """<(game|machine)\s+([^>]+)>([\s\S]*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val NAME_ATTR = Regex("""\bname\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val IS_BIOS = Regex("""\bisbios\s*=\s*"yes"""", RegexOption.IGNORE_CASE)
    private val DESC = Regex(
        """<description>\s*([^<]*?)\s*</description>""",
        RegexOption.IGNORE_CASE,
    )

    private val CLR_GAME = Regex(
        """game\s*\(([\s\S]*?)\)\s*(?=game\s*\(|\z)""",
        RegexOption.IGNORE_CASE,
    )
    private val CLR_FIELD = Regex(
        """\b(name|description)\s+"([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val CLR_ROM = Regex(
        """\brom\s*\([^)]*\bname\s+"?([^"\s)]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(xml: String): Map<String, String> {
        if (xml.isBlank()) return emptyMap()
        val fromXml = parseXml(xml)
        if (fromXml.isNotEmpty()) return fromXml
        return parseClrMame(xml)
    }

    private fun parseXml(xml: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        BLOCK.findAll(xml).forEach { m ->
            val attrs = m.groupValues[2]
            if (IS_BIOS.containsMatchIn(attrs)) return@forEach
            val name = NAME_ATTR.find(attrs)?.groupValues?.get(1)
                ?.trim()?.lowercase().orEmpty()
            if (name.isEmpty()) return@forEach
            val desc = DESC.find(m.groupValues[3])?.groupValues?.get(1)
                ?.trim()
                ?.let(::unescape)
                .orEmpty()
            if (desc.isNotEmpty()) out[name] = desc
        }
        return out
    }

    /** ClrMame Pro text DAT (`game ( name "…" rom ( name foo.zip ) )`). */
    fun parseClrMame(text: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        CLR_GAME.findAll(text).forEach { m ->
            val body = m.groupValues[1]
            var title = ""
            var labeled = ""
            CLR_FIELD.findAll(body).forEach { f ->
                val key = f.groupValues[1].lowercase()
                val value = f.groupValues[2].trim()
                if (key == "description" && value.isNotEmpty()) labeled = value
                if (key == "name" && value.isNotEmpty() && title.isEmpty()) title = value
            }
            val rom = CLR_ROM.find(body)?.groupValues?.get(1)?.trim().orEmpty()
            val stem = rom.substringAfterLast('/').substringBeforeLast('.', missingDelimiterValue = "")
                .trim().lowercase()
            val display = labeled.ifEmpty { title }
            val key = if (stem.isNotEmpty() && !stem.contains(' ')) {
                stem
            } else {
                title.trim().lowercase().takeIf { it.isNotEmpty() && !it.contains(' ') }.orEmpty()
            }
            if (key.isNotEmpty() && display.isNotEmpty()) out[key] = display
        }
        return out
    }

    private fun unescape(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
}
