package com.callrecorder.android.ui

import android.app.Application
import androidx.lifecycle.*
import com.callrecorder.android.data.AppDatabase
import com.callrecorder.android.data.Recording
import com.callrecorder.android.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).recordingDao()
    private val allRecordings = dao.getAllRecordings()

    private val _searchQuery = MutableLiveData("")
    private val _groupByContact = MutableLiveData(Prefs.getGroupByContact(application))

    val displayItems: LiveData<List<RecyclerItem>> = MediatorLiveData<List<RecyclerItem>>().apply {
        fun rebuild() {
            val all = allRecordings.value ?: return
            val query = _searchQuery.value ?: ""
            val group = _groupByContact.value ?: false
            val filtered = if (query.isBlank()) all else all.filter { r ->
                r.contactName.contains(query, ignoreCase = true) ||
                r.phoneNumber.contains(query, ignoreCase = true)
            }
            value = if (group) groupByContact(filtered) else groupByDate(filtered)
        }
        addSource(allRecordings) { rebuild() }
        addSource(_searchQuery) { rebuild() }
        addSource(_groupByContact) { rebuild() }
    }

    // transcription state: recordingId → state
    private val _transcription = MutableLiveData<Map<Long, TranscriptionState>>(emptyMap())
    val transcriptionState: LiveData<Map<Long, TranscriptionState>> = _transcription

    sealed class TranscriptionState {
        object Loading : TranscriptionState()
        data class Done(val text: String) : TranscriptionState()
        data class Error(val message: String) : TranscriptionState()
    }

    fun setQuery(query: String) { _searchQuery.value = query }

    fun setGroupByContact(group: Boolean) {
        _groupByContact.value = group
        Prefs.setGroupByContact(getApplication(), group)
    }

    fun isGroupByContact() = _groupByContact.value ?: false

    fun delete(recording: Recording) = viewModelScope.launch {
        dao.delete(recording)
        try { File(recording.filePath).delete() } catch (_: Exception) {}
    }

    fun autoDeleteOld() = viewModelScope.launch {
        val days = Prefs.getAutoDeleteDays(getApplication())
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            dao.deleteOlderThan(cutoff)
        }
    }

    fun transcribe(recording: Recording) = viewModelScope.launch {
        val apiKey = Prefs.getWhisperApiKey(getApplication())
        if (apiKey.isBlank()) {
            setTranscriptionState(recording.id, TranscriptionState.Error("NO_KEY"))
            return@launch
        }
        setTranscriptionState(recording.id, TranscriptionState.Loading)

        val result = withContext(Dispatchers.IO) {
            try {
                val file = File(recording.filePath)
                if (!file.exists()) return@withContext TranscriptionState.Error("Файл не найден")
                if (file.length() > 25 * 1024 * 1024L) return@withContext TranscriptionState.Error("Файл слишком большой (>25MB)")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build()

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("language", "ru")
                    .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val text = JSONObject(responseBody).getString("text")
                    TranscriptionState.Done(text)
                } else {
                    TranscriptionState.Error("Ошибка API ${response.code}")
                }
            } catch (e: Exception) {
                TranscriptionState.Error(e.message ?: "Неизвестная ошибка")
            }
        }

        setTranscriptionState(recording.id, result)
        if (result is TranscriptionState.Done) {
            dao.update(recording.copy(transcription = result.text))
        }
    }

    private fun setTranscriptionState(id: Long, state: TranscriptionState) {
        _transcription.value = (_transcription.value ?: emptyMap()) + (id to state)
    }

    // Stats

    data class Stats(
        val total: Int,
        val incoming: Int,
        val outgoing: Int,
        val totalDurationSec: Long,
        val totalSizeBytes: Long,
        val avgDurationSec: Long,
        val topContacts: List<Pair<String, Int>>
    )

    suspend fun getStats(): Stats = withContext(Dispatchers.IO) {
        val all = dao.getAllSync()
        val total = all.size
        val incoming = all.count { it.isIncoming }
        Stats(
            total = total,
            incoming = incoming,
            outgoing = total - incoming,
            totalDurationSec = all.sumOf { it.durationSeconds },
            totalSizeBytes = all.sumOf { it.fileSize },
            avgDurationSec = if (total > 0) all.sumOf { it.durationSeconds } / total else 0,
            topContacts = all
                .groupBy { it.contactName.ifEmpty { it.phoneNumber.ifEmpty { "Неизвестный" } } }
                .map { (name, list) -> name to list.size }
                .sortedByDescending { it.second }
                .take(5)
        )
    }

    // Grouping helpers

    private fun groupByDate(recordings: List<Recording>): List<RecyclerItem> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayMs = cal.timeInMillis
        val yesterdayMs = todayMs - 86_400_000L
        val weekMs = todayMs - 7 * 86_400_000L
        val monthMs = todayMs - 30 * 86_400_000L

        fun label(r: Recording) = when {
            r.dateMillis >= todayMs -> "Сегодня"
            r.dateMillis >= yesterdayMs -> "Вчера"
            r.dateMillis >= weekMs -> "На этой неделе"
            r.dateMillis >= monthMs -> "В этом месяце"
            else -> "Ранее"
        }

        val order = listOf("Сегодня", "Вчера", "На этой неделе", "В этом месяце", "Ранее")
        val groups = recordings.groupBy { label(it) }
        return order.flatMap { sec ->
            val items = groups[sec] ?: return@flatMap emptyList()
            listOf(RecyclerItem.Header(sec)) + items.map { RecyclerItem.Item(it) }
        }
    }

    private fun groupByContact(recordings: List<Recording>): List<RecyclerItem> {
        val groups = recordings
            .groupBy { it.contactName.ifEmpty { it.phoneNumber.ifEmpty { "Неизвестный" } } }
            .entries
            .sortedBy { it.key.lowercase() }
        return groups.flatMap { (name, items) ->
            listOf(RecyclerItem.Header(name)) + items.map { RecyclerItem.Item(it) }
        }
    }
}
