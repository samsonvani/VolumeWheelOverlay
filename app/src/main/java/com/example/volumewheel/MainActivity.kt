package com.example.volumewheel

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_overlay_permission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_start_stop).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Сначала разрешите «Поверх других приложений»",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val intent = Intent(this, VolumeOverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Регулятор запущен", Toast.LENGTH_SHORT).show()
        }
    }
}
