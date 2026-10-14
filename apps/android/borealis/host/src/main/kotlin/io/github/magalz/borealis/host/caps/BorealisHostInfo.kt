package io.github.magalz.borealis.host.caps

import io.github.magalz.borealis.nativecore.HostCaps

/** What the PC details dialog shows about a host: its parsed capabilities and its ports. */
data class BorealisHostInfo(val caps: HostCaps, val httpsPort: Int, val httpPort: Int)
