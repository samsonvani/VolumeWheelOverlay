package com.example.volumewheel

import android.content.Context

data class OverlaySettings(
    val enabled: Boolean = true,
    val wheelColor: Int = 0xFF2A2A2A.toInt(),
    val tickColor: Int = 0xFFFFFFFF.toInt(),
    val size: Int = 120
) {
    companion object {
        private const val PREFS = "overlay_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WHEEL_COLOR = "wheel_color"
        private const val KEY_TICK_COLOR = "tick_color"
        private const val KEY_SIZE = "size"

        fun load(context: Context): OverlaySettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return OverlaySettings(
                enabled = p.getBoolean(KEY_ENABLED, true),
                wheelColor = p.getInt(KEY_WHEEL_COLOR, 0xFF2A2A2A.toInt()),
                tickColor = p.getInt(KEY_TICK_COLOR, 0xFFFFFFFF.toInt()),
                size = p.getInt(KEY_SIZE, 120)
            )
        }

        fun save(context: Context, settings: OverlaySettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                putBoolean(KEY_ENABLED, settings.enabled)
                putInt(KEY_WHEEL_COLOR, settings.wheelColor)
                putInt(KEY_TICK_COLOR, settings.tickColor)
                putInt(KEY_SIZE, settings.size)
                apply()
            }
        }

        fun wheelColorPresets(): Pair<IntArray, Array<String>> {
            val colors = intArrayOf(
                0xFF1A1A1A.toInt(), 0xFF2A2A2A.toInt(), 0xFF37474F.toInt(),
                0xFF1A237E.toInt(), 0xFF1B5E20.toInt(), 0xFF4A148C.toInt(),
                0xFFB71C1C.toInt(), 0xFFE65100.toInt(), 0xFF004D40.toInt(),
                0xFF3E2723.toInt()
            )
            val names = arrayOf(
                "Чёрный", "Тёмно-серый", "Графит", "Тёмно-синий",
                "Тёмно-зелёный", "Тёмно-фиолетовый", "Тёмно-красный",
                "Тёмно-оранжевый", "Тёмно-бирюзовый", "Тёмно-коричневый"
            )
            return colors to names
        }

        fun tickColorPresets(): Pair<IntArray, Array<String>> {
            val colors = intArrayOf(
                0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E676.toInt(),
                0xFF40C4FF.toInt(), 0xFFFF4081.toInt(), 0xFFFFAB40.toInt(),
                0xFFE0E0E0.toInt(), 0xFFF5F5F5.toInt()
            )
            val names = arrayOf(
                "Белый", "Жёлтый", "Зелёный", "Голубой",
                "Розовый", "Оранжевый", "Светло-серый", "Почти белый"
            )
            return colors to names
        }
    }
}
