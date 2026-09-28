package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class ZeroTheme(
    val displayName: String,
    val primaryColor: Color,
    val accentColor: Color
) {
    ZENITH_DARK(
        displayName = "Zenith Cyan",
        primaryColor = Color(0xFF00E5FF),
        accentColor = Color(0xFF9D4EDD)
    ),
    CYBER_NEON(
        displayName = "Cyber Neon",
        primaryColor = Color(0xFFFF2A85),
        accentColor = Color(0xFF00F5A0)
    ),
    MIDNIGHT_SAPPHIRE(
        displayName = "Midnight Sapphire",
        primaryColor = Color(0xFF38BDF8),
        accentColor = Color(0xFF818CF8)
    ),
    SUNSET_AMBER(
        displayName = "Sunset Amber",
        primaryColor = Color(0xFFF59E0B),
        accentColor = Color(0xFFF43F5E)
    ),
    MATRIX_EMERALD(
        displayName = "Matrix Emerald",
        primaryColor = Color(0xFF10B981),
        accentColor = Color(0xFF06B6D4)
    )
}
