package io.github.magalz.borealis.host.caps

import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.NativeHostRules

class HostInfoReader(private val rules: HostRules) {
    @Suppress("LongParameterList")
    fun read(
        hostId: String?,
        appVersion: String?,
        codecMask: String?,
        httpsPort: Int,
        httpPort: Int,
    ): BorealisHostInfo? = TODO("not implemented")

    companion object {
        @JvmField val SHARED = HostInfoReader(NativeHostRules)
    }
}
