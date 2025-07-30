package com.example.backgroundautomotiveapp

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
import com.example.backgroundautomotiveapp.util.SecureStorage

class MyBackgroundService : Service() {

    private val tag = "MyBackgroundService_DEBUG"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Background thread

    private val httpClient by lazy {
        HttpClient(CIO) {
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
                    responseBodyText // Return the full response body text
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
        Log.d(tag, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Service Started")

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

            fetchTolls(currentToken)
            postTrip(currentToken, plate)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service Destroyed")
        serviceJob.cancel()
        httpClient.close()
        Log.d(tag, "HttpClient closed and coroutines cancelled.")
    }
}