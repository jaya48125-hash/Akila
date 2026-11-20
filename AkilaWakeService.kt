package com.akila.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class AkilaWakeService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var triggered = false

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Akila::VoskWakeLock").apply {
            acquire(60 * 60 * 1000L)
        }
        startAsForeground()
        initVosk()
    }

    private fun startAsForeground() {
        val channelId = "akila_wake_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "AKILA Offline Wake",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val notif = Notification.Builder(this, channelId)
            .setContentTitle("AKILA Listening (Offline)")
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

    private fun initVosk() {
        StorageService.unpack(
            this,
            "model-en-in",
            "model",
            { loadedModel ->
                model = loadedModel
                val rec = Recognizer(loadedModel, 16000.0f)
                speechService = SpeechService(rec, 16000.0f)
                speechService?.startListening(this)
            },
            { _ -> stopSelf() }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun checkWakeWord(jsonText: String?) {
        if (triggered || jsonText.isNullOrBlank()) return
        val lower = jsonText.lowercase()
        val variants = listOf("akila", "akhila", "akela", "aquila", "killa")
        if (variants.any { lower.contains(it) }) {
            triggered = true
            speechService?.stop()
            val launch = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("wake_spoken", "akila")
            }
            startActivity(launch)
            stopSelf()
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        checkWakeWord(hypothesis)
    }

    override fun onResult(hypothesis: String?) {
        checkWakeWord(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        checkWakeWord(hypothesis)
    }

    override fun onError(exception: Exception?) {}

    override fun onTimeout() {}

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        model?.close()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
