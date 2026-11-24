package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import java.util.Locale

/**
 * Pure, host-tested layout and metadata formatting helpers for the SteamGridDB
 * artwork candidate picker in [Deck.kt]. Matches thumbnail aspect ratio to art
 * kind (portrait box art, wide hero banners, transparent logos) and surfaces
 * original resolution + format sublines using existing localized string formats.
 */
object ArtPickerFormatter {

    enum class PreviewKind {
        GRID,
        HERO,
        LOGO,
    }

    data class PreviewSpec(
        val widthDp: Int,
        val heightDp: Int,
        val cornerRadiusDp: Int,
        val fitCenter: Boolean,
        val preserveAlpha: Boolean,
    )

    const val TWO_COLUMN_MIN_WIDTH_DP: Int = 500

    /** Map picker dialog title resource to its canonical [PreviewKind]. */
    fun previewKindForTitleRes(titleRes: Int): PreviewKind = when (titleRes) {
        R.string.artwork_pick_hero -> PreviewKind.HERO
        R.string.artwork_pick_logo -> PreviewKind.LOGO
        else -> PreviewKind.GRID
    }

    /**
     * Responsive grid column count: portrait `GRID` box art uses 2 columns on
     * panels `>= 500dp` wide (540dp Sugar & 960dp Odin 2 Portal), while narrow
     * panels (`< 500dp`), wide `HERO` banners, and `LOGO`s stay single-column.
     * Host-tested.
     */
    fun gridColumns(kind: PreviewKind, screenWidthDp: Int): Int =
        if (kind == PreviewKind.GRID && screenWidthDp >= TWO_COLUMN_MIN_WIDTH_DP) 2 else 1

    /** Aspect-aware thumbnail dimensions and scaling rules per [PreviewKind]. */
    fun previewSpec(kind: PreviewKind): PreviewSpec = when (kind) {
        PreviewKind.GRID -> PreviewSpec(
            widthDp = 68,
            heightDp = 98,
            cornerRadiusDp = 8,
            fitCenter = false,
            preserveAlpha = false,
        )
        PreviewKind.HERO -> PreviewSpec(
            widthDp = 116,
            heightDp = 38,
            cornerRadiusDp = 8,
            fitCenter = false,
            preserveAlpha = false,
        )
        PreviewKind.LOGO -> PreviewSpec(
            widthDp = 96,
            heightDp = 46,
            cornerRadiusDp = 8,
            fitCenter = true,
            preserveAlpha = true,
        )
    }

    /** Crisp memory-bounded decode max dimension (px) for picker previews. */
    fun decodeTargetPx(kind: PreviewKind, density: Float): Int {
        val spec = previewSpec(kind)
        val safeDensity = density.coerceAtLeast(1f)
        return (maxOf(spec.widthDp, spec.heightDp) * safeDensity * 1.5f)
            .toInt()
            .coerceIn(128, 320)
    }

    /** Normalized image format extension (e.g. `PNG`, `JPG`, `WEBP`), or null. */
    fun extractFileFormat(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val segment = path.substringAfterLast('/')
        val dot = segment.lastIndexOf('.')
        if (dot <= 0 || dot >= segment.lastIndex) return null
        val ext = segment.substring(dot + 1).uppercase(Locale.ROOT)
        if (ext.length !in 2..5 || !ext.all { it.isLetterOrDigit() }) return null
        return if (ext == "JPEG") "JPG" else ext
    }

    /** Original pixel dimensions formatted as `600×900`, or null when invalid. */
    fun formatDimensions(width: Int, height: Int): String? =
        if (width > 0 && height > 0) "${width}×${height}" else null

    /** Readable file stem stripped of query parameters and extension. */
    fun cleanFileName(url: String, maxChars: Int = 26): String {
        val path = url.substringBefore('?').substringBefore('#')
        val segment = path.substringAfterLast('/').trim()
        if (segment.isEmpty()) return ""
        val stem = segment.substringBeforeLast('.').ifBlank { segment }
        val limit = maxChars.coerceAtLeast(4)
        return if (stem.length <= limit) stem else stem.take(limit - 1) + "…"
    }

    /** Localized primary row title: `1/6 · stem` (or `1/6` when stem is blank). */
    fun candidateTitleText(
        indexOneBased: Int,
        totalCount: Int,
        url: String,
    ): UiText {
        val progress = text(
            R.string.format_progress,
            indexOneBased.coerceAtLeast(1),
            totalCount.coerceAtLeast(1),
        )
        val stem = cleanFileName(url)
        return if (stem.isEmpty()) {
            progress
        } else {
            text(R.string.format_dot_pair, progress, dynamicText(stem))
        }
    }

    /** Localized secondary metadata subline (`600×900 · PNG`), or null. */
    fun candidateSublineText(
        origWidth: Int,
        origHeight: Int,
        url: String,
    ): UiText? {
        val dims = formatDimensions(origWidth, origHeight)
        val fmt = extractFileFormat(url)
        return when {
            dims != null && fmt != null ->
                text(R.string.format_dot_pair, dynamicText(dims), dynamicText(fmt))
            dims != null -> dynamicText(dims)
            fmt != null -> dynamicText(fmt)
            else -> null
        }
    }

    /** Combined TalkBack label for a candidate preview card. */
    fun candidateAccessibilityText(title: UiText, subline: UiText?): UiText =
        if (subline != null) text(R.string.format_dot_pair, title, subline) else title
}
