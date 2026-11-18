package com.example.satboost

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { f() }
        }

        root.addView(TextView(this).apply { text = "SatBoost"; textSize = 30f })
        root.addView(TextView(this).apply {
            text = "Saturation, contrast and brightness (0-300) over every app.\n" +
                "Root is needed for the real effect. Without root you only get brightness dimming."
            setPadding(0, pad / 2, 0, pad)
        })
        root.addView(btn("1. Allow display over other apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(btn("2. Start") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Tap the ◐ bubble in any app", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(btn("Stop and reset") {
            stopService(Intent(this, OverlayService::class.java))
        })
        setContentView(root)
    }
}
