package com.lytmkai.batteryuploader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import java.util.concurrent.TimeUnit
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var startStopButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var urlEditText: EditText
    private lateinit var uploadStatusTextView: TextView
    private lateinit var batteryHelper: BatteryInfoHelper
    private var isServiceRunning = false
    
    private val PREFS_NAME = "BatteryUploaderPrefs"
    private val KEY_URL = "upload_url"
    // 延迟重启 WorkManager 的防抖（避免用户每次输入都重启）
    private val saveDelayMs = 1000L
    private val handler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    
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
        urlEditText = findViewById(R.id.urlEditText)
    uploadStatusTextView = findViewById(R.id.uploadStatusTextView)
        
        // 从 SharedPreferences 读取保存的 URL
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        urlEditText.setText(prefs.getString(KEY_URL, ""))
        
        // 在文本变化时保存 URL，并在服务运行时延迟重启 WorkManager 以应用新 URL
        urlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s.toString()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_URL, url)
                    .apply()

                // 如果上传服务正在运行，重启 WorkManager 使其使用新 URL（采用防抖，1s）
                if (isServiceRunning) {
                    // 取消之前的重启任务
                    saveRunnable?.let { handler.removeCallbacks(it) }
                    saveRunnable = Runnable {
                        // 先取消旧的任务，再调度新的
                        WorkManager.getInstance(this@MainActivity).cancelUniqueWork("battery_upload")
                        schedulePeriodicWork()
                        // 立即触发一次上传以验证新 URL
                        triggerImmediateUpload()
                    }
                    handler.postDelayed(saveRunnable!!, saveDelayMs)
                }
            }
        })
    }

    // 仅调度/启动 WorkManager 的周期性任务，不改变 UI 状态或标志（用于重启时调用）
    private fun schedulePeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val uploadRequest = PeriodicWorkRequestBuilder<BatteryUploadWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,  // 指数退避
                WorkRequest.MIN_BACKOFF_MILLIS,  // 最小10秒
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "battery_upload",
            ExistingPeriodicWorkPolicy.UPDATE,
            uploadRequest
        )
    }

    // 立即触发一次上传，并观察状态以更新 UI 日志
    private fun triggerImmediateUpload() {
        val oneTime = OneTimeWorkRequestBuilder<BatteryUploadWorker>().build()
        val wm = WorkManager.getInstance(this)
        wm.enqueue(oneTime)

        // 观察 WorkInfo 更新并显示输出信息
        wm.getWorkInfoByIdLiveData(oneTime.id).observe(this) { info ->
            if (info != null) {
                val state = info.state
                val output = info.outputData
                val stateMsg = when (state) {
                    WorkInfo.State.SUCCEEDED -> "上传成功"
                    WorkInfo.State.FAILED -> "上传失败"
                    WorkInfo.State.RUNNING -> "上传中..."
                    WorkInfo.State.ENQUEUED -> "已入队"
                    else -> state.name
                }
                
                val resultMsg = output.getString("result_message") ?: stateMsg
                val responseData = output.getString("response") ?: ""
                val time = output.getLong("timestamp", 0L)
                val timeStr = if (time > 0L) 
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time)) 
                else ""
                
                val displayMsg = buildString {
                    append("状态: $stateMsg")
                    if (timeStr.isNotEmpty()) append(" ($timeStr)")
                    if (resultMsg.isNotEmpty() && resultMsg != stateMsg) {
                        append("\n结果: $resultMsg")
                    }
                    if (responseData.isNotEmpty()) {
                        append("\n响应: $responseData")
                    }
                }
                
                uploadStatusTextView.text = displayMsg
            }
        }
    }
    
    private fun updateBatteryStatus() {
        val batteryData = batteryHelper.getBatteryData()
        val statusText = """
            电量: ${batteryData.percentage}%
            状态: ${batteryData.status}
            健康状况: ${batteryData.health}
            温度: ${batteryData.temperature}°C
            电压: ${batteryData.voltage}V
            电流: ${String.format("%.2f", batteryData.current)}mA
            功率: ${String.format("%.2f", batteryData.power)}mW
            时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(batteryData.timestamp))}
        """.trimIndent()
        
        statusTextView.text = statusText
    }
    
    private fun startBatteryUploadService() {
        schedulePeriodicWork()
        // 立即触发一次上传
        triggerImmediateUpload()

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

    override fun onDestroy() {
        // 清理 handler 回调，避免内存泄漏
        saveRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}


