package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.theme.GlassmorphicContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    viewModel: ChatViewModel,
    onNavigateToChats: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawCookie by remember { mutableStateOf("") }
    var igHandle by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    var activeTab by remember { mutableStateOf(0) } // 0 = standard cookie, 1 = advanced manual protocol
    var developerUsername by remember { mutableStateOf("") }
    var targetUserId by remember { mutableStateOf("") }
    var sessionToken by remember { mutableStateOf("") }
    var csrfToken by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36") }

    var isSessionTokenVisible by remember { mutableStateOf(false) }
    var isCsrfTokenVisible by remember { mutableStateOf(false) }

    // Alphanumeric/hex regex validation
    val tokenRegex = remember { Regex("^[a-zA-Z0-9\\-:_]+$") }
    val isSessionTokenValid = remember(sessionToken) {
        sessionToken.isEmpty() || tokenRegex.matches(sessionToken)
    }
    val isCsrfTokenValid = remember(csrfToken) {
        csrfToken.isEmpty() || tokenRegex.matches(csrfToken)
    }
    val isTargetUserIdValid = remember(targetUserId) {
        targetUserId.isEmpty() || tokenRegex.matches(targetUserId)
    }

    var showWebAuthSheet by remember { mutableStateOf(false) }

    fun parseCookieValue(cookieString: String, key: String): String {
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.startsWith("$key=")) {
                return trimmed.substring(key.length + 1)
            }
        }
        return ""
    }

    fun extractCookieValue(cookieString: String, key: String): String {
        return parseCookieValue(cookieString, key)
    }

    val onSessionExtracted: (String, String, String) -> Unit = { sessionToken, dsUserId, csrftoken ->
        viewModel.saveManualProtocolConfig(
            username = "ig_user_$dsUserId",
            targetUserId = dsUserId,
            sessionToken = sessionToken,
            csrfToken = csrftoken,
            userAgent = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        )
        WebStorage.getInstance().deleteAllData()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()

        showWebAuthSheet = false
        onNavigateToChats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CIPHERGRAM SESSION",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Secure Lock Header
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(32.dp)
                    )
                    .padding(16.dp)
            )

            Text(
                text = "Client-Side DM Pipeline",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Store Session Cookie elements locally inside secure Keystore container, enabling low-level direct client requests to private Instagram DM feeds & CDN metadata scrapers.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            // Dynamic credential info cards
            if (viewModel.isAuthenticated) {
                GlassmorphicContainer(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Logged in as @${viewModel.currentUsername}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("logout_button")
                        ) {
                            Text("Clear Credentials Session")
                        }
                    }
                }
            } else {
                // Tabrow to choose between standard cookie parser and manual protocol session configuration
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Standard Cookies", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Protocol Config", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeTab == 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Automated Browser Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Logs into the official platform endpoint securely using our on-device sandboxed Web Automation wrapper. No passwords are ever read or transmitted by CipherGram; we solely sync the authorized cookie tokens locally.",
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            Button(
                                onClick = { showWebAuthSheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("launch_web_auth_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                                    Text("Open Web Login Sheet", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Text(
                            text = " OR MANUAL VALUE INPUT ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }

                    OutlinedTextField(
                        value = igHandle,
                        onValueChange = { igHandle = it },
                        label = { Text("Instagram Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("ig_username_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = rawCookie,
                        onValueChange = { rawCookie = it },
                        label = { Text("Web Session Cookies (sessionid=...)") },
                        placeholder = { Text("sessionid=12345; ds_user_id=6789; ...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("ig_cookies_input")
                    )

                    Button(
                        onClick = {
                            if (igHandle.isNotEmpty() && rawCookie.isNotEmpty()) {
                                viewModel.saveSession(rawCookie, igHandle)
                            } else {
                                viewModel.saveSession("sessionid=simulated_cookie", igHandle.ifEmpty { "demo_crypt_user" })
                            }
                            onNavigateToChats()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_session_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Initialize Crypt-Session", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Manual Protocol Configuration Interface
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = developerUsername,
                            onValueChange = { developerUsername = it },
                            label = { Text("Developer Username") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("manual_username_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = targetUserId,
                            onValueChange = { targetUserId = it },
                            label = { Text("Target User ID (Numeric/Alphanumeric)") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            isError = !isTargetUserIdValid,
                            supportingText = {
                                if (!isTargetUserIdValid) {
                                    Text("Must be alphanumeric or numeric", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("manual_target_id_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = sessionToken,
                            onValueChange = { sessionToken = it },
                            label = { Text("Custom Session Token") },
                            visualTransformation = if (isSessionTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { isSessionTokenVisible = !isSessionTokenVisible }) {
                                    Text(if (isSessionTokenVisible) "HIDE" else "SHOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            isError = !isSessionTokenValid,
                            supportingText = {
                                if (!isSessionTokenValid) {
                                    Text("Must follow alphanumeric/hex conventions", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("manual_session_token_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = csrfToken,
                            onValueChange = { csrfToken = it },
                            label = { Text("Custom CSRF Token") },
                            visualTransformation = if (isCsrfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { isCsrfTokenVisible = !isCsrfTokenVisible }) {
                                    Text(if (isCsrfTokenVisible) "HIDE" else "SHOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            isError = !isCsrfTokenValid,
                            supportingText = {
                                if (!isCsrfTokenValid) {
                                    Text("Must follow alphanumeric/hex conventions", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("manual_csrf_token_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = { Text("Custom User-Agent String") },
                            modifier = Modifier.fillMaxWidth().testTag("manual_ua_input"),
                            singleLine = false,
                            maxLines = 2
                        )

                        val isManualFormValid = developerUsername.isNotEmpty() &&
                                targetUserId.isNotEmpty() &&
                                sessionToken.isNotEmpty() &&
                                isTargetUserIdValid &&
                                isSessionTokenValid &&
                                isCsrfTokenValid

                        Button(
                            onClick = {
                                if (isManualFormValid) {
                                    viewModel.saveManualProtocolConfig(
                                        username = developerUsername,
                                        targetUserId = targetUserId,
                                        sessionToken = sessionToken,
                                        csrfToken = csrfToken,
                                        userAgent = userAgent
                                    )
                                    onNavigateToChats()
                                }
                            },
                            enabled = isManualFormValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_manual_session_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Initialize Protocol Session", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Step instructions
            GlassmorphicContainer(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How to gather session cookies:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = "1. Sign in to instagram.com in browser.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2. Open Inspector tools (F12) -> Network Tab -> refresh.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. Copy matching request headers 'Cookie' section value, and input above.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Fallback & Launch Sandbox Mode components
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Offline Core Development Sandbox",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "Quickly test secure chat interfaces, local cryptographic modules, Media viewer rendering, and Share Sheet pipelines completely offline with simulated credentials.",
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = {
                            viewModel.enableSandboxMode()
                            onNavigateToChats()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("launch_sandbox_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Launch Sandbox Mode", fontWeight = FontWeight.Black)
                    }
                }
            }

            TextButton(
                onClick = onNavigateToChats,
                modifier = Modifier.testTag("bypass_cookies_button")
            ) {
                Text(
                    text = "Bypass, access direct secure chat simulation →",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showWebAuthSheet) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showWebAuthSheet = false }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Secure Cloud Sync",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Sign in to authenticate on the official platform",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { showWebAuthSheet = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Close Sync",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            var isWebViewLoading by remember { mutableStateOf(true) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color.White)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            // Override raw default package tokens with an authentic Android mobile Chrome footprint
                                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                                            
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.useWideViewPort = true
                                            settings.loadWithOverviewMode = true
                                            
                                            // Allow third-party cookie sync to guarantee seamless Multi-Factor Authentication (MFA) or SSO redirections
                                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                            
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                    super.onPageStarted(view, url, favicon)
                                                    isWebViewLoading = true
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    isWebViewLoading = false
                                                    url?.let { currentUrl ->
                                                        if (currentUrl.contains("instagram.com") && !currentUrl.contains("login")) {
                                                            val cookieManager = android.webkit.CookieManager.getInstance()
                                                            val cookies = cookieManager.getCookie(currentUrl)
                                                            if (!cookies.isNullOrEmpty()) {
                                                                val sessionid = extractCookieValue(cookies, "sessionid")
                                                                val dsUserId = extractCookieValue(cookies, "ds_user_id")
                                                                val csrftoken = extractCookieValue(cookies, "csrftoken")
                                                                
                                                                if (!sessionid.isNullOrEmpty()) {
                                                                    onSessionExtracted(sessionid, dsUserId ?: "", csrftoken ?: "")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                override fun onReceivedError(
                                                    view: WebView?,
                                                    request: android.webkit.WebResourceRequest?,
                                                    error: android.webkit.WebResourceError?
                                                ) {
                                                    super.onReceivedError(view, request, error)
                                                    // Graceful fallback to avoid unhandled blank rendering loops
                                                }
                                            }
                                            loadUrl("https://www.instagram.com/accounts/login/")
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isWebViewLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
         columnPaddingValues()
        }
    }
}

@Composable
fun ColumnScope.columnPaddingValues() {
    Spacer(modifier = Modifier.navigationBarsPadding())
}
