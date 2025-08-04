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
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.Process
import android.os.RemoteException
import com.example.common_aidl_interfaces.ILocationProvider
import com.example.common_aidl_interfaces.ILocationReceiverCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.example.locationreceiver.util.GeofencePoint
import com.example.locationreceiver.util.Toll
import kotlinx.serialization.json.Json
import org.json.JSONArray
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
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

class LocationReceiverService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var ttsInitialized = false
    private val speechQueue = ArrayDeque<String>()

    private val tag = "LocationReceiverService"
    private val NOTIFICATION_CHANNEL_ID = "LocationReceiverChannel"
    private val NOTIFICATION_ID = 101

    private var iLocationProvider: ILocationProvider? = null
    private var isBound = false

    private val collectedLocationPoints = mutableListOf<LocationPoint>()
    private val tollList = mutableListOf<Toll>()
    private var closedTollBuffer: Toll? = null
    private var openedTollLocationBuffer: LocationPoint? = null

    //Cache infos
    private val tollCache = mutableListOf<Toll>()
    private val cacheRadiusKm = 50.0
    private val cacheUpdateIntervalMs = 5 * 60 * 1000L // 5 minutes
    private var lastCacheUpdate = 0L
    private var lastKnownLocation: LocationPoint? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Background thread

    private val httpClient by lazy {
        HttpClient(CIO) {
        }
    }

    private val locationCallback = object : ILocationReceiverCallback.Stub() {
        override fun onNewLocationData(latitude: Double, longitude: Double, timestamp: Long, accuracy: Float) {
            Log.i(tag, "onNewLocationData: Lat=$latitude, Lon=$longitude, Time=$timestamp, Acc=$accuracy")
            serviceScope.launch {
                try {
                    processLocationUpdate(latitude, longitude, timestamp, accuracy)
                } catch (e: Exception) {
                    Log.e(tag, "Error processing location update: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun processLocationUpdate(latitude: Double, longitude: Double, timestamp: Long, accuracy: Float) {
        val currentLocation = LocationPoint(latitude, longitude, timestamp)
        lastKnownLocation = currentLocation
        val now = System.currentTimeMillis()

        if (now - lastCacheUpdate > cacheUpdateIntervalMs || tollCache.isEmpty()) {
            Log.d(tag, "Refreshing toll cache...")
            updateTollCache(currentLocation)
            Log.d(tag, "Cache contains ${tollCache.size} entries")

        }

        for (toll in tollCache) {
            for (geofence in toll.geofences) {
                if (isPointInPolygon(currentLocation.latitude, currentLocation.longitude, geofence.geofencePoints)) {
                    Log.d(tag, "Inside toll: ${toll.name}, type=${toll.type}")
                    handleTollEvent(toll, currentLocation)
                    return
                }
            }
        }
    }

    fun handleTollEvent(eventToll: Toll, eventPoint: LocationPoint) {
        serviceScope.launch {
            Log.i(
                tag,
                "handleTollEvent: Toll='${eventToll.name}', Type='${eventToll.type}'"
            )

            val authToken = SecureStorage.getAuthToken(applicationContext)
            val plate = BuildConfig.API_PLATE

            if (authToken == null || plate.isEmpty()) {
                Log.e(tag, "Cannot handle toll event: Auth token or plate is missing.")
                return@launch
            }

            if (eventToll.type == "CLOSED") {
                if (closedTollBuffer == null) {
                    closedTollBuffer = eventToll
                    collectedLocationPoints.add(eventPoint)
                    val messageToSpeak = "Nova viagem iniciada em ${eventToll.name}."
                    speak(messageToSpeak)
                } else if (closedTollBuffer != eventToll) {
                    closedTollBuffer = null
                    collectedLocationPoints.add(eventPoint)
                    val messageToSpeak = "Viagem concluida em ${eventToll.name}."
                    speak(messageToSpeak)
                    postTrip(authToken, plate, collectedLocationPoints)
                    collectedLocationPoints.clear()
                }
            } else {
                if (openedTollLocationBuffer == null) {
                    openedTollLocationBuffer = eventPoint
                    collectedLocationPoints.add(eventPoint)
                    postTrip(authToken, plate, collectedLocationPoints)
                    collectedLocationPoints.clear()
                } else {
                    val currentBuffer = openedTollLocationBuffer
                    if (currentBuffer != null && currentBuffer.timestamp - eventPoint.timestamp < 300000) {
                        collectedLocationPoints.add(eventPoint)
                        postTrip(authToken, plate, collectedLocationPoints)
                        collectedLocationPoints.clear()
                    }
                    openedTollLocationBuffer = eventPoint
                }
            }
        }
    }

    // Cache utilities
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun calculateDistanceToTollCenter(location: LocationPoint, toll: Toll): Double {
        return calculateDistance(location.latitude, location.longitude, toll.latitude, toll.longitude)
    }

    private suspend fun updateTollCache(currentLocation: LocationPoint) {
        tollCache.clear()
        val nearbyTolls = tollList.filter {
            calculateDistanceToTollCenter(currentLocation, it) <= cacheRadiusKm
        }
        tollCache.addAll(nearbyTolls)
        lastCacheUpdate = System.currentTimeMillis()
        Log.d(tag, "Updated toll cache with ${tollCache.size} nearby tolls")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            iLocationProvider = ILocationProvider.Stub.asInterface(service)
            isBound = true
            Log.d(tag, "Connected to LocationProvider and TollsProvider.")

            try {
                iLocationProvider?.registerCallback(locationCallback)
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

    private suspend fun postTrip(
        authToken: String,
        plate: String,
        pointsToPost: List<LocationPoint> // New third argument
    ): String? { // Return type can be String (trip ID/status) or Boolean (success/fail)
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/trips"

        if (pointsToPost.isEmpty()) {
            Log.w(tag, "postTrip called with no location points to send.")
            // Depending on API: return null, or an error, or proceed if API allows empty locations
            return null // Example: Don't proceed if no points
        }

        val locationsJsonArray = JSONArray()
        Log.d(tag, "Preparing to post ${pointsToPost.size} location points provided as argument.")

        pointsToPost.forEach { point ->
            val locationJson = JSONObject()
            try {
                locationJson.put("latitude", String.format("%.17f", point.latitude))
                locationJson.put("longitude", String.format("%.17f", point.longitude))
                locationJson.put("altitude", point.altitude)
                locationJson.put("timestamp", point.timestamp)
                locationJson.put("inHighway", point.inHighway) // Assuming LocationPoint has this
                // locationJson.put("accuracy", String.format("%.1f", point.accuracy)) // If needed
                locationsJsonArray.put(locationJson)
            } catch (e: org.json.JSONException) {
                Log.e(tag, "JSONException while building a location JSON object for postTrip: ${e.message}", e)
                // Skip this point or decide to fail the entire post
            }
        }

        if (locationsJsonArray.length() == 0 && pointsToPost.isNotEmpty()) {
            Log.e(tag, "Failed to serialize any provided location points into JSON for postTrip. Aborting.")
            return null
        }

        val requestBodyJson = JSONObject()
        try {
            requestBodyJson.put("licensePlate", plate)
            requestBodyJson.put("locations", locationsJsonArray)
            requestBodyJson.put("startTripMethod", "AUTO") // Or your desired value
            requestBodyJson.put("stopTripMethod", "AUTO")  // Or your desired value
        } catch (e: org.json.JSONException) {
            Log.e(tag, "JSONException while building the main request body for postTrip: ${e.message}", e)
            return null
        }

        val jsonRequestBodyString = requestBodyJson.toString()
        Log.d(tag, "Post Trip (with argument) Request Body: $jsonRequestBodyString")

        return try {
            val response: HttpResponse = httpClient.post(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
                contentType(ContentType.Application.Json)
                setBody(jsonRequestBodyString)
            }

            val responseBodyText = response.bodyAsText(Charsets.UTF_8)
            Log.d(tag, "Post Trip (with argument) Response Code: ${response.status}")

            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                try {
                    val jsonResponse = JSONObject(responseBodyText)
                    val tripNumber = jsonResponse.optString("tripNumber", jsonResponse.optString("id", ""))
                    Log.i(tag, "Trip posted successfully. Response: $jsonResponse")

                    if (tripNumber.isEmpty()) "SUCCESS_NO_TRIP_ID" else tripNumber
                } catch (e: org.json.JSONException) {
                    Log.e(tag, "Failed to parse JSON from successful postTrip response: ${e.message}. Body: $responseBodyText", e)
                    "SUCCESS_RESPONSE_PARSE_ERROR"
                }
            } else {
                Log.e(tag, "Trip post failed (with argument): ${response.status} - $responseBodyText")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during HTTP POST in postTrip (with argument) to $apiUrl: ${e.message}", e)
            null
        }
    }

    // OnInitListener callback
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language (optional, defaults to device locale)
            // val result = tts.setLanguage(Locale.US) // Example: US English
            val result = tts.setLanguage(Locale("pt", "PT"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(tag, "TTS: The Language specified is not supported!")
                // Optionally, try to install missing language data
                // val installIntent = Intent()
                // installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                // installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                // startActivity(installIntent)
            } else {
                ttsInitialized = true
                Log.i(tag, "TTS Initialization successful.")
                // Process any queued messages
                while (speechQueue.isNotEmpty() && ttsInitialized) {
                    speak(speechQueue.removeFirst())
                }
            }
        } else {
            Log.e(tag, "TTS Initialization Failed! Status: $status")
        }
    }

    // Method to speak text
    private fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!ttsInitialized) {
            Log.w(tag, "TTS not initialized yet. Queuing message: $text")
            speechQueue.addLast(text) // Add to the end of the queue
            // Attempt to re-initialize if it failed previously or wasn't called
            if (!::tts.isInitialized || tts.defaultEngine.isNullOrEmpty()) {
                Log.d(tag, "Re-attempting TTS initialization as it seems to be in a bad state.")
                tts = TextToSpeech(this, this)
            }
            return
        }

        // Generate a unique ID for each utterance (optional but good for tracking)
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle() // Use Bundle for parameters

        // On Android Lollipop (API 21) and above, you can pass an utteranceId.
        // For older versions, this might not be available or might work differently.
        // The speak method itself handles API level differences for utteranceId.

        // Default to QUEUE_ADD so messages don't interrupt each other unless desired
        // Use TextToSpeech.QUEUE_FLUSH to interrupt current speech
        val result = tts.speak(text, queueMode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(tag, "Error speaking text: $text")
        } else {
            Log.d(tag, "TTS speaking: $text (Utterance ID: $utteranceId)")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Client Service Created.")
        createNotificationChannel()

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this) // 'this' refers to OnInitListener

        // Optional: Set a progress listener to know when speech starts/ends
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(tag, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(tag, "TTS finished: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(tag, "TTS error: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(tag, "TTS error with code $errorCode: $utteranceId")
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Client Service Started.")

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.i(tag, "Service started in foreground.")

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

            val json = Json {
                ignoreUnknownKeys = true // skips unknown fields
            }
            val json_string = fetchTolls(currentToken)
            val parsed = json.decodeFromString<TollsResponse>(json_string.toString())
            Log.d(tag, "Got ${parsed.tolls.size} tolls")
            for (toll in parsed.tolls) {
                tollList.add(toll)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Foreground service channel (you likely have this)
            val foregroundServiceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, // Your existing foreground channel ID
                "Toll Tracking Service",
                NotificationManager.IMPORTANCE_LOW // Or your preferred importance
            ).apply {
                description = "Notification for the active toll tracking service."
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(foregroundServiceChannel)
            Log.d(tag, "Notification channels created/updated.")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Toll Tracking Active")
            .setContentText("Monitoring location for toll events.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
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
                iLocationProvider?.unregisterCallback(locationCallback)
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
        // Shutdown TTS
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
            Log.d(tag, "TTS shutdown.")
        }
        super.onDestroy()
        Log.d(tag, "Client Service Destroyed.")
        serviceJob.cancel()
        httpClient.close()
        unbindFromRemoteService()
        stopForeground(true)
        Log.i(tag, "Service stopped from foreground.")
    }
}