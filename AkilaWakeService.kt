package com.akila.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AkilaWakeService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Akila::WakeLock").apply {
            acquire(60 * 60 * 1000L)
        }
        startAsForeground()
    }

    private fun startAsForeground() {
        val channelId = "akila_wake_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "AKILA Background Wake",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val notif = Notification.Builder(this, channelId)
            .setContentTitle("AKILA Listening")
            .setContentText("'Hey Akila' என்று அழைக்கவும்...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            startListening()
        }
        return START_STICKY
    }

    private fun setBeepMuted(muted: Boolean) {
        try {
            val flag = if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, flag, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, flag, 0)
        } catch (_: Exception) {}
    }

    private fun startListening() {
        if (!running) return
        setBeepMuted(true)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (running) handler.postDelayed({ startListening() }, 400)
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank() && containsWakeWord(spoken)) {
                        wakeUpMainActivity(spoken)
                    } else if (running) {
                        handler.postDelayed({ startListening() }, 300)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase()
        val variants = listOf("akila", "akhila", "akela", "aquila", "killa", "அகிலா")
        return variants.any { lower.contains(it) }
    }

    private fun wakeUpMainActivity(spoken: String) {
        setBeepMuted(false)
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("wake_spoken", spoken)
        }
        startActivity(launch)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        setBeepMuted(false)
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
