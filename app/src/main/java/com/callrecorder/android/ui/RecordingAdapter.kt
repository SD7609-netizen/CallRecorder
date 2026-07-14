package com.callrecorder.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.android.data.Recording
import com.callrecorder.android.databinding.ItemRecordingBinding
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(
    private val onPlay: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit
) : ListAdapter<Recording, RecordingAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Recording>() {
            override fun areItemsTheSame(a: Recording, b: Recording) = a.id == b.id
            override fun areContentsTheSame(a: Recording, b: Recording) = a == b
        }
    }

    inner class VH(val b: ItemRecordingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = getItem(position)
        val b = holder.b
        val displayName = when {
            r.contactName.isNotEmpty() -> r.contactName
            r.phoneNumber.isNotEmpty() -> r.phoneNumber
            else -> "Неизвестный"
        }
        val direction = if (r.isIncoming) "↙" else "↗"
        b.tvName.text = "$direction $displayName"
        b.tvPhone.text = r.phoneNumber.ifEmpty { "Номер неизвестен" }
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.dateMillis))
        val duration = formatDuration(r.durationSeconds)
        val size = formatSize(r.fileSize)
        b.tvMeta.text = "$date · $duration · $size"
        b.btnPlay.setOnClickListener { onPlay(r) }
        b.btnDelete.setOnClickListener { onDelete(r) }
    }

    private fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun formatSize(bytes: Long) = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "%.1fMB".format(bytes.toDouble() / (1024 * 1024))
    }
}
