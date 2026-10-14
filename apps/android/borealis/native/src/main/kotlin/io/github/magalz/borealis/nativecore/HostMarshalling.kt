package io.github.magalz.borealis.nativecore

/** Longest `appversion` and host message the C ABI accepts, in UTF-8 bytes. */
internal const val APP_VERSION_MAX_BYTES = 64
internal const val MESSAGE_MAX_BYTES = 256

/** Pure encoding and decoding between the Kotlin types and the primitives the JNI carries. */
internal object HostMarshalling {
    fun encodeMessage(message: String?): ByteArray? = TODO()

    fun clampAttempts(attempts: Int): Int = TODO()

    fun decodeCaps(raw: IntArray?): HostCaps? = TODO()

    fun decodeDecision(raw: IntArray?): LaunchDecision? = TODO()
}
