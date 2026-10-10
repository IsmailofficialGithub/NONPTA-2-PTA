package com.pta.forwarder.util

data class ParsedOtp(
    val code: String,
    val serviceName: String?
)

object OtpParser {

    private val KNOWN_SERVICES = listOf(
        "UBL", "JazzCash", "Easypaisa", "SadaPay", "NayaPay",
        "Meezan", "HBL", "MCB", "Bank Alfalah", "Faysal",
        "Allied", "Standard Chartered", "WhatsApp", "Google", "Telegram"
    )

    // Matches 4 to 8 digit codes
    private val OTP_REGEXES = listOf(
        Regex("""\b(?:otp|code|pin|password|verification)\s*(?:is|:|-)?\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b([0-9]{4,8})\s*(?:is\s*your\s*(?:otp|code|pin|verification))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b([0-9]{4,8})\b""") // Fallback: any standalone 4-8 digit number
    )

    fun extractOtp(text: String): ParsedOtp? {
        if (text.isBlank()) return null

        // 1. Detect service name
        var detectedService: String? = null
        for (service in KNOWN_SERVICES) {
            if (text.contains(service, ignoreCase = true)) {
                detectedService = service
                break
            }
        }

        // 2. Extract code
        for (regex in OTP_REGEXES) {
            val match = regex.find(text)
            if (match != null) {
                val code = match.groupValues.getOrNull(1) ?: match.value
                // Validate it's not a common year (e.g. 2024-2026) if it's 4 digits and no keyword matched
                if (code.length == 4 && (code.toIntOrNull() in 2020..2030) && !text.contains("otp", true) && !text.contains("code", true)) {
                    continue
                }
                return ParsedOtp(code = code, serviceName = detectedService)
            }
        }

        return null
    }
}
