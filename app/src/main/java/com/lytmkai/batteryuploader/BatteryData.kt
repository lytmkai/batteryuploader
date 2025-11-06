package com.lytmkai.batteryuploader

import com.google.gson.annotations.SerializedName

data class BatteryData(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("level") val level: Int,
    @SerializedName("scale") val scale: Int,
    @SerializedName("percentage") val percentage: Float,
    @SerializedName("status") val status: String,
    @SerializedName("health") val health: String,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("voltage") val voltage: Float,
    @SerializedName("timestamp") val timestamp: Long
)