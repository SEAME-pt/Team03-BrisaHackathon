package com.example.locationprovider

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {

    private val TAG_MAIN = "MainActivity_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG_MAIN, "MainActivity onCreate called")

        Log.i(TAG_MAIN, "Attempting to start LocationProviderService from MainActivity...")
        startOurService()

        finish()
    }

    private fun startOurService() {
        val serviceIntent = Intent(this, LocationProviderService::class.java)

        try {
            startForegroundService(serviceIntent)
            Log.i(TAG_MAIN, "startService(LocationProviderService) called successfully from MainActivity.")
        } catch (e: Exception) {
            Log.e(TAG_MAIN, "Error starting LocationProviderService from MainActivity: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_MAIN, "MainActivity onDestroy called")

    }
}