package com.example.locationreceiver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val TAG_MAIN = "MainActivityReceiver_DEBUG"
    private val LOCATION_PERMISSION_REQUEST_CODE = 2 // Unique request code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG_MAIN, "MainActivity onCreate called")

        checkAndRequestLocationPermissions()
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        // For Android 10 (API 29) and above, also check/request background location if your service needs it
        // when the app is not in the foreground (after this activity finishes).
        val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            PackageManager.PERMISSION_GRANTED // Treat as granted on older versions
        }

        val permissionsToRequest = mutableListOf<String>()
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            backgroundLocationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.i(TAG_MAIN, "Requesting location permissions: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.i(TAG_MAIN, "All required location permissions already granted.")
            startOurServiceAndFinish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty()) {
                for (i in permissions.indices) {
                    Log.i(TAG_MAIN, "Permission: ${permissions[i]}, Granted: ${grantResults[i] == PackageManager.PERMISSION_GRANTED}")
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (permissions[i] == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                            Log.w(TAG_MAIN, "Background location permission was denied. Service might have limited background capabilities.")
                        } else if (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION) {
                            Log.e(TAG_MAIN, "Fine location permission was denied. Cannot start location service effectively.")
                        }
                    }
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG_MAIN, "Fine location permission granted. Proceeding to start service.")
                    startOurServiceAndFinish()
                } else {
                    Log.e(TAG_MAIN, "Fine location permission was not granted. Service will not be started.")
                    finish() // Finish the activity if crucial permissions are missing
                }

            } else {
                // For example, if the request is cancelled
                Log.w(TAG_MAIN, "Permission request was cancelled or produced empty results.")
                finish() // Finish the activity
            }
        }
    }

    private fun startOurServiceAndFinish() {
        Log.i(TAG_MAIN, "Attempting to start LocationReceiverService from MainActivity...")
        val serviceIntent = Intent(this, LocationReceiverService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.i(TAG_MAIN, "Service start initiated successfully from MainActivity.")
        } catch (e: Exception) {
            Log.e(TAG_MAIN, "Error starting LocationReceiverService from MainActivity: ${e.message}", e)
        } finally {
            Log.d(TAG_MAIN, "Finishing MainActivity.")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG_MAIN, "MainActivity onDestroy called")
    }
}
