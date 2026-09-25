package com.flowesal.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class FlowesalVpnService : VpnService() {

    companion object {
        private const val CHANNEL_ID = "flowesal_vpn"
        private const val NOTIFICATION_ID = 1001
    }

    private var iface: ParcelFileDescriptor? = null

    @Volatile
    private var running = false

    private var profile = "General"

    // These are deliberately placeholders until the real Flowesal/Zapret
    // strategy engine is integrated. The current engine is a DNS filter.
    private val profiles = mapOf(
        "General" to setOf("example-blocked.invalid"),
        "Alt 1" to setOf("example-blocked.invalid"),
        "Alt 2" to setOf("example-blocked.invalid"),
        "Alt 3" to setOf("example-blocked.invalid"),
        "Alt 4" to setOf("example-blocked.invalid")
    )

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        profile = intent?.getStringExtra("profile") ?: "General"
        startForegroundNotification()

        if (!running) {
            startVpn()
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Flowesal VPN",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Статус локального VPN Flowesal"
                }
            )
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Flowesal работает")
            .setContentText("Профиль: $profile")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession("Flowesal")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addDnsServer("10.10.0.1")
            .addRoute("10.10.0.1", 32)

        iface = builder.establish()

        if (iface == null) {
            stopSelf()
            return
        }

        running = true

        thread(name = "flowesal-dns") {
            loop()
        }
    }

    private fun loop() {
        val pfd = iface ?: return

        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    val buffer = ByteArray(32767)

                    while (running) {
                        val length = input.read(buffer)
                        if (length <= 0) continue
                        handlePacket(buffer, length, output)
                    }
                }
            }
        } catch (_: Exception) {
            if (running) stopSelf()
        }
    }

    private fun handlePacket(
        packet: ByteArray,
        len: Int,
        out: FileOutputStream
    ) {
        if (len < 28) return

        val version = (packet[0].toInt() ushr 4) and 0x0F
        val ihl = (packet[0].toInt() and 0x0F) * 4

        if (version != 4 || ihl < 20 || len < ihl + 8) return
        if ((packet[9].toInt() and 0xFF) != 17) return

        val udp = ihl
        val srcPort = u16(packet, udp)
        val dstPort = u16(packet, udp + 2)
        if (dstPort != 53) return

        val dnsStart = udp + 8
        if (dnsStart >= len) return

        val dns = packet.copyOfRange(dnsStart, len)
        val name = readDnsName(dns) ?: return

        val blocked = profiles[profile].orEmpty().any {
            name == it || name.endsWith(".$it")
        }

        val response = if (blocked) buildBlockedDns(dns) else forwardDns(dns)
        if (response == null) return

        val reply = packet.copyOf(ihl + 8 + response.size)

        for (i in 0 until 4) {
            reply[12 + i] = packet[16 + i]
            reply[16 + i] = packet[12 + i]
        }

        reply[8] = 64

        put16(reply, udp, dstPort)
        put16(reply, udp + 2, srcPort)
        put16(reply, udp + 4, 8 + response.size)
        System.arraycopy(response, 0, reply, udp + 8, response.size)

        put16(reply, 10, 0)
        put16(reply, 10, checksum(reply, 0, ihl))

        put16(reply, udp + 6, 0)
        put16(reply, udp + 6, udpChecksum(reply, udp, 8 + response.size))

        out.write(reply)
    }

    private fun forwardDns(q: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null

                socket.soTimeout = 2500
                val address = InetAddress.getByName("1.1.1.1")

                socket.send(DatagramPacket(q, q.size, address, 53))

                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                packet.data.copyOf(packet.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBlockedDns(q: ByteArray): ByteArray {
        val response = q.copyOf()
        response[2] = (response[2].toInt() or 0x80).toByte()
        response[3] = (response[3].toInt() or 0x83).toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        return response
    }

    private fun readDnsName(data: ByteArray): String? {
        if (data.size < 13) return null

        var index = 12
        val parts = mutableListOf<String>()

        while (index < data.size) {
            val length = data[index].toInt() and 0xFF
            index++

            if (length == 0) break
            if (length > 63 || index + length > data.size) return null

            parts += String(data, index, length, Charsets.US_ASCII)
            index += length
        }

        return parts.joinToString(".").lowercase()
    }

    private fun u16(data: ByteArray, position: Int): Int {
        return ((data[position].toInt() and 0xFF) shl 8) or
            (data[position + 1].toInt() and 0xFF)
    }

    private fun put16(data: ByteArray, position: Int, value: Int) {
        data[position] = (value ushr 8).toByte()
        data[position + 1] = value.toByte()
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var index = offset

        while (index < offset + length) {
            val high = (data[index].toInt() and 0xFF) shl 8
            val low = if (index + 1 < offset + length) {
                data[index + 1].toInt() and 0xFF
            } else {
                0
            }

            sum += high or low

            while ((sum ushr 16) != 0) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }

            index += 2
        }

        return sum.inv() and 0xFFFF
    }

    private fun udpChecksum(data: ByteArray, udpOffset: Int, length: Int): Int {
        var sum = 0

        for (i in 12..19 step 2) {
            sum += u16(data, i)
        }

        sum += 17
        sum += length

        var index = udpOffset
        while (index < udpOffset + length) {
            val high = (data[index].toInt() and 0xFF) shl 8
            val low = if (index + 1 < udpOffset + length) {
                data[index + 1].toInt() and 0xFF
            } else {
                0
            }

            sum += high or low
            index += 2
        }

        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return sum.inv() and 0xFFFF
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun stopVpn() {
        running = false
        iface?.close()
        iface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}
