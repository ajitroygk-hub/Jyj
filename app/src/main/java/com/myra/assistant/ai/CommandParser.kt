package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand
import java.util.Locale

object CommandParser {

    fun parse(text: String): AppCommand? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Prime Contacts Commands
        if (lower.contains("close friend") || lower.contains("mere close friend") || lower.contains("first contact")) {
            if (lower.contains("message") || lower.contains("msg") || lower.contains("sms")) {
                return AppCommand(AppCommand.PRIME_MSG, mapOf("index" to "0"))
            }
            if (lower.contains("call") || lower.contains("phone")) {
                return AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "0"))
            }
        }

        if (lower.contains("meri jaan") || lower.contains("my love") || lower.contains("jaan ko")) {
            if (lower.contains("message") || lower.contains("msg") || lower.contains("sms")) {
                return AppCommand(AppCommand.PRIME_MSG, mapOf("index" to "0"))
            }
            if (lower.contains("call") || lower.contains("phone")) {
                return AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "0"))
            }
        }

        if (lower.contains("second contact") || lower.contains("dusra contact") || lower.contains("dusre contact")) {
            if (lower.contains("message") || lower.contains("msg") || lower.contains("sms")) {
                return AppCommand(AppCommand.PRIME_MSG, mapOf("index" to "1"))
            }
            if (lower.contains("call") || lower.contains("phone")) {
                return AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "1"))
            }
        }

        // 2. Volume Commands
        if (lower.contains("volume badhao") || lower.contains("volume up") || lower.contains("awaaz badhao") || lower.contains("increase volume")) {
            return AppCommand(AppCommand.VOLUME_UP)
        }
        if (lower.contains("volume kam karo") || lower.contains("volume down") || lower.contains("awaaz kam karo") || lower.contains("decrease volume")) {
            return AppCommand(AppCommand.VOLUME_DOWN)
        }

        // 3. Flashlight / Torch Commands
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("torch jalao") || lower.contains("flash on")) {
            return AppCommand(AppCommand.FLASHLIGHT_ON)
        }
        if (lower.contains("torch off") || lower.contains("torch band") || lower.contains("flashlight off") || lower.contains("flash off")) {
            return AppCommand(AppCommand.FLASHLIGHT_OFF)
        }

        // 4. WiFi Commands
        if (lower.contains("wifi on") || lower.contains("wi-fi on") || lower.contains("wifi chalu")) {
            return AppCommand(AppCommand.WIFI_ON)
        }
        if (lower.contains("wifi off") || lower.contains("wifi band") || lower.contains("wi-fi off")) {
            return AppCommand(AppCommand.WIFI_OFF)
        }

        // 5. Bluetooth Commands
        if (lower.contains("bluetooth on") || lower.contains("bluetooth chalu")) {
            return AppCommand(AppCommand.BLUETOOTH_ON)
        }
        if (lower.contains("bluetooth off") || lower.contains("bluetooth band")) {
            return AppCommand(AppCommand.BLUETOOTH_OFF)
        }

        // 6. Close App Commands
        if (lower.contains("band karo") || lower.contains("close app") || lower.contains("band kar do")) {
            val appTarget = extractAppName(lower)
            return AppCommand(AppCommand.CLOSE_APP, if (appTarget.isNotEmpty()) mapOf("app_name" to appTarget) else emptyMap())
        }

        // 7. Open App Commands
        if (lower.startsWith("open ") || lower.contains("kholo") || lower.contains("chalu karo") || lower.startsWith("launch ")) {
            val appTarget = extractAppName(lower)
            if (appTarget.isNotEmpty()) {
                return AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to appTarget))
            }
        }

        // 8. WhatsApp Message or Call
        if (lower.contains("whatsapp karo") || lower.contains("whatsapp message") || lower.contains("whatsapp msg") || lower.contains("send whatsapp")) {
            val name = extractContactName(lower, listOf("whatsapp karo", "whatsapp message", "whatsapp msg", "send whatsapp"))
            return AppCommand(AppCommand.WHATSAPP_MSG, mapOf("name" to name, "message" to ""))
        }
        if (lower.contains("whatsapp call")) {
            val name = extractContactName(lower, listOf("whatsapp call"))
            return AppCommand(AppCommand.WHATSAPP_CALL, mapOf("name" to name))
        }

        // 9. SMS Commands
        if (lower.contains("sms bhejo") || lower.contains("send sms") || lower.contains("message bhejo") || lower.contains("send message")) {
            val name = extractContactName(lower, listOf("sms bhejo", "send sms", "message bhejo", "send message"))
            return AppCommand(AppCommand.SMS, mapOf("name" to name, "message" to ""))
        }

        // 10. Call Commands
        if (lower.contains("ko call karo") || lower.contains("ko phone lagao") || lower.startsWith("call ") || lower.contains("call lagao")) {
            val name = extractContactName(lower, listOf("ko call karo", "ko phone lagao", "call ", "call lagao"))
            if (name.isNotEmpty()) {
                return AppCommand(AppCommand.CALL, mapOf("name" to name))
            }
        }

        return null
    }

    private fun extractAppName(text: String): String {
        val knownApps = listOf(
            "youtube", "whatsapp", "instagram", "facebook", "chrome", "gmail",
            "maps", "spotify", "netflix", "twitter", "x", "telegram", "snapchat",
            "settings", "calculator", "calendar", "clock", "phone", "contacts",
            "play store", "amazon", "flipkart", "paytm", "phonepe", "gpay",
            "google pay", "zoom", "meet", "teams", "tiktok", "discord", "linkedin",
            "camera", "gallery", "photos", "browser", "files"
        )
        for (app in knownApps) {
            if (text.contains(app)) return app
        }
        // Fallback: strip command words
        var cleaned = text.replace("open", "")
            .replace("kholo", "")
            .replace("chalu karo", "")
            .replace("launch", "")
            .replace("band karo", "")
            .replace("app", "")
            .replace("ko", "")
            .trim()
        return cleaned
    }

    private fun extractContactName(text: String, keywords: List<String>): String {
        var result = text
        for (kw in keywords) {
            result = result.replace(kw, "")
        }
        result = result
            .replace("ko", "")
            .replace("par", "")
            .replace("to", "")
            .replace("please", "")
            .replace("karo", "")
            .replace("lagao", "")
            .replace("ek", "")
            .trim()
        return result
    }
}
