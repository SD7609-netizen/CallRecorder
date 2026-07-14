package com.callrecorder.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.android.data.AppDatabase
import com.callrecorder.android.data.Recording
import com.callrecorder.android.util.Prefs
import kotlinx.coroutines.launch
import java.io.File

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).recordingDao()
    val recordings = dao.getAllRecordings()

    fun delete(recording: Recording) = viewModelScope.launch {
        dao.delete(recording)
        val file = File(recording.filePath)
        if (file.exists()) file.delete()
    }

    fun autoDeleteOld() = viewModelScope.launch {
        val days = Prefs.getAutoDeleteDays(getApplication())
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            dao.deleteOlderThan(cutoff)
        }
    }
}
