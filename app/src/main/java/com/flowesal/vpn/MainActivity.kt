package com.flowesal.vpn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val profiles = arrayOf("General", "Alt 1", "Alt 2", "Alt 3", "Alt 4")
    private var selected = "General"
    private var connected = false
    private lateinit var power: TextView
    private lateinit var status: TextView
    private lateinit var mode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun bg(c: Int, radius: Float = 24f, stroke: Int = 0, sc: Int = 0) =
        GradientDrawable().apply {
            setColor(c)
            cornerRadius = radius
            if (stroke > 0) setStroke(stroke, sc)
        }

    private fun tv(t: String, s: Float, c: Int) = TextView(this).apply {
        text = t
        textSize = s
        setTextColor(c)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 34, 28, 18)
            setBackgroundColor(Color.rgb(7, 13, 22))
        }

        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = tv("FLOWESAL", 30f, Color.WHITE)
        title.setTypeface(null, 1)
        head.addView(title, LinearLayout.LayoutParams(0, 70, 1f))
        head.addView(
            tv("⚙", 30f, Color.WHITE).apply {
                setPadding(12, 0, 0, 0)
            }
        )

        root.addView(head)
        root.addView(
            tv("VPN  •  Запрет  •  Свобода", 16f, Color.rgb(143, 163, 194)).apply {
                setPadding(0, 0, 0, 10)
            }
        )

        power = tv("⏻\nSTART", 26f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setBackground(
                bg(
                    Color.rgb(13, 38, 78),
                    180f,
                    4,
                    Color.rgb(24, 136, 255)
                )
            )
            setOnClickListener { toggle() }
        }
        root.addView(
            power,
            LinearLayout.LayoutParams(-1, 310).apply {
                setMargins(50, 18, 50, 24)
            }
        )

        status = tv(
            "●  VPN выключен\n    DNS-фильтрация не активна",
            18f,
            Color.WHITE
        ).apply {
            setPadding(20, 18, 20, 18)
            setBackground(
                bg(
                    Color.rgb(16, 26, 40),
                    26f,
                    2,
                    Color.rgb(31, 50, 75)
                )
            )
        }
        root.addView(status)

        root.addView(
            tv("ПРОФИЛИ ЗАПРЕТА", 16f, Color.rgb(143, 163, 194)).apply {
                setPadding(0, 28, 0, 12)
            }
        )

        val scroll = HorizontalScrollView(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        profiles.forEach { p ->
            val b = tv(p, 15f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                setPadding(24, 0, 24, 0)
                setBackground(
                    bg(
                        Color.rgb(16, 26, 40),
                        22f,
                        2,
                        Color.rgb(31, 50, 75)
                    )
                )
                setOnClickListener {
                    selected = p
                    refreshProfiles(row)
                }
            }
            row.addView(
                b,
                LinearLayout.LayoutParams(120, 110).apply {
                    setMargins(0, 0, 10, 0)
                }
            )
        }

        scroll.addView(row)
        root.addView(scroll)

        mode = tv("Режим: General", 15f, Color.rgb(143, 163, 194)).apply {
            setPadding(0, 18, 0, 0)
        }
        root.addView(mode)

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        root.addView(
            tv(
                "Главная     Правила     Логи     О приложении",
                14f,
                Color.rgb(143, 163, 194)
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8)
            }
        )

        setContentView(root)
        refreshProfiles(row)
    }

    private fun refreshProfiles(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i) as TextView
            val active = v.text.toString() == selected
            v.setTextColor(
                if (active) Color.WHITE else Color.rgb(143, 163, 194)
            )
            v.setBackground(
                bg(
                    if (active) Color.rgb(12, 52, 105) else Color.rgb(16, 26, 40),
                    22f,
                    2,
                    if (active) Color.rgb(24, 136, 255) else Color.rgb(31, 50, 75)
                )
            )
        }
        mode.text = "Режим: $selected"
    }

    private fun toggle() {
        if (connected) {
            stopService(Intent(this, FlowesalVpnService::class.java))
            connected = false
            power.text = "⏻\nSTART"
            status.text = "●  VPN выключен\n    DNS-фильтрация не активна"
            return
        }

        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 10)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        startService(
            Intent(this, FlowesalVpnService::class.java)
                .putExtra("profile", selected)
        )
        connected = true
        power.text = "⏻\nSTOP"
        status.text = "●  VPN включен\n    Профиль: $selected"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            startVpn()
        }
    }
}
