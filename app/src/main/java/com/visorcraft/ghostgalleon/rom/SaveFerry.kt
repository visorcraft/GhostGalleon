package com.visorcraft.ghostgalleon.rom

enum class FerryKind { RA_SRM, RA_STATE }
enum class FerryRefuse { NONE, NOT_READY, DIFFERENT_TITLE, DIFFERENT_PLAYER, YIELD_DEST }

data class SaveDoc(val uri: String, val name: String)

data class FerryOffer(
    val fromRomId: String,
    val toRomId: String,
    val kind: FerryKind,
    val fromUri: String,
    val toName: String,
    val slot: Int?,
)

object SaveFerry {
    fun sameTitle(a: RomIdentity?, b: RomIdentity?): Boolean {
        if (a == null || b == null || !a.ready || !b.ready) return false
        val hashOk = !a.hash.isNullOrBlank() && a.hash == b.hash
        val groupOk = !a.groupId.isNullOrBlank() && a.groupId == b.groupId
        return hashOk || groupOk
    }

    fun samePlayerHint(fromPlayer: String?, toPlayer: String?): Boolean =
        SessionHandoff.isRaPlayer(fromPlayer, fromPlayer) &&
            SessionHandoff.isRaPlayer(toPlayer, toPlayer)

    fun classifyName(name: String, stem: String): Pair<FerryKind, Int?>? {
        if (stem.isEmpty()) return null
        val prefix = "$stem."
        if (!name.startsWith(prefix)) return null
        val rest = name.substring(prefix.length)
        if (rest == "srm") return FerryKind.RA_SRM to null
        if (rest == "state") return FerryKind.RA_STATE to 0
        if (!rest.startsWith("state")) return null
        val slot = rest.removePrefix("state").toIntOrNull() ?: return null
        // Cinema band 9–12 is never offered.
        if (slot !in 1..8) return null
        return FerryKind.RA_STATE to slot
    }

    fun refuse(
        fromId: RomIdentity?,
        toId: RomIdentity?,
        fromPlayer: String?,
        toPlayer: String?,
        destIsOpenYield: Boolean,
    ): FerryRefuse {
        if (fromId == null || toId == null || !fromId.ready || !toId.ready) {
            return FerryRefuse.NOT_READY
        }
        if (!sameTitle(fromId, toId)) return FerryRefuse.DIFFERENT_TITLE
        if (!samePlayerHint(fromPlayer, toPlayer)) return FerryRefuse.DIFFERENT_PLAYER
        if (destIsOpenYield) return FerryRefuse.YIELD_DEST
        return FerryRefuse.NONE
    }

    fun offers(
        from: RomEntry,
        to: RomEntry,
        fromDocs: List<SaveDoc>,
        refuse: FerryRefuse,
    ): List<FerryOffer> {
        if (refuse != FerryRefuse.NONE) return emptyList()
        val fromStem = stemOf(from)
        val toStem = stemOf(to)
        val out = ArrayList<FerryOffer>(fromDocs.size)
        for (doc in fromDocs) {
            val classified = classifyName(doc.name, fromStem) ?: continue
            val suffix = doc.name.removePrefix(fromStem)
            out += FerryOffer(
                fromRomId = from.id,
                toRomId = to.id,
                kind = classified.first,
                fromUri = doc.uri,
                toName = toStem + suffix,
                slot = classified.second,
            )
        }
        return out
    }

    private fun stemOf(entry: RomEntry): String {
        val file = entry.path?.substringAfterLast('/')
            ?: entry.id.substringAfter(':', entry.name).substringAfterLast('/')
        val dot = file.lastIndexOf('.')
        return if (dot > 0) file.substring(0, dot) else file
    }
}
