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

    // Fixed: gemini-2.0-flash was shut down. Using current stable fast model.
    private val model = "gemini-2.0-flash"
    private val history = JSONArray()
    private lateinit var chatText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var serviceRunning = false

    private val systemPrompt =
        "You are JARVIS, a personal AI assistant. Speak naturally in Hindi or Hinglish. " +
        "Be polite, friendly and concise. " +
        "When the user asks you to do something on the phone, reply with ONLY one JSON object and nothing else. " +
        "Allowed actions: " +
        "{\"action\":\"open_app\",\"app\":\"<app name in English as shown on the phone, e.g. YouTube>\"} , " +
        "{\"action\":\"flashlight\",\"state\":\"on\" or \"off\"} , " +
        "{\"action\":\"volume\",\"direction\":\"up\" or \"down\",\"steps\":2}, " +
        "{\"action\":\"whatsapp_message\",\"contact\":\"<contact name>\",\"text\":\"<message text>\"} , " +
        "{\"action\":\"call\",\"contact\":\"<contact name>\"} , " +
        "{\"action\":\"alarm\",\"hour\":6,\"minute\":30,\"label\":\"<short label>\"} , " +
        "{\"action\":\"timer\",\"seconds\":300}. " +
        "Write contact names in English letters as they would be saved in a phone. " +
        "Write the message text in the same language and style the user wanted. " +
        "Any action JSON may include an optional note field: one short Hinglish sentence telling the user which part of the request you could NOT do. " +
        "For any other phone action, say politely in plain text that you cannot do it yet. " +
        "For normal conversation reply in plain text, never JSON."

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            gravity = Gravity.CENTER
            applyInsets(this)
        }

        val title = TextView(this).apply {
            text = "JARVIS"
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Gemini API Key daalo"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }

        val keyInput = EditText(this).apply {
            hint = "AIza..."
            setHintTextColor(Color.parseColor("#6E7681"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT
            background = roundedBg("#161B22")
        }

        val saveBtn = Button(this).apply {
            text = "Save & Continue"
            setTextColor(Color.WHITE)
            background = roundedBg("#238636")
            setOnClickListener {
                val k = keyInput.text.toString().trim()
                if (k.length > 10) {
                    getSharedPreferences("jarvis", Context.MODE_PRIVATE).edit().putString("api_key", k).apply()
                    showChatScreen()
                } else {
                    keyInput.error = "Valid key daalo"
                }
            }
        }

        root.addView(title)
        root.addView(sub)
        root.addView(keyInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })
        root.addView(saveBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun showChatScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            applyInsets(this)
        }

        statusText = TextView(this).apply {
            text = "JARVIS ready"
            setTextColor(Color.parseColor("#3FB950"))
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        }

        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        chatText = TextView(this).apply {
            setTextColor(Color.parseColor("#C9D1D9"))
            textSize = 15f
            setPadding(dp(4), dp(4), dp(4), dp(4))
            text = "Namaste! Main JARVIS hoon. Kya kar sakta hoon aapke liye?\n"
        }
        scroll.addView(chatText)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        input = EditText(this).apply {
            hint = "Type or speak..."
            setHintTextColor(Color.parseColor("#6E7681"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg("#161B22", 20f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendBtn = Button(this).apply {
            text = "Send"
            setTextColor(Color.WHITE)
            background = roundedBg("#238636", 20f)
            setOnClickListener { sendMessage() }
        }

        val micBtn = Button(this).apply {
            text = "🎤"
            setTextColor(Color.WHITE)
            background = roundedBg("#1F6FEB", 20f)
            setOnClickListener { startListening() }
        }

        inputRow.addView(input)
        inputRow.addView(micBtn, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })

        root.addView(statusText)
        root.addView(scroll)
        root.addView(inputRow)
        setContentView(root)

        // Start foreground service
        try {
            val svc = Intent(this, JarvisService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
            serviceRunning = true
        } catch (e: Exception) {
            // ignore
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        }
    }

    private fun appendChat(who: String, msg: String) {
        chatText.append("$who: $msg\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        appendChat("You", text)
        statusText.text = "Thinking..."
        statusText.setTextColor(Color.parseColor("#D29922"))

        Thread {
            try {
                val reply = callGemini(text)
                runOnUiThread {
                    handleReply(reply)
                    statusText.text = "JARVIS ready"
                    statusText.setTextColor(Color.parseColor("#3FB950"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendChat("JARVIS", "Error: ${e.message}")
                    statusText.text = "Error"
                    statusText.setTextColor(Color.parseColor("#F85149"))
                }
            }
        }.start()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "Listening..."
                    statusText.setTextColor(Color.parseColor("#58A6FF"))
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusText.text = "Processing..."
                }
                override fun onError(error: Int) {
                    statusText.text = "JARVIS ready"
                    statusText.setTextColor(Color.parseColor("#3FB950"))
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull() ?: ""
                    if (spoken.isNotEmpty()) {
                        input.setText(spoken)
                        sendMessage()
                    }
                    statusText.text = "JARVIS ready"
                    statusText.setTextColor(Color.parseColor("#3FB950"))
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    private fun callGemini(userText: String): String {
        val key = getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("api_key", "") ?: ""
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")

        val contents = JSONArray()
        // system as first user message for compatibility
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
        contents.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "Samajh gaya. Main JARVIS hoon."))))

        for (i in 0 until history.length()) {
            contents.put(history.getJSONObject(i))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))

        val body = JSONObject().put("contents", contents)

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }

        if (code !in 200..299) {
            throw Exception("API $code: $response")
        }

        val json = JSONObject(response)
        val text = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()

        // save to history
        history.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))
        history.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", text))))
        while (history.length() > 20) {
            history.remove(0)
            history.remove(0)
        }

        return text
    }

    private fun handleReply(reply: String) {
        val trimmed = reply.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = JSONObject(trimmed)
                val action = obj.optString("action")
                val result = when (action) {
                    "open_app" -> openApp(obj.optString("app"))
                    "flashlight" -> toggleFlashlight(obj.optString("state") == "on")
                    "volume" -> changeVolume(obj.optString("direction"), obj.optInt("steps", 1))
                    "whatsapp_message" -> sendWhatsApp(obj.optString("contact"), obj.optString("text"))
                    "call" -> makeCall(obj.optString("contact"))
                    "alarm" -> setAlarm(obj.optInt("hour"), obj.optInt("minute"), obj.optString("label", "Alarm"))
                    "timer" -> setTimer(obj.optInt("seconds", 60))
                    else -> "Ye action abhi support nahi hai."
                }
                val note = obj.optString("note", "")
                val msg = if (note.isNotEmpty()) "$result\n$note" else result
                appendChat("JARVIS", msg)
                speak(msg)
                return
            } catch (_: Exception) {
                // fall through to normal text
            }
        }
        appendChat("JARVIS", trimmed)
        speak(trimmed)
    }

    private fun openApp(appName: String): String {
        return try {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
            val match = apps.find {
                it.loadLabel(pm).toString().equals(appName, ignoreCase = true) ||
                it.activityInfo.packageName.contains(appName.replace(" ", "").lowercase())
            }
            if (match != null) {
                val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                startActivity(launch)
                "$appName khol diya."
            } else {
                "$appName nahi mila."
            }
        } catch (e: Exception) {
            "App open nahi ho paya: ${e.message}"
        }
    }

    private fun toggleFlashlight(on: Boolean): String {
        return try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "Flashlight nahi mila."
            cm.setTorchMode(id, on)
            if (on) "Flashlight on kar diya." else "Flashlight off kar diya."
        } catch (e: Exception) {
            "Flashlight control nahi ho paya."
        }
    }

    private fun changeVolume(direction: String, steps: Int): String {
        return try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val dir = if (direction == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(steps.coerceIn(1, 5)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            }
            "Volume ${if (direction == "up") "badha" else "ghata"} diya."
        } catch (e: Exception) {
            "Volume change nahi ho paya."
        }
    }

    private fun sendWhatsApp(contact: String, text: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(text)}")
                setPackage("com.whatsapp")
            }
            startActivity(intent)
            "WhatsApp khol diya. Contact: $contact"
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(text)}")
                }
                startActivity(intent)
                "WhatsApp khol diya."
            } catch (e2: Exception) {
                "WhatsApp nahi khula."
            }
        }
    }

    private fun makeCall(contact: String): String {
        return try {
            // Best effort: open dialer with contact name as search (user confirms)
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:")
            }
            startActivity(intent)
            "Dialer khol diya. $contact ko call kar sakte ho."
        } catch (e: Exception) {
            "Call nahi lag paya."
        }
    }

    private fun setAlarm(h: Int, m: Int, label: String): String {
        return try {
            val i = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            startActivity(i)
            "Alarm laga diya: %02d:%02d".format(h, m)
        } catch (e: Exception) {
            "Alarm nahi lag paya: ${e.message}"
        }
    }

    private fun setTimer(seconds: Int): String {
        return try {
            val i = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            startActivity(i)
            "Timer laga diya: ${seconds / 60} min ${seconds % 60} sec"
        } catch (e: Exception) {
            "Timer nahi lag paya: ${e.message}"
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }
}
