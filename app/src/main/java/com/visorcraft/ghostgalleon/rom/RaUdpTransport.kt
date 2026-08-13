package com.visorcraft.ghostgalleon.rom

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Loopback UDP for RetroArch network commands. Android-only; not used by [RaCommand]. */
class RaUdpTransport : RaTransport {
    override fun send(port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val addr = InetAddress.getByName("127.0.0.1")
                socket.send(DatagramPacket(payload, payload.size, addr, port))
                val buf = ByteArray(512)
                val incoming = DatagramPacket(buf, buf.size)
                socket.receive(incoming)
                incoming.data.copyOf(incoming.length)
            }
        } catch (_: Exception) {
            null
        }
    }
}
