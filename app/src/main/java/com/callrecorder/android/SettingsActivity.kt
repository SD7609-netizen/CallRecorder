package com.callrecorder.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.callrecorder.android.databinding.ActivitySettingsBinding
import com.callrecorder.android.util.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val recordingModeLabels = arrayOf("Все звонки", "Только входящие", "Только исходящие", "Нет")
    private val audioSourceLabels = arrayOf("Авто", "Microphone", "Voice call", "Voice recognition", "Voice communication", "Voice performance")
    private val channelLabels = arrayOf("Stereo", "Mono")
    private val channelValues = intArrayOf(2, 1)
    private val sampleRates = intArrayOf(48000, 44100, 32000, 22050, 16000, 11025, 8000)
    private val bitrates = intArrayOf(320000, 256000, 192000, 160000, 128000, 96000, 64000)
    private val autoDeleteLabels = arrayOf("Никогда", "7 дней", "14 дней", "30 дней", "60 дней", "90 дней")
    private val autoDeleteValues = intArrayOf(0, 7, 14, 30, 60, 90)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Настройки"
        loadValues()
        setupListeners()
    }

    private fun loadValues() {
        binding.tvRecordingMode.text = recordingModeLabels[Prefs.getRecordingMode(this)]
        binding.tvAudioSource.text = audioSourceLabels[Prefs.getAudioSourceIndex(this)]
        val chIdx = channelValues.indexOfFirst { it == Prefs.getAudioChannels(this) }.coerceAtLeast(0)
        binding.tvAudioChannel.text = channelLabels[chIdx]
        binding.tvSampleRate.text = "${Prefs.getSampleRate(this)} Hz"
        binding.tvBitrate.text = "${Prefs.getBitrate(this) / 1000} Kbps"
        binding.seekVolume.progress = Prefs.getVolumeBoost(this)
        binding.tvVolumeValue.text = "${Prefs.getVolumeBoost(this)}"
        binding.switchVibrate.isChecked = Prefs.getVibrate(this)
        val dIdx = autoDeleteValues.indexOfFirst { it == Prefs.getAutoDeleteDays(this) }.coerceAtLeast(0)
        binding.tvAutoDelete.text = autoDeleteLabels[dIdx]
    }

    private fun setupListeners() {
        binding.rowRecordingMode.setOnClickListener {
            showSingleChoice("Режим записи", recordingModeLabels, Prefs.getRecordingMode(this)) { i ->
                Prefs.setRecordingMode(this, i)
                binding.tvRecordingMode.text = recordingModeLabels[i]
            }
        }

        binding.rowAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.rowAudioSource.setOnClickListener {
            showSingleChoice("Источник звука", audioSourceLabels, Prefs.getAudioSourceIndex(this)) { i ->
                Prefs.setAudioSourceIndex(this, i)
                binding.tvAudioSource.text = audioSourceLabels[i]
            }
        }

        binding.rowAudioChannel.setOnClickListener {
            val cur = channelValues.indexOfFirst { it == Prefs.getAudioChannels(this) }.coerceAtLeast(0)
            showSingleChoice("Аудиоканал", channelLabels, cur) { i ->
                Prefs.setAudioChannels(this, channelValues[i])
                binding.tvAudioChannel.text = channelLabels[i]
            }
        }

        binding.rowSampleRate.setOnClickListener {
            val labels = sampleRates.map { "$it Hz" }.toTypedArray()
            val cur = sampleRates.indexOfFirst { it == Prefs.getSampleRate(this) }.coerceAtLeast(1)
            showSingleChoice("Частота дискретизации", labels, cur) { i ->
                Prefs.setSampleRate(this, sampleRates[i])
                binding.tvSampleRate.text = "${sampleRates[i]} Hz"
            }
        }

        binding.rowBitrate.setOnClickListener {
            val labels = bitrates.map { "${it / 1000} Kbps" }.toTypedArray()
            val cur = bitrates.indexOfFirst { it == Prefs.getBitrate(this) }.coerceAtLeast(4)
            showSingleChoice("Аудио битрейт", labels, cur) { i ->
                Prefs.setBitrate(this, bitrates[i])
                binding.tvBitrate.text = "${bitrates[i] / 1000} Kbps"
            }
        }

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, user: Boolean) {
                binding.tvVolumeValue.text = "$p"
                if (user) Prefs.setVolumeBoost(this@SettingsActivity, p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.switchVibrate.setOnCheckedChangeListener { _, checked ->
            Prefs.setVibrate(this, checked)
        }

        binding.rowAutoDelete.setOnClickListener {
            val cur = autoDeleteValues.indexOfFirst { it == Prefs.getAutoDeleteDays(this) }.coerceAtLeast(0)
            showSingleChoice("Автоматическое удаление", autoDeleteLabels, cur) { i ->
                Prefs.setAutoDeleteDays(this, autoDeleteValues[i])
                binding.tvAutoDelete.text = autoDeleteLabels[i]
            }
        }
    }

    private fun showSingleChoice(title: String, items: Array<String>, current: Int, onSelect: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(items, current) { dialog, which ->
                onSelect(which)
                dialog.dismiss()
            }.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
