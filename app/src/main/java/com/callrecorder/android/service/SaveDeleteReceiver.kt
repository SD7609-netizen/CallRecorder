package com.callrecorder.android.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callrecorder.android.data.AppDatabase
import com.callrecorder.android.data.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SaveDeleteReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SAVE = "com.callrecorder.SAVE_RECORDING"
        const val ACTION_DELETE = "com.callrecorder.DELETE_RECORDING"
        const val NOTIFICATION_ID = 1002

        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_DATE_MILLIS = "date_millis"
        const val EXTRA_DURATION_SEC = "duration_sec"
        const val EXTRA_FILE_SIZE = "file_size"
        const val EXTRA_IS_INCOMING = "is_incoming"
    }

    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: return

        when (intent.action) {
            ACTION_SAVE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AppDatabase.getInstance(context).recordingDao().insert(
                            Recording(
                                phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "",
                                contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "",
                                filePath = filePath,
                                dateMillis = intent.getLongExtra(EXTRA_DATE_MILLIS, System.currentTimeMillis()),
                                durationSeconds = intent.getLongExtra(EXTRA_DURATION_SEC, 0),
                                fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0),
                                isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                            )
                        )
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_DELETE -> {
                File(filePath).delete()
            }
        }
    }
}
