package com.example.locationreceiver

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import com.example.common_aidl_interfaces.ILocationService
import com.example.common_aidl_interfaces.IMyCallback

class ClientAIDLService : Service() {

    private val TAG = "ClientAIDLService_AppB"
    private var iLocationService: ILocationService? = null
    private var isBound = false

    /**
     * Implementation of the callback interface.
     * This will be called by App A's service.
     */
    private val myCallback = object : IMyCallback.Stub() {
        override fun onDataReceived(data: String?) {
            // This is called on a Binder thread from the service.
            // For a service, you might process data directly here or post to a handler
            // if you have a specific thread for processing.
            Log.i(TAG, "onDataReceived: $data (from PID: ${Binder.getCallingPid()}, my PID: ${Process.myPid()})")
            // Example: You could store this data, send a local broadcast within App B,
            // or trigger further actions.
            // For this example, we'll just log it.
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            iLocationService = ILocationService.Stub.asInterface(service)
            isBound = true
            Log.d(TAG, "Connected to App A's Service.")

            try {
                iLocationService?.registerCallback(myCallback)
                Log.d(TAG, "Callback registered with App A's Service.")

                // Optionally, get the initial message right after connecting
                val message = iLocationService?.getServiceMessage()
                Log.i(TAG, "Initial message from App A's Service: $message")

            } catch (e: RemoteException) {
                Log.e(TAG, "Error during onServiceConnected: ${e.message}")
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.e(TAG, "Disconnected from App A's Service (Process crashed?)")
            cleanupConnection()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(TAG, "Binding Died for App A's Service component: $name")
            cleanupConnection()
            // Consider attempting to re-bind after a delay if persistent connection is critical
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(TAG, "Null Binding for App A's Service component: $name - Service might not be running or exported correctly in App A.")
            // Service not found or onBind returned null in App A.
            // No need to call cleanupConnection() as nothing was bound.
            stopSelf() // Stop this client service if it can't bind.
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Client Service Created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Client Service Started.")
        if (!isBound) {
            bindToRemoteService()
        }
        // Use START_STICKY if you want the system to try to restart the service
        // if it gets killed, and re-attempt binding.
        // Use START_NOT_STICKY if it should only run while processing commands
        // and doesn't need to be restarted automatically.
        return START_STICKY
    }

    private fun bindToRemoteService() {
        val serviceIntent = Intent("com.example.locationprovider.BIND_MY_AIDL_SERVICE")
        // You MUST specify the package for the intent when binding to a service
        // in another app, especially from Android 5.0 (Lollipop) onwards.
        serviceIntent.setPackage("com.example.locationprovider") // Target App A's package name

        val success = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        if (success) {
            Log.d(TAG, "Attempting to bind to App A's service...")
        } else {
            Log.e(TAG, "Failed to initiate binding to App A's service. Is App A installed and service exported?")
            stopSelf() // Stop if binding cannot even be initiated.
        }
    }

    private fun unbindFromRemoteService() {
        if (isBound) {
            try {
                iLocationService?.unregisterCallback(myCallback)
                Log.d(TAG, "Callback unregistered from App A's Service.")
            } catch (e: RemoteException) {
                Log.e(TAG, "Error unregistering callback: ${e.message}")
            }
            unbindService(connection)
            cleanupConnection() // Call common cleanup
            Log.d(TAG, "Unbound from App A's Service.")
        }
    }

    private fun cleanupConnection() {
        isBound = false
        iLocationService = null
    }

    override fun onBind(intent: Intent): IBinder? {
        // This service is not designed to be bound by other components within App B by default.
        // If you needed that, you'd return a Binder here.
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Client Service Destroyed.")
        unbindFromRemoteService() // Ensure unbinding on destroy
    }
}