package com.flowesal.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class FlowesalVpnService : VpnService() {
    private var iface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var profile = "General"
    private val profiles = mapOf(
        "General" to setOf("example-blocked.invalid"),
        "Alt 1" to setOf("example-blocked.invalid"),
        "Alt 2" to setOf("example-blocked.invalid"),
        "Alt 3" to setOf("example-blocked.invalid"),
        "Alt 4" to setOf("example-blocked.invalid")
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        profile = intent?.getStringExtra("profile") ?: "General"
        if (!running) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val b = Builder().setSession("Flowesal").setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addDnsServer("10.10.0.1")
            .addRoute("10.10.0.1", 32)
        iface = b.establish()
        running = true
        thread(name = "flowesal-dns") { loop() }
    }

    private fun loop() {
        val pfd = iface ?: return
        FileInputStream(pfd.fileDescriptor).use { input ->
            FileOutputStream(pfd.fileDescriptor).use { output ->
                val buf = ByteArray(32767)
                while (running) {
                    val n = input.read(buf)
                    if (n <= 0) continue
                    handlePacket(buf, n, output)
                }
            }
        }
    }

    private fun handlePacket(packet: ByteArray, len: Int, out: FileOutputStream) {
        if (len < 28) return
        val version = (packet[0].toInt() ushr 4) and 0xF
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (version != 4 || ihl < 20 || packet[9].toInt() and 0xFF != 17) return
        val udp = ihl
        val srcPort = u16(packet, udp)
        val dstPort = u16(packet, udp + 2)
        if (dstPort != 53) return
        val dns = packet.copyOfRange(udp + 8, len)
        val name = readDnsName(dns) ?: return
        val blocked = profiles[profile].orEmpty().any { name == it || name.endsWith("." + it) }
        val response = if (blocked) buildBlockedDns(dns) else forwardDns(dns)
        if (response != null) {
            val reply = packet.copyOf(ihl + 8 + response.size)
            // swap IPs
            for (i in 0 until 4) { reply[12+i] = packet[16+i]; reply[16+i] = packet[12+i] }
            reply[8] = 64
            // swap UDP ports
            put16(reply, udp, dstPort); put16(reply, udp + 2, srcPort)
            put16(reply, udp + 4, 8 + response.size)
            System.arraycopy(response, 0, reply, udp + 8, response.size)
            put16(reply, 10, 0); put16(reply, 10, checksum(reply, 0, ihl))
            put16(reply, udp + 6, 0); put16(reply, udp + 6, udpChecksum(reply, udp, 8 + response.size))
            out.write(reply)
        }
    }

    private fun forwardDns(q: ByteArray): ByteArray? = try {
        val socket = DatagramSocket(); protect(socket)
        socket.soTimeout = 2500
        val addr = InetAddress.getByName("1.1.1.1")
        socket.send(DatagramPacket(q, q.size, addr, 53))
        val b = ByteArray(4096); val p = DatagramPacket(b, b.size); socket.receive(p); socket.close(); p.data.copyOf(p.length)
    } catch (_: Exception) { null }

    private fun buildBlockedDns(q: ByteArray): ByteArray {
        val r = q.copyOf(); r[2] = (r[2].toInt() or 0x80).toByte(); r[3] = (r[3].toInt() or 0x83).toByte()
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; return r
    }

    private fun readDnsName(d: ByteArray): String? {
        if (d.size < 13) return null; var i = 12; val parts = mutableListOf<String>()
        while (i < d.size) { val n = d[i].toInt() and 255; i++; if (n == 0) break; if (n > 63 || i+n > d.size) return null; parts += String(d, i, n, Charsets.US_ASCII); i += n }
        return parts.joinToString(".").lowercase()
    }
    private fun u16(a: ByteArray, p: Int) = ((a[p].toInt() and 255) shl 8) or (a[p+1].toInt() and 255)
    private fun put16(a: ByteArray, p: Int, v: Int) { a[p]=(v ushr 8).toByte(); a[p+1]=v.toByte() }
    private fun checksum(a: ByteArray, off: Int, len: Int): Int { var s=0; var i=off; while(i<off+len){s+=(a[i].toInt()and255)shl8 or(if(i+1<off+len)a[i+1].toInt()and255 else 0); while(s ushr 16 !=0)s=(s and 65535)+(s ushr 16); i+=2}; return s.inv() and 65535 }
    private fun udpChecksum(a: ByteArray, udp: Int, len: Int): Int { var s=0; for(i in 12..19 step 2)s+=u16(a,i); s+=17; s+=len; var i=udp; while(i<udp+len){s+=u16(a,i);i+=2}; while(s ushr 16 !=0)s=(s and 65535)+(s ushr 16); return s.inv() and 65535 }
    override fun onDestroy(){running=false; iface?.close(); iface=null; super.onDestroy()}
}
