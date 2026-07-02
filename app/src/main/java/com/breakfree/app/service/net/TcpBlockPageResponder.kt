package com.breakfree.app.service.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

private data class ConnKey(val srcIp: String, val srcPort: Int, val dstPort: Int)
private data class ConnState(var seq: Long, var ack: Long, var established: Boolean = false)

/**
 * Serves a single static HTML response for any TCP connection to our sinkhole IP on
 * port 80 (plain HTTP). Deliberately minimal: no retransmission, no partial-request
 * reassembly — good enough for a browser's first GET on a blocked domain, not a
 * general-purpose server. HTTPS (port 443) connections to the sinkhole are simply
 * not answered here, so they fail as a normal connection error (see README for why
 * we don't attempt to fake a TLS response).
 */
class TcpBlockPageResponder(private val htmlBody: String) {

    private val connections = HashMap<ConnKey, ConnState>()

    fun handles(destPort: Int) = destPort == 80

    fun handle(packet: ByteArray, ipHeaderLength: Int, write: (ByteArray) -> Unit) {
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val srcIp = PacketUtils.sourceAddress(packet)
        val dstIp = PacketUtils.destAddress(packet)
        val tcpStart = ipHeaderLength

        val srcPort = buf.getShort(tcpStart).toInt() and 0xFFFF
        val dstPort = buf.getShort(tcpStart + 2).toInt() and 0xFFFF
        val seq = buf.getInt(tcpStart + 4).toLong() and 0xFFFFFFFFL
        val ack = buf.getInt(tcpStart + 8).toLong() and 0xFFFFFFFFL
        val dataOffsetWords = (buf.get(tcpStart + 12).toInt() shr 4) and 0x0F
        val tcpHeaderLen = dataOffsetWords * 4
        val flags = buf.get(tcpStart + 13).toInt() and 0xFF
        val payloadStart = tcpStart + tcpHeaderLen
        val payloadLen = packet.size - payloadStart

        val key = ConnKey(srcIp.joinToString(".") { (it.toInt() and 0xFF).toString() }, srcPort, dstPort)
        val isSyn = flags and PacketUtils.TCP_FLAG_SYN != 0
        val isFin = flags and PacketUtils.TCP_FLAG_FIN != 0
        val isRst = flags and PacketUtils.TCP_FLAG_RST != 0

        if (isRst) { connections.remove(key); return }

        if (isSyn) {
            // Fresh connection: reply SYN-ACK, our initial sequence number = 0.
            val state = ConnState(seq = 0L, ack = seq + 1)
            connections[key] = state
            write(
                PacketUtils.buildTcpPacket(
                    dstIp, dstPort, srcIp, srcPort,
                    seqOf(state.seq), state.ack,
                    PacketUtils.TCP_FLAG_SYN or PacketUtils.TCP_FLAG_ACK
                )
            )
            state.seq += 1
            return
        }

        val state = connections[key] ?: return

        if (isFin) {
            write(
                PacketUtils.buildTcpPacket(
                    dstIp, dstPort, srcIp, srcPort,
                    seqOf(state.seq), seq + 1,
                    PacketUtils.TCP_FLAG_ACK
                )
            )
            connections.remove(key)
            return
        }

        if (payloadLen > 0 && !state.established) {
            // First payload after handshake = the HTTP request. We don't parse it; any
            // GET gets the same static block page.
            state.established = true
            state.ack = seq + payloadLen

            val response = buildHttpResponse(htmlBody)
            write(
                PacketUtils.buildTcpPacket(
                    dstIp, dstPort, srcIp, srcPort,
                    seqOf(state.seq), state.ack,
                    PacketUtils.TCP_FLAG_PSH or PacketUtils.TCP_FLAG_ACK,
                    response
                )
            )
            state.seq += response.size
            // Immediately follow with FIN — this is a one-shot response server.
            write(
                PacketUtils.buildTcpPacket(
                    dstIp, dstPort, srcIp, srcPort,
                    seqOf(state.seq), state.ack,
                    PacketUtils.TCP_FLAG_FIN or PacketUtils.TCP_FLAG_ACK
                )
            )
            state.seq += 1
        }
    }

    private fun seqOf(v: Long): Long = v and 0xFFFFFFFFL

    private fun buildHttpResponse(body: String): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        return headers.toByteArray(Charsets.US_ASCII) + bodyBytes
    }
}
