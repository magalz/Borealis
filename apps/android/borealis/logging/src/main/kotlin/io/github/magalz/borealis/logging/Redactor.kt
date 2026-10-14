package io.github.magalz.borealis.logging

/**
 * Removes secrets from log text before it reaches any sink:
 * - pairing and launch secrets that upstream logs (salt, challenges, pairing secrets, certificates,
 *   `rikey`, in URLs, XML and URL-encoded form),
 * - labelled pairing PINs (4-8 digits), password/secret/token/API-key values, `Authorization` and
 *   `Cookie` headers, PEM blocks and long hex/base64 values in cert/key/signature fields.
 *
 * IP addresses are only masked when [maskIpAddresses] is on. Input is capped at a maximum length
 * first, so hostile or huge strings cannot make redaction expensive.
 */
class Redactor(private val maskIpAddresses: Boolean = false) {
    /** Redacts secrets in free text; text longer than [maxLength] is cut and marked. */
    fun redact(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (text.length <= maxLength) return redactAll(text)
        val dropped = text.length - maxLength
        return redactAll(text.substring(0, maxLength)) + "...[truncated $dropped chars]"
    }

    /** Redacts a structured field value, taking the field name into account. */
    fun redactField(key: String, value: String): String {
        if (SENSITIVE_FIELD.matches(key)) return REDACTED
        // Redact "key=value" so key-labelled rules (cert=, key=, signature=) also apply to field
        // values.
        val redacted = redact("$key=$value")
        return if (redacted.startsWith("$key=")) redacted.substring(key.length + 1) else redacted
    }

    private fun redactAll(text: String): String {
        var result = text
        result = PEM_BLOCK.replace(result, REDACTED)
        result = PEM_UNTERMINATED.replace(result, REDACTED)
        result = XML_SECRET.replace(result) { it.groupValues[1] + REDACTED }
        result = AUTHORIZATION.replace(result) { it.groupValues[1] + REDACTED }
        result = COOKIE.replace(result) { it.groupValues[1] + REDACTED }
        result = BEARER.replace(result) { it.groupValues[1] + REDACTED }
        result = GENERIC_SECRET.replace(result) { it.groupValues[1] + it.groupValues[2] + REDACTED }
        result = PAIRING_PARAM.replace(result) { it.groupValues[1] + REDACTED }
        result = PIN.replace(result) { it.groupValues[1] + REDACTED }
        result = LONG_SECRET.replace(result) { it.groupValues[1] + it.groupValues[2] + REDACTED }
        if (maskIpAddresses) {
            result = IPV4.replace(result, IP_MASK)
            result = IPV6.replace(result, IP_MASK)
        }
        return result
    }

    companion object {
        const val REDACTED = "[REDACTED]"
        const val IP_MASK = "[IP]"
        const val DEFAULT_MAX_LENGTH = 16 * 1024

        // Names (request parameters / XML elements) whose values are always secret.
        private const val PAIRING_NAMES =
            "salt|clientchallenge|serverchallengeresp|clientpairingsecret|pairingsecret|" +
                "challengeresponse|plaincert|clientcert|rikey|ri_key"

        private val PEM_BLOCK =
            Regex("""-----BEGIN [A-Z0-9 ]+-----[\s\S]*?-----END [A-Z0-9 ]+-----""")
        private val PEM_UNTERMINATED = Regex("""-----BEGIN [A-Z0-9 ]+-----[\s\S]*""")
        private val XML_SECRET = Regex("""(?i)(<(?:$PAIRING_NAMES)>)[^<]*""")
        private val AUTHORIZATION =
            Regex(
                """(?i)(\bauthorization["']?\s*[=:]\s*["']?(?:(?:basic|bearer|digest)\s+)?)[^\s"',;&]+"""
            )
        private val COOKIE = Regex("""(?i)(\b(?:set-)?cookie["']?\s*[=:]\s*)[^\r\n]+""")
        private val BEARER = Regex("""(?i)(\bBearer\s+)[A-Za-z0-9._~+/=-]+""")
        private val GENERIC_SECRET =
            Regex(
                """(?i)\b([\w-]{0,64}(?:token|password|passwd|pwd|secret|api[_-]?key)(?![\w-]))""" +
                    """(["']?\s*[=:]\s*["']?)[^\s"',;&]+"""
            )
        private val PAIRING_PARAM =
            Regex(
                """(?i)(?<![A-Za-z])((?:$PAIRING_NAMES)(?:%3D|["']?\s*[=:]\s*["']?))[A-Za-z0-9+/_=.-]+"""
            )
        private val PIN =
            Regex(
                """(?<![A-Za-z0-9])""" +
                    """((?:[\w-]{0,32}[_-]|[A-Za-z][A-Za-z0-9]{0,31}(?=Pin(?![A-Za-z0-9])))?""" +
                    """(?i:pin)(?:[_-]?(?i:code))?(?![A-Za-z0-9])""" +
                    """["']?(?:\s*[=:]\s*|\s+(?:is\s+)?)["']?)\d{4,8}(?![A-Za-z0-9])"""
            )
        private val LONG_SECRET =
            Regex(
                """(?i)\b([\w-]{0,64}(?:cert|key|signature)[\w-]{0,64})\b(["']?\s*[=:]\s*["']?)""" +
                    """(?:[A-Fa-f0-9]{32,}|[A-Za-z0-9+/_-]{40,}={0,2})"""
            )
        private val SENSITIVE_FIELD =
            Regex(
                """(?i)[\w.-]{0,64}(?:pin|pin_?code|token|secret|password|passwd|pwd|api[_-]?key|""" +
                    """authorization|cookie|$PAIRING_NAMES)"""
            )

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
