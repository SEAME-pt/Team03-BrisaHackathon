package com.example.backgroundautomotiveapp
import com.google.gson.annotations.SerializedName

class TripData {


    data class TripDetailResponse(
        @SerializedName("tripNumber") val tripNumber: Int,
        @SerializedName("totalDistance") val totalDistance: Int, // em metros
        @SerializedName("totalDuration") val totalDuration: Long, // em milissegundos ou segundos (confirmar)
        @SerializedName("startDateTime") val startDateTime: Long, // epoch millis
        @SerializedName("endDateTime") val endDateTime: Long, // epoch millis
        @SerializedName("totalCost") val totalCost: Double,
        @SerializedName("licensePlate") val licensePlate: LicensePlateInfo,
        @SerializedName("tripLegs") val tripLegs: List<TripLegInfo>?, // Pode ser nulo ou vazio
        @SerializedName("startTripMethod") val startTripMethod: String?,
        @SerializedName("stopTripMethod") val stopTripMethod: String?
    )

    data class LicensePlateInfo(
        @SerializedName("value") val value: String,
        @SerializedName("vehicleCategory") val vehicleCategory: String?,
        @SerializedName("default") val default: Boolean?
    )

    data class TripLegInfo(
        // Adicione os campos que existem em cada objeto de tripLegs
        // Exemplo:
        @SerializedName("id") val id: String?,
        @SerializedName("distance") val distance: Int?,
        @SerializedName("description") val description: String?
        // ... outros campos do tripLeg
    )
}

