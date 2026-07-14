package com.callrecorder.android.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class RecorderAccessibilityService : AccessibilityService() {
    // Accessibility connector for Android 10+ two-sided recording support.
    // When enabled, allows MediaRecorder to capture both sides of a call
    // on supported devices (Samsung, Xiaomi MIUI, etc.).
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
