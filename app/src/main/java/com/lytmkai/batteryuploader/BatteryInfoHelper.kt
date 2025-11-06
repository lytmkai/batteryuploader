package com.lytmkai.batteryuploader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

class BatteryInfoHelper(private val context: Context) {
    
    fun getBatteryData(): BatteryData {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = context.registerReceiver(null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = getBatteryStatus(intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
        val health = getBatteryHealth(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.toFloat()?.div(10) ?: 0f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toFloat() ?: 0f
        val percentage = (level.toFloat() / scale.toFloat()) * 100
        
            // 获取电流信息（微安）
            var current = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 正值表示充电电流，负值表示放电电流
                current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toFloat() / 1000 // 转换为毫安
            }

            // 计算功率（毫瓦）
            val power = (voltage * current).div(1000) // 电压(mV) * 电流(mA) / 1000 = 功率(mW)

        return BatteryData(
            deviceId = getDeviceId(),
            level = level,
            scale = scale,
            percentage = percentage,
            status = status,
            health = health,
            temperature = temperature,
            voltage = voltage,
            current = current,
            power = power,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun getBatteryStatus(status: Int?): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown"
            else -> "Unknown"
        }
    }
    
    private fun getBatteryHealth(health: Int?): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_UNKNOWN -> "Unknown"
            else -> "Unknown"
        }
    }
    
    private fun getDeviceId(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Build.getSerial()
            } catch (e: SecurityException) {
                "unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            Build.SERIAL
        }
        return "${manufacturer}_${model}_${serial}".replace(" ", "_")
    }
}