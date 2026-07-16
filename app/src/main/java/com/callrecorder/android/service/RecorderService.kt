package com.callrecorder.android.service

import android.app.*
import android.content.Intent
import android.graphics.Color
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var overlayManager: RecordingOverlayManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlayManager = RecordingOverlayManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                startRecording()
            }
            ACTION_STOP -> stopRecordingAndPrompt()
        }
        return START_STICKY
    }

    private fun startRecording() {
        startForeground(NOTIFICATION_ID, buildRecordingNotification())

        val dir = getExternalFilesDir("Recordings") ?: filesDir
        dir.mkdirs()

        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val safeNumber = phoneNumber.replace(Regex("[^0-9+]"), "").ifEmpty { "unknown" }
        val direction = if (isIncoming) "IN" else "OUT"
        outputFile = File(dir, "${direction}_${safeNumber}_${dateStr}.m4a")
        startTimeMillis = System.currentTimeMillis()

        val started = tryStartMediaRecorder(Prefs.resolveAudioSource(this))
        if (!started && !tryStartMediaRecorder(MediaRecorder.AudioSource.MIC)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (Prefs.getVibrate(this)) vibrate(300)
    }

    private fun tryStartMediaRecorder(audioSource: Int): Boolean {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
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
        } catch (_: Exception) { false }
    }

    private fun stopRecordingAndPrompt() {
        val recorder = mediaRecorder
        if (recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try { recorder.stop(); recorder.release() } catch (_: Exception) {}
        mediaRecorder = null

        val file = outputFile
        if (file == null || !file.exists() || file.length() < 1000) {
            file?.delete()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val durationSec = (System.currentTimeMillis() - startTimeMillis) / 1000
        // On Android 10+, EXTRA_INCOMING_NUMBER is often empty even with
        // READ_CALL_LOG. Fall back to reading the most recent CallLog entry.
        val resolvedNumber = phoneNumber.ifBlank { resolveNumberFromCallLog() }
        val contactName = resolveContactName(resolvedNumber)
        val displayName = when {
            contactName.isNotEmpty() -> contactName
            resolvedNumber.isNotEmpty() -> resolvedNumber
            else -> "Неизвестный"
        }
        val direction = if (isIncoming) "Входящий" else "Исходящий"
        val duration = "%d:%02d".format(durationSec / 60, durationSec % 60)

        if (Prefs.getVibrate(this)) vibrate(200)
        stopForeground(STOP_FOREGROUND_REMOVE)

        val recording = Recording(
            phoneNumber = resolvedNumber,
            contactName = contactName,
            filePath = file.absolutePath,
            dateMillis = startTimeMillis,
            durationSeconds = durationSec,
            fileSize = file.length(),
            isIncoming = isIncoming
        )

        val nm = getSystemService(NotificationManager::class.java)
        when {
            overlayManager?.canShow() == true -> {
                // Show floating overlay strip — service stays alive until button is tapped
                overlayManager?.show(
                    title = "$direction · $displayName · $duration",
                    onSave = {
                        CoroutineScope(Dispatchers.IO).launch {
                            try { AppDatabase.getInstance(this@RecorderService).recordingDao().insert(recording) } catch (_: Exception) {}
                        }
                        stopSelf()
                    },
                    onDelete = {
                        file.delete()
                        stopSelf()
                    }
                )
            }
            Build.VERSION.SDK_INT >= 33 && !nm.areNotificationsEnabled() -> {
                // Android 13+ without notification permission — auto-save
                autoSaveRecording(recording)
                stopSelf()
            }
            else -> {
                showSaveDeleteNotification(file, durationSec, contactName, displayName, direction, duration)
                stopSelf()
            }
        }
    }

    private fun autoSaveRecording(recording: Recording) {
        CoroutineScope(Dispatchers.IO).launch {
            try { AppDatabase.getInstance(this@RecorderService).recordingDao().insert(recording) } catch (_: Exception) {}
        }
    }

    private fun showSaveDeleteNotification(
        file: File, durationSec: Long, contactName: String,
        displayName: String, direction: String, duration: String
    ) {
        fun makeIntent(action: String) = PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            Intent(this, SaveDeleteReceiver::class.java).apply {
                this.action = action
                putExtra(SaveDeleteReceiver.EXTRA_FILE_PATH, file.absolutePath)
                putExtra(SaveDeleteReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(SaveDeleteReceiver.EXTRA_CONTACT_NAME, contactName)
                putExtra(SaveDeleteReceiver.EXTRA_DATE_MILLIS, startTimeMillis)
                putExtra(SaveDeleteReceiver.EXTRA_DURATION_SEC, durationSec)
                putExtra(SaveDeleteReceiver.EXTRA_FILE_SIZE, file.length())
                putExtra(SaveDeleteReceiver.EXTRA_IS_INCOMING, isIncoming)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, App.SAVE_DELETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentTitle("$direction · $displayName · $duration")
            .setContentText("Сохранить запись звонка?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(0, "✓ Сохранить", makeIntent(SaveDeleteReceiver.ACTION_SAVE))
            .addAction(0, "✕ Удалить", makeIntent(SaveDeleteReceiver.ACTION_DELETE))
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(SaveDeleteReceiver.NOTIFICATION_ID, notification)
    }

    private fun resolveNumberFromCallLog(): String {
        return try {
            contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER),
                null, null,
                "${android.provider.CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
            } ?: ""
        } catch (_: Exception) { "" }
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

    private fun buildRecordingNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val direction = if (isIncoming) "Входящий" else "Исходящий"
        val display = phoneNumber.ifEmpty { "неизвестный номер" }
        // PRIORITY_DEFAULT keeps the icon visible in the status bar for the
        // full duration of the call (PRIORITY_LOW collapses on many devices).
        // setColorized(true)+setColor(RED) gives a red background in the shade;
        // the status bar icon itself is always white — Android system limitation.
        return NotificationCompat.Builder(this, App.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recording_dot)
            .setColor(Color.RED)
            .setColorized(true)
            .setContentTitle("⏺ Запись звонка")
            .setContentText("$direction: $display")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        overlayManager?.dismiss()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager?.dismiss()
        overlayManager = null
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }
}
