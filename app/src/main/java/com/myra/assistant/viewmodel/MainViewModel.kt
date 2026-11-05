package com.myra.assistant.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.model.AppCommand
import com.myra.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"

        private val APP_PACKAGE_MAP = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "play store" to "com.android.vending",
            "playstore" to "com.android.vending",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "paytm" to "net.one97.paytm",
            "phonepe" to "com.phonepe.app",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            "zoom" to "us.zoom.videomeetings",
            "meet" to "com.google.android.apps.meetings",
            "teams" to "com.microsoft.teams",
            "tiktok" to "com.zhiliaoapp.musically",
            "discord" to "com.discord",
            "linkedin" to "com.linkedin.android"
        )
    }

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult

    private var isFlashlightOn = false

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            when (command.type) {
                AppCommand.OPEN_APP -> {
                    val appName = command.params["app_name"] ?: ""
                    openApp(appName)
                }
                AppCommand.CLOSE_APP -> {
                    closeApp()
                }
                AppCommand.CALL -> {
                    val target = command.params["name"] ?: ""
                    makeCall(target)
                }
                AppCommand.SMS -> {
                    val target = command.params["name"] ?: ""
                    val msg = command.params["message"] ?: ""
                    sendSms(target, msg)
                }
                AppCommand.WHATSAPP_MSG -> {
                    val target = command.params["name"] ?: ""
                    val msg = command.params["message"] ?: ""
                    sendWhatsApp(target, msg)
                }
                AppCommand.PRIME_CALL -> {
                    val index = command.params["index"]?.toIntOrNull() ?: 0
                    callPrimeContact(index)
                }
                AppCommand.PRIME_MSG -> {
                    val index = command.params["index"]?.toIntOrNull() ?: 0
                    msgPrimeContact(index)
                }
                AppCommand.VOLUME_UP -> {
                    adjustVolume(true)
                }
                AppCommand.VOLUME_DOWN -> {
                    adjustVolume(false)
                }
                AppCommand.FLASHLIGHT_ON -> {
                    toggleFlashlight(true)
                }
                AppCommand.FLASHLIGHT_OFF -> {
                    toggleFlashlight(false)
                }
                AppCommand.WIFI_ON, AppCommand.WIFI_OFF -> {
                    // Open Network Settings
                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    getApplication<Application>().startActivity(intent)
                    _commandResult.postValue("WiFi settings opened.")
                }
                AppCommand.BLUETOOTH_ON, AppCommand.BLUETOOTH_OFF -> {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    getApplication<Application>().startActivity(intent)
                    _commandResult.postValue("Bluetooth settings opened.")
                }
            }
        }
    }

    private fun openApp(appName: String) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val cleanName = appName.lowercase().trim()

        // 1. Direct package lookup
        var pkg = APP_PACKAGE_MAP[cleanName]

        // 2. Fallback search by label
        if (pkg == null) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(cleanName) || cleanName.contains(label)) {
                    pkg = app.packageName
                    break
                }
            }
        }

        if (pkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                _commandResult.postValue("App $appName opened successfully.")
                return
            }
        }

        _commandResult.postValue("Could not find app $appName on your phone.")
    }

    private fun closeApp() {
        val helper = AccessibilityHelperService.instance
        if (helper != null) {
            val success = helper.closeCurrentApp()
            if (success) {
                _commandResult.postValue("App closed successfully.")
            } else {
                _commandResult.postValue("Could not close app.")
            }
        } else {
            _commandResult.postValue("Accessibility service is OFF. Please enable MYRA in Accessibility Settings.")
        }
    }

    private fun makeCall(target: String) {
        val context = getApplication<Application>()
        val number = if (target.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            target
        } else {
            lookupContactNumber(target)
        }

        if (!number.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(intent)
                _commandResult.postValue("Calling $target at $number.")
            } else {
                _commandResult.postValue("Call permission is missing.")
            }
        } else {
            _commandResult.postValue("Could not find contact number for $target.")
        }
    }

    private fun sendSms(target: String, message: String) {
        val context = getApplication<Application>()
        val number = lookupContactNumber(target) ?: target
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            _commandResult.postValue("Opening SMS composer for $target.")
        } catch (e: Exception) {
            _commandResult.postValue("Could not open SMS: ${e.message}")
        }
    }

    private fun sendWhatsApp(target: String, message: String) {
        val context = getApplication<Application>()
        val rawNumber = lookupContactNumber(target) ?: target
        val cleanNumber = rawNumber.replace("+", "").replace(" ", "").replace("-", "")

        val url = if (cleanNumber.isNotEmpty() && cleanNumber.all { it.isDigit() }) {
            "https://wa.me/$cleanNumber?text=${Uri.encode(message)}"
        } else {
            "https://wa.me/?text=${Uri.encode(message)}"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(intent)
            _commandResult.postValue("WhatsApp opened for $target.")
        } catch (e: Exception) {
            // Try standard browser fallback
            intent.setPackage(null)
            try {
                context.startActivity(intent)
                _commandResult.postValue("WhatsApp web link opened.")
            } catch (ex: Exception) {
                _commandResult.postValue("Could not open WhatsApp: ${e.message}")
            }
        }
    }

    private fun callPrimeContact(index: Int) {
        val primeContacts = getPrimeContacts()
        if (index in primeContacts.indices) {
            val contact = primeContacts[index]
            makeCall(contact.second)
        } else if (primeContacts.isNotEmpty()) {
            makeCall(primeContacts[0].second)
        } else {
            _commandResult.postValue("No Prime Contacts found! Please add them in Settings.")
        }
    }

    private fun msgPrimeContact(index: Int) {
        val primeContacts = getPrimeContacts()
        if (index in primeContacts.indices) {
            val contact = primeContacts[index]
            sendWhatsApp(contact.second, "Hey!")
        } else if (primeContacts.isNotEmpty()) {
            sendWhatsApp(primeContacts[0].second, "Hey!")
        } else {
            _commandResult.postValue("No Prime Contacts configured.")
        }
    }

    private fun getPrimeContacts(): List<Pair<String, String>> {
        val prefs = getApplication<Application>().getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val list = mutableListOf<Pair<String, String>>()
        val json = prefs.getString("prime_contacts_json", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Pair(obj.getString("name"), obj.getString("number")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing prime contacts JSON", e)
            }
        }
        if (list.isEmpty()) {
            val name = prefs.getString("prime_name", null)
            val num = prefs.getString("prime_number", null)
            if (!name.isNullOrEmpty() && !num.isNullOrEmpty()) {
                list.add(Pair(name, num))
            }
        }
        return list
    }

    private fun lookupContactNumber(name: String): String? {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        var number: String? = null
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx != -1) {
                        number = it.getString(numIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }
        return number
    }

    private fun adjustVolume(increase: Boolean) {
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        _commandResult.postValue(if (increase) "Volume increased." else "Volume decreased.")
    }

    private fun toggleFlashlight(turnOn: Boolean) {
        val cm = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cm.cameraIdList.firstOrNull() ?: return
            cm.setTorchMode(cameraId, turnOn)
            isFlashlightOn = turnOn
            _commandResult.postValue(if (turnOn) "Flashlight turned on." else "Flashlight turned off.")
        } catch (e: Exception) {
            _commandResult.postValue("Could not toggle flashlight: ${e.message}")
        }
    }

    fun acceptCall() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS)
                    == PackageManager.PERMISSION_GRANTED) {
                    @Suppress("DEPRECATION")
                    tm?.acceptRingingCall()
                    _commandResult.postValue("Call accepted.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting call", e)
            }
        }
    }

    fun rejectCall() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS)
                    == PackageManager.PERMISSION_GRANTED) {
                    tm?.endCall()
                    _commandResult.postValue("Call rejected.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting call", e)
            }
        }
    }
}
