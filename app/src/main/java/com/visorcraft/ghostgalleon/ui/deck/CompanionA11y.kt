package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, locale-safe TalkBack announcement formatters for CompanionPanel,
 * TOP_STRIP, PERF_HUD cards, and HintBar. Contains zero hardcoded prose.
 * Host-tested across all supported locales.
 */
object CompanionA11y {

    private val MULTI_SPACE = Regex("\\s{2,}")

    /** Combine a localized PERF_HUD card [label] and live [value] into one single-swipe description. */
    fun metricCardDescription(label: String, value: String): String {
        val l = label.trim()
        val v = value.trim()
        return when {
            l.isNotEmpty() && v.isNotEmpty() -> "$l: $v"
            l.isNotEmpty() -> l
            else -> v
        }
    }

    /** Collapse redundant whitespace inside localized hint strings for smoother screen-reader pacing. */
    fun plainHintDescription(raw: String): String =
        raw.trim().replace(MULTI_SPACE, " ")

    /** Combine non-blank, non-duplicate hero title, subtitle, and optional RA progress for TalkBack. */
    fun heroSummary(
        title: String,
        subline: String? = null,
        raLine: String? = null,
    ): String {
        val parts = ArrayList<String>(3)
        fun addUnique(candidate: String?) {
            val trimmed = candidate?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            if (parts.none { it.equals(trimmed, ignoreCase = true) }) {
                parts += trimmed
            }
        }
        addUnique(title)
        addUnique(subline)
        addUnique(raLine)
        return parts.joinToString(", ")
    }
}
