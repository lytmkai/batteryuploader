package com.lytmkais.batteryuploader

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface BatteryApi {
    @POST("battery/upload")
    fun uploadBatteryData(@Body batteryData: BatteryData): Call<ApiResponse>
}
