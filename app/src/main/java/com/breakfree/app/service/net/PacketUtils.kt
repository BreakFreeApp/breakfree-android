package com.breakfree.app.service.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal IPv4/UDP/TCP packet parsing and construction, just enough to:
 *  1. Recognise a DNS query (UDP/53) and read the queried name.
 *  2. Recognise a plain HTTP (TCP/80) connection to our sinkhole address and
 *     drive a tiny hand-rolled TCP state machine to serve one static response.
 *
 * This is intentionally NOT a general-purpose TCP/IP stack: no retransmission,
 * no fragmentation, no window scaling. It only needs to handle one short-lived,
 * low-throughput connection at a time well enough for a browser to render a page.
 */
object PacketUtils {

    fun ipv4HeaderLength(packet: ByteArray): Int = (packet[0].toInt() and 0x0F) * 4

    fun protocol(packet: ByteArray): Int = packet[9].toInt() and 0xFF

    fun sourceAddress(packet: ByteArray): ByteArray = packet.copyOfRange(12, 16)
    fun destAddress(packet: ByteArray): ByteArray = packet.copyOfRange(16, 20)

    private fun checksum(buffer: ByteBuffer, start: Int, length: Int): Int {
        var sum = 0
        var i = start
        val end = start + length
        while (i < end - 1) {
            sum += ((buffer.get(i).toInt() and 0xFF) shl 8) or (buffer.get(i + 1).toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) {
            sum += (buffer.get(end - 1).toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    /** Builds a complete IPv4 + UDP packet with correct checksums. */
    fun buildUdpPacket(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val buf = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put((0x45).toByte())          // version 4, IHL 5
        buf.put(0)                        // DSCP/ECN
        buf.putShort(totalLength.toShort())
        buf.putShort(0)                   // identification
        buf.putShort(0x4000.toShort())    // flags: don't fragment
        buf.put(64)                       // TTL
        buf.put(17)                       // protocol UDP
        buf.putShort(0)                   // header checksum placeholder
        buf.put(srcIp)
        buf.put(dstIp)

        val ipChecksum = checksum(buf, 0, 20)
        buf.putShort(10, ipChecksum.toShort())

        // UDP header
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLength.toShort())
        buf.putShort(0) // checksum optional for IPv4 UDP; 0 = not computed
        buf.put(payload)

        return buf.array()
    }

    /** Builds a complete IPv4 + TCP packet (header only, or with payload) with correct checksums. */
    fun buildTcpPacket(
        srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val tcpHeaderLength = 20
        val totalLength = 20 + tcpHeaderLength + payload.size
        val buf = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put((0x45).toByte())
        buf.put(0)
        buf.putShort(totalLength.toShort())
        buf.putShort(0)
        buf.putShort(0x4000.toShort())
        buf.put(64)
        buf.put(6) // protocol TCP
        buf.putShort(0)
        buf.put(srcIp)
        buf.put(dstIp)
        val ipChecksum = checksum(buf, 0, 20)
        buf.putShort(10, ipChecksum.toShort())

        val tcpStart = 20
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putInt(seq.toInt())
        buf.putInt(ack.toInt())
        buf.put(((tcpHeaderLength / 4) shl 4).toByte()) // data offset
        buf.put(flags.toByte())
        buf.putShort(65535.toShort()) // window size
        buf.putShort(0) // checksum placeholder
        buf.putShort(0) // urgent pointer
        buf.put(payload)

        // TCP checksum uses a pseudo-header
        val pseudoAndTcp = ByteBuffer.allocate(12 + tcpHeaderLength + payload.size).order(ByteOrder.BIG_ENDIAN)
        pseudoAndTcp.put(srcIp)
        pseudoAndTcp.put(dstIp)
        pseudoAndTcp.put(0)
        pseudoAndTcp.put(6)
        pseudoAndTcp.putShort((tcpHeaderLength + payload.size).toShort())
        for (i in tcpStart until totalLength) pseudoAndTcp.put(buf.get(i))
        val tcpChecksum = checksum(pseudoAndTcp, 0, pseudoAndTcp.capacity())
        buf.putShort(tcpStart + 16, tcpChecksum.toShort())

        return buf.array()
    }

    const val TCP_FLAG_FIN = 0x01
    const val TCP_FLAG_SYN = 0x02
    const val TCP_FLAG_RST = 0x04
    const val TCP_FLAG_PSH = 0x08
    const val TCP_FLAG_ACK = 0x10
}
