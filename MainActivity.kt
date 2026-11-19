package com.akila.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
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
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var micButton: Button
    private lateinit var wakeButton: Button
    private lateinit var langButton: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var currentLocale = "en-IN"
    private var handsFreeMode = false
    private var awaitingCommand = false
    private var isSpeaking = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "🔑 API Key"
            textSize = 12f
            setOnClickListener { showApiKeyDialog() }
        }

        headerRow.addView(title)
        headerRow.addView(keyButton)

        statusView = TextView(this).apply {
            text = "Hands-Free ON செய்து 'Hey Akila' எனப் பேசுங்கள்"
            textSize = 14f
            setTextColor(Color.parseColor("#93C5FD"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 20)
        }

        logView = TextView(this).apply {
            text = "AKILA தயார் நிலையில் உள்ளது...\n\n"
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
            text = "🎙️ Hands-Free ('Hey Akila'): OFF"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            setOnClickListener { toggleHandsFree() }
        }

        langButton = Button(this).apply {
            text = "மொழி: English / Tanglish"
            setOnClickListener {
                currentLocale = if (currentLocale == "en-IN") "ta-IN" else "en-IN"
                text = if (currentLocale == "en-IN") "மொழி: English / Tanglish" else "மொழி: தமிழ் (Tamil)"
                tts?.language = if (currentLocale == "ta-IN") Locale("ta", "IN") else Locale("en", "IN")
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
    }

    private fun toggleHandsFree() {
        handsFreeMode = !handsFreeMode
        if (handsFreeMode) {
            wakeButton.text = "🎙️ Hands-Free ('Hey Akila'): ON"
            wakeButton.setBackgroundColor(Color.parseColor("#15803D"))
            statusView.text = "'Hey Akila' என்று சொல்லுங்கள்..."
            startListening()
        } else {
            wakeButton.text = "🎙️ Hands-Free ('Hey Akila'): OFF"
            wakeButton.setBackgroundColor(Color.parseColor("#334155"))
            statusView.text = "Hands-Free நிறுத்தப்பட்டது."
            speechRecognizer?.cancel()
        }
    }

    private fun showApiKeyDialog() {
        val prefs = getSharedPreferences("akila_prefs", Context.MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "AIzaSy..."
            setText(prefs.getString("gemini_key", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Gemini API Key")
            .setMessage("AIzaSy... எனத் தொடங்கும் Gemini API Key-ஐ உள்ளிடவும்:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("gemini_key", input.text.toString().trim()).apply()
                speak("API Key saved")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    if (handsFreeMode) {
                        mainHandler.postDelayed({ startListening() }, 500)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
        }
    }

    private fun requestAllPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
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

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusView.text = if (handsFreeMode && !awaitingCommand) {
                        "'Hey Akila' என அழைக்கவும்..."
                    } else {
                        "AKILA கேட்கிறது..."
                    }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusView.text = "செயலாக்குகிறது..."
                }
                override fun onError(error: Int) {
                    if (handsFreeMode && !isSpeaking) {
                        mainHandler.postDelayed({ startListening() }, 700)
                    } else {
                        statusView.text = "மீண்டும் முயற்சிக்கவும் (Error $error)"
                    }
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        processSpokenInput(spoken)
                    } else if (handsFreeMode && !isSpeaking) {
                        mainHandler.postDelayed({ startListening() }, 500)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase()
        val wakeVariants = listOf("akila", "akhila", "akela", "aquila", "அகிலா")
        return wakeVariants.any { lower.contains(it) }
    }

    private fun stripWakeWord(text: String): String {
        val patterns = listOf("hey akila", "hi akila", "hey akhila", "hi akhila", "akila", "akhila", "akela", "aquila", "அகிலா")
        var out = text.lowercase().trim()
        for (p in patterns) {
            out = out.replace(p, " ")
        }
        return out.replace(Regex("\\s+"), " ").trim()
    }

    private fun processSpokenInput(spoken: String) {
        if (handsFreeMode && !awaitingCommand) {
            if (!containsWakeWord(spoken)) {
                mainHandler.postDelayed({ startListening() }, 400)
                return
            }
            val afterWake = stripWakeWord(spoken)
            if (afterWake.isBlank()) {
                awaitingCommand = true
                speak("Yes, I am listening")
                return
            }
            appendLog("நீங்கள்: $spoken")
            handleCommand(afterWake)
        } else {
            awaitingCommand = false
            appendLog("நீங்கள்: $spoken")
            handleCommand(spoken)
        }
    }

    private fun cleanTanglish(text: String): String {
        val fillers = listOf("hey akila", "hi akila", "akila", "akhila", "pannu", "panu", "podu", "please", "and", "the", "app")
        var cleaned = text.lowercase().trim()
        for (word in fillers) {
            cleaned = cleaned.replace(Regex("\\b$word\\b"), " ")
        }
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    private fun handleCommand(rawText: String) {
        val cmd = cleanTanglish(rawText)

        when {
            cmd.contains("call") || cmd.contains("கால்") -> {
                val name = cmd.replace("call to", "")
                    .replace("call", "")
                    .replace("ku", "")
                    .replace("கால்", "")
                    .trim()
                callContact(name)
            }
            cmd.contains("torch") || cmd.contains("flash") || cmd.contains("light") || cmd.contains("டார்ச்") -> {
                val turnOn = !cmd.contains("off") && !cmd.contains("ஆஃப்")
                toggleTorch(turnOn)
            }
            cmd.contains("open") || cmd.contains("திற") -> {
                val appName = cmd.replace("open", "").replace("திற", "").trim()
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
            else -> askGemini(rawText)
        }
    }

    private fun askGemini(prompt: String) {
        val apiKey = getSharedPreferences("akila_prefs", Context.MODE_PRIVATE)
            .getString("gemini_key", "") ?: ""

        if (apiKey.isBlank()) {
            speak("Offline mode-ல் உள்ளேன். AI பதில்களுக்கு மேலே உள்ள API Key-ஐ சேமிக்கவும்.")
            return
        }

        statusView.text = "AKILA சிந்திக்கிறது..."
        Thread {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 20000
                }

                val body = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(
                            JSONObject().put("text", "You are AKILA, a concise, friendly personal voice assistant. Reply briefly in the same language (Tamil, English, or Tanglish): $prompt")
                        ))
                    ))
                }

                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code !in 200..299) {
                    val errMsg = try {
                        JSONObject(responseText).optJSONObject("error")?.optString("message") ?: "HTTP $code"
                    } catch (_: Exception) {
                        "HTTP $code"
                    }
                    runOnUiThread { speak("API பிழை: $errMsg") }
                    return@Thread
                }

                val json = JSONObject(responseText)
                val reply = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                runOnUiThread { speak(reply) }
            } catch (e: Exception) {
                runOnUiThread {
                    speak("இணையப் பிழை: ${e.localizedMessage}")
                }
            }
        }.start()
    }

    private fun callContact(nameQuery: String) {
        if (nameQuery.isBlank()) {
            speak("Please tell me who to call")
            return
        }
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        var matchedName: String? = null
        var matchedNumber: String? = null
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val cName = it.getString(nameIdx) ?: continue
                if (cName.lowercase().contains(nameQuery.lowercase())) {
                    matchedName = cName
                    matchedNumber = it.getString(numIdx)
                    break
                }
            }
        }
        if (matchedNumber != null) {
            speak("Calling $matchedName")
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$matchedNumber")))
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
        } catch (e: Exception) {
            speak("Unable to control torch")
        }
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
        appendLog("AKILA: $message")
        statusView.text = message
        val params = Bundle()
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "akila_tts")
    }

    private fun appendLog(line: String) {
        logView.append("$line\n\n")
    }

    override fun onDestroy() {
        handsFreeMode = false
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
