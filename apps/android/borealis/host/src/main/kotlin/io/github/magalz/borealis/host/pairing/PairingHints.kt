package io.github.magalz.borealis.host.pairing

import android.content.Context
import android.os.Build
import io.github.magalz.borealis.host.R

/**
 * What the user needs to know to pair with and unpair from LuminalShine: the Web UI address, a
 * device name the host does not treat specially, and the texts that say so (host contract 4.1 and
 * 10.2).
 */
object PairingHints {
    private const val MAX_HTTP_PORT = 65534
    private const val FALLBACK_NAME = "Borealis"
    private val FORBIDDEN_NAME_PARTS = Regex("xbox|webos", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
    private const val UNRESERVED = "-._~"
    private const val INVALID_HOST_CHARACTERS = "/?#@[]"

    /**
     * The Web UI address: HTTPS on the HTTP [httpPort] plus one. IPv6 literals are bracketed once
     * and a zone id is percent-encoded as RFC 6874 requires. Null for a port outside 1..65534 or an
     * address that is blank or cannot be part of a URL authority.
     */
    @JvmStatic
    fun webUiUrl(address: String, httpPort: Int): String? {
        val host = address.trim().removePrefix("[").removeSuffix("]")
        val usable =
            httpPort in 1..MAX_HTTP_PORT &&
                host.isNotEmpty() &&
                host.none { it.isWhitespace() || it in INVALID_HOST_CHARACTERS }
        return if (usable) "https://${authority(host)}:${httpPort + 1}" else null
    }

    private fun authority(host: String): String {
        if (':' !in host) return host
        val address = host.substringBefore('%')
        val zone = host.substringAfter('%', "")
        return if ('%' in host) "[$address%25${encodeZone(zone)}]" else "[$address]"
    }

    private fun encodeZone(zone: String): String =
        zone.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val char = (byte.toInt() and 0xFF).toChar()
            if (isUnreserved(char)) char.toString() else "%%%02X".format(byte)
        }

    private fun isUnreserved(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in UNRESERVED

    /**
     * A device name derived from the device [model] without `xbox` or `webos` in any case, which
     * make the host treat the client as a strict-first-frame client. Falls back to `Borealis`.
     */
    @JvmStatic
    fun suggestedDeviceName(model: String?): String {
        var name = model.orEmpty()
        // Removing a word can join the letters around it into a new one: repeat until stable.
        while (FORBIDDEN_NAME_PARTS.containsMatchIn(name)) {
            name = FORBIDDEN_NAME_PARTS.replace(name, "")
        }
        return name.replace(WHITESPACE, " ").trim().ifEmpty { FALLBACK_NAME }
    }

    /** The PIN dialog hint: where to type the PIN and which device name to use. */
    @JvmStatic
    fun pinHint(context: Context, address: String, httpPort: Int): String =
        pinHint(address, httpPort, Build.MODEL, contextStrings(context))

    /** The message shown when the host did not confirm the unpair request. */
    @JvmStatic
    fun unpairHint(context: Context, address: String, httpPort: Int): String =
        unpairHint(address, httpPort, contextStrings(context))

    internal fun pinHint(
        address: String,
        httpPort: Int,
        model: String?,
        getString: (Int, List<Any>) -> String,
    ): String =
        getString(
            R.string.pair_pin_hint,
            listOf(webUi(address, httpPort, getString), suggestedDeviceName(model)),
        )

    internal fun unpairHint(
        address: String,
        httpPort: Int,
        getString: (Int, List<Any>) -> String,
    ): String = getString(R.string.unpair_webui_hint, listOf(webUi(address, httpPort, getString)))

    private fun webUi(address: String, httpPort: Int, getString: (Int, List<Any>) -> String) =
        webUiUrl(address, httpPort) ?: getString(R.string.webui_url_unknown, emptyList())

    private fun contextStrings(context: Context): (Int, List<Any>) -> String = { id, args ->
        context.getString(id, *args.toTypedArray())
    }
}
