package com.callrecorder.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callrecorder.android.util.Prefs

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var wasRinging = false
        private var savedIncomingNumber = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.getRecordingMode(context) == Prefs.MODE_NONE) return

        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        wasRinging = true
                        if (number.isNotEmpty()) savedIncomingNumber = number
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        val isIncoming = wasRinging
                        val phoneNumber = if (isIncoming) savedIncomingNumber else savedIncomingNumber
                        val mode = Prefs.getRecordingMode(context)
                        val shouldRecord = when (mode) {
                            Prefs.MODE_ALL -> true
                            Prefs.MODE_INCOMING -> isIncoming
                            Prefs.MODE_OUTGOING -> !isIncoming
                            else -> false
                        }
                        if (shouldRecord) {
                            context.startForegroundService(
                                Intent(context, RecorderService::class.java).apply {
                                    action = RecorderService.ACTION_START
                                    putExtra(RecorderService.EXTRA_PHONE_NUMBER, phoneNumber)
                                    putExtra(RecorderService.EXTRA_IS_INCOMING, isIncoming)
                                }
                            )
                        }
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        wasRinging = false
                        savedIncomingNumber = ""
                        context.startService(
                            Intent(context, RecorderService::class.java).apply {
                                action = RecorderService.ACTION_STOP
                            }
                        )
                    }
                }
            }
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                savedIncomingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: ""
                wasRinging = false
            }
        }
    }
}
