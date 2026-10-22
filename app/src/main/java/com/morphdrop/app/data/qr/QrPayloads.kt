package com.morphdrop.app.data.qr

/**
 * Builders for the standard QR payload formats phone scanners understand.
 * Kept as pure string functions so they can be unit tested without Android.
 */
object QrPayloads {

    fun text(value: String): String = value

    fun url(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun phone(number: String): String = "tel:${number.filter { it.isDigit() || it == '+' }}"

    fun sms(number: String, message: String): String {
        val base = "sms:${number.filter { it.isDigit() || it == '+' }}"
        return if (message.isBlank()) base else "$base?&body=${message.encodeQueryValue()}"
    }

    fun email(address: String, subject: String, body: String): String {
        val params = mutableListOf<String>()
        if (subject.isNotBlank()) params.add("subject=${subject.encodeQueryValue()}")
        if (body.isNotBlank()) params.add("body=${body.encodeQueryValue()}")
        val base = "mailto:${address.trim()}"
        return if (params.isEmpty()) base else params.joinToString("&", prefix = "$base?")
    }

    /**
     * Wi-Fi payload in the de-facto standard format:
     * `WIFI:T:WPA;S:<ssid>;P:<password>;H:false;;`
     * Special characters in values are backslash-escaped per the spec.
     */
    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String {
        val builder = StringBuilder("WIFI:")
        builder.append("T:").append(security).append(';')
        builder.append("S:").append(escapeWifiValue(ssid)).append(';')
        if (security != "nopass" && password.isNotEmpty()) {
            builder.append("P:").append(escapeWifiValue(password)).append(';')
        }
        if (hidden) builder.append("H:true;")
        builder.append(';')
        return builder.toString()
    }

    /** MECARD contact — the format Android and iOS scanners map to "Add contact". */
    fun contact(name: String, phone: String, email: String): String {
        val builder = StringBuilder("MECARD:")
        if (name.isNotBlank()) builder.append("N:").append(escapeWifiValue(name)).append(';')
        if (phone.isNotBlank()) {
            builder.append("TEL:").append(phone.filter { it.isDigit() || it == '+' }).append(';')
        }
        if (email.isNotBlank()) builder.append("EMAIL:").append(escapeWifiValue(email)).append(';')
        builder.append(';')
        return builder.toString()
    }

    private fun escapeWifiValue(value: String): String =
        value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")

    private fun String.encodeQueryValue(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
