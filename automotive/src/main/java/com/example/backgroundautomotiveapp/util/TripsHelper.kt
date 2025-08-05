package com.example.backgroundautomotiveapp.util

import android.content.Context
import android.util.Log
//import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.json.JSONObject

object TripsHelper {
    private val httpClient by lazy { HttpClient(CIO) }
    private val tag = "Fetch_trips_DEBUG"
//    private val gson = Gson()

    // Agora método público e retorna lista de Trip
    suspend fun fetchTrips(context: Context, authToken: String): List<Trip> {
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/trips"
        return try {
            val response: HttpResponse = httpClient.get(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }

            Log.d(tag, "GET request to $apiUrl - Status: ${response.status}")
            when (response.status) {
                HttpStatusCode.OK -> {
                    val responseBodyText = response.bodyAsText(Charsets.UTF_8)
                    val jsonObject = JSONObject(responseBodyText)

                    val dataArray = jsonObject.optJSONArray("data")
                    val tripList = mutableListOf<Trip>()

                    for (i in 0 until dataArray.length()) {
                        val tripJson = dataArray.getJSONObject(i)

                        val licensePlateJson = tripJson.getJSONObject("licensePlate")
                        val licensePlate = LicensePlate(
                            value = licensePlateJson.getString("value"),
                            vehicleCategory = licensePlateJson.getString("vehicleCategory"),
                            default = licensePlateJson.getBoolean("default")
                        )

                        val trip = Trip(
                            tripNumber = tripJson.getInt("tripNumber"),
                            totalDistance = tripJson.getInt("totalDistance"),
                            totalDuration = tripJson.getInt("totalDuration"),
                            highways = tripJson.getString("highways"),
                            startDate = tripJson.getLong("startDate"),  // ou converter se for timestamp
                            totalCost = tripJson.getDouble("totalCost"),
                            licensePlate = licensePlate
                        )

                        tripList.add(trip)
                    }

                    Log.i(tag, "Fetched ${tripList.size} trips successfully.")
                    tripList
                }

                HttpStatusCode.Unauthorized -> {
                    Log.w(tag, "Token is invalid or expired (401 Unauthorized). Clearing token.")
                    SecureStorage.clearAuthToken(context.applicationContext)
                    emptyList()
                }

                else -> {
                    val errorBody = response.bodyAsText(Charsets.UTF_8)
                    Log.e(tag, "Error fetching data: ${response.status} - $errorBody")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during GET request to $apiUrl: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchTripDetail(tripNumber: Int, authToken: String): String? {
        val apiUrl = "https://dev.a-to-be.com/mtolling/services/mtolling/trips/$tripNumber"

        return try {
            val response: HttpResponse = httpClient.get(apiUrl) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }

            if (response.status != HttpStatusCode.OK) return null

            val body = response.bodyAsText()
            val tripJson = JSONObject(body)
            val legsArray = tripJson.optJSONArray("tripLegs") ?: return null
            if (legsArray.length() == 0) return null

            val leg = legsArray.getJSONObject(0)
            val entry = leg.optJSONObject("entryTripToll")
            val exit = leg.optJSONObject("exitTripToll")

            val entryName = entry?.optString("tollName")?.takeIf { it.isNotBlank() }
            val exitName = exit?.optString("tollName")?.takeIf { it.isNotBlank() }

            return when {
                entryName != null && exitName != null -> "$entryName - $exitName"
                entryName != null -> entryName
                exitName != null -> exitName
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }



}
