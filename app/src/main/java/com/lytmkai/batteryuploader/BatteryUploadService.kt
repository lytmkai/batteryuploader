package com.lytmkai.batteryuploader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class BatteryUploadService : Service() {


    private var uploadJob: Job? = null
    private lateinit var batteryHelper: BatteryInfoHelper
    
    private var lastUploadTime = 0L  // 记录上次上传时间
    private val MIN_UPLOAD_INTERVAL = 5000L  // 最小上传间隔5秒


    private val KEY_CURRENT_UNIT = "current_unit_ma"

    companion object {
        const val CHANNEL_ID = "BatteryUploadServiceChannel"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
        const val UPLOAD_INTERVAL_MS = 10 * 60 * 1000L // 15分钟，可调小如 5*60*1000L



        fun startService(context: Context) {
            val intent = Intent(context, BatteryUploadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BatteryUploadService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        batteryHelper = BatteryInfoHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 启动前台服务
        val notification = createNotification()
        startForeground(101, notification)

        // 开始周期上传
        startPeriodicUpload()

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, BatteryUploadService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
             stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("电池数据上传中")
            .setContentText("正在运行")
            .setSmallIcon(R.drawable.ic_battery_alert) // 建议添加一个图标，或用默认
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "电池上传服务",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startPeriodicUpload() {
        uploadJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUploadTime >= MIN_UPLOAD_INTERVAL) {
                        uploadBatteryData()
                        lastUploadTime = currentTime
                    } else {
                        // 如果距离上次上传不足5秒，则等待剩余时间
                        val remainingTime = MIN_UPLOAD_INTERVAL - (currentTime - lastUploadTime)
                        delay(remainingTime)
                        // 再次检查是否可以上传
                        val newCurrentTime = System.currentTimeMillis()
                        if (newCurrentTime - lastUploadTime >= MIN_UPLOAD_INTERVAL) {
                            uploadBatteryData()
                            lastUploadTime = newCurrentTime
                        }
                    }
                } catch (e: Exception) {
                    sendUploadResultBroadcast(500, e.toString(), 1)
                }
                delay(UPLOAD_INTERVAL_MS)
            }
        }
    }

    private suspend fun uploadBatteryData() {
        val prefs = getSharedPreferences("BatteryUploaderPrefs", MODE_PRIVATE)
        

        val url = prefs.getString("upload_url", "") ?: return
        if (url.isBlank()) return

        val currentUnitInmA = prefs.getBoolean(KEY_CURRENT_UNIT, false)

        val batteryData = batteryHelper.getBatteryData(currentUnitInmA)
        val gson = Gson()

        val json = gson.toJson(batteryData)

        val pthis = this

        withContext(Dispatchers.IO) {

            val date = Date()
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeStr = formatter.format(date)


            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.doOutput = true
                connection.outputStream.write(json.toByteArray())

                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()






                val notiMsg = "正在运行 [$timeStr 上传成功]"



                val notification = NotificationCompat.Builder(pthis, CHANNEL_ID)
                    .setContentTitle("电池数据上传中")
                    .setContentText(notiMsg)
                    .setSmallIcon(R.drawable.ic_battery_alert)
                    .setOngoing(true)
                    .build()
                startForeground(101, notification)

                // 可选：发送广播更新 UI（见下文）
                sendUploadResultBroadcast(responseCode, response, System.currentTimeMillis())
            } catch (e: IOException) {

                val notiMsg = "正在运行 [$timeStr 上传失败]"
                val notification = NotificationCompat.Builder(pthis, CHANNEL_ID)
                    .setContentTitle("电池数据上传中")
                    .setContentText(notiMsg)
                    .setSmallIcon(R.drawable.ic_battery_alert)
                    .setOngoing(true)
                    .build()
                startForeground(101, notification)

                sendUploadResultBroadcast(-1, e.message ?: "网络错误", System.currentTimeMillis())
            }
        }
    }

    private fun sendUploadResultBroadcast(code: Int, response: String, timestamp: Long) {
        val intent = Intent("com.lytmkai.batteryuploader.UPLOAD_RESULT")
        intent.putExtra("response_code", code)
        intent.putExtra("response_body", response)
        intent.putExtra("timestamp", timestamp)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        uploadJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}