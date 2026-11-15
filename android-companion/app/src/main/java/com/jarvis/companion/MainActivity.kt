package com.jarvis.companion

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val model = "gemini-3.5-flash-lite"
    private val history = JSONArray()
    private lateinit var chatText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    private val systemPrompt =
        "You are JARVIS, a personal AI assistant. Speak naturally in Hindi or Hinglish. " +
        "Be polite, friendly and concise. Never claim to control the phone. " +
        "The app itself handles only battery status and vibration."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("api_key", "")
        if (key.isNullOrEmpty()) showKeyScreen() else showChatScreen()
    }

    private fun applyInsets(v: LinearLayout) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(32, insets.systemWindowInsetTop + 16, 32, insets.systemWindowInsetBottom + 16)
            insets
        }
    }

    private fun showKeyScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        applyInsets(layout)
        val label = TextView(this).apply {
            text = "Gemini API key daalo (sirf is phone me save hogi)"
            textSize = 16f
        }
        val keyInput = EditText(this).apply {
            hint = "API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                val k = keyInput.text.toString().trim()
                if (k.isNotEmpty()) {
                    getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                        .edit().putString("api_key", k).apply()
                    showChatScreen()
                }
            }
        }
        layout.addView(label)
        layout.addView(keyInput)
        layout.addView(save)
        setContentView(layout)
    }

    private fun showChatScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        applyInsets(root)

        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        chatText = TextView(this).apply { textSize = 16f }
        scroll = ScrollView(this).apply { addView(chatText) }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Kuch likho..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { onSend() }
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(send)

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(row)
        setContentView(root)

        append("JARVIS", "Namaste! Main JARVIS hoon. Battery status, vibrate phone, ya kuch bhi poochho.")
    }

    private fun append(who: String, msg: String) {
        runOnUiThread {
            chatText.append("$who: $msg\n\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun onSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        append("You", text)
        val low = text.lowercase()

        val batteryWords = listOf("battery status", "battery level", "battery kitni", "battery check", "phone battery")
        val vibrateWords = listOf("vibrate phone", "phone vibrate", "vibrate my phone")

        when {
            batteryWords.any { low.contains(it) } -> append("JARVIS", batteryText())
            vibrateWords.any { low.contains(it) } -> {
                AlertDialog.Builder(this)
                    .setTitle("Permission chahiye")
                    .setMessage("Phone vibrate karna hai?")
                    .setPositiveButton("Allow") { _, _ ->
                        vibratePhone()
                        append("JARVIS", "Phone vibrate kar diya.")
                    }
                    .setNegativeButton("Cancel") { _, _ -> append("JARVIS", "Cancel kiya.") }
                    .show()
            }
            else -> askGemini(text)
        }
    }

    private fun batteryText(): String {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return "Battery $pct percent hai, " + if (charging) "charger laga hai." else "charger nahi laga hai."
    }

    private fun vibratePhone() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun askGemini(text: String) {
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("api_key", "") ?: ""
        history.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
        )
        Thread {
            try {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))))
                    .put("contents", history)

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key)
                conn.connectTimeout = 20000
                conn.readTimeout = 120000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) throw RuntimeException("API error $code")
                val resp = conn.inputStream.bufferedReader().use { it.readText() }

                val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (j in 0 until parts.length()) sb.append(parts.getJSONObject(j).optString("text"))
                val answer = sb.toString().ifEmpty { "(khali jawab aaya)" }

                history.put(
                    JSONObject().put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", answer)))
                )
                while (history.length() > 30) {
                    history.remove(0)
                    history.remove(0)
                }
                append("JARVIS", answer)
            } catch (e: Exception) {
                if (history.length() > 0) history.remove(history.length() - 1)
                append("JARVIS", "Error: ${e.message}")
            }
        }.start()
    }
}
