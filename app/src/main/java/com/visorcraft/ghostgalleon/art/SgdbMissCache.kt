package com.visorcraft.ghostgalleon.art

import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Negative cache for SteamGridDB: skip titles that already failed a search
 * until the query changes or the TTL elapses.
 * Pure selection + debounced atomic persistence helpers (zero Android deps).
 */
object SgdbMissCache {

    const val DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** Coalesce burst SteamGridDB misses into ~2 s debounced atomic disk writes. */
    const val PERSIST_DEBOUNCE_MS = 2_000L

    data class Miss(
        val romId: String,
        val query: String,
        val atMs: Long,
    )

    fun shouldSkip(
        miss: Miss?,
        currentQuery: String,
        nowMs: Long,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): Boolean {
        if (miss == null) return false
        if (miss.query != currentQuery) return false
        if (ttlMs <= 0L) return false
        return nowMs - miss.atMs < ttlMs
    }

    fun record(
        existing: Map<String, Miss>,
        romId: String,
        query: String,
        nowMs: Long,
    ): Map<String, Miss> {
        val q = query.trim()
        if (romId.isBlank() || q.isEmpty()) return existing
        return existing + (romId to Miss(romId, q, nowMs))
    }

    fun prune(existing: Map<String, Miss>, nowMs: Long, ttlMs: Long = DEFAULT_TTL_MS): Map<String, Miss> =
        existing.filterValues { nowMs - it.atMs < ttlMs }

    /** Serializes [misses] and persists atomically via `.tmp` + rename. */
    fun writeAtomic(file: File, misses: Map<String, Miss>) {
        val root = JSONObject()
        misses.forEach { (id, miss) ->
            root.put(
                id,
                JSONObject().put("query", miss.query).put("atMs", miss.atMs),
            )
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            throw t
        }
    }

    /**
     * Pure JVM debounced persistence coordinator eliminating O(M²) full-file rewrites
     * during batch SteamGridDB scrapes while guaranteeing atomic flush on batch completion
     * and process exit.
     */
    class Debouncer(
        private val debounceMs: Long = PERSIST_DEBOUNCE_MS,
        private val scheduleDelayed: (Long, Runnable) -> Unit,
        private val cancelDelayed: (Runnable) -> Unit,
        private val dispatchIo: (Runnable) -> Unit,
        private val writeAction: () -> Unit,
    ) {
        private val dirty = AtomicBoolean(false)
        private val timerArmed = AtomicBoolean(false)
        private val writeLock = Any()

        private val timerRunnable = Runnable {
            timerArmed.set(false)
            if (dirty.get()) {
                dispatchIo(ioRunnable)
            }
        }

        private val ioRunnable = Runnable {
            drainWrite()
        }

        /** Marks state dirty and arms a coalesced trailing-window debounce timer. */
        fun markDirty() {
            dirty.set(true)
            if (timerArmed.compareAndSet(false, true)) {
                scheduleDelayed(debounceMs, timerRunnable)
            }
        }

        /** Cancels any pending timer and dispatches an immediate background flush if dirty. */
        fun flushAsync() {
            cancelDelayed(timerRunnable)
            timerArmed.set(false)
            if (dirty.get()) {
                dispatchIo(ioRunnable)
            }
        }

        /** Cancels any pending timer and performs a serialized synchronous flush if dirty. */
        fun flushBlocking() {
            cancelDelayed(timerRunnable)
            timerArmed.set(false)
            drainWrite()
        }

        fun isDirty(): Boolean = dirty.get()

        private fun drainWrite() {
            synchronized(writeLock) {
                if (!dirty.getAndSet(false)) return
                writeAction()
            }
        }
    }
}
