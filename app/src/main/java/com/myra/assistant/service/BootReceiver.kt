package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, starting CallMonitorService...")
            val serviceIntent = Intent(context, CallMonitorService::class.java)
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallMonitorService on boot: ${e.message}")
            }
        }
    }
}
