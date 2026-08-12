package com.visorcraft.ghostgalleon.rom

/**
 * Best-effort skip of a full SAF walk when the tree root's lastModified
 * is known and unchanged. lastModified 0 is treated as unknown (never skip).
 * Pure; host-tested.
 */
object TreeSkip {

    fun skipWalk(
        storedLastModifiedMs: Long,
        currentLastModifiedMs: Long,
        force: Boolean,
    ): Boolean {
        if (force) return false
        if (storedLastModifiedMs <= 0L || currentLastModifiedMs <= 0L) return false
        return storedLastModifiedMs == currentLastModifiedMs
    }
}
