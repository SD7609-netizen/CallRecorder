package com.callrecorder.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "recording_channel"
        const val SAVE_DELETE_CHANNEL_ID = "save_delete_channel"
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Запись звонка",
                // IMPORTANCE_DEFAULT keeps the status bar icon visible throughout
                // the call. Sound is suppressed via setSound(null,null) — the user
                // won't hear anything, but the icon stays in the status bar.
                // IMPORTANCE_LOW hides the icon on many devices (Android 8+).
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Отображается во время записи звонка"
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                SAVE_DELETE_CHANNEL_ID,
                "Сохранить запись?",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Запрос на сохранение или удаление записи после звонка"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Воспроизведение записи",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Управление воспроизведением записи звонка"
                setSound(null, null)
            }
        )
    }
}
