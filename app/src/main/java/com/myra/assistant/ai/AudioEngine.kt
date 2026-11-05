package com.myra.assistant.ai

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        const val SAMPLE_RATE_IN = 16000 // 16 kHz for mic
        const val SAMPLE_RATE_OUT = 24000 // 24 kHz from Gemini
        const val CHUNK_SIZE = 1024 // 1024 bytes
    }

    interface Listener {
        fun onAudioCaptured(chunk: ByteArray)
        fun onAmplitudeChanged(rms: Float)
        fun onSpeakingStarted()
        fun onSpeakingStopped()
        fun onError(error: String)
    }

    var listener: Listener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()

    private var recordingThread: Thread? = null
    private var playbackThread: Thread? = null

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun isMuted(): Boolean = isMuted.get()

    fun isSpeaking(): Boolean = isSpeaking.get()

    fun startRecording() {
        if (isRecording.get()) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            listener?.onError("Microphone permission not granted")
            return
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, CHUNK_SIZE * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_IN,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                listener?.onError("AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordingThread = Thread({
                val buffer = ByteArray(CHUNK_SIZE)
                while (isRecording.get()) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val rms = calculateRms(buffer, readBytes)
                        mainHandler.post {
                            listener?.onAmplitudeChanged(rms)
                        }

                        // Echo suppression: Only send if NOT speaking and NOT muted
                        if (!isSpeaking.get() && !isMuted.get()) {
                            val chunk = buffer.copyOf(readBytes)
                            listener?.onAudioCaptured(chunk)
                        }
                    }
                }
            }, "MyraRecordThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            listener?.onError("Failed to start audio recording: ${e.message}")
        }
    }

    fun stopRecording() {
        isRecording.set(false)
        try {
            recordingThread?.interrupt()
            recordingThread = null
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
        audioRecord = null
    }

    fun startPlayback() {
        if (isPlaying.get()) return

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_OUT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufSize, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying.set(true)

            playbackThread = Thread({
                while (isPlaying.get()) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null) {
                        if (!isSpeaking.get()) {
                            isSpeaking.set(true)
                            mainHandler.post { listener?.onSpeakingStarted() }
                        }

                        val rms = calculateRms(chunk, chunk.size)
                        mainHandler.post { listener?.onAmplitudeChanged(rms) }

                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        if (isSpeaking.get()) {
                            // Give brief delay to check if more chunks arrive
                            Thread.sleep(100)
                            if (playbackQueue.isEmpty()) {
                                isSpeaking.set(false)
                                mainHandler.post { listener?.onSpeakingStopped() }
                            }
                        } else {
                            Thread.sleep(20)
                        }
                    }
                }
            }, "MyraPlaybackThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            listener?.onError("Failed to start audio playback: ${e.message}")
        }
    }

    fun queueAudio(pcmData: ByteArray) {
        playbackQueue.offer(pcmData)
        if (!isPlaying.get()) {
            startPlayback()
        }
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }
        if (isSpeaking.get()) {
            isSpeaking.set(false)
            mainHandler.post { listener?.onSpeakingStopped() }
        }
    }

    fun stopPlayback() {
        isPlaying.set(false)
        isSpeaking.set(false)
        playbackQueue.clear()
        try {
            playbackThread?.interrupt()
            playbackThread = null
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
        audioTrack = null
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i < length - 1) {
            // Little-endian 16-bit PCM
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signedSample = if (sample >= 32768) sample - 65536 else sample
            sum += (signedSample * signedSample).toDouble()
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val rms = sqrt(sum / samples)
        // Normalize 0..1 (32767 is max amplitude)
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }
}
