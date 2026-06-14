package com.example.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import kotlinx.coroutines.delay

// Facebook brand colours
private val FBBlue = Color(0xFF1877F2)

@Composable
fun SessionScreen(
    viewModel: ChatViewModel,
    onNavigateToChats: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val isDark = isSystemInDarkTheme()

    // Pulse animation for the lock icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Navigate when authenticated
    LaunchedEffect(viewModel.isAuthenticated) {
        if (viewModel.isAuthenticated) {
            delay(300)
            onNavigateToChats()
        }
    }

    // Register Facebook login callback
    val callbackManager = viewModel.callbackManager
    DisposableEffect(callbackManager) {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    Log.d("SessionScreen", "Facebook login success")
                    viewModel.handleFacebookLogin(result.accessToken)
                }
                override fun onCancel() {
                    viewModel.setLoginError("Login cancelled.")
                }
                override fun onError(error: FacebookException) {
                    viewModel.setLoginError(error.message ?: "Facebook login error.")
                    Log.e("SessionScreen", "Facebook error: ${error.message}")
                }
            }
        )
        onDispose { LoginManager.getInstance().unregisterAllCallbacks() }
    }

    // Theme-adaptive blob colors
    val blob1Color = if (isDark) Color(0xFF6C3BFF).copy(alpha = 0.35f) else Color(0xFF6C3BFF).copy(alpha = 0.15f)
    val blob2Color = if (isDark) Color(0xFF1877F2).copy(alpha = 0.25f) else Color(0xFF1877F2).copy(alpha = 0.12f)
    val blob3Color = if (isDark) ElectricCyan.copy(alpha = 0.15f) else ElectricCyanDarker.copy(alpha = 0.10f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Animated background gradient blobs
        Box(
            modifier = Modifier
                .size(380.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .blur(90.dp)
                .background(
                    brush = Brush.radialGradient(listOf(blob1Color, Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(listOf(blob2Color, Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
                .offset(y = 120.dp)
                .blur(70.dp)
                .background(
                    brush = Brush.radialGradient(listOf(blob3Color, Color.Transparent)),
                    shape = CircleShape
                )
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock icon with pulsing glow
            Box(contentAlignment = Alignment.Center) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF6C3BFF).copy(alpha = if (isDark) 0.4f else 0.2f),
                                    Color(0xFF1877F2).copy(alpha = if (isDark) 0.15f else 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Icon background
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF6C3BFF), Color(0xFF1877F2)),
                                start = Offset.Zero,
                                end = Offset(80f, 80f)
                            )
                        )
                        .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // App title
            Text(
                text = "CipherGram",
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF8B5CF6), Color(0xFF60A5FA))
                    ),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    shadow = Shadow(
                        color = Color(0xFF6C3BFF).copy(alpha = if (isDark) 0.6f else 0.2f),
                        offset = Offset(0f, 4f),
                        blurRadius = 16f
                    )
                )
            )

            Spacer(Modifier.height(8.dp))

            // Tagline
            Text(
                text = "End-to-End Encrypted Chat",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.height(12.dp))

            // E2EE badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF1E1B4B).copy(alpha = 0.6f) else Color(0xFFE0E7FF).copy(alpha = 0.6f))
                    .border(1.dp, Color(0xFF6C3BFF).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isDark) PulsatingGreen else PulsatingGreenDarker,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "ECDH P-256 · AES-256-GCM",
                    fontSize = 11.sp,
                    color = if (isDark) PulsatingGreen else PulsatingGreenDarker,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(52.dp))

            // ── Facebook Login Button ───────────────────────────────────────
            AnimatedVisibility(
                visible = !viewModel.isLoading,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            LoginManager.getInstance().logInWithReadPermissions(
                                activity,
                                callbackManager,
                                listOf("email", "public_profile")
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FBBlue,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "f",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Continue with Facebook",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Divider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        Text(
                            "  or  ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }

                    // Sandbox mode button
                    OutlinedButton(
                        onClick = { viewModel.enableSandboxMode() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            "Launch Sandbox Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Loading spinner ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = viewModel.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6C3BFF),
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        "Signing in securely…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            // ── Error message ───────────────────────────────────────────────
            viewModel.loginError?.let { error ->
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF7F1D1D).copy(alpha = if (isDark) 0.4f else 0.1f))
                        .border(
                            1.dp,
                            Color(0xFFF87171).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .clickable { viewModel.setLoginError(null) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = error,
                        color = Color(0xFFDC2626),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // Bottom disclaimer
            Text(
                text = "Your messages are encrypted locally using ECDH key exchange.\nCipherGram cannot read your messages.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
