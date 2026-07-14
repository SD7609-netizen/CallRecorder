package com.callrecorder.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val contactName: String,
    val filePath: String,
    val dateMillis: Long,
    val durationSeconds: Long,
    val fileSize: Long,
    val isIncoming: Boolean
)
