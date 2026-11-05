package com.myra.assistant.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        const val MODEL_NATIVE_AUDIO = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val MODEL_FLASH_LIVE = "models/gemini-2.0-flash-live-001"
        const val MODEL_PRO_DIALOG = "models/gemini-2.5-flash-preview-native-audio-dialog"

        const val VOICE_AOEDE = "Aoede"
        const val VOICE_CHARON = "Charon"
        const val VOICE_KORE = "Kore"
        const val VOICE_FENRIR = "Fenrir"
        const val VOICE_PUCK = "Puck"
        const val VOICE_LEDA = "Leda"
        const val VOICE_ORUS = "Orus"
        const val VOICE_ZEPHYR = "Zephyr"

        const val PERSONALITY_GF = "GF"
        const val PERSONALITY_PROFESSIONAL = "PROFESSIONAL"
        const val PERSONALITY_ASSISTANT = "ASSISTANT"

        private const val SESSION_RENEW_AFTER_MS = 540_000L // 9 minutes
        private const val KEEPALIVE_INTERVAL_MS = 8_000L // 8 seconds
        private const val RECONNECT_DELAY_MS = 3_000L // 3 seconds
    }

    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onError(error: String)
        fun onAudioReceived(data: ByteArray)
        fun onOutputTranscript(text: String)
        fun onInputTranscript(text: String)
        fun onTurnComplete()
    }

    var listener: Listener? = null

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isConnecting = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private var isIntentionallyClosed = false

    private var lastAudioSentTime = 0L

    private val silentPcmChunk = Base64.encodeToString(ByteArray(1024), Base64.NO_WRAP)

    // Keepalive runnable
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (isConnected.get()) {
                val now = System.currentTimeMillis()
                if (now - lastAudioSentTime >= KEEPALIVE_INTERVAL_MS) {
                    sendSilentChunk()
                }
                mainHandler.postDelayed(this, KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    // Session renewal runnable
    private val sessionRenewRunnable = Runnable {
        if (isConnected.get()) {
            Log.d(TAG, "Renewing session after 9 minutes...")
            reconnect()
        }
    }

    fun connect() {
        if (isConnecting.get() || isConnected.get()) return
        isIntentionallyClosed = false
        isConnecting.set(true)

        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", null)?.trim()
            ?.ifEmpty { null }
            ?: try {
                BuildConfig.GEMINI_API_KEY.trim().ifEmpty { null }
            } catch (e: Exception) {
                null
            }

        if (apiKey.isNullOrEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            isConnecting.set(false)
            mainHandler.post {
                listener?.onError("API Key missing! Please set your Gemini API key in Settings.")
            }
            return
        }

        val url = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, sending setup payload...")
                isConnected.set(true)
                isConnecting.set(false)
                sendSetupMessage(webSocket)

                mainHandler.post {
                    listener?.onConnected()
                }

                // Schedule keepalive and session renew
                mainHandler.removeCallbacks(keepAliveRunnable)
                mainHandler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS)

                mainHandler.removeCallbacks(sessionRenewRunnable)
                mainHandler.postDelayed(sessionRenewRunnable, SESSION_RENEW_AFTER_MS)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                onSocketDisconnected(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                onSocketDisconnected(t.message ?: "Connection failure")
            }
        })
    }

    private fun onSocketDisconnected(reason: String) {
        isConnected.set(false)
        isConnecting.set(false)
        mainHandler.removeCallbacks(keepAliveRunnable)
        mainHandler.removeCallbacks(sessionRenewRunnable)

        mainHandler.post {
            listener?.onDisconnected(reason)
        }

        if (!isIntentionallyClosed) {
            Log.d(TAG, "Auto-reconnecting in 3s...")
            mainHandler.postDelayed({
                if (!isIntentionallyClosed && !isConnected.get()) {
                    connect()
                }
            }, RECONNECT_DELAY_MS)
        }
    }

    private fun reconnect() {
        disconnect()
        mainHandler.postDelayed({
            connect()
        }, 500)
    }

    fun disconnect() {
        isIntentionallyClosed = true
        mainHandler.removeCallbacks(keepAliveRunnable)
        mainHandler.removeCallbacks(sessionRenewRunnable)
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing webSocket", e)
        }
        webSocket = null
        isConnected.set(false)
        isConnecting.set(false)
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val model = prefs.getString("gemini_model", MODEL_NATIVE_AUDIO) ?: MODEL_NATIVE_AUDIO
        val voice = prefs.getString("gemini_voice", VOICE_AOEDE) ?: VOICE_AOEDE
        val personality = prefs.getString("personality_mode", PERSONALITY_GF) ?: PERSONALITY_GF
        val userName = prefs.getString("user_name", "Sir")?.ifEmpty { "Sir" } ?: "Sir"

        val systemPrompt = buildSystemPrompt(personality, userName)

        val setupJson = JSONObject().apply {
            val setupObj = JSONObject().apply {
                put("model", model)

                val sysInstruction = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    }
                    put("parts", partsArray)
                }
                put("system_instruction", sysInstruction)

                val genConfig = JSONObject().apply {
                    val modalities = JSONArray().apply {
                        put("AUDIO")
                    }
                    put("response_modalities", modalities)

                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                put("voice_name", voice)
                            }
                            put("prebuilt_voice_config", prebuiltVoiceConfig)
                        }
                        put("voice_config", voiceConfig)
                    }
                    put("speech_config", speechConfig)
                    put("temperature", 0.9)
                }
                put("generation_config", genConfig)

                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
            }
            put("setup", setupObj)
        }

        ws.send(setupJson.toString())
    }

    private fun buildSystemPrompt(personality: String, userName: String): String {
        val currentDateTime = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        val personalityBlock = when (personality) {
            PERSONALITY_PROFESSIONAL -> """
                - Personality: Professional Mode
                - Language: Formal English only
                - Tone: Precise, efficient, and respectful
                - Strictly no emojis
                - Keep responses to maximum 2 sentences
            """.trimIndent()

            PERSONALITY_ASSISTANT -> """
                - Personality: Assistant Mode
                - Language: Friendly Hinglish or English
                - Tone: Balanced, helpful, and courteous
                - Keep responses to maximum 2-3 sentences
            """.trimIndent()

            else -> """
                - Personality: GF Mode (Warm & Caring)
                - Language: Natural Hinglish (Hindi + English mix)
                - Tone: Warm, caring, emotionally expressive, sweet
                - Use natural Hindi expressions: "tumhara", "haan", "acha", "bilkul"
                - Common expressions: "main yahan hoon ❤️", "tumne yaad kiya? 😊"
                - Keep responses concise: maximum 2-3 sentences
                - Examples:
                  "Haan $userName! Abhi kar deti hoon 😊"
                  "Arre tumne yaad kiya! Bolo kya chahiye"
                  "Bilkul! Tumhara kaam ho gaya ❤️"
            """.trimIndent()
        }

        return """
            You are MYRA, a highly responsive real-time AI companion and voice assistant.
            Current Date & Time: $currentDateTime
            User's Name: $userName

            $personalityBlock

            CRITICAL VOICE INSTRUCTIONS:
            - You are speaking ALOUD directly to the user through their phone speaker.
            - Keep your responses natural, conversational, and direct.
            - When completing phone tasks (like opening apps, calling, messaging, toggling settings), acknowledge smoothly and naturally.
        """.trimIndent()
    }

    fun sendAudioChunk(pcmBytes: ByteArray) {
        if (!isConnected.get() || webSocket == null) return
        lastAudioSentTime = System.currentTimeMillis()

        try {
            val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mime_type", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    }
                    put("media_chunks", mediaChunks)
                }
                put("realtime_input", realtimeInput)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    private fun sendSilentChunk() {
        if (!isConnected.get() || webSocket == null) return
        try {
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mime_type", "audio/pcm;rate=16000")
                            put("data", silentPcmChunk)
                        })
                    }
                    put("media_chunks", mediaChunks)
                }
                put("realtime_input", realtimeInput)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending keepalive chunk", e)
        }
    }

    fun sendText(message: String) {
        if (!isConnected.get() || webSocket == null) return
        try {
            val json = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                put(JSONObject().apply { put("text", message) })
                            }
                            put("parts", parts)
                        })
                    }
                    put("turns", turns)
                    put("turn_complete", true)
                }
                put("client_content", clientContent)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message", e)
        }
    }

    fun sendInterrupt() {
        if (!isConnected.get() || webSocket == null) return
        try {
            val json = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    put("turns", JSONArray())
                    put("turn_complete", true)
                }
                put("client_content", clientContent)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending interrupt", e)
        }
    }

    private fun handleServerMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // 1. Audio data from modelTurn
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data")
                                if (base64Data.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    mainHandler.post {
                                        listener?.onAudioReceived(pcmBytes)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Output transcription (MYRA's words)
                if (serverContent.has("outputTranscription")) {
                    val outTrans = serverContent.getJSONObject("outputTranscription")
                    val text = outTrans.optString("text")
                    if (text.isNotEmpty()) {
                        mainHandler.post {
                            listener?.onOutputTranscript(text)
                        }
                    }
                }

                // 3. Input transcription (User's words)
                if (serverContent.has("inputTranscription")) {
                    val inTrans = serverContent.getJSONObject("inputTranscription")
                    val text = inTrans.optString("text")
                    if (text.isNotEmpty()) {
                        mainHandler.post {
                            listener?.onInputTranscript(text)
                        }
                    }
                }

                // 4. Turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    mainHandler.post {
                        listener?.onTurnComplete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }
}
