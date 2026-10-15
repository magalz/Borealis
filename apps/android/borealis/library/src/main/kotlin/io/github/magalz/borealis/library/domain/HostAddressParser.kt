package io.github.magalz.borealis.library.domain

/**
 * Parses what the user types in the Add host field, with the rules of the Moonlight 12.2 field: an
 * IPv4 address, a host name or an IPv6 literal (bracketed, or bare when it has no port), each with
 * an optional port.
 */
object HostAddressParser {
    sealed interface ParseResult {
        data class Valid(val address: HostAddress) : ParseResult

        data object Empty : ParseResult

        data object Invalid : ParseResult
    }

    private const val MAX_PORT = 65535

    fun parse(input: String): ParseResult {
        val text = input.trim()
        return when {
            text.isEmpty() -> ParseResult.Empty
            text.startsWith('[') -> parseBracketed(text)
            text.count { it == ':' } > 1 -> valid(text, null, ::isIpv6Literal)
            else -> parseHostAndPort(text)
        }
    }

    private fun parseBracketed(text: String): ParseResult {
        val close = text.indexOf(']')
        if (close < 0) return ParseResult.Invalid
        val host = text.substring(1, close)
        val rest = text.substring(close + 1)
        return when {
            rest.isEmpty() -> valid(host, null, ::isIpv6Literal)
            rest.startsWith(':') -> valid(host, rest.substring(1), ::isIpv6Literal)
            else -> ParseResult.Invalid
        }
    }

    private fun parseHostAndPort(text: String): ParseResult {
        val colon = text.indexOf(':')
        return if (colon < 0) {
            valid(text, null, ::isHostName)
        } else {
            valid(text.substring(0, colon), text.substring(colon + 1), ::isHostName)
        }
    }

    /** [port] is the text after the colon, or null when the user typed no port. */
    private fun valid(host: String, port: String?, isHost: (String) -> Boolean): ParseResult {
        val number = if (port == null) HostAddress.DEFAULT_PORT else parsePort(port)
        return if (isHost(host) && number != null) {
            ParseResult.Valid(HostAddress(host, number))
        } else {
            ParseResult.Invalid
        }
    }

    private fun parsePort(text: String): Int? =
        if (text.isNotEmpty() && text.all { it in '0'..'9' }) {
            text.toIntOrNull()?.takeIf { it in 1..MAX_PORT }
        } else {
            null
        }

    private fun isHostName(host: String): Boolean =
        host.isNotEmpty() &&
            host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }

    /** Hex digits, colons and dots (IPv4-mapped forms), with an optional `%zone` suffix. */
    private fun isIpv6Literal(host: String): Boolean {
        val address = host.substringBefore('%')
        val zone = host.substringAfter('%', "")
        val zoneValid =
            !host.contains('%') || (zone.isNotEmpty() && zone.all { it.isLetterOrDigit() })
        return zoneValid &&
            address.contains(':') &&
            address.all {
                it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.'
            }
    }
}
