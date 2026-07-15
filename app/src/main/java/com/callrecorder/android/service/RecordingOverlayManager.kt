package com.callrecorder.android.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.callrecorder.android.R
import com.google.android.material.button.MaterialButton

class RecordingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    fun canShow() = Settings.canDrawOverlays(context)

    fun show(title: String, onSave: () -> Unit, onDelete: () -> Unit) {
        if (!canShow() || overlayView != null) return

        val themed = ContextThemeWrapper(context, R.style.Theme_CallRecorder)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_save_delete, null)

        view.findViewById<TextView>(R.id.tvOverlayTitle).text = title
        view.findViewById<MaterialButton>(R.id.btnOverlaySave).setOnClickListener {
            dismiss()
            onSave()
        }
        view.findViewById<MaterialButton>(R.id.btnOverlayDelete).setOnClickListener {
            dismiss()
            onDelete()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (_: Exception) {}
    }

    fun dismiss() {
        try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlayView = null
    }
}
