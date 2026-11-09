package com.jarvis.companion

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("JARVIS", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        Log.d(
            "JARVIS",
            "UI Event received: ${event.eventType}"
        )
    }

    override fun onInterrupt() {
        Log.w("JARVIS", "Accessibility Service Interrupted")
    }
}
