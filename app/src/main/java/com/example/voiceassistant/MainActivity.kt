package com.example.voiceassistant

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.voiceassistant.handlers.LockScreenAdminReceiver

/**
 * The ONLY screen in this app. It requests permissions once, requests the battery
 * exemption once, and starts the foreground service. It is never reopened by the
 * wake-word -> command -> reply loop — that all happens headlessly in the service.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var detailText: TextView

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) {
            requestBatteryOptimizationExemption()
            startAssistantService()
        } else {
            // Don't hard-block on optional ones (contacts/SMS/call) — the assistant can
            // still handle time/open-app/calculation/music without them. Just warn.
            Toast.makeText(
                this,
                "Some features will be limited without: ${denied.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
            detailText.text = "Some permissions were denied — a few features will be limited."
            requestBatteryOptimizationExemption()
            startAssistantService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        detailText = findViewById(R.id.detailText)
        askForPermissions()
    }

    private fun askForPermissions() {
        val needed = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(android.Manifest.permission.POST_NOTIFICATIONS)
            needed.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            needed.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val notGranted = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) {
            requestBatteryOptimizationExemption()
            startAssistantService()
        } else {
            detailText.text = "Requesting permissions..."
            requestPermissions.launch(notGranted.toTypedArray())
        }
    }

    /**
     * Without this, Doze/App Standby will suspend the service's background work after
     * a period of inactivity even though the notification is still showing.
     */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startAssistantService() {
        requestDeviceAdminForLockScreen()
        val intent = Intent(this, AssistantForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        detailText.text = "Assistant is running in the background.\nCheck your notification shade."
        Toast.makeText(this, "Assistant is now running in the background.", Toast.LENGTH_SHORT).show()
    }

    /**
     * One-time system prompt so "lock the screen" can work — Android requires explicit
     * device-admin approval for this, there's no regular runtime permission for it.
     * Declining just means that one command replies "I need permission" instead of
     * working; everything else in the app is unaffected.
     */
    private fun requestDeviceAdminForLockScreen() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, LockScreenAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Needed so the assistant can lock your screen on request."
                )
            }
            startActivity(intent)
        }
    }
}
