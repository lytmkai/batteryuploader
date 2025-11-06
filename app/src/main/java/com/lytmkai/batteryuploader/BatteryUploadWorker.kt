package com.lytmkai.batteryuploader

import android.content.Context
import androidx.work.*
import androidx.work.workDataOf
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BatteryUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    private val batteryHelper = BatteryInfoHelper(context)
    private val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
        level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)  // 允许自动重试
        .addInterceptor { chain ->
            var retryCount = 0
            var response: Response? = null
            while (retryCount < 3 && response == null) {
                try {
                    response = chain.proceed(chain.request())
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount == 3) throw e
                }
            }
            response!!
        }
        .addInterceptor(loggingInterceptor)
        .build()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    
    override fun doWork(): Result {
        return try {
            val batteryData = batteryHelper.getBatteryData()
            uploadData(batteryData)
        } catch (e: Exception) {
            val msg = "Exception: ${e.message}"
            Result.failure(workDataOf("result_message" to msg, "result" to "exception"))
        }
    }
    
    private fun uploadData(batteryData: BatteryData): Result {
        val prefs = applicationContext.getSharedPreferences("BatteryUploaderPrefs", Context.MODE_PRIVATE)
        var baseUrl = prefs.getString("upload_url", "") ?: ""
        
        if (baseUrl.isEmpty()) {
            return Result.failure(workDataOf(
                "result_message" to "未配置上传服务器地址",
                "result" to "error"
            ))
        }
        
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://$baseUrl"
        }
        
        
        val jsonBody = gson.toJson(batteryData).toRequestBody(JSON)
        val request = Request.Builder()
            .url(baseUrl )
            .post(jsonBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val timestamp = System.currentTimeMillis()
            val statusMessage = buildString {
                append("HTTP ${response.code}")
                if (!response.isSuccessful) {
                    append(" (")
                    append(response.message)
                    append(")")
                }
                append(" at $timestamp")
            }

            when {
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    val apiResponse = try {
                        gson.fromJson(responseBody, ApiResponse::class.java)
                    } catch (e: Exception) {
                        null
                    }

                    when {
                        apiResponse?.success == true -> {
                            prefs.edit()
                                .putString("last_upload_message", "上传成功")
                                .putLong("last_upload_time", timestamp)
                                .apply()
                            Result.success(workDataOf(
                                "result_message" to "上传成功",
                                "result" to "success",
                                "timestamp" to timestamp,
                                "response" to (responseBody ?: "")
                            ))
                        }
                        responseBody.isNullOrEmpty() -> {
                            prefs.edit()
                                .putString("last_upload_message", "响应为空")
                                .putLong("last_upload_time", timestamp)
                                .apply()
                            Result.retry()
                        }
                        else -> {
                            prefs.edit()
                                .putString("last_upload_message", "服务器返回失败")
                                .putLong("last_upload_time", timestamp)
                                .apply()
                            Result.retry()
                        }
                    }
                }
                response.code == 408 || response.code == 500 || response.code == 502 || response.code == 503 || response.code == 504 -> {
                    // 服务器错误，需要重试
                    prefs.edit()
                        .putString("last_upload_message", statusMessage)
                        .putLong("last_upload_time", timestamp)
                        .apply()
                    Result.retry()
                }
                else -> {
                    // 其他错误，不重试
                    prefs.edit()
                        .putString("last_upload_message", statusMessage)
                        .putLong("last_upload_time", timestamp)
                        .apply()
                    Result.failure(workDataOf(
                        "result_message" to statusMessage,
                        "result" to "error",
                        "timestamp" to timestamp
                    ))
                }
            }
        } catch (e: Exception) {
            val timestamp = System.currentTimeMillis()
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "无法连接到服务器"
                is java.net.ConnectException -> "连接服务器失败"
                is java.net.SocketTimeoutException -> "连接超时"
                else -> e.message ?: "未知错误"
            }
            val msg = "$errorMessage at $timestamp"
            
            prefs.edit()
                .putString("last_upload_message", msg)
                .putLong("last_upload_time", timestamp)
                .apply()
                
            // 对于网络相关的错误进行重试
            when (e) {
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                is java.net.SocketTimeoutException -> Result.retry()
                else -> Result.failure(workDataOf(
                    "result_message" to msg,
                    "result" to "exception",
                    "timestamp" to timestamp
                ))
            }
        }
    }
}
