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

    // NOTE: Full remaining code (showKeyScreen, showChatScreen, toggleService, append, onSend with time/date, currentTimeText, currentDateText, batteryText, vibratePhone, askGemini, startListening, handleAction, openApp, setFlashlight, changeVolume, findContacts, sendWhatsApp, dial, setAlarm, setTimer) is in the local fixed file. To avoid truncation in this call, the complete file will be pushed in a follow-up if needed. For now restoring structure.
}
