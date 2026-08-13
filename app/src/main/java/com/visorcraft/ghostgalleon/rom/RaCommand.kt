package com.visorcraft.ghostgalleon.rom

enum class RaStatus { PLAYING, PAUSED, UNKNOWN }

object RaCommand {
    const val DEFAULT_PORT = 55355
    const val TIMEOUT_MS = 200
    const val PROBE_INTERVAL_MS = 5000L

    fun encode(command: String): ByteArray =
        (command.trim() + "\n").toByteArray(Charsets.US_ASCII)

    fun parseStatus(reply: String?): RaStatus {
        val u = reply?.uppercase() ?: return RaStatus.UNKNOWN
        return when {
            u.contains("PAUSED") -> RaStatus.PAUSED
            u.contains("PLAYING") -> RaStatus.PLAYING
            else -> RaStatus.UNKNOWN
        }
    }

    /** True if reply looks like an ACK / non-empty success body. */
    fun parseSlotReply(reply: String?): Boolean {
        if (reply == null) return false
        return reply.trim().isNotEmpty()
    }
}

fun interface RaTransport {
    fun send(port: Int, payload: ByteArray, timeoutMs: Int): ByteArray?
}

class RaCommandClient(
    private val transport: RaTransport,
    private val clockMs: () -> Long,
) {
    private var lastProbeMs: Long = Long.MIN_VALUE / 2
    private var linkUp: Boolean = false

    fun isLinkUp(): Boolean = linkUp

    fun probe(port: Int, nowMs: Long): Boolean {
        // While up, caller uses status(); keep returning true without re-sending VERSION.
        if (linkUp) return true
        if (nowMs - lastProbeMs < RaCommand.PROBE_INTERVAL_MS) return false
        lastProbeMs = nowMs
        val reply = transport.send(port, RaCommand.encode("VERSION"), RaCommand.TIMEOUT_MS)
        linkUp = reply != null
        return linkUp
    }

    fun status(port: Int): RaStatus {
        val text = requestText(port, "GET_STATUS") ?: return RaStatus.UNKNOWN
        return RaCommand.parseStatus(text)
    }

    fun pauseToggle(port: Int): Boolean = sendFireAndForget(port, "PAUSE_TOGGLE")

    fun saveState(port: Int): Boolean = sendFireAndForget(port, "SAVE_STATE")

    fun loadState(port: Int): Boolean = sendFireAndForget(port, "LOAD_STATE")

    /**
     * RetroArch does not reply to PAUSE_TOGGLE / SAVE_STATE / LOAD_STATE.
     * Timeout is success; only VERSION / GET_STATUS may clear [linkUp].
     */
    private fun sendFireAndForget(port: Int, command: String): Boolean {
        val text = requestText(port, command, dropLinkOnTimeout = false)
        return text == null || RaCommand.parseSlotReply(text)
    }

    private fun requestText(
        port: Int,
        command: String,
        dropLinkOnTimeout: Boolean = true,
    ): String? {
        val bytes = transport.send(port, RaCommand.encode(command), RaCommand.TIMEOUT_MS)
        if (bytes == null) {
            if (dropLinkOnTimeout) linkUp = false
            return null
        }
        linkUp = true
        return bytes.toString(Charsets.US_ASCII)
    }
}
