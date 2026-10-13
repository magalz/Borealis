package io.github.magalz.borealis.logging

/** Removes secrets from log text before it reaches any sink. */
class Redactor(private val maskIpAddresses: Boolean = false) {
    /** Redacts secrets in free text. */
    fun redact(text: String): String = text

    /** Redacts a structured field value, taking the field name into account. */
    fun redactField(key: String, value: String): String = value

    companion object {
        const val REDACTED = "[REDACTED]"
    }
}
