package com.callrecorder.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.callrecorder.android.util.Prefs

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var wasRinging = false
        private var savedIncomingNumber = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isRecordingEnabled(context)) return
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
                        val phoneNumber = savedIncomingNumber
                        val mode = Prefs.getRecordingMode(context)
                        val modeOk = when (mode) {
                            Prefs.MODE_ALL -> true
                            Prefs.MODE_INCOMING -> isIncoming
                            Prefs.MODE_OUTGOING -> !isIncoming
                            else -> false
                        }
                        if (!modeOk) return

                        // Contact filter
                        val filter = Prefs.getContactFilter(context)
                        if (filter != Prefs.FILTER_ALL && phoneNumber.isNotEmpty()) {
                            val inBook = isInPhonebook(context, phoneNumber)
                            val filterOk = when (filter) {
                                Prefs.FILTER_CONTACTS_ONLY -> inBook
                                Prefs.FILTER_UNKNOWN_ONLY -> !inBook
                                else -> true
                            }
                            if (!filterOk) return
                        }

                        try {
                            context.startForegroundService(
                                Intent(context, RecorderService::class.java).apply {
                                    action = RecorderService.ACTION_START
                                    putExtra(RecorderService.EXTRA_PHONE_NUMBER, phoneNumber)
                                    putExtra(RecorderService.EXTRA_IS_INCOMING, isIncoming)
                                }
                            )
                        } catch (_: Exception) {}
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

    private fun isInPhonebook(context: Context, number: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (_: Exception) { false }
    }
}
