package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.rom.RomEntry

/**
 * Pure SGDB work-queue shaping: which ROMs need art and in what order.
 * Host-tested. Heroes-first puts hero-only backfills ahead of full grid+hero
 * scrapes so companion art improves earlier in long jobs.
 */
object SgdbQueue {

    data class Need(
        val entry: RomEntry,
        /** Grid tile missing (or no local artUri and no disk grid). */
        val needGrid: Boolean,
        /** Hero panel art missing. */
        val needHero: Boolean,
    ) {
        val needsWork: Boolean get() = needGrid || needHero
        /** Hero-only backfill ranks before full scrapes. */
        val heroOnly: Boolean get() = needHero && !needGrid
    }

    /**
     * Filter to entries that still need network art and order them:
     * 1) hero-only backfills, 2) need both / grid, preserving relative order.
     */
    fun prioritize(
        entries: List<RomEntry>,
        hasGrid: (romId: String) -> Boolean,
        hasHero: (romId: String) -> Boolean,
    ): List<Need> {
        val needs = entries.mapNotNull { rom ->
            if (rom.artUri != null && hasGrid(rom.id) && hasHero(rom.id)) return@mapNotNull null
            val gridOk = rom.artUri != null || hasGrid(rom.id)
            val heroOk = hasHero(rom.id)
            val needGrid = !gridOk
            val needHero = !heroOk
            if (!needGrid && !needHero) null
            else Need(rom, needGrid = needGrid, needHero = needHero)
        }
        val heroFirst = needs.filter { it.heroOnly }
        val rest = needs.filter { !it.heroOnly }
        return heroFirst + rest
    }

    /** Default worker count for bounded parallel scrape (1..4). */
    fun workerCount(totalJobs: Int, maxWorkers: Int = MAX_WORKERS): Int {
        if (totalJobs <= 0) return 0
        return maxWorkers.coerceIn(1, 4).coerceAtMost(totalJobs)
    }

    const val MAX_WORKERS = 2
}
