package io.github.magalz.borealis.logging

/**
 * Removes secrets from log text before it reaches any sink:
 * - pairing and launch secrets that upstream logs (salt, challenges, pairing secrets, certificates,
 *   `rikey`, in URLs, XML and URL-encoded form),
 * - labelled pairing PINs (4-8 digits), password/secret/token/API-key values, `Authorization` and
 *   `Cookie` headers, PEM blocks and long hex/base64 values in cert/key/signature fields.
 *
 * - query values of URLs, except for the allowlisted protocol parameters in [ALLOWED_QUERY_KEYS],
 *   and the credentials of a URL.
 *
 * IP addresses (IPv4 with octets of at most 255, IPv6) and host names (the host of a URL and the
 * values of `host`, `hostname`, `computer` and `computerName` keys) are only masked when
 * [maskAddresses] is on. Input is capped at a maximum length first, so hostile or huge strings
 * cannot make redaction expensive.
 */
class Redactor(maskAddresses: Boolean = false) {
    /** Address masking; may be switched while the redactor is in use and affects later calls. */
    @Volatile var maskAddresses: Boolean = maskAddresses

    /** Redacts secrets in free text; text longer than [maxLength] is cut and marked. */
    fun redact(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (text.length <= maxLength) return redactAll(text)
        val dropped = text.length - maxLength
        return redactAll(text.substring(0, maxLength)) + "...[truncated $dropped chars]"
    }

    /** Redacts a structured field value, taking the field name into account. */
    fun redactField(key: String, value: String): String {
        if (SENSITIVE_FIELD.matches(key)) return REDACTED
        if (maskAddresses && HOST_FIELD.matches(key)) return HOST_MASK
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
        val masking = maskAddresses
        result = URL.replace(result) { redactUrl(it, masking) }
        if (masking) {
            result = HOST_VALUE.replace(result) { it.groupValues[1] + HOST_MASK }
            result = IPV4.replace(result, IP_MASK)
            result = IPV6.replace(result, IP_MASK)
        }
        return result
    }

    private fun redactUrl(match: MatchResult, masking: Boolean): String {
        val groups = match.groups
        val credentials = if (groups[2] != null) "$REDACTED@" else ""
        val host = if (masking) HOST_MASK else groups[3]!!.value
        val query =
            QUERY_PARAM.replace(groups[4]!!.value) { param ->
                val key = param.groupValues[1]
                if (key.lowercase() in ALLOWED_QUERY_KEYS) param.value else "$key=$REDACTED"
            }
        return groups[1]!!.value + credentials + host + query
    }

    companion object {
        const val REDACTED = "[REDACTED]"
        const val IP_MASK = "[IP]"
        const val HOST_MASK = "[HOST]"
        const val DEFAULT_MAX_LENGTH = 16 * 1024

        /**
         * Non-identifying GameStream parameters whose query values stay readable in logs.
         * Everything else (`uniqueid`, `uuid`, `devicename`, `appid`, `rikey`, `rikeyid`, `salt`,
         * `clientcert` and every other pairing parameter) is redacted. Keys are compared in lower
         * case.
         */
        val ALLOWED_QUERY_KEYS =
            setOf(
                "phrase",
                "updatestate",
                "mode",
                "additionalstates",
                "sops",
                "hdrmode",
                "clienthdrcapversion",
                "clienthdrcapsupportedflagsinuint32",
                "clienthdrcapmetadataid",
                "clienthdrcapdisplaydata",
                "localaudioplaymode",
                "surroundaudioinfo",
                "remotecontrollersbitmap",
                "gcmap",
                "gcpersist",
                "corever",
                "assettype",
                "assetidx",
            )

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

        // scheme://[userinfo@]host[:port][/path][?query][#fragment]
        private val URL =
            Regex(
                """(?i)\b([a-z][a-z0-9+.-]*://)(?:([^\s/?#@"'<>]*)@)?""" +
                    """(\[[0-9a-f:.]+]|[^\s/?#:"'<>\[\]]*)([^\s"'<>]*)"""
            )
        private val QUERY_PARAM = Regex("""(?<=[?&])([^=&#\s]+)=[^&#\s]*""")
        private const val HOST_KEYS = "host|hostname|computer|computername"
        private val HOST_FIELD = Regex("""(?i)[\w.-]{0,64}(?:$HOST_KEYS)""")
        private val HOST_VALUE =
            Regex("""(?i)\b([\w.-]{0,64}(?:$HOST_KEYS)["']?\s*[=:]\s*["']?)[^\s"',;&]+""")

        private const val OCTET = """(?:25[0-5]|2[0-4]\d|1\d\d|0?\d?\d)"""
        private val IPV4 = Regex("""(?<![\d.])(?:$OCTET\.){3}$OCTET(?!\d|\.\d)""")
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
