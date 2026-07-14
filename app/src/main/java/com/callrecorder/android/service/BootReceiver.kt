package com.callrecorder.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // CallReceiver is registered in manifest — auto-activated after boot
    }
}
