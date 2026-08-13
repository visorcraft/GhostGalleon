package com.visorcraft.ghostgalleon.rom

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Loopback UDP for RetroArch network commands. Android-only; not used by
 * [RaCommand]. Callers must invoke [send] off the main thread — bind plus
 * [RaCommand.TIMEOUT_MS] receive can hitch Companion for hundreds of ms.
 * One socket is reused so each command does not pay a fresh ephemeral bind.
 */
class RaUdpTransport : RaTransport {
    private var socket: DatagramSocket? = null

    override fun send(port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? {
        return try {
            val sock = socket ?: DatagramSocket().also { socket = it }
            sock.soTimeout = timeoutMs
            val addr = InetAddress.getByName("127.0.0.1")
            sock.send(DatagramPacket(payload, payload.size, addr, port))
            val buf = ByteArray(512)
            val incoming = DatagramPacket(buf, buf.size)
            sock.receive(incoming)
            incoming.data.copyOf(incoming.length)
        } catch (_: Exception) {
            runCatching { socket?.close() }
            socket = null
            null
        }
    }
}
