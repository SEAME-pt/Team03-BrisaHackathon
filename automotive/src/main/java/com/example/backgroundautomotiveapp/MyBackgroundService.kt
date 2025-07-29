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

        // Manually construct the JSON request body string
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
                    val authToken = jsonResponse.optString("authToken", null)
                    if (authToken != null) {
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
                    SecureStorage.saveAuthToken(applicationContext, currentToken) // <-- SAVE THE TOKEN
                } else {
                    Log.e(tag, "Login attempt failed. Unable to acquire token.")
                    // Handle login failure: retry logic, stop service, etc.
                    stopSelf() // Example: stop if login fails
                    return@launch
                }
            } else {
                Log.i(tag, "Existing token found. Using stored token.")
            }

            if (currentToken != null) {
                fetchSomeDataWithToken(currentToken)
            } else {
                Log.e(tag, "No valid token available to perform tasks.")
            }
        }
        return START_STICKY
    }

    private suspend fun fetchSomeDataWithToken(authToken: String): String? {
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/tolls" // Replace with actual endpoint
        return try {
            val response: HttpResponse = httpClient.get(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $authToken") // Common way to send Bearer tokens
                // Or if your API expects it differently:
                // header("X-Auth-Token", authToken)
            }

            Log.d(tag, "GET request to $apiUrl - Status: ${response.status}")
            if (response.status == HttpStatusCode.OK) {
                Log.e(tag, "Data fetched: ${response.bodyAsText()}")
                response.bodyAsText()
            } else if (response.status == HttpStatusCode.Unauthorized) {
                Log.w(tag, "Token is invalid or expired (401 Unauthorized). Clearing token.")
                SecureStorage.clearAuthToken(applicationContext) // Clear the bad token
                // You might want to trigger a re-login attempt here or stop the service.
                null
            } else {
                Log.e(tag, "Error fetching data: ${response.status} - ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during GET request to $apiUrl: ${e.message}", e)
            null
        }
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