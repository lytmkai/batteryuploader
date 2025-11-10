package com.lytmkai.batteryuploader

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit


class MainActivity : AppCompatActivity() {
    
    private lateinit var startStopButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var urlEditText: EditText

    private lateinit var uploadStatusTextView: TextView

    private lateinit var swCurrentUnit: SwitchCompat

    private lateinit var batteryHelper: BatteryInfoHelper
    private var isServiceRunning = false
    
    private val PREFS_NAME = "BatteryUploaderPrefs"
    private val KEY_URL = "upload_url"
    private val KEY_CURRENT_UNIT = "current_unit_ma"
    private val handler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null






    private val uploadResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code = intent.getIntExtra("response_code", -1)
            val response = intent.getStringExtra("response_body") ?: ""
            val timestamp = intent.getLongExtra("timestamp", 0L)

            val timeStr = if (timestamp > 0L) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            } else ""

            val stateMsg = if (code in 200..299) "上传成功" else "上传失败"
            val displayMsg = buildString {
                append("$timeStr:$stateMsg")
                if (response.isNotEmpty()) {
                    append("\n响应: $response")
                }
            }
            uploadStatusTextView.text = displayMsg
        }
    }
        
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


        registerReceiver(uploadResultReceiver,IntentFilter("com.lytmkai.batteryuploader.UPLOAD_RESULT") )



        statusTextView.isFocusable = true
        statusTextView.isFocusableInTouchMode = true
        statusTextView.requestFocus() // 现在可以成功获取焦点

        urlEditText.clearFocus()
        
    }
    
    private fun initViews() {
        startStopButton = findViewById(R.id.startStopButton)
        statusTextView = findViewById(R.id.statusTextView)
        urlEditText = findViewById(R.id.urlEditText)
        uploadStatusTextView = findViewById(R.id.uploadStatusTextView)
        swCurrentUnit = findViewById(R.id.swCurrentUnit)


        
        // 从 SharedPreferences 读取保存的 URL
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        urlEditText.setText(prefs.getString(KEY_URL, ""))

        val currentUnitInmA = prefs.getBoolean(KEY_CURRENT_UNIT, false)

        if ( currentUnitInmA ){
            swCurrentUnit.isChecked = true

        }
        else{
            swCurrentUnit.isChecked = false

        }
        
        
        // 在文本变化时保存 URL，并在服务运行时延迟重启 WorkManager 以应用新 URL
        urlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s.toString()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit {
                        putString(KEY_URL, url)
                    }
                
            }
        })

        swCurrentUnit.setOnCheckedChangeListener{_, isChecked->

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit {
                    putBoolean(KEY_CURRENT_UNIT, isChecked)
                }


            updateBatteryStatus()
        }

    }

    @SuppressLint("DefaultLocale")
    private fun updateBatteryStatus() {

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentUnitInmA = prefs.getBoolean(KEY_CURRENT_UNIT, false)

        val batteryData = batteryHelper.getBatteryData(currentUnitInmA)
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

        // 注销广播
        unregisterReceiver(uploadResultReceiver)



        super.onDestroy()
    }
    

    private fun startBatteryUploadService() {
        BatteryUploadService.startService(this)
        isServiceRunning = true
        startStopButton.text = "停止上传"
        Toast.makeText(this, "电池上传服务已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopBatteryUploadService() {
        BatteryUploadService.stopService(this)
        isServiceRunning = false
        startStopButton.text = "开始上传"
        uploadStatusTextView.text = "上传已停止"
        Toast.makeText(this, "电池上传服务已停止", Toast.LENGTH_SHORT).show()
    }


}


