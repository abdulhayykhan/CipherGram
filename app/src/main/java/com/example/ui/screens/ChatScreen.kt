package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.security.UiChatMessage
import com.example.ui.theme.GlassmorphicContainer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.Brush
import com.example.security.AttachmentEngine
import com.example.ui.audio.AudioRecorder
import com.example.ui.components.VoicePlayer
import com.example.ui.components.ReelPlayer
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeThread by viewModel.activeThread.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var showKeyEditor by remember { mutableStateOf(false) }

    // Voice recording infrastructure
    val audioRecorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var recordingFileId by remember { mutableStateOf<String?>(null) }

    // Recording ticking timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.cancelRecording()
        }
    }

    // Permission launcher for Recording Audio
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val fileId = "voice_${System.currentTimeMillis()}_m4a"
            recordingFileId = fileId
            val file = audioRecorder.startRecording(fileId)
            if (file != null) {
                isRecording = true
            }
        }
    }

    // Picker launcher for Single Media
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val type = if (context.contentResolver.getType(uri)?.startsWith("video") == true) "vid" else "img"
            val ext = if (type == "vid") "mp4" else "jpg"
            val fileId = "${type}_${System.currentTimeMillis()}_$ext"

            coroutineScope.launch(Dispatchers.IO) {
                val bytes = AttachmentEngine.getOptimizedMediaBytes(context, uri, type)
                if (bytes.isNotEmpty()) {
                    viewModel.sendSecureAttachment(type, fileId, bytes)
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return")
                    }
                },
                title = {
                    activeThread?.let { thread ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showKeyEditor = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                AsyncImage(
                                    model = thread.profilePicUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = thread.contactName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "key: ${thread.sharedSecret}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showKeyEditor = true },
                        modifier = Modifier.testTag("chat_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Shared Key", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteCurrentThread()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("delete_chat_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Discard Chat Thread")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )
        },
        bottomBar = {
            ChatBottomInputBar(
                text = viewModel.stagedMessageText,
                onValueChange = { viewModel.stagedMessageText = it },
                encryptEnabled = viewModel.encryptEnabled,
                onEncryptToggle = { viewModel.encryptEnabled = it },
                onSend = {
                    viewModel.sendMessage()
                    focusManager.clearFocus()
                },
                isRecording = isRecording,
                recordingDurationSec = recordingDurationSec,
                onStartRecord = {
                    recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                onStopRecord = {
                    val file = audioRecorder.stopRecording()
                    isRecording = false
                    val fileId = recordingFileId
                    if (file != null && fileId != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            val bytes = file.readBytes()
                            if (bytes.isNotEmpty()) {
                                viewModel.sendSecureAttachment("voice", fileId, bytes)
                            }
                        }
                    }
                },
                onCancelRecord = {
                    audioRecorder.cancelRecording()
                    isRecording = false
                },
                onPickMedia = {
                    pickVisualMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            )
        },
        modifier = modifier.imePadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("messages_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message
                    )
                }
            }
        }

        if (showKeyEditor) {
            KeyEditorDialog(
                currentSecret = viewModel.sharedSecretInput,
                onDismiss = { 
                    showKeyEditor = false 
                    focusManager.clearFocus()
                },
                onSave = {
                    viewModel.updateSharedSecret(it)
                    showKeyEditor = false
                    focusManager.clearFocus()
                    coroutineScope.launch {
                        delay(200)
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: UiChatMessage
) {
    var revealCiphertext by remember { mutableStateOf(false) }

    val alignment = if (message.isSender) Alignment.End else Alignment.Start
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDecryptedAndSecure = message.isEncrypted && !message.isDecryptionError

    // Create infinite pulsating transition for E2EE decrypted message glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Bubble custom background translucency
    val bubbleColor = if (isDecryptedAndSecure) {
        if (isDark) Color(0x2210B981) else Color(0x3310B981) // light translucent premium green backing
    } else if (message.isDecryptionError) {
        if (isDark) Color(0x38EF5350) else Color(0x2EF55350) // warning tint backing
    } else if (message.isSender) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
    }

    val textColor = if (message.isSender) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Border glowing setup (C. client-side decrypted messages should feature subtle pulsating edge glow, unencrypted utilize standard clean)
    val borderBrush = if (isDecryptedAndSecure) {
        Brush.linearGradient(
            colors = listOf(
                com.example.ui.theme.PulsatingGreen.copy(alpha = glowAlpha),
                com.example.ui.theme.ElectricCyan.copy(alpha = 0.4f),
                com.example.ui.theme.PulsatingGreen.copy(alpha = glowAlpha * 0.4f)
            )
        )
    } else if (message.isDecryptionError) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFEF5350).copy(alpha = 0.9f),
                Color(0xFFEF5350).copy(alpha = 0.2f)
            )
        )
    } else {
        // Standard unencrypted elements utilize standard clean boundaries (subtle border)
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.12f),
                    Color.Black.copy(alpha = 0.05f)
                )
            )
        }
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (message.isSender) 16.dp else 4.dp,
        bottomEnd = if (message.isSender) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_bubble_row_${message.id}"),
        horizontalAlignment = alignment
    ) {
        BoxWithConstraints(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            GlassmorphicContainer(
                shape = bubbleShape,
                backgroundColor = bubbleColor,
                borderBrush = borderBrush,
                modifier = Modifier.clickable {
                    if (message.isEncrypted) {
                        revealCiphertext = !revealCiphertext
                    }
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Security tag styling (pulsating green shield badge)
                    if (isDecryptedAndSecure) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "🛡",
                                fontSize = 11.sp,
                                modifier = Modifier.graphicsLayer { alpha = glowAlpha } // subtle pulsating representation
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SECURE / DECRYPTED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.PulsatingGreen,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (message.isDecryptionError) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Mismatched Keys",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CIPHER BLOCKED (KEY MISMATCH)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF8A80),
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Scraped native media preview block
                    if (message.isScrapedMedia) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            if (message.mediaVideoUrl != null && message.mediaVideoUrl.isNotEmpty()) {
                                ReelPlayer(
                                    videoUrl = message.mediaVideoUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                )
                            } else if (message.mediaImageUrl != null && message.mediaImageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = message.mediaImageUrl,
                                    contentDescription = "Instagram Preview Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            message.mediaCaption?.let { caption ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = caption,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    fontFamily = FontFamily.Monospace,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Handles advanced file attachments of Client-Side E2EE Attachment Engine
                    if (message.isAttachment) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            if (message.attachmentType == "img" && message.attachmentPath != null) {
                                AsyncImage(
                                    model = message.attachmentPath,
                                    contentDescription = "Decrypted secure photograph",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (message.attachmentType == "vid" && message.attachmentPath != null) {
                                ReelPlayer(
                                    videoUrl = message.attachmentPath,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else if (message.attachmentType == "voice" && message.attachmentPath != null) {
                                VoicePlayer(
                                    audioUrl = message.attachmentPath,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Main plaintext message text content (unless it's a voice note, where player is enough)
                    if (message.attachmentType != "voice" || message.isDecryptionError) {
                        Text(
                            text = if (message.isDecryptionError) "[Encrypted Ciphertext]" else message.text,
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Monospace // MONO-SPACED TYPOGRAPHY LIKE MATRIX!
                        )
                    }

                    // Expandable Ciphertext visualizer
                    AnimatedVisibility(visible = revealCiphertext) {
                        Column(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "RAW CIPHER PAYLOAD:",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = message.rawCipherPayload.ifEmpty { "[No Cipher Payload / Local Attachment]" },
                                fontSize = 10.sp,
                                color = Color(0xFFA5D6A7),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
        
        val timeStr = remember(message.timestamp) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
        }
        Text(
            text = timeStr,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ChatBottomInputBar(
    text: String,
    onValueChange: (String) -> Unit,
    encryptEnabled: Boolean,
    onEncryptToggle: (Boolean) -> Unit,
    onSend: () -> Unit,
    isRecording: Boolean,
    recordingDurationSec: Int,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onPickMedia: () -> Unit
) {
    GlassmorphicContainer(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        cornerRadius = 24.dp,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (encryptEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (encryptEnabled) "E2EE ACTIVE (AES-GCM)" else "E2EE INACTIVE (PLAIN)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (encryptEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                    )
                }

                Switch(
                    checked = encryptEnabled,
                    onCheckedChange = onEncryptToggle,
                    thumbContent = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    modifier = Modifier
                        .graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                        .testTag("e2ee_switch")
                )
            }

            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Recording voice... ${String.format("%02d:%02d", recordingDurationSec / 60, recordingDurationSec % 60)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onCancelRecord) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Recording", tint = Color.Red)
                        }
                        IconButton(onClick = onStopRecord) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(0xFF4CAF50), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onPickMedia,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Pick Media Visuals",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = onValueChange,
                        placeholder = { Text("E2EE input or paste IG Reel link...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_text_field"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        maxLines = 4
                    )

                    if (text.trim().isEmpty()) {
                        IconButton(
                            onClick = onStartRecord,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("🎙", fontSize = 18.sp)
                        }
                    } else {
                        IconButton(
                            onClick = onSend,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp)
                             )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyEditorDialog(
    currentSecret: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var rawSecret by remember { mutableStateOf(currentSecret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Session Shared Key")
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This shared secret drives the 256-bit AES key derivation function. Changing this key will instantly lock or unlock decryptable message histories in this chat stream.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rawSecret,
                    onValueChange = { rawSecret = it },
                    label = { Text("Cryptographic Shared Key") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth().testTag("key_editor_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(rawSecret.trim()) },
                modifier = Modifier.testTag("key_editor_save")
            ) {
                Text("Lock in Key")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("key_editor_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}
