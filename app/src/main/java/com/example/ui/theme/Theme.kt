package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val CyberDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00373F),
    secondary = NeonPurple,
    onSecondary = Color.White,
    background = CyberDarkBackground,
    onBackground = Color.White,
    surface = CyberDarkSurfaceOpaque,
    onSurface = Color.White,
    surfaceVariant = CyberDarkSurfaceOpaque,
    onSurfaceVariant = ElectricCyan,
    error = WarningAmber,
    onError = Color.Black
  )

private val CyberLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF006D77), // Slightly deeper cyber blue for rich accessibility contrast
    onPrimary = Color.White,
    secondary = NeonPurple,
    onSecondary = Color.White,
    background = CyberLightBackground,
    onBackground = CyberLightText,
    surface = Color.White,
    onSurface = CyberLightText,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = CyberLightTextSecondary,
    error = WarningAmber,
    onError = Color.Black
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable device dynamic to enforce CipherGram identity
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) CyberDarkColorScheme else CyberLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

