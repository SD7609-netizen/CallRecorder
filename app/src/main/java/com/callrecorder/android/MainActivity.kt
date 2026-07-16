package com.callrecorder.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.callrecorder.android.data.Recording
import com.callrecorder.android.databinding.ActivityMainBinding
import com.callrecorder.android.service.PlaybackService
import com.callrecorder.android.ui.RecordingAdapter
import com.callrecorder.android.ui.RecordingsViewModel
import com.callrecorder.android.util.Prefs
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RecordingsViewModel by viewModels()
    private lateinit var adapter: RecordingAdapter

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // no-op: notification handles state display
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isPermissionsDone(this)) {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.switchRecording.isChecked = Prefs.isRecordingEnabled(this)
        binding.switchRecording.setOnCheckedChangeListener { _, checked ->
            Prefs.setRecordingEnabled(this, checked)
            binding.tvRecordingStatus.text = if (checked) "Запись активна" else "Запись отключена"
        }
        binding.tvRecordingStatus.text =
            if (Prefs.isRecordingEnabled(this)) "Запись активна" else "Запись отключена"

        adapter = RecordingAdapter(
            onPlay = { playRecording(it) },
            onShare = { shareRecording(it) },
            onDelete = { confirmDelete(it) },
            onTranscribe = { handleTranscribe(it) }
        )
        binding.rvRecordings.adapter = adapter

        viewModel.displayItems.observe(this) { items ->
            adapter.submitList(items)
            val empty = items.none { it is com.callrecorder.android.ui.RecyclerItem.Item }
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        }

        viewModel.transcriptionState.observe(this) { stateMap ->
            stateMap.forEach { (id, state) ->
                when (state) {
                    is RecordingsViewModel.TranscriptionState.Loading ->
                        Toast.makeText(this, "Расшифровка...", Toast.LENGTH_SHORT).show()
                    is RecordingsViewModel.TranscriptionState.Done ->
                        Toast.makeText(this, "Расшифровка сохранена", Toast.LENGTH_SHORT).show()
                    is RecordingsViewModel.TranscriptionState.Error -> {
                        if (state.message == "NO_KEY") promptForApiKey()
                        else Toast.makeText(this, "Ошибка: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewModel.autoDeleteOld()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(playbackReceiver, IntentFilter(PlaybackService.BROADCAST_STATE))
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(playbackReceiver) } catch (_: Exception) {}
    }

    private fun playRecording(recording: Recording) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_LONG).show()
            return
        }
        val displayName = recording.contactName.ifEmpty { recording.phoneNumber.ifEmpty { "Неизвестный" } }
        val playIntent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY
            putExtra(PlaybackService.EXTRA_FILE_PATH, file.absolutePath)
            putExtra(PlaybackService.EXTRA_TITLE, displayName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(playIntent)
        } else {
            startService(playIntent)
        }
        Toast.makeText(this, "▶ Воспроизведение: $displayName", Toast.LENGTH_SHORT).show()
    }

    private fun shareRecording(recording: Recording) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_LONG).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val displayName = recording.contactName.ifEmpty { recording.phoneNumber.ifEmpty { "звонок" } }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Запись: $displayName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Поделиться записью"
        ))
    }

    private fun handleTranscribe(recording: Recording) {
        val key = Prefs.getWhisperApiKey(this)
        if (key.isBlank()) {
            promptForApiKey { viewModel.transcribe(recording) }
        } else {
            if (recording.transcription != null) {
                AlertDialog.Builder(this)
                    .setTitle("Расшифровка")
                    .setMessage(recording.transcription)
                    .setPositiveButton("Перерасшифровать") { _, _ -> viewModel.transcribe(recording) }
                    .setNegativeButton("Закрыть", null)
                    .show()
            } else {
                viewModel.transcribe(recording)
            }
        }
    }

    private fun promptForApiKey(afterSave: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "sk-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("OpenAI API ключ")
            .setMessage("Введите ключ для расшифровки через Whisper API.\nМожно изменить в Настройках.")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    Prefs.setWhisperApiKey(this, key)
                    afterSave?.invoke()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDelete(recording: Recording) {
        stopPlayback()
        AlertDialog.Builder(this)
            .setTitle("Удалить запись?")
            .setMessage("Файл будет удалён безвозвратно")
            .setPositiveButton("Удалить") { _, _ -> viewModel.delete(recording) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun stopPlayback() {
        startService(Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_STOP_PLAYBACK
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = "Поиск по имени или номеру"
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(q: String?) = true
                override fun onQueryTextChange(q: String?): Boolean {
                    viewModel.setQuery(q ?: "")
                    return true
                }
            })
            setOnCloseListener { viewModel.setQuery(""); false }
        }

        val groupItem = menu.findItem(R.id.action_group)
        groupItem?.isChecked = viewModel.isGroupByContact()

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                true
            }
            R.id.action_group -> {
                val newState = !item.isChecked
                item.isChecked = newState
                viewModel.setGroupByContact(newState)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}
