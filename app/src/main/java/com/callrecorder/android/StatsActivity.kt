package com.callrecorder.android

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.callrecorder.android.databinding.ActivityStatsBinding
import com.callrecorder.android.ui.RecordingsViewModel
import kotlinx.coroutines.launch

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Статистика"

        val vm = ViewModelProvider(this)[RecordingsViewModel::class.java]
        lifecycleScope.launch {
            val stats = vm.getStats()
            bindStats(stats)
        }
    }

    private fun bindStats(stats: RecordingsViewModel.Stats) {
        binding.tvTotalCount.text = "${stats.total}"
        binding.tvTotalDuration.text = formatDuration(stats.totalDurationSec)
        binding.tvTotalSize.text = formatSize(stats.totalSizeBytes)
        binding.tvIncomingCount.text = "${stats.incoming}"
        binding.tvOutgoingCount.text = "${stats.outgoing}"
        binding.tvAvgDuration.text = if (stats.total > 0) formatDuration(stats.avgDurationSec) else "—"

        // Scale bars
        val total = stats.total.coerceAtLeast(1)
        val maxWidth = resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).toInt()

        setBarWidth(binding.barIncoming, stats.incoming.toFloat() / total, maxWidth)
        setBarWidth(binding.barOutgoing, stats.outgoing.toFloat() / total, maxWidth)

        // Top contacts
        binding.llTopContacts.removeAllViews()
        if (stats.topContacts.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Нет данных"
                setTextColor(0xFF888888.toInt())
                textSize = 13f
            }
            binding.llTopContacts.addView(tv)
        } else {
            stats.topContacts.forEach { (name, count) ->
                val row = layoutInflater.inflate(android.R.layout.simple_list_item_2, binding.llTopContacts, false)
                (row.findViewById<TextView>(android.R.id.text1)).apply {
                    text = name
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                }
                (row.findViewById<TextView>(android.R.id.text2)).apply {
                    text = "$count записей"
                    setTextColor(0xFF888888.toInt())
                    textSize = 12f
                }
                binding.llTopContacts.addView(row)
            }
        }
    }

    private fun setBarWidth(view: View, fraction: Float, maxPx: Int) {
        val params = view.layoutParams
        params.width = (maxPx * fraction).toInt().coerceAtLeast(0)
        view.layoutParams = params
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatSize(bytes: Long) = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "%.1fMB".format(bytes.toDouble() / (1024 * 1024))
        else -> "%.2fGB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
