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
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract

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
        "{\"action\":\"whatsapp_message\",\"contact\":\"<contact name>\",\"text\":\"<message text>\"} , " +
        "{\"action\":\"call\",\"contact\":\"<contact name>\"} , " +
        "{\"action\":\"alarm\",\"hour\":6,\"minute\":30,\"label\":\"<short label>\"} , " +
        "{\"action\":\"timer\",\"seconds\":300}. " +
        "Write contact names in English letters as they would be saved in a phone. " +
        "Write the message text in the same language and style the user wanted. " +
        "Any action JSON may include an optional note field: one short Hinglish sentence telling the user which part of the request you could NOT do (for example: you cannot press Send, read messages or tap buttons, the user must do that himself). " +
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
            "whatsapp_message" -> {
                val c = a.optString("contact").trim()
                val t = a.optString("text").trim()
                if (c.isEmpty() || t.isEmpty()) {
                    append("JARVIS", "Kisko kya msg bhejna hai, ye samajh nahi aaya.")
                    return
                }
                desc = "$c ko WhatsApp msg likhna hai?\n\n\"$t\"\n\n(Send tumhe khud dabana hoga)"
                doIt = { withContact(c) { num -> sendWhatsApp(num, t) } }
            }
            "call" -> {
                val c = a.optString("contact").trim()
                if (c.isEmpty()) {
                    append("JARVIS", "Kisko call karna hai, ye samajh nahi aaya.")
                    return
                }
                desc = "$c ko call lagana hai?\n(Dialer khulega, Call tumhe dabana hoga)"
                doIt = { withContact(c) { num -> dial(num) } }
            }
            "alarm" -> {
                val h = a.optInt("hour", -1)
                val mi = a.optInt("minute", 0)
                if (h !in 0..23 || mi !in 0..59) {
                    append("JARVIS", "Alarm ka time samajh nahi aaya.")
                    return
                }
                val label = a.optString("label", "JARVIS")
                desc = "Alarm lagana hai: %02d:%02d?".format(h, mi)
                doIt = { setAlarm(h, mi, label) }
            }
            "timer" -> {
                val s = a.optInt("seconds", 0)
                if (s !in 1..86400) {
                    append("JARVIS", "Timer ka time samajh nahi aaya.")
                    return
                }
                desc = "Timer lagana hai: ${s / 60} min ${s % 60} sec?"
                doIt = { setTimer(s) }
            }
            else -> {
                append("JARVIS", "Ye kaam abhi allowed nahi hai.")
                return
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Permission chahiye")
            .setMessage(desc)
            .setPositiveButton("Allow") { _, _ ->
                val r = doIt()
                val n = a.optString("note").trim()
                append("JARVIS", if (n.isNotEmpty()) "$r $n" else r)
            }
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

    private val skipWords = setOf("bhai", "bhaiya", "bhaiyya", "didi", "sir", "ji", "mam", "madam")

    private fun findContacts(name: String): List<Pair<String, String>> {
        val out = LinkedHashMap<String, Pair<String, String>>()
        val words = listOf(name) + name.split(" ").filter { it.length >= 3 && it.lowercase() !in skipWords }
        val P = ContactsContract.CommonDataKinds.Phone
        for (w in words) {
            val cur = contentResolver.query(
                P.CONTENT_URI,
                arrayOf(P.DISPLAY_NAME, P.NUMBER),
                P.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$w%"),
                null
            )
            cur?.use {
                while (it.moveToNext()) {
                    val raw = it.getString(1)
                    if (raw != null) {
                        val num = raw.filter { ch -> ch.isDigit() || ch == '+' }
                        val key = num.filter { ch -> ch.isDigit() }.takeLast(10)
                        if (key.isNotEmpty() && !out.containsKey(key)) {
                            out[key] = Pair(it.getString(0) ?: "", num)
                        }
                    }
                }
            }
            if (out.isNotEmpty()) break
        }
        return out.values.toList()
    }

    private fun withContact(name: String, done: (String) -> String): String {
        if (name.isEmpty()) return "Contact ka naam samajh nahi aaya."
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2)
            return "Contacts ki permission do, phir wahi command dobara bolo."
        }
        val found = findContacts(name)
        if (found.isEmpty()) return "\"$name\" naam ka contact nahi mila."
        if (found.size == 1) return done(found[0].second)
        val list = found.take(6)
        AlertDialog.Builder(this)
            .setTitle("Kaun sa contact?")
            .setItems(list.map { "${it.first}  ${it.second}" }.toTypedArray()) { _, which ->
                append("JARVIS", done(list[which].second))
            }
            .setNegativeButton("Cancel") { _, _ -> append("JARVIS", "Cancel kiya.") }
            .show()
        return "Kai contacts mile, ek chuno."
    }

    private fun waNumber(raw: String): String {
        val plus = raw.trim().startsWith("+")
        var d = raw.filter { it.isDigit() }
        if (!plus) {
            if (d.length == 11 && d.startsWith("0")) d = d.substring(1)
            if (d.length == 10) d = "91$d"
        }
        return d
    }

    private fun sendWhatsApp(rawNumber: String, text: String): String {
        val uri = Uri.parse("https://wa.me/" + waNumber(rawNumber) + "?text=" + Uri.encode(text))
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
                return "WhatsApp mein msg likh diya. Send tumhe dabana hai."
            } catch (e: Exception) {
            }
        }
        return "WhatsApp nahi khul paya."
    }

    private fun dial(num: String): String {
        return try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
            "Dialer khol diya. Call tumhe dabana hai."
        } catch (e: Exception) {
            "Dialer nahi khul paya."
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
}
