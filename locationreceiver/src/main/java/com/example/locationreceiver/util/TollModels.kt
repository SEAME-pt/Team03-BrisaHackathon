package com.example.locationreceiver.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeofencePoint(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class Geofence(
    val geofencePoints: List<GeofencePoint>
)

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
