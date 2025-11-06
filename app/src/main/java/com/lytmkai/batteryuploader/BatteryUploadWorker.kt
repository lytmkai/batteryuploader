package com.lytmkai.batteryuploader

import android.content.Context
import androidx.work.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class BatteryUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    private val batteryHelper = BatteryInfoHelper(context)
    private val apiService = Retrofit.Builder()
        .baseUrl("https://your-server.com/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BatteryApi::class.java)
    
    override fun doWork(): Result {
        return try {
            val batteryData = batteryHelper.getBatteryData()
            uploadData(batteryData)
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    private fun uploadData(batteryData: BatteryData): Result {
        val call = apiService.uploadBatteryData(batteryData)
        var result = Result.success()
        
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true) {
                    result = Result.success()
                } else {
                    result = Result.retry()
                }
            } else {
                result = Result.retry()
            }
        } catch (e: Exception) {
            result = Result.retry()
        }
        
        return result
    }
}
