package com.example.volumewheel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class VolumeOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var volumeView: CircularVolumeView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var settings: OverlaySettings

    private var viewAdded = false
    private var dragStartX = 0
    private var dragStartY = 0

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applySettingsFromPrefs()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(1, buildNotification())

        settings = OverlaySettings.load(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dm = resources.displayMetrics
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (settings.posX >= 0) settings.posX
                else (dm.widthPixels - settings.size * 2) / 2
            y = if (settings.posY >= 0) settings.posY
                else (dm.heightPixels - settings.size * 2) / 2
        }

        buildView()
        if (settings.enabled) {
            windowManager.addView(volumeView, params)
            viewAdded = true
        }

        registerReceiver(settingsReceiver, IntentFilter(ACTION_SETTINGS_CHANGED))
    }

    private fun buildView() {
        volumeView = CircularVolumeView(this, settings).also { v ->
            v.onDragStart = {
                dragStartX = params.x
                dragStartY = params.y
            }
            v.onDrag = { dx, dy ->
                params.x = dragStartX + dx.toInt()
                params.y = dragStartY + dy.toInt()
                if (viewAdded) windowManager.updateViewLayout(volumeView, params)
            }
            v.onDragEnd = {
                OverlaySettings.savePosition(this, params.x, params.y)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun applySettingsFromPrefs() {
        val oldSize = settings.size
        settings = OverlaySettings.load(this)

        if (viewAdded) {
            windowManager.removeView(volumeView)
            viewAdded = false
        }
        buildView()

        if (settings.size != oldSize) {
            val delta = settings.size - oldSize
            params.x -= delta
            params.y -= delta
            OverlaySettings.savePosition(this, params.x, params.y)
        }

        if (settings.enabled) {
            windowManager.addView(volumeView, params)
            viewAdded = true
        }
    }

    override fun onDestroy() {
        isRunning = false
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        if (viewAdded) {
            windowManager.removeView(volumeView)
            viewAdded = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Volume Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volume Wheel")
            .setContentText("Плавающий регулятор громкости активен")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "volume_overlay_channel"
        const val ACTION_SETTINGS_CHANGED = "com.example.volumewheel.SETTINGS_CHANGED"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
