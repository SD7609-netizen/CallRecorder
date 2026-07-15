package com.callrecorder.android.ui

import com.callrecorder.android.data.Recording

sealed class RecyclerItem {
    data class Header(val title: String) : RecyclerItem()
    data class Item(val recording: Recording) : RecyclerItem()
}
