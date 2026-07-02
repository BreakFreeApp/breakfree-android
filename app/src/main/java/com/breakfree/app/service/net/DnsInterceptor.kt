package com.breakfree.app.service.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DnsInterceptor {

    /** Extracts the queried domain name (lowercase) from a raw DNS UDP payload, or null if malformed. */
    fun parseQueriedName(dnsPayload: ByteArray): String? {
        if (dnsPayload.size < 12) return null
        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        if (qdCount < 1) return null

        val labels = mutableListOf<String>()
        var pos = 12
        while (pos < dnsPayload.size) {
            val len = dnsPayload[pos].toInt() and 0xFF
            if (len == 0) { pos += 1; break }
            if (len and 0xC0 == 0xC0) return null // compression pointer in question section: skip (rare)
            pos += 1
            if (pos + len > dnsPayload.size) return null
            labels.add(String(dnsPayload, pos, len, Charsets.US_ASCII))
            pos += len
        }
        if (labels.isEmpty()) return null
        return labels.joinToString(".").lowercase()
    }

    /**
     * Builds a spoofed DNS response for the given query, resolving to [sinkholeIp].
     * Copies the original transaction ID and question section, sets QR=1, RA=1, RCODE=0,
     * and appends a single A record answer with a short TTL.
     */
    fun buildSpoofedResponse(originalQuery: ByteArray, sinkholeIp: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(originalQuery.size + 16).order(ByteOrder.BIG_ENDIAN)

        // Header
        buf.put(originalQuery, 0, 2) // transaction ID
        buf.putShort(0x8180.toShort()) // flags: response, recursion available, no error
        buf.putShort(1) // QDCOUNT
        buf.putShort(1) // ANCOUNT
        buf.putShort(0) // NSCOUNT
        buf.putShort(0) // ARCOUNT

        // Copy the original question section verbatim
        var qEnd = 12
        while (qEnd < originalQuery.size) {
            val len = originalQuery[qEnd].toInt() and 0xFF
            if (len == 0) { qEnd += 1; break }
            qEnd += 1 + len
        }
        qEnd += 4 // QTYPE + QCLASS
        buf.put(originalQuery, 12, qEnd - 12)

        // Answer: pointer to name at offset 12, type A, class IN, TTL, RDLENGTH 4, RDATA = sinkhole IP
        buf.putShort(0xC00C.toShort())
        buf.putShort(1)  // TYPE A
        buf.putShort(1)  // CLASS IN
        buf.putInt(30)   // TTL seconds (short, so unblocking after a break takes effect quickly)
        buf.putShort(4)  // RDLENGTH
        buf.put(sinkholeIp)

        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }
}
