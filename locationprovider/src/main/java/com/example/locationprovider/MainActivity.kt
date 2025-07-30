package com.example.locationprovider // Make sure this matches your package name

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.locationprovider.LocationProviderService

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var btnRequestPermission: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "ACCESS_FINE_LOCATION permission granted.")
                Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show()
                btnStartService.isEnabled = true // Enable start button
                btnRequestPermission.isEnabled = false
            } else {
                Log.d(TAG, "ACCESS_FINE_LOCATION permission denied.")
                Toast.makeText(this, "Location permission denied. App cannot track location.", Toast.LENGTH_LONG).show()
                btnStartService.isEnabled = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRequestPermission = findViewById(R.id.btnRequestPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)

        // Check initial permission status
        if (isLocationPermissionGranted()) {
            btnStartService.isEnabled = true
            btnRequestPermission.isEnabled = false
        } else {
            btnStartService.isEnabled = false
            btnRequestPermission.isEnabled = true
        }

        btnRequestPermission.setOnClickListener {
            requestLocationPermission()
        }

        btnStartService.setOnClickListener {
            if (isLocationPermissionGranted()) {
                startLocationService()
            } else {
                Toast.makeText(this, "Please grant location permission first.", Toast.LENGTH_LONG).show()
                requestLocationPermission() // Prompt again
            }
        }

        btnStopService.setOnClickListener {
            stopLocationService()
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        Log.d(TAG, "Requesting location permission.")
        // For Android 14+, also need FOREGROUND_SERVICE_LOCATION if not granted with FINE_LOCATION
        // However, the system usually bundles this if foregroundServiceType="location" is in manifest.
        // For simplicity, we directly request FINE_LOCATION.
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startLocationService() {
        Log.d(TAG, "Attempting to start LocationService.")
        val serviceIntent = Intent(this, LocationProviderService::class.java)
        // For Android O (API 26) and above, you must use startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Location tracking service started.", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationService() {
        Log.d(TAG, "Attempting to stop LocationService.")
        val serviceIntent = Intent(this, LocationProviderService::class.java)
        // stopService is sufficient even if it was started with startForegroundService
        stopService(serviceIntent)
        Toast.makeText(this, "Location tracking service stopped.", Toast.LENGTH_SHORT).show()
    }
}