package io.github.magalz.borealis.library.shell

import kotlinx.serialization.Serializable

/** The home screen, and the start destination. */
@Serializable internal data object Hosts

@Serializable internal data object AddHost

/** [hostName] is for the title when Hosts knew it. */
@Serializable internal data class Pairing(val hostId: String, val hostName: String? = null)

@Serializable internal data class Apps(val hostId: String)
