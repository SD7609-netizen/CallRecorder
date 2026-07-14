package com.callrecorder.android.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build

object Prefs {
    private const val PREFS_NAME = "call_recorder_prefs"

    const val KEY_PERMISSIONS_DONE = "permissions_done"
    const val KEY_RECORDING_MODE = "recording_mode"
    const val KEY_AUDIO_SOURCE = "audio_source"
    const val KEY_AUDIO_CHANNEL = "audio_channel"
    const val KEY_SAMPLE_RATE = "sample_rate"
    const val KEY_BITRATE = "bitrate"
    const val KEY_VOLUME_BOOST = "volume_boost"
    const val KEY_AUTO_DELETE_DAYS = "auto_delete_days"
    const val KEY_VIBRATE = "vibrate"

    const val MODE_ALL = 0
    const val MODE_INCOMING = 1
    const val MODE_OUTGOING = 2
    const val MODE_NONE = 3

    const val SOURCE_AUTO = 0
    const val SOURCE_MIC = 1
    const val SOURCE_VOICE_CALL = 2
    const val SOURCE_VOICE_RECOGNITION = 3
    const val SOURCE_VOICE_COMMUNICATION = 4
    const val SOURCE_VOICE_PERFORMANCE = 5

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPermissionsDone(context: Context) =
        prefs(context).getBoolean(KEY_PERMISSIONS_DONE, false)

    fun setPermissionsDone(context: Context, done: Boolean) =
        prefs(context).edit().putBoolean(KEY_PERMISSIONS_DONE, done).apply()

    fun getRecordingMode(context: Context) =
        prefs(context).getInt(KEY_RECORDING_MODE, MODE_ALL)

    fun setRecordingMode(context: Context, mode: Int) =
        prefs(context).edit().putInt(KEY_RECORDING_MODE, mode).apply()

    fun getAudioSourceIndex(context: Context) =
        prefs(context).getInt(KEY_AUDIO_SOURCE, SOURCE_AUTO)

    fun setAudioSourceIndex(context: Context, index: Int) =
        prefs(context).edit().putInt(KEY_AUDIO_SOURCE, index).apply()

    fun resolveAudioSource(context: Context): Int = when (getAudioSourceIndex(context)) {
        SOURCE_MIC -> MediaRecorder.AudioSource.MIC
        SOURCE_VOICE_CALL -> MediaRecorder.AudioSource.VOICE_CALL
        SOURCE_VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        SOURCE_VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        SOURCE_VOICE_PERFORMANCE ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaRecorder.AudioSource.VOICE_PERFORMANCE
            else
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }

    fun getAudioChannels(context: Context) =
        prefs(context).getInt(KEY_AUDIO_CHANNEL, 1)

    fun setAudioChannels(context: Context, channels: Int) =
        prefs(context).edit().putInt(KEY_AUDIO_CHANNEL, channels).apply()

    fun getSampleRate(context: Context) =
        prefs(context).getInt(KEY_SAMPLE_RATE, 44100)

    fun setSampleRate(context: Context, rate: Int) =
        prefs(context).edit().putInt(KEY_SAMPLE_RATE, rate).apply()

    fun getBitrate(context: Context) =
        prefs(context).getInt(KEY_BITRATE, 128000)

    fun setBitrate(context: Context, bitrate: Int) =
        prefs(context).edit().putInt(KEY_BITRATE, bitrate).apply()

    fun getVolumeBoost(context: Context) =
        prefs(context).getInt(KEY_VOLUME_BOOST, 10)

    fun setVolumeBoost(context: Context, boost: Int) =
        prefs(context).edit().putInt(KEY_VOLUME_BOOST, boost).apply()

    fun getAutoDeleteDays(context: Context) =
        prefs(context).getInt(KEY_AUTO_DELETE_DAYS, 0)

    fun setAutoDeleteDays(context: Context, days: Int) =
        prefs(context).edit().putInt(KEY_AUTO_DELETE_DAYS, days).apply()

    fun getVibrate(context: Context) =
        prefs(context).getBoolean(KEY_VIBRATE, false)

    fun setVibrate(context: Context, vibrate: Boolean) =
        prefs(context).edit().putBoolean(KEY_VIBRATE, vibrate).apply()
}
