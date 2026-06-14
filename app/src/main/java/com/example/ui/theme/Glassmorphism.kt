package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassmorphicContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    blurRadius: Dp = 16.dp,
    backgroundColor: Color? = null,
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    // Transparent fills matching exact spec
    val resolvedBgColor = backgroundColor ?: if (isDark) {
        CyberDarkGlassBackground 
    } else {
        CyberLightGlassBackground 
    }

    // Precise refract highlight border gradient (1.dp)
    val resolvedBorderBrush = borderBrush ?: if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.18f),
                ElectricCyan.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.05f),
                ElectricCyan.copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                CyberLightGlassBorder.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.25f),
                CyberLightGlassBorder.copy(alpha = 0.15f)
            )
        )
    }

    Box(modifier = modifier) {
        // Hardware-accelerated background blur backing layer (RenderEffect under Android 12+)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurRadius.toPx(),
                                blurRadius.toPx(),
                                android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } catch (e: Exception) {
                            // Graceful fallback on failure
                        }
                    }
                }
                .background(resolvedBgColor)
                .border(borderWidth, resolvedBorderBrush, shape)
        )
        // Fully unblurred foreground content to maintain maximum textual crispness & legibility
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
        ) {
            content()
        }
    }
}
