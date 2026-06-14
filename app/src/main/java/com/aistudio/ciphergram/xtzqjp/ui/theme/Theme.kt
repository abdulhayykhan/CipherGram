package com.aistudio.ciphergram.xtzqjp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00373F),
    secondary = NeonPurple,
    onSecondary = Color.White,
    background = CyberDarkBackground,
    onBackground = CyberDarkTextPrimary,
    surface = CyberDarkSurface,
    onSurface = CyberDarkTextPrimary,
    surfaceVariant = CyberDarkSurfaceVariant,
    onSurfaceVariant = CyberDarkTextSecondary,
    error = WarningAmber,
    onError = Color.Black
)

private val CyberLightColorScheme = lightColorScheme(
    primary = ElectricCyanDarker, // Deeper cyber blue for contrast in light mode
    onPrimary = Color.White,
    secondary = NeonPurple,
    onSecondary = Color.White,
    background = CyberLightBackground,
    onBackground = CyberLightTextPrimary,
    surface = CyberLightSurface,
    onSurface = CyberLightTextPrimary,
    surfaceVariant = CyberLightSurfaceVariant,
    onSurfaceVariant = CyberLightTextSecondary,
    error = WarningAmber,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable device dynamic to enforce CipherGram cyber identity
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CyberDarkColorScheme else CyberLightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
