package io.github.magalz.borealis

import io.github.magalz.borealis.nativecore.NativeLibrary

/**
 * Kotlin side of `libborealis_bridge.so`, the ndk-build module in `jni/borealis` that links
 * borealis-core through Prefab.
 */
object NativeBridge {
    private val library = NativeLibrary("borealis_bridge")

    /** The borealis-core version seen from the bridge, or null when the bridge is unavailable. */
    fun coreVersion(): String? = library.call("coreVersion") { nativeCoreVersion() }

    @JvmStatic private external fun nativeCoreVersion(): String?
}
