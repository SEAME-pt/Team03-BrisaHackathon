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
import kotlin.coroutines.coroutineContext

class MyBackgroundService : Service() {

    private val tag = "MyBackgroundService_DEBUG"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Background thread

    private val httpClient by lazy {
        HttpClient(CIO) {

        }
    }

    suspend fun performLoginManually(email: String, password: String): String? { // Returns authToken string or null
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/login"

        // Manually construct the JSON request body string
        val jsonRequestBody = """
            {
                "email": "$email",
                "password": "$password"
            }
        """.trimIndent() // trimIndent is useful for multi-line strings

        return try {
            Log.d("Ktor_Login_Manual", "Attempting login to $apiUrl with body: $jsonRequestBody")
            val response: HttpResponse = httpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(jsonRequestBody) // Send the raw JSON string
            }

            Log.d("Ktor_Login_Manual", "Response Code: ${response.status}")
            val responseBodyText = response.bodyAsText(Charsets.UTF_8) // Specify charset if needed
            Log.d("Ktor_Login_Manual", "Response Body: $responseBodyText")

            if (response.status == HttpStatusCode.OK) {
                // Manually parse the JSON response to get the authToken
                try {
                    val jsonResponse = JSONObject(responseBodyText)
                    val authToken = jsonResponse.optString("authToken", null)
                    if (authToken != null) {
                        Log.i("Ktor_Login_Manual", "Login successful. AuthToken: $authToken")
                        // You could also parse licensePlates here if needed using jsonResponse.optJSONArray("licensePlates")
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
            val emailFromSomewhere = "seame3@teste.com"
            val passwordFromSomewhere = "}jcUX]BBp*73"

            val authToken = performLoginManually(emailFromSomewhere, passwordFromSomewhere)

            if (authToken != null) {
                Log.i(tag, "Successfully logged in (manual). Token: $authToken")
                // Now you can store authToken securely and use it for future API calls
                // Example: performBackgroundTask(authToken)
            } else {
                Log.e(tag, "Login attempt failed (manual).")
            }
        }
        return START_STICKY
    }

    private suspend fun performBackgroundTask(authToken: String) { // Takes authToken
        var count = 0
        while (coroutineContext.isActive) {
            delay(5000)
            count++
            Log.d(tag, "Background task running... Count: $count. Using token: $authToken")
            // Make authenticated API calls here
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