package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.network.InstagramScraper
import com.example.data.network.SessionManager
import com.example.data.repository.ChatRepository
import com.example.security.CryptoEngine
import com.example.security.UiChatMessage
import com.example.security.AttachmentEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ChatDatabase.getDatabase(application)
    private val sessionManager = SessionManager(application)
    private val repository = ChatRepository(database.messageDao(), sessionManager)

    // User session state
    var isAuthenticated by mutableStateOf(sessionManager.isAuthenticated())
        private set
    var currentUsername by mutableStateOf(sessionManager.getUsername())
        private set
    var isSandboxMode by mutableStateOf(sessionManager.isSandboxMode())
        private set

    // Selected thread state
    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    private val _activeThread = MutableStateFlow<ChatThread?>(null)
    val activeThread: StateFlow<ChatThread?> = _activeThread.asStateFlow()

    // Threads list
    val threads: StateFlow<List<ChatThread>> = repository.threadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Messages list for selected thread, merged on-the-fly and processed through AttachmentEngine E2EE pipeline
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<UiChatMessage>> = combine(
        _activeThreadId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getMessagesForThread(id)
        },
        _activeThread
    ) { rawList, thread ->
        val secret = thread?.sharedSecret ?: ""
        AttachmentEngine.processRawMessages(getApplication(), rawList, secret)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // State bindings for Compose text boxes & toggles
    var stagedMessageText by mutableStateOf("")
    var encryptEnabled by mutableStateOf(true)
    var sharedSecretInput by mutableStateOf("")

    init {
        // Pre-populate beautiful default threads to enable E2EE exploration out-of-the-box
        viewModelScope.launch {
            if (repository.threadsFlow.stateIn(viewModelScope).value.isEmpty()) {
                // Thread 1: Secure Design Partner
                repository.createThread(
                    id = "alex_design",
                    contactName = "Alex Rivera",
                    contactUsername = "alex_design",
                    profilePicUrl = "https://picsum.photos/seed/alex/150/150",
                    sharedSecret = "crypt_secure_alex"
                )
                // Seeding initial conversation
                repository.createMessage(
                    threadId = "alex_design",
                    senderId = "alex_design",
                    rawText = "Please make sure E2EE is toggled. Here is my secret key: crypt_secure_alex",
                    isSender = false,
                    encryptMode = false
                )
                repository.createMessage(
                    threadId = "alex_design",
                    senderId = "alex_design",
                    rawText = "Let's share secure design updates and reels privately!",
                    isSender = false,
                    encryptMode = false
                )

                // Thread 2: Travel Influencer
                repository.createThread(
                    id = "sarah_adventures",
                    contactName = "Sarah Chen",
                    contactUsername = "sarah_adventures",
                    profilePicUrl = "https://picsum.photos/seed/sarah/150/150",
                    sharedSecret = "sarah_travels_2026"
                )
                repository.createMessage(
                    threadId = "sarah_adventures",
                    senderId = "sarah_adventures",
                    rawText = "Hey! Check out this awesome travel reel I found!",
                    isSender = false,
                    encryptMode = false
                )
                repository.createMessage(
                    threadId = "sarah_adventures",
                    senderId = "sarah_adventures",
                    rawText = "https://www.instagram.com/reel/C7-M7_nI9Z_/",
                    isSender = false,
                    encryptMode = false
                )
            }
        }
    }

    fun selectThread(threadId: String?) {
        _activeThreadId.value = threadId
        viewModelScope.launch {
            if (threadId != null) {
                val thread = repository.getThreadById(threadId)
                _activeThread.value = thread
                sharedSecretInput = thread?.sharedSecret ?: ""
            } else {
                _activeThread.value = null
                sharedSecretInput = ""
            }
        }
    }

    fun updateSharedSecret(newSecret: String) {
        val threadId = _activeThreadId.value ?: return
        sharedSecretInput = newSecret
        viewModelScope.launch {
            val thread = repository.getThreadById(threadId)
            if (thread != null) {
                val updatedThread = thread.copy(sharedSecret = newSecret)
                // Use insert to replace existing config
                database.messageDao().insertThread(updatedThread)
                _activeThread.value = updatedThread
            }
        }
    }

    fun handleIncomingShareUrl(url: String) {
        stagedMessageText = url
        encryptEnabled = false // Keep media URLs plain to allow immediate scraping
    }

    fun sendMessage() {
        val threadId = _activeThreadId.value ?: return
        val text = stagedMessageText.trim()
        if (text.isEmpty()) return

        val isUrl = InstagramScraper.isInstagramUrl(text)
        val encryptState = encryptEnabled && !isUrl // Don't encrypt links, only raw private texts

        viewModelScope.launch {
            val sentMsgId = repository.createMessage(
                threadId = threadId,
                senderId = "me",
                rawText = text,
                isSender = true,
                encryptMode = encryptState
            )
            stagedMessageText = ""

            // Trigger contact reply simulator to test end-to-end flow!
            simulateContactReply(threadId, text, encryptState)
        }
    }

    private fun simulateContactReply(threadId: String, userText: String, wasUserEncrypted: Boolean) {
        viewModelScope.launch {
            delay(1500) // Realistic conversational typing lag
            
            val thread = repository.getThreadById(threadId) ?: return@launch
            val secret = thread.sharedSecret
            
            val replyText: String
            val shouldEncrypt: Boolean

            when {
                sessionManager.isSandboxMode() -> {
                    replyText = if (wasUserEncrypted) {
                        "Local hardware KeyStore GCM ciphertext parsed cleanly! Safe and verified offline. Returning authenticated response."
                    } else {
                        "Offline sandbox echo received. Try ticking 'Encryption Active' to experience direct on-device hardware E2EE."
                    }
                    shouldEncrypt = wasUserEncrypted
                }
                userText.contains("instagram.com") -> {
                    replyText = "Wow, that's an incredible post! Thanks for sharing this. Check this response video:"
                    shouldEncrypt = false
                    
                    // Respond with text and then simulate a reel share
                    repository.createMessage(
                        threadId = threadId,
                        senderId = threadId,
                        rawText = replyText,
                        isSender = false,
                        encryptMode = false
                    )
                    
                    delay(2000)
                    repository.createMessage(
                        threadId = threadId,
                        senderId = threadId,
                        rawText = "https://www.instagram.com/reel/C7-M7_nI9Z_/",
                        isSender = false,
                        encryptMode = false
                    )
                    return@launch
                }
                wasUserEncrypted -> {
                    replyText = "Secure payload parsed! Decryption successful side-channel with key [$secret]. Here is my client-side E2EE response."
                    shouldEncrypt = true
                }
                userText.lowercase().contains("hi") || userText.lowercase().contains("hello") -> {
                    replyText = "Hello! Secure channel established. Let's communicate privately. Try ticking 'Encryption Active' to lock our chat."
                    shouldEncrypt = false
                }
                else -> {
                    replyText = "Message securely received. My cryptographic key is in lockstep with yours. Ready for more shares!"
                    shouldEncrypt = encryptEnabled // Match user preference
                }
            }

            repository.createMessage(
                threadId = threadId,
                senderId = threadId,
                rawText = replyText,
                isSender = false,
                encryptMode = shouldEncrypt
            )
        }
    }

    fun saveSession(cookies: String, username: String) {
        sessionManager.setSandboxMode(false)
        isSandboxMode = false
        sessionManager.saveCookies(cookies, username)
        isAuthenticated = true
        currentUsername = username
    }

    fun saveManualProtocolConfig(
        username: String,
        targetUserId: String,
        sessionToken: String,
        csrfToken: String,
        userAgent: String
    ) {
        sessionManager.setSandboxMode(false)
        isSandboxMode = false
        sessionManager.saveManualSession(username, targetUserId, sessionToken, csrfToken, userAgent)
        isAuthenticated = true
        currentUsername = username
    }

    fun enableSandboxMode() {
        sessionManager.setSandboxMode(true)
        isSandboxMode = true
        isAuthenticated = true
        currentUsername = "sandbox_developer"

        viewModelScope.launch {
            val threadId = "sandbox_channel"
            val existing = repository.getThreadById(threadId)
            if (existing == null) {
                repository.createThread(
                    id = threadId,
                    contactName = "Hardware Sandbox",
                    contactUsername = "offline_sandbox",
                    profilePicUrl = "https://picsum.photos/seed/offline_sandbox/150/150",
                    sharedSecret = "sandbox_key_256"
                )

                // Seed some offline messages, including local keystore encryption and local mock attachment types
                repository.createMessage(
                    threadId = threadId,
                    senderId = "offline_sandbox",
                    rawText = "Welcome to the CipherGram closed hardware-backed sandbox environment! All operations are local here.",
                    isSender = false,
                    encryptMode = false
                )

                // Encrypted message using LocalCryptoEngine keystore!
                val samplePlain = "This message is encrypted entirely on your device using KeyStore-backed AES-GCM 256-bit parameters."
                val sampleCiphertext = com.example.security.LocalCryptoEngine.encrypt(samplePlain)
                repository.createMessage(
                    threadId = threadId,
                    senderId = "offline_sandbox",
                    rawText = sampleCiphertext,
                    isSender = false,
                    encryptMode = false
                )

                // Mock voice message cache URI
                repository.createMessage(
                    threadId = threadId,
                    senderId = "offline_sandbox",
                    rawText = "MOCK_VOICE:https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    isSender = false,
                    encryptMode = false
                )

                // Mock static video looping stream (EXOPLAYER DEMO!)
                repository.createMessage(
                    threadId = threadId,
                    senderId = "offline_sandbox",
                    rawText = "MOCK_VIDEO:https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    isSender = false,
                    encryptMode = false
                )
                
                repository.createMessage(
                    threadId = threadId,
                    senderId = "offline_sandbox",
                    rawText = "Toggle 'Encryption Active' below to send messages with your Keystore's AES-GCM hardware key. Safe, offline, and instant.",
                    isSender = false,
                    encryptMode = false
                )
            }
        }
    }

    fun logout() {
        sessionManager.setSandboxMode(false)
        isSandboxMode = false
        sessionManager.clearSession()
        isAuthenticated = false
        currentUsername = "Guest User"
    }

    fun createCustomThread(username: String, name: String, secret: String) {
        viewModelScope.launch {
            val safeId = username.lowercase().replace(" ", "_").trim()
            repository.createThread(
                id = safeId,
                contactName = name,
                contactUsername = username,
                profilePicUrl = "https://picsum.photos/seed/$safeId/150/150",
                sharedSecret = secret
            )
            selectThread(safeId)
        }
    }

    fun deleteCurrentThread() {
        val threadId = _activeThreadId.value ?: return
        viewModelScope.launch {
            repository.deleteThread(threadId)
            selectThread(null)
        }
    }

    /**
     * Slices high-resolution inputs, encrypts client-side under AES-GCM (256-bit) GCM payload,
     * and sequentially inserts segments across text message fields safely.
     */
    fun sendSecureAttachment(type: String, fileId: String, data: ByteArray) {
        val threadId = _activeThreadId.value ?: return
        val thread = _activeThread.value ?: return
        val secret = thread.sharedSecret
        if (secret.isEmpty()) return

        viewModelScope.launch {
            try {
                // 1. Encrypt binary files safely using GCM spec
                val encrypted = AttachmentEngine.encryptBytes(data, secret)
                // 2. Fragment payloads into transportable packet sequences
                val packets = AttachmentEngine.sliceToChunkPackets(encrypted, fileId)
                
                // 3. Sequentially register packets inside Room Db
                for (packet in packets) {
                    repository.createMessage(
                        threadId = threadId,
                        senderId = "me",
                        rawText = packet,
                        isSender = true,
                        encryptMode = false // Pre-encrypted chunks
                    )
                }

                // Update text preview within active contacts thread
                val displayLabel = when (type) {
                    "img" -> "[🔒 E2EE Photo]"
                    "voice" -> "[🔒 E2EE Voice Message]"
                    "vid" -> "[🔒 E2EE Video]"
                    else -> "[🔒 E2EE Media]"
                }
                database.messageDao().updateThreadLastMessage(threadId, displayLabel, System.currentTimeMillis())
                
                // Simulate side-channel confirmation
                simulateAttachmentReply(threadId, type)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to transmit encrypted attachment", e)
            }
        }
    }

    private fun simulateAttachmentReply(threadId: String, type: String) {
        viewModelScope.launch {
            delay(2500)
            val responseText = when (type) {
                "img" -> "Image decrypted successfully! Checked AES-GCM authentication tags in step context."
                "voice" -> "Audio file decoded from container and decrypted. Speech clarity is crystal clear."
                "vid" -> "Video data received, reassembled, and successfully validated."
                else -> "CipherGram chunk pipeline verified. Data Integrity OK!"
            }
            repository.createMessage(
                threadId = threadId,
                senderId = threadId,
                rawText = responseText,
                isSender = false,
                encryptMode = encryptEnabled
            )
        }
    }
}
