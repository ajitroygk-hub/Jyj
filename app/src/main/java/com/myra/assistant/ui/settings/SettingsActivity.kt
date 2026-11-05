package com.myra.assistant.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.service.AccessibilityHelperService
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var apiKeyInput: EditText
    private lateinit var userNameInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var personalityRadioGroup: RadioGroup
    private lateinit var primeContactsRecycler: RecyclerView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var accessibilityStatusRow: View
    private lateinit var saveSettingsBtn: Button
    private lateinit var addPrimeContactBtn: Button
    private lateinit var backBtn: ImageButton

    private lateinit var primeAdapter: PrimeContactAdapter
    private val primeContactsList = mutableListOf<PrimeContact>()

    data class PrimeContact(val name: String, val number: String)

    private val modelsList = listOf(
        Pair("Native Audio (Human Voice) — DEFAULT", GeminiLiveClient.MODEL_NATIVE_AUDIO),
        Pair("Flash Live (Fast)", GeminiLiveClient.MODEL_FLASH_LIVE),
        Pair("Pro Audio Dialog", GeminiLiveClient.MODEL_PRO_DIALOG)
    )

    private val voicesList = listOf(
        Pair("Aoede (Female)", GeminiLiveClient.VOICE_AOEDE),
        Pair("Charon (Male)", GeminiLiveClient.VOICE_CHARON),
        Pair("Kore (Female)", GeminiLiveClient.VOICE_KORE),
        Pair("Fenrir (Male)", GeminiLiveClient.VOICE_FENRIR),
        Pair("Puck (Male)", GeminiLiveClient.VOICE_PUCK),
        Pair("Leda (Female)", GeminiLiveClient.VOICE_LEDA),
        Pair("Orus (Male)", GeminiLiveClient.VOICE_ORUS),
        Pair("Zephyr (Female)", GeminiLiveClient.VOICE_ZEPHYR)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        initViews()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun initViews() {
        backBtn = findViewById(R.id.backBtn)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        userNameInput = findViewById(R.id.userNameInput)
        modelSpinner = findViewById(R.id.modelSpinner)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        personalityRadioGroup = findViewById(R.id.personalityRadioGroup)
        primeContactsRecycler = findViewById(R.id.primeContactsRecycler)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        accessibilityStatusRow = findViewById(R.id.accessibilityStatusRow)
        saveSettingsBtn = findViewById(R.id.saveSettingsBtn)
        addPrimeContactBtn = findViewById(R.id.addPrimeContactBtn)

        backBtn.setOnClickListener { finish() }

        // Setup Spinners
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelsList.map { it.first })
        modelSpinner.adapter = modelAdapter

        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voicesList.map { it.first })
        voiceSpinner.adapter = voiceAdapter

        // Setup Prime Contacts Recycler
        primeAdapter = PrimeContactAdapter(primeContactsList) { position ->
            primeContactsList.removeAt(position)
            primeAdapter.notifyItemRemoved(position)
        }
        primeContactsRecycler.layoutManager = LinearLayoutManager(this)
        primeContactsRecycler.adapter = primeAdapter

        addPrimeContactBtn.setOnClickListener {
            showAddPrimeContactDialog()
        }

        accessibilityStatusRow.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        saveSettingsBtn.setOnClickListener {
            savePreferences()
        }
    }

    private fun loadPreferences() {
        apiKeyInput.setText(prefs.getString("api_key", ""))
        userNameInput.setText(prefs.getString("user_name", "Sir"))

        val savedModel = prefs.getString("gemini_model", GeminiLiveClient.MODEL_NATIVE_AUDIO)
        val modelIndex = modelsList.indexOfFirst { it.second == savedModel }.coerceAtLeast(0)
        modelSpinner.setSelection(modelIndex)

        val savedVoice = prefs.getString("gemini_voice", GeminiLiveClient.VOICE_AOEDE)
        val voiceIndex = voicesList.indexOfFirst { it.second == savedVoice }.coerceAtLeast(0)
        voiceSpinner.setSelection(voiceIndex)

        val personality = prefs.getString("personality_mode", GeminiLiveClient.PERSONALITY_GF)
        when (personality) {
            GeminiLiveClient.PERSONALITY_PROFESSIONAL -> personalityRadioGroup.check(R.id.radioProfessional)
            GeminiLiveClient.PERSONALITY_ASSISTANT -> personalityRadioGroup.check(R.id.radioAssistant)
            else -> personalityRadioGroup.check(R.id.radioGf)
        }

        // Prime Contacts (with legacy migration)
        primeContactsList.clear()
        val primeJson = prefs.getString("prime_contacts_json", null)
        if (primeJson != null) {
            try {
                val array = JSONArray(primeJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    primeContactsList.add(PrimeContact(obj.getString("name"), obj.getString("number")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Legacy fallback
            val legacyName = prefs.getString("prime_name", null)
            val legacyNumber = prefs.getString("prime_number", null)
            if (!legacyName.isNullOrEmpty() && !legacyNumber.isNullOrEmpty()) {
                primeContactsList.add(PrimeContact(legacyName, legacyNumber))
            } else {
                // Default starter contacts
                primeContactsList.add(PrimeContact("Priya", "+919876543210"))
                primeContactsList.add(PrimeContact("Mom", "+919123456789"))
            }
        }
        primeAdapter.notifyDataSetChanged()
    }

    private fun updateAccessibilityStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        if (enabled) {
            accessibilityStatusText.text = "ENABLED ✅"
            accessibilityStatusText.setTextColor(getColor(R.color.color_success))
        } else {
            accessibilityStatusText.text = "DISABLED ❌ (Tap to Enable)"
            accessibilityStatusText.setTextColor(getColor(R.color.color_danger))
        }
    }

    private fun showAddPrimeContactDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_add_prime_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.dialogNameInput)
        val numberInput = dialogView.findViewById<EditText>(R.id.dialogNumberInput)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("ADD") { _, _ ->
                val name = nameInput.text.toString().trim()
                val number = numberInput.text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    primeContactsList.add(PrimeContact(name, number))
                    primeAdapter.notifyItemInserted(primeContactsList.size - 1)
                } else {
                    Toast.makeText(this, "Please enter both name and number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun savePreferences() {
        val selectedModel = modelsList[modelSpinner.selectedItemPosition].second
        val selectedVoice = voicesList[voiceSpinner.selectedItemPosition].second
        val selectedPersonality = when (personalityRadioGroup.checkedRadioButtonId) {
            R.id.radioProfessional -> GeminiLiveClient.PERSONALITY_PROFESSIONAL
            R.id.radioAssistant -> GeminiLiveClient.PERSONALITY_ASSISTANT
            else -> GeminiLiveClient.PERSONALITY_GF
        }

        val jsonArray = JSONArray()
        for (contact in primeContactsList) {
            val obj = JSONObject().apply {
                put("name", contact.name)
                put("number", contact.number)
            }
            jsonArray.put(obj)
        }

        prefs.edit().apply {
            putString("api_key", apiKeyInput.text.toString().trim())
            putString("user_name", userNameInput.text.toString().trim())
            putString("gemini_model", selectedModel)
            putString("gemini_voice", selectedVoice)
            putString("personality_mode", selectedPersonality)
            putString("prime_contacts_json", jsonArray.toString())
            apply()
        }

        Toast.makeText(this, "Settings saved! Restart app to apply changes", Toast.LENGTH_LONG).show()
        finish()
    }

    class PrimeContactAdapter(
        private val items: List<PrimeContact>,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PrimeContactAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.primeItemName)
            val number: TextView = view.findViewById(R.id.primeItemNumber)
            val deleteBtn: ImageButton = view.findViewById(R.id.primeItemDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prime_contact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.number.text = item.number
            holder.deleteBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
