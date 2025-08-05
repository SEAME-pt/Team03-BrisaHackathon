package com.example.backgroundautomotiveapp.util

data class LicensePlate(
    val value: String,
    val vehicleCategory: String,
    val default: Boolean
)

data class Trip(
    val tripNumber: Int,
    val totalDistance: Int,
    val totalDuration: Int,
    val highways: String,
    val startDate: Long,
    val totalCost: Double,
    val licensePlate: LicensePlate
)

data class TripDetails(
    val tripNumber: Int,
    val tripLegs: List<TripLeg>
)

data class TripLeg(
    val entryTripToll: TripToll?,
    val exitTripToll: TripToll?
)

data class TripToll(
    val tollName: String
)
