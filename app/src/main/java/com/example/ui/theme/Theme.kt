package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ZeroMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    selectedTheme: ZeroTheme = ZeroTheme.ZENITH_DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> getDarkColorScheme(selectedTheme)
        else -> getLightColorScheme(selectedTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun getDarkColorScheme(theme: ZeroTheme): ColorScheme = when (theme) {
    ZeroTheme.ZENITH_DARK -> darkColorScheme(
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFF80F2FF),
        secondary = Color(0xFFC77DFF),
        onSecondary = Color(0xFF420072),
        secondaryContainer = Color(0xFF5B118C),
        onSecondaryContainer = Color(0xFFF3DAFF),
        background = Color(0xFF080A0F),
        onBackground = Color(0xFFF1F3F9),
        surface = Color(0xFF10131B),
        onSurface = Color(0xFFF1F3F9),
        surfaceVariant = Color(0xFF181D29),
        onSurfaceVariant = Color(0xFFC4CBD8),
        outline = Color(0xFF2C3446)
    )
    ZeroTheme.CYBER_NEON -> darkColorScheme(
        primary = Color(0xFFFF2A85),
        onPrimary = Color(0xFF4A0021),
        primaryContainer = Color(0xFF6B0031),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFF00F5A0),
        onSecondary = Color(0xFF003822),
        secondaryContainer = Color(0xFF005334),
        onSecondaryContainer = Color(0xFF74FFC5),
        background = Color(0xFF0C0A14),
        onBackground = Color(0xFFF6F2FC),
        surface = Color(0xFF161224),
        onSurface = Color(0xFFF6F2FC),
        surfaceVariant = Color(0xFF231D38),
        onSurfaceVariant = Color(0xFFD6CEE5),
        outline = Color(0xFF3B325A)
    )
    ZeroTheme.MIDNIGHT_SAPPHIRE -> darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF003548),
        primaryContainer = Color(0xFF004D68),
        onPrimaryContainer = Color(0xFFBAE6FD),
        secondary = Color(0xFF818CF8),
        onSecondary = Color(0xFF1E1B4B),
        secondaryContainer = Color(0xFF312E81),
        onSecondaryContainer = Color(0xFFE0E7FF),
        background = Color(0xFF060E1A),
        onBackground = Color(0xFFF0F5FA),
        surface = Color(0xFF0D1B30),
        onSurface = Color(0xFFF0F5FA),
        surfaceVariant = Color(0xFF142948),
        onSurfaceVariant = Color(0xFFC7D3E3),
        outline = Color(0xFF244372)
    )
    ZeroTheme.SUNSET_AMBER -> darkColorScheme(
        primary = Color(0xFFF59E0B),
        onPrimary = Color(0xFF452B00),
        primaryContainer = Color(0xFF633F00),
        onPrimaryContainer = Color(0xFFFFDDAE),
        secondary = Color(0xFFF43F5E),
        onSecondary = Color(0xFF4C0519),
        secondaryContainer = Color(0xFF700727),
        onSecondaryContainer = Color(0xFFFFD9DF),
        background = Color(0xFF120D0A),
        onBackground = Color(0xFFFAF3F0),
        surface = Color(0xFF1D1612),
        onSurface = Color(0xFFFAF3F0),
        surfaceVariant = Color(0xFF2B201B),
        onSurfaceVariant = Color(0xFFE2D4CD),
        outline = Color(0xFF4B382F)
    )
    ZeroTheme.MATRIX_EMERALD -> darkColorScheme(
        primary = Color(0xFF10B981),
        onPrimary = Color(0xFF003822),
        primaryContainer = Color(0xFF005234),
        onPrimaryContainer = Color(0xFFA7F3D0),
        secondary = Color(0xFF06B6D4),
        onSecondary = Color(0xFF003640),
        secondaryContainer = Color(0xFF004F5E),
        onSecondaryContainer = Color(0xFFB5F2FF),
        background = Color(0xFF06120C),
        onBackground = Color(0xFFEFFBF4),
        surface = Color(0xFF0D2117),
        onSurface = Color(0xFFEFFBF4),
        surfaceVariant = Color(0xFF143122),
        onSurfaceVariant = Color(0xFFC2DDD0),
        outline = Color(0xFF25523A)
    )
}

private fun getLightColorScheme(theme: ZeroTheme): ColorScheme = when (theme) {
    ZeroTheme.ZENITH_DARK -> lightColorScheme(
        primary = Color(0xFF006874),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF97F0FF),
        onPrimaryContainer = Color(0xFF001F24),
        secondary = Color(0xFF7E2A9F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF3DAFF),
        onSecondaryContainer = Color(0xFF330045),
        background = Color(0xFFF6FAFB),
        onBackground = Color(0xFF191C1D),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF191C1D),
        surfaceVariant = Color(0xFFDBE4E6),
        onSurfaceVariant = Color(0xFF3F484A),
        outline = Color(0xFF6F797B)
    )
    ZeroTheme.CYBER_NEON -> lightColorScheme(
        primary = Color(0xFFB90059),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E001B),
        secondary = Color(0xFF006C47),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF78F9BC),
        onSecondaryContainer = Color(0xFF002113),
        background = Color(0xFFFDF7F9),
        onBackground = Color(0xFF201A1C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF201A1C),
        surfaceVariant = Color(0xFFF2DDE2),
        onSurfaceVariant = Color(0xFF514347),
        outline = Color(0xFF837377)
    )
    ZeroTheme.MIDNIGHT_SAPPHIRE -> lightColorScheme(
        primary = Color(0xFF00658E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC7E7FF),
        onPrimaryContainer = Color(0xFF001E2E),
        secondary = Color(0xFF4355B9),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDEE0FF),
        onSecondaryContainer = Color(0xFF00105C),
        background = Color(0xFFF7F9FF),
        onBackground = Color(0xFF181C20),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF181C20),
        surfaceVariant = Color(0xFFDCE2E9),
        onSurfaceVariant = Color(0xFF40484F),
        outline = Color(0xFF71787F)
    )
    ZeroTheme.SUNSET_AMBER -> lightColorScheme(
        primary = Color(0xFF8C5300),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDDB8),
        onPrimaryContainer = Color(0xFF2C1600),
        secondary = Color(0xFFB3183F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9DE),
        onSecondaryContainer = Color(0xFF400010),
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF221A14),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF221A14),
        surfaceVariant = Color(0xFFF3DFD1),
        onSurfaceVariant = Color(0xFF51443B),
        outline = Color(0xFF84746A)
    )
    ZeroTheme.MATRIX_EMERALD -> lightColorScheme(
        primary = Color(0xFF006C4C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF84F8C7),
        onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF006878),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFA6EEFF),
        onSecondaryContainer = Color(0xFF001F25),
        background = Color(0xFFF5FAF6),
        onBackground = Color(0xFF171D1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF171D1A),
        surfaceVariant = Color(0xFFDBE5DE),
        onSurfaceVariant = Color(0xFF3F4944),
        outline = Color(0xFF6F7974)
    )
}
