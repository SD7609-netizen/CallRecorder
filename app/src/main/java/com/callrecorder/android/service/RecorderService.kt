package com.callrecorder.android.service

import android.app.*
import android.content.Intent
import android.media.MediaRecorder
import android.os.*
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import com.callrecorder.android.App
import com.callrecorder.android.MainActivity
import com.callrecorder.android.R
import com.callrecorder.android.data.AppDatabase
import com.callrecorder.android.data.Recording
import com.callrecorder.android.util.Prefs
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.callrecorder.START"
        const val ACTION_STOP = "com.callrecorder.STOP"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_IS_INCOMING = "is_incoming"
        const val NOTIFICATION_ID = 1001
    }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMillis = 0L
    private var phoneNumber = ""
    private var isIncoming = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                startRecording()
            }
            ACTION_STOP -> stopRecordingAndSave()
        }
        return START_STICKY
    }

    private fun startRecording() {
        startForeground(NOTIFICATION_ID, buildNotification())

        val dir = getExternalFilesDir("Recordings") ?: filesDir
        dir.mkdirs()

        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val safeNumber = phoneNumber.replace(Regex("[^0-9+]"), "").ifEmpty { "unknown" }
        val direction = if (isIncoming) "IN" else "OUT"
        outputFile = File(dir, "${direction}_${safeNumber}_${dateStr}.m4a")
        startTimeMillis = System.currentTimeMillis()

        val started = tryStartMediaRecorder(Prefs.resolveAudioSource(this))
        if (!started) {
            val fallback = tryStartMediaRecorder(MediaRecorder.AudioSource.MIC)
            if (!fallback) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
        }

        if (Prefs.getVibrate(this)) vibrate(300)
    }

    private fun tryStartMediaRecorder(audioSource: Int): Boolean {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this)
            else
                @Suppress("DEPRECATION") MediaRecorder()

            recorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(Prefs.getAudioChannels(this@RecorderService))
                setAudioSamplingRate(Prefs.getSampleRate(this@RecorderService))
                setAudioEncodingBitRate(Prefs.getBitrate(this@RecorderService))
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopRecordingAndSave() {
        val recorder = mediaRecorder
        if (recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            recorder.stop()
            recorder.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        val file = outputFile
        if (file == null || !file.exists() || file.length() < 1000) {
            file?.delete()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val durationSec = (System.currentTimeMillis() - startTimeMillis) / 1000
        val contactName = resolveContactName(phoneNumber)

        scope.launch {
            try {
                AppDatabase.getInstance(this@RecorderService).recordingDao().insert(
                    Recording(
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        filePath = file.absolutePath,
                        dateMillis = startTimeMillis,
                        durationSeconds = durationSec,
                        fileSize = file.length(),
                        isIncoming = isIncoming
                    )
                )
            } catch (_: Exception) {}
        }

        if (Prefs.getVibrate(this)) vibrate(200)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun vibrate(ms: Long) {
        val v = getSystemService(Vibrator::class.java)
        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun resolveContactName(number: String): String {
        if (number.isBlank()) return ""
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val direction = if (isIncoming) "Входящий" else "Исходящий"
        val display = phoneNumber.ifEmpty { "неизвестный номер" }
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentTitle("Запись звонка")
            .setContentText("$direction: $display")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }
}
