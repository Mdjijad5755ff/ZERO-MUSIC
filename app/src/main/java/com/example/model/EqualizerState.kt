package com.example.model

data class EqualizerState(
    val isEnabled: Boolean = true,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0), // 5 bands in millibels (-1500 to 1500)
    val bassBoost: Int = 200, // 0 to 1000
    val virtualizer: Int = 150, // 0 to 1000
    val currentPreset: String = "Normal"
) {
    companion object {
        val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

        val PRESETS = mapOf(
            "Normal" to listOf(0, 0, 0, 0, 0),
            "Bass Boost" to listOf(600, 400, 100, 0, -100),
            "Rock" to listOf(500, 300, -100, 300, 500),
            "Pop" to listOf(-100, 200, 500, 200, -100),
            "Electronic" to listOf(500, 300, 0, 200, 400),
            "Jazz" to listOf(400, 200, -100, 200, 400),
            "Classical" to listOf(400, 300, -200, 300, 400),
            "Vocal" to listOf(-200, 100, 500, 300, 100),
            "Heavy Metal" to listOf(400, 100, -200, 300, 600)
        )
    }
}
