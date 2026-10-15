package io.github.magalz.borealis.library.domain

/** Parses what the user types in the Add host field. */
object HostAddressParser {
    sealed interface ParseResult {
        data class Valid(val address: HostAddress) : ParseResult

        data object Empty : ParseResult

        data object Invalid : ParseResult
    }

    fun parse(input: String): ParseResult = ParseResult.Invalid
}
