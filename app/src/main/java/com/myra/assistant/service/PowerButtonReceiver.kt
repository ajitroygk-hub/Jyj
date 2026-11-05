package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PowerButtonReceiver"
        private const val DOUBLE_PRESS_INTERVAL_MS = 600L
        private var lastPressTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastPressTime
            lastPressTime = currentTime

            if (timeDiff in 100..DOUBLE_PRESS_INTERVAL_MS) {
                Log.d(TAG, "Double power/screen toggle detected within ${timeDiff}ms! Launching overlay...")
                if (Settings.canDrawOverlays(context)) {
                    val overlayIntent = Intent(context, MyraOverlayService::class.java)
                    try {
                        context.startService(overlayIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start MyraOverlayService: ${e.message}")
                    }
                }
            }
        }
    }
}
