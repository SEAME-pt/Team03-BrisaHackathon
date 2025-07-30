package com.example.locationprovider

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.*
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.common_aidl_interfaces.ILocationProvider
import com.example.common_aidl_interfaces.ILocationReceiverCallback

class LocationProviderService : Service() {

    private val TAG = "LocationProviderService"
    private val NOTIFICATION_CHANNEL_ID = "LocationProviderChannel"
    private val NOTIFICATION_ID = 123 // Unique ID for your notification

    private val mCallbacks = RemoteCallbackList<ILocationReceiverCallback>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Binder implementation (remains mostly the same)
    private val binder = object : ILocationProvider.Stub() {
        override fun registerCallback(callback: ILocationReceiverCallback?) {
            callback?.let {
                mCallbacks.register(it)
                Log.d(TAG, "Callback registered from PID: ${Binder.getCallingPid()}")
            }
        }

        override fun unregisterCallback(callback: ILocationReceiverCallback?) {
            callback?.let {
                mCallbacks.unregister(it)
                Log.d(TAG, "Callback unregistered from PID: ${Binder.getCallingPid()}")
            }
        }

        override fun getServiceMessage(): String {
            Log.d(TAG, "getServiceMessage() called from PID: ${Binder.getCallingPid()}")
            return "LocationProviderService is active. PID: ${Process.myPid()}"
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created. PID: ${Process.myPid()}")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d(TAG, "Location Update: Lat: $location.latitude, Lon: $location.longitude, Accuracy: ${location.accuracy}m")
                    broadcastLocationData(location)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Location Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
        Log.d(TAG, "Notification channel created")
    }

    private fun startForegroundServiceWithNotification() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Car Location Tracker")
            .setContentText("Tracking car location...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground service started")
    }

    private fun broadcastLocationData(location: Location) {
        val dataToSend = "Location: Lat=${location.latitude}, Lon=${location.longitude}, Acc=${location.accuracy}"
        Log.d(TAG, "Preparing to send data: $dataToSend")

        val n = mCallbacks.beginBroadcast()
        for (i in 0 until n) {
            try {
                mCallbacks.getBroadcastItem(i).onDataReceived(dataToSend)
            } catch (e: RemoteException) {
                Log.e(TAG, "Error sending data to callback: ${e.message}")
            }
        }
        mCallbacks.finishBroadcast()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received")
        startForegroundServiceWithNotification()
        startLocationUpdates()
        return START_STICKY // Keep service running
    }


    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Cannot start updates.")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // Update every 10 seconds
            .setMinUpdateIntervalMillis(5000L) // Minimum update interval 5 seconds
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "Requested location updates")
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission. $unlikely")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "Service onBind called.")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service onUnbind called.")
        // You might consider stopping location updates if no clients are bound,
        // but for "always sending", we keep it running as long as the service is alive.
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mCallbacks.kill()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Service Destroyed")
    }
}
