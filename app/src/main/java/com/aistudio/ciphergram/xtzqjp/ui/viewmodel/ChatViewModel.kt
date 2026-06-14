package com.aistudio.ciphergram.xtzqjp.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.ciphergram.xtzqjp.data.model.ChatMessage
import com.aistudio.ciphergram.xtzqjp.data.model.ChatThread
import com.aistudio.ciphergram.xtzqjp.data.model.UserProfile
import com.aistudio.ciphergram.xtzqjp.data.network.InstagramScraper
import com.aistudio.ciphergram.xtzqjp.data.network.SessionManager
import com.aistudio.ciphergram.xtzqjp.data.repository.ChatRepository
import com.aistudio.ciphergram.xtzqjp.security.AttachmentEngine
import com.aistudio.ciphergram.xtzqjp.security.CryptoEngine
import com.aistudio.ciphergram.xtzqjp.security.UiChatMessage
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.PrivateKey

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val repository = ChatRepository(sessionManager)

    // Facebook SDK CallbackManager (stored here so MainActivity can delegate onActivityResult)
    val callbackManager: CallbackManager = CallbackManager.Factory.create()

    // ── Auth state ──────────────────────────────────────────────────────────
    var isAuthenticated by mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
        private set
    var currentUserProfile by mutableStateOf<UserProfile?>(null)
        private set
    var isSandboxMode by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // ── Thread list state ────────────────────────────────────────────────────
    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    // ── Active thread & messages ─────────────────────────────────────────────
    private val _activeThread = MutableStateFlow<ChatThread?>(null)
    val activeThread: StateFlow<ChatThread?> = _activeThread.asStateFlow()

    private val _messages = MutableStateFlow<List<UiChatMessage>>(emptyList())
    val messages: StateFlow<List<UiChatMessage>> = _messages.asStateFlow()

    // ── User search state ─────────────────────────────────────────────────────
    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()
    var isSearching by mutableStateOf(false)
        private set

    // ── Chat input state ──────────────────────────────────────────────────────
    var stagedMessageText by mutableStateOf("")
    var encryptEnabled by mutableStateOf(true)

    // ── Private key (loaded once per session) ─────────────────────────────────
    private var myPrivateKey: PrivateKey? = null

    // ── Listener jobs ─────────────────────────────────────────────────────────
    private var threadListJob: Job? = null
    private var messagesJob: Job? = null

    init {
        if (isAuthenticated) {
            initSession()
        }
    }

    // ── Session Lifecycle ─────────────────────────────────────────────────────

    private fun initSession() {
        // Load ECDH private key into memory
        val keyPair = CryptoEngine.getOrCreateKeyPair(getApplication())
        myPrivateKey = keyPair.private

        // Load user profile
        viewModelScope.launch {
            currentUserProfile = sessionManager.getCurrentUserProfile()
        }

        // Start listening for threads
        startThreadListener()
    }

    private fun startThreadListener() {
        threadListJob?.cancel()
        threadListJob = viewModelScope.launch {
            repository.getThreadsFlow().collect { threads ->
                _threads.value = threads
            }
        }
    }

    // ── Facebook Login ────────────────────────────────────────────────────────

    /**
     * Called from [SessionScreen] after Facebook Login SDK returns a token.
     */
    fun handleFacebookLogin(accessToken: AccessToken) {
        viewModelScope.launch {
            isLoading = true
            loginError = null
            try {
                val profile = sessionManager.signInWithFacebook(accessToken)
                if (profile != null) {
                    currentUserProfile = profile
                    isAuthenticated = true
                    isSandboxMode = false
                    initSession()
                    Log.d("ViewModel", "Logged in as ${profile.displayName}")
                } else {
                    loginError = "Sign-in failed. Please try again."
                }
            } catch (e: Exception) {
                loginError = e.message ?: "An unexpected error occurred."
                Log.e("ViewModel", "Login error", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun setLoginError(message: String?) {
        loginError = message
    }

    /** Activates offline sandbox mode for testing without real accounts. */
    fun enableSandboxMode() {
        isSandboxMode = true
        isAuthenticated = true
        currentUserProfile = UserProfile(
            uid = "sandbox_user",
            displayName = "Sandbox Developer",
            photoUrl = "https://picsum.photos/seed/sandbox/150/150",
            publicKey = ""
        )
        // Use local crypto for sandbox
        val keyPair = CryptoEngine.getOrCreateKeyPair(getApplication())
        myPrivateKey = keyPair.private

        // Seed a demo sandbox thread
        _threads.value = listOf(
            ChatThread(
                id = "sandbox_thread",
                participants = listOf("sandbox_user", "bot_user"),
                participantNames = mapOf(
                    "sandbox_user" to "You",
                    "bot_user" to "Sandbox Bot"
                ),
                participantPics = mapOf(
                    "sandbox_user" to "https://picsum.photos/seed/me/150/150",
                    "bot_user" to "https://picsum.photos/seed/bot/150/150"
                ),
                lastMessage = "Welcome to E2EE Sandbox 🔒",
                lastMessageTime = System.currentTimeMillis()
            )
        )
    }

    fun logout() {
        messagesJob?.cancel()
        threadListJob?.cancel()
        _threads.value = emptyList()
        _messages.value = emptyList()
        _activeThread.value = emptyList<ChatThread>().let { null }
        currentUserProfile = null
        myPrivateKey = null
        isAuthenticated = false
        isSandboxMode = false
        CryptoEngine.clearKeyCache()
        sessionManager.signOut()
    }

    // ── Thread Selection ──────────────────────────────────────────────────────

    fun selectThread(thread: ChatThread?) {
        messagesJob?.cancel()
        _activeThread.value = thread
        _messages.value = emptyList()

        if (thread == null) return

        messagesJob = viewModelScope.launch {
            repository.getMessagesFlow(thread.id).collect { rawMessages ->
                _messages.value = processMessages(rawMessages, thread)
            }
        }
    }

    /**
     * Decrypts raw Firestore messages into [UiChatMessage] for display.
     * Uses ECDH shared key derived from the remote user's public key.
     */
    private suspend fun processMessages(
        rawMessages: List<ChatMessage>,
        thread: ChatThread
    ): List<UiChatMessage> {
        val myUid = currentUserProfile?.uid ?: "sandbox_user"
        val remoteUid = thread.getOtherParticipantId(myUid)

        // Get the remote user's public key (for ECDH)
        val remoteProfile = if (!isSandboxMode && remoteUid.isNotEmpty()) {
            repository.getUserProfile(remoteUid)
        } else null

        val privateKey = myPrivateKey
        val sharedKey = if (privateKey != null && remoteProfile?.publicKey?.isNotEmpty() == true) {
            try {
                CryptoEngine.getSharedKey(remoteUid, remoteProfile.publicKey, privateKey)
            } catch (e: Exception) {
                Log.e("ViewModel", "ECDH key derivation failed", e)
                null
            }
        } else if (isSandboxMode) {
            // Sandbox: use local key derivation
            CryptoEngine.deriveKeyFromPassphrase("sandbox_key_256")
        } else null

        return rawMessages.map { msg ->
            val decryptedText = when {
                sharedKey != null && CryptoEngine.isEncrypted(msg.encryptedText) -> {
                    CryptoEngine.decrypt(msg.encryptedText, sharedKey)
                }
                InstagramScraper.isInstagramUrl(msg.encryptedText) -> msg.encryptedText
                else -> msg.encryptedText
            }

            val isError = decryptedText.startsWith("[Decryption Failed")

            UiChatMessage(
                id = msg.id,
                threadId = msg.threadId,
                senderId = msg.senderId,
                text = decryptedText,
                timestamp = msg.timestamp,
                isEncrypted = CryptoEngine.isEncrypted(msg.encryptedText),
                isSender = msg.isSender,
                isScrapedMedia = msg.isScrapedMedia,
                mediaImageUrl = msg.mediaImageUrl,
                mediaVideoUrl = msg.mediaVideoUrl,
                mediaCaption = msg.mediaCaption,
                isDecryptionError = isError,
                rawCipherPayload = if (CryptoEngine.isEncrypted(msg.encryptedText)) msg.encryptedText else ""
            )
        }
    }

    // ── Sending Messages ──────────────────────────────────────────────────────

    fun sendMessage() {
        val thread = _activeThread.value ?: return
        val text = stagedMessageText.trim()
        if (text.isEmpty()) return
        stagedMessageText = ""

        viewModelScope.launch {
            val myUid = currentUserProfile?.uid ?: "sandbox_user"
            val remoteUid = thread.getOtherParticipantId(myUid)

            // Determine encryption key
            val encryptedPayload = if (encryptEnabled && !InstagramScraper.isInstagramUrl(text)) {
                val privateKey = myPrivateKey
                val remoteProfile = if (!isSandboxMode) repository.getUserProfile(remoteUid) else null

                when {
                    isSandboxMode -> {
                        val key = CryptoEngine.deriveKeyFromPassphrase("sandbox_key_256")
                        CryptoEngine.encrypt(text, key)
                    }
                    privateKey != null && remoteProfile?.publicKey?.isNotEmpty() == true -> {
                        val key = CryptoEngine.getSharedKey(remoteUid, remoteProfile.publicKey, privateKey)
                        CryptoEngine.encrypt(text, key)
                    }
                    else -> text // No key available, send plain
                }
            } else {
                text // Instagram URLs or encryption disabled
            }

            if (isSandboxMode) {
                // Add to local sandbox state directly
                val uiMsg = UiChatMessage(
                    id = System.currentTimeMillis().toString(),
                    threadId = thread.id,
                    senderId = myUid,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isEncrypted = encryptEnabled,
                    isSender = true,
                    isScrapedMedia = false,
                    mediaImageUrl = null,
                    mediaVideoUrl = null,
                    mediaCaption = null
                )
                _messages.value = _messages.value + uiMsg
            } else {
                repository.sendMessage(
                    threadId = thread.id,
                    encryptedText = encryptedPayload,
                    senderDisplayName = currentUserProfile?.displayName ?: ""
                )
            }
        }
    }

    fun handleIncomingShareUrl(url: String) {
        stagedMessageText = url
        encryptEnabled = false
    }

    // ── Attachment Sending ────────────────────────────────────────────────────

    fun sendSecureAttachment(type: String, fileId: String, data: ByteArray) {
        val thread = _activeThread.value ?: return
        val myUid = currentUserProfile?.uid ?: return
        val remoteUid = thread.getOtherParticipantId(myUid)

        viewModelScope.launch {
            try {
                val privateKey = myPrivateKey ?: return@launch
                val remoteProfile = repository.getUserProfile(remoteUid) ?: return@launch
                val sharedKey = CryptoEngine.getSharedKey(remoteUid, remoteProfile.publicKey, privateKey)

                // Encrypt attachment bytes
                val encryptedBytes = AttachmentEngine.encryptBytes(data, sharedKey)
                val packets = AttachmentEngine.sliceToChunkPackets(encryptedBytes, fileId)

                for (packet in packets) {
                    repository.sendMessage(
                        threadId = thread.id,
                        encryptedText = packet,
                        senderDisplayName = currentUserProfile?.displayName ?: ""
                    )
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Attachment send failed", e)
            }
        }
    }

    // ── User Discovery ────────────────────────────────────────────────────────

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            isSearching = true
            _searchResults.value = repository.searchUsers(query)
            isSearching = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    /**
     * Opens or creates a chat thread with [otherUser] and navigates to it.
     * @return The thread ID.
     */
    suspend fun startOrOpenChat(otherUser: UserProfile): String {
        return repository.getOrCreateThread(otherUser)
    }

    fun deleteThread(thread: ChatThread) {
        viewModelScope.launch {
            repository.deleteThread(thread.id)
            if (_activeThread.value?.id == thread.id) {
                selectThread(null)
            }
        }
    }
}
