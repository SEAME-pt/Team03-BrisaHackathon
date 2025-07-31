package com.example.locationreceiver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import org.json.JSONObject
import com.example.locationreceiver.util.SecureStorage
import com.example.locationreceiver.util.TollsResponse
import com.example.locationreceiver.util.TollState
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.RemoteException
import com.example.common_aidl_interfaces.ILocationProvider
import com.example.common_aidl_interfaces.ILocationReceiverCallback
import com.example.locationreceiver.util.GeofencePoint
import com.example.locationreceiver.util.Toll
import kotlinx.serialization.json.Json
import kotlin.math.*

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val altitude: Double = 0.0,
    val inHighway: String = "YES"
)

data class TollCache(
    val toll: Toll,
    val distanceToCenter: Double,
    val lastChecked: Long = System.currentTimeMillis()
)

class LocationReceiverService : Service() {

    private val tag = "LocationReceiverService"
    private var iLocationProvider: ILocationProvider? = null
    private var isBound = false

    private val tollList = mutableListOf<Toll>()
	private val tollCache = mutableListOf<Toll>() // near tolls are stored to reduce computation
    private var lastClosedToll: Toll? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Background thread

    // Cache config
    private val cacheRadiusKm = 50.0
    private val cacheUpdateIntervalMs = 5 * 60 * 1000L // 5 minutes
    private var lastCacheUpdate = 0L
    private var lastKnownLocation: LocationPoint? = null

    private val httpClient by lazy {
        HttpClient(CIO) {
        }
    }

    private val LocationCallback = object : ILocationReceiverCallback.Stub() {
        override fun onNewLocationData(
            latitude: Double,
            longitude: Double,
            timestamp: Long,
            accuracy: Float
        ) {
            Log.i(tag, "onNewLocationData: Lat=$latitude, Lon=$longitude, Time=$timestamp, Acc=$accuracy")

            // Launch coroutine to avoid blocking the callback
            serviceScope.launch {
                try {
                    processLocationUpdate(latitude, longitude, timestamp, accuracy)
                } catch (e: Exception) {
                    Log.e(tag, "Error processing location update: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun processLocationUpdate(
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        accuracy: Float
    ) {
        val currentLocation = LocationPoint(latitude, longitude, timestamp)
        lastKnownLocation = currentLocation
        val now = System.currentTimeMillis()

        if (now - lastCacheUpdate > cacheUpdateIntervalMs || tollCache.isEmpty()) {
            Log.d(tag, "Updating toll cache...")
            updateTollCache(currentLocation)
            Log.d(tag, "Cache contains ${tollCache.size} entries")
            lastCacheUpdate = now
        }

        checkTollZones(currentLocation)
    }

    private suspend fun updateTollCache(currentLocation: LocationPoint) {
        tollCache.clear()

        val nearbyTolls = tollList.filter { toll ->
            val distance = calculateDistanceToTollCenter(currentLocation, toll)
            distance <= cacheRadiusKm
        }

        tollCache.addAll(nearbyTolls)

        Log.d(tag, "Updated toll cache with ${tollCache.size} nearby tolls")
    }


    private fun calculateDistanceToTollCenter(location: LocationPoint, toll: Toll): Double {
        return calculateDistance(location.latitude, location.longitude, toll.latitude, toll.longitude)
    }

    // harvesine distance
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // earth radius in km

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    // checks if the current location is within a toll zone
    private suspend fun checkTollZones(currentLocation: LocationPoint) {
        var enteredToll: Toll? = null

        // Check cached tolls for entry
        for (toll in tollCache) {
            for (geofence in toll.geofences) {
                if (isPointInPolygon(currentLocation.latitude, currentLocation.longitude, geofence.geofencePoints)) {
                    Log.d(tag, "Entered toll zone: ${toll.name}")
                    enteredToll = toll
                    break // can't be in multiple toll
                }
            }
        }

        // check if we exited the current closed toll zone
        lastClosedToll?.let { closedToll ->
            var stillInside = false
            for (geofence in closedToll.geofences) {
                if (isPointInPolygon(currentLocation.latitude, currentLocation.longitude, geofence.geofencePoints)) {
                    stillInside = true
                    break
                }
            }

            // if we went out we can clear the last closed toll and send the api the infos
            if (!stillInside) {
                Log.d(tag, "Exited closed toll zone: ${closedToll.name}")
                handleClosedTollExit(closedToll, currentLocation)
                lastClosedToll = null
            }
        }

        if (enteredToll != null) {
            handleTollEntry(enteredToll, currentLocation)
        }
    }

    private suspend fun handleTollEntry(toll: Toll, location: LocationPoint) {
        val tollState = determineTollState(toll)

        when (tollState) {
            TollState.OPEN -> {
                Log.i(tag, "Processing OPEN toll: ${toll.name}")
                handleOpenToll(toll, location)
            }
            TollState.CLOSED -> {
                // Check if we're already in this specific closed toll
                if (lastClosedToll?.code != toll.code) {
                    // If we're in a different closed toll, exit it first
                    lastClosedToll?.let { previousToll ->
                        Log.i(tag, "Exiting previous closed toll: ${previousToll.name}")
                        handleClosedTollExit(previousToll, location)
                    }

                    // Enter the new closed toll
                    Log.i(tag, "Entering CLOSED toll zone: ${toll.name}")
                    lastClosedToll = toll
                    handleClosedTollEntry(toll, location)
                } else {
                    // We're already in this closed toll, do nothing (avoid duplicate entries)
                    Log.d(tag, "Already in closed toll zone: ${toll.name}")
                }
            }
        }
    }

    private suspend fun handleClosedTollEntry(toll: Toll, location: LocationPoint) {}
    private suspend fun handleClosedTollExit(toll: Toll, location: LocationPoint) {}


    private suspend fun handleOpenToll(toll: Toll, location: LocationPoint) {
        Log.i(tag, "Processing payment for OPEN toll: ${toll.name}")

        // Send post

        // Avoid duplicate notification
    }

    private fun determineTollState(toll: Toll): TollState {
        return when (toll.type.uppercase()) {
            "OPEN" -> TollState.OPEN
            "CLOSED" -> TollState.CLOSED
            else -> TollState.OPEN // Default fallback
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            iLocationProvider = ILocationProvider.Stub.asInterface(service)
            isBound = true
            Log.d(tag, "Connected to LocationProvider and TollsProvider.")

            try {
                iLocationProvider?.registerCallback(LocationCallback)
                Log.d(tag, "Callback registered with LocationProvider and TollsProvider.")

            } catch (e: RemoteException) {
                Log.e(tag, "Error during onServiceConnected: ${e.message}")
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.e(tag, "Disconnected from LocationProviderService (Process crashed?)")
            cleanupConnection()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(tag, "Binding Died for LocationProviderService component: $name")
            cleanupConnection()
            // Consider attempting to re-bind after a delay if persistent connection is critical
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(tag, "Null Binding for LocationProviderService component: $name - Service might not be running or exported correctly in App A.")
            // Service not found or onBind returned null in App A.
            // No need to call cleanupConnection() as nothing was bound.
            stopSelf() // Stop this client service if it can't bind.
        }
    }

    private suspend fun performLoginManually(email: String, password: String): String? { // Returns authToken string or null
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/login"

        val jsonRequestBody = """
            {
                "email": "$email",
                "password": "$password"
            }
        """.trimIndent() // trimIndent is useful for multi-line strings

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(jsonRequestBody)
            }

            val responseBodyText = response.bodyAsText(Charsets.UTF_8) // Specify charset if needed
            Log.d("Ktor_Login_Manual", "Response Code: ${response.status}")

            if (response.status == HttpStatusCode.OK) {
                try {
                    val jsonResponse = JSONObject(responseBodyText)
                    val authToken = jsonResponse.optString("authToken", "")
                    if (authToken.isNotEmpty()) {
                        Log.i("Ktor_Login_Manual", "Login successful.")
                        authToken
                    } else {
                        Log.e("Ktor_Login_Manual", "AuthToken not found in JSON response.")
                        null
                    }
                } catch (e: org.json.JSONException) {
                    Log.e("Ktor_Login_Manual", "Failed to parse JSON response: ${e.message}", e)
                    null
                }
            } else {
                Log.e("Ktor_Login_Manual", "Login failed: ${response.status} - $responseBodyText")
                null
            }
        } catch (e: Exception) {
            Log.e("Ktor_Login_Manual", "Exception during login to $apiUrl: ${e.message}", e)
            null
        }
    }

    fun isPointInPolygon(lat: Double, lon: Double, polygon: List<GeofencePoint>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].latitude
            val yi = polygon[i].longitude
            val xj = polygon[j].latitude
            val yj = polygon[j].longitude

            val intersect = ((yi > lon) != (yj > lon)) &&
                    (lat < (xj - xi) * (lon - yi) / (yj - yi + 0.0000001) + xi) // no div by zero
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private suspend fun fetchTolls(authToken: String): String? {
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/tolls"
        return try {
            val response: HttpResponse = httpClient.get(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }

            Log.d(tag, "GET request to $apiUrl - Status: ${response.status}")
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBodyText = response.bodyAsText(Charsets.UTF_8)
                    val jsonResponse = JSONObject(responseBodyText)
                    val tollList = jsonResponse.optString("tollsList", "")
                    Log.i(tag, "Data fetched successfully. TollsList: $tollList") // Changed to Log.i
                    responseBodyText
                }
                HttpStatusCode.Unauthorized -> {
                    Log.w(tag, "Token is invalid or expired (401 Unauthorized). Clearing token.")
                    SecureStorage.clearAuthToken(applicationContext)
                    null
                }
                else -> {
                    val errorBody = response.bodyAsText(Charsets.UTF_8)
                    Log.e(tag, "Error fetching data: ${response.status} - $errorBody")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during GET request to $apiUrl: ${e.message}", e)
            null
        }
    }

    private suspend fun postTrip(authToken: String, plate: String): String? {
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/trips"

        val jsonRequestBody = """
            {
                "licensePlate": "$plate",
                "locations": [
                    {
                      "latitude": "38.657100000000000",
                      "longitude": "-8.894200000000000",
                      "altitude": 0,
                      "timestamp": 2,
                      "inHighway": "YES"
                    },
                    {
                      "latitude": "38.656748619072500",
                      "longitude": "-8.893693884252881",
                      "altitude": 0,
                      "timestamp": 2,
                      "inHighway": "YES"
                    },
                    {
                      "latitude": "38.656450000000000",
                      "longitude": "-8.893000000000000",
                      "altitude": 0,
                      "timestamp": 2,
                      "inHighway": "YES"
                    }
                ],
                "startTripMethod": "",
                "stopTripMethod": ""
            }
        """.trimIndent()

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
                contentType(ContentType.Application.Json)
                setBody(jsonRequestBody)
            }

            val responseBodyText = response.bodyAsText(Charsets.UTF_8) // Specify charset if needed
            Log.d("Post Trip", "Response Code: ${response.status}")

            if (response.status == HttpStatusCode.OK) {
                try {
                    val jsonResponse = JSONObject(responseBodyText)
                    val tripNumber = jsonResponse.optString("tripNumber", "")
                    Log.e("Post Trip", "Trip: $jsonResponse")
                    tripNumber
                } catch (e: org.json.JSONException) {
                    Log.e("Post Trip", "Failed to parse JSON response: ${e.message}", e)
                    null
                }
            } else {
                Log.e("Post Trip", "Trip post failed: ${response.status} - $responseBodyText")
                null
            }
        } catch (e: Exception) {
            Log.e("Post Trip", "Exception during trip post to $apiUrl: ${e.message}", e)
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Client Service Created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Client Service Started.")
        if (!isBound) {
            bindToRemoteService()
        }
        serviceScope.launch {
            var currentToken = SecureStorage.getAuthToken(applicationContext)

            if (currentToken == null) {
                Log.i(tag, "No existing token found. Attempting login...")
                val apiEmail = BuildConfig.API_EMAIL
                val apiPassword = BuildConfig.API_PASSWORD

                if (apiEmail.isEmpty() ||apiPassword.isEmpty()) {
                    Log.e(tag, "API credentials are not properly configured in BuildConfig.")
                    stopSelf()
                    return@launch
                }

                currentToken = performLoginManually(apiEmail, apiPassword)

                if (currentToken != null) {
                    Log.i(tag, "Successfully logged in. Token acquired and will be saved.")
                    SecureStorage.saveAuthToken(applicationContext, currentToken)
                } else {
                    Log.e(tag, "Login attempt failed. Unable to acquire token.")
                    // Handle login failure: retry logic, stop service, etc.
                    stopSelf() // Example: stop if login fails
                    return@launch
                }
            } else {
                Log.i(tag, "Existing token found. Using stored token.")
            }

            val plate = BuildConfig.API_PLATE

            val json = Json {
                ignoreUnknownKeys = true // skips unknown fields
            }
            val json_string = fetchTolls(currentToken)
            val parsed = json.decodeFromString<TollsResponse>(json_string.toString())
            Log.d(tag, "Got ${parsed.tolls.size} tolls")
            for (toll in parsed.tolls) {
                tollList.add(toll)
            }

            postTrip(currentToken, plate)
        }
        return START_STICKY
    }

    private fun bindToRemoteService() {
        val serviceIntent = Intent("com.example.locationprovider.BIND_MY_AIDL_SERVICE")

        serviceIntent.setPackage("com.example.locationprovider")

        val success = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        if (success) {
            Log.d(tag, "Attempting to bind to LocationProviderService...")
        } else {
            Log.e(tag, "Failed to initiate binding to LocationProviderService. Is LocationProviderService installed and service exported?")
            stopSelf() // Stop if binding cannot even be initiated.
        }
    }

    private fun unbindFromRemoteService() {
        if (isBound) {
            try {
                iLocationProvider?.unregisterCallback(LocationCallback)
                Log.d(tag, "Callback unregistered from LocationProviderService.")
            } catch (e: RemoteException) {
                Log.e(tag, "Error unregistering callback: ${e.message}")
            }
            unbindService(connection)
            cleanupConnection() // Call common cleanup
            Log.d(tag, "Unbound from LocationProviderService.")
        }
    }

    private fun cleanupConnection() {
        isBound = false
        iLocationProvider = null
    }

    override fun onBind(intent: Intent): IBinder? {
        // This service is not designed to be bound by other components within App B by default.
        // If you needed that, you'd return a Binder here.
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Client Service Destroyed.")
        serviceJob.cancel()
        httpClient.close()
        unbindFromRemoteService() // Ensure unbinding on destroy
    }
}
