package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, locale-safe tokenizer for gamepad control hints displayed by [HintBar].
 *
 * Splits hint strings on middle-dot (`·`) separators and classifies button/trigger
 * keycaps, mode prefixes (`Moving - ...`), separators, and action labels without
 * hardcoding any localized prose. Host-tested across all supported locales
 * (`en-US`, `es`, `de`, `th`, `fr`).
 */
object HintBarFormat {

    enum class TokenKind {
        /** Button or hold trigger rendered inside a tactile keycap badge. */
        KEYCAP,
        /** Action prose describing what the preceding keycap does. */
        ACTION,
        /** Mode prefix before `" - "` (e.g. move-mode indicator). */
        PREFIX,
        /** Muted visual delimiter (`·` or `-`). */
        SEPARATOR,
    }

    data class Token(
        val start: Int,
        val end: Int,
        val kind: TokenKind,
    )

    private const val MIDDOT = '\u00B7'
    private const val PREFIX_DELIM = " - "
    private const val MAX_LEADING_TRIGGER_LEN = 12

    /** Universal gamepad button symbols (locale-independent). */
    private val KNOWN_BUTTONS = setOf(
        "A", "B", "X", "Y",
        "L", "R", "L1", "R1", "L2", "R2", "LB", "RB", "LT", "RT",
        "START", "SELECT",
    )

    fun isKnownButton(token: String): Boolean =
        token.uppercase() in KNOWN_BUTTONS

    /**
     * Tokenize [raw] into non-overlapping [Token] ranges strictly within
     * `0..raw.length`. Whitespace outside tokens is preserved in the original string.
     */
    fun tokenize(raw: String): List<Token> {
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<Token>(12)
        val n = raw.length
        var segStart = 0
        var prevStandaloneKeycap = false

        var i = 0
        while (i <= n) {
            if (i == n || raw[i] == MIDDOT) {
                prevStandaloneKeycap = parseSegment(
                    raw = raw,
                    segStart = segStart,
                    segEnd = i,
                    prevStandaloneKeycap = prevStandaloneKeycap,
                    out = out,
                )
                if (i < n) {
                    out += Token(i, i + 1, TokenKind.SEPARATOR)
                }
                segStart = i + 1
            }
            i++
        }
        return out
    }

    private fun parseSegment(
        raw: String,
        segStart: Int,
        segEnd: Int,
        prevStandaloneKeycap: Boolean,
        out: MutableList<Token>,
    ): Boolean {
        val start = trimStartIndex(raw, segStart, segEnd)
        val end = trimEndIndex(raw, start, segEnd)
        if (start >= end) return prevStandaloneKeycap

        // Check for mode-prefix delimiter ("Moving - A drop").
        val prefixDelim = raw.indexOf(PREFIX_DELIM, startIndex = start)
        if (prefixDelim in start until end) {
            val prefixEnd = trimEndIndex(raw, start, prefixDelim)
            if (prefixEnd > start) {
                out += Token(start, prefixEnd, TokenKind.PREFIX)
            }
            out += Token(prefixDelim + 1, prefixDelim + 2, TokenKind.SEPARATOR)
            val tailStart = trimStartIndex(raw, prefixDelim + PREFIX_DELIM.length, end)
            if (tailStart < end) {
                return parseKeyAction(raw, tailStart, end, prevStandaloneKeycap = false, out = out)
            }
            return false
        }

        return parseKeyAction(raw, start, end, prevStandaloneKeycap, out)
    }

    private fun parseKeyAction(
        raw: String,
        start: Int,
        end: Int,
        prevStandaloneKeycap: Boolean,
        out: MutableList<Token>,
    ): Boolean {
        val firstSpace = firstWhitespaceIndex(raw, start, end)
        if (firstSpace < 0) {
            // Single token segment (e.g. "A", "Launch", "B", "Close").
            val slash = raw.indexOf('/', startIndex = start)
            if (slash in (start + 1) until end) {
                val beforeSlash = raw.substring(start, slash)
                if (isKnownButton(beforeSlash)) {
                    out += Token(start, slash, TokenKind.KEYCAP)
                    out += Token(slash, end, TokenKind.ACTION)
                    return true
                }
            }
            val word = raw.substring(start, end)
            if (isKnownButton(word)) {
                out += Token(start, end, TokenKind.KEYCAP)
                return true
            }
            out += Token(start, end, TokenKind.ACTION)
            return false
        }

        val head = raw.substring(start, firstSpace)
        val tailStart = trimStartIndex(raw, firstSpace, end)

        // Compound head with slash (e.g. "Y/กดค้าง ลบ" or "Y/Appui long").
        val headSlash = head.indexOf('/')
        if (headSlash > 0) {
            val beforeSlash = head.substring(0, headSlash)
            if (isKnownButton(beforeSlash)) {
                out += Token(start, start + headSlash, TokenKind.KEYCAP)
                out += Token(start + headSlash, end, TokenKind.ACTION)
                return headSlash == head.length - 1 || tailStart >= end
            }
        }

        // Known gamepad button followed by action or "/ long-press".
        if (isKnownButton(head)) {
            out += Token(start, firstSpace, TokenKind.KEYCAP)
            if (tailStart < end) {
                out += Token(tailStart, end, TokenKind.ACTION)
                // "Y / long-press" acts as a compound key descriptor before "· Remove".
                return raw[tailStart] == '/'
            }
            return true
        }

        // Localized trigger word (e.g. "Hold Move / Remove", "Halten ...", "กดค้าง ..."):
        // only when not immediately following a standalone keycap like "A · <multi-word action>".
        val isShortTriggerWord = !prevStandaloneKeycap &&
            head.length in 1..MAX_LEADING_TRIGGER_LEN &&
            tailStart < end &&
            head.none { it == '/' || it == '-' || it == ':' }
        if (isShortTriggerWord) {
            out += Token(start, firstSpace, TokenKind.KEYCAP)
            out += Token(tailStart, end, TokenKind.ACTION)
            return false
        }

        out += Token(start, end, TokenKind.ACTION)
        return false
    }

    private fun trimStartIndex(s: String, from: Int, to: Int): Int {
        var i = from
        while (i < to && s[i].isWhitespace()) i++
        return i
    }

    private fun trimEndIndex(s: String, from: Int, to: Int): Int {
        var i = to
        while (i > from && s[i - 1].isWhitespace()) i--
        return i
    }

    private fun firstWhitespaceIndex(s: String, from: Int, to: Int): Int {
        for (i in from until to) {
            if (s[i].isWhitespace()) return i
        }
        return -1
    }
}
