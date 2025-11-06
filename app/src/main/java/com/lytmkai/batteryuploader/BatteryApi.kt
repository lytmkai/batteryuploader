package com.lytmkai.batteryuploader

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface BatteryApi {
    @POST(".")
    fun uploadBatteryData(@Body batteryData: BatteryData): Call<ApiResponse>
}
