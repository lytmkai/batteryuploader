package com.example.batteryuploader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var startStopButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var batteryHelper: BatteryInfoHelper
    private var isServiceRunning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        batteryHelper = BatteryInfoHelper(this)
        updateBatteryStatus()
        
        requestPermissions()
        
        startStopButton.setOnClickListener {
            if (isServiceRunning) {
                stopBatteryUploadService()
            } else {
                startBatteryUploadService()
            }
        }
    }
    
    private fun initViews() {
        startStopButton = findViewById(R.id.startStopButton)
        statusTextView = findViewById(R.id.statusTextView)
    }
    
    private fun updateBatteryStatus() {
        val batteryData = batteryHelper.getBatteryData()
        val statusText = """
            电量: ${batteryData.percentage}%
            状态: ${batteryData.status}
            健康状况: ${batteryData.health}
            温度: ${batteryData.temperature}°C
            电压: ${batteryData.voltage}V
            时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(batteryData.timestamp))}
        """.trimIndent()
        
        statusTextView.text = statusText
    }
    
    private fun startBatteryUploadService() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val uploadRequest = PeriodicWorkRequestBuilder<BatteryUploadWorker>(
            15, TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "battery_upload",
            ExistingPeriodicWorkPolicy.KEEP,
            uploadRequest
        )
        
        isServiceRunning = true
        startStopButton.text = "停止上传"
        Toast.makeText(this, "电池信息上传服务已启动", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopBatteryUploadService() {
        WorkManager.getInstance(this).cancelUniqueWork("battery_upload")
        isServiceRunning = false
        startStopButton.text = "开始上传"
        Toast.makeText(this, "电池信息上传服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.INTERNET)
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_NETWORK_STATE)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
}


