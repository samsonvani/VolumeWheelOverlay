package com.example.volumewheel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val settings = OverlaySettings.load(context)
        if (!settings.enabled) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            val i = Intent(context, VolumeOverlayService::class.java)
            ContextCompat.startForegroundService(context, i)
        } catch (_: Exception) {
            // На Android 12+ / 14+ запуск FGS из background может быть запрещён —
            // тогда сервис поднимется при следующем открытии приложения.
        }
    }
}
