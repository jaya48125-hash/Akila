package com.akila.assistant

import android.Manifest
import android.app.AlertDialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var micButton: Button
    private lateinit var wakeButton: Button
    private lateinit var langButton: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var currentLocale = "en-IN"
    private var handsFreeMode = false
    private var awaitingCommand = false
    private var isSpeaking = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val prefs = getSharedPreferences("akila_prefs", Context.MODE_PRIVATE)
        handsFreeMode = prefs.getBoolean("hands_free", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(48, 72, 48, 48)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "AKILA Assistant"
            textSize = 22f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val keyButton = Button(this).apply {
            text = "🔑 Groq Key"
            textSize = 12f
            setOnClickListener { showApiKeyDialog() }
        }

        headerRow.addView(title)
        headerRow.addView(keyButton)

        statusView = TextView(this).apply {
            text = "Siri-Level AKILA தயார்! ('Hey Akila' எனப் பேசுங்கள்)"
            textSize = 14f
            setTextColor(Color.parseColor("#93C5FD"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 20)
        }

        logView = TextView(this).apply {
            text = "Call, WhatsApp, Alarm, Timer, Maps, Music, Volume, Battery, AI அனைத்தும் தயார்!\n\n"
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(24, 24, 24, 24)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1E293B"))
            addView(logView)
        }
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        wakeButton = Button(this).apply {
            text = if (handsFreeMode) "🎙️ Hands-Free ('Hey Akila'): ON" else "🎙️ Hands-Free ('Hey Akila'): OFF"
            setBackgroundColor(Color.parseColor(if (handsFreeMode) "#15803D" else "#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener { toggleHandsFree() }
        }

        langButton = Button(this).apply {
            text = "மொழி: English / Tanglish"
            setOnClickListener {
                switchLanguage(if (currentLocale == "en-IN") "ta-IN" else "en-IN")
            }
        }

        micButton = Button(this).apply {
            text = "🎤  பேசு (Speak Now)"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setPadding(24, 34, 24, 34)
            setOnClickListener {
                awaitingCommand = true
                startListening()
            }
        }

        root.addView(headerRow)
        root.addView(statusView)
        root.addView(scroll, scrollParams)
        root.addView(wakeButton)
        root.addView(langButton)
        root.addView(micButton)
        setContentView(root)

        tts = TextToSpeech(this, this)
        requestAllPermissions()

        val wakeSpoken = intent?.getStringExtra("wake_spoken")
        if (!wakeSpoken.isNullOrBlank()) {
            intent.removeExtra("wake_spoken")
            mainHandler.postDelayed({ processSpokenInput(wakeSpoken) }, 400)
        }
    }

    override fun onResume() {
        super.onResume()
        stopService(Intent(this, AkilaWakeService::class.java))
        if (handsFreeMode && !isSpeaking) {
            mainHandler.postDelayed({ startListening() }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (handsFreeMode) {
            startForegroundService(Intent(this, AkilaWakeService::class.java))
        }
    }

    private fun setBeepMuted(muted: Boolean) {
        try {
            val flag = if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, flag, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, flag, 0)
        } catch (_: Exception) {}
    }

    private fun switchLanguage(localeCode: String) {
        currentLocale = localeCode
        langButton.text = if (currentLocale == "en-IN") "மொழி: English / Tanglish" else "மொழி: தமிழ் (Tamil)"
        tts?.language = if (currentLocale == "ta-IN") Locale("ta", "IN") else Locale("en", "IN")
    }

    private fun toggleHandsFree() {
        handsFreeMode = !handsFreeMode
        getSharedPreferences("akila_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hands_free", handsFreeMode).apply()

        if (handsFreeMode) {
            wakeButton.text = "🎙️ Hands-Free ('Hey Akila'): ON"
            wakeButton.setBackgroundColor(Color.parseColor("#15803D"))
            statusView.text = "'Hey Akila' என்று சொல்லுங்கள்..."
            startListening()
        } else {
            setBeepMuted(false)
            stopService(Intent(this, AkilaWakeService::class.java))
            wakeButton.text = "🎙️ Hands-Free ('Hey Akila'): OFF"
            wakeButton.setBackgroundColor(Color.parseColor("#334155"))
            statusView.text = "Hands-Free நிறுத்தப்பட்டது."
            speechRecognizer?.cancel()
        }
    }

    private fun showApiKeyDialog() {
        val prefs = getSharedPreferences("akila_prefs", Context.MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "gsk_..."
            setText(prefs.getString("groq_key", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Groq API Key")
            .setMessage("console.groq.com-ல் இருந்து gsk_... எனத் தொடங்கும் Key-ஐ உள்ளிடவும்:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("groq_key", input.text.toString().trim()).apply()
                speak("Groq API Key saved")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { isSpeaking = true }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    if (handsFreeMode) mainHandler.postDelayed({ startListening() }, 250)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { isSpeaking = false }
            })
        }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun startListening() {
        if (isSpeaking) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestAllPermissions()
            return
        }

        if (handsFreeMode && !awaitingCommand) setBeepMuted(true) else setBeepMuted(false)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusView.text = if (handsFreeMode && !awaitingCommand) "'Hey Akila' என அழைக்கவும்..." else "AKILA கேட்கிறது..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusView.text = "செயலாக்குகிறது..." }
                override fun onError(error: Int) {
                    if (handsFreeMode && !isSpeaking) mainHandler.postDelayed({ startListening() }, 300)
                    else statusView.text = "மீண்டும் முயற்சிக்கவும் (Error $error)"
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) processSpokenInput(spoken)
                    else if (handsFreeMode && !isSpeaking) mainHandler.postDelayed({ startListening() }, 250)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("akila", "akhila", "akela", "aquila", "killa", "அகிலா").any { lower.contains(it) }
    }

    private fun stripWakeWord(text: String): String {
        val patterns = listOf(
            "hey akila", "hi akila", "hai akila", "hey akhila", "hi akhila", "hai akhila",
            "hey akela", "hi akela", "hai akela", "hey killa", "hi killa", "hai killa",
            "akila", "akhila", "akela", "aquila", "killa", "அகிலா", "hai", "hey", "hi"
        )
        var out = text.lowercase().trim()
        for (p in patterns) out = out.replace(Regex("\\b$p\\b"), " ")
        return out.replace(Regex("\\s+"), " ").trim()
    }

    private fun processSpokenInput(spoken: String) {
        if (handsFreeMode && !awaitingCommand) {
            if (!containsWakeWord(spoken)) {
                mainHandler.postDelayed({ startListening() }, 250)
                return
            }
            val afterWake = stripWakeWord(spoken)
            if (afterWake.isBlank()) {
                awaitingCommand = true
                speak("Yes, listening")
                return
            }
            appendLog("நீங்கள்: $spoken")
            handleCommand(afterWake)
        } else {
            awaitingCommand = false
            val cleaned = stripWakeWord(spoken)
            if (cleaned.isBlank()) {
                awaitingCommand = true
                speak("Yes, listening")
                return
            }
            appendLog("நீங்கள்: $spoken")
            handleCommand(cleaned)
        }
    }

    private fun cleanTanglish(text: String): String {
        val fillers = listOf("pannu", "panu", "podu", "vai", "please", "and", "the", "app")
        var cleaned = text.lowercase().trim()
        for (word in fillers) cleaned = cleaned.replace(Regex("\\b$word\\b"), " ")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    private fun handleCommand(rawText: String) {
        val cmd = cleanTanglish(rawText)

        when {
            cmd == "tamil" || cmd == "தமிழ்" -> {
                switchLanguage("ta-IN")
                speak("இப்போது தமிழில் பேசுங்கள்")
            }
            cmd == "english" || cmd == "ஆங்கிலம்" -> {
                switchLanguage("en-IN")
                speak("Switched to English and Tanglish")
            }
            cmd.contains("time") || cmd.contains("மணி") -> {
                val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("The time is $now")
            }
            cmd.contains("date") || cmd.contains("தேதி") -> {
                val today = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
                speak("Today is $today")
            }
            cmd.contains("battery") || cmd.contains("பேட்டரி") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Battery level is $level percent")
            }
            cmd.contains("alarm") || cmd.contains("அலாரம்") -> setAlarmFromText(cmd)
            cmd.contains("timer") || cmd.contains("டைமர்") -> setTimerFromText(cmd)
            cmd.contains("volume") || cmd.contains("mute") || cmd.contains("சத்தம்") -> controlVolume(cmd)
            cmd.contains("whatsapp") && (cmd.contains("send") || cmd.contains("message") || cmd.contains("msg")) -> sendWhatsApp(cmd)
            cmd.contains("sms") || (cmd.contains("message") && !cmd.contains("whatsapp")) -> sendSms(cmd)
            cmd.contains("navigate") || cmd.contains("direction") || cmd.contains("route") || cmd.contains("maps") -> {
                val place = cmd.replace("navigate to", "").replace("directions to", "")
                    .replace("direction to", "").replace("route to", "").replace("maps", "").trim()
                openMaps(place)
            }
            cmd.contains("search") || cmd.contains("google") -> {
                val query = cmd.replace("search for", "").replace("search", "").replace("google", "").trim()
                searchWeb(query)
            }
            cmd.contains("take photo") || cmd.contains("take a photo") || cmd.contains("selfie") || cmd.contains("camera") -> {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Opening camera")
            }
            cmd.contains("call") || cmd.contains("கால்") -> {
                val name = cmd.replace("call to", "").replace("call", "").replace("ku", "").replace("கால்", "").trim()
                callContact(name)
            }
            cmd.contains("torch") || cmd.contains("flash") || cmd.contains("light") || cmd.contains("டார்ச்") -> {
                val turnOn = !cmd.contains("off") && !cmd.contains("ஆஃப்")
                toggleTorch(turnOn)
            }
            cmd.startsWith("play ") -> {
                val query = cmd.removePrefix("play ").replace("in spotify", "").trim()
                playMusic(query)
            }
            cmd.contains("open") || cmd.contains("go to") || cmd.contains("திற") -> {
                val appName = cmd.replace("open", "").replace("go to", "").replace("திற", "").trim()
                openAppByName(appName)
            }
            cmd.contains("wifi") || cmd.contains("wi-fi") -> {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
                speak("Opening Wi-Fi settings")
            }
            cmd.contains("bluetooth") -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                speak("Opening Bluetooth settings")
            }
            else -> askGroq(rawText)
        }
    }

    private fun setAlarmFromText(cmd: String) {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?").find(cmd)
        var hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 7
        val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
        if ((cmd.contains("pm") || cmd.contains("p.m")) && hour < 12) hour += 12
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        speak("Alarm set for $hour:${minute.toString().padStart(2, '0')}")
    }

    private fun setTimerFromText(cmd: String) {
        val num = Regex("(\\d+)").find(cmd)?.groupValues?.get(1)?.toIntOrNull() ?: 5
        val seconds = if (cmd.contains("second")) num else num * 60
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        speak("Timer set for $num ${if (cmd.contains("second")) "seconds" else "minutes"}")
    }

    private fun controlVolume(cmd: String) {
        setBeepMuted(false)
        when {
            cmd.contains("mute") -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                speak("Muted")
            }
            cmd.contains("down") || cmd.contains("low") || cmd.contains("decrease") || cmd.contains("kammi") -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                speak("Volume decreased")
            }
            else -> {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                speak("Volume increased")
            }
        }
    }

    private fun findContactNumber(nameQuery: String): Pair<String, String>? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val cName = it.getString(nameIdx) ?: continue
                if (cName.lowercase().contains(nameQuery.lowercase())) {
                    return Pair(cName, it.getString(numIdx))
                }
            }
        }
        return null
    }

    private fun sendWhatsApp(cmd: String) {
        val clean = cmd.replace("send whatsapp to", "").replace("whatsapp", "").replace("send", "").replace("message", "").trim()
        val parts = clean.split(" ", limit = 2)
        val name = parts.getOrNull(0) ?: ""
        val msg = parts.getOrNull(1) ?: "Hi"
        val contact = if (name.isNotBlank()) findContactNumber(name) else null
        if (contact != null) {
            val num = contact.second.replace(Regex("[^0-9+]"), "")
            val url = "https://wa.me/$num?text=${URLEncoder.encode(msg, "UTF-8")}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Opening WhatsApp for ${contact.first}")
        } else {
            openAppByName("whatsapp")
        }
    }

    private fun sendSms(cmd: String) {
        val clean = cmd.replace("send sms to", "").replace("send message to", "").replace("message", "").replace("sms", "").trim()
        val parts = clean.split(" ", limit = 2)
        val name = parts.getOrNull(0) ?: ""
        val msg = parts.getOrNull(1) ?: ""
        val contact = if (name.isNotBlank()) findContactNumber(name) else null
        val number = contact?.second ?: ""
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", msg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        speak("Opening SMS")
    }

    private fun openMaps(place: String) {
        val uri = Uri.parse("google.navigation:q=${URLEncoder.encode(place, "UTF-8")}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
            speak("Navigating to $place")
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${URLEncoder.encode(place, "UTF-8")}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("Searching $place on maps")
        }
    }

    private fun searchWeb(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        speak("Searching for $query")
    }

    private fun playMusic(query: String) {
        try {
            setBeepMuted(false)
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            speak("Playing $query")
            startActivity(intent)
        } catch (_: Exception) {
            openAppByName("spotify")
        }
    }

    private fun fetchActiveGroqModels(apiKey: String): List<String> {
        val fallback = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3-32b")
        return try {
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode !in 200..299) return fallback
            val data = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("data")
            val valid = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).getString("id")
                val low = id.lowercase()
                if (!low.contains("whisper") && !low.contains("tts") && !low.contains("guard") && !low.contains("orpheus")) {
                    valid.add(id)
                }
            }
            if (valid.isNotEmpty()) valid else fallback
        } catch (_: Exception) { fallback }
    }

    private fun askGroq(prompt: String) {
        val apiKey = getSharedPreferences("akila_prefs", Context.MODE_PRIVATE).getString("groq_key", "") ?: ""
        if (apiKey.isBlank()) {
            speak("Offline mode-ல் உள்ளேன். AI பதிலுக்கு மேலே உள்ள Groq Key-ஐ சேமிக்கவும்.")
            return
        }

        statusView.text = "AKILA சிந்திக்கிறது..."
        Thread {
            val models = fetchActiveGroqModels(apiKey)
            var lastError = "Unable to connect"

            for (model in models) {
                try {
                    val url = URL("https://api.groq.com/openai/v1/chat/completions")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $apiKey")
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 15000
                    }

                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are AKILA, an ultra-fast, friendly personal voice assistant. Reply in 1-2 complete short sentences in the same language (Tamil, English, or Tanglish).")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }

                    val body = JSONObject().apply {
                        put("model", model)
                        put("messages", messages)
                        put("max_tokens", 500)
                        put("temperature", 0.5)
                    }

                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                    if (code in 200..299) {
                        val reply = JSONObject(responseText).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                        if (reply.isNotBlank()) {
                            runOnUiThread { speak(reply) }
                            return@Thread
                        }
                    } else {
                        lastError = try {
                            JSONObject(responseText).optJSONObject("error")?.optString("message") ?: "HTTP $code"
                        } catch (_: Exception) { "HTTP $code" }
                    }
                } catch (e: Exception) {
                    lastError = e.localizedMessage ?: "Network error"
                }
            }
            runOnUiThread { speak("Groq பிழை: $lastError") }
        }.start()
    }

    private fun callContact(nameQuery: String) {
        if (nameQuery.isBlank()) {
            speak("Please tell me who to call")
            return
        }
        val contact = findContactNumber(nameQuery)
        if (contact != null) {
            speak("Calling ${contact.first}")
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.second}")))
        } else {
            speak("Contact $nameQuery not found")
        }
    }

    private fun toggleTorch(enable: Boolean) {
        try {
            val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cam.cameraIdList.firstOrNull() ?: return
            cam.setTorchMode(id, enable)
            speak(if (enable) "Torch turned on" else "Torch turned off")
        } catch (_: Exception) { speak("Unable to control torch") }
    }

    private fun openAppByName(appQuery: String) {
        if (appQuery.isBlank()) {
            speak("Which app should I open?")
            return
        }
        val pm = packageManager
        val launchableApps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        val q = appQuery.lowercase()
        val match = launchableApps.firstOrNull {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            label.contains(q) || q.contains(label) || (q.length >= 4 && label.contains(q.substring(0, 4)))
        }
        val launch = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
        if (launch != null) {
            speak("Opening ${pm.getApplicationLabel(match)}")
            startActivity(launch)
        } else {
            speak("App $appQuery not found")
        }
    }

    private fun speak(message: String) {
        setBeepMuted(false)
        appendLog("AKILA: $message")
        statusView.text = message
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, Bundle(), "akila_tts")
    }

    private fun appendLog(line: String) {
        logView.append("$line\n\n")
    }

    override fun onDestroy() {
        setBeepMuted(false)
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
