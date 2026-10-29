package com.example.volumewheel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class CircularVolumeView(
    context: Context,
    private var settings: OverlaySettings
) : View(context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private var currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    private var lastMutedState = isMutedNow

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(8f, 2f, 2f, Color.BLACK)
    }
    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var radius = settings.size.toFloat()
    private val startAngle = -210f
    private val sweepAngle = 240f

    private val centerX get() = width / 2f
    private val centerY get() = height / 2f
    private val muteButtonRadius get() = radius * 0.35f

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    private var isDragging = false
    private var isAdjusting = false
    private var isCenterPress = false
    private var downX = 0f
    private var downY = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private val longPressRunnable = Runnable {
        isDragging = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onDragStart?.invoke()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val sysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val sysMuted = isMutedNow
            if (sysVol != currentVolume || sysMuted != lastMutedState) {
                currentVolume = sysVol
                lastMutedState = sysMuted
                invalidate()
            }
            handler.postDelayed(this, 800)
        }
    }

    private val isMutedNow: Boolean
        get() = try {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } catch (_: Exception) { false }

    init {
        applySettings()
        isClickable = true
        isFocusable = false
    }

    fun applySettings() {
        radius = settings.size.toFloat()
        requestLayout()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(refreshRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (radius * 2 + 24).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Диск
        val shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(lighten(settings.wheelColor, 0.15f), darken(settings.wheelColor, 0.4f)),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        circlePaint.shader = shader
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        circlePaint.shader = null

        // 2. Метки
        tickPaint.color = settings.tickColor
        for (i in 0..maxVolume) {
            val angle = startAngle + sweepAngle * i / maxVolume
            val rad = Math.toRadians(angle.toDouble())
            val innerR = radius - 18f
            val outerR = radius - 6f
            val x1 = centerX + innerR * cos(rad).toFloat()
            val y1 = centerY + innerR * sin(rad).toFloat()
            val x2 = centerX + outerR * cos(rad).toFloat()
            val y2 = centerY + outerR * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // 3. Указатель
        pointerPaint.color = settings.tickColor
        val pointerAngle = startAngle + sweepAngle * currentVolume / maxVolume
        val rad = Math.toRadians(pointerAngle.toDouble())
        val px = centerX + (radius - 30f) * cos(rad).toFloat()
        val py = centerY + (radius - 30f) * sin(rad).toFloat()
        canvas.drawCircle(px, py, 13f, pointerPaint)

        // 4. Кнопка mute в центре
        drawMuteButton(canvas)
    }

    private fun drawMuteButton(canvas: Canvas) {
        val muted = isMutedNow
        val r = muteButtonRadius

        buttonBgPaint.color = when {
            isCenterPress -> darken(settings.wheelColor, 0.55f)
            muted -> adjustAlpha(settings.tickColor, 0.25f)
            else -> darken(settings.wheelColor, 0.35f)
        }
        canvas.drawCircle(centerX, centerY, r, buttonBgPaint)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = adjustAlpha(settings.tickColor, 0.4f)
        }
        canvas.drawCircle(centerX, centerY, r, border)

        drawSpeakerIcon(canvas, centerX, centerY, r * 1.25f, muted, settings.tickColor)
    }

    private fun drawSpeakerIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        muted: Boolean,
        color: Int
    ) {
        val half = size / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        val body = Path().apply {
            moveTo(cx - half * 0.7f, cy - half * 0.25f)
            lineTo(cx - half * 0.35f, cy - half * 0.25f)
            lineTo(cx - half * 0.05f, cy - half * 0.55f)
            lineTo(cx - half * 0.05f, cy + half * 0.55f)
            lineTo(cx - half * 0.35f, cy + half * 0.25f)
            lineTo(cx - half * 0.7f, cy + half * 0.25f)
            close()
        }
        canvas.drawPath(body, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = size * 0.08f
            strokeCap = Paint.Cap.ROUND
        }

        if (muted) {
            canvas.drawLine(
                cx + half * 0.15f, cy - half * 0.35f,
                cx + half * 0.55f, cy + half * 0.35f, strokePaint
            )
            canvas.drawLine(
                cx + half * 0.55f, cy - half * 0.35f,
                cx + half * 0.15f, cy + half * 0.35f, strokePaint
            )
        } else {
            val rect1 = RectF(
                cx - half * 0.05f, cy - half * 0.3f,
                cx + half * 0.4f, cy + half * 0.3f
            )
            canvas.drawArc(rect1, -60f, 120f, false, strokePaint)

            val rect2 = RectF(
                cx - half * 0.15f, cy - half * 0.55f,
                cx + half * 0.7f, cy + half * 0.55f
            )
            canvas.drawArc(rect2, -60f, 120f, false, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                isDragging = false
                isAdjusting = false
                isCenterPress = isInMuteButton(event.x, event.y)
                invalidate()
                handler.postDelayed(
                    longPressRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    onDrag?.invoke(event.rawX - downX, event.rawY - downY)
                } else {
                    val moved = hypot(
                        (event.rawX - downX).toDouble(),
                        (event.rawY - downY).toDouble()
                    )
                    if (moved > touchSlop && !isAdjusting) {
                        handler.removeCallbacks(longPressRunnable)
                        isCenterPress = false
                        isAdjusting = true
                        invalidate()
                    }
                    if (isAdjusting) {
                        updateVolumeFromTouch(event)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (isCenterPress && !isDragging) {
                    toggleMute()
                } else if (!isDragging && !isAdjusting && event.action == MotionEvent.ACTION_UP) {
                    updateVolumeFromTouch(event)
                }
                isDragging = false
                isAdjusting = false
                isCenterPress = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isInMuteButton(x: Float, y: Float): Boolean {
        val dist = hypot((x - centerX).toDouble(), (y - centerY).toDouble())
        return dist < muteButtonRadius
    }

    private fun toggleMute() {
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            lastMutedState = isMutedNow
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) { }
        invalidate()
    }

    private fun updateVolumeFromTouch(event: MotionEvent) {
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
            if (volume > 0 && isMutedNow) {
                try {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0
                    )
                } catch (_: Exception) { }
            }
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI
            )
            lastMutedState = isMutedNow
            invalidate()
        }
    }

    private fun darken(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * (1 - factor)).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun lighten(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + (1f - hsv[2]) * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int = Color.argb(
        (Color.alpha(color) * factor).coerceIn(0f, 255f).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )
}
