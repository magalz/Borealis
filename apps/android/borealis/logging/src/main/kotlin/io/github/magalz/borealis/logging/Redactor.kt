package io.github.magalz.borealis.logging

/**
 * Removes secrets from log text before it reaches any sink: labelled pairing PINs, bearer and API
 * tokens, PEM blocks and long hex/base64 values in cert/key/signature fields. IP addresses are only
 * masked when [maskIpAddresses] is on.
 */
class Redactor(private val maskIpAddresses: Boolean = false) {
    /** Redacts secrets in free text. */
    @Suppress("UnusedParameter")
    fun redact(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        var result = text
        result = PEM_BLOCK.replace(result, REDACTED)
        result = PEM_UNTERMINATED.replace(result, REDACTED)
        result = BEARER.replace(result) { it.groupValues[1] + REDACTED }
        result = TOKEN.replace(result) { it.groupValues[1] + it.groupValues[2] + REDACTED }
        result = PIN.replace(result) { it.groupValues[1] + REDACTED }
        result = LONG_SECRET.replace(result) { it.groupValues[1] + it.groupValues[2] + REDACTED }
        if (maskIpAddresses) {
            result = IPV4.replace(result, IP_MASK)
            result = IPV6.replace(result, IP_MASK)
        }
        return result
    }

    /** Redacts a structured field value, taking the field name into account. */
    fun redactField(key: String, value: String): String {
        if (SENSITIVE_FIELD.matches(key)) return REDACTED
        // Redact "key=value" so key-labelled rules (cert=, key=, signature=) also apply to field
        // values.
        val redacted = redact("$key=$value")
        return if (redacted.startsWith("$key=")) redacted.substring(key.length + 1) else redacted
    }

    companion object {
        const val REDACTED = "[REDACTED]"
        const val IP_MASK = "[IP]"
        const val DEFAULT_MAX_LENGTH = 16 * 1024

        private val PEM_BLOCK =
            Regex("""-----BEGIN [A-Z0-9 ]+-----[\s\S]*?-----END [A-Z0-9 ]+-----""")
        private val PEM_UNTERMINATED = Regex("""-----BEGIN [A-Z0-9 ]+-----[\s\S]*""")
        private val BEARER = Regex("""(?i)(\bBearer\s+)[A-Za-z0-9._~+/=-]+""")
        private val TOKEN = Regex("""(?i)\b([\w-]*token)\b(["']?\s*[=:]\s*["']?)[^\s"',;&]+""")
        private val PIN = Regex("""(?i)(\bpin\b["']?\s*[=:]?\s*["']?)\d{4}\b""")
        private val LONG_SECRET =
            Regex(
                """(?i)\b([\w-]*(?:cert|key|signature)[\w-]*)\b(["']?\s*[=:]\s*["']?)""" +
                    """(?:[A-Fa-f0-9]{32,}|[A-Za-z0-9+/_-]{40,}={0,2})"""
            )
        private val SENSITIVE_FIELD =
            Regex("""(?i)pin|authorization|[\w.-]*(?:token|secret|password|passwd)""")

        private val IPV4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?!\d)""")
        private const val HEXTET = "[0-9A-Fa-f]{1,4}"
        private val IPV6 =
            Regex(
                """(?<![\w:])(?:""" +
                    """(?:$HEXTET:){7}$HEXTET""" +
                    """|(?:$HEXTET:){1,7}:(?:$HEXTET(?::$HEXTET){0,6})?""" +
                    """|::$HEXTET(?::$HEXTET){0,6}""" +
                    """)(?![\w:])"""
            )
    }
}
