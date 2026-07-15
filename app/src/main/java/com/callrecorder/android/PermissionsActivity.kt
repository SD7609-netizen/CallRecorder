package com.callrecorder.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callrecorder.android.databinding.ActivityPermissionsBinding
import com.callrecorder.android.util.Prefs

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updateStates() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.isPermissionsDone(this) && allGranted()) {
            goMain(); return
        }
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnGrant.setOnClickListener {
            if (allGranted()) {
                Prefs.setPermissionsDone(this, true)
                goMain()
            } else {
                permissionLauncher.launch(requiredPermissions)
                if (!batteryIgnored()) {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
            }
        }
        updateStates()
    }

    override fun onResume() {
        super.onResume()
        updateStates()
    }

    private fun updateStates() {
        binding.ivMic.setImageResource(icon(has(Manifest.permission.RECORD_AUDIO)))
        binding.ivContacts.setImageResource(icon(has(Manifest.permission.READ_CONTACTS)))
        binding.ivPhone.setImageResource(icon(has(Manifest.permission.READ_PHONE_STATE)))
        binding.ivBattery.setImageResource(icon(batteryIgnored()))
        binding.ivAccessibility.setImageResource(icon(accessibilityEnabled()))
        binding.btnGrant.text = if (allGranted()) "Продолжить" else "Предоставить разрешения"
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun allGranted() = requiredPermissions.all { has(it) }

    private fun batteryIgnored(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun accessibilityEnabled(): Boolean {
        val v = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return v.contains("$packageName/.service.RecorderAccessibilityService")
    }

    private fun icon(ok: Boolean) = if (ok) R.drawable.ic_check_green else R.drawable.ic_cross_red

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
