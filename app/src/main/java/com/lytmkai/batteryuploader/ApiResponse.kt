package com.lytmkai.batteryuploader

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)
