package com.breakfree.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.service.net.DnsInterceptor
import com.breakfree.app.service.net.PacketUtils
import com.breakfree.app.service.net.TcpBlockPageResponder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * A local-only VPN whose sole purpose is DNS-level domain blocking. It does NOT
 * route general traffic through itself: only packets destined to our fake DNS
 * server address are captured (see route setup in [establishVpn]), so everyday
 * browsing bypasses this service entirely except for the DNS lookup itself.
 *
 * Blocked domains resolve to a local sinkhole IP; everything else is forwarded
 * to a real upstream DNS resolver and relayed back, so normal DNS keeps working.
 */
class BreakFreeVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var scope: CoroutineScope? = null
    private var readThread: Thread? = null
    private val outputLock = Any()

    @Volatile private var blockedDomains: Set<String> = emptySet()

    private val tcpResponder = TcpBlockPageResponder(BLOCK_PAGE_HTML)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        startForeground(NOTIFICATION_ID, buildNotification())

        val app = BreakFreeApplication.from(this)
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = serviceScope
        serviceScope.launch {
            app.repository.observeBlockedDomains().collectLatest { domains ->
                blockedDomains = domains.map { it.domain.lowercase() }.toSet()
            }
        }

        vpnInterface = establishVpn()
        if (vpnInterface == null) return

        readThread = thread(name = "BreakFreeVpnReadLoop") { readLoop() }
    }

    private fun stopVpn() {
        readThread?.interrupt()
        readThread = null
        scope?.cancel()
        scope = null
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("BreakFree")
            .addAddress(VPN_LOCAL_ADDRESS, 32)
            .addDnsServer(FAKE_DNS_ADDRESS)
            .addRoute(FAKE_DNS_ADDRESS, 32)
            .addRoute(SINKHOLE_ADDRESS, 32)
        return runCatching { builder.establish() }.getOrNull()
    }

    private fun readLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (!Thread.currentThread().isInterrupted) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                break
            }
            if (length <= 0) continue
            val packet = buffer.copyOf(length)

            try {
                handlePacket(packet, output)
            } catch (e: Exception) {
                // Malformed/unsupported packet: drop it silently, never crash the read loop.
            }
        }
    }

    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        if (packet.isEmpty()) return
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return // IPv6 not handled in this simplified implementation

        val ipHeaderLength = PacketUtils.ipv4HeaderLength(packet)
        val protocol = PacketUtils.protocol(packet)
        val srcIp = PacketUtils.sourceAddress(packet)
        val dstIp = PacketUtils.destAddress(packet)

        when (protocol) {
            17 -> handleUdp(packet, ipHeaderLength, srcIp, dstIp, output) // UDP
            6 -> handleTcp(packet, ipHeaderLength, output) // TCP
        }
    }

    private fun handleUdp(packet: ByteArray, ipHeaderLength: Int, srcIp: ByteArray, dstIp: ByteArray, output: FileOutputStream) {
        val udpStart = ipHeaderLength
        val srcPort = ((packet[udpStart].toInt() and 0xFF) shl 8) or (packet[udpStart + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or (packet[udpStart + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsPayload = packet.copyOfRange(udpStart + 8, packet.size)
        val queriedName = DnsInterceptor.parseQueriedName(dnsPayload) ?: return

        if (isBlocked(queriedName)) {
            val response = DnsInterceptor.buildSpoofedResponse(dnsPayload, sinkholeBytes())
            val reply = PacketUtils.buildUdpPacket(dstIp, 53, srcIp, srcPort, response)
            synchronized(outputLock) { output.write(reply) }
        } else {
            forwardDnsQuery(dnsPayload, srcIp, srcPort, output)
        }
    }

    private fun handleTcp(packet: ByteArray, ipHeaderLength: Int, output: FileOutputStream) {
        val tcpStart = ipHeaderLength
        val dstPort = ((packet[tcpStart + 2].toInt() and 0xFF) shl 8) or (packet[tcpStart + 3].toInt() and 0xFF)
        if (!tcpResponder.handles(dstPort)) return // only the sinkhole's port 80 is served; 443 is silently dropped
        tcpResponder.handle(packet, ipHeaderLength) { response ->
            synchronized(outputLock) { output.write(response) }
        }
    }

    /** Exact match or any subdomain of a blocked domain (e.g. "instagram.com" blocks "www.instagram.com"). */
    private fun isBlocked(host: String): Boolean {
        val domains = blockedDomains
        if (host in domains) return true
        return domains.any { host.endsWith(".$it") }
    }

    private fun forwardDnsQuery(query: ByteArray, clientIp: ByteArray, clientPort: Int, output: FileOutputStream) {
        thread(name = "BreakFreeDnsForward") {
            try {
                val socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 3000
                val upstream = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(query, query.size, upstream, 53))

                val respBuf = ByteArray(1500)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)
                socket.close()

                val dnsResponsePayload = respBuf.copyOf(respPacket.length)
                val reply = PacketUtils.buildUdpPacket(sinkholeSourceForReplyIsFakeDns(), 53, clientIp, clientPort, dnsResponsePayload)
                synchronized(outputLock) { output.write(reply) }
            } catch (e: Exception) {
                // Upstream timeout/failure: the client's own DNS query will simply time out.
            }
        }
    }

    private fun sinkholeSourceForReplyIsFakeDns(): ByteArray = FAKE_DNS_ADDRESS.split(".").map { it.toInt().toByte() }.toByteArray()
    private fun sinkholeBytes(): ByteArray = SINKHOLE_ADDRESS.split(".").map { it.toInt().toByte() }.toByteArray()

    private fun buildNotification(): Notification {
        val channelId = "breakfree_vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(channelId, "Domain blocking", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BreakFree domain blocking is active")
            .setContentText("Blocking runs entirely on-device. No data leaves your phone.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        private const val VPN_LOCAL_ADDRESS = "10.111.222.1"
        private const val FAKE_DNS_ADDRESS = "10.111.222.2"
        private const val SINKHOLE_ADDRESS = "10.111.222.3"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.breakfree.app.action.VPN_STOP"

        private const val BLOCK_PAGE_HTML = """<!DOCTYPE html><html><head><meta charset="utf-8">
            <title>Blocked</title>
            <style>body{font-family:sans-serif;text-align:center;margin-top:15vh;background:#111;color:#eee}
            h1{font-size:2rem}</style></head>
            <body><h1>This site is blocked</h1><p>Request a break in BreakFree to access it temporarily.</p></body></html>"""

        /** Returns an Intent to hand to startActivityForResult if VPN permission is not yet granted, or null if already granted. */
        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BreakFreeVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BreakFreeVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
