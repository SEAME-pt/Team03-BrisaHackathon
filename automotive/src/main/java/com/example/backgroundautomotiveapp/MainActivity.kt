package com.example.backgroundautomotiveapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {

    private val TAG_MAIN = "MainActivity_DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG_MAIN, "MainActivity onCreate called")

        Log.i(TAG_MAIN, "Attempting to start MyBackgroundService from MainActivity...")
        startOurService()

        // Você pode querer fechar a Activity depois de iniciar o serviço se ela não tem UI
        // finish()
    }

    private fun startOurService() {
        val serviceIntent = Intent(this, MyBackgroundService::class.java)

        try {
            startService(serviceIntent)
            Log.i(TAG_MAIN, "startService(MyBackgroundService) called successfully from MainActivity.")
        } catch (e: Exception) {
            Log.e(TAG_MAIN, "Error starting MyBackgroundService from MainActivity: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_MAIN, "MainActivity onDestroy called")

    }
}