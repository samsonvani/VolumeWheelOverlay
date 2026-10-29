package com.example.volumewheel

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: OverlaySettings
    private lateinit var wheelPreview: View
    private lateinit var tickPreview: View
    private lateinit var sizeLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = OverlaySettings.load(this)

        val switchEnabled = findViewById<SwitchCompat>(R.id.switch_enabled)
        val seekSize = findViewById<SeekBar>(R.id.seek_size)
        val btnWheelColor = findViewById<Button>(R.id.btn_wheel_color)
        val btnTickColor = findViewById<Button>(R.id.btn_tick_color)
        wheelPreview = findViewById(R.id.wheel_color_preview)
        tickPreview = findViewById(R.id.tick_color_preview)
        sizeLabel = findViewById(R.id.size_label)

        switchEnabled.isChecked = settings.enabled
        seekSize.max = 180
        seekSize.progress = settings.size - 80
        updatePreviews()
        updateSizeLabel()

        switchEnabled.setOnCheckedChangeListener { _, checked ->
            settings = settings.copy(enabled = checked)
            saveAndNotify()
        }

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings = settings.copy(size = progress + 80)
                    updateSizeLabel()
                    saveAndNotify()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnWheelColor.setOnClickListener { showWheelColorPicker() }
        btnTickColor.setOnClickListener { showTickColorPicker() }
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

    private fun updateSizeLabel() {
        sizeLabel.text = "Размер: ${settings.size} px"
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
