package com.example.ui.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    /**
     * Initializes the native MediaRecorder, target output file directories,
     * low-latency MPEG_4 format with direct sub-systems AAC encoder.
     */
    fun startRecording(fileId: String): File? {
        try {
            val voiceCacheDir = File(context.cacheDir, "voice_cache")
            if (!voiceCacheDir.exists()) {
                voiceCacheDir.mkdirs()
            }
            
            val audioOutput = File(voiceCacheDir, "${fileId}.m4a")
            currentOutputFile = audioOutput

            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(audioOutput.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            Log.d("AudioRecorder", "MediaRecorder successfully started: ${audioOutput.absolutePath}")
            return audioOutput
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Could not start MediaRecorder", e)
            mediaRecorder = null
            currentOutputFile = null
            return null
        }
    }

    /**
     * Stops the active recording session and returns the resulting file
     */
    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d("AudioRecorder", "MediaRecorder successfully stopped and outputs stored.")
            val result = currentOutputFile
            mediaRecorder = null
            currentOutputFile = null
            result
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to cleanly stop recorder", e)
            mediaRecorder = null
            currentOutputFile = null
            null
        }
    }

    /**
     * Terminate current tracking session and prune temp storage
     */
    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (ignored: Exception) {
        } finally {
            mediaRecorder = null
        }
        
        currentOutputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        currentOutputFile = null
    }
}
