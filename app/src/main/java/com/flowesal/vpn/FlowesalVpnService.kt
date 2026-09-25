package com.flowesal.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import hev.htproxy.TProxyService
import io.github.oviron.libbyedpi.ByeDpi
import io.github.oviron.libbyedpi.ByeDpiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.File

class FlowesalVpnService : VpnService() {
    companion object {
        private const val TAG = "FlowesalVpn"
        private const val CHANNEL_ID = "flowesal_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val PROXY_PORT = 1080

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var iface: ParcelFileDescriptor? = null
    private var profile = "General"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val profiles = mapOf(
        "General" to listOf("--auto=torst"),
        "Alt 1" to listOf("--split", "1", "--auto=torst"),
        "Alt 2" to listOf("--disorder", "1", "--auto=torst"),
        "Alt 3" to listOf("--split", "1", "--disorder", "1", "--auto=torst"),
        "Alt 4" to listOf("--tlsrec", "1+s", "--auto=torst")
    )

    override fun onCreate() {
        super.onCreate()
        ByeDpi.load(applicationInfo.nativeLibraryDir)
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        profile = intent?.getStringExtra("profile") ?: "General"
        startForegroundNotification()
        if (!isRunning) {
            scope.launch { startEngine() }
        }
        return START_STICKY
    }

    private suspend fun startEngine() {
        if (isRunning) return

        var config: File? = null
        try {
            val args = mutableListOf("-i", "127.0.0.1", "-p", PROXY_PORT.toString())
            args += profiles[profile] ?: profiles.getValue("General")
            Log.i(TAG, "Starting ByeDPI profile=$profile args=$args")

            ByeDpi.start(ByeDpiConfig(args))
            Log.i(TAG, "ByeDPI is listening on 127.0.0.1:$PROXY_PORT")

            val builder = Builder()
                .setSession("Flowesal")
                .setMtu(8500)
                .addAddress("10.10.10.10", 32)
                .addRoute("0.0.0.0", 0)
                // Capture IPv6 too; otherwise IPv6 traffic can bypass the TUN.
                .addAddress("fd00::1", 128)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                // Keep the app's own proxy/tunnel sockets outside the VPN.
                .addDisallowedApplication(packageName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val fd = builder.establish()
                ?: throw IllegalStateException("VPN establish() returned null")
            iface = fd
            Log.i(TAG, "VPN interface established fd=${fd.fd}")

            config = File.createTempFile("flowesal-tun-", ".yml", cacheDir)
            config.writeText(
                """
                misc:
                  task-stack-size: 81920
                  log-file: ${File(cacheDir, "hev-socks5-tunnel.log").absolutePath}
                  log-level: info
                socks5:
                  mtu: 8500
                  address: 127.0.0.1
                  port: $PROXY_PORT
                  udp: 'udp'
                """.trimIndent()
            )

            val started = TProxyService.TProxyStartService(config.absolutePath, fd.fd)
            if (!started) {
                throw IllegalStateException("hev-socks5-tunnel rejected the start request")
            }
            if (!TProxyService.TProxyIsRunning()) {
                throw IllegalStateException("hev-socks5-tunnel is not running")
            }

            isRunning = true
            updateNotification("Flowesal включен • $profile")
            Log.i(TAG, "Flowesal VPN is running")
        } catch (e: Throwable) {
            isRunning = false
            Log.e(TAG, "VPN start failed", e)
            runCatching { TProxyService.TProxyStopService() }
            iface?.close()
            iface = null
            runCatching { ByeDpi.stop() }
            updateNotification("Ошибка запуска: ${e.message ?: e.javaClass.simpleName}")
            stopSelf()
        } finally {
            runCatching { config?.delete() }
        }
    }

    private fun stopEngine() {
        Log.i(TAG, "Stopping Flowesal VPN")
        isRunning = false
        runBlocking(Dispatchers.IO) {
            runCatching { TProxyService.TProxyStopService() }
            runCatching { ByeDpi.stop() }
            iface?.close()
            iface = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Flowesal VPN",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Flowesal DPI bypass tunnel"
                }
            )
        }
    }

    private fun startForegroundNotification() {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Flowesal")
            .setContentText("Запуск туннеля…")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Flowesal")
            .setContentText(text)
            .setOngoing(isRunning)
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onRevoke() {
        stopEngine()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopEngine()
        scope.cancel()
        super.onDestroy()
    }
}
