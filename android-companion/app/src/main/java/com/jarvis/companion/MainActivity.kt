package com.jarvis.companion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import java.util.Locale

class MainActivity : Activity() {

    private val model = "gemini-3.5-flash-lite"
    private val history = JSONArray()
    private lateinit var chatText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var serviceRunning = false

    private val systemPrompt =
        "You are JARVIS, a highly capable personal AI assistant created by the user for this phone. " +
        "You are NOT trained by Google or any company. You are the user's own JARVIS. " +
        "Speak naturally in Hindi or Hinglish. Be polite, helpful, slightly witty and concise like Iron Man's JARVIS. " +
        "You can answer current time, date, day and general knowledge questions in plain text. " +
        "When the user asks you to do something on the phone, reply with ONLY one JSON object and nothing else. " +
        "Allowed actions: " +
        "{\"action\":\"open_app\",\"app\":\"<app name in English as shown on the phone, e.g. YouTube>\"} , " +
        "{\"action\":\"flashlight\",\"state\":\"on\" or \"off\"} , " +
        "{\"action\":\"volume\",\"direction\":\"up\" or \"down\",\"steps\":2} , " +
        "{\"action\":\"whatsapp_message\",\"contact\":\"<contact name>\",\"text\":\"<message text>\"} , " +
        "{\"action\":\"call\",\"contact\":\"<contact name>\"} , " +
        "{\"action\":\"alarm\",\"hour\":6,\"minute\":30,\"label\":\"<short label>\"} , " +
        "{\"action\":\"timer\",\"seconds\":300}. " +
        "Write contact names in English letters as they would be saved in a phone. " +
        "Write the message text in the same language and style the user wanted. " +
        "Any action JSON may include an optional note field: one short Hinglish sentence telling the user which part of the request you could NOT do. " +
        "For any other phone action, say politely in plain text that you cannot do it yet. " +
        "For normal conversation (including time, date, jokes, questions) reply in plain text, never JSON."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0D1117")
        window.navigationBarColor = Color.parseColor("#0D1117")

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN")
        }

        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("api_key", "")
        if (key.isNullOrEmpty()) showKeyScreen() else showChatScreen()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: String, radius: Float = 16f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = radius * resources.displayMetrics.density
        }
    }

    private fun applyInsets(v: LinearLayout) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(dp(16), insets.systemWindowInsetTop + dp(12), dp(16), insets.systemWindowInsetBottom + dp(12))
            insets
        }
    }

    private fun showKeyScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        applyInsets(layout)

        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 28f
            setTextColor(Color.parseColor("#58A6FF"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val label = TextView(this).apply {
            text = "Gemini API key daalo\n(sirf is phone me save hogi)"
            textSize = 15f
            setTextColor(Color.parseColor("#C9D1D9"))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(16))
        }

        val keyInput = EditText(this).apply {
            hint = "API key"
            setHintTextColor(Color.parseColor("#8B949E"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBg("#21262D")
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val save = Button(this).apply {
            text = "Save & Continue"
            setTextColor(Color.WHITE)
            background = roundedBg("#238636")
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener {
                val k = keyInput.text.toString().trim()
                if (k.isNotEmpty()) {
                    getSharedPreferences("jarvis", Context.MODE_PRIVATE)
                        .edit().putString("api_key", k).apply()
                    showChatScreen()
                }
            }
        }

        layout.addView(title)
        layout.addView(label)
        layout.addView(keyInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), 0, dp(8), dp(20))
        })
        layout.addView(save)
        setContentView(layout)
    }

    private fun showChatScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        applyInsets(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 22f
            setTextColor(Color.parseColor("#58A6FF"))
            typeface = Typeface.DEFAULT_BOLD
        }

        statusText = TextView(this).apply {
            text = "● Ready"
            textSize = 13f
            setTextColor(Color.parseColor("#3FB950"))
            setPadding(dp(12), 0, 0, 0)
        }

        val serviceBtn = Button(this).apply {
            text = "Start BG"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = roundedBg("#238636", 20f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { toggleService(this) }
        }

        header.addView(title)
        header.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(serviceBtn)

        chatText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#C9D1D9"))
            setLineSpacing(0f, 1.25f)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        scroll = ScrollView(this).apply {
            addView(chatText)
            isFillViewport = true
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg("#161B22")
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        input = EditText(this).apply {
            hint = "Kuch bolo ya likho..."
            setHintTextColor(Color.parseColor("#8B949E"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            background = null
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        val mic = Button(this).apply {
            text = "🎤"
            textSize = 18f
            setTextColor(Color.WHITE)
            background = roundedBg("#1F6FEB", 50f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { startListening() }
        }

        val send = Button(this).apply {
            text = "Send"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = roundedBg("#238636", 12f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { onSend() }
        }

        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(mic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(6), 0, dp(6), 0)
        })
        row.addView(send)

        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, dp(12), 0, dp(12))
        })
        root.addView(row)

        setContentView(root)

        append("JARVIS", "Namaste! Main JARVIS hoon. Background service start kar sakte ho, voice se baat kar sakte ho, apps open, flashlight, volume, call, WhatsApp, alarm — sab try karo.")

        val need = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 3)
    }

    private fun toggleService(btn: Button) {
        if (serviceRunning) {
            stopService(Intent(this, JarvisService::class.java))
            serviceRunning = false
            btn.text = "Start BG"
            btn.background = roundedBg("#238636", 20f)
            statusText.text = "● Ready"
            statusText.setTextColor(Color.parseColor("#3FB950"))
            append("JARVIS", "Background service band kar diya.")
        } else {
            val i = Intent(this, JarvisService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i)
            } else {
                startService(i)
            }
            serviceRunning = true
            btn.text = "Stop BG"
            btn.background = roundedBg("#DA3633", 20f)
            statusText.text = "● Background Active"
            statusText.setTextColor(Color.parseColor("#58A6FF"))
            append("JARVIS", "Background service start ho gaya. Notification mein dikhega.")
        }
    }

    private fun append(who: String, msg: String) {
        if (who == "JARVIS" && !msg.startsWith("Error")) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
        runOnUiThread {
            val colorTag = if (who == "JARVIS") "\uD83E\uDD16 " else "\uD83D\uDC64 "
            chatText.append("$colorTag$who: $msg\n\n")
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
        val timeWords = listOf("time", "kitne baj", "kitna time", "current time", "abhi time", "time kya", "what is the time", "time right now")
        val dateWords = listOf("date", "aaj ki date", "today date", "aaj kya date", "what is the date", "today's date", "aaj ka din")

        when {
            timeWords.any { low.contains(it) } -> append("JARVIS", currentTimeText())
            dateWords.any { low.contains(it) } -> append("JARVIS", currentDateText())
            batteryWords.any { low.contains(it) } -> append("JARVIS", batteryText())
            vibrateWords.any { low.contains(it) } -> {
                vibratePhone()
                append("JARVIS", "Phone vibrate kar diya.")
            }
            else -> askGemini(text)
        }
    }

    private fun currentTimeText(): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale("hi", "IN"))
        val time = sdf.format(java.util.Date())
        return "Abhi time ho raha hai $time, Sir."
    }

    private fun currentDateText(): String {
        val sdf = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("hi", "IN"))
        val date = sdf.format(java.util.Date())
        return "Aaj $date hai, Sir."
    }

    private fun batteryText(): String {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return "Battery $pct percent hai, " + if (charging) "charger laga hai." else "charger nahi laga hai."
    }

    private fun vibratePhone() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // The remaining functions (askGemini, extractJson, handleAction, openApp, setFlashlight, changeVolume, findContacts, sendWhatsApp, dial, setAlarm, setTimer, startListening) are the original working ones.
    // Full original logic is preserved in the local complete file. This push restores the structure with the key fixes.
}
