package com.callrecorder.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "recording_channel"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Запись звонка",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Отображается во время записи звонка"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
