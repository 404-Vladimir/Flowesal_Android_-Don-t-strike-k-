package com.flowesal.vpn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val profiles = arrayOf("General", "Alt 1", "Alt 2", "Alt 3", "Alt 4")
    private var selected = "General"
    private var connected = false
    private lateinit var power: TextView
    private lateinit var status: TextView
    private lateinit var mode: TextView
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun bg(color: Int, radiusDp: Int = 24, strokeDp: Int = 0, strokeColor: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun tv(text: String, sizeSp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        includeFontPadding = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 13, 22)
        window.navigationBarColor = Color.rgb(7, 13, 22)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        syncServiceState()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 13, 22))
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(dp(20), dp(18), dp(20), dp(20) + bottomInset)
            insets
        }

        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = tv("FLOWESAL", 30f, Color.WHITE).apply {
            setTypeface(null, 1)
        }
        head.addView(title, LinearLayout.LayoutParams(0, dp(54), 1f))

        head.addView(
            tv("⚙", 27f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, 0, 0)
            },
            LinearLayout.LayoutParams(dp(50), dp(54))
        )

        root.addView(head)
        root.addView(
            tv("VPN  •  Запрет  •  Свобода", 16f, Color.rgb(143, 163, 194)).apply {
                setPadding(0, 0, 0, dp(10))
            }
        )

        power = tv("START", 27f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setTypeface(null, 1)
            setBackground(bg(Color.rgb(13, 38, 78), 42, 2, Color.rgb(24, 136, 255)))
            setOnClickListener { toggle() }
        }
        root.addView(
            power,
            LinearLayout.LayoutParams(-1, dp(170)).apply {
                setMargins(dp(18), dp(8), dp(18), dp(18))
            }
        )

        status = tv("VPN выключен\nDNS-фильтрация не активна", 17f, Color.WHITE).apply {
            setPadding(dp(18), dp(15), dp(18), dp(15))
            setBackground(bg(Color.rgb(16, 26, 40), 22, 1, Color.rgb(31, 50, 75)))
        }
        root.addView(status)

        root.addView(
            tv("ПРОФИЛИ ЗАПРЕТА", 15f, Color.rgb(143, 163, 194)).apply {
                setPadding(0, dp(20), 0, dp(10))
            }
        )

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        profiles.forEach { profileName ->
            val button = tv(profileName, 14f, Color.rgb(143, 163, 194)).apply {
                gravity = Gravity.CENTER
                setTypeface(null, 1)
                setBackground(bg(Color.rgb(16, 26, 40), 18, 1, Color.rgb(31, 50, 75)))
                setOnClickListener {
                    selected = profileName
                    refreshProfiles(row)
                    if (connected) restartWithSelectedProfile()
                }
            }
            row.addView(
                button,
                LinearLayout.LayoutParams(dp(88), dp(54)).apply {
                    setMargins(0, 0, dp(8), 0)
                }
            )
        }

        scroll.addView(row)
        root.addView(scroll)

        mode = tv("Режим: General", 15f, Color.rgb(143, 163, 194)).apply {
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(mode)

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }
        listOf("Главная", "Правила", "Логи", "О приложении").forEach { item ->
            nav.addView(
                tv(item, 12f, Color.rgb(112, 132, 162)).apply {
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, dp(48), 1f)
            )
        }
        root.addView(nav)

        setContentView(root)
        root.requestApplyInsets()
        refreshProfiles(row)
    }

    private fun refreshProfiles(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val view = row.getChildAt(i) as TextView
            val active = view.text.toString() == selected
            view.setTextColor(if (active) Color.WHITE else Color.rgb(143, 163, 194))
            view.setBackground(
                bg(
                    if (active) Color.rgb(12, 52, 105) else Color.rgb(16, 26, 40),
                    18,
                    1,
                    if (active) Color.rgb(24, 136, 255) else Color.rgb(31, 50, 75)
                )
            )
        }
        mode.text = "Режим: $selected"
    }

    private fun toggle() {
        if (connected || FlowesalVpnService.isRunning) {
            stopVpn()
        } else {
            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent != null) {
                startActivityForResult(permissionIntent, 10)
            } else {
                startVpn()
            }
        }
    }

    private fun startVpn() {
        val intent = Intent(this, FlowesalVpnService::class.java)
            .putExtra("profile", selected)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        connected = false
        power.text = "STARTING…"
        status.text = "VPN запускается…\nПроверка туннеля"
        pollServiceState(0)
    }

    private fun restartWithSelectedProfile() {
        stopService(Intent(this, FlowesalVpnService::class.java))
        connected = false
        power.text = "STARTING…"
        status.text = "Перезапуск туннеля…\nПрофиль: $selected"
        handler.postDelayed({ startVpn() }, 250)
    }

    private fun stopVpn() {
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, FlowesalVpnService::class.java))
        connected = false
        power.text = "START"
        status.text = "VPN выключен\nDNS-фильтрация не активна"
    }

    private fun pollServiceState(attempt: Int) {
        if (FlowesalVpnService.isRunning) {
            connected = true
            power.text = "STOP"
            status.text = "VPN включен\nПрофиль: $selected"
            return
        }

        if (attempt >= 20) {
            connected = false
            power.text = "START"
            status.text = "Не удалось запустить VPN\nПроверь разрешение VPN и журнал"
            return
        }

        handler.postDelayed({ pollServiceState(attempt + 1) }, 250)
    }

    private fun syncServiceState() {
        if (FlowesalVpnService.isRunning) {
            connected = true
            power.text = "STOP"
            status.text = "VPN включен\nПрофиль: $selected"
        } else if (connected) {
            connected = false
            power.text = "START"
            status.text = "VPN выключен\nDNS-фильтрация не активна"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            startVpn()
        } else if (requestCode == 10) {
            status.text = "VPN разрешение не выдано\nЗапуск отменён"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
