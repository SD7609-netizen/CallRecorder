package com.callrecorder.android.service

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.callrecorder.android.App
import com.callrecorder.android.MainActivity
import com.callrecorder.android.R
import java.io.File

class PlaybackService : Service() {

    companion object {
        const val ACTION_PLAY = "com.callrecorder.PLAY"
        const val ACTION_PAUSE_RESUME = "com.callrecorder.PAUSE_RESUME"
        const val ACTION_STOP_PLAYBACK = "com.callrecorder.STOP_PLAYBACK"
        const val EXTRA_FILE_PATH = "playback_file_path"
        const val EXTRA_TITLE = "playback_title"
        const val NOTIFICATION_ID = 2001
        const val BROADCAST_STATE = "com.callrecorder.PLAYBACK_STATE"
        const val EXTRA_IS_PLAYING = "is_playing"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentTitle = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "CallRecorderPlayback").apply {
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Запись"
                currentTitle = title
                startPlayback(path)
            }
            ACTION_PAUSE_RESUME -> togglePause()
            ACTION_STOP_PLAYBACK -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(path: String) {
        val file = File(path)
        if (!file.exists()) {
            stopSelf()
            return
        }

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(path)
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepare()
                start()
            }
            showNotification(playing = true)
            broadcastState(true)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun togglePause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            showNotification(playing = false)
            broadcastState(false)
        } else {
            mp.start()
            showNotification(playing = true)
            broadcastState(true)
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        broadcastState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcastState(isPlaying: Boolean) {
        sendBroadcast(Intent(BROADCAST_STATE).putExtra(EXTRA_IS_PLAYING, isPlaying))
    }

    private fun makeIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, PlaybackService::class.java).apply { this.action = action },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun showNotification(playing: Boolean) {
        val mainPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResIcon = if (playing) R.drawable.ic_pause_notif else R.drawable.ic_play_notif
        val pauseResLabel = if (playing) "Пауза" else "Продолжить"

        val state = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP)
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
            ).build()
        mediaSession?.setPlaybackState(state)

        val notification = NotificationCompat.Builder(this, App.PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentTitle(currentTitle)
            .setContentText(if (playing) "Воспроизведение" else "Пауза")
            .setContentIntent(mainPi)
            .setOngoing(playing)
            .addAction(pauseResIcon, pauseResLabel, makeIntent(ACTION_PAUSE_RESUME))
            .addAction(R.drawable.ic_stop_notif, "Стоп", makeIntent(ACTION_STOP_PLAYBACK))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
    }
}
