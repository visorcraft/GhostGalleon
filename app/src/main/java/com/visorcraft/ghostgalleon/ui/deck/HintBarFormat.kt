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

    /** Auto-size bounds (sp) for [HintBar] so verbose locales fit 540dp Sugar panels. */
    const val MIN_TEXT_SP: Int = 9
    const val MAX_TEXT_SP: Int = 13

    /** Symmetric top/bottom padding (dp) for [HintBar]. */
    const val BAR_PAD_VERTICAL_DP: Int = 6

    /** Subtle wallpaper-legibility drop shadow tokens for [HintBar] prose. */
    const val TEXT_SHADOW_COLOR: Int = 0xB3000000.toInt()
    const val TEXT_SHADOW_RADIUS_DP: Float = 1.5f
    const val TEXT_SHADOW_DY_DP: Float = 0.75f

    /** Darker bottom bevel skirt tokens for tactile 3D keycap rim depth. */
    const val KEYCAP_BEVEL_DP: Float = 1.5f
    const val KEYCAP_BEVEL_COLOR: Int = 0x59000000

    /** Compact keycap proportions relative to the host text size. */
    const val KEYCAP_TEXT_SCALE: Float = 0.90f
    const val KEYCAP_PAD_H_FACTOR: Float = 0.30f
    const val KEYCAP_MIN_PILL_FACTOR: Float = 1.32f
    const val KEYCAP_MARGIN_H_FACTOR: Float = 0.10f
    const val KEYCAP_PAD_V_FACTOR: Float = 0.14f

    data class KeycapGeometry(
        val badgeTextSizePx: Float,
        val padHPx: Float,
        val minPillWidthPx: Float,
        val marginHPx: Float,
        val padVPx: Int,
    ) {
        /** Pill background width (px) excluding outer margins. */
        fun pillWidthPx(measuredTextWidthPx: Float): Float =
            maxOf(minPillWidthPx, measuredTextWidthPx.coerceAtLeast(0f) + padHPx * 2f)

        /** Total horizontal advance (px) including symmetric side margins. */
        fun totalWidthPx(measuredTextWidthPx: Float): Float =
            pillWidthPx(measuredTextWidthPx) + marginHPx * 2f
    }

    /** Zero-allocation primitive keycap pill width (px) for `KeycapBadgeSpan.draw()`. */
    fun keycapPillWidthPx(baseTextSizePx: Float, measuredTextWidthPx: Float): Float {
        val size = baseTextSizePx.coerceAtLeast(1f)
        return maxOf(
            size * KEYCAP_MIN_PILL_FACTOR,
            measuredTextWidthPx.coerceAtLeast(0f) + size * KEYCAP_PAD_H_FACTOR * 2f,
        )
    }

    /** Zero-allocation primitive keycap horizontal margin (px). */
    fun keycapMarginHPx(baseTextSizePx: Float): Float =
        baseTextSizePx.coerceAtLeast(1f) * KEYCAP_MARGIN_H_FACTOR

    /** Zero-allocation primitive keycap vertical font-metric padding (px). */
    fun keycapPadVPx(baseTextSizePx: Float): Int =
        kotlin.math.round(baseTextSizePx.coerceAtLeast(1f) * KEYCAP_PAD_V_FACTOR)
            .toInt()
            .coerceAtLeast(1)

    /**
     * Pure keycap geometry spec for a host text size in px (or sp when working
     * in dp-equivalent units). Host-tested across `9..13sp`.
     */
    fun keycapGeometry(baseTextSizePx: Float): KeycapGeometry {
        val size = baseTextSizePx.coerceAtLeast(1f)
        return KeycapGeometry(
            badgeTextSizePx = size * KEYCAP_TEXT_SCALE,
            padHPx = size * KEYCAP_PAD_H_FACTOR,
            minPillWidthPx = size * KEYCAP_MIN_PILL_FACTOR,
            marginHPx = keycapMarginHPx(size),
            padVPx = keycapPadVPx(size),
        )
    }

    /**
     * Conservative single-line width estimate (dp) for [raw] at [textSizeSp],
     * using [keycapGeometry] and ~0.58em average sans-serif glyph advance.
     */
    fun estimateLineWidthDp(raw: String, textSizeSp: Float): Float {
        if (raw.isEmpty()) return 0f
        val size = textSizeSp.coerceAtLeast(1f)
        val tokens = tokenize(raw)
        val geom = keycapGeometry(size)
        val avgCharDp = size * 0.58f
        val badgeCharDp = geom.badgeTextSizePx * 0.62f
        val spaceDp = size * 0.28f
        var width = 0f
        var cursor = 0
        for (token in tokens) {
            if (token.start > cursor) {
                width += (token.start - cursor) * spaceDp
            }
            val len = token.end - token.start
            width += when (token.kind) {
                TokenKind.KEYCAP -> geom.totalWidthPx(len * badgeCharDp)
                TokenKind.SEPARATOR -> size * 0.45f
                TokenKind.PREFIX -> len * avgCharDp * 1.04f
                TokenKind.ACTION -> len * avgCharDp
            }
            cursor = token.end
        }
        if (cursor < raw.length) {
            width += (raw.length - cursor) * spaceDp
        }
        return width
    }

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
