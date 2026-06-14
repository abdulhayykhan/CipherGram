package com.aistudio.ciphergram.xtzqjp.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.aistudio.ciphergram.xtzqjp.data.model.ChatMessage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ParsedChunk(
    val id: String,
    val index: Int,
    val total: Int,
    val payloadBase64: String
)

data class UiChatMessage(
    val id: String,
    val threadId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isEncrypted: Boolean,
    val isSender: Boolean,
    val isScrapedMedia: Boolean,
    val mediaImageUrl: String?,
    val mediaVideoUrl: String?,
    val mediaCaption: String?,
    val isVoiceNote: Boolean = false,
    val isAttachment: Boolean = false,
    val attachmentPath: String? = null,
    val attachmentType: String? = null, // "img", "voice", "vid"
    val isDecryptionError: Boolean = false,
    val rawCipherPayload: String = "",
    val chunksCount: Int = 0,
    val chunksTotal: Int = 0
)

object AttachmentEngine {
    private const val TAG = "AttachmentEngine"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val CHUNK_SIZE_BYTES = 28000 // Around 28KB per chunk to prevent SQL line-length overflow and latency

    /**
     * Reads a Uri, downscales if image, and compresses to optimize payload weight.
     */
    fun getOptimizedMediaBytes(context: Context, uri: Uri, type: String): ByteArray {
        val cr = context.contentResolver
        val inputStream: InputStream? = cr.openInputStream(uri)
        if (inputStream == null) {
            Log.e(TAG, "Failed to open input stream for Uri: $uri")
            return ByteArray(0)
        }

        return try {
            if (type == "img") {
                // Downscale image using custom bitmap scale conversion steps
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                val tempBytes = inputStream.readBytes()
                BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.size, options)

                val maxDimension = 800
                var inSampleSize = 1
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                val bitmap = BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.size, decodeOptions)
                if (bitmap != null) {
                    val outStream = ByteArrayOutputStream()
                    // Compress to JPG at 70% quality to shrink packet pipeline footprint
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outStream)
                    Log.d(TAG, "Compressed high-res image from ${tempBytes.size} to ${outStream.size()} bytes")
                    outStream.toByteArray()
                } else {
                    tempBytes
                }
            } else {
                // Return audio or video standard binary array
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing media bytes", e)
            ByteArray(0)
        } finally {
            try { inputStream.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Encrypts the raw byte stream with AES-GCM using a [SecretKeySpec] derived from ECDH.
     */
    fun encryptBytes(data: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertextBytes = cipher.doFinal(data)
        val combined = ByteArray(iv.size + ciphertextBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertextBytes, 0, combined, iv.size, ciphertextBytes.size)
        return combined
    }

    /**
     * Decrypts the raw unified GCM buffer using a [SecretKeySpec] derived from ECDH.
     */
    fun decryptBytes(combined: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        val ciphertextBytes = ByteArray(combined.size - iv.size)
        System.arraycopy(combined, iv.size, ciphertextBytes, 0, ciphertextBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertextBytes)
    }

    /**
     * Slices the encrypted bytes, base64 encodes, and structures as sequence packets:
     * ENC_CHUNK:[fileId]:[index]:[total]:[payload]
     */
    fun sliceToChunkPackets(encryptedBytes: ByteArray, fileId: String): List<String> {
        val packets = mutableListOf<String>()
        val totalChunks = Math.ceil(encryptedBytes.size.toDouble() / CHUNK_SIZE_BYTES).toInt()

        for (i in 0 until totalChunks) {
            val start = i * CHUNK_SIZE_BYTES
            val length = Math.min(CHUNK_SIZE_BYTES, encryptedBytes.size - start)
            val chunkBytes = ByteArray(length)
            System.arraycopy(encryptedBytes, start, chunkBytes, 0, length)

            val base64Payload = Base64.encodeToString(chunkBytes, Base64.NO_WRAP or Base64.DEFAULT)
            packets.add("ENC_CHUNK:$fileId:$i:$totalChunks:$base64Payload")
        }
        return packets
    }

    /**
     * Checks if a text message is part of our advanced chunking system
     */
    fun isChunkMessage(text: String): Boolean {
        return text.startsWith("ENC_CHUNK:")
    }

    /**
     * Parses chunk header parameters
     */
    fun parseChunk(text: String): ParsedChunk? {
        if (!text.startsWith("ENC_CHUNK:")) return null
        return try {
            val parts = text.split(":")
            if (parts.size < 5) return null
            val id = parts[1]
            val index = parts[2].toIntOrNull() ?: return null
            val total = parts[3].toIntOrNull() ?: return null
            val payloadBase64 = parts.drop(4).joinToString(":")
            ParsedChunk(id, index, total, payloadBase64)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts and dynamically merges chat history messages on-the-fly.
     * Groups fragmented packets, assembles complete files, caches decrypted local media,
     * and maps standard text items.
     */
    fun processRawMessages(
        context: Context,
        rawList: List<ChatMessage>,
        sharedKey: SecretKeySpec?
    ): List<UiChatMessage> {
        val result = mutableListOf<UiChatMessage>()
        
        // Standard text messages
        val nonChunkMessages = rawList.filter { !isChunkMessage(it.encryptedText) }
        for (msg in nonChunkMessages) {
            val textToParse = msg.encryptedText
            val isMockVoice = textToParse.startsWith("MOCK_VOICE:")
            val isMockVideo = textToParse.startsWith("MOCK_VIDEO:")
            
            val isLocalEncrypted = LocalCryptoEngine.isEncrypted(textToParse)
            val isEncryptedMsg = CryptoEngine.isEncrypted(textToParse) || isLocalEncrypted
            val parsedText = when {
                isLocalEncrypted -> LocalCryptoEngine.decrypt(textToParse)
                isMockVoice -> "Voice Note (Local Sandbox Cache)"
                isMockVideo -> "Video Message (Local Sandbox Video)"
                sharedKey != null && CryptoEngine.isEncrypted(textToParse) ->
                    CryptoEngine.decrypt(textToParse, sharedKey)
                else -> textToParse
            }
            val isError = !isMockVoice && !isMockVideo && (parsedText.contains("[Decryption Failed") || parsedText.startsWith("[Error"))
            
            result.add(
                UiChatMessage(
                    id = msg.id,
                    threadId = msg.threadId,
                    senderId = msg.senderId,
                    text = parsedText,
                    timestamp = msg.timestamp,
                    isEncrypted = isEncryptedMsg,
                    isSender = msg.isSender,
                    isScrapedMedia = msg.isScrapedMedia,
                    mediaImageUrl = msg.mediaImageUrl,
                    mediaVideoUrl = msg.mediaVideoUrl,
                    mediaCaption = msg.mediaCaption,
                    isVoiceNote = isMockVoice,
                    isAttachment = isMockVoice || isMockVideo,
                    attachmentPath = if (isMockVoice) textToParse.removePrefix("MOCK_VOICE:") else if (isMockVideo) textToParse.removePrefix("MOCK_VIDEO:") else null,
                    attachmentType = if (isMockVoice) "voice" else if (isMockVideo) "vid" else null,
                    isDecryptionError = isError,
                    rawCipherPayload = if (isEncryptedMsg) textToParse else ""
                )
            )
        }

        // Segmented Chunks
        val chunkMessages = rawList.filter { isChunkMessage(it.encryptedText) }
        val parsedChunksGrouped = chunkMessages.mapNotNull { msg ->
            val parsed = parseChunk(msg.encryptedText)
            if (parsed != null) msg to parsed else null
        }.groupBy { it.second.id }

        for ((id, group) in parsedChunksGrouped) {
            val totalRequired = group.first().second.total
            val receivedIndices = group.map { it.second.index }.toSet()
            val senderMsg = group.first().first
            
            // Extract type info from id (e.g., img_17183204_jpg => img, jpg)
            val idParts = id.split("_")
            val mediaType = idParts.getOrNull(0) ?: "img"
            val fileExt = idParts.getOrNull(idParts.size - 1) ?: "jpg"

            val isFullyReceived = (0 until totalRequired).all { it in receivedIndices }

            if (isFullyReceived) {
                // Assemble decrypted cached file location
                val decryptFolder = File(context.cacheDir, "decrypted_attachments")
                if (!decryptFolder.exists()) {
                    decryptFolder.mkdirs()
                }
                val cachedFile = File(decryptFolder, "$id.$fileExt")

                var hasDecryptedSuccessfully = true
                var decryptionErrorMsg = ""

                if (!cachedFile.exists()) {
                    try {
                        // Gather sorted chunks
                        val sorted = group.sortedBy { it.second.index }
                        val stream = ByteArrayOutputStream()
                        for (item in sorted) {
                            val chunkBytes = Base64.decode(item.second.payloadBase64, Base64.DEFAULT)
                            stream.write(chunkBytes)
                        }
                        
                        // Decrypt binary AES stream
                        val decryptedBytes = if (sharedKey != null) decryptBytes(stream.toByteArray(), sharedKey) else ByteArray(0)
                        
                        // Dump uncompressed binary values inside local storage
                        FileOutputStream(cachedFile).use { fos ->
                            fos.write(decryptedBytes)
                        }
                        Log.d(TAG, "Successfully assembled and decrypted secure file to: ${cachedFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption or assembly loop failed for: $id", e)
                        hasDecryptedSuccessfully = false
                        decryptionErrorMsg = "[Attachment Decryption Failed: Invalid/Mismatching Keys]"
                    }
                }

                val filePathUri = "file://${cachedFile.absolutePath}"
                
                result.add(
                    UiChatMessage(
                        id = id,
                        threadId = senderMsg.threadId,
                        senderId = senderMsg.senderId,
                        text = if (hasDecryptedSuccessfully) "Secure Attachment: ${mediaType.uppercase()}" else decryptionErrorMsg,
                        timestamp = group.maxOf { it.first.timestamp },
                        isEncrypted = true,
                        isSender = senderMsg.isSender,
                        isScrapedMedia = false,
                        mediaImageUrl = if (mediaType == "img" && hasDecryptedSuccessfully) filePathUri else null,
                        mediaVideoUrl = if (mediaType == "vid" && hasDecryptedSuccessfully) filePathUri else null,
                        mediaCaption = null,
                        isVoiceNote = mediaType == "voice",
                        isAttachment = true,
                        attachmentPath = if (hasDecryptedSuccessfully) filePathUri else null,
                        attachmentType = mediaType,
                        isDecryptionError = !hasDecryptedSuccessfully,
                        rawCipherPayload = "[ENC_CHUNK:$id]"
                    )
                )
            } else {
                // Incomplete chunks: report reception transit progress safely
                result.add(
                    UiChatMessage(
                        id = id,
                        threadId = senderMsg.threadId,
                        senderId = senderMsg.senderId,
                        text = "Receiving Cipher Attachment... [${group.size}/${totalRequired} parts in transit]",
                        timestamp = group.maxOf { it.first.timestamp },
                        isEncrypted = true,
                        isSender = senderMsg.isSender,
                        isScrapedMedia = false,
                        mediaImageUrl = null,
                        mediaVideoUrl = null,
                        mediaCaption = null,
                        isVoiceNote = mediaType == "voice",
                        isAttachment = false,
                        attachmentType = mediaType,
                        isDecryptionError = false,
                        chunksCount = group.size,
                        chunksTotal = totalRequired
                    )
                )
            }
        }

        return result.sortedBy { it.timestamp }
    }
}
