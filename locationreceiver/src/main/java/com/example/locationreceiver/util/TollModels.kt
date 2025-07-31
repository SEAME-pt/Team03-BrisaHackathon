package com.example.locationreceiver.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Those data structure are define in a way they are automatically deserializable by kotlin plugin.
//Ultimately the one that matters is the TollsResponse which is then use to compute centroids
@Serializable
data class GeofencePoint(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class Geofence(
    val geofencePoints: List<GeofencePoint>
)

// Is this going to work when desiarilized
enum class TollState {
    OPEN,
    CLOSED
}

@Serializable
data class Toll(
    val code: String,
    val name: String,
    val highway: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val geofences: List<Geofence>
)

@Serializable
data class TollsResponse(
    @SerialName("tollsList")
    val tolls: List<Toll>
)