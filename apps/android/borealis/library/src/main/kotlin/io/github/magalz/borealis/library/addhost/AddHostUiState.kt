package io.github.magalz.borealis.library.addhost

enum class AddHostError {
    /** The field is empty. */
    EMPTY,

    /** The text is not a host name or IP address, or the host cannot be resolved. */
    INVALID,

    /** A private address on a different subnet than this device. */
    WRONG_SUBNET,

    /** The host did not answer. */
    UNREACHABLE,
}

data class AddHostUiState(
    val address: String = "",
    val busy: Boolean = false,
    val error: AddHostError? = null,
)

sealed interface AddHostEffect {
    /** The host was added and reported [name]. The screen says so and closes. */
    data class Added(val name: String) : AddHostEffect

    data object Close : AddHostEffect
}
