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
import android.util.Log
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

    companion object {
        const val VOLUME_SCALE = 4
        private const val TAG = "VolumeWheel"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Реальные максимумы — читаем заново при каждом взаимодействии
    private var systemMaxVolume: Int = 0
    private var displayMaxVolume: Int = 0

    private var currentVolume: Int = 0

    // --- Кисти ---
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }
    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val knobRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val knobEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val knobIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // --- Геометрия ---
    private var radius = settings.size.toFloat()
    private val startAngle = -210f
    private val sweepAngle = 240f

    private val centerX get() = width / 2f
    private val centerY get() = height / 2f

    private val muteButtonRadius get() = radius * 0.22f
    private val innerKnobCenterR get() = radius * 0.52f
    private val innerKnobHalfWidth get() = radius * 0.065f

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

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
            refreshMaxVolume()
            if (!isAdjusting && !isDragging) {
                val sysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val virtualVol = sysVol * VOLUME_SCALE
                if (virtualVol != currentVolume) {
                    currentVolume = virtualVol
                    invalidate()
                }
            }
            handler.postDelayed(this, 800)
        }
    }

    private val isMutedNow: Boolean get() = currentVolume == 0

    init {
        systemMaxVolume = readSystemMax()
        displayMaxVolume = systemMaxVolume * VOLUME_SCALE
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * VOLUME_SCALE
        Log.d(TAG, "init: systemMax=$systemMaxVolume displayMax=$displayMaxVolume current=$currentVolume")
        applySettings()
        isClickable = true
        isFocusable = false
    }

    /**
     * Читаем реальный максимум из системы.
     * Некоторые прошивки отдают 15 при старте, но 63 после инициализации аудио —
     * поэтому вызываем при каждом взаимодействии, а не только в конструкторе.
     */
    private fun readSystemMax(): Int {
        return try {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        } catch (_: Exception) {
            15
        }
    }

    private fun refreshMaxVolume(): Boolean {
        val newMax = readSystemMax()
        if (newMax != systemMaxVolume) {
            Log.d(TAG, "maxVolume changed: $systemMaxVolume -> $newMax")
            val oldDisplayMax = displayMaxVolume
            systemMaxVolume = newMax
            displayMaxVolume = newMax * VOLUME_SCALE
            if (oldDisplayMax > 0) {
                currentVolume = (currentVolume.toFloat() / oldDisplayMax * displayMaxVolume)
                    .roundToInt()
                    .coerceIn(0, displayMaxVolume)
            }
            return true
        }
        return false
    }

    fun applySettings() {
        radius = settings.size.toFloat()
        requestLayout()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMaxVolume()
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

        val shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(lighten(settings.wheelColor, 0.15f), darken(settings.wheelColor, 0.4f)),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        circlePaint.shader = shader
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        circlePaint.shader = null

        tickPaint.color = settings.tickColor
        for (i in 0..displayMaxVolume) {
            val angle = startAngle + sweepAngle * i / displayMaxVolume
            val rad = Math.toRadians(angle.toDouble())
            val innerR = radius - 18f
            val outerR = radius - 6f
            val x1 = centerX + innerR * cos(rad).toFloat()
            val y1 = centerY + innerR * sin(rad).toFloat()
            val x2 = centerX + outerR * cos(rad).toFloat()
            val y2 = centerY + outerR * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        drawInnerKnob(canvas)
        drawMuteButton(canvas)
    }

    private fun drawInnerKnob(canvas: Canvas) {
        val ringR = innerKnobCenterR
        val ringW = innerKnobHalfWidth * 2

        knobRingPaint.strokeWidth = ringW
        knobRingPaint.color = darken(settings.wheelColor, 0.5f)
        canvas.drawCircle(centerX, centerY, ringR, knobRingPaint)

        knobEdgePaint.color = adjustAlpha(settings.tickColor, 0.22f)
        canvas.drawCircle(centerX, centerY, ringR - ringW / 2f, knobEdgePaint)
        canvas.drawCircle(centerX, centerY, ringR + ringW / 2f, knobEdgePaint)

        val markerAngle = startAngle + sweepAngle * currentVolume / displayMaxVolume
        val rad = Math.toRadians(markerAngle.toDouble())
        val rIn = radius * 0.44f
        val rOut = radius * 0.60f
        val x1 = centerX + rIn * cos(rad).toFloat()
        val y1 = centerY + rIn * sin(rad).toFloat()
        val x2 = centerX + rOut * cos(rad).toFloat()
        val y2 = centerY + rOut * sin(rad).toFloat()

        knobIndicatorPaint.color = settings.tickColor
        knobIndicatorPaint.strokeWidth = innerKnobHalfWidth * 0.9f
        knobIndicatorPaint.setShadowLayer(4f, 0f, 0f, settings.tickColor)
        canvas.drawLine(x1, y1, x2, y2, knobIndicatorPaint)
        knobIndicatorPaint.clearShadowLayer()
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

        drawSpeakerIcon(canvas, centerX, centerY, r * 1.35f, muted, settings.tickColor)
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
                when {
                    isDragging -> onDragEnd?.invoke()
                    isCenterPress && event.action == MotionEvent.ACTION_UP -> muteToZero()
                    !isAdjusting && event.action == MotionEvent.ACTION_UP -> updateVolumeFromTouch(event)
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

    private fun muteToZero() {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            currentVolume = 0
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) { }
        invalidate()
    }

    private fun updateVolumeFromTouch(event: MotionEvent) {
        // Всегда читаем актуальный максимум перед вычислением
        refreshMaxVolume()

        val dx = event.x - centerX
        val dy = event.y - centerY
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

        val arcCenter = startAngle + sweepAngle / 2f
        while (angle - arcCenter > 180f) angle -= 360f
        while (arcCenter - angle > 180f) angle += 360f

        val clampedAngle = angle.coerceIn(startAngle, startAngle + sweepAngle)

        val virtualVolume = ((clampedAngle - startAngle) / sweepAngle * displayMaxVolume)
            .roundToInt()
            .coerceIn(0, displayMaxVolume)

        // Если виртуальный максимум достигнут — целимся прямо в системный максимум
        val systemVol = if (virtualVolume >= displayMaxVolume) {
            systemMaxVolume
        } else {
            (virtualVolume.toFloat() / VOLUME_SCALE)
                .roundToInt()
                .coerceIn(0, systemMaxVolume)
        }

        if (virtualVolume != currentVolume) {
            currentVolume = virtualVolume

            if (systemVol > 0) {
                try {
                    if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0
                        )
                    }
                } catch (_: Exception) { }
            }

            try {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC, systemVol, AudioManager.FLAG_SHOW_UI
                )
                Log.d(TAG, "set volume: virtual=$virtualVolume/$displayMaxVolume system=$systemVol/$systemMaxVolume")
            } catch (e: Exception) {
                Log.e(TAG, "setStreamVolume failed: $systemVol", e)
            }

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