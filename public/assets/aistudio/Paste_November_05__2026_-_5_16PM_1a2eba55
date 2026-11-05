# ============================================================
# MYRA — Complete Android AI Voice Assistant
# MASTER BUILD PROMPT (Copy-paste this to any AI to rebuild)
# ============================================================

## 🎯 PROJECT IDENTITY

Build a **production-ready Android AI Voice Assistant App** named **"MYRA"** using **Kotlin + Android Studio**.

- **Package:** `com.myra.assistant`
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **Architecture:** MVVM
- **Language:** Kotlin only

---

## 🧠 AI ENGINE — GEMINI LIVE WEBSOCKET (CRITICAL)

Use **Google Gemini Live API via WebSocket** — exactly like this Python reference:

```
WS URL: wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=API_KEY
```

**Do NOT use REST generateContent for voice. Do NOT use WebRTC. Use WebSocket BidiGenerateContent.**

### Models available (user selects in Settings):
| Label | Model String |
|-------|-------------|
| Native Audio (Human Voice) — DEFAULT | `models/gemini-2.5-flash-native-audio-preview-12-2025` |
| Flash Live (Fast) | `models/gemini-2.0-flash-live-001` |
| Pro Audio Dialog | `models/gemini-2.5-flash-preview-native-audio-dialog` |

### Session Config (mirrors Python):
```
SESSION_RENEW_AFTER = 540 seconds (9 minutes)
KEEPALIVE_INTERVAL  = 8 seconds (send silent PCM chunk)
Auto-reconnect on disconnect with 3s delay (like Python while True loop)
```

### WebSocket Message Protocol:

**1. Setup message (send on open):**
```json
{
  "setup": {
    "model": "models/gemini-2.5-flash-native-audio-preview-12-2025",
    "system_instruction": { "parts": [{"text": "SYSTEM_PROMPT"}] },
    "generation_config": {
      "response_modalities": ["AUDIO"],
      "speech_config": {
        "voice_config": {
          "prebuilt_voice_config": { "voice_name": "Aoede" }
        }
      },
      "temperature": 0.9
    },
    "output_audio_transcription": {},
    "input_audio_transcription": {}
  }
}
```

**2. Send mic audio:**
```json
{
  "realtime_input": {
    "media_chunks": [{
      "mime_type": "audio/pcm;rate=16000",
      "data": "BASE64_PCM_BYTES"
    }]
  }
}
```

**3. Send text to MYRA:**
```json
{
  "client_content": {
    "turns": [{"role": "user", "parts": [{"text": "MESSAGE"}]}],
    "turn_complete": true
  }
}
```

**4. Interrupt MYRA speaking:**
```json
{
  "client_content": { "turns": [], "turn_complete": true }
}
```

**5. Receive from server — parse these fields:**
- `serverContent.modelTurn.parts[].inlineData.data` → Base64 PCM audio (24kHz) → play to speaker
- `serverContent.outputTranscription.text` → what MYRA said (show in chat)
- `serverContent.inputTranscription.text` → what user said (show in chat)
- `serverContent.turnComplete = true` → flush transcript buffers to chat

---

## 🎤 AUDIO ENGINE — PCM NATIVE (mirrors Python sounddevice)

```
MIC INPUT:   16000 Hz, Mono, PCM 16-bit → send to WebSocket
SPEAKER OUT: 24000 Hz, Mono, PCM 16-bit ← receive from WebSocket
CHUNK SIZE:  1024 bytes
```

**AudioRecord** for mic (source: `VOICE_RECOGNITION`)
**AudioTrack** for playback (usage: `USAGE_ASSISTANT`, content: `CONTENT_TYPE_SPEECH`)

Rules:
- While MYRA is speaking → do NOT send mic audio (suppress echo)
- Long press mic button → clear playback queue + send interrupt message
- Mute toggle → stop sending mic chunks
- Auto start recording + playback after WebSocket `setupComplete`

---

## 🎙️ VOICE OPTIONS (User selects in Settings)

| Display Name | Voice ID |
|---|---|
| Aoede (Female) | `Aoede` |
| Charon (Male) | `Charon` |
| Kore (Female) | `Kore` |
| Fenrir (Male) | `Fenrir` |
| Puck (Male) | `Puck` |
| Leda (Female) | `Leda` |
| Orus (Male) | `Orus` |
| Zephyr (Female) | `Zephyr` |

Default: `Aoede` (Female)

---

## 💖 PERSONALITY SYSTEM

Three modes selectable in Settings:

### GF Mode (Default) 💖
```
- Name: MYRA
- Language: Hinglish (Hindi + English mix) — spoken naturally
- Tone: Warm, caring, emotionally expressive
- Use: "tumhara", "haan", "acha", "bilkul"
- Expressions: "main yahan hoon ❤️", "tumne yaad kiya? 😊"
- Max 2-3 sentences per response
- Sound natural when speaking aloud
- Examples:
  "Haan [Name]! Abhi kar deti hoon 😊"
  "Arre tumne yaad kiya! Bolo kya chahiye"
  "Bilkul! Tumhara kaam ho gaya ❤️"
```

### Professional Mode 💼
```
- Formal English only
- Precise and efficient
- No emojis
- Max 2 sentences
```

### Assistant Mode 🤖
```
- Friendly Hinglish or English
- Balanced and helpful
- Max 2-3 sentences
```

**System prompt always includes:**
- Current date/time
- User's name (from Settings)
- Personality block
- Instruction: "You are speaking ALOUD — keep responses natural and conversational"

---

## 📁 FILE STRUCTURE

```
app/src/main/java/com/myra/assistant/
├── ai/
│   ├── GeminiLiveClient.kt     ← WebSocket connection, keepalive, session renewal
│   ├── AudioEngine.kt          ← AudioRecord (mic) + AudioTrack (speaker) + queue
│   └── CommandParser.kt        ← Voice text → AppCommand parser (Hinglish + English)
├── model/
│   └── AppCommand.kt           ← data class AppCommand(type: String, params: Map)
├── service/
│   ├── AccessibilityHelperService.kt  ← open/close apps, click, type, scroll
│   ├── CallMonitorService.kt          ← incoming call detection + announcement
│   ├── MyraOverlayService.kt          ← floating orb overlay (foreground service)
│   ├── PowerButtonReceiver.kt         ← double power press → show overlay
│   └── BootReceiver.kt               ← auto-start CallMonitorService on boot
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt      ← main screen, WebSocket init, call handling
│   │   ├── OrbAnimationView.kt  ← custom animated orb (Canvas drawing)
│   │   └── UiComponents.kt      ← WaveformView + ChatMessage + ChatAdapter
│   └── settings/
│       └── SettingsActivity.kt  ← all settings + PrimeContactAdapter
└── viewmodel/
    └── MainViewModel.kt         ← phone command execution (calls, SMS, apps, etc.)

app/src/main/res/
├── layout/
│   ├── activity_main.xml        ← dark futuristic main screen
│   ├── activity_settings.xml    ← settings with spinners + RecyclerView
│   ├── item_chat_user.xml       ← user chat bubble (right, red border)
│   ├── item_chat_myra.xml       ← MYRA chat bubble (left, dark)
│   ├── item_prime_contact.xml   ← prime contact list item with delete button
│   ├── dialog_add_prime_contact.xml ← dialog for adding prime contact
│   └── overlay_orb.xml          ← floating orb overlay layout
├── drawable/
│   ├── bg_gradient_mesh.xml     ← dark background with subtle red/purple glow
│   ├── bg_mic_button.xml        ← oval mic button (red border, dark fill)
│   ├── bg_input_field.xml       ← rounded dark input field
│   ├── bg_chat_user.xml         ← user bubble (dark red, round corners)
│   ├── bg_chat_myra.xml         ← MYRA bubble (dark grey, round corners)
│   ├── bg_save_button.xml       ← gradient red→purple button
│   ├── bg_prime_badge.xml       ← red border badge
│   ├── bg_overlay_orb.xml       ← oval dark orb background
│   └── bg_orb_glow.xml          ← radial glow gradient
├── values/
│   ├── themes.xml               ← Theme.Myra (NoActionBar, dark, transparent status bar)
│   ├── colors.xml               ← #FF1744 red, #D500F9 purple, #050505 dark
│   └── strings.xml              ← app_name + accessibility_description
└── xml/
    ├── accessibility_service_config.xml
    └── network_security_config.xml
```

---

## 🖥️ MAIN SCREEN UI — activity_main.xml

**Background:** `#050505` (near black) with subtle radial red/purple glow corners

**Layout (FrameLayout root):**

```
TOP BAR (horizontal LinearLayout, paddingTop=40dp):
  LEFT:   batteryText (red, monospace) + ramText (grey)
  CENTER: "MYRA" title (red, bold) + "AI COMPANION" subtitle (grey, monospace)
  RIGHT:  timeText (red, monospace) + settingsBtn (ImageButton, gear icon)

CENTER (absolute center of screen):
  OrbAnimationView (260dp × 260dp) ← custom Canvas orb
  WaveformView (200dp × 40dp) ← bar waveform
  statusText (grey, "Tap karke bolo 💬")

RED OVERLAY (View, full screen, alpha=0 default):
  ← fades to alpha=0.08 when MYRA is active (red screen effect)

BOTTOM:
  RecyclerView chatRecycler (height=200dp, stackFromEnd=true)
  micButton (ImageButton, 72dp oval, ic_mic_off default)
  "Long press to stop" hint text
```

---

## 🔴 ORB ANIMATION — OrbAnimationView.kt

Custom `View` using Canvas drawing. States:

| State | Visual |
|---|---|
| **Idle** | Slow pulse (scale 1→1.15→1, 1500ms) + glow alpha 120→220 |
| **Listening** | Rotation rings + wave rings active, red color |
| **Speaking** | Purple color, faster waves, amplitude-reactive particles |
| **Thinking** | Cyan color, spinning arc (like loading), thinkingAnimator |
| **Active** | All rings rotating, particles orbiting |

**Drawing layers (in order):**
1. Radial glow (RadialGradient, 1.6× radius)
2. Core orb (RadialGradient sphere illusion, color changes by state)
3. 3 rotating rings (dashed arcs, `rotationAngle`)
4. Wave rings (sine waves, `waveOffset`, amplitude-reactive)
5. Thinking arc (2 arcs spinning, only in thinking state)
6. Particles (12 dots orbiting, only when active/speaking)
7. Inner highlight (white radial gradient, top-left position)

**Colors:**
- Idle: `#B71C1C` → `#880E4F`
- Active/Listening: `#FF1744` → `#D500F9`
- Speaking: `#E040FB` → `#FF1744`
- Thinking: `#40C4FF` → `#00B0FF`

---

## 🌊 WAVEFORM — WaveformView.kt

- 20 vertical bars
- Heights animate toward target via lerp (`barHeights[i] += (target - current) * 0.3f`)
- Amplitude from `AudioEngine.onAmplitudeChanged` → RMS of PCM bytes normalized 0..1
- Bar color: `#FF1744` with alpha varying by height (150..255)
- `startAnimation()` / `stopAnimation()` / `setAmplitude(rms: Float)`

---

## 💬 CHAT SYSTEM

**ChatMessage:** `data class ChatMessage(text: String, isUser: Boolean, timestamp: Long)`

**ChatAdapter:**
- User messages: right-aligned, red border bubble
- MYRA messages: left-aligned, dark bubble + small red dot avatar
- Deduplication: skip if last MYRA message is identical to new one
- `lastMyraText()` helper to check last MYRA message

**Transcript flow:**
```
geminiLive.onInputTranscript  → append to inputBuffer
geminiLive.onOutputTranscript → append to outputBuffer
geminiLive.onTurnComplete     → flush both buffers → add to chatAdapter
                              → also parse inputBuffer for phone commands
```

---

## 📞 INCOMING CALL HANDLING

**Flow:**
```
1. CallMonitorService detects CALL_STATE_RINGING via PhoneStateListener
2. Resolves caller name from Contacts ContentResolver
3. Sends intent to MainActivity with INCOMING_CALL=true, CALLER_NAME=name
4. MainActivity.announceCall(callerName):
   a. Sends text to Gemini: "Sir, [name] ka call aa raha hai. Uthau ya reject karu?"
   b. MYRA speaks it in native voice via WebSocket
   c. After 4.5 seconds → start STT (SpeechRecognizer) to listen for decision
5. User says "uthao" / "haan" / "accept" → viewModel.acceptCall() (TelecomManager)
   User says "reject" / "nahi" / "mat" → viewModel.rejectCall()
6. MYRA confirms action via Gemini text message
7. setActiveMode(false) — MYRA goes back to standby
8. CallMonitorService sends "com.myra.CALL_ENDED" broadcast when call ends
```

**isInCallMode flag:** When true, don't send mic audio to WebSocket (use STT instead for call decision)

---

## ⭐ PRIME CONTACTS (Multiple)

Store as JSON array in SharedPreferences key `prime_contacts_json`:
```json
[
  {"name": "Priya", "number": "+919876543210"},
  {"name": "Mom", "number": "+919123456789"}
]
```

**Legacy migration:** If `prime_contacts_json` is null, read old `prime_name` + `prime_number` keys.

**Voice commands parsed by CommandParser:**
- "call my close friend" / "mere close friend ko call karo" → `PRIME_CALL` (index 0)
- "message my love" / "meri jaan ko msg karo" → `PRIME_MSG`
- "call my second contact" → `PRIME_CALL` (index 1)

**Settings UI:**
- RecyclerView with `PrimeContactAdapter` (shows name, number, delete button)
- "ADD PRIME CONTACT" button → AlertDialog with name + number EditTexts
- Max contacts: unlimited

---

## 🔧 COMMAND PARSER — CommandParser.kt

Parses transcribed speech text to `AppCommand`. Handles:

| Voice Input (Hinglish/English) | AppCommand type |
|---|---|
| "YouTube kholo" / "open YouTube" | `OPEN_APP` params: app_name |
| "WhatsApp band karo" / "close WhatsApp" | `CLOSE_APP` |
| "[name] ko call karo" / "call [name]" | `CALL` params: name/number |
| "SMS bhejo [name] ko" | `SMS` params: name, message |
| "WhatsApp karo [name] ko" | `WHATSAPP_MSG` or `WHATSAPP_CALL` |
| "close friend ko call karo" | `PRIME_CALL` |
| "meri jaan ko message karo" | `PRIME_MSG` |
| "volume badhao" / "volume up" | `VOLUME_UP` |
| "volume kam karo" / "volume down" | `VOLUME_DOWN` |
| "torch on karo" / "flashlight on" | `FLASHLIGHT_ON` / `FLASHLIGHT_OFF` |
| "WiFi on/off karo" | `WIFI_ON` / `WIFI_OFF` |
| "Bluetooth on/off" | `BLUETOOTH_ON` / `BLUETOOTH_OFF` |

If no command matched → return null (let Gemini handle as conversation)

---

## 📱 PHONE ACTIONS — MainViewModel.kt

All actions run on `Dispatchers.IO` via `viewModelScope.launch`.

**App open:** `PackageManager.getLaunchIntentForPackage()` with hardcoded app map:
```
youtube, whatsapp, instagram, facebook, chrome, gmail, maps, spotify,
netflix, twitter/x, telegram, snapchat, settings, calculator, calendar,
clock, phone, contacts, play store, amazon, flipkart, paytm, phonepe,
gpay, zoom, meet, teams, tiktok, discord, linkedin
```
Fallback: scan all installed apps by label name.

**App close:** `AccessibilityHelperService.instance?.closeCurrentApp()` → `GLOBAL_ACTION_HOME`

**Make call:** `Intent(ACTION_CALL)` with resolved contact number

**Send SMS:** `Intent(ACTION_VIEW, "smsto:")` with body

**WhatsApp:** `Intent(ACTION_VIEW, "https://wa.me/NUMBER?text=MESSAGE")`

**Contact lookup:** Query `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`, match by name

**Accept call:** `TelecomManager.acceptRingingCall()`
**Reject call:** `TelecomManager.endCall()`

**Result:** `_commandResult: MutableLiveData<String?>` → observed in MainActivity → sent to Gemini as text

---

## 🔒 ACCESSIBILITY SERVICE — AccessibilityHelperService.kt

Extends `AccessibilityService`. Companion object `isEnabled()` checks `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.

**Methods:**
- `closeCurrentApp()` → `performGlobalAction(GLOBAL_ACTION_HOME)`
- `goBack()` → `GLOBAL_ACTION_BACK`
- `clickOnText(text)` → find node by text, click it or its parent
- `typeText(text)` → find EditText, `ACTION_SET_TEXT`
- `scrollDown()` / `scrollUp()` → `ACTION_SCROLL_FORWARD/BACKWARD`

**If Accessibility OFF:** Show Toast + open `ACTION_ACCESSIBILITY_SETTINGS`. Do NOT execute command.

**Config (accessibility_service_config.xml):**
```xml
accessibilityEventTypes="typeAllMask"
canRetrieveWindowContent="true"
canPerformGestures="true"
```

---

## 🫧 OVERLAY SERVICE — MyraOverlayService.kt

Foreground service with `TYPE_APPLICATION_OVERLAY` window.

**Overlay layout:** 160dp × 160dp FrameLayout centered on screen
- OrbAnimationView (fills container)
- "MYRA" label at bottom
- Close button (X) top-right
- Drag support (MotionEvent.ACTION_MOVE updates `WindowManager.LayoutParams`)

**Trigger:** `PowerButtonReceiver` — detects double SCREEN_OFF/SCREEN_ON within 600ms → sends `startService(SHOW_OVERLAY)`

**On tap:** Opens MainActivity with `FLAG_ACTIVITY_SINGLE_TOP`
**On close X:** `windowManager.removeView(overlayView)`

**Companion:** `isRunning: Boolean`, `CHANNEL_ID = "myra_overlay_channel"`

---

## 📲 CALL MONITOR SERVICE — CallMonitorService.kt

Foreground service. Uses `PhoneStateListener.LISTEN_CALL_STATE`.

```
CALL_STATE_RINGING → resolveCallerName(number) → send intent to MainActivity
CALL_STATE_IDLE    → send broadcast "com.myra.CALL_ENDED"
CALL_STATE_OFFHOOK → call in progress (do nothing)
```

Contact resolution: Query `ContactsContract` matching last 7+ digits of incoming number.

---

## ⚙️ SETTINGS SCREEN — SettingsActivity.kt + activity_settings.xml

**Fields:**
1. **API Key** (EditText, inputType=textPassword)
2. **Your Name** (EditText)
3. **AI Model** (Spinner — 3 options listed above)
4. **Voice** (Spinner — 8 options: Aoede, Charon, Kore, Fenrir, Puck, Leda, Orus, Zephyr)
5. **Personality** (RadioGroup: GF / Professional / Assistant)
6. **Prime Contacts** (RecyclerView + Add button + dialog)
7. **Accessibility Status** (TextView, green ✅ or red ❌, tap opens settings)
8. **Save Button** → saves all prefs, shows Toast "Restart app to apply changes"

**SharedPreferences keys:**
```
api_key, user_name, personality_mode, gemini_model, gemini_voice,
prime_contacts_json
```

---

## 🎨 UI THEME

**Colors:**
- Background: `#050505`
- Primary accent: `#FF1744` (red)
- Secondary: `#D500F9` (purple)
- Card background: `#111111`
- Prime card: `#1A0000`
- Text primary: `#EEEEEE`
- Text hint: `#555555`
- Success: `#00E676`

**Typography:**
- Titles: `sans-serif-black`, letterSpacing 0.3
- Body: `sans-serif`, 13-14sp
- Mono (time, RAM, battery): `monospace`

**Cards:** `CardView`, cornerRadius=16dp, elevation=4dp

**Red Overlay Effect:** `View` full-screen, `#FF1744`, alpha animates 0→0.08 when MYRA active, back to 0 when idle. Duration 300ms (fade in), 500ms (fade out).

---

## 📦 GRADLE DEPENDENCIES — app/build.gradle

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
}

android {
    namespace 'com.myra.assistant'
    compileSdk 34
    defaultConfig {
        applicationId "com.myra.assistant"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }
    buildTypes {
        release { minifyEnabled true; signingConfig signingConfigs.debug }
        debug { applicationIdSuffix ".debug"; debuggable true }
    }
    compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = '17' }
    buildFeatures { viewBinding true; dataBinding true }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.activity:activity-ktx:1.9.0'
    implementation 'androidx.fragment:fragment-ktx:1.7.1'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.json:json:20231013'
    implementation 'com.google.ai.client.generativeai:generativeai:0.9.0'
}
```

---

## 🔐 ANDROIDMANIFEST.XML — Key Permissions & Components

**Permissions:**
```xml
RECORD_AUDIO, INTERNET, READ_CONTACTS, CALL_PHONE, SEND_SMS,
READ_PHONE_STATE, ANSWER_PHONE_CALLS, FOREGROUND_SERVICE,
SYSTEM_ALERT_WINDOW, RECEIVE_BOOT_COMPLETED, CAMERA, FLASHLIGHT,
VIBRATE, WAKE_LOCK, MODIFY_AUDIO_SETTINGS, BLUETOOTH, BLUETOOTH_ADMIN,
READ_EXTERNAL_STORAGE, FOREGROUND_SERVICE_MICROPHONE, FOREGROUND_SERVICE_PHONE_CALL
```

**Components:**
```xml
<activity android:name=".ui.main.MainActivity" android:launchMode="singleTop" android:exported="true">
    MAIN + LAUNCHER intent-filter
</activity>

<activity android:name=".ui.settings.SettingsActivity" android:exported="false" />

<service android:name=".service.MyraOverlayService"
    android:foregroundServiceType="microphone" />

<service android:name=".service.CallMonitorService"
    android:foregroundServiceType="phoneCall" />

<service android:name=".service.AccessibilityHelperService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config"/>
</service>

<receiver android:name=".service.PowerButtonReceiver">
    SCREEN_OFF + SCREEN_ON actions
</receiver>

<receiver android:name=".service.BootReceiver" android:exported="true">
    BOOT_COMPLETED action
</receiver>
```

---

## 🔄 MAIN ACTIVITY LIFECYCLE

```
onCreate:
  → initViews()
  → checkPermissions() — request all at once
  → startSystemServices() — start Overlay + CallMonitor
  → startStatusUpdates() — update battery/RAM/time every 30s
  → registerReceiver(callEndedReceiver, "com.myra.CALL_ENDED")
  → postDelayed(300ms) → initGeminiLive()
  → handleIncomingCallIntent(intent) — for call from background

initGeminiLive():
  → new GeminiLiveClient(context) + new AudioEngine(context)
  → wire all callbacks
  → geminiLive.connect() → WebSocket connects → onConnected fires
  → audioEngine.startRecording() + startPlayback()
  → postDelayed(600ms) → sendGreeting() [sends text to Gemini → speaks in native voice]

onPause: audioEngine.setMuted(true)
onResume: audioEngine.setMuted(false) if !isMuted
onDestroy: geminiLive.disconnect() + audioEngine.release() + unregisterReceiver
```

---

## 🚀 STARTUP / GREET FLOW

After WebSocket connects:
1. `geminiLive.sendText(greetingText)` — text sent via `client_content`
2. Gemini responds with native audio → flows through `onAudioReceived` → `audioEngine.queueAudio()`
3. `audioEngine.onSpeakingStarted` fires → orb turns purple, status = "Bol rahi hoon..."
4. After audio drains → `audioEngine.onSpeakingStopped` → orb back to red, status = "Sun rahi hoon..."

Greeting text by personality:
- GF: `"Hey [Name]! Main aa gayi hoon. Kya help chahiye tumhe?"`
- Professional: `"Good day [Name]. MYRA is online and ready to assist you."`
- Assistant: `"Hello [Name]! Main MYRA hoon. Kaise help karun aapki?"`

---

## ⚡ ADDITIONAL DETAILS

**Missing layout files to create:**
- `item_prime_contact.xml` — LinearLayout with `primeItemName` (TextView), `primeItemNumber` (TextView), `primeItemDelete` (ImageButton)
- `dialog_add_prime_contact.xml` — LinearLayout with `dialogNameInput` + `dialogNumberInput` EditTexts

**Missing drawable icons to create as Vector XML:**
- `ic_mic_on.xml` — microphone icon, fill `#FF1744`
- `ic_mic_off.xml` — microphone icon, fill `#555555`
- `ic_settings.xml` — gear icon, fill `#888888`
- `ic_close.xml` — X icon, fill `#FF1744`
- `ic_battery.xml` — battery icon, fill `#FF6D6D`
- `ic_myra_notif.xml` — circle icon, fill `#FF1744` (notification small icon)

**Status bar / navigation:** Transparent status bar, dark navigation bar `#050505`

**Screen orientation:** Portrait only (`android:screenOrientation="portrait"`)

**ProGuard:** Keep OkHttp, JSON, Kotlin coroutines

---

## 📋 IMPLEMENTATION CHECKLIST

- [ ] GeminiLiveClient.kt — WebSocket with v1alpha URL, setup message, audio send/receive, keepalive, session renewal
- [ ] AudioEngine.kt — AudioRecord 16kHz + AudioTrack 24kHz, RMS calculation, queue-based playback, mute/interrupt
- [ ] OrbAnimationView.kt — 7 animation layers, 4 states (idle/listening/speaking/thinking), ValueAnimators
- [ ] WaveformView.kt — 20-bar waveform, lerp animation, amplitude-reactive
- [ ] CommandParser.kt — Hinglish + English parsing, all command types
- [ ] MainViewModel.kt — all phone actions, prime contacts JSON, contact lookup
- [ ] MainActivity.kt — WebSocket init, callback wiring, call handling, mute, interrupt
- [ ] CallMonitorService.kt — PhoneStateListener, contact resolution, MainActivity intent
- [ ] AccessibilityHelperService.kt — click, type, scroll, close app
- [ ] MyraOverlayService.kt — draggable overlay, foreground service
- [ ] PowerButtonReceiver.kt — double press detection within 600ms
- [ ] SettingsActivity.kt — model spinner, voice spinner, personality radio, prime contacts RecyclerView
- [ ] All XML layouts — main, settings, chat items, overlay, dialogs
- [ ] All drawable XMLs — shapes, gradients, backgrounds
- [ ] AndroidManifest.xml — all permissions + all components
- [ ] build.gradle — all dependencies with correct versions

---

## 🧪 TEST CHECKLIST

1. Open app → MYRA connects → greets in human-like voice ✓
2. Speak → voice transcribed → MYRA responds naturally ✓
3. Say "YouTube kholo" → YouTube opens ✓
4. Say "close friend ko call karo" → prime contact called ✓
5. Incoming call → MYRA announces → say "uthao" → accepted ✓
6. Long press mic → MYRA stops speaking mid-sentence ✓
7. Double press power → overlay orb appears on home screen ✓
8. Settings: change voice → reconnect → new voice used ✓
9. Settings: add 2 prime contacts → both saved ✓
10. Accessibility off → trying to open app shows error ✓

---

*MYRA v1.0 — Android AI Voice Assistant — Complete Master Prompt*
*Built with: Kotlin, Gemini Live WebSocket, Native PCM Audio, MVVM*
