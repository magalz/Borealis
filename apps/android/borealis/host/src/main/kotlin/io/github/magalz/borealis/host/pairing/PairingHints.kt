package io.github.magalz.borealis.host.pairing

/** Placeholder: the real rules arrive with the implementation commit. */
object PairingHints {
    fun webUiUrl(address: String, httpPort: Int): String? = null

    fun suggestedDeviceName(model: String?): String = ""

    fun pinHint(
        address: String,
        httpPort: Int,
        model: String?,
        getString: (Int, List<Any>) -> String,
    ): String = ""

    fun unpairHint(address: String, httpPort: Int, getString: (Int, List<Any>) -> String): String =
        ""
}
