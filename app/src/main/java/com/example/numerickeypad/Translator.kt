package com.example.numerickeypad

/**
 * Lightweight runtime string translator with English and Slovenian.
 * Usage: Translator.setLanguage(Language.SL); Translator.t("Connect HID Device")
 * For dynamic strings, Translator tries simple pattern detection (e.g., "Connected to X").
 */

enum class Language { EN, SL }

object Translator {
    private var current: Language = Language.EN

    fun setLanguage(lang: Language) { current = lang }
    fun getLanguage(): Language = current

    fun t(text: String): String = when (current) {
        Language.EN -> text
        Language.SL -> translateTextToSlovenian(text)
    }
}

fun translateTextToSlovenian(text: String): String {
    // Exact translations for common UI strings
    val exact = mapOf(
        // Buttons and labels
        "Connect HID Device" to "Poveži HID napravo",
        "Disconnect" to "Prekini povezavo",
        "Disconnected" to "Povezava prekinjena",
        "TRACKPAD" to "MIŠKA",
        "NUM LOCK" to "NUM LOCK",
        "▲ UP" to "▲ GOR",
        "▼ DOWN" to "▼ DOL",
        "TRACKPAD MODE" to "MIŠKA",
        "LEFT" to "LEVI",
        "RIGHT" to "DESNI",
        "Close Trackpad" to "Zapri miško",
        // Status / messages
        "Not connected" to "Ni povezave",
        "Failed to connect" to "Povezava ni uspela",
        "Device is now discoverable" to "Naprava je zdaj vidna",
        "Permissions required to continue" to "Za nadaljevanje so potrebna dovoljenja",
        "Select Device" to "Izberite napravo",
        "No paired devices" to "Ni seznanjenih naprav",
        "Pair your computer with this phone first in Bluetooth settings, then try again." to "Najprej seznanite računalnik s telefonom v nastavitvah Bluetooth, nato poskusite znova.",
        "Open Settings" to "Odpri nastavitve",
        "Cancel" to "Prekliči",
        "HID Device registered successfully" to "HID naprava je bila uspešno registrirana",
        "HID registered. Ready to connect." to "HID registrirana. Pripravljeno za povezavo.",
        "HID Device unregistered" to "HID naprava ni več povezana",
        "HID unregistered" to "HID ni registrirana",
        "Ensure Num Lock is ON on the host!" to "Preverite, da je Num Lock na računalniku vklopljen!",
        "ready" to "pripravljeno",
        "not-ready" to "ni pripravljeno",
        "connected" to "povezano",
        "disconnected" to "brez povezave",
        "Settings" to "Nastavitve",
        "Language:" to "Jezik:",
        "Numpad" to "Številčnica",
        "Cannot send, please connect device" to "Pošiljanje ni mogoče, prosim povežite napravo",
        "Language updated" to "Jezik posodobljen",
        "About" to "Informacije",
        "Interface" to "Vmesnik"
    )

    exact[text]?.let { return it }

    // Simple dynamic patterns
    when {
        text.startsWith("Connected to ") -> {
            val name = text.removePrefix("Connected to ")
            return "Povezano z $name"
        }
        text.startsWith("Connecting to ") -> {
            val name = text.removePrefix("Connecting to ").removeSuffix("...").removeSuffix("…")
            return "Povezovanje z $name…"
        }
        text.startsWith("Disconnected from ") -> {
            val name = text.removePrefix("Disconnected from ")
            return "Povezava z $name je prekinjena"
        }
        text.startsWith("Cannot send ") -> {
            val (ready, conn) = text.removePrefix("Cannot send ").split(" / ")
            return "Pošiljanje ni mogoče $ready / $conn"
        }
        text.startsWith("Scheduling reconnect attempt #") -> {
            // Example: Scheduling reconnect attempt #2 in 5000ms
            return text
                .replace("Scheduling reconnect attempt #", "Poizkus ponovnega povezovanja št. ")
                .replace(" in ", " v ")
        }
    }

    // Fallback: return original if unknown
    return text
}