package com.example.backgroundautomotiveapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver_DEBUG"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(tag, "onReceive - ENTERED. Action: ${intent?.action}")

        if (context == null) {
            Log.e(tag, "Context is NULL, cannot start service.")
            return
        }

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(tag, "BOOT_COMPLETED received. Attempting to start MyBackgroundService.")
            try {
                val serviceIntent = Intent(context, MyBackgroundService::class.java)
                context.startService(serviceIntent)
                Log.i(tag, "startService(MyBackgroundService) called successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Error starting MyBackgroundService: ${e.message}", e)
            }
        } else {
            Log.w(tag, "Received unexpected action: ${intent?.action}")
        }
        Log.d(tag, "onReceive - EXITING")
    }
}