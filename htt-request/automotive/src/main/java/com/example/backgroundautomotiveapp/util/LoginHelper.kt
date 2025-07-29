package com.example.backgroundautomotiveapp.util

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object LoginHelper {

    private val httpClient by lazy { HttpClient(CIO) }

    suspend fun loginAndSaveToken(context: Context, email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/login"
            val jsonRequestBody = """
                {
                    "email": "$email",
                    "password": "$password"
                }
            """.trimIndent()

            try {
                val response: HttpResponse = httpClient.post(apiUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(jsonRequestBody)
                }

                val responseBodyText = response.bodyAsText(Charsets.UTF_8)

                if (response.status == HttpStatusCode.OK) {
                    val jsonResponse = JSONObject(responseBodyText)
                    val authToken = jsonResponse.optString("authToken", "")
                    if (authToken.isNotEmpty()) {
                        SecureStorage.saveAuthToken(context, authToken)
                        Log.i("LoginHelper", "Login successful. Token saved.")
                        return@withContext true
                    }
                } else {
                    Log.e("LoginHelper", "Login failed: ${response.status} - $responseBodyText")
                }
            } catch (e: Exception) {
                Log.e("LoginHelper", "Exception during login: ${e.message}", e)
            }
            return@withContext false
        }
    }
}
