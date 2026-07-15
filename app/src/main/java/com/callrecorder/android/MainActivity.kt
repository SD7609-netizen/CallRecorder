package com.callrecorder.android

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.callrecorder.android.data.Recording
import com.callrecorder.android.databinding.ActivityMainBinding
import com.callrecorder.android.ui.RecordingAdapter
import com.callrecorder.android.ui.RecordingsViewModel
import com.callrecorder.android.util.Prefs
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RecordingsViewModel by viewModels()
    private var mediaPlayer: MediaPlayer? = null

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

        // Recording toggle
        binding.switchRecording.isChecked = Prefs.isRecordingEnabled(this)
        binding.switchRecording.setOnCheckedChangeListener { _, checked ->
            Prefs.setRecordingEnabled(this, checked)
            binding.tvRecordingStatus.text = if (checked) "Запись активна" else "Запись отключена"
        }
        binding.tvRecordingStatus.text =
            if (Prefs.isRecordingEnabled(this)) "Запись активна" else "Запись отключена"

        val adapter = RecordingAdapter(
            onPlay = { playRecording(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvRecordings.adapter = adapter

        viewModel.recordings.observe(this) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.autoDeleteOld()
    }

    private fun playRecording(recording: Recording) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_LONG).show()
            return
        }

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@MainActivity, "Ошибка воспроизведения ($what/$extra)", Toast.LENGTH_LONG).show()
                    true
                }
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    Toast.makeText(this@MainActivity, "Воспроизведение завершено", Toast.LENGTH_SHORT).show()
                }
                prepare()
                start()
            }
            Toast.makeText(this, "▶ Воспроизведение...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            Toast.makeText(this, "Не удалось воспроизвести: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(recording: Recording) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        AlertDialog.Builder(this)
            .setTitle("Удалить запись?")
            .setMessage("Файл будет удалён безвозвратно")
            .setPositiveButton("Удалить") { _, _ -> viewModel.delete(recording) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
