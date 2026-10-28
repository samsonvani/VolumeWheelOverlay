package com.example.volumewheel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class CircularVolumeView(context: Context) : View(context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private var currentVolume =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.LTGRAY
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val radius = 120f
    private val centerX get() = width / 2f
    private val centerY get() = height / 2f
    private val startAngle = -210f
    private val sweepAngle = 240f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Тики: по одному на каждый уровень громкости
        for (i in 0..maxVolume) {
            val angle = startAngle + sweepAngle * i / maxVolume
            val rad = Math.toRadians(angle.toDouble())
            val innerR = radius - 15f
            val outerR = radius - 5f
            val x1 = centerX + innerR * cos(rad).toFloat()
            val y1 = centerY + innerR * sin(rad).toFloat()
            val x2 = centerX + outerR * cos(rad).toFloat()
            val y2 = centerY + outerR * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Указатель текущей громкости
        val pointerAngle = startAngle + sweepAngle * currentVolume / maxVolume
        val rad = Math.toRadians(pointerAngle.toDouble())
        val px = centerX + (radius - 25f) * cos(rad).toFloat()
        val py = centerY + (radius - 25f) * sin(rad).toFloat()
        canvas.drawCircle(px, py, 10f, pointerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

                if (angle < startAngle) angle += 360f
                if (angle > startAngle + sweepAngle) angle = startAngle + sweepAngle

                val volume = ((angle - startAngle) / sweepAngle * maxVolume)
                    .roundToInt()
                    .coerceIn(0, maxVolume)

                if (volume != currentVolume) {
                    currentVolume = volume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}