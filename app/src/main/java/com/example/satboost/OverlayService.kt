package com.example.satboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val bars = mutableListOf<SeekBar>()
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var dim: View? = null
    private var warned = false

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("sat", MODE_PRIVATE)

        val ch = "satboost"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "SatBoost", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("SatBoost is running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        buildPanel()
        buildBubble()
        push()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, flags: Int, gravity: Int) =
        WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT
        ).also { it.gravity = gravity }

    private fun buildBubble() {
        val b = TextView(this).apply {
            text = "◐"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC000000.toInt())
            }
            setOnClickListener {
                panel?.visibility = if (panel?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        bubble = b
        val p = lp(dp(48), dp(48), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, Gravity.END or Gravity.CENTER_VERTICAL)
        wm.addView(b, p)
    }

    private fun buildPanel() {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xEE111111.toInt())
            }
            visibility = View.GONE
        }
        p.addView(row("Saturation", "sat"))
        p.addView(row("Contrast", "con"))
        p.addView(row("Brightness", "bri"))
        p.addView(Button(this).apply {
            text = "Reset to 100"
            setOnClickListener { bars.forEach { it.progress = 100 } }
        })
        panel = p
        val params = lp(dp(300), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        params.y = dp(100)
        wm.addView(p, params)
    }

    private fun row(name: String, key: String): View {
        val label = TextView(this).apply { setTextColor(Color.WHITE) }
        val bar = SeekBar(this).apply {
            max = 300
            progress = prefs.getInt(key, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    label.text = "$name  $v"
                    prefs.edit().putInt(key, v).apply()
                    push()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        label.text = "$name  ${bar.progress}"
        bars.add(bar)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(bar)
        }
    }

    private fun push() {
        val s = prefs.getInt("sat", 100)
        val c = prefs.getInt("con", 100)
        val b = prefs.getInt("bri", 100)
        worker.execute {
            val ok = ColorEngine.apply(s, c, b)
            ui.post { fallbackDim(ok, b) }
        }
    }

    private fun fallbackDim(rootOk: Boolean, bri: Int) {
        if (rootOk || bri >= 100) {
            dim?.let { wm.removeView(it) }
            dim = null
            return
        }
        if (!warned) {
            warned = true
            Toast.makeText(this, "No root: saturation/contrast unavailable, dimming only", Toast.LENGTH_LONG).show()
        }
        if (dim == null) {
            val v = View(this)
            wm.addView(v, lp(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                Gravity.TOP
            ))
            dim = v
        }
        val alpha = ((1f - bri / 100f) * 0.9f * 255).toInt()
        dim!!.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }

    override fun onDestroy() {
        bubble?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        dim?.let { wm.removeView(it) }
        worker.execute { ColorEngine.reset() }
        worker.shutdown()
        super.onDestroy()
    }
}
