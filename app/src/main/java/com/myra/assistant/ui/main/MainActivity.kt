package com.myra.assistant.ui.main

import android.Manifest
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var viewModel: MainViewModel
    private var geminiLive: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null

    // Views
    private lateinit var batteryText: TextView
    private lateinit var ramText: TextView
    private lateinit var timeText: TextView
    private lateinit var settingsBtn: ImageButton
    private lateinit var orbAnimationView: OrbAnimationView
    private lateinit var waveformView: WaveformView
    private lateinit var statusText: TextView
    private lateinit var redOverlayView: View
    private lateinit var chatRecycler: RecyclerView
    private lateinit var micButton: ImageButton
    private lateinit var hintText: TextView

    private lateinit var chatAdapter: ChatAdapter

    private val mainHandler = Handler(Looper.getMainLooper())

    // Transcription buffers
    private val inputTranscriptBuffer = StringBuilder()
    private val outputTranscriptBuffer = StringBuilder()

    private var isUserMuted = false
    private var isInCallMode = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isReceiverRegistered = false

    private var redOverlayAnimator: ValueAnimator? = null

    // System status periodic updates
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateSystemStatus()
            mainHandler.postDelayed(this, 30_000L)
        }
    }

    // Call ended broadcast receiver
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CallMonitorService.ACTION_CALL_ENDED) {
                Log.d(TAG, "Call ended broadcast received")
                isInCallMode = false
                setActiveMode(false)
                statusText.text = "Call samapt ho gaya 📞"
            }
        }
    }

    // Permissions launcher
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            try {
                audioEngine?.startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording after permission grant", e)
            }
        } else {
            Toast.makeText(this, "Microphone permission is required for voice assistant", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            viewModel = ViewModelProvider(this)[MainViewModel::class.java]

            initViews()
            checkPermissions()
            startSystemServices()
            startStatusUpdates()

            try {
                ContextCompat.registerReceiver(
                    this,
                    callEndedReceiver,
                    IntentFilter(CallMonitorService.ACTION_CALL_ENDED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not register callEndedReceiver: ${e.message}")
            }

            mainHandler.postDelayed({
                try {
                    initGeminiLive()
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing Gemini Live: ${e.message}", e)
                }
            }, 400L)

            handleIncomingCallIntent(intent)

            viewModel.commandResult.observe(this) { result ->
                if (!result.isNullOrEmpty()) {
                    chatAdapter.addMessage(ChatMessage(result, isUser = false))
                    chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    geminiLive?.sendText("System Action Result: $result. Please confirm briefly to user.")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error initializing MainActivity", t)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCallIntent(intent)
    }

    private fun initViews() {
        batteryText = findViewById(R.id.batteryText)
        ramText = findViewById(R.id.ramText)
        timeText = findViewById(R.id.timeText)
        settingsBtn = findViewById(R.id.settingsBtn)
        orbAnimationView = findViewById(R.id.orbAnimationView)
        waveformView = findViewById(R.id.waveformView)
        statusText = findViewById(R.id.statusText)
        redOverlayView = findViewById(R.id.redOverlayView)
        chatRecycler = findViewById(R.id.chatRecycler)
        micButton = findViewById(R.id.micButton)
        hintText = findViewById(R.id.hintText)

        // Setup Chat RecyclerView
        chatAdapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecycler.layoutManager = layoutManager
        chatRecycler.adapter = chatAdapter

        settingsBtn.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Tap Mic -> Mute toggle
        micButton.setOnClickListener {
            toggleMute()
        }

        // Long Press Mic -> Clear playback queue + send interrupt
        micButton.setOnLongClickListener {
            handleInterrupt()
            true
        }

        updateSystemStatus()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startSystemServices() {
        try {
            val callServiceIntent = Intent(this, CallMonitorService::class.java)
            startService(callServiceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CallMonitorService: ${e.message}")
        }

        if (android.provider.Settings.canDrawOverlays(this)) {
            try {
                val overlayIntent = Intent(this, MyraOverlayService::class.java)
                startService(overlayIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting MyraOverlayService: ${e.message}")
            }
        }
    }

    private fun startStatusUpdates() {
        mainHandler.removeCallbacks(statusUpdateRunnable)
        mainHandler.post(statusUpdateRunnable)
    }

    private fun updateSystemStatus() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            batteryText.text = if (batLevel >= 0) "BAT ${batLevel}%" else "BAT 100%"
        } catch (e: Exception) {
            batteryText.text = "BAT --%"
        }

        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val availGb = memInfo.availMem / (1024f * 1024f * 1024f)
            ramText.text = String.format(Locale.getDefault(), "RAM %.1fGB", availGb)
        } catch (e: Exception) {
            ramText.text = "RAM --GB"
        }

        try {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            timeText.text = timeFormat.format(Date())
        } catch (e: Exception) {
            timeText.text = "--:--"
        }
    }

    private fun initGeminiLive() {
        geminiLive = GeminiLiveClient(this)
        audioEngine = AudioEngine(this)

        geminiLive?.listener = object : GeminiLiveClient.Listener {
            override fun onConnected() {
                statusText.text = "Sun rahi hoon... 💬"
                orbAnimationView.setState(OrbAnimationView.OrbState.LISTENING)
                micButton.setImageResource(R.drawable.ic_mic_on)

                audioEngine?.startRecording()
                audioEngine?.startPlayback()

                mainHandler.postDelayed({
                    sendGreeting()
                }, 600L)
            }

            override fun onDisconnected(reason: String) {
                statusText.text = "Reconnecting... ($reason)"
                orbAnimationView.setState(OrbAnimationView.OrbState.IDLE)
            }

            override fun onError(error: String) {
                statusText.text = error
                Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                orbAnimationView.setState(OrbAnimationView.OrbState.IDLE)
            }

            override fun onAudioReceived(data: ByteArray) {
                audioEngine?.queueAudio(data)
            }

            override fun onOutputTranscript(text: String) {
                outputTranscriptBuffer.append(text)
            }

            override fun onInputTranscript(text: String) {
                inputTranscriptBuffer.append(text)
            }

            override fun onTurnComplete() {
                val userSpeech = inputTranscriptBuffer.toString().trim()
                val myraSpeech = outputTranscriptBuffer.toString().trim()

                inputTranscriptBuffer.setLength(0)
                outputTranscriptBuffer.setLength(0)

                if (userSpeech.isNotEmpty()) {
                    chatAdapter.addMessage(ChatMessage(userSpeech, isUser = true))
                    chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)

                    val command = CommandParser.parse(userSpeech)
                    if (command != null) {
                        Log.d(TAG, "Parsed phone command: $command")
                        viewModel.executeCommand(command)
                    }
                }

                if (myraSpeech.isNotEmpty()) {
                    chatAdapter.addMessage(ChatMessage(myraSpeech, isUser = false))
                    chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }

        audioEngine?.listener = object : AudioEngine.Listener {
            override fun onAudioCaptured(chunk: ByteArray) {
                if (!isInCallMode) {
                    geminiLive?.sendAudioChunk(chunk)
                }
            }

            override fun onAmplitudeChanged(rms: Float) {
                orbAnimationView.setAmplitude(rms)
                waveformView.setAmplitude(rms)
            }

            override fun onSpeakingStarted() {
                orbAnimationView.setState(OrbAnimationView.OrbState.SPEAKING)
                statusText.text = "Bol rahi hoon... 🔊"
                setActiveMode(true)
            }

            override fun onSpeakingStopped() {
                orbAnimationView.setState(OrbAnimationView.OrbState.LISTENING)
                statusText.text = "Sun rahi hoon... 💬"
                setActiveMode(false)
            }

            override fun onError(error: String) {
                Log.e(TAG, "AudioEngine error: $error")
            }
        }

        geminiLive?.connect()
    }

    private fun sendGreeting() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val personality = prefs.getString("personality_mode", GeminiLiveClient.PERSONALITY_GF)
        val name = prefs.getString("user_name", "Sir")?.ifEmpty { "Sir" } ?: "Sir"

        val greetingText = when (personality) {
            GeminiLiveClient.PERSONALITY_PROFESSIONAL -> "Good day $name. MYRA is online and ready to assist you."
            GeminiLiveClient.PERSONALITY_ASSISTANT -> "Hello $name! Main MYRA hoon. Kaise help karun aapki?"
            else -> "Hey $name! Main aa gayi hoon. Kya help chahiye tumhe?"
        }

        geminiLive?.sendText(greetingText)
    }

    private fun toggleMute() {
        isUserMuted = !isUserMuted
        audioEngine?.setMuted(isUserMuted)
        if (isUserMuted) {
            micButton.setImageResource(R.drawable.ic_mic_off)
            statusText.text = "Mic Muted 🔇"
            orbAnimationView.setState(OrbAnimationView.OrbState.IDLE)
        } else {
            micButton.setImageResource(R.drawable.ic_mic_on)
            statusText.text = "Sun rahi hoon... 💬"
            orbAnimationView.setState(OrbAnimationView.OrbState.LISTENING)
        }
    }

    private fun handleInterrupt() {
        audioEngine?.clearPlaybackQueue()
        geminiLive?.sendInterrupt()
        orbAnimationView.setState(OrbAnimationView.OrbState.LISTENING)
        statusText.text = "Sun rahi hoon... 💬"
        setActiveMode(false)
        Toast.makeText(this, "Stopped MYRA speaking", Toast.LENGTH_SHORT).show()
    }

    private fun setActiveMode(active: Boolean) {
        val targetAlpha = if (active) 0.08f else 0.0f
        redOverlayAnimator?.cancel()
        val duration = if (active) 300L else 500L
        redOverlayAnimator = ValueAnimator.ofFloat(redOverlayView.alpha, targetAlpha).apply {
            this.duration = duration
            addUpdateListener {
                redOverlayView.alpha = it.animatedValue as Float
            }
            start()
        }
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        val isCall = intent?.getBooleanExtra("INCOMING_CALL", false) ?: false
        val caller = intent?.getStringExtra("CALLER_NAME") ?: "Someone"
        if (isCall) {
            announceCall(caller)
        }
    }

    private fun announceCall(callerName: String) {
        isInCallMode = true
        val announcement = "Sir, $callerName ka call aa raha hai. Uthau ya reject karu?"
        chatAdapter.addMessage(ChatMessage(announcement, isUser = false))
        chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)

        geminiLive?.sendText(announcement)

        mainHandler.postDelayed({
            startCallDecisionStt()
        }, 4500L)
    }

    private fun startCallDecisionStt() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            isInCallMode = false
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "Bolo: 'Uthao' ya 'Reject'..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isInCallMode = false
                    statusText.text = "Sun rahi hoon... 💬"
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                    Log.d(TAG, "Call decision STT heard: $text")

                    if (text.contains("uthao") || text.contains("haan") || text.contains("accept") || text.contains("pick")) {
                        viewModel.acceptCall()
                        geminiLive?.sendText("Call utha liya hai.")
                    } else if (text.contains("reject") || text.contains("nahi") || text.contains("mat") || text.contains("cut")) {
                        viewModel.rejectCall()
                        geminiLive?.sendText("Call reject kar diya hai.")
                    }

                    isInCallMode = false
                    setActiveMode(false)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onPause() {
        super.onPause()
        audioEngine?.setMuted(true)
    }

    override fun onResume() {
        super.onResume()
        if (!isUserMuted) {
            audioEngine?.setMuted(false)
        }
        updateSystemStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(statusUpdateRunnable)
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(callEndedReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callEndedReceiver: ${e.message}")
            }
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speechRecognizer: ${e.message}")
        }
        speechRecognizer = null
        try {
            geminiLive?.disconnect()
            audioEngine?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio/gemini: ${e.message}")
        }
    }
}
