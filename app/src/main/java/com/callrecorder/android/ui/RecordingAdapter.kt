package com.callrecorder.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.android.R
import com.callrecorder.android.data.Recording
import com.callrecorder.android.databinding.ItemRecordingBinding
import com.callrecorder.android.databinding.ItemRecordingHeaderBinding
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(
    private val onPlay: (Recording) -> Unit,
    private val onShare: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit,
    private val onTranscribe: (Recording) -> Unit
) : ListAdapter<RecyclerItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        private val DIFF = object : DiffUtil.ItemCallback<RecyclerItem>() {
            override fun areItemsTheSame(a: RecyclerItem, b: RecyclerItem) = when {
                a is RecyclerItem.Header && b is RecyclerItem.Header -> a.title == b.title
                a is RecyclerItem.Item && b is RecyclerItem.Item -> a.recording.id == b.recording.id
                else -> false
            }
            override fun areContentsTheSame(a: RecyclerItem, b: RecyclerItem) = a == b
        }
    }

    inner class HeaderVH(val b: ItemRecordingHeaderBinding) : RecyclerView.ViewHolder(b.root)
    inner class ItemVH(val b: ItemRecordingBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is RecyclerItem.Header -> TYPE_HEADER
        is RecyclerItem.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemRecordingHeaderBinding.inflate(inflater, parent, false))
            else -> ItemVH(ItemRecordingBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecyclerItem.Header -> (holder as HeaderVH).b.tvSectionHeader.text = item.title
            is RecyclerItem.Item -> bindItem(holder as ItemVH, item.recording)
        }
    }

    private fun bindItem(holder: ItemVH, r: Recording) {
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
        b.tvMeta.text = "$date · ${formatDuration(r.durationSeconds)} · ${formatSize(r.fileSize)}"

        if (r.transcription.isNullOrEmpty()) {
            b.tvTranscription.visibility = View.GONE
        } else {
            b.tvTranscription.visibility = View.VISIBLE
            b.tvTranscription.text = r.transcription
        }

        b.btnPlay.setOnClickListener { onPlay(r) }
        b.btnShare.setOnClickListener { onShare(r) }
        b.btnMore.setOnClickListener { showPopup(b.btnMore, r) }
    }

    private fun showPopup(anchor: View, r: Recording) {
        PopupMenu(anchor.context, anchor).apply {
            menu.add(0, 1, 0, "Расшифровать")
            menu.add(0, 2, 1, "Удалить")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onTranscribe(r)
                    2 -> onDelete(r)
                }
                true
            }
            show()
        }
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
