package com.jarvis.companion

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "JARVIS COMPANION"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "Android Core Ready\nAccessibility: Not configured"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val settingsButton = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(settingsButton)

        setContentView(layout)
    }
}
