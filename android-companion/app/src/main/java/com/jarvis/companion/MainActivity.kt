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
import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager

class MainActivity : Activity() {

    private val model = "gemini-3.5-flash-lite"
    private val history = JSONArray()
    private lateinit var chatText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private val systemPrompt =
        "You are JARVIS, a personal AI assistant. Speak naturally in Hindi or Hinglish. " +
        "Be polite, friendly and concise. " +
        "When the user asks you to do something on the phone, reply with ONLY one JSON object and nothing else. " +
        "Allowed actions: " +
        "{\"action\":\"open_app\",\"app\":\"<app name in English as shown on the phone, e.g. YouTube>\"} , " +
        "{\"action\":\"flashlight\",\"state\":\"on\" or \"off\"} , " +
        "{\"action\":\"volume\",\"direction\":\"up\" or \"down\",\"steps\":2}. " +
        "For any other phone action, say politely in plain text that you cannot do it yet. " +
        "For normal conversation reply in plain text, never JSON."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN")
        }
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
        val mic = Button(this).apply {
            text = "🎤"
            setOnClickListener { startListening() }
        }
        row.addView(mic)
        row.addView(send)

        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(row)
        setContentView(root)

        append("JARVIS", "Namaste! Main JARVIS hoon. Battery status, vibrate phone, ya kuch bhi poochho.")
    }

    private fun append(who: String, msg: String) {
        if (who == "JARVIS" && !msg.startsWith("Error")) {
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        }
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
                val act = extractJson(answer)
                if (act != null) runOnUiThread { handleAction(act) } else append("JARVIS", answer)
            } catch (e: Exception) {
                if (history.length() > 0) history.remove(history.length() - 1)
                append("JARVIS", "Error: ${e.message}")
            }
        }.start()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            append("JARVIS", "Is phone me speech recognition available nahi hai.")
            return
        }
        tts?.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) { input.setText(t); onSend() }
            }
            override fun onError(error: Int) {
                input.hint = "Kuch likho..."
                if (error != 11 && error != 5) append("JARVIS", "Sun nahi paya (error $error). Dobara try karo.")
            }
            override fun onReadyForSpeech(p: Bundle?) { input.hint = "Sun raha hoon..." }
            override fun onEndOfSpeech() { input.hint = "Kuch likho..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        recognizer?.startListening(i)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
    }

    override fun onDestroy() {
        tts?.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }

    private fun extractJson(s: String): JSONObject? {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            JSONObject(s.substring(a, b + 1)).takeIf { it.has("action") }
        } catch (e: Exception) {
            null
        }
    }

    private fun handleAction(a: JSONObject) {
        val desc: String
        val doIt: () -> String
        when (a.optString("action")) {
            "open_app" -> {
                val name = a.optString("app").trim()
                desc = "App kholna hai: $name?"
                doIt = { openApp(name) }
            }
            "flashlight" -> {
                val on = a.optString("state").lowercase() != "off"
                desc = if (on) "Flashlight on karni hai?" else "Flashlight off karni hai?"
                doIt = { setFlashlight(on) }
            }
            "volume" -> {
                val up = a.optString("direction").lowercase() != "down"
                val steps = a.optInt("steps", 2).coerceIn(1, 5)
                desc = if (up) "Volume badhana hai?" else "Volume kam karna hai?"
                doIt = { changeVolume(up, steps) }
            }
            else -> {
                append("JARVIS", "Ye kaam abhi allowed nahi hai.")
                return
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Permission chahiye")
            .setMessage(desc)
            .setPositiveButton("Allow") { _, _ -> append("JARVIS", doIt()) }
            .setNegativeButton("Cancel") { _, _ -> append("JARVIS", "Cancel kiya.") }
            .show()
    }

    private fun openApp(name: String): String {
        if (name.isEmpty()) return "App ka naam samajh nahi aaya."
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val q = name.lowercase()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "$name naam ka app nahi mila."
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "$name khul nahi paya."
        startActivity(intent)
        return "$name khol diya."
    }

    private fun setFlashlight(on: Boolean): String {
        return try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "Is phone me flashlight nahi mili."
            cm.setTorchMode(id, on)
            if (on) "Flashlight on kar di." else "Flashlight off kar di."
        } catch (e: Exception) {
            "Flashlight nahi chal payi: ${e.message}"
        }
    }

    private fun changeVolume(up: Boolean, steps: Int): String {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        repeat(steps) {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                if (it == steps - 1) AudioManager.FLAG_SHOW_UI else 0
            )
        }
        return if (up) "Volume badha diya." else "Volume kam kar diya."
    }
}
