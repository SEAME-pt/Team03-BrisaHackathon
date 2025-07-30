package com.example.locationreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i(TAG, "Device boot completed. Attempting to start LocationReceiverService.")
            val serviceIntent = Intent(context, LocationReceiverService::class.java)

            // For Android 8 (Oreo) and above, you need to start foreground services
            // from the background using startForegroundService()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.w(TAG, "Received unexpected intent: ${intent.action}")
        }
    }
}