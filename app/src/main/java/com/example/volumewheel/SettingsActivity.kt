package com.example.volumewheel

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: OverlaySettings
    private lateinit var wheelPreview: View
    private lateinit var tickPreview: View
    private lateinit var sizeLabel: TextView
    private lateinit var alphaLabel: TextView
    private lateinit var timeoutLabel: TextView
    private lateinit var seekAlpha: SeekBar
    private lateinit var seekTimeout: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = OverlaySettings.load(this)

        val switchEnabled = findViewById<SwitchCompat>(R.id.switch_enabled)
        val switchFadeEnabled = findViewById<SwitchCompat>(R.id.switch_fade_enabled)
        val seekSize = findViewById<SeekBar>(R.id.seek_size)
        seekAlpha = findViewById(R.id.seek_inactive_alpha)
        seekTimeout = findViewById(R.id.seek_inactive_timeout)
        val btnWheelColor = findViewById<Button>(R.id.btn_wheel_color)
        val btnTickColor = findViewById<Button>(R.id.btn_tick_color)

        wheelPreview = findViewById(R.id.wheel_color_preview)
        tickPreview = findViewById(R.id.tick_color_preview)
        sizeLabel = findViewById(R.id.size_label)
        alphaLabel = findViewById(R.id.alpha_label)
        timeoutLabel = findViewById(R.id.timeout_label)

        switchEnabled.isChecked = settings.enabled
        switchFadeEnabled.isChecked = settings.inactiveFadeEnabled

        seekSize.max = OverlaySettings.SIZE_MAX - OverlaySettings.SIZE_MIN
        seekSize.progress = (settings.size - OverlaySettings.SIZE_MIN)
            .coerceIn(0, seekSize.max)

        seekAlpha.max = OverlaySettings.ALPHA_MAX_PCT - OverlaySettings.ALPHA_MIN_PCT
        seekAlpha.progress = ((settings.inactiveAlpha * 100f).toInt() - OverlaySettings.ALPHA_MIN_PCT)
            .coerceIn(0, seekAlpha.max)

        seekTimeout.max = OverlaySettings.TIMEOUT_MAX - OverlaySettings.TIMEOUT_MIN
        seekTimeout.progress = (settings.inactiveTimeoutSec - OverlaySettings.TIMEOUT_MIN)
            .coerceIn(0, seekTimeout.max)

        updatePreviews()
        updateSizeLabel()
        updateAlphaLabel()
        updateTimeoutLabel()
        updateFadeControlsEnabled()

        switchEnabled.setOnCheckedChangeListener { _, checked ->
            settings = settings.copy(enabled = checked)
            saveAndNotify()
            if (checked && !VolumeOverlayService.isRunning && Settings.canDrawOverlays(this)) {
                val intent = Intent(this, VolumeOverlayService::class.java)
                ContextCompat.startForegroundService(this, intent)
            }
        }

        switchFadeEnabled.setOnCheckedChangeListener { _, checked ->
            settings = settings.copy(inactiveFadeEnabled = checked)
            updateFadeControlsEnabled()
            saveAndNotify()
        }

        seekSize.setOnSeekBarChangeListener(simpleListener { progress ->
            settings = settings.copy(size = progress + OverlaySettings.SIZE_MIN)
            updateSizeLabel()
            saveAndNotify()
        })

        seekAlpha.setOnSeekBarChangeListener(simpleListener { progress ->
            val pct = progress + OverlaySettings.ALPHA_MIN_PCT
            settings = settings.copy(inactiveAlpha = pct / 100f)
            updateAlphaLabel()
            saveAndNotify()
        })

        seekTimeout.setOnSeekBarChangeListener(simpleListener { progress ->
            settings = settings.copy(
                inactiveTimeoutSec = progress + OverlaySettings.TIMEOUT_MIN
            )
            updateTimeoutLabel()
            saveAndNotify()
        })

        btnWheelColor.setOnClickListener { showWheelColorPicker() }
        btnTickColor.setOnClickListener { showTickColorPicker() }
    }

    /** Ползунки прозрачности/таймаута активны только при включённом режиме. */
    private fun updateFadeControlsEnabled() {
        val enabled = settings.inactiveFadeEnabled
        seekAlpha.isEnabled = enabled
        seekTimeout.isEnabled = enabled
        alphaLabel.alpha = if (enabled) 1f else 0.4f
        timeoutLabel.alpha = if (enabled) 1f else 0.4f
    }

    private fun simpleListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

    private fun updatePreviews() {
        wheelPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(settings.wheelColor)
            setStroke(2, Color.WHITE)
        }
        tickPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(settings.tickColor)
            setStroke(2, Color.BLACK)
        }
    }

    private fun updateSizeLabel() { sizeLabel.text = "Размер: ${settings.size} px" }
    private fun updateAlphaLabel() {
        val pct = (settings.inactiveAlpha * 100).toInt()
        alphaLabel.text = "Прозрачность в покое: $pct%"
    }
    private fun updateTimeoutLabel() {
        timeoutLabel.text = "Таймаут неактивности: ${settings.inactiveTimeoutSec} с"
    }

    private fun saveAndNotify() {
        OverlaySettings.save(this, settings)
        sendBroadcast(Intent(VolumeOverlayService.ACTION_SETTINGS_CHANGED))
    }

    private fun showWheelColorPicker() {
        val (colors, names) = OverlaySettings.wheelColorPresets()
        AlertDialog.Builder(this)
            .setTitle("Цвет регулятора")
            .setItems(names) { _, i ->
                settings = settings.copy(wheelColor = colors[i])
                updatePreviews()
                saveAndNotify()
            }
            .show()
    }

    private fun showTickColorPicker() {
        val (colors, names) = OverlaySettings.tickColorPresets()
        AlertDialog.Builder(this)
            .setTitle("Цвет меток")
            .setItems(names) { _, i ->
                settings = settings.copy(tickColor = colors[i])
                updatePreviews()
                saveAndNotify()
            }
            .show()
    }
}
