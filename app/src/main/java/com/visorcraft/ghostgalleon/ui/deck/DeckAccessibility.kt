package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, host-tested TalkBack description composer for GridDeck, DockBar, and
 * FolderPanel slots.
 *
 * Accepts only pre-resolved localized resource strings / user item names and
 * joins non-blank unique segments cleanly with `", "` — zero hardcoded English
 * prose, zero redundant repetition.
 */
object DeckAccessibility {

    /**
     * Join non-blank, trimmed parts with `", "`, skipping consecutive or
     * case-insensitive duplicates so TalkBack reads concise natural labels.
     */
    fun joinParts(vararg parts: String?): String {
        val out = ArrayList<String>(parts.size)
        for (raw in parts) {
            val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (out.none { it.equals(clean, ignoreCase = true) }) {
                out.add(clean)
            }
        }
        return out.joinToString(", ")
    }

    /** Compose localized TalkBack description for a GridDeck slot tile. */
    fun gridSlotDescription(
        primaryLabel: String,
        secondaryDetail: String? = null,
        favoriteLabel: String? = null,
        dockLabel: String? = null,
        positionLabel: String? = null,
        movingLabel: String? = null,
    ): String = joinParts(
        primaryLabel,
        secondaryDetail,
        favoriteLabel,
        dockLabel,
        positionLabel,
        movingLabel,
    )

    /** Compose localized TalkBack description for a DockBar slot. */
    fun dockSlotDescription(
        primaryLabel: String,
        positionLabel: String? = null,
        movingLabel: String? = null,
    ): String = joinParts(
        primaryLabel,
        positionLabel,
        movingLabel,
    )
}
